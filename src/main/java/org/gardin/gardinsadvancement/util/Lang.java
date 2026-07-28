package org.gardin.gardinsadvancement.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Lang {
    private static final String DEFAULT_LANGUAGE = "zh_cn";
    private static final String LANG_FOLDER = "lang";

    private static JavaPlugin plugin;
    private static String language = DEFAULT_LANGUAGE;
    private static YamlConfiguration activeMessages;
    private static YamlConfiguration fallbackMessages;

    private Lang() {
    }

    public static void initialize(JavaPlugin plugin, String requestedLanguage) {
        Lang.plugin = plugin;
        saveDefaultLangFiles();

        fallbackMessages = loadFromJar(DEFAULT_LANGUAGE);
        language = normalizeLanguage(requestedLanguage);
        activeMessages = loadFromDisk(language);
        if (activeMessages == null) {
            activeMessages = loadFromJar(language);
        }
        if (activeMessages == null) {
            language = DEFAULT_LANGUAGE;
            activeMessages = fallbackMessages;
        }
        if (activeMessages == null) {
            activeMessages = new YamlConfiguration();
        }
    }

    public static String getLanguage() {
        return language;
    }

    public static String text(String key, Object... args) {
        String template = lookup(key);
        return format(template == null ? key : template, args);
    }

    public static void send(CommandSender sender, String key, Object... args) {
        sender.sendMessage(color(text(key, args)));
    }

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    private static String lookup(String key) {
        if (activeMessages != null && activeMessages.contains(key)) {
            return activeMessages.getString(key);
        }
        if (fallbackMessages != null && fallbackMessages.contains(key)) {
            return fallbackMessages.getString(key);
        }
        return null;
    }

    private static String format(String template, Object... args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", Objects.toString(args[i], "null"));
        }
        return result;
    }

    private static String normalizeLanguage(String rawLanguage) {
        if (rawLanguage == null || rawLanguage.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return rawLanguage.trim().toLowerCase().replace('-', '_');
    }

    private static void saveDefaultLangFiles() {
        saveResourceIfMissing(DEFAULT_LANGUAGE);
        saveResourceIfMissing("en_us");
    }

    private static void saveResourceIfMissing(String locale) {
        if (plugin == null) {
            return;
        }
        File target = new File(plugin.getDataFolder(), LANG_FOLDER + "/" + locale + ".yml");
        if (!target.exists()) {
            plugin.saveResource(LANG_FOLDER + "/" + locale + ".yml", false);
        }
    }

    private static YamlConfiguration loadFromDisk(String locale) {
        if (plugin == null) {
            return null;
        }
        File target = new File(plugin.getDataFolder(), LANG_FOLDER + "/" + locale + ".yml");
        if (!target.exists()) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(target);
    }

    private static YamlConfiguration loadFromJar(String locale) {
        if (plugin == null) {
            return null;
        }
        var resource = plugin.getResource(LANG_FOLDER + "/" + locale + ".yml");
        if (resource == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
    }
}
