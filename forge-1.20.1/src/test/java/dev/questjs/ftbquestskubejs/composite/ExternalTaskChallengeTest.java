package dev.questjs.ftbquestskubejs.composite;

import dev.questjs.ftbquestskubejs.composite.ExternalTaskTask.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The timed challenge itself: the clock starts on the first reference completed,
 * and running out resets everything.
 *
 * <p>Tested through {@link ExternalTaskTask#decide} rather than through
 * {@code submitTask}, because the whole rule is a function of five numbers, and
 * standing up a {@code TeamData}, a {@code ServerPlayer} and a quest file to read
 * those numbers would test the harness rather than the rule.
 *
 * <p>The two edges worth pinning are the ones a reader cannot infer from the prose:
 * the deadline is inclusive ({@code >=}, so a window of 200 ticks ends on tick
 * {@code start + 200}, not {@code start + 201}), and completion is checked before
 * the deadline, so a last reference landing exactly on the boundary counts as a
 * success rather than a reset.
 *
 * @see ExternalTaskReferenceListTest for the list the counts are taken over
 */
class ExternalTaskChallengeTest {

	private static final long LIMIT = 200L;

	@Test
	void nothingDoneLeavesTheClockStopped() {
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 3, false, 0L, 1_000L, LIMIT));
	}

	@Test
	void theFirstCompletionStartsTheClock() {
		assertEquals(Action.START, ExternalTaskTask.decide(1, 3, false, 0L, 1_000L, LIMIT));
	}

	@Test
	void referencesAlreadyDoneBeforeTheAttemptStillStartIt() {
		// A player who finished some of these before ever reaching the quest is not
		// granted a free pass: the window opens on the first poll that sees progress.
		assertEquals(Action.START, ExternalTaskTask.decide(2, 3, false, 0L, 1_000L, LIMIT));
	}

	@Test
	void withinTheWindowNothingHappens() {
		assertEquals(Action.NONE, ExternalTaskTask.decide(1, 3, true, 1_000L, 1_199L, LIMIT));
	}

	@Test
	void theWindowEndsOnTheTickTheLimitElapses() {
		assertEquals(Action.RESET, ExternalTaskTask.decide(1, 3, true, 1_000L, 1_200L, LIMIT));
	}

	@Test
	void runningOutResetsEvenWhenMostReferencesAreDone() {
		assertEquals(Action.RESET, ExternalTaskTask.decide(2, 3, true, 1_000L, 5_000L, LIMIT));
	}

	@Test
	void allDoneCompletesWhileTheClockRuns() {
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(3, 3, true, 1_000L, 1_050L, LIMIT));
	}

	@Test
	void allDoneCompletesWithNoClockRunning() {
		// Every reference was already satisfied when the quest became available, so
		// there was never a partial state to time.
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(3, 3, false, 0L, 1_000L, LIMIT));
	}

	@Test
	void completionBeatsTheDeadlineOnTheSameTick() {
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(3, 3, true, 1_000L, 1_200L, LIMIT));
	}

	@Test
	void anEmptyReferenceListNeverActs() {
		// done == total == 0 must not read as "all done", or a task with nothing
		// configured would complete itself.
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 0, false, 0L, 1_000L, LIMIT));
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 0, true, 1_000L, 9_000L, LIMIT));
	}

	@Test
	void aResetLeavesTheAttemptIdleRatherThanRestartingItImmediately() {
		// The caller resets the references, so the next poll sees none done and no
		// clock: the player has to earn a completion again to open a new window.
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 3, false, 0L, 1_210L, LIMIT));
	}

	@Test
	void aSingleReferenceCompletesAsSoonAsItIsDone() {
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 1, false, 0L, 100L, LIMIT));
		// Completion is checked before the clock, so a one-reference challenge never
		// opens a window it could then fail: the poll that sees it done ends it.
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(1, 1, false, 0L, 100L, LIMIT));
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(1, 1, true, 100L, 9_999L, LIMIT));
	}
}
