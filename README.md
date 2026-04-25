# Emaki Series

Emaki Series 是一组面向 Minecraft Spigot 服务端的 Java 插件，采用多模块 Maven 工程组织。项目以 `EmakiCoreLib` 为共享基础库，向上承载属性战斗、锻造、强化、烹饪与装备宝石等 RPG 玩法模块。

当前工作区的当前版本线为：`EmakiCoreLib / EmakiAttribute / EmakiForge / EmakiStrengthen = 3.2.0`、`EmakiCooking = 2.0.0`、`EmakiGem = 1.1.0`、`EmakiSkills = 1.0.0`。

## 模块概览

| 模块              | 当前版本 | 角色       | 说明                                                                                  |
| ----------------- | -------- | ---------- | ------------------------------------------------------------------------------------- |
| `EmakiCoreLib`    | `3.2.0`  | 核心基础库 | 提供 GUI、动作系统、物品源桥接、物品装配、表达式、YAML、PDC、经济桥接与共享运行时能力 |
| `EmakiAttribute`  | `3.2.0`  | 属性系统   | 提供 RPG 属性、三系伤害、资源状态、PDC 属性接入、条件检查、快照调试与战斗反馈能力     |
| `EmakiForge`      | `3.2.0`  | 锻造系统   | 提供配方驱动锻造、品质随机、材料贡献、图鉴、编辑器、结果组装与 PDC 属性写入能力       |
| `EmakiStrengthen` | `3.2.0`  | 强化系统   | 提供星级强化、成功率配置、锻印 / 里程碑、强化 GUI、材料消耗与强化层刷新能力           |
| `EmakiCooking`    | `2.0.0`  | 烹饪系统   | 提供砧板、炒锅、研磨机、蒸锅的 Java 化运行骨架，以及旧版 JiuWu's Kitchen 配置导入链路 |
| `EmakiGem`        | `1.1.0`  | 宝石系统   | 提供装备开孔、宝石镶嵌、取出、升级、装备模板、宝石定义与可选属性系统接入能力          |
| `EmakiSkills`     | `1.0.0`  | 技能系统   | 提供主动技能槽位、被动触发器、施法模式、冷却与 MythicMobs / Attribute 桥接能力        |

## 技术基线

| 项目       | 说明                               |
| ---------- | ---------------------------------- |
| Java       | `21`                               |
| 服务端 API | `Spigot API 1.21.11-R0.1-SNAPSHOT` |
| 文本组件   | `Adventure 4.26.1`                 |
| 构建工具   | Maven 多模块聚合                   |
| 许可证     | `GPL-3.0-only`                     |

## 模块关系

```text
EmakiCoreLib
├── EmakiAttribute
├── EmakiForge
├── EmakiStrengthen
├── EmakiCooking
├── EmakiGem
└── EmakiSkills
```

- `EmakiCoreLib` 是所有业务模块的强依赖，负责共享 GUI、动作、物品源、配置、经济、PDC 与运行时服务。
- `EmakiAttribute` 可独立提供属性与战斗计算，也可接收 Forge / Strengthen / Gem 通过 PDC 写入的属性。
- `EmakiForge` 通过 CoreLib 装配物品、读取材料来源，并可选写入 EmakiAttribute 属性数据。
- `EmakiStrengthen` 通过配方系统为装备附加强化层，并可选把强化属性接入 EmakiAttribute。
- `EmakiCooking` 通过 CoreLib 的物品源、动作系统、方块桥接与结构化展示能力承接四类厨具与旧版数据迁移。
- `EmakiGem` 通过装备模板、开孔器和宝石定义提供可组合装备成长，可选把宝石属性接入 EmakiAttribute。
- `EmakiSkills` 通过技能解锁、主动触发器、被动触发器、技能等级、技能传参与施法模式承接技能管理，并可桥接 MythicMobs 与 EmakiAttribute。

## 仓库结构

