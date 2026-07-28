package org.gardin.gardinsadvancement.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.gardin.gardinsadvancement.Gardinsadvancement;
import org.gardin.gardinsadvancement.service.PlaceholderConditionService;
import org.gardin.gardinsadvancement.util.GLogger;
import org.gardin.gardinsadvancement.util.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class commandsRegister implements CommandExecutor, TabCompleter {
    private static final String RELOAD_PERMISSION = "gardinsadvancement.reload";
    private static final String MANAGE_PERMISSION = "gardinsadvancement.manage";
    private final Gardinsadvancement plugin;

    public commandsRegister(Gardinsadvancement plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (plugin.getCommand("gardinsadvancement") == null) {
            GLogger.errorLang("commands.command_missing", "gardinsadvancement");
            return;
        }
        plugin.getCommand("gardinsadvancement").setExecutor(this);
        plugin.getCommand("gardinsadvancement").setTabCompleter(this);
        GLogger.infoLang("commands.registered", "/gardinsadvancement reload|grant|revoke");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "grant" -> handleGrant(sender, label, args);
            case "revoke" -> handleRevoke(sender, label, args);
            default -> {
                Lang.send(sender, "commands.unknown_subcommand", args[0]);
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return filterPrefix(List.of("reload", "grant", "revoke"), args[0]);
        }
        if (args.length == 2 && isManageSubcommand(args[0])) {
            return filterPrefix(getOnlinePlayerNames(), args[1]);
        }
        if (args.length == 3 && isManageSubcommand(args[0])) {
            List<String> keys = new ArrayList<>();
            keys.add(PlaceholderConditionService.ALL_ADVANCEMENTS_KEY);
            keys.addAll(plugin.getManageableAdvancementKeys());
            return filterPrefix(keys, args[2]);
        }
        return List.of();
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            Lang.send(sender, "commands.no_permission");
            return true;
        }
        plugin.reloadSettings();
        Lang.send(sender, "commands.reload_success");
        Lang.send(sender, "commands.reload_interval", plugin.getGconfig().getPlaceholderCheckIntervalTicks());
        Lang.send(sender, "commands.reload_notice");
        return true;
    }

    private boolean handleGrant(CommandSender sender, String label, String[] args) {
        return handleManage(sender, label, args, true);
    }

    private boolean handleRevoke(CommandSender sender, String label, String[] args) {
        return handleManage(sender, label, args, false);
    }

    private boolean handleManage(CommandSender sender, String label, String[] args, boolean grant) {
        if (!sender.hasPermission(MANAGE_PERMISSION)) {
            Lang.send(sender, "commands.no_permission");
            return true;
        }
        if (args.length < 3) {
            Lang.send(sender, grant ? "commands.grant_usage" : "commands.revoke_usage", label);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Lang.send(sender, "commands.player_not_found", args[1]);
            return true;
        }

        String advancementKey = args[2];
        boolean success = grant
                ? plugin.grantAdvancement(target, advancementKey)
                : plugin.revokeAdvancement(target, advancementKey);
        if (!success) {
            Lang.send(
                    sender,
                    "commands.manage_failed",
                    grant ? "grant" : "revoke",
                    target.getName(),
                    advancementKey
            );
            return true;
        }

        Lang.send(
                sender,
                grant ? "commands.grant_success" : "commands.revoke_success",
                target.getName(),
                advancementKey
        );
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        Lang.send(sender, "commands.usage", label);
        Lang.send(sender, "commands.reload_usage", label);
        Lang.send(sender, "commands.grant_usage", label);
        Lang.send(sender, "commands.revoke_usage", label);
    }

    private boolean isManageSubcommand(String input) {
        return "grant".equalsIgnoreCase(input) || "revoke".equalsIgnoreCase(input);
    }

    private List<String> getOnlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filterPrefix(List<String> candidates, String input) {
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
