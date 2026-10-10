# VoxelShop — independently implemented shop and market platform

## Phase 2: Paper 26.2 VoxelShop preview and VaultUnlocked (stacked PR)

The repository now contains a first *deployable, read-only* Paper 26.2 plugin:
`org.voxelhorizons.shop.VoxelShop`, its `plugin.yml`, `/vshop` (`/shop` alias),
GUI category and shop-page browsing, and protected inventory click/drag handling.
It reads ordinary `shops/*.yml` configurations from `plugins/VoxelShop/shops`
or (if absent) `plugins/ShopGUIPlus/shops`. It does not write to those files.

### Dependencies

* Paper 26.2 / Java 25 for this build
* VaultUnlocked (plugin name `Vault`) **and** a currency provider implementing
  the modern `net.milkbowl.vault2.economy.Economy` service. VaultUnlocked is
  an abstraction/API, **not the player-balance backend**.
* `VaultUnlockedAPI:2.20` is `provided` (not bundled in the VoxelShop JAR).
  The implementation is compiled against the actual modern API, not the
  legacy single-currency `net.milkbowl.vault.economy.Economy`.

Configure `economy.default-currency` as the currency ID actually registered by
your economy provider. Do not assume a `voxel:` namespace unless that provider
defines it. Multi-currency requires `hasMultiCurrencySupport()` and
`hasCurrency(id)`; the bridge rejects a non-default currency on providers
that only implement the legacy/default wallet.

### Trial deployment

1. Back up your server. Stop it before updating plugins.
2. Install Paper 26.2, VaultUnlocked and an appropriate vault2 economy provider.
3. Install the **VoxelShop 26.2 plugin JAR** from successful CI artifacts.
4. Copy the *user-authored* shop YAML files into `plugins/VoxelShop/shops`
   and set the currency ID in `plugins/VoxelShop/config.yml`.
5. Restart, then use `/vshop`, `/vshop lumberjack`, and `/vshop lumberjack 2`.
   `/vshop reload` requires `voxelshop.admin`.

### Safety boundary

This is **preview-only by design**: no item or wallet changes, no stock writes.
Live trades must not be enabled by changing a configuration flag: they require
the still-unimplemented durable coordinator. GUI items are informational and
clicks are cancelled in both shop and player inventories, including drags.
This protects from unintended item transfer/duping during migration testing.

The first GUI is functional for categories, paging and live price *display*,
but is **not feature-complete parity** with ShopGUIPlus. Missing: actual
buy/sell/sell-all; amount-selector GUI; dynamic stock persistence; admin and
price-modifier commands; item-provider integrations; configurable click mappings;
pre/post transaction hooks; and server restart/recovery testing.

The user's licensed ShopGUIPlus JAR was analysed for command, event, GUI and
provider interfaces, but no decompiled proprietary source is published here.
The source in this repository is an independent reimplementation. Original
plugin licensing and redistribution permissions must be observed.


VoxelShop is a new implementation, not a recompiled ShopGUIPlus JAR. Proprietary binary code, vendor assets, credentials and user data must never be committed here.

## Status: Phase 1 foundation (NOT a deployable shop plugin)

This PR contains the Java market calculator, quote/revision API, currency-provider interface, and unit tests. It **does not** register Bukkit commands, create GUI shops, persist stock, pay or credit players, or transfer items. Its JAR is a *library/test artifact*, not a server plugin. Don't put it in the plugins folder.

The supplied ShopGUIPlus 1.113.0 JAR and private configuration ZIP were examined locally for interface and behaviour inventory only. They are not part of this repository.

## Phase 1b: ShopGUIPlus configuration import (stacked PR)

The independent `ShopGuiCatalogueImporter.read(directory, currencyId, targetStock, capacity)` reads ordinary ShopGUIPlus-style shop YAML files into immutable catalogue layouts plus deduplicated global `MarketDefinition` seeds. It preserves custom menu-title Unicode/glyph placeholders, slot, page, fill-item styling, per-offer original buy/sell values and special `balance` display items.

The importer **does not** transfer player data, pay money, change inventory, or write migrated files yet. It validates duplicate YAML keys, overlapping slots, unknown offer types and conflicting baseline prices for the same material. It deliberately refuses free, disabled or malformed prices rather than silently changing gameplay. `worth.yml` is excluded; separately importing `worth.yml` needs a distinct valuation policy.

In the supplied private configuration snapshot, this structure accounts for 8 shop categories, 249 regular offers, 5 special entries and 247 unique material market IDs. No proprietary JAR or private config is committed to this repository.

`MarketDefinition` seeds are derived from each offer's initial buy price divided by its quantity; actual **live** prices will later come from market stock and VoxelCore currency balances. Currency conversion is never inferred from Vault/EXP.

## Architecture

