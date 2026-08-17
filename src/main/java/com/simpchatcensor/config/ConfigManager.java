package com.simpchatcensor.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigManager {
    private static final String MESSAGE_BLACKLIST_FILE = "message-blacklist.yml";
    private static final String IP_WHITELIST_FILE = "ip-whitelist.yml";
    private static final String BANNED_WORDS_PATH = "banned-words";
    private static final String IP_WHITELIST_PATH = "ip-whitelist";

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
                        cleanList(source.getStringList("ip-domain-detection.domain-whitelist")),
                        ipAction
                ),
                cleanList(messageBlacklist.getStringList(BANNED_WORDS_PATH)),
                plugin.getDataFolder().toPath()
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
