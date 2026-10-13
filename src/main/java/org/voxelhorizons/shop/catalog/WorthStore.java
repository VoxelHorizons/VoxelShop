package org.voxelhorizons.shop.catalog;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only import of player-authored ShopGUI+ worth.yml prices. This static
 * reference list NEVER controls VoxelShop dynamic transaction values.
 */
public final class WorthStore {
    private final Map<String, BigDecimal> values;
    private WorthStore(Map<String, BigDecimal> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
    public static WorthStore empty() { return new WorthStore(Map.of()); }

    public static WorthStore read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return empty();
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(16);
        options.setCodePointLimit(2 * 1024 * 1024);
        Object parsed;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            parsed = new Yaml(new SafeConstructor(options)).load(reader);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid worth file: " + invalid.getMessage(), invalid);
        }
        if (!(parsed instanceof Map<?, ?> map))
            throw new IOException("Expected top-level worth material mapping");
        if (map.size() > 10000) throw new IOException("Too many worth entries");
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String material = String.valueOf(entry.getKey()).toUpperCase(Locale.ROOT);
            if (!material.matches("[A-Z0-9_]+"))
                throw new IOException("Invalid worth material key: " + material);
            try {
                BigDecimal amount = new BigDecimal(String.valueOf(entry.getValue()));
                if (amount.scale() > 8 || amount.precision() > 24)
                    throw new IOException("Unsafe worth amount for " + material);
                if (amount.signum() > 0) result.put(material, amount);
                // 0/-1 disables worth entries instead of enabling free trades.
            } catch (NumberFormatException invalid) {
                throw new IOException("Invalid worth amount for " + material, invalid);
            }
        }
        return new WorthStore(result);
    }

    public Optional<BigDecimal> price(String material) {
        if (material == null) return Optional.empty();
        String id = material.toUpperCase(Locale.ROOT);
        if (id.startsWith("MINECRAFT:")) id = id.substring("MINECRAFT:".length());
        return Optional.ofNullable(values.get(id));
    }
    public int size() { return values.size(); }
}
