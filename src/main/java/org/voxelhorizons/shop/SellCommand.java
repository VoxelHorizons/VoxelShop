package org.voxelhorizons.shop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.voxelhorizons.shop.catalog.ImportedCatalog;
import org.voxelhorizons.shop.market.MarketQuote;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /sell hand [amount], /sell handall and /sell all for ordinary vanilla items. */
public final class SellCommand implements CommandExecutor {
    private final VoxelShop plugin;
    private final Map<UUID, Long> lastCommand = new java.util.HashMap<>();
    public SellCommand(VoxelShop plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can sell inventory items.");
            return true;
        }
        if (!player.hasPermission("voxelshop.use")) {
            player.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /sell hand [amount], /sell handall, /sell all");
            return true;
        }
        if (!plugin.trades().ready()) {
            player.sendMessage(ChatColor.RED + "VoxelShop trading is disabled or requires staff reconciliation.");
            return true;
        }
        long now = System.nanoTime();
        Long prev = lastCommand.get(player.getUniqueId());
        if (prev != null && now - prev < 1_000_000_000L) {
            player.sendMessage(ChatColor.RED + "Please wait before selling again.");
            return true;
        }
        lastCommand.put(player.getUniqueId(), now);

        String mode = args[0].toLowerCase(Locale.ROOT);
        if (mode.equals("hand") || mode.equals("handall")) {
            if (mode.equals("hand") && !player.hasPermission("voxelshop.sell.hand")
                    || mode.equals("handall") && !player.hasPermission("voxelshop.sell.handall")) {
                player.sendMessage(ChatColor.RED + "No permission."); return true;
            }
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand.getType() == Material.AIR || !inHand.isSimilar(new ItemStack(inHand.getType()))) {
                player.sendMessage(ChatColor.RED + "Hold an unmodified vanilla item."); return true;
            }
            int total = mode.equals("handall") ? count(player, inHand.getType()) : inHand.getAmount();
            if (mode.equals("hand") && args.length > 1) {
                try { total = Integer.parseInt(args[1]); }
                catch (NumberFormatException ex) { player.sendMessage(ChatColor.RED + "Invalid quantity."); return true; }
                if (total < 1 || total > inHand.getAmount()) {
                    player.sendMessage(ChatColor.RED + "Amount exceeds the stack in your hand."); return true;
                }
            }
            sellMaterial(player, inHand.getType(), total);
        } else if (mode.equals("all")) {
            if (!player.hasPermission("voxelshop.sell.all")) {
                player.sendMessage(ChatColor.RED + "No permission."); return true;
            }
            Map<Material, Integer> counts = new LinkedHashMap<>();
            for (ItemStack item : player.getInventory().getStorageContents()) {
                if (item == null || item.getType() == Material.AIR) continue;
                if (!item.isSimilar(new ItemStack(item.getType()))) continue;
                counts.merge(item.getType(), item.getAmount(), Integer::sum);
            }
            int orders = 0;
            for (Map.Entry<Material, Integer> item : counts.entrySet()) {
                if (orders >= 8 || !plugin.trades().ready()) break;
                int amount = Math.min(item.getValue(), item.getKey().getMaxStackSize());
                if (amount == 0) continue;
                Selection selection = bestOffer(player, item.getKey(), amount);
                if (selection == null) continue;
                String result = plugin.trades().trade(player, selection.shopId, selection.offer,
                        MarketQuote.Side.SELL, amount);
                player.sendMessage(ChatColor.YELLOW + result);
                orders++;
            }
            if (orders == 0) player.sendMessage(ChatColor.YELLOW + "No sellable vanilla items were found.");
            if (orders == 8) player.sendMessage(ChatColor.GRAY + "Maximum 8 orders per command; run again for remaining items.");
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /sell hand [amount], /sell handall, /sell all");
        }
        return true;
    }

    private void sellMaterial(Player player, Material material, int amount) {
        int complete = 0;
        for (int i = 0; amount > 0 && i < 8 && plugin.trades().ready(); i++) {
            int quantity = Math.min(amount, material.getMaxStackSize());
            Selection selection = bestOffer(player, material, quantity);
            if (selection == null) {
                if (i == 0) player.sendMessage(ChatColor.RED + "No available shop buys this item with default currency.");
                break;
            }
            String message = plugin.trades().trade(player, selection.shopId, selection.offer,
                    MarketQuote.Side.SELL, quantity);
            player.sendMessage(ChatColor.YELLOW + message);
            if (!message.startsWith("Sold ")) break;
            amount -= quantity;
            complete += quantity;
        }
        if (complete > 0 && amount > 0)
            player.sendMessage(ChatColor.GRAY + "Sold " + complete + " items; repeat for remaining stacks.");
    }

    private Selection bestOffer(Player player, Material material, int quantity) {
        String currency = plugin.getConfig().getString("economy.default-currency", "coins");
        Selection selected = null;
        BigDecimal best = BigDecimal.ZERO;
        for (ImportedCatalog.Shop shop : plugin.catalog().shops().values()) {
            if (!player.hasPermission("voxelshop.shop." + shop.id())
                    && !player.hasPermission("voxelshop.shop.*")) continue;
            for (ImportedCatalog.Offer offer : shop.offers()) {
                if (!"item".equals(offer.type())
                        || !material.name().equalsIgnoreCase(offer.materialOrSpecial())) continue;
                if (!currency.equalsIgnoreCase(plugin.catalog().markets().get(offer.marketId()).currency()))
                    continue;
                try {
                    MarketQuote quote = plugin.market().quote(offer.marketId(),
                            MarketQuote.Side.SELL, quantity, offer.sellRatio());
                    if (quote.total().compareTo(best) > 0) {
                        best = quote.total();
                        selected = new Selection(shop.id(), offer);
                    }
                } catch (RuntimeException ignored) { /* This offer cannot accept this quantity. */ }
            }
        }
        return selected;
    }

    private int count(Player player, Material material) {
        int n = 0;
        ItemStack plain = new ItemStack(material);
        for (ItemStack stack : player.getInventory().getStorageContents())
            if (stack != null && stack.isSimilar(plain)) n += stack.getAmount();
        return n;
    }

    private record Selection(String shopId, ImportedCatalog.Offer offer) {}
}
