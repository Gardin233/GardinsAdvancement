package org.gardin.gardinsadvancement.textchecker;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.gardin.gardinsadvancement.conf.Gconfig;
import org.gardin.gardinsadvancement.util.Lang;

public class ConfLoader {
    private static final String DEFAULT_BACKGROUND =
            "minecraft:textures/gui/advancements/backgrounds/stone.png";

    private final JavaPlugin plugin;

    public ConfLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Gconfig load() {
        FileConfiguration config = plugin.getConfig();
        boolean debug = config.getBoolean("debug", false);
        String contentFolder = YamlLexicalParser.readString(config, "content-folder", "content");
        boolean copyExampleContent = config.getBoolean("copy-example-content", true);
        String defaultTabBackground = YamlLexicalParser.readString(
                config,
                "default-tab-background",
                DEFAULT_BACKGROUND
        );
        Material fallbackIcon = YamlLexicalParser.parseMaterial(
                config.getString("fallback-icon"),
                Material.STONE,
                "config.yml:fallback-icon"
        );
        long placeholderCheckIntervalTicks = Math.max(
                20L,
                config.getLong("placeholder-check-interval-ticks", 100L)
        );
        long startupDelayTicks = Math.max(
                0L,
                config.getLong("startup-delay-ticks", 60L)
        );
        return new Gconfig(
                YamlLexicalParser.readString(config, "language", "zh_cn"),
                debug,
                contentFolder,
                copyExampleContent,
                defaultTabBackground,
                fallbackIcon,
                placeholderCheckIntervalTicks,
                startupDelayTicks
        );
    }

    public Gconfig init() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        Lang.initialize(plugin, plugin.getConfig().getString("language", "zh_cn"));
        return load();
    }

    public String readLanguage() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        return plugin.getConfig().getString("language", "zh_cn");
    }
}
