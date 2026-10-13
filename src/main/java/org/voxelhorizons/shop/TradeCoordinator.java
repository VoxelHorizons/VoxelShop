package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.shop.catalog.ImportedCatalog;
import org.voxelhorizons.shop.api.VoxelShopTradePreEvent;
import org.voxelhorizons.shop.api.VoxelShopTradeCompleteEvent;
import org.voxelhorizons.shop.market.MarketQuote;
import org.voxelhorizons.shop.state.MarketSnapshotStore;
import org.voxelhorizons.shop.state.TradeJournal;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Paper main-thread transaction coordinator.
 *
 * Vault2 wallet calls have no provider-enforced idempotency key. This design
 * records an intent BEFORE any money/item operation; after a crash, any
 * ambiguous transaction blocks trading until manually reconciled. It does
 * not silently refund, replay wallet calls, or re-deliver items.
 */
public final class TradeCoordinator {
    private final VoxelShop plugin;
    private final TradeJournal journal;
    private final MarketSnapshotStore snapshots;

    TradeCoordinator(VoxelShop plugin, TradeJournal journal, MarketSnapshotStore snapshots) {
        this.plugin = plugin;
        this.journal = journal;
        this.snapshots = snapshots;
    }

    public boolean ready() {
        try {
            return plugin.catalogueValid()
                    && plugin.getConfig().getBoolean("trading.enabled", false)
                    && plugin.economy().available()
                    && !journal.hasPending();
        } catch (IOException error) {
            plugin.getLogger().severe("Cannot read transaction journal: " + error.getMessage());
            return false;
        }
    }

