package org.voxelhorizons.shop.market;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PriceModifiersTest {
    @TempDir Path folder;
    private final UUID player = UUID.randomUUID();

    private MarketQuote buy() {
        MarketEngine engine = new MarketEngine(MarketPolicy.defaults());
        engine.register(new MarketDefinition("minecraft:diamond", "coins",
                new BigDecimal("20"), 100, 100, 1000));
        return engine.quote("minecraft:diamond", MarketQuote.Side.BUY, 1);
    }

    @Test void durableWildcardAndExactOverrides() throws Exception {
        Path file = folder.resolve("modifiers.properties");
        PriceModifiers first = new PriceModifiers(file);
        first.set(player, "*", MarketQuote.Side.BUY, new BigDecimal("0.8"));
        assertEquals(new BigDecimal("16.00"), first.total(player, buy()));
        first.set(player, "minecraft:diamond", MarketQuote.Side.BUY, new BigDecimal("0.5"));
        assertEquals(new BigDecimal("10.00"), first.total(player, buy()));

        PriceModifiers restored = new PriceModifiers(file);
        assertEquals(new BigDecimal("10.00"), restored.total(player, buy()));
        restored.reset(player, "minecraft:diamond", MarketQuote.Side.BUY);
        assertEquals(new BigDecimal("16.00"), restored.total(player, buy()));
    }

    @Test void boundariesAndInvalidEntriesFailClosed() throws Exception {
        PriceModifiers service = new PriceModifiers(folder.resolve("m.properties"));
        assertThrows(IllegalArgumentException.class, () ->
                service.set(player, "*", MarketQuote.Side.SELL, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                service.set(player, "*", MarketQuote.Side.BUY, new BigDecimal("5.0")));
        assertThrows(IllegalArgumentException.class, () ->
                service.set(player, "*", MarketQuote.Side.BUY, new BigDecimal("0.123456789")));
        Path file = folder.resolve("corrupt.properties");
        Files.writeString(file, "format=1\nbroken.key=3.5\n");
        assertThrows(IOException.class, () -> new PriceModifiers(file));
    }

    @Test void combinesRankWithPlayerModifierBeforeRounding() throws Exception {
        PriceModifiers service = new PriceModifiers(folder.resolve("m.properties"));
        service.set(player, "*", MarketQuote.Side.BUY, new BigDecimal("0.875"));
        assertEquals(new BigDecimal("14.00"),
                service.total(player, buy(), new BigDecimal("0.80")));
    }

    @Test void defaultsToUnmodifiedPrice() throws Exception {
        PriceModifiers service = new PriceModifiers(folder.resolve("m.properties"));
        assertEquals(buy().total(), service.total(player, buy()));
    }
}
