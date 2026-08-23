package com.simpchatcensor.util;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import com.simpchatcensor.detection.DetectionResult;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViolationLoggerTest {
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");

    @TempDir
    Path tempDir;

    @Test
    void writesFiveFieldsToSeparateLocalDateFilesWithoutCallingUuid() throws Exception {
        PluginConfig config = config(true);
        DetectionResult result = DetectionResult.of(
                com.simpchatcensor.detection.DetectionType.SPAM,
                "bad",
                "duplicate-message",
                DetectionAction.CANCEL,
                "duplicate"
        );
        Player player = playerWithoutUuid("Alice");

        ViolationLogger beforeMidnight = new ViolationLogger(
                Logger.getAnonymousLogger(),
                Clock.fixed(Instant.parse("2026-08-21T15:59:59Z"), SERVER_ZONE)
        );
        beforeMidnight.log(config, player, result, "line\tone\nline2");
        beforeMidnight.log(config, player, result, "same-day");
        new ViolationLogger(
                Logger.getAnonymousLogger(),
                Clock.fixed(Instant.parse("2026-08-21T16:00:01Z"), SERVER_ZONE)
        ).log(config, player, result, "next");

        Path first = tempDir.resolve("violations/2026-08-21.log");
        Path second = tempDir.resolve("violations/2026-08-22.log");
        assertTrue(Files.isRegularFile(first));
        assertTrue(Files.isRegularFile(second));
        assertEquals(2, Files.readAllLines(first).size());
        assertFalse(Files.exists(tempDir.resolve("violations.log")));

        String firstLine = Files.readAllLines(first).get(0);
        String[] fields = firstLine.split("\\t", -1);
        assertEquals(5, fields.length, firstLine);
        assertTrue(firstLine.contains("Alice"));
        assertTrue(firstLine.contains("duplicate-message"));
        assertFalse(firstLine.contains("00000000-0000-0000-0000-000000000001"));
        assertFalse(firstLine.contains("\n"));
        assertFalse(firstLine.contains("\tone"));
    }

    @Test
    void doesNotCreateViolationDirectoryWhenFileLoggingIsDisabled() {
        new ViolationLogger(
                Logger.getAnonymousLogger(),
                Clock.fixed(Instant.parse("2026-08-21T16:00:01Z"), SERVER_ZONE)
        ).log(
                config(false),
                playerWithoutUuid("Alice"),
                DetectionResult.of(
                        com.simpchatcensor.detection.DetectionType.LITERAL,
                        "bad",
                        "bad",
                        DetectionAction.CANCEL,
                        ""
                ),
                "bad"
        );

        assertFalse(Files.exists(tempDir.resolve("violations")));
    }

    @Test
    void printsChatFilterStyleThreeLineConsoleSummary() {
        CapturingHandler handler = new CapturingHandler();
        Logger logger = Logger.getAnonymousLogger();
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);

        new ViolationLogger(
                logger,
                Clock.fixed(Instant.parse("2026-08-21T16:00:01Z"), SERVER_ZONE)
        ).log(
                config(false),
                playerWithoutUuid("Alice"),
                DetectionResult.of(
                        com.simpchatcensor.detection.DetectionType.DOMAIN,
                        "cnpvp,top",
                        "cnpvp.top",
                        DetectionAction.CANCEL,
                        "domain"
                ),
                "say cnpvp,top\nnext",
                "CMD"
        );

        assertEquals(
                List.of(
                        "------- Match Type: DOMAIN ~ CMD",
                        "Match: cnpvp.top",
                        "Catch > Alice: say cnpvp,top next"
                ),
                handler.messages
        );
    }

    private PluginConfig config(boolean logToFile) {
        return new PluginConfig(
                DetectionAction.CANCEL,
                false,
                true,
                true,
                PluginConfig.DEFAULT_COMMAND_WHITELIST,
                false,
                logToFile,
                "",
                "",
                "",
                "",
                new PluginConfig.PinyinConfig(false, 1),
                new PluginConfig.LevenshteinConfig(false, 0, 1),
                new PluginConfig.IpDomainConfig(
                        false,
                        false,
                        false,
                        false,
                        false,
                        "GeoLite2-Country.mmdb",
                        "",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        DetectionAction.CANCEL
                ),
                List.of(),
                tempDir,
                new PluginConfig.SpamConfig(
                        false,
                        5,
                        new PluginConfig.RepeatedContentConfig(false, 4, 1, 16, 12),
                        new PluginConfig.LongMessageConfig(false, 300, 3, 10)
                )
        );
    }

    private static Player playerWithoutUuid(String name) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) {
                        return name;
                    }
                    if (method.getName().equals("getUniqueId")) {
                        throw new AssertionError("violation logs must not read player UUID");
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == byte.class
                            || method.getReturnType() == short.class
                            || method.getReturnType() == int.class
                            || method.getReturnType() == long.class
                            || method.getReturnType() == float.class
                            || method.getReturnType() == double.class) {
                        return 0;
                    }
                    if (method.getReturnType() == char.class) {
                        return '\0';
                    }
                    return null;
                }
        );
    }

    private static final class CapturingHandler extends Handler {
        private final List<String> messages = new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
