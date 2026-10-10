package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.client.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.client.config.editable.EditableConfigValue;
import dev.ftb.mods.ftblibrary.client.gui.input.MouseButton;
import dev.ftb.mods.ftblibrary.client.gui.widget.Widget;
import dev.ftb.mods.ftblibrary.util.NameMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.Locale;

final class AttributeConditionConfig extends EditableConfigValue<AttributeCondition> {
    private final NameMap<Identifier> attributeNames;

    AttributeConditionConfig(NameMap<Identifier> attributeNames) {
        this.attributeNames = attributeNames;
    }

    @Override
    public Component getStringForGUI(AttributeCondition condition) {
        Identifier id = Identifier.tryParse(condition.attributeId());
        Component attribute = id == null
                ? Component.literal(condition.attributeId())
                : attributeNames.getDisplayName(id);
        Component comparison = Component.translatable(
                "ftbq_attribute_tasks.operator." + condition.comparison().name().toLowerCase(Locale.ROOT)
        );
        Component value = condition.comparison() == ComparisonOperator.RANGE
                ? Component.literal(condition.value() + " – " + condition.upperValue())
                : Component.literal(Double.toString(condition.value()));
        return attribute.copy().append(Component.literal(" ")).append(comparison).append(Component.literal(" ")).append(value);
    }

    @Override
    public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
        AttributeCondition current = getValue();
        Identifier currentAttribute = Identifier.tryParse(current.attributeId());
        if (currentAttribute == null || BuiltInRegistries.ATTRIBUTE.get(currentAttribute).isEmpty()) {
            currentAttribute = attributeNames.defaultValue;
        }

        Draft draft = new Draft(currentAttribute, current.comparison(), current.value(), current.upperValue());
        EditableConfigGroup group = new EditableConfigGroup("attribute_condition", accepted -> {
            if (!accepted) {
                callback.save(false);
                return;
            }

            AttributeCondition edited = new AttributeCondition(
                    draft.attribute.toString(), draft.comparison, draft.value, draft.upperValue
            );
            if (!edited.isValid()) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.translatable("ftbq_attribute_tasks.error.invalid_condition")
                    );
                }
                callback.save(false);
                return;
            }

            setValue(edited);
            callback.save(true);
        });

        group.add("attribute", new AttributeDropdownConfig(attributeNames), draft.attribute,
                value -> draft.attribute = value, attributeNames.defaultValue)
                .setNameKey("ftbq_attribute_tasks.config.attribute");

        NameMap<ComparisonOperator> comparisonNames = NameMap.of(
                        ComparisonOperator.GREATER_THAN_OR_EQUAL,
                        Arrays.asList(ComparisonOperator.values())
                )
                .id(operator -> operator.name().toLowerCase(Locale.ROOT))
                .name(operator -> Component.translatable(
                        "ftbq_attribute_tasks.operator." + operator.name().toLowerCase(Locale.ROOT)
                ))
                .create();
        group.addEnum("comparison", draft.comparison, value -> draft.comparison = value,
                comparisonNames, ComparisonOperator.GREATER_THAN_OR_EQUAL)
                .setNameKey("ftbq_attribute_tasks.config.comparison");
        group.addDouble("value", draft.value, value -> draft.value = value,
                draft.value, -Double.MAX_VALUE, Double.MAX_VALUE)
                .setNameKey("ftbq_attribute_tasks.config.threshold_or_lower_bound");
        group.addDouble("upper_value", draft.upperValue, value -> draft.upperValue = value,
                draft.upperValue, -Double.MAX_VALUE, Double.MAX_VALUE)
                .setNameKey("ftbq_attribute_tasks.config.upper_bound_range_only");
        group.setNameKey("ftbq_attribute_tasks.config.condition");

        var screen = new dev.ftb.mods.ftblibrary.client.config.gui.EditConfigScreen(group);
        screen.setAutoclose(true);
        screen.openGui();
    }

    private static final class Draft {
        private Identifier attribute;
        private ComparisonOperator comparison;
        private double value;
        private double upperValue;

        private Draft(Identifier attribute, ComparisonOperator comparison, double value, double upperValue) {
            this.attribute = attribute;
            this.comparison = comparison;
            this.value = value;
            this.upperValue = upperValue;
        }
    }
}