- **VoxelCore Economy** (separate upcoming VoxelCore work): owns authoritative player balances, named currencies (`voxel:coins`, `voxel:cosmetics`), audit ledger, transaction idempotency, optional external economy adapters, database durability and integration APIs.
- **VoxelShop**: manages shop catalogues, permissions, GUIs, inventory checks, pricing, quotes, and trade workflows. It must use VoxelCore economy transactions via a stable public API, not reach into implementation classes.
- **Market state**: per item and currency, with stock, stock target, capacity, revision and base value. Both shop buy and sell move supply and affect the next quote.
- **Safety**: amounts use BigDecimal, with separate rounding for purchases and payouts; stock and order sizes are bounded; quote revisions detect changes between GUI display and click.

No economy or item mutations happen in this phase: `MarketEngine.commitStock` only changes in-memory simulation state and MUST NOT be invoked directly for a real player trade.

## Default model and configuration design

Each marginal unit's reference value is:

```text
imbalance    = (targetStock - stockBeforeUnit) / targetStock
multiplier   = clamp(1 + sensitivity * imbalance, minMultiplier, maxMultiplier)
buyUnit      = baseValue * multiplier
sellUnit     = buyUnit * sellRatio, evaluated at stockAfterSellingTheUnit
buyTotal     = ROUND_CEILING(sum(buyUnits), currencyPrecision)
sellTotal    = ROUND_DOWN(sum(sellUnits), currencyPrecision)
```

Default safety profile: sensitivity 0.35, floor 0.50x, ceiling 2.00x, sell ratio 0.15, max quantity 64, currency precision 2. These are **conservative starting parameters, not economically proven or guaranteed to be exploit-free**. They need simulations based on actual server production, farming and player trade patterns. Also enforce item-graph crafting/smelting arbitrage constraints and cross-shop exchange restrictions before enabling live trading.

Example intended configuration for the future Bukkit plugin (NOT loaded by the Phase 1 library):

```yaml
pricing:
  sensitivity: 0.35
  min-multiplier: 0.50
  max-multiplier: 2.00
  sell-ratio: 0.15
  max-order: 64
  precision: 2

markets:
  minecraft:oak_log:
    currency: voxel:coins
    base-value: 9.06
    target-stock: 1000
    initial-stock: 1000
    capacity: 2000
  voxel:cosmetic_hat:
    currency: voxel:cosmetics
    base-value: 30
    target-stock: 200
    initial-stock: 200
    capacity: 500
```

## Production transaction invariants (Phase 2)

1. Validate the player, permissions, shop/item identity, stock, economic balance, inventory space, item metadata and amount. Ignore client-submitted prices entirely.
2. Recompute the quote at commit time with the current market revision and current price configuration. A GUI's displayed price is only informational.
3. Serialize conflicting trades; no re-entrant buy/sell operations, double-clicks, shift-click exploits or concurrent duplicate orders.
4. Use durable transaction IDs and a persisted operation journal; the wallet must guarantee idempotent debit/credit. External providers with no idempotency need an explicit recovery strategy.
5. Coordinate the wallet debit/credit, market stock, and inventory transfer with a recoverable state machine. Never credit money without permanently receiving the sold items. Never deliver items without permanently recording the debit.
6. Roll back or reconcile incomplete trades after restart; define fail-closed behaviour for downtime and unsafe inventory events.
7. Reject invalid, non-finite, negative, overly precise or overflowing monetary amounts. Reject NBT/component substitutions, creative inventory injection, mismatched item identities and unsupported custom items.
8. Persist stock snapshots and full audit history, and make external market/supply changes revisioned.
9. Run concurrency, restart, lag, disconnect, duplicate-click, high-volume and exploit regression tests before server deployment.

## Migration findings from supplied configuration

The provided source configuration archive has 8 shop categories containing 254 entries (249 standard item entries plus 5 special entries), and a `worth.yml` list with 1,367 reference values. Shop economyTypes currently uses VAULT. Existing `buyPrice`/`sellPrice` amounts should be imported as reference baselines, not used as live prices. The archive also includes an SQLite database: treat it as private user state, and migrate only after schema review and backup.

Dynamic values must be tied to each *market and currency*, not overwritten in the source shop configs. Define how shop-category offers share a global item market, and how sell-only items differ. Separate cosmetics currencies to prevent conversion exploits.

## Phases

- Phase 1: this PR — pure Java pricing and currency interface, regression tests, independent architecture.
- Phase 2: VoxelCore public economy service: multi-currency wallets, ledgers, idempotent transaction API, persistence and providers.
- Phase 3: VoxelShop YAML importer and Paper inventory UI with server-authoritative buy/sell and async-safe persistence.
- Phase 4: protections/optimizations, compatibility with existing shop layouts, custom VoxelCore items, economy simulation and benchmark suites.
