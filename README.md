# Emaki Series

Emaki Series 是一组面向 Minecraft Paper 服务端的 Java 插件，采用多模块 Maven 工程组织。当前仓库包含四个运行时模块：`EmakiCoreLib`、`EmakiAttribute`、`EmakiForge`、`EmakiStrengthen`，分别承载基础设施、属性战斗、锻造系统与强化成长能力。

## 模块概览

| 模块              | 当前版本 | 角色     | 说明                                                                           |
| ----------------- | -------- | -------- | ------------------------------------------------------------------------------ |
| `EmakiCoreLib`    | `2.3.0`  | 基础库   | 提供 GUI、动作系统、物品源桥接、物品装配、表达式、YAML 与经济桥接等通用能力    |
| `EmakiAttribute`  | `2.2.0`  | 属性系统 | 提供 RPG 属性、伤害协议层、资源状态、快照调试与战斗反馈能力                    |
| `EmakiForge`      | `2.2.0`  | 锻造系统 | 提供配方驱动的锻造 DSL、图鉴、编辑器、材料校验与结果组装能力                   |
| `EmakiStrengthen` | `2.0.0`  | 强化系统 | 提供 recipe 驱动的星级成长、锻印机制、强化 GUI、多货币成本与跨模块物品重建能力 |

## 技术基线

| 项目      | 说明                    |
| --------- | ----------------------- |
| Java      | 21                      |
| Paper API | `1.21.11-R0.1-SNAPSHOT` |
| 构建      | Maven 多模块聚合        |

## 版本策略

- 根 `pom.xml` 负责模块聚合、依赖版本和构建插件配置。
- 子模块分别维护各自的 `project.version`。
- 当前父工程版本为 `2.0.0`，并统一管理 `emaki.corelib.version` 与第三方依赖版本。

## 仓库结构

```text
Project/
├── EmakiCoreLib/      # 核心基础库
├── EmakiAttribute/    # 属性与战斗系统
├── EmakiForge/        # 锻造系统
├── EmakiStrengthen/   # 强化系统
├── PROJECT_OVERVIEW.md
├── release-notes.md
└── pom.xml
```

## 默认资源定位

- `EmakiStrengthen` 当前默认强化内容位于 `recipes/*.yml`，例如 `weapon_physical`、`weapon_projectile`、`weapon_spell`、`armor_guard`、`generic_visual`、`offhand_focus`。
- `EmakiForge` 当前默认锻造示例也以 `recipes/*.yml` 为主；`blueprints/` 与 `materials/` 目录仍保留，但默认样例已退出主流程。

## 文档

- 项目功能总览：`PROJECT_OVERVIEW.md`
- 当前工作区发布通知：`release-notes.md`
- 模块当前版更新日志：各模块目录下 `CHANGELOG.md` / `CHANGELOG.en-US.md`
- 项目 Wiki: [Emaki Series Wiki](https://github.com/jiuwu02/Emaki_Series/wiki)

## 开源许可

本项目采用 `GNU General Public License v3.0` 开源发布。

---

> \[!NOTE] 注意  
> 本项目的Wiki文档全权由Codex阅读项目源代码制作而成的，使用的模型为 GPT5.4 xhigh 模式制作  
> 如有问题，请联系项目维护者或在Discord服务器中联系

[Join Discord Community](https://discord.gg/FV4GFQbvCM) | [QQ Group](https://qm.qq.com/q/GqGrzHp0wU)