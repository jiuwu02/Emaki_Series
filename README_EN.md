# Emaki Series

Emaki Series is a multi-module Maven project for Minecraft 1.21.8+ Paper-based RPG servers (Paper / Purpur / Folia). `EmakiCoreLib` provides shared infrastructure for item sources, GUI templates, actions, YAML, PDC, expressions, economy bridges, and runtime services, while the business modules implement equipment progression, combat, skills, cooking, crafting, codex, storage, and custom-mob systems.

Current source versions: `EmakiCoreLib 4.8.0`, `EmakiAttribute 4.8.0`, `EmakiForge 4.8.0`, `EmakiStrengthen 4.8.0`, `EmakiCooking 4.3.0`, `EmakiGem 2.8.0`, `EmakiSkills 2.8.0`, `EmakiItem 2.8.0`, `EmakiLevel 1.6.0`, `EmakiCodex 1.1.0`, `EmakiStorage 1.1.0`, `EmakiStation 1.1.0`, `EmakiAccessory 1.1.0`, and `EmakiMobs 1.0.0`.

## Modules

| Module            | Version | Role                  | Description                                                                                                               |
| ----------------- | ------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `EmakiCoreLib`    | `4.8.0`  | Core library          | Shared GUI, actions, item sources, assembly, expressions, YAML, PDC, economy bridges, and runtime services.               |
| `EmakiAttribute`  | `4.8.0`  | Attributes and combat | RPG attributes, damage types, resources, PDC contributions, conditions, snapshots, and combat feedback.                   |
| `EmakiForge`      | `4.8.0`  | Forging               | Recipe-driven forging, quality rolls, material contributions, recipe books, editors, output assembly, and attribute PDC.  |
| `EmakiStrengthen` | `4.8.0`  | Strengthening         | Star levels, success rates, milestones, GUI flows, material consumption, and strengthening-layer refreshes.               |
| `EmakiCooking`    | `4.3.0`  | Cooking               | World stations, recipe matching, input restrictions, displays, and persistent station state.                              |
| `EmakiGem`        | `2.8.0`  | Gems                  | Socket opening, inlay, extraction, upgrades, equipment templates, gem definitions, and optional attribute integration.    |
| `EmakiSkills`     | `2.8.0`  | Skills                | Active slots, passive triggers, cast modes, cooldowns, and MythicMobs / Attribute integration.                            |
| `EmakiItem`       | `2.8.0`  | Custom items          | Stable item definitions, vanilla components, repair, automatic refresh, sets, triggers, and item-state management.       |
| `EmakiLevel`      | `1.6.0`  | Progression           | Multiple level types, experience sources, requirements, PDC, placeholders, and cross-module progression bridges.          |
| `EmakiCodex`      | `1.1.0`  | Collections           | Codex entries, progress tracking, Gameplay Event conditions, rewards, and advancement-toast integration.                  |
| `EmakiStorage`    | `1.1.0`  | Storage               | Paged GUI warehouse, large per-slot quantities, capacity tiers and permissions, paid unlocks, and storage events.         |
| `EmakiStation`    | `1.1.0`  | Crafting stations     | World crafting stations, crafting queues and costs, recipes and material lists, plus equipment dismantling and recovery.  |
| `EmakiAccessory`  | `1.1.0`  | Accessories           | Accessory parts expanded into slots, accessory sets, uniqueness and death-drop policies, and attribute integration.       |
| `EmakiMobs`       | `1.0.0`  | Custom mobs           | Custom mob definitions, spawn rules, loot tables, skill behavior, and Attribute / Skills / Item integration.              |

The repository also contains the compile-time `Emaki*Api` contract modules. The equipment skill PDC contract is not a separate module; it lives in the `emaki.jiuwu.craft.skills.api.pdc` package of `EmakiSkillsApi` and is shaded and relocated by the runtime modules that need it. These Api modules are not server plugins and must not be placed in `plugins/`.

## Requirements

