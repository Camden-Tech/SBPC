package me.BaddCamden.SBPC.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import me.BaddCamden.SBPC.SBPCPlugin;
import me.BaddCamden.SBPC.config.MessageConfig;
import me.BaddCamden.SBPC.progress.ProgressEntry;
import me.BaddCamden.SBPC.progress.SectionDefinition;

public class SbpcCommand implements CommandExecutor, TabCompleter {

    private final SBPCPlugin plugin;

    public SbpcCommand(SBPCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sbpc.admin")) {
            sender.sendMessage(MessageConfig.get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            return false;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reloadconfig":
                handleReload(sender);
                return true;
            case "jump":
                return handleJump(sender, args);
            case "speed":
                return handleSpeed(sender, args);
            case "config":
                return handleConfig(sender, args);
            default:
                return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {
        if (!sender.hasPermission("sbpc.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterPrefix(Arrays.asList("reloadConfig", "jump", "speed", "config"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("jump")) {
            if (args.length == 2) {
                return filterPrefix(Arrays.asList("section", "entry"), args[1]);
            }
            if (args.length == 3) {
                return filterPrefix(getPlayerNames(), args[2]);
            }
            if (args.length == 4) {
                if (args[1].equalsIgnoreCase("section")) {
                    return filterPrefix(getSectionIds(), args[3]);
                }
                if (args[1].equalsIgnoreCase("entry")) {
                    return filterPrefix(getEntryIds(), args[3]);
                }
            }
        }

        if (sub.equals("speed")) {
            if (args.length == 2) {
                return filterPrefix(Arrays.asList("player", "global"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("player")) {
                return filterPrefix(getPlayerNames(), args[2]);
            }
        }

        if (sub.equals("config")) {
            if (args.length == 2) {
                return filterPrefix(Arrays.asList("section", "entry"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("section")) {
                return filterPrefix(getSectionIds(), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("section")) {
                return filterPrefix(Arrays.asList("set", "add-related", "remove-related"), args[3]);
            }
            if (args.length == 5 && args[1].equalsIgnoreCase("section") && args[3].equalsIgnoreCase("set")) {
                return filterPrefix(Arrays.asList("display-name", "color", "special-info"), args[4]);
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("entry")) {
                return filterPrefix(getSectionIds(), args[2]);
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("entry")) {
                return filterPrefix(getEntryIdsForSection(args[2]), args[3]);
            }
            if (args.length == 5 && args[1].equalsIgnoreCase("entry")) {
                return filterPrefix(Collections.singletonList("set"), args[4]);
            }
            if (args.length == 6 && args[1].equalsIgnoreCase("entry") && args[4].equalsIgnoreCase("set")) {
                return filterPrefix(Arrays.asList("name", "seconds", "type", "material", "custom-key", "enchant-key", "level"), args[5]);
            }
        }

        return Collections.emptyList();
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.getLogger().info("Reloading SBPC config and progression.");
        plugin.getProgressManager().saveAll();
        plugin.loadConfigValues();
        plugin.getProgressManager().reloadFromConfig();

        sender.sendMessage(MessageConfig.get("config-reloaded"));
    }

    private boolean handleJump(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§eUsage: /sbpc jump <section|entry> <player> <id>");
            return false;
        }

        String kind = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage("§cUnknown player: " + args[2]);
            return true;
        }

        boolean success;
        if (kind.equals("section")) {
            success = plugin.getProgressManager().jumpPlayerToSection(target.getUniqueId(), args[3]);
        } else if (kind.equals("entry")) {
            success = plugin.getProgressManager().jumpPlayerToEntry(target.getUniqueId(), args[3]);
        } else {
            sender.sendMessage("§cJump target must be section or entry.");
            return false;
        }

        if (!success) {
            sender.sendMessage("§cNo matching " + kind + " found for id '" + args[3] + "'.");
            return false;
        }

        Player online = target.getPlayer();
        if (online != null) {
            plugin.getProgressManager().ensureBossBar(online);
            online.sendMessage("§aYour progression position was updated by an admin.");
        }

        sender.sendMessage("§aMoved " + target.getName() + " to " + kind + " " + args[3] + ".");
        return true;
    }

    private boolean handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /sbpc speed <player|global> <target> <multiplier>");
            return false;
        }

        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("player")) {
            if (args.length < 4) {
                sender.sendMessage("§eUsage: /sbpc speed player <player> <multiplier>");
                return false;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (target == null || target.getUniqueId() == null) {
                sender.sendMessage("§cUnknown player: " + args[2]);
                return true;
            }
            Double mult = parseDouble(args[3], sender, "§cMultiplier must be a number.");
            if (mult == null) {
                return false;
            }

            plugin.getProgressManager().setPlayerSpeed(target.getUniqueId(), mult);
            plugin.getProgressManager().saveAll();
            Player online = target.getPlayer();
            if (online != null) {
                online.sendMessage("§aYour progression speed multiplier is now " + mult + "x.");
            }
            sender.sendMessage("§aSet " + target.getName() + " speed multiplier to " + mult + "x.");
            return true;
        }

        if (mode.equals("global")) {
            Double mult = parseDouble(args[2], sender, "§cMultiplier must be a number.");
            if (mult == null) {
                return false;
            }

            plugin.getProgressManager().setGlobalSpeedMultiplier(mult);
            FileConfiguration cfg = plugin.getConfig();
            cfg.set("progression.global-speed-multiplier", mult);
            plugin.saveConfig();
            sender.sendMessage("§aSet global progression speed to " + mult + "x.");
            return true;
        }

        sender.sendMessage("§cSpeed target must be player or global.");
        return false;
    }

    private boolean handleConfig(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /sbpc config <section|entry> ...");
            return false;
        }

        String kind = args[1].toLowerCase(Locale.ROOT);
        if (kind.equals("section")) {
            return handleSectionConfig(sender, args);
        }
        if (kind.equals("entry")) {
            return handleEntryConfig(sender, args);
        }

        sender.sendMessage("§cConfig target must be section or entry.");
        return false;
    }

    private boolean handleSectionConfig(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage("§eUsage: /sbpc config section <sectionId> <set|add-related|remove-related> ...");
            return false;
        }

        String sectionId = args[2];
        String action = args[3].toLowerCase(Locale.ROOT);
        FileConfiguration cfg = plugin.getConfig();
        String basePath = "progression.sections." + sectionId;
        if (!cfg.isConfigurationSection(basePath)) {
            sender.sendMessage("§cUnknown section id: " + sectionId);
            return false;
        }

        switch (action) {
            case "set": {
                if (args.length < 6) {
                    sender.sendMessage("§eUsage: /sbpc config section " + sectionId + " set <display-name|color|special-info> <value>");
                    return false;
                }
                String field = args[4].toLowerCase(Locale.ROOT);
                String value = String.join(" ", Arrays.copyOfRange(args, 5, args.length));
                if (field.equals("display-name")) {
                    cfg.set(basePath + ".display-name", value);
                } else if (field.equals("color")) {
                    cfg.set(basePath + ".color", value);
                } else if (field.equals("special-info")) {
                    cfg.set(basePath + ".special-info", value);
                } else {
                    sender.sendMessage("§cUnknown section field: " + field);
                    return false;
                }
                break;
            }
            case "add-related": {
                Material mat = Material.matchMaterial(args[4]);
                if (mat == null) {
                    sender.sendMessage("§cUnknown material: " + args[4]);
                    return false;
                }
                List<String> mats = new ArrayList<>(cfg.getStringList(basePath + ".related-materials"));
                if (!mats.contains(mat.name())) {
                    mats.add(mat.name());
                    cfg.set(basePath + ".related-materials", mats);
                }
                break;
            }
            case "remove-related": {
                Material mat = Material.matchMaterial(args[4]);
                if (mat == null) {
                    sender.sendMessage("§cUnknown material: " + args[4]);
                    return false;
                }
                List<String> mats = new ArrayList<>(cfg.getStringList(basePath + ".related-materials"));
                mats.removeIf(s -> s.equalsIgnoreCase(mat.name()));
                cfg.set(basePath + ".related-materials", mats);
                break;
            }
            default:
                sender.sendMessage("§cUnknown section action: " + action);
                return false;
        }

        applyConfigReload(sender, "section " + sectionId);
        return true;
    }

    private boolean handleEntryConfig(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage("§eUsage: /sbpc config entry <sectionId> <entryId> set <field> <value>");
            return false;
        }

        String sectionId = args[2];
        String entryId = args[3];
        String action = args[4].toLowerCase(Locale.ROOT);

        FileConfiguration cfg = plugin.getConfig();
        String basePath = "progression.sections." + sectionId + ".entries." + entryId;
        if (!cfg.isConfigurationSection(basePath)) {
            sender.sendMessage("§cUnknown entry id: " + entryId + " in section " + sectionId);
            return false;
        }

        if (!action.equals("set")) {
            sender.sendMessage("§cEntry action must be set.");
            return false;
        }

        String field = args[5].toLowerCase(Locale.ROOT);
        if (args.length < 7) {
            sender.sendMessage("§eUsage: /sbpc config entry " + sectionId + " " + entryId + " set <field> <value>");
            return false;
        }
        String value = String.join(" ", Arrays.copyOfRange(args, 6, args.length));

        switch (field) {
            case "name":
                cfg.set(basePath + ".name", value);
                break;
            case "seconds":
                Integer seconds = parseInteger(value, sender, "§cSeconds must be a number.");
                if (seconds == null) {
                    return false;
                }
                cfg.set(basePath + ".seconds", seconds);
                break;
            case "type":
                cfg.set(basePath + ".type", value.toUpperCase(Locale.ROOT));
                break;
            case "material":
                if (Material.matchMaterial(value) == null) {
                    sender.sendMessage("§cUnknown material: " + value);
                    return false;
                }
                cfg.set(basePath + ".material", value.toUpperCase(Locale.ROOT));
                break;
            case "custom-key":
                cfg.set(basePath + ".custom-key", value);
                break;
            case "enchant-key":
                cfg.set(basePath + ".enchant-key", value.toLowerCase(Locale.ROOT));
                break;
            case "level":
                Integer level = parseInteger(value, sender, "§cLevel must be a number.");
                if (level == null) {
                    return false;
                }
                cfg.set(basePath + ".level", level);
                break;
            default:
                sender.sendMessage("§cUnknown entry field: " + field);
                return false;
        }

        applyConfigReload(sender, "entry " + entryId);
        return true;
    }

    private void applyConfigReload(CommandSender sender, String changedTarget) {
        plugin.getProgressManager().saveAll();
        plugin.saveConfig();
        plugin.loadConfigValues();
        plugin.getProgressManager().reloadFromConfig();
        sender.sendMessage("§aUpdated " + changedTarget + " and reloaded progression settings.");
    }

    private List<String> filterPrefix(List<String> options, String token) {
        if (token == null) return options;
        String lower = token.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }

    private List<String> getPlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> getSectionIds() {
        List<SectionDefinition> sections = plugin.getProgressManager().getSections();
        List<String> ids = new ArrayList<>();
        for (SectionDefinition s : sections) {
            ids.add(s.getId());
        }
        return ids;
    }

    private List<String> getEntryIds() {
        List<String> ids = new ArrayList<>();
        for (ProgressEntry e : plugin.getProgressManager().getAllEntries()) {
            ids.add(e.getId());
        }
        return ids;
    }

    private List<String> getEntryIdsForSection(String sectionId) {
        List<String> ids = new ArrayList<>();
        for (ProgressEntry e : plugin.getProgressManager().getAllEntries()) {
            if (e.getSectionId().equalsIgnoreCase(sectionId)) {
                ids.add(e.getId());
            }
        }
        return ids.isEmpty() ? getEntryIds() : ids;
    }

    private Double parseDouble(String raw, CommandSender sender, String errorMessage) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(errorMessage);
            return null;
        }
    }

    private Integer parseInteger(String raw, CommandSender sender, String errorMessage) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(errorMessage);
            return null;
        }
    }

}
