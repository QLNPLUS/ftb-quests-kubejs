package dev.questjs.ftbquestskubejs;

import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.ChapterGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.QuestObject;
import dev.ftb.mods.ftbquests.quest.task.Task;
import dev.latvian.mods.kubejs.client.ClientPlayerKubeEvent;
import net.minecraft.client.player.LocalPlayer;

public final class FTBQuestsOpenEvent extends ClientPlayerKubeEvent {
    private final String type;
    private final ChapterGroup chapterGroup;
    private final Chapter chapter;
    private final Quest quest;
    private final Task task;
    private final QuestObject openedObject;
    private final QuestObject closedObject;

    public FTBQuestsOpenEvent(LocalPlayer player, String type, ChapterGroup chapterGroup, Chapter chapter, Quest quest, Task task, QuestObject openedObject) {
        this(player, type, chapterGroup, chapter, quest, task, openedObject, null);
    }

    public FTBQuestsOpenEvent(LocalPlayer player, String type, ChapterGroup chapterGroup, Chapter chapter, Quest quest, Task task, QuestObject openedObject, QuestObject closedObject) {
        super(player);
        this.type = type;
        this.chapterGroup = chapterGroup;
        this.chapter = chapter;
        this.quest = quest;
        this.task = task;
        this.openedObject = openedObject;
        this.closedObject = closedObject;
    }

    public String getType() {
        return type;
    }

    public ChapterGroup getChapterGroup() {
        return chapterGroup;
    }

    public Chapter getChapter() {
        return chapter;
    }

    public Quest getQuest() {
        return quest;
    }

    public Task getTask() {
        return task;
    }

    public QuestObject getOpenedObject() {
        return openedObject;
    }

    public QuestObject getClosedObject() {
        return closedObject;
    }
}
