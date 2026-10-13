package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Reflection boundary for optional VoxelCore. Compile-time dependencies and
 * proprietary ShopGUI+ item-provider classes are intentionally not bundled.
 * Trades compare the full item meta/PDC of created content, not just material
 * or model-data, so renamed vanilla items cannot impersonate VoxelCore items.
 */
public final class ShopItemFactory {
    public ItemStack create(String identity, int quantity) {
        if (identity == null || quantity < 1)
            throw new IllegalArgumentException("Invalid shop item identity or amount");
        ItemStack result;
        if (!identity.startsWith("voxelcore:")) {
            Material material = Material.matchMaterial(identity);
            if (material == null || !material.isItem() || material == Material.AIR)
                throw new IllegalArgumentException("Unsupported vanilla item: " + identity);
            result = new ItemStack(material, quantity);
        } else {
            String contentId = identity.substring("voxelcore:".length());
            Plugin plugin = Bukkit.getPluginManager().getPlugin("VoxelCore");
            if (plugin == null || !plugin.isEnabled())
                throw new IllegalStateException("VoxelCore is required for item " + contentId);
            try {
                Class<?> type = Class.forName("org.voxelhorizons.content.ContentID",
                        true, plugin.getClass().getClassLoader());
                Object id = type.getMethod("parse", String.class, String.class).invoke(null, contentId, "voxel");
                Object manager = plugin.getClass().getMethod("getItemManager").invoke(plugin);
                result = (ItemStack) manager.getClass().getMethod("createItem", type, int.class)
                        .invoke(manager, id, quantity);
            } catch (ReflectiveOperationException | ClassCastException failure) {
                throw new IllegalStateException("VoxelCore item creation failed for " + contentId, failure);
            }
        }
        if (result == null || result.getType() == Material.AIR || result.getAmount() != quantity
                || quantity > result.getMaxStackSize())
            throw new IllegalArgumentException("Invalid or unsupported shop item stack: " + identity);
        return result;
    }

    public boolean matches(ItemStack offered, ItemStack one) {
        return offered != null && offered.getType() != Material.AIR && offered.isSimilar(one);
    }
}
