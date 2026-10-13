package org.voxelhorizons.shop.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TradeJournalTest {
    @TempDir Path folder;

    @Test void unfinishedExternalMutationLocksTradesAcrossRestart() throws Exception {
        TradeJournal first = new TradeJournal(folder);
        UUID player = UUID.randomUUID();
        TradeJournal.Entry entry = first.begin(player, "world", "minecraft:diamond", "BUY", 2,
                "coins", new BigDecimal("40.50"));
        entry.stage("DEBIT_IN_PROGRESS");
        TradeJournal second = new TradeJournal(folder);
        assertTrue(second.hasPending());
        assertEquals(1, second.unresolved().size());
        assertTrue(second.unresolved().get(0).contains("DEBIT_IN_PROGRESS"));
        assertThrows(IllegalStateException.class, () -> second.begin(
                player, "world", "minecraft:stone", "SELL", 1, "coins", BigDecimal.ONE));
        second.acknowledge(entry.id());
        assertFalse(second.hasPending());
        assertEquals(0, second.unresolved().size());
    }

    @Test void completedTradeDoesNotBlockFurtherOperations() throws Exception {
        TradeJournal journal = new TradeJournal(folder);
        TradeJournal.Entry first = journal.begin(UUID.randomUUID(), "world",
                "minecraft:diamond", "SELL", 1, "coins", BigDecimal.TEN);
        first.stage("CREDIT_CONFIRMED");
        first.finish();
        assertFalse(new TradeJournal(folder).hasPending());
        TradeJournal.Entry next = journal.begin(UUID.randomUUID(), "world",
                "minecraft:oak_log", "BUY", 1, "coins", BigDecimal.ONE);
        assertNotNull(next.id());
    }

    @Test void cannotAcknowledgeMissingTransaction() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeJournal(folder).acknowledge(UUID.randomUUID()));
    }
}
