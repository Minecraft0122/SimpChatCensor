package com.simpchatcensor.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class ConfigManager {
    private final JavaPlugin plugin;
    private PluginConfig config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration source = plugin.getConfig();

        DetectionAction defaultAction = DetectionAction.fromString(source.getString("action"), DetectionAction.CANCEL);
        DetectionAction ipAction = DetectionAction.fromString(
                source.getString("ip-domain-detection.action"),
                defaultAction
        );

        this.config = new PluginConfig(
                defaultAction,
                source.getBoolean("use-regex", false),
                source.getBoolean("advanced-preprocess", true),
                source.getBoolean("filter-commands", true),
                source.getBoolean("notify-staff", true),
                source.getBoolean("log-to-file", true),
                source.getString("staff-message", "&c[SimpChatCensor] &f%player% &7触发 &e%type%&7: &f%match%"),
                source.getString("player-warning", "&c请不要发送违规内容。"),
                source.getString("kick-message", "&c你因发送违规内容被移出服务器。"),
                new PluginConfig.PinyinConfig(
                        source.getBoolean("fuzzy-pinyin.enabled", false),
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
                        source.getBoolean("ip-domain-detection.check-ipv6", false),
                        source.getBoolean("ip-domain-detection.check-domains", true),
                        source.getBoolean("ip-domain-detection.block-private-ips", true),
                        source.getBoolean("ip-domain-detection.geoip-enabled", false),
                        source.getString("ip-domain-detection.geoip-database", "GeoLite2-Country.mmdb"),
                        source.getString(
                                "ip-domain-detection.geoip-download-url",
                                "https://github.com/P3TERX/GeoLite.mmdb/releases/latest/download/GeoLite2-Country.mmdb"
                        ),
                        cleanList(source.getStringList("ip-domain-detection.blocked-countries")),
                        cleanList(source.getStringList("ip-domain-detection.allowed-countries")),
                        cleanList(source.getStringList("ip-domain-detection.cidr-blacklist")),
                        cleanList(source.getStringList("ip-domain-detection.ip-whitelist")),
                        cleanList(source.getStringList("ip-domain-detection.domain-whitelist")),
                        ipAction
                ),
                cleanList(source.getStringList("banned-words")),
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

        List<String> words = new ArrayList<>(plugin.getConfig().getStringList("banned-words"));
        if (containsIgnoreCase(words, cleaned)) {
            return false;
        }
        words.add(cleaned);
        plugin.getConfig().set("banned-words", words);
        plugin.saveConfig();
        reload();
        return true;
    }

    public boolean removeBannedWord(String word) {
        String cleaned = cleanValue(word);
        List<String> words = new ArrayList<>(plugin.getConfig().getStringList("banned-words"));
        boolean removed = words.removeIf(existing -> existing.equalsIgnoreCase(cleaned));
        if (!removed) {
            return false;
        }
        plugin.getConfig().set("banned-words", words);
        plugin.saveConfig();
        reload();
        return true;
    }

    public boolean addWhitelistEntry(String entry) {
        String cleaned = cleanValue(entry);
        if (cleaned.isEmpty()) {
            return false;
        }

        String path = cleaned.matches("^[0-9a-fA-F:.]+$") || cleaned.contains("/")
                ? "ip-domain-detection.ip-whitelist"
                : "ip-domain-detection.domain-whitelist";
        List<String> values = new ArrayList<>(plugin.getConfig().getStringList(path));
        if (containsIgnoreCase(values, cleaned)) {
            return false;
        }
        values.add(cleaned);
        plugin.getConfig().set(path, values);
        plugin.saveConfig();
        reload();
        return true;
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
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
