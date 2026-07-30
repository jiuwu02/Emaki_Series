# Emaki Series

Emaki Series is a multi-module Maven project for Paper-based Minecraft RPG servers (Paper / Purpur / Folia). `EmakiCoreLib` provides shared infrastructure for item sources, GUI templates, actions, YAML, PDC, expressions, economy bridges, and runtime services, while the business modules implement equipment, combat, progression, skills, crafting, and collection systems.

Current source versions: `EmakiCoreLib 4.6.9`, `EmakiAttribute 4.6.9`, `EmakiForge 4.6.4`, `EmakiStrengthen 4.6.4`, `EmakiCooking 4.1.5`, `EmakiGem 2.6.5`, `EmakiSkills 2.6.4`, `EmakiItem 2.6.7`, `EmakiLevel 1.4.3`, `EmakiCodex 1.0.4`, and `EmakiStorage 1.0.6`.

## Modules

| Module            | Version | Role                  | Description                                                                                                               |
| ----------------- | ------- | --------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `EmakiCoreLib`    | `4.6.9` | Core library          | Shared GUI, actions, item sources, assembly, expressions, YAML, PDC, economy bridges, and runtime services.               |
| `EmakiAttribute`  | `4.6.9` | Attributes and combat | RPG attributes, damage types, resources, PDC contributions, conditions, snapshots, and combat feedback.                   |
| `EmakiForge`      | `4.6.4` | Forging               | Recipe-driven forging, quality rolls, material contributions, recipe books, editors, output assembly, and attribute PDC.  |
| `EmakiStrengthen` | `4.6.4` | Strengthening         | Star levels, success rates, milestones, GUI flows, material consumption, and strengthening-layer refreshes.               |
| `EmakiCooking`    | `4.1.5` | Cooking               | World stations, recipe matching, input restrictions, displays, and persistent station state.                              |
| `EmakiGem`        | `2.6.5` | Gems                  | Socket opening, inlay, extraction, upgrades, equipment templates, gem definitions, and optional attribute integration.    |
| `EmakiSkills`     | `2.6.4` | Skills                | Active slots, passive triggers, cast modes, cooldowns, and MythicMobs / Attribute integration.                            |
| `EmakiItem`       | `2.6.7` | Custom items          | Stable item definitions, vanilla components, repair, automatic refresh, sets, triggers, and safe legacy-config migration. |
| `EmakiLevel`      | `1.4.3` | Progression           | Multiple level types, experience sources, requirements, PDC, placeholders, and cross-module progression bridges.          |
| `EmakiCodex`      | `1.0.4` | Collections           | Codex entries, progress tracking, Gameplay Event conditions, rewards, and advancement-toast integration.                  |
| `EmakiStorage`    | `1.0.6` | Storage               | Paged GUI warehouse, large per-slot quantities, capacity tiers and permissions, paid unlocks, and storage events.         |

The repository also contains the compile-time `Emaki*Api` contract modules and the protocol-only module `EmakiSkillsProtocol` (artifact `emaki-skills-protocol`, version `2.6.0`). These are not server plugins and must not be placed in `plugins/`.

## Requirements

| Item            | Requirement                                           |
| --------------- | ----------------------------------------------------- |
| Java            | `25`                                                  |
| Server API      | `Paper API 1.21.8-R0.1-SNAPSHOT`                      |
| Descriptor base | `api-version: "1.21.8"`                               |
| Folia           | Every runtime plugin declares `folia-supported: true` |
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
└── EmakiCodex
```

- `EmakiCoreLib` is the hard dependency shared by the business modules.
- `EmakiAttribute` can run as the attribute/combat layer and accepts PDC contributions written by Item, Forge, Strengthen, and Gem.
- `EmakiItem` owns stable custom-item definitions, vanilla data components, repairs, updates, and sets.
- `EmakiForge`, `EmakiStrengthen`, and `EmakiGem` add independent equipment-growth layers on top of CoreLib assembly.
- `EmakiLevel` provides progression state to placeholders, actions, attributes, and MythicMobs bridges.
- `EmakiSkills` manages active and passive skill execution and can integrate with MythicMobs and EmakiAttribute.
- `EmakiCooking` implements persistent world-station gameplay through CoreLib item, action, block, and presentation services.
- `EmakiCodex` consumes the CoreLib Gameplay Event channel for collection progress and rewards.

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
├── Emaki*Api/             # Compile-time API contracts (never deployed)
├── EmakiSkillsProtocol/   # Equipment-skill PDC protocol (embedded and relocated)
└── pom.xml                # Maven parent project
```

The `private-modules` profile activates automatically when a `.key` file exists in the repository root, adding `EmakiSkills`, `EmakiGem`, and `EmakiItem` to the reactor.

## Legacy configured-item YAML migration

Shared GUI YAML files and EmakiItem `items/*.{yml,yaml}` definitions migrate legacy item fields before normal loading and parsing begins.

- Legacy `material`, `item_source` / `item_sources`, `display_name`, `item_name`, `lore`, `custom_model_data`, `enchantments`, `item_flags`, `hidden_components`, and related fields are converted to `item.source`, `item.amount`, and `item.components`.
- Migration is a pure YAML-node transformation and never creates a Bukkit `ItemStack`. Canonical modern fields always win, so legacy values do not overwrite modern values in mixed files.
- Nodes containing `components.raw` are skipped safely. Non-target business fields such as `effects`, `repair`, `update`, and `actions` remain unchanged.
- Before any modified file is replaced, its original text is backed up using its path relative to the source directory. The migrated document is written to a temporary file and parsed again; replacement happens only after validation succeeds.
- Backups are stored under the relevant plugin data directory at `migration-backups/configured-item-format/<timestamp>/`. On failure, the original file remains unchanged or is restored from its backup.

The migration is idempotent, so a migrated file is not rewritten on every startup. A full backup of `plugins/Emaki*/` is still recommended before production upgrades.

This is a physically isolated transitional compatibility layer. It does not read or compare plugin, configuration, or language-file versions; it runs only when legacy fields are detected. CoreLib's `configureditem` package provides the generic migration runner and node converter, while EmakiItem owns its legacy definition rules in `EmakiItemLegacyDefinitionConverter`. Production bridges exist only at GUI-template and EmakiItem-definition loading boundaries, so the layer can be removed as a unit after the legacy window without changing canonical `item.*` parsers, models, or general YAML infrastructure.

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

Build outputs are written to each module's `target/` directory. Install only the plugin runtime jars on the server; the `emaki-*-api` and `emaki-skills-protocol` artifacts are compile-time dependencies for third-party developers.

## Documentation

- Project documentation: [Emaki Series Docs](https://jiuwu02.github.io/Emaki_Series/)

## License

This project is released under the GNU General Public License v3.0.

---

> [!NOTE]
> The project Wiki documentation is generated after reviewing the source tree. If you find an issue, contact the maintainer or report it in the community channels.

[Join Discord Community](https://discord.gg/FV4GFQbvCM) | [QQ Group](https://qm.qq.com/q/GqGrzHp0wU)
