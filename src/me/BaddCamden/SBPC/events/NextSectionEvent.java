package me.BaddCamden.SBPC.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.BaddCamden.SBPC.progress.SectionDefinition;

public class NextSectionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SectionDefinition newSection;

    public NextSectionEvent(Player player, SectionDefinition newSection) {
        this.player = player;
        this.newSection = newSection;
    }

    public Player getPlayer() { return player; }
    public SectionDefinition getNewSection() { return newSection; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
