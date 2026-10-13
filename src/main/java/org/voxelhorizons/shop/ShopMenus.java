package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.voxelhorizons.shop.catalog.ImportedCatalog;
import org.voxelhorizons.shop.market.MarketQuote;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-authoritative GUI browser. Inventory slot mapping is held server-side;
 * all clicks/drags are cancelled, including hotbar swaps and shift-click.
 */
public final class ShopMenus implements Listener {
    private final VoxelShop plugin;
    private final Map<java.util.UUID, Long> lastTradeClick = new HashMap<>();

    public ShopMenus(VoxelShop plugin) { this.plugin = plugin; }

    public void openCategories(Player player) {
        Holder holder = new Holder(null, 1);
        Inventory inventory = Bukkit.createInventory(holder, 54, colors(
                plugin.getConfig().getString("gui.title", "&8VoxelShop")));
        holder.inventory = inventory;
        int slot = 0;
        for (ImportedCatalog.Shop shop : plugin.catalog().shops().values()) {
            if (!player.hasPermission("voxelshop.shop." + shop.id())
                    && !player.hasPermission("voxelshop.shop.*")) continue;
            if (slot >= 54) break;
            ItemStack icon = item(Material.CHEST, ChatColor.GOLD + shop.title(),
                    Arrays.asList(ChatColor.GRAY + "Click to browse", ChatColor.DARK_GRAY + shop.id()));
            inventory.setItem(slot, icon);
            holder.categoryClicks.put(slot++, shop.id());
        }
        player.openInventory(inventory);
    }

