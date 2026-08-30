package com.simpchatcensor.listener;

import com.simpchatcensor.ActionExecutor;
import com.simpchatcensor.detection.BannedWordManager;
import com.simpchatcensor.detection.DetectionResult;
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
        SpamDetector.ChatInspection chatInspection = spamDetector.inspectChat(player.getUniqueId(), message);
        Optional<DetectionResult> spamResult = chatInspection.result();
        if (spamResult.isPresent()) {
            actionExecutor.applyTextAction(
                    player,
                    message,
                    spamResult.orElseThrow(),
                    filtered -> event.message(Component.text(filtered)),
                    () -> event.setCancelled(true),
                    false
            );
            return;
        }

        Optional<DetectionResult> contentResult = bannedWordManager.scan(message);
        if (contentResult.isPresent()) {
            DetectionResult detectionResult = contentResult.orElseThrow();
            boolean cancelled = actionExecutor.applyTextAction(
                    player,
                    message,
                    detectionResult,
                    filtered -> event.message(Component.text(filtered)),
                    () -> event.setCancelled(true),
                    true
            );
            if (cancelled || event.isCancelled()) {
                return;
            }
        }

        // Re-check atomically after content actions. A concurrent message from
        // the same player may have committed history during the content scan.
        spamDetector.detectAfterInspection(player.getUniqueId(), message, chatInspection)
                .ifPresent(detectionResult -> actionExecutor.applyTextAction(
                        player,
                        message,
                        detectionResult,
                        filtered -> event.message(Component.text(filtered)),
                        () -> event.setCancelled(true),
                        false
                ));
    }
}
