package dev.questjs.ftbquestskubejs.mixin;

import dev.ftb.mods.ftblibrary.client.gui.input.MouseButton;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.client.gui.quests.ChapterPanel;
import dev.questjs.ftbquestskubejs.FTBQuestsKubeJS;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChapterPanel.ChapterGroupButton.class, remap = false)
public abstract class ChapterGroupButtonMixin {
    @Shadow(remap = false) public ChapterGroup group;

    @Inject(method = "onClicked", at = @At("RETURN"), remap = false)
    private void ftbquestskubejs$onClicked(MouseButton button, CallbackInfo callbackInfo) {
        if (!group.isGuiCollapsed()) {
            FTBQuestsKubeJS.postChapterGroup(group);
        } else {
            FTBQuestsKubeJS.postCloseChapterGroup(group);
        }
    }
}
