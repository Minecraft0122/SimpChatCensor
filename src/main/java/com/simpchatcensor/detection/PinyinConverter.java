package com.simpchatcensor.detection;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PinyinConverter {
    private final HanyuPinyinOutputFormat format;

    public PinyinConverter() {
        this.format = new HanyuPinyinOutputFormat();
        this.format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        this.format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        this.format.setVCharType(HanyuPinyinVCharType.WITH_V);
    }

    public Set<String> combinations(String input, int maxCombinations) {
        if (input == null || input.isBlank() || maxCombinations <= 0) {
            return Set.of();
        }

        List<String> combinations = new ArrayList<>();
        combinations.add("");

        for (int offset = 0; offset < input.length(); ) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);

            List<String> options = options(codePoint);
            if (options.isEmpty()) {
                continue;
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
        for (String value : combinations) {
            if (!value.isBlank()) {
                output.add(value);
            }
        }
        return output;
    }

    private List<String> options(int codePoint) {
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
            try {
                String[] values = PinyinHelper.toHanyuPinyinStringArray((char) codePoint, format);
                if (values == null || values.length == 0) {
                    return List.of();
                }
                LinkedHashSet<String> unique = new LinkedHashSet<>();
                for (String value : values) {
                    if (value != null && !value.isBlank()) {
                        unique.add(value.toLowerCase(Locale.ROOT));
                    }
                }
                return List.copyOf(unique);
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
                return List.of();
            }
        }

        if (Character.isLetterOrDigit(codePoint)) {
            return List.of(new String(Character.toChars(codePoint)).toLowerCase(Locale.ROOT));
        }
        return List.of();
    }
}
