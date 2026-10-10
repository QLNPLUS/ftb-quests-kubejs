package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public final class AttributeTask extends Task {
    /**
     * Kept as the original standalone mod's namespace so quest files authored
     * against More And More Quest load unchanged inside QuestJS.
     */
    public static final String TASK_NAMESPACE = "ftbq_attribute_tasks";

    private static final String CONDITIONS_TAG = "conditions";

    public static final TaskType TYPE = TaskTypes.register(
            ResourceLocation.fromNamespaceAndPath(TASK_NAMESPACE, "attribute_check"),
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
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        conditions.clear();
        conditions.addAll(AttributeConditionCodec.readNbt(nbt.getList(CONDITIONS_TAG, Tag.TAG_COMPOUND)));
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.put(CONDITIONS_TAG, AttributeConditionCodec.writeNbt(conditions));
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        conditions.clear();
        conditions.addAll(AttributeConditionCodec.readNetwork(buffer));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        AttributeConditionCodec.writeNetwork(buffer, conditions);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        AttributeCondition defaultCondition = new AttributeCondition(
                Attributes.MAX_HEALTH.unwrapKey().orElseThrow().location().toString(),
                ComparisonOperator.GREATER_THAN_OR_EQUAL,
                20.0,
                20.0
        );
        AttributeConditionConfig conditionType = new AttributeConditionConfig(
                AttributeDropdownConfig.createNameMap()
        );
        config.addList("conditions", conditions, conditionType, defaultCondition)
                .setNameKey("ftbq_attribute_tasks.config.conditions");
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
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) {
            return OptionalDouble.empty();
        }

        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
        if (attribute == null) {
            return OptionalDouble.empty();
        }
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? OptionalDouble.empty() : OptionalDouble.of(instance.getValue());
    }
}
