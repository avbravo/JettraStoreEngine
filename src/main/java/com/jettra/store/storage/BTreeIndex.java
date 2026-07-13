package com.jettra.store.storage;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A simple robust index using ConcurrentSkipListMap (which acts like a B-Tree structure in memory).
 * Allows for range queries and fast exact lookups.
 */
public class BTreeIndex {
    
    // Key: Indexed value, Value: List of document IDs containing that value
    private final ConcurrentSkipListMap<String, java.util.concurrent.CopyOnWriteArrayList<String>> index;
    private final String fieldName;

    public BTreeIndex(String fieldName) {
        this.fieldName = fieldName;
        this.index = new ConcurrentSkipListMap<>();
    }

    public void insert(String value, String documentId) {
        if (value == null) return;
        index.computeIfAbsent(value, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(documentId);
    }

    public void remove(String value, String documentId) {
        if (value == null) return;
        List<String> docs = index.get(value);
        if (docs != null) {
            docs.remove(documentId);
            if (docs.isEmpty()) {
                index.remove(value);
            }
        }
    }

    public List<String> findExact(String value) {
        List<String> docs = index.get(value);
        return docs != null ? docs : List.of();
    }

    public List<String> findRange(String startValue, String endValue) {
        return index.subMap(startValue, true, endValue, true).values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public String getFieldName() {
        return fieldName;
    }
}