    public void openShop(Player player, String shopId, int page) {
        ImportedCatalog.Shop shop = plugin.catalog().shops().get(shopId);
        if (shop == null || page < 1 || page > 100) {
            player.sendMessage(ChatColor.RED + "Unknown shop or page: " + shopId + " " + page);
            return;
        }
        if (!player.hasPermission("voxelshop.shop." + shopId) &&
                !player.hasPermission("voxelshop.shop.*")) {
            player.sendMessage(ChatColor.RED + "You cannot open that shop.");
            return;
        }

        Holder holder = new Holder(shopId, page);
        Inventory inventory = Bukkit.createInventory(holder, 54, colors(shop.title()));
        holder.inventory = inventory;
        Material fill = match(shop.fillerMaterial());
        if (fill != Material.AIR) {
            for (int i = 0; i < 54; i++)
                inventory.setItem(i, item(fill, colors(shop.fillerName()), java.util.Collections.emptyList()));
        }
        boolean nextPage = false;
        for (ImportedCatalog.Offer offer : shop.offers()) {
            if (offer.page() > page) nextPage = true;
            if (offer.page() != page) continue;
            int slot = offer.slot();
            if ("special".equals(offer.type())) {
                if ("balance".equalsIgnoreCase(offer.materialOrSpecial())) {
                    List<String> lore = new ArrayList<>();
                    try {
                        String currency = shop.currency();
                        BigDecimal balance = plugin.economy().balance(player, currency);
                        lore.add(ChatColor.WHITE + balance.toPlainString() + " " + currency);
                    } catch (Exception exception) {
                        lore.add(ChatColor.RED + "Currency provider unavailable");
                    }
                    inventory.setItem(slot, item(Material.GOLD_INGOT, ChatColor.GOLD + "Balance", lore));
                }
                continue;
            }
            ItemStack icon;
            try {
                icon = plugin.items().create(offer.materialOrSpecial(), offer.quantity());
            } catch (RuntimeException unavailable) {
                icon = new ItemStack(Material.BARRIER);
            }
            ItemMeta metadata = icon.getItemMeta();
            if (metadata == null) continue;
            metadata.setDisplayName(ChatColor.WHITE + pretty(offer.materialOrSpecial()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Original buy: " + ChatColor.GOLD + offer.baselineBuy());
            lore.add(ChatColor.GRAY + "Original sell: " + ChatColor.GOLD + offer.baselineSell());
            try {
                MarketQuote buy = plugin.market().quote(offer.marketId(), MarketQuote.Side.BUY, offer.quantity());
                MarketQuote sell = plugin.market().quote(offer.marketId(), MarketQuote.Side.SELL, offer.quantity(), offer.sellRatio());
                lore.add(ChatColor.GRAY + "Live buy: " + ChatColor.YELLOW + buy.total().toPlainString()
                        + " " + buy.currency());
                lore.add(ChatColor.GRAY + "Live sell: " + ChatColor.YELLOW + sell.total().toPlainString()
                        + " " + sell.currency());
            } catch (RuntimeException exception) {
                lore.add(ChatColor.RED + "Market unavailable: " + exception.getMessage());
            }
            lore.add(ChatColor.GRAY + "Left click: buy " + offer.quantity());
            lore.add(ChatColor.GRAY + "Right click: sell " + offer.quantity());
            lore.add(ChatColor.GRAY + "Shift-click: select amount");
            if (!plugin.trades().ready())
                lore.add(ChatColor.RED + "Trading disabled / pending staff reconciliation");
            metadata.setLore(lore);
            icon.setItemMeta(metadata);
            inventory.setItem(slot, icon);
            holder.offerClicks.put(slot, offer);
        }

        if (!holder.offerClicks.containsKey(0) && inventory.getItem(0) == null) {
            inventory.setItem(0, item(Material.ARROW, ChatColor.YELLOW + "Shop categories",
                    java.util.Collections.singletonList(ChatColor.GRAY + "Return to the shop list")));
            holder.navigation.put(0, 0);
        }
        if (page > 1 && !holder.offerClicks.containsKey(45)) {
            inventory.setItem(45, item(Material.ARROW, ChatColor.YELLOW + "Previous page",
                    java.util.Collections.singletonList(ChatColor.GRAY + "Page " + (page - 1))));
            holder.navigation.put(45, -1);
        }
        if (nextPage && !holder.offerClicks.containsKey(53)) {
            inventory.setItem(53, item(Material.ARROW, ChatColor.YELLOW + "Next page",
                    java.util.Collections.singletonList(ChatColor.GRAY + "Page " + (page + 1))));
            holder.navigation.put(53, 1);
        }
        player.openInventory(inventory);
    }

    /**
     * Amount selection is a separate server-owned inventory, not a client
     * price request. Each chosen quantity is requoted on the main thread.
     */
    public void openAmount(Player player, String shopId, int page, ImportedCatalog.Offer offer, MarketQuote.Side side) {
        Holder holder = new Holder(shopId, page);
        holder.selectedOffer = offer;
        holder.selectedSide = side;
        Inventory gui = Bukkit.createInventory(holder, 27, colors("&8Select " + side.name().toLowerCase(Locale.ROOT) + " quantity"));
        holder.inventory = gui;
        int[] quantities = {1, 4, 8, 16, 32, 64};
        int[] slots = {10, 11, 12, 13, 14, 15};
        ItemStack template;
        try {
            template = plugin.items().create(offer.materialOrSpecial(), 1);
        } catch (RuntimeException unavailable) {
            player.sendMessage(ChatColor.RED + unavailable.getMessage());
            return;
        }
        for (int i = 0; i < quantities.length; i++) {
            int q = quantities[i];
            if (q > template.getMaxStackSize()) continue;
            List<String> lore = new ArrayList<>();
            try {
                MarketQuote quote = plugin.market().quote(offer.marketId(), side, q, offer.sellRatio());
                lore.add(ChatColor.GRAY + "Total: " + ChatColor.GOLD + quote.total().toPlainString()
                        + " " + quote.currency());
                holder.amountClicks.put(slots[i], q);
            } catch (RuntimeException unavailable) {
                lore.add(ChatColor.RED + unavailable.getMessage());
            }
            ItemStack button = template.clone();
            button.setAmount(q);
            ItemMeta meta = button.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + Integer.toString(q) + " items");
                meta.setLore(lore);
                button.setItemMeta(meta);
            }
            gui.setItem(slots[i], button);
        }
        gui.setItem(22, item(Material.BARRIER, ChatColor.RED + "Back",
                java.util.Collections.singletonList(ChatColor.GRAY + "Return without trading")));
        holder.navigation.put(22, 2);
        player.openInventory(gui);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        // Never turn events previously cancelled by protections into payment attempts.
        boolean previouslyCancelled = event.isCancelled();
        event.setCancelled(true);
        if (previouslyCancelled) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        Player player = (Player) event.getWhoClicked();
        Holder holder = (Holder) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        if (holder.categoryClicks.containsKey(slot)) {
            String destination = holder.categoryClicks.get(slot);
            Bukkit.getScheduler().runTask(plugin, () -> openShop(player, destination, 1));
        } else if (holder.navigation.containsKey(slot)) {
            int step = holder.navigation.get(slot);
            if (step == 0) Bukkit.getScheduler().runTask(plugin, () -> openCategories(player));
            else if (step == 2) Bukkit.getScheduler().runTask(plugin, () -> openShop(player, holder.shop, holder.page));
            else Bukkit.getScheduler().runTask(plugin, () -> openShop(player, holder.shop, holder.page + step));
        } else if (holder.offerClicks.containsKey(slot)) {
            ImportedCatalog.Offer offer = holder.offerClicks.get(slot);
            MarketQuote.Side side = event.isRightClick() ? MarketQuote.Side.SELL : MarketQuote.Side.BUY;
            if (event.isShiftClick()) {
                Bukkit.getScheduler().runTask(plugin, () -> openAmount(player, holder.shop, holder.page, offer, side));
            } else {
                if (!canTradeClick(player)) return;
                player.sendMessage(ChatColor.YELLOW + plugin.trades().trade(player, holder.shop, offer, side, offer.quantity()));
                Bukkit.getScheduler().runTask(plugin, () -> openShop(player, holder.shop, holder.page));
            }
        } else if (holder.selectedOffer != null && holder.amountClicks.containsKey(slot)) {
            int quantity = holder.amountClicks.get(slot);
            if (!canTradeClick(player)) return;
            player.sendMessage(ChatColor.YELLOW + plugin.trades().trade(player, holder.shop,
                    holder.selectedOffer, holder.selectedSide, quantity));
            Bukkit.getScheduler().runTask(plugin, () -> openShop(player, holder.shop, holder.page));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    private boolean canTradeClick(Player player) {
        // Prevent double-click/hotbar packet bursts from submitting multiple orders.
        long now = System.nanoTime();
        Long previous = lastTradeClick.get(player.getUniqueId());
        if (previous != null && now - previous < 300_000_000L) return false;
        lastTradeClick.put(player.getUniqueId(), now);
        return true;
    }

    private static Material match(String name) {
        Material found = Material.matchMaterial(name == null ? "" : name);
        return found == null ? Material.BARRIER : found;
    }

    private static String pretty(String material) {
        String[] words = material.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
        StringBuilder display = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (display.length() > 0) display.append(' ');
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }

    private static String colors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static ItemStack item(Material material, String display, List<String> lore) {
        ItemStack result = new ItemStack(material);
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(display);
            meta.setLore(lore);
            result.setItemMeta(meta);
        }
        return result;
    }

    private static final class Holder implements InventoryHolder {
        final String shop;
        final int page;
        final Map<Integer, String> categoryClicks = new HashMap<>();
        final Map<Integer, ImportedCatalog.Offer> offerClicks = new HashMap<>();
        final Map<Integer, Integer> navigation = new HashMap<>();
        final Map<Integer, Integer> amountClicks = new HashMap<>();
        ImportedCatalog.Offer selectedOffer;
        MarketQuote.Side selectedSide;
        Inventory inventory;
        Holder(String shop, int page) { this.shop = shop; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
