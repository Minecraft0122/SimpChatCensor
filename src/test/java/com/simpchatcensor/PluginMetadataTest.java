package com.simpchatcensor;

import com.simpchatcensor.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMetadataTest {
    @Test
    void grantsBypassToOperatorsByDefault() throws IOException {
        YamlConfiguration metadata = loadYamlResource("plugin.yml");
        assertEquals("op", metadata.getString("permissions.simpchatcensor.bypass.default"));
    }

    @Test
    void keepsTheDefaultCommandWhitelistAlignedWithTheRuntimeFallback() throws IOException {
        YamlConfiguration config = loadYamlResource("config.yml");
        assertEquals(PluginConfig.DEFAULT_COMMAND_WHITELIST, config.getStringList("command-whitelist"));
        assertEquals(
                PluginConfig.DEFAULT_DOMAIN_SUFFIXES,
                config.getStringList("ip-domain-detection.domain-suffixes")
        );
    }

    @Test
    void shipsSpamDetectionDefaultsAndAnInitialBannedWordLibrary() throws IOException {
        YamlConfiguration config = loadYamlResource("config.yml");
        assertEquals(300, config.getInt("spam-detection.long-message-burst.min-length"));
        assertEquals(3, config.getInt("spam-detection.long-message-burst.max-messages"));
        assertEquals(1, config.getInt("spam-detection.repeated-content.min-segment-length"));
        assertEquals(200, config.getInt("content-minimum-interval.message-millis"));
        assertEquals(250, config.getInt("content-minimum-interval.command-millis"));
        assertEquals(PluginConfig.MIN_MESSAGE_HISTORY_SECONDS, config.getInt("spam-detection.duplicate-window-seconds"));
        assertEquals(PluginConfig.MIN_MESSAGE_HISTORY_SECONDS, config.getInt("spam-detection.similar-message.window-seconds"));
        assertEquals(12, config.getInt("spam-detection.similar-message.minimum-common-prefix-length"));
        assertEquals(64, config.getInt("spam-detection.similar-message.maximum-suffix-length"));
        assertEquals("&e消息发送过快，请稍后再试。", config.getString("spam-warning"));

        YamlConfiguration blacklist = loadYamlResource("message-blacklist.yml");
        assertEquals(3, blacklist.getInt("library-version"));
        assertEquals(true, blacklist.getStringList("banned-words").contains("神经病"));
        assertTrue(blacklist.getStringList("banned-words").containsAll(java.util.List.of(
                "™", "si冯", "网警卫", "嘛的", "麻的", "马的", "外挂出售", "低价代充"
        )));
        assertFalse(blacklist.getStringList("banned-words").contains("开挂"));
    }

    private YamlConfiguration loadYamlResource(String resourceName) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(stream, resourceName + " must be packaged as a plugin resource");

        try (InputStream input = stream; Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
