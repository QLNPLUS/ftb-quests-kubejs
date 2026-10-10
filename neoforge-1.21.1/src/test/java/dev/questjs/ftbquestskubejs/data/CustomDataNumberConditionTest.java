package dev.questjs.ftbquestskubejs.data;

import dev.questjs.ftbquestskubejs.attribute.ComparisonOperator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomDataNumberConditionTest {
    @Test
    void negativeAndFractionalValuesCompare() {
        assertTrue(new CustomDataNumberCondition("points", ComparisonOperator.LESS_THAN, 0, 0).matches(-5));
        assertTrue(new CustomDataNumberCondition("points", ComparisonOperator.GREATER_THAN, -10.5, 0).matches(-10.49));
        assertTrue(new CustomDataNumberCondition("balance", ComparisonOperator.EQUAL, 0.1, 0).matches(0.1));
        assertTrue(new CustomDataNumberCondition("balance", ComparisonOperator.RANGE, -2.5, 2.5).matches(-2.5));
        assertFalse(new CustomDataNumberCondition("balance", ComparisonOperator.RANGE, -2.5, 2.5).matches(2.51));
    }

    @Test
    void malformedPathsAndRangesAreInvalid() {
        assertFalse(new CustomDataNumberCondition("", ComparisonOperator.EQUAL, 1, 0).isValid());
        assertFalse(new CustomDataNumberCondition("a..b", ComparisonOperator.EQUAL, 1, 0).isValid());
        assertFalse(new CustomDataNumberCondition(".a", ComparisonOperator.EQUAL, 1, 0).isValid());
        assertFalse(new CustomDataNumberCondition("a.", ComparisonOperator.EQUAL, 1, 0).isValid());
        assertFalse(new CustomDataNumberCondition(" points ", ComparisonOperator.EQUAL, 1, 0).isValid());
        assertFalse(new CustomDataNumberCondition("points", ComparisonOperator.RANGE, 5, 4).isValid());
        assertFalse(new CustomDataNumberCondition("points", ComparisonOperator.RANGE, Double.NaN, 4).isValid());
        assertTrue(new CustomDataNumberCondition("shop.balance", ComparisonOperator.GREATER_THAN_OR_EQUAL, -1, 0).isValid());
    }

    @Test
    void invalidConditionNeverMatches() {
        assertFalse(new CustomDataNumberCondition("", ComparisonOperator.GREATER_THAN, 1, 0).matches(100));
    }
}
