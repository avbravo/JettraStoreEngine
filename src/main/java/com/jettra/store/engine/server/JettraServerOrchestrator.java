package com.jettra.store.engine.server;

import com.jettra.store.engine.auth.AuthManager;
import com.jettra.store.engine.core.JettraStorageEngine;
import com.jettra.store.engine.models.DocumentEngine;
import java.io.InputStream;
import java.io.OutputStream;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import com.jettra.store.engine.wui.JettraWUIAdminPage;
import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrates the network interfaces for JettraStorageEngine.
 * Bootstraps JettraServer, mounts JettraRest and JettraGRPC endpoints,
 * and configures JettraJWT authentication.
 */
public class JettraServerOrchestrator {
    
    private final JettraStorageEngine engine;
    private final int restPort;
    private final int grpcPort;
    private final AuthManager authManager;
    private io.jettra.server.JettraServer jettraServer;

    public static JettraStorageEngine CURRENT_ENGINE;
    public static AuthManager CURRENT_AUTH_MANAGER;

    
    public JettraServerOrchestrator(JettraStorageEngine engine, int restPort, int grpcPort) {
        this.engine = engine;
        this.restPort = restPort;
        this.grpcPort = grpcPort;
        this.authManager = new AuthManager(engine);
        CURRENT_ENGINE = this.engine;
        CURRENT_AUTH_MANAGER = this.authManager;
    }
    
    public void start() {
        System.out.println("Starting JettraServerOrchestrator...");
        System.out.println("REST/GraphQL Port: " + restPort);
        System.out.println("gRPC Port: " + grpcPort);
        
        // Initialize JettraServer instance
        jettraServer = new io.jettra.server.JettraServer();
        jettraServer.setPort(restPort);
        
        // Configure JettraJWT / Auth for admin/admin bootstrap
        jettraServer.addHandler("/api/auth/login", new AuthRestController(authManager));
        
        // Mount JettraRest controllers mapped to JettraStorageEngine operations
        jettraServer.addHandler("/api/document/", new DocumentRestController((DocumentEngine) engine.getEngine("DOCUMENT"), authManager));
        
        // Universal Model endpoint
        jettraServer.addHandler("/api/model/", new ModelRestController(engine, authManager));
        
        // Serve WUI Portal
        jettraServer.addHandler("/wui", JettraWUIAdminPage.class);
        jettraServer.addHandler("/wui/login", com.jettra.store.engine.wui.JettraWUILoginPage.class);

        
        // Backup API
        jettraServer.addHandler("/api/backup", new BackupHandler(engine));
        
        // Start server
        jettraServer.start();
        
        System.out.println("\n========================================================");
        System.out.println("  JettraStoreEngine WUI disponible en: ");
        System.out.println("  http://localhost:" + restPort + "/wui");
        System.out.println("========================================================\n");
        
        // TODO: Mount JettraGRPC services
    }
    
    public void stop() {
        System.out.println("Stopping JettraServerOrchestrator...");
        if (jettraServer != null) {
            jettraServer.stop();
        }
    }

    private static class BackupHandler implements HttpHandler {
        private final JettraStorageEngine engine;
        public BackupHandler(JettraStorageEngine engine) {
            this.engine = engine;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                com.jettra.store.engine.core.BackupManager backupManager = new com.jettra.store.engine.core.BackupManager(engine.getStorageDir());
                backupManager.createBackup();
                String resp = "{\"status\":\"Backup initiated\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp.getBytes());
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}
