// src/me/BaddCamden/SBPC/config/MessageConfig.java
package me.BaddCamden.SBPC.config;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

public class MessageConfig {

    private static final Map<String, String> messages = new HashMap<>();

    /**
     * Loads all configurable messages from the plugin config into memory.
     * Call this when the plugin starts or after reloading configuration.
     */
    public static void load(FileConfiguration cfg) {
        messages.clear();

        // Core join / server messages
        put(cfg, "server-closed-join", "messages.server-closed-join",
                "&cThe server is currently closed. There is currently no active session.");

        // Progression messages
        put(cfg, "item-unlocked", "messages.item-unlocked",
                "&aUnlocked: &e{item}");
        put(cfg, "new-section-title", "messages.new-section-title",
                "&6You have reached a new section: &e{section}");
        put(cfg, "new-section-reset", "messages.new-section-reset",
                "&7Your progress speed has been reset.");
        put(cfg, "new-section-collect", "messages.new-section-collect",
                "&7Collect related materials to speed up this section.");

        put(cfg, "related-bonus", "messages.related-bonus",
                "&aYou have collected {material}. This section skips {seconds} seconds and speeds up by {percent}% each time!");

        put(cfg, "locked-section", "messages.locked-section",
                "&cYou have not unlocked the section &e{section}&c yet. You can still collect these blocks, but you will not receive any time benefits for doing so.");

        put(cfg, "all-sections-complete", "messages.all-sections-complete",
                "&aYou have completed all progression sections.");

        put(cfg, "current-section-header", "messages.current-section-header",
                "&6Current section: &e{section}");
        put(cfg, "current-section-related-header", "messages.current-section-related-header",
                "&7Related materials:");
        put(cfg, "current-section-related-entry", "messages.current-section-related-entry",
                " &8- &b{material}");
        put(cfg, "current-section-special", "messages.current-section-special",
                "&dSPECIAL: &f{special}");
        put(cfg, "current-section-no-special", "messages.current-section-no-special",
                "&dNo special criteria configured for this section.");

        put(cfg, "cannot-use-item", "messages.cannot-use-item",
                "&cYou have not unlocked that item or its enchantments yet.");
        put(cfg, "enchant-cancelled", "messages.enchant-cancelled",
                "&cYou have not unlocked any of the enchantments rolled; enchantment cancelled.");

        put(cfg, "first-steps-intro", "messages.first-steps-intro",
                "&aSection &e{section} &ahas begun! Chop down trees to speed up the timer's progress!");

        put(cfg, "section-unlock-broadcast", "messages.section-unlock-broadcast",
                "&e{player} &7has unlocked the {section-colored}&7 section!");

        // Bossbar titles
        put(cfg, "bossbar-active", "messages.bossbar-active",
                "&e{entry}&7 - &b{seconds}s remaining");
        put(cfg, "bossbar-complete", "messages.bossbar-complete",
                "&aAll progression unlocked!");

        // Generic command / admin messages
        put(cfg, "no-permission", "messages.no-permission",
                "&cYou do not have permission.");
        put(cfg, "config-reloaded", "messages.config-reloaded",
                "&aSBPC config reloaded.");
        put(cfg, "must-be-player", "messages.must-be-player",
                "&cOnly players may run this command.");
    }

    /**
     * Reads a message from the config with a default value and stores it under the given key.
     */
    private static void put(FileConfiguration cfg, String key, String path, String def) {
        String value = cfg.getString(path, def);
        if (value == null) value = def;
        messages.put(key, colorize(value));
    }

    /**
     * Retrieves a previously loaded message by key, returning an empty string if missing.
     */
    public static String get(String key) {
        String v = messages.get(key);
        return v == null ? "" : v;
    }

    /**
     * Retrieves and performs simple placeholder replacement on a message.
     */
    public static String format(String key, Map<String, String> placeholders) {
        String base = get(key);
        if (base.isEmpty() || placeholders == null) return base;
        String out = base;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    /**
     * Replace legacy ampersand colour codes with the actual section character.
     * This keeps all colour configuration in config.yml and avoids using
     * the Bukkit colour API at runtime.
     */
    public static String colorize(String input) {
        if (input == null) return "";
        return input.replace('&', '�');
    }
}
