# Emaki Series

Emaki Series 是一组面向 Minecraft Paper 系服务端的 Java 插件，采用多模块 Maven 工程组织。项目以 `EmakiCoreLib` 为共享基础库，向上承载属性战斗、锻造、强化、烹饪与装备宝石等 RPG 玩法模块。

当前源码版本线为：`EmakiCoreLib 4.6.9`、`EmakiAttribute 4.6.9`、`EmakiForge 4.6.4`、`EmakiStrengthen 4.6.4`、`EmakiCooking 4.1.5`、`EmakiGem 2.6.5`、`EmakiSkills 2.6.4`、`EmakiItem 2.6.7`、`EmakiLevel 1.4.3`、`EmakiCodex 1.0.4`、`EmakiStorage 1.0.6`。

## 模块概览

| 模块              | 当前版本 | 角色       | 说明                                                                                  |
| ----------------- | -------- | ---------- | ------------------------------------------------------------------------------------- |
| `EmakiCoreLib`    | `4.6.19` | 核心基础库 | 提供 GUI、动作系统、物品源桥接、物品装配、表达式、YAML、PDC、经济桥接与共享运行时能力 |
| `EmakiAttribute`  | `4.6.14` | 属性系统   | 提供 RPG 属性、三系伤害、资源状态、PDC 属性接入、条件检查、快照调试与战斗反馈能力     |
| `EmakiForge`      | `4.6.9`  | 锻造系统   | 提供配方驱动锻造、品质随机、材料贡献、图鉴、编辑器、结果组装与 PDC 属性写入能力       |
| `EmakiStrengthen` | `4.6.8`  | 强化系统   | 提供星级强化、成功率配置、锻印 / 里程碑、强化 GUI、材料消耗与强化层刷新能力           |
| `EmakiCooking`    | `4.1.7`  | 烹饪系统   | 提供多类世界工位运行时、配方判定、输入限制、展示与状态持久化能力                      |
| `EmakiGem`        | `2.6.8`  | 宝石系统   | 提供装备开孔、宝石镶嵌、取出、升级、装备模板、宝石定义与可选属性系统接入能力          |
| `EmakiSkills`     | `2.6.13` | 技能系统   | 提供主动技能槽位、被动触发器、施法模式、冷却与 MythicMobs / Attribute 桥接能力        |
| `EmakiItem`       | `2.6.11` | 物品系统   | 提供自定义物品定义、原版组件、修复、自动更新、套装、触发器与旧配置安全迁移            |
| `EmakiLevel`      | `1.4.7`  | 等级系统   | 提供多等级类型、经验来源、升级需求、PDC、占位符与跨模块成长桥接                       |
| `EmakiCodex`      | `1.0.6`  | 图鉴系统   | 提供图鉴、进度追踪、Gameplay Event 条件、奖励与客户端成就提示桥接                     |
| `EmakiStorage`    | `1.0.11` | 仓库系统   | 提供分页 GUI 仓库、单槽大额存量、容量档位与权限、付费解锁与存取事件                   |

除上述运行时模块外，仓库还包含各模块对应的 `Emaki*Api` 编译期契约模块。装备技能 PDC 协议不是独立模块，而是 `EmakiSkillsApi` 的 `emaki.jiuwu.craft.skills.api.pdc` 包，由需要它的运行时模块在 shade 时嵌入并 relocate。这些 Api 模块不是服务器插件，不要放入 `plugins/`。

## 技术基线

| 项目       | 说明                                       |
| ---------- | ------------------------------------------ |
| Java       | `25`                                       |
| 服务端 API | `Paper API 1.21.8-R0.1-SNAPSHOT`           |
| 描述符基线 | `api-version: "1.21.8"`                    |
| Folia      | 11 个运行时插件均声明 `folia-supported: true`（声明值，尚未有实机验证记录）|
| 文本组件   | `Adventure 4.26.1`                         |
| 构建工具   | Maven 多模块聚合                           |
| 许可证     | `GPL-3.0-only`                             |

## 模块关系

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

