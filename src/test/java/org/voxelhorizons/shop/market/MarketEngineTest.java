package org.voxelhorizons.shop.market;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MarketEngineTest {
    private MarketEngine engine() {
        MarketEngine engine = new MarketEngine(MarketPolicy.defaults());
        engine.register(new MarketDefinition("minecraft:oak_log", "voxel:coins",
                new BigDecimal("10.00"), 1000, 1000, 2000));
        return engine;
    }

    @Test void scarcityRaisesAndSurplusLowersPrices() {
        MarketEngine engine = engine();
        BigDecimal atTarget = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 1).total();
        MarketQuote buy = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 64);
        engine.commitStock(buy);
        BigDecimal scarce = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 1).total();
        assertTrue(scarce.compareTo(atTarget) > 0);
        for (int i = 0; i < 10; i++) {
            engine.commitStock(engine.quote("minecraft:oak_log", MarketQuote.Side.SELL, 64));
        }
        BigDecimal surplus = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 1).total();
        assertTrue(surplus.compareTo(atTarget) < 0);
    }

    @Test void staleQuotesAreRejected() {
        MarketEngine engine = engine();
        MarketQuote first = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 1);
        MarketQuote second = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 1);
        engine.commitStock(first);
        assertFalse(engine.isCurrent(second));
        assertThrows(IllegalStateException.class, () -> engine.commitStock(second));
    }

    @Test void noProfitFromImmediateBuyAndResell() {
        MarketEngine engine = engine();
        for (int i = 0; i < 15; i++) {
            MarketQuote buy = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 10);
            engine.commitStock(buy);
            MarketQuote sell = engine.quote("minecraft:oak_log", MarketQuote.Side.SELL, 10);
            assertTrue(sell.total().compareTo(buy.total()) < 0);
            engine.commitStock(sell);
        }
        assertEquals(1000, engine.stock("minecraft:oak_log"));
    }

    @Test void bulkOrdersUseMarginalPricingAndRoundSafely() {
        MarketEngine engine = engine();
        MarketQuote bulk = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 20);
        MarketQuote single = engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 1);
        assertTrue(bulk.total().compareTo(single.total().multiply(BigDecimal.valueOf(20))) > 0);
        assertEquals(2, bulk.total().scale());
        assertThrows(IllegalArgumentException.class, () -> engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 4097));
        assertThrows(IllegalArgumentException.class, () -> engine.quote("minecraft:oak_log", MarketQuote.Side.BUY, 0));
    }

    @Test void rejectsOverflowCapacityAndUnsafeSettings() {
        MarketEngine engine = engine();
        MarketEngine nearlyFull = new MarketEngine(MarketPolicy.defaults());
        nearlyFull.register(new MarketDefinition("minecraft:oak_log", "voxel:coins",
                new BigDecimal("10.00"), 1000, 1990, 2000));
        assertThrows(IllegalStateException.class, () ->
                nearlyFull.quote("minecraft:oak_log", MarketQuote.Side.SELL, 11));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketPolicy(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE,
                        new BigDecimal("0.15"), 64, 2));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketDefinition("x", "coin", new BigDecimal("-1"), 100, 100, 100));
    }

    @Test void supportsIndependentMarketsAndCurrencies() {
        MarketEngine engine = engine();
        engine.register(new MarketDefinition("voxel:hat", "voxel:cosmetics",
                new BigDecimal("30"), 200, 200, 500));
        MarketQuote cosmetic = engine.quote("voxel:hat", MarketQuote.Side.BUY, 1);
        assertEquals("voxel:cosmetics", cosmetic.currency());
        engine.commitStock(cosmetic);
        assertEquals(1000, engine.stock("minecraft:oak_log"));
    }
}
