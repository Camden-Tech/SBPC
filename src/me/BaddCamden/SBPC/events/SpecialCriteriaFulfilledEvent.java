package me.BaddCamden.SBPC.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.BaddCamden.SBPC.progress.SectionDefinition;

/**
 * Fired when a player's special section criteria is met (e.g., custom unlock rules).
 * Provides the section context and an optional description of the criteria.
 */
public class SpecialCriteriaFulfilledEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SectionDefinition section;
    private final String description;

    /**
     * Creates a new special-criteria event.
     *
     * @param player      player who satisfied the criteria
     * @param section     section whose criteria was fulfilled
     * @param description human-readable description of the criteria
     */
    public SpecialCriteriaFulfilledEvent(Player player,
                                         SectionDefinition section,
                                         String description) {
        this.player = player;
        this.section = section;
        this.description = description;
    }

    /**
     * Player who completed the special criteria.
     */
    public Player getPlayer() { return player; }

    /**
     * Section associated with the fulfilled criteria.
     */
    public SectionDefinition getSection() { return section; }

    /**
     * Human-friendly description of the criteria that was met.
     */
    public String getDescription() { return description; }

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
