package dev.questjs.ftbquestskubejs.attribute;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AttributeConditionCodecTest {
    private static final List<AttributeCondition> CONDITIONS = List.of(
            new AttributeCondition("minecraft:generic.max_health", ComparisonOperator.GREATER_THAN_OR_EQUAL, 20, 0),
            new AttributeCondition("minecraft:generic.movement_speed", ComparisonOperator.RANGE, 0.1, 0.2)
    );

    @Test
    void nbtRoundTripPreservesAllConditions() {
        assertEquals(CONDITIONS, AttributeConditionCodec.readNbt(AttributeConditionCodec.writeNbt(CONDITIONS)));
    }

    @Test
    void networkRoundTripPreservesAllConditions() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AttributeConditionCodec.writeNetwork(buffer, CONDITIONS);
            assertEquals(CONDITIONS, AttributeConditionCodec.readNetwork(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void malformedNbtConditionsAreIgnored() {
        ListTag tags = new ListTag();
        CompoundTag reversedRange = new CompoundTag();
        reversedRange.putString("attribute", "minecraft:generic.max_health");
        reversedRange.putString("comparison", ComparisonOperator.RANGE.name());
        reversedRange.putDouble("value", 10);
        reversedRange.putDouble("upper_value", 5);
        tags.add(reversedRange);

        assertEquals(List.of(), AttributeConditionCodec.readNbt(tags));
    }

    @Test
    void excessiveNetworkConditionCountIsRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(AttributeConditionCodec.MAX_CONDITIONS + 1);
            assertThrows(RuntimeException.class, () -> AttributeConditionCodec.readNetwork(buffer));
        } finally {
            buffer.release();
        }
    }
}
