package org.gardin.gardinsadvancement.conf;

import org.bukkit.plugin.java.JavaPlugin;
import org.gardin.gardinsadvancement.textchecker.ConfLoader;

public class ConfRegister {
    private final ConfLoader confLoader;
    private Gconfig gconfig;

    public ConfRegister(JavaPlugin plugin) {
        this.confLoader = new ConfLoader(plugin);
    }

    public Gconfig init() {
        this.gconfig = confLoader.init();
        return this.gconfig;
    }

    public Gconfig getGconfig() {
        return gconfig;
    }
}
