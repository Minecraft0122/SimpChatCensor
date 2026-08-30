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
    void rejectedMinimumIntervalMessagesDoNotAdvanceActivity() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                false,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10),
                new PluginConfig.MinimumIntervalConfig(200, 0),
                new PluginConfig.SimilarMessageConfig(false, 8, 8, 12)
        ));

        assertTrue(detector.detect(PLAYER, "allowed", 1_000).isEmpty());
        // A message rejected by an earlier rule must not advance the activity
        // clock before the listener considers the next message.
        assertEquals("message-minimum-interval", detector.detect(PLAYER, "blocked", 1_100)
                .orElseThrow().rule());
        assertTrue(detector.detect(PLAYER, "next", 1_200).isEmpty());
    }

    @Test
    void rejectedLongMessagesDoNotOccupyTheBurstWindow() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(true, 10, 3, 1),
                new PluginConfig.MinimumIntervalConfig(0, 0),
                new PluginConfig.SimilarMessageConfig(false, 8, 8, 12)
        ));

        assertTrue(detector.detect(PLAYER, "1234567890", 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "abcdefghij", 1_500).isEmpty());
        assertEquals(DetectionType.SPAM, detector.detect(PLAYER, "klmnopqrst", 1_600).orElseThrow().type());
        // The rejected third message must not keep the burst full at 2_400.
        assertTrue(detector.detect(PLAYER, "uvwxyzabcd", 2_400).isEmpty());
    }

    @Test
    void rejectedCommandsDoNotAdvanceTheSharedActivityClock() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                false,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10),
                new PluginConfig.MinimumIntervalConfig(200, 250),
                new PluginConfig.SimilarMessageConfig(false, 8, 8, 12)
        ));

        assertTrue(detector.detect(PLAYER, "allowed", 1_000).isEmpty());
        assertEquals("command-minimum-interval", detector.detectCommandInterval(PLAYER, "/blocked", 1_100)
                .orElseThrow().rule());
        assertTrue(detector.detectCommandInterval(PLAYER, "/next", 1_250).isEmpty());
    }

    @Test
    void inspectionDoesNotCommitChatHistory() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10),
                new PluginConfig.MinimumIntervalConfig(0, 0),
                new PluginConfig.SimilarMessageConfig(false, 8, 8, 12)
        ));

        assertTrue(detector.inspect(PLAYER, "same", 1_000).isEmpty());
        assertTrue(detector.inspect(PLAYER, "same", 1_001).isEmpty());
        assertTrue(detector.detect(PLAYER, "same", 1_000).isEmpty());
        assertEquals(DetectionType.SPAM, detector.inspect(PLAYER, "same", 1_001)
                .orElseThrow().type());
    }

    @Test
    void commandInspectionDoesNotCommitActivity() {
        SpamDetector detector = detector(new PluginConfig.SpamConfig(
                false,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10),
                new PluginConfig.MinimumIntervalConfig(0, 250),
                new PluginConfig.SimilarMessageConfig(false, 8, 8, 12)
        ));

        assertTrue(detector.inspectCommandInterval(PLAYER, "/same", 1_000).isEmpty());
        assertTrue(detector.inspectCommandInterval(PLAYER, "/same", 1_001).isEmpty());
        assertTrue(detector.detectCommandInterval(PLAYER, "/same", 1_000).isEmpty());
        assertEquals("command-minimum-interval", detector.inspectCommandInterval(PLAYER, "/next", 1_001)
                .orElseThrow().rule());
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

    @Test
    void blocksMessagesThatOnlyChangeByAShortRandomSuffix() {
        SpamDetector detector = new SpamDetector();
        detector.reload(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10),
                new PluginConfig.MinimumIntervalConfig(0, 0),
                new PluginConfig.SimilarMessageConfig(true, 8, 8, 12)
        ));

        assertTrue(detector.detect(PLAYER, "加入服务器领取奖励-A1B2", 1_000).isEmpty());
        DetectionResult result = detector.detect(PLAYER, "加入服务器领取奖励-X9Y8", 2_000).orElseThrow();
        assertEquals(DetectionType.SPAM, result.type());
        assertEquals("similar-message", result.rule());
        assertEquals(DetectionAction.CANCEL, result.action());
    }

    @Test
    void doesNotTreatACompletelyDifferentLongTailAsARandomSuffix() {
        SpamDetector detector = new SpamDetector();
        detector.reload(new PluginConfig.SpamConfig(
                true,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10),
                new PluginConfig.MinimumIntervalConfig(0, 0),
                new PluginConfig.SimilarMessageConfig(true, 8, 8, 4)
        ));

        assertTrue(detector.detect(PLAYER, "请查看服务器公告并完成新手任务", 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "请查看服务器公告然后前往竞技场参加比赛", 2_000).isEmpty());
    }

    @Test
    void enforcesMessageAndCommandIntervalsWithSharedPlayerActivity() {
        SpamDetector detector = new SpamDetector();
        detector.reload(new PluginConfig.SpamConfig(
                false,
                5,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10),
                new PluginConfig.MinimumIntervalConfig(200, 250),
                new PluginConfig.SimilarMessageConfig(false, 8, 8, 12)
        ));

        assertTrue(detector.detect(PLAYER, "first", 1_000).isEmpty());
        assertEquals("message-minimum-interval", detector.detect(PLAYER, "second", 1_199).orElseThrow().rule());
        assertTrue(detector.detect(PLAYER, "third", 1_400).isEmpty());
        assertEquals("command-minimum-interval", detector.detectCommandInterval(PLAYER, "/home", 1_500).orElseThrow().rule());
        assertEquals("command-minimum-interval", detector.detectCommandInterval(PLAYER, "/spawn", 1_600).orElseThrow().rule());
        assertTrue(detector.detectCommandInterval(PLAYER, "/spawn", 1_750).isEmpty());
    }

    private static SpamDetector detector(PluginConfig.SpamConfig config) {
        SpamDetector detector = new SpamDetector();
        detector.reload(config);
        return detector;
    }
}
