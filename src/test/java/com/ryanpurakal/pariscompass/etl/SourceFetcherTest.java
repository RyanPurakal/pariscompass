package com.ryanpurakal.pariscompass.etl;

import com.ryanpurakal.pariscompass.support.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The download step over real HTTP (local server) and through Spring resources. */
class SourceFetcherTest {
    private final SourceFetcher fetcher = new SourceFetcher(new DefaultResourceLoader());
    private FakeHttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = new FakeHttpServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void downloadsOverHttpAndHashesWhatItWrote() throws Exception {
        byte[] csv = "iso_code,year,co2\nUSA,2024,4904.12\n".getBytes(StandardCharsets.UTF_8);
        server.respond(200, csv);

        Path path;
        try (SourceFetcher.FetchedFile file = fetcher.fetch(server.url() + "/owid-co2-data.csv")) {
            path = file.path();
            assertThat(Files.readAllBytes(path)).isEqualTo(csv);
            assertThat(file.bytes()).isEqualTo(csv.length);
            assertThat(file.sha256()).isEqualTo(sha256(csv));
        }
        assertThat(path).as("temp file is deleted on close").doesNotExist();
        assertThat(server.requests().get(0).path()).isEqualTo("/owid-co2-data.csv");
    }

    @Test
    void nonOkStatusFailsTheSource() {
        server.respond(404, "not found", "text/plain");
        assertThatThrownBy(() -> fetcher.fetch(server.url() + "/missing.csv"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 404");
    }

    @Test
    void readsClasspathResourcesAndHashesThemTheSameWay() throws Exception {
        byte[] expected = new DefaultResourceLoader().getResource("classpath:etl/energy.csv").getContentAsByteArray();
        try (SourceFetcher.FetchedFile file = fetcher.fetch("classpath:etl/energy.csv")) {
            assertThat(file.sha256()).isEqualTo(sha256(expected));
        }
    }

    @Test
    void missingResourceFailsTheSource() {
        assertThatThrownBy(() -> fetcher.fetch("classpath:etl/nope.csv"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Source not found");
    }

    @Test
    void unreachableHostFailsTheSource() {
        String url = server.url() + "/x.csv";
        server.close();
        assertThatThrownBy(() -> fetcher.fetch(url)).isInstanceOf(IOException.class);
    }
}
