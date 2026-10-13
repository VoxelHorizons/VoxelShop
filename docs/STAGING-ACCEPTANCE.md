# VoxelShop 0.3.0 — Paper 26.2 staging acceptance

**DO NOT enable live player trading on production solely because Maven/JUnit
or the Paper plugin metadata checks pass.** This checklist must be executed
on a backed-up server containing the actual VoxelCore, Vault, VaultUnlocked,
economy provider, LuckPerms, PlaceholderAPI, and user-authored YAML.

## Staging setup

1. Back up world, user inventories, economy database, original ShopGUIPlus YAML
   and `plugins/VoxelShop/market-state.properties`, `journal` and `modifiers.properties`.
2. Stop the server. Avoid competing `/shop` registrations: remove ShopGUIPlus
   from the staging plugins folder after saving your original config.
3. Install the new VoxelShop 0.3.0 JAR from the successful PR build artifact.
4. Keep `trading.enabled: false` initially and launch Paper 26.2 / Java 25.
5. Check `/vshop check`, `/vshop pending`, `/vshop worth DIAMOND 2`,
   shop menu categories and page changes. Validate `:offset_-8:`, custom UI
   placeholders and item name/lore. Verify original shop categories have
   correct icons, slots and prices.
6. Test one-sided items and permissions with a non-operator account. Players
   lacking an offer-specific permission must not see or transact the offer.

## Currencies

- With Coins through legacy EssentialsX/Vault and a separate Vault2 token
  provider installed, verify Coins never debit Tokens and vice versa.
- With no Vault2 token provider, all token-denominated offers must fail closed.
- Verify insufficient balance and unsupported currency error behaviour.
- Configure permission tiers and per-player modifiers; verify GUI total,
  actual debited/credited amount and balance changes all match.
- Test worst-case VIP purchase discount + sale bonus against every market;
  the round-trip safety guard must reject any profitable pair.

## Trading, controls and exploits

On a staging account, temporarily set `trading.enabled: true`:

- Left-click BUY, right-click SELL, shift-left BUY_GUI, shift-right SELL_ALL,
  middle-click SELL_ALL; test alternate click mappings and disabled actions.
- In the amount GUI, change quantity by 1/10, set 1/max, and confirm. The
  **selected item** must show live quantity, total price and currency; the
  confirm button must not incorrectly display a stale amount.
- Buy/sell ordinary vanilla items and at least one `voxelcore:voxel:...` item.
  Reject items with mismatched display name, NBT, PDC, tint or custom metadata.
- Test zero items, full inventory, insufficient funds, a closed shop, stock
  zero, stock capacity reached, item-only sell stock, and unstackable items.
- Test inventory hotbar-number swaps, drag, shift-click, double-click,
  middle-click in creative mode, rapidly repeated clicks and externally
  cancelled InventoryClickEvent. No unexpected wallet changes may occur.
- Validate bounded SELL_ALL behaviour when stock capacity has only 1 item
  remaining and when the inventory contains multiple stacks.

## Crash/fault injection — requires real server testing

Trigger controlled shutdown/kill, JVM termination or simulated IOException at
each transaction journal state: INTENT, DEBIT_IN_PROGRESS, DEBIT_CONFIRMED,
STOCK_PERSISTED, DELIVERY_IN_PROGRESS, PLAYER_DATA_PERSISTED, or equivalent
SELL states. After restart:

1. VoxelShop must refuse ALL new trading while any pending file remains.
2. `/vshop pending` must show transaction UUID, player, market, side, amount.
3. Manually compare inventory, wallet, stock snapshot and economy history.
4. Reconcile the outcome **externally**, then run
   `/vshop reconcile <UUID> --confirmed` to archive, never to repay.
5. No duplicate item, free purchase or unearned sale credit is acceptable.
6. Verify market stock and revision are stable after repeated restarts.

## Performance and compatibility

- Run spark with shops open and a representative 20+ players clicking
  concurrently. The durable synchronous journal can create disk/fsync pressure.
- Profile main-thread latency during SELL_ALL, inventory savedata, economy
  provider operations, and stock snapshot writes. Never accept repeated
  >50 ms stalls as a production target.
- Recheck after `/vshop reload` with invalid config: trading must stay locked.
- Manually test external VoxelShopTradePreEvent cancellation and successful
  VoxelShopTradeCompleteEvent post-payment dispatch.
- Compare all active original ShopGUIPlus shop files against loaded offers,
  currencies, special items, potion/enchantment metadata, navigation and commands.

## Remaining feature work before full ShopGUIPlus parity

Full vendor event/API compatibility and external plugin adapters; complex
special items, enchantment/potion/spawner/NBT shop definitions; legacy
per-player price-modifier command/data migration; advanced GUI sounds, messages,
bulk quantity pages and customization; provider-aware idempotent recovery;
automated Paper end-to-end fault-injection and economic arbitrage simulation.
This project currently provides an independent functional subset and must
not be represented as 100% vendor parity.
