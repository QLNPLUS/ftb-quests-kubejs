package dev.questjs.ftbquestskubejs.attribute;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.questjs.ftbquestskubejs.composite.ReevaluableTask;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

public final class AttributeTask extends Task implements ReevaluableTask {
    /**
     * Kept as the original standalone mod's namespace so quest files authored
     * against More And More Quest load unchanged inside QuestJS.
     */
    public static final String TASK_NAMESPACE = "ftbq_attribute_tasks";

    private static final String CONDITIONS_TAG = "conditions";

    public static final TaskType TYPE = TaskTypes.register(
            new Identifier(TASK_NAMESPACE, "attribute_check"),
            AttributeTask::new,
            () -> ItemIcon.getItemIcon(Items.IRON_CHESTPLATE)
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
    public void readData(NbtCompound nbt) {
        super.readData(nbt);
        conditions.clear();
        conditions.addAll(AttributeConditionCodec.readNbt(nbt.getList(CONDITIONS_TAG, NbtElement.COMPOUND_TYPE)));
    }

    @Override
    public void writeData(NbtCompound nbt) {
        super.writeData(nbt);
        nbt.put(CONDITIONS_TAG, AttributeConditionCodec.writeNbt(conditions));
    }

    @Override
    public void readNetData(PacketByteBuf buffer) {
        super.readNetData(buffer);
        conditions.clear();
        conditions.addAll(AttributeConditionCodec.readNetwork(buffer));
    }

    @Override
    public void writeNetData(PacketByteBuf buffer) {
        super.writeNetData(buffer);
        AttributeConditionCodec.writeNetwork(buffer, conditions);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        AttributeCondition defaultCondition = new AttributeCondition(
                Registries.ATTRIBUTE.getId(EntityAttributes.GENERIC_MAX_HEALTH).toString(),
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
    public void submitTask(TeamData teamData, ServerPlayerEntity player, ItemStack craftedItem) {
        if (teamData.isCompleted(this) || !checkTaskSequence(teamData)) {
            return;
        }

        if (isSatisfiedNow(teamData, player)) {
            teamData.setProgress(this, 1L);
        }
    }

    @Override
    public boolean isSatisfiedNow(TeamData teamData, ServerPlayerEntity player) {
        return AttributeConditionEvaluator.allMatch(conditions, id -> readAttributeValue(player, id));
    }

    private static OptionalDouble readAttributeValue(ServerPlayerEntity player, String attributeId) {
        Identifier id = Identifier.tryParse(attributeId);
        if (id == null || !Registries.ATTRIBUTE.containsId(id)) {
            return OptionalDouble.empty();
        }

        EntityAttribute attribute = Registries.ATTRIBUTE.get(id);
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        return instance == null ? OptionalDouble.empty() : OptionalDouble.of(instance.getValue());
    }
}
