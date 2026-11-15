package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Arrays;
import java.util.Locale;

@Environment(EnvType.CLIENT)
final class AttributeConditionConfig extends ConfigValue<AttributeCondition> {
    private final NameMap<Identifier> attributeNames;

    AttributeConditionConfig(NameMap<Identifier> attributeNames) {
        this.attributeNames = attributeNames;
    }

    @Override
    public AttributeCondition copy(AttributeCondition value) {
        return value;
    }

    @Override
    public Text getStringForGUI(AttributeCondition condition) {
        Identifier id = Identifier.tryParse(condition.attributeId());
        Text attribute = id == null
                ? Text.literal(condition.attributeId())
                : attributeNames.getDisplayName(id);
        Text comparison = Text.translatable(
                "ftbq_attribute_tasks.operator." + condition.comparison().name().toLowerCase(Locale.ROOT)
        );
        Text value = condition.comparison() == ComparisonOperator.RANGE
                ? Text.literal(condition.value() + " – " + condition.upperValue())
                : Text.literal(Double.toString(condition.value()));
        return attribute.copy().append(Text.literal(" ")).append(comparison).append(Text.literal(" ")).append(value);
    }

    @Override
    public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
        AttributeCondition current = getValue();
        Identifier currentAttribute = Identifier.tryParse(current.attributeId());
        if (currentAttribute == null || !Registries.ATTRIBUTE.containsId(currentAttribute)) {
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
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().player.sendMessage(
                            Text.translatable("ftbq_attribute_tasks.error.invalid_condition"),
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
                .id(operator -> operator.name().toLowerCase(Locale.ROOT))
                .name(operator -> Text.translatable("ftbq_attribute_tasks.operator." + operator.name().toLowerCase(Locale.ROOT)))
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
