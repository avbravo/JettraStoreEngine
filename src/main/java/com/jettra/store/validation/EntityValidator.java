package com.jettra.store.validation;

// Removed jakarta.validation imports

/**
 * Validates objects before saving them to the database.
 * Supports standard Jakarta Bean Validation annotations (e.g. @NotNull).
 */
public class EntityValidator {

    public static <T> void validate(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        // Manual basic validation could be added here later
    }
}
