package com.simpchatcensor.listener;

import com.simpchatcensor.ActionExecutor;
import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.detection.DetectionResult;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Optional;

public final class ConnectionListener implements Listener {
    private final BannedWordManager bannedWordManager;
    private final ActionExecutor actionExecutor;

    public ConnectionListener(BannedWordManager bannedWordManager, ActionExecutor actionExecutor) {
        this.bannedWordManager = bannedWordManager;
        this.actionExecutor = actionExecutor;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        Optional<DetectionResult> result = bannedWordManager.scanRemoteAddress(event.getAddress());
        if (result.isEmpty()) {
            return;
        }

        DetectionResult detectionResult = result.get();
        actionExecutor.record(event.getPlayer(), detectionResult, event.getAddress().getHostAddress());
        DetectionAction action = detectionResult.action();
        if (action == DetectionAction.CANCEL || action == DetectionAction.KICK || action == DetectionAction.FILTER) {
            event.disallow(
                    PlayerLoginEvent.Result.KICK_BANNED,
                    actionExecutor.loginKickMessage(event.getPlayer(), detectionResult)
            );
        }
    }
}
