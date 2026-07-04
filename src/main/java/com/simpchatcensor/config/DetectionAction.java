package com.simpchatcensor.config;

import java.util.Locale;

public enum DetectionAction {
    CANCEL,
    FILTER,
    WARN,
    KICK;

    public static DetectionAction fromString(String value, DetectionAction fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return DetectionAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
