package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.voxelhorizons.shop.catalog.ImportedCatalog;
import org.voxelhorizons.shop.catalog.ShopItemMetadata;
import java.util.Locale;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Reflection boundary for optional VoxelCore. Compile-time dependencies and
 * proprietary ShopGUI+ item-provider classes are intentionally not bundled.
 * Trades compare the full item meta/PDC of created content, not just material
 * or model-data, so renamed vanilla items cannot impersonate VoxelCore items.
 */
public final class ShopItemFactory {
    /** The only item template supported for actual player trades. */
    public ItemStack create(ImportedCatalog.Offer offer, int quantity) {
        ItemStack item = create(offer.materialOrSpecial(), quantity);
        ShopItemMetadata spec = offer.itemMetadata();
        if (spec.isPlain()) return item;
        if (offer.materialOrSpecial().startsWith("voxelcore:"))
            throw new IllegalArgumentException("VoxelCore items cannot override their own metadata");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("Item has no supported metadata");
        if (spec.name() != null)
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', spec.name()));
        if (!spec.lore().isEmpty())
            meta.setLore(spec.lore().stream()
                    .map(s -> ChatColor.translateAlternateColorCodes('&', s)).toList());
        if (spec.unbreakable()) meta.setUnbreakable(true);
        if (spec.customModelData() != null) meta.setCustomModelData(spec.customModelData());
        for (String flag : spec.flags()) {
            try { meta.addItemFlags(ItemFlag.valueOf(flag)); }
            catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown item flag: " + flag, invalid);
            }
        }
        for (Map.Entry<String,Integer> enchantment : spec.enchantments().entrySet()) {
            String id = ShopItemMetadata.canonicalEnchantment(enchantment.getKey());
            Enchantment registered = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(id));
            if (registered == null)
                throw new IllegalArgumentException("Unknown enchantment: " + enchantment.getKey());
            if (!meta.addEnchant(registered, enchantment.getValue(), true))
                throw new IllegalArgumentException("Cannot enchant item with " + enchantment.getKey());
        }
        if (spec.damage() != null) {
            if (!(meta instanceof Damageable damageable))
                throw new IllegalArgumentException("Damage metadata requires damageable material");
            if (spec.damage() >= item.getType().getMaxDurability() && spec.damage() != 0)
                throw new IllegalArgumentException("Damage exceeds item durability");
            damageable.setDamage(spec.damage());
        }
        if (spec.mob() != null) {
            if (!(meta instanceof BlockStateMeta stateMeta)
                    || !(stateMeta.getBlockState() instanceof CreatureSpawner spawner))
                throw new IllegalArgumentException("Spawner mob needs SPAWNER material");
            try { spawner.setSpawnedType(EntityType.valueOf(spec.mob())); }
            catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("Unknown mob type: " + spec.mob(), unknown);
            }
            stateMeta.setBlockState(spawner);
        }
        if (spec.potion() != null) {
            if (!(meta instanceof PotionMeta potionMeta))
                throw new IllegalArgumentException("Potion metadata requires a potion-compatible item");
            try { potionMeta.setBasePotionType(PotionType.valueOf(spec.potion())); }
            catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("Unknown potion: " + spec.potion(), unknown);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

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
