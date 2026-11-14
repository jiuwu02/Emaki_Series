# Emaki Series

Emaki Series is a multi-module Gradle project for Minecraft 26.1+ Paper-based RPG servers (Paper / Purpur / Folia). `EmakiCoreLib` provides shared infrastructure for item sources, GUI templates, actions, YAML, PDC, expressions, economy bridges, and runtime services, while the business modules implement equipment progression, combat, skills, cooking, crafting, codex, storage, and custom-mob systems.

Current source versions: `EmakiCoreLib 5.0.2`, `EmakiAttribute 5.0.5`, `EmakiForge 5.0.1`, `EmakiStrengthen 5.0.1`, `EmakiCooking 5.0.1`, `EmakiGem 3.0.1`, `EmakiSkills 3.0.1`, `EmakiItem 3.0.4`, `EmakiLevel 2.0.1`, `EmakiCodex 2.0.1`, `EmakiStorage 2.0.1`, `EmakiStation 2.0.1`, `EmakiAccessory 2.0.1`, `EmakiMobs 2.0.1`, `EmakiStructure 1.0.9`, `EmakiDungeon 1.0.17`, `EmakiKits 1.0.1`, and `EmakiDummy 1.0.4`.

## Modules

| Module            | Version | Role                  | Description                                                                                                               |
| ----------------- | ------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `EmakiCoreLib`    | `5.0.2`   | Core library          | Shared GUI, actions, item sources, assembly, expressions, YAML, PDC, economy bridges, and runtime services.               |
| `EmakiAttribute`  | `5.0.5`  | Attributes and combat | RPG attributes, damage types, resources, PDC contributions, conditions, snapshots, and combat feedback.                   |
| `EmakiForge`      | `5.0.1`  | Forging               | Recipe-driven forging, quality rolls, material contributions, recipe books, editors, output assembly, and attribute PDC.  |
| `EmakiStrengthen` | `5.0.1`  | Strengthening         | Star levels, success rates, milestones, GUI flows, material consumption, and strengthening-layer refreshes.               |
| `EmakiCooking`    | `5.0.1`  | Cooking               | World stations, recipe matching, input restrictions, displays, and persistent station state.                              |
| `EmakiGem`        | `3.0.1`  | Gems                  | Socket opening, inlay, extraction, upgrades, equipment templates, gem definitions, and optional attribute integration.    |
| `EmakiSkills`     | `3.0.1`  | Skills                | Active slots, passive triggers, cast modes, cooldowns, and MythicMobs / Attribute integration.                            |
| `EmakiItem`       | `3.0.4`   | Custom items          | Stable item definitions, vanilla components, repair, automatic refresh, sets, triggers, and item-state management.       |
| `EmakiLevel`      | `2.0.1`  | Progression           | Multiple level types, experience sources, requirements, PDC, placeholders, and cross-module progression bridges.          |
| `EmakiCodex`      | `2.0.1`  | Collections           | Codex entries, progress tracking, Gameplay Event conditions, rewards, and advancement-toast integration.                  |
| `EmakiStorage`    | `2.0.1`  | Storage               | Paged GUI warehouse, large per-slot quantities, capacity tiers and permissions, paid unlocks, and storage events.         |
| `EmakiStation`    | `2.0.1`  | Crafting stations     | World crafting stations, crafting queues and costs, recipes and material lists, plus equipment dismantling and recovery.  |
| `EmakiAccessory`  | `2.0.1`  | Accessories           | Accessory parts expanded into slots, accessory sets, uniqueness and death-drop policies, and attribute integration.       |
| `EmakiMobs`       | `2.0.1`  | Custom mobs           | Custom mob definitions, spawn rules, loot tables, skill behavior, and Attribute / Skills / Item integration.              |
| `EmakiStructure`  | `1.0.9`  | Structures            | Structure capture and export, directional template placement, multiblock interaction definitions, and vanilla structure replacement. |
| `EmakiDungeon`    | `1.0.17` | Party dungeons        | Template-cloned dungeon worlds, party challenge flow, entry limits, reward staging, and score settlement.                 |
| `EmakiKits`       | `1.0.1`  | Kits                  | Config-driven kit definitions, reward delivery, CDK codes, choice kits, and purchases.                                   |
| `EmakiDummy`      | `1.0.4`  | Dummy testing         | Protocol-level dummy players and scenario-script testing built on MCProtocolLib.                                        |

