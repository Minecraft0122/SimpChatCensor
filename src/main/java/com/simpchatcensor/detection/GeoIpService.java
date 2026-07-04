package com.simpchatcensor.detection;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import com.simpchatcensor.config.PluginConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class GeoIpService implements AutoCloseable {
    private DatabaseReader reader;

    public synchronized void reload(PluginConfig config, Logger logger) {
        close();

        PluginConfig.IpDomainConfig ipDomain = config.ipDomain();
        if (!ipDomain.geoIpEnabled()) {
            return;
        }

        Path database = config.dataFolder().resolve(ipDomain.geoIpDatabase()).normalize();
        if (!Files.isRegularFile(database)) {
            logger.warning("GeoIP is enabled, but database was not found: " + database);
            return;
        }

        try {
            this.reader = new DatabaseReader.Builder(database.toFile()).build();
            logger.info("Loaded GeoIP database: " + database.getFileName());
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Failed to load GeoIP database: " + database, exception);
        }
    }

    public Optional<CountryMatch> lookup(InetAddress address) {
        DatabaseReader snapshot = reader;
        if (snapshot == null || address == null) {
            return Optional.empty();
        }

        try {
            CountryResponse response = snapshot.country(address);
            Country country = response.getCountry();
            Set<String> names = new LinkedHashSet<>();
            if (country.getName() != null) {
                names.add(country.getName());
            }
            if (country.getIsoCode() != null) {
                names.add(country.getIsoCode());
            }
            names.addAll(country.getNames().values());
            return names.isEmpty() ? Optional.empty() : Optional.of(new CountryMatch(Set.copyOf(names)));
        } catch (AddressNotFoundException ignored) {
            return Optional.empty();
        } catch (IOException | GeoIp2Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized void close() {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (IOException ignored) {
            // Closing during reload/disable should not make the plugin fail.
        } finally {
            reader = null;
        }
    }

    public record CountryMatch(Set<String> names) {
        public boolean matchesAny(Iterable<String> configuredValues) {
            for (String configured : configuredValues) {
                for (String name : names) {
                    if (name.equalsIgnoreCase(configured)) {
                        return true;
                    }
                }
            }
            return false;
        }

        public String displayName() {
            return names.stream().findFirst().orElse("unknown");
        }
    }
}
