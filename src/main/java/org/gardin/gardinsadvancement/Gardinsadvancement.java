package org.gardin.gardinsadvancement;

import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.gardin.gardinsadvancement.advancementregister.AdvancementRegister;
import org.gardin.gardinsadvancement.commands.commandsRegister;
import org.gardin.gardinsadvancement.conf.ConfRegister;
import org.gardin.gardinsadvancement.conf.Gconfig;
import org.gardin.gardinsadvancement.service.PlaceholderConditionService;
import org.gardin.gardinsadvancement.tabcreater.TabRegister;
import org.gardin.gardinsadvancement.textchecker.ContentDocument;
import org.gardin.gardinsadvancement.textchecker.ContentLoader;
import org.gardin.gardinsadvancement.util.GLogger;

public final class Gardinsadvancement extends JavaPlugin {
    private UltimateAdvancementAPI advancementAPI;
    private TabRegister tabRegister;
    private AdvancementRegister advancementRegister;
    private ConfRegister confRegister;
    private Gconfig gconfig;
    private PlaceholderConditionService placeholderConditionService;
    private commandsRegister commandsRegister;
    private BukkitTask startupTask;
    private boolean runtimeInitialized;

    @Override
    public void onEnable() {
        GLogger.info("&f插件启动中，开始加载基础配置");
        this.confRegister = new ConfRegister(this);
        this.gconfig = this.confRegister.init();
        GLogger.setDebug(gconfig.isDebug());
        GLogger.debug("配置摘要: content-folder=" + gconfig.getContentFolder()
                + ", copy-example-content=" + gconfig.isCopyExampleContent()
                + ", default-tab-background=" + gconfig.getDefaultTabBackground()
                + ", fallback-icon=" + gconfig.getFallbackIcon()
                + ", placeholder-check-interval-ticks=" + gconfig.getPlaceholderCheckIntervalTicks()
                + ", startup-delay-ticks=" + gconfig.getStartupDelayTicks());
        this.commandsRegister = new commandsRegister(this);
        this.commandsRegister.init();
        scheduleRuntimeBootstrap();
    }

    private void scheduleRuntimeBootstrap() {
        long delay = gconfig.getStartupDelayTicks();
        if (delay <= 0L) {
            GLogger.info("&f未设置启动延迟，立即初始化进度系统");
            bootstrapRuntime();
            return;
        }
        GLogger.info("&f已启用延迟启动，将在 " + delay + " ticks 后初始化进度系统，等待 CraftEngine 数据包先完成加载");
        this.startupTask = Bukkit.getScheduler().runTaskLater(this, this::bootstrapRuntime, delay);
    }

    private void bootstrapRuntime() {
        if (runtimeInitialized) {
            GLogger.debug("进度系统已初始化，跳过重复启动");
            return;
        }
        this.startupTask = null;
        GLogger.info("&f开始初始化进度系统与运行时服务");
        this.advancementAPI = UltimateAdvancementAPI.getInstance(this);
        GLogger.info("&fUltimateAdvancementAPI 已挂载");

        ContentLoader contentLoader = new ContentLoader(this, gconfig);
        ContentDocument contentDocument = contentLoader.init();
        GLogger.info("&f内容解析完成，开始注册 Tab 与 Advancement");

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

        GLogger.info("&a插件已完成启动，解析到 "
                + contentDocument.getTabs().size() + " 个 Tab，"
                + contentDocument.getAdvancements().size() + " 个进度");
    }

    public void reloadSettings() {
        GLogger.info("&f收到设置重载请求，开始重新读取 config.yml");
        this.gconfig = this.confRegister.init();
        GLogger.setDebug(gconfig.isDebug());
        GLogger.debug("重载后配置摘要: content-folder=" + gconfig.getContentFolder()
                + ", copy-example-content=" + gconfig.isCopyExampleContent()
                + ", default-tab-background=" + gconfig.getDefaultTabBackground()
                + ", fallback-icon=" + gconfig.getFallbackIcon()
                + ", placeholder-check-interval-ticks=" + gconfig.getPlaceholderCheckIntervalTicks()
                + ", startup-delay-ticks=" + gconfig.getStartupDelayTicks());
        if (this.placeholderConditionService != null) {
            this.placeholderConditionService.reloadSettings(gconfig);
        }
        if (!runtimeInitialized && startupTask != null) {
            startupTask.cancel();
            GLogger.info("&f检测到进度系统尚未初始化，已按新设置重新安排延迟启动");
            scheduleRuntimeBootstrap();
        }
        GLogger.info("&a设置重载完成，仅运行时设置已立即生效");
    }

    public Gconfig getGconfig() {
        return gconfig;
    }

    @Override
    public void onDisable() {
        GLogger.info("&f插件关闭中，开始释放运行时服务");
        if (startupTask != null) {
            startupTask.cancel();
            startupTask = null;
        }
        if (placeholderConditionService != null) {
            placeholderConditionService.stop();
        }
        GLogger.info("&e插件已卸载");
    }
}
