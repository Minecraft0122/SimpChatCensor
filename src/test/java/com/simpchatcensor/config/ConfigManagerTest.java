package com.simpchatcensor.config;

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
}