- `EmakiCoreLib` 是所有业务模块的强依赖，负责共享 GUI、动作、物品源、配置、经济、PDC 与运行时服务。
- `EmakiAttribute` 可独立提供属性与战斗计算，也可接收 Item / Forge / Strengthen / Gem 通过 PDC 写入的属性。
- `EmakiItem` 负责稳定物品 ID、原版数据组件、修复、更新与套装，并在解析前安全迁移旧版 configured-item 字段。
- `EmakiForge` 通过 CoreLib 装配物品、读取材料来源，并可选写入 EmakiAttribute 属性数据。
- `EmakiStrengthen` 通过配方系统为装备附加强化层，并可选把强化属性接入 EmakiAttribute。
- `EmakiGem` 通过装备模板、开孔器和宝石定义提供可组合装备成长，可选把宝石属性接入 EmakiAttribute。
- `EmakiLevel` 管理多类型等级与经验，并向占位符、动作、属性和 MythicMobs 桥接成长状态。
- `EmakiSkills` 通过技能解锁、主动触发器、被动触发器、技能等级、技能传参与施法模式承接技能管理，并可桥接 MythicMobs 与 EmakiAttribute。
- `EmakiCooking` 通过 CoreLib 的物品源、动作系统、方块桥接与结构化展示能力承接世界工位玩法。
- `EmakiCodex` 基于 CoreLib Gameplay Event 通道记录图鉴、进度和奖励。

## 仓库结构

```text
Project/
├── EmakiCoreLib/          # 核心基础库
├── EmakiAttribute/        # 属性与战斗系统
├── EmakiItem/             # 自定义物品系统（private-modules profile）
├── EmakiForge/            # 锻造系统
├── EmakiStrengthen/       # 强化系统
├── EmakiGem/              # 装备宝石系统（private-modules profile）
├── EmakiLevel/            # 等级成长系统
├── EmakiSkills/           # 主动/被动技能系统（private-modules profile）
├── EmakiCooking/          # 烹饪系统
├── EmakiCodex/            # 图鉴与进度系统
├── Emaki*Api/             # 各模块编译期 API 契约（不部署到服务器）
│                          #   装备技能 PDC 协议在 EmakiSkillsApi 的 api.pdc 包内
└── pom.xml                # Maven 父工程
```

`private-modules` profile 在根目录存在 `.key` 文件时按文件自动激活，用于纳入 `EmakiSkills`、`EmakiGem`、`EmakiItem`、`EmakiStorage` 四个模块。`.key` 被 `.gitignore` 忽略且不受 git 跟踪，因此新克隆的仓库默认不存在该文件，此时 Maven 只构建 19 个模块而不报错；这是受支持的合法状态，需要私有模块时在仓库根目录创建空文件 `.key` 即可。

## 默认资源定位

- `EmakiCoreLib`：共享配置位于 `EmakiCoreLib/src/main/resources/config.yml`；各业务插件的共享 GUI 位于自己的 `gui/` 目录。GUI 中的旧版 configured-item 字段会在正式解析前安全迁移。
- `EmakiAttribute`：属性、伤害类型、条件与 Lore 格式配置位于 `attributes/*.yml`、`damage_types/*.yml`、`conditions/*.yml`、`lore_formats/*.yml`，默认档案位于 `config.yml > default_profile`。
- `EmakiItem`：物品与套装定义位于 `items/`、`sets/`，ID 别名位于 `id_aliases.yml`；旧顶层物品字段会在正式解析前迁移到 `item.source` / `item.components`。
- `EmakiForge`：锻造配方、GUI 与语言资源位于 `recipes/`、`gui/`、`lang/` 等目录，当前重点围绕材料批次、容量与结果装配链路。
- `EmakiStrengthen`：默认强化配方位于 `recipes/*.yml`（内置 `example_recipe.yml` 与 `example_branch_recipe.yml`），强化定义目录为 `enhancements/`，广播与全局成功率位于 `config.yml`。
- `EmakiGem`：宝石、装备模板、开孔器与 GUI 资源位于 `gems/`、`items/`、`gui/gem/`、`gui/open/`、`gui/upgrade/` 与 `config.yml`。
- `EmakiLevel`：等级类型、经验来源与升级需求位于 `types/`、`sources/`、`requirements.yml` 与 `config.yml`。
- `EmakiSkills`：技能、GUI、主动/被动触发器配置位于 `skills/`、`gui/` 与 `config.yml`，技能效果本体通过 MythicMobs 配置桥接；技能配置可通过 `variables` 注入 `<skill.var.xxx>`，并使用与宝石/强化一致的 `upgrade` 风格配置技能升级。
- `EmakiCooking`：配方按工位分目录位于 `recipes/<station>/`（砧板、炒锅、研磨机、蒸锅、烤炉、榨汁机、发酵桶），另有 `nutrition/`、`item_adjustments/` 与 `gui/`；工位运行时状态保存在数据目录的 `data/stations/`。
- `EmakiCodex`：图鉴、分类、进度规则与奖励资源位于模块配置目录及对应 definitions 文件中。

