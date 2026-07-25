package org.gardin.gardinsadvancement.tabcreater;

import org.gardin.gardinsadvancement.util.GLogger;

import java.util.Locale;

public enum TabDisplayMode {
    DIRECT,
    INDIRECT,
    MANUAL;

    public static TabDisplayMode parse(String rawValue, String source) {
        if (rawValue == null || rawValue.isBlank()) {
            return DIRECT;
        }
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "direct" -> DIRECT;
            case "indirect" -> INDIRECT;
            case "manual", "off", "disabled" -> MANUAL;
            default -> {
                GLogger.warning(source + " 的 display-mode=" + rawValue + " 非法，已回退为 direct");
                yield DIRECT;
            }
        };
    }
}
