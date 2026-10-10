package dev.questjs.ftbquestskubejs.attribute;

import java.util.Collection;
import java.util.OptionalDouble;
import java.util.function.Function;

/** Shared evaluation logic; platform adapters supply the current player values. */
public final class AttributeConditionEvaluator {
    private AttributeConditionEvaluator() {
    }

    /**
     * Every configured condition must resolve and match. An empty condition list
     * is deliberately false so an unconfigured task cannot complete by accident.
     */
    public static boolean allMatch(
            Collection<AttributeCondition> conditions,
            Function<String, OptionalDouble> valueLookup
    ) {
        if (conditions == null || conditions.isEmpty() || valueLookup == null) {
            return false;
        }

        for (AttributeCondition condition : conditions) {
            if (condition == null || !condition.isValid()) {
                return false;
            }

            OptionalDouble value = valueLookup.apply(condition.attributeId());
            if (value == null || value.isEmpty() || !condition.matches(value.getAsDouble())) {
                return false;
            }
        }

        return true;
    }
}
