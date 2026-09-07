package dev.questjs.ftbquestskubejs;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.client.Minecraft;

public final class FTBQuestsKubeJS {
    private FTBQuestsKubeJS() {
    }

    public static void postChapterGroup(ChapterGroup group) {
        if (Minecraft.getInstance().player != null) {
            FTBQuestsEvents.OPEN_CHAPTER_GROUP.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent("chapterGroup", group, null, null, null, group));
        }
    }

    public static void postChapter(Chapter chapter) {
        if (Minecraft.getInstance().player != null) {
            FTBQuestsEvents.OPEN_CHAPTER.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent("chapter", chapter.group, chapter, null, null, chapter));
        }
    }

    public static void postQuest(Quest quest) {
        if (Minecraft.getInstance().player != null) {
            FTBQuestsEvents.OPEN_QUEST.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent("quest", quest.chapter.group, quest.chapter, quest, null, quest));
        }
    }

    public static void postCloseChapterGroup(ChapterGroup group) {
        if (Minecraft.getInstance().player != null) {
            FTBQuestsEvents.CLOSE_CHAPTER_GROUP.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent("chapterGroup", group, null, null, null, null, group));
        }
    }

    public static void postCloseChapter(Chapter chapter) {
        if (Minecraft.getInstance().player != null) {
            FTBQuestsEvents.CLOSE_CHAPTER.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent("chapter", chapter.group, chapter, null, null, null, chapter));
        }
    }

    public static void postCloseQuest(Quest quest) {
        if (Minecraft.getInstance().player != null) {
            FTBQuestsEvents.CLOSE_QUEST.post(ScriptType.CLIENT,
                    new FTBQuestsOpenEvent("quest", quest.chapter.group, quest.chapter, quest, null, null, quest));
        }
    }
}
