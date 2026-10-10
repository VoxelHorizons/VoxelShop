package org.voxelhorizons.shop.market;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded, deterministic market simulation. Not a payment processor.
 *
 * Callers must enforce ownership, inventory capacity, wallet funding and
 * durable commit BEFORE invoking commitStock; production transactions need
 * one serializable transaction across market state, wallet and inventory.
 */
public final class MarketEngine {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private final MarketPolicy policy;
    private final Map<String, State> markets = new HashMap<>();

    public MarketEngine(MarketPolicy policy) {
        this.policy = Objects.requireNonNull(policy);
    }

    public synchronized void register(MarketDefinition definition) {
        Objects.requireNonNull(definition);
        if (markets.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate market: " + definition.id());
        }
        markets.put(definition.id(), new State(definition));
    }

    public synchronized MarketQuote quote(String marketId, MarketQuote.Side side, int quantity) {
        State state = require(marketId);
        Objects.requireNonNull(side, "side");
        if (quantity < 1 || quantity > policy.maxOrder()) {
            throw new IllegalArgumentException("Order quantity is outside configured bounds");
        }
        if (side == MarketQuote.Side.BUY && quantity > state.stock) {
            throw new IllegalStateException("Market is out of stock");
        }
        if (side == MarketQuote.Side.SELL && (long) state.stock + quantity > state.definition.stockCapacity()) {
            throw new IllegalStateException("Market stock capacity exceeded");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < quantity; i++) {
            // Buying depletes supply. Selling increases supply.
            // Each marginal unit is priced from the stock BEFORE its movement.
            long marginalStock = side == MarketQuote.Side.BUY
                    ? state.stock - i : (long) state.stock + i + 1;
            BigDecimal price = marginalPrice(state.definition, marginalStock);
            if (side == MarketQuote.Side.SELL) {
                BigDecimal sellRatio = state.definition.sellRatioOverride() == null
                        ? policy.sellRatio() : state.definition.sellRatioOverride();
                price = price.multiply(sellRatio, MATH);
            }
            total = total.add(price, MATH);
        }
        // Charge up and pay out down. This prevents rounding from leaking money.
        RoundingMode rounding = side == MarketQuote.Side.BUY ? RoundingMode.CEILING : RoundingMode.DOWN;
        total = total.setScale(policy.priceScale(), rounding);
        if (total.signum() <= 0) throw new IllegalStateException("Order rounds to zero");
        return new MarketQuote(marketId, state.definition.currency(), side,
                quantity, state.revision, total);
    }

    public synchronized boolean isCurrent(MarketQuote quote) {
        if (quote == null) return false;
        State state = markets.get(quote.marketId());
        return state != null && state.revision == quote.revision()
                && state.definition.currency().equals(quote.currency())
                && quote.total().equals(quote(quote.marketId(), quote.side(), quote.quantity()).total());
    }

    /**
     * Stock-only test hook. Do NOT use directly for real player purchases:
     * it does not debit or credit wallets or transfer Bukkit items.
     */
    public synchronized void commitStock(MarketQuote quote) {
        if (!isCurrent(quote)) throw new IllegalStateException("Stale market quote");
        State state = require(quote.marketId());
        if (quote.side() == MarketQuote.Side.BUY) state.stock -= quote.quantity();
        else state.stock += quote.quantity();
        state.revision = Math.addExact(state.revision, 1L);
    }

    public synchronized int stock(String marketId) { return require(marketId).stock; }
    public synchronized long revision(String marketId) { return require(marketId).revision; }

    private BigDecimal marginalPrice(MarketDefinition definition, long currentStock) {
        BigDecimal gap = BigDecimal.valueOf((long) definition.targetStock() - currentStock)
                .divide(BigDecimal.valueOf(definition.targetStock()), MATH);
        BigDecimal multiplier = BigDecimal.ONE.add(policy.sensitivity().multiply(gap, MATH), MATH);
        multiplier = multiplier.max(policy.minimumMultiplier()).min(policy.maximumMultiplier());
        return definition.basePrice().multiply(multiplier, MATH);
    }

    private State require(String marketId) {
        State state = markets.get(marketId);
        if (state == null) throw new IllegalArgumentException("Unknown market: " + marketId);
        return state;
    }

    private static final class State {
        private final MarketDefinition definition;
        private int stock;
        private long revision;
        State(MarketDefinition definition) {
            this.definition = definition;
            this.stock = definition.initialStock();
        }
    }
}
