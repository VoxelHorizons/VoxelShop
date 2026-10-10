package org.voxelhorizons.shop.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShopGuiCatalogueImporterTest {
    @TempDir Path folder;

    private void write(String name, String yaml) throws IOException {
        Files.write(folder.resolve(name), yaml.getBytes(StandardCharsets.UTF_8));
    }
    private ImportedCatalog importShops() throws IOException {
        return ShopGuiCatalogueImporter.read(folder, "voxel:coins", 1000, 2000);
    }

    @Test void preservesMenuLayoutAndDeduplicatesGlobalMarkets() throws Exception {
        write("lumberjack.yml", "lumberjack:\n"
                + "  name: ':offset_-8::wood_shop:'\n"
                + "  fillItem: {material: BLACK_STAINED_GLASS_PANE, name: ' '}\n"
                + "  items:\n"
                + "    1: {type: item, item: {material: OAK_LOG, quantity: 1}, buyPrice: 9.06, sellPrice: 1.36, slot: 10}\n"
                + "    2: {type: item, item: {material: OAK_PLANKS, quantity: 4}, buyPrice: 8.00, sellPrice: 1.00, slot: 10, page: 2}\n"
                + "    3: {type: special, special: balance, slot: 49}\n");
        write("builder.yml", "builder:\n"
                + "  name: 'Builder'\n"
                + "  items:\n"
                + "    1: {type: item, item: {material: OAK_LOG, quantity: 1}, buyPrice: 9.060, sellPrice: 1.36, slot: 11}\n");
        write("worth.yml", "OAK_LOG: 123456\n");

        ImportedCatalog result = importShops();
        assertEquals(2, result.shops().size());
        assertEquals(2, result.markets().size());
        assertEquals(new BigDecimal("9.06"),
                result.markets().get("minecraft:oak_log").basePrice());
        assertEquals(new BigDecimal("2"),
                result.markets().get("minecraft:oak_planks").basePrice());
        ImportedCatalog.Shop lumberjack = result.shops().get("lumberjack");
        assertEquals(":offset_-8::wood_shop:", lumberjack.title());
        assertEquals("BLACK_STAINED_GLASS_PANE", lumberjack.fillerMaterial());
        assertEquals(3, lumberjack.offers().size());
        assertEquals(2, lumberjack.offers().get(1).page());
        assertEquals(10, lumberjack.offers().get(1).slot());
        assertEquals("special", lumberjack.offers().get(2).type());
        assertEquals("balance", lumberjack.offers().get(2).materialOrSpecial());
    }

    @Test void supportsPerShopAndPerOfferVaultUnlockedCurrencies() throws Exception {
        write("mixed.yml", "mixed:\n"
                + "  currency: coins\n"
                + "  items:\n"
                + "    1: {type: item, item: {material: DIAMOND}, buyPrice: 200, sellPrice: 10, slot: 10}\n"
                + "    2: {type: item, item: {material: DIAMOND}, currency: cosmetics, buyPrice: 30, sellPrice: 3, slot: 11}\n");
        ImportedCatalog result = importShops();
        assertEquals("coins", result.shops().get("mixed").currency());
        assertEquals(2, result.markets().size());
        assertEquals("coins", result.markets().get("minecraft:diamond").currency());
        assertEquals("cosmetics", result.markets().get("cosmetics|minecraft:diamond").currency());
        assertEquals(new BigDecimal("30"), result.markets().get("cosmetics|minecraft:diamond").basePrice());
        assertEquals("cosmetics|minecraft:diamond", result.shops().get("mixed").offers().get(1).marketId());
    }

    @Test void rejectsConflictingPricesForSharedMarkets() throws Exception {
        write("one.yml", "one:\n  items:\n"
                + "    1: {type: item, item: {material: OAK_LOG}, buyPrice: 9, sellPrice: 1, slot: 10}\n");
        write("two.yml", "two:\n  items:\n"
                + "    1: {type: item, item: {material: OAK_LOG}, buyPrice: 11, sellPrice: 1, slot: 10}\n");
        IOException ex = assertThrows(IOException.class, this::importShops);
        assertTrue(ex.getMessage().contains("Conflicting baseline"));
    }

    @Test void rejectsDuplicateKeysOrOverlappingSlots() throws Exception {
        write("one.yml", "one:\n  items:\n"
                + "    1: {type: item, item: {material: OAK_LOG}, buyPrice: 9, sellPrice: 1, slot: 10}\n"
                + "    2: {type: special, special: balance, slot: 10}\n");
        assertTrue(assertThrows(IOException.class, this::importShops)
                .getMessage().contains("Duplicate page/slot"));
        write("one.yml", "one:\n  items: {}\n  items: {}\n");
        assertTrue(assertThrows(IOException.class, this::importShops)
                .getMessage().contains("Invalid YAML"));
    }

    @Test void refusesUnknownOfferTypeAndFreePrice() throws Exception {
        write("one.yml", "one:\n  items:\n"
                + "    1: {type: item, item: {material: DIAMOND}, buyPrice: 0, sellPrice: 1, slot: 10}\n");
        assertTrue(assertThrows(IOException.class, this::importShops)
                .getMessage().contains("disabled or free"));
        write("one.yml", "one:\n  items:\n"
                + "    1: {type: mystery, slot: 10}\n");
        assertTrue(assertThrows(IOException.class, this::importShops)
                .getMessage().contains("Unsupported offer"));
    }
}
