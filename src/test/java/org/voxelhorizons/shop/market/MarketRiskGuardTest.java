package org.voxelhorizons.shop.market;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MarketRiskGuardTest {
    @Test void refusesProfitableRoundTripEvenAtZeroStock() {
        assertTrue(MarketRiskGuard.unsafeRoundTrip(
                new BigDecimal("0.25"), new BigDecimal("4.0"), new BigDecimal("0.15")));
        assertFalse(MarketRiskGuard.unsafeRoundTrip(
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.15")));
        assertTrue(MarketRiskGuard.unsafeRoundTrip(
                new BigDecimal("0.5"), BigDecimal.ONE, new BigDecimal("0.8")));
    }
    @Test void rejectsInvalidFactors() {
        assertThrows(IllegalArgumentException.class, () -> MarketRiskGuard.unsafeRoundTrip(
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE));
    }
}
