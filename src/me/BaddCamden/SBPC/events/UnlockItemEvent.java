package me.BaddCamden.SBPC.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.BaddCamden.SBPC.progress.ProgressEntry;

/**
 * Fired whenever a player unlocks a progression entry (item, enchant, etc.).
 * Hook plugins can use this to reward or react to new unlocks.
 */
public class UnlockItemEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ProgressEntry entry;

    /**
     * Creates a new unlock event for the given player and entry.
     *
     * @param player player receiving the unlock
     * @param entry  progression entry that was unlocked
     */
    public UnlockItemEvent(Player player, ProgressEntry entry) {
        this.player = player;
        this.entry = entry;
    }

    /**
     * Player associated with this unlock.
     */
    public Player getPlayer() { return player; }

    /**
     * The progression entry that has just been unlocked.
     */
    public ProgressEntry getEntry() { return entry; }

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
