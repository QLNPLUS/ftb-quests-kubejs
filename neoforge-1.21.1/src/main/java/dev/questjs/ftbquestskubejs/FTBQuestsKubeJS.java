package dev.questjs.ftbquestskubejs;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class FTBQuestsKubeJS {
    private FTBQuestsKubeJS() {
    }

    public static void postChapterGroup(ChapterGroup group) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            FTBQuestsEvents.OPEN_CHAPTER_GROUP.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent(player, "chapterGroup", group, null, null, null, group));
        }
    }

    public static void postChapter(Chapter chapter) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            FTBQuestsEvents.OPEN_CHAPTER.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent(player, "chapter", chapter.getGroup(), chapter, null, null, chapter));
        }
    }

    public static void postQuest(Quest quest) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            FTBQuestsEvents.OPEN_QUEST.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent(player, "quest", quest.getChapter().getGroup(), quest.getChapter(), quest, null, quest));
        }
    }

    public static void postCloseChapterGroup(ChapterGroup group) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            FTBQuestsEvents.CLOSE_CHAPTER_GROUP.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent(player, "chapterGroup", group, null, null, null, null, group));
        }
    }

    public static void postCloseChapter(Chapter chapter) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            FTBQuestsEvents.CLOSE_CHAPTER.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent(player, "chapter", chapter.getGroup(), chapter, null, null, null, chapter));
        }
    }

    public static void postCloseQuest(Quest quest) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            FTBQuestsEvents.CLOSE_QUEST.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent(player, "quest", quest.getChapter().getGroup(), quest.getChapter(), quest, null, null, quest));
        }
    }
}
