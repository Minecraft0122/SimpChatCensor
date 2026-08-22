package com.simpchatcensor.listener;

import com.simpchatcensor.ActionExecutor;
import com.simpchatcensor.config.PluginConfig;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.detection.DetectionResult;
import com.simpchatcensor.detection.SpamDetector;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

public final class CommandListener implements Listener {
    private final Supplier<PluginConfig> configSupplier;
    private final BannedWordManager bannedWordManager;
    private final SpamDetector spamDetector;
    private final ActionExecutor actionExecutor;

    public CommandListener(
            Supplier<PluginConfig> configSupplier,
            BannedWordManager bannedWordManager,
            SpamDetector spamDetector,
            ActionExecutor actionExecutor
    ) {
        this.configSupplier = configSupplier;
        this.bannedWordManager = bannedWordManager;
        this.spamDetector = spamDetector;
        this.actionExecutor = actionExecutor;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        PluginConfig config = configSupplier.get();
        if (!config.filterCommands()) {
            return;
        }

        String command = event.getMessage();
        if (isWhitelistedCommand(command, config.commandWhitelist())) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("simpchatcensor.bypass")) {
            spamDetector.clear(player.getUniqueId());
            return;
        }

        Optional<DetectionResult> interval = spamDetector.detectCommandInterval(
                player.getUniqueId(),
                command
        );
        if (interval.isPresent()) {
            actionExecutor.applyTextAction(
                    player,
                    command,
                    interval.orElseThrow(),
                    filtered -> event.setCancelled(true),
                    () -> event.setCancelled(true),
                    false,
                    "CMD"
            );
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
                true,
                "CMD"
        ));
    }

    public static boolean isWhitelistedCommand(String command, Collection<String> commandWhitelist) {
        String root = commandRoot(command);
        if (root == null || commandWhitelist == null) {
            return false;
        }

        return commandWhitelist.stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .anyMatch(entry -> root.equalsIgnoreCase(entry));
    }

    private static String commandRoot(String command) {
        if (command == null) {
            return null;
        }

        String trimmed = command.trim();
        if (!trimmed.startsWith("/")) {
            return null;
        }

        String root = firstToken(trimmed.substring(1));
        int namespaceSeparator = root.indexOf(':');
        if (namespaceSeparator >= 0) {
            if (namespaceSeparator + 1 == root.length()) {
                return null;
            }
            root = root.substring(namespaceSeparator + 1);
        }
        return root.isEmpty() ? null : root;
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
