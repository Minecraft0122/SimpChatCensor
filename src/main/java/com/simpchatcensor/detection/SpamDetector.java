package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Tracks short-lived per-player chat state. The state is deliberately kept
 * outside the text detector because spam rules depend on message history.
 */
public final class SpamDetector implements AutoCloseable {
    private static final int MAX_TRACKED_PLAYERS = 10_000;
    private static final int CLEANUP_INTERVAL = 256;
    private static final int MAX_REPEATED_SCAN_LENGTH = 8_192;
    private static final int MAX_EXACT_DUPLICATE_LENGTH = 16_384;
    private static final int MAX_REPEATED_COMPARISONS = 200_000;
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    });
    private final ConcurrentMap<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final ReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private final AtomicInteger detectionsSinceCleanup = new AtomicInteger();
    private volatile PluginConfig.SpamConfig config = disabledConfig();

    public void reload(PluginConfig newConfig) {
        reload(newConfig.spam());
    }

    public void reload(PluginConfig.SpamConfig newConfig) {
        lifecycle.writeLock().lock();
        try {
            config = newConfig;
            states.clear();
            detectionsSinceCleanup.set(0);
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    public Optional<DetectionResult> detect(UUID playerId, String message) {
        return detect(playerId, message, System.currentTimeMillis());
    }

    /** Visible for deterministic unit tests; timestamps are epoch milliseconds. */
    public Optional<DetectionResult> detect(UUID playerId, String message, long nowMillis) {
        lifecycle.readLock().lock();
        try {
            PluginConfig.SpamConfig snapshot = config;
            if (!snapshot.enabled() || playerId == null || message == null || message.isBlank()) {
                return Optional.empty();
            }

            PlayerState state = states.computeIfAbsent(playerId, ignored -> new PlayerState());
            synchronized (state) {
                Optional<DetectionResult> duplicate = detectDuplicate(state, message, nowMillis, snapshot);
                Optional<DetectionResult> repeated = detectRepeatedContent(message, snapshot);
                Optional<DetectionResult> longBurst = detectLongMessageBurst(state, message, nowMillis, snapshot);

                state.lastMessage = message.length() <= MAX_EXACT_DUPLICATE_LENGTH ? message : null;
                state.lastMessageLength = message.length();
                state.lastMessageDigest = message.length() <= MAX_EXACT_DUPLICATE_LENGTH
                        ? new byte[0]
                        : fingerprint(message);
                state.lastMessageAt = nowMillis;
                state.lastActivityAt = nowMillis;
                cleanupIfNeeded(nowMillis, snapshot);
                if (duplicate.isPresent()) {
                    return duplicate;
                }
                if (repeated.isPresent()) {
                    return repeated;
                }
                return longBurst;
            }
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    public void clear(UUID playerId) {
        lifecycle.writeLock().lock();
        try {
            if (playerId != null) {
                states.remove(playerId);
            }
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        lifecycle.writeLock().lock();
        try {
            states.clear();
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    private static Optional<DetectionResult> detectDuplicate(
            PlayerState state,
            String message,
            long nowMillis,
            PluginConfig.SpamConfig config
    ) {
        long windowMillis = config.duplicateWindowSeconds() * 1000L;
        boolean sameMessage = state.lastMessage != null
                ? state.lastMessage.equals(message)
                : state.lastMessageLength == message.length()
                && Arrays.equals(state.lastMessageDigest, fingerprint(message));
        if (sameMessage
                && elapsedWithin(state.lastMessageAt, nowMillis, windowMillis)) {
            return Optional.of(DetectionResult.of(
                    DetectionType.SPAM,
                    message,
                    "duplicate message within " + config.duplicateWindowSeconds() + "s",
                    DetectionAction.CANCEL,
                    "duplicate-message"
            ));
        }
        return Optional.empty();
    }

    private static Optional<DetectionResult> detectRepeatedContent(
            String message,
            PluginConfig.SpamConfig config
    ) {
        PluginConfig.RepeatedContentConfig repeated = config.repeatedContent();
        if (!repeated.enabled() || message.length() < repeated.minTotalLength()) {
            return Optional.empty();
        }

        String scanText = message.length() <= MAX_REPEATED_SCAN_LENGTH
                ? message
                : message.substring(0, MAX_REPEATED_SCAN_LENGTH);
        int maxUnitLength = Math.min(repeated.maxSegmentLength(), scanText.length() / repeated.minRepeats());
        int comparisons = 0;
        for (int start = 0; start < scanText.length(); start++) {
            for (int unitLength = repeated.minSegmentLength(); unitLength <= maxUnitLength; unitLength++) {
                if (++comparisons > MAX_REPEATED_COMPARISONS) {
                    return Optional.empty();
                }
                if (start + unitLength * repeated.minRepeats() > scanText.length()) {
                    break;
                }

                String unit = scanText.substring(start, start + unitLength);
                int repeats = 1;
                int next = start + unitLength;
                while (next + unitLength <= scanText.length()
                        && scanText.regionMatches(next, unit, 0, unitLength)) {
                    if (++comparisons > MAX_REPEATED_COMPARISONS) {
                        return Optional.empty();
                    }
                    repeats++;
                    next += unitLength;
                }

                if (repeats >= repeated.minRepeats()
                        && unitLength * repeats >= repeated.minTotalLength()) {
                    String repeatedText = scanText.substring(start, next);
                    return Optional.of(DetectionResult.of(
                            DetectionType.SPAM,
                            repeatedText,
                            "repeated segment x" + repeats,
                            DetectionAction.CANCEL,
                            "repeated-content"
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<DetectionResult> detectLongMessageBurst(
            PlayerState state,
            String message,
            long nowMillis,
            PluginConfig.SpamConfig config
    ) {
        PluginConfig.LongMessageConfig longMessage = config.longMessageBurst();
        if (!longMessage.enabled() || message.length() < longMessage.minLength()) {
            pruneLongMessages(state.longMessageTimes, nowMillis, longMessage.windowSeconds() * 1000L);
            return Optional.empty();
        }

        long windowMillis = longMessage.windowSeconds() * 1000L;
        pruneLongMessages(state.longMessageTimes, nowMillis, windowMillis);
        state.longMessageTimes.addLast(nowMillis);
        while (state.longMessageTimes.size() > longMessage.maxMessages()) {
            state.longMessageTimes.removeFirst();
        }
        if (state.longMessageTimes.size() < longMessage.maxMessages()) {
            return Optional.empty();
        }

        return Optional.of(DetectionResult.of(
                DetectionType.SPAM,
                message,
                ">=" + longMessage.minLength() + " chars, "
                        + longMessage.maxMessages() + " messages / " + longMessage.windowSeconds() + "s",
                DetectionAction.CANCEL,
                "long-message-burst"
        ));
    }

    private void cleanupIfNeeded(long nowMillis, PluginConfig.SpamConfig snapshot) {
        int detections = detectionsSinceCleanup.incrementAndGet();
        if (detections < CLEANUP_INTERVAL && states.size() <= MAX_TRACKED_PLAYERS) {
            return;
        }
        detectionsSinceCleanup.set(0);
        long maxAge = Math.max(
                snapshot.duplicateWindowSeconds(),
                snapshot.longMessageBurst().windowSeconds()
        ) * 1000L * 2L;
        for (var entry : states.entrySet()) {
            PlayerState state = entry.getValue();
            if (nowMillis - state.lastActivityAt > maxAge || states.size() > MAX_TRACKED_PLAYERS) {
                states.remove(entry.getKey(), state);
            }
        }
    }

    private static void pruneLongMessages(Deque<Long> timestamps, long nowMillis, long windowMillis) {
        while (!timestamps.isEmpty() && !elapsedWithin(timestamps.peekFirst(), nowMillis, windowMillis)) {
            timestamps.removeFirst();
        }
    }

    private static boolean elapsedWithin(long previousMillis, long nowMillis, long windowMillis) {
        long elapsed = nowMillis - previousMillis;
        return elapsed >= 0 && elapsed <= windowMillis;
    }

    private static byte[] fingerprint(String message) {
        MessageDigest digest = SHA256.get();
        digest.reset();
        return digest.digest(message.getBytes(StandardCharsets.UTF_8));
    }

    private static PluginConfig.SpamConfig disabledConfig() {
        return new PluginConfig.SpamConfig(
                false,
                0,
                new PluginConfig.RepeatedContentConfig(false, 0, 0, 0, 0),
                new PluginConfig.LongMessageConfig(false, 0, 0, 0)
        );
    }

    private static final class PlayerState {
        private volatile String lastMessage;
        private volatile int lastMessageLength;
        private volatile byte[] lastMessageDigest = new byte[0];
        private volatile long lastMessageAt;
        private volatile long lastActivityAt;
        private final Deque<Long> longMessageTimes = new ArrayDeque<>();
    }
}
