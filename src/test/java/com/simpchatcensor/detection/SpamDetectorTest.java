package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpamDetectorTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void blocksAnIdenticalRecentMessageButNotCaseOrWhitespaceVariants() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10)
        ));

        assertTrue(detector.detect(PLAYER, "Hello", 1_000).isEmpty());
        assertEquals(DetectionType.SPAM, detector.detect(PLAYER, "Hello", 6_000).orElseThrow().type());
        detector.clear(PLAYER);
        assertTrue(detector.detect(PLAYER, "Hello", 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "hello", 1_001).isEmpty());
        assertTrue(detector.detect(PLAYER, " Hello", 1_002).isEmpty());
        assertTrue(detector.detect(PLAYER, "Hello", 6_001).isEmpty());
        detector.clear(PLAYER);
        assertTrue(detector.detect(PLAYER, "Hello", 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "Hello", 6_001).isEmpty());
    }

    @Test
    void blocksConsecutiveRepeatedContentWithoutBlockingShortNormalText() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(true, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10)
        ));

        assertEquals(
                DetectionType.SPAM,
                detector.detect(PLAYER, "神经病".repeat(11), 1_000).orElseThrow().type()
        );
        detector.clear(PLAYER);
        assertEquals(DetectionType.SPAM, detector.detect(PLAYER, "哈".repeat(12), 1_000).orElseThrow().type());
        detector.clear(PLAYER);
        assertTrue(detector.detect(PLAYER, "abcabcabc", 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "ab ab ab ab", 1_001).isEmpty());
    }

    @Test
    void blocksTheThirdLongMessageInsideTheBurstWindow() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(true, 10, 3, 1)
        ));
        String longMessage = "1234567890";

        assertTrue(detector.detect(PLAYER, longMessage, 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "abcdefghij", 1_500).isEmpty());
        assertEquals(DetectionType.SPAM, detector.detect(PLAYER, "klmnopqrst", 2_000).orElseThrow().type());
        detector.clear(PLAYER);
        assertTrue(detector.detect(PLAYER, longMessage, 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "differentxx", 2_001).isEmpty());
    }

    @Test
    void reloadClearsHistoryAndSpamAlwaysUsesCancelAction() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10)
        ));

        detector.detect(PLAYER, "same", 1_000);
        detector.reload(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10)
        ));
        assertTrue(detector.detect(PLAYER, "same", 1_001).isEmpty());
        DetectionResult result = detector.detect(PLAYER, "same", 1_002).orElseThrow();
        assertEquals(DetectionAction.CANCEL, result.action());
    }

    @Test
    void keepsLongDuplicateMessagesBoundedWithAContentFingerprint() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10)
        ));
        String longMessage = "unique-" + "x".repeat(17_000);

        assertTrue(detector.detect(PLAYER, longMessage, 1_000).isEmpty());
        assertEquals(DetectionType.SPAM, detector.detect(PLAYER, longMessage, 1_001).orElseThrow().type());
    }

    private static SpamDetector detector(PluginConfig.SpamConfig config) {
        SpamDetector detector = new SpamDetector();
        detector.reload(config);
        return detector;
    }
}
