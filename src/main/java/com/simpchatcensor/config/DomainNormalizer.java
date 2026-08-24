package com.simpchatcensor.config;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Converts domain configuration and detector candidates to one ASCII form.
 * Keeping this in the config package makes whitelist and suffix values follow
 * exactly the same IDN rules as the runtime detector.
 */
public final class DomainNormalizer {
    public static final String SEPARATOR_CHARS = ".,，。．｡﹒､、";

    private static final Pattern BRACKET_DOT = Pattern.compile("\\h*\\[\\h*[.．]\\h*]\\h*");
    private static final Pattern SEPARATOR_WITH_SPACE = Pattern.compile(
            "\\h*[" + SEPARATOR_CHARS + "]\\h*"
    );

    private DomainNormalizer() {
    }

    /**
     * Returns a lower-case ASCII host, or an empty string when the value is
     * not a valid IDN host. A host may contain one or more labels here; the
     * detector separately requires at least two labels before matching.
     */
    public static String canonicalizeHost(String value) {
        if (value == null) {
            return "";
        }

        String normalized = normalizeSeparators(value.trim());
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty() || containsWhitespace(normalized)) {
            return "";
        }

        StringBuilder asciiHost = new StringBuilder(normalized.length());
        String[] labels = normalized.split("\\.", -1);
        for (String label : labels) {
            if (!isValidUnicodeLabel(label)) {
                return "";
            }

            final String ascii;
            try {
                ascii = IDN.toASCII(label, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            } catch (IllegalArgumentException exception) {
                return "";
            }
            if (!isValidAsciiLabel(ascii)) {
                return "";
            }

            if (asciiHost.length() > 0) {
                asciiHost.append('.');
            }
            asciiHost.append(ascii);
        }

        String result = asciiHost.toString();
        return result.getBytes(StandardCharsets.US_ASCII).length <= 253 ? result : "";
    }

    public static String normalizeSeparators(String value) {
        String normalized = BRACKET_DOT.matcher(value).replaceAll(".");
        return SEPARATOR_WITH_SPACE.matcher(normalized).replaceAll(".");
    }

    public static boolean isDomainLabelCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '-';
    }

    public static boolean isSeparator(char value) {
        return SEPARATOR_CHARS.indexOf(value) >= 0;
    }

    public static boolean isHorizontalWhitespace(char value) {
        return value == ' '
                || value == '\t'
                || value == '\u00a0'
                || value == '\u1680'
                || (value >= '\u2000' && value <= '\u200a')
                || value == '\u202f'
                || value == '\u205f'
                || value == '\u3000';
    }

    private static boolean isValidUnicodeLabel(String label) {
        if (label.isEmpty() || label.length() > 63
                || label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        for (int index = 0; index < label.length(); index++) {
            char current = label.charAt(index);
            if (!Character.isLetterOrDigit(current) && current != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidAsciiLabel(String label) {
        if (label.isEmpty() || label.length() > 63
                || label.startsWith("-") || label.endsWith("-")) {
            return false;
        }
        for (int index = 0; index < label.length(); index++) {
            char current = label.charAt(index);
            if (!isAsciiAlphaNumeric(current) && current != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiAlphaNumeric(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9');
    }

    private static boolean containsWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }
}
