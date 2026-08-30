package com.simpchatcensor.detection;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PinyinConverter {
    private static final int MAX_OPTIONS_CACHE_ENTRIES = 2048;
    private static final int MAX_COMBINATION_CACHE_ENTRIES = 2048;
    private final ThreadLocal<HanyuPinyinOutputFormat> format = ThreadLocal.withInitial(
            PinyinConverter::createFormat
    );
    private final ConcurrentMap<Integer, List<String>> optionsCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<CombinationKey, Set<String>> combinationsCache = new ConcurrentHashMap<>();

    public Set<String> combinations(String input, int maxCombinations) {
        if (input == null || input.isBlank() || maxCombinations <= 0) {
            return Set.of();
        }

        CombinationKey key = new CombinationKey(input, maxCombinations);
        Set<String> cached = combinationsCache.get(key);
        if (cached != null) {
            return cached;
        }

        // Avoid the per-character String concatenation path for the common
        // ASCII/pinyin form. Besides being faster, this keeps long tokens
        // linear instead of repeatedly copying the growing prefix.
        if (isAsciiAlphaNumeric(input)) {
            Set<String> result = Set.of(input.toLowerCase(Locale.ROOT));
            if (input.length() <= 64) {
                cache(combinationsCache, key, result, MAX_COMBINATION_CACHE_ENTRIES);
            }
            return result;
        }

        List<String> combinations = null;
        StringBuilder deterministic = new StringBuilder(input.length());

        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);

            List<String> options = options(codePoint);
            if (options.isEmpty()) {
                continue;
            }

            if (combinations == null && options.size() == 1) {
                deterministic.append(options.get(0));
                continue;
            }

            if (combinations == null) {
                combinations = new ArrayList<>();
                combinations.add(deterministic.toString());
            }

            LinkedHashSet<String> next = new LinkedHashSet<>();
            for (String existing : combinations) {
                for (String option : options) {
                    next.add(existing + option);
                    if (next.size() >= maxCombinations) {
                        break;
                    }
                }
                if (next.size() >= maxCombinations) {
                    break;
                }
            }
            combinations = new ArrayList<>(next);
        }

        LinkedHashSet<String> output = new LinkedHashSet<>();
        if (combinations == null) {
            if (!deterministic.isEmpty()) {
                output.add(deterministic.toString());
            }
        } else {
            for (String value : combinations) {
                if (!value.isBlank()) {
                    output.add(value);
                }
            }
        }
        Set<String> result = Collections.unmodifiableSet(output);
        if (input.length() <= 64) {
            cache(combinationsCache, key, result, MAX_COMBINATION_CACHE_ENTRIES);
        }
        return result;
    }

    private List<String> options(int codePoint) {
        List<String> cached = optionsCache.get(codePoint);
        if (cached != null) {
            return cached;
        }

        List<String> result;
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
            try {
                String[] values = PinyinHelper.toHanyuPinyinStringArray((char) codePoint, format.get());
                if (values == null || values.length == 0) {
                    result = List.of();
                } else {
                    LinkedHashSet<String> unique = new LinkedHashSet<>();
                    for (String value : values) {
                        if (value != null && !value.isBlank()) {
                            unique.add(value.toLowerCase(Locale.ROOT));
                        }
                    }
                    result = List.copyOf(unique);
                }
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                result = List.of();
            }
        } else if (Character.isLetterOrDigit(codePoint)) {
            String value = new String(Character.toChars(codePoint)).toLowerCase(Locale.ROOT);
            result = List.of(value);
        } else {
            result = List.of();
        }

        cache(optionsCache, codePoint, result, MAX_OPTIONS_CACHE_ENTRIES);
        return result;
    }

    private static HanyuPinyinOutputFormat createFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }

    private static boolean isAsciiAlphaNumeric(String input) {
        for (int index = 0; index < input.length(); index++) {
            char value = input.charAt(index);
            if (!((value >= 'a' && value <= 'z')
                    || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9'))) {
                return false;
            }
        }
        return !input.isEmpty();
    }

    private static <K, V> void cache(ConcurrentMap<K, V> cache, K key, V value, int maximumEntries) {
        synchronized (cache) {
            if (cache.size() < maximumEntries) {
                cache.putIfAbsent(key, value);
            }
        }
    }

    private record CombinationKey(String input, int maxCombinations) {
    }
}
