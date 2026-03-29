# Emaki Series

Emaki Series 是一组面向 Minecraft Paper 服务端的 Java 插件，采用多模块 Maven 工程组织。项目当前包含公共基础库 `Emaki_CoreLib`、功能插件 `Emaki_Forge` 与属性系统 `Emaki_Attribute`，运行时插件名分别为 `EmakiCoreLib`、`EmakiForge` 与 `EmakiAttribute`，用于承载通用运行时能力、锻造系统业务能力与 RPG 战斗属性能力。

## 模块概览

| 模块               | 当前版本    | 角色   | 说明                                                           |
| ---------------- | ------- | ---- | ------------------------------------------------------------ |
| `EmakiCoreLib`   | `1.9.0` | 基础库  | 提供 GUI、可扩展物品源、Emaki 物品装配、表达式、条件评估、动作系统、占位符、YAML 工具与经济桥接等通用能力 |
| `EmakiForge`     | `1.8.0` | 功能插件 | 基于 CoreLib 构建的锻造系统，当前结果重组、配方索引与 GUI 会话管理已进一步重构               |
| `EmakiAttribute` | `1.6.0` | 属性系统 | 提供 RPG 属性、伤害协议层、属性平衡与战斗反馈能力，并补入快照服务与缓存统计能力                   |

## 技术基线

| 项目        | 说明                      |
| --------- | ----------------------- |
| Java      | 21                      |
| Paper API | `1.21.11-R0.1-SNAPSHOT` |

## 版本策略

项目采用“父工程聚合、子模块独立版本”的方式维护版本：

- 根 `pom.xml` 仅负责模块聚合、依赖版本和插件配置。
- `Emaki_CoreLib`、`Emaki_Forge` 与 `Emaki_Attribute` 分别维护各自的 `project.version`。
- 三个功能模块通过父工程统一管理 `emaki.corelib.version` 与公共依赖版本。

这意味着 `CoreLib` 与 `Forge` 可以在不同发布节奏下独立构建和演进。

## 仓库结构

```text
Project/
├── EmakiCoreLib/   # 核心基础库
├── EmakiForge/     # 锻造系统插件
├── EmakiAttribute/ # 属性与战斗系统
├── release-notes.md # 当前工作区发布日志
├── pom.xml          # 聚合父工程
└── README.md
```

## 物品源格式

CoreLib 当前使用统一物品源抽象，常见写法如下：

- Vanilla：`diamond_sword`
- NeigeItems：`ni-example_sword`
- CraftEngine：`ce-namespace:item`
- MMOItems：`mi-sword:cutlass`

## 当前重构重点

- `EmakiCoreLib` 已把动作模板展开、调度分发、物品渲染、装配数据读写拆分为独立协作类。
- `EmakiAttribute` 已补入 `AttributeSnapshotService` 与缓存命中率统计，进一步收敛快照职责。
- `EmakiForge` 已把查找索引、GUI 会话、结果模型从超大服务类中抽离，降低后续继续拆分的阻力。

## 文档

- 项目 Wiki: [Emaki Series Wiki](https://github.com/jiuwu02/Emaki_Series/wiki)
- 发布日志: [release-notes.md](release-notes.md)
- 问题反馈: [GitHub Issues](https://github.com/jiuwu02/Emaki_Series/issues)

## 开源许可

本项目采用 `GNU General Public License v3.0` 开源发布。

- 许可文件位置：`LICENSE`
- SPDX 标识：`GPL-3.0-only`

