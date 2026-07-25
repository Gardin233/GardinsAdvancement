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
            GLogger.debug("CraftEngine 未挂载，CE 图标功能保持降级模式");
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
            GLogger.info("&f已挂载 CraftEngine 图标解析支持");
            return true;
        } catch (ReflectiveOperationException exception) {
            GLogger.warning("检测到 CraftEngine，但加载 CE API 失败，将禁用 CE 挂载");
            available = false;
            return false;
        }
    }
    public static ItemStack resolveItem(String rawId, String source) {
        if (!isAvailable()) {
            GLogger.warning(source + " 使用了 CE 图标 " + rawId + "，但服务器未挂载 CraftEngine，将回退为默认物品");
            return null;
        }
        try {
            Object definition = findDefinition(rawId);
            if (definition == null) {
                GLogger.warning(source + " 的 CraftEngine 图标=" + rawId + " 未在已加载物品中找到，将回退为默认物品");
                return null;
            }
            Object itemStack = buildBukkitItemMethod.invoke(definition);
            if (itemStack instanceof ItemStack stack) {
                GLogger.debug("CraftEngine 图标解析成功: " + rawId + " -> " + source);
                return stack;
            }
        } catch (ReflectiveOperationException exception) {
            GLogger.warning(source + " 的 CraftEngine 图标=" + rawId + " 解析失败，将回退为默认物品");
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
                GLogger.debug("通过直接 CE id 查找到物品: input=" + trimmed + ", candidate=" + candidate);
                return definition;
            }
        }
        Object loadedItems = loadedItemsMethod.invoke(null);
        if (!(loadedItems instanceof Map<?, ?> itemMap) || itemMap.isEmpty()) {
            GLogger.debug("CraftEngine 已加载物品表为空，无法扫描匹配 id: " + trimmed);
            return null;
        }

        for (Map.Entry<?, ?> entry : itemMap.entrySet()) {
            String asString = readKeyString(entry.getKey(), keyAsStringMethod);
            String asMinimalString = readKeyString(entry.getKey(), keyAsMinimalStringMethod);
            if (matches(trimmed, asString) || matches(trimmed, asMinimalString)) {
                GLogger.debug("通过 CE 已加载物品表匹配到物品: input=" + trimmed
                        + ", asString=" + asString
                        + ", asMinimalString=" + asMinimalString);
                return entry.getValue();
            }
        }

        GLogger.debug("未在 CE 已加载物品表中找到匹配项: " + trimmed);
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
