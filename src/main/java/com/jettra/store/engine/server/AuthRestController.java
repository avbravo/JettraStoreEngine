package com.jettra.store.engine.server;

import io.jettra.json.JettraJson;
import io.jettra.json.JsonObject;
import com.jettra.store.engine.auth.AuthManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles REST requests for Authentication.
 * Endpoint: POST /api/auth/login
 */
public class AuthRestController implements HttpHandler {

    private final AuthManager authManager;
    private final JettraJson gson;

    public AuthRestController(AuthManager authManager) {
        this.authManager = authManager;
        this.gson = new JettraJson();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        
        if (!"POST".equals(method)) {
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }

        try (InputStream is = exchange.getRequestBody()) {
            String jsonBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject req = gson.fromJson(jsonBody, JsonObject.class);
            String username = req.has("username") ? (String) req.get("username") : null;
            String password = req.has("password") ? (String) req.get("password") : null;
            
            if (username == null || password == null) {
                sendResponse(exchange, 400, "{\"error\":\"Missing username or password\"}");
                return;
            }
            
            String token = authManager.login(username, password);
            sendResponse(exchange, 200, "{\"token\":\"" + token + "\"}");
        } catch (Exception e) {
            sendResponse(exchange, 401, "{\"error\":\"Invalid credentials\"}");
        }
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
