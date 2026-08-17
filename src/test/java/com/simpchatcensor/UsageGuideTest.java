package com.simpchatcensor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageGuideTest {
    @Test
    void bundledGuideDocumentsCommandsAndRegexMode() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("USAGE.md")) {
            assertNotNull(stream, "USAGE.md must be packaged as a plugin resource");
            String guide = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(guide.contains("/bwf geoip update"));
            assertTrue(guide.contains("simpchatcensor.bypass"));
            assertTrue(guide.contains("command-whitelist"));
            assertTrue(guide.contains("use-regex: true"));
            assertTrue(guide.contains("Matcher.find()"));
            assertTrue(guide.contains("CASE_INSENSITIVE | UNICODE_CASE"));
            assertTrue(guide.contains("返回首个结果"));
            assertTrue(guide.contains("只有需放行合法域名时才加入 domain-whitelist"));
        }
    }
}
