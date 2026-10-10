package dev.questjs.ftbquestskubejs.attribute;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AttributeConditionJson5CodecTest {
    private static final List<AttributeCondition> CONDITIONS = List.of(
            new AttributeCondition("minecraft:max_health", ComparisonOperator.GREATER_THAN_OR_EQUAL, 20, 0),
            new AttributeCondition("minecraft:movement_speed", ComparisonOperator.RANGE, 0.1, 0.2)
    );

    @Test
    void listCodecRoundTripPreservesConditions() {
        var codec = AttributeConditionJson5Codec.CODEC.listOf();
        var encoded = codec.encodeStart(JsonOps.INSTANCE, CONDITIONS).getOrThrow();
        assertEquals(CONDITIONS, codec.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void invalidRangeIsRejectedByCodec() {
        AttributeCondition invalid = new AttributeCondition(
                "minecraft:max_health", ComparisonOperator.RANGE, 10, 5
        );
        assertFalse(AttributeConditionJson5Codec.CODEC.encodeStart(JsonOps.INSTANCE, invalid).result().isPresent());
    }
}
