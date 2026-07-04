package com.simpchatcensor.detection;

import com.simpchatcensor.config.PluginConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public final class GeoIpDatabaseUpdater {
    private static final long MIN_DATABASE_BYTES = 1024L * 1024L;

    private GeoIpDatabaseUpdater() {
    }

    public static UpdateResult download(PluginConfig config) throws IOException, InterruptedException {
        Path dataFolder = config.dataFolder().toAbsolutePath().normalize();
        Path database = dataFolder.resolve(config.ipDomain().geoIpDatabase()).toAbsolutePath().normalize();
        if (!database.startsWith(dataFolder)) {
            throw new IOException("GeoIP database path must stay inside the plugin data folder.");
        }

        URI downloadUri = URI.create(config.ipDomain().geoIpDownloadUrl());
        Files.createDirectories(database.getParent());
        Path temporaryFile = Files.createTempFile(database.getParent(), "geoip-", ".mmdb.tmp");

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(downloadUri)
                    .timeout(Duration.ofMinutes(2))
                    .header("User-Agent", "SimpChatCensor GeoIP Updater")
                    .GET()
                    .build();

            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporaryFile));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("GeoIP download failed with HTTP " + response.statusCode());
            }

            long size = Files.size(temporaryFile);
            if (size < MIN_DATABASE_BYTES) {
                throw new IOException("Downloaded GeoIP database is unexpectedly small: " + size + " bytes");
            }

            moveAtomicallyWhenPossible(temporaryFile, database);
            return new UpdateResult(database, size, downloadUri);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void moveAtomicallyWhenPossible(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record UpdateResult(Path database, long bytes, URI source) {
    }
}
