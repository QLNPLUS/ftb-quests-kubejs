package dev.questjs.ftbquestskubejs.composite;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the timing rules a composite task lives or dies by. The helper
 * {@link #check} mirrors {@code CompositeTask.submitTask}: every slot is
 * offered to the window on every pass, and the composite completes only when
 * all of them are simultaneously fresh.
 */
class RetentionWindowTest {
    private static final int WINDOW = 20;

    private final UUID team = UUID.randomUUID();

    /** One composite check at {@code now}; returns whether it would complete. */
    private static boolean check(RetentionWindow window, UUID team, boolean[] satisfied, long now, int windowTicks) {
        boolean allFresh = true;
        for (int i = 0; i < satisfied.length; i++) {
            if (!window.isFresh(team, i, satisfied.length, satisfied[i], now, windowTicks)) {
                allFresh = false;
            }
        }
        return allFresh;
    }

    @Test
    void neverSatisfiedNeverCompletes() {
        RetentionWindow window = new RetentionWindow();
        assertFalse(check(window, team, new boolean[]{false, false}, 100L, WINDOW));
        assertFalse(check(window, team, new boolean[]{false, false}, 1_000L, WINDOW));
    }

    @Test
    void bothSatisfiedOnTheSameCheckCompletes() {
        RetentionWindow window = new RetentionWindow();
        assertTrue(check(window, team, new boolean[]{true, true}, 100L, WINDOW));
    }

    /**
     * The relaxation the user asked for: two sub-tasks driven by different
     * sources do not have to hold on the same tick.
     */
    @Test
    void holdsWithinTheWindowRetainEachOther() {
        RetentionWindow window = new RetentionWindow();
        // Only A holds at t=100.
        assertFalse(check(window, team, new boolean[]{true, false}, 100L, WINDOW));
        // A has gone quiet, B holds at t=110 — A's hold at t=100 is still fresh.
        assertTrue(check(window, team, new boolean[]{false, true}, 110L, WINDOW));
    }

    /**
     * The case the user explicitly wanted excluded: A held long ago, stopped
     * holding, and only then did B hold.
     */
    @Test
    void aHoldThatExpiredBeforeBHoldsDoesNotComplete() {
        RetentionWindow window = new RetentionWindow();
        assertFalse(check(window, team, new boolean[]{true, false}, 100L, WINDOW));
        // Silence for longer than the window.
        assertFalse(check(window, team, new boolean[]{false, false}, 121L, WINDOW));
        // B finally holds, but A's hold is 40 ticks stale.
        assertFalse(check(window, team, new boolean[]{false, true}, 140L, WINDOW));
    }

    @Test
    void windowBoundaryIsInclusive() {
        RetentionWindow window = new RetentionWindow();
        assertFalse(check(window, team, new boolean[]{true, false}, 100L, WINDOW));
        assertTrue(check(window, team, new boolean[]{false, true}, 120L, WINDOW));

        RetentionWindow next = new RetentionWindow();
        assertFalse(check(next, team, new boolean[]{true, false}, 100L, WINDOW));
        assertFalse(check(next, team, new boolean[]{false, true}, 121L, WINDOW));
    }

    @Test
    void windowLengthIsConfigurable() {
        RetentionWindow window = new RetentionWindow();
        assertFalse(check(window, team, new boolean[]{true, false}, 100L, 1));
        assertFalse(check(window, team, new boolean[]{false, true}, 102L, 1));

        RetentionWindow wide = new RetentionWindow();
        assertFalse(check(wide, team, new boolean[]{true, false}, 100L, 200));
        assertTrue(check(wide, team, new boolean[]{false, true}, 300L, 200));
    }

    /**
     * Regression for the early-return bug: bailing out of the loop at the first
     * failing slot would skip B's refresh at t=5, so B's hold would be lost
     * even though it is well inside the window when A finally holds.
     */
    @Test
    void everySlotIsRefreshedEvenWhenAnEarlierSlotFails() {
        RetentionWindow window = new RetentionWindow();
        assertFalse(check(window, team, new boolean[]{false, true}, 5L, WINDOW));
        assertTrue(check(window, team, new boolean[]{true, false}, 10L, WINDOW));
    }

    /**
     * A fresh world's gameTime starts at 0, and the tick handler fires on
     * {@code gameTime % period == 0}. A sentinel of 0 for "never held" would
     * make a hold at tick 0 invisible.
     */
    @Test
    void aHoldAtTickZeroCounts() {
        RetentionWindow window = new RetentionWindow();
        assertTrue(check(window, team, new boolean[]{true, true}, 0L, WINDOW));
    }

    @Test
    void aHoldAtTickZeroStillExpires() {
        RetentionWindow window = new RetentionWindow();
        assertFalse(check(window, team, new boolean[]{true, false}, 0L, WINDOW));
        assertFalse(check(window, team, new boolean[]{false, true}, 21L, WINDOW));
    }

    @Test
    void teamsDoNotShareFreshness() {
        RetentionWindow window = new RetentionWindow();
        UUID other = UUID.randomUUID();

        assertFalse(check(window, team, new boolean[]{true, false}, 100L, WINDOW));
        // The other team has never had either slot hold.
        assertFalse(check(window, other, new boolean[]{false, true}, 105L, WINDOW));
        // The first team's retained hold is untouched.
        assertTrue(check(window, team, new boolean[]{false, true}, 105L, WINDOW));
    }

    @Test
    void changingTheSlotCountDropsStaleIndexes() {
        RetentionWindow window = new RetentionWindow();
        assertTrue(check(window, team, new boolean[]{true, true}, 100L, WINDOW));
        assertEquals(100L, window.lastSatisfiedAt(team, 1));

        // A sub-task was added, so the state array is rebuilt: the new index 2
        // starts empty and must not inherit anything.
        assertFalse(check(window, team, new boolean[]{true, true, false}, 105L, WINDOW));
        assertEquals(-1L, window.lastSatisfiedAt(team, 2));
    }

    @Test
    void clearForgetsEveryHold() {
        RetentionWindow window = new RetentionWindow();
        assertFalse(check(window, team, new boolean[]{true, false}, 100L, WINDOW));
        window.clear();
        assertFalse(check(window, team, new boolean[]{false, true}, 105L, WINDOW));
    }

    @Test
    void aHeldSlotStaysFreshWhileItKeepsHolding() {
        RetentionWindow window = new RetentionWindow();
        for (long now = 100L; now <= 1_000L; now += 10L) {
            assertTrue(check(window, team, new boolean[]{true, true}, now, WINDOW),
                    "should still hold at t=" + now);
        }
    }
}
