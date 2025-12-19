# SBPC (Session Based Progression Cap)

Session-Based Progression Cap (SBPC) is a Spigot plugin that sequences item, enchant, and mechanic unlocks over time-bound “sections.” It ships with a rich `config.yml` defining pacing, messages, and progression milestones, and expects a companion SessionLibrary for session gating.

## Project layout

- `pom.xml` – Maven build using Java 17, depends on `spigot-api` 1.21.10 and `SessionLibrary` 0.0.1-SNAPSHOT.
- `src/plugin.yml` – Plugin metadata, commands, and permissions.
- `src/config.yml` – Primary gameplay configuration (messages and progression sections).

## Runtime entrypoint

`plugin.yml` points to `me.BaddCamden.SBPC.SBPCPlugin` as the main class, but no Java sources are included in this repository snapshot. To run the plugin you must supply that class (and any supporting code) on the classpath when packaging the jar.

```yaml
main: me.BaddCamden.SBPC.SBPCPlugin
softdepend:
  - SessionLibrary
api-version: 1.21
```

### Expected startup flow
1. Plugin enables after its soft dependency (SessionLibrary) if present.
2. Main class should read `config.yml`, register listeners/commands, and initialize progression state.
3. If `session.session-library-enabled` is `true`, defer availability to SessionLibrary session state; otherwise always allow progression.

## Commands and permissions

| Command | Permission | Purpose |
| --- | --- | --- |
| `/sbpc <reloadConfig|jump|speed|config>` | `sbpc.admin` (default: op) | Administrative controls: reload config, jump to sections, adjust speed, and inspect configuration. |
| `/currenttimeskip` | _none declared_ | Shows a player’s current section, related materials, and special info. |

## Configuration reference (high level)

- `session.session-library-enabled`: Toggles integration with SessionLibrary for session gating.
- `messages.*`: All player-facing strings; placeholders like `{section}`, `{item}`, and `{seconds}` must be replaced at runtime.
- `progression.global-speed-multiplier`: Global pacing modifier applied to all sections.
- `progression.related-bonus`: Base per-material bonus (`skip-seconds`, `percent-speed-increase`) applied when related materials are collected.
- `progression.sections`: Ordered progression blocks. Each section defines:
  - `display-name`, `color`, optional `broadcast-unlock`, `related-materials`, `special-info`.
  - `entries`: Timed unlockables with `type` (`ITEM`, `CUSTOM_ITEM`, `ENCHANT`, `MECHANIC`), identifiers (`material`, `custom-key`, or `enchant-key` + `level`), and `seconds` duration.

## Implementation hooks and quirks

- **Custom items/mechanics**: Many entries use `CUSTOM_ITEM` or `MECHANIC` with `custom-key` (e.g., `pvp_unlock`, `tracking_compass`, `housing_infrastructure`). Your code must map these keys to actual behaviors, progression triggers, or items.
- **Auto-complete conditions**: Several sections complete when the player possesses specific items or effects “even if not obtained naturally” (e.g., Pale Log, Obsidian, Dragon Egg). Implement listeners that scan inventories/effects rather than only crafting/pickup events.
- **Kill-based progress**: Sections like `murder`, `massacre`, and `serial_killer` rely on player kill events, sometimes tracking unique victims or skipping fixed time per kill. Ensure you record unique player UUIDs to prevent duplicate credit.
- **Enchant tiers**: Four large blocks of `ENCHANT` entries expect interception of enchanting, disenchanting, and potentially anvil actions to validate availability and to speed progress when allowed.
- **Long timers with large boosts**: Some entries last up to 96 hours (`blaze_powder`, `shulker_shell`) but can be accelerated by 10,000% or auto-completed. Clamp progress calculations to avoid negative remaining time when stacking boosts.
- **Bossbar and messaging**: `messages.bossbar-*` imply a bossbar showing active entry time remaining. Keep UI updates in sync with the internal scheduler.
- **Session gating**: When SessionLibrary reports no active session, joins should be denied with `messages.server-closed-join` and progression timers paused or blocked.

## Suggested API surface

Expose a small API for other plugins or internal modules:

- `ProgressionService#getCurrentSection(Player)` → returns section id and entry progress for the player.
- `ProgressionService#advance(Player, key, amountSeconds)` → adds time progress to a specific entry (used by hooks for kills, crafting, pickups).
- `ProgressionService#jumpToSection(String sectionId)` → admin and `/sbpc jump` support.
- `ProgressionService#setSpeedMultiplier(double multiplier)` → integrates with `/sbpc speed` and global modifiers.
- `ProgressionService#reload()` → reloads `config.yml` and rebinds hooks.

## Edge cases to test

- Using `/sbpc jump` while timers are mid-progress (ensure carry-over rules are defined).
- Applying overlapping speed boosts (related materials + special items + admin speed) without producing negative durations.
- Enchant table rolls when the player has not unlocked any rolled enchantment (`messages.enchant-cancelled`).
- Inventory-based auto-complete detection when items are moved via hoppers or commands, not picked up.
- Session disabled while a section is in progress—timers should pause and bossbars hide until the session resumes.

## Building

Because the build excludes `*.java` under `src`, you must either:

1. Place Java sources outside `src` or adjust `pom.xml` resource excludes; **or**
2. Move sources into `src` and remove the `**/*.java` exclude from the resources block.

Compile with Maven:

```bash
mvn clean package
```

Ensure the produced jar contains your implementation classes and `plugin.yml` at the root, along with the bundled `config.yml` if you want defaults shipped.
