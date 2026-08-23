package com.simpchatcensor;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import com.simpchatcensor.detection.DetectionResult;
import com.simpchatcensor.detection.DetectionType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionExecutorTest {
    @Test
    void keepsSpamWarningsSeparateFromContentWarnings() {
        PluginConfig config = config();
        DetectionResult spam = DetectionResult.of(
                DetectionType.SPAM,
                "message",
                "message-minimum-interval",
                DetectionAction.CANCEL,
                "message interval"
        );
        DetectionResult bannedWord = DetectionResult.of(
                DetectionType.LITERAL,
                "bad",
                "bad",
                DetectionAction.CANCEL,
                "literal"
        );

        assertEquals("spam-warning", ActionExecutor.warningTemplate(config, spam));
        assertEquals("player-warning", ActionExecutor.warningTemplate(config, bannedWord));
        assertEquals("player-warning", ActionExecutor.warningTemplate(config, null));
    }

    private static PluginConfig config() {
        return new PluginConfig(
                DetectionAction.CANCEL,
                false,
                true,
                true,
                PluginConfig.DEFAULT_COMMAND_WHITELIST,
                false,
                false,
                "staff-message",
                "player-warning",
                "spam-warning",
                "kick-message",
                new PluginConfig.PinyinConfig(false, 1),
                new PluginConfig.LevenshteinConfig(false, 0, 1),
                new PluginConfig.IpDomainConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        "GeoLite2-Country.mmdb",
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        DetectionAction.CANCEL
                ),
                List.of(),
                Path.of("."),
                new PluginConfig.SpamConfig(
                        false,
                        5,
                        new PluginConfig.RepeatedContentConfig(false, 4, 1, 16, 12),
                        new PluginConfig.LongMessageConfig(false, 300, 3, 10)
                )
        );
    }
}
