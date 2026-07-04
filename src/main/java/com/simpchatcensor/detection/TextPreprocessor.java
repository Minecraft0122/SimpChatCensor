package com.simpchatcensor.detection;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextPreprocessor {
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private TextPreprocessor() {
    }

    public static String basicLower(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }

    public static String advanced(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD);
        normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");

        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char c = Character.toLowerCase(normalized.charAt(i));
            if (Character.isWhitespace(c)) {
                continue;
            }
            builder.append(restoreLeet(c));
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    public static List<String> tokens(String input, boolean advanced) {
        String source = advanced ? advancedTokenSource(input) : basicLower(input);
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int offset = 0; offset < source.length(); ) {
            int codePoint = source.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isLetterOrDigit(codePoint)) {
                current.appendCodePoint(Character.toLowerCase(codePoint));
                continue;
            }

            if (!current.isEmpty()) {
                tokens.add(current.toString());
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String advancedTokenSource(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(input.length());
        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            int charIndex = offset;
            offset += Character.charCount(codePoint);

            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                builder.appendCodePoint(codePoint);
                continue;
            }

            String normalized = Normalizer.normalize(
                    new String(Character.toChars(codePoint)),
                    Normalizer.Form.NFKD
            );
            normalized = COMBINING_MARKS.matcher(normalized).replaceAll("");
            for (int i = 0; i < normalized.length(); i++) {
                appendTokenSourceChar(builder, Character.toLowerCase(normalized.charAt(i)), input, charIndex);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static void appendTokenSourceChar(StringBuilder builder, char c, String original, int charIndex) {
        if (Character.isLetter(c)) {
            builder.append(c);
            return;
        }
        if (Character.isDigit(c)) {
            builder.append(restoreLeet(c));
            return;
        }
        if (isRestorableSymbol(c) && shouldRestoreSymbol(original, charIndex, c)) {
            builder.append(restoreLeet(c));
            return;
        }
        builder.append(' ');
    }

    private static boolean isRestorableSymbol(char c) {
        return c == '@' || c == '$' || c == '!' || c == '+';
    }

    private static boolean shouldRestoreSymbol(String input, int charIndex, char symbol) {
        boolean previousAscii = charIndex > 0 && isAsciiLetterOrDigit(input.charAt(charIndex - 1));
        int nextIndex = charIndex + 1;
        boolean nextAscii = nextIndex < input.length() && isAsciiLetterOrDigit(input.charAt(nextIndex));
        if (symbol == '!') {
            return previousAscii && nextAscii;
        }
        return previousAscii || nextAscii;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9');
    }

    private static char restoreLeet(char c) {
        return switch (c) {
            case '4', '@' -> 'a';
            case '3' -> 'e';
            case '1', '!' -> 'i';
            case '0' -> 'o';
            case '$', '5' -> 's';
            case '7', '+' -> 't';
            default -> c;
        };
    }
}
