# ShopGUIPlus 1.113.0 functional compatibility inventory

This checklist uses the user's legally supplied JAR **only to identify public-facing
features and extension points**. No vendor source, bytecode, assets, configuration,
database, or decompilation output is copied or redistributed here. VoxelShop is an
independent implementation with its own API and design.

**Legend:** implemented in current branch / partial / not implemented.

| Feature / functional surface | Current VoxelShop state | Validation needed |
| --- | --- | --- |
| Shop categories, menu browsing, pagination, /shop | Implemented (Paper 26.2) | Test Unicode titles/placeholder rendering and individual layouts |
| Shop offer buy/sell, shift-click amount GUI | Implemented for supported stack identities | Live purchase/sale, stale quote, inventory full, wallet denial |
| /sell hand, handall, all | Partial: exact ordinary vanilla items, capped batches | Multi-stack behavior and command parity |
| Dynamic marginal stock-based pricing | Implemented bounded model | Economy and crafting arbitrage simulation |
| Persistent market stocks | Implemented with strict definition validation | Kill/restart, filesystem recovery and invalid snapshot |
| Legacy Vault and named Vault2 currencies | Implemented with explicit Coins fallback | EssentialsX Coins and VoxelCore tokens side by side |
| Per-offer original sell margins | Implemented | Same material across multiple category offers |
| Item providers / custom items | Partial: exact VoxelCore templates | Modifiable tints, non-VoxelCore items, provider reloads |
| Advanced click actions and per-shop GUI layout | Partial | Configurable right/left/shift mappings, special buttons |
| Shop links, special GUI elements | Partial (balance display) | Full special-element import |
| Bulk quantity selection | Partial (preset quantities) | Bulk amount GUI and custom amount controls |
| Shop permissions | Partial | Per-offer permission, dynamic ranks, bypass rules |
| Price modifiers (permission and command) | Not implemented | Multipliers, precedence, add/reset/query commands |
| /shop check, worth and price modifiers | Not implemented | Command argument/permission parity |
| Pre/post transaction events | Not implemented | Cancellation and safe synchronous dispatch |
| ShopGUIPlus external plugin API | Not implemented | Design independent API; bridge only as needed |
| Persistent player-specific settings and database migrations | Not implemented | Backups, migration scripts and rollback |
| Additional economy providers and XP systems | Partial (Vault and Vault2 only) | Provider adapters where required |
| Custom metadata, enchantments, potion and spawner items | Partial via exact ItemStack template only | Controlled item identity and anti-spoofing tests |
| Localization, sounds, per-action messages | Not implemented | Config/menu translation and reload |
| Full crash-atomic cross-plugin transactions | **Impossible to guarantee** with generic Vault APIs | Manual reconciliation; optional idempotent provider integration |

## Required production acceptance

1. Build with Java 25 for Paper 26.2. Never enable production trades just
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
