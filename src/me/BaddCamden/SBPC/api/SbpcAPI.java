// src/me/BaddCamden/SBPC/api/SbpcAPI.java
package me.BaddCamden.SBPC.api;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import me.BaddCamden.SBPC.progress.PlayerProgress;
import me.BaddCamden.SBPC.progress.ProgressEntry;
import me.BaddCamden.SBPC.progress.ProgressEntry.EntryKind;
import me.BaddCamden.SBPC.progress.ProgressManager;
import me.BaddCamden.SBPC.progress.SectionDefinition;

public final class SbpcAPI {

    private static ProgressManager manager;

    /**
     * Utility class; prevent instantiation.
     */
    private SbpcAPI() {}

    /**
     * Wires the API to the plugin's ProgressManager. Must be called during plugin startup.
     */
    public static void init(ProgressManager mgr) {
        manager = mgr;
    }

    /**
     * Ensures the API has been initialised before accessing the manager.
     */
    private static ProgressManager requireManager() {
        if (manager == null) {
            throw new IllegalStateException("SbpcAPI not initialized yet.");
        }
        return manager;
    }

    /**
     * Returns the PlayerProgress for this player, creating it if needed.
     * Hook plugins should generally prefer the higher-level helpers instead of
     * poking at PlayerProgress directly.
     */
    public static PlayerProgress getProgress(UUID uuid) {
        return requireManager().getOrCreateProgress(uuid);
    }
    /**
     * Returns the ID of the player's current progression entry, or null if there is none.
     * This uses the same "current entry" notion as the boss bar and external time skip.
     */
    public static String getCurrentEntryId(UUID uuid) {
        if (manager == null || uuid == null) return null;

        PlayerProgress prog = manager.getOrCreateProgress(uuid);
        if (prog == null) return null;

        ProgressEntry current = prog.getCurrentEntry();
        return (current != null) ? current.getId() : null;
    }


    /**
     * Returns true if the progression entry with this id has been unlocked for the given player.
     */
    public static boolean isEntryUnlocked(UUID uuid, String entryId) {
        if (manager == null || uuid == null || entryId == null || entryId.isEmpty()) {
            return false;
        }
        return getProgress(uuid).isEntryUnlocked(entryId);
    }

    /**
     * Returns true if any CUSTOM_ITEM or MECHANIC entry that uses the given customKey
     * has been unlocked for this player.
     *
     * customKey is the same identifier used in the config�s "custom-key" field.
     */
    public static boolean isCustomUnlocked(UUID uuid, String customKey) {
        return getProgress(uuid).isCustomUnlocked(customKey);
    }

    /**
     * Returns the first CUSTOM_ITEM or MECHANIC ProgressEntry that uses the given customKey,
     * or null if none is configured.
     */
    public static ProgressEntry getCustomEntry(String customKey) {
        return requireManager().getFirstCustomEntry(customKey);
    }

    /**
     * Returns the SectionDefinition for a given section id, or null if not found.
     */
    public static SectionDefinition getSection(String sectionId) {
        return requireManager().getSectionById(sectionId);
    }

    /**
     * Returns true if the player is currently on the provided entry id.
     */
    public static boolean isOnEntry(UUID uuid, String entryId) {
        if (manager == null || uuid == null || entryId == null || entryId.isEmpty()) {
            return false;
        }
        return requireManager().getOrCreateProgress(uuid).isOnEntry(entryId);
    }

    /**
     * Returns true if the player is currently in the given section.
     */
    public static boolean isOnSection(UUID uuid, String sectionId) {
        return isOnSection(uuid, sectionId, false);
    }

    /**
     * Checks if the player is in the given section, with an option to treat the last
     * configured section as "current" once all entries are complete.
     */
    public static boolean isOnSection(UUID uuid, String sectionId, boolean includeCompleted) {
        if (manager == null || uuid == null || sectionId == null || sectionId.isEmpty()) {
            return false;
        }
        return requireManager().getOrCreateProgress(uuid).isOnSection(sectionId, includeCompleted);
    }

