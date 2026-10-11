package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftblibrary.config.IntConfig;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The window's unit, which three different tag names have held across rewrites —
 * and not all of them the same unit.
 *
 * <p>This is the part of the rewrite with the most ways to be quietly wrong: a save
 * file read under the wrong assumption runs twenty times fast or twenty times slow,
 * and nothing about the quest looks broken, the challenge is just unplayable. The
 * editor shows whatever the read produced, so an author can even "fix" it by
 * re-entering a number and persist the mistake.
 *
 * <p>{@link CompoundTag} needs no game bootstrap, so unlike the reference rules this
 * behaviour is testable directly rather than only in play.
 *
 * @see ExternalTaskChallengeTest for the clock once the window is known
 */
class ExternalTaskTimeLimitTest {

	private static CompoundTag tag(String key, int value) {
		CompoundTag nbt = new CompoundTag();
		nbt.putInt(key, value);
		return nbt;
	}

	@Test
	void aFileCarryingNoTimeTagAtAllGetsTheDefault() {
		assertEquals(ExternalTaskTask.DEFAULT_TIME_LIMIT_TICKS,
				ExternalTaskTask.resolveTimeLimitTicks(new CompoundTag()));
	}

	@Nested
	@DisplayName("each tag name in its own unit")
	class PerTagUnit {

		@Test
		void theCurrentTagIsTicks() {
			assertEquals(200, ExternalTaskTask.resolveTimeLimitTicks(
					tag("time_limit_ticks", 200)));
		}

		@Test
		void theIntermediateTagWasSecondsAndIsScaled() {
			// 100 of those seconds the editor used to offer is 2000 ticks, not 100.
			assertEquals(2_000, ExternalTaskTask.resolveTimeLimitTicks(
					tag("time_limit_seconds", 100)));
		}

		@Test
		void theOldestTagWasAlreadyTicks() {
			assertEquals(300, ExternalTaskTask.resolveTimeLimitTicks(
					tag("window_ticks", 300)));
		}
	}

	@Test
	void theCurrentTagWinsWhenAnOlderOneSurvivesAlongsideIt() {
		CompoundTag nbt = tag("time_limit_ticks", 400);
		nbt.putInt("time_limit_seconds", 100);
		nbt.putInt("window_ticks", 300);

		assertEquals(400, ExternalTaskTask.resolveTimeLimitTicks(nbt));
	}

	@Nested
	@DisplayName("bounds")
	class Bounds {

		@Test
		void aWindowShorterThanASecondIsRaised() {
			assertEquals(ExternalTaskTask.MIN_TIME_LIMIT_TICKS,
					ExternalTaskTask.resolveTimeLimitTicks(tag("time_limit_ticks", 5)));
		}

		@Test
		void aWindowLongerThanADayIsLowered() {
			assertEquals(ExternalTaskTask.MAX_TIME_LIMIT_TICKS,
					ExternalTaskTask.resolveTimeLimitTicks(tag("time_limit_ticks", 99_999_999)));
		}

		@Test
		void scalingASecondsTagCannotSlipPastTheCeiling() {
			// The multiply happens before the clamp on purpose: 1e9 seconds is not a
			// day, and reading it as 1e9 ticks would be worse than clamping it.
			assertEquals(ExternalTaskTask.MAX_TIME_LIMIT_TICKS,
					ExternalTaskTask.resolveTimeLimitTicks(tag("time_limit_seconds", 1_000_000_000)));
		}
	}

	@Nested
	@DisplayName("the editor accepts a number digit by digit")
	class EditorTyping {

		// FTB's config field validates every keystroke: TextBox.insertText keeps the
		// new text only when IntConfig.parse accepts it, and parse rejects anything
		// outside its range. So the range the field is built with decides what can be
		// typed, not just what can be saved — a floor above 9 makes an emptied field
		// impossible to type into, because the first digit of every allowed value is
		// itself below the floor. This is why fillConfigGroup passes a floor of 1 and
		// leaves MIN_TIME_LIMIT_TICKS to clampTicks.
		@Test
		void aFloorOfTwentyRefusesTheFirstDigitOfEveryLegalValue() {
			IntConfig broken = new IntConfig(ExternalTaskTask.MIN_TIME_LIMIT_TICKS,
					ExternalTaskTask.MAX_TIME_LIMIT_TICKS);
			assertFalse(broken.parse(null, "2"), "typing the \"2\" of 200");
			assertFalse(broken.parse(null, "1"), "typing the \"1\" of 1200");
			assertFalse(broken.parse(null, "5"), "typing the \"5\" of 500");
		}

		@Test
		void aFloorOfOneLetsTheSameKeystrokesThrough() {
			IntConfig field = new IntConfig(1, ExternalTaskTask.MAX_TIME_LIMIT_TICKS);
			for (String typed : new String[]{"2", "20", "200", "1", "12", "120", "1200"}) {
				assertTrue(field.parse(null, typed), "typing \"" + typed + "\" incrementally");
			}
		}

		@Test
		void anEmptyFieldIsStillAcceptedSoTheAuthorCanClearBeforeRetyping() {
			IntConfig field = new IntConfig(1, ExternalTaskTask.MAX_TIME_LIMIT_TICKS);
			assertTrue(field.parse(null, ""));
		}

		@Test
		void theCeilingStillRefusesOvershootSoItCannotBeTypedPastADay() {
			IntConfig field = new IntConfig(1, ExternalTaskTask.MAX_TIME_LIMIT_TICKS);
			assertFalse(field.parse(null, "2000000"));
		}
	}
}
