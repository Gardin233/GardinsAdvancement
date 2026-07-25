package org.gardin.gardinsadvancement.textchecker;

import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementFrameType;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.gardin.gardinsadvancement.hook.CraftEngine;
import org.gardin.gardinsadvancement.util.GLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class YamlLexicalParser {
    private static final String CE_PREFIX = "CE:";

    private YamlLexicalParser() {
    }

    public static String readString(ConfigurationSection section, String path, String fallback) {
        String value = section.getString(path);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public static List<String> readStringList(ConfigurationSection section, String path) {
        List<String> rawList = section.getStringList(path);
        if (!rawList.isEmpty()) {
            return normalizeStrings(rawList);
        }
        String singleValue = section.getString(path);
        if (singleValue == null || singleValue.isBlank()) {
            return List.of();
        }
        return List.of(singleValue.trim());
    }

    public static List<String> normalizeStrings(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    //成就类型解析器
    public static String parseAdvancementType(String rawType, String source) {
        String normalized = rawType == null ? "common" : rawType.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("root") || normalized.equals("common")) {
            return normalized;
        }
        GLogger.warning(source + " 的 type=" + rawType + " 非法，已回退为 common");
        return "common";
    }
    //Frame解析器
    public static AdvancementFrameType parseFrameType(String rawType, String source) {
        if (rawType == null || rawType.isBlank()) {
            return AdvancementFrameType.TASK;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        try {
            return AdvancementFrameType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            GLogger.warning(source + " 的 frame=" + rawType + " 非法，已回退为 TASK");
            return AdvancementFrameType.TASK;
        }
    }
    //颜色解析器
    public static ChatColor parseColor(String rawColor, String source) {
        if (rawColor == null || rawColor.isBlank()) {
            return null;
        }
        String normalized = rawColor.trim();
        try {
            if (normalized.startsWith("#")) {
                return ChatColor.of(normalized);
            }
            return ChatColor.of(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            GLogger.warning(source + " 的 color=" + rawColor + " 非法，已忽略");
            return null;
        }
    }
    //材质解析器
    public static Material parseMaterial(String rawMaterial, Material fallback, String source) {
        if (rawMaterial == null || rawMaterial.isBlank()) {
            return fallback;
        }
        String candidate = rawMaterial.trim();
        if (candidate.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            candidate = candidate.substring("minecraft:".length());
        }
        Material material = Material.matchMaterial(candidate);
        if (material == null) {
            material = Material.matchMaterial(candidate.toUpperCase(Locale.ROOT));
        }
        if (material == null) {
            GLogger.warning(source + " 的材质=" + rawMaterial + " 无法识别，已回退为 " + fallback);
            return fallback;
        }
        return material;
    }
    //Icon解析器
    public static ItemStack parseIcon(String rawIcon, Material fallback, String source) {
        if (rawIcon == null || rawIcon.isBlank()) {
            return new ItemStack(fallback);
        }
        String icon = rawIcon.trim();
        if (icon.regionMatches(true, 0, CE_PREFIX, 0, CE_PREFIX.length())) {
            ItemStack craftEngineItem = parseCraftEngineIcon(icon.substring(CE_PREFIX.length()).trim(), source);
            if (craftEngineItem != null) {
                return craftEngineItem;
            }
            GLogger.warning(source + " 的 CE 图标=" + icon + " 当前未能解析，已回退为 " + fallback);
            return new ItemStack(fallback);
        }
        return new ItemStack(parseMaterial(icon, fallback, source));
    }
    private static ItemStack parseCraftEngineIcon(String rawId, String source) {
        return CraftEngine.resolveItem(rawId, source);
    }
}
