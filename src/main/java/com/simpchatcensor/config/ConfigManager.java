package com.simpchatcensor.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigManager {
    private static final String MESSAGE_BLACKLIST_FILE = "message-blacklist.yml";
    private static final String IP_WHITELIST_FILE = "ip-whitelist.yml";
    private static final String BANNED_WORDS_PATH = "banned-words";
    private static final String IP_WHITELIST_PATH = "ip-whitelist";
    private static final String BANNED_WORD_LIBRARY_VERSION_PATH = "library-version";
    private static final int BUNDLED_BANNED_WORD_LIBRARY_VERSION = 1;
    private static final List<String> LEGACY_BUNDLED_BANNED_WORDS = List.of("fuck", "混蛋");

    private final JavaPlugin plugin;
    private PluginConfig config;
    private YamlConfiguration messageBlacklist;
    private YamlConfiguration ipWhitelist;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration source = plugin.getConfig();
        boolean messageBlacklistExisted = dataFile(MESSAGE_BLACKLIST_FILE).isFile();
        boolean ipWhitelistExisted = dataFile(IP_WHITELIST_FILE).isFile();
        this.messageBlacklist = loadDataFile(MESSAGE_BLACKLIST_FILE);
        this.ipWhitelist = loadDataFile(IP_WHITELIST_FILE);
        migrateLegacyLists(source, messageBlacklistExisted, ipWhitelistExisted);
        mergeBundledBannedWords();

        DetectionAction defaultAction = DetectionAction.fromString(source.getString("action"), DetectionAction.CANCEL);
        DetectionAction ipAction = DetectionAction.fromString(
                source.getString("ip-domain-detection.action"),
                defaultAction
        );
        boolean commandWhitelistConfigured = source.contains("command-whitelist", true);
        boolean commandWhitelistIsList = source.isList("command-whitelist");
        if (commandWhitelistConfigured && !commandWhitelistIsList) {
            plugin.getLogger().warning(
                    "command-whitelist must be a YAML list; no command roots will bypass content checks."
            );
        }
        boolean domainSuffixesConfigured = source.contains("ip-domain-detection.domain-suffixes", true);
        boolean domainSuffixesIsList = source.isList("ip-domain-detection.domain-suffixes");
        if (domainSuffixesConfigured && !domainSuffixesIsList) {
            plugin.getLogger().warning(
                    "ip-domain-detection.domain-suffixes must be a YAML list; built-in suffixes will be used."
            );
        }

        this.config = new PluginConfig(
                defaultAction,
                source.getBoolean("use-regex", false),
                source.getBoolean("advanced-preprocess", true),
                source.getBoolean("filter-commands", true),
                resolveCommandWhitelist(
                        commandWhitelistConfigured,
                        commandWhitelistIsList,
                        source.getStringList("command-whitelist")
                ),
                source.getBoolean("notify-staff", true),
                source.getBoolean("log-to-file", true),
                source.getString("staff-message", "&c[SimpChatCensor] &f%player% &7触发 &e%type%&7: &f%match%"),
                source.getString("player-warning", "&c请不要发送违规内容。"),
                source.getString("spam-warning", "&e消息发送过快，请稍后再试。"),
                source.getString("kick-message", "&c你因发送违规内容被移出服务器。"),
                new PluginConfig.PinyinConfig(
                        source.getBoolean("fuzzy-pinyin.enabled", true),
                        positive(source.getInt("fuzzy-pinyin.max-combinations", 32), 32)
                ),
                new PluginConfig.LevenshteinConfig(
                        source.getBoolean("fuzzy-levenshtein.enabled", false),
                        Math.max(0, source.getInt("fuzzy-levenshtein.global-threshold", 1)),
                        positive(source.getInt("fuzzy-levenshtein.max-checks-per-message", 50), 50)
                ),
                new PluginConfig.IpDomainConfig(
                        source.getBoolean("ip-domain-detection.enabled", true),
                        source.getBoolean("ip-domain-detection.check-ipv4", true),
                        source.getBoolean("ip-domain-detection.check-ipv6", true),
                        source.getBoolean("ip-domain-detection.check-domains", true),
                        source.getBoolean("ip-domain-detection.geoip-enabled", false),
                        source.getString("ip-domain-detection.geoip-database", "GeoLite2-Country.mmdb"),
                        source.getString(
                                "ip-domain-detection.geoip-download-url",
                                "https://github.com/P3TERX/GeoLite.mmdb/releases/latest/download/GeoLite2-Country.mmdb"
                        ),
                        cleanList(source.getStringList("ip-domain-detection.blocked-countries")),
                        cleanList(source.getStringList("ip-domain-detection.allowed-countries")),
                        cleanList(ipWhitelist.getStringList(IP_WHITELIST_PATH)),
                        cleanDomainWhitelist(source.getStringList("ip-domain-detection.domain-whitelist")),
                        resolveDomainSuffixes(
                                domainSuffixesConfigured,
                                domainSuffixesIsList,
                                source.getStringList("ip-domain-detection.domain-suffixes")
                        ),
                        ipAction
                ),
                cleanList(messageBlacklist.getStringList(BANNED_WORDS_PATH)),
                plugin.getDataFolder().toPath(),
                loadSpamConfig(source)
        );
    }

    public PluginConfig getConfig() {
        if (config == null) {
            reload();
        }
        return config;
    }

    public boolean addBannedWord(String word) {
        String cleaned = cleanValue(word);
        if (cleaned.isEmpty()) {
            return false;
        }

        List<String> words = new ArrayList<>(messageBlacklist.getStringList(BANNED_WORDS_PATH));
        if (containsIgnoreCase(words, cleaned)) {
            return false;
        }
        words.add(cleaned);
        messageBlacklist.set(BANNED_WORDS_PATH, words);
        if (!saveDataFile(messageBlacklist, MESSAGE_BLACKLIST_FILE)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean removeBannedWord(String word) {
        String cleaned = cleanValue(word);
        List<String> words = new ArrayList<>(messageBlacklist.getStringList(BANNED_WORDS_PATH));
        boolean removed = words.removeIf(existing -> existing.equalsIgnoreCase(cleaned));
        if (!removed) {
            return false;
        }
        messageBlacklist.set(BANNED_WORDS_PATH, words);
        if (!saveDataFile(messageBlacklist, MESSAGE_BLACKLIST_FILE)) {
            return false;
        }
        reload();
        return true;
    }

    public boolean addWhitelistEntry(String entry) {
        String cleaned = cleanValue(entry);
        if (cleaned.isEmpty()) {
            return false;
        }

        boolean isIp = isIpEntry(cleaned);
        if (!isIp) {
            cleaned = DomainNormalizer.canonicalizeHost(cleaned);
            if (cleaned.isEmpty()) {
                return false;
            }
        }
        FileConfiguration target = isIp ? ipWhitelist : plugin.getConfig();
        String path = isIp ? IP_WHITELIST_PATH : "ip-domain-detection.domain-whitelist";
        List<String> values = new ArrayList<>(target.getStringList(path));
        if (containsIgnoreCase(values, cleaned)) {
            return false;
        }
        values.add(cleaned);
        target.set(path, values);
        if (isIp) {
            if (!saveDataFile(ipWhitelist, IP_WHITELIST_FILE)) {
                return false;
            }
        } else {
            plugin.saveConfig();
        }
        reload();
        return true;
    }

    private YamlConfiguration loadDataFile(String fileName) {
        File file = dataFile(fileName);
        if (!file.isFile()) {
            plugin.saveResource(fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    static PluginConfig.SpamConfig loadSpamConfig(FileConfiguration source) {
        int minSegmentLength = bounded(
                source.getInt("spam-detection.repeated-content.min-segment-length", 1),
                1,
                64,
                1
        );
        int maxSegmentLength = Math.max(
                minSegmentLength,
                bounded(
                        source.getInt("spam-detection.repeated-content.max-segment-length", 16),
                        1,
                        64,
                        16
                )
        );

        return new PluginConfig.SpamConfig(
                source.getBoolean("spam-detection.enabled", true),
                bounded(
                        source.getInt("spam-detection.duplicate-window-seconds", PluginConfig.MIN_MESSAGE_HISTORY_SECONDS),
                        PluginConfig.MIN_MESSAGE_HISTORY_SECONDS,
                        3600,
                        PluginConfig.MIN_MESSAGE_HISTORY_SECONDS
                ),
                new PluginConfig.RepeatedContentConfig(
                        source.getBoolean("spam-detection.repeated-content.enabled", true),
                        bounded(source.getInt("spam-detection.repeated-content.min-repeats", 4), 2, 100, 4),
                        minSegmentLength,
                        maxSegmentLength,
                        bounded(source.getInt("spam-detection.repeated-content.min-total-length", 12), 1, 100_000, 12)
                ),
                new PluginConfig.LongMessageConfig(
                        source.getBoolean("spam-detection.long-message-burst.enabled", true),
                        bounded(source.getInt("spam-detection.long-message-burst.min-length", 300), 1, 100_000, 300),
                        bounded(source.getInt("spam-detection.long-message-burst.max-messages", 3), 2, 100, 3),
                        bounded(source.getInt("spam-detection.long-message-burst.window-seconds", 10), 1, 3600, 10)
                ),
                new PluginConfig.MinimumIntervalConfig(
                        bounded(source.getInt("content-minimum-interval.message-millis", 200), 100, 60_000, 200),
                        bounded(source.getInt("content-minimum-interval.command-millis", 250), 100, 60_000, 250)
                ),
                new PluginConfig.SimilarMessageConfig(
                        source.getBoolean("spam-detection.similar-message.enabled", true),
                        bounded(
                                source.getInt("spam-detection.similar-message.window-seconds", PluginConfig.MIN_MESSAGE_HISTORY_SECONDS),
                                PluginConfig.MIN_MESSAGE_HISTORY_SECONDS,
                                3600,
                                PluginConfig.MIN_MESSAGE_HISTORY_SECONDS
                        ),
                        bounded(source.getInt("spam-detection.similar-message.minimum-common-prefix-length", 12), 4, 256, 12),
                        bounded(source.getInt("spam-detection.similar-message.maximum-suffix-length", 64), 1, 256, 64)
                )
        );
    }

    static List<String> resolveDomainSuffixes(
            boolean configured,
            boolean configuredAsList,
            List<String> configuredEntries
    ) {
        if (!configured || !configuredAsList) {
            return PluginConfig.DEFAULT_DOMAIN_SUFFIXES;
        }

        List<String> cleaned = new ArrayList<>();
        for (String entry : configuredEntries) {
            String suffix = cleanDomainSuffix(entry);
            if (!suffix.isEmpty() && !containsIgnoreCase(cleaned, suffix)) {
                cleaned.add(suffix);
            }
        }
        return List.copyOf(cleaned);
    }

    private void mergeBundledBannedWords() {
        int currentVersion = messageBlacklist.getInt(BANNED_WORD_LIBRARY_VERSION_PATH, 0);
        if (currentVersion >= BUNDLED_BANNED_WORD_LIBRARY_VERSION) {
            return;
        }

        YamlConfiguration bundled = loadBundledYaml(MESSAGE_BLACKLIST_FILE);
        if (bundled == null) {
            return;
        }

        List<String> merged = mergeBannedWordsForUpgrade(
                currentVersion,
                messageBlacklist.getStringList(BANNED_WORDS_PATH),
                bundled.getStringList(BANNED_WORDS_PATH)
        );
        messageBlacklist.set(BANNED_WORDS_PATH, merged);
        messageBlacklist.set(BANNED_WORD_LIBRARY_VERSION_PATH, BUNDLED_BANNED_WORD_LIBRARY_VERSION);
        if (!saveDataFile(messageBlacklist, MESSAGE_BLACKLIST_FILE)) {
            plugin.getLogger().warning("Failed to persist bundled banned-word library updates.");
        }
    }

    private YamlConfiguration loadBundledYaml(String fileName) {
        InputStream stream = plugin.getResource(fileName);
        if (stream == null) {
            plugin.getLogger().warning("Missing bundled resource: " + fileName);
            return null;
        }
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to read bundled resource " + fileName + ": " + exception.getMessage());
            return null;
        }
    }

    private File dataFile(String fileName) {
        return new File(plugin.getDataFolder(), fileName);
    }

    private void migrateLegacyLists(
            FileConfiguration source,
            boolean messageBlacklistExisted,
            boolean ipWhitelistExisted
    ) {
        boolean changedMainConfig = false;

        if (source.contains(BANNED_WORDS_PATH)) {
            migrateList(
                    messageBlacklist,
                    BANNED_WORDS_PATH,
                    source.getStringList(BANNED_WORDS_PATH),
                    messageBlacklistExisted
            );
            if (saveDataFile(messageBlacklist, MESSAGE_BLACKLIST_FILE)) {
                source.set(BANNED_WORDS_PATH, null);
                changedMainConfig = true;
            }
        }

        String legacyWhitelistPath = "ip-domain-detection.ip-whitelist";
        if (source.contains(legacyWhitelistPath)) {
            migrateList(
                    ipWhitelist,
                    IP_WHITELIST_PATH,
                    source.getStringList(legacyWhitelistPath),
                    ipWhitelistExisted
            );
            if (saveDataFile(ipWhitelist, IP_WHITELIST_FILE)) {
                source.set(legacyWhitelistPath, null);
                changedMainConfig = true;
            }
        }

        String legacyBlacklistPath = "ip-domain-detection.cidr-blacklist";
        if (source.contains(legacyBlacklistPath)) {
            source.set(legacyBlacklistPath, null);
            changedMainConfig = true;
        }

        if (source.contains("ip-domain-detection.block-private-ips")) {
            source.set("ip-domain-detection.block-private-ips", null);
            changedMainConfig = true;
        }

        if (changedMainConfig) {
            plugin.saveConfig();
        }
    }

    private static void migrateList(
            FileConfiguration target,
            String path,
            List<String> legacyValues,
            boolean targetExisted
    ) {
        if (!targetExisted) {
            target.set(path, cleanList(legacyValues));
            return;
        }
        mergeList(target, path, legacyValues);
    }

    private static void mergeList(FileConfiguration target, String path, List<String> legacyValues) {
        List<String> merged = new ArrayList<>(target.getStringList(path));
        for (String legacyValue : legacyValues) {
            String cleaned = cleanValue(legacyValue);
            if (!cleaned.isEmpty() && !containsIgnoreCase(merged, cleaned)) {
                merged.add(cleaned);
            }
        }
        target.set(path, merged);
    }

    private boolean saveDataFile(YamlConfiguration source, String fileName) {
        try {
            source.save(dataFile(fileName));
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save " + fileName + ": " + exception.getMessage());
            return false;
        }
    }

    private static boolean isIpEntry(String value) {
        return value.matches("^[0-9a-fA-F:.]+(?:/\\d{1,3})?$");
    }

    private static int bounded(int value, int minimum, int maximum, int fallback) {
        if (value < minimum) {
            return fallback;
        }
        return Math.min(value, maximum);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    static List<String> resolveCommandWhitelist(
            boolean configured,
            boolean configuredAsList,
            List<String> configuredEntries
    ) {
        if (!configured) {
            return PluginConfig.DEFAULT_COMMAND_WHITELIST;
        }
        if (!configuredAsList) {
            return List.of();
        }

        List<String> cleaned = new ArrayList<>();
        for (String entry : configuredEntries) {
            String root = cleanCommandRoot(entry);
            if (!root.isEmpty() && !cleaned.contains(root)) {
                cleaned.add(root);
            }
        }
        return List.copyOf(cleaned);
    }

    static List<String> mergeBannedWords(List<String> existing, List<String> bundled) {
        List<String> merged = new ArrayList<>();
        for (String value : existing) {
            String cleaned = cleanValue(value);
            if (!cleaned.isEmpty() && !containsIgnoreCase(merged, cleaned)) {
                merged.add(cleaned);
            }
        }
        for (String value : bundled) {
            String cleaned = cleanValue(value);
            if (!cleaned.isEmpty() && !containsIgnoreCase(merged, cleaned)) {
                merged.add(cleaned);
            }
        }
        return List.copyOf(merged);
    }

    static List<String> mergeBannedWordsForUpgrade(
            int currentVersion,
            List<String> existing,
            List<String> bundled
    ) {
        List<String> wordsToAdd = bundled;
        if (currentVersion <= 0) {
            wordsToAdd = bundled.stream()
                    .filter(word -> {
                        String cleaned = cleanValue(word);
                        return !containsIgnoreCase(LEGACY_BUNDLED_BANNED_WORDS, cleaned)
                                || containsIgnoreCase(existing, cleaned);
                    })
                    .toList();
        }
        return mergeBannedWords(existing, wordsToAdd);
    }

    private static String cleanCommandRoot(String value) {
        String root = cleanValue(value);
        if (root.startsWith("/")) {
            root = root.substring(1);
        }
        if (root.isEmpty() || root.chars().anyMatch(Character::isWhitespace)) {
            return "";
        }

        int namespaceSeparator = root.indexOf(':');
        if (namespaceSeparator >= 0) {
            if (namespaceSeparator + 1 == root.length() || root.indexOf(':', namespaceSeparator + 1) >= 0) {
                return "";
            }
            root = root.substring(namespaceSeparator + 1);
        }
        return root.toLowerCase(Locale.ROOT);
    }

    private static String cleanDomainSuffix(String value) {
        return DomainNormalizer.canonicalizeHost(value);
    }

    private static List<String> cleanDomainWhitelist(List<String> input) {
        List<String> cleaned = new ArrayList<>();
        for (String value : input) {
            String normalized = DomainNormalizer.canonicalizeHost(value);
            if (!normalized.isEmpty() && !cleaned.contains(normalized)) {
                cleaned.add(normalized);
            }
        }
        return List.copyOf(cleaned);
    }

    private static List<String> cleanList(List<String> input) {
        List<String> cleaned = new ArrayList<>();
        for (String value : input) {
            String trimmed = cleanValue(value);
            if (!trimmed.isEmpty() && !containsIgnoreCase(cleaned, trimmed)) {
                cleaned.add(trimmed);
            }
        }
        return List.copyOf(cleaned);
    }

    private static String cleanValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
