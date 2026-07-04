package com.simpchatcensor.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.Map;

public final class ColorUtil {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ColorUtil() {
    }

    public static Component component(String template, Map<String, String> placeholders) {
        return LEGACY.deserialize(apply(template, placeholders));
    }

    public static String legacyString(String template, Map<String, String> placeholders) {
        return ChatColor.translateAlternateColorCodes('&', apply(template, placeholders));
    }

    public static String apply(String template, Map<String, String> placeholders) {
        String output = template == null ? "" : template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return output;
    }
}
