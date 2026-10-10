package dev.questjs.ftbquestskubejs.composite;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * One sub-task slot in the composite task's editor, rendered as a dropdown.
 *
 * <p>This replaces an {@code addEnum} picker, which cycles one click per option
 * and gives no way to see the choices at a glance. Following FTB Quests' own
 * {@code ConfigQuestObject} idiom, a {@link ConfigValue} whose {@code onClicked}
 * opens {@code BaseScreen.openContextMenu} turns the field into a real menu that
 * lists every usable task type with its name and icon.
 *
 * <p>The value type is {@link Task} rather than a type id: the slot's identity is
 * the sub-task itself, and its label and icon come from the task's own type, so
 * no id round-trip is needed.
 */
@OnlyIn(Dist.CLIENT)
public final class SubTaskTypeConfig extends ConfigValue<Task> {

    private final CompositeTask owner;
    private final int slot;

    public SubTaskTypeConfig(CompositeTask owner, int slot) {
        this.owner = owner;
        this.slot = slot;
    }

    /**
     * The slot's label.
     *
     * <p>{@code task} is null for an empty slot, and {@code ConfigValue.addInfo}
     * also renders this value's <i>default</i>, which is null here. An unguarded
     * dereference of a null default is what previously crashed the config screen,
     * so the null case is handled explicitly rather than assumed unreachable.
     *
     * <p>Package-visible so the regression can be tested without a client.
     */
    static Component labelFor(Task task) {
        return task == null
                ? Component.translatable("ftbquestskubejs.config.sub_task_none")
                : task.getType().getDisplayName();
    }

    @Override
    public Component getStringForGUI(Task task) {
        return labelFor(task);
    }

    @Override
    public Icon getIcon(Task task) {
        return task == null ? Icon.empty() : task.getType().getIconSupplier();
    }

    @Override
    public void onClicked(Widget widget, MouseButton button, ConfigCallback callback) {
        if (!getCanEdit()) {
            return;
        }
        // Right-click is the quick path for the common "empty this slot" action.
        if (button.isRight()) {
            if (getValue() != null) {
                replace(null);
            }
            return;
        }

        List<ContextMenuItem> items = new ArrayList<>();
        items.add(ContextMenuItem.title(Component.translatable("ftbquestskubejs.config.sub_task_type")));

        List<TaskType> types = ReevaluableTaskRegistry.supportedTypes(owner.getQuest());
        for (TaskType type : types) {
            items.add(new ContextMenuItem(type.getDisplayName(), type.getIconSupplier(),
                    clicked -> replace(type)));
        }
        if (types.isEmpty()) {
            items.add(new ContextMenuItem(
                    Component.translatable("ftbquestskubejs.config.sub_task_no_types"),
                    Icon.empty(),
                    clicked -> { }));
        }

        if (getValue() != null) {
            items.add(ContextMenuItem.separator());
            items.add(new ContextMenuItem(
                    Component.translatable("ftbquestskubejs.config.sub_task_clear"),
                    Icon.empty(),
                    clicked -> replace(null)));
        }

        widget.getGui().openContextMenu(items);
    }

    /**
     * Installs a sub-task of {@code type} in this slot (or clears it when
     * {@code type} is null) and reopens the composite editor.
     *
     * <p>The reopen is not cosmetic. {@code EditConfigScreen} collects its entry
     * widgets once, in its constructor; {@code refreshWidgets()} only re-lays out
     * that cached list, so config values added to the group afterwards never
     * appear on screen. Choosing a type is precisely the moment the new
     * sub-task's own fields come into existence, so a fresh screen is the only
     * way to make them editable without the user closing and reopening the panel
     * by hand.
     *
     * <p>The open panel is saved before it is replaced, so whatever was typed
     * into it is not lost.
     */
    private void replace(TaskType type) {
        ConfigGroup group = getGroup();
        while (group != null && group.getParent() != null) {
            group = group.getParent();
        }
        if (group != null) {
            group.save(true);
        }

        owner.applySubTask(slot, type);
        owner.openEditor();
    }
}
