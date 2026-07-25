package org.gardin.gardinsadvancement.service;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.gardin.gardinsadvancement.advancementregister.AdvancementRegister;
import org.gardin.gardinsadvancement.advancementregister.GAdvancement;
import org.gardin.gardinsadvancement.condition.PlaceholderConditionExpression;
import org.gardin.gardinsadvancement.conf.Gconfig;
import org.gardin.gardinsadvancement.hook.PlaceholderHook;
import org.gardin.gardinsadvancement.textchecker.ContentDocument;
import org.gardin.gardinsadvancement.util.GLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class PlaceholderConditionService {
    private static final String PREFIX = "placeholder:";

    private final JavaPlugin plugin;
    private Gconfig gconfig;
    private final PlaceholderHook placeholderHook;
    private final List<TrackedAdvancement> trackedAdvancements;
    private BukkitTask task;

    public PlaceholderConditionService(
            JavaPlugin plugin,
            Gconfig gconfig,
            ContentDocument contentDocument,
            AdvancementRegister advancementRegister
    ) {
        this.plugin = plugin;
        this.gconfig = gconfig;
        this.placeholderHook = new PlaceholderHook(plugin);
        this.trackedAdvancements = compileTrackedAdvancements(
                contentDocument.getAdvancements(),
                advancementRegister.getRegisteredAdvancements()
        );
    }

    public void start() {
        if (!placeholderHook.isAvailable()) {
            GLogger.warning("未检测到可用的 PlaceholderAPI，插件仅支持 placeholder 表达式判定，条件服务未启动");
            return;
        }
        if (trackedAdvancements.isEmpty()) {
            GLogger.warning("未发现可用的 placeholder 条件表达式，条件服务未启动");
            return;
        }
        long interval = gconfig.getPlaceholderCheckIntervalTicks();
        GLogger.info("&f开始启动 placeholder 条件服务，仅使用 placeholder 表达式轮询判定，间隔=" + interval + " ticks");
        this.task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::checkOnlinePlayers,
                interval,
                interval
        );
        checkOnlinePlayers();
        GLogger.info("&a已启用 placeholder 条件检查，共跟踪 " + trackedAdvancements.size() + " 个进度");
    }

    public void reloadSettings(Gconfig gconfig) {
        this.gconfig = gconfig;
        if (task != null) {
            stop();
            start();
            GLogger.info("&fplaceholder 条件服务已按新设置重新启动，当前轮询间隔="
                    + gconfig.getPlaceholderCheckIntervalTicks() + " ticks");
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
            GLogger.info("&fplaceholder 条件服务已停止");
        }
    }

    private void checkOnlinePlayers() {
        GLogger.debug("开始轮询在线玩家 placeholder 条件，当前在线: " + plugin.getServer().getOnlinePlayers().size());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            evaluatePlayer(player);
        }
    }

    private void evaluatePlayer(Player player) {
        for (TrackedAdvancement trackedAdvancement : trackedAdvancements) {
            Advancement advancement = trackedAdvancement.advancement();
            if (advancement.isGranted(player)) {
                continue;
            }
            GLogger.debug("检查玩家 " + player.getName() + " 的进度条件: " + trackedAdvancement.key());
            if (trackedAdvancement.matches(player, placeholderHook)) {
                advancement.grant(player);
                GLogger.debug("玩家 " + player.getName() + " 满足 placeholder 条件，授予进度 " + trackedAdvancement.key());
            }
        }
    }

    private List<TrackedAdvancement> compileTrackedAdvancements(
            List<GAdvancement> definitions,
            Map<String, Advancement> registeredAdvancements
    ) {
        List<TrackedAdvancement> result = new ArrayList<>();
        List<GAdvancement> orderedDefinitions = definitions.stream()
                .sorted(Comparator.comparing(GAdvancement::isRoot).reversed())
                .toList();

        for (GAdvancement definition : orderedDefinitions) {
            String key = buildKey(definition);
            List<PlaceholderConditionExpression> expressions = new ArrayList<>();
            boolean hasUnsupportedCondition = false;

            for (String rawCondition : definition.getCondition()) {
                String expressionSource = unwrapPlaceholderExpression(rawCondition);
                if (expressionSource == null) {
                    hasUnsupportedCondition = true;
                    continue;
                }
                try {
                    expressions.add(PlaceholderConditionExpression.compile(expressionSource));
                    GLogger.debug("已编译 placeholder 条件: " + key + " -> " + expressionSource);
                } catch (IllegalArgumentException exception) {
                    GLogger.error("placeholder 条件解析失败: " + key + " -> " + exception.getMessage());
                    hasUnsupportedCondition = true;
                    break;
                }
            }

            if (expressions.isEmpty()) {
                GLogger.warning("进度 " + key + " 未提供有效 placeholder 表达式，已跳过自动判断");
                continue;
            }
            if (hasUnsupportedCondition) {
                GLogger.warning("进度 " + key + " 含有非 placeholder 条件；当前插件仅支持 placeholder 表达式，已跳过自动判断");
                continue;
            }

            Advancement advancement = registeredAdvancements.get(key);
            if (advancement == null) {
                GLogger.warning("进度 " + key + " 尚未注册到 UAA，跳过 placeholder 跟踪");
                continue;
            }
            GLogger.info("&f已跟踪 placeholder 条件进度: " + key + "，表达式数量=" + expressions.size());
            result.add(new TrackedAdvancement(key, advancement, List.copyOf(expressions)));
        }
        return result;
    }

    private String unwrapPlaceholderExpression(String rawCondition) {
        if (rawCondition == null) {
            return null;
        }
        String trimmed = rawCondition.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            String expression = trimmed.substring(PREFIX.length()).trim();
            return expression.isEmpty() ? null : expression;
        }
        return trimmed.contains("%") ? trimmed : null;
    }

    private String buildKey(GAdvancement advancement) {
        return advancement.getTab() + ":" + advancement.getId();
    }

    private record TrackedAdvancement(
            String key,
            Advancement advancement,
            List<PlaceholderConditionExpression> expressions
    ) {
        boolean matches(Player player, PlaceholderHook hook) {
            for (PlaceholderConditionExpression expression : expressions) {
                if (!expression.test(player, hook)) {
                    return false;
                }
            }
            return true;
        }
    }
}
