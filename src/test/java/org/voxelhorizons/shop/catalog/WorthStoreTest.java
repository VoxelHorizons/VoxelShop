package org.voxelhorizons.shop.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class WorthStoreTest {
    @TempDir Path folder;

    @Test void importsStaticValuesAndIgnoresUnavailableItems() throws Exception {
        Path source = folder.resolve("worth.yml");
        Files.writeString(source, """
                OAK_LOG: 2.50
                DIAMOND: 120
                BEDROCK: -1
                STRUCTURE_VOID: 0
                """);
        WorthStore store = WorthStore.read(source);
        assertEquals(2, store.size());
        assertEquals(new BigDecimal("2.50"), store.price("oak_log").orElseThrow());
        assertTrue(store.price("minecraft:diamond").isPresent());
        assertTrue(store.price("bedrock").isEmpty());
    }

    @Test void invalidPriceAndDuplicateMaterialFail() throws Exception {
        Path file = folder.resolve("worth.yml");
        Files.writeString(file, "DIAMOND: nope\n");
        assertThrows(IOException.class, () -> WorthStore.read(file));
        Files.writeString(file, "DIAMOND: 1\nDIAMOND: 2\n");
        assertThrows(IOException.class, () -> WorthStore.read(file));
    }
}