The repository also contains the compile-time `Emaki*Api` contract modules. The equipment skill PDC contract is not a separate module; it lives in the `emaki.jiuwu.craft.skills.api.pdc` package of `EmakiSkillsApi` and is shaded and relocated by the runtime modules that need it. These Api modules are not server plugins and must not be placed in `plugins/`.

## Requirements

| Item            | Requirement                                           |
| --------------- | ----------------------------------------------------- |
| Java            | `25`                                                  |
| Server API      | `Paper API 26.1.2.build.74-stable`                      |
| Descriptor base | `api-version: "26.1"`                               |
| Folia           | All 18 runtime plugins declare `folia-supported: true` (declaration only; no live-server verification recorded) |
| Text components | `Adventure 4.26.1`                                    |
| Build tool      | Multi-module Gradle project (wrapper pins Gradle 9.8.0) |
| License         | `GPL-3.0-only`                                        |

## Module relationships

```text
EmakiCoreLib
├── EmakiAttribute
├── EmakiItem
├── EmakiForge
├── EmakiStrengthen
├── EmakiGem
├── EmakiLevel
├── EmakiSkills
├── EmakiCooking
├── EmakiCodex
├── EmakiStorage
├── EmakiStation
├── EmakiAccessory
├── EmakiMobs
├── EmakiStructure
├── EmakiDungeon
├── EmakiKits
└── EmakiDummy
```

- `EmakiCoreLib` is the hard dependency shared by the business modules.
- `EmakiAttribute` can run as the attribute/combat layer and accepts PDC contributions written by Item, Forge, Strengthen, and Gem.
- `EmakiItem` owns stable custom-item definitions, vanilla data components, repairs, updates, sets, and triggers.
- `EmakiForge`, `EmakiStrengthen`, and `EmakiGem` add independent equipment-growth layers on top of CoreLib assembly.
- `EmakiLevel` provides progression state to placeholders, actions, attributes, and MythicMobs bridges.
- `EmakiSkills` manages active and passive skill execution and can integrate with MythicMobs and EmakiAttribute.
- `EmakiCooking` implements persistent world-station gameplay through CoreLib item, action, block, and presentation services.
- `EmakiCodex` consumes the CoreLib Gameplay Event channel for collection progress and rewards.
- `EmakiStorage` provides paged storage GUI, large per-slot quantities, capacity tiers, paid unlocks, and storage events.
- `EmakiStation` provides world crafting stations, crafting queues, costs, and equipment dismantling/recovery.
- `EmakiAccessory` provides paged accessory slots, accessory sets, uniqueness/death-drop policies, and Attribute integration.
- `EmakiMobs` provides YAML-defined custom mobs, spawn rules, loot tables, skill behavior, and integration with Attribute, Skills, and Item.
- `EmakiStructure` provides structure template export and placement, multiblock interaction definitions, and bridges to MythicMobs spawn points and third-party block/item plugins.
- `EmakiDungeon` provides party dungeon worlds and challenge flow, with MythicMobs, PlaceholderAPI, and BetterHUD bridges.
- `EmakiKits` delivers kit rewards through CoreLib item sources and actions, covering first-join, periodic, CDK, choice, and purchase kits.
- `EmakiDummy` logs in protocol-level dummy players via MCProtocolLib for scenario and load testing.

## Repository layout

```text
Project/
├── EmakiCoreLib/          # Shared core library
├── EmakiAttribute/        # Attributes and combat
├── EmakiItem/             # Custom items (private module, .key gated)
├── EmakiForge/            # Forging
├── EmakiStrengthen/       # Strengthening
├── EmakiGem/              # Equipment gems (private module, .key gated)
├── EmakiLevel/            # Level progression
├── EmakiSkills/           # Active/passive skills (private module, .key gated)
├── EmakiCooking/          # Cooking stations
├── EmakiCodex/            # Collections and progress
├── EmakiStorage/          # Warehouse system (private module, .key gated)
├── EmakiStation/          # Crafting stations and dismantling
├── EmakiAccessory/        # Accessories (private module, .key gated)
├── EmakiMobs/             # Custom mobs
├── EmakiStructure/        # Structures and multiblock (private module, .key gated)
├── EmakiDungeon/          # Party dungeons
├── EmakiKits/             # Kits
├── EmakiDummy/            # Protocol dummies and scenario testing
├── Emaki*Api/             # Compile-time API contracts (never deployed)
│                          #   Equipment-skill PDC protocol lives in EmakiSkillsApi's api.pdc package
├── settings.gradle        # Module list (29 default + 6 .key-gated private modules)
├── build.gradle           # Root build script: shared compile / javadoc / shade conventions
├── gradle/libs.versions.toml  # Version catalog: single source of truth for dependency versions
└── gradlew, gradlew.bat   # Gradle wrapper (pins Gradle 9.8.0)
```

