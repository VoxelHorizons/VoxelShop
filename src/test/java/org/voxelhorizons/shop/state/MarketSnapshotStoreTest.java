package org.voxelhorizons.shop.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.voxelhorizons.shop.market.MarketDefinition;
import org.voxelhorizons.shop.market.MarketEngine;
import org.voxelhorizons.shop.market.MarketPolicy;
import org.voxelhorizons.shop.market.MarketQuote;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MarketSnapshotStoreTest {
    @TempDir Path folder;
    private final MarketDefinition def = new MarketDefinition(
            "minecraft:oak_log", "coins", new BigDecimal("10"), 100, 100, 500);

    private MarketEngine engine() {
        MarketEngine e = new MarketEngine(MarketPolicy.defaults());
        e.register(def);
        return e;
    }

    @Test void survivesRestartWithStockAndRevision() throws Exception {
        MarketSnapshotStore store = new MarketSnapshotStore(folder.resolve("markets.properties"));
        MarketEngine original = engine();
        original.commitStock(original.quote(def.id(), MarketQuote.Side.BUY, 12));
        store.save(original, Map.of(def.id(), def));

        MarketEngine restored = engine();
        store.restore(restored, Map.of(def.id(), def));
        assertEquals(88, restored.stock(def.id()));
        assertEquals(1L, restored.revision(def.id()));
        assertEquals(0, original.quote(def.id(), MarketQuote.Side.BUY, 3).total().compareTo(
                restored.quote(def.id(), MarketQuote.Side.BUY, 3).total()));
    }

    @Test void corruptSnapshotFailsInsteadOfResettingPrices() throws Exception {
        Path file = folder.resolve("markets.properties");
        MarketSnapshotStore store = new MarketSnapshotStore(file);
        store.save(engine(), Map.of(def.id(), def));
        java.nio.file.Files.writeString(file, "format=1\nmarket.minecraft\\:oak_log.stock=999999\n"
                + "market.minecraft\\:oak_log.currency=coins\n"
                + "market.minecraft\\:oak_log.base=10\n"
                + "market.minecraft\\:oak_log.capacity=500\n"
                + "market.minecraft\\:oak_log.revision=2\n");
        assertThrows(java.io.IOException.class, () -> store.restore(engine(), Map.of(def.id(), def)));
    }

    @Test void preservesFormerMarketStockWhenCatalogChanges() throws Exception {
        Path path = folder.resolve("market-state.properties");
        MarketSnapshotStore storage = new MarketSnapshotStore(path);
        MarketEngine original = engine();
        original.commitStock(original.quote(def.id(), MarketQuote.Side.BUY, 11));
        storage.save(original, Map.of(def.id(), def));
        assertEquals(89, original.stock(def.id()));

        // Simulate removing / renaming a shop category and adding a variant.
        MarketDefinition variant = new MarketDefinition(
                "minecraft:oak_log.meta_abc123", "coins", new BigDecimal("20"), 100, 100, 500);
        MarketEngine replacement = new MarketEngine(MarketPolicy.defaults());
        replacement.register(variant);
        storage.restore(replacement, Map.of(variant.id(), variant));
        storage.save(replacement, Map.of(variant.id(), variant));

        assertEquals(java.util.List.of(def.id()),
                storage.orphanedMarkets(Map.of(variant.id(), variant)));
        MarketEngine historical = engine();
        storage.restore(historical, Map.of(def.id(), def));
        assertEquals(89, historical.stock(def.id()));
        assertEquals(1L, historical.revision(def.id()));
    }

    @Test void refusesPartiallyDeletedActiveMarketState() throws Exception {
        Path path = folder.resolve("market-state.properties");
        MarketSnapshotStore storage = new MarketSnapshotStore(path);
        storage.save(engine(), Map.of(def.id(), def));
        var props = new java.util.Properties();
        try (var input = java.nio.file.Files.newInputStream(path)) { props.load(input); }
        props.remove("market." + def.id() + ".stock");
        try (var output = java.nio.file.Files.newOutputStream(path)) { props.store(output, null); }
        assertThrows(java.io.IOException.class,
                () -> storage.restore(engine(), Map.of(def.id(), def)));
    }

    @Test void acceptsEmptyInitialSnapshot() throws Exception {
        MarketEngine fresh = engine();
        new MarketSnapshotStore(folder.resolve("never-created.properties"))
                .restore(fresh, Map.of(def.id(), def));
        assertEquals(100, fresh.stock(def.id()));
    }
}
