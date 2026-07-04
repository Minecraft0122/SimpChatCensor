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

        Player player = event.getPlayer();
        if (player.hasPermission("simpchatcensor.bypass")) {
            return;
        }

        String command = event.getMessage();
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
}