| Item            | Requirement                                           |
| --------------- | ----------------------------------------------------- |
| Java            | `25`                                                  |
| Server API      | `Paper API 1.21.8-R0.1-SNAPSHOT`                      |
| Descriptor base | `api-version: "1.21.8"`                               |
| Folia           | All 14 runtime plugins declare `folia-supported: true` (declaration only; no live-server verification recorded) |
| Text components | `Adventure 4.26.1`                                    |
| Build tool      | Multi-module Maven project                            |
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
└── EmakiMobs
```

- `EmakiCoreLib` is the hard dependency shared by the business modules.
- `EmakiAttribute` can run as the attribute/combat layer and accepts PDC contributions written by Item, Forge, Strengthen, and Gem.
- `EmakiItem` owns stable custom-item definitions, vanilla data components, repairs, updates, sets, and triggers; legacy configured-item definitions are no longer converted automatically.
- `EmakiForge`, `EmakiStrengthen`, and `EmakiGem` add independent equipment-growth layers on top of CoreLib assembly.
- `EmakiLevel` provides progression state to placeholders, actions, attributes, and MythicMobs bridges.
- `EmakiSkills` manages active and passive skill execution and can integrate with MythicMobs and EmakiAttribute.
- `EmakiCooking` implements persistent world-station gameplay through CoreLib item, action, block, and presentation services.
- `EmakiCodex` consumes the CoreLib Gameplay Event channel for collection progress and rewards.
- `EmakiStorage` provides paged storage GUI, large per-slot quantities, capacity tiers, paid unlocks, and storage events.
- `EmakiStation` provides world crafting stations, crafting queues, costs, and equipment dismantling/recovery.
- `EmakiAccessory` provides paged accessory slots, accessory sets, uniqueness/death-drop policies, and Attribute integration.
- `EmakiMobs` provides YAML-defined custom mobs, spawn rules, loot tables, skill behavior, and integration with Attribute, Skills, and Item.

## Repository layout

```text
Project/
├── EmakiCoreLib/          # Shared core library
├── EmakiAttribute/        # Attributes and combat
├── EmakiItem/             # Custom items (private-modules profile)
├── EmakiForge/            # Forging
├── EmakiStrengthen/       # Strengthening
├── EmakiGem/              # Equipment gems (private-modules profile)
├── EmakiLevel/            # Level progression
├── EmakiSkills/           # Active/passive skills (private-modules profile)
├── EmakiCooking/          # Cooking stations
├── EmakiCodex/            # Collections and progress
├── EmakiStorage/          # Warehouse system (private-modules profile)
├── EmakiStation/          # Crafting stations and dismantling
├── EmakiAccessory/        # Accessories (private-modules profile)
├── EmakiMobs/             # Custom mobs (private-modules profile)
├── Emaki*Api/             # Compile-time API contracts (never deployed)
│                          #   Equipment-skill PDC protocol lives in EmakiSkillsApi's api.pdc package
└── pom.xml                # Maven parent project
```

The `private-modules` profile activates automatically when a `.key` file exists in the repository root, adding `EmakiSkills`, `EmakiGem`, `EmakiItem`, `EmakiStorage`, `EmakiAccessory`, and `EmakiMobs` to the reactor. `.key` is ignored by `.gitignore` and untracked, so a fresh clone does not contain it; Maven then builds the default 22 project modules without failing, which is a supported state. Create an empty `.key` file in the repository root when you need the private modules.

## Item configuration and migration boundary

Current `EmakiItem` definitions use the canonical `item.source`, `item.amount`, and `item.components` structure. The legacy configured-item definition converters have been removed from CoreLib and EmakiItem; historical `material`, `display_name`, `lore`, and similar fields are not converted automatically at runtime. Rewrite them explicitly before upgrading.

Item-ID renaming and online-inventory migration are separate capabilities:

- `id_aliases.yml` stores mappings from old item IDs to new IDs.
- `/ei migrate id <old> <new> --dry-run|--apply` migrates configuration references.
- `/ei migrate inventory <player|all>` refreshes old IDs in online player inventories.

## EmakiItem set state

Set-membership visibility is calculated separately from the valid equipped-piece count. An item that declares an available set ID retains a `0/N` set state, while only pieces that resolve to a real set piece and match both the item's `equip_slot` and the piece `slot` count as active. Missing set definitions are isolated and reported through `set` DEBUG without creating empty state, activating bonuses, or performing destructive writes.

## Build

From the repository root:

```bash
mvn clean package
```

A common local compilation check is:

```bash
mvn -DskipTests compile
```

Build outputs are written to each module's `target/` directory. Install only the plugin runtime jars on the server; the `emaki-*-api` artifacts are compile-time dependencies for third-party developers.

## Installation and first check

1. Use Java `25` with Paper `1.21.8+` or a compatible Paper-family server.
2. Put `EmakiCoreLib-*.jar` and the runtime modules you need into the server's `plugins/` directory.
3. Do not put `emaki-*-api-*.jar` into `plugins/`; API jars are compile-time dependencies for developers only.
4. After startup, run `/corelib check` to inspect loaded modules and configuration precheck results.

## Maven repository

API artifacts are published to the CrypticLib Maven repository. Browse the Emaki artifact directory here:

[Emaki API artifacts](https://repo.crypticlib.com/service/rest/repository/browse/maven-public/emaki/jiuwu/craft/)

Use this repository URL in Maven builds:

```xml
<repositories>
  <repository>
    <id>emaki-public</id>
    <url>https://repo.crypticlib.com/repository/maven-public/</url>
  </repository>
</repositories>
```

For example, to depend on `EmakiCoreLibApi`:

```xml
<dependency>
  <groupId>emaki.jiuwu.craft</groupId>
  <artifactId>emaki-corelib-api</artifactId>
  <version>4.8.0</version>
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
