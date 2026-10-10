package dev.questjs.ftbquestskubejs.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

final class AttributeConditionJson5Codec {
    private static final Codec<ComparisonOperator> COMPARISON_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(ComparisonOperator.valueOf(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Unknown attribute comparison: " + value);
                }
            },
            ComparisonOperator::name
    );

    static final Codec<AttributeCondition> CODEC = RecordCodecBuilder.<AttributeCondition>create(instance -> instance.group(
            Codec.STRING.fieldOf("attribute").forGetter(AttributeCondition::attributeId),
            COMPARISON_CODEC.fieldOf("comparison").forGetter(AttributeCondition::comparison),
            Codec.DOUBLE.fieldOf("value").forGetter(AttributeCondition::value),
            Codec.DOUBLE.fieldOf("upper_value").forGetter(AttributeCondition::upperValue)
    ).apply(instance, AttributeCondition::new)).validate(condition -> condition.isValid()
            && Identifier.tryParse(condition.attributeId()) != null
            ? DataResult.success(condition)
            : DataResult.error(() -> "Invalid attribute condition"));

    private AttributeConditionJson5Codec() {
    }
}
