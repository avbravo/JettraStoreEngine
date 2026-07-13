package com.jettra.store.engine.core;

/**
 * Interface defining a multi-model engine family (e.g., Document, Vector, Graph).
 */
public interface EngineFamily {
    
    /**
     * Returns the name of the engine family (e.g., "DOCUMENT", "VECTOR").
     */
    String getName();
    
    /**
     * Initializes the engine.
     */
    void init();
    
    /**
     * Shuts down the engine.
     */
    void close();
}
