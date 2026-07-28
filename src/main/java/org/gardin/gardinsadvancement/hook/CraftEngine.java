package org.gardin.gardinsadvancement.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.gardin.gardinsadvancement.util.GLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CraftEngine {
    private static final String PLUGIN_NAME = "CraftEngine";
    private static Boolean available;
    private static Method byIdStringMethod;
    private static Method loadedItemsMethod;
    private static Method buildBukkitItemMethod;
    private static Method keyAsStringMethod;
    private static Method keyAsMinimalStringMethod;

    private CraftEngine() {
    }

    public static boolean isAvailable() {
        if (available != null) {
            return available;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
        if (plugin == null || !plugin.isEnabled()) {
            available = false;
            GLogger.debugLang("hook.craftengine.unavailable");
            return false;
        }
        try {
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
            Class<?> craftEngineItemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Class<?> definitionClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemDefinition");
            byIdStringMethod = craftEngineItemsClass.getMethod("byId", String.class);
            loadedItemsMethod = craftEngineItemsClass.getMethod("loadedItems");
            buildBukkitItemMethod = definitionClass.getMethod("buildBukkitItem");
            keyAsStringMethod = keyClass.getMethod("asString");
            keyAsMinimalStringMethod = keyClass.getMethod("asMinimalString");
            available = true;
            GLogger.infoLang("hook.craftengine.attached");
            return true;
        } catch (ReflectiveOperationException exception) {
            GLogger.warningLang("hook.craftengine.attach_failed");
            available = false;
            return false;
        }
    }
    public static ItemStack resolveItem(String rawId, String source) {
        if (!isAvailable()) {
            GLogger.warningLang("hook.craftengine.not_available_for_icon", source, rawId);
            return null;
        }
        try {
            Object definition = findDefinition(rawId);
            if (definition == null) {
                GLogger.warningLang("hook.craftengine.item_not_found", source, rawId);
                return null;
            }
            Object itemStack = buildBukkitItemMethod.invoke(definition);
            if (itemStack instanceof ItemStack stack) {
                GLogger.debugLang("hook.craftengine.resolve_success", rawId, source);
                return stack;
            }
        } catch (ReflectiveOperationException exception) {
            GLogger.warningLang("hook.craftengine.resolve_failed", source, rawId);
        }
        return null;
    }
    private static Object findDefinition(String rawId) throws ReflectiveOperationException {
        String trimmed = rawId == null ? "" : rawId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (String candidate : buildCandidates(trimmed)) {
            Object definition = byIdStringMethod.invoke(null, candidate);
            if (definition != null) {
                GLogger.debugLang("hook.craftengine.direct_match", trimmed, candidate);
                return definition;
            }
        }
        Object loadedItems = loadedItemsMethod.invoke(null);
        if (!(loadedItems instanceof Map<?, ?> itemMap) || itemMap.isEmpty()) {
            GLogger.debugLang("hook.craftengine.loaded_map_empty", trimmed);
            return null;
        }

        for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
            String asString = readKeyString(entry.getKey(), keyAsStringMethod);
            String asMinimalString = readKeyString(entry.getKey(), keyAsMinimalStringMethod);
            if (matches(trimmed, asString) || matches(trimmed, asMinimalString)) {
                GLogger.debugLang("hook.craftengine.map_match", trimmed, asString, asMinimalString);
                return entry.getValue();
            }
        }

        GLogger.debugLang("hook.craftengine.no_match", trimmed);
        return null;
    }

    private static List<String> buildCandidates(String rawId) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(rawId);
        candidates.add(rawId.toLowerCase(Locale.ROOT));

        String[] split = rawId.split(":", 2);
        if (split.length == 2) {
            candidates.add(split[0].toLowerCase(Locale.ROOT) + ":" + split[1]);
            candidates.add(split[0] + ":" + split[1].toLowerCase(Locale.ROOT));
            candidates.add(split[0].toLowerCase(Locale.ROOT) + ":" + split[1].toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(candidates);
    }

    private static String readKeyString(Object keyObject, Method method) throws ReflectiveOperationException {
        Object value = method.invoke(keyObject);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean matches(String input, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (candidate.equals(input) || candidate.equalsIgnoreCase(input)) {
            return true;
        }
        for (String normalized : buildCandidates(input)) {
            if (candidate.equals(normalized) || candidate.equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }
}
