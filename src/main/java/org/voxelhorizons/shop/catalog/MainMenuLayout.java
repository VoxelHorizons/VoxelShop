package org.voxelhorizons.shop.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable independent representation of a ShopGUI-style category menu. */
public final class MainMenuLayout {
    private final String title, fillerMaterial, fillerName;
    private final int size;
    private final List<Entry> entries;

    public MainMenuLayout(String title, int size, String fillerMaterial, String fillerName, List<Entry> entries) {
        this.title = title;
        this.size = size;
        this.fillerMaterial = fillerMaterial;
        this.fillerName = fillerName;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }
    public String title() { return title; }
    public int size() { return size; }
    public String fillerMaterial() { return fillerMaterial; }
    public String fillerName() { return fillerName; }
    public List<Entry> entries() { return entries; }

    public static final class Entry {
        private final String shop, special, material, name, permission;
        private final int slot, quantity;
        private final List<String> lore;
        public Entry(String shop, String special, String material, String name, String permission,
                     int slot, int quantity, List<String> lore) {
            this.shop = shop; this.special = special; this.material = material;
            this.name = name; this.permission = permission; this.slot = slot; this.quantity = quantity;
            this.lore = Collections.unmodifiableList(new ArrayList<>(lore));
        }
        public String shop() { return shop; }
        public String special() { return special; }
        public String material() { return material; }
        public String name() { return name; }
        public String permission() { return permission; }
        public int slot() { return slot; }
        public int quantity() { return quantity; }
        public List<String> lore() { return lore; }
    }
}
