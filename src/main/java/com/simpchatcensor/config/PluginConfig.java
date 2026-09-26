package com.simpchatcensor.config;

import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record PluginConfig(
        DetectionAction defaultAction,
        boolean useRegex,
        boolean advancedPreprocess,
        boolean filterCommands,
        List<String> commandWhitelist,
        boolean notifyStaff,
        boolean logToFile,
        String staffMessage,
        String playerWarning,
        String spamWarning,
        String kickMessage,
        PinyinConfig pinyin,
        LevenshteinConfig levenshtein,
        IpDomainConfig ipDomain,
        List<String> bannedWords,
        Path dataFolder,
        SpamConfig spam
) {
    public static final int MIN_MESSAGE_HISTORY_SECONDS = 15;
    public static final List<String> DEFAULT_COMMAND_WHITELIST = List.of("scc", "lp", "luckperms");
    /**
     * IANA root-zone suffixes bundled with the plugin. The list is loaded once
     * at startup/reload and remains immutable for chat-thread scans.
     */
    public static final List<String> DEFAULT_DOMAIN_SUFFIXES = loadDefaultDomainSuffixes();

    private static List<String> loadDefaultDomainSuffixes() {
        List<String> suffixes = new ArrayList<>();
        try (InputStream stream = PluginConfig.class.getClassLoader().getResourceAsStream("domain-suffixes.txt")) {
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String suffix = line.trim().toLowerCase(Locale.ROOT);
                        if (!suffix.isEmpty() && !suffix.startsWith("#") && !suffixes.contains(suffix)) {
                            suffixes.add(suffix);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // The fallback keeps the detector useful if a malformed jar omits the snapshot.
        }
        if (suffixes.isEmpty()) {
            suffixes.addAll(List.of(
                    "com", "net", "org", "cn", "uk", "hk", "zip", "ai", "cloud", "tech",
                    "top", "xyz", "cc", "me", "tv", "io", "co", "info", "biz", "app", "dev"
            ));
        }
        return List.copyOf(suffixes);
    }

    public record PinyinConfig(boolean enabled, int maxCombinations) {
    }

    public record LevenshteinConfig(boolean enabled, int globalThreshold, int maxChecksPerMessage) {
    }

    public record SpamConfig(
            boolean enabled,
            int duplicateWindowSeconds,
            RepeatedContentConfig repeatedContent,
            LongMessageConfig longMessageBurst,
            MinimumIntervalConfig minimumInterval,
            SimilarMessageConfig similarMessage
    ) {
        /**
         * Keeps the old constructor useful for isolated callers that do not
         * need the new rate-limit rules.
         */
        public SpamConfig(
                boolean enabled,
                int duplicateWindowSeconds,
                RepeatedContentConfig repeatedContent,
                LongMessageConfig longMessageBurst
        ) {
            this(
                    enabled,
                    duplicateWindowSeconds,
                    repeatedContent,
                    longMessageBurst,
                    new MinimumIntervalConfig(0, 0),
                    new SimilarMessageConfig(false, 0, 0, 0)
            );
        }
    }

    public record MinimumIntervalConfig(int messageMillis, int commandMillis) {
    }

    public record SimilarMessageConfig(
            boolean enabled,
            int windowSeconds,
            int minimumCommonPrefixLength,
            int maximumSuffixLength
    ) {
    }

    public record RepeatedContentConfig(
            boolean enabled,
            int minRepeats,
            int minSegmentLength,
            int maxSegmentLength,
            int minTotalLength
    ) {
    }

    public record LongMessageConfig(
            boolean enabled,
            int minLength,
            int maxMessages,
            int windowSeconds
    ) {
    }

    public record IpDomainConfig(
            boolean enabled,
            boolean checkIpv4,
            boolean checkIpv6,
            boolean checkDomains,
            boolean geoIpEnabled,
            String geoIpDatabase,
            String geoIpDownloadUrl,
            List<String> blockedCountries,
            List<String> allowedCountries,
            List<String> ipWhitelist,
            List<String> domainWhitelist,
            List<String> domainSuffixes,
            DetectionAction action
    ) {
        /**
         * Keeps the previous constructor source-compatible for integrations
         * that do not provide a domain suffix policy yet.
         */
        public IpDomainConfig(
                boolean enabled,
                boolean checkIpv4,
                boolean checkIpv6,
                boolean checkDomains,
                boolean geoIpEnabled,
                String geoIpDatabase,
                String geoIpDownloadUrl,
                List<String> blockedCountries,
                List<String> allowedCountries,
                List<String> ipWhitelist,
                List<String> domainWhitelist,
                DetectionAction action
        ) {
            this(
                    enabled,
                    checkIpv4,
                    checkIpv6,
                    checkDomains,
                    geoIpEnabled,
                    geoIpDatabase,
                    geoIpDownloadUrl,
                    blockedCountries,
                    allowedCountries,
                    ipWhitelist,
                    domainWhitelist,
                    DEFAULT_DOMAIN_SUFFIXES,
                    action
            );
        }
    }
}
