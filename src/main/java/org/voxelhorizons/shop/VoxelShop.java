package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.shop.catalog.ImportedCatalog;
import org.voxelhorizons.shop.catalog.MainMenuLayout;
import org.voxelhorizons.shop.catalog.MainMenuImporter;
import org.voxelhorizons.shop.catalog.ShopGuiCatalogueImporter;
import org.voxelhorizons.shop.market.MarketDefinition;
import org.voxelhorizons.shop.market.MarketEngine;
import org.voxelhorizons.shop.market.MarketPolicy;
import org.voxelhorizons.shop.market.PriceModifiers;

import java.nio.file.Files;
import org.voxelhorizons.shop.state.MarketSnapshotStore;
import org.voxelhorizons.shop.state.TradeJournal;
import java.nio.file.Path;
import java.util.Locale;

/** Paper entrypoint for the independent VoxelShop implementation. */
public final class VoxelShop extends JavaPlugin {
    private ImportedCatalog catalog;
    private MainMenuLayout mainMenu;
    private MarketEngine market;
    private VaultUnlockedBridge economy;
    private ShopMenus menus;
    private MarketSnapshotStore snapshots;
    private TradeJournal journal;
    private TradeCoordinator trades;
    private PriceModifiers modifiers;
    private final ShopItemFactory itemFactory = new ShopItemFactory();
    private final ShopText shopText = new ShopText();

    @Override public void onEnable() {
        saveDefaultConfig();
        economy = new VaultUnlockedBridge(getName());
        economy.setLegacyCurrency(getConfig().getString("economy.legacy-currency", "coins"));
        try {
            snapshots = new MarketSnapshotStore(getDataFolder().toPath().resolve("market-state.properties"));
            journal = new TradeJournal(getDataFolder().toPath().resolve("journal"));
            modifiers = new PriceModifiers(getDataFolder().toPath().resolve("modifiers.properties"));
            loadCatalog();
            trades = new TradeCoordinator(this, journal, snapshots);
        } catch (Exception exception) {
            getLogger().severe("Could not load shop catalogue: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        menus = new ShopMenus(this);
        getServer().getPluginManager().registerEvents(menus, this);
        PluginCommand command = getCommand("vshop");
        if (command == null) {
            getLogger().severe("Missing vshop command in plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ShopCommand handler = new ShopCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
        PluginCommand sellCommand = getCommand("sell");
        if (sellCommand != null) sellCommand.setExecutor(new SellCommand(this));

        if (!economy.available())
            getLogger().warning("No Vault or Vault2 economy provider found: browsing works but trading is unavailable.");
        try {
            long unresolved = journal.unresolved().size();
            if (unresolved > 0) getLogger().severe("TRADING LOCKED: " + unresolved
                    + " unresolved transaction(s). Review /vshop pending before reconciliation.");
        } catch (Exception error) {
            getLogger().severe("Cannot inspect transaction journal: " + error.getMessage());
        }
        getLogger().info("VoxelShop loaded " + catalog.shops().size() + " shops and "
                + catalog.markets().size() + " markets (trading "
                + (trades.ready() ? "enabled" : "disabled") + ")");
    }

    public void loadCatalog() throws Exception {
        // Import user-authored ShopGUIPlus YAML. Does not copy the original plugin code.
        Path source = getDataFolder().toPath().resolve("shops");
        if (!Files.isDirectory(source))
            source = java.nio.file.Paths.get("plugins", "ShopGUIPlus", "shops");
        if (!Files.isDirectory(source))
            throw new IllegalStateException("Missing shop YAML. Copy ShopGUIPlus/shops/*.yml into plugins/VoxelShop/shops/");

        economy.setLegacyCurrency(getConfig().getString("economy.legacy-currency", "coins"));
        String currency = getConfig().getString("economy.default-currency", "coins")
                .toLowerCase(Locale.ROOT);
        int target = getConfig().getInt("market.target-stock", 1000);
        int capacity = getConfig().getInt("market.stock-capacity", 100000);
        ImportedCatalog imported = ShopGuiCatalogueImporter.read(source, currency, target, capacity);
        // Validate dynamic pricing explicitly; never silently fall back from
        // an unsafe/malformed owner-supplied configuration.
        MarketPolicy policy = new MarketPolicy(
                priceDecimal("market.sensitivity", "0.35"),
                priceDecimal("market.min-multiplier", "0.50"),
                priceDecimal("market.max-multiplier", "2.00"),
                priceDecimal("market.default-sell-ratio", "0.15"),
                getConfig().getInt("market.max-order", 64),
                getConfig().getInt("market.precision", 2));
        MarketEngine created = new MarketEngine(policy);
        for (MarketDefinition definition : imported.markets().values()) created.register(definition);
        snapshots.restore(created, imported.markets());
        // If an earlier state exists, it has been validated before publishing.
        // Initial snapshot is also durable so runtime restarts cannot reroll stock.
        snapshots.save(created, imported.markets());
        Path menuSource = getDataFolder().toPath().resolve("menus.yml");
        if (!Files.isRegularFile(menuSource))
            menuSource = java.nio.file.Paths.get("plugins", "ShopGUIPlus", "config.yml");
        MainMenuLayout parsedMenu = Files.isRegularFile(menuSource)
                ? MainMenuImporter.read(menuSource) : null;
        this.mainMenu = parsedMenu;
        this.catalog = imported;
        this.market = created;
    }

    private java.math.BigDecimal priceDecimal(String key, String fallback) {
        return new java.math.BigDecimal(String.valueOf(getConfig().get(key, fallback)));
    }

    public ImportedCatalog catalog() { return catalog; }
    public MainMenuLayout mainMenu() { return mainMenu; }
    public MarketEngine market() { return market; }
    public VaultUnlockedBridge economy() { return economy; }
    public ShopMenus menus() { return menus; }
    public TradeCoordinator trades() { return trades; }
    public PriceModifiers modifiers() { return modifiers; }
    public ShopItemFactory items() { return itemFactory; }
    public ShopText text() { return shopText; }
}
