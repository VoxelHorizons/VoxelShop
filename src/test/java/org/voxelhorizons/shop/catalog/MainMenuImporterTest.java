package org.voxelhorizons.shop.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainMenuImporterTest {
    @TempDir Path folder;

    @Test void preservesSlotsIconsAndSpecialBalance() throws Exception {
        Path source = folder.resolve("menus.yml");
        Files.writeString(source, """
                shopMenuName: ':offset_-8::shop_menu:'
                shopMenuSize: 45
                shopMenuFillItem: {material: BLACK_STAINED_GLASS_PANE, name: ' '}
                shopMenuItems:
                  1:
                    shop: farmer
                    slot: 11
                    item: {material: WHEAT, name: '&2Farmer', lore: ['&7Crops']}
                  2:
                    type: special
                    special: balance
                    slot: 34
                specialElements:
                  balance:
                    item: {material: GOLD_INGOT, name: '&6Balance', lore: ['&7%balance%']}
                """);
        MainMenuLayout layout = MainMenuImporter.read(source);
        assertEquals(45, layout.size());
        assertEquals(":offset_-8::shop_menu:", layout.title());
        assertEquals("BLACK_STAINED_GLASS_PANE", layout.fillerMaterial());
        assertEquals("farmer", layout.entries().get(0).shop());
        assertEquals(11, layout.entries().get(0).slot());
        assertEquals("WHEAT", layout.entries().get(0).material());
        assertEquals("balance", layout.entries().get(1).special());
        assertEquals(34, layout.entries().get(1).slot());
        assertEquals("&6Balance", layout.entries().get(1).name());
    }

    @Test void duplicateSlotsFailClosed() throws Exception {
        Path source = folder.resolve("menus.yml");
        Files.writeString(source, """
                shopMenuItems:
                  1: {shop: farmer, slot: 11}
                  2: {shop: miner, slot: 11}
                """);
        assertThrows(java.io.IOException.class, () -> MainMenuImporter.read(source));
    }
}
