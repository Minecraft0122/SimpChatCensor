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
                List.of("bwf", "lp", "luckperms"),
                ConfigManager.resolveCommandWhitelist(
                        true,
                        true,
                        List.of("/BWF", "minecraft:LP", "luckperms", "lp", "", "with space", "namespace:", "one:two:three")
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
}
