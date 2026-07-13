package com.jettra.store.engine.server;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import com.jettra.store.engine.auth.AuthManager;
import com.jettra.store.engine.models.DocumentEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles REST requests for Document Engine operations.
 * Endpoints:
 * - POST /api/document/{collection}/{id}
 * - GET /api/document/{collection}/{id}
 * - DELETE /api/document/{collection}/{id}
 */
public class DocumentRestController implements HttpHandler {

    private final DocumentEngine engine;
    private final AuthManager authManager;
    private final JettraJson gson;

    public DocumentRestController(DocumentEngine engine, AuthManager authManager) {
        this.engine = engine;
        this.authManager = authManager;
        this.gson = new JettraJson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        
        // Authenticate request
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendResponse(exchange, 401, "{\"error\":\"Unauthorized\"}");
            return;
        }
        String token = authHeader.substring(7);
        if (!authManager.validateToken(token)) {
            sendResponse(exchange, 401, "{\"error\":\"Invalid Token\"}");
            return;
        }
        
        // Expected path: /api/document/{collection}/{id}
        String[] parts = path.split("/");
        if (parts.length < 5) {
            sendResponse(exchange, 400, "{\"error\":\"Invalid path format\"}");
            return;
        }
        
        String collection = parts[3];
        String id = parts[4];

        try {
            switch (method) {
                case "POST":
                    handlePost(exchange, collection, id);
                    break;
                case "GET":
                    handleGet(exchange, collection, id);
                    break;
                case "DELETE":
                    handleDelete(exchange, collection, id);
                    break;
                default:
                    sendResponse(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
        }
    }

    private void handlePost(HttpExchange exchange, String collection, String id) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String jsonBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject doc = gson.fromJson(jsonBody, JsonObject.class);
            engine.insert(collection, id, doc);
            sendResponse(exchange, 201, "{\"status\":\"inserted\"}");
        }
    }

    private void handleGet(HttpExchange exchange, String collection, String id) throws IOException {
        JsonObject doc = engine.get(collection, id);
        if (doc != null) {
            sendResponse(exchange, 200, doc.toString());
        } else {
            sendResponse(exchange, 404, "{\"error\":\"Document not found\"}");
        }
    }

    private void handleDelete(HttpExchange exchange, String collection, String id) throws IOException {
        engine.delete(collection, id);
        sendResponse(exchange, 200, "{\"status\":\"deleted\"}");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
