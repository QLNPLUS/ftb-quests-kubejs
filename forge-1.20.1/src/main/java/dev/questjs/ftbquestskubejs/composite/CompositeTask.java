package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.snbt.SNBTCompoundTag;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Completes when every sub-task has been satisfied within one shared retention
 * window, rather than when each sub-task has <i>ever</i> been satisfied.
 *
 * <h2>Why a window instead of a single tick</h2>
 * Sub-tasks are driven by different sources — an attribute read, a position
 * check, an advancement — and there is no guarantee they all read true on the
 * same game tick. Requiring an exact common tick makes such a task practically
 * uncompletable. So each sub-task keeps a "last satisfied at" timestamp: it is
 * refreshed whenever the sub-task currently holds, and it stops counting once
 * {@code windowTicks} have passed without a refresh. The composite completes on
 * the first check where all timestamps are simultaneously fresh.
 *
 * <p>Concretely, with the default 20-tick window: sub-task A holds at t=0 and
 * B holds at t=10 → completes at t=10. But if A holds at t=0 and B only holds
 * at t=40, A has timed out (40 − 0 &gt; 20) and the task does not complete —
 * which is the "A was satisfied, then stopped being satisfied, then B was
 * satisfied" case this task type exists to reject.
 *
 * <h2>Sub-tasks never enter team state</h2>
 * FTB Quests latches completion one way: {@code TeamData.isCompleted(task)}
 * turning true makes the tick handler skip that task forever. If sub-tasks went
 * through {@code submitTask}, "A was satisfied once" would be remembered
 * permanently and the window would be meaningless. So sub-tasks are evaluated
 * through {@link ReevaluableTaskRegistry}, which only ever calls side-effect
 * free predicates, and only the composite task itself writes progress.
 *
 * <p>The window timestamps live in {@link #lastSatisfiedTick}, keyed by team id
 * because a {@code Task} object is shared by every player in the quest file.
 * They are intentionally <b>not persisted</b>: the worst case of losing them on
 * a restart or quest reload is one window's worth of freshness (about a second).
 */
public final class CompositeTask extends Task {
    public static final String TASK_NAMESPACE = "ftbquestskubejs";

    private static final String WINDOW_TICKS_TAG = "window_ticks";
    private static final String SUB_TASKS_TAG = "sub_tasks";
    private static final String TYPE_TAG = "type";

    /** Guards against an unbounded config panel and a runaway per-tick cost. */
    public static final int MAX_SUB_TASKS = 8;
    public static final int DEFAULT_WINDOW_TICKS = 20;
    public static final int MIN_WINDOW_TICKS = 1;
    public static final int MAX_WINDOW_TICKS = 200;

    /**
     * Checked twice a second. Deliberately finer than the default window so the
     * window's edge is not quantised by the polling period: with a 20-tick
     * period a 20-tick window would effectively stretch to 40 ticks.
     */
    private static final int CHECK_PERIOD_TICKS = 10;

    public static final TaskType TYPE = TaskTypes.register(
            new ResourceLocation(TASK_NAMESPACE, "composite"),
            CompositeTask::new,
            () -> ItemIcon.getItemIcon(Items.COMPARATOR)
    );

    private final List<Task> subTasks = new ArrayList<>();
    private final RetentionWindow window = new RetentionWindow();
    private int windowTicks = DEFAULT_WINDOW_TICKS;

    public CompositeTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return TYPE;
    }

    public List<Task> getSubTasks() {
        return subTasks;
    }

    public int getWindowTicks() {
        return windowTicks;
    }

    // ------------------------------------------------------------------ data

    /**
     * Mirrors how FTB itself writes a quest's task list: a {@link ListTag} of
     * {@link SNBTCompoundTag} entries, each carrying {@code type} followed by
     * that task's own {@code writeData}.
     *
     * <p>Unlike FTB's task entries, sub-task entries carry no {@code id}. FTB
     * needs ids because tasks are keys into {@code TeamData} progress maps;
     * sub-tasks never enter team state, so an id would only be a collision
     * hazard. Each read allocates a fresh id from the quest file instead.
     */
    @Override
    public void writeData(CompoundTag nbt) {
        super.writeData(nbt);
        nbt.putInt(WINDOW_TICKS_TAG, windowTicks);

        ListTag list = new ListTag();
        for (Task child : subTasks) {
            SNBTCompoundTag entry = new SNBTCompoundTag();
            if (child != null) {
                entry.putString(TYPE_TAG, child.getType().getTypeForNBT());
                child.writeData(entry);
            }
            // A null child is a deliberate gap in the slot layout. Writing an
            // empty entry for it keeps the slots after it in place across a
            // reload; dropping the entry would silently shift them.
            list.add(entry);
        }
        nbt.put(SUB_TASKS_TAG, list);
    }

    @Override
    public void readData(CompoundTag nbt) {
        super.readData(nbt);
        windowTicks = clampWindow(nbt.contains(WINDOW_TICKS_TAG, Tag.TAG_INT)
                ? nbt.getInt(WINDOW_TICKS_TAG) : DEFAULT_WINDOW_TICKS);

        subTasks.clear();
        ListTag list = nbt.contains(SUB_TASKS_TAG, Tag.TAG_LIST)
                ? nbt.getList(SUB_TASKS_TAG, Tag.TAG_COMPOUND) : new ListTag();
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            String typeId = entry.contains(TYPE_TAG, Tag.TAG_STRING)
                    ? entry.getString(TYPE_TAG) : "";
            Task child = TaskType.createTask(getQuestFile().newID(), getQuest(), typeId);
            if (child == null) {
                // A deliberate gap (see writeData), or a task type that no longer
                // exists. Keep an empty slot so the slots after it stay put
                // instead of failing the file.
                subTasks.add(null);
                continue;
            }
            child.readData(entry);
            subTasks.add(child);
        }

        trimTrailingBlanks();
        // Sub-task slots shifted, so any remembered timestamps are meaningless.
        window.clear();
    }

    @Override
    public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeVarInt(windowTicks);
        // The count must match exactly what follows, or the client desyncs the
        // remaining packet payload.
        // The count is positional: it counts empty slots too, so a gap in the
        // middle survives the trip to the client instead of shifting every later
        // slot left. An empty type id marks a gap, and the count must match what
        // follows or the client desyncs the rest of the packet payload.
        buffer.writeVarInt(subTasks.size());
        for (Task child : subTasks) {
            if (child == null) {
                buffer.writeUtf("", 128);
                continue;
            }
            buffer.writeUtf(child.getType().getTypeForNBT(), 128);
            child.writeNetData(buffer);
        }
    }

    @Override
    public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        windowTicks = clampWindow(buffer.readVarInt());

        subTasks.clear();
        int count = Math.min(buffer.readVarInt(), MAX_SUB_TASKS);
        for (int i = 0; i < count; i++) {
            String typeId = buffer.readUtf(128);
            if (typeId.isEmpty()) {
                subTasks.add(null);
                continue;
            }
            Task child = TaskType.createTask(getQuestFile().newID(), getQuest(), typeId);
            if (child == null) {
                // Cannot skip this child's payload, so abort the rest of the list.
                break;
            }
            child.readNetData(buffer);
            subTasks.add(child);
        }
        trimTrailingBlanks();

        window.clear();
    }

    private static int clampWindow(int value) {
        return Math.max(MIN_WINDOW_TICKS, Math.min(MAX_WINDOW_TICKS, value));
    }

    // ------------------------------------------------------------- evaluation

    @Override
    public int autoSubmitOnPlayerTick() {
        return CHECK_PERIOD_TICKS;
    }

    @Override
    public void submitTask(TeamData teamData, ServerPlayer player, ItemStack craftedItem) {
        if (teamData.isCompleted(this) || !checkTaskSequence(teamData)) {
            return;
        }
        if (subTasks.isEmpty()) {
            return;
        }

        long now = player.level().getGameTime();
        UUID teamId = teamData.getTeamId();
        boolean allFresh = true;

        // Every sub-task is offered to the window on every check. Returning
        // early on the first failing one would skip the rest of their
        // refreshes and drop a hold the window should still have covered.
        for (int i = 0; i < subTasks.size(); i++) {
            Boolean satisfied = ReevaluableTaskRegistry.evaluate(subTasks.get(i), teamData, player);
            if (satisfied == null) {
                // A sub-task we cannot evaluate live makes the whole composite
                // unachievable rather than silently degrading it to "latched".
                return;
            }
            if (!window.isFresh(teamId, i, subTasks.size(), satisfied, now, windowTicks)) {
                allFresh = false;
            }
        }

        if (allFresh) {
            teamData.setProgress(this, 1L);
        }
    }

    // -------------------------------------------------------------------- UI

    @Override
    @OnlyIn(Dist.CLIENT)
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        config.addInt(WINDOW_TICKS_TAG, windowTicks, value -> windowTicks = clampWindow(value),
                DEFAULT_WINDOW_TICKS, MIN_WINDOW_TICKS, MAX_WINDOW_TICKS)
                .setNameKey("ftbquestskubejs.config.window_ticks");

        for (int index = 0; index < MAX_SUB_TASKS; index++) {
            final int slot = index;
            ConfigGroup subgroup = config.getOrCreateSubgroup("subtask_" + slot, MAX_SUB_TASKS);
            // ConfigGroup.getName() is Component.translatable(getNameKey()) with
            // no argument substitution, so each slot needs its own key.
            subgroup.setNameKey("ftbquestskubejs.config.sub_task_" + slot);
            Task child = slot < subTasks.size() ? subTasks.get(slot) : null;
            // A dropdown rather than a click-cycled enum: the whole set of usable
            // task types has to be visible at once. See SubTaskTypeConfig.
            subgroup.add(TYPE_TAG, new SubTaskTypeConfig(this, slot), child, value -> {
            }, null).setNameKey("ftbquestskubejs.config.sub_task_type");
            if (child != null) {
                // The sub-task's own fields, inline. A type change reopens this
                // panel, so a newly chosen type's fields appear right away.
                child.fillConfigGroup(subgroup);
            }
        }
    }

    /**
     * Installs a sub-task of {@code type} in {@code slot}, or clears the slot when
     * {@code type} is null. Trailing slots are dropped so the list stays dense,
     * which is what {@link #writeData} and the per-slot UI both assume.
     */
    @OnlyIn(Dist.CLIENT)
    void applySubTask(int slot, TaskType type) {
        if (type == null) {
            if (slot < subTasks.size()) {
                subTasks.remove(slot);
            }
            trimTrailingBlanks();
            return;
        }

        Task created = type.createTask(getQuestFile().newID(), getQuest());
        if (created == null) {
            return;
        }
        while (subTasks.size() <= slot) {
            subTasks.add(null);
        }
        subTasks.set(slot, created);
        trimTrailingBlanks();
    }

    /**
     * Rebuilds this task's config panel and opens it.
     *
     * <p>Necessary because {@code EditConfigScreen} snapshots its entry widgets in
     * its constructor, so config values added to the group afterwards never show up.
     * After a sub-task type change, the new sub-task's own fields only exist in a
     * freshly built group. Same shape as FTB's own task editor button: a root group
     * carrying the save callback, then {@code createSubGroup}, then
     * {@code fillConfigGroup}.
     */
    @OnlyIn(Dist.CLIENT)
    void openEditor() {
        ConfigGroup root = new ConfigGroup("ftbquestskubejs_composite", changed -> {
            if (changed) {
                editedFromGUI();
            }
        });
        root.setNameKey("ftbquestskubejs.config.composite");
        fillConfigGroup(createSubGroup(root));
        new EditConfigScreen(root).openGui();
    }

    private void trimTrailingBlanks() {
        while (!subTasks.isEmpty() && subTasks.get(subTasks.size() - 1) == null) {
            subTasks.remove(subTasks.size() - 1);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addMouseOverText(TooltipList list, TeamData teamData) {
        super.addMouseOverText(list, teamData);
        list.add(Component.translatable("ftbquestskubejs.tooltip.window_ticks", windowTicks));
        for (Task child : subTasks) {
            if (child != null) {
                list.add(Component.literal(" - " + child.getAltTitle().getString()));
            }
        }
    }

    /**
     * The fallback title, used when the user left this task untitled.
     *
     * <p>It must not call {@link #getTitle()}: {@code getTitle()} is final and,
     * when {@code rawTitle} is blank, resolves <i>through</i> {@code getAltTitle()}.
     * Calling back the other way recursed until the click handler died with a
     * {@code StackOverflowError}. FTB's own task types only ever build their
     * fallback from their own fields plus a lang key — see {@code DimensionTask}.
     */
    @Override
    public Component getAltTitle() {
        return Component.translatable("ftbquests.task." + TASK_NAMESPACE + ".composite");
    }
}
