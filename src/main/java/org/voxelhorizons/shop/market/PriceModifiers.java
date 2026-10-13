package org.voxelhorizons.shop.market;

import org.voxelhorizons.shop.state.MarketSnapshotStore;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/**
 * Server-authoritative, durable player-market price multipliers.
 * Not a ShopGUIPlus binary-compatible implementation.
 *
 * Multipliers affect final wallet price only, never supply/revisions.
 * Always charge upward and pay downward. Wildcards are explicit.
 */
public final class PriceModifiers {
    private static final BigDecimal LOWER = new BigDecimal("0.25");
    private static final BigDecimal UPPER = new BigDecimal("4.00");
    private final Path file;
    private final Map<String, BigDecimal> overrides = new HashMap<>();

    public PriceModifiers(Path file) throws IOException {
        this.file = Objects.requireNonNull(file);
        if (!Files.exists(file)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) { properties.load(input); }
        if (!"1".equals(properties.getProperty("format"))) throw new IOException("Unsupported price modifier file");
        for (String name : properties.stringPropertyNames()) {
            if ("format".equals(name)) continue;
            try {
                String[] parts = name.split("\\|", 3);
                if (parts.length != 3) throw new IllegalArgumentException("Malformed modifier key");
                UUID.fromString(parts[0]);
                MarketQuote.Side.valueOf(parts[1]);
                checkMarket(parts[2]);
                overrides.put(name, validated(new BigDecimal(properties.getProperty(name))));
            } catch (RuntimeException problem) {
                throw new IOException("Invalid price modifier entry: " + name, problem);
            }
        }
    }

    public static BigDecimal validated(BigDecimal multiplier) {
        if (multiplier == null || multiplier.compareTo(LOWER) < 0
                || multiplier.compareTo(UPPER) > 0 || multiplier.scale() > 6)
            throw new IllegalArgumentException("Multiplier must be between 0.25 and 4.00 (max 6 decimals)");
        return multiplier;
    }

    public synchronized BigDecimal multiplier(UUID player, String marketId, MarketQuote.Side side) {
        checkMarket(marketId);
        Objects.requireNonNull(player);
        Objects.requireNonNull(side);
        return overrides.getOrDefault(key(player, marketId, side),
                overrides.getOrDefault(key(player, "*", side), BigDecimal.ONE));
    }

    public synchronized BigDecimal total(UUID player, MarketQuote quote) {
        return total(player, quote, BigDecimal.ONE);
    }

    /** Applies optional rank adjustment in the same rounding step as overrides. */
    public synchronized BigDecimal total(UUID player, MarketQuote quote, BigDecimal rankFactor) {
        BigDecimal factor = multiplier(player, quote.marketId(), quote.side())
                .multiply(validated(rankFactor));
        BigDecimal adjusted = quote.total().multiply(factor);
        BigDecimal total = adjusted.setScale(quote.total().scale(),
                quote.side() == MarketQuote.Side.BUY ? RoundingMode.CEILING : RoundingMode.DOWN);
        if (total.signum() <= 0) throw new IllegalStateException("Modified quote is zero; trade refused");
        return total;
    }

    public synchronized void set(UUID player, String market, MarketQuote.Side side, BigDecimal multiplier) throws IOException {
        checkMarket(market);
        BigDecimal value = validated(multiplier);
        Map<String, BigDecimal> next = new HashMap<>(overrides);
        next.put(key(player, market, side), value);
        save(next);
        overrides.clear();
        overrides.putAll(next);
    }

    public synchronized void reset(UUID player, String market, MarketQuote.Side side) throws IOException {
        checkMarket(market);
        Map<String, BigDecimal> next = new HashMap<>(overrides);
        next.remove(key(player, market, side));
        save(next);
        overrides.clear();
        overrides.putAll(next);
    }

    private void save(Map<String, BigDecimal> entries) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("format", "1");
        entries.forEach((key, value) -> properties.setProperty(key, value.toPlainString()));
        MarketSnapshotStore.atomicProperties(file, properties);
    }

    private static String key(UUID player, String market, MarketQuote.Side side) {
        return Objects.requireNonNull(player) + "|" + Objects.requireNonNull(side).name() + "|" + market;
    }

    private static void checkMarket(String market) {
        if (market == null || !market.matches("(\\*|[a-z0-9_.:-]+)"))
            throw new IllegalArgumentException("Invalid market ID: " + market);
    }
}