The private modules are gated by the `.key` marker rather than a Maven profile: `settings.gradle` adds `EmakiSkills`, `EmakiGem`, `EmakiItem`, `EmakiStorage`, `EmakiStructure`, and `EmakiAccessory` to the build only when a `.key` file exists in the repository root. `.key` is ignored by `.gitignore` and untracked, so a fresh clone does not contain it; the build then compiles the default 29 project modules without failing, which is a supported state. Create an empty `.key` file in the repository root when you need the private modules.

## Item configuration and ID migration

Current `EmakiItem` definitions use the canonical `item.source`, `item.amount`, and `item.components` structure: the source goes in `item.source`, the count in `item.amount`, and names, lore, model data, and components in `item.components`.

Item-ID renaming and online-inventory migration are separate capabilities:

- `id_aliases.yml` stores mappings from old item IDs to new IDs.
- `/ei migrate id <old> <new> --dry-run|--apply` migrates configuration references.
- `/ei migrate inventory <player|all>` refreshes old IDs in online player inventories.

## EmakiItem set state

Set-membership visibility is calculated separately from the valid equipped-piece count. An item that declares an available set ID retains a `0/N` set state, while only pieces that resolve to a real set piece and match both the item's `equip_slot` and the piece `slot` count as active. Missing set definitions are isolated and reported through `set` DEBUG without creating empty state, activating bonuses, or performing destructive writes.

## Build

From the repository root (Java `25`; start the build with `JAVA_HOME` pointing at your JDK 25 installation):

```bash
./gradlew build
```

A common local compilation check is:

```bash
./gradlew compileJava
```

To assemble the jars without running the test suite:

```bash
./gradlew build -x test
```

Build outputs are written to each module's `build/libs/` directory. Runtime modules produce `EmakiXxx-<version>.jar` (the deployable shaded plugin jar) alongside an unshaded `EmakiXxx-<version>-plain.jar`; API modules produce `emaki-xxx-api-<version>.jar` with matching `-sources` and `-javadoc` jars. `./gradlew publishToMavenLocal` installs the API artifacts into the local Maven repository, replacing the former `mvn install`. Install only the plugin runtime jars on the server; the `emaki-*-api` artifacts are compile-time dependencies for third-party developers.

## Installation and first check

1. Use Java `25` with Paper `26.1+` or a compatible Paper-family server.
2. Put `EmakiCoreLib-*.jar` and the runtime modules you need into the server's `plugins/` directory.
3. Do not put `emaki-*-api-*.jar` into `plugins/`; API jars are compile-time dependencies for developers only.
4. After startup, run `/corelib check` to inspect loaded modules and configuration precheck results.

## Maven repository

API artifacts are published to the CrypticLib Maven repository. Browse the Emaki artifact directory here:

[Emaki API artifacts](https://repo2.crypticlib.com/releases/emaki/jiuwu/craft/)

Use this repository URL in Maven builds:

```xml
<repositories>
  <repository>
    <id>jiuwu-releases</id>
    <url>https://repo2.crypticlib.com/releases/</url>
  </repository>
</repositories>
```

For example, to depend on `EmakiCoreLibApi`:

```xml
<dependency>
  <groupId>emaki.jiuwu.craft</groupId>
  <artifactId>emaki-corelib-api</artifactId>
  <version>5.0.2</version>
  <scope>provided</scope>
</dependency>
```

Use API jars only as `provided` compile dependencies. Do not install them on the server or shade/relocate them into your own plugin.

## Documentation

- Project documentation: [Emaki Series Docs](https://jiuwu02.github.io/Emaki_Series/)

## License

This project is released under the GNU General Public License v3.0.

---

> [!NOTE]
> The project Wiki documentation is generated after reviewing the source tree. If you find an issue, contact the maintainer or report it in the community channels.

[Join Discord Community](https://discord.gg/FV4GFQbvCM) | [QQ Group](https://qm.qq.com/q/GqGrzHp0wU)