```text
Project/
├── EmakiCoreLib/        # 核心基础库
├── EmakiAttribute/      # 属性与战斗系统
├── EmakiForge/          # 锻造系统
├── EmakiStrengthen/     # 强化系统
├── EmakiCooking/        # 烹饪系统
├── EmakiGem/            # 装备宝石系统
├── EmakiSkills/         # 主动/被动技能系统（private-modules profile）
└── pom.xml              # Maven 父工程
```

## 默认资源定位

- `EmakiCoreLib`：共享配置位于 `EmakiCoreLib/src/main/resources/config.yml`，主要提供动作模板、经济、物品源与基础服务配置。
- `EmakiAttribute`：属性、条件与默认档案配置位于 `attributes/*.yml`、`conditions/*.yml` 与 `config.yml > default_profile`；旧的 `profiles/global.yml` 已不再作为默认基线入口。
- `EmakiForge`：锻造配方、GUI 与语言资源位于 `recipes/`、`gui/`、`lang/` 等目录，当前重点围绕材料批次、容量与结果装配链路。
- `EmakiStrengthen`：默认强化内容位于 `recipes/*.yml`，例如 `weapon_physical`、`weapon_projectile`、`weapon_spell`、`armor_guard`、`generic_visual`、`offhand_focus`。
- `EmakiCooking`：烹饪资源位于 `recipes/chopping_board/`、`recipes/wok/`、`recipes/grinder/`、`recipes/steamer/`、`gui/steamer.yml` 与 `data/stations/`。
- `EmakiGem`：宝石、装备模板、开孔器与 GUI 资源位于 `gems/`、`items/`、`gui/gem/`、`gui/open/`、`gui/upgrade/` 与 `config.yml`。
- `EmakiSkills`：技能、GUI、主动/被动触发器配置位于 `skills/`、`gui/` 与 `config.yml`，技能效果本体通过 MythicMobs 配置桥接；技能配置可通过 `skill_parameters` 注入 `<skill.var.xxx>`，并使用与宝石/强化一致的 `upgrade` 风格配置技能升级。

## EmakiSkills 技能参数与升级

- `skill_parameters` 用于配置释放技能时注入 MythicMobs 的变量，MythicMobs 技能中推荐使用 `<skill.var.damage>`、`<skill.var.radius>`、`<skill.var.emaki_skill_level>` 读取。
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

构建完成后，各模块产物会输出到对应模块的 `target/` 目录。

> Windows / IDEA 环境中如果系统 PATH 没有 `mvn`，可以使用 IDEA 自带 Maven，或通过本地管理脚本执行对应构建流程。

## 文档

- 项目文档：[Emaki Series Docs](https://jiuwu02.github.io/Emaki_Series/)


## 版本与发布策略

- 根 `pom.xml` 负责模块聚合、依赖版本和 Maven 插件配置，当前父工程版本为 `3.0.0`。
- 根级默认聚合模块当前为 `EmakiCoreLib`、`EmakiAttribute`、`EmakiForge`、`EmakiStrengthen`、`EmakiCooking`。
- `.key` 存在时会通过 `private-modules` profile 额外聚合 `EmakiGem` 与 `EmakiSkills`。
- 当前工作区版本线分别为：`EmakiCoreLib / EmakiAttribute / EmakiForge / EmakiStrengthen = 3.2.0`、`EmakiCooking = 2.0.0`、`EmakiGem = 1.1.0`、`EmakiSkills = 1.0.0`。

## 开源许可

本项目采用 `GNU General Public License v3.0` 开源发布。

---

> [!NOTE]
> 本项目 Wiki 文档由 Codex 阅读项目源代码后整理生成，使用模型为 GPT-5.4 xhigh 模式。
> 如有问题，请联系项目维护者，或在 Discord 服务器中反馈。

[Join Discord Community](https://discord.gg/FV4GFQbvCM) | [QQ Group](https://qm.qq.com/q/GqGrzHp0wU)
