package org.voxelhorizons.shop;

import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Independently authored bridge. Prefer modern vault2 for named currencies.
 * Route an explicitly configured legacy currency (Essentials coins) to its
 * legacy provider, even if vault2 is also present. Other currencies always
 * use vault2. Never silently route tokens/cosmetics to coins.
 */
public final class VaultUnlockedBridge {
    private final String pluginName;
    private String legacyCurrency = "coins";

    public VaultUnlockedBridge(String pluginName) { this.pluginName = pluginName; }

    public void setLegacyCurrency(String id) {
        if (id == null || !id.matches("[a-z0-9_.:-]+"))
            throw new IllegalArgumentException("Invalid legacy Vault currency ID");
        this.legacyCurrency = id;
    }

    public Optional<Economy> provider() {
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null
                || !registration.getProvider().isEnabled()) return Optional.empty();
        return Optional.of(registration.getProvider());
    }

    private Optional<net.milkbowl.vault.economy.Economy> legacyProvider() {
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration =
                Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (registration == null || registration.getProvider() == null
                || !registration.getProvider().isEnabled()) return Optional.empty();
        return Optional.of(registration.getProvider());
    }

    public boolean available() { return provider().isPresent() || legacyProvider().isPresent(); }

    /**
     * Explicit route: configured legacy Coins use the registered legacy Vault
     * service even when a separate vault2 Tokens provider is also installed.
     */
    private boolean useLegacy(String currency) {
        return legacyCurrency.equalsIgnoreCase(currency) && legacyProvider().isPresent();
    }

    public Economy requireProvider(String currency) {
        Economy economy = provider().orElseThrow(() ->
                new IllegalStateException("No active vault2 Economy provider registered"));
        if (!economy.hasMultiCurrencySupport()) {
            String defaultCurrency = economy.getDefaultCurrency(pluginName);
            if (defaultCurrency == null || !defaultCurrency.equalsIgnoreCase(currency))
                throw new IllegalStateException("Provider does not support currency " + currency);
        } else if (!economy.hasCurrency(currency)) {
            throw new IllegalStateException("Unknown vault2 currency: " + currency);
        }
        return economy;
    }

    private net.milkbowl.vault.economy.Economy requireLegacy(String currency) {
        if (!legacyCurrency.equalsIgnoreCase(currency))
            throw new IllegalStateException("Legacy Vault may only transact " + legacyCurrency);
        return legacyProvider().orElseThrow(() -> new IllegalStateException("No legacy Vault economy provider"));
    }

    public BigDecimal balance(Player player, String currency) {
        if (!useLegacy(currency) && provider().isPresent()) {
            Economy economy = requireProvider(currency);
            if (!economy.accountSupportsCurrency(pluginName, player.getUniqueId(),
                    currency, player.getWorld().getName()))
                throw new IllegalStateException("Wallet does not support " + currency);
            return economy.balance(pluginName, player.getUniqueId(), player.getWorld().getName(), currency);
        }
        double amount = requireLegacy(currency).getBalance(player, player.getWorld().getName());
        if (!Double.isFinite(amount)) throw new IllegalStateException("Invalid legacy Vault balance");
        return BigDecimal.valueOf(amount);
    }

    public String format(BigDecimal amount, String currency) {
        if (!useLegacy(currency) && provider().isPresent()) return requireProvider(currency).format(pluginName, amount, currency);
        return requireLegacy(currency).format(requireSafeDouble(amount));
    }

    public boolean canAfford(Player player, String currency, BigDecimal amount) {
        requirePositive(amount);
        return balance(player, currency).compareTo(amount) >= 0;
    }

    public boolean withdraw(Player player, String currency, BigDecimal amount) {
        requirePositive(amount);
        if (!useLegacy(currency) && provider().isPresent()) {
            var response = requireProvider(currency).withdraw(pluginName, player.getUniqueId(),
                    player.getWorld().getName(), currency, amount);
            return response != null && response.transactionSuccess();
        }
        var response = requireLegacy(currency).withdrawPlayer(player, player.getWorld().getName(),
                requireSafeDouble(amount));
        return response != null && response.transactionSuccess();
    }

    public boolean deposit(Player player, String currency, BigDecimal amount) {
        requirePositive(amount);
        if (!useLegacy(currency) && provider().isPresent()) {
            var response = requireProvider(currency).deposit(pluginName, player.getUniqueId(),
                    player.getWorld().getName(), currency, amount);
            return response != null && response.transactionSuccess();
        }
        var response = requireLegacy(currency).depositPlayer(player, player.getWorld().getName(),
                requireSafeDouble(amount));
        return response != null && response.transactionSuccess();
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Transaction amount must be positive");
    }

    private static double requireSafeDouble(BigDecimal amount) {
        requirePositive(amount);
        if (amount.compareTo(new BigDecimal("1000000000000")) > 0)
            throw new IllegalArgumentException("Legacy Vault amount too large for safe conversion");
        double d = amount.doubleValue();
        if (!Double.isFinite(d) || BigDecimal.valueOf(d).compareTo(amount) != 0)
            throw new IllegalArgumentException("Legacy Vault amount would lose decimal precision");
        return d;
    }
}
