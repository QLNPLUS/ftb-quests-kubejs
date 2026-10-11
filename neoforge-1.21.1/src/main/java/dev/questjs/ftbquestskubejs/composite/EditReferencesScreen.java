package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import dev.ftb.mods.ftblibrary.ui.misc.SimpleToast;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.quest.Quest;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The editor for one {@link ExternalTaskTask}'s references, shaped like FTB's own
 * 依赖关系 panel: the current entries are listed, and a {@code +} row adds another.
 * The entries are quests — FTB's 任务, the box with rewards — not the 目标 inside them.
 *
 * <p>Clicking a listed reference removes it. That is the inverse of FTB's
 * dependency panel, which removes through the connection line rather than through
 * the list — but a reference has no other affordance, and the row's tooltip says so.
 *
 * <p>Every change is applied to the task immediately, and {@code onChanged} tells the
 * task editor that something changed. Nothing is staged behind 确认: an
 * accept/cancel pair over a list that mutates as you click it could only either lie
 * about what already happened, or undo it, and neither is what the user expects
 * from a panel that looks like FTB's.
 *
 * <p>Rows are {@link SimpleTextButton}, not {@code SimpleButton}. FTB's
 * {@code Button.draw()} only calls {@code drawBackground} and {@code drawIcon} — the
 * title of a plain {@code SimpleButton} is never rendered, it only reaches the screen
 * through {@code addMouseOverText}. That is why an earlier version of this picker
 * showed a column of bare icons with the label hiding in the tooltip.
 *
 * @see SelectReferenceScreen the picker that the {@code +} row opens
 */
@OnlyIn(Dist.CLIENT)
final class EditReferencesScreen extends AbstractButtonListScreen {
	private final ExternalTaskTask task;
	private final Runnable onChanged;

	EditReferencesScreen(ExternalTaskTask task, Runnable onChanged) {
		this.task = task;
		this.onChanged = onChanged;
		showCloseButton(true);
		setHasSearchBox(true);
		setTitle(Component.translatable("ftbquestskubejs.ui.reference_editor_title"));
	}

	@Override
	public void addButtons(Panel panel) {
		panel.add(new AddRow(panel));
		for (Quest reference : task.references()) {
			panel.add(new ReferenceRow(panel, reference));
		}
	}

	/**
	 * Search matches the id as well as the label. The label deliberately leaves the
	 * id out, because a 19-digit prefix is unreadable in a list, but the id is the
	 * handle that works in a KubeJS script and in {@code /ftbquests}, so pasting one
	 * into the box has to find the quest.
	 */
	@Override
	public String getFilterText(Widget widget) {
		if (widget instanceof ReferenceRow row) {
			return (super.getFilterText(widget) + " " + row.reference.getId()).toLowerCase();
		}
		return super.getFilterText(widget);
	}

	@Override
	protected void doCancel() {
		closeGui(true);
	}

	@Override
	protected void doAccept() {
		closeGui(true);
	}

	/** The {@code +} row: opens the picker for a quest that is not referenced yet. */
	private final class AddRow extends SimpleTextButton {
		AddRow(Panel panel) {
			super(panel, Component.translatable("ftbquestskubejs.ui.reference_add"), Icon.empty());
			setHeight(18);
		}

		@Override
		public void onClicked(MouseButton mouseButton) {
			playClickSound();
			if (task.getReferencedQuestIds().size() >= ExternalTaskTask.MAX_REFERENCES) {
				SimpleToast.info(getTitle(), Component.translatable(
						"ftbquestskubejs.ui.reference_full", ExternalTaskTask.MAX_REFERENCES));
				return;
			}
			if (task.candidateQuests().isEmpty()) {
				SimpleToast.info(getTitle(),
						Component.translatable("ftbquestskubejs.config.reference_none_available"));
				return;
			}
			new SelectReferenceScreen(task, this::addPicked).openGui();
		}

		/**
		 * Applies the pick, then rebuilds this list so the new row is there when the
		 * picker closes back onto it. Rebuilding is explicit rather than left to
		 * {@code closeGui(true)}, because the widget list is only ever produced by
		 * {@link #addButtons}.
		 */
		private void addPicked(Quest picked) {
			if (task.addReference(picked)) {
				onChanged.run();
			}
			refreshWidgets();
		}
	}

	/** One current reference. Clicking it drops the reference. */
	private final class ReferenceRow extends SimpleTextButton {
		private final Quest reference;

		ReferenceRow(Panel panel, Quest reference) {
			super(panel, Component.literal(ExternalTaskTask.referenceLabel(reference)),
					reference.getIcon());
			this.reference = reference;
			setHeight(18);
		}

		@Override
		public void addMouseOverText(TooltipList list) {
			super.addMouseOverText(list);
			list.add(Component.translatable("ftbquestskubejs.ui.reference_remove"));
			list.add(Component.translatable("ftbquestskubejs.ui.reference_id", reference.getId()));
		}

		@Override
		public void onClicked(MouseButton mouseButton) {
			playClickSound();
			if (task.removeReference(reference)) {
				onChanged.run();
			}
			refreshWidgets();
		}
	}
}
