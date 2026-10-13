package org.voxelhorizons.shop.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable vanilla item metadata. These fields describe the ACTUAL
 * deliverable/sellable stack, not merely its GUI preview. Unrecognised fields
 * are rejected, because silently discarding a component can pay premium
 * prices for ordinary items or deliver the wrong product.
 */
public final class ShopItemMetadata {
    private static final Set<String> SUPPORTED = Set.of(
            "material", "quantity", "name", "lore", "enchantments",
            "damage", "unbreakable", "customModelData", "flags", "mob", "potion", "voxelcore");
    private final String name, mob, potion;
    private final List<String> lore, flags;
    private final Map<String, Integer> enchantments;
    private final Integer damage, customModelData;
    private final boolean unbreakable;

    private ShopItemMetadata(String name, List<String> lore, Map<String, Integer> enchantments,
                             Integer damage, Integer customModelData, boolean unbreakable,
                             List<String> flags, String mob, String potion) {
        this.name = name; this.mob = mob; this.potion = potion;
        this.lore = List.copyOf(lore);
        this.flags = List.copyOf(flags);
        this.enchantments = Collections.unmodifiableMap(new LinkedHashMap<>(enchantments));
        this.damage = damage; this.customModelData = customModelData;
        this.unbreakable = unbreakable;
    }
    public static ShopItemMetadata plain() {
        return new ShopItemMetadata(null, List.of(), Map.of(), null, null, false, List.of(), null, null);
    }
    public static ShopItemMetadata read(Map<?, ?> map, String where, boolean voxelItem) throws IOException {
        for (Object key : map.keySet()) {
            if (!SUPPORTED.contains(String.valueOf(key)))
                throw new IOException("Unsupported item metadata key '" + key + "' at " + where
                        + " — refusing to trade a silently altered item");
        }
        String name = optional(map.get("name"), where, 256);
        List<String> lore = strings(map.get("lore"), where + ".lore", 32, 512);
        Map<String,Integer> enchants = new TreeMap<>();
        Object rawEnchants = map.get("enchantments");
        if (rawEnchants != null) {
            if (!(rawEnchants instanceof List<?> list) || list.size() > 32)
                throw new IOException("Expected enchantments list at " + where);
            for (Object entry : list) {
                if (entry == null) throw new IOException("Null enchantment at " + where);
                String enchantment = String.valueOf(entry);
                int colon = enchantment.lastIndexOf(':');
                if (colon <= 0) throw new IOException("Expected ENCHANTMENT:LEVEL at " + where);
                String key = enchantment.substring(0, colon).toUpperCase(Locale.ROOT);
                if (!key.matches("[A-Z0-9_]+"))
                    throw new IOException("Unsupported enchantment ID " + key + " at " + where);
                int level = number(enchantment.substring(colon + 1), 1, 255, where + ".enchantments");
                if (enchants.putIfAbsent(key, level) != null)
                    throw new IOException("Duplicate enchantment " + key + " at " + where);
            }
        }
        Integer damage = map.get("damage") == null ? null : number(map.get("damage"), 0, 32767, where + ".damage");
        Integer model = map.get("customModelData") == null ? null
                : number(map.get("customModelData"), 0, 16_777_215, where + ".customModelData");
        Object unbreakableValue = map.get("unbreakable");
        if (unbreakableValue != null && !(unbreakableValue instanceof Boolean))
            throw new IOException("unbreakable must be boolean at " + where);
        boolean unbreakable = Boolean.TRUE.equals(unbreakableValue);
        List<String> flags = strings(map.get("flags"), where + ".flags", 16, 64);
        for (String flag : flags) if (!flag.matches("[A-Z0-9_]+"))
            throw new IOException("Invalid item flag " + flag + " at " + where);
        String mob = optional(map.get("mob"), where + ".mob", 64);
        String potion = optional(map.get("potion"), where + ".potion", 64);
        if (mob != null && !mob.matches("[A-Z0-9_]+")
                || potion != null && !potion.matches("[A-Z0-9_]+"))
            throw new IOException("Invalid spawner/potion type at " + where);
        ShopItemMetadata result = new ShopItemMetadata(name, lore, enchants, damage, model,
                unbreakable, flags, mob, potion);
        if (voxelItem && !result.isPlain())
            throw new IOException("VoxelCore content already defines its own item metadata at " + where);
        return result;
    }
    private static String optional(Object value, String where, int length) throws IOException {
        if (value == null) return null;
        if (!(value instanceof String text) || text.length() > length)
            throw new IOException("Invalid item metadata string at " + where);
        return text;
    }
    private static List<String> strings(Object value, String where, int count, int maxLen) throws IOException {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list) || list.size() > count)
            throw new IOException("Invalid list at " + where);
        List<String> result = new ArrayList<>();
        for (Object entry : list) {
            if (!(entry instanceof String s) || s.length() > maxLen)
                throw new IOException("Invalid list entry at " + where);
            result.add(s);
        }
        return List.copyOf(result);
    }
    private static int number(Object value, int low, int high, String where) throws IOException {
        try {
            int n = new java.math.BigDecimal(String.valueOf(value)).intValueExact();
            if (n < low || n > high) throw new NumberFormatException();
            return n;
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid item integer at " + where + ": " + value, invalid);
        }
    }

    public boolean isPlain() {
        return name == null && lore.isEmpty() && enchantments.isEmpty()
                && damage == null && customModelData == null && !unbreakable
                && flags.isEmpty() && mob == null && potion == null;
    }
    /** Stable identity separate from the base material market. */
    public String fingerprint() {
        StringBuilder canonical = new StringBuilder();
        append(canonical, name);
        for (String line : lore) append(canonical, line);
        canonical.append("enchants:");
        new TreeMap<>(enchantments).forEach((k,v) -> append(canonical,k + ":" + v));
        append(canonical, String.valueOf(damage));
        append(canonical, String.valueOf(customModelData));
        append(canonical, Boolean.toString(unbreakable));
        for (String flag : new java.util.TreeSet<>(flags)) append(canonical, flag);
        append(canonical, mob); append(canonical, potion);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 24);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("JVM does not support SHA-256", unavailable);
        }
    }
    private static void append(StringBuilder target, String value) {
        // Length prefix avoids ambiguity even when lore contains separators.
        target.append(value == null ? -1 : value.length()).append(':')
                .append(value == null ? "" : value).append(';');
    }
    public String name() { return name; }
    public String mob() { return mob; }
    public String potion() { return potion; }
    public List<String> lore() { return lore; }
    public List<String> flags() { return flags; }
    public Map<String,Integer> enchantments() { return enchantments; }
    public Integer damage() { return damage; }
    public Integer customModelData() { return customModelData; }
    public boolean unbreakable() { return unbreakable; }
}
