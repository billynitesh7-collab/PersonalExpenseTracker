package com.expense.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StaticFileHandler implements HttpHandler {
    private final Path frontendRoot;

    public StaticFileHandler(Path frontendDirectory) throws IOException {
        // Resolve the directory once, then require every requested file to stay under it.
        frontendRoot = frontendDirectory.toRealPath();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        // Normalize traversal components, then resolve symlinks before checking the root.
        Path candidate = frontendRoot.resolve(requestPath.substring(1)).normalize();
        if (!candidate.startsWith(frontendRoot) || !Files.exists(candidate)) {
            sendText(exchange, 404, "Not found");
            return;
        }

        Path realFile = candidate.toRealPath();
        if (!realFile.startsWith(frontendRoot) || !Files.isRegularFile(realFile)) {
            sendText(exchange, 404, "Not found");
            return;
        }

        String contentType = Files.probeContentType(realFile);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        if (realFile.toString().endsWith(".html")) {
            contentType = "text/html; charset=utf-8";
        }

        byte[] body = Files.readAllBytes(realFile);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void sendText(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