    public synchronized String trade(Player player, String shopId, ImportedCatalog.Offer offer,
                                     MarketQuote.Side side, int quantity) {
        if (!Bukkit.isPrimaryThread()) return "Trading must run on the server thread.";
        if (!player.isOnline() || player.isDead())
            return "You must be alive and online to trade.";
        if (!ready()) return "Trading is disabled, or an unresolved transaction needs staff review.";
        ImportedCatalog.Shop shop = plugin.catalog().shops().get(shopId);
        if (shop == null || !shop.offers().contains(offer) || !"item".equals(offer.type()))
            return "This shop offer is no longer available.";
        if (side == MarketQuote.Side.BUY && !offer.canBuy()
                || side == MarketQuote.Side.SELL && !offer.canSell())
            return "This offer does not permit that trade direction.";
        if (offer.permission() != null && !player.hasPermission(offer.permission()))
            return "You do not have permission to trade this offer.";
        if (!player.hasPermission("voxelshop.use") || (!player.hasPermission("voxelshop.shop." + shopId)
                && !player.hasPermission("voxelshop.shop.*")))
            return "You do not have permission to use this shop.";
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
            return "Trading is unavailable in this game mode.";

        ItemStack exact;
        try {
            exact = plugin.items().create(offer.materialOrSpecial(), 1);
        } catch (RuntimeException error) {
            return "This item cannot be traded: " + error.getMessage();
        }
        Material material = exact.getType();
        if (quantity < 1 || quantity > exact.getMaxStackSize())
            return "Invalid amount for this item.";
        MarketQuote quote;
        try {
            quote = plugin.market().quote(offer.marketId(), side, quantity, offer.sellRatio());
        } catch (RuntimeException error) {
            return "Market quote unavailable: " + error.getMessage();
        }
        BigDecimal effectiveTotal;
        try {
            effectiveTotal = plugin.price(player, quote);
            // Prevent per-player discount/sell-bonus combinations that allow
            // immediate buy/sell cycling within one offer's market.
            MarketQuote other = plugin.market().quote(offer.marketId(),
                    side == MarketQuote.Side.BUY ? MarketQuote.Side.SELL : MarketQuote.Side.BUY,
                    quantity, offer.sellRatio());
            BigDecimal opposite = plugin.price(player, other);
            BigDecimal purchase = side == MarketQuote.Side.BUY ? effectiveTotal : opposite;
            BigDecimal sale = side == MarketQuote.Side.SELL ? effectiveTotal : opposite;
            if (sale.compareTo(purchase) >= 0)
                return "Trade rejected: price modifier would permit market arbitrage.";
        } catch (IllegalStateException missingOpposite) {
            // Opposite trade may be out of stock or at maximum market capacity.
            try { effectiveTotal = plugin.price(player, quote); }
            catch (RuntimeException invalid) { return "Invalid price modifier: " + invalid.getMessage(); }
        } catch (RuntimeException invalid) {
            return "Invalid price modifier: " + invalid.getMessage();
        }
        if (side == MarketQuote.Side.BUY && !hasSpace(player, exact, quantity))
            return "Your inventory does not have enough space.";
        if (side == MarketQuote.Side.SELL && count(player, exact) < quantity)
            return "You do not have enough unmodified items to sell.";
        if (side == MarketQuote.Side.BUY) {
            try {
                BigDecimal balance = plugin.economy().balance(player, quote.currency());
                if (balance == null || balance.compareTo(effectiveTotal) < 0)
                    return "Insufficient " + quote.currency() + ".";
            } catch (RuntimeException failure) {
                return "Could not verify your balance: " + failure.getMessage();
            }
        }

        // Give installed integrations a veto BEFORE anything durable or
        // external changes. Recheck invariants after callbacks to prevent
        // handlers from swapping inventory, balances or quote revisions.
        VoxelShopTradePreEvent before = new VoxelShopTradePreEvent(player, shopId,
                offer.id(), side, quantity, quote.currency(), effectiveTotal);
        try {
            Bukkit.getPluginManager().callEvent(before);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Pre-trade listener failed: " + error);
            return "Trade blocked because a pre-trade hook failed.";
        }
        if (before.isCancelled()) return "Trade cancelled by another plugin.";
        if (!player.isOnline() || player.isDead() || !plugin.market().isCurrent(quote)
                || plugin.price(player, quote).compareTo(effectiveTotal) != 0)
            return "Trade conditions changed; reopen the shop.";
        if (side == MarketQuote.Side.BUY && !hasSpace(player, exact, quantity)
                || side == MarketQuote.Side.SELL && count(player, exact) < quantity)
            return "Inventory changed during trade preparation.";
        if (side == MarketQuote.Side.BUY) {
            try {
                if (plugin.economy().balance(player, quote.currency()).compareTo(effectiveTotal) < 0)
                    return "Insufficient " + quote.currency() + " after trade validation.";
            } catch (RuntimeException error) {
                return "Could not revalidate balance: " + error.getMessage();
            }
        }

        TradeJournal.Entry entry = null;
        try {
            entry = journal.begin(player.getUniqueId(), player.getWorld().getName(),
                    quote.marketId(), side.name(), quantity, quote.currency(), effectiveTotal);
            if (side == MarketQuote.Side.BUY) {
                // Anything after this stage may have mutated an external wallet.
                entry.stage("DEBIT_IN_PROGRESS");
                boolean accepted = plugin.economy().withdraw(player, quote.currency(), effectiveTotal);
                if (!accepted)
                    throw new IllegalStateException("Economy debit failed or returned an ambiguous result");
                entry.stage("DEBIT_CONFIRMED");
                plugin.market().commitStock(quote);
                snapshots.save(plugin.market(), plugin.catalog().markets());
                entry.stage("STOCK_PERSISTED");
                entry.stage("DELIVERY_IN_PROGRESS");
                ItemStack delivery = exact.clone();
                delivery.setAmount(quantity);
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(delivery);
                if (!leftovers.isEmpty())
                    throw new IllegalStateException("Unexpected inventory overflow; staff reconciliation required");
                player.saveData();
                entry.stage("PLAYER_DATA_PERSISTED");
            } else {
                entry.stage("REMOVAL_IN_PROGRESS");
                removeExact(player, exact, quantity);
                player.saveData();
                entry.stage("ITEMS_REMOVED_AND_SAVED");
                entry.stage("CREDIT_IN_PROGRESS");
                boolean accepted = plugin.economy().deposit(player, quote.currency(), effectiveTotal);
                if (!accepted)
                    throw new IllegalStateException("Economy credit failed or returned an ambiguous result");
                entry.stage("CREDIT_CONFIRMED");
                plugin.market().commitStock(quote);
                snapshots.save(plugin.market(), plugin.catalog().markets());
                entry.stage("STOCK_PERSISTED");
            }
            entry.finish();
            try {
                Bukkit.getPluginManager().callEvent(new VoxelShopTradeCompleteEvent(player, shopId,
                        offer.id(), side, quantity, quote.currency(), effectiveTotal, entry.id()));
            } catch (RuntimeException listenerError) {
                // Money and inventory have ALREADY committed. A downstream
                // listener failure must never reclassify a complete trade as
                // ambiguous or encourage the player to retry.
                plugin.getLogger().warning("Post-trade listener failed: " + listenerError);
            }
            return (side == MarketQuote.Side.BUY ? "Purchased " : "Sold ") + quantity + " "
                    + material.name() + " for " + effectiveTotal.toPlainString() + " " + quote.currency() + ".";
        } catch (Exception exception) {
            plugin.getLogger().severe("TRADE HALTED. Unresolved transaction "
                    + (entry == null ? "(journal write failed)" : entry.id()) + ": " + exception);
            return "Trade interrupted. Trading is locked until staff reconcile the journal. "
                    + "Do not repeat the purchase.";
        }
    }

