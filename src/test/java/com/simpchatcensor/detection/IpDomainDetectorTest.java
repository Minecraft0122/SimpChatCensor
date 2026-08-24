package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpDomainDetectorTest {
    @Test
    void blocksEveryIpv4ByDefault() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of()
        );

        assertEquals(
                DetectionType.IP_ADDRESS,
                detector.detectInMessage("join 45.67.89.123").orElseThrow().type()
        );
    }

    @Test
    void detectsCommaSeparatedIpv4Bypass() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of()
        );

        DetectionResult result = detector.detectInMessage("try 1,1,1,1").orElseThrow();
        assertEquals(DetectionType.IP_ADDRESS, result.type());
        assertEquals("1,1,1,1", result.detectedText());
        assertEquals("1.1.1.1", result.rule());
    }

    @Test
    void detectsCommaSeparatedDomainBypass() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of()
        );

        DetectionResult result = detector.detectInMessage("join cnpvp,top").orElseThrow();
        assertEquals(DetectionType.DOMAIN, result.type());
        assertEquals("cnpvp,top", result.detectedText());
        assertEquals("cnpvp.top", result.rule());
        assertEquals(DetectionType.DOMAIN, detector.detectInMessage("join mc.example.com,test").orElseThrow().type());
        assertEquals(DetectionType.DOMAIN, detector.detectInMessage("join cnpvp，top").orElseThrow().type());
        assertTrue(detector.detectInMessage("hello, world").isEmpty());
    }

    @Test
    void ignoresDomainWithUnconfiguredSuffix() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of(),
                List.of("com")
        );

        assertTrue(detector.detectInMessage("ordinary example.world text").isEmpty());
        assertEquals(
                DetectionType.DOMAIN,
                detector.detectInMessage("ordinary example.com text").orElseThrow().type()
        );
    }

    @Test
    void supportsConfiguredMultiLevelSuffix() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of(),
                List.of("co.uk")
        );

        assertEquals(
                DetectionType.DOMAIN,
                detector.detectInMessage("join server.example.co.uk").orElseThrow().type()
        );
        assertTrue(detector.detectInMessage("join server.example.com").isEmpty());
    }

    @Test
    void explicitEmptySuffixListDisablesOnlyDomainDetection() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of(),
                List.of()
        );

        assertTrue(detector.detectInMessage("join server.example.com").isEmpty());
        assertEquals(
                DetectionType.IP_ADDRESS,
                detector.detectInMessage("join 45.67.89.123").orElseThrow().type()
        );
    }

    @Test
    void detectsObfuscatedIpv4() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of()
        );

        DetectionResult result = detector.detectInMessage("join 45，67，89，123").orElseThrow();
        assertEquals(DetectionType.IP_ADDRESS, result.type());
        assertEquals("45，67，89，123", result.detectedText());
    }

    @Test
    void ignoresPartialFiveOctetIpv4Candidates() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of()
        );

        assertTrue(detector.detectInMessage("bad shape 1.1.1.1.1").isEmpty());
    }

    @Test
    void respectsWhitelistForObfuscatedIpv4() {
        IpDomainDetector detector = detector(
                List.of("1.1.1.1"),
                List.of()
        );

        assertTrue(detector.detectInMessage("try 1,1,1,1").isEmpty());
    }

    @Test
    void respectsIpv4CidrWhitelist() {
        IpDomainDetector detector = detector(
                List.of("45.67.89.0/24"),
                List.of()
        );

        assertTrue(detector.detectInMessage("join 45.67.89.123").isEmpty());
        assertEquals(
                DetectionType.IP_ADDRESS,
                detector.detectInMessage("join 45.67.90.123").orElseThrow().type()
        );
    }

    @Test
    void blocksIpv6ByDefaultAndRespectsExactWhitelist() {
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        IpDomainDetector blockedDetector = detector(List.of(), List.of());
        IpDomainDetector allowedDetector = detector(List.of(ipv6), List.of());

        assertEquals(
                DetectionType.IP_ADDRESS,
                blockedDetector.detectInMessage("join " + ipv6).orElseThrow().type()
        );
        assertTrue(allowedDetector.detectInMessage("join " + ipv6).isEmpty());
    }

    @Test
    void allowsWhitelistedDomain() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of("minecraft.net")
        );

        assertTrue(detector.detectInMessage("open auth.minecraft.net please").isEmpty());
        assertEquals(
                DetectionType.DOMAIN,
                detector.detectInMessage("join mc.example.com:25565").orElseThrow().type()
        );
    }

    @Test
    void supportsModernTopLevelDomainsAndInternationalizedDomains() {
        IpDomainDetector detector = detector(List.of(), List.of());

        for (String suffix : List.of("zip", "uk", "hk")) {
            DetectionResult result = detector.detectInMessage("join server.example." + suffix).orElseThrow();
            assertEquals(DetectionType.DOMAIN, result.type());
            assertEquals("server.example." + suffix, result.rule());
        }

        DetectionResult idn = detector.detectInMessage("访问 例子.公司").orElseThrow();
        assertEquals(DetectionType.DOMAIN, idn.type());
        assertEquals("例子.公司", idn.detectedText());
        assertEquals("xn--fsqu00a.xn--55qx5d", idn.rule());

        DetectionResult punycode = detector.detectInMessage("访问 server.example.xn--p1ai").orElseThrow();
        assertEquals("server.example.xn--p1ai", punycode.rule());
    }

    @Test
    void supportsObfuscatedAuthoritySeparatorsWithoutChangingPath() {
        IpDomainDetector detector = detector(List.of(), List.of());

        for (String message : List.of("example . com", "example[.]com", "example [.] com")) {
            DetectionResult result = detector.detectInMessage(message).orElseThrow();
            assertEquals(DetectionType.DOMAIN, result.type());
            assertEquals("example.com", result.rule());
        }

        DetectionResult path = detector.detectInMessage("https://example.com/path[.]x").orElseThrow();
        assertEquals("example.com/path[.]x", path.detectedText());
        assertEquals("example.com", path.rule());
        assertTrue(detector.detectInMessage("example[x]com").isEmpty());
    }

    @Test
    void treatsPunctuationAroundAHostAsAnObfuscationBoundaryButRejectsInvalidLabels() {
        IpDomainDetector detector = detector(List.of(), List.of());

        for (String message : List.of("_example.com", "example.com_", "-example.com", "example.com-")) {
            assertEquals(DetectionType.DOMAIN, detector.detectInMessage(message).orElseThrow().type());
        }
        assertEquals(
                DetectionType.DOMAIN,
                detector.detectInMessage("foo-example.com").orElseThrow().type()
        );
        assertTrue(detector.detectInMessage("foo_bar.example.com").isEmpty());
        assertTrue(detector.detectInMessage("foo..com").isEmpty());
        assertTrue(detector.detectInMessage("example.invalid").isEmpty());
    }

    @Test
    void canonicalizesInternationalizedWhitelistEntries() {
        IpDomainDetector unicodeWhitelist = detector(List.of(), List.of("例子.公司"));
        IpDomainDetector asciiWhitelist = detector(List.of(), List.of("xn--fsqu00a.xn--55qx5d"));

        assertTrue(unicodeWhitelist.detectInMessage("join 例子.公司").isEmpty());
        assertTrue(asciiWhitelist.detectInMessage("join 例子.公司").isEmpty());
        assertEquals(
                DetectionType.DOMAIN,
                unicodeWhitelist.detectInMessage("join other.公司").orElseThrow().type()
        );
    }

    private static IpDomainDetector detector(List<String> ipWhitelist, List<String> domainWhitelist) {
        return detector(ipWhitelist, domainWhitelist, PluginConfig.DEFAULT_DOMAIN_SUFFIXES);
    }

    private static IpDomainDetector detector(
            List<String> ipWhitelist,
            List<String> domainWhitelist,
            List<String> domainSuffixes
    ) {
        return new IpDomainDetector(
                new PluginConfig.IpDomainConfig(
                        true,
                        true,
                        true,
                        true,
                        false,
                        "GeoLite2-Country.mmdb",
                        "https://github.com/P3TERX/GeoLite.mmdb/releases/latest/download/GeoLite2-Country.mmdb",
                        List.of(),
                        List.of(),
                        ipWhitelist,
                        domainWhitelist,
                        domainSuffixes,
                        DetectionAction.CANCEL
                ),
                new GeoIpService()
        );
    }
}
