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
