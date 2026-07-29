package org.gardin.gardinsadvancement.conf;

import org.bukkit.Material;

public class Gconfig {
    private final String language;
    private final boolean debug;
    private final String contentFolder;
    private final boolean copyExampleContent;
    private final String defaultTabBackground;
    private final Material fallbackIcon;
    private final long placeholderCheckIntervalTicks;
    private final long startupDelayTicks;
    private final DatabaseConfig databaseConfig;

    public Gconfig(
            String language,
            boolean debug,
            String contentFolder,
            boolean copyExampleContent,
            String defaultTabBackground,
            Material fallbackIcon,
            long placeholderCheckIntervalTicks,
            long startupDelayTicks,
            DatabaseConfig databaseConfig
    ) {
        this.language = language;
        this.debug = debug;
        this.contentFolder = contentFolder;
        this.copyExampleContent = copyExampleContent;
        this.defaultTabBackground = defaultTabBackground;
        this.fallbackIcon = fallbackIcon;
        this.placeholderCheckIntervalTicks = placeholderCheckIntervalTicks;
        this.startupDelayTicks = startupDelayTicks;
        this.databaseConfig = databaseConfig;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getContentFolder() {
        return contentFolder;
    }

    public boolean isCopyExampleContent() {
        return copyExampleContent;
    }

    public String getDefaultTabBackground() {
        return defaultTabBackground;
    }

    public Material getFallbackIcon() {
        return fallbackIcon;
    }

    public long getPlaceholderCheckIntervalTicks() {
        return placeholderCheckIntervalTicks;
    }

    public long getStartupDelayTicks() {
        return startupDelayTicks;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }
}
