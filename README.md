# HiddenOre

HiddenOre is a mining economy and anti-xray plugin for Minecraft servers. It replaces visible ore distribution with configurable rewards hidden behind ordinary blocks, while keeping reward discovery deterministic and compatible with mining integrations such as Adapt.

## Language and localization

Canonical English is defined in the typed Java catalog at `src/main/java/art/arcane/hiddenore/util/common/Messages.java`; HiddenOre does not ship a separate English translation bundle. Complete bundles are included for German, Spanish, Finnish, French, Hebrew, Italian, Japanese, Korean, Lithuanian, Dutch, Polish, Portuguese, Russian, Turkish, Vietnamese, Simplified Chinese, and Traditional Chinese. Set `locale` in `language.yml` to select one. Message entries in that file are optional sparse server overrides; omitted entries resolve from the selected bundle and then code-owned English. Sound settings remain in the same file.

## Requirements

- Java 25
- Minecraft/Paper API 26.2
- Paper, Purpur, Folia, or Spigot-compatible server software

## How it works

Configured base blocks enter HiddenOre's mining pipeline. The default configuration manages stone and deepslate, preserves their normal cobblestone-style drops, and can add hidden items, experience, or command rewards.

Creative players, non-pickaxe breaks, unmanaged blocks, and cancelled block-break events do not enter the reward pipeline. HiddenOre commits rewards only after Bukkit has completed an accepted block break, then fires `HiddenOreDropsEvent` before commands, items, or experience are delivered.

### Seeded veins

`veins.generation: seeded` derives virtual vein positions from the world seed, chunk coordinates, and configured rule order. No physical ore block is placed. Each discovered position is recorded in chunk persistent data and pays at most once, including across restarts.

Changing the order of item rules changes their deterministic positions. Back up worlds before reordering, inserting, or deleting item rules on an established server.

### Pure random rewards

`veins.generation: pure_random` rolls each eligible break independently. There are no pre-existing positions for detection APIs to find. `veins_per_chunk` and the configured vein-size range are converted into an approximate per-break probability.

Player-placed managed blocks are tracked regardless of the current policy and blocked from hidden rewards by default. Keeping `allow_placed_blocks: false` is especially important in pure-random mode because it prevents place-and-break reward farming. Temporarily enabling the option does not discard placement provenance if it is disabled again later.

## Ore removal and anti-xray

`ore-removal.enabled` is disabled by default. HiddenOre keeps one lifecycle-managed world populator attached and publishes its immutable replacement policy with the rest of the runtime configuration. When the policy is enabled, configured vanilla ores are replaced with stone, deepslate, or netherrack while new chunks are generated; when disabled, the populator is a no-op.

- Existing chunks are not modified retroactively.
- Disabling the option stops replacement but does not restore previously removed ores.
- World-specific exceptions override the global replacement policy and use fully qualified keys such as `minecraft:the_nether`.
- Iris dimensions can remove their own terrain, deposit, and object ores with `hideOresForHiddenOre`.

HiddenOre does not perform packet obfuscation. Servers that leave physical ores enabled still expose those physical blocks to ordinary x-ray clients.

## Rewards

Item rules support:

- material
- veins per chunk
- minimum and maximum vein size
- minimum and maximum world Y
- allowed pickaxe tiers
- vanilla-style Fortune multiplication
- inclusive random experience from `0..exp_drop`

To keep generation and break handling bounded, each item rule allows at most 64 veins per chunk and a maximum vein size of 256. Across all item rules, the worst-case target count may not exceed 1,024 blocks per chunk, `exp_drop` is capped at 1,000, and pure-random settings may not derive a probability above 1.

Command rules support a probability, Y range, default execution target, and one or more commands. Individual commands can override their target with `player:` or `console:`. Mixed player/console command sequences retain their configured order across the required schedulers.

Available placeholders are `%player%`, `%uuid%`, `%world%`, `%x%`, `%y%`, and `%z%`.

`suppress_block_drop_on_custom_drop` controls whether an item or command reward replaces the normal configured base-block drop. `auto_pickup_drops` delivers rewards to the player's inventory and drops only inventory overflow at the mined block.

## Configuration safety and reloads

HiddenOre validates typed mining rules, runtime settings, command messages, usage text, and reload notifications before publishing them. Invalid materials, missing sections, malformed tool tiers, invalid ranges, non-finite probabilities, unsafe vein-work limits, empty commands, and invalid execution targets reject the reload. The previous live runtime remains active and the full error is written to the console.

Both `/hiddenore reload` and the config file watcher use the same serialized global reload path. File watching handles common atomic-save editors, overflow signals, and debounces repeated filesystem events. A successful reload replaces the rule-bound seeded cache and publishes mining rules, language, reward flags, notifications, and ore-removal policy in one runtime swap.

## Commands and permissions

- `/hiddenore` shows command help.
- `/hiddenore reload` validates and reloads configuration and language files.
- `/hiddenore debug` toggles per-player reward-roll diagnostics.
- `hiddenore.admin` grants command access and defaults to operators.

## API and integrations

`HiddenOreAPI` exposes managed-block checks, a vein at a block, remaining same-chunk siblings, and nearby unconsumed veins. Single-block and sibling methods must run on the owning region thread. Nearby queries are capped at a 128-block radius and 4,096 results. On Folia, they inspect only chunks owned by the caller's current region; foreign-region chunks are skipped instead of being accessed unsafely.

`HiddenOreDropsEvent` lets integrations edit its mutable drop list, experience, and inventory-delivery flag. It is intentionally not cancellable because existing mining integrations perform irreversible progression work while handling it; block/drop protection is settled before the event is fired.

Adapt consumes this API for hidden-vein sensing, veinminer, autosmelt, gem polish, inventory delivery, and skill experience.

## Persistence and lifecycle

Consumed vein positions and protected player placements are stored as packed, sorted coordinate arrays in each chunk's persistent data. Piston movement carries placed-block provenance, including sticky-piston retraction. World unloads evict that world's in-memory vein cache.

Normal disable and BileTools hot unload serialize against reloads, reject queued watcher callbacks, stop the watcher, detach world populators, clear transient debug state, and close Adventure audiences. A failed startup drains partial services and disables HiddenOre instead of leaving a partially initialized plugin active.

## Building

```bash
./gradlew test
./gradlew build
```

The unclassified jar under `build/libs` is the thin compile-facing artifact. The deployable shaded plugin jar uses the `plugin` classifier. Custom development output tasks continue to copy and rename the shaded artifact as `HiddenOre.jar`; deployment into a server plugin directory is intentionally left to the server operator.
