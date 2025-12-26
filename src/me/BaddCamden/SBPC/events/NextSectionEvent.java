package me.BaddCamden.SBPC.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.BaddCamden.SBPC.progress.SectionDefinition;

/**
 * Fired when a player completes their current section and moves into the next one.
 * Hook plugins can listen for this to react to section transitions.
 */
public class NextSectionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SectionDefinition newSection;

    /**
     * Constructs a new section change event for the given player.
     *
     * @param player     player who advanced
     * @param newSection section the player entered
     */
    public NextSectionEvent(Player player, SectionDefinition newSection) {
        this.player = player;
        this.newSection = newSection;
    }

    /**
     * Player that progressed into the next section.
     */
    public Player getPlayer() { return player; }

    /**
     * Definition of the section the player has just entered.
     */
    public SectionDefinition getNewSection() { return newSection; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Required Bukkit boilerplate for event handler registration.
     */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
