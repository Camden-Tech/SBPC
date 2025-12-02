package me.BaddCamden.SBPC.progress;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

/**
 * Single progression entry (item, enchant, custom item, or mechanic).
 */
public class ProgressEntry {

    public enum EntryKind {
        ITEM,         // vanilla items / blocks (Material)
        ENCHANT,      // vanilla enchants (Enchant + level)
        CUSTOM_ITEM,  // fully custom items (SBPC never enforces; hook plugin does)
        MECHANIC      // abstract mechanics, like "PlayerKillUnlock"
    }

    private final String id;
    private final String sectionId;
    private final String displayName;

    private final EntryKind kind;

    // For ITEM
    private final Material material;

    // For ENCHANT
    private final Enchantment enchantment;
    private final int enchantLevel;

    // For CUSTOM_ITEM / MECHANIC
    // Arbitrary identifier; SBPC never interprets this, it just uses it as a key.
    private final String customKey;

    private final int baseSeconds;

    public ProgressEntry(String id,
                         String sectionId,
                         String displayName,
                         EntryKind kind,
                         Material material,
                         Enchantment enchantment,
                         int enchantLevel,
                         String customKey,
                         int baseSeconds) {
        this.id = id;
        this.sectionId = sectionId;
        this.displayName = displayName;
        this.kind = kind;
        this.material = material;
        this.enchantment = enchantment;
        this.enchantLevel = enchantLevel;
        this.customKey = customKey;
        this.baseSeconds = baseSeconds;
    }

    public String getId() {
        return id;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntryKind getKind() {
        return kind;
    }

    /**
     * Backwards-compat alias in case any old code still calls getType().
     */
    @Deprecated
    public EntryKind getType() {
        return kind;
    }

    public Material getMaterial() {
        return material;
    }

    public Enchantment getEnchantment() {
        return enchantment;
    }

    public int getEnchantLevel() {
        return enchantLevel;
    }

    /**
     * Returns the configured custom key for CUSTOM_ITEM or MECHANIC entries.
     * This is an arbitrary string (e.g. "tracking_compass", "player_kill_unlock")
     * that only the hook plugin understands.
     */
    public String getCustomKey() {
        return customKey;
    }

    public int getBaseSeconds() {
        return baseSeconds;
    }
}
