package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.task.AbstractBooleanTask;
import dev.ftb.mods.ftbquests.quest.task.CheckmarkTask;
import dev.ftb.mods.ftbquests.quest.task.DimensionTask;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.ftb.mods.ftbquests.quest.task.ObservationTask;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the composite task's sub-task picker.
 *
 * <p>Two things used to break here, and both are still worth pinning down.
 *
 * <p>The picker used to be an {@code addEnum} whose "empty slot" option was the
 * empty string. {@code NameMap.getNullable} reports a blank id (and any id
 * starting with {@code '-'}) as absent, so {@code NameMap} could not resolve its
 * own default and left it null; {@code EnumConfig} copied that null, and
 * hovering the field made {@code ConfigValue.addInfo} render it — which
 * dereferenced null and crashed the config screen. The picker is now a dropdown
 * over {@code ConfigValue<Task>}, but the same hazard survives in a different
 * place: an empty slot's value really is null, and {@code addInfo} still renders
 * the configured default, which is null too. So the null path is still the one
 * that must not throw.
 *
 * <p>Second, the set of task types offered is now decided by inheritance rather
 * than by an allow-list this mod maintains, which makes the deny-list
 * load-bearing: a type whose {@code canSubmit} is constant-true would complete a
 * composite task the instant it loaded.
 */
class CompositeTaskEditorTest {

    /**
     * Stands in for a task type registered by another mod: a subclass of
     * {@code AbstractBooleanTask} that appears nowhere in the registry. It is
     * never instantiated — the registry decides membership from the class alone.
     */
    private static final class ThirdPartyBooleanTask extends AbstractBooleanTask {
        private ThirdPartyBooleanTask(long id, Quest quest) {
            super(id, quest);
        }

        @Override
        public TaskType getType() {
            return null;
        }

        @Override
        public boolean canSubmit(TeamData teamData, ServerPlayer player) {
            return true;
        }
    }

    /**
     * The regression, in its current form: an empty slot renders through a null
     * task, and the value's default is null as well.
     */
    @Test
    void emptySlotLabelDoesNotThrow() {
        Component label = assertDoesNotThrow(() -> SubTaskTypeConfig.labelFor(null));
        assertNotNull(label);
        // With no locale installed in a unit-test JVM, a translatable component
        // falls back to exposing its key.
        assertEquals("ftbquestskubejs.config.sub_task_none", label.getString());
    }

    /**
     * Constant-true {@code canSubmit} types must stay out. This is not a nicety:
     * admitting {@code checkmark} would make every composite containing one
     * complete on load.
     */
    @Test
    void constantTrueTypesAreDenied() {
        assertFalse(ReevaluableTaskRegistry.isSupportedClass(CheckmarkTask.class),
                "CheckmarkTask.canSubmit is a constant true");
        assertFalse(ReevaluableTaskRegistry.isSupportedClass(ObservationTask.class),
                "ObservationTask.canSubmit is a constant true");
    }

    /** A composite holding a composite would recurse. */
    @Test
    void compositeItselfIsDenied() {
        assertFalse(ReevaluableTaskRegistry.isSupportedClass(CompositeTask.class));
    }

    /**
     * The point of moving off an allow-list: any boolean task that reads live
     * state qualifies, including types this mod has never heard of.
     */
    @Test
    void unknownBooleanTaskSubclassesAreAdmitted() {
        assertTrue(ReevaluableTaskRegistry.isSupportedClass(DimensionTask.class));
        assertTrue(ReevaluableTaskRegistry.isSupportedClass(ThirdPartyBooleanTask.class),
                "membership follows the AbstractBooleanTask contract, not a list we maintain");
    }

    /**
     * Progress-based types fall out by construction: they are not boolean tasks,
     * and bytecode shows they expose no side-effect-free "satisfied right now"
     * predicate — {@code ItemTask.test} matches an {@code ItemStack} against a
     * matcher rather than reading the player.
     */
    @Test
    void progressBasedTypesAreNotAdmitted() {
        assertFalse(ReevaluableTaskRegistry.isSupportedClass(ItemTask.class));
    }
}
