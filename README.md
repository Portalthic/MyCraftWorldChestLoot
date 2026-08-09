# MyCraftWorldChestLoot

Paper 1.12.2 random chest loot plugin with WorldGuard 6.2.2 region selection and optional Zaphkiel item IDs.

## Pool files

Loot pools are loaded from `plugins/MyCraftWorldChestLoot/LootTables/*.yml`. The primary format is the PhatLoots serialized format, so an existing `LootTables/<name>.yml` can be copied into this directory without conversion. The loader supports `PhatLoot`, nested `LootCollection`, and `Item` entries, including `Probability`, `LowerNumberOfLoots`, `UpperNumberOfLoots`, `Global`, and `Reset`.

Only the PhatLoots-compatible files in `LootTables` are loaded. The former `pools` directory, compact reward format, and `config.yml` `pools` section are not supported.

The original PhatLoots sample contains `Money` and `Experience` nodes. They are intentionally ignored by this chest-only plugin; item and nested collection entries are loaded.

`LootTables/SampleLoot.yml` is the upstream reference sample. `LootTables/SampleLootZaphkiel.yml` demonstrates the additional item-library entry:

```yaml
- ==: ZaphkielItem
  ItemID: example_sword
  Amount: 1
  Probability: 70.0
```

`ZaphkielItem` is the only intentional format extension; it stores the Zaphkiel ID instead of a copied `ItemStack`.

## Commands

`/wcl` is an alias of `/mcwcl`.

- `/mcwcl reload`
- `/mcwcl make <name>`
- `/mcwcl info <name>`
- `/mcwcl link <name>`
- `/mcwcl unlink`
- `/mcwcl reset`
- `/mcwcl reset <pool>`
- `/mcwcl reset all`
- `/mcwcl clean`
- `/mcwcl list`

`link <name>` follows the original PhatLoots workflow: look at a configured chest and execute the command. The plugin automatically records world, current highest-priority WorldGuard region, and chest material. No coordinates or region arguments are needed.

`info <name>` opens the administrator editor GUI. Click items in the player inventory to add rewards, select the Probability, Amount, or Reset tool, then click reward entries to adjust them. Left-click in Manage mode removes a reward. Close the inventory to save.

`reset` follows the original PhatLoots command behavior: with no argument, it resets the chest you are looking at; with a pool name, it clears all existing cooldown records for that pool; `reset all` clears every cooldown record. These commands do not change a pool's configured cooldown duration. `clean` removes records whose cooldown has already expired.

## Selection

When a configured chest is opened, the plugin selects the highest-priority WorldGuard region linked for that world and chest material. If no region link matches, the world's material default and then `settings.default-pool` are used. Each pool controls its own cooldown and `global-reset` mode.

For example:

```yaml
links:
  survival:
    regions:
      dungeon:
        CHEST: rare:稀有宝箱
    default:
      CHEST: basic:基础宝箱
```

This means a normal chest inside the WorldGuard region `dungeon` in world `survival` uses `rare` and displays `稀有宝箱`; other normal chests use `basic` and display `基础宝箱`. The title after the first colon is optional. Without it, the loot table name is displayed. The original PhatLoots AutoLink form is also accepted:

The general regional structure is:

```yaml
links:
  <世界名>:
    regions:
      <区域名>:
        <方块名>: <链接的loot名>
    default:
      <方块名>: <链接的loot名>
```

The direct world-default structure, equivalent to the original AutoLink format, is:

```yaml
links:
  <世界名>:
    <方块名>: <链接的loot名>
```

For example, `survival: CHEST: basic` links normal chests in the `survival` world to the `basic` loot table.

## Chest behavior

The following PhatLoots-style settings are in `config.yml`:

```yaml
ShuffleLoot: false
ForgetInventoryTime: 60
ChestName: "&6<name>"
```

`ShuffleLoot: true` scatters generated items across the available slots. A normal chest uses 27 slots; a double chest uses 54 slots. Both halves of a double chest are normalized to the same left-side chest for region selection, cooldowns, cached contents, linking, and reset commands.

`ForgetInventoryTime` controls how long a generated virtual inventory remains in memory. During that period, reopening the chest shows the same inventory with any already-taken items missing. Once the cache expires, a chest that is still on cooldown opens as an empty inventory instead of being blocked. The cooldown itself is still stored in `cooldowns.yml`.

Chest opening and closing play the 1.12.2 chest block animation and chest sound. `message.yml` contains all player-facing command, cooldown, editor, and overflow text and can be edited without changing the plugin JAR.
