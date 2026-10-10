package org.voxelhorizons.shop.market;

import java.math.BigDecimal;
import java.util.Objects;

/** A single item's independent market, denominated in a named currency. */
public final class MarketDefinition {
    private final String id;
    private final String currency;
    private final BigDecimal basePrice;
    private final int targetStock;
    private final int initialStock;
    private final int stockCapacity;

    public MarketDefinition(String id, String currency, BigDecimal basePrice,
                            int targetStock, int initialStock, int stockCapacity) {
        this.id = Objects.requireNonNull(id, "id");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice");
        if (!id.matches("[a-z0-9_.:-]+") || !currency.matches("[a-z0-9_.:-]+")
                || basePrice.signum() <= 0 || basePrice.scale() > 8
                || targetStock < 1 || initialStock < 0 || stockCapacity < targetStock
                || initialStock > stockCapacity || stockCapacity > 1_000_000_000) {
            throw new IllegalArgumentException("Invalid market definition");
        }
        this.targetStock = targetStock;
        this.initialStock = initialStock;
        this.stockCapacity = stockCapacity;
    }

    public String id() { return id; }
    public String currency() { return currency; }
    public BigDecimal basePrice() { return basePrice; }
    public int targetStock() { return targetStock; }
    public int initialStock() { return initialStock; }
    public int stockCapacity() { return stockCapacity; }
}
