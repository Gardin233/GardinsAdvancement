package org.gardin.gardinsadvancement.service;

import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PlaceholderConditionService {
    private static final String PREFIX = "placeholder:";

    private final JavaPlugin plugin;
    private Gconfig gconfig;
    private final PlaceholderHook placeholderHook;
    private final List<TrackedAdvancement> trackedAdvancementOrder;
    private final Map<String, TrackedAdvancement> trackedAdvancements;
    private final Map<String, PlaceholderRegistration> placeholderRegistrations;
    private final Map<UUID, PlayerPlaceholderState> playerStates = new HashMap<>();
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
        CompiledTracking compiledTracking = compileTrackedAdvancements(
                contentDocument.getAdvancements(),
                advancementRegister.getRegisteredAdvancements()
        );
        this.trackedAdvancementOrder = compiledTracking.orderedAdvancements();
        this.trackedAdvancements = compiledTracking.trackedAdvancements();
        this.placeholderRegistrations = compiledTracking.placeholderRegistrations();
    }

    public void start() {
        if (!placeholderHook.isAvailable()) {
            GLogger.warning("未检测到可用的 PlaceholderAPI，插件仅支持 placeholder 表达式判定，条件服务未启动");
            return;
        }
        if (trackedAdvancementOrder.isEmpty()) {
            GLogger.warning("未发现可用的 placeholder 条件表达式，条件服务未启动");
            return;
        }
        long interval = gconfig.getPlaceholderCheckIntervalTicks();
        GLogger.info("&f开始启动 placeholder 条件服务，已注册 "
                + trackedAdvancementOrder.size() + " 个进度、"
                + placeholderRegistrations.size() + " 个占位符，轮询间隔=" + interval + " ticks");
        this.task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::checkOnlinePlayers,
                interval,
                interval
        );
        checkOnlinePlayers();
        GLogger.info("&a已启用 placeholder 条件检查");
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
        cleanupOfflinePlayers();
        GLogger.debug("开始轮询在线玩家 placeholder 条件，当前在线: " + plugin.getServer().getOnlinePlayers().size()
                + "，缓存玩家数=" + playerStates.size());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            evaluatePlayer(player);
        }
    }

    private void evaluatePlayer(Player player) {
        PlayerPlaceholderState state = playerStates.computeIfAbsent(player.getUniqueId(), uuid -> createPlayerState(player));
        if (state.isFullyCompleted()) {
            return;
        }

        Set<String> changedPlaceholders = updatePlaceholderCaches(player, state);
        if (changedPlaceholders.isEmpty()) {
            GLogger.debug("玩家 " + player.getName() + " 的占位符数据未发生变化，跳过成就判定");
            return;
        }

        Set<String> affectedAdvancementKeys = collectAffectedAdvancements(state, changedPlaceholders);
        if (affectedAdvancementKeys.isEmpty()) {
            GLogger.debug("玩家 " + player.getName() + " 的占位符虽有变化，但没有影响到任何未完成进度");
            return;
        }

        for (TrackedAdvancement trackedAdvancement : sortAffectedAdvancements(affectedAdvancementKeys)) {
            if (!state.isAdvancementActive(trackedAdvancement.key())) {
                continue;
            }
            Advancement advancement = trackedAdvancement.advancement();
            if (advancement.isGranted(player)) {
                releaseAdvancementDependencies(player, state, trackedAdvancement);
                continue;
            }
            GLogger.debug("检查玩家 " + player.getName() + " 的进度条件: " + trackedAdvancement.key());
            if (trackedAdvancement.matches(state::getCurrentValue)) {
                advancement.grant(player);
                if (advancement.isGranted(player)) {
                    GLogger.debug("玩家 " + player.getName() + " 满足 placeholder 条件，授予进度 " + trackedAdvancement.key());
                    executeCommands(player, trackedAdvancement);
                    releaseAdvancementDependencies(player, state, trackedAdvancement);
                }
            }
        }

        if (state.isFullyCompleted()) {
            GLogger.debug("玩家 " + player.getName() + " 的 placeholder 缓存已全部释放");
        }
    }

    private CompiledTracking compileTrackedAdvancements(
            List<GAdvancement> definitions,
            Map<String, Advancement> registeredAdvancements
    ) {
        Map<String, TrackedAdvancement> trackedAdvancementMap = new LinkedHashMap<>();
        Map<String, PlaceholderRegistration> registrations = new LinkedHashMap<>();
        List<TrackedAdvancement> orderedResult = new ArrayList<>();
        List<GAdvancement> orderedDefinitions = definitions.stream()
                .sorted(Comparator.comparing(GAdvancement::isRoot).reversed())
                .toList();
        int order = 0;

        for (GAdvancement definition : orderedDefinitions) {
            String key = buildKey(definition);
            List<PlaceholderConditionExpression> expressions = new ArrayList<>();
            Set<String> placeholders = new LinkedHashSet<>();
            boolean hasUnsupportedCondition = false;

            for (String rawCondition : definition.getCondition()) {
                String expressionSource = unwrapPlaceholderExpression(rawCondition);
                if (expressionSource == null) {
                    hasUnsupportedCondition = true;
                    continue;
                }
                try {
                    PlaceholderConditionExpression expression = PlaceholderConditionExpression.compile(expressionSource);
                    expressions.add(expression);
                    placeholders.addAll(expression.getPlaceholders());
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
            if (placeholders.isEmpty()) {
                GLogger.warning("进度 " + key + " 未解析到 placeholder 依赖，已跳过自动判断");
                continue;
            }

            Advancement advancement = registeredAdvancements.get(key);
            if (advancement == null) {
                GLogger.warning("进度 " + key + " 尚未注册到 UAA，跳过 placeholder 跟踪");
                continue;
            }
            TrackedAdvancement trackedAdvancement = new TrackedAdvancement(
                    key,
                    advancement,
                    List.copyOf(expressions),
                    List.copyOf(definition.getCommands()),
                    Set.copyOf(placeholders),
                    order++
            );
            trackedAdvancementMap.put(key, trackedAdvancement);
            orderedResult.add(trackedAdvancement);
            for (String placeholder : placeholders) {
                registrations.computeIfAbsent(placeholder, PlaceholderRegistration::new).addAdvancement(key);
            }
            GLogger.info("&f已跟踪 placeholder 条件进度: " + key
                    + "，表达式数量=" + expressions.size()
                    + "，依赖占位符=" + placeholders.size());
        }
        GLogger.info("&fplaceholder 依赖图构建完成，共注册 "
                + trackedAdvancementMap.size() + " 个进度、"
                + registrations.size() + " 个占位符索引");
        return new CompiledTracking(
                List.copyOf(orderedResult),
                Map.copyOf(trackedAdvancementMap),
                Map.copyOf(registrations)
        );
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

    private PlayerPlaceholderState createPlayerState(Player player) {
        PlayerPlaceholderState state = new PlayerPlaceholderState();
        for (TrackedAdvancement trackedAdvancement : trackedAdvancementOrder) {
            if (trackedAdvancement.advancement().isGranted(player)) {
                continue;
            }
            state.activateAdvancement(trackedAdvancement.key());
            for (String placeholder : trackedAdvancement.placeholders()) {
                state.retainPlaceholder(placeholder);
            }
        }
        GLogger.debug("初始化玩家 " + player.getName() + " 的 placeholder 缓存，未完成进度="
                + state.getActiveAdvancementCount() + "，活跃占位符=" + state.getActivePlaceholderCount());
        return state;
    }

    private Set<String> updatePlaceholderCaches(Player player, PlayerPlaceholderState state) {
        Set<String> changedPlaceholders = new LinkedHashSet<>();
        for (String placeholder : state.getActivePlaceholders()) {
            String newValue = placeholderHook.resolve(player, placeholder);
            if (state.updatePlaceholderValue(placeholder, newValue)) {
                changedPlaceholders.add(placeholder);
            }
        }
        GLogger.debug("玩家 " + player.getName() + " 本轮更新占位符="
                + state.getActivePlaceholderCount() + "，发生变化=" + changedPlaceholders.size());
        return changedPlaceholders;
    }

    private Set<String> collectAffectedAdvancements(PlayerPlaceholderState state, Set<String> changedPlaceholders) {
        Set<String> affectedAdvancements = new LinkedHashSet<>();
        for (String placeholder : changedPlaceholders) {
            PlaceholderRegistration registration = placeholderRegistrations.get(placeholder);
            if (registration == null) {
                continue;
            }
            for (String advancementKey : registration.advancementKeys()) {
                if (state.isAdvancementActive(advancementKey)) {
                    affectedAdvancements.add(advancementKey);
                }
            }
        }
        return affectedAdvancements;
    }

    private List<TrackedAdvancement> sortAffectedAdvancements(Set<String> advancementKeys) {
        List<TrackedAdvancement> result = new ArrayList<>();
        for (String advancementKey : advancementKeys) {
            TrackedAdvancement trackedAdvancement = trackedAdvancements.get(advancementKey);
            if (trackedAdvancement != null) {
                result.add(trackedAdvancement);
            }
        }
        result.sort(Comparator.comparingInt(TrackedAdvancement::order));
        return result;
    }

    private void releaseAdvancementDependencies(Player player, PlayerPlaceholderState state, TrackedAdvancement trackedAdvancement) {
        if (!state.deactivateAdvancement(trackedAdvancement.key())) {
            return;
        }
        for (String placeholder : trackedAdvancement.placeholders()) {
            int remainingRefs = state.releasePlaceholder(placeholder);
            if (remainingRefs <= 0) {
                GLogger.debug("玩家 " + player.getName() + " 的占位符缓存已释放: "
                        + placeholder + "，不再被未完成进度引用");
            } else {
                GLogger.debug("玩家 " + player.getName() + " 的占位符缓存继续保留: "
                        + placeholder + "，剩余未完成引用=" + remainingRefs);
            }
        }
    }

    private void executeCommands(Player player, TrackedAdvancement trackedAdvancement) {
        if (trackedAdvancement.commands().isEmpty()) {
            return;
        }
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        for (String rawCommand : trackedAdvancement.commands()) {
            String command = renderCommand(player, rawCommand);
            if (command == null || command.isBlank()) {
                continue;
            }
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            GLogger.debug("以控制台身份执行成就指令: " + trackedAdvancement.key() + " -> " + normalized);
            boolean success = Bukkit.dispatchCommand(console, normalized);
            if (!success) {
                GLogger.warning("成就 " + trackedAdvancement.key() + " 的指令执行失败: " + normalized);
            }
        }
    }

    private String renderCommand(Player player, String rawCommand) {
        if (rawCommand == null) {
            return null;
        }
        String trimmed = rawCommand.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.contains("%")) {
            String parsed = placeholderHook.resolve(player, trimmed);
            if (parsed != null && !parsed.isBlank()) {
                return parsed;
            }
        }
        return trimmed;
    }

    private void cleanupOfflinePlayers() {
        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
        }
        playerStates.keySet().removeIf(uuid -> !onlinePlayers.contains(uuid));
    }

    private record CompiledTracking(
            List<TrackedAdvancement> orderedAdvancements,
            Map<String, TrackedAdvancement> trackedAdvancements,
            Map<String, PlaceholderRegistration> placeholderRegistrations
    ) {
    }

    private record TrackedAdvancement(
            String key,
            Advancement advancement,
            List<PlaceholderConditionExpression> expressions,
            List<String> commands,
            Set<String> placeholders,
            int order
    ) {
        boolean matches(PlaceholderConditionExpression.PlaceholderValueResolver resolver) {
            for (PlaceholderConditionExpression expression : expressions) {
                if (!expression.test(resolver)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class PlaceholderRegistration {
        private final String placeholder;
        private final Set<String> advancementKeys = new LinkedHashSet<>();

        private PlaceholderRegistration(String placeholder) {
            this.placeholder = placeholder;
        }

        private void addAdvancement(String advancementKey) {
            advancementKeys.add(advancementKey);
        }

        private Set<String> advancementKeys() {
            return advancementKeys;
        }

        @SuppressWarnings("unused")
        private String placeholder() {
            return placeholder;
        }
    }

    private static final class PlayerPlaceholderState {
        private final Set<String> activeAdvancements = new LinkedHashSet<>();
        private final Map<String, Integer> placeholderReferences = new HashMap<>();
        private final Map<String, PlaceholderValueSnapshot> placeholderSnapshots = new HashMap<>();

        void activateAdvancement(String advancementKey) {
            activeAdvancements.add(advancementKey);
        }

        boolean deactivateAdvancement(String advancementKey) {
            return activeAdvancements.remove(advancementKey);
        }

        boolean isAdvancementActive(String advancementKey) {
            return activeAdvancements.contains(advancementKey);
        }

        boolean isFullyCompleted() {
            return activeAdvancements.isEmpty();
        }

        int getActiveAdvancementCount() {
            return activeAdvancements.size();
        }

        void retainPlaceholder(String placeholder) {
            placeholderReferences.merge(placeholder, 1, Integer::sum);
        }

        int releasePlaceholder(String placeholder) {
            Integer current = placeholderReferences.get(placeholder);
            if (current == null) {
                placeholderSnapshots.remove(placeholder);
                return 0;
            }
            int remaining = current - 1;
            if (remaining <= 0) {
                placeholderReferences.remove(placeholder);
                placeholderSnapshots.remove(placeholder);
                return 0;
            }
            placeholderReferences.put(placeholder, remaining);
            return remaining;
        }

        boolean updatePlaceholderValue(String placeholder, String newValue) {
            PlaceholderValueSnapshot snapshot = placeholderSnapshots.computeIfAbsent(
                    placeholder,
                    key -> new PlaceholderValueSnapshot()
            );
            return snapshot.update(newValue);
        }

        String getCurrentValue(String placeholder) {
            PlaceholderValueSnapshot snapshot = placeholderSnapshots.get(placeholder);
            return snapshot == null ? null : snapshot.getCurrentValue();
        }

        Set<String> getActivePlaceholders() {
            return Set.copyOf(placeholderReferences.keySet());
        }

        int getActivePlaceholderCount() {
            return placeholderReferences.size();
        }
    }

    private static final class PlaceholderValueSnapshot {
        private boolean initialized;
        private String previousValue;
        private String currentValue;

        boolean update(String newValue) {
            if (!initialized) {
                initialized = true;
                previousValue = null;
                currentValue = newValue;
                return true;
            }
            if (Objects.equals(currentValue, newValue)) {
                previousValue = currentValue;
                return false;
            }
            previousValue = currentValue;
            currentValue = newValue;
            return true;
        }

        String getCurrentValue() {
            return currentValue;
        }
    }
}
