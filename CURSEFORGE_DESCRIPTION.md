# FTB Quests KubeJS Events

FTB Quests KubeJS Events adds client-side KubeJS events for the FTB Quests GUI.

Use it to react when a player opens or closes a chapter group, chapter, or quest detail panel. The event object identifies the player and exposes the related FTB Quests objects, making it possible to drive custom client-side UI, sounds, overlays, tracking, or other KubeJS logic.

## Events

```js
FTBQuestsKubeJSEvents.openChapterGroup(event => {
  console.log(`opened group: ${event.chapterGroup.id}`)
})

FTBQuestsKubeJSEvents.openChapter(event => {
  console.log(`opened chapter: ${event.chapter.id}`)
})

FTBQuestsKubeJSEvents.openQuest(event => {
  console.log(`opened quest details: ${event.quest.id}`)
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

## Behavior

- `openChapterGroup`: fired when a chapter group is opened.
- `openChapter`: fired when a chapter is selected.
- `openQuest`: fired when the quest detail panel is opened.
- `closeChapterGroup`: fired when a chapter group is collapsed.
- `closeChapter`: fired when the selected chapter changes or the quest screen closes.
- `closeQuest`: fired when the quest detail panel closes or changes to another quest.

Close events provide the object being closed through `event.closedObject`. Open events provide the object through `event.openedObject` and the matching typed field.

The event group is named `FTBQuestsKubeJSEvents` so it remains compatible with the `FTBQuestsEvents` group provided by FTB XMod Compat.

## Requirements

Install the matching release for your Minecraft version and loader together with:

- FTB Quests
- KubeJS
- The required loader: Forge or NeoForge

The addon does not replace FTB Quests or KubeJS. It only exposes FTB Quests client GUI transitions to KubeJS scripts.

## Supported Versions

- Forge 1.19.2
- Forge 1.20.1
- NeoForge 1.21.1
- NeoForge 26.1.2

See the repository README for the exact dependency versions and source for each supported target.
