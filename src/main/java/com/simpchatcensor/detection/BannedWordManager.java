package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class BannedWordManager implements AutoCloseable {
    private final Logger logger;
    private final PinyinConverter pinyinConverter = new PinyinConverter();
    private final GeoIpService geoIpService = new GeoIpService();
    private final EmojiDetector emojiDetector = new EmojiDetector();

    private volatile PluginConfig config;
    private volatile Set<String> literalWords = Set.of();
    private volatile Map<String, String> normalizedWords = Map.of();
    private volatile List<Pattern> regexPatterns = List.of();
    private volatile Map<String, Set<String>> pinyinIndex = Map.of();
    private volatile List<String> levenshteinWords = List.of();
    private volatile IpDomainDetector ipDomainDetector;

    public BannedWordManager(Logger logger) {
        this.logger = logger;
    }

    public synchronized void reload(PluginConfig newConfig) {
        this.config = newConfig;
        this.literalWords = compileLiteralWords(newConfig.bannedWords());
        this.normalizedWords = compileNormalizedWords(newConfig.bannedWords());
        this.regexPatterns = compileRegexPatterns(newConfig);
        this.pinyinIndex = compilePinyinIndex(newConfig);
        this.levenshteinWords = compileLevenshteinWords(newConfig);
        this.geoIpService.reload(newConfig, logger);
        this.ipDomainDetector = new IpDomainDetector(newConfig.ipDomain(), geoIpService);
    }

    public Optional<DetectionResult> scan(String message) {
        PluginConfig snapshot = config;
        if (snapshot == null || message == null || message.isBlank()) {
            return Optional.empty();
        }

        Optional<DetectionResult> basic = scanBasic(message, snapshot);
        if (basic.isPresent()) {
            return basic;
        }

        IpDomainDetector detector = ipDomainDetector;
        if (detector != null) {
            Optional<DetectionResult> ipDomain = detector.detectInMessage(message);
            if (ipDomain.isPresent()) {
                return ipDomain;
            }
        }

        Optional<DetectionResult> pinyin = scanPinyin(message, snapshot);
        if (pinyin.isPresent()) {
            return pinyin;
        }

        Optional<DetectionResult> levenshtein = scanLevenshtein(message, snapshot);
        if (levenshtein.isPresent()) {
            return levenshtein;
        }

        return emojiDetector.detect(message).map(match -> DetectionResult.of(
                DetectionType.EMOJI,
                match.detectedText(),
                match.rule(),
                DetectionAction.CANCEL,
                "emoji character"
        ));
    }

    public Optional<DetectionResult> scanRemoteAddress(InetAddress address) {
        IpDomainDetector detector = ipDomainDetector;
        return detector == null ? Optional.empty() : detector.detectRemoteAddress(address);
    }

    public String censor(String message, DetectionResult result) {
        String filtered = replaceCaseInsensitive(message, result.detectedText());
        if (!filtered.equals(message)) {
            return filtered;
        }

        filtered = replaceCaseInsensitive(message, result.rule());
        if (!filtered.equals(message)) {
            return filtered;
        }

        return "[内容已过滤]";
    }

    public List<String> bannedWords() {
        PluginConfig snapshot = config;
        return snapshot == null ? List.of() : snapshot.bannedWords();
    }

    @Override
    public void close() {
        geoIpService.close();
    }

    private Optional<DetectionResult> scanBasic(String message, PluginConfig snapshot) {
        DetectionAction action = snapshot.defaultAction();
        if (snapshot.useRegex()) {
            for (Pattern pattern : regexPatterns) {
                Matcher matcher = pattern.matcher(message);
                if (matcher.find()) {
                    return Optional.of(DetectionResult.of(
                            DetectionType.REGEX,
                            matcher.group(),
                            pattern.pattern(),
                            action,
                            "regex"
                    ));
                }
            }
            return Optional.empty();
        }

        String lower = TextPreprocessor.basicLower(message);
        for (String word : literalWords) {
            if (lower.contains(word)) {
                return Optional.of(DetectionResult.of(
                        DetectionType.LITERAL,
                        word,
                        word,
                        action,
                        "literal"
                ));
            }
        }

        if (snapshot.advancedPreprocess()) {
            String normalizedMessage = TextPreprocessor.advanced(message);
            for (Map.Entry<String, String> entry : normalizedWords.entrySet()) {
                if (normalizedMessage.contains(entry.getKey())) {
                    return Optional.of(DetectionResult.of(
                            DetectionType.LITERAL,
                            entry.getValue(),
                            entry.getValue(),
                            action,
                            "advanced preprocess"
                    ));
                }
            }
        }

        return Optional.empty();
    }

    private Optional<DetectionResult> scanPinyin(String message, PluginConfig snapshot) {
        if (!snapshot.pinyin().enabled() || pinyinIndex.isEmpty()) {
            return Optional.empty();
        }

        // Match each contiguous text token as a whole. Searching one flattened
        // pinyin string with contains() lets a longer phrase such as "干嘛的"
        // (ganmade) trigger the suffix rule "妈的" (made).
        for (String token : TextPreprocessor.tokens(message, false)) {
            if (!containsHan(token)) {
                Set<String> originals = pinyinIndex.get(token);
                if (originals != null && !originals.isEmpty()) {
                    return pinyinResult(token, token, originals.iterator().next(), snapshot);
                }
                continue;
            }

            Set<String> tokenPinyin = pinyinConverter.combinations(token, snapshot.pinyin().maxCombinations());
            for (String variant : tokenPinyin) {
                Set<String> originals = pinyinIndex.get(variant);
                if (originals == null || originals.isEmpty()) {
                    continue;
                }

                String original = originals.iterator().next();
                return pinyinResult(token, variant, original, snapshot);
            }
        }
        return Optional.empty();
    }

    private static Optional<DetectionResult> pinyinResult(
            String token,
            String variant,
            String original,
            PluginConfig snapshot
    ) {
        return Optional.of(DetectionResult.of(
                DetectionType.FUZZY_PINYIN,
                token,
                original,
                snapshot.defaultAction(),
                variant
        ));
    }

    private static boolean containsHan(String value) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private Optional<DetectionResult> scanLevenshtein(String message, PluginConfig snapshot) {
        if (!snapshot.levenshtein().enabled() || levenshteinWords.isEmpty()) {
            return Optional.empty();
        }

        int checks = 0;
        List<String> tokens = TextPreprocessor.tokens(message, snapshot.advancedPreprocess());
        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            for (String word : levenshteinWords) {
                int threshold = Levenshtein.adaptiveThreshold(word, snapshot.levenshtein().globalThreshold());
                if (threshold <= 0 || token.equals(word) || Math.abs(token.length() - word.length()) > threshold) {
                    continue;
                }

                checks++;
                if (checks > snapshot.levenshtein().maxChecksPerMessage()) {
                    return Optional.empty();
                }

                if (Levenshtein.withinDistance(token, word, threshold)) {
                    return Optional.of(DetectionResult.of(
                            DetectionType.FUZZY_LEVENSHTEIN,
                            token,
                            word,
                            snapshot.defaultAction(),
                            "distance <= " + threshold
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private List<Pattern> compileRegexPatterns(PluginConfig snapshot) {
        if (!snapshot.useRegex()) {
            return List.of();
        }

        List<Pattern> patterns = new ArrayList<>();
        for (String word : snapshot.bannedWords()) {
            try {
                patterns.add(Pattern.compile(word, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (PatternSyntaxException exception) {
                logger.warning("Invalid banned regex ignored: " + word);
            }
        }
        return List.copyOf(patterns);
    }

    private Set<String> compileLiteralWords(List<String> words) {
        LinkedHashSet<String> compiled = new LinkedHashSet<>();
        for (String word : words) {
            String lower = TextPreprocessor.basicLower(word);
            if (!lower.isBlank()) {
                compiled.add(lower);
            }
        }
        return Set.copyOf(compiled);
    }

    private Map<String, String> compileNormalizedWords(List<String> words) {
        LinkedHashMap<String, String> compiled = new LinkedHashMap<>();
        for (String word : words) {
            String normalized = TextPreprocessor.advanced(word);
            if (!normalized.isBlank()) {
                compiled.put(normalized, word.toLowerCase(Locale.ROOT));
            }
        }
        return Map.copyOf(compiled);
    }

    private Map<String, Set<String>> compilePinyinIndex(PluginConfig snapshot) {
        if (!snapshot.pinyin().enabled()) {
            return Map.of();
        }

        LinkedHashMap<String, Set<String>> index = new LinkedHashMap<>();
        for (String word : snapshot.bannedWords()) {
            for (String pinyin : pinyinConverter.combinations(word, snapshot.pinyin().maxCombinations())) {
                index.computeIfAbsent(pinyin, ignored -> new LinkedHashSet<>()).add(word);
            }
        }
        return deepCopy(index);
    }

    private List<String> compileLevenshteinWords(PluginConfig snapshot) {
        if (!snapshot.levenshtein().enabled()) {
            return List.of();
        }

        List<String> words = new ArrayList<>();
        for (String word : snapshot.bannedWords()) {
            String comparable = snapshot.advancedPreprocess()
                    ? TextPreprocessor.advanced(word)
                    : TextPreprocessor.basicLower(word);
            if (comparable.length() >= 3) {
                words.add(comparable);
            }
        }
        return List.copyOf(words);
    }

    private static Map<String, Set<String>> deepCopy(Map<String, Set<String>> source) {
        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static String replaceCaseInsensitive(String message, String target) {
        if (target == null || target.isBlank()) {
            return message;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return pattern.matcher(message).replaceAll("***");
    }
}
