package com.jettra.store.engine.rules;

public interface JettraRule<T> {
    boolean evaluate(T entity);
    void execute(T entity);
}
