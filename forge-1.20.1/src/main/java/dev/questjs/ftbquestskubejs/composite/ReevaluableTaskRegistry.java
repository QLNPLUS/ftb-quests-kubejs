package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.ObservationTask;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.ftb.mods.ftbquests.quest.task.TaskTypes;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Decides whether a candidate composite sub-task can answer "satisfied right
 * now", and asks it.
 *
 * <p>Two sources of predicates exist, and neither is universal:
 * <ul>
 *   <li>Our own task types implement {@link ReevaluableTask} directly.</li>
 *   <li>FTB Quests' {@link AbstractBooleanTask} subclasses expose
 *       {@code canSubmit(TeamData, ServerPlayer)}. Verified from bytecode,
 *       {@code AbstractBooleanTask.submitTask} is literally
 *       {@code if (!isCompleted && checkTaskSequence && canSubmit) setProgress(1)},
 *       so {@code canSubmit} is a pure state test by construction and is safe
 *       to call without latching anything.</li>
 * </ul>
 *
 * <h2>Why inheritance, not an allow-list, decides membership</h2>
 * An earlier revision used an explicit allow-list of six built-in types. That
 * was needlessly closed: it could never admit a task type registered by another
 * mod, even though such a type is exactly as re-evaluable as FTB's own if it
 * extends {@code AbstractBooleanTask}. Membership is now "extends
 * {@code AbstractBooleanTask}, or implements {@link ReevaluableTask}", minus a
 * deny-list of types whose {@code canSubmit} was verified to be a constant
 * {@code true}.
 *
 * <p>The deny-list is short because it only has to cover the cases that are
 * actually wrong. {@code CheckmarkTask} is completed by a manual click and
 * {@code ObservationTask} by a client-side raycast event; both report
 * {@code canSubmit == true} unconditionally, so admitting them would let a
 * composite task complete the instant it loaded. Everything else in that class
 * family reads live player or team state.
 *
 * <h2>What is excluded by construction</h2>
 * Progress-based types ({@code item}, {@code kill}, {@code stat}, {@code xp},
 * {@code fluid}, {@code energy}) do not extend {@code AbstractBooleanTask}, so
 * they fall out automatically. That is the right outcome, not an oversight:
 * their state is cumulative team progress, so "satisfied right now" is not a
 * question they can answer. Bytecode inspection confirms they expose no
 * side-effect-free live predicate — {@code ItemTask.test} matches an
 * {@code ItemStack} against a matcher rather than reading the player, and
 * {@code StatTask} / {@code XPTask} / {@code KillTask} expose only
 * {@code getMaxProgress} and a mutating {@code submitTask}.
 *
 * <p>Caveat, stated plainly: a third-party subclass of {@code AbstractBooleanTask}
 * is admitted on the strength of the base class contract, which this mod cannot
 * verify. If such a type overrides {@code canSubmit} to be constant-true, a
 * composite using it can complete immediately.
 */
public final class ReevaluableTaskRegistry {

    /**
     * Built-in boolean tasks whose {@code canSubmit} is a constant {@code true},
     * verified from bytecode. Admitting these would complete a composite task on
     * load, so they are named out even though they extend
     * {@link AbstractBooleanTask}.
     */
    private static final Set<Class<?>> DENIED_TASKS = Set.of(
            CheckmarkTask.class,
            ObservationTask.class,
            // A composite holding a composite would recurse; it is not a
            // predicate at all, it is the thing being composed.
            CompositeTask.class
    );

    private ReevaluableTaskRegistry() {
    }

    /**
     * @return the sub-task's current satisfaction, or {@code null} when this
     *         task type cannot be evaluated as a live condition
     */
    public static Boolean evaluate(Task task, TeamData teamData, ServerPlayer player) {
        if (task instanceof ReevaluableTask reevaluable) {
            return reevaluable.isSatisfiedNow(teamData, player);
        }
        if (task instanceof AbstractBooleanTask booleanTask && isSupportedClass(task.getClass())) {
            return booleanTask.canSubmit(teamData, player);
        }
        return null;
    }

    /** Whether a task of this class could be used as a composite sub-task. */
    public static boolean isSupportedClass(Class<?> taskClass) {
        if (DENIED_TASKS.contains(taskClass)) {
            return false;
        }
        if (ReevaluableTask.class.isAssignableFrom(taskClass)) {
            return true;
        }
        return AbstractBooleanTask.class.isAssignableFrom(taskClass);
    }

    /**
     * The task types the editor offers as sub-task choices for {@code quest}.
     *
     * <p>Enumerates the whole live registry rather than a hard-coded list, so
     * types registered by other mods appear too. {@link TaskType} does not expose
     * its task class, so membership is decided by constructing a throwaway
     * instance and testing its class. The probe uses id {@code -1} and is never
     * added to the quest, which keeps the quest file's id counter untouched.
     *
     * <p>Needs a bootstrapped registry, so it is not unit-testable; the policy it
     * applies is {@link #isSupportedClass}, which is.
     */
    public static List<TaskType> supportedTypes(Quest quest) {
        List<TaskType> types = new ArrayList<>();
        if (quest == null) {
            return types;
        }
        for (TaskType type : TaskTypes.TYPES.values()) {
            if (type == null) {
                continue;
            }
            Task probe;
            try {
                probe = type.createTask(-1L, quest);
            } catch (RuntimeException e) {
                // A type that cannot be instantiated cannot be offered either.
                continue;
            }
            if (probe != null && isSupportedClass(probe.getClass())) {
                types.add(type);
            }
        }
        types.sort(Comparator.comparing(type -> type.getTypeId().toString()));
        return types;
    }
}
