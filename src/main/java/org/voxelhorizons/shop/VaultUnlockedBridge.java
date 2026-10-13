package org.voxelhorizons.shop;

import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * No currency storage in VoxelShop. All balances and payments belong to a
 * separate economy plugin registered with VaultUnlocked's modern service API.
 */
public final class VaultUnlockedBridge {
    private final String pluginName;

    public VaultUnlockedBridge(String pluginName) {
        this.pluginName = pluginName;
    }

    public Optional<Economy> provider() {
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null
                || !registration.getProvider().isEnabled()) {
            return Optional.empty();
        }
        return Optional.of(registration.getProvider());
    }

    public Economy requireProvider(String currency) {
        Economy economy = provider().orElseThrow(() ->
                new IllegalStateException("No active VaultUnlocked Economy (vault2) provider registered"));
        if (!economy.hasMultiCurrencySupport()) {
            String defaultCurrency = economy.getDefaultCurrency(pluginName);
            if (!defaultCurrency.equalsIgnoreCase(currency))
                throw new IllegalStateException("Economy provider only supports default currency: " + defaultCurrency);
        } else if (!economy.hasCurrency(currency)) {
            throw new IllegalStateException("Unknown VaultUnlocked currency: " + currency);
        }
        return economy;
    }

    public BigDecimal balance(Player player, String currency) {
        Economy economy = requireProvider(currency);
        if (!economy.accountSupportsCurrency(pluginName, player.getUniqueId(), currency, player.getWorld().getName()))
            throw new IllegalStateException("Account does not support currency " + currency);
        return economy.balance(pluginName, player.getUniqueId(), player.getWorld().getName(), currency);
    }

    public String format(BigDecimal amount, String currency) {
        Economy economy = requireProvider(currency);
        return economy.format(pluginName, amount, currency);
    }

    /**
     * Preflight only; no inventory or market state is changed here.
     * A future transaction coordinator must control debit and item delivery.
     */
    public boolean canAfford(Player player, String currency, BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        return balance(player, currency).compareTo(amount) >= 0;
    }

    public EconomyResponse withdraw(Player player, String currency, BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        Economy economy = requireProvider(currency);
        return economy.withdraw(pluginName, player.getUniqueId(), player.getWorld().getName(), currency, amount);
    }

    public EconomyResponse deposit(Player player, String currency, BigDecimal amount) {
        if (amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        Economy economy = requireProvider(currency);
        return economy.deposit(pluginName, player.getUniqueId(), player.getWorld().getName(), currency, amount);
    }
}
