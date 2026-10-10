package dev.questjs.ftbquestskubejs.data;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.questjs.ftbquestskubejs.attribute.ComparisonOperator;
import dev.questjs.ftbquestskubejs.composite.ReevaluableTask;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.OptionalDouble;

/**
 * Completes when a numeric value stored in the player's persistent data
 * satisfies the configured comparison. Two stores are consulted, in order:
 * the KubeJS store ({@code KubeJSPersistentData}, written by
 * {@code /kubejs persistent_data} and {@code entity.persistentData} in scripts)
 * and the Forge store ({@code PersistentData}, written by
 * {@code /data merge entity @s {PersistentData:{...}}}). Negative and
 * fractional numbers are supported; every numeric NBT tag type is read as a
 * double.
 */
public final class CustomDataNumberTask extends Task implements ReevaluableTask {
    public static final String TASK_NAMESPACE = "ftbquestskubejs";

    private static final String PATH_TAG = "path";
    private static final String COMPARISON_TAG = "comparison";
    private static final String VALUE_TAG = "value";
    private static final String UPPER_VALUE_TAG = "upper_value";
    private static final int MAX_PATH_LENGTH = 256;

    /**
     * KubeJS injects {@code kjs$getPersistentData()} onto Entity via mixin;
     * reflection keeps this compile-time independent of KubeJS internals.
     * Null when KubeJS is absent, in which case only the Forge store is read.
     */
    private static final Method KJS_GET_PERSISTENT_DATA = findKjsPersistentDataMethod();

    private static Method findKjsPersistentDataMethod() {
        try {
            return Entity.class.getMethod("kjs$getPersistentData");
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    public static final TaskType TYPE = TaskTypes.register(
            new ResourceLocation(TASK_NAMESPACE, "custom_data_number"),
            CustomDataNumberTask::new,
            () -> ItemIcon.getItemIcon(Items.NAME_TAG)
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
                value, -1.0E15, 1.0E15)
                .setNameKey("ftbq_attribute_tasks.config.threshold_or_lower_bound");
        config.addDouble(UPPER_VALUE_TAG, upperValue, newValue -> upperValue = newValue,
                upperValue, -1.0E15, 1.0E15)
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

        if (isSatisfiedNow(teamData, player)) {
            teamData.setProgress(this, 1L);
        }
    }

    @Override
    public boolean isSatisfiedNow(TeamData teamData, ServerPlayer player) {
        CustomDataNumberCondition condition = new CustomDataNumberCondition(path, comparison, value, upperValue);
        if (!condition.isValid()) {
            return false;
        }

        OptionalDouble actual = readNumericRoots(kubeJsPersistentData(player), player.getPersistentData(), path);
        return actual.isPresent() && condition.matches(actual.getAsDouble());
    }

    private static CompoundTag kubeJsPersistentData(ServerPlayer player) {
        Method method = KJS_GET_PERSISTENT_DATA;
        if (method == null) {
            return null;
        }
        try {
            return (CompoundTag) method.invoke(player);
        } catch (ReflectiveOperationException exception) {
            return null;
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
     * Resolves the path against each root in order and returns the first
     * numeric hit. Null roots are skipped.
     */
    static OptionalDouble readNumericRoots(CompoundTag first, CompoundTag second, String path) {
        OptionalDouble value = readNumericPath(first, path);
        return value.isPresent() ? value : readNumericPath(second, path);
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
