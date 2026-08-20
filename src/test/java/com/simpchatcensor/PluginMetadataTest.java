package com.simpchatcensor;

import com.simpchatcensor.config.PluginConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    }

    @Test
    void shipsSpamDetectionDefaultsAndAnInitialBannedWordLibrary() throws IOException {
        YamlConfiguration config = loadYamlResource("config.yml");
        assertEquals(300, config.getInt("spam-detection.long-message-burst.min-length"));
        assertEquals(3, config.getInt("spam-detection.long-message-burst.max-messages"));
        assertEquals(1, config.getInt("spam-detection.repeated-content.min-segment-length"));

        YamlConfiguration blacklist = loadYamlResource("message-blacklist.yml");
        assertEquals(1, blacklist.getInt("library-version"));
        assertEquals(true, blacklist.getStringList("banned-words").contains("神经病"));
    }

    private YamlConfiguration loadYamlResource(String resourceName) throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(stream, resourceName + " must be packaged as a plugin resource");

        try (InputStream input = stream; Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
