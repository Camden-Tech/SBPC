package me.BaddCamden.SBPC.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.BaddCamden.SBPC.progress.SectionDefinition;

public class SpecialCriteriaFulfilledEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SectionDefinition section;
    private final String description;

    public SpecialCriteriaFulfilledEvent(Player player,
                                         SectionDefinition section,
                                         String description) {
        this.player = player;
        this.section = section;
        this.description = description;
    }

    public Player getPlayer() { return player; }
    public SectionDefinition getSection() { return section; }
    public String getDescription() { return description; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
