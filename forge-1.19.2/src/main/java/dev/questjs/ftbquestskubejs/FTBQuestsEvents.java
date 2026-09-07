package dev.questjs.ftbquestskubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public final class FTBQuestsEvents {
    public static final EventGroup GROUP = EventGroup.of("FTBQuestsKubeJSEvents");
    public static final EventHandler OPEN_CHAPTER_GROUP = GROUP.client("openChapterGroup", () -> FTBQuestsOpenEvent.class);
    public static final EventHandler OPEN_CHAPTER = GROUP.client("openChapter", () -> FTBQuestsOpenEvent.class);
    public static final EventHandler OPEN_QUEST = GROUP.client("openQuest", () -> FTBQuestsOpenEvent.class);
    public static final EventHandler CLOSE_CHAPTER_GROUP = GROUP.client("closeChapterGroup", () -> FTBQuestsOpenEvent.class);
    public static final EventHandler CLOSE_CHAPTER = GROUP.client("closeChapter", () -> FTBQuestsOpenEvent.class);
    public static final EventHandler CLOSE_QUEST = GROUP.client("closeQuest", () -> FTBQuestsOpenEvent.class);

    private FTBQuestsEvents() {
    }
}
