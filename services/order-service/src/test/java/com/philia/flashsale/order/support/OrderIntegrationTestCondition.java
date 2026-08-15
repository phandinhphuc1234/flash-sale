package com.philia.flashsale.order.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Opt-in JUnit condition for tests that require a live Schema Registry. */
public final class OrderIntegrationTestCondition implements ExecutionCondition {

    private static final String ENABLE_PROPERTY = "order.schema-registry.enabled";

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (Boolean.getBoolean(ENABLE_PROPERTY)) {
            return ConditionEvaluationResult.enabled("Order Schema Registry integration tests enabled");
        }
        return ConditionEvaluationResult.disabled(
                "Set -D" + ENABLE_PROPERTY + "=true to enable Schema Registry integration tests");
    }
}
