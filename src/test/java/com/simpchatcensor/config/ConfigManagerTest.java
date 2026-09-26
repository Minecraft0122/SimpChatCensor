package com.simpchatcensor.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {
    @Test
    void usesTheBuiltInCommandWhitelistWhenOlderConfigsDoNotContainTheKey() {
        assertEquals(
                PluginConfig.DEFAULT_COMMAND_WHITELIST,
                ConfigManager.resolveCommandWhitelist(false, false, List.of())
        );
    }

    @Test
    void normalizesConfiguredCommandRootsWithoutWideningMatches() {
        assertEquals(
                List.of("scc", "lp", "luckperms"),
                ConfigManager.resolveCommandWhitelist(
                        true,
                        true,
                        List.of("/SCC", "minecraft:LP", "luckperms", "lp", "", "with space", "namespace:", "one:two:three")
                )
        );
    }

    @Test
    void keepsAnExplicitlyEmptyCommandWhitelistEmpty() {
        assertEquals(List.of(), ConfigManager.resolveCommandWhitelist(true, true, List.of()));
    }

    @Test
    void failsClosedWhenTheConfiguredCommandWhitelistIsNotAList() {
        assertEquals(List.of(), ConfigManager.resolveCommandWhitelist(true, false, List.of("lp")));
    }

    @Test
    void usesBuiltInDomainSuffixesWhenOlderConfigsDoNotContainTheKey() {
        assertEquals(
                PluginConfig.DEFAULT_DOMAIN_SUFFIXES,
                ConfigManager.resolveDomainSuffixes(false, false, List.of())
        );
    }

    @Test
    void normalizesConfiguredDomainSuffixesAndKeepsExplicitEmptyListsEmpty() {
        assertEquals(
                List.of("com", "co.uk"),
                ConfigManager.resolveDomainSuffixes(
                        true,
                        true,
                        List.of(".COM", "co。uk", "com", "", "bad_suffix")
                )
        );
        assertEquals(
                List.of("com"),
                ConfigManager.resolveDomainSuffixes(true, true, List.of("。COM"))
        );
        assertEquals(
                List.of(),
                ConfigManager.resolveDomainSuffixes(true, true, List.of())
        );
    }

    @Test
    void mergesBundledBannedWordsWithoutDroppingCustomEntriesOrDuplicates() {
        assertEquals(
                List.of("Custom", "fuck", "混蛋"),
                ConfigManager.mergeBannedWords(
                        List.of("Custom", "fuck", "custom"),
                        List.of("混蛋", "FUCK")
                )
        );
    }

    @Test
    void firstLibraryUpgradeDoesNotReaddRemovedLegacyDefaults() {
        assertEquals(
                List.of("Custom", "new-word"),
                ConfigManager.mergeBannedWordsForUpgrade(
                        0,
                        List.of("Custom"),
                        List.of("fuck", "混蛋", "new-word")
                )
        );
    }

    @Test
    void boundsSpamSettingsThatCouldOtherwiseMakeScanningExpensive() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("spam-detection.repeated-content.min-repeats", 10_000);
        source.set("spam-detection.repeated-content.min-segment-length", -1);
        source.set("spam-detection.repeated-content.max-segment-length", 10_000);
        source.set("spam-detection.long-message-burst.max-messages", 1);
        source.set("spam-detection.long-message-burst.window-seconds", 10_000);

        PluginConfig.SpamConfig spam = ConfigManager.loadSpamConfig(source);
        assertEquals(100, spam.repeatedContent().minRepeats());
        assertEquals(1, spam.repeatedContent().minSegmentLength());
        assertEquals(64, spam.repeatedContent().maxSegmentLength());
        assertEquals(3, spam.longMessageBurst().maxMessages());
        assertEquals(3600, spam.longMessageBurst().windowSeconds());
    }

    @Test
    void boundsMinimumIntervalsAndSimilarMessageSettings() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("content-minimum-interval.message-millis", 1);
        source.set("content-minimum-interval.command-millis", 100_000);
        source.set("spam-detection.similar-message.minimum-common-prefix-length", 1);
        source.set("spam-detection.similar-message.maximum-suffix-length", 100_000);

        PluginConfig.SpamConfig spam = ConfigManager.loadSpamConfig(source);
        assertEquals(200, spam.minimumInterval().messageMillis());
        assertEquals(60_000, spam.minimumInterval().commandMillis());
        assertEquals(12, spam.similarMessage().minimumCommonPrefixLength());
        assertEquals(256, spam.similarMessage().maximumSuffixLength());
    }

    @Test
    void defaultsBothMessageHistoryWindowsToFifteenSeconds() {
        PluginConfig.SpamConfig spam = ConfigManager.loadSpamConfig(new YamlConfiguration());

        assertEquals(15, spam.duplicateWindowSeconds());
        assertEquals(15, spam.similarMessage().windowSeconds());
    }

    @Test
    void raisesLegacyHistoryWindowsWithoutRewritingTheSourceConfig() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("spam-detection.duplicate-window-seconds", 5);
        source.set("spam-detection.similar-message.window-seconds", 8);

        PluginConfig.SpamConfig spam = ConfigManager.loadSpamConfig(source);

        assertEquals(15, spam.duplicateWindowSeconds());
        assertEquals(15, spam.similarMessage().windowSeconds());
        assertEquals(5, source.getInt("spam-detection.duplicate-window-seconds"));
        assertEquals(8, source.getInt("spam-detection.similar-message.window-seconds"));
    }

    @Test
    void boundsMessageHistoryWindowsAndPreservesIndependentValidValues() {
        YamlConfiguration source = new YamlConfiguration();
        source.set("spam-detection.duplicate-window-seconds", -1);
        source.set("spam-detection.similar-message.window-seconds", 0);
        PluginConfig.SpamConfig invalid = ConfigManager.loadSpamConfig(source);
        assertEquals(15, invalid.duplicateWindowSeconds());
        assertEquals(15, invalid.similarMessage().windowSeconds());

        source.set("spam-detection.duplicate-window-seconds", 10_000);
        source.set("spam-detection.similar-message.window-seconds", 10_000);
        PluginConfig.SpamConfig maximum = ConfigManager.loadSpamConfig(source);
        assertEquals(3600, maximum.duplicateWindowSeconds());
        assertEquals(3600, maximum.similarMessage().windowSeconds());

        source.set("spam-detection.duplicate-window-seconds", 30);
        source.set("spam-detection.similar-message.window-seconds", 60);
        PluginConfig.SpamConfig independent = ConfigManager.loadSpamConfig(source);
        assertEquals(30, independent.duplicateWindowSeconds());
        assertEquals(60, independent.similarMessage().windowSeconds());
    }
}
