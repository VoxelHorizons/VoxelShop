package org.voxelhorizons.shop.state;

import org.voxelhorizons.shop.market.MarketDefinition;
import org.voxelhorizons.shop.market.MarketEngine;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;

/**
 * Durable, independently authored stock snapshots. Reject corrupt or
 * incompatible data instead of silently resetting supply (and prices).
 */
public final class MarketSnapshotStore {
    private final Path file;

    public MarketSnapshotStore(Path file) { this.file = file; }

    public void restore(MarketEngine engine, Map<String, MarketDefinition> definitions) throws IOException {
        if (!Files.exists(file)) return;
        Properties saved = new Properties();
        try (InputStream input = Files.newInputStream(file)) { saved.load(input); }
        if (!"1".equals(saved.getProperty("format"))) throw new IOException("Unsupported market state format");
        for (MarketDefinition d : definitions.values()) {
            String k = "market." + d.id() + ".";
            if (saved.getProperty(k + "stock") == null) continue;
            if (!d.currency().equals(saved.getProperty(k + "currency"))
                    || d.basePrice().compareTo(new java.math.BigDecimal(saved.getProperty(k + "base", "-1"))) != 0
                    || d.stockCapacity() != Integer.parseInt(saved.getProperty(k + "capacity", "-1")))
                throw new IOException("Market definition changed for " + d.id() + "; explicitly migrate state");
            try {
                engine.restore(d.id(), Integer.parseInt(saved.getProperty(k + "stock")),
                        Long.parseLong(saved.getProperty(k + "revision")));
            } catch (RuntimeException e) {
                throw new IOException("Corrupt persisted stock/revision for " + d.id(), e);
            }
        }
    }

    public void save(MarketEngine engine, Map<String, MarketDefinition> definitions) throws IOException {
        Properties values = new Properties();
        values.setProperty("format", "1");
        for (MarketDefinition d : definitions.values()) {
            String k = "market." + d.id() + ".";
            values.setProperty(k + "currency", d.currency());
            values.setProperty(k + "base", d.basePrice().toPlainString());
            values.setProperty(k + "capacity", Integer.toString(d.stockCapacity()));
            values.setProperty(k + "stock", Integer.toString(engine.stock(d.id())));
            values.setProperty(k + "revision", Long.toString(engine.revision(d.id())));
        }
        atomicProperties(file, values);
    }

    public static void atomicProperties(Path target, Properties values) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".vshop-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.TRUNCATE_EXISTING);
                 FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                values.store(output, "VoxelShop durable state; do not edit while server is online");
                output.flush();
                channel.force(true);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
