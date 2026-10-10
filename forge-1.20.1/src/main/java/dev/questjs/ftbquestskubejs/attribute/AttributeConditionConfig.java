package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;

@OnlyIn(Dist.CLIENT)
final class AttributeConditionConfig extends ConfigValue<AttributeCondition> {
    private final NameMap<ResourceLocation> attributeNames;

    AttributeConditionConfig(NameMap<ResourceLocation> attributeNames) {
        this.attributeNames = attributeNames;
    }

    @Override
    public AttributeCondition copy(AttributeCondition value) {
        return value;
    }

    @Override
    public Component getStringForGUI(AttributeCondition condition) {
        ResourceLocation id = ResourceLocation.tryParse(condition.attributeId());
        Component attribute = id == null
                ? Component.literal(condition.attributeId())
                : attributeNames.getDisplayName(id);
        Component comparison = Component.translatable(
                "ftbq_attribute_tasks.operator." + condition.comparison().name().toLowerCase(java.util.Locale.ROOT)
        );
        Component value = condition.comparison() == ComparisonOperator.RANGE
                ? Component.literal(condition.value() + " – " + condition.upperValue())
                : Component.literal(Double.toString(condition.value()));
        return attribute.copy().append(Component.literal(" ")).append(comparison).append(Component.literal(" ")).append(value);
    }

    @Override
    public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
        AttributeCondition current = getValue();
        ResourceLocation currentAttribute = ResourceLocation.tryParse(current.attributeId());
        if (currentAttribute == null || !BuiltInRegistries.ATTRIBUTE.containsKey(currentAttribute)) {
            currentAttribute = attributeNames.defaultValue;
        }

        Draft draft = new Draft(currentAttribute, current.comparison(), current.value(), current.upperValue());
        ConfigGroup group = new ConfigGroup("attribute_condition", accepted -> {
            if (!accepted) {
                callback.save(false);
                return;
            }

            AttributeCondition edited = new AttributeCondition(
                    draft.attribute.toString(),
                    draft.comparison,
                    draft.value,
                    draft.upperValue
            );
            if (!edited.isValid()) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.translatable("ftbq_attribute_tasks.error.invalid_condition"),
                            false
                    );
                }
                callback.save(false);
                return;
            }

            setCurrentValue(edited);
            callback.save(true);
        });
        group.add("attribute", new AttributeDropdownConfig(attributeNames), draft.attribute,
                value -> draft.attribute = value, attributeNames.defaultValue)
                .setNameKey("ftbq_attribute_tasks.config.attribute");
        NameMap<ComparisonOperator> comparisonNames = NameMap.of(
                        ComparisonOperator.GREATER_THAN,
                        Arrays.asList(ComparisonOperator.values())
                )
                .id(operator -> operator.name().toLowerCase())
                .name(operator -> Component.translatable("ftbq_attribute_tasks.operator." + operator.name().toLowerCase()))
                .create();
        group.addEnum("comparison", draft.comparison, value -> draft.comparison = value, comparisonNames)
                .setNameKey("ftbq_attribute_tasks.config.comparison");
        group.addDouble("value", draft.value, value -> draft.value = value,
                draft.value, -1.0E15, 1.0E15)
                .setNameKey("ftbq_attribute_tasks.config.threshold_or_lower_bound");
        group.addDouble("upper_value", draft.upperValue, value -> draft.upperValue = value,
                draft.upperValue, -1.0E15, 1.0E15)
                .setNameKey("ftbq_attribute_tasks.config.upper_bound_range_only");
        group.setNameKey("ftbq_attribute_tasks.config.condition");

        EditConfigScreen screen = new EditConfigScreen(group);
        screen.setAutoclose(true);
        screen.openGui();
    }

    private static final class Draft {
        private ResourceLocation attribute;
        private ComparisonOperator comparison;
        private double value;
        private double upperValue;

        private Draft(ResourceLocation attribute, ComparisonOperator comparison, double value, double upperValue) {
            this.attribute = attribute;
            this.comparison = comparison;
            this.value = value;
            this.upperValue = upperValue;
        }
    }
}
