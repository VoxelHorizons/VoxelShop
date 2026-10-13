package org.voxelhorizons.shop;

import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.voxelhorizons.shop.market.MarketQuote;
import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final VoxelShop plugin;
    public ShopCommand(VoxelShop plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("voxelshop.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload VoxelShop.");
                return true;
            }
            try {
                plugin.reloadConfig();
                plugin.loadCatalog();
                sender.sendMessage(ChatColor.GREEN + "VoxelShop shop catalogue and market previews reloaded.");
            } catch (Exception ex) {
                sender.sendMessage(ChatColor.RED + "Reload failed: " + ex.getMessage());
                plugin.getLogger().warning("Shop catalogue reload rejected: " + ex.getMessage());
            }
            return true;
        }
        if (args.length > 0 && ("pending".equalsIgnoreCase(args[0]) || "reconcile".equalsIgnoreCase(args[0]))) {
            if (!sender.hasPermission("voxelshop.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission."); return true;
            }
            try {
                if ("pending".equalsIgnoreCase(args[0])) {
                    List<String> outstanding = plugin.trades().journal().unresolved();
                    sender.sendMessage(ChatColor.YELLOW + "Unresolved trades: " + outstanding.size());
                    for (String entry : outstanding) sender.sendMessage(ChatColor.GRAY + entry);
                } else if (args.length == 3 && "--confirmed".equalsIgnoreCase(args[2])) {
                    plugin.trades().journal().acknowledge(java.util.UUID.fromString(args[1]));
                    sender.sendMessage(ChatColor.YELLOW + "Marked reviewed; you must have independently reconciled "
                            + "the player's items, wallet and market stock before running this command.");
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /vshop reconcile <transaction UUID> --confirmed "
                            + "(manual reconciliation ONLY)");
                }
            } catch (Exception ex) { sender.sendMessage(ChatColor.RED + "Journal error: " + ex.getMessage()); }
            return true;
        }
        if (args.length > 0 && "modifier".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("voxelshop.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW +
                        "Usage: /vshop modifier <set|reset|check> <online-player|UUID> [market|*] [buy|sell] [multiplier]");
                return true;
            }
            try {
                UUID playerId = resolvePlayer(args[2]);
                String action = args[1].toLowerCase(Locale.ROOT);
                if ("check".equals(action) && args.length == 4) {
                    String marketId = args[3];
                    sender.sendMessage(ChatColor.YELLOW + "Modifiers for " + args[2] + " / " + marketId
                            + ": buy=" + plugin.modifiers().multiplier(playerId, marketId, MarketQuote.Side.BUY)
                            + " sell=" + plugin.modifiers().multiplier(playerId, marketId, MarketQuote.Side.SELL));
                } else if ("set".equals(action) && args.length == 6) {
                    MarketQuote.Side side = MarketQuote.Side.valueOf(args[4].toUpperCase(Locale.ROOT));
                    plugin.modifiers().set(playerId, args[3], side, new BigDecimal(args[5]));
                    sender.sendMessage(ChatColor.GREEN + "Modifier persisted for " + playerId);
                } else if ("reset".equals(action) && args.length == 5) {
                    MarketQuote.Side side = MarketQuote.Side.valueOf(args[4].toUpperCase(Locale.ROOT));
                    plugin.modifiers().reset(playerId, args[3], side);
                    sender.sendMessage(ChatColor.GREEN + "Modifier reset for " + playerId);
                } else {
                    sender.sendMessage(ChatColor.RED +
                            "Usage: /vshop modifier set <player|UUID> <market|*> <buy|sell> <0.25..4.0>"
                            + " OR modifier reset <player|UUID> <market|*> <buy|sell>"
                            + " OR modifier check <player|UUID> <market|*>");
                }
            } catch (Exception error) {
                sender.sendMessage(ChatColor.RED + "Modifier command rejected: " + error.getMessage());
            }
            return true;
        }
        if (args.length > 0 && "worth".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("voxelshop.worth")) {
                sender.sendMessage(ChatColor.RED + "No permission."); return true;
            }
            try {
                String name;
                int amount = 1;
                if (args.length < 2 || "hand".equalsIgnoreCase(args[1])) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Provide a material: /vshop worth <material> [quantity]");
                        return true;
                    }
                    ItemStack held = player.getInventory().getItemInMainHand();
                    name = held.getType().name();
                    amount = held.getAmount();
                } else {
                    name = args[1];
                }
                if (args.length >= 3) amount = Integer.parseInt(args[2]);
                if (amount < 1 || amount > 4096)
                    throw new IllegalArgumentException("Quantity must be between 1 and 4096");
                Material material = Material.matchMaterial(name);
                if (material == null || !material.isItem())
                    throw new IllegalArgumentException("Unsupported material " + name);
                int quantity = amount;
                plugin.worth().price(material.name()).ifPresentOrElse(
                        value -> sender.sendMessage(ChatColor.YELLOW + material.name() + " reference worth: "
                                + value.multiply(BigDecimal.valueOf(quantity)).toPlainString()
                                + " for " + quantity + " items (not a guaranteed trade quote)"),
                        () -> sender.sendMessage(ChatColor.GRAY + "No static worth entry for "
                                + material.name() + " in worth.yml"));
            } catch (RuntimeException invalid) {
                sender.sendMessage(ChatColor.RED + "Worth query rejected: " + invalid.getMessage());
            }
            return true;
        }
        if (args.length > 0 && "check".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("voxelshop.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission."); return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "VoxelShop: " + plugin.catalog().shops().size()
                    + " shops, " + plugin.catalog().markets().size() + " markets, "
                    + plugin.worth().size() + " static worth entries, trading "
                    + (plugin.trades().ready() ? "enabled" : "disabled"));
            if (args.length >= 2) {
                var shop = plugin.catalog().shops().get(args[1].toLowerCase(Locale.ROOT));
                if (shop == null) sender.sendMessage(ChatColor.RED + "Unknown shop: " + args[1]);
                else sender.sendMessage(ChatColor.GRAY + "Shop " + shop.id() + ": "
                        + shop.offers().size() + " entries, currency " + shop.currency());
            }
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can open the shop UI.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("voxelshop.use")) {
            player.sendMessage(ChatColor.RED + "You do not have access to VoxelShop.");
            return true;
        }
        if (args.length == 0) {
            plugin.menus().openCategories(player);
            return true;
        }
        int page = 1;
        if (args.length >= 2) {
            try { page = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) { player.sendMessage(ChatColor.RED + "Invalid shop page."); return true; }
        }
        plugin.menus().openShop(player, args[0].toLowerCase(Locale.ROOT), page);
        return true;
    }

    private static UUID resolvePlayer(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) {
            Player online = Bukkit.getPlayerExact(value);
            if (online == null) throw new IllegalArgumentException("Player must be online or provide a UUID");
            return online.getUniqueId();
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voxelshop.use")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> result = new ArrayList<String>();
            for (String id : plugin.catalog().shops().keySet())
                if (id.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(id);
            if (sender.hasPermission("voxelshop.admin") && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)))
                result.add("reload");
            if (sender.hasPermission("voxelshop.admin")) {
                for (String admin : new String[]{"pending", "reconcile", "modifier", "check"})
                    if (admin.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(admin);
            }
            if ("worth".startsWith(args[0].toLowerCase(Locale.ROOT))
                    && sender.hasPermission("voxelshop.worth")) result.add("worth");
            return result;
        }
        if (args.length == 2 && "modifier".equalsIgnoreCase(args[0])
                && sender.hasPermission("voxelshop.admin")) {
            List<String> result = new ArrayList<>();
            for (String action : new String[]{"set", "reset", "check"})
                if (action.startsWith(args[1].toLowerCase(Locale.ROOT))) result.add(action);
            return result;
        }
        if (args.length == 5 && "modifier".equalsIgnoreCase(args[0])
                && sender.hasPermission("voxelshop.admin")) {
            List<String> result = new ArrayList<>();
            for (String side : new String[]{"buy", "sell"})
                if (side.startsWith(args[4].toLowerCase(Locale.ROOT))) result.add(side);
            return result;
        }
        return Collections.emptyList();
    }
}
