package me.BaddCamden.SBPC.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import me.BaddCamden.SBPC.SBPCPlugin;
import me.BaddCamden.SBPC.config.MessageConfig;

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

        if (args.length == 1 && args[0].equalsIgnoreCase("reloadConfig")) {
            // Only reload the static config (session + progression layout).
            // Per-player progress stays entirely in memory and is only saved/loaded
            // on plugin enable/disable.
            plugin.reloadConfig();
            plugin.getLogger().info("Reloading SBPC config and progression.");
            plugin.loadConfigValues();
            plugin.getProgressManager().reloadFromConfig();

            sender.sendMessage(MessageConfig.get("config-reloaded"));
            return true;
        }

        sender.sendMessage("§eUsage: /sbpc reloadConfig");
        return true;
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
            List<String> suggestions = new ArrayList<>();
            if ("reloadConfig".toLowerCase().startsWith(args[0].toLowerCase())) {
                suggestions.add("reloadConfig");
            }
            return suggestions;
        }

        return Collections.emptyList();
    }
}
