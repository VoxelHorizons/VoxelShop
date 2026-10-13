# VoxelShop payment safety and operator recovery

VoxelShop is a **new implementation**, not a copy of ShopGUIPlus. It does not
bundle the licensed JAR, textures, or other proprietary assets.

## Defaults and dependencies

- Transactions are **off** unless `trading.enabled: true` is set.
- Paper 26.2 and Java 25 are targeted by this build. Folia is unsupported.
- Prefer VaultUnlocked `vault2` for named currencies. For EssentialsX Coins
  without a registered vault2 economy, the legacy Vault service is used only
  for `economy.legacy-currency` (default `coins`).
- For tokens or cosmetic currencies, install a registered vault2 provider.
  Do not assume VaultUnlocked stores player balances.
- Never run ShopGUIPlus and VoxelShop with conflicting `/shop` aliases at
  the same time. Back up ShopGUIPlus YAML and the server economy beforehand.

## What the coordinator guarantees

Prices are always computed by the server from current stock and shop offer,
never accepted from inventory item lore or client clicks. All operations are
serialized on the Paper server thread. GUI clicks, drags and shift transfers
are cancelled. Sell operations accept exact, ordinary vanilla item stacks
without custom item metadata. Creative and spectator trades are rejected.

Every trade creates a durable file in `journal/pending` before **any**
inventory or wallet change. A transaction advances through explicit phases
recorded with atomic file replacement. When a wallet operation succeeds and a
market movement is applied, `market-state.properties` is saved atomically.

## IMPORTANT: external wallet operations cannot be atomic with player inventory

Vault/VaultUnlocked APIs do **not** provide cross-provider atomic commits or
idempotency keys. A server crash or plugin exception between a provider call
and its recorded result can leave an ambiguous outcome. This implementation
does not silently retry, refund, or redeliver, because that would risk dupes.

If ANY pending entry remains, **all new purchases/sales halt** (browsing still
works). See `/vshop pending` and the server log. Inspect a player's wallet,
items and corresponding stock snapshot (and external plugin's transaction
history if available). Determine whether the transaction needs manual
compensation or no action, then run:

`/vshop reconcile <transaction UUID> --confirmed`

This command only archives the journal entry. It never changes money,
items or stock. **Do not acknowledge a pending entry before actually
reconciling it.** Preserve the `journal/completed` archive for audits.

## Remaining production validation

This version is **NOT certified for unattended production deployment**.
Before enabling trades for real players, test against the exact economy
backend, including rejected deposits, insufficient funds, disconnects,
inventory-full cases, restart and forced termination between every journal
phase, plugin reloads, high-volume trade queues, and item metadata spoofing.
Review craft/smelt arbitrage and dynamic price parameters. There is not yet
a full ShopGUIPlus API compatibility layer, VoxelCore custom-item trading,
per-player price modifiers, player-facing `/sell` commands or an automated
provider-specific recovery processor.
