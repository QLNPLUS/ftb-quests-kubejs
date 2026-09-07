# FTB Quests KubeJS Events

This workspace contains version-specific addon projects for FTB Quests and KubeJS.

| Project | Minecraft | Loader | FTB Quests | KubeJS |
| --- | --- | --- | --- | --- |
| `forge-1.19.2` | 1.19.2 | Forge | 1902.5.10-build.497 | 1902.6.2-build.73 |
| `forge-1.20.1` | 1.20.1 | Forge | 2001.4.22 | 2001.6.5-build.26 |
| `neoforge-1.21.1` | 1.21.1 | NeoForge | 2101.1.34 | 2101.7.2-build.374 |
| `neoforge-1.26.1.2` | 26.1.2 | NeoForge | 26.1.2.7 | 26.1.2-8.0.4+neoforge |

`main` is the workspace/documentation branch because it has no Minecraft version in its directory name. The four version directories are standalone Gradle projects.

## KubeJS API

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

The client event group is `FTBQuestsKubeJSEvents`, intentionally separate from FTB XMod Compat's `FTBQuestsEvents` group. The client event exposes `player`, `chapterGroup`, `chapter`, `quest`, `task`, `openedObject`, and `type`. Values unrelated to the event are `null`. `openQuest` represents the quest detail panel from the screenshot: `quest` and `openedObject` are the displayed quest, while `task` is `null` because FTB Quests uses `Task` for individual objectives inside that panel.

A normal click on a task objective submits that objective and does not open the quest detail panel, so it does not fire this event.

The close events mirror the open events: `closeChapterGroup` fires when a chapter group is collapsed, `closeChapter` when the selected chapter changes or the quest screen closes, and `closeQuest` when the quest detail panel closes or is replaced. Close events expose the object being closed through `event.closedObject`; unrelated object fields are `null`.

## Build And Smoke Test

From this directory:

```powershell
./build-all.ps1
./validate-artifacts.ps1
./smoke-test.ps1
```

The build also validates that every release JAR contains a valid root `pack.mcmeta`. The smoke script uses each loader's client run configuration, waits for the game log to reach a loading or startup marker, records the result, and stops only the Gradle process it started.
