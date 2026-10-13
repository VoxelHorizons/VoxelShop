package org.voxelhorizons.shop;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

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

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("voxelshop.use")) return Collections.emptyList();
        if (args.length == 1) {
            List<String> result = new ArrayList<String>();
            for (String id : plugin.catalog().shops().keySet())
                if (id.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(id);
            if (sender.hasPermission("voxelshop.admin") && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)))
                result.add("reload");
            if (sender.hasPermission("voxelshop.admin")) {
                for (String admin : new String[]{"pending", "reconcile"})
                    if (admin.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(admin);
            }
            return result;
        }
        return Collections.emptyList();
    }
}
