package com.jettra.store.core;

import java.io.File;
import java.util.Properties;

public class JettraStoreConfig {

    private String dataDirectory;
    private int cacheSizeMB;
    private boolean enableCompactHeaders;
    private String clusterNodeId;

    // Backup & Restore
    private boolean backupEnabled;
    private int backupIntervalMinutes;
    private boolean autoRestore;

    public JettraStoreConfig() {
        this.dataDirectory = System.getProperty("user.dir") + File.separator + "store_data";
        this.cacheSizeMB = 512;
        this.enableCompactHeaders = true;
        this.clusterNodeId = "node-1";
        
        // Defaults for backup
        this.backupEnabled = false;
        this.backupIntervalMinutes = 1440; // 1 día por defecto
        this.autoRestore = false;
    }

    public static JettraStoreConfig load(Properties props) {
        JettraStoreConfig config = new JettraStoreConfig();
        if (props.containsKey("store.data.dir")) {
            config.dataDirectory = props.getProperty("store.data.dir");
        }
        if (props.containsKey("store.cache.mb")) {
            config.cacheSizeMB = Integer.parseInt(props.getProperty("store.cache.mb"));
        }
        if (props.containsKey("store.compact.headers")) {
            config.enableCompactHeaders = Boolean.parseBoolean(props.getProperty("store.compact.headers"));
        }
        if (props.containsKey("store.cluster.nodeId")) {
            config.clusterNodeId = props.getProperty("store.cluster.nodeId");
        }
        
        if (props.containsKey("store.backup.enabled")) {
            config.backupEnabled = Boolean.parseBoolean(props.getProperty("store.backup.enabled"));
        }
        if (props.containsKey("store.backup.interval.minutes")) {
            config.backupIntervalMinutes = Integer.parseInt(props.getProperty("store.backup.interval.minutes"));
        }
        if (props.containsKey("store.restore.auto")) {
            config.autoRestore = Boolean.parseBoolean(props.getProperty("store.restore.auto"));
        }
        
        return config;
    }

    public String getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }
    public int getCacheSizeMB() { return cacheSizeMB; }
    public void setCacheSizeMB(int cacheSizeMB) { this.cacheSizeMB = cacheSizeMB; }
    public boolean isEnableCompactHeaders() { return enableCompactHeaders; }
    public void setEnableCompactHeaders(boolean enableCompactHeaders) { this.enableCompactHeaders = enableCompactHeaders; }
    public String getClusterNodeId() { return clusterNodeId; }
    public void setClusterNodeId(String clusterNodeId) { this.clusterNodeId = clusterNodeId; }

    public boolean isBackupEnabled() { return backupEnabled; }
    public void setBackupEnabled(boolean backupEnabled) { this.backupEnabled = backupEnabled; }
    public int getBackupIntervalMinutes() { return backupIntervalMinutes; }
    public void setBackupIntervalMinutes(int backupIntervalMinutes) { this.backupIntervalMinutes = backupIntervalMinutes; }
    public boolean isAutoRestore() { return autoRestore; }
    public void setAutoRestore(boolean autoRestore) { this.autoRestore = autoRestore; }
}
