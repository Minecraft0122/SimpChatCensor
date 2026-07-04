package com.simpchatcensor.util;

import com.simpchatcensor.config.PluginConfig;
import com.simpchatcensor.detection.DetectionResult;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ViolationLogger {
    private final Logger logger;

    public ViolationLogger(Logger logger) {
        this.logger = logger;
    }

    public synchronized void log(PluginConfig config, Player player, DetectionResult result, String originalText) {
        logger.info(() -> "[SimpChatCensor] "
                + player.getName()
                + " triggered "
                + result.type().key()
                + " match="
                + result.detectedText()
                + " rule="
                + result.rule());

        if (!config.logToFile()) {
            return;
        }

        Path logFile = config.dataFolder().resolve("violations.log");
        String line = "%s\t%s\t%s\t%s\t%s\t%s%n".formatted(
                Instant.now(),
                player.getUniqueId(),
                player.getName(),
                result.type().key(),
                result.rule(),
                sanitize(originalText)
        );

        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(
                    logFile,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Failed to write violation log", exception);
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }
}
