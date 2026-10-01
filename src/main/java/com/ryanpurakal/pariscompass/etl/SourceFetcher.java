package com.ryanpurakal.pariscompass.etl;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Copies a source to a temp file while computing its SHA-256 in the same pass.
 * http(s) locations use java.net.http with explicit timeouts; anything else (file:, classpath:)
 * goes through Spring's ResourceLoader, which is how tests feed fixture files.
 */
@Component
public class SourceFetcher {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);

    private final ResourceLoader resourceLoader;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SourceFetcher(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public FetchedFile fetch(String location) throws IOException, InterruptedException {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(location))
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "paris-compass-etl")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("GET " + location + " returned HTTP " + response.statusCode());
            }
            return copyAndHash(response.body());
        }
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IOException("Source not found: " + location);
        }
        return copyAndHash(resource.getInputStream());
    }

    private static FetchedFile copyAndHash(InputStream body) throws IOException {
        Path tmp = Files.createTempFile("pariscompass-etl-", ".csv");
        try (DigestInputStream in = new DigestInputStream(body, sha256())) {
            long bytes = Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return new FetchedFile(tmp, HexFormat.of().formatHex(in.getMessageDigest().digest()), bytes);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    /** A downloaded source. Closing it deletes the temp file. */
    public record FetchedFile(Path path, String sha256, long bytes) implements AutoCloseable {
        @Override
        public void close() {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
