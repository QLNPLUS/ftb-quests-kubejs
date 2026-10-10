package dev.questjs.ftbquestskubejs.data;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.questjs.ftbquestskubejs.attribute.ComparisonOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.OptionalDouble;

/**
 * Completes when a numeric value stored in the player's persistent data
 * ({@code PersistentData} compound, written by commands or other mods)
 * satisfies the configured comparison. Supports negative and fractional
 * numbers; every numeric NBT tag type is read as a double.
 */
public final class CustomDataNumberTask extends Task {
    public static final String TASK_NAMESPACE = "ftbquestskubejs";

    private static final String PATH_TAG = "path";
    private static final String COMPARISON_TAG = "comparison";
    private static final String VALUE_TAG = "value";
    private static final String UPPER_VALUE_TAG = "upper_value";
    private static final int MAX_PATH_LENGTH = 256;

    public static final TaskType TYPE = TaskTypes.register(
            new ResourceLocation(TASK_NAMESPACE, "custom_data_number"),
            CustomDataNumberTask::new,
            () -> Icon.getIcon("minecraft:command_block")
    );

    private String path = "";
    private ComparisonOperator comparison = ComparisonOperator.GREATER_THAN_OR_EQUAL;
    private double value = 0.0;
    private double upperValue = 0.0;

    public CustomDataNumberTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return TYPE;
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        path = nbt.contains(PATH_TAG, Tag.TAG_STRING) ? nbt.getString(PATH_TAG) : "";
        comparison = parseComparison(nbt.contains(COMPARISON_TAG, Tag.TAG_STRING) ? nbt.getString(COMPARISON_TAG) : "");
        value = nbt.contains(VALUE_TAG, Tag.TAG_DOUBLE) ? nbt.getDouble(VALUE_TAG) : 0.0;
        upperValue = nbt.contains(UPPER_VALUE_TAG, Tag.TAG_DOUBLE) ? nbt.getDouble(UPPER_VALUE_TAG) : 0.0;
    }

    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putString(PATH_TAG, path);
        nbt.putString(COMPARISON_TAG, comparison.name());
        nbt.putDouble(VALUE_TAG, value);
        nbt.putDouble(UPPER_VALUE_TAG, upperValue);
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        path = buffer.readUtf(MAX_PATH_LENGTH);
        comparison = parseComparison(buffer.readUtf(64));
        value = buffer.readDouble();
        upperValue = buffer.readDouble();
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(path, MAX_PATH_LENGTH);
        buffer.writeUtf(comparison.name(), 64);
        buffer.writeDouble(value);
        buffer.writeDouble(upperValue);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString(PATH_TAG, path, newValue -> path = newValue, "points")
                .setNameKey("ftbquestskubejs.config.data_path");
        NameMap<ComparisonOperator> comparisonNames = NameMap.of(
                        ComparisonOperator.GREATER_THAN_OR_EQUAL,
                        Arrays.asList(ComparisonOperator.values())
                )
                .id(operator -> operator.name().toLowerCase(java.util.Locale.ROOT))
                .name(operator -> Component.translatable(
                        "ftbq_attribute_tasks.operator." + operator.name().toLowerCase(java.util.Locale.ROOT)
                ))
                .create();
        config.addEnum(COMPARISON_TAG, comparison, newValue -> comparison = newValue, comparisonNames)
                .setNameKey("ftbq_attribute_tasks.config.comparison");
        config.addDouble(VALUE_TAG, value, newValue -> value = newValue,
                value, -Double.MAX_VALUE, Double.MAX_VALUE)
                .setNameKey("ftbq_attribute_tasks.config.threshold_or_lower_bound");
        config.addDouble(UPPER_VALUE_TAG, upperValue, newValue -> upperValue = newValue,
                upperValue, -Double.MAX_VALUE, Double.MAX_VALUE)
                .setNameKey("ftbq_attribute_tasks.config.upper_bound_range_only");
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

        CustomDataNumberCondition condition = new CustomDataNumberCondition(path, comparison, value, upperValue);
        if (!condition.isValid()) {
            return;
        }

        OptionalDouble actual = readNumericPath(player.getPersistentData(), path);
        if (actual.isPresent() && condition.matches(actual.getAsDouble())) {
            teamData.setProgress(this, 1L);
        }
    }

    private static ComparisonOperator parseComparison(String name) {
        try {
            return ComparisonOperator.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return ComparisonOperator.GREATER_THAN_OR_EQUAL;
        }
    }

    /**
     * Walks the dot-separated path through nested compounds and returns the
     * leaf as a double when it is any numeric tag. Missing paths, non-compound
     * intermediates and non-numeric leaves yield empty.
     */
    static OptionalDouble readNumericPath(CompoundTag root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return OptionalDouble.empty();
        }

        String[] segments = path.split("\\.");
        CompoundTag current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            Tag next = current.get(segments[i]);
            if (!(next instanceof CompoundTag compound)) {
                return OptionalDouble.empty();
            }
            current = compound;
        }

        Tag leaf = current.get(segments[segments.length - 1]);
        if (leaf instanceof NumericTag numeric) {
            double asDouble = numeric.getAsDouble();
            return Double.isFinite(asDouble) ? OptionalDouble.of(asDouble) : OptionalDouble.empty();
        }
        return OptionalDouble.empty();
    }
}
