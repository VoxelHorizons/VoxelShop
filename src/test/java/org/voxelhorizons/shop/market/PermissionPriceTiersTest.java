package org.voxelhorizons.shop.market;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class PermissionPriceTiersTest {
    @Test void appliesHighestPriorityOnlyAndNeverStacksRanks() {
        var ranks = new PermissionPriceTiers(List.of(
                new PermissionPriceTiers.Tier("vip", "voxelshop.vip", 10,
                        new BigDecimal("0.95"), new BigDecimal("1.05")),
                new PermissionPriceTiers.Tier("premium", "voxelshop.premium", 20,
                        new BigDecimal("0.80"), new BigDecimal("1.10"))));
        Set<String> permissions = Set.of("voxelshop.vip", "voxelshop.premium");
        assertEquals(new BigDecimal("0.80"),
                ranks.factor(permissions::contains, MarketQuote.Side.BUY));
        assertEquals(new BigDecimal("1.10"),
                ranks.factor(permissions::contains, MarketQuote.Side.SELL));
        assertEquals(BigDecimal.ONE, ranks.factor(ignore -> false, MarketQuote.Side.BUY));
    }

    @Test void rejectsUnsafePricingGroups() {
        assertThrows(IllegalArgumentException.class, () -> new PermissionPriceTiers.Tier(
                "bad", "voxelshop.bad", 1, new BigDecimal("0.01"), BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new PermissionPriceTiers.Tier(
                "bad", "invalid node", 1, BigDecimal.ONE, BigDecimal.ONE));
    }
}
