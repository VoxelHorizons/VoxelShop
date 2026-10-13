package org.voxelhorizons.shop.market;

import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * One highest-priority rank modifier per player and transaction side.
 * Rank modifiers combine with a player's durable override, without stacking
 * multiple groups and without leaking rank permissions across currencies.
 */
public final class PermissionPriceTiers {
    public record Tier(String name, String permission, int priority,
                       BigDecimal buy, BigDecimal sell) {
        public Tier {
            if (name == null || !name.matches("[a-zA-Z0-9_-]+")
                    || permission == null || !permission.matches("[a-zA-Z0-9_.:-]+")
                    || priority < -10000 || priority > 10000)
                throw new IllegalArgumentException("Invalid permission tier");
            PriceModifiers.validated(buy);
            PriceModifiers.validated(sell);
        }
    }

    private final List<Tier> tiers;
    public PermissionPriceTiers(List<Tier> tiers) {
        List<Tier> ordered = new ArrayList<>(tiers);
        ordered.sort(Comparator.comparingInt(Tier::priority).reversed()
                .thenComparing(Tier::name));
        this.tiers = Collections.unmodifiableList(ordered);
    }

    public static PermissionPriceTiers read(ConfigurationSection parent) {
        if (parent == null) return new PermissionPriceTiers(List.of());
        List<Tier> parsed = new ArrayList<>();
        for (String name : parent.getKeys(false)) {
            ConfigurationSection tier = parent.getConfigurationSection(name);
            if (tier == null) throw new IllegalArgumentException("Invalid tier object: " + name);
            String permission = tier.getString("permission");
            int priority = tier.getInt("priority", 0);
            BigDecimal buy = decimal(tier, "buy", "1.0");
            BigDecimal sell = decimal(tier, "sell", "1.0");
            parsed.add(new Tier(name, permission, priority, buy, sell));
        }
        return new PermissionPriceTiers(parsed);
    }

    public BigDecimal factor(Predicate<String> hasPermission, MarketQuote.Side side) {
        Objects.requireNonNull(hasPermission);
        Objects.requireNonNull(side);
        for (Tier tier : tiers)
            if (hasPermission.test(tier.permission()))
                return side == MarketQuote.Side.BUY ? tier.buy() : tier.sell();
        return BigDecimal.ONE;
    }
    public int size() { return tiers.size(); }

    private static BigDecimal decimal(ConfigurationSection config, String path, String fallback) {
        return new BigDecimal(String.valueOf(config.get(path, fallback)));
    }
}
