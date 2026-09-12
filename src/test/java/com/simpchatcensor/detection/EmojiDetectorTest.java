package com.simpchatcensor.detection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmojiDetectorTest {
    private final EmojiDetector detector = new EmojiDetector();

    @Test
    void returnsCompleteEmojiGraphemeClusters() {
        assertMatch("你🐎死了", "🐎", "U+1F40E");
        assertMatch("爱心❤️", "❤️", "U+2764 U+FE0F");
        assertMatch("家庭👨‍👩‍👧‍👦", "👨‍👩‍👧‍👦", "U+1F468 U+200D U+1F469 U+200D U+1F467 U+200D U+1F466");
        assertMatch("国旗🇨🇳", "🇨🇳", "U+1F1E8 U+1F1F3");
        assertMatch("按键1️⃣", "1️⃣", "U+0031 U+FE0F U+20E3");
        assertMatch("肤色👍🏽", "👍🏽", "U+1F44D U+1F3FD");
        assertMatch("指向☝🏽", "☝🏽", "U+261D U+1F3FD");
        assertMatch("胜利✌🏽", "✌🏽", "U+270C U+1F3FD");
        assertMatch("红发👩‍🦰", "👩‍🦰", "U+1F469 U+200D U+1F9B0");
        assertMatch("无变体键帽#⃣", "#⃣", "U+0023 U+20E3");
    }

    @Test
    void doesNotTreatOrdinaryTextSymbolsAsEmoji() {
        for (String message : List.of(
                "版权 ©",
                "商标 ™",
                "文本心形 ❤",
                "方向 →",
                "普通中文",
                "ASCII :-)",
                "孤立区域🇨",
                "孤立肤色🏽",
                "字母a🏽"
        )) {
            assertTrue(detector.detect(message).isEmpty(), message);
        }
    }

    @Test
    void trimsDanglingJoinerInsteadOfReturningItAsPartOfTheMatch() {
        EmojiDetector.Match match = detector.detect("🐎\u200D").orElseThrow();
        assertEquals("🐎", match.detectedText());
        assertEquals("U+1F40E", match.rule());
    }

    private void assertMatch(String message, String detectedText, String rule) {
        EmojiDetector.Match match = detector.detect(message).orElseThrow();
        assertEquals(detectedText, match.detectedText());
        assertEquals(rule, match.rule());
    }
}
