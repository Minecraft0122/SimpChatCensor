package com.simpchatcensor.detection;

public enum DetectionType {
    LITERAL("literal"),
    REGEX("regex"),
    IP_ADDRESS("ip-address"),
    DOMAIN("domain"),
    GEOIP_COUNTRY("geoip-country"),
    FUZZY_PINYIN("fuzzy-pinyin"),
    FUZZY_LEVENSHTEIN("fuzzy-levenshtein"),
    EMOJI("emoji"),
    SPAM("spam");

    private final String key;

    DetectionType(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
