package com.simpchatcensor.detection;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class EmojiDetector {
    private static final int ZERO_WIDTH_JOINER = 0x200D;
    private static final int VARIATION_SELECTOR_15 = 0xFE0E;
    private static final int VARIATION_SELECTOR_16 = 0xFE0F;
    private static final int COMBINING_ENCLOSING_KEYCAP = 0x20E3;
    private static final Pattern GRAPHEME_CLUSTER = Pattern.compile("\\X");

    Optional<Match> detect(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        // Most messages do not contain a possible Emoji code point. Avoid
        // allocating a grapheme matcher for those common messages.
        if (!containsEmojiCandidate(message)) {
            return Optional.empty();
        }

        Matcher matcher = GRAPHEME_CLUSTER.matcher(message);
        while (matcher.find()) {
            String cluster = matcher.group();
            String emoji = extractEmojiCluster(cluster);
            if (emoji != null) {
                return Optional.of(new Match(emoji, formatCodePoints(emoji)));
            }
        }
        return Optional.empty();
    }

    private static String extractEmojiCluster(String cluster) {
        if (cluster.isEmpty()) {
            return null;
        }

        int firstCodePoint = cluster.codePointAt(0);
        if (isKeycapBase(firstCodePoint)) {
            int keycapEnd = consumeKeycap(cluster, Character.charCount(firstCodePoint));
            if (keycapEnd > 0) {
                return cluster.substring(0, keycapEnd);
            }
        }

        if (isRegionalIndicator(firstCodePoint)) {
            int firstEnd = Character.charCount(firstCodePoint);
            if (firstEnd < cluster.length()) {
                int second = cluster.codePointAt(firstEnd);
                if (isRegionalIndicator(second)) {
                    return cluster.substring(0, firstEnd + Character.charCount(second));
                }
            }
            return null;
        }

        BasePart first = consumeBase(cluster, 0, false);
        if (first == null) {
            return null;
        }

        int offset = first.end();
        offset = consumeModifierAndTags(cluster, offset);
        while (offset < cluster.length() && cluster.codePointAt(offset) == ZERO_WIDTH_JOINER) {
            int joinerOffset = offset;
            BasePart next = consumeBase(cluster, offset + Character.charCount(ZERO_WIDTH_JOINER), true);
            if (next == null) {
                // A dangling joiner is not part of the Emoji that preceded it.
                break;
            }
            offset = consumeModifierAndTags(cluster, next.end());
            if (offset <= joinerOffset) {
                break;
            }
        }
        return cluster.substring(0, offset);
    }

    private static boolean containsEmojiCandidate(String message) {
        for (int offset = 0; offset < message.length(); ) {
            int codePoint = message.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isEmojiPresentation(codePoint)
                    || codePoint == VARIATION_SELECTOR_16
                    || codePoint == COMBINING_ENCLOSING_KEYCAP) {
                return true;
            }
        }
        return false;
    }

    private static BasePart consumeBase(String cluster, int offset, boolean allowComponent) {
        if (offset >= cluster.length()) {
            return null;
        }

        int codePoint = cluster.codePointAt(offset);
        if (isNonBaseComponent(codePoint, allowComponent)) {
            return null;
        }

        int end = offset + Character.charCount(codePoint);
        boolean hasVariationSelector16 = false;
        if (end < cluster.length()) {
            int variation = cluster.codePointAt(end);
            if (variation == VARIATION_SELECTOR_15 || variation == VARIATION_SELECTOR_16) {
                hasVariationSelector16 = variation == VARIATION_SELECTOR_16;
                end += Character.charCount(variation);
            }
        }

        boolean emojiBase = Character.isEmojiPresentation(codePoint)
                || (Character.isEmoji(codePoint) && hasVariationSelector16)
                || (Character.isEmojiModifierBase(codePoint)
                && end < cluster.length()
                && Character.isEmojiModifier(cluster.codePointAt(end)));
        return emojiBase ? new BasePart(end) : null;
    }

    private static boolean isNonBaseComponent(int codePoint, boolean allowComponent) {
        if (codePoint == ZERO_WIDTH_JOINER
                || codePoint == VARIATION_SELECTOR_15
                || codePoint == VARIATION_SELECTOR_16
                || codePoint == COMBINING_ENCLOSING_KEYCAP
                || isRegionalIndicator(codePoint)
                || Character.isEmojiModifier(codePoint)
                || isTag(codePoint)) {
            return true;
        }
        // Hair, skin and other Emoji components are valid only after a ZWJ;
        // accepting one as the first code point would flag a standalone part.
        return !allowComponent && Character.isEmojiComponent(codePoint);
    }

    private static int consumeModifierAndTags(String cluster, int offset) {
        if (offset < cluster.length()) {
            int codePoint = cluster.codePointAt(offset);
            if (Character.isEmojiModifier(codePoint)) {
                offset += Character.charCount(codePoint);
            }
        }
        while (offset < cluster.length() && isTag(cluster.codePointAt(offset))) {
            offset += Character.charCount(cluster.codePointAt(offset));
        }
        return offset;
    }

    private static int consumeKeycap(String cluster, int offset) {
        if (offset < cluster.length() && cluster.codePointAt(offset) == VARIATION_SELECTOR_16) {
            offset += Character.charCount(VARIATION_SELECTOR_16);
        }
        if (offset < cluster.length() && cluster.codePointAt(offset) == COMBINING_ENCLOSING_KEYCAP) {
            return offset + Character.charCount(COMBINING_ENCLOSING_KEYCAP);
        }
        return -1;
    }

    private static boolean isRegionalIndicator(int codePoint) {
        return codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF;
    }

    private static boolean isTag(int codePoint) {
        return codePoint >= 0xE0020 && codePoint <= 0xE007F;
    }

    private static boolean isKeycapBase(int codePoint) {
        return codePoint == '#' || codePoint == '*' || codePoint >= '0' && codePoint <= '9';
    }

    private static String formatCodePoints(String cluster) {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < cluster.length(); ) {
            int codePoint = cluster.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!result.isEmpty()) {
                result.append(' ');
            }
            String hex = Integer.toHexString(codePoint).toUpperCase(Locale.ROOT);
            result.append("U+");
            result.append("0".repeat(Math.max(0, 4 - hex.length())));
            result.append(hex);
        }
        return result.toString();
    }

    record Match(String detectedText, String rule) {
    }

    private record BasePart(int end) {
    }
}
