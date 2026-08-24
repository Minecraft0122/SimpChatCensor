package com.simpchatcensor.listener;

import com.simpchatcensor.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandListenerTest {
    @Test
    void skipsConfiguredCommandsBeforeCensoringArguments() {
        assertTrue(CommandListener.isWhitelistedCommand(
                "/scc whitelist add simpmc.org",
                PluginConfig.DEFAULT_COMMAND_WHITELIST
        ));
        assertTrue(CommandListener.isWhitelistedCommand(
                "/SCC add 1,1,1,1",
                PluginConfig.DEFAULT_COMMAND_WHITELIST
        ));
        assertTrue(CommandListener.isWhitelistedCommand(
                "/simpchatcensor:scc whitelist add simpmc.org",
                PluginConfig.DEFAULT_COMMAND_WHITELIST
        ));
    }

    @Test
    void skipsLuckPermsCommandsContainingPermissionNodes() {
        assertTrue(CommandListener.isWhitelistedCommand(
                "/lp user Steve permission set essentials.home true",
                PluginConfig.DEFAULT_COMMAND_WHITELIST
        ));
        assertTrue(CommandListener.isWhitelistedCommand(
                "/luckperms group default permission set minecraft.command.help true",
                PluginConfig.DEFAULT_COMMAND_WHITELIST
        ));
        assertTrue(CommandListener.isWhitelistedCommand(
                "/luckperms:lp user Steve permission unset essentials.home",
                PluginConfig.DEFAULT_COMMAND_WHITELIST
        ));
    }

    @Test
    void keepsCommandsOutsideTheWhitelistCensorable() {
        List<String> whitelist = List.of("scc", "lp", "luckperms", "mail");
        assertTrue(CommandListener.isWhitelistedCommand("/mail send Steve cnpvp.top", whitelist));
        assertFalse(CommandListener.isWhitelistedCommand("/msg player simpmc.org", whitelist));
        assertFalse(CommandListener.isWhitelistedCommand("bwf whitelist add simpmc.org", whitelist));
        assertFalse(CommandListener.isWhitelistedCommand("/sccextra whitelist add simpmc.org", whitelist));
        assertFalse(CommandListener.isWhitelistedCommand("/bwf whitelist add simpmc.org", whitelist));
        assertFalse(CommandListener.isWhitelistedCommand("/lpa user Steve permission set example.node true", whitelist));
        assertFalse(CommandListener.isWhitelistedCommand("/luckpermsx user Steve permission set example.node true", whitelist));
        assertFalse(CommandListener.isWhitelistedCommand("/mailbox Steve cnpvp.top", whitelist));
        assertFalse(CommandListener.isWhitelistedCommand("/scc whitelist add simpmc.org", List.of("scc*")));
    }
}
