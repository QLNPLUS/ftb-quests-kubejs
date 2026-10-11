package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import dev.ftb.mods.ftbquests.util.ProgressChange;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A composite task whose parts are <i>other, already-defined quests</i> — FTB's 任务,
 * the box with a title, an icon, dependencies and rewards, not the 目标 inside it.
 * It stores the ids of up to {@value #MAX_REFERENCES} quests anywhere in the quest
 * file, and the challenge is to finish all of them within a time limit that starts
 * the moment the first one is finished.
 *
 * <h2>The rule</h2>
 * <ol>
 *   <li>No referenced quest is complete yet → the task waits, no clock running.</li>
 *   <li>The first poll that finds a referenced quest complete → the clock starts, for
 *       {@link #getTimeLimitTicks()} game ticks (20 ticks = 1 second).</li>
 *   <li>All referenced quests are complete → this task completes.</li>
 *   <li>The clock runs out with some still missing → <b>every</b> referenced quest
 *       is reset, exactly like right-clicking it in the editor and choosing
 *       重置进度. The clock stops, so the attempt can be started again.</li>
 * </ol>
 *
 * <p>Step 2 is watched from the moment the quest is <i>unlockable</i>, not from the
 * moment the player opens it. An earlier revision gated on {@code TeamData.isStarted}
 * and that silently switched the whole type off: a quest is only ever marked started
 * when the player opens it in the quest book, so an author testing from the editor saw
 * neither a reset nor a completion, at any limit.
 *
 * <p>The clock runs on one timeline — the Overworld's game time — because game time
 * is per level, and an attempt that spans dimensions must not see a jump.
 *
 * <p>Step 4 is what makes this more than {@code Quest.addDependency}: a dependency
 * only ever gates forward, while this type takes progress away from quests it does
 * not own. That is deliberate and it is the whole point — a failed timed attempt
 * has to be redoable from scratch, not from wherever the player happened to be.
 *
 * <h2>What resetting a quest actually does</h2>
 * {@link #resetQuest} calls {@code Quest.forceProgress(teamData, new ProgressChange(...))}.
 * That is the same entry point FTB's own 重置进度 / 更改进度 UI uses, verified against
 * the 2001.4.22 bytecode, and it is the only call that covers all three things a
 * quest reset has to undo:
 * <ul>
 *   <li>{@code QuestObject.forceProgress} clears {@code setStarted(id, null)} and
 *       {@code setCompleted(id, null)} — the quest stops being complete.</li>
 *   <li>It then recurses into {@code getChildren()}, which for a quest is its tasks,
 *       and {@code Task.forceProgress} calls {@code TeamData.setProgress(task, 0)} —
 *       so every 目标 inside goes back to zero, counters included.</li>
 *   <li>{@code Quest.forceProgress} additionally walks {@code rewards}, so claimed
 *       rewards become claimable again. A quest reset that left the rewards spent
 *       would not be a quest reset.</li>
 * </ul>
 * The {@code ProgressChange} constructor defaults {@code reset = true} and
 * {@code notifications = false}, so no extra flags are needed.
 *
 * <h2>Why the old composite/window machinery is gone</h2>
 * Earlier revisions of this feature kept a per-reference "freshness window" and
 * consumed each reference the moment it was found complete, so that a quest
 * satisfied long ago would lapse. That needed a custom tick driver
 * ({@code ExternalTaskTickHandler}) because FTB stops ticking a task once it is
 * complete, a retention window per team, and a policy that refused references whose
 * reset would reopen other quests — and that last rule is what made whole groups of
 * entries invisible in the picker, which read as "this can't be referenced". None of
 * it is needed under the timed rule: completion is a plain latch check, the clock
 * only runs while this task is still incomplete, and reopening dependent quests is
 * the intended consequence of a failed attempt.
 *
 * <h2>Where the polling comes from</h2>
 * {@code FTBQuestsEventHandler.playerTick} walks every task with a positive
 * {@link #autoSubmitOnPlayerTick()}, and calls {@link #submitTask} when
 * {@code gameTime % period == 0}, the task is not complete, and
 * {@code TeamData.canStartTasks(quest)} holds. So the whole state machine lives in
 * {@link #submitTask}, and it stops running by itself once this task completes.
 * That loop runs on the server with a {@code ServerPlayer}, which is why nothing
 * here touches a client-only API.
 *
 * <h2>What the clock does not survive</h2>
 * The start tick is held in memory on the task instance, not in the quest file. A
 * server restart (or a quest-file reload) therefore restarts the countdown for
 * every team that had one running. That is accepted: the failure mode is that a
 * team gets a full window again, which is lenient rather than destructive.
 *
 * <h2>One caveat about resetting</h2>
 * Resetting a referenced quest re-arms that quest's own detection. An {@code item}
 * 目标 whose items are still in the inventory, or a {@code stat} 目标 whose counter
 * has not moved, may be satisfied again within a tick or two, which re-completes
 * that quest and restarts the clock. That is FTB's normal behaviour for 重置进度 too,
 * so it is not special-cased here.
 */
public final class ExternalTaskTask extends Task {
	public static final String TASK_NAMESPACE = "ftbquestskubejs";

	private static final String REFERENCED_QUESTS_TAG = "referenced_quests";
	private static final String TIME_LIMIT_TAG = "time_limit_ticks";
	/** The name this field had while it was stored in seconds. */
	private static final String LEGACY_SECONDS_TAG = "time_limit_seconds";
	/** The pre-rewrite name, which already stored ticks. */
	private static final String LEGACY_TIME_TAG = "window_ticks";
	// The pre-rewrite reference tag was "referenced_tasks" and held *task* ids. It is
	// deliberately not read back: quest and task ids are both plain longs, so an old
	// task id would silently resolve to some unrelated quest instead of failing. Old
	// references are dropped, and the editor shows an empty list.

	/**
	 * A ceiling on references. It bounds the editor list and the per-poll cost,
	 * which is one id dereference plus one flag read each.
	 */
	public static final int MAX_REFERENCES = 16;

	/** A minute. */
	public static final int DEFAULT_TIME_LIMIT_TICKS = 1_200;
	/** One second — anything shorter is not a challenge, it is a coin flip. */
	public static final int MIN_TIME_LIMIT_TICKS = 20;
	/** A day, so a challenge spanning several play sessions is expressible. */
	public static final int MAX_TIME_LIMIT_TICKS = 1_728_000;

	/**
	 * Checked twice a second. The clock is compared against whole game ticks, so
	 * this only sets how late a start or a reset can be noticed, not how long the
	 * window is.
	 */
	public static final int CHECK_PERIOD_TICKS = 10;

	public static final TaskType TYPE = TaskTypes.register(
			ResourceLocation.fromNamespaceAndPath(TASK_NAMESPACE, "external_task"),
			ExternalTaskTask::new,
			() -> ItemIcon.getItemIcon(Items.HOPPER)
	);

	private final List<Long> referencedQuestIds = new ArrayList<>();
	private int timeLimitTicks = DEFAULT_TIME_LIMIT_TICKS;

	/**
	 * team → game tick the countdown started on. Server-side only, and only while
	 * this task is incomplete; see the class note about restarts.
	 */
	private final Map<UUID, Long> startedAt = new HashMap<>();

	/**
	 * Records every state transition of the window. The type has no in-game feedback
	 * of its own — a reset is silent, and a countdown is invisible — so this is the
	 * only way an author can see why a challenge did or did not reset.
	 */
	private static final org.apache.logging.log4j.Logger LOGGER =
			org.apache.logging.log4j.LogManager.getLogger("ftbquestskubejs.external_task");

	public ExternalTaskTask(long id, Quest quest) {
		super(id, quest);
	}

	@Override
	public TaskType getType() {
		return TYPE;
	}

	// ------------------------------------------------------------------ data

	@Override
	public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
		super.writeData(nbt, provider);
		nbt.putLongArray(REFERENCED_QUESTS_TAG, toLongArray());
		nbt.putInt(TIME_LIMIT_TAG, timeLimitTicks);
	}

	@Override
	public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
		super.readData(nbt, provider);
		applyIds(nbt.getLongArray(REFERENCED_QUESTS_TAG));
		timeLimitTicks = resolveTimeLimitTicks(nbt);
		startedAt.clear();
	}

	/**
	 * The window in ticks, from whichever tag this save file happens to carry.
	 *
	 * <p>Three names have held this value across rewrites, and they are not all the
	 * same unit, so the order matters: the current tag is ticks, the intermediate one
	 * was seconds and has to be scaled, and the oldest was ticks again. A file
	 * written by any of them loads unchanged rather than silently running twenty
	 * times fast or slow.
	 */
	static int resolveTimeLimitTicks(CompoundTag nbt) {
		if (nbt.contains(TIME_LIMIT_TAG, Tag.TAG_INT)) {
			return clampTicks(nbt.getInt(TIME_LIMIT_TAG));
		}
		if (nbt.contains(LEGACY_SECONDS_TAG, Tag.TAG_INT)) {
			return clampTicks(nbt.getInt(LEGACY_SECONDS_TAG) * 20L);
		}
		if (nbt.contains(LEGACY_TIME_TAG, Tag.TAG_INT)) {
			return clampTicks(nbt.getInt(LEGACY_TIME_TAG));
		}
		return DEFAULT_TIME_LIMIT_TICKS;
	}

	@Override
	public void writeNetData(RegistryFriendlyByteBuf buffer) {
		super.writeNetData(buffer);
		buffer.writeLongArray(toLongArray());
		buffer.writeVarInt(timeLimitTicks);
	}

	@Override
	public void readNetData(RegistryFriendlyByteBuf buffer) {
		super.readNetData(buffer);
		// The whole array is read before it is capped, so the rest of the packet
		// payload stays aligned even if a peer sent more than MAX_REFERENCES.
		applyIds(buffer.readLongArray());
		timeLimitTicks = clampTicks(buffer.readVarInt());
		startedAt.clear();
	}

	public List<Long> getReferencedQuestIds() {
		return Collections.unmodifiableList(referencedQuestIds);
	}

	public int getTimeLimitTicks() {
		return timeLimitTicks;
	}

	/** The referenced quest at {@code index}, or null when the id is dangling. */
	public Quest getReference(int index) {
		if (index < 0 || index >= referencedQuestIds.size()) {
			return null;
		}
		return getQuestFile().getQuest(referencedQuestIds.get(index));
	}


	/** The referenced quests that still resolve, in list order. */
	public List<Quest> references() {
		List<Quest> resolved = new ArrayList<>(referencedQuestIds.size());
		for (long id : referencedQuestIds) {
			Quest quest = getQuestFile().getQuest(id);
			if (quest != null) {
				resolved.add(quest);
			}
		}
		return resolved;
	}

	private void applyIds(long[] ids) {
		referencedQuestIds.clear();
		for (long id : ids) {
			if (id != 0L && referencedQuestIds.size() < MAX_REFERENCES) {
				referencedQuestIds.add(id);
			}
		}
	}

	private long[] toLongArray() {
		long[] out = new long[referencedQuestIds.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = referencedQuestIds.get(i);
		}
		return out;
	}

	/**
	 * Takes the value in ticks. It is a {@code long} so that scaling a seconds tag
	 * cannot overflow into a negative number, which would clamp to the floor and hand
	 * the author a one-second window they never asked for.
	 */
	private static int clampTicks(long value) {
		if (value < MIN_TIME_LIMIT_TICKS) {
			return MIN_TIME_LIMIT_TICKS;
		}
		return (int) Math.min(MAX_TIME_LIMIT_TICKS, value);
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

		UUID team = teamData.getTeamId();
		// One timeline for the whole window. GameTime is per level, so measuring the
		// start in the Overworld and a later poll in the Nether would make the player
		// appear to have skipped years and reset the attempt on the spot.
		long now = player.server.overworld().getGameTime();
		List<Quest> referenced = references();
		Long start = startedAt.get(team);
		int done = 0;
		for (Quest quest : referenced) {
			if (teamData.isCompleted(quest)) {
				done++;
			}
		}

		if (referenced.isEmpty()) {
			// Nothing to chase, so nothing to time. An empty or fully dangling list
			// leaves this task incomplete rather than completing on zero references.
			startedAt.remove(team);
			return;
		}

		Action action = decide(done, referenced.size(), start, now, timeLimitTicks);
		if (action != Action.NONE) {
			LOGGER.info("external_task {} action={} done={}/{} clock={} now={} limit={}",
					getId(), action, done, referenced.size(),
					start == null ? "stopped" : start, now, timeLimitTicks);
		}

		switch (action) {
			case START -> startedAt.put(team, now);
			case COMPLETE -> {
				startedAt.remove(team);
				teamData.setProgress(this, 1L);
			}
			case RESET -> {
				startedAt.remove(team);
				for (Quest quest : referenced) {
					resetQuest(teamData, quest, team);
				}
			}
			case NONE -> {
			}
		}
	}

	/** What one poll decided. */
	public enum Action {
		NONE,
		/** The first referenced quest just completed: start the clock at this tick. */
		START,
		/** Every referenced quest is complete: finish this task. */
		COMPLETE,
		/** The clock ran out with quests missing: reset them all. */
		RESET
	}

	/**
	 * The whole state machine, as pure data so it can be tested without a level, a
	 * team or a quest file.
	 *
	 * <p>{@code startTick} is null when no countdown is running for this team. One
	 * parameter carries both the flag and the value on purpose: an earlier version
	 * took them separately, a call site inverted the flag, and every poll then read
	 * as "clock running, started at tick 0" — which reset the attempt instantly and
	 * made the window look ignored.
	 *
	 * <p>Success is checked before the deadline on purpose: if the last quest lands
	 * on the same poll that the window expires on, the attempt counts. The deadline
	 * bounds how late a completion may be noticed, not whether a completion that did
	 * happen counts.
	 */
	public static Action decide(int done, int total, Long startTick, long now, long limitTicks) {
		if (total == 0) {
			return Action.NONE;
		}
		if (done >= total) {
			return Action.COMPLETE;
		}
		if (startTick == null) {
			return done == 0 ? Action.NONE : Action.START;
		}
		return now - startTick >= limitTicks ? Action.RESET : Action.NONE;
	}

	/**
	 * Puts {@code quest} back to "not started, not completed", including everything
	 * inside it and its rewards. See the class note on
	 * {@code QuestObject.forceProgress} for why this one call is the whole reset.
	 */
	private void resetQuest(TeamData teamData, Quest quest, UUID team) {
		quest.forceProgress(teamData, new ProgressChange(quest, team));
	}

	// ------------------------------------------------------------- references

	/**
	 * Adds {@code quest} to the reference list. Returns false when it is already
	 * there, when the list is full, or when {@code quest} may not be referenced.
	 */
	public boolean addReference(Quest quest) {
		if (!canReference(this, quest)) {
			return false;
		}
		boolean changed = addReferenceToList(referencedQuestIds, quest.getId(), MAX_REFERENCES);
		if (changed) {
			startedAt.clear();
		}
		return changed;
	}

	/** Removes {@code quest} from the reference list. Returns false if it was not there. */
	public boolean removeReference(Quest quest) {
		boolean changed = removeReferenceFromList(referencedQuestIds, quest.getId());
		if (changed) {
			startedAt.clear();
		}
		return changed;
	}

	/**
	 * Whether {@code candidate} may become a reference of {@code owner}'s task.
	 *
	 * <p>Two structural rules, both about what a reset would do to the composite
	 * itself, and neither about the candidate's contents:
	 * <ul>
	 *   <li>A task cannot reference the quest it belongs to. Resetting that quest
	 *       runs through its 目标, which includes this task, so the attempt would
	 *       wipe itself and start over forever.</li>
	 *   <li>A task cannot reference a quest that holds another composite. Resetting
	 *       that quest wipes the other composite's progress, and a chain of
	 *       composites wiping each other has no bounded end.</li>
	 * </ul>
	 * Everything else is allowed — including quests that gate other quests, since
	 * reopening those is exactly what a failed attempt is supposed to do.
	 */
	public static boolean canReference(Task owner, Quest candidate) {
		return candidate != null && owner != null && candidate != owner.getQuest()
				&& !containsCompositeTask(candidate);
	}

	/** Whether any 目标 of {@code quest} is one of our own composite tasks. */
	private static boolean containsCompositeTask(Quest quest) {
		for (Task task : quest.getTasks()) {
			if (task instanceof ExternalTaskTask) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Appends {@code referencedId} when it is not already present and there is
	 * room. Kept free of any quest-file dependency so the list invariants — dense,
	 * unique, capped — are unit testable.
	 */
	static boolean addReferenceToList(List<Long> ids, long referencedId, int max) {
		if (ids.size() >= max || ids.contains(referencedId)) {
			return false;
		}
		ids.add(referencedId);
		return true;
	}

	/** Removes the first occurrence of {@code referencedId}. */
	static boolean removeReferenceFromList(List<Long> ids, long referencedId) {
		return ids.removeIf(id -> id == referencedId);
	}

	/**
	 * The quests this editor may offer: every quest in the file that
	 * {@link #canReference} permits and that is not referenced already, so the
	 * picker can never suggest a duplicate.
	 */
	@OnlyIn(Dist.CLIENT)
	public List<Quest> candidateQuests() {
		List<Quest> candidates = new ArrayList<>();
		for (Chapter chapter : getQuestFile().getAllChapters()) {
			for (Quest quest : chapter.getQuests()) {
				if (canReference(this, quest) && !referencedQuestIds.contains(quest.getId())) {
					candidates.add(quest);
				}
			}
		}
		candidates.sort(Comparator
				.comparing((Quest quest) -> quest.getChapter().getTitle().getString())
				.thenComparing(quest -> quest.getTitle().getString()));
		return candidates;
	}

	// -------------------------------------------------------------------- UI

	@Override
	@OnlyIn(Dist.CLIENT)
	public void fillConfigGroup(ConfigGroup config) {
		super.fillConfigGroup(config);
		// The floor here is 1, not MIN_TIME_LIMIT_TICKS, and that is not an oversight.
		// FTB's text field validates every keystroke: TextBox.insertText only keeps the
		// new text when IntConfig.parse accepts it, and parse rejects anything outside
		// the range. With a floor of 20 the first digit typed into an emptied field is
		// always below it — "2" for 200, "1" for 1200 — so the insert is refused, the
		// buffer stays empty, and no number can be entered at all. The real floor is
		// enforced by clampTicks on the way in, which is where it belongs.
		config.addInt(TIME_LIMIT_TAG, timeLimitTicks, value -> timeLimitTicks = clampTicks(value),
				DEFAULT_TIME_LIMIT_TICKS, 1, MAX_TIME_LIMIT_TICKS)
				.setNameKey("ftbquestskubejs.config.external_time_limit");

		// One row that opens the reference editor, rather than one row per slot.
		// Fixed slot rows read like a form to fill in order, and a picker per row
		// gave no overview of what is already referenced.
		// The default value must not be null: ConfigValue.addInfo() renders the
		// tooltip as getStringForGUI(defaultValue), so a null here crashes the
		// editor the moment the mouse hovers over the row.
		config.add(REFERENCED_QUESTS_TAG, new ReferenceListConfig(), Collections.emptyList(), references -> {
		}, Collections.emptyList()).setNameKey("ftbquestskubejs.config.reference_list");
	}

	/**
	 * The 引用的任务 row of the task editor.
	 *
	 * <p>{@link #getValue()} reads the live list instead of a cached value, because
	 * {@code EditConfigScreen.ConfigEntryButton.draw()} re-reads
	 * {@code value.getStringForGUI(value.getValue())} every frame — so the row's
	 * count updates the moment a reference is added or removed, without rebuilding
	 * the editor. Rebuilding it from inside a picker callback used to tear down the
	 * very editor the user was working in; {@link #onClicked} avoids that by handing
	 * the change back through the callback FTB supplied.
	 */
	@OnlyIn(Dist.CLIENT)
	private final class ReferenceListConfig extends ConfigValue<List<Quest>> {
		@Override
		public List<Quest> getValue() {
			return references();
		}

		@Override
		public void onClicked(Widget button, MouseButton mouseButton, ConfigCallback callback) {
			if (!getCanEdit()) {
				return;
			}
			new EditReferencesScreen(ExternalTaskTask.this, () -> callback.save(true)).openGui();
		}

		@Override
		public Component getStringForGUI(List<Quest> references) {
			// Null-tolerant: FTB renders some ConfigValue rows from the stored
			// default rather than getValue(), and callers other than draw() exist.
			return references == null || references.isEmpty()
					? Component.translatable("ftbquestskubejs.config.reference_none")
					: Component.translatable("ftbquestskubejs.config.reference_count", references.size());
		}

		@Override
		public Icon getIcon(List<Quest> references) {
			return Icon.empty();
		}
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void addMouseOverText(TooltipList list, TeamData teamData) {
		super.addMouseOverText(list, teamData);
		List<Quest> referenced = references();
		for (int i = 0; i < referenced.size(); i++) {
			list.add(Component.translatable("ftbquestskubejs.tooltip.referenced_quest",
					i + 1, referenceLabel(referenced.get(i))));
		}
	}

	/**
	 * The text used for one reference wherever it is shown: the quest's title, then
	 * the chapter that holds it.
	 *
	 * <p>The chapter matters because references come from anywhere in the file and
	 * quest titles repeat across chapters. The id is not in the label — a 19-digit
	 * number is unreadable in a list — but {@link EditReferencesScreen} and
	 * {@link SelectReferenceScreen} both add it to their search text, so pasting an
	 * id into the box still finds the quest.
	 */
	static String referenceLabel(Quest quest) {
		String title = quest.getTitle().getString().trim();
		if (title.isEmpty()) {
			title = quest.getAltTitle().getString().trim();
		}
		Chapter chapter = quest.getChapter();
		String group = chapter == null ? "" : chapter.getTitle().getString().trim();
		return group.isEmpty() ? title : title + "（" + group + "）";
	}

	@Override
	public Component getAltTitle() {
		if (referencedQuestIds.isEmpty()) {
			return Component.translatable("ftbquests.task." + TASK_NAMESPACE + ".external_task");
		}
		return Component.translatable("ftbquests.task." + TASK_NAMESPACE + ".external_task.count",
				referencedQuestIds.size());
	}
}
