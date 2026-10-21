# ShopGUIPlus 1.113.0 functional compatibility inventory

This checklist uses the user's legally supplied JAR **only to identify public-facing
features and extension points**. No vendor source, bytecode, assets, configuration,
database, or decompilation output is copied or redistributed here. VoxelShop is an
independent implementation with its own API and design.

**Legend:** implemented in current branch / partial / not implemented.

| Feature / functional surface | Current VoxelShop state | Validation needed |
| --- | --- | --- |
| Shop categories, menu browsing, pagination, /shop | Implemented; legacy main-menu icon/layout import and VoxelCore glyph resolution | Test private layouts, Unicode titles and placeholder rendering on Paper |
| Shop offer buy/sell, shift-click amount GUI | Implemented for supported stack identities | Live purchase/sale, stale quote, inventory full, wallet denial |
| /sell hand, handall, all | Partial: exact ordinary vanilla items, capped batches | Multi-stack behavior and command parity |
| Dynamic marginal stock-based pricing | Implemented bounded model | Economy and crafting arbitrage simulation |
| Persistent market stocks | Implemented with strict definition validation | Kill/restart, filesystem recovery and invalid snapshot |
| Legacy Vault and named Vault2 currencies | Implemented with explicit Coins fallback | EssentialsX Coins and VoxelCore tokens side by side |
| Per-offer original sell margins | Implemented | Same material across multiple category offers |
| Item providers / custom items | Partial: exact VoxelCore templates | Modifiable tints, non-VoxelCore items, provider reloads |
| Advanced click actions and per-shop GUI layout | Partial: configurable buy/sell/sell-all/amount GUI, imported category icons | Complex custom actions, buttons and bulk UI still missing |
| Shop links, special GUI elements | Partial (balance display) | Full special-element import |
| Bulk quantity selection | Implemented +/-1/10, set 1/max and on-item live price | Advanced bulk menus, quick-repeat settings and custom controls |
| Shop permissions | Partial | Per-offer permission, dynamic ranks, bypass rules |
| Price modifiers (permission and command) | Partial: persisted per-player exact/wildcard plus priority-ranked LuckPerms permission tiers | Expiry, player-specific legacy migration and full vendor command syntax remain |
| /shop check, worth and price modifiers | Partial: /vshop check, worth and modifier management | Alias/legacy argument parity and advanced worth valuations |
| Pre/post transaction events | Implemented independent VoxelShop cancellable prepare/completed events | Third-party integration testing and optional legacy adapters |
| ShopGUIPlus external plugin API | Not implemented | Design independent API; bridge only as needed |
| Persistent player-specific settings and database migrations | Not implemented | Backups, migration scripts and rollback |
| Additional economy providers and XP systems | Partial (Vault and Vault2 only) | Provider adapters where required |
| Custom metadata, enchantments, potion and spawner items | Partial via exact ItemStack template only | Controlled item identity and anti-spoofing tests |
| Localization, sounds, per-action messages | Not implemented | Config/menu translation and reload |
| Full crash-atomic cross-plugin transactions | **Impossible to guarantee** with generic Vault APIs | Manual reconciliation; optional idempotent provider integration |

## Required production acceptance

1. Build with Java 25 for Paper 26.3 (CI also verifies Paper 26.2). Never enable production trades just
   because CI compiles; `trading.enabled` defaults to `false`.
2. Test every configured shop and command on a staging copy of the exact
   server plugins, economy wallets and resource pack.
3. Inject process crashes before/after debit, inventory mutation, market
   persistence and journal updates. A pending journal entry must **lock trade**
   and need explicit reconciliation; no auto-refund/auto-repeat.
4. Confirm that VoxelCore item identifiers cannot be spoofed by similar vanilla
   materials, and that namespaced currency routes cannot move tokens as Coins.
5. Validate conservation, per-currency market isolation and craft/smelt
   arbitrage over large repeat trade simulations.
6. Implement remaining compatibility surfaces above before claiming full
   ShopGUIPlus functional replacement.

This document describes *functional differences*, not a promise of binary/API
compatibility with the vendor's plugin.

## Live staging acceptance

See [STAGING-ACCEPTANCE.md](STAGING-ACCEPTANCE.md) for validation of
actual Vault/Vault2 providers, VoxelCore items, dynamic prices, full click
surface, controlled process crashes, disk journal integrity, and concurrency.
Java unit tests or JAR compilation do not demonstrate production recovery.
