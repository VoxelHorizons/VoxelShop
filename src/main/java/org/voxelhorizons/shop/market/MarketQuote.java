package org.voxelhorizons.shop.market;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable quote tied to a market revision. A UI quote is NEVER authority
 * for inventory movement or an economy withdrawal.
 */
public final class MarketQuote {
    public enum Side { BUY, SELL }
    private final String marketId;
    private final String currency;
    private final Side side;
    private final int quantity;
    private final long revision;
    private final BigDecimal total;

    MarketQuote(String marketId, String currency, Side side,
                int quantity, long revision, BigDecimal total) {
        this.marketId = Objects.requireNonNull(marketId);
        this.currency = Objects.requireNonNull(currency);
        this.side = Objects.requireNonNull(side);
        this.quantity = quantity;
        this.revision = revision;
        this.total = Objects.requireNonNull(total);
    }

    public String marketId() { return marketId; }
    public String currency() { return currency; }
    public Side side() { return side; }
    public int quantity() { return quantity; }
    public long revision() { return revision; }
    public BigDecimal total() { return total; }
}
