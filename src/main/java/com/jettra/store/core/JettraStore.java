package com.jettra.store.core;

import com.jettra.store.storage.ObjectStorage;
import com.jettra.store.validation.EntityValidator;
import io.jettra.rules.core.JettraComputeEngine;
import io.jettra.rules.core.JettraRulesEngine;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Main Database Interface for JettraStoreEngine.
 * Handles multimodelo operations and enforces validations and rules.
 */
public class JettraStore {

    private final ObjectStorage storage;

    public JettraStore() {
        this.storage = JettraStoreContext.getInstance().getStorage();
    }

    /**
     * Saves an entity using Jettra Validations and JettraRules Compute.
     */
    public <T extends Serializable> void save(String id, T entity) {
        // 1. Validation (e.g., @NotNull)
        EntityValidator.validate(entity);

        // 2. Rules Evaluation
        // Compute any fields annotated with @Compute
        JettraComputeEngine.compute(entity);
        
        // Evaluate any business rules annotated with @Rules
        List<io.jettra.rules.core.RuleResult> ruleResults = JettraRulesEngine.validate(entity);
        for (io.jettra.rules.core.RuleResult ruleResult : ruleResults) {
            if (!ruleResult.isValid()) {
                throw new RuntimeException("Business Rule Validation Failed: " + ruleResult.getMessage());
            }
        }

        // 3. Persist
        storage.save(id, entity);
    }

    public <T extends Serializable> void save(T entity) {
        save(UUID.randomUUID().toString(), entity);
    }

    public <T extends Serializable> Optional<T> findById(Class<T> clazz, String id) {
        return storage.findById(clazz, id);
    }

    public <T extends Serializable> List<T> findAll(Class<T> clazz) {
        return storage.findAll(clazz);
    }

    public <T extends Serializable> void delete(Class<T> clazz, String id) {
        storage.delete(clazz, id);
    }
    public <T extends Serializable> List<T> search(Class<T> clazz, java.util.function.Predicate<T> predicate) {
        return storage.search(clazz, predicate);
    }

    public <T extends Serializable> List<T> search(Class<T> clazz, java.util.function.Predicate<T> predicate, int offset, int limit) {
        return storage.search(clazz, predicate, offset, limit);
    }
}
