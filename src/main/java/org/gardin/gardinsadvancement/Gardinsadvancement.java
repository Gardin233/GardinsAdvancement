package org.gardin.gardinsadvancement;

import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.gardin.gardinsadvancement.advancementregister.AdvancementRegister;
import org.gardin.gardinsadvancement.commands.commandsRegister;
import org.gardin.gardinsadvancement.conf.ConfRegister;
import org.gardin.gardinsadvancement.conf.Gconfig;
import org.gardin.gardinsadvancement.hook.GardinsAdvancementPlaceholderExpansion;
import org.gardin.gardinsadvancement.service.PlaceholderConditionService;
import org.gardin.gardinsadvancement.tabcreater.TabRegister;
import org.gardin.gardinsadvancement.textchecker.ContentDocument;
import org.gardin.gardinsadvancement.textchecker.ContentLoader;
import org.gardin.gardinsadvancement.util.GLogger;

import java.util.List;

public final class Gardinsadvancement extends JavaPlugin {
    private UltimateAdvancementAPI advancementAPI;
    private TabRegister tabRegister;
    private AdvancementRegister advancementRegister;
    private ConfRegister confRegister;
    private Gconfig gconfig;
    private PlaceholderConditionService placeholderConditionService;
    private commandsRegister commandsRegister;
    private GardinsAdvancementPlaceholderExpansion placeholderExpansion;
    private BukkitTask startupTask;
    private boolean runtimeInitialized;

    @Override
    public void onEnable() {
        this.confRegister = new ConfRegister(this);
        this.gconfig = this.confRegister.init();
        GLogger.infoLang("plugin.enable.loading_base");
        GLogger.setDebug(gconfig.isDebug());
        GLogger.debugLang(
                "plugin.config_summary",
                gconfig.getLanguage(),
                gconfig.getContentFolder(),
                gconfig.isCopyExampleContent(),
                gconfig.getDefaultTabBackground(),
                gconfig.getFallbackIcon(),
                gconfig.getPlaceholderCheckIntervalTicks(),
                gconfig.getStartupDelayTicks()
        );
        this.commandsRegister = new commandsRegister(this);
        this.commandsRegister.init();
        registerPlaceholderExpansion();
        scheduleRuntimeBootstrap();
    }

    private void scheduleRuntimeBootstrap() {
        long delay = gconfig.getStartupDelayTicks();
        if (delay <= 0L) {
            GLogger.infoLang("plugin.enable.no_startup_delay");
            bootstrapRuntime();
            return;
        }
        GLogger.infoLang("plugin.enable.delayed_startup", delay);
        this.startupTask = Bukkit.getScheduler().runTaskLater(this, this::bootstrapRuntime, delay);
    }

    private void bootstrapRuntime() {
        if (runtimeInitialized) {
            GLogger.debugLang("plugin.enable.runtime_already_initialized");
            return;
        }
        this.startupTask = null;
        GLogger.infoLang("plugin.enable.runtime_bootstrap_start");
        this.advancementAPI = UltimateAdvancementAPI.getInstance(this);
        GLogger.infoLang("plugin.enable.uaa_hooked");

        ContentLoader contentLoader = new ContentLoader(this, gconfig);
        ContentDocument contentDocument = contentLoader.init();
        GLogger.infoLang("plugin.enable.content_loaded");

        this.tabRegister = new TabRegister(advancementAPI, contentDocument.getTabs(), gconfig);
        this.tabRegister.init();
        this.advancementRegister = new AdvancementRegister(
                advancementAPI,
                gconfig,
                contentDocument,
                tabRegister.getTabList()
        );
        this.advancementRegister.init();
        this.placeholderConditionService = new PlaceholderConditionService(
                this,
                gconfig,
                contentDocument,
                advancementRegister
        );
        this.placeholderConditionService.start();
        this.runtimeInitialized = true;

        GLogger.infoLang(
                "plugin.enable.complete",
                contentDocument.getTabs().size(),
                contentDocument.getAdvancements().size()
        );
    }

