package com.simpchatcensor.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandListenerTest {
    @Test
    void skipsOwnManagementCommandsBeforeCensoringArguments() {
        assertTrue(CommandListener.isManagementCommand("/bwf whitelist add simpmc.org"));
        assertTrue(CommandListener.isManagementCommand("/BWF add 1,1,1,1"));
        assertTrue(CommandListener.isManagementCommand("/simpchatcensor:bwf whitelist add simpmc.org"));
    }

    @Test
    void skipsLuckPermsCommandsContainingPermissionNodes() {
        assertTrue(CommandListener.isManagementCommand(
                "/lp user Steve permission set essentials.home true"
        ));
        assertTrue(CommandListener.isManagementCommand(
                "/luckperms group default permission set minecraft.command.help true"
        ));
        assertTrue(CommandListener.isManagementCommand(
                "/luckperms:lp user Steve permission unset essentials.home"
        ));
    }

    @Test
    void keepsOtherCommandsCensorable() {
        assertFalse(CommandListener.isManagementCommand("/msg player simpmc.org"));
        assertFalse(CommandListener.isManagementCommand("bwf whitelist add simpmc.org"));
        assertFalse(CommandListener.isManagementCommand("/bwfextra whitelist add simpmc.org"));
    }
}
