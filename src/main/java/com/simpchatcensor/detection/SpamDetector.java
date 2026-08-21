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
 * Tracks short-lived per-player content state. The state is deliberately kept
 * outside the text detector because spam and minimum-interval rules depend on
 * player history.
 */
public final class SpamDetector implements AutoCloseable {
    private static final int MAX_TRACKED_PLAYERS = 10_000;
    private static final int CLEANUP_INTERVAL = 256;
    private static final int MAX_REPEATED_SCAN_LENGTH = 8_192;
    private static final int MAX_EXACT_DUPLICATE_LENGTH = 16_384;
    private static final int MAX_SIMILAR_MESSAGE_LENGTH = 4_096;
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
            if (playerId == null || message == null || message.isBlank()) {
                return Optional.empty();
            }

            PlayerState state = states.computeIfAbsent(playerId, ignored -> new PlayerState());
            synchronized (state) {
                Optional<DetectionResult> minimumInterval = detectMinimumInterval(
                        state,
                        message,
                        nowMillis,
                        snapshot.minimumInterval().messageMillis(),
                        "message"
                );
                Optional<DetectionResult> duplicate = snapshot.enabled()
                        ? detectDuplicate(state, message, nowMillis, snapshot)
                        : Optional.empty();
                Optional<DetectionResult> similar = snapshot.enabled()
                        ? detectSimilarMessage(state, message, nowMillis, snapshot.similarMessage())
                        : Optional.empty();
                Optional<DetectionResult> repeated = snapshot.enabled()
                        ? detectRepeatedContent(message, snapshot)
                        : Optional.empty();
                Optional<DetectionResult> longBurst = snapshot.enabled()
                        ? detectLongMessageBurst(state, message, nowMillis, snapshot)
                        : Optional.empty();

                state.lastMessage = message.length() <= MAX_EXACT_DUPLICATE_LENGTH ? message : null;
                state.lastMessageLength = message.length();
                state.lastMessageDigest = message.length() <= MAX_EXACT_DUPLICATE_LENGTH
                        ? new byte[0]
                        : fingerprint(message);
                state.lastComparableMessage = comparableMessage(message);
                state.lastMessageAt = nowMillis;
                state.hasMessageAt = true;
                state.lastActivityAt = nowMillis;
                cleanupIfNeeded(nowMillis, snapshot);
                if (minimumInterval.isPresent()) {
                    return minimumInterval;
                }
                if (duplicate.isPresent()) {
                    return duplicate;
                }
                if (similar.isPresent()) {
                    return similar;
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

    public Optional<DetectionResult> detectCommandInterval(UUID playerId, String command) {
        return detectCommandInterval(playerId, command, System.currentTimeMillis());
    }

    /** Visible for deterministic unit tests; timestamps are epoch milliseconds. */
    public Optional<DetectionResult> detectCommandInterval(UUID playerId, String command, long nowMillis) {
        lifecycle.readLock().lock();
        try {
            PluginConfig.SpamConfig snapshot = config;
            int minimumMillis = snapshot.minimumInterval().commandMillis();
            if (minimumMillis <= 0 || playerId == null || command == null || command.isBlank()) {
                return Optional.empty();
            }

            PlayerState state = states.computeIfAbsent(playerId, ignored -> new PlayerState());
            synchronized (state) {
                Optional<DetectionResult> result = detectMinimumInterval(
                        state,
                        command,
                        nowMillis,
                        minimumMillis,
                        "command"
                );
                state.lastActivityAt = nowMillis;
                cleanupIfNeeded(nowMillis, snapshot);
                return result;
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

    private static Optional<DetectionResult> detectMinimumInterval(
            PlayerState state,
            String content,
            long nowMillis,
            int minimumMillis,
            String contentType
    ) {
        Optional<DetectionResult> result = Optional.empty();
        if (minimumMillis > 0 && state.hasContentAt) {
            long elapsed = nowMillis - state.lastContentAt;
            if (elapsed >= 0 && elapsed < minimumMillis) {
                result = Optional.of(DetectionResult.of(
                        DetectionType.SPAM,
                        content,
                        contentType + "-minimum-interval",
                        DetectionAction.CANCEL,
                        contentType + " interval " + minimumMillis + "ms"
                ));
            }
        }
        state.lastContentAt = nowMillis;
        state.hasContentAt = true;
        return result;
    }

    private static Optional<DetectionResult> detectSimilarMessage(
            PlayerState state,
            String message,
            long nowMillis,
            PluginConfig.SimilarMessageConfig config
    ) {
        if (!config.enabled() || state.lastComparableMessage == null) {
            return Optional.empty();
        }

        long windowMillis = config.windowSeconds() * 1000L;
        if (!state.hasMessageAt || !elapsedWithin(state.lastMessageAt, nowMillis, windowMillis)) {
            return Optional.empty();
        }

        String current = comparableMessage(message);
        if (current == null || current.equals(state.lastComparableMessage)) {
            return Optional.empty();
        }

        int commonPrefixLength = commonPrefixLength(state.lastComparableMessage, current);
        int previousSuffixLength = state.lastComparableMessage.length() - commonPrefixLength;
        int currentSuffixLength = current.length() - commonPrefixLength;
        if (commonPrefixLength < config.minimumCommonPrefixLength()
                || previousSuffixLength > config.maximumSuffixLength()
                || currentSuffixLength > config.maximumSuffixLength()) {
            return Optional.empty();
        }

        return Optional.of(DetectionResult.of(
                DetectionType.SPAM,
                message,
                "similar-message",
                DetectionAction.CANCEL,
                "same prefix " + commonPrefixLength + " chars, suffixes <= "
                        + config.maximumSuffixLength() + " chars"
        ));
    }

    private static String comparableMessage(String message) {
        if (message.length() > MAX_SIMILAR_MESSAGE_LENGTH) {
            return null;
        }
        String comparable = TextPreprocessor.basicLower(message).strip();
        return comparable.isEmpty() ? null : comparable;
    }

    private static int commonPrefixLength(String first, String second) {
        int limit = Math.min(first.length(), second.length());
        int index = 0;
        while (index < limit && first.charAt(index) == second.charAt(index)) {
            index++;
        }
        return index;
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
                Math.max(snapshot.duplicateWindowSeconds(), snapshot.longMessageBurst().windowSeconds()),
                snapshot.similarMessage().windowSeconds()
        ) * 1000L * 2L;
        maxAge = Math.max(maxAge, snapshot.minimumInterval().messageMillis() * 2L);
        maxAge = Math.max(maxAge, snapshot.minimumInterval().commandMillis() * 2L);
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
                new PluginConfig.LongMessageConfig(false, 0, 0, 0),
                new PluginConfig.MinimumIntervalConfig(0, 0),
                new PluginConfig.SimilarMessageConfig(false, 0, 0, 0)
        );
    }

    private static final class PlayerState {
        private volatile String lastMessage;
        private volatile int lastMessageLength;
        private volatile byte[] lastMessageDigest = new byte[0];
        private volatile String lastComparableMessage;
        private volatile long lastMessageAt;
        private volatile boolean hasMessageAt;
        private volatile long lastActivityAt;
        private volatile long lastContentAt;
        private volatile boolean hasContentAt;
        private final Deque<Long> longMessageTimes = new ArrayDeque<>();
    }
}
