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

    /**
     * Constructs a new progression entry describing one unlockable.
     *
     * @param id           unique identifier for the entry
     * @param sectionId    section this entry belongs to
     * @param displayName  player-facing name used in boss bars/messages
     * @param kind         entry category (item, enchant, custom, mechanic)
     * @param material     material tied to ITEM entries (null otherwise)
     * @param enchantment  enchantment tied to ENCHANT entries (null otherwise)
     * @param enchantLevel enchantment level for ENCHANT entries
     * @param customKey    arbitrary key for CUSTOM_ITEM / MECHANIC entries
     * @param baseSeconds  base time required before the entry unlocks
     */
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

    /**
     * Identifier used for persistence and API access.
     */
    public String getId() {
        return id;
    }

    /**
     * Section identifier this entry belongs to.
     */
    public String getSectionId() {
        return sectionId;
    }

    /**
     * Player-friendly display name of the entry.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Kind/category of this entry.
     */
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

    /**
     * Material tied to ITEM unlocks, or null for other entry types.
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Enchantment tied to ENCHANT unlocks, or null otherwise.
     */
    public Enchantment getEnchantment() {
        return enchantment;
    }

    /**
     * Level associated with an ENCHANT entry.
     */
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

    /**
     * Base time required to complete this entry before any bonuses are applied.
     */
    public int getBaseSeconds() {
        return baseSeconds;
    }
}
