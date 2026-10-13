package org.voxelhorizons.shop.state;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Write-ahead ledger with NO automatic retries for ambiguous economy/inventory
 * operations. External Vault providers do not guarantee idempotency. Unsettled
 * transactions quarantine all trading until reviewed and reconciled by staff.
 */
public final class TradeJournal {
    private final Path pending;
    private final Path closed;

    public TradeJournal(Path folder) throws IOException {
        pending = folder.resolve("pending");
        closed = folder.resolve("completed");
        Files.createDirectories(pending);
        Files.createDirectories(closed);
    }

    public Entry begin(UUID player, String world, String market, String side, int quantity, String currency,
                       java.math.BigDecimal total) throws IOException {
        if (hasPending()) throw new IllegalStateException("Unresolved trades exist; trading is locked");
        Entry e = new Entry(UUID.randomUUID(), player, world, market, side, quantity, currency, total);
        e.stage("INTENT");
        return e;
    }

    public boolean hasPending() throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(pending, "*.properties")) {
            return entries.iterator().hasNext();
        }
    }

    public List<String> unresolved() throws IOException {
        List<String> result = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(pending, "*.properties")) {
            for (Path file : entries) {
                Properties values = new Properties();
                try (InputStream input = Files.newInputStream(file)) { values.load(input); }
                result.add(file.getFileName() + " | " + values.getProperty("stage", "?") + " | "
                        + values.getProperty("player", "?") + " | " + values.getProperty("market", "?")
                        + " | " + values.getProperty("side", "?") + " | " + values.getProperty("amount", "?")
                        + " " + values.getProperty("currency", "?"));
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    /**
     * Staff must separately reconcile wallet, stock and inventory. This action
     * never issues a second wallet mutation or replays an item delivery.
     */
    public void acknowledge(UUID id) throws IOException {
        Path from = pending.resolve(id + ".properties");
        if (!Files.isRegularFile(from)) throw new IllegalArgumentException("No pending entry: " + id);
        Path to = closed.resolve("reviewed-" + id + ".properties");
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    }

    public final class Entry {
        private final UUID id;
        private final Properties values = new Properties();

        private Entry(UUID id, UUID player, String world, String market, String side, int quantity,
                      String currency, java.math.BigDecimal total) {
            this.id = id;
            values.setProperty("id", id.toString());
            values.setProperty("player", player.toString());
            values.setProperty("world", world);
            values.setProperty("market", market);
            values.setProperty("side", side);
            values.setProperty("quantity", Integer.toString(quantity));
            values.setProperty("currency", currency);
            values.setProperty("amount", total.toPlainString());
            values.setProperty("created", java.time.Instant.now().toString());
        }

        public UUID id() { return id; }

        public void stage(String stage) throws IOException {
            values.setProperty("stage", stage);
            MarketSnapshotStore.atomicProperties(pending.resolve(id + ".properties"), values);
        }

        public void finish() throws IOException {
            stage("COMPLETED");
            Files.move(pending.resolve(id + ".properties"),
                    closed.resolve(id + ".properties"), StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
