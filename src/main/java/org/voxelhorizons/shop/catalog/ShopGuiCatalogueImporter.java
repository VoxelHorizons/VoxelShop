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
        if (currency == null || !currency.matches("[a-z0-9_.:-]+"))
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
        Set<String> authoritativeBuyMarkets = new HashSet<>();
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
            String shopCurrency = String.valueOf(definition.containsKey("currency")
                    ? definition.get("currency") : currency).toLowerCase(Locale.ROOT);
            if (!shopCurrency.matches("[a-z0-9_.:-]+"))
                throw new IOException("Invalid currency ID in shop " + shopId);
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
                            String.valueOf(item.get("special")), 0, page, slot, null, null, null, null));
                    continue;
                }
                if (!"item".equals(type))
                    throw new IOException("Unsupported offer type " + type + " at " + where);
                Map<?, ?> stack = mapping(item.get("item"), where + ".item");
                String customId = stack.containsKey("voxelcore") ? String.valueOf(stack.get("voxelcore"))
                        : item.containsKey("voxelcore") ? String.valueOf(item.get("voxelcore")) : null;
                String material;
                if (customId != null) {
                    if (!customId.matches("[a-z0-9._-]+:[a-z0-9._-]+"))
                        throw new IOException("Invalid VoxelCore content ID at " + where);
                    material = "voxelcore:" + customId;
                } else {
                    material = String.valueOf(stack.get("material")).toUpperCase(Locale.ROOT);
                    if (!material.matches("[A-Z0-9_]+"))
                        throw new IOException("Invalid material at " + where);
                }
                int quantity = integer(stack.containsKey("quantity") ? stack.get("quantity") : 1, where + ".quantity");
                if (quantity < 1 || quantity > 64) throw new IOException("Invalid item quantity at " + where);
                // Missing or negative prices disable that trade direction; never trade for free.
                BigDecimal buy = item.get("buyPrice") == null ? null : price(item.get("buyPrice"), where + ".buyPrice");
                BigDecimal sell = item.get("sellPrice") == null ? null : price(item.get("sellPrice"), where + ".sellPrice");
                boolean canBuy = buy != null && buy.signum() > 0;
                boolean canSell = sell != null && sell.signum() > 0;
                if (!canBuy && !canSell || buy != null && buy.signum() == 0 || sell != null && sell.signum() == 0)
                    throw new IOException("No valid buy/sell direction or zero price at " + where);
                String permission = item.containsKey("permission") ? String.valueOf(item.get("permission")) : null;
                if (permission != null && !permission.matches("[a-zA-Z0-9_.:-]+"))
                    throw new IOException("Invalid permission at " + where);
                String displayName = stack.containsKey("name") ? String.valueOf(stack.get("name"))
                        : item.containsKey("displayName") ? String.valueOf(item.get("displayName")) : null;
                List<String> displayLore = new ArrayList<>();
                Object rawLore = stack.containsKey("lore") ? stack.get("lore") : item.get("lore");
                if (rawLore != null) {
                    if (!(rawLore instanceof List<?> lines) || lines.size() > 32)
                        throw new IOException("Invalid item lore at " + where);
                    for (Object line : lines) {
                        if (line == null || String.valueOf(line).length() > 512)
                            throw new IOException("Invalid lore line at " + where);
                        displayLore.add(String.valueOf(line));
                    }
                }
                String itemCurrency = String.valueOf(item.containsKey("currency")
                        ? item.get("currency") : shopCurrency).toLowerCase(Locale.ROOT);
                if (!itemCurrency.matches("[a-z0-9_.:-]+"))
                    throw new IOException("Invalid offer currency at " + where);
                String materialId = customId == null ? "minecraft:" + material.toLowerCase(Locale.ROOT)
                        : "voxelitem:" + customId;
                // Keep the historical ID for default currency while separating
                // independent supply/price markets for alternative currencies.
                String marketId = itemCurrency.equals(currency) ? materialId : itemCurrency + ":" + materialId;
                // Sell-only offers use a derived reference valuation at the default
                // 15% market spread, but cannot be purchased by players.
                BigDecimal referenceBuy = canBuy ? buy
                        : sell.divide(new BigDecimal("0.15"), 8, RoundingMode.HALF_UP);
                BigDecimal pricePerUnit = referenceBuy.divide(BigDecimal.valueOf(quantity), 8,
                        RoundingMode.HALF_UP).stripTrailingZeros();
                if (pricePerUnit.signum() <= 0)
                    throw new IOException("Calculated market price is zero at " + where);
                // Preserve each legacy offer's initial sell-to-buy ratio.
                // Ratios are bounded below 1.0 to prevent baseline round-trip inflation.
                BigDecimal sellRatio = !canSell ? null
                        : canBuy ? sell.divide(buy, 12, RoundingMode.HALF_UP) : new BigDecimal("0.15");
                if (sellRatio != null && (sellRatio.signum() <= 0
                        || sellRatio.compareTo(BigDecimal.ONE) >= 0))
                    throw new IOException("Unsafe sell ratio at " + where);
                MarketDefinition current = markets.get(marketId);
                if (current == null || canBuy && !authoritativeBuyMarkets.contains(marketId)) {
                    markets.put(marketId, new MarketDefinition(marketId, itemCurrency, pricePerUnit,
                            targetStock, targetStock, capacity, sellRatio));
                    if (canBuy) authoritativeBuyMarkets.add(marketId);
                } else if (canBuy && current.basePrice().compareTo(pricePerUnit) != 0) {
                    // Only conflicting authoritative BUY valuations are unsafe.
                    // A sell-only offer's derived baseline must not overwrite a
                    // separate buy-enabled market for the same item/currency.
                    throw new IOException("Conflicting buy baseline for global market " + marketId
                            + " in " + where + ": " + current.basePrice() + " vs " + pricePerUnit);
                }
                offers.add(new ImportedCatalog.Offer(offerId, type, material,
                        quantity, page, slot, buy, sell, marketId, sellRatio,
                        canBuy, canSell, permission, displayName, displayLore));
            }
            shops.put(shopId, new ImportedCatalog.Shop(shopId, title, shopCurrency,
                    fillerMaterial, fillerName, offers));
        }
        // Normalize sell-only margins only after every shop is imported.
        // This preserves original sell payouts when an authoritative BUY
        // baseline for that material was encountered in another shop/file.
        Map<String, ImportedCatalog.Shop> normalized = new LinkedHashMap<>();
        for (ImportedCatalog.Shop shop : shops.values()) {
            List<ImportedCatalog.Offer> offers = new ArrayList<>();
            for (ImportedCatalog.Offer offer : shop.offers()) {
                if (!"item".equals(offer.type()) || offer.canBuy() || !offer.canSell()) {
                    offers.add(offer);
                    continue;
                }
                MarketDefinition reference = markets.get(offer.marketId());
                BigDecimal referenceTotal = reference.basePrice()
                        .multiply(BigDecimal.valueOf(offer.quantity()));
                BigDecimal ratio = offer.baselineSell().divide(referenceTotal, 12, RoundingMode.HALF_UP);
                if (ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) >= 0)
                    throw new IOException("Sell-only payout would exceed or equal reference BUY valuation: "
                            + shop.id() + "." + offer.id());
                offers.add(new ImportedCatalog.Offer(offer.id(), offer.type(),
                        offer.materialOrSpecial(), offer.quantity(), offer.page(), offer.slot(),
                        offer.baselineBuy(), offer.baselineSell(), offer.marketId(), ratio,
                        false, true, offer.permission(), offer.displayName(), offer.displayLore()));
            }
            normalized.put(shop.id(), new ImportedCatalog.Shop(shop.id(), shop.title(), shop.currency(),
                    shop.fillerMaterial(), shop.fillerName(), offers));
        }
        return new ImportedCatalog(normalized, markets);
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
