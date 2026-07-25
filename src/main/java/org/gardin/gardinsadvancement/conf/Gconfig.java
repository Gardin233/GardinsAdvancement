package org.gardin.gardinsadvancement.conf;

import org.bukkit.Material;

public class Gconfig {
    private final boolean debug;
    private final String contentFolder;
    private final boolean copyExampleContent;
    private final String defaultTabBackground;
    private final Material fallbackIcon;
    private final long placeholderCheckIntervalTicks;
    private final long startupDelayTicks;

    public Gconfig(
            boolean debug,
            String contentFolder,
            boolean copyExampleContent,
            String defaultTabBackground,
            Material fallbackIcon,
            long placeholderCheckIntervalTicks,
            long startupDelayTicks
    ) {
        this.debug = debug;
        this.contentFolder = contentFolder;
        this.copyExampleContent = copyExampleContent;
        this.defaultTabBackground = defaultTabBackground;
        this.fallbackIcon = fallbackIcon;
        this.placeholderCheckIntervalTicks = placeholderCheckIntervalTicks;
        this.startupDelayTicks = startupDelayTicks;
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
}
