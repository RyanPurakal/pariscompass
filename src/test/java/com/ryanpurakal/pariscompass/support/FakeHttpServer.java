package com.ryanpurakal.pariscompass.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal local HTTP server on the JDK's built-in com.sun.net.httpserver: no extra test dependency.
 * Every request is recorded; every response is the configured status and body.
 */
public final class FakeHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile byte[] body = new byte[0];
    private volatile String contentType = "application/json";

    public record Request(String method, String path, String query, String body, String apiKeyHeader) {
    }

    public FakeHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(), requestBody, exchange.getRequestHeaders().getFirst("x-goog-api-key")));
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    public FakeHttpServer respond(int status, String body, String contentType) {
        this.status = status;
        this.body = body.getBytes(StandardCharsets.UTF_8);
        this.contentType = contentType;
        return this;
    }

    public FakeHttpServer respond(int status, byte[] body) {
        this.status = status;
        this.body = body;
        return this;
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