    /** Sell all matching ordinary/custom template items in guarded bounded batches. */
    public synchronized String sellAll(Player player, String shopId, ImportedCatalog.Offer offer) {
        if (!offer.canSell()) return "This offer cannot be sold.";
        ItemStack template;
        try {
            template = plugin.items().create(offer.materialOrSpecial(), 1);
        } catch (RuntimeException error) { return "Invalid item template: " + error.getMessage(); }
        int sold = 0;
        int maxBatches = plugin.getConfig().getInt("trading.max-sell-all-orders", 8);
        maxBatches = Math.max(1, Math.min(64, maxBatches));
        for (int i = 0; i < maxBatches && ready(); i++) {
            int available = count(player, template);
            if (available < 1) break;
            org.voxelhorizons.shop.market.MarketDefinition definition =
                    plugin.catalog().markets().get(offer.marketId());
            int room = definition.stockCapacity() - plugin.market().stock(offer.marketId());
            int qty = Math.min(available, Math.min(room, Math.min(64, template.getMaxStackSize())));
            if (qty <= 0) break;
            String result = trade(player, shopId, offer, MarketQuote.Side.SELL, qty);
            if (!result.startsWith("Sold ")) {
                return sold == 0 ? result : "Sold " + sold + " items; remaining batch: " + result;
            }
            sold += qty;
        }
        return sold > 0 ? "Sold " + sold + " items in bulk. Check remaining inventory for more."
                : "No matching items in your inventory to sell.";
    }

    public TradeJournal journal() { return journal; }

    public BigDecimal displayPrice(Player player, MarketQuote quote) {
        return plugin.price(player, quote);
    }

    private static int count(Player player, ItemStack exact) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents())
            if (stack != null && stack.isSimilar(exact)) total += stack.getAmount();
        return total;
    }

    private static boolean hasSpace(Player player, ItemStack exact, int amount) {
        long room = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) room += exact.getMaxStackSize();
            else if (stack.isSimilar(exact))
                room += Math.max(0, stack.getMaxStackSize() - stack.getAmount());
            if (room >= amount) return true;
        }
        return false;
    }

    private static void removeExact(Player player, ItemStack exact, int amount) {
        if (count(player, exact) < amount) throw new IllegalStateException("Sale inventory changed");
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length && amount > 0; slot++) {
            ItemStack stack = storage[slot];
            if (stack == null || !stack.isSimilar(exact)) continue;
            int taken = Math.min(amount, stack.getAmount());
            if (taken == stack.getAmount()) storage[slot] = null;
            else {
                ItemStack remaining = stack.clone();
                remaining.setAmount(stack.getAmount() - taken);
                storage[slot] = remaining;
            }
            amount -= taken;
        }
        if (amount != 0) throw new IllegalStateException("Sale inventory changed during removal");
        player.getInventory().setStorageContents(storage);
    }
}
