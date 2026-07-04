package com.simpchatcensor.config;

import java.nio.file.Path;
import java.util.List;

public record PluginConfig(
        DetectionAction defaultAction,
        boolean useRegex,
        boolean advancedPreprocess,
        boolean filterCommands,
        boolean notifyStaff,
        boolean logToFile,
        String staffMessage,
        String playerWarning,
        String kickMessage,
        PinyinConfig pinyin,
        LevenshteinConfig levenshtein,
        IpDomainConfig ipDomain,
        List<String> bannedWords,
        Path dataFolder
) {
    public record PinyinConfig(boolean enabled, int maxCombinations) {
    }

    public record LevenshteinConfig(boolean enabled, int globalThreshold, int maxChecksPerMessage) {
    }

    public record IpDomainConfig(
            boolean enabled,
            boolean checkIpv4,
            boolean checkIpv6,
            boolean checkDomains,
            boolean blockPrivateIps,
            boolean geoIpEnabled,
            String geoIpDatabase,
            String geoIpDownloadUrl,
            List<String> blockedCountries,
            List<String> allowedCountries,
            List<String> cidrBlacklist,
            List<String> ipWhitelist,
            List<String> domainWhitelist,
            DetectionAction action
    ) {
    }
}
