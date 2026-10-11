package dev.questjs.ftbquestskubejs.composite;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the list rules behind {@link ExternalTaskTask}'s references: unique, dense,
 * capped.
 *
 * <p>Uniqueness is what the new editor needs and the old slot-per-row panel did not.
 * The reference list is now the challenge's definition — every entry is a quest that
 * has to be finished inside the window — so a duplicate would mean the same quest
 * counted twice toward "all done", and removing it would leave a second copy the
 * author can no longer see. {@link ExternalTaskTask#addReferenceToList} refuses the
 * duplicate, and {@link EditReferencesScreen} never offers one, because
 * {@link ExternalTaskTask#candidateQuests()} filters referenced ids out.
 *
 * <p>These cover the mutation the screens commit; the screens themselves need a real
 * client.
 *
 * @see ExternalTaskChallengeTest for what the same list drives at runtime
 */
class ExternalTaskReferenceListTest {

	private final List<Long> ids = new ArrayList<>();

	private boolean add(long id) {
		return ExternalTaskTask.addReferenceToList(ids, id, ExternalTaskTask.MAX_REFERENCES);
	}

	@Test
	void addAppends() {
		assertTrue(add(10L));
		assertTrue(add(20L));
		assertEquals(Arrays.asList(10L, 20L), ids);
	}

	@Test
	void addingTheSameQuestTwiceChangesNothing() {
		assertTrue(add(10L));
		assertFalse(add(10L));
		assertEquals(1, ids.size());
	}

	@Test
	void removeDropsTheEntryAndShiftsTheRest() {
		add(10L);
		add(20L);
		add(30L);
		assertTrue(ExternalTaskTask.removeReferenceFromList(ids, 20L));
		assertEquals(Arrays.asList(10L, 30L), ids);
	}

	@Test
	void removingTheOnlyEntryEmptiesTheList() {
		add(10L);
		assertTrue(ExternalTaskTask.removeReferenceFromList(ids, 10L));
		assertTrue(ids.isEmpty());
	}

	@Test
	void removingAnAbsentEntryReportsNoChange() {
		add(10L);
		assertFalse(ExternalTaskTask.removeReferenceFromList(ids, 99L));
		assertEquals(Arrays.asList(10L), ids);
	}

	@Test
	void removeThenAddAgainWorks() {
		add(10L);
		ExternalTaskTask.removeReferenceFromList(ids, 10L);
		assertTrue(add(10L));
		assertEquals(Arrays.asList(10L), ids);
	}

	@Test
	void referencesStopAtTheCeiling() {
		for (int i = 0; i < ExternalTaskTask.MAX_REFERENCES; i++) {
			assertTrue(add(i + 1));
		}
		assertFalse(add(999L));
		assertEquals(ExternalTaskTask.MAX_REFERENCES, ids.size());
	}

	@Test
	void removingOneMakesRoomForAnother() {
		for (int i = 0; i < ExternalTaskTask.MAX_REFERENCES; i++) {
			add(i + 1);
		}
		assertTrue(ExternalTaskTask.removeReferenceFromList(ids, 4L));
		assertTrue(add(42L));
		assertEquals(ExternalTaskTask.MAX_REFERENCES, ids.size());
		for (Long id : ids) {
			assertTrue(id > 0L, "no gap may survive a removal: " + ids);
		}
	}
}
