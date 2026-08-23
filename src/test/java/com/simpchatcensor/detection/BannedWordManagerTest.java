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
        return new PluginConfig(
                DetectionAction.CANCEL,
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
