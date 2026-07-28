package org.gardin.gardinsadvancement.util;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public final class GLogger {
    private static final String PREFIX = "&6[GardinsAdvancement]&r ";
    private static boolean debug = false;
    private GLogger() {}
    /*** 发送消息给玩家*/
    public static void player(Player player, String message) {
        player.sendMessage(Lang.color(message));
    }

    public static void playerLang(Player player, String key, Object... args) {
        player.sendMessage(Lang.color(Lang.text(key, args)));
    }
    /*** 系统普通信息*/
    public static void info(String message) {
        logToConsole("", message);
    }

    public static void infoLang(String key, Object... args) {
        info(Lang.text(key, args));
    }
    /*** 系统警告*/
    public static void warning(String message) {
        logToConsole("&e[WARN]&r ", message);
    }

    public static void warningLang(String key, Object... args) {
        warning(Lang.text(key, args));
    }
    /** 系统错误 */
    public static void error(String message) {
        logToConsole("&c[ERROR]&r ", message);
    }

    public static void errorLang(String key, Object... args) {
        error(Lang.text(key, args));
    }
    /**Debug信息*/
    public static void debug(String message) {
        if(!debug){return;}
        logToConsole("&8[DEBUG]&r ", message);
    }

    public static void debugLang(String key, Object... args) {
        if (!debug) {
            return;
        }
        debug(Lang.text(key, args));
    }
    /**设置debug*/
    public static void setDebug(boolean enable) {
        debug = enable;
        infoLang(enable ? "logger.debug_enabled" : "logger.debug_disabled");
    }
    /** 颜色转换*/
    private static String color(String message) {
        return Lang.color(message);
    }

    private static void logToConsole(String levelPrefix, String message) {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(color(PREFIX + levelPrefix + message));
    }
}
