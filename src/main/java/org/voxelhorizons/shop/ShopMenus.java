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
import org.voxelhorizons.shop.catalog.MainMenuLayout;
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
        MainMenuLayout layout = plugin.mainMenu();
        Holder holder = new Holder(null, 1);
        String title = layout == null
                ? plugin.getConfig().getString("gui.title", "&8VoxelShop") : layout.title();
        Inventory inventory = Bukkit.createInventory(holder, layout == null ? 54 : layout.size(),
                plugin.text().render(player, title, true));
        holder.inventory = inventory;
        if (layout != null) {
            Material filler = match(layout.fillerMaterial());
            if (filler != Material.AIR && filler != Material.BARRIER)
                for (int slot = 0; slot < inventory.getSize(); slot++)
                    inventory.setItem(slot, item(filler,
                            plugin.text().render(player, layout.fillerName(), false), List.of()));
            for (MainMenuLayout.Entry entry : layout.entries()) {
                if (entry.permission() != null && !player.hasPermission(entry.permission())) continue;
                if (entry.shop() != null && !plugin.catalog().shops().containsKey(entry.shop())) continue;
                if (entry.shop() != null && !player.hasPermission("voxelshop.shop." + entry.shop())
                        && !player.hasPermission("voxelshop.shop.*")) continue;
                ItemStack icon;
                try { icon = plugin.items().create(entry.material(), entry.quantity()); }
                catch (RuntimeException unavailable) { icon = new ItemStack(Material.BARRIER); }
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(plugin.text().render(player, entry.name(), false));
                    List<String> lore = new ArrayList<>();
                    String balance = "";
                    if ("balance".equalsIgnoreCase(entry.special())) {
                        try {
                            String currency = plugin.getConfig().getString("economy.default-currency", "coins");
                            balance = plugin.economy().balance(player, currency).toPlainString();
                        } catch (RuntimeException ignored) { balance = "Unavailable"; }
                    }
                    for (String line : entry.lore())
                        lore.add(plugin.text().render(player, line.replace("%balance%", balance), false));
                    meta.setLore(lore);
                    icon.setItemMeta(meta);
                }
                inventory.setItem(entry.slot(), icon);
                if (entry.shop() != null) holder.categoryClicks.put(entry.slot(), entry.shop());
            }
        } else {
            int slot = 0;
            for (ImportedCatalog.Shop shop : plugin.catalog().shops().values()) {
                if (!player.hasPermission("voxelshop.shop." + shop.id())
                        && !player.hasPermission("voxelshop.shop.*")) continue;
                if (slot >= inventory.getSize()) break;
                ItemStack icon = item(Material.CHEST,
                        ChatColor.GOLD + plugin.text().render(player, shop.title(), false),
                        Arrays.asList(ChatColor.GRAY + "Click to browse",
                                ChatColor.DARK_GRAY + shop.id()));
                inventory.setItem(slot, icon);
                holder.categoryClicks.put(slot++, shop.id());
            }
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

        int pages = shop.offers().stream().mapToInt(ImportedCatalog.Offer::page).max().orElse(1);
        if (page > pages) {
            player.sendMessage(ChatColor.RED + "There is no page " + page + " in this shop.");
            return;
        }
        Holder holder = new Holder(shopId, page);
        String title = shop.title().replace("%page%", Integer.toString(page))
                .replace("%pages%", Integer.toString(pages));
        Inventory inventory = Bukkit.createInventory(holder, shop.size(), plugin.text().render(player, title, true));
        holder.inventory = inventory;
        Material fill = match(shop.fillerMaterial());
        if (fill != Material.AIR) {
            for (int i = 0; i < shop.size(); i++)
                inventory.setItem(i, item(fill, plugin.text().render(player, shop.fillerName(), false), java.util.Collections.emptyList()));
        }
        boolean nextPage = false;
        for (ImportedCatalog.Offer offer : shop.offers()) {
            if (offer.page() > page) nextPage = true;
            if (offer.page() != page) continue;
            if (offer.permission() != null && !player.hasPermission(offer.permission())) continue;
            int slot = offer.slot();
            holder.occupiedSlots.add(slot);
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
                icon = plugin.items().create(offer, offer.quantity());
            } catch (RuntimeException unavailable) {
                icon = new ItemStack(Material.BARRIER);
            }
            ItemMeta metadata = icon.getItemMeta();
            if (metadata == null) continue;
            metadata.setDisplayName(offer.displayName() == null
                    ? ChatColor.WHITE + pretty(offer.materialOrSpecial()) : plugin.text().render(player, offer.displayName(), false));
            List<String> lore = new ArrayList<>();
            for (String line : offer.displayLore()) lore.add(plugin.text().render(player, line, false));
            lore.add(ChatColor.GRAY + "Original buy: " + ChatColor.GOLD
                    + (offer.canBuy() ? offer.baselineBuy() : "Disabled"));
            lore.add(ChatColor.GRAY + "Original sell: " + ChatColor.GOLD
                    + (offer.canSell() ? offer.baselineSell() : "Disabled"));
            try {
                if (offer.canBuy()) {
                    MarketQuote buy = plugin.market().quote(offer.marketId(), MarketQuote.Side.BUY, offer.quantity());
                    lore.add(ChatColor.GRAY + "Live buy: " + ChatColor.YELLOW + plugin.trades().displayPrice(player, buy).toPlainString()
                            + " " + buy.currency());
                }
                if (offer.canSell()) {
                    MarketQuote sell = plugin.market().quote(offer.marketId(), MarketQuote.Side.SELL, offer.quantity(), offer.sellRatio());
                    lore.add(ChatColor.GRAY + "Live sell: " + ChatColor.YELLOW + plugin.trades().displayPrice(player, sell).toPlainString()
                            + " " + sell.currency());
                }
            } catch (RuntimeException exception) {
                lore.add(ChatColor.RED + "Market unavailable: " + exception.getMessage());
            }
            for (ClickType clickAction : new ClickType[]{
                    ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT,
                    ClickType.MIDDLE}) {
                ShopClickActions.Action action = ShopClickActions.action(plugin.getConfig(), clickAction, shop.clickActions());
                if (action == ShopClickActions.Action.NONE) continue;
                if ((action == ShopClickActions.Action.BUY || action == ShopClickActions.Action.BUY_GUI)
                        && !offer.canBuy()) continue;
                if ((action == ShopClickActions.Action.SELL || action == ShopClickActions.Action.SELL_ALL
                        || action == ShopClickActions.Action.SELL_GUI) && !offer.canSell()) continue;
                lore.add(ChatColor.GRAY + clickAction.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + ": " + action.name().toLowerCase(Locale.ROOT).replace('_', ' '));
            }
            if (!plugin.trades().ready())
                lore.add(ChatColor.RED + "Trading disabled / pending staff reconciliation");
            metadata.setLore(lore);
            icon.setItemMeta(metadata);
            inventory.setItem(slot, icon);
            holder.offerClicks.put(slot, offer);
        }

        int backSlot = shop.buttonSlot("goBack", 0);
        int previousSlot = shop.buttonSlot("previousPage", shop.size() - 9);
        int nextSlot = shop.buttonSlot("nextPage", shop.size() - 1);
        if (backSlot >= 0 && !holder.occupiedSlots.contains(backSlot)) {
            inventory.setItem(backSlot, navigationIcon(player, shop.buttonIcon("goBack"), "Shop categories",
                    List.of(ChatColor.GRAY + "Return to the shop list")));
            holder.navigation.put(backSlot, 0);
        }
        if (page > 1 && previousSlot >= 0 && !holder.occupiedSlots.contains(previousSlot)
                && !holder.navigation.containsKey(previousSlot)) {
            inventory.setItem(previousSlot, navigationIcon(player, shop.buttonIcon("previousPage"), "Previous page",
                    List.of(ChatColor.GRAY + "Page " + (page - 1))));
            holder.navigation.put(previousSlot, -1);
        }
        if (nextPage && nextSlot >= 0 && !holder.occupiedSlots.contains(nextSlot)
                && !holder.navigation.containsKey(nextSlot)) {
            inventory.setItem(nextSlot, navigationIcon(player, shop.buttonIcon("nextPage"), "Next page",
                    List.of(ChatColor.GRAY + "Page " + (page + 1))));
            holder.navigation.put(nextSlot, 1);
        }
        player.openInventory(inventory);
    }

    private static final int AMOUNT_ONE = -1001;
    private static final int AMOUNT_STACK = -1002;
    private static final int AMOUNT_CONFIRM = -1003;

    /**
     * Increment/decrement amount selection. Only a server-owned holder tracks
     * the choice; the client item's text and size are never trade authority.
     */
    public void openAmount(Player player, String shopId, int page, ImportedCatalog.Offer offer, MarketQuote.Side side) {
        if (side == MarketQuote.Side.BUY && !offer.canBuy()
                || side == MarketQuote.Side.SELL && !offer.canSell()) return;
        ItemStack template;
        try { template = plugin.items().create(offer, 1); }
        catch (RuntimeException failure) {
            player.sendMessage(ChatColor.RED + failure.getMessage());
            return;
        }
        Holder holder = new Holder(shopId, page);
        holder.selectedOffer = offer;
        holder.selectedSide = side;
        holder.maxQuantity = Math.max(1, Math.min(template.getMaxStackSize(),
                Math.min(64, plugin.getConfig().getInt("market.max-order", 64))));
        holder.selectedQuantity = Math.min(holder.maxQuantity, Math.max(1, offer.quantity()));
        Inventory gui = Bukkit.createInventory(holder, 27, plugin.text().render(player,
                "&8Select " + side.name().toLowerCase(Locale.ROOT) + " quantity", true));
        holder.inventory = gui;
        refreshAmount(player, holder);
        player.openInventory(gui);
    }

    private void refreshAmount(Player player, Holder holder) {
        if (holder.selectedOffer == null || holder.inventory == null) return;
        Inventory gui = holder.inventory;
        gui.clear();
        holder.amountClicks.clear();
        gui.setItem(10, item(Material.RED_DYE, ChatColor.RED + "Remove 10", List.of()));
        gui.setItem(11, item(Material.RED_DYE, ChatColor.RED + "Remove 1", List.of()));
        gui.setItem(15, item(Material.LIME_DYE, ChatColor.GREEN + "Add 1", List.of()));
        gui.setItem(16, item(Material.LIME_DYE, ChatColor.GREEN + "Add 10", List.of()));
        gui.setItem(20, item(Material.PAPER, ChatColor.YELLOW + "Set 1", List.of()));
        gui.setItem(24, item(Material.CHEST, ChatColor.YELLOW + "Set max stack", List.of()));
        gui.setItem(22, item(Material.BARRIER, ChatColor.RED + "Cancel", List.of()));
        gui.setItem(23, item(Material.EMERALD, ChatColor.GREEN + "Confirm", List.of(
                ChatColor.GRAY + "Click to complete this trade")));
        holder.amountClicks.put(10, -10);
        holder.amountClicks.put(11, -1);
        holder.amountClicks.put(15, 1);
        holder.amountClicks.put(16, 10);
        holder.amountClicks.put(20, AMOUNT_ONE);
        holder.amountClicks.put(24, AMOUNT_STACK);
        holder.amountClicks.put(23, AMOUNT_CONFIRM);
        holder.navigation.put(22, 2);

        ItemStack selected;
        try { selected = plugin.items().create(holder.selectedOffer, holder.selectedQuantity); }
        catch (RuntimeException failure) { selected = new ItemStack(Material.BARRIER); }
        ItemMeta meta = selected.getItemMeta();
        if (meta != null) {
            String label = holder.selectedOffer.displayName() != null
                    ? plugin.text().render(player, holder.selectedOffer.displayName(), false)
                    : ChatColor.YELLOW + pretty(holder.selectedOffer.materialOrSpecial());
            meta.setDisplayName(label + ChatColor.WHITE + " x" + holder.selectedQuantity);
            List<String> lore = new ArrayList<>();
            try {
                MarketQuote quote = plugin.market().quote(holder.selectedOffer.marketId(),
                        holder.selectedSide, holder.selectedQuantity, holder.selectedOffer.sellRatio());
                lore.add(ChatColor.GRAY + "Quantity: " + ChatColor.WHITE + holder.selectedQuantity);
                lore.add(ChatColor.GRAY + (holder.selectedSide == MarketQuote.Side.BUY ? "Buy" : "Sell")
                        + " total: " + ChatColor.GOLD
                        + plugin.trades().displayPrice(player, quote).toPlainString()
                        + " " + quote.currency());
            } catch (RuntimeException failure) {
                lore.add(ChatColor.RED + "Unavailable: " + failure.getMessage());
                holder.amountClicks.remove(23);
                gui.setItem(23, item(Material.BARRIER, ChatColor.RED + "Cannot trade", List.of()));
            }
            meta.setLore(lore);
            selected.setItemMeta(meta);
        }
        gui.setItem(13, selected);
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
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT
                && click != ClickType.MIDDLE) return;
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
            ShopClickActions.Action action = ShopClickActions.action(plugin.getConfig(), click,
                    plugin.catalog().shops().get(holder.shop).clickActions());
            MarketQuote.Side side = switch (action) {
                case BUY, BUY_GUI -> MarketQuote.Side.BUY;
                case SELL, SELL_GUI, SELL_ALL -> MarketQuote.Side.SELL;
                case NONE -> null;
            };
            if (side == null) return;
            if (side == MarketQuote.Side.BUY && !offer.canBuy()
                    || side == MarketQuote.Side.SELL && !offer.canSell()) return;
            if (action == ShopClickActions.Action.BUY_GUI || action == ShopClickActions.Action.SELL_GUI) {
                Bukkit.getScheduler().runTask(plugin, () -> openAmount(player, holder.shop, holder.page, offer, side));
            } else {
                if (!canTradeClick(player)) return;
                String result = action == ShopClickActions.Action.SELL_ALL
                        ? plugin.trades().sellAll(player, holder.shop, offer)
                        : plugin.trades().trade(player, holder.shop, offer, side, offer.quantity());
                player.sendMessage(ChatColor.YELLOW + result);
                Bukkit.getScheduler().runTask(plugin, () -> openShop(player, holder.shop, holder.page));
            }
        } else if (holder.selectedOffer != null && holder.amountClicks.containsKey(slot)) {
            int action = holder.amountClicks.get(slot);
            if (action == AMOUNT_CONFIRM) {
                if (!canTradeClick(player)) return;
                player.sendMessage(ChatColor.YELLOW + plugin.trades().trade(player, holder.shop,
                        holder.selectedOffer, holder.selectedSide, holder.selectedQuantity));
                Bukkit.getScheduler().runTask(plugin, () -> openShop(player, holder.shop, holder.page));
            } else {
                if (action == AMOUNT_ONE) holder.selectedQuantity = 1;
                else if (action == AMOUNT_STACK) holder.selectedQuantity = holder.maxQuantity;
                else holder.selectedQuantity = Math.max(1,
                        Math.min(holder.maxQuantity, holder.selectedQuantity + action));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder)
                        refreshAmount(player, holder);
                });
            }
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

    private ItemStack navigationIcon(Player player, ImportedCatalog.NavigationIcon configured,
                                     String fallbackTitle, List<String> fallbackLore) {
        if (configured == null) return item(Material.ARROW, ChatColor.YELLOW + fallbackTitle, fallbackLore);
        ItemStack result;
        try { result = plugin.items().create(configured.material(), configured.quantity()); }
        catch (RuntimeException unsupported) {
            plugin.getLogger().warning("Unknown navigation icon: " + configured.material());
            return item(Material.BARRIER, ChatColor.RED + fallbackTitle, fallbackLore);
        }
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(configured.name() == null
                    ? ChatColor.YELLOW + fallbackTitle
                    : plugin.text().render(player, configured.name(), false));
            meta.setLore(configured.lore().isEmpty() ? fallbackLore
                    : configured.lore().stream()
                            .map(line -> plugin.text().render(player, line, false)).toList());
            result.setItemMeta(meta);
        }
        return result;
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
        final java.util.Set<Integer> occupiedSlots = new java.util.HashSet<>();
        final Map<Integer, ImportedCatalog.Offer> offerClicks = new HashMap<>();
        final Map<Integer, Integer> navigation = new HashMap<>();
        final Map<Integer, Integer> amountClicks = new HashMap<>();
        ImportedCatalog.Offer selectedOffer;
        MarketQuote.Side selectedSide;
        int selectedQuantity, maxQuantity;
        Inventory inventory;
        Holder(String shop, int page) { this.shop = shop; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
