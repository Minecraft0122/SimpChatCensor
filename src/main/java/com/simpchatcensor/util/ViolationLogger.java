package com.simpchatcensor.util;

import com.simpchatcensor.config.PluginConfig;
import com.simpchatcensor.detection.DetectionResult;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ViolationLogger {
    private static final String LOG_DIRECTORY = "violations";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private final Logger logger;
    private final Clock clock;

    public ViolationLogger(Logger logger) {
        this(logger, Clock.systemDefaultZone());
    }

    ViolationLogger(Logger logger, Clock clock) {
        this.logger = logger;
        this.clock = clock;
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

        OffsetDateTime timestamp = OffsetDateTime.now(clock);
        LocalDate date = timestamp.toLocalDate();
        Path logFile = config.dataFolder().resolve(LOG_DIRECTORY).resolve(date + ".log");
        String line = "%s\t%s\t%s\t%s\t%s%n".formatted(
                timestamp.format(TIMESTAMP_FORMAT),
                sanitize(player.getName()),
                sanitize(result.type().key()),
                sanitize(result.rule()),
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
            logger.log(Level.WARNING, "Failed to write violation log file " + logFile, exception);
        }
    }

    private static String sanitize(String value) {
        return value == null
                ? ""
                : value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }
}
