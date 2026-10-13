package org.voxelhorizons.shop.market;

import java.math.BigDecimal;

/** Conservative cross-shop margin guard independent of current stock. */
public final class MarketRiskGuard {
    private MarketRiskGuard() {}

    public static boolean unsafeRoundTrip(BigDecimal buyFactor, BigDecimal sellFactor,
                                          BigDecimal largestOfferSellRatio) {
        if (buyFactor == null || sellFactor == null || largestOfferSellRatio == null
                || buyFactor.signum() <= 0 || sellFactor.signum() <= 0
                || largestOfferSellRatio.signum() < 0)
            throw new IllegalArgumentException("Invalid market margin factors");
        return largestOfferSellRatio.multiply(sellFactor).compareTo(buyFactor) >= 0;
    }
}
