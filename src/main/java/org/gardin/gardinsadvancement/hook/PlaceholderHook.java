package org.gardin.gardinsadvancement.hook;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.gardin.gardinsadvancement.util.GLogger;

import java.lang.reflect.Method;

public class PlaceholderHook {
    private final boolean available;
    private final Method parseMethod;

    public PlaceholderHook(JavaPlugin plugin) {
        PluginManager pluginManager = plugin.getServer().getPluginManager();
        Plugin placeholderApiPlugin = pluginManager.getPlugin("PlaceholderAPI");
        if (placeholderApiPlugin == null || !placeholderApiPlugin.isEnabled()) {
            this.available = false;
            this.parseMethod = null;
            GLogger.debugLang("hook.placeholder.unavailable");
            return;
        }

        Method method = null;
        boolean loaded = false;
        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            method = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            loaded = true;
            GLogger.infoLang("hook.placeholder.attached");
        } catch (ReflectiveOperationException exception) {
            GLogger.warningLang("hook.placeholder.attach_failed");
        }
        this.available = loaded;
        this.parseMethod = method;
    }

    public boolean isAvailable() {
        return available;
    }

    public String resolve(Player player, String placeholder) {
        if (!available || player == null || placeholder == null || placeholder.isBlank()) {
            return null;
        }
        try {
            Object result = parseMethod.invoke(null, player, placeholder);
            if (!(result instanceof String parsed)) {
                return null;
            }
            String trimmed = parsed.trim();
            if (trimmed.isEmpty() || trimmed.equals(placeholder)) {
                return null;
            }
            return trimmed;
        } catch (ReflectiveOperationException exception) {
            GLogger.warningLang("hook.placeholder.resolve_failed", placeholder);
            return null;
        }
    }
}
