package org.gardin.gardinsadvancement.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.gardin.gardinsadvancement.Gardinsadvancement;
import org.gardin.gardinsadvancement.util.GLogger;

import java.util.List;

public class commandsRegister implements CommandExecutor, TabCompleter {
    private final Gardinsadvancement plugin;

    public commandsRegister(Gardinsadvancement plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (plugin.getCommand("gardinsadvancement") == null) {
            GLogger.error("命令 gardinsadvancement 未在 plugin.yml 中声明，无法注册");
            return;
        }
        plugin.getCommand("gardinsadvancement").setExecutor(this);
        plugin.getCommand("gardinsadvancement").setTabCompleter(this);
        GLogger.info("&f已注册命令: /gardinsadvancement reload");
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage("§e用法: /" + label + " reload");
            return true;
        }
        if (!args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage("§c未知子命令: " + args[0]);
            sender.sendMessage("§e用法: /" + label + " reload");
            return true;
        }
        if (!sender.hasPermission("gardinsadvancement.reload")) {
            sender.sendMessage("§c你没有权限执行该命令。");
            return true;
        }
        plugin.reloadSettings();
        sender.sendMessage("§a已重新加载设置。");
        sender.sendMessage("§7placeholder 轮询间隔: §f"
                + plugin.getGconfig().getPlaceholderCheckIntervalTicks() + " ticks");
        sender.sendMessage("§7仅 config.yml 中的运行时设置会立即生效，进度内容需重启后更新。");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
