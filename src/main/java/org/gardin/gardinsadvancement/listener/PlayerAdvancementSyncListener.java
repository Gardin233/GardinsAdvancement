package org.gardin.gardinsadvancement.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.gardin.gardinsadvancement.Gardinsadvancement;

public class PlayerAdvancementSyncListener implements Listener {
    private final Gardinsadvancement plugin;

    public PlayerAdvancementSyncListener(Gardinsadvancement plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.syncPlayerAdvancementsFromStorage(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.unloadPlayerAdvancementCache(event.getPlayer());
    }
}
