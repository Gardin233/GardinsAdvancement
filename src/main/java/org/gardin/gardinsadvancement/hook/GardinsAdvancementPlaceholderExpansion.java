package org.gardin.gardinsadvancement.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.gardin.gardinsadvancement.Gardinsadvancement;

public class GardinsAdvancementPlaceholderExpansion extends PlaceholderExpansion {
    private static final String IDENTIFIER = "ga";

    private final Gardinsadvancement plugin;

    public GardinsAdvancementPlaceholderExpansion(Gardinsadvancement plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getAuthor() {
        return "Gardin";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String getRequiredPlugin() {
        return plugin.getName();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (params == null || params.isBlank()) {
            return null;
        }
        String normalized = params.trim();
        if (normalized.equalsIgnoreCase("advancement_all_count")) {
            return Integer.toString(plugin.getAllAdvancementCount());
        }
        if (normalized.equalsIgnoreCase("advancement_player_finished_count")) {
            Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
            return Integer.toString(plugin.getPlayerFinishedAdvancementCount(player));
        }
        if (normalized.regionMatches(true, 0, "advancement_count:", 0, "advancement_count:".length())) {
            String namespace = normalized.substring("advancement_count:".length()).trim();
            return Integer.toString(plugin.getAdvancementCount(namespace));
        }
        if (normalized.regionMatches(true, 0, "advancement_is_finished:", 0, "advancement_is_finished:".length())) {
            String advancementKey = normalized.substring("advancement_is_finished:".length()).trim();
            Player player = offlinePlayer == null ? null : offlinePlayer.getPlayer();
            return Boolean.toString(plugin.isAdvancementFinished(player, advancementKey));
        }
        return null;
    }
}
