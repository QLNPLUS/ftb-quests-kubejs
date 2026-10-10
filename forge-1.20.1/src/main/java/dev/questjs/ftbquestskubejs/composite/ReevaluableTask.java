package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftbquests.quest.TeamData;
import net.minecraft.server.level.ServerPlayer;

/**
 * A task that can answer "is this condition satisfied <i>right now</i>?"
 * without writing any progress, consuming any resource, or otherwise mutating
 * team state.
 *
 * <p>FTB Quests exposes no such predicate on {@code Task}. Its only check
 * entry point, {@code submitTask}, writes progress, and completion is a
 * one-way latch: once {@code TeamData.isCompleted(task)} turns true, the tick
 * handler skips the task forever. A composite task therefore cannot treat
 * {@code submitTask} as a condition test — doing so would latch every
 * sub-task it ever evaluated, which is exactly the "satisfied earlier, still
 * counts later" behaviour composite tasks exist to rule out.
 *
 * @see CompositeTask
 * @see ReevaluableTaskRegistry
 */
public interface ReevaluableTask {
    /**
     * Evaluates the condition against the player's current state.
     *
     * <p>Implementations must be side-effect free: in particular they must not
     * call {@code TeamData.setProgress}, consume inventory, or grant anything.
     *
     * @param teamData the team whose state is being evaluated
     * @param player   the player currently being ticked
     * @return true when the condition holds at this instant
     */
    boolean isSatisfiedNow(TeamData teamData, ServerPlayer player);
}
