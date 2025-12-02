package me.BaddCamden.SBPC.progress;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import me.BaddCamden.SBPC.config.MessageConfig;

import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import me.BaddCamden.SBPC.progress.ProgressEntry.EntryKind;

import me.BaddCamden.SBPC.events.NextSectionEvent;
import me.BaddCamden.SBPC.events.UnlockItemEvent;

public class PlayerProgress {

    private final UUID uuid;
    private final List<ProgressEntry> allEntries;
    private final List<SectionDefinition> sections;
    private final BossBar bossBar;

    private int currentIndex = 0;
    private int remainingSeconds;
    private double speedMultiplier = 1.0;
    private double adminSpeedMultiplier = 1.0;
    private double timeAccumulator = 0.0;

    private final Set<String> unlockedEntries = new HashSet<>();
    private final Set<String> notifiedLockedSections = new HashSet<>();
    private final Set<String> benefitMessageSent = new HashSet<>();

    // Base per-player related-material bonus. The SBCPSpecials hook plugin
    // can change these via SbpcAPI.setRelatedMaterialBonus(..) – this is
    // where the "after Iron Pickaxe" behavior will be moved.
    private double relatedBonusPercentPerHit = 3.0;
    private int relatedBonusSkipSeconds = 10;

    private boolean firstStepsIntroShown = false;

    

    public PlayerProgress(UUID uuid,
                          List<ProgressEntry> allEntries,
                          List<SectionDefinition> sections,
                          BossBar bossBar) {
        this.uuid = uuid;
        this.allEntries = allEntries;
        this.sections = sections;
        this.bossBar = bossBar;

        if (!allEntries.isEmpty()) {
            remainingSeconds = allEntries.get(0).getBaseSeconds();
        } else {
            remainingSeconds = 0;
        }
    }

    public UUID getUuid() {
        return uuid;
    }

    // ------------------------------------------------------------------------
    // Save/load state
    // ------------------------------------------------------------------------

    public Set<String> getUnlockedEntryIds() {
        return new HashSet<>(unlockedEntries);
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public double getAdminSpeedMultiplier() {
        return adminSpeedMultiplier;
    }

    public void setAdminSpeedMultiplier(double adminSpeedMultiplier) {
        if (adminSpeedMultiplier < 0) {
            adminSpeedMultiplier = 0;
        }
        this.adminSpeedMultiplier = adminSpeedMultiplier;
    }


    public void loadState(int savedIndex,
                          int savedRemaining,
                          Set<String> unlocked,
                          double savedAdminSpeedMultiplier) {
        unlockedEntries.clear();
        if (unlocked != null) {
            unlockedEntries.addAll(unlocked);
        }

        int maxIndex = allEntries.size();
        if (savedIndex < 0) savedIndex = 0;
        if (savedIndex > maxIndex) savedIndex = maxIndex;
        this.currentIndex = savedIndex;


        if (currentIndex >= allEntries.size()) {
            this.remainingSeconds = 0;
        } else {
            int base = allEntries.get(currentIndex).getBaseSeconds();
            if (savedRemaining <= 0 || savedRemaining > base) {
                this.remainingSeconds = base;
            } else {
                this.remainingSeconds = savedRemaining;
            }
        }

        if (savedAdminSpeedMultiplier > 0) {
            this.adminSpeedMultiplier = savedAdminSpeedMultiplier;
        }
    }

    // ------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------
    /**
     * Reset ONLY the current entry's remaining time.
     * The section, speed multiplier and unlocked entries remain unchanged.
     */
    public void resetCurrentEntryProgress() {
        ProgressEntry entry = getCurrentEntry();
        if (entry == null) {
            return;
        }
        this.remainingSeconds = entry.getBaseSeconds();
        this.timeAccumulator = 0.0;
        // keep speedMultiplier as-is
        updateBossBar();
    }

    /**
     * Move this player's progression back by one FULL section.
     *
     * - They are moved to the first entry of the previous section.
     * - All entries in that section and any later sections are locked again.
     * - Earlier sections remain completed.
     */
    public void moveDownOneSection() {
        SectionDefinition currentSec = getCurrentSection();
        if (currentSec == null) {
            return;
        }

        // Build a mapping from section id -> index
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            indexById.put(sections.get(i).getId(), i);
        }

        Integer curIdxObj = indexById.get(currentSec.getId());
        if (curIdxObj == null) {
            return;
        }
        int currentSecIndex = curIdxObj;
        if (currentSecIndex <= 0) {
            // already at the first section; can't go lower
            return;
        }

        int targetSecIndex = currentSecIndex - 1;
        SectionDefinition targetSection = sections.get(targetSecIndex);

        // Find the first progression entry belonging to the target section
        int newCurrentIndex = -1;
        for (int i = 0; i < allEntries.size(); i++) {
            ProgressEntry e = allEntries.get(i);
            if (targetSection.getId().equals(e.getSectionId())) {
                newCurrentIndex = i;
                break;
            }
        }
        if (newCurrentIndex == -1) {
            // No entries in the target section; nothing to demote to
            return;
        }

        // Rebuild the unlocked set:
        // keep ONLY entries from sections strictly BEFORE the target section.
        // (Target section and everything after it are locked again.)
        Set<String> newUnlocked = new HashSet<>();
        for (ProgressEntry e : allEntries) {
            String secId = e.getSectionId();
            Integer idx = indexById.get(secId);
            if (idx != null && idx < targetSecIndex && unlockedEntries.contains(e.getId())) {
                newUnlocked.add(e.getId());
            }
        }

        unlockedEntries.clear();
        unlockedEntries.addAll(newUnlocked);

        // Position the player at the start of the target section
        this.currentIndex = newCurrentIndex;
        ProgressEntry currentEntry = allEntries.get(currentIndex);
        this.remainingSeconds = currentEntry.getBaseSeconds();
        this.timeAccumulator = 0.0;
        this.speedMultiplier = 1.0; // clean slate for the new section

        updateBossBar();
    }

