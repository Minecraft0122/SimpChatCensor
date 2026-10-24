package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannedWordManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsLiteralAdvancedIpAndLevenshteinMatches() {
        BannedWordManager manager = new BannedWordManager(Logger.getAnonymousLogger());
        manager.reload(config(
                true,
                false,
                true,
                List.of("fuck", "混蛋"),
                tempDir
        ));

        assertEquals(DetectionType.LITERAL, manager.scan("f u c k").orElseThrow().type());
        assertEquals(DetectionType.IP_ADDRESS, manager.scan("server 192.168.1.10").orElseThrow().type());
        assertEquals(DetectionType.FUZZY_LEVENSHTEIN, manager.scan("what the fack").orElseThrow().type());
    }

    @Test
    void detectsNewBundledEvasionExamplesAndKeepsRuleText() {
        BannedWordManager manager = new BannedWordManager(Logger.getAnonymousLogger());
        manager.reload(config(
                true,
                false,
                false,
                List.of("™", "si冯", "网警卫"),
                tempDir
        ));

        for (String message : List.of("™", "si冯", "网-警。卫")) {
            DetectionResult result = manager.scan(message).orElseThrow();
            assertEquals(DetectionType.LITERAL, result.type());
        }
    }

    @Test
    void detectsPinyinHomophonesWhenEnabled() {
        BannedWordManager manager = new BannedWordManager(Logger.getAnonymousLogger());
        manager.reload(config(
                true,
                true,
                false,
                List.of("卧槽", "你妈"),
                tempDir
        ));

        assertEquals(DetectionType.FUZZY_PINYIN, manager.scan("窝操").orElseThrow().type());
        assertEquals(DetectionType.FUZZY_PINYIN, manager.scan("你嘛").orElseThrow().type());
        assertEquals(DetectionType.FUZZY_PINYIN, manager.scan("你吗").orElseThrow().type());
    }

    @Test
    void doesNotMatchPinyinBannedWordsInsideALongerChineseToken() {
        BannedWordManager manager = new BannedWordManager(Logger.getAnonymousLogger());
        manager.reload(config(
                true,
                true,
                false,
                List.of("妈的"),
                tempDir
        ));

        assertTrue(manager.scan("干嘛的").isEmpty());
        DetectionResult result = manager.scan("嘛的").orElseThrow();
        assertEquals(DetectionType.FUZZY_PINYIN, result.type());
        assertEquals("嘛的", result.detectedText());
        assertEquals("妈的", result.rule());
        assertEquals(DetectionType.FUZZY_PINYIN, manager.scan("made").orElseThrow().type());
    }

    @Test
    void detectsBundledHomophoneVariantWithoutDependingOnPinyinDetection() {
        BannedWordManager manager = new BannedWordManager(Logger.getAnonymousLogger());
        manager.reload(config(
                true,
                false,
                false,
                List.of("嘛的"),
                tempDir
        ));

        DetectionResult result = manager.scan("你嘛的").orElseThrow();
        assertEquals(DetectionType.LITERAL, result.type());
        assertEquals("嘛的", result.rule());
    }

    @Test
    void detectsRegexInRawMessageWithoutMatchingAContainingWord() {
        BannedWordManager manager = new BannedWordManager(Logger.getAnonymousLogger());
        manager.reload(config(
                true,
                false,
                false,
                List.of("(?<![A-Za-z0-9_])bad(?![A-Za-z0-9_])"),
                tempDir,
                true
        ));

        DetectionResult result = manager.scan("BAD").orElseThrow();
        assertEquals(DetectionType.REGEX, result.type());
        assertEquals("BAD", result.detectedText());
        assertEquals("(?<![A-Za-z0-9_])bad(?![A-Za-z0-9_])", result.rule());
        assertTrue(manager.scan("badger").isEmpty());
    }

    @Test
    void directlyBlocksEmojiAfterExistingDetectionStages() {
        BannedWordManager manager = new BannedWordManager(Logger.getAnonymousLogger());
        manager.reload(config(
                true,
                false,
                false,
                List.of("违规词"),
                tempDir,
                false,
                DetectionAction.WARN
        ));

        DetectionResult result = manager.scan("你🐎死了").orElseThrow();
        assertEquals(DetectionType.EMOJI, result.type());
        assertEquals("🐎", result.detectedText());
        assertEquals("U+1F40E", result.rule());
        assertEquals(DetectionAction.CANCEL, result.action());
        assertEquals("你***死了", manager.censor("你🐎死了", result));
        assertEquals(DetectionType.LITERAL, manager.scan("违规词🐎").orElseThrow().type());

        BannedWordManager defaultLikeManager = new BannedWordManager(Logger.getAnonymousLogger());
        defaultLikeManager.reload(config(
                true,
                true,
                false,
                List.of("妈的"),
                tempDir
        ));
        assertEquals(DetectionType.EMOJI, defaultLikeManager.scan("你🐎死了").orElseThrow().type());
    }

    private static PluginConfig config(
            boolean advanced,
            boolean pinyin,
            boolean levenshtein,
            List<String> words,
            Path dataFolder
    ) {
        return config(advanced, pinyin, levenshtein, words, dataFolder, false);
    }

    private static PluginConfig config(
            boolean advanced,
            boolean pinyin,
            boolean levenshtein,
            List<String> words,
            Path dataFolder,
            boolean useRegex
    ) {
        return config(advanced, pinyin, levenshtein, words, dataFolder, useRegex, DetectionAction.CANCEL);
    }

    private static PluginConfig config(
            boolean advanced,
            boolean pinyin,
            boolean levenshtein,
            List<String> words,
            Path dataFolder,
            boolean useRegex,
            DetectionAction defaultAction
    ) {
        return new PluginConfig(
                defaultAction,
                useRegex,
                advanced,
                true,
                PluginConfig.DEFAULT_COMMAND_WHITELIST,
                false,
                false,
                "",
                "",
                "",
                "",
                new PluginConfig.PinyinConfig(pinyin, 32),
                new PluginConfig.LevenshteinConfig(levenshtein, 1, 50),
                new PluginConfig.IpDomainConfig(
                        true,
                        true,
                        false,
                        true,
                        false,
                        "GeoLite2-Country.mmdb",
                        "https://github.com/P3TERX/GeoLite.mmdb/releases/latest/download/GeoLite2-Country.mmdb",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of("minecraft.net"),
                        DetectionAction.CANCEL
                ),
                words,
                dataFolder,
                new PluginConfig.SpamConfig(
                        false,
                        5,
                        new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                        new PluginConfig.LongMessageConfig(false, 300, 3, 10)
                )
        );
    }
}
