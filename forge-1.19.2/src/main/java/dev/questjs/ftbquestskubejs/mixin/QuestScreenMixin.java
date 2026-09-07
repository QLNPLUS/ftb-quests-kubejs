package dev.questjs.ftbquestskubejs.mixin;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.gui.quests.QuestScreen;
import dev.questjs.ftbquestskubejs.FTBQuestsKubeJS;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuestScreen.class, remap = false)
public abstract class QuestScreenMixin {
    @Shadow(remap = false) private Chapter selectedChapter;

    @Shadow(remap = false) public abstract Quest getViewedQuest();

    @Inject(method = "viewQuest(Ldev/ftb/mods/ftbquests/quest/Quest;)V", at = @At("HEAD"), remap = false)
    private void ftbquestskubejs$onCloseQuest(Quest quest, CallbackInfo callbackInfo) {
        Quest previousQuest = getViewedQuest();
        if (previousQuest != null && previousQuest != quest) {
            FTBQuestsKubeJS.postCloseQuest(previousQuest);
        }
    }

    @Inject(method = "open", at = @At("HEAD"), remap = false)
    private void ftbquestskubejs$onOpenChapter(dev.ftb.mods.ftbquests.quest.QuestObject object, boolean focus, CallbackInfo callbackInfo) {
        if (object instanceof Chapter chapter) {
            FTBQuestsKubeJS.postChapter(chapter);
        }
    }

    @Inject(method = "viewQuest(Ldev/ftb/mods/ftbquests/quest/Quest;)V", at = @At("RETURN"), remap = false)
    private void ftbquestskubejs$onViewQuest(Quest quest, CallbackInfo callbackInfo) {
        if (quest != null) {
            FTBQuestsKubeJS.postQuest(quest);
        }
    }

    @Inject(method = "selectChapter(Ldev/ftb/mods/ftbquests/quest/Chapter;)V", at = @At("HEAD"), remap = false)
    private void ftbquestskubejs$onCloseChapter(Chapter chapter, CallbackInfo callbackInfo) {
        if (selectedChapter != null && selectedChapter != chapter) {
            FTBQuestsKubeJS.postCloseChapter(selectedChapter);
        }
    }

    @Inject(method = "onClosed", at = @At("HEAD"), remap = false)
    private void ftbquestskubejs$onCloseScreen(CallbackInfo callbackInfo) {
        Quest quest = getViewedQuest();
        if (quest != null) {
            FTBQuestsKubeJS.postCloseQuest(quest);
        }
        if (selectedChapter != null) {
            FTBQuestsKubeJS.postCloseChapter(selectedChapter);
        }
    }
}
