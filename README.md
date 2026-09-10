# HiddenOre

HiddenOre is a mining economy and anti-xray plugin for Minecraft servers. It replaces visible ore distribution with configurable rewards hidden behind ordinary blocks, while keeping reward discovery deterministic and compatible with mining integrations such as Adapt.

## Language and localization

Canonical English is defined in the typed Java catalog at `src/main/java/art/arcane/hiddenore/util/common/Messages.java`. Startup creates an editable `plugins/HiddenOre/languages/en_US.toml`; other selected catalogs download when their local file is missing. Set `language` in `hiddenore.toml` to select the server default, and edit `languages/<locale>.toml` directly or use `/hiddenore language server edit [locale]` to customize messages. Missing or invalid entries use English while valid translations remain active.

Messages use Minecraft color codes: `&0` through `&f` for colors, `&k` through `&o` for text decorations, `&r` to reset formatting, and `&#RRGGBB` for hex colors. Keep runtime variables such as `{block}` unchanged.

## Requirements

- Java 25
- Minecraft/Paper API 26.1.2 - 26.2
- Paper, Purpur, Folia, or Spigot-compatible server software

## How it works

Configured base blocks enter HiddenOre's mining pipeline. The default configuration manages stone and deepslate, preserves their normal cobblestone-style drops, and can add hidden items, experience, or command rewards.

Creative players, non-pickaxe breaks, unmanaged blocks, and cancelled block-break events do not enter the reward pipeline. HiddenOre commits rewards only after Bukkit has completed an accepted block break, then fires `HiddenOreDropsEvent` before commands, items, or experience are delivered.

### Seeded veins

`veins.generation = "seeded"` derives virtual vein positions from the world seed, chunk coordinates, and each rule's material and spatial settings. No physical ore block is placed. Each discovered position is recorded in chunk persistent data and pays at most once, including across restarts.

Reordering item rules leaves their deterministic positions unchanged. Changing a rule's material, vein count, size range, or height range changes its undiscovered layout; adding or removing unrelated rules affects retained layouts only at direct overlaps.

### Pure random rewards

`veins.generation = "pure_random"` rolls each eligible break independently. There are no pre-existing positions for detection APIs to find. `veins_per_chunk` and the configured vein-size range are converted into an approximate per-break probability.

Player-placed managed blocks are tracked regardless of the current policy and blocked from hidden rewards by default. Keeping `allow_placed_blocks = false` is especially important in pure-random mode because it prevents place-and-break reward farming. Temporarily enabling the option does not discard placement provenance if it is disabled again later.

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

Available built-in placeholders are `%player%`, `%uuid%`, `%world%`, `%x%`, `%y%`, and `%z%`. After the built-in pass, the whole command is handed to PlaceholderAPI when it is installed, so `eco give %player% %vault_eco_balance%` resolves. Rewards fire on an accepted block break, never per tick.

`suppress_block_drop_on_custom_drop` controls whether an item or command reward replaces the normal configured base-block drop. `auto_pickup_drops` delivers rewards to the player's inventory and drops only inventory overflow at the mined block.

## PlaceholderAPI

HiddenOre registers the `hiddenore` expansion when PlaceholderAPI is installed. Because HiddenOre loads at `STARTUP` and PlaceholderAPI loads after worlds, registration also runs on PlaceholderAPI's own enable; the expansion is unregistered during drain, so BileTools hot unloads leave nothing serving against a dead plugin.

| Key | Answers | Source |
| --- | --- | --- |
| `%hiddenore_available%` | `true` / `false` | the published runtime exists |
| `%hiddenore_seeded%` | `true` / `false` | `veins.generation` is `seeded` |
| `%hiddenore_drop-rules%` | count of configured drop rules | the published rule set |

All three read one volatile field holding the immutable runtime record, so a resolution costs a hash lookup and a field read with no allocation, no lock, and no Bukkit call. Before the runtime is published, `available` answers `false` and the other two answer `---`. A misspelled key resolves to nothing and PlaceholderAPI re-emits the literal text, so typos stay visible.

There are deliberately no vein, provenance, or per-player keys. The vein and provenance API contractually requires the owning world region thread and PlaceholderAPI never resolves on it; the nearby-vein query walks up to 289 chunks of block reads; and publishing vein positions to a scoreboard would turn an anti-xray plugin into an xray oracle. Those answers stay behind `/hiddenore`.

## Configuration safety and reloads

