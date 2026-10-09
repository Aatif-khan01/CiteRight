package com.citeright.service;

import com.citeright.model.Publication;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import javafx.application.Platform;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Lightweight HTTP server on localhost:9876 for browser extension integration.
 * 
 * Endpoints:
 *   POST /import   — JSON body { "doi": "10.xxx" } or { "url": "..." } or { "arxiv": "..." }
 *   GET  /ping     — returns { "status": "ok", "app": "CiteRight" }
 *   GET  /stats    — returns library count
 * 
 * The browser extension sends DOIs/URLs to this server,
 * and CiteRight auto-imports the paper metadata.
 */
public class LocalImportServer {

    private static final int PORT = 9876;
    private HttpServer server;
    private final MetadataLookupService lookupService;
    private final LibraryService libraryService;
    private Consumer<Publication> onImportSuccess;
    private Consumer<String> onImportError;
    private boolean running = false;

    public LocalImportServer(MetadataLookupService lookupService, LibraryService libraryService) {
        this.lookupService = lookupService;
        this.libraryService = libraryService;
    }

    public void setOnImportSuccess(Consumer<Publication> handler) { this.onImportSuccess = handler; }
    public void setOnImportError(Consumer<String> handler) { this.onImportError = handler; }

    public void start() {
        if (running) return;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
            server.createContext("/ping", this::handlePing);
            server.createContext("/import", this::handleImport);
            server.createContext("/stats", this::handleStats);
            server.setExecutor(null);
            server.start();
            running = true;
            System.out.println("[LocalImportServer] Started on http://127.0.0.1:" + PORT);
        } catch (Exception e) {
            System.err.println("[LocalImportServer] Failed to start: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            System.out.println("[LocalImportServer] Stopped.");
        }
    }

    public boolean isRunning() { return running; }

    private void handlePing(HttpExchange exchange) throws IOException {
        String json = "{\"status\":\"ok\",\"app\":\"CiteRight\",\"version\":\"1.0\"}";
        sendResponse(exchange, 200, json);
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        int count = libraryService.getLibraryCount();
        String json = "{\"papers\":" + count + "}";
        sendResponse(exchange, 200, json);
    }

    private void handleImport(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"POST only\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            JsonObject req = JsonParser.parseString(body).getAsJsonObject();
            String identifier = null;

            if (req.has("doi")) identifier = req.get("doi").getAsString();
            else if (req.has("arxiv")) identifier = req.get("arxiv").getAsString();
            else if (req.has("pmid")) identifier = req.get("pmid").getAsString();
            else if (req.has("url")) {
                String url = req.get("url").getAsString();
                // Try to extract DOI from URL
                if (url.contains("doi.org/")) {
                    identifier = url.substring(url.indexOf("doi.org/") + 8);
                } else if (url.contains("arxiv.org/abs/")) {
                    identifier = url.substring(url.indexOf("abs/") + 4);
                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Cannot extract identifier from URL\"}");
                    return;
                }
            }

            if (identifier == null || identifier.isBlank()) {
                sendResponse(exchange, 400, "{\"error\":\"Provide doi, arxiv, pmid, or url\"}");
                return;
            }

            final String id = identifier;
            // Lookup in background
            new Thread(() -> {
                try {
                    Publication pub = lookupService.lookup(id);
                    if (pub != null) {
                        libraryService.saveToDefaultCollection(pub);
                        Platform.runLater(() -> {
                            if (onImportSuccess != null) onImportSuccess.accept(pub);
                        });
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        if (onImportError != null) onImportError.accept(e.getMessage());
                    });
                }
            }).start();

            sendResponse(exchange, 200, "{\"status\":\"importing\",\"identifier\":\"" + id + "\"}");

        } catch (Exception e) {
            sendResponse(exchange, 400, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
}