## 旧版 configured-item YAML 自动迁移

共享 GUI YAML 和 EmakiItem `items/*.{yml,yaml}` 会在正式加载、解析之前迁移旧版物品字段：

- 旧 `material`、`item_source` / `item_sources`、`display_name`、`item_name`、`lore`、`custom_model_data`、`enchantments`、`item_flags`、`hidden_components` 等会转换为 `item.source`、`item.amount` 与 `item.components`。
- 迁移为纯 YAML 节点转换，不创建 Bukkit `ItemStack`；现代字段始终优先，混合配置不会被旧值覆盖。
- 包含 `components.raw` 的节点会安全跳过；`effects`、`repair`、`update`、`actions` 等非目标业务字段保持不变。
- 每批修改都会先按源目录相对路径保存原文备份，再把结果写入临时文件并重新解析校验；只有校验成功才替换原文件。
- 备份目录位于对应插件数据目录的 `migration-backups/configured-item-format/<时间戳>/`。迁移失败时会报告错误并保留或恢复原文件。

迁移是幂等的；迁移后的文件再次启动不会重复改写。不过正式服升级前仍建议先备份完整的 `plugins/Emaki*/` 配置目录。

这是一层物理隔离的过渡兼容实现：它不读取或比较插件、配置、语言文件中的版本号，只在检测到旧字段时执行。CoreLib 的 `corelib.action.legacy` 包提供通用迁移执行器与行转换器（`LegacyActionMigrator`、`LegacyLineConverter`），旧 configured-item definition 的转换规则由 `corelib.item.LegacyConfiguredItemConverter` 承担；生产桥接只位于 GUI 模板加载与 EmakiItem definition 加载入口，未来结束旧格式兼容时可整体删除，不影响 canonical `item.*` parser、model 或 YAML 基础设施。

## EmakiItem 套装状态

套装 membership 的可见性与合法激活件数分开计算：物品只要声明了可用套装 ID，就会保留该套装的 `0/N` 状态；只有真实解析到套装部件且同时匹配物品 `equip_slot` 与部件 `slot` 的装备才计入激活件数。缺失套装定义会被安全隔离并写入 `set` DEBUG，不会制造空状态、套装效果或破坏性写回。

## EmakiSkills 技能参数与升级

- `variables` 用于配置释放技能时注入 MythicMobs 的变量，MythicMobs 技能中推荐使用 `<skill.var.damage>`、`<skill.var.radius>`、`<skill.var.emaki_skill_level>` 读取。
- `upgrade` 是技能升级的唯一配置入口，字段风格对齐 `EmakiGem` / `EmakiStrengthen`：`enabled`、`max_level`、`gui_template`、`economy.currencies`、`success_rates`、`failure_penalty`、`levels`。
- `upgrade.levels.<target_level>` 表示升级到该等级的材料、经济覆盖、成功率覆盖、成功/失败动作，以及可选的参数覆盖。
- 玩家技能等级由 `EmakiSkills` 持久化到玩家档案的 `skill_levels.<skill_id>.level`，未开启升级的技能等级固定为 `1`。
- 第一版升级入口提供命令 `/eskills upgrade <skill>`；管理命令为 `/eskills level get|set|add <player> <skill> [value]`。

## 构建方式

在仓库根目录执行：

```bash
mvn clean package
```

常用本地编译检查：

```bash
mvn -DskipTests compile
```

构建完成后，各模块产物会输出到对应模块的 `target/` 目录。运行时插件 Jar 才需要放入服务器 `plugins/`；`emaki-*-api` 与 `emaki-skills-protocol` 属于编译期构件，不要部署。

> Windows / IDEA 环境中如果系统 PATH 没有 `mvn`，可以使用 IDEA 自带 Maven，或通过本地管理脚本执行对应构建流程。

## 文档

- 项目文档：[Emaki Series Docs](https://jiuwu02.github.io/Emaki_Series/)

## 开源许可

本项目采用 `GNU General Public License v3.0` 开源发布。

---

> [!NOTE]
> 本项目 Wiki 文档由 Codex 阅读项目源代码后整理生成，使用模型为 GPT-5.6-SOL MAX 模式。
> 如有问题，请联系项目维护者，或在 Discord 服务器中反馈。

[Join Discord Community](https://discord.gg/FV4GFQbvCM) | [QQ Group](https://qm.qq.com/q/GqGrzHp0wU)
