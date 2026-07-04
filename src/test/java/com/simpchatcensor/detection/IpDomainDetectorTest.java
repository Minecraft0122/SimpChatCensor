package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpDomainDetectorTest {
    @Test
    void detectsIpv4AndCidrBeforeGenericIp() {
        IpDomainDetector detector = detector(
                List.of("45.67.89.0/24"),
                List.of(),
                List.of()
        );

        assertEquals(
                DetectionType.CIDR,
                detector.detectInMessage("join 45.67.89.123").orElseThrow().type()
        );
    }

    @Test
    void detectsCommaSeparatedIpv4Bypass() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of(),
                List.of()
        );

        DetectionResult result = detector.detectInMessage("try 1,1,1,1").orElseThrow();
        assertEquals(DetectionType.IP_ADDRESS, result.type());
        assertEquals("1,1,1,1", result.detectedText());
        assertEquals("1.1.1.1", result.rule());
    }

    @Test
    void detectsObfuscatedIpv4AndStillAppliesCidr() {
        IpDomainDetector detector = detector(
                List.of("45.67.89.0/24"),
                List.of(),
                List.of()
        );

        DetectionResult result = detector.detectInMessage("join 45，67，89，123").orElseThrow();
        assertEquals(DetectionType.CIDR, result.type());
        assertEquals("45，67，89，123", result.detectedText());
    }

    @Test
    void ignoresPartialFiveOctetIpv4Candidates() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of(),
                List.of()
        );

        assertTrue(detector.detectInMessage("bad shape 1.1.1.1.1").isEmpty());
    }

    @Test
    void respectsWhitelistForObfuscatedIpv4() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of("1.1.1.1"),
                List.of()
        );

        assertTrue(detector.detectInMessage("try 1,1,1,1").isEmpty());
    }

    @Test
    void allowsWhitelistedDomain() {
        IpDomainDetector detector = detector(
                List.of(),
                List.of(),
                List.of("minecraft.net")
        );

        assertTrue(detector.detectInMessage("open auth.minecraft.net please").isEmpty());
        assertEquals(
                DetectionType.DOMAIN,
                detector.detectInMessage("join mc.example.com:25565").orElseThrow().type()
        );
    }

    private static IpDomainDetector detector(List<String> cidrs, List<String> ipWhitelist, List<String> domainWhitelist) {
        return new IpDomainDetector(
                new PluginConfig.IpDomainConfig(
                        true,
                        true,
                        false,
                        true,
                        true,
                        false,
                        "GeoLite2-Country.mmdb",
                        List.of(),
                        List.of(),
                        cidrs,
                        ipWhitelist,
                        domainWhitelist,
                        DetectionAction.CANCEL
                ),
                new GeoIpService()
        );
    }
}