    /**
     * Forcefully set the active entry index while keeping chronology consistent.
     * Earlier entries become unlocked; later entries become locked again.
     */
    public void setProgressIndex(int targetIndex) {
        if (targetIndex < 0) {
            targetIndex = 0;
        }
        if (targetIndex > allEntries.size()) {
            targetIndex = allEntries.size();
        }

        unlockedEntries.clear();
        for (int i = 0; i < targetIndex && i < allEntries.size(); i++) {
            unlockedEntries.add(allEntries.get(i).getId());
        }

        currentIndex = targetIndex;
        timeAccumulator = 0.0;
        speedMultiplier = 1.0;

        if (currentIndex >= allEntries.size()) {
            remainingSeconds = 0;
            updateBossBarComplete();
        } else {
            remainingSeconds = allEntries.get(currentIndex).getBaseSeconds();
            updateBossBar();
        }

        // Avoid re-sending locked-section spam on new path
        notifiedLockedSections.clear();
        benefitMessageSent.clear();

        // Don't re-show the intro once admins have repositioned progress
        if (currentIndex > 0) {
            firstStepsIntroShown = true;
        }
    }

    public boolean isEntryUnlocked(ProgressEntry entry) {
        return unlockedEntries.contains(entry.getId());
    }

    /**
     * Items NOT in the progression list are always allowed.
     */
    public boolean isItemUnlocked(org.bukkit.Material mat) {
        boolean found = false;
        for (ProgressEntry e : allEntries) {
            if (e.getKind() == ProgressEntry.EntryKind.ITEM &&
                e.getMaterial() == mat) {
                found = true;
                if (unlockedEntries.contains(e.getId())) {
                    return true;
                }
            }
        }
        // No progression entry for this item -> not managed -> allowed
        if (!found) return true;
        return false;
    }

    /**
     * Enchantments NOT in the progression list are always allowed.
     * For managed enchants, a level is allowed only if that exact level
     * has an unlocked entry.
     */
    public boolean isEnchantUnlocked(org.bukkit.enchantments.Enchantment ench, int level) {
        boolean anyForEnchant = false;
        for (ProgressEntry e : allEntries) {
            if (e.getKind() == ProgressEntry.EntryKind.ENCHANT &&
                e.getEnchantment().equals(ench)) {
                anyForEnchant = true;
                if (e.getEnchantLevel() == level &&
                    unlockedEntries.contains(e.getId())) {
                    return true;
                }
            }
        }
        if (!anyForEnchant) {
            // This enchantment isn't governed by SBPC; always allowed
            return true;
        }
        return false;
    }

    public ProgressEntry getCurrentEntry() {
        if (currentIndex < 0 || currentIndex >= allEntries.size()) return null;
        return allEntries.get(currentIndex);
    }

