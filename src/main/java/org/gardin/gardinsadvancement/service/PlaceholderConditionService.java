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
    public static final String ALL_ADVANCEMENTS_KEY = "all";
    private static final String PREFIX = "placeholder:";

    private final JavaPlugin plugin;
    private Gconfig gconfig;
    private final PlaceholderHook placeholderHook;
    private final Map<String, Advancement> registeredAdvancements;
    private final Map<String, ManagedAdvancement> managedAdvancements;
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
        this.registeredAdvancements = Map.copyOf(advancementRegister.getRegisteredAdvancements());
        AdvancementIndex advancementIndex = buildAdvancementIndex(
                contentDocument.getAdvancements(),
                this.registeredAdvancements
        );
        this.managedAdvancements = advancementIndex.managedAdvancements();
        CompiledTracking compiledTracking = compileTrackedAdvancements(
                contentDocument.getAdvancements(),
                this.registeredAdvancements
        );
        this.trackedAdvancementOrder = compiledTracking.orderedAdvancements();
        this.trackedAdvancements = compiledTracking.trackedAdvancements();
        this.placeholderRegistrations = compiledTracking.placeholderRegistrations();
    }

    public void start() {
        if (!placeholderHook.isAvailable()) {
            GLogger.warningLang("service.placeholder_api_missing");
            return;
        }
        if (trackedAdvancementOrder.isEmpty()) {
            GLogger.warningLang("service.no_tracked_conditions");
            return;
        }
        long interval = gconfig.getPlaceholderCheckIntervalTicks();
        GLogger.infoLang(
                "service.start",
                trackedAdvancementOrder.size(),
                placeholderRegistrations.size(),
                interval
        );
        this.task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::checkOnlinePlayers,
                interval,
                interval
        );
        checkOnlinePlayers();
        GLogger.infoLang("service.enabled");
    }

    public void reloadSettings(Gconfig gconfig) {
        this.gconfig = gconfig;
        if (task != null) {
            stop();
            start();
            GLogger.infoLang("service.reloaded", gconfig.getPlaceholderCheckIntervalTicks());
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
            GLogger.infoLang("service.stopped");
        }
    }

    public boolean grantAdvancement(Player player, String tabId, String advancementId) {
        return grantAdvancement(player, buildKey(tabId, advancementId));
    }

    public boolean grantAdvancement(Player player, String advancementKey) {
        ManagedAdvancement managedAdvancement = getManagedAdvancement(advancementKey);
        if (player == null || managedAdvancement == null) {
            return false;
        }
        ensureParentPathGranted(player, managedAdvancement);
        Advancement advancement = managedAdvancement.advancement();
        if (advancement.isGranted(player)) {
            syncPlayerState(player);
            GLogger.debugLang("service.manual_grant_skipped", player.getName(), managedAdvancement.key());
            return true;
        }

        advancement.grant(player);
        boolean granted = advancement.isGranted(player);
        syncPlayerState(player);
        if (!granted) {
            GLogger.warningLang("service.manual_grant_failed", player.getName(), managedAdvancement.key());
            return false;
        }

        GLogger.infoLang("service.manual_grant_applied", player.getName(), managedAdvancement.key());
        executeCommands(player, managedAdvancement.key(), managedAdvancement.commands());
        return true;
    }

    public boolean grantAllAdvancements(Player player) {
        if (player == null) {
            return false;
        }
        int grantedCount = 0;
        List<ManagedAdvancement> ordered = new ArrayList<>(managedAdvancements.values());
        ordered.sort(Comparator.comparingInt(advancement -> depthOf(advancement.key())));
        for (ManagedAdvancement managedAdvancement : ordered) {
            Advancement advancement = managedAdvancement.advancement();
            if (advancement.isGranted(player)) {
                continue;
            }
            advancement.grant(player);
            if (!advancement.isGranted(player)) {
                GLogger.warningLang("service.manual_grant_failed", player.getName(), managedAdvancement.key());
                continue;
            }
            grantedCount++;
            syncPlayerState(player);
            executeCommands(player, managedAdvancement.key(), managedAdvancement.commands());
        }
        syncPlayerState(player);
        GLogger.infoLang("service.manual_grant_all_applied", player.getName(), grantedCount);
        return true;
    }

    public boolean revokeAdvancement(Player player, String tabId, String advancementId) {
        return revokeAdvancement(player, buildKey(tabId, advancementId));
    }

    public boolean revokeAdvancement(Player player, String advancementKey) {
        ManagedAdvancement managedAdvancement = getManagedAdvancement(advancementKey);
        if (player == null || managedAdvancement == null) {
            return false;
        }

        Advancement advancement = managedAdvancement.advancement();
        boolean revoked = false;
        if (advancement.isGranted(player)) {
            advancement.revoke(player);
            revoked = true;
        }
        syncPlayerState(player);
        if (!revoked) {
            GLogger.debugLang("service.manual_revoke_skipped", player.getName(), managedAdvancement.key());
            return true;
        }

        GLogger.infoLang("service.manual_revoke_applied", player.getName(), managedAdvancement.key());
        return true;
    }

    public boolean revokeAllAdvancements(Player player) {
        if (player == null) {
            return false;
        }
        int revokedCount = 0;
        List<ManagedAdvancement> ordered = new ArrayList<>(managedAdvancements.values());
        ordered.sort(Comparator.comparingInt((ManagedAdvancement advancement) -> depthOf(advancement.key())).reversed());
        for (ManagedAdvancement managedAdvancement : ordered) {
            Advancement advancement = managedAdvancement.advancement();
            if (!advancement.isGranted(player)) {
                continue;
            }
            advancement.revoke(player);
            revokedCount++;
        }
        syncPlayerState(player);
        GLogger.infoLang("service.manual_revoke_all_applied", player.getName(), revokedCount);
        return true;
    }

    public List<String> getManagedAdvancementKeys() {
        List<String> result = new ArrayList<>(managedAdvancements.keySet());
        result.sort(String::compareToIgnoreCase);
        return List.copyOf(result);
    }

    public void syncPlayerState(Player player) {
        if (player == null) {
            return;
        }
        PlayerPlaceholderState state = createPlayerState(player);
        playerStates.put(player.getUniqueId(), state);
        GLogger.debugLang(
                "service.player_state_synchronized",
                player.getName(),
                state.getActiveAdvancementCount(),
                state.getActivePlaceholderCount()
        );
    }

    private void checkOnlinePlayers() {
        cleanupOfflinePlayers();
        GLogger.debugLang(
                "service.poll_start",
                plugin.getServer().getOnlinePlayers().size(),
                playerStates.size()
        );
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
            GLogger.debugLang("service.no_placeholder_change", player.getName());
            return;
        }

        Set<String> affectedAdvancementKeys = collectAffectedAdvancements(state, changedPlaceholders);
        if (affectedAdvancementKeys.isEmpty()) {
            GLogger.debugLang("service.no_affected_advancements", player.getName());
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
            GLogger.debugLang("service.evaluating", player.getName(), trackedAdvancement.key());
            if (trackedAdvancement.matches(state::getCurrentValue)) {
                advancement.grant(player);
                if (advancement.isGranted(player)) {
                    GLogger.debugLang("service.player_granted", player.getName(), trackedAdvancement.key());
                    releaseAdvancementDependencies(player, state, trackedAdvancement);
                    executeCommands(player, trackedAdvancement.key(), trackedAdvancement.commands());
                }
            }
        }

        if (state.isFullyCompleted()) {
            GLogger.debugLang("service.player_cache_released", player.getName());
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
                    GLogger.debugLang("service.condition_compiled", key, expressionSource);
                } catch (IllegalArgumentException exception) {
                    GLogger.errorLang("service.condition_parse_failed", key, exception.getMessage());
                    hasUnsupportedCondition = true;
                    break;
                }
            }

            if (expressions.isEmpty()) {
                GLogger.warningLang("service.no_valid_expressions", key);
                continue;
            }
            if (hasUnsupportedCondition) {
                GLogger.warningLang("service.unsupported_condition", key);
                continue;
            }
            if (placeholders.isEmpty()) {
                GLogger.warningLang("service.no_placeholder_dependency", key);
                continue;
            }

            Advancement advancement = registeredAdvancements.get(key);
            if (advancement == null) {
                GLogger.warningLang("service.advancement_not_registered", key);
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
            GLogger.infoLang("service.tracked_advancement", key, expressions.size(), placeholders.size());
        }
        GLogger.infoLang("service.tracking_complete", trackedAdvancementMap.size(), registrations.size());
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

    private String buildKey(String tabId, String advancementId) {
        return tabId + ":" + advancementId;
    }

    private AdvancementIndex buildAdvancementIndex(
            List<GAdvancement> definitions,
            Map<String, Advancement> registeredAdvancements
    ) {
        Map<String, ManagedAdvancement> managed = new LinkedHashMap<>();
        for (GAdvancement definition : definitions) {
            String key = buildKey(definition);
            Advancement advancement = registeredAdvancements.get(key);
            if (advancement == null) {
                continue;
            }
            String parentKey = null;
            if (!definition.isRoot() && definition.getParentId() != null && !definition.getParentId().isBlank()) {
                parentKey = buildKey(definition.getTab(), definition.getParentId());
            }
            managed.put(
                    key,
                    new ManagedAdvancement(
                            key,
                            advancement,
                            List.copyOf(definition.getCommands()),
                            parentKey
                    )
            );
        }
        return new AdvancementIndex(Map.copyOf(managed));
    }

    private ManagedAdvancement getManagedAdvancement(String advancementKey) {
        if (advancementKey == null || advancementKey.isBlank()) {
            return null;
        }
        String normalized = advancementKey.trim();
        ManagedAdvancement managedAdvancement = managedAdvancements.get(normalized);
        if (managedAdvancement == null) {
            GLogger.warningLang("service.manual_advancement_missing", normalized);
        }
        return managedAdvancement;
    }

    private void ensureParentPathGranted(Player player, ManagedAdvancement managedAdvancement) {
        List<ManagedAdvancement> ancestors = new ArrayList<>();
        String currentKey = managedAdvancement.parentKey();
        while (currentKey != null) {
            ManagedAdvancement parent = managedAdvancements.get(currentKey);
            if (parent == null) {
                break;
            }
            ancestors.add(parent);
            currentKey = parent.parentKey();
        }
        ancestors.sort(Comparator.comparingInt(ancestor -> depthOf(ancestor.key())));
        for (ManagedAdvancement ancestor : ancestors) {
            if (ancestor.advancement().isGranted(player)) {
                continue;
            }
            ancestor.advancement().grant(player);
            if (ancestor.advancement().isGranted(player)) {
                GLogger.debugLang("service.parent_auto_granted", player.getName(), ancestor.key(), managedAdvancement.key());
            } else {
                GLogger.warningLang("service.parent_auto_grant_failed", player.getName(), ancestor.key(), managedAdvancement.key());
            }
        }
    }

    private int depthOf(String advancementKey) {
        int depth = 0;
        ManagedAdvancement current = managedAdvancements.get(advancementKey);
        while (current != null && current.parentKey() != null) {
            depth++;
            current = managedAdvancements.get(current.parentKey());
        }
        return depth;
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
        GLogger.debugLang(
                "service.player_state_initialized",
                player.getName(),
                state.getActiveAdvancementCount(),
                state.getActivePlaceholderCount()
        );
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
        GLogger.debugLang(
                "service.cache_updated",
                player.getName(),
                state.getActivePlaceholderCount(),
                changedPlaceholders.size()
        );
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
                GLogger.debugLang("service.placeholder_released", player.getName(), placeholder);
            } else {
                GLogger.debugLang("service.placeholder_retained", player.getName(), placeholder, remainingRefs);
            }
        }
    }

    private void executeCommands(Player player, String advancementKey, List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        for (String rawCommand : commands) {
            String command = renderCommand(player, rawCommand);
            if (command == null || command.isBlank()) {
                continue;
            }
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            GLogger.debugLang("service.command_execute", advancementKey, normalized);
            boolean success = Bukkit.dispatchCommand(console, normalized);
            if (!success) {
                GLogger.warningLang("service.command_failed", advancementKey, normalized);
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

    private record AdvancementIndex(
            Map<String, ManagedAdvancement> managedAdvancements
    ) {
    }

    private record ManagedAdvancement(
            String key,
            Advancement advancement,
            List<String> commands,
            String parentKey
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
