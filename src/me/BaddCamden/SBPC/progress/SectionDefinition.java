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
    

    /**
     * Logical type for this section (e.g. "NORMAL", "SPECIAL", "BOSS", etc.).
     * Used by SBPCSpecials and other hook plugins.
     */
    private final String type;

    public SectionDefinition(String id,
                             String displayName,
                             List<ProgressEntry> entries,
                             Set<Material> relatedMaterials,
                             String specialInfo,
                             String colorCode,
                             String type) {
        this.id = id;
        this.displayName = displayName;
        this.entries = Collections.unmodifiableList(entries);
        this.relatedMaterials = Collections.unmodifiableSet(relatedMaterials);
        this.specialInfo = specialInfo == null ? "" : specialInfo;
        this.colorCode = colorCode == null ? "&e" : colorCode;
        // If no type is provided, default to NORMAL.
        // Infer section type if not explicitly stored elsewhere:
        // If this section has special-info text, treat it as SPECIAL, else NORMAL.
        String inferredType;
        if (this.specialInfo != null && !this.specialInfo.isEmpty()) {
            inferredType = "SPECIAL";
        } else {
            inferredType = "NORMAL";
        }
        this.type = inferredType;

    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<ProgressEntry> getEntries() {
        return entries;
    }

    public Set<Material> getRelatedMaterials() {
        return relatedMaterials;
    }

    public String getSpecialInfo() {
        return specialInfo;
    }

    public String getColorCode() {
        return colorCode;
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
