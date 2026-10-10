package dev.questjs.ftbquestskubejs.attribute;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public final class AttributeTask extends Task {
    /**
     * Kept as the original standalone mod's namespace so quest files authored
     * against More And More Quest load unchanged inside QuestJS.
     */
    public static final String TASK_NAMESPACE = "ftbq_attribute_tasks";

    private static final int MAX_CONDITIONS = 256;
    private static final String CONDITIONS_KEY = "conditions";

    public static final TaskType TYPE = TaskTypes.register(
            Identifier.fromNamespaceAndPath(TASK_NAMESPACE, "attribute_check"),
            AttributeTask::new,
            () -> Icon.getIcon("minecraft:paper")
    );

    private final List<AttributeCondition> conditions = new ArrayList<>();

    public AttributeTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return TYPE;
    }

    @Override
    public void readData(Json5Object json, HolderLookup.Provider provider) {
        super.readData(json, provider);
        conditions.clear();
        conditions.addAll(Json5Util.fetch(
                json, CONDITIONS_KEY, AttributeConditionJson5Codec.CODEC.listOf()
        ).orElse(List.of()));
    }

    @Override
    public void writeData(Json5Object json, HolderLookup.Provider provider) {
        super.writeData(json, provider);
        Json5Util.store(
                json,
                CONDITIONS_KEY,
                AttributeConditionJson5Codec.CODEC.listOf(),
                conditions.stream()
                        .filter(condition -> condition.isValid() && Identifier.tryParse(condition.attributeId()) != null)
                        .toList()
        );
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        int size = buffer.readVarInt();
        if (size < 0 || size > MAX_CONDITIONS) {
            throw new DecoderException("Invalid attribute condition count: " + size);
        }

        conditions.clear();
        for (int i = 0; i < size; i++) {
            Identifier attributeId = buffer.readIdentifier();
            String comparisonName = buffer.readUtf(64);
            double value = buffer.readDouble();
            double upperValue = buffer.readDouble();
            try {
                AttributeCondition condition = new AttributeCondition(
                        attributeId.toString(), ComparisonOperator.valueOf(comparisonName), value, upperValue
                );
                if (condition.isValid()) {
                    conditions.add(condition);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore incompatible or malformed condition data.
            }
        }
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        List<AttributeCondition> validConditions = conditions.stream()
                .filter(condition -> condition.isValid() && Identifier.tryParse(condition.attributeId()) != null)
                .toList();
        if (validConditions.size() > MAX_CONDITIONS) {
            throw new IllegalStateException("Too many attribute conditions: " + validConditions.size());
        }

        buffer.writeVarInt(validConditions.size());
        for (AttributeCondition condition : validConditions) {
            Identifier id = Identifier.tryParse(condition.attributeId());
            if (id == null) {
                throw new IllegalStateException("Invalid attribute identifier: " + condition.attributeId());
            }
            buffer.writeIdentifier(id);
            buffer.writeUtf(condition.comparison().name());
            buffer.writeDouble(condition.value());
            buffer.writeDouble(condition.upperValue());
        }
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        AttributeCondition defaultCondition = new AttributeCondition(
                Attributes.MAX_HEALTH.unwrapKey().orElseThrow().identifier().toString(),
                ComparisonOperator.GREATER_THAN_OR_EQUAL,
                20.0,
                20.0
        );
        config.addList(
                CONDITIONS_KEY,
                conditions,
                new AttributeConditionConfig(AttributeDropdownConfig.createNameMap()),
                defaultCondition
        ).setNameKey("ftbq_attribute_tasks.config.conditions");
    }

    @Override
    public int autoSubmitOnPlayerTick() {
        return 20;
    }

    @Override
    public void submitTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem) {
        if (teamData.isCompleted(this) || !checkTaskSequence(teamData)) {
            return;
        }

        if (AttributeConditionEvaluator.allMatch(conditions, id -> readAttributeValue(player, id))) {
            teamData.setProgress(this, 1L);
        }
    }

    private static OptionalDouble readAttributeValue(ServerPlayer player, String attributeId) {
        Identifier id = Identifier.tryParse(attributeId);
        if (id == null) {
            return OptionalDouble.empty();
        }

        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.get(id).orElse(null);
        if (attribute == null) {
            return OptionalDouble.empty();
        }

        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? OptionalDouble.empty() : OptionalDouble.of(instance.getValue());
    }
}
