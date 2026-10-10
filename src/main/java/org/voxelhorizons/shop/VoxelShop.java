package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.voxelhorizons.shop.catalog.ImportedCatalog;
import org.voxelhorizons.shop.catalog.ShopGuiCatalogueImporter;
import org.voxelhorizons.shop.market.MarketDefinition;
import org.voxelhorizons.shop.market.MarketEngine;
import org.voxelhorizons.shop.market.MarketPolicy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Paper entrypoint for the independent VoxelShop implementation. */
public final class VoxelShop extends JavaPlugin {
    private ImportedCatalog catalog;
    private MarketEngine market;
    private VaultUnlockedBridge economy;
    private ShopMenus menus;

    @Override public void onEnable() {
        saveDefaultConfig();
        economy = new VaultUnlockedBridge(getName());
        try {
            loadCatalog();
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
        command.setExecutor(new ShopCommand(this));
        command.setTabCompleter(new ShopCommand(this));

        if (!economy.provider().isPresent())
            getLogger().warning("No VaultUnlocked vault2 Economy provider found: shop browsing works, trading is disabled.");
        getLogger().info("VoxelShop loaded " + catalog.shops().size() + " shops and "
                + catalog.markets().size() + " markets (preview-only mode)");
    }

    public void loadCatalog() throws Exception {
        // Import user-authored ShopGUIPlus YAML. Does not copy the original plugin code.
        Path source = getDataFolder().toPath().resolve("shops");
        if (!Files.isDirectory(source))
            source = java.nio.file.Paths.get("plugins", "ShopGUIPlus", "shops");
        if (!Files.isDirectory(source))
            throw new IllegalStateException("Missing shop YAML. Copy ShopGUIPlus/shops/*.yml into plugins/VoxelShop/shops/");

        String currency = getConfig().getString("economy.default-currency", "coins")
                .toLowerCase(Locale.ROOT);
        int target = getConfig().getInt("market.target-stock", 1000);
        int capacity = getConfig().getInt("market.stock-capacity", 100000);
        ImportedCatalog imported = ShopGuiCatalogueImporter.read(source, currency, target, capacity);
        MarketPolicy policy = MarketPolicy.defaults();
        MarketEngine created = new MarketEngine(policy);
        for (MarketDefinition definition : imported.markets().values()) created.register(definition);
        this.catalog = imported;
        this.market = created;
    }

    public ImportedCatalog catalog() { return catalog; }
    public MarketEngine market() { return market; }
    public VaultUnlockedBridge economy() { return economy; }
    public ShopMenus menus() { return menus; }
}
