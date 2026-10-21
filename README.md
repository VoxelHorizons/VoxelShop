# VoxelShop

Independent Paper 26.2 and 26.3 shop, YAML importer and dynamic stock-pricing project for
VoxelHorizons. **Not a fork or redistributable copy of the licensed ShopGUIPlus
JAR.** Current development: `0.3.0-SNAPSHOT`.

## What works on this branch

- Browser for existing user-authored ShopGUIPlus-style `shops/*.yml`, with
  categories, page navigation, balance previews and current marginal pricing.
- Buy and sell ordinary vanilla and exact VoxelCore item templates with left/right-click, shift-click amount selection, safe
  server-owned GUI inventory mappings and no client-authoritative prices.
- `/shop` and `/vshop <category> [page]`; `/sell hand [quantity]`,
  `/sell handall`, `/sell all` for **unmodified vanilla items**.
- Dynamic shared market supply/price per currency, persistent stock/revisions,
  shop-specific sell margins, and deterministic BigDecimal amounts.
- Modern VaultUnlocked vault2 named currencies, and tightly restricted legacy
  Vault routing for EssentialsX Coins even when a separate vault2 provider is registered.
- Write-ahead transaction records. Trades blocked while any ambiguous
  transaction awaits manual reconciliation; never replay questionable debits.

## New in 0.3.0 — parity and validation phase

- **Author-written main-menu YAML:** If `plugins/VoxelShop/menus.yml` exists,
  use its ShopGUI-style `shopMenuItems`, `shopMenuSize`, `shopMenuFillItem`,
  title and balance element. Otherwise, read the existing user-authored
  `plugins/ShopGUIPlus/config.yml`; if neither exists, use a generated
  category grid. No licensed JAR assets are copied into VoxelShop.
- **Author-written item YAML:** individual offer permissions, custom
  names/lore, and independent buy-only/sell-only options. Missing or negative
  prices disable that direction; zero prices are rejected.
- **Clicks:** `gui.click-actions` supports `BUY`, `SELL`, `SELL_ALL`,
  `BUY_GUI`, `SELL_GUI`, and `NONE`. The quantity GUI supports +/-1,
  +/-10, set 1/max, a live price on the selected item, and separate confirmation.
  Bulk selling is bounded by `trading.max-sell-all-orders` and market capacity.
- **Currency modifiers:** durable admin-controlled player/market multipliers,
  wildcard `*` markets, and highest-priority permission-based ranks
  (`modifiers.permission-tiers`). Multipliers affect GUI and wallet identically
  and cross-shop ratio checks prevent buy/sell arbitrage, including at zero stock.
- **Veto and audit hooks:** synchronous `VoxelShopTradePreEvent` (cancellable,
  before any mutation) and `VoxelShopTradeCompleteEvent` (after commit).
  These are VoxelShop APIs, **not binary replacements** for ShopGUIPlus events.
- **VoxelCore resource-pack UI:** optionally resolve `:offset_-8:`, custom
  named fonts and PlaceholderAPI text in shop titles, item names and lore.
  Shop title placeholders `%page%` and `%pages%` are supported.
- **Static worth migration:** read legacy `worth.yml` (read-only) and expose
  `/vshop worth [hand|material] [quantity]`. Static worth is deliberately
  separate from real-time sale quotes.
- **Reload safety:** a failed catalogue reload locks all new trading until a
  valid reload succeeds. GUI inventory click exploit protections have been
  tightened; cancelled events, unrelated clicks and rapid duplicate trades
  cannot mutate wallets.

### Commands

```text
/shop [category] [page]
/vshop check [category]                         (admin)
/vshop worth [hand|material] [quantity]
/vshop modifier set <online-player|UUID> <market|*> <buy|sell> <0.25..4.00>
/vshop modifier reset <online-player|UUID> <market|*> <buy|sell>
/vshop modifier check <online-player|UUID> <market|*>
/vshop pending                                  (admin)
/vshop reconcile <transaction-UUID> --confirmed (manual recovery only)
/vshop reload                                   (admin)
/sell hand [quantity] | handall | all
```

For the exact server-side test sequence see
[Staging acceptance](docs/STAGING-ACCEPTANCE.md).

**Still blocked from production certification:** provider-specific crash
reconciliation, dynamic item variants and unsupported item types, advanced
quantity UI, complete external ShopGUIPlus API/event compatibility,
crafting-arbitrage simulations, and in-server testing with the real player
economy and authored configs. `trading.enabled` stays **false** by default.

## Install for staging

1. Use **Paper 26.3 / Java 25** (CI also builds Paper 26.2) and a working compatible Vault economy provider.
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
included, but **live fault-injection** on Paper 26.3 remains required.
