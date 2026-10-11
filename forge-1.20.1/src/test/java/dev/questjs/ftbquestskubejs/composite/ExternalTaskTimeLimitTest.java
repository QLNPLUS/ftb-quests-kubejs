package dev.questjs.ftbquestskubejs.composite;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
