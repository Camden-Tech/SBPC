package me.BaddCamden.SBPC.commands;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.BaddCamden.SBPC.SBPCPlugin;

public class CurrentTimeSkipCommand implements CommandExecutor, TabCompleter {

    private final SBPCPlugin plugin;

    public CurrentTimeSkipCommand(SBPCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        plugin.getProgressManager().sendCurrentTimeSkipInfo(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {
        // No arguments for /currenttimeskip, so no suggestions
        return Collections.emptyList();
    }
}
