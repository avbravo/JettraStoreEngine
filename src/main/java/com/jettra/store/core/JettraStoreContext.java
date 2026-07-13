package com.jettra.store.core;

import com.jettra.store.storage.ObjectStorage;
import java.util.Properties;

public class JettraStoreContext {
    
    private static JettraStoreContext instance;
    private final JettraStoreConfig config;
    private final ObjectStorage storage;

    private JettraStoreContext(JettraStoreConfig config) {
        this.config = config;
        this.storage = new ObjectStorage(config.getDataDirectory());
    }

    public static synchronized void initialize(Properties props) {
        if (instance == null) {
            JettraStoreConfig config = JettraStoreConfig.load(props);
            instance = new JettraStoreContext(config);
            System.out.println("[JettraStoreEngine] Initialized at " + config.getDataDirectory());
        }
    }

    public static JettraStoreContext getInstance() {
        if (instance == null) {
            initialize(new Properties());
        }
        return instance;
    }

    public JettraStoreConfig getConfig() {
        return config;
    }

    public ObjectStorage getStorage() {
        return storage;
    }
}
