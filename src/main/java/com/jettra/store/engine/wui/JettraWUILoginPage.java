package com.jettra.store.engine.wui;

import com.jettra.store.engine.auth.AuthManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class JettraWUILoginPage implements HttpHandler {
    private final AuthManager authManager;

    public JettraWUILoginPage() {
        this.authManager = com.jettra.store.engine.server.JettraServerOrchestrator.CURRENT_AUTH_MANAGER;
    }

    public JettraWUILoginPage(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            sendLoginForm(exchange, null);
        } else if ("POST".equalsIgnoreCase(method)) {
            handleLoginPost(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void sendLoginForm(HttpExchange exchange, String errorMsg) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>JettraStoreEngine Login</title>");
        html.append("<style>");
        html.append("body { font-family: sans-serif; background-color: #f4f7f6; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }");
        html.append(".login-box { background: white; padding: 40px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); width: 300px; text-align: center; }");
        html.append("input { width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; }");
        html.append("button { width: 100%; padding: 10px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; margin-top: 10px; }");
        html.append("button:hover { background-color: #0056b3; }");
        html.append(".error { color: red; margin-bottom: 10px; }");
        html.append("</style></head><body>");
        html.append("<div class='login-box'>");
        html.append("<h2>JettraStoreEngine</h2>");
        if (errorMsg != null) {
            html.append("<div class='error'>").append(errorMsg).append("</div>");
        }
        html.append("<form method='POST' action='/wui/login'>");
        html.append("<input type='text' name='username' placeholder='Username' required />");
        html.append("<input type='password' name='password' placeholder='Password' required />");
        html.append("<button type='submit'>Login</button>");
        html.append("</form>");
        html.append("</div></body></html>");

        byte[] bytes = html.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleLoginPost(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String[] pairs = body.split("&");
            String username = "";
            String password = "";
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    if ("username".equals(kv[0])) username = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    if ("password".equals(kv[0])) password = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }

            try {
                String token = authManager.login(username, password);
                exchange.getResponseHeaders().add("Set-Cookie", "jettra_token=" + token + "; Path=/");
                exchange.getResponseHeaders().add("Location", "/wui");
                exchange.sendResponseHeaders(302, -1);
                exchange.getResponseBody().close();
            } catch (Exception e) {
                sendLoginForm(exchange, "Invalid credentials");
            }
        }
    }
}
