package org.voxelhorizons.shop.catalog;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads only user-authored YAML, never the vendor plugin's embedded assets.
 * Accepts a full ShopGUIPlus config.yml OR a dedicated VoxelShop menus.yml.
 */
public final class MainMenuImporter {
    private MainMenuImporter() {}

    public static MainMenuLayout read(Path path) throws IOException {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(4 * 1024 * 1024);
        options.setMaxAliasesForCollections(16);
        Object parsed;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            parsed = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (RuntimeException invalid) {
            throw new IOException("Could not parse main-menu YAML " + path + ": " + invalid.getMessage(), invalid);
        }
        Map<?, ?> root = mapping(parsed, "root");
        String title = string(root.get("shopMenuName"), "&8VoxelShop");
        int size = integer(root.get("shopMenuSize"), 54);
        if (size < 9 || size > 54 || size % 9 != 0)
            throw new IOException("Main shop menu must have 9..54 slots in rows of nine");

        Map<?, ?> filler = root.get("shopMenuFillItem") == null ? Map.of()
                : mapping(root.get("shopMenuFillItem"), "shopMenuFillItem");
        Map<?, ?> special = root.get("specialElements") == null ? Map.of()
                : mapping(root.get("specialElements"), "specialElements");
        Map<?, ?> rawEntries = mapping(root.get("shopMenuItems"), "shopMenuItems");
        List<MainMenuLayout.Entry> entries = new ArrayList<>();
        Set<Integer> slots = new HashSet<>();
        for (Map.Entry<?, ?> itemEntry : rawEntries.entrySet()) {
            String where = "shopMenuItems." + itemEntry.getKey();
            Map<?, ?> section = mapping(itemEntry.getValue(), where);
            int slot = integer(section.get("slot"), -1);
            if (slot < 0 || slot >= size || !slots.add(slot))
                throw new IOException("Invalid or duplicate main-menu slot: " + slot + " at " + where);
            String type = string(section.get("type"), "shop").toLowerCase(Locale.ROOT);
            String shop = type.equals("shop") ? string(section.get("shop"), null) : null;
            String specialKey = type.equals("special") ? string(section.get("special"), null) : null;
            if (shop == null && specialKey == null)
                throw new IOException("Missing shop/special at " + where);
            if (!type.equals("shop") && !type.equals("special"))
                throw new IOException("Unsupported main-menu type at " + where);
            Map<?, ?> appearance = section.get("item") == null ? Map.of() :
                    mapping(section.get("item"), where + ".item");
            if (specialKey != null && appearance.isEmpty() && special.containsKey(specialKey)) {
                Map<?, ?> spec = mapping(special.get(specialKey), "specialElements." + specialKey);
                if (spec.get("item") != null) appearance = mapping(spec.get("item"), where + ".item");
            }
            String material = string(appearance.get("material"), specialKey != null ? "GOLD_INGOT" : "CHEST");
            String name = string(appearance.get("name"), specialKey != null ? "&6Balance" : "&e" + shop);
            String permission = string(section.get("permission"), null);
            if (permission != null && !permission.matches("[a-zA-Z0-9_.:-]+"))
                throw new IOException("Invalid main-menu permission at " + where);
            int amount = integer(appearance.get("quantity"), 1);
            if (amount < 1 || amount > 64) throw new IOException("Invalid menu icon quantity at " + where);
            List<String> lore = new ArrayList<>();
            if (appearance.get("lore") != null) {
                if (!(appearance.get("lore") instanceof List<?> lines) || lines.size() > 32)
                    throw new IOException("Invalid lore at " + where);
                for (Object line : lines) lore.add(String.valueOf(line));
            }
            entries.add(new MainMenuLayout.Entry(shop == null ? null : shop.toLowerCase(Locale.ROOT),
                    specialKey, material, name, permission, slot, amount, lore));
        }
        return new MainMenuLayout(title, size, string(filler.get("material"), "AIR"),
                string(filler.get("name"), ""), entries);
    }

    private static Map<?, ?> mapping(Object value, String where) throws IOException {
        if (!(value instanceof Map<?, ?> map)) throw new IOException("Expected mapping at " + where);
        return map;
    }
    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }
    private static int integer(Object value, int fallback) throws IOException {
        if (value == null) return fallback;
        try { return new java.math.BigDecimal(String.valueOf(value)).intValueExact(); }
        catch (RuntimeException wrong) { throw new IOException("Invalid integer " + value, wrong); }
    }
}
