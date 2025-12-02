package me.BaddCamden.SBPC.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.BaddCamden.SBPC.progress.SectionDefinition;

public class RelatedResourceTimeSkipEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SectionDefinition section;
    private final Material material; // may be null for external events

    public RelatedResourceTimeSkipEvent(Player player,
                                        SectionDefinition section,
                                        Material material) {
        this.player = player;
        this.section = section;
        this.material = material;
    }

    public Player getPlayer() { return player; }
    public SectionDefinition getSection() { return section; }
    public Material getMaterial() { return material; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
