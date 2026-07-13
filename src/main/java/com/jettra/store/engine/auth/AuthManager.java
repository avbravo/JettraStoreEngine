package com.jettra.store.engine.auth;

import com.jettra.store.engine.core.JettraStorageEngine;
import com.jettra.store.engine.models.DocumentEngine;
import io.jettra.json.JsonObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages JettraStoreEngine authentication, users, and tokens.
 */
public class AuthManager {
    
    public static final String SUPER_USER = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    
    private final JettraStorageEngine engine;
    private final Map<String, String> activeTokens;
    private static final String GLOBAL_SECURITY_DB = "JettraSecurityDB";

    public AuthManager(JettraStorageEngine engine) {
        this.engine = engine;
        this.activeTokens = new ConcurrentHashMap<>();
        
        initializeSecurityDB();
    }

    private void initializeSecurityDB() {
        DocumentEngine docEngine = (DocumentEngine) engine.getEngine("DOCUMENT");
        // Ensure engine is ready. In JettraStorageEngine, engines are registered before start() but DocumentEngine may be null if called too early.
        // Wait, AuthManager is initialized in Orchestrator AFTER storageEngine is created and started.
        if (docEngine != null) {
            JsonObject adminDoc = docEngine.get(GLOBAL_SECURITY_DB, SUPER_USER);
            if (adminDoc == null) {
                System.out.println("Initializing JettraSecurityDB with default admin user.");
                JsonObject defaultAdmin = new JsonObject();
                defaultAdmin.addProperty("username", SUPER_USER);
                defaultAdmin.addProperty("password", DEFAULT_PASSWORD);
                defaultAdmin.addProperty("requiresPasswordChange", true);
                docEngine.insert(GLOBAL_SECURITY_DB, SUPER_USER, defaultAdmin);
            }
        }
    }

    /**
     * Authenticates a user and returns a token if successful.
     */
    public String login(String username, String password) throws Exception {
        DocumentEngine docEngine = (DocumentEngine) engine.getEngine("DOCUMENT");
        JsonObject userDoc = docEngine.get(GLOBAL_SECURITY_DB, username);
        
        if (userDoc != null) {
            String storedPassword = (String) userDoc.get("password");
            if (storedPassword.equals(password)) {
                boolean requiresPasswordChange = false;
                if (userDoc.has("requiresPasswordChange")) {
                    Object rpc = userDoc.get("requiresPasswordChange");
                    if (rpc instanceof Boolean) {
                        requiresPasswordChange = (Boolean) rpc;
                    } else if (rpc instanceof String) {
                        requiresPasswordChange = Boolean.parseBoolean((String) rpc);
                    }
                }
                
                if (requiresPasswordChange) {
                    System.out.println("User " + username + " must change their password!");
                }
                String token = UUID.randomUUID().toString(); 
                activeTokens.put(token, username);
                return token;
            }
        }
        throw new Exception("Invalid credentials");
    }

    public String getUsernameFromToken(String token) {
        if (token == null) return null;
        return activeTokens.get(token);
    }

    /**
     * Changes a user's password.
     */
    public void changePassword(String username, String oldPassword, String newPassword) throws Exception {
        DocumentEngine docEngine = (DocumentEngine) engine.getEngine("DOCUMENT");
        JsonObject userDoc = docEngine.get(GLOBAL_SECURITY_DB, username);
        
        if (userDoc != null) {
            String storedPassword = (String) userDoc.get("password");
            if (storedPassword.equals(oldPassword)) {
                userDoc.addProperty("password", newPassword);
                userDoc.addProperty("requiresPasswordChange", false);
                docEngine.insert(GLOBAL_SECURITY_DB, username, userDoc);
            } else {
                throw new Exception("Invalid old password");
            }
        } else {
            throw new Exception("User not found");
        }
    }

    /**
     * Creates a new user in the global JettraSecurityDB.
     */
    public void createUser(String username, String password) throws Exception {
        DocumentEngine docEngine = (DocumentEngine) engine.getEngine("DOCUMENT");
        JsonObject existing = docEngine.get(GLOBAL_SECURITY_DB, username);
        if (existing != null) {
            throw new Exception("User already exists");
        }
        
        JsonObject user = new JsonObject();
        user.addProperty("username", username);
        user.addProperty("password", password);
        user.addProperty("requiresPasswordChange", true);
        docEngine.insert(GLOBAL_SECURITY_DB, username, user);
    }

    /**
     * Assigns a role to a user within a specific database.
     */
    public void assignUserToDatabase(String username, String databaseName, String role) throws Exception {
        DocumentEngine docEngine = (DocumentEngine) engine.getEngine("DOCUMENT");
        
        // Ensure user exists globally
        JsonObject userDoc = docEngine.get(GLOBAL_SECURITY_DB, username);
        if (userDoc == null) {
            throw new Exception("User does not exist in JettraSecurityDB");
        }
        
        // Store in DB specific credentials collection
        String credentialsCollection = databaseName + "_CredentialsDB";
        JsonObject roleDoc = new JsonObject();
        roleDoc.addProperty("userId", username);
        roleDoc.addProperty("role", role);
        
        docEngine.insert(credentialsCollection, username, roleDoc);
    }

    /**
     * Validates if a token is active.
     */
    public boolean validateToken(String token) {
        if (token == null) return false;
        return activeTokens.containsKey(token);
    }
}