    public SectionDefinition getCurrentSection() {
        ProgressEntry entry = getCurrentEntry();
        if (entry == null) return null;
        String secId = entry.getSectionId();
        for (SectionDefinition s : sections) {
            if (s.getId().equals(secId)) return s;
        }
        return null;
    }

    public boolean isSectionCompleted(String sectionId) {
        boolean found = false;
        for (ProgressEntry e : allEntries) {
            if (e.getSectionId().equals(sectionId)) {
                found = true;
                if (!unlockedEntries.contains(e.getId())) {
                    return false;
                }
            }
        }
        // If the section has no entries in the progression list, treat as completed
        return found;
    }
    public boolean isEntryUnlocked(String entryId) {
        return unlockedEntries.contains(entryId);
    }

    /**
     * Returns true if any CUSTOM_ITEM or MECHANIC with the given customKey
     * is unlocked for this player.
     */
    public boolean isCustomUnlocked(String customKey) {
        if (customKey == null || customKey.isEmpty()) return false;
        String keyLower = customKey.toLowerCase();
        for (ProgressEntry e : allEntries) {
            EntryKind kind = e.getKind();
            if (kind != EntryKind.CUSTOM_ITEM && kind != EntryKind.MECHANIC) continue;
            String ck = e.getCustomKey();
            if (ck == null) continue;
            if (!ck.equalsIgnoreCase(keyLower)) continue;
            if (unlockedEntries.contains(e.getId())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyUnlocked() {
        return !unlockedEntries.isEmpty();
    }

    public boolean isFirstStepsIntroShown() {
        return firstStepsIntroShown;
    }

    public void setFirstStepsIntroShown(boolean value) {
        this.firstStepsIntroShown = value;
    }


    // ------------------------------------------------------------------------
    // Core ticking
    // ------------------------------------------------------------------------

    public void tick(double seconds) {
        ProgressEntry entry = getCurrentEntry();
        if (entry == null) {
            updateBossBarComplete();
            return;
        }

        // Accumulate fractional speed instead of rounding each tick
        double amount = seconds * adminSpeedMultiplier * speedMultiplier;
        timeAccumulator += amount;

        int whole = (int) Math.floor(timeAccumulator);
        if (whole <= 0) {
            // not enough accumulated to reduce a full second yet
            updateBossBar();
            return;
        }

        timeAccumulator -= whole;
        remainingSeconds -= whole;

        if (remainingSeconds <= 0) {
            unlockCurrentEntry(entry);
            // reset accumulator on entry completion
            timeAccumulator = 0.0;
            advanceToNextEntry();
        }

        updateBossBar();
    }
    private void unlockCurrentEntry(ProgressEntry entry) {
        unlockedEntries.add(entry.getId());

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("item", entry.getDisplayName());
            String msg = MessageConfig.format("item-unlocked", ph);
            player.sendMessage(msg);
        }

        UnlockItemEvent unlockEvent = new UnlockItemEvent(Bukkit.getPlayer(uuid), entry);
        Bukkit.getPluginManager().callEvent(unlockEvent);
    }



    private void advanceToNextEntry() {
        ProgressEntry old = getCurrentEntry();
        currentIndex++;
        if (currentIndex >= allEntries.size()) {
            remainingSeconds = 0;
            updateBossBarComplete();
            return;
        }

        ProgressEntry next = getCurrentEntry();
        remainingSeconds = next.getBaseSeconds();

        SectionDefinition newSection = getCurrentSection();

        if (old != null && newSection != null && !old.getSectionId().equals(newSection.getId())) {
            // NEW: only reset multiplier when we actually change sections
            speedMultiplier = 1.0;
            timeAccumulator = 0.0;
            benefitMessageSent.remove(newSection.getId());

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("section", newSection.getDisplayName());
                player.sendMessage(MessageConfig.format("new-section-title", ph));
                player.sendMessage(MessageConfig.get("new-section-reset"));
                player.sendMessage(MessageConfig.get("new-section-collect"));

                if (newSection.shouldBroadcastUnlock()) {
                    // NEW: global broadcast with per-section color
                    String colorCode = newSection.getColorCode();
                    String coloredName = MessageConfig.colorize(
                            (colorCode != null ? colorCode : "") + newSection.getDisplayName()
                    );
                    Map<String, String> phBroadcast = new HashMap<>();
                    phBroadcast.put("player", player.getName());
                    phBroadcast.put("section", newSection.getDisplayName());
                    phBroadcast.put("section-colored", coloredName);
                    String broadcast = MessageConfig.format("section-unlock-broadcast", phBroadcast);
                    Bukkit.broadcastMessage(broadcast);
                }
            }

            NextSectionEvent ev = new NextSectionEvent(player, newSection);
            Bukkit.getPluginManager().callEvent(ev);
        }
    }


    // ------------------------------------------------------------------------
    // Bonuses
    // ------------------------------------------------------------------------

    public void applyRelatedMaterialBonus(String sectionId, String materialName) {
        if (isSectionCompleted(sectionId)) return;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        double bonusPercent = relatedBonusPercentPerHit;
        int skipSeconds = relatedBonusSkipSeconds;

        speedMultiplier += bonusPercent / 100.0;
        remainingSeconds = Math.max(0, remainingSeconds - skipSeconds);

        if (!benefitMessageSent.contains(sectionId)) {
            benefitMessageSent.add(sectionId);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("material", materialName);
            placeholders.put("seconds", Integer.toString(skipSeconds));
            placeholders.put("percent", Double.toString(bonusPercent));
            player.sendMessage(MessageConfig.format("related-bonus", placeholders));
        }
    }


    /**
     * External hook: additional time-reducing events and specials can call this.
     */
    public void applyExternalTimeSkip(int skipSeconds, double percentSpeedIncrease, String sourceDescription) {
        SectionDefinition sec = getCurrentSection();
        if (sec == null) return;

        if (percentSpeedIncrease != 0) {
            speedMultiplier += percentSpeedIncrease / 100.0;
        }
        if (skipSeconds != 0) {
            remainingSeconds = Math.max(0, remainingSeconds - skipSeconds);
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && sourceDescription != null && !sourceDescription.isEmpty()) {
            /*player.sendMessage("§aYour progression in §e" + sec.getDisplayName()
                    + "§a advanced due to: §f" + sourceDescription); too spammy */
        }
    }

    public void notifyLockedSectionOnce(String sectionId, String sectionName) {
        if (notifiedLockedSections.contains(sectionId)) return;
        notifiedLockedSections.add(sectionId);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("section", sectionName);
            player.sendMessage(MessageConfig.format("locked-section", ph));
        }
    }

