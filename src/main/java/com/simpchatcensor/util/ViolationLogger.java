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
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ViolationLogger implements AutoCloseable {
    private static final String LOG_DIRECTORY = "violations";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final int FILE_QUEUE_CAPACITY = 4096;
    private static final long CLOSE_TIMEOUT_SECONDS = 10;
    private final Logger logger;
    private final Clock clock;
    private final ThreadPoolExecutor writer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean queueFullWarningLogged = new AtomicBoolean();

    public ViolationLogger(Logger logger) {
        this(logger, Clock.systemDefaultZone());
    }

    ViolationLogger(Logger logger, Clock clock) {
        this.logger = logger;
        this.clock = clock;
        this.writer = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(FILE_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "SimpChatCensor-ViolationLogger");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /** Enqueues a violation without blocking the caller on logging I/O. */
    public void log(PluginConfig config, Player player, DetectionResult result, String originalText) {
        logAsync(config, player, result, originalText, "CHAT");
    }

    /** Enqueues a violation without blocking the caller on logging I/O. */
    public void log(
            PluginConfig config,
            Player player,
            DetectionResult result,
            String originalText,
            String source
    ) {
        logAsync(config, player, result, originalText, source);
    }

    /** Enqueues a violation without doing console or file I/O on the caller. */
    public void logAsync(PluginConfig config, Player player, DetectionResult result, String originalText) {
        logAsync(config, player, result, originalText, "CHAT");
    }

    /** Enqueues a violation without doing console or file I/O on the caller. */
    public void logAsync(
            PluginConfig config,
            Player player,
            DetectionResult result,
            String originalText,
            String source
    ) {
        enqueue(prepare(config, player, result, originalText, source));
    }

    private QueuedViolation prepare(
            PluginConfig config,
            Player player,
            DetectionResult result,
            String originalText,
            String source
    ) {
        String matchType = result.type().key().toUpperCase(Locale.ROOT);
        String context = source == null || source.isBlank()
                ? "CHAT"
                : sanitize(source).trim().toUpperCase(Locale.ROOT);
        String playerName = sanitize(player.getName());
        String match = sanitize(result.rule());
        String caught = sanitize(originalText);

        String[] consoleLines = {
                "------- Match Type: " + matchType + " ~ " + context,
                "Match: " + match,
                "Catch > " + playerName + ": " + caught
        };

        Path logFile = null;
        String line = null;
        if (config.logToFile()) {
            OffsetDateTime timestamp = OffsetDateTime.now(clock);
            LocalDate date = timestamp.toLocalDate();
            logFile = config.dataFolder().resolve(LOG_DIRECTORY).resolve(date + ".log");
            line = "%s\t%s\t%s\t%s\t%s%n".formatted(
                    timestamp.format(TIMESTAMP_FORMAT),
                    playerName,
                    sanitize(result.type().key()),
                    match,
                    caught
            );
        }

        return new QueuedViolation(consoleLines, logFile, line);
    }

    private void enqueue(QueuedViolation violation) {
        if (closed.get()) {
            return;
        }

        try {
            writer.execute(() -> write(violation));
        } catch (RejectedExecutionException exception) {
            if (closed.get()) {
                return;
            }
            // Drop this record rather than running disk or console work on the
            // chat thread when a pathological flood fills the bounded queue.
            if (queueFullWarningLogged.compareAndSet(false, true)) {
                logger.warning("Violation log queue is full; records are being dropped until it drains.");
            }
        }
    }

    private void write(QueuedViolation violation) {
        for (String line : violation.consoleLines()) {
            logger.info(line);
        }
        if (violation.logFile() == null) {
            return;
        }

        try {
            Files.createDirectories(violation.logFile().getParent());
            Files.writeString(
                    violation.logFile(),
                    violation.fileLine(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException | SecurityException exception) {
            logger.log(Level.WARNING, "Failed to write violation log file " + violation.logFile(), exception);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        writer.shutdown();
        try {
            if (!writer.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                logger.warning("Violation log writer did not finish before shutdown; pending records were dropped.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private record QueuedViolation(String[] consoleLines, Path logFile, String fileLine) {
    }

    private static String sanitize(String value) {
        return value == null
                ? ""
                : value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
    }
}
