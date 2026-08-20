package com.simpchatcensor.listener;

import com.simpchatcensor.ActionExecutor;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.detection.DetectionResult;
import com.simpchatcensor.detection.DetectionType;
import com.simpchatcensor.detection.SpamDetector;
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
    private final SpamDetector spamDetector;
    private final ActionExecutor actionExecutor;

    public ChatListener(
            BannedWordManager bannedWordManager,
            SpamDetector spamDetector,
            ActionExecutor actionExecutor
    ) {
        this.bannedWordManager = bannedWordManager;
        this.spamDetector = spamDetector;
        this.actionExecutor = actionExecutor;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("simpchatcensor.bypass")) {
            spamDetector.clear(player.getUniqueId());
            return;
        }

        String message = PLAIN_TEXT.serialize(event.message());
        Optional<DetectionResult> spamResult = spamDetector.detect(player.getUniqueId(), message);
        Optional<DetectionResult> result = spamResult.isPresent()
                ? spamResult
                : bannedWordManager.scan(message);
        result.ifPresent(detectionResult -> actionExecutor.applyTextAction(
                player,
                message,
                detectionResult,
                filtered -> event.message(Component.text(filtered)),
                () -> event.setCancelled(true),
                detectionResult.type() != DetectionType.SPAM
        ));
    }
}
