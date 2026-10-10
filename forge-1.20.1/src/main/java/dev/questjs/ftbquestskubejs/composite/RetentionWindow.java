package dev.questjs.ftbquestskubejs.composite;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The retention window behind {@link CompositeTask}: per-team, per-sub-task
 * "last satisfied at" timestamps that expire after a configurable number of
 * ticks.
 *
 * <p>Kept free of Minecraft and FTB types so the timing rules can be unit
 * tested without a running server.
 *
 * <p>State is keyed by team id because a {@code Task} object is shared by every
 * player in the quest file — storing the timestamps on the task itself would
 * leak one team's freshness into another's.
 *
 * <p>The map is deliberately never persisted. Losing it on a restart or a quest
 * reload costs at most one window's worth of freshness (about a second), which
 * is the whole point of the window: nothing that outlives it should count.
 */
public final class RetentionWindow {

    /**
     * Sentinel for "this slot has never held yet". It cannot be 0, because a
     * fresh world's {@code gameTime} starts at 0 and the tick handler runs on
     * {@code gameTime % period == 0} — a sub-task that holds at tick 0 would
     * otherwise be indistinguishable from one that never held.
     */
    private static final long NEVER_SATISFIED = -1L;

    private final Map<UUID, long[]> lastSatisfiedTick = new HashMap<>();

    /**
     * Records one sub-task's current result and reports whether it counts as
     * satisfied for this check.
     *
     * <p>Every slot must be offered to this method on every check, even once
     * one slot has already failed. Skipping the remaining slots would drop
     * their refresh and lose a hold that the window should still have covered.
     *
     * @param teamId       the team being evaluated
     * @param index        sub-task index, aligned with the task's sub-task list
     * @param slotCount    current sub-task count; a change rebuilds the state
     * @param satisfiedNow whether the sub-task's predicate holds at {@code now}
     * @param now          the current game time in ticks
     * @param windowTicks  how long a hold stays fresh
     * @return true when this sub-task was satisfied at some tick no more than
     *         {@code windowTicks} ago
     */
    public boolean isFresh(UUID teamId, int index, int slotCount,
                           boolean satisfiedNow, long now, int windowTicks) {
        long[] last = stateFor(teamId, slotCount);
        if (satisfiedNow) {
            last[index] = now;
        }
        // A slot that has never held must not read as fresh merely because
        // now - 0 is small during the first ticks of a new world.
        return last[index] != NEVER_SATISFIED && now - last[index] <= windowTicks;
    }

    /**
     * Returns the state array for a team, rebuilding it when the sub-task count
     * changed so a mid-session edit to the sub-task list cannot leave a value
     * under the wrong index.
     */
    private long[] stateFor(UUID teamId, int slotCount) {
        long[] state = lastSatisfiedTick.get(teamId);
        if (state == null || state.length != slotCount) {
            state = new long[slotCount];
            java.util.Arrays.fill(state, NEVER_SATISFIED);
            lastSatisfiedTick.put(teamId, state);
        }
        return state;
    }

    /** Drops all remembered freshness, e.g. after a quest file reload. */
    public void clear() {
        lastSatisfiedTick.clear();
    }

    /** Exposed for tests: the tick a slot last held, or -1 if it never has. */
    long lastSatisfiedAt(UUID teamId, int index) {
        long[] state = lastSatisfiedTick.get(teamId);
        return state != null && index < state.length ? state[index] : NEVER_SATISFIED;
    }
}
