package dev.questjs.ftbquestskubejs.composite;

import de.marhali.json5.Json5Object;
import dev.ftb.mods.ftblibrary.client.config.editable.EditableInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The window's unit, which three different key names have held across rewrites —
 * and not all of them the same unit.
 *
 * <p>This is the part of the rewrite with the most ways to be quietly wrong: a save
 * file read under the wrong assumption runs twenty times fast or twenty times slow,
 * and nothing about the quest looks broken, the challenge is just unplayable. The
 * editor shows whatever the read produced, so an author can even "fix" it by
 * re-entering a number and persist the mistake.
 *
 * <p>{@link Json5Object} is a plain data object with no game bootstrap behind it, so
 * unlike the reference rules this behaviour is testable directly rather than only in
 * play.
 *
 * @see ExternalTaskChallengeTest for the clock once the window is known
 */
class ExternalTaskTimeLimitTest {

	private static Json5Object json(String key, int value) {
		Json5Object object = new Json5Object();
		object.addProperty(key, value);
		return object;
	}

	@Test
	void aFileCarryingNoTimeKeyAtAllGetsTheDefault() {
		assertEquals(ExternalTaskTask.DEFAULT_TIME_LIMIT_TICKS,
				ExternalTaskTask.resolveTimeLimitTicks(new Json5Object()));
	}

	@Nested
	@DisplayName("each key name in its own unit")
	class PerKeyUnit {

		@Test
		void theCurrentKeyIsTicks() {
			assertEquals(200, ExternalTaskTask.resolveTimeLimitTicks(
					json("time_limit_ticks", 200)));
		}

		@Test
		void theIntermediateKeyWasSecondsAndIsScaled() {
			// 100 of those seconds the editor used to offer is 2000 ticks, not 100.
			assertEquals(2_000, ExternalTaskTask.resolveTimeLimitTicks(
					json("time_limit_seconds", 100)));
		}

		@Test
		void theOldestKeyWasAlreadyTicks() {
			assertEquals(300, ExternalTaskTask.resolveTimeLimitTicks(
					json("window_ticks", 300)));
		}
	}

	@Test
	void theCurrentKeyWinsWhenAnOlderOneSurvivesAlongsideIt() {
		Json5Object object = json("time_limit_ticks", 400);
		object.addProperty("time_limit_seconds", 100);
		object.addProperty("window_ticks", 300);

		assertEquals(400, ExternalTaskTask.resolveTimeLimitTicks(object));
	}

	@Nested
	@DisplayName("bounds")
	class Bounds {

		@Test
		void aWindowShorterThanASecondIsRaised() {
			assertEquals(ExternalTaskTask.MIN_TIME_LIMIT_TICKS,
					ExternalTaskTask.resolveTimeLimitTicks(json("time_limit_ticks", 5)));
		}

		@Test
		void aWindowLongerThanADayIsLowered() {
			assertEquals(ExternalTaskTask.MAX_TIME_LIMIT_TICKS,
					ExternalTaskTask.resolveTimeLimitTicks(json("time_limit_ticks", 99_999_999)));
		}

		@Test
		void scalingASecondsKeyCannotSlipPastTheCeiling() {
			// The multiply happens before the clamp on purpose: 1e9 seconds is not a
			// day, and reading it as 1e9 ticks would be worse than clamping it.
			assertEquals(ExternalTaskTask.MAX_TIME_LIMIT_TICKS,
					ExternalTaskTask.resolveTimeLimitTicks(json("time_limit_seconds", 1_000_000_000)));
		}
	}

	@Nested
	@DisplayName("the editor field accepts a number digit by digit")
	class EditorTyping {

		// What EditableInt.parse accepts decides what the field will commit, not just
		// what fillConfigGroup could save. On 2001.x the same predicate also gated
		// every keystroke (TextBox.insertText dropped text parse refused), so a floor
		// above 9 made an emptied field impossible to type into at all — the first
		// digit of every legal value is itself below the floor. 26.1.2's TextBox
		// tracks validity instead of refusing the edit (strictValidity defaults to
		// false), so the keystroke symptom is gone on its own; the floor of 1 stays
		// because the real floor belongs to clampTicks, and these tests pin the
		// predicate that still decides what reaches the quest file.
		@Test
		void aFloorOfTwentyRefusesTheFirstDigitOfEveryLegalValue() {
			EditableInt broken = new EditableInt(ExternalTaskTask.MIN_TIME_LIMIT_TICKS,
					ExternalTaskTask.MAX_TIME_LIMIT_TICKS);
			assertFalse(broken.parse(null, "2"), "typing the \"2\" of 200");
			assertFalse(broken.parse(null, "1"), "typing the \"1\" of 1200");
			assertFalse(broken.parse(null, "5"), "typing the \"5\" of 500");
		}

		@Test
		void aFloorOfOneLetsTheSameKeystrokesThrough() {
			EditableInt field = new EditableInt(1, ExternalTaskTask.MAX_TIME_LIMIT_TICKS);
			for (String typed : new String[]{"2", "20", "200", "1", "12", "120", "1200"}) {
				assertTrue(field.parse(null, typed), "typing \"" + typed + "\" incrementally");
			}
		}

		@Test
		void anEmptyFieldIsStillAcceptedSoTheAuthorCanClearBeforeRetyping() {
			EditableInt field = new EditableInt(1, ExternalTaskTask.MAX_TIME_LIMIT_TICKS);
			assertTrue(field.parse(null, ""));
		}

		@Test
		void theCeilingStillRefusesOvershootSoItCannotBeTypedPastADay() {
			EditableInt field = new EditableInt(1, ExternalTaskTask.MAX_TIME_LIMIT_TICKS);
			assertFalse(field.parse(null, "2000000"));
		}
	}
}
