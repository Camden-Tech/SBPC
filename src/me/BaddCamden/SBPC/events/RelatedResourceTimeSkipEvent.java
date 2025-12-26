package me.BaddCamden.SBPC.events;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.BaddCamden.SBPC.progress.SectionDefinition;

/**
 * Fired when a player gains a time skip or speed boost from collecting a related resource.
 * Allows hooks to observe or modify the bonus before it is applied.
 */
public class RelatedResourceTimeSkipEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SectionDefinition section;
    private final Material material; // may be null for external events

    /**
     * Creates a new related-resource event.
     *
     * @param player   player who earned the bonus
     * @param section  section whose related material was gathered
     * @param material material that triggered the event (may be null for external triggers)
     */
    public RelatedResourceTimeSkipEvent(Player player,
                                        SectionDefinition section,
                                        Material material) {
        this.player = player;
        this.section = section;
        this.material = material;
    }

    /**
     * Player who triggered the related resource bonus.
     */
    public Player getPlayer() { return player; }

    /**
     * Section that the related resource belongs to.
     */
    public SectionDefinition getSection() { return section; }

    /**
     * The material that provided the bonus, or null for synthetic events.
     */
    public Material getMaterial() { return material; }

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