    /**
     * Change the per-player related-material bonus.
     * This is used by the SBCPSpecials hook plugin (e.g. after Iron Pickaxe).
     */
    public void setRelatedBonus(double bonusPercent, int skipSeconds) {
        if (bonusPercent < 0) {
            bonusPercent = 0;
        }
        if (skipSeconds < 0) {
            skipSeconds = 0;
        }
        this.relatedBonusPercentPerHit = bonusPercent;
        this.relatedBonusSkipSeconds = skipSeconds;
    }

    /**
     * Auto-complete the player's *current* section, unlocking all remaining
     * entries in it and advancing into the next section.
     * Used by specials like "having Pale Log auto completes this section".
     */
    public void completeCurrentSection() {
        SectionDefinition sec = getCurrentSection();
        if (sec == null) return;

        String sectionId = sec.getId();
        ProgressEntry current = getCurrentEntry();

        while (current != null && sectionId.equals(current.getSectionId())) {
            if (!unlockedEntries.contains(current.getId())) {
                unlockCurrentEntry(current);
            }
            advanceToNextEntry();
            current = getCurrentEntry();
        }
    }

    
    // ------------------------------------------------------------------------
    // BossBar
    // ------------------------------------------------------------------------

    public void updateBossBar() {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        ProgressEntry entry = getCurrentEntry();
        if (entry == null) {
            updateBossBarComplete();
            return;
        }

        double progress = 1.0 - (remainingSeconds / (double) entry.getBaseSeconds());
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;

        bossBar.setProgress(progress);
        Map<String, String> ph = new HashMap<>();
        ph.put("entry", entry.getDisplayName());
        ph.put("seconds", Integer.toString(remainingSeconds));
        bossBar.setTitle(MessageConfig.format("bossbar-active", ph));
        bossBar.addPlayer(player);

    }

    public void updateBossBarComplete() {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        bossBar.setProgress(1.0);
        bossBar.setTitle(MessageConfig.get("bossbar-complete"));
        bossBar.addPlayer(player);

    }
}