    /**
     * Returns the SectionDefinition for the given entry, or null if none.
     */
    public static SectionDefinition getSectionForEntry(ProgressEntry entry) {
        if (entry == null) return null;
        return getSection(entry.getSectionId());
    }

    
    
    /**
     * Convenience: returns true if the given entry is custom (CUSTOM_ITEM or MECHANIC).
     */
    public static boolean isCustomEntry(ProgressEntry entry) {
        if (entry == null) return false;
        EntryKind k = entry.getKind();
        return k == EntryKind.CUSTOM_ITEM || k == EntryKind.MECHANIC;
    }

    // === Public API for hook plugins (SBCPSpecials, SBCPLifesteal, etc.) ===

    /**
     * Change the player's related-material bonus (percent per event and seconds skipped).
     */
    public static void setRelatedMaterialBonus(UUID uuid, double bonusPercent, int skipSeconds) {
        if (manager == null) return;
        manager.setRelatedBonus(uuid, bonusPercent, skipSeconds);
    }

    /**
     * Apply a global external time skip and speed change to every player.
     * The values compound with per-player and global defaults.
     */
    public static void applyGlobalTimeSkip(int skipSeconds, double percentSpeedIncrease, String sourceDescription) {
        if (manager == null) return;
        manager.applyGlobalTimeSkip(skipSeconds, percentSpeedIncrease, sourceDescription);
    }

    /**
     * Configure the default time skip/percent applied to every global skip request.
     */
    public static void setGlobalTimeSkipDefaults(int skipSeconds, double percentSpeedIncrease) {
        if (manager == null) return;
        manager.setGlobalTimeSkipDefaults(skipSeconds, percentSpeedIncrease);
    }

    /**
     * Apply an external time skip and speed bonus to the player's current entry.
     * This is the generic hook for "additional time reducing events".
     */
    public static void applyExternalTimeSkip(UUID uuid, int skipSeconds, double percentSpeedIncrease, String sourceDescription) {
        if (manager == null) return;
        manager.applyExternalTimeSkip(uuid, skipSeconds, percentSpeedIncrease, sourceDescription);
    }

    /**
     * Adjust the global timer speed multiplier. This compounds with any per-player multipliers.
     */
    public static void setGlobalTimerSpeed(double multiplier) {
        if (manager == null) return;
        manager.setGlobalSpeedMultiplier(multiplier);
    }

    /**
     * Add a globally unlocked entry. Any player that reaches (or is currently at) this entry
     * will automatically skip it.
     */
    public static void addGlobalEntryUnlock(String entryId) {
        if (manager == null) return;
        manager.addGlobalEntryUnlock(entryId);
    }

    /**
     * Add a globally unlocked section. Any player that reaches (or is currently in) this section
     * will automatically complete it.
     */
    public static void addGlobalSectionUnlock(String sectionId) {
        if (manager == null) return;
        manager.addGlobalSectionUnlock(sectionId);
    }

    /**
     * Clear all globally configured unlock overrides.
     */
    public static void clearGlobalUnlocks() {
        if (manager == null) return;
        manager.clearGlobalUnlocks();
    }

    /**
     * Stop the global timer and remove boss bars for all players.
     */
    public static void stopGlobalTimer() {
        if (manager == null) return;
        manager.stopAllTimers();
    }

    /**
     * Resume the global timer and restore boss bars for all players.
     */
    public static void startGlobalTimer() {
        if (manager == null) return;
        manager.startAllTimers();
    }

    /**
     * Returns true when the global timer is paused.
     */
    public static boolean isGlobalTimerPaused() {
        if (manager == null) return false;
        return manager.isTimerPaused();
    }

    /**
     * Auto-completes the player's current section (for "auto completes this section" specials).
     */
    public static void completeCurrentSection(UUID uuid) {
        if (manager == null) return;
        manager.completeCurrentSection(uuid);
    }

    /**
     * Returns the ID of the player's current section, or null if there is none.
     * This returns the section of the CURRENT ENTRY (so if everything is complete, this may be null).
     */
    public static String getCurrentSectionId(UUID uuid) {
        if (manager == null) return null;
        return manager.getCurrentSectionId(uuid);
    }

