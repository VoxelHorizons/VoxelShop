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
                + "  currency: voxel:coins\n"
                + "  items:\n"
                + "    1: {type: item, item: {material: DIAMOND}, buyPrice: 200, sellPrice: 10, slot: 10}\n"
                + "    2: {type: item, item: {material: DIAMOND}, currency: voxel:cosmetics, buyPrice: 30, sellPrice: 3, slot: 11}\n");
        ImportedCatalog result = importShops();
        assertEquals("voxel:coins", result.shops().get("mixed").currency());
        assertEquals(2, result.markets().size());
        assertEquals("voxel:coins", result.markets().get("minecraft:diamond").currency());
        assertEquals("voxel:cosmetics", result.markets().get("voxel:cosmetics:minecraft:diamond").currency());
        assertEquals(0, new BigDecimal("30").compareTo(result.markets().get("voxel:cosmetics:minecraft:diamond").basePrice()));
        assertEquals("voxel:cosmetics:minecraft:diamond", result.shops().get("mixed").offers().get(1).marketId());
    }

    @Test void preservesDifferentShopSellMarginsForOneSharedMarket() throws Exception {
        write("farmer.yml", "farmer:\n  items:\n"
                + "    1: {type: item, item: {material: REDSTONE}, buyPrice: 3.30, sellPrice: 0.17, slot: 10}\n");
        write("miner.yml", "miner:\n  items:\n"
                + "    1: {type: item, item: {material: REDSTONE}, buyPrice: 3.30, sellPrice: 0.49, slot: 11}\n");
        ImportedCatalog result = importShops();
        assertEquals(1, result.markets().size());
        ImportedCatalog.Offer low = result.shops().get("farmer").offers().get(0);
        ImportedCatalog.Offer high = result.shops().get("miner").offers().get(0);
        assertEquals(low.marketId(), high.marketId());
        assertTrue(high.sellRatio().compareTo(low.sellRatio()) > 0);
        org.voxelhorizons.shop.market.MarketEngine engine =
                new org.voxelhorizons.shop.market.MarketEngine(
                        org.voxelhorizons.shop.market.MarketPolicy.defaults());
        engine.register(result.markets().get(low.marketId()));
        BigDecimal first = engine.quote(low.marketId(),
                org.voxelhorizons.shop.market.MarketQuote.Side.SELL, 1, low.sellRatio()).total();
        BigDecimal second = engine.quote(high.marketId(),
                org.voxelhorizons.shop.market.MarketQuote.Side.SELL, 1, high.sellRatio()).total();
        assertTrue(second.compareTo(first) > 0);
    }

    @Test void rejectsConflictingPricesForSharedMarkets() throws Exception {
        write("one.yml", "one:\n  items:\n"
                + "    1: {type: item, item: {material: OAK_LOG}, buyPrice: 9, sellPrice: 1, slot: 10}\n");
        write("two.yml", "two:\n  items:\n"
                + "    1: {type: item, item: {material: OAK_LOG}, buyPrice: 11, sellPrice: 1, slot: 10}\n");
        IOException ex = assertThrows(IOException.class, this::importShops);
        assertTrue(ex.getMessage().contains("Conflicting buy baseline"));
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

    @Test void handlesOptionalVoxelCoreCustomItemIdentityWithoutMaterialConfusion() throws Exception {
        write("custom.yml", "custom:\n  items:\n"
                + "    one: {type: item, item: {voxelcore: 'voxel:watering_can', quantity: 1}, buyPrice: 50, sellPrice: 5, slot: 10}\n"
                + "    two: {type: item, item: {material: PAPER}, buyPrice: 10, sellPrice: 1, slot: 11}\n");
        ImportedCatalog result = importShops();
        assertTrue(result.markets().containsKey("voxelitem:voxel:watering_can"));
        assertTrue(result.markets().containsKey("minecraft:paper"));
        assertEquals("voxelcore:voxel:watering_can",
                result.shops().get("custom").offers().get(0).materialOrSpecial());
    }

    @Test void supportsBuyOnlySellOnlyAndPermissionGatedOffers() throws Exception {
        write("directions.yml", "directions:\n"
                + "  items:\n"
                + "    buy: {type: item, item: {material: DIAMOND, name: '&bGem', lore: ['&7Precious']}, buyPrice: 250, sellPrice: -1, permission: premium.shop.gems, slot: 10}\n"
                + "    sell: {type: item, item: {material: DIRT}, buyPrice: -1, sellPrice: 1.50, slot: 11}\n");
        ImportedCatalog results = importShops();
        ImportedCatalog.Offer buy = results.shops().get("directions").offers().get(0);
        ImportedCatalog.Offer sell = results.shops().get("directions").offers().get(1);
        assertTrue(buy.canBuy());
        assertFalse(buy.canSell());
        assertEquals("premium.shop.gems", buy.permission());
        assertEquals("&bGem", buy.displayName());
        assertEquals(java.util.List.of("&7Precious"), buy.displayLore());
        assertFalse(sell.canBuy());
        assertTrue(sell.canSell());
        assertNotNull(results.markets().get(sell.marketId()));
        assertEquals(0, new BigDecimal("0.15").compareTo(sell.sellRatio()));
    }

    @Test void sellOnlyOfferSharesMarketWithLaterBuyEnabledCategory() throws Exception {
        write("a_selling.yml", "a_selling:\n  items:\n"
                + "    item: {type: item, item: {material: STONE}, buyPrice: -1, sellPrice: 2, slot: 10}\n");
        write("b_buying.yml", "b_buying:\n  items:\n"
                + "    item: {type: item, item: {material: STONE}, buyPrice: 20, sellPrice: 4, slot: 11}\n");
        ImportedCatalog result = importShops();
        assertEquals(1, result.markets().size());
        assertEquals(0, new BigDecimal("20").compareTo(
                result.markets().get("minecraft:stone").basePrice()));
        ImportedCatalog.Offer seller = result.shops().get("a_selling").offers().get(0);
        assertEquals(0, new BigDecimal("0.10").compareTo(seller.sellRatio()));
        assertTrue(seller.canSell());
        assertFalse(seller.canBuy());
    }

    @Test void isolatesEnchantedItemMarketFromUnmodifiedVanillaMaterial() throws Exception {
        write("weapons.yml", "weapons:\n  items:\n"
                + "    plain: {type: item, item: {material: DIAMOND_SWORD}, buyPrice: 50, sellPrice: 5, slot: 10}\n"
                + "    premium: {type: item, item: {material: DIAMOND_SWORD, enchantments: ['SHARPNESS:5'], unbreakable: true, name: '&6Excalibur'}, buyPrice: 5000, sellPrice: 500, slot: 11}\n");
        ImportedCatalog snapshot = importShops();
        assertEquals(2, snapshot.markets().size());
        var plain = snapshot.shops().get("weapons").offers().get(0);
        var premium = snapshot.shops().get("weapons").offers().get(1);
        assertEquals("minecraft:diamond_sword", plain.marketId());
        assertNotEquals(plain.marketId(), premium.marketId());
        assertTrue(premium.marketId().startsWith("minecraft:diamond_sword.meta_"));
        assertEquals(5, premium.itemMetadata().enchantments().get("SHARPNESS"));
        assertTrue(premium.itemMetadata().unbreakable());
        assertEquals("&6Excalibur", premium.itemMetadata().name());
        assertTrue(plain.itemMetadata().isPlain());
    }

    @Test void stableIdentityDeduplicatesEquivalentEnchantmentOrder() throws Exception {
        write("a.yml", "a:\n  items:\n"
                + "    premium: {type: item, item: {material: DIAMOND_PICKAXE, enchantments: ['EFFICIENCY:5', 'UNBREAKING:3']}, buyPrice: 1000, sellPrice: 100, slot: 10}\n");
        write("b.yml", "b:\n  items:\n"
                + "    premium: {type: item, item: {material: DIAMOND_PICKAXE, enchantments: ['UNBREAKING:3', 'EFFICIENCY:5']}, buyPrice: 1000, sellPrice: 150, slot: 10}\n");
        var result = importShops();
        assertEquals(1, result.markets().size());
        assertEquals(result.shops().get("a").offers().get(0).marketId(),
                     result.shops().get("b").offers().get(0).marketId());
    }

    @Test void refusesUnknownComponentInsteadOfDeliveringWrongItem() throws Exception {
        write("invalid.yml", "invalid:\n  items:\n"
                + "    x: {type: item, item: {material: DIAMOND, secretItemData: some_value}, buyPrice: 1000, sellPrice: 10, slot: 10}\n");
        var rejected = assertThrows(IOException.class, this::importShops);
        assertTrue(rejected.getMessage().contains("Unsupported item metadata key"));
    }

    @Test void preservesCustomShopSizeButtonsAndPerShopMouseActions() throws Exception {
        write("compact.yml", "compact:\n"
                + "  name: 'Compact'\n"
                + "  size: 27\n"
                + "  buttons:\n"
                + "    goBack: {slot: 18}\n"
                + "    previousPage: {slot: -1}\n"
                + "    nextPage: {slot: 26}\n"
                + "  clickActions: {LEFT: SELL, RIGHT: BUY_GUI, SHIFT_RIGHT: NONE}\n"
                + "  items:\n"
                + "    x: {type: item, item: {material: DIAMOND}, buyPrice: 100, sellPrice: 10, slot: 10}\n");
        var shop = importShops().shops().get("compact");
        assertEquals(27, shop.size());
        assertEquals(18, shop.buttonSlot("goBack", 0));
        assertEquals(-1, shop.buttonSlot("previousPage", 18));
        assertEquals("SELL", shop.clickActions().get("LEFT"));
        assertEquals("BUY_GUI", shop.clickActions().get("RIGHT"));
    }

    @Test void rejectsOfferOrButtonOutsideCustomInventorySize() throws Exception {
        write("bad.yml", "bad:\n  size: 18\n  items:\n"
                + "    x: {type: item, item: {material: DIAMOND}, buyPrice: 100, sellPrice: 10, slot: 20}\n");
        assertTrue(assertThrows(IOException.class, this::importShops).getMessage().contains("Invalid GUI page/slot"));
        write("bad.yml", "bad:\n  size: 18\n  buttons: {goBack: {slot: 30}}\n  items: {}\n");
        assertTrue(assertThrows(IOException.class, this::importShops).getMessage().contains("Navigation slot"));
    }

    @Test void legacyEnchantmentAndColorAliasesDoNotCreateDuplicateMarkets() throws Exception {
        write("a.yml", "a:\n  items:\n"
                + "    x: {type: item, item: {material: DIAMOND_PICKAXE, name: '&6Miner', enchantments: ['DIG_SPEED:5']}, buyPrice: 250, sellPrice: 25, slot: 10}\n");
        write("b.yml", "b:\n  items:\n"
                + "    x: {type: item, item: {material: DIAMOND_PICKAXE, name: '§6Miner', enchantments: ['EFFICIENCY:5']}, buyPrice: 250, sellPrice: 25, slot: 10}\n");
        var catalog = importShops();
        assertEquals(1, catalog.markets().size());
        assertEquals(catalog.shops().get("a").offers().get(0).marketId(),
                     catalog.shops().get("b").offers().get(0).marketId());
    }

    @Test void refusesUnknownOfferTypeAndFreePrice() throws Exception {
        write("one.yml", "one:\n  items:\n"
                + "    1: {type: item, item: {material: DIAMOND}, buyPrice: 0, sellPrice: 1, slot: 10}\n");
        assertTrue(assertThrows(IOException.class, this::importShops)
                .getMessage().contains("zero price"));
        write("one.yml", "one:\n  items:\n"
                + "    1: {type: mystery, slot: 10}\n");
        assertTrue(assertThrows(IOException.class, this::importShops)
                .getMessage().contains("Unsupported offer"));
    }
}
