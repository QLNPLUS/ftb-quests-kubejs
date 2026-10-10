package dev.questjs.ftbquestskubejs.attribute;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeConditionTest {
    @Test
    void supportsAllComparators() {
        assertTrue(condition(ComparisonOperator.GREATER_THAN, 10, 0).matches(10.01));
        assertFalse(condition(ComparisonOperator.GREATER_THAN, 10, 0).matches(10));
        assertTrue(condition(ComparisonOperator.GREATER_THAN_OR_EQUAL, 10, 0).matches(10));
        assertTrue(condition(ComparisonOperator.LESS_THAN, 10, 0).matches(9.99));
        assertFalse(condition(ComparisonOperator.LESS_THAN, 10, 0).matches(10));
        assertTrue(condition(ComparisonOperator.LESS_THAN_OR_EQUAL, 10, 0).matches(10));
        assertTrue(condition(ComparisonOperator.EQUAL, 10, 0).matches(10));
        assertFalse(condition(ComparisonOperator.EQUAL, 10, 0).matches(10.0001));
    }

    @Test
    void rangeIncludesBothEndpoints() {
        AttributeCondition condition = condition(ComparisonOperator.RANGE, 2.5, 4.5);

        assertTrue(condition.matches(2.5));
        assertTrue(condition.matches(3.5));
        assertTrue(condition.matches(4.5));
        assertFalse(condition.matches(2.49));
        assertFalse(condition.matches(4.51));
    }

    @Test
    void invalidConditionsAndNonFiniteValuesNeverMatch() {
        assertFalse(new AttributeCondition(" ", ComparisonOperator.EQUAL, 1, 0).isValid());
        assertFalse(new AttributeCondition("minecraft:generic.max_health", ComparisonOperator.RANGE, 5, 4).isValid());
        assertFalse(condition(ComparisonOperator.GREATER_THAN, Double.NaN, 0).matches(10));
        assertFalse(condition(ComparisonOperator.GREATER_THAN, 1, 0).matches(Double.POSITIVE_INFINITY));
    }

    private static AttributeCondition condition(ComparisonOperator operator, double value, double upperValue) {
        return new AttributeCondition("minecraft:generic.max_health", operator, value, upperValue);
    }
}
