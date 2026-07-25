package org.gardin.gardinsadvancement.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class GLogger {
    private static final String PREFIX = "&6[GardinsAdvancement]&r ";
    private static boolean debug = false;
    private GLogger() {}
    /*** 发送消息给玩家*/
    public static void player(Player player, String message) {
        player.sendMessage(
                ChatColor.translateAlternateColorCodes('&', message)
        );
    }
    /*** 系统普通信息*/
    public static void info(String message) {
        Bukkit.getLogger().info(
                color(PREFIX + message)
        );
    }
    /*** 系统警告*/
    public static void warning(String message) {
        Bukkit.getLogger().warning(
                color(PREFIX + message)
        );
    }
    /** 系统错误 */
    public static void error(String message) {
        Bukkit.getLogger().severe(
                color(PREFIX + message)
        );
    }
    /**Debug信息*/
    public static void debug(String message) {
        if(!debug){return;}
        Bukkit.getLogger().info(color(PREFIX + "&8[DEBUG]&r " + message));
    }
    /**设置debug*/
    public static void setDebug(boolean enable) {
        debug = enable;
        info("Debug 日志已" + (enable ? "&a启用" : "&c禁用"));
    }
    /** 颜色转换*/
    private static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
