package dev.questjs.ftbquestskubejs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.EditableConfigGroup;
import dev.ftb.mods.ftblibrary.client.gui.input.MouseButton;
import dev.ftb.mods.ftblibrary.client.gui.widget.Widget;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.json5.Json5Util;
import dev.ftb.mods.ftblibrary.util.NameMap;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.questjs.ftbquestskubejs.attribute.ComparisonOperator;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Completes when a numeric value stored in the player's persistent data
 * satisfies the configured comparison. Two stores are consulted, in order:
 * the KubeJS store ({@code KubeJSPersistentData}, written by
 * {@code /kubejs persistent_data} and {@code entity.persistentData} in scripts)
 * and the NeoForge store ({@code PersistentData}, written by
 * {@code /data merge entity @s {PersistentData:{...}}}). Negative and
 * fractional numbers are supported; every numeric NBT tag type is read as a
 * double.
 */
public final class CustomDataNumberTask extends Task {
    public static final String TASK_NAMESPACE = "ftbquestskubejs";

    private static final String PATH_KEY = "path";
    private static final String COMPARISON_KEY = "comparison";
    private static final String VALUE_KEY = "value";
    private static final String UPPER_VALUE_KEY = "upper_value";
    private static final int MAX_PATH_LENGTH = 256;

    /**
     * KubeJS injects {@code kjs$getPersistentData()} onto Entity via mixin;
     * reflection keeps this compile-time independent of KubeJS internals.
     * Null when KubeJS is absent, in which case only the NeoForge store is read.
     */
    private static final Method KJS_GET_PERSISTENT_DATA = findKjsPersistentDataMethod();

    private static Method findKjsPersistentDataMethod() {
        try {
            return Entity.class.getMethod("kjs$getPersistentData");
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static final Codec<ComparisonOperator> COMPARISON_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(ComparisonOperator.valueOf(name));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Unknown comparison: " + name);
                }
            },
            ComparisonOperator::name
    );

    public static final TaskType TYPE = TaskTypes.register(
            Identifier.fromNamespaceAndPath(TASK_NAMESPACE, "custom_data_number"),
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
    public void readData(Json5Object json, HolderLookup.Provider provider) {
        super.readData(json, provider);
        path = Json5Util.fetch(json, PATH_KEY, Codec.STRING).orElse("");
        comparison = Json5Util.fetch(json, COMPARISON_KEY, COMPARISON_CODEC).orElse(ComparisonOperator.GREATER_THAN_OR_EQUAL);
        value = Json5Util.fetch(json, VALUE_KEY, Codec.DOUBLE).orElse(0.0);
        upperValue = Json5Util.fetch(json, UPPER_VALUE_KEY, Codec.DOUBLE).orElse(0.0);
    }

    @Override
    public void writeData(Json5Object json, HolderLookup.Provider provider) {
        super.writeData(json, provider);
        Json5Util.store(json, PATH_KEY, Codec.STRING, path);
        Json5Util.store(json, COMPARISON_KEY, COMPARISON_CODEC, comparison);
        Json5Util.store(json, VALUE_KEY, Codec.DOUBLE, value);
        Json5Util.store(json, UPPER_VALUE_KEY, Codec.DOUBLE, upperValue);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        path = buffer.readUtf(MAX_PATH_LENGTH);
        try {
            comparison = ComparisonOperator.valueOf(buffer.readUtf(64));
        } catch (IllegalArgumentException exception) {
            comparison = ComparisonOperator.GREATER_THAN_OR_EQUAL;
        }
        value = buffer.readDouble();
        upperValue = buffer.readDouble();
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(path, MAX_PATH_LENGTH);
        buffer.writeUtf(comparison.name(), 64);
        buffer.writeDouble(value);
        buffer.writeDouble(upperValue);
    }

    @Override
    public void fillConfigGroup(EditableConfigGroup config) {
        super.fillConfigGroup(config);
        config.addString(PATH_KEY, path, newValue -> path = newValue, "points")
                .setNameKey("ftbquestskubejs.config.data_path");
        NameMap<ComparisonOperator> comparisonNames = NameMap.of(
                        ComparisonOperator.GREATER_THAN_OR_EQUAL,
                        Arrays.asList(ComparisonOperator.values())
                )
                .id(operator -> operator.name().toLowerCase(Locale.ROOT))
                .name(operator -> Component.translatable(
                        "ftbq_attribute_tasks.operator." + operator.name().toLowerCase(Locale.ROOT)
                ))
                .create();
        config.addEnum(COMPARISON_KEY, comparison, newValue -> comparison = newValue,
                comparisonNames, ComparisonOperator.GREATER_THAN_OR_EQUAL)
                .setNameKey("ftbq_attribute_tasks.config.comparison");
        config.addDouble(VALUE_KEY, value, newValue -> value = newValue,
                value, -1.0E15, 1.0E15)
                .setNameKey("ftbq_attribute_tasks.config.threshold_or_lower_bound");
        config.addDouble(UPPER_VALUE_KEY, upperValue, newValue -> upperValue = newValue,
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

        CustomDataNumberCondition condition = new CustomDataNumberCondition(path, comparison, value, upperValue);
        if (!condition.isValid()) {
            return;
        }

        OptionalDouble actual = readNumericRoots(kubeJsPersistentData(player), player.getPersistentData(), path);
        if (actual.isPresent() && condition.matches(actual.getAsDouble())) {
            teamData.setProgress(this, 1L);
        }
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
            double asDouble = numeric.doubleValue();
            return Double.isFinite(asDouble) ? OptionalDouble.of(asDouble) : OptionalDouble.empty();
        }
        return OptionalDouble.empty();
    }
}
