package com.simpchatcensor.detection;

import com.simpchatcensor.config.DetectionAction;
import com.simpchatcensor.config.DomainNormalizer;
import com.simpchatcensor.config.PluginConfig;
import org.apache.commons.net.util.SubnetUtils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IpDomainDetector {
    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)";
    private static final String IPV4_SEPARATOR_CHARS = ".,，。．｡﹒､、";
    private static final String IPV4_SEPARATOR = "\\s*[" + IPV4_SEPARATOR_CHARS + "]\\s*";
    private static final String DOMAIN_SEPARATOR_CHARS = ".，。．｡﹒､、";
    private static final String DOMAIN_COMMA_CHARS = ",，､、";
    private static final String DOMAIN_SEPARATOR = "\\h{0,8}(?:[" + DOMAIN_SEPARATOR_CHARS
            + "]|\\[\\h*[.．]\\])\\h{0,8}";
    private static final String DOMAIN_LABEL = "[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?";
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "(" + IPV4_OCTET + "(?:" + IPV4_SEPARATOR + IPV4_OCTET + "){3})"
    );
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "(?i)(?<![0-9a-f:])((?:[0-9a-f]{1,4}:){7}[0-9a-f]{1,4})(?![0-9a-f:])"
    );
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])((?:" + DOMAIN_LABEL + DOMAIN_SEPARATOR + ")+"
                    + DOMAIN_LABEL + "(?::[0-9]{1,5})?(?:/\\S*)?"
                    + ")(?![\\p{L}\\p{N}])"
    );
    private static final Pattern COMMA_DOMAIN_PATTERN = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}])((?:" + DOMAIN_LABEL + "[" + DOMAIN_COMMA_CHARS + "])+"
                    + DOMAIN_LABEL + "(?::[0-9]{1,5})?(?:/\\S*)?"
                    + ")(?![\\p{L}\\p{N}])"
    );

    private final PluginConfig.IpDomainConfig config;
    private final GeoIpService geoIpService;
    private final List<SubnetUtils> cidrWhitelist;
    private final Set<String> domainSuffixes;

    public IpDomainDetector(PluginConfig.IpDomainConfig config, GeoIpService geoIpService) {
        this.config = config;
        this.geoIpService = geoIpService;
        this.cidrWhitelist = compileCidrs(config.ipWhitelist());
        this.domainSuffixes = compileDomainSuffixes(config.domainSuffixes());
    }

    public Optional<DetectionResult> detectInMessage(String message) {
        if (!config.enabled() || message == null || message.isBlank()) {
            return Optional.empty();
        }

        DetectionAction action = config.action();
        if (config.checkIpv4() && mightContainIpv4(message)) {
            Matcher matcher = IPV4_PATTERN.matcher(message);
            while (matcher.find()) {
                if (isEmbeddedIpv4Candidate(message, matcher.start(1), matcher.end(1))) {
                    continue;
                }

                String detectedText = matcher.group(1);
                String ip = normalizeIpv4Candidate(detectedText);
                if (isWhitelistedIp(ip)) {
                    continue;
                }
                return Optional.of(DetectionResult.of(
                        DetectionType.IP_ADDRESS,
                        detectedText,
                        ip,
                        action,
                        ip.equals(detectedText) ? "IPv4 address" : "obfuscated IPv4 address"
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

        if (config.checkDomains() && mightContainDomainSeparator(message)) {
            Optional<DetectionResult> normalDomain = detectDomains(message, DOMAIN_PATTERN, action);
            if (normalDomain.isPresent()) {
                return normalDomain;
            }
            return detectDomains(message, COMMA_DOMAIN_PATTERN, action);
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

    private boolean isWhitelistedIp(String ip) {
        for (String allowed : config.ipWhitelist()) {
            if (allowed.equalsIgnoreCase(ip)) {
                return true;
            }
        }
        if (ip.indexOf(':') < 0) {
            for (SubnetUtils subnet : cidrWhitelist) {
                if (subnet.getInfo().isInRange(ip)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isWhitelistedDomain(String host) {
        String normalizedHost = DomainNormalizer.canonicalizeHost(host);
        if (normalizedHost.isEmpty()) {
            return false;
        }
        for (String allowed : config.domainWhitelist()) {
            String normalizedAllowed = DomainNormalizer.canonicalizeHost(allowed);
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

    private static Set<String> compileDomainSuffixes(List<String> suffixes) {
        Set<String> compiled = new HashSet<>();
        if (suffixes == null) {
            return Set.of();
        }
        for (String suffix : suffixes) {
            String normalized = normalizeDomainSuffix(suffix);
            if (!normalized.isEmpty()) {
                compiled.add(normalized);
            }
        }
        return Set.copyOf(compiled);
    }

    private boolean hasAllowedDomainSuffix(String host) {
        for (String suffix : domainSuffixes) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    private Optional<DetectionResult> detectDomains(
            String message,
            Pattern pattern,
            DetectionAction action
    ) {
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String domain = trimTrailingPunctuation(matcher.group(1));
            if (hasEmbeddedWordPunctuation(message, matcher.start(1), matcher.end(1))) {
                continue;
            }
            String normalizedCandidate = normalizeDomainCandidate(domain);
            String host = DomainNormalizer.canonicalizeHost(hostOnly(normalizedCandidate));
            if (host.isEmpty()) {
                continue;
            }
            if (!hasAllowedDomainSuffix(host) || isWhitelistedDomain(host)) {
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
        return Optional.empty();
    }

    private static boolean hasEmbeddedWordPunctuation(String message, int start, int end) {
        if (start > 0 && isWordPunctuation(message.charAt(start - 1))
                && start > 1 && Character.isLetterOrDigit(message.charAt(start - 2))) {
            return true;
        }
        return end < message.length() && isWordPunctuation(message.charAt(end))
                && end + 1 < message.length() && Character.isLetterOrDigit(message.charAt(end + 1));
    }

    private static boolean isWordPunctuation(char value) {
        return value == '_' || value == '-';
    }

    private static boolean mightContainIpv4(String message) {
        boolean hasDigit = false;
        boolean hasSeparator = false;
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            hasDigit |= Character.isDigit(c);
            hasSeparator |= isIpv4Separator(c);
            if (hasDigit && hasSeparator) {
                return true;
            }
        }
        return false;
    }

    private static boolean mightContainDomainSeparator(String message) {
        for (int i = 0; i < message.length(); i++) {
            if (DOMAIN_SEPARATOR_CHARS.indexOf(message.charAt(i)) >= 0 || message.charAt(i) == ',') {
                return true;
            }
        }
        return false;
    }

    private static String normalizeIpv4Candidate(String candidate) {
        return String.join(".", candidate.trim().split(IPV4_SEPARATOR));
    }

    private static boolean isEmbeddedIpv4Candidate(String message, int start, int end) {
        return touchesLetterOrDigit(message, start, end)
                || hasIpv4ContinuationBefore(message, start)
                || hasIpv4ContinuationAfter(message, end);
    }

    private static boolean touchesLetterOrDigit(String message, int start, int end) {
        return (start > 0 && Character.isLetterOrDigit(message.charAt(start - 1)))
                || (end < message.length() && Character.isLetterOrDigit(message.charAt(end)));
    }

    private static boolean hasIpv4ContinuationBefore(String message, int start) {
        int index = start - 1;
        while (index >= 0 && Character.isWhitespace(message.charAt(index))) {
            index--;
        }
        if (index < 0 || !isIpv4Separator(message.charAt(index))) {
            return false;
        }
        index--;
        while (index >= 0 && Character.isWhitespace(message.charAt(index))) {
            index--;
        }
        return index >= 0 && Character.isDigit(message.charAt(index));
    }

    private static boolean hasIpv4ContinuationAfter(String message, int end) {
        int index = end;
        while (index < message.length() && Character.isWhitespace(message.charAt(index))) {
            index++;
        }
        if (index >= message.length() || !isIpv4Separator(message.charAt(index))) {
            return false;
        }
        index++;
        while (index < message.length() && Character.isWhitespace(message.charAt(index))) {
            index++;
        }
        return index < message.length() && Character.isDigit(message.charAt(index));
    }

    private static boolean isIpv4Separator(char c) {
        return IPV4_SEPARATOR_CHARS.indexOf(c) >= 0;
    }

    private static String normalizeDomainCandidate(String candidate) {
        int authorityEnd = candidate.indexOf('/');
        if (authorityEnd < 0) {
            authorityEnd = candidate.length();
        }

        String authority = candidate.substring(0, authorityEnd);
        String path = candidate.substring(authorityEnd);
        return DomainNormalizer.normalizeSeparators(authority) + path;
    }

    private static String normalizeDomainSuffix(String suffix) {
        return DomainNormalizer.canonicalizeHost(suffix);
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
