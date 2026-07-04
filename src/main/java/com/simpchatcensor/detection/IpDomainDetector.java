package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.PluginConfig;
import org.apache.commons.net.util.SubnetUtils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IpDomainDetector {
    private static final String IPV4_CORE = "(?:(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}"
            + "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";
    private static final Pattern IPV4_PATTERN = Pattern.compile("(?<![\\d.])(" + IPV4_CORE + ")(?![\\d.])");
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "(?i)(?<![0-9a-f:])((?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4})(?![0-9a-f:])"
    );
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9_-])((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                    + "[a-z]{2,63}(?::[0-9]{1,5})?(?:/\\S*)?)(?![a-z0-9_-])"
    );

    private final PluginConfig.IpDomainConfig config;
    private final GeoIpService geoIpService;
    private final List<SubnetUtils> cidrBlacklist;

    public IpDomainDetector(PluginConfig.IpDomainConfig config, GeoIpService geoIpService) {
        this.config = config;
        this.geoIpService = geoIpService;
        this.cidrBlacklist = compileCidrs(config.cidrBlacklist());
    }

    public Optional<DetectionResult> detectInMessage(String message) {
        if (!config.enabled() || message == null || message.isBlank()) {
            return Optional.empty();
        }

        DetectionAction action = config.action();
        if (config.checkIpv4() && mightContainIpv4(message)) {
            Matcher matcher = IPV4_PATTERN.matcher(message);
            while (matcher.find()) {
                String ip = matcher.group(1);
                if (isWhitelistedIp(ip)) {
                    continue;
                }
                Optional<DetectionResult> cidrResult = detectCidr(ip, action);
                if (cidrResult.isPresent()) {
                    return cidrResult;
                }
                if (config.blockPrivateIps() && isPrivateIpv4(ip)) {
                    return Optional.of(DetectionResult.of(
                            DetectionType.IP_ADDRESS,
                            ip,
                            "private-ip",
                            action,
                            "private IPv4 address"
                    ));
                }
                return Optional.of(DetectionResult.of(
                        DetectionType.IP_ADDRESS,
                        ip,
                        "ipv4",
                        action,
                        "IPv4 address"
                ));
            }
        }

        if (config.checkIpv6() && message.indexOf(':') >= 0) {
            Matcher matcher = IPV6_PATTERN.matcher(message);
            while (matcher.find()) {
                String ip = matcher.group(1);
                if (isWhitelistedIp(ip)) {
                    continue;
                }
                return Optional.of(DetectionResult.of(
                        DetectionType.IP_ADDRESS,
                        ip,
                        "ipv6",
                        action,
                        "IPv6 address"
                ));
            }
        }

        if (config.checkDomains() && message.indexOf('.') >= 0) {
            Matcher matcher = DOMAIN_PATTERN.matcher(message);
            while (matcher.find()) {
                String domain = trimTrailingPunctuation(matcher.group(1));
                String host = hostOnly(domain);
                if (isWhitelistedDomain(host)) {
                    continue;
                }
                return Optional.of(DetectionResult.of(
                        DetectionType.DOMAIN,
                        domain,
                        host,
                        action,
                        "domain"
                ));
            }
        }

        return Optional.empty();
    }

    public Optional<DetectionResult> detectRemoteAddress(InetAddress address) {
        if (!config.enabled() || address == null) {
            return Optional.empty();
        }

        String ip = address.getHostAddress();
        if (isWhitelistedIp(ip)) {
            return Optional.empty();
        }

        Optional<DetectionResult> cidrResult = detectCidr(ip, config.action());
        if (cidrResult.isPresent()) {
            return cidrResult;
        }

        if (config.geoIpEnabled()) {
            Optional<GeoIpService.CountryMatch> country = geoIpService.lookup(address);
            if (country.isPresent()) {
                GeoIpService.CountryMatch match = country.get();
                if (!config.allowedCountries().isEmpty() && !match.matchesAny(config.allowedCountries())) {
                    return Optional.of(DetectionResult.of(
                            DetectionType.GEOIP_COUNTRY,
                            ip,
                            match.displayName(),
                            config.action(),
                            "country is not in allowed list"
                    ));
                }
                if (match.matchesAny(config.blockedCountries())) {
                    return Optional.of(DetectionResult.of(
                            DetectionType.GEOIP_COUNTRY,
                            ip,
                            match.displayName(),
                            config.action(),
                            "blocked country"
                    ));
                }
            }
        }

        return Optional.empty();
    }

    private Optional<DetectionResult> detectCidr(String ip, DetectionAction action) {
        if (ip.indexOf(':') >= 0) {
            return Optional.empty();
        }

        for (SubnetUtils subnet : cidrBlacklist) {
            if (subnet.getInfo().isInRange(ip)) {
                return Optional.of(DetectionResult.of(
                        DetectionType.CIDR,
                        ip,
                        subnet.getInfo().getCidrSignature(),
                        action,
                        "CIDR blacklist"
                ));
            }
        }
        return Optional.empty();
    }

    private boolean isWhitelistedIp(String ip) {
        for (String allowed : config.ipWhitelist()) {
            if (allowed.equalsIgnoreCase(ip)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWhitelistedDomain(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String allowed : config.domainWhitelist()) {
            String normalizedAllowed = allowed.toLowerCase(Locale.ROOT);
            if (normalizedHost.equals(normalizedAllowed) || normalizedHost.endsWith("." + normalizedAllowed)) {
                return true;
            }
        }
        return false;
    }

    private static List<SubnetUtils> compileCidrs(List<String> cidrs) {
        List<SubnetUtils> compiled = new ArrayList<>();
        for (String cidr : cidrs) {
            try {
                SubnetUtils subnet = new SubnetUtils(cidr);
                subnet.setInclusiveHostCount(true);
                compiled.add(subnet);
            } catch (IllegalArgumentException ignored) {
                // Invalid entries are ignored so a typo does not prevent startup.
            }
        }
        return List.copyOf(compiled);
    }

    private static boolean mightContainIpv4(String message) {
        boolean hasDigit = false;
        boolean hasDot = false;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            hasDigit |= Character.isDigit(c);
            hasDot |= c == '.';
            if (hasDigit && hasDot) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrivateIpv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        return first == 10
                || first == 127
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 169 && second == 254)
                || (first == 100 && second >= 64 && second <= 127)
                || first == 0;
    }

    private static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && ".,;:!?)]}\"'".indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }

    private static String hostOnly(String domain) {
        String host = domain;
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        int colon = host.indexOf(':');
        if (colon >= 0) {
            host = host.substring(0, colon);
        }
        return host.toLowerCase(Locale.ROOT);
    }
}