HiddenOre validates mining rules and runtime settings before publishing them. Invalid materials, missing sections, malformed tool tiers, invalid ranges, non-finite probabilities, unsafe vein-work limits, empty commands, and invalid execution targets reject the update. The previous live runtime remains active and the full error is written to the console.

The file watcher applies saved changes to `hiddenore.toml` and `languages/<locale>.toml` automatically. It waits for 250 ms without further edits and enforces a 3-second cooldown between reloads. Periodic scans detect changes missed by file events, including atomic-save editors. A successful configuration reload replaces the rule-bound seeded cache and publishes mining rules, language, reward flags, and ore-removal policy in one runtime swap.

All settings in `hiddenore.toml` apply on save, including enabling or disabling metrics. The watcher also detects additions and deletions of locale TOML files directly inside `languages/`. Language updates retain personal choices; a player may briefly receive server-default text while the selected locale loads.

`/hiddenore config` opens an inventory editor for existing settings and drop rules. Booleans toggle on click; text, numbers, and primitive lists use private chat input with `cancel` and a 60-second timeout. Whole tables and drop rules are added or removed directly in the file.

Configuration-editor saves validate the complete configuration and reject edits when the original file has changed. Editor saves and server-language selection replace only the selected TOML value, preserving surrounding comments and formatting. The watcher applies saved edits automatically.

Automatic reloads notify online operators with a message and the HiddenOre command theme's success sound from VolmLib.

## Commands and permissions

- `/hiddenore` shows command help.
- `/hiddenore config` opens the in-game configuration editor.
- `/hiddenore debug mode` toggles per-player reward-roll diagnostics.
- `hiddenore.admin` grants command access and defaults to operators.

## API and integrations

`HiddenOreService` is the supported entry point. Acquire it with `getServer().getServicesManager().getRegistration(HiddenOreService.class)`; it is registered during enable and unregistered during drain. It references only `org.bukkit`, `java` and HiddenOre's own `art.arcane.hiddenore.api` types, so a consumer needs neither VolmLib nor Adventure on its classpath. It exposes managed-block checks, a vein at a block, remaining same-chunk siblings, nearby unconsumed veins, per-block placement origin, per-chunk placement provenance, consumed-vein checks, and a region-ownership probe.

`originOf` answers `PLAYER_PLACED`, `PRESUMED_GENERATED` or `UNTRACKED`. `PRESUMED_GENERATED` means "this material is tracked and there is no placement record" — it does not mean worldgen produced the block, which is why the constant is named for the presumption rather than for the conclusion. Blocks placed before HiddenOre was installed, before their material was added to the `blocks` tables, or during a failed enable all report `PRESUMED_GENERATED`. There is no backfill. Do not build an anti-grief rule that treats it as proof.

`provenanceOf(Chunk)` returns an immutable `ChunkProvenance` snapshot: one persistent-data read at construction, then unlimited `contains(worldX, worldY, worldZ)` queries with no further I/O. Use it instead of calling `originOf` in a loop. `contains` is total — coordinates outside the chunk or outside world height answer `false` rather than throwing, so you can walk a 3x3 chunk area against one snapshot without guarding the edges. Compare against `chunk()` yourself if you need the strict reading.

`veinAt`, `veinSiblings`, `originOf`, `provenanceOf` and `isVeinConsumed` throw `IllegalStateException` off-region — call `ownsRegion` first to branch instead of catching. Nearby queries are capped at a 128-block radius and 4,096 results and skip foreign-region and unloaded chunks rather than accessing them unsafely.

`HiddenOreBreakEvent` fires once per accepted managed break, before any reward is computed, and is cancellable. Cancelling means HiddenOre contributes nothing: no hidden item, no experience, no reward command, no discovery sound, and the vein is not marked consumed. The block still yields its configured guaranteed drop and `HiddenOreDropsEvent` still fires, carrying only that drop, a null vein and zero experience. A listener that throws is logged and treated as no veto; a listener slower than 5ms is logged by plugin name without changing the outcome.

`HiddenOreDropsEvent` lets integrations edit its mutable drop list, experience, and inventory-delivery flag. It is intentionally not cancellable because existing mining integrations perform irreversible progression work while handling it; block/drop protection is settled before the event is fired, and `HiddenOreBreakEvent` is the place to refuse. At most 256 stacks are spawned from one break; overflow is logged by plugin name.

`HiddenOreAPI` remains reachable through the plugin main class for existing consumers. It implements `HiddenOreService` and behaves identically. New integrations should use the service: reaching `HiddenOreAPI` through the main class additionally requires VolmLib on the consumer's compile classpath.

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
