package me.BaddCamden.SBPC.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.BaddCamden.SBPC.progress.ProgressEntry;

public class UnlockItemEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ProgressEntry entry;

    public UnlockItemEvent(Player player, ProgressEntry entry) {
        this.player = player;
        this.entry = entry;
    }

    public Player getPlayer() { return player; }
    public ProgressEntry getEntry() { return entry; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
