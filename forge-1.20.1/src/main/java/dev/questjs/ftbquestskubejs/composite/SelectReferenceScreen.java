package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The picker the {@code +} row of {@link EditReferencesScreen} opens: every quest
 * {@link ExternalTaskTask#canReference} permits and that isn't referenced already,
 * which {@link ExternalTaskTask#candidateQuests()} assembles.
 *
 * <p>Choosing is immediate — the row commits the reference and closes the picker —
 * rather than staged behind an accept button, so there is no way to leave this
 * screen having "selected" something that never got applied.
 *
 * <p>Rows are {@link SimpleTextButton} so the label is actually drawn; see
 * {@link EditReferencesScreen} for why {@code SimpleButton} is not enough.
 */
@OnlyIn(Dist.CLIENT)
final class SelectReferenceScreen extends AbstractButtonListScreen {
	private final ExternalTaskTask task;
	private final java.util.function.Consumer<Quest> onPicked;

	SelectReferenceScreen(ExternalTaskTask task, java.util.function.Consumer<Quest> onPicked) {
		this.task = task;
		this.onPicked = onPicked;
		showCloseButton(true);
		setHasSearchBox(true);
		setTitle(Component.translatable("ftbquestskubejs.ui.reference_pick_title"));
	}

	@Override
	public void addButtons(Panel panel) {
		for (Quest candidate : task.candidateQuests()) {
			panel.add(new CandidateRow(panel, candidate));
		}
	}

	/** So the search box matches on what a row actually shows rather than on a class name. */
	@Override
	public String getFilterText(Widget widget) {
		if (widget instanceof CandidateRow row) {
			return ExternalTaskTask.referenceLabel(row.candidate);
		}
		return super.getFilterText(widget);
	}

	/**
	 * The base screen has accept and cancel buttons, but this picker commits per row —
	 * there is nothing staged to accept, and nothing to undo on cancel — so both just
	 * close it.
	 */
	@Override
	protected void doCancel() {
		closeGui(true);
	}

	@Override
	protected void doAccept() {
		closeGui(true);
	}

	private final class CandidateRow extends SimpleTextButton {
		private final Quest candidate;

		CandidateRow(Panel panel, Quest candidate) {
			super(panel, Component.literal(ExternalTaskTask.referenceLabel(candidate)),
					candidate.getIcon());
			this.candidate = candidate;
			setHeight(18);
		}

		@Override
		public void addMouseOverText(TooltipList list) {
			super.addMouseOverText(list);
			list.add(Component.translatable("ftbquestskubejs.ui.reference_id", candidate.getId()));
			list.add(Component.translatable("ftbquestskubejs.ui.reference_goals", candidate.getTasks().size()));
		}

		@Override
		public void onClicked(MouseButton mouseButton) {
			playClickSound();
			onPicked.accept(candidate);
			closeGui(true);
		}
	}
}
