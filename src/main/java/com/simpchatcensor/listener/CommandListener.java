package com.simpchatcensor.listener;

import com.simpchatcensor.ActionExecutor;
import com.simpchatcensor.config.PluginConfig;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.detection.DetectionResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;
import java.util.function.Supplier;

public final class CommandListener implements Listener {
    private final Supplier<PluginConfig> configSupplier;
    private final BannedWordManager bannedWordManager;
    private final ActionExecutor actionExecutor;

    public CommandListener(
            Supplier<PluginConfig> configSupplier,
            BannedWordManager bannedWordManager,
            ActionExecutor actionExecutor
    ) {
        this.configSupplier = configSupplier;
        this.bannedWordManager = bannedWordManager;
        this.actionExecutor = actionExecutor;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        PluginConfig config = configSupplier.get();
        if (!config.filterCommands()) {
            return;
        }

        String command = event.getMessage();
        if (isManagementCommand(command)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("simpchatcensor.bypass")) {
            return;
        }

        Optional<DetectionResult> result = bannedWordManager.scan(command);
        result.ifPresent(detectionResult -> actionExecutor.applyTextAction(
                player,
                command,
                detectionResult,
                filtered -> {
                    if (filtered.startsWith("/")) {
                        event.setMessage(filtered);
                    } else {
                        event.setCancelled(true);
                    }
                },
                () -> event.setCancelled(true),
                true
        ));
    }

    public static boolean isManagementCommand(String command) {
        if (command == null) {
            return false;
        }

        String trimmed = command.trim();
        if (!trimmed.startsWith("/")) {
            return false;
        }

        String root = firstToken(trimmed.substring(1));
        int namespaceSeparator = root.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < root.length()) {
            root = root.substring(namespaceSeparator + 1);
        }

        return root.equalsIgnoreCase("bwf")
                || root.equalsIgnoreCase("lp")
                || root.equalsIgnoreCase("luckperms");
    }

    private static String firstToken(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return value.substring(0, i);
            }
        }
        return value;
    }
}