    /**
     * Variant that, when includeCompleted == true, still returns the highest section
     * the player has reached, even if all entries are complete.
     *
     * - includeCompleted == false: same as getCurrentSectionId(uuid)
     * - includeCompleted == true:
     *      - if there is a current entry, return its section
     *      - if everything is complete, return the last configured section id (or null if none)
     */
    public static String getCurrentSectionId(UUID uuid, boolean includeCompleted) {
        if (!includeCompleted) {
            return getCurrentSectionId(uuid);
        }
        if (manager == null) return null;

        PlayerProgress prog = manager.getOrCreateProgress(uuid);
        ProgressEntry current = prog.getCurrentEntry();
        if (current != null) {
            return current.getSectionId();
        }

        List<SectionDefinition> secs = manager.getSections();
        if (secs == null || secs.isEmpty()) return null;
        return secs.get(secs.size() - 1).getId();
    }

    /**
     * Returns true if the given section is fully completed for this player.
     */
    public static boolean isSectionCompleted(UUID uuid, String sectionId) {
        if (manager == null) return false;
        return manager.isSectionCompleted(uuid, sectionId);
    }

    /**
     * Returns the index (0-based) of a section id in the global section order,
     * or -1 if the section cannot be found.
     */
    public static int getSectionIndex(String sectionId) {
        if (manager == null || sectionId == null) return -1;
        List<SectionDefinition> secs = manager.getSections();
        if (secs == null) return -1;
        for (int i = 0; i < secs.size(); i++) {
            SectionDefinition s = secs.get(i);
            if (sectionId.equals(s.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the SectionDefinition for the player's current section,
     * or null if none.
     *
     * This is just a convenience wrapper around getCurrentSectionId(...) and getSection(...).
     */
    public static SectionDefinition getCurrentSectionDefinition(UUID uuid) {
        return getCurrentSectionDefinition(uuid, false);
    }

    /**
     * Variant that allows including completed sections when resolving the current
     * section definition.
     *
     * - includeCompleted == false: uses getCurrentSectionId(uuid)
     * - includeCompleted == true: uses getCurrentSectionId(uuid, true)
     */
    public static SectionDefinition getCurrentSectionDefinition(UUID uuid, boolean includeCompleted) {
        String sectionId = includeCompleted
                ? getCurrentSectionId(uuid, true)
                : getCurrentSectionId(uuid);

        if (sectionId == null) {
            return null;
        }
        return getSection(sectionId);
    }

    /**
     * Bumps the player up by one full section:
     * - any remaining entries in the current section are auto-completed
     * - the player moves into the next section.
     */
    public static void bumpPlayerUpOneSection(UUID uuid) {
        if (manager == null || uuid == null) return;
        PlayerProgress prog = manager.getOrCreateProgress(uuid);
        prog.completeCurrentSection();
    }

    /**
     * Bumps the player DOWN by one full section:
     * - they are moved to the previous section
     * - unlocks in that section and later sections are cleared
     * - earlier sections stay completed.
     */
    public static void bumpPlayerDownOneSection(UUID uuid) {
        if (manager == null || uuid == null) return;
        PlayerProgress prog = manager.getOrCreateProgress(uuid);
        prog.moveDownOneSection();
    }

    /**
     * Resets ONLY the current entry's progress (environmental death).
     * The section and speed multiplier stay intact.
     */
    public static void resetCurrentEntryProgress(UUID uuid) {
        if (manager == null || uuid == null) return;
        PlayerProgress prog = manager.getOrCreateProgress(uuid);
        prog.resetCurrentEntryProgress();
    }

    /**
     * Drops any EQUIPPED items that are now disallowed (after a section demotion)
     * when keepInventory is true.
     *
     * The dropAt parameter is currently ignored � SBPC will drop them at the
     * player's location via its existing sanitizeEquipment(...) logic.
     */
    public static void dropNowDisallowedEquippedItems(UUID uuid, Location dropAt) {
        if (manager == null || uuid == null) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        // Reuse SBPC's own equipment sanitisation logic
        manager.sanitizeEquipment(player);
    }
}
