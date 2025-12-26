package me.BaddCamden.SBPC.progress;

import me.BaddCamden.SBPC.config.MessageConfig;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable description of one progression section.
 * Holds its entries, related materials, colour, and special info text.
 */
public class SectionDefinition {

    private final String id;
    private final String displayName;
    private final List<ProgressEntry> entries;
    private final Set<Material> relatedMaterials;
    private final String specialInfo;
    private final String colorCode;
    private final boolean broadcastUnlock;
    

    /**
     * Logical type for this section (e.g. "NORMAL", "SPECIAL", "BOSS", etc.).
     * Used by SBPCSpecials and other hook plugins.
     */
    private final String type;

    /**
     * Creates an immutable description of a progression section.
     *
     * @param id               unique identifier used in config and APIs
     * @param displayName      player-facing section name
     * @param entries          ordered list of entries within the section
     * @param relatedMaterials materials that speed up progress in this section
     * @param specialInfo      optional special criteria description
     * @param colorCode        colour code prefix for display name formatting
     * @param type             logical section type (NORMAL, SPECIAL, etc.)
     * @param broadcastUnlock  whether to broadcast when the section is unlocked
     */
    public SectionDefinition(String id,
                             String displayName,
                             List<ProgressEntry> entries,
                             Set<Material> relatedMaterials,
                             String specialInfo,
                             String colorCode,
                             String type,
                             boolean broadcastUnlock) {
        this.id = id;
        this.displayName = displayName;
        this.entries = Collections.unmodifiableList(entries);
        this.relatedMaterials = Collections.unmodifiableSet(relatedMaterials);
        this.specialInfo = specialInfo == null ? "" : specialInfo;
        this.colorCode = colorCode == null ? "&e" : colorCode;
        this.broadcastUnlock = broadcastUnlock;
        // If no type is provided, default to NORMAL.
        // Infer section type if not explicitly stored elsewhere:
        // If this section has special-info text, treat it as SPECIAL, else NORMAL.
        String inferredType;
        if (this.specialInfo != null && !this.specialInfo.isEmpty()) {
            inferredType = "SPECIAL";
        } else {
            inferredType = "NORMAL";
        }
        this.type = (type != null && !type.isEmpty()) ? type : inferredType;

    }

    /**
     * Unique identifier for this section.
     */
    public String getId() {
        return id;
    }

    /**
     * Player-facing display name (without colour codes applied).
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Ordered list of entries contained in this section.
     */
    public List<ProgressEntry> getEntries() {
        return entries;
    }

    /**
     * Set of materials that provide speed bonuses for this section.
     */
    public Set<Material> getRelatedMaterials() {
        return relatedMaterials;
    }

    /**
     * Optional special criteria text displayed to players.
     */
    public String getSpecialInfo() {
        return specialInfo;
    }

    /**
     * Colour code prefix configured for this section.
     */
    public String getColorCode() {
        return colorCode;
    }

    /**
     * Whether unlocking this section should be broadcast to the server.
     */
    public boolean shouldBroadcastUnlock() {
        return broadcastUnlock;
    }

    /**
     * Logical type string for this section (e.g. "NORMAL", "SPECIAL", "BOSS").
     */
    public String getType() {
        return type;
    }

    /**
     * Display name with the section colour code applied and colourized.
     */
    public String getColoredName() {
        return MessageConfig.colorize(colorCode + displayName);
    }
}
