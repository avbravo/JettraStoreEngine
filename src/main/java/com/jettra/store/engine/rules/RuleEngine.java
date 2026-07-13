package com.jettra.store.engine.rules;

import java.util.ArrayList;
import java.util.List;

public class RuleEngine<T> {
    private final List<JettraRule<T>> rules = new ArrayList<>();

    public void addRule(JettraRule<T> rule) {
        rules.add(rule);
    }

    public void applyRules(T entity) {
        for (JettraRule<T> rule : rules) {
            if (rule.evaluate(entity)) {
                rule.execute(entity);
            }
        }
    }
}
