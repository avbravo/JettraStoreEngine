package com.jettra.store.storage;

import java.io.Serializable;

/**
 * Ensures optimal metadata storage before objects are written to disk.
 * Uses Java Compact Headers for minimal footprint in JVM.
 */
public record CompactRecordHeader(
    String id,
    long timestamp,
    int version,
    boolean deleted
) implements Serializable {
    
    public CompactRecordHeader(String id) {
        this(id, System.currentTimeMillis(), 1, false);
    }
}
