package me.BaddCamden.SBPC;

import me.BaddCamden.SessionLibrary.events.SessionEndEvent;
import me.BaddCamden.SessionLibrary.events.SessionStartEvent;
import me.BaddCamden.SessionLibrary.events.SessionTickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Bridges SessionLibrary events into SBPC's session-handling callbacks.
 */
class SessionLibraryListener implements Listener {

    private final SBPCPlugin plugin;

    /**
     * Creates a listener that forwards SessionLibrary events to the plugin.
     */
    SessionLibraryListener(SBPCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    /**
     * Called when SessionLibrary signals a session start; enables SBPC ticking.
     */
    public void onSessionStart(SessionStartEvent event) {
        plugin.handleSessionStart();
    }

    @EventHandler
    /**
     * Called when SessionLibrary signals a session end; forces non-ops to disconnect.
     */
    public void onSessionEnd(SessionEndEvent event) {
        plugin.handleSessionEnd();
    }

    @EventHandler
    /**
     * Called on each SessionLibrary tick; available for future hook logic.
     */
    public void onSessionTick(SessionTickEvent event) {
        plugin.handleSessionTick();
    }
}
