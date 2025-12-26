package me.BaddCamden.SBPC.commands;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.BaddCamden.SBPC.SBPCPlugin;

/**
 * Implements /currenttimeskip for players to view their current section bonuses.
 */
public class CurrentTimeSkipCommand implements CommandExecutor, TabCompleter {

    private final SBPCPlugin plugin;

    /**
     * Creates the command handler bound to the main plugin instance.
     */
    public CurrentTimeSkipCommand(SBPCPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    /**
     * Sends the caller a breakdown of related materials for their current section.
     */
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return false;
        }
        Player player = (Player) sender;
        plugin.getProgressManager().sendCurrentTimeSkipInfo(player);
        return true;
    }

    @Override
    /**
     * No tab completion is provided because the command takes no arguments.
     */
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {
        return Collections.emptyList();
    }
}
