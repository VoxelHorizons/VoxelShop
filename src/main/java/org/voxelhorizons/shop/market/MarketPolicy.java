package org.voxelhorizons.shop.market;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable safety bounds for a market. Prices are always positive, finite decimal
 * amounts; spread means the seller receives a fraction of the marginal buy value.
 */
public final class MarketPolicy {
    private final BigDecimal sensitivity;
    private final BigDecimal minimumMultiplier;
    private final BigDecimal maximumMultiplier;
    private final BigDecimal sellRatio;
    private final int maxOrder;
    private final int priceScale;

    public MarketPolicy(BigDecimal sensitivity, BigDecimal minimumMultiplier,
                        BigDecimal maximumMultiplier, BigDecimal sellRatio,
                        int maxOrder, int priceScale) {
        this.sensitivity = Objects.requireNonNull(sensitivity, "sensitivity");
        this.minimumMultiplier = Objects.requireNonNull(minimumMultiplier, "minimumMultiplier");
        this.maximumMultiplier = Objects.requireNonNull(maximumMultiplier, "maximumMultiplier");
        this.sellRatio = Objects.requireNonNull(sellRatio, "sellRatio");
        if (sensitivity.signum() < 0 || sensitivity.compareTo(BigDecimal.ONE) > 0
                || minimumMultiplier.signum() <= 0
                || maximumMultiplier.compareTo(minimumMultiplier) < 0
                || sellRatio.signum() <= 0 || sellRatio.compareTo(BigDecimal.ONE) >= 0
                || maxOrder < 1 || maxOrder > 4096 || priceScale < 0 || priceScale > 8) {
            throw new IllegalArgumentException("Unsafe market policy");
        }
        this.maxOrder = maxOrder;
        this.priceScale = priceScale;
    }

    public static MarketPolicy defaults() {
        return new MarketPolicy(new BigDecimal("0.35"), new BigDecimal("0.50"),
                new BigDecimal("2.00"), new BigDecimal("0.15"), 64, 2);
    }

    public BigDecimal sensitivity() { return sensitivity; }
    public BigDecimal minimumMultiplier() { return minimumMultiplier; }
    public BigDecimal maximumMultiplier() { return maximumMultiplier; }
    public BigDecimal sellRatio() { return sellRatio; }
    public int maxOrder() { return maxOrder; }
    public int priceScale() { return priceScale; }
}
