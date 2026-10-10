package org.voxelhorizons.shop.catalog;

import org.voxelhorizons.shop.market.MarketDefinition;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One-time input adapter for common ShopGUIPlus shop YAML structure.
 * Reads user-authored configurations only; no licensed plugin classes or assets.
 * Does NOT register commands, create player inventories or move money/items.
 */
public final class ShopGuiCatalogueImporter {
    private ShopGuiCatalogueImporter() {}

    public static ImportedCatalog read(Path directory, String currency, int targetStock, int capacity) throws IOException {
        Objects.requireNonNull(directory, "directory");
        if (!Files.isDirectory(directory)) throw new IOException("Missing shop configuration directory: " + directory);
        if (currency == null || !currency.matches("[a-z0-9._-]+:[a-z0-9._-]+"))
            throw new IllegalArgumentException("Invalid currency ID");
        if (targetStock < 1 || capacity < targetStock)
            throw new IllegalArgumentException("Invalid market stock targets");

        List<Path> sources = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.yml")) {
            for (Path entry : stream) {
                if (!entry.getFileName().toString().equalsIgnoreCase("worth.yml") && Files.isRegularFile(entry))
                    sources.add(entry);
            }
        }
        sources.sort(Comparator.comparing(p -> p.getFileName().toString()));
        if (sources.isEmpty()) throw new IOException("No shop YAML files found in " + directory);

        Map<String, ImportedCatalog.Shop> shops = new LinkedHashMap<>();
        Map<String, MarketDefinition> markets = new LinkedHashMap<>();
        for (Path source : sources) {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setCodePointLimit(4 * 1024 * 1024);
            options.setMaxAliasesForCollections(16);
            Yaml parser = new Yaml(new SafeConstructor(options));
            Object value;
            try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                value = parser.load(reader);
            } catch (RuntimeException error) {
                throw new IOException("Invalid YAML in " + source + ": " + error.getMessage(), error);
            }
            Map<?, ?> root = mapping(value, source + ": root");
            if (root.size() != 1) throw new IOException("Expected exactly one named shop in " + source);
            Map.Entry<?, ?> named = root.entrySet().iterator().next();
            String shopId = String.valueOf(named.getKey()).toLowerCase(Locale.ROOT);
            if (!shopId.matches("[a-z0-9_-]+") || shops.containsKey(shopId))
                throw new IOException("Duplicate or invalid shop ID: " + shopId);
            Map<?, ?> definition = mapping(named.getValue(), source + ": " + shopId);
            String title = String.valueOf(definition.containsKey("name") ? definition.get("name") : shopId);
            Map<?, ?> filler = definition.containsKey("fillItem") ?
                    mapping(definition.get("fillItem"), shopId + ".fillItem") : java.util.Collections.emptyMap();
            String fillerMaterial = String.valueOf(filler.containsKey("material") ? filler.get("material") : "AIR");
            String fillerName = String.valueOf(filler.containsKey("name") ? filler.get("name") : "");
            Map<?, ?> entries = mapping(definition.get("items"), shopId + ".items");
            List<ImportedCatalog.Offer> offers = new ArrayList<>();
            Set<String> occupied = new HashSet<>();
            for (Map.Entry<?, ?> row : entries.entrySet()) {
                String offerId = String.valueOf(row.getKey());
                String where = shopId + ".items." + offerId;
                Map<?, ?> item = mapping(row.getValue(), where);
                String type = String.valueOf(item.containsKey("type") ? item.get("type") : "item")
                        .toLowerCase(Locale.ROOT);
                int page = integer(item.containsKey("page") ? item.get("page") : 1, where + ".page");
                int slot = integer(item.get("slot"), where + ".slot");
                if (page < 1 || page > 100 || slot < 0 || slot >= 54)
                    throw new IOException("Invalid GUI page/slot at " + where);
                if (!occupied.add(page + ":" + slot))
                    throw new IOException("Duplicate page/slot " + page + ":" + slot + " in shop " + shopId);
                if ("special".equals(type)) {
                    offers.add(new ImportedCatalog.Offer(offerId, type,
                            String.valueOf(item.get("special")), 0, page, slot, null, null, null));
                    continue;
                }
                if (!"item".equals(type))
                    throw new IOException("Unsupported offer type " + type + " at " + where);
                Map<?, ?> stack = mapping(item.get("item"), where + ".item");
                String material = String.valueOf(stack.get("material")).toUpperCase(Locale.ROOT);
                if (!material.matches("[A-Z0-9_]+"))
                    throw new IOException("Invalid material at " + where);
                int quantity = integer(stack.containsKey("quantity") ? stack.get("quantity") : 1, where + ".quantity");
                if (quantity < 1 || quantity > 64) throw new IOException("Invalid item quantity at " + where);
                BigDecimal buy = price(item.get("buyPrice"), where + ".buyPrice");
                BigDecimal sell = price(item.get("sellPrice"), where + ".sellPrice");
                // Negative/unavailable item prices need explicit handling in a later adapter;
                // never turn them into free transactions or silently assign an unsafe value.
                if (buy.signum() <= 0 || sell.signum() < 0)
                    throw new IOException("Unsupported disabled or free price at " + where);
                String marketId = "minecraft:" + material.toLowerCase(Locale.ROOT);
                BigDecimal pricePerUnit = buy.divide(BigDecimal.valueOf(quantity), 8, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                if (pricePerUnit.signum() <= 0)
                    throw new IOException("Calculated market price is zero at " + where);
                MarketDefinition current = markets.get(marketId);
                if (current == null) {
                    markets.put(marketId, new MarketDefinition(marketId, currency, pricePerUnit,
                            targetStock, targetStock, capacity));
                } else if (current.basePrice().compareTo(pricePerUnit) != 0) {
                    throw new IOException("Conflicting baseline for global market " + marketId
                            + " in " + where + ": " + current.basePrice() + " vs " + pricePerUnit);
                }
                offers.add(new ImportedCatalog.Offer(offerId, type, material,
                        quantity, page, slot, buy, sell, marketId));
            }
            shops.put(shopId, new ImportedCatalog.Shop(shopId, title,
                    fillerMaterial, fillerName, offers));
        }
        return new ImportedCatalog(shops, markets);
    }

    private static Map<?, ?> mapping(Object raw, String where) throws IOException {
        if (!(raw instanceof Map)) throw new IOException("Expected mapping at " + where);
        return (Map<?, ?>) raw;
    }

    private static int integer(Object raw, String where) throws IOException {
        try { return new BigDecimal(String.valueOf(raw)).intValueExact(); }
        catch (RuntimeException error) { throw new IOException("Invalid integer at " + where + ": " + raw, error); }
    }

    private static BigDecimal price(Object raw, String where) throws IOException {
        if (raw == null) throw new IOException("Missing price at " + where);
        try { return new BigDecimal(String.valueOf(raw)); }
        catch (RuntimeException error) { throw new IOException("Invalid price at " + where + ": " + raw, error); }
    }
}
