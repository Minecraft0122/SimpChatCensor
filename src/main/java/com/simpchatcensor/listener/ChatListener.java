package com.simpchatcensor.listener;

import com.simpchatcensor.ActionExecutor;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.detection.DetectionResult;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;

public final class ChatListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private final BannedWordManager bannedWordManager;
    private final ActionExecutor actionExecutor;

    public ChatListener(BannedWordManager bannedWordManager, ActionExecutor actionExecutor) {
        this.bannedWordManager = bannedWordManager;
        this.actionExecutor = actionExecutor;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("simpchatcensor.bypass")) {
            return;
        }

        String message = PLAIN_TEXT.serialize(event.message());
        Optional<DetectionResult> result = bannedWordManager.scan(message);
        result.ifPresent(detectionResult -> actionExecutor.applyTextAction(
                player,
                message,
                detectionResult,
                filtered -> event.message(Component.text(filtered)),
                () -> event.setCancelled(true),
                true
        ));
    }
}
