# FTB Quests KubeJS Events

A client-side addon that exposes FTB Quests GUI state changes to KubeJS scripts.

## Supported Versions

| Project | Minecraft | Loader | FTB Quests | KubeJS |
| --- | --- | --- | --- | --- |
| `forge-1.19.2` | 1.19.2 | Forge | 1902.5.10-build.497 | 1902.6.2-build.73 |
| `forge-1.20.1` | 1.20.1 | Forge | 2001.4.22 | 2001.6.5-build.26 |
| `fabric-1.20.1` | 1.20.1 | Fabric | 2001.4.22 | 2001.6.5-build.26 |
| `neoforge-1.21.1` | 1.21.1 | NeoForge | 2101.1.34 | 2101.7.2-build.374 |
| `neoforge-1.26.1.2` | 26.1.2 | NeoForge | 26.1.2.7 | 26.1.2-8.0.4+neoforge |

## KubeJS API

The event group is intentionally named `FTBQuestsKubeJSEvents` so it can coexist with the `FTBQuestsEvents` group supplied by FTB XMod Compat.

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

All events are client events. The event object exposes `player`, `chapterGroup`, `chapter`, `quest`, `task`, `openedObject`, `closedObject`, and `type`. Values unrelated to the event are `null`.

`openQuest` represents the FTB Quests quest detail panel. It exposes the displayed `Quest`; an individual objective inside that panel is a `Task` and is not treated as a quest-detail open event.

## Project Layout

- `forge-1.19.2/`: Forge 1.19.2 project
- `forge-1.20.1/`: Forge 1.20.1 project
- `fabric-1.20.1/`: Fabric 1.20.1 project
- `neoforge-1.21.1/`: NeoForge 1.21.1 project
- `neoforge-1.26.1.2/`: NeoForge 26.1.2 project
- `main/`: build, packaging validation, smoke-test, and version documentation
- `CURSEFORGE_DESCRIPTION.md`: CurseForge project description

## Build And Validation

From PowerShell:

```powershell
./main/build-all.ps1
./main/validate-artifacts.ps1
```

The five projects use their own Gradle wrapper, Java toolchain, dependencies, run directory, and release artifact.

## CurseForge Publishing

The workflow at `.github/workflows/curseforge-publish.yml` publishes all five loader/version builds when a GitHub Release is published. It also supports manual retries for `all`, `forge`, `fabric`, or `neoforge` from an explicit branch or tag.

Configure these repository settings before publishing:

- Repository variable `CURSEFORGE_PROJECT_ID`: `1685965`
- Repository secret `CURSEFORGE_TOKEN`: a CurseForge API token

Create a GitHub Release with a tag such as `v1.0.0`, or manually provide `ref=main` and `version=1.0.0`. The matching `CHANGELOG.md` section is uploaded with each CurseForge file. The workflow builds the exact release version by passing the selected version to Gradle.
