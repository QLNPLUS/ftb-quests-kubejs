package dev.questjs.ftbquestskubejs.attribute;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeConditionEvaluatorTest {
    @Test
    void allConditionsMustPass() {
        List<AttributeCondition> conditions = List.of(
                new AttributeCondition("minecraft:generic.max_health", ComparisonOperator.GREATER_THAN_OR_EQUAL, 20, 0),
                new AttributeCondition("minecraft:generic.movement_speed", ComparisonOperator.RANGE, 0.1, 0.2)
        );

        assertTrue(AttributeConditionEvaluator.allMatch(conditions, id -> switch (id) {
            case "minecraft:generic.max_health" -> OptionalDouble.of(20);
            case "minecraft:generic.movement_speed" -> OptionalDouble.of(0.15);
            default -> OptionalDouble.empty();
        }));
        assertFalse(AttributeConditionEvaluator.allMatch(conditions, id -> switch (id) {
            case "minecraft:generic.max_health" -> OptionalDouble.of(20);
            case "minecraft:generic.movement_speed" -> OptionalDouble.of(0.3);
            default -> OptionalDouble.empty();
        }));
    }

    @Test
    void missingValuesAndEmptyListsFail() {
        AttributeCondition condition = new AttributeCondition(
                "mod:missing", ComparisonOperator.GREATER_THAN, 1, 0
        );

        assertFalse(AttributeConditionEvaluator.allMatch(List.of(), id -> OptionalDouble.of(1)));
        assertFalse(AttributeConditionEvaluator.allMatch(List.of(condition), id -> OptionalDouble.empty()));
    }
}
