package dev.questjs.ftbquestskubejs.composite;

import dev.questjs.ftbquestskubejs.composite.ExternalTaskTask.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The timed challenge itself: the clock starts on the first reference completed,
 * and running out resets everything.
 *
 * <p>Tested through {@link ExternalTaskTask#decide} rather than through
 * {@code submitTask}, because the whole rule is a function of a few numbers, and
 * standing up a {@code TeamData}, a {@code ServerPlayer} and a quest file to read
 * those numbers would test the harness rather than the rule. {@link Attempt} below
 * closes part of that gap by replaying how the caller carries the clock between
 * polls, which is where the "resets instantly at any limit" bug actually lived.
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

	/**
	 * Replays the caller's side of the rule: the clock is a nullable tick held across
	 * polls, and each action updates it. A stopped clock is {@code null}, not
	 * {@code 0} — the reported bug was passing "no clock" as "clock started at tick 0",
	 * which made every poll look overdue once the world was a million ticks old.
	 */
	private static final class Attempt {
		Long start;

		Action poll(int done, int total, long now, long limit) {
			Action action = ExternalTaskTask.decide(done, total, start, now, limit);
			switch (action) {
				case START -> start = now;
				case COMPLETE, RESET -> start = null;
				default -> {
				}
			}
			return action;
		}
	}

	@Test
	void nothingDoneLeavesTheClockStopped() {
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 3, null, 1_000L, LIMIT));
	}

	@Test
	void theFirstCompletionStartsTheClock() {
		assertEquals(Action.START, ExternalTaskTask.decide(1, 3, null, 1_000L, LIMIT));
	}

	@Test
	void referencesAlreadyDoneBeforeTheAttemptStillStartIt() {
		// A player who finished some of these before ever reaching the quest is not
		// granted a free pass: the window opens on the first poll that sees progress.
		assertEquals(Action.START, ExternalTaskTask.decide(2, 3, null, 1_000L, LIMIT));
	}

	@Test
	void withinTheWindowNothingHappens() {
		assertEquals(Action.NONE, ExternalTaskTask.decide(1, 3, 1_000L, 1_199L, LIMIT));
	}

	@Test
	void theWindowEndsOnTheTickTheLimitElapses() {
		assertEquals(Action.RESET, ExternalTaskTask.decide(1, 3, 1_000L, 1_200L, LIMIT));
	}

	@Test
	void runningOutResetsEvenWhenMostReferencesAreDone() {
		assertEquals(Action.RESET, ExternalTaskTask.decide(2, 3, 1_000L, 5_000L, LIMIT));
	}

	@Test
	void allDoneCompletesWhileTheClockRuns() {
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(3, 3, 1_000L, 1_050L, LIMIT));
	}

	@Test
	void allDoneCompletesWithNoClockRunning() {
		// Every reference was already satisfied when the quest became available, so
		// there was never a partial state to time.
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(3, 3, null, 1_000L, LIMIT));
	}

	@Test
	void completionBeatsTheDeadlineOnTheSameTick() {
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(3, 3, 1_000L, 1_200L, LIMIT));
	}

	@Test
	void anEmptyReferenceListNeverActs() {
		// done == total == 0 must not read as "all done", or a task with nothing
		// configured would complete itself.
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 0, null, 1_000L, LIMIT));
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 0, 1_000L, 9_000L, LIMIT));
	}

	@Test
	void aResetLeavesTheAttemptIdleRatherThanRestartingItImmediately() {
		// The caller resets the references, so the next poll sees none done and no
		// clock: the player has to earn a completion again to open a new window.
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 3, null, 1_210L, LIMIT));
	}

	@Test
	void aSingleReferenceCompletesAsSoonAsItIsDone() {
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 1, null, 100L, LIMIT));
		// Completion is checked before the clock, so a one-reference challenge never
		// opens a window it could then fail: the poll that sees it done ends it.
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(1, 1, null, 100L, LIMIT));
		assertEquals(Action.COMPLETE, ExternalTaskTask.decide(1, 1, 100L, 9_999L, LIMIT));
	}

	@Test
	void aStoppedClockIsNeverOverdueHoweverOldTheWorldIs() {
		// The shape of the live report: a world 1.5 million ticks in, a 40-tick
		// window, nothing done. Treated as "started at tick 0" this reads as overdue
		// and resets on every single poll; treated as "no clock" it does nothing.
		long now = 1_498_170L;
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 2, null, now, 40L));
		assertEquals(Action.NONE, ExternalTaskTask.decide(0, 2, null, now + 1_000_000L, 40L));
	}

	@Test
	void aWholeAttemptRunsItsLifecycleInOrder() {
		Attempt attempt = new Attempt();
		assertEquals(Action.NONE, attempt.poll(0, 2, 1_498_170L, 40L));
		assertEquals(Action.START, attempt.poll(1, 2, 1_498_180L, 40L));
		assertEquals(Action.NONE, attempt.poll(1, 2, 1_498_200L, 40L));
		assertEquals(Action.NONE, attempt.poll(1, 2, 1_498_219L, 40L));
		assertEquals(Action.RESET, attempt.poll(1, 2, 1_498_220L, 40L));
		// The references went back to undone with the reset, so the attempt is idle.
		assertEquals(Action.NONE, attempt.poll(0, 2, 1_498_230L, 40L));
	}

	@Test
	void finishingInsideTheWindowCompletesInsteadOfResetting() {
		Attempt attempt = new Attempt();
		assertEquals(Action.START, attempt.poll(1, 2, 1_498_180L, 40L));
		assertEquals(Action.COMPLETE, attempt.poll(2, 2, 1_498_219L, 40L));
		assertEquals(null, attempt.start);
	}
}
