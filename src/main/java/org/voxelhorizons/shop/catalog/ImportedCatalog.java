package org.voxelhorizons.shop.catalog;

import org.voxelhorizons.shop.market.MarketDefinition;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable migration snapshot: shop display layouts + deduplicated global markets. */
public final class ImportedCatalog {
    private final Map<String, Shop> shops;
    private final Map<String, MarketDefinition> markets;

    ImportedCatalog(Map<String, Shop> shops, Map<String, MarketDefinition> markets) {
        this.shops = Collections.unmodifiableMap(new LinkedHashMap<>(shops));
        this.markets = Collections.unmodifiableMap(new LinkedHashMap<>(markets));
    }
    public Map<String, Shop> shops() { return shops; }
    public Map<String, MarketDefinition> markets() { return markets; }

    public static final class Shop {
        private final String id;
        private final String title;
        private final String currency;
        private final String fillerMaterial;
        private final String fillerName;
        private final List<Offer> offers;

        Shop(String id, String title, String currency, String fillerMaterial, String fillerName, List<Offer> offers) {
            this.id = id;
            this.title = title;
            this.currency = currency;
            this.fillerMaterial = fillerMaterial;
            this.fillerName = fillerName;
            this.offers = Collections.unmodifiableList(new ArrayList<>(offers));
        }
        public String id() { return id; }
        public String title() { return title; }
        public String currency() { return currency; }
        public String fillerMaterial() { return fillerMaterial; }
        public String fillerName() { return fillerName; }
        public List<Offer> offers() { return offers; }
    }

    public static final class Offer {
        private final String id;
        private final String type;
        private final String materialOrSpecial;
        private final int quantity;
        private final int page;
        private final int slot;
        private final BigDecimal baselineBuy;
        private final BigDecimal baselineSell;
        private final String marketId;
        private final BigDecimal sellRatio;
        private final boolean buyEnabled, sellEnabled;
        private final String permission, displayName;
        private final List<String> displayLore;

        Offer(String id, String type, String materialOrSpecial, int quantity, int page, int slot,
              BigDecimal baselineBuy, BigDecimal baselineSell, String marketId, BigDecimal sellRatio) {
            this(id, type, materialOrSpecial, quantity, page, slot, baselineBuy, baselineSell,
                    marketId, sellRatio, true, true, null, null, Collections.emptyList());
        }

        Offer(String id, String type, String materialOrSpecial, int quantity, int page, int slot,
              BigDecimal baselineBuy, BigDecimal baselineSell, String marketId, BigDecimal sellRatio,
              boolean buyEnabled, boolean sellEnabled, String permission, String displayName,
              List<String> displayLore) {
            this.buyEnabled = buyEnabled;
            this.sellEnabled = sellEnabled;
            this.permission = permission;
            this.displayName = displayName;
            this.displayLore = Collections.unmodifiableList(new ArrayList<>(displayLore));
            this.id = id;
            this.type = type;
            this.materialOrSpecial = materialOrSpecial;
            this.quantity = quantity;
            this.page = page;
            this.slot = slot;
            this.baselineBuy = baselineBuy;
            this.baselineSell = baselineSell;
            this.marketId = marketId;
            this.sellRatio = sellRatio;
        }
        public String id() { return id; }
        public String type() { return type; }
        public String materialOrSpecial() { return materialOrSpecial; }
        public int quantity() { return quantity; }
        public int page() { return page; }
        public int slot() { return slot; }
        public BigDecimal baselineBuy() { return baselineBuy; }
        public BigDecimal baselineSell() { return baselineSell; }
        public String marketId() { return marketId; }
        public BigDecimal sellRatio() { return sellRatio; }
        public boolean canBuy() { return buyEnabled; }
        public boolean canSell() { return sellEnabled; }
        public String permission() { return permission; }
        public String displayName() { return displayName; }
        public List<String> displayLore() { return displayLore; }
    }
}
