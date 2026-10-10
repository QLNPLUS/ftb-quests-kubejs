package dev.questjs.ftbquestskubejs.attribute;

import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class AttributeConditionCodec {
    static final int MAX_CONDITIONS = 256;
    private static final String ATTRIBUTE = "attribute";
    private static final String COMPARISON = "comparison";
    private static final String VALUE = "value";
    private static final String UPPER_VALUE = "upper_value";

    private AttributeConditionCodec() {
    }

    static ListTag writeNbt(Collection<AttributeCondition> conditions) {
        ListTag tags = new ListTag();
        for (AttributeCondition condition : conditions) {
            if (condition == null || !condition.isValid()) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString(ATTRIBUTE, condition.attributeId());
            tag.putString(COMPARISON, condition.comparison().name());
            tag.putDouble(VALUE, condition.value());
            tag.putDouble(UPPER_VALUE, condition.upperValue());
            tags.add(tag);
        }
        return tags;
    }

    static List<AttributeCondition> readNbt(ListTag tags) {
        List<AttributeCondition> conditions = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag tag = tags.getCompound(i);
            if (!tag.contains(ATTRIBUTE, Tag.TAG_STRING)
                    || !tag.contains(COMPARISON, Tag.TAG_STRING)
                    || !tag.contains(VALUE, Tag.TAG_DOUBLE)
                    || !tag.contains(UPPER_VALUE, Tag.TAG_DOUBLE)) {
                continue;
            }
            AttributeCondition condition = parse(
                    tag.getString(ATTRIBUTE), tag.getString(COMPARISON),
                    tag.getDouble(VALUE), tag.getDouble(UPPER_VALUE)
            );
            if (condition != null) {
                conditions.add(condition);
            }
        }
        return conditions;
    }

    static void writeNetwork(FriendlyByteBuf buffer, Collection<AttributeCondition> conditions) {
        List<AttributeCondition> valid = conditions.stream()
                .filter(condition -> condition != null && condition.isValid())
                .toList();
        if (valid.size() > MAX_CONDITIONS) {
            throw new IllegalStateException("Too many attribute conditions: " + valid.size());
        }

        buffer.writeVarInt(valid.size());
        for (AttributeCondition condition : valid) {
            buffer.writeUtf(condition.attributeId());
            buffer.writeUtf(condition.comparison().name());
            buffer.writeDouble(condition.value());
            buffer.writeDouble(condition.upperValue());
        }
    }

    static List<AttributeCondition> readNetwork(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_CONDITIONS) {
            throw new DecoderException("Invalid attribute condition count: " + size);
        }

        List<AttributeCondition> conditions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            AttributeCondition condition = parse(
                    buffer.readUtf(256), buffer.readUtf(64), buffer.readDouble(), buffer.readDouble()
            );
            if (condition != null) {
                conditions.add(condition);
            }
        }
        return conditions;
    }

    private static AttributeCondition parse(String attributeId, String comparisonId, double value, double upperValue) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) {
            return null;
        }

        try {
            AttributeCondition condition = new AttributeCondition(
                    id.toString(), ComparisonOperator.valueOf(comparisonId), value, upperValue
            );
            return condition.isValid() ? condition : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
