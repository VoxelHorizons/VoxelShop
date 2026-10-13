# VoxelShop

Independent Paper 26.2 shop, YAML importer and dynamic stock-pricing project for
VoxelHorizons. **Not a fork or redistributable copy of the licensed ShopGUIPlus
JAR.** Current development: `0.2.0-SNAPSHOT`.

## What works on this branch

- Browser for existing user-authored ShopGUIPlus-style `shops/*.yml`, with
  categories, page navigation, balance previews and current marginal pricing.
- Buy and sell with left/right-click, shift-click amount selection, safe
  server-owned GUI inventory mappings and no client-authoritative prices.
- `/shop` and `/vshop <category> [page]`; `/sell hand [quantity]`,
  `/sell handall`, `/sell all` for **unmodified vanilla items**.
- Dynamic shared market supply/price per currency, persistent stock/revisions,
  shop-specific sell margins, and deterministic BigDecimal amounts.
- Modern VaultUnlocked vault2 named currencies, and tightly restricted legacy
  Vault routing for EssentialsX Coins even when a separate vault2 provider is registered.
- Write-ahead transaction records. Trades blocked while any ambiguous
  transaction awaits manual reconciliation; never replay questionable debits.

## Install for staging

1. Use **Paper 26.2 / Java 25** and a working compatible Vault economy provider.
2. **Do not run ShopGUIPlus and VoxelShop simultaneously with conflicting /shop
   commands.** Back up your server and shop configs.
3. Put the shaded JAR from GitHub Actions in `plugins/`.
4. Copy your own `plugins/ShopGUIPlus/shops/*.yml` files into
   `plugins/VoxelShop/shops/`. VoxelShop can also read them from the
   ShopGUIPlus folder without modifying them.
5. Set `economy.default-currency` and optionally
   `economy.legacy-currency: coins`. The latter can ONLY route to legacy Vault.
6. Leave **`trading.enabled: false`** until staging validation is complete.
   Start the server, browse `/vshop` and verify price/currency displays.
7. To test transactions on a **backed-up non-production server**, change
   `trading.enabled` to `true` and `/vshop reload`.

### Crash recovery

Use `/vshop pending` (permission: `voxelshop.admin`) to inspect unresolved
transactions. An administrator must compare player inventory, currency wallet,
transaction audit and market stock, then manually reconcile before calling
`/vshop reconcile <UUID> --confirmed`. This acknowledgement archives a record;
**it does not refund or give items.** Details: [Transaction safety](docs/TRANSACTION-SAFETY.md).

### Current limitations

This is **not yet a production-certified full ShopGUIPlus replacement**. It
does not currently cover VoxelCore custom item trading, all external item
providers, custom GUI click-action configurations, every special offer type,
per-player price modifiers, the full ShopGUIPlus API/event compatibility
surface, automated provider-specific crash reconciliation, or complete
crafting-arbitrage protections. Folia is unsupported. Test on your exact
economy backend before letting players trade.

The market seeds from imported YAML are independent of original ShopGUIPlus
code. The licensed JAR, server data and user-private YAML must stay out of
the public repository.

## Architecture and testing

- `MarketEngine`: pure Java, synchronized marginal-pricing calculator
- `ShopGuiCatalogueImporter`: sanitized YAML reader, immutable shops
- `MarketSnapshotStore`: persisted stock, revisions and definition checks
- `TradeJournal`: write-ahead ledger, fail-closed recovery state
- `TradeCoordinator`: main-thread wallet + item + market coordinator
- `ShopMenus`, `SellCommand`: Paper GUI and player interaction
- `.github/workflows/build.yml`: JUnit + Maven shade build and downloadable JAR

Run `mvn clean verify` with Java 25. Market/restart/journal unit tests are
included, but **live fault-injection** on Paper 26.2 remains required.
