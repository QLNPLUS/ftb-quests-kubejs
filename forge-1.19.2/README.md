# FTB Quests KubeJS Events - Forge 1.19.2

This addon adds three client-side KubeJS events:

```js
FTBQuestsKubeJSEvents.openChapterGroup(event => {
  console.log(`opened group: ${event.chapterGroup.id}`)
  console.log(`player: ${event.player.username}`)
})

FTBQuestsKubeJSEvents.openChapter(event => {
  console.log(`opened chapter: ${event.chapter.id}`)
})

FTBQuestsKubeJSEvents.openQuest(event => {
  console.log(`opened quest details: ${event.quest.id}`)
  console.log(`player: ${event.player.username}`)
})

FTBQuestsKubeJSEvents.closeChapterGroup(event => {
  console.log(`closed group: ${event.closedObject.id}`)
})

FTBQuestsKubeJSEvents.closeChapter(event => {
  console.log(`closed chapter: ${event.closedObject.id}`)
})

FTBQuestsKubeJSEvents.closeQuest(event => {
  console.log(`closed quest details: ${event.closedObject.id}`)
})
```

The event object exposes `player`, `chapterGroup`, `chapter`, `quest`, `task`, `openedObject`, and `type`. Values unrelated to the event are `null`.

`openQuest` fires when the quest detail panel shown in FTB Quests is opened. In this event, `event.quest` and `event.openedObject` are the displayed quest; `event.task` is `null` because FTB Quests tasks are the individual objectives inside the quest detail panel.

The close events use the same object fields as their open counterparts. `closeChapterGroup` fires when a chapter group is collapsed, `closeChapter` fires when the selected chapter changes or the quest screen closes, and `closeQuest` fires when the quest detail panel is closed or replaced by another quest. For close events, `event.closedObject` is the object being closed.
