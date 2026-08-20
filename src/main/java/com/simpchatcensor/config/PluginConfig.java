package com.simpchatcensor.config;

import java.nio.file.Path;
import java.util.List;

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
        String kickMessage,
        PinyinConfig pinyin,
        LevenshteinConfig levenshtein,
        IpDomainConfig ipDomain,
        List<String> bannedWords,
        Path dataFolder,
        SpamConfig spam
) {
    public static final List<String> DEFAULT_COMMAND_WHITELIST = List.of("bwf", "lp", "luckperms");

    public record PinyinConfig(boolean enabled, int maxCombinations) {
    }

    public record LevenshteinConfig(boolean enabled, int globalThreshold, int maxChecksPerMessage) {
    }

    public record SpamConfig(
            boolean enabled,
            int duplicateWindowSeconds,
            RepeatedContentConfig repeatedContent,
            LongMessageConfig longMessageBurst
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
            DetectionAction action
    ) {
    }
}
