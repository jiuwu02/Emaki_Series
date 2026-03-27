# Emaki Series

Emaki Series 是一组面向 Minecraft Paper 服务端的 Java 插件，采用多模块 Maven 工程组织。项目当前包含公共基础库 `Emaki_CoreLib`、功能插件 `Emaki_Forge` 与属性系统 `Emaki_Attribute`，用于承载通用运行时能力、锻造系统业务能力与 RPG 战斗属性能力。

## 模块概览

| 模块 | 当前版本 | 角色 | 说明 |
|------|----------|------|------|
| `Emaki_CoreLib` | `1.5.0` | 基础库 | 提供 GUI、物品源、表达式、条件评估、动作系统、占位符、YAML 工具与经济桥接等通用能力 |
| `Emaki_Forge` | `1.4.0` | 功能插件 | 基于 CoreLib 构建的锻造系统，包含图纸、材料、配方、品质与 GUI 交互能力 |
| `Emaki_Attribute` | `1.1.0` | 属性系统 | 提供 RPG 属性、伤害协议层、属性平衡与战斗反馈能力 |

## 技术基线

| 项目 | 说明 |
|------|------|
| Java | 21 |
| Maven | 3.9+ |
| Paper API | `1.21.11-R0.1-SNAPSHOT` |
| 单元测试 | JUnit 5 |

## 构建要求

开始构建前，请确认本地环境已安装 JDK 21 与 Maven。

```powershell
mvn -pl Emaki_CoreLib clean package
mvn -pl Emaki_Forge -am clean package
mvn -pl Emaki_Attribute -am clean package
```

构建产物默认输出到各模块的 `target/` 目录。

## 版本策略

项目采用“父工程聚合、子模块独立版本”的方式维护版本：

- 根 `pom.xml` 仅负责模块聚合、依赖版本和插件配置。
- `Emaki_CoreLib`、`Emaki_Forge` 与 `Emaki_Attribute` 分别维护各自的 `project.version`。
- 三个功能模块通过父工程统一管理 `emaki.corelib.version` 与公共依赖版本。

这意味着 `CoreLib` 与 `Forge` 可以在不同发布节奏下独立构建和演进。

## 仓库结构

```text
Project/
├── Emaki_CoreLib/   # 核心基础库
├── Emaki_Forge/     # 锻造系统插件
├── Emaki_Attribute/ # 属性与战斗系统
├── pom.xml          # 聚合父工程
└── README.md
```

## 文档

- 项目 Wiki: [Emaki Series Wiki](https://github.com/jiuwu02/Emaki_Series/wiki)
- 问题反馈: [GitHub Issues](https://github.com/jiuwu02/Emaki_Series/issues)

## 开源许可

本项目采用 `GNU General Public License v3.0` 开源发布。

- 许可文件位置：`LICENSE`
- SPDX 标识：`GPL-3.0-only`