    public void reloadSettings() {
        this.gconfig = this.confRegister.init();
        GLogger.infoLang("plugin.reload.request");
        GLogger.setDebug(gconfig.isDebug());
        GLogger.debugLang(
                "plugin.config_summary",
                gconfig.getLanguage(),
                gconfig.getContentFolder(),
                gconfig.isCopyExampleContent(),
                gconfig.getDefaultTabBackground(),
                gconfig.getFallbackIcon(),
                gconfig.getPlaceholderCheckIntervalTicks(),
                gconfig.getStartupDelayTicks()
        );
        if (this.placeholderConditionService != null) {
            this.placeholderConditionService.reloadSettings(gconfig);
        }
        if (!runtimeInitialized && startupTask != null) {
            startupTask.cancel();
            GLogger.infoLang("plugin.reload.runtime_rescheduled");
            scheduleRuntimeBootstrap();
        }
        GLogger.infoLang("plugin.reload.complete");
    }

    public Gconfig getGconfig() {
        return gconfig;
    }

    public boolean grantAdvancement(Player player, String tabId, String advancementId) {
        if (placeholderConditionService == null) {
            GLogger.warningLang("plugin.runtime_unavailable");
            return false;
        }
        return placeholderConditionService.grantAdvancement(player, tabId, advancementId);
    }

    public boolean grantAdvancement(Player player, String advancementKey) {
        if (placeholderConditionService == null) {
            GLogger.warningLang("plugin.runtime_unavailable");
            return false;
        }
        if (PlaceholderConditionService.ALL_ADVANCEMENTS_KEY.equalsIgnoreCase(advancementKey)) {
            return placeholderConditionService.grantAllAdvancements(player);
        }
        return placeholderConditionService.grantAdvancement(player, advancementKey);
    }

    public boolean revokeAdvancement(Player player, String tabId, String advancementId) {
        if (placeholderConditionService == null) {
            GLogger.warningLang("plugin.runtime_unavailable");
            return false;
        }
        return placeholderConditionService.revokeAdvancement(player, tabId, advancementId);
    }

    public boolean revokeAdvancement(Player player, String advancementKey) {
        if (placeholderConditionService == null) {
            GLogger.warningLang("plugin.runtime_unavailable");
            return false;
        }
        if (PlaceholderConditionService.ALL_ADVANCEMENTS_KEY.equalsIgnoreCase(advancementKey)) {
            return placeholderConditionService.revokeAllAdvancements(player);
        }
        return placeholderConditionService.revokeAdvancement(player, advancementKey);
    }

    public List<String> getManageableAdvancementKeys() {
        if (placeholderConditionService == null) {
            return List.of();
        }
        return placeholderConditionService.getManagedAdvancementKeys();
    }

    public boolean isAdvancementFinished(Player player, String advancementKey) {
        if (player == null || advancementRegister == null || advancementKey == null || advancementKey.isBlank()) {
            return false;
        }
        var advancement = advancementRegister.findRegisteredAdvancement(advancementKey);
        return advancement != null && advancement.isGranted(player);
    }

    public int getAdvancementCount(String namespace) {
        if (advancementRegister == null) {
            return 0;
        }
        return advancementRegister.countRegisteredAdvancements(namespace);
    }

    public int getAllAdvancementCount() {
        if (advancementRegister == null) {
            return 0;
        }
        return advancementRegister.countRegisteredAdvancements();
    }

    public int getPlayerFinishedAdvancementCount(Player player) {
        if (advancementRegister == null) {
            return 0;
        }
        return advancementRegister.countFinishedAdvancements(player);
    }

    public void syncPlayerAdvancementCache(Player player) {
        if (placeholderConditionService == null) {
            GLogger.warningLang("plugin.runtime_unavailable");
            return;
        }
        placeholderConditionService.syncPlayerState(player);
    }

    @Override
    public void onDisable() {
        GLogger.infoLang("plugin.disable.start");
        if (startupTask != null) {
            startupTask.cancel();
            startupTask = null;
        }
        if (placeholderExpansion != null && placeholderExpansion.isRegistered()) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (placeholderConditionService != null) {
            placeholderConditionService.stop();
        }
        GLogger.infoLang("plugin.disable.complete");
    }

    private void registerPlaceholderExpansion() {
        if (this.placeholderExpansion != null && this.placeholderExpansion.isRegistered()) {
            return;
        }
        this.placeholderExpansion = new GardinsAdvancementPlaceholderExpansion(this);
        if (this.placeholderExpansion.register()) {
            GLogger.infoLang("hook.placeholder.expansion_registered");
            return;
        }
        GLogger.warningLang("hook.placeholder.expansion_register_failed");
    }
}
