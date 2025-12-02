package me.BaddCamden.SBPC;

import me.BaddCamden.SessionLibrary.events.SessionEndEvent;
import me.BaddCamden.SessionLibrary.events.SessionStartEvent;
import me.BaddCamden.SessionLibrary.events.SessionTickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

class SessionLibraryListener implements Listener {

    private final SBPCPlugin plugin;

    SessionLibraryListener(SBPCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSessionStart(SessionStartEvent event) {
        plugin.handleSessionStart();
    }

    @EventHandler
    public void onSessionEnd(SessionEndEvent event) {
        plugin.handleSessionEnd();
    }

    @EventHandler
    public void onSessionTick(SessionTickEvent event) {
        plugin.handleSessionTick();
    }
}
