# Changelog

## 2.1.0 - 2026-10-11

- Added the Composite Task quest type to NeoForge 1.21.1 and 26.1.2. It can reference other quests, complete when they are all finished within the time limit, and reset them if the limit expires.
- Updated Composite Task descriptions on Forge 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2 to show numbered references without progress counts or time-limit text.

## 2.0.0 - 2026-10-10

- New quest task type "Player Attributes": a task completes when the player's attributes (health, speed, attack damage, reach, luck, and any modded attribute) satisfy configured comparisons. Multiple conditions can be combined and all must match. The in-game editor offers greater than, greater/equal, less than, less/equal, equal, and inclusive range, with a searchable attribute list. The task shows an iron chestplate icon by default. Available on Forge 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2.
- New quest task type "Custom Data Number": a task completes when a numeric value stored in the player's persistent data (written by commands or other mods, e.g. points or balances) satisfies a configured comparison. The data path is dot-separated (like "shop.balance"); values may be negative or fractional. The task shows a name tag icon by default. Available on Forge 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2.
- Quests already authored with the standalone "More And More Quest" mod keep working unchanged; that mod is no longer needed alongside this one.

## 1.0.0 - 2026-09-07

- First stable release.
- Publishes Forge 1.19.2, Forge 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2 files.
- Includes client-side open and close events for FTB Quests chapter groups, chapters, and quest detail panels.

## 0.1.0

- Initial release.
- Adds client-side KubeJS events for opening and closing FTB Quests chapter groups, chapters, and quest detail panels.
- Supports Forge 1.19.2, Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2.
