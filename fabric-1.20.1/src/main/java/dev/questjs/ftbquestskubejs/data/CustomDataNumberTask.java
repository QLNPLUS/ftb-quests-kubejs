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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Completes when a numeric value stored in the player's persistent data
 * satisfies the configured comparison. Fabric has no Forge-style
 * {@code PersistentData} compound, so the KubeJS store is the only one
 * consulted: {@code KubeJSPersistentData}, written by
 * {@code /kubejs persistent_data} and {@code entity.persistentData} in scripts.
 * Negative and fractional numbers are supported; every numeric NBT tag type is
 * read as a double.
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
     * Null when KubeJS is absent, in which case no store is readable.
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
            new Identifier(TASK_NAMESPACE, "custom_data_number"),
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
    public void readData(NbtCompound nbt) {
        super.readData(nbt);
        path = nbt.contains(PATH_TAG, NbtElement.STRING_TYPE) ? nbt.getString(PATH_TAG) : "";
        comparison = parseComparison(nbt.contains(COMPARISON_TAG, NbtElement.STRING_TYPE) ? nbt.getString(COMPARISON_TAG) : "");
        value = nbt.contains(VALUE_TAG, NbtElement.DOUBLE_TYPE) ? nbt.getDouble(VALUE_TAG) : 0.0;
        upperValue = nbt.contains(UPPER_VALUE_TAG, NbtElement.DOUBLE_TYPE) ? nbt.getDouble(UPPER_VALUE_TAG) : 0.0;
    }

    @Override
    public void writeData(NbtCompound nbt) {
        super.writeData(nbt);
        nbt.putString(PATH_TAG, path);
        nbt.putString(COMPARISON_TAG, comparison.name());
        nbt.putDouble(VALUE_TAG, value);
        nbt.putDouble(UPPER_VALUE_TAG, upperValue);
    }

    @Override
    public void readNetData(PacketByteBuf buffer) {
        super.readNetData(buffer);
        path = buffer.readString(MAX_PATH_LENGTH);
        comparison = parseComparison(buffer.readString(64));
        value = buffer.readDouble();
        upperValue = buffer.readDouble();
    }

    @Override
    public void writeNetData(PacketByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeString(path, MAX_PATH_LENGTH);
        buffer.writeString(comparison.name(), 64);
        buffer.writeDouble(value);
        buffer.writeDouble(upperValue);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString(PATH_TAG, path, newValue -> path = newValue, "points")
                .setNameKey("ftbquestskubejs.config.data_path");
        NameMap<ComparisonOperator> comparisonNames = NameMap.of(
                        ComparisonOperator.GREATER_THAN_OR_EQUAL,
                        Arrays.asList(ComparisonOperator.values())
                )
                .id(operator -> operator.name().toLowerCase(Locale.ROOT))
                .name(operator -> Text.translatable(
                        "ftbq_attribute_tasks.operator." + operator.name().toLowerCase(Locale.ROOT)
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
        CustomDataNumberCondition condition = new CustomDataNumberCondition(path, comparison, value, upperValue);
        if (!condition.isValid()) {
            return false;
        }

        OptionalDouble actual = readNumericRoots(kubeJsPersistentData(player), null, path);
        return actual.isPresent() && condition.matches(actual.getAsDouble());
    }

    private static NbtCompound kubeJsPersistentData(ServerPlayerEntity player) {
        Method method = KJS_GET_PERSISTENT_DATA;
        if (method == null) {
            return null;
        }
        try {
            return (NbtCompound) method.invoke(player);
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
    static OptionalDouble readNumericRoots(NbtCompound first, NbtCompound second, String path) {
        OptionalDouble value = readNumericPath(first, path);
        return value.isPresent() ? value : readNumericPath(second, path);
    }

    /**
     * Walks the dot-separated path through nested compounds and returns the
     * leaf as a double when it is any numeric tag. Missing paths, non-compound
     * intermediates and non-numeric leaves yield empty.
     */
    static OptionalDouble readNumericPath(NbtCompound root, String path) {
        if (root == null || path == null || path.isEmpty()) {
            return OptionalDouble.empty();
        }

        String[] segments = path.split("\\.");
        NbtCompound current = root;
        for (int i = 0; i < segments.length - 1; i++) {
            NbtElement next = current.get(segments[i]);
            if (!(next instanceof NbtCompound compound)) {
                return OptionalDouble.empty();
            }
            current = compound;
        }

        NbtElement leaf = current.get(segments[segments.length - 1]);
        if (leaf instanceof AbstractNbtNumber numeric) {
            double asDouble = numeric.doubleValue();
            return Double.isFinite(asDouble) ? OptionalDouble.of(asDouble) : OptionalDouble.empty();
        }
        return OptionalDouble.empty();
    }
}