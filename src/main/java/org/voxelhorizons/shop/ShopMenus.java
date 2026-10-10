package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    public ShopMenus(VoxelShop plugin) { this.plugin = plugin; }

    public void openCategories(Player player) {
        Holder holder = new Holder(null, 1);
        Inventory inventory = Bukkit.createInventory(holder, 54, colors(
                plugin.getConfig().getString("gui.title", "&8VoxelShop")));
        holder.inventory = inventory;
        int slot = 0;
        for (ImportedCatalog.Shop shop : plugin.catalog().shops().values()) {
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
                        String currency = plugin.getConfig().getString("economy.default-currency", "coins");
                        BigDecimal balance = plugin.economy().balance(player, currency);
                        lore.add(ChatColor.WHITE + balance.toPlainString() + " " + currency);
                    } catch (Exception exception) {
                        lore.add(ChatColor.RED + "Currency provider unavailable");
                    }
                    inventory.setItem(slot, item(Material.GOLD_INGOT, ChatColor.GOLD + "Balance", lore));
                }
                continue;
            }
            Material material = match(offer.materialOrSpecial());
            ItemStack icon = new ItemStack(material == Material.AIR ? Material.BARRIER : material,
                    Math.max(1, Math.min(offer.quantity(), Math.max(1, material.getMaxStackSize()))));
            ItemMeta metadata = icon.getItemMeta();
            if (metadata == null) continue;
            metadata.setDisplayName(ChatColor.WHITE + pretty(offer.materialOrSpecial()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Original buy: " + ChatColor.GOLD + offer.baselineBuy());
            lore.add(ChatColor.GRAY + "Original sell: " + ChatColor.GOLD + offer.baselineSell());
            try {
                MarketQuote buy = plugin.market().quote(offer.marketId(), MarketQuote.Side.BUY, 1);
                MarketQuote sell = plugin.market().quote(offer.marketId(), MarketQuote.Side.SELL, 1);
                lore.add(ChatColor.GRAY + "Live buy: " + ChatColor.YELLOW + buy.total().toPlainString()
                        + " " + buy.currency());
                lore.add(ChatColor.GRAY + "Live sell: " + ChatColor.YELLOW + sell.total().toPlainString()
                        + " " + sell.currency());
            } catch (RuntimeException exception) {
                lore.add(ChatColor.RED + "Market unavailable: " + exception.getMessage());
            }
            lore.add(ChatColor.DARK_GRAY + "Preview only — purchases and sales disabled");
            metadata.setLore(lore);
            icon.setItemMeta(metadata);
            inventory.setItem(slot, icon);
            holder.offerClicks.put(slot, offer);
        }

        if (inventory.getItem(0) == null || fill == Material.AIR || !holder.offerClicks.containsKey(0)) {
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        Player player = (Player) event.getWhoClicked();
        Holder holder = (Holder) event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        if (holder.categoryClicks.containsKey(slot)) {
            openShop(player, holder.categoryClicks.get(slot), 1);
        } else if (holder.navigation.containsKey(slot)) {
            int step = holder.navigation.get(slot);
            if (step == 0) openCategories(player);
            else openShop(player, holder.shop, holder.page + step);
        } else if (holder.offerClicks.containsKey(slot)) {
            player.sendMessage(ChatColor.YELLOW + "VoxelShop is running in preview mode. "
                    + "Trading will be enabled only after the transaction-safety layer is complete.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder)
            event.setCancelled(true);
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
        Inventory inventory;
        Holder(String shop, int page) { this.shop = shop; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
