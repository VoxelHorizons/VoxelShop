package org.voxelhorizons.shop.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adapter boundary for a named currency. VoxelCore's future economy service
 * can implement this interface. All amounts use BigDecimal, never double.
 *
 * withdraw/deposit are NOT atomic together: VoxelShop must introduce
 * a durable transaction coordinator and compensating recovery before trading.
 */
public interface EconomyProvider {
    String currencyId();
    BigDecimal balance(UUID account);
    void withdraw(UUID account, BigDecimal amount, String transactionId);
    void deposit(UUID account, BigDecimal amount, String transactionId);
}
