# SBPC (Session-Based Progression Cap)

SBPC is a Spigot plugin that paces progression behind **time-bound sections**. Players unlock items, enchants, and mechanics as in-game time advances or when they hit specific milestones, giving you fine-grained control over how fast a world opens up. It is built to cooperate with [SessionLibrary] for session-aware gating but works without it.

## Why use SBPC?
- **Sectioned unlocks:** Progression is split into ordered "sections" with timers. Related materials or milestones speed the timer up, while admin commands can jump or slow entire sections.
- **Rich pacing controls:** Global speed multipliers, per-material bonuses, and kill- or inventory-based auto-completion make it easy to tune a season’s tempo.
- **Player-facing guidance:** Bossbars, chat messages, and `/currenttimeskip` give clear feedback about what is locked, what is next, and how to accelerate progress.
- **Session-aware access:** Optional SessionLibrary integration can block joins or pause progression when a session is closed, keeping events synchronized.

## Getting started
1. **Drop the jar** (with your compiled main class) into `plugins/` and start the server. Java 17+ and Spigot/Paper 1.21 are expected.
2. **Configure progression** in `plugins/SBPC/config.yml`. Sections are ordered; each entry defines a timed unlock for an item, enchant, custom mechanic, or custom item.
3. **(Optional) Enable SessionLibrary** by setting `session.session-library-enabled: true`. When no session is active, joins are blocked with `messages.server-closed-join` and progression pauses.
4. **Guide players**: Encourage `/currenttimeskip` to show the current section, its related materials, and any special completion rules.

### Example gameplay flow
- Early game, the **First Steps** section starts a 30s timer to unlock wooden axes. Chopping logs speeds it up using the related-material bonus.
- When the server moves into **Wood Tools + Leather Armor**, collecting a **Pale Log** (even via commands) auto-completes the entire section. Otherwise, crafting the listed items or collecting leather speeds the timers.
- Combat-focused sections such as **Meats** and **Murder** grant time skips on mob or player kills; unique kill tracking prevents farming the same victim repeatedly.
- Long-haul sections like **Blaze Powder** can run for hours but accept stacking speed boosts from special items or admin multipliers to keep them manageable.

### Admin command examples
- `/sbpc reloadConfig` → Reload `config.yml` and rebind listeners without restarting.
- `/sbpc jump massacre` → Skip directly to the `massacre` section for testing.
- `/sbpc speed 0.5` → Halve all timers globally; combine with related-material bonuses for rapid progression events.
- `/sbpc config` → Inspect the active configuration and confirm section ordering during setup.

### Configuration snippets
Configure a section that autocompletes on inventory detection and gains speed from related materials:
```yaml
progression:
  related-bonus:
    skip-seconds: 10
    percent-speed-increase: 3.0
  sections:
    shulker_shell:
      display-name: "Shulker Shells"
      color: "§d"
      related-materials: [SHULKER_SHELL]
      special-info: "Possessing an Elytra auto completes this section."
      entries:
        shulker_shell:
          type: ITEM
          material: SHULKER_SHELL
          seconds: 7200
```

## Plugin integration surface (for hook plugins)
Expose or implement the following API so other plugins can react to or influence SBPC progression. These names reflect the expected runtime types even though the Java sources are not bundled in this snapshot.

### Services and managers
- `ProgressionService` — Primary API for reading and adjusting player progression.
  - `SectionProgress getCurrentSection(Player player)` — Returns the active section identifier, display metadata, and per-entry timing for the player.
  - `void advance(Player player, String entryKey, double seconds)` — Adds progress toward a specific entry (e.g., called when a mob is killed or an item is gathered).
  - `void jumpToSection(String sectionId)` — Forces the server to advance directly to the given section; used by admin commands or tests.
  - `void setSpeedMultiplier(double multiplier)` — Applies a global speed factor that stacks with related-material bonuses.
  - `void reload()` — Reloads `config.yml`, rebuilds sections, and rebinds listeners.
- `SessionGate` — (Optional) bridge to SessionLibrary; exposes current session state and events so hooks can align behavior when the server is closed.

### Data types
- `SectionProgress`
  - `String id` — Unique section key (e.g., `first_steps`).
  - `String displayName` and `ChatColor color` — Presentable name and color for bossbars/messages.
  - `List<EntryProgress> entries` — Ordered unlockables with remaining time.
  - `List<Material> relatedMaterials` — Materials that trigger speed bonuses; useful for external listeners to register interest.
  - `String specialInfo` — Human-readable rule for auto-completion or milestones.
- `EntryProgress`
  - `String key` — Internal identifier matching the config entry (e.g., `blaze_powder`).
  - `String name` — Player-facing label shown in bossbars.
  - `EntryType type` (`ITEM`, `CUSTOM_ITEM`, `ENCHANT`, `MECHANIC`) — The kind of unlock represented.
  - `Material material` / `String customKey` / `Enchantment enchantKey` / `int level` — Type-specific identifiers hook plugins can recognize when listening to events.
  - `double remainingSeconds` and `double totalSeconds` — Time bookkeeping for visualizations or alternative UIs.

### Events (suggested)
If you expose Bukkit events, hook plugins can respond without direct service calls:
- `SectionStartEvent(Player, SectionProgress)` — Fired when a player enters a new section.
- `EntryUnlockEvent(Player, EntryProgress)` — Fired when an entry finishes; allows other plugins to grant items, play sounds, or log analytics.
- `SectionCompleteEvent(Player, SectionProgress)` — Triggered when all entries in a section unlock.
- `SessionStateChangeEvent(boolean active)` — Indicates SessionLibrary open/closed state; useful for pausing custom systems alongside SBPC.

## Implementation notes
- The packaged `plugin.yml` points to `me.BaddCamden.SBPC.SBPCPlugin` as the main class; include your sources when building the jar.
- The bundled `config.yml` contains all sections and messages described above; customize it to fit your server’s pacing, or extend it with new `CUSTOM_ITEM` or `MECHANIC` keys for your own plugins to implement.
- Build with Maven (`mvn clean package`). Ensure compiled classes and `plugin.yml` ship at the jar root.

## Useful references
- `src/plugin.yml` — Commands, permissions, and plugin metadata.
- `src/config.yml` — Default messages and the full progression tree, including related materials and special completion rules.

[SessionLibrary]: https://github.com/BaddCamden/SessionLibrary
