package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.shop.catalog.ImportedCatalog;
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
            return plugin.getConfig().getBoolean("trading.enabled", false)
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
        if (!ready()) return "Trading is disabled, or an unresolved transaction needs staff review.";
        ImportedCatalog.Shop shop = plugin.catalog().shops().get(shopId);
        if (shop == null || !shop.offers().contains(offer) || !"item".equals(offer.type()))
            return "This shop offer is no longer available.";
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
        if (side == MarketQuote.Side.BUY && !hasSpace(player, exact, quantity))
            return "Your inventory does not have enough space.";
        if (side == MarketQuote.Side.SELL && count(player, exact) < quantity)
            return "You do not have enough unmodified items to sell.";
        if (side == MarketQuote.Side.BUY) {
            try {
                BigDecimal balance = plugin.economy().balance(player, quote.currency());
                if (balance == null || balance.compareTo(quote.total()) < 0)
                    return "Insufficient " + quote.currency() + ".";
            } catch (RuntimeException failure) {
                return "Could not verify your balance: " + failure.getMessage();
            }
        }

        TradeJournal.Entry entry = null;
        try {
            entry = journal.begin(player.getUniqueId(), player.getWorld().getName(),
                    quote.marketId(), side.name(), quantity, quote.currency(), quote.total());
            if (side == MarketQuote.Side.BUY) {
                // Anything after this stage may have mutated an external wallet.
                entry.stage("DEBIT_IN_PROGRESS");
                boolean accepted = plugin.economy().withdraw(player, quote.currency(), quote.total());
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
                boolean accepted = plugin.economy().deposit(player, quote.currency(), quote.total());
                if (!accepted)
                    throw new IllegalStateException("Economy credit failed or returned an ambiguous result");
                entry.stage("CREDIT_CONFIRMED");
                plugin.market().commitStock(quote);
                snapshots.save(plugin.market(), plugin.catalog().markets());
                entry.stage("STOCK_PERSISTED");
            }
            entry.finish();
            return (side == MarketQuote.Side.BUY ? "Purchased " : "Sold ") + quantity + " "
                    + material.name() + " for " + quote.total().toPlainString() + " " + quote.currency() + ".";
        } catch (Exception exception) {
            plugin.getLogger().severe("TRADE HALTED. Unresolved transaction "
                    + (entry == null ? "(journal write failed)" : entry.id()) + ": " + exception);
            return "Trade interrupted. Trading is locked until staff reconcile the journal. "
                    + "Do not repeat the purchase.";
        }
    }

    public TradeJournal journal() { return journal; }

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
