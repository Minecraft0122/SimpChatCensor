package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpamHistoryTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    void interleavingNumbersDoesNotHideEarlierDuplicates() {
        SpamDetector detector = detector(config(5, true, 8));
        String repeated = "你好你好你好";

        assertTrue(detector.detect(PLAYER, repeated, 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "1", 1_200).isEmpty());
        assertDuplicate(detector.detect(PLAYER, repeated, 1_400).orElseThrow(), repeated);
        assertTrue(detector.detect(PLAYER, "2", 1_600).isEmpty());
        assertDuplicate(detector.detect(PLAYER, repeated, 1_800).orElseThrow(), repeated);
        assertTrue(detector.detect(PLAYER, "3", 2_000).isEmpty());
    }

    @Test
    void comparesSimilarMessagesAgainstEarlierHistoryAfterUnrelatedMessages() {
        SpamDetector detector = detector(config(5, true, 8));
        String first = "加入服务器领取奖励-A1B2";
        String next = "加入服务器领取奖励-X9Y8";

        assertTrue(detector.detect(PLAYER, first, 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "1", 1_200).isEmpty());
        assertSpam(detector.detect(PLAYER, next, 10_000).orElseThrow(), next, "similar-message");
        assertTrue(detector.detect(PLAYER, "2", 10_200).isEmpty());
        assertSpam(detector.detect(PLAYER, "加入服务器领取奖励-Z7", 16_000).orElseThrow(),
                "加入服务器领取奖励-Z7", "similar-message");
    }

    @Test
    void normalizedEqualMessagesAreSimilarOnlyWhenTheCommonPrefixIsLongEnough() {
        SpamDetector detector = detector(config(5, true, 8));

        assertTrue(detector.detect(PLAYER, "HELLO WORLD AGAIN", 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "1", 1_200).isEmpty());
        String normalizedVariant = " hello world again ";
        assertSpam(detector.detect(PLAYER, normalizedVariant, 1_400).orElseThrow(),
                normalizedVariant, "similar-message");

        assertTrue(detector.detect(OTHER_PLAYER, "Hi", 1_000).isEmpty());
        assertTrue(detector.detect(OTHER_PLAYER, " hi ", 1_200).isEmpty());
    }

    @Test
    void includesTheFifteenSecondBoundaryWithoutExtendingItForRejectedMessages() {
        SpamDetector detector = detector(config(1, false, 1));

        assertTrue(detector.detect(PLAYER, "boundary", 1_000).isEmpty());
        assertDuplicate(detector.detect(PLAYER, "boundary", 15_999).orElseThrow(), "boundary");
        assertDuplicate(detector.detect(PLAYER, "boundary", 16_000).orElseThrow(), "boundary");
        assertTrue(detector.detect(PLAYER, "boundary", 16_001).isEmpty());
    }

    @Test
    void rejectedSimilarMessagesDoNotExtendTheOriginalHistoryEntry() {
        SpamDetector detector = detector(config(1, true, 1));

        assertTrue(detector.detect(PLAYER, "领取服务器奖励-A1", 1_000).isEmpty());
        assertSpam(detector.detect(PLAYER, "领取服务器奖励-B2", 16_000).orElseThrow(),
                "领取服务器奖励-B2", "similar-message");
        assertTrue(detector.detect(PLAYER, "领取服务器奖励-C3", 16_001).isEmpty());
    }

    @Test
    void preservesConfiguredWindowsLongerThanFifteenSeconds() {
        SpamDetector detector = detector(config(30, true, 30));

        assertTrue(detector.detect(PLAYER, "original", 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, "unrelated", 20_000).isEmpty());
        assertDuplicate(detector.detect(PLAYER, "original", 31_000).orElseThrow(), "original");
        assertTrue(detector.detect(PLAYER, "original", 31_001).isEmpty());

        assertTrue(detector.detect(OTHER_PLAYER, "领取服务器奖励-A1", 1_000).isEmpty());
        assertTrue(detector.detect(OTHER_PLAYER, "1", 20_000).isEmpty());
        assertSpam(detector.detect(OTHER_PLAYER, "领取服务器奖励-B2", 31_000).orElseThrow(),
                "领取服务器奖励-B2", "similar-message");
        assertTrue(detector.detect(OTHER_PLAYER, "领取服务器奖励-C3", 31_001).isEmpty());
    }

    @Test
    void longerSimilarityWindowDoesNotExtendTheExactDuplicateWindow() {
        SpamDetector detector = detector(config(15, true, 30));
        String longMessage = "领取服务器奖励-A1";

        assertTrue(detector.detect(PLAYER, "short", 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, longMessage, 1_001).isEmpty());
        assertDuplicate(detector.detect(PLAYER, "short", 16_000).orElseThrow(), "short");
        assertTrue(detector.detect(PLAYER, "short", 16_001).isEmpty());
        assertSpam(detector.detect(PLAYER, longMessage, 16_002).orElseThrow(),
                longMessage, "similar-message");
        assertSpam(detector.detect(PLAYER, "领取服务器奖励-B2", 31_001).orElseThrow(),
                "领取服务器奖励-B2", "similar-message");
        assertTrue(detector.detect(PLAYER, "领取服务器奖励-C3", 31_002).isEmpty());
    }

    @Test
    void longerDuplicateWindowDoesNotExtendTheSimilarityWindow() {
        SpamDetector detector = detector(config(30, true, 15));
        String original = "领取服务器奖励-A1";
        String variant = "领取服务器奖励-B2";

        assertTrue(detector.detect(PLAYER, original, 1_000).isEmpty());
        assertSpam(detector.detect(PLAYER, variant, 16_000).orElseThrow(),
                variant, "similar-message");
        assertTrue(detector.detect(PLAYER, variant, 16_001).isEmpty());
        assertDuplicate(detector.detect(PLAYER, original, 31_000).orElseThrow(), original);
        assertTrue(detector.detect(PLAYER, original, 31_002).isEmpty());

        assertTrue(detector.detect(OTHER_PLAYER, "short", 1_000).isEmpty());
        assertDuplicate(detector.detect(OTHER_PLAYER, "short", 31_000).orElseThrow(), "short");
        assertTrue(detector.detect(OTHER_PLAYER, "short", 31_001).isEmpty());
    }

    @Test
    void inspectionsDoNotRecordMessagesRejectedByOtherDetectors() {
        SpamDetector detector = detector(config(5, true, 8));

        assertTrue(detector.inspectChat(PLAYER, "previously blocked", 1_000).result().isEmpty());
        assertTrue(detector.inspectChat(PLAYER, "previously blocked", 1_200).result().isEmpty());
        assertTrue(detector.detect(PLAYER, "previously blocked", 1_400).isEmpty());
        assertDuplicate(detector.inspect(PLAYER, "previously blocked", 1_600).orElseThrow(),
                "previously blocked");
    }

    @Test
    void commitRechecksHistoryWhenAnotherMessageArrivesBetweenInspections() {
        SpamDetector detector = detector(config(5, false, 8));
        SpamDetector.ChatInspection first = detector.inspectChat(PLAYER, "same", 1_000);
        SpamDetector.ChatInspection second = detector.inspectChat(PLAYER, "same", 1_001);
        assertTrue(first.result().isEmpty());
        assertTrue(second.result().isEmpty());

        assertTrue(detector.detectAfterInspection(PLAYER, "same", first, 1_002).isEmpty());
        assertTrue(detector.detect(PLAYER, "intervening", 1_003).isEmpty());
        assertDuplicate(detector.detectAfterInspection(PLAYER, "same", second, 1_004).orElseThrow(),
                "same");
    }

    @Test
    void keepsPlayerHistoriesIsolatedAndClearsThemOnClearAndReload() {
        PluginConfig.SpamConfig config = config(5, false, 8);
        SpamDetector detector = detector(config);

        assertTrue(detector.detect(PLAYER, "same", 1_000).isEmpty());
        assertTrue(detector.detect(OTHER_PLAYER, "same", 1_001).isEmpty());
        detector.clear(PLAYER);
        assertTrue(detector.detect(PLAYER, "same", 1_002).isEmpty());
        assertDuplicate(detector.detect(OTHER_PLAYER, "same", 1_003).orElseThrow(), "same");

        detector.reload(config);
        assertTrue(detector.detect(PLAYER, "same", 1_004).isEmpty());
        assertTrue(detector.detect(OTHER_PLAYER, "same", 1_005).isEmpty());
    }

    @Test
    void retainsFingerprintsForLongMessagesAcrossInterveningMessages() {
        SpamDetector detector = detector(config(5, false, 8));
        String first = "x".repeat(17_000) + "A";
        String different = "x".repeat(17_000) + "B";

        assertTrue(detector.detect(PLAYER, first, 1_000).isEmpty());
        assertTrue(detector.detect(PLAYER, different, 1_200).isEmpty());
        assertTrue(detector.detect(PLAYER, "1", 1_400).isEmpty());
        assertDuplicate(detector.detect(PLAYER, first, 16_000).orElseThrow(), first);
        assertDuplicate(detector.detect(PLAYER, different, 16_200).orElseThrow(), different);
        assertTrue(detector.detect(PLAYER, first, 16_201).isEmpty());
    }

    @Test
    void fullHistoryRejectsNewMessagesWithoutEvictingValidEntries() {
        SpamDetector detector = detector(config(5, false, 8));
        assertTrue(detector.detect(PLAYER, "original", 1_000).isEmpty());
        for (int index = 1; index < 256; index++) {
            assertTrue(detector.detect(PLAYER, "entry-" + index, 1_000 + index).isEmpty(),
                    "History should accept entry " + index);
        }

        assertSpam(detector.detect(PLAYER, "overflow", 2_000).orElseThrow(),
                "overflow", "message-history-capacity");
        assertDuplicate(detector.detect(PLAYER, "original", 16_000).orElseThrow(), "original");
        assertSpam(detector.inspect(PLAYER, "still full", 16_000).orElseThrow(),
                "still full", "message-history-capacity");
        assertTrue(detector.detect(PLAYER, "overflow", 16_256).isEmpty());
    }

    @Test
    void fullCharacterBudgetRejectsNewMessagesWithoutEvictingValidEntries() {
        SpamDetector detector = detector(config(15, false, 15));
        String content = "x".repeat(16_383);
        String original = content + "0";
        // Eight retained messages of 16,384 characters reach the 131,072-character cap.
        for (int index = 0; index < 8; index++) {
            assertTrue(detector.detect(PLAYER, content + index, 1_000 + index).isEmpty(),
                    "Character budget should accept message " + index);
        }

        assertSpam(detector.detect(PLAYER, "overflow", 2_000).orElseThrow(),
                "overflow", "message-history-capacity");
        assertDuplicate(detector.detect(PLAYER, original, 16_000).orElseThrow(), original);
        // Only the first entry expires here, freeing exactly one message's character budget.
        assertTrue(detector.detect(PLAYER, content + "8", 16_001).isEmpty());
        assertSpam(detector.detect(PLAYER, "overflow", 16_001).orElseThrow(),
                "overflow", "message-history-capacity");
        assertTrue(detector.detect(PLAYER, "overflow", 16_008).isEmpty());
    }

    @Test
    void oneHundredFiftyInterveningMessagesDoNotEvictTheFifteenSecondBoundary() {
        SpamDetector detector = detector(config(5, false, 8));
        assertTrue(detector.detect(PLAYER, "original", 1_000).isEmpty());
        for (int index = 1; index <= 150; index++) {
            assertTrue(detector.detect(PLAYER, Integer.toString(index), 1_000 + index * 100L).isEmpty());
        }

        assertDuplicate(detector.detect(PLAYER, "original", 16_000).orElseThrow(), "original");
        assertTrue(detector.detect(PLAYER, "original", 16_001).isEmpty());
    }

    private static void assertDuplicate(DetectionResult result, String message) {
        assertEquals(DetectionType.SPAM, result.type());
        assertEquals(message, result.detectedText());
        assertEquals(DetectionAction.CANCEL, result.action());
        assertEquals("duplicate-message", result.detail());
        assertFalse(result.rule().isBlank());
    }

    private static void assertSpam(DetectionResult result, String message, String rule) {
        assertEquals(DetectionType.SPAM, result.type());
        assertEquals(message, result.detectedText());
        assertEquals(rule, result.rule());
        assertEquals(DetectionAction.CANCEL, result.action());
    }

    private static PluginConfig.SpamConfig config(int duplicateWindow, boolean similar, int similarWindow) {
        return new PluginConfig.SpamConfig(
                true,
                duplicateWindow,
                new PluginConfig.RepeatedContentConfig(false, 4, 2, 16, 12),
                new PluginConfig.LongMessageConfig(false, 300, 3, 10),
                new PluginConfig.MinimumIntervalConfig(0, 0),
                new PluginConfig.SimilarMessageConfig(similar, similarWindow, 8, 12)
        );
    }

    private static SpamDetector detector(PluginConfig.SpamConfig config) {
        SpamDetector detector = new SpamDetector();
        detector.reload(config);
        return detector;
    }
}
