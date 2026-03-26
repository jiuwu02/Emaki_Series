# Emaki Plugin Series

欢迎来到 **Emaki Plugin Series** 官方 Wiki！这是一套专为 Minecraft Paper 服务器设计的高质量插件系列，旨在为服主提供强大、灵活且易于配置的游戏功能扩展。

---

## 插件系列概览

| 插件 | 类型 | 描述 |
|------|------|------|
| **[Emaki CoreLib](./Emaki_CoreLib/01-项目概述)** | 核心库 | 基础功能模块，为其他插件提供统一 API |
| **[Emaki Forge](./Emaki_Forge/01-项目概述)** | 功能插件 | 自定义锻造系统，支持图纸、材料、配方 |

---

## 快速导航

### Emaki CoreLib

CoreLib 是整个 Emaki 系列的基石，封装了 GUI 系统、物品源管理、表达式引擎、条件评估器、操作管理等核心功能。

| 章节 | 说明 |
|------|------|
| [项目概述](./Emaki_CoreLib/01-项目概述) | 插件简介、技术规格、模块架构 |
| [安装指南](./Emaki_CoreLib/02-安装指南) | 系统要求、安装步骤、配置说明 |
| [GUI 系统](./Emaki_CoreLib/03-GUI系统) | GUI 框架使用指南 |
| [物品源系统](./Emaki_CoreLib/04-物品源系统) | 统一的物品管理方案 |
| [表达式引擎](./Emaki_CoreLib/05-表达式引擎) | 数学表达式计算与随机分布 |
| [条件评估器](./Emaki_CoreLib/06-条件评估器) | 条件判断系统 |
| [数学工具](./Emaki_CoreLib/07-数学工具) | 数值处理与随机数生成 |
| [操作系统](./Emaki_CoreLib/08-操作系统) | 操作注册、执行与内置操作 |
| [API 接口文档](./Emaki_CoreLib/09-API接口文档) | 完整的开发者接口参考 |
| [常见问题](./Emaki_CoreLib/10-常见问题) | 问题解答与故障排查 |
| [使用示例](./Emaki_CoreLib/11-使用示例) | 完整代码示例 |

### Emaki Forge

Forge 是基于 CoreLib 构建的自定义锻造系统，支持图纸、材料、配方、品质等完整锻造工作流。

| 章节 | 说明 |
|------|------|
| [项目概述](./Emaki_Forge/01-项目概述) | 插件简介、功能特性、模块架构 |
| [安装指南](./Emaki_Forge/02-安装指南) | 安装步骤、配置文件结构 |
| [图纸系统](./Emaki_Forge/03-图纸系统) | 图纸配置与使用 |
| [材料系统](./Emaki_Forge/04-材料系统) | 材料定义与属性贡献 |
| [配方系统](./Emaki_Forge/05-配方系统) | 配方创建与条件判断 |
| [品质系统](./Emaki_Forge/06-品质系统) | 品质等级与保底机制 |
| [GUI 配置](./Emaki_Forge/07-GUI配置) | 锻造界面与配方图鉴 |
| [API 接口文档](./Emaki_Forge/08-API接口文档) | 开发者接口参考 |
| [常见问题](./Emaki_Forge/09-常见问题) | 问题解答与故障排查 |
| [使用示例](./Emaki_Forge/10-使用示例) | 完整配置示例 |

---

## 技术要求

| 项目 | 最低要求 | 推荐版本 |
|------|---------|---------|
| Minecraft | 1.21 | 1.21+ |
| 服务端 | Paper | Paper (最新版) |
| Java | 21 | 21+ |

---

## 依赖关系

```
Emaki Forge ──────► Emaki CoreLib ──────► Paper API 1.21+
      │                    │
      │                    ├── exp4j (表达式解析)
      │                    └── PlaceholderAPI (可选)
      │
      ├── NeigeItems (可选)
      └── CraftEngine (可选)
```

---

## 安装顺序

1. 安装 **Emaki CoreLib** (必需)
2. 安装 **Emaki Forge** (可选)
3. 安装可选依赖插件 (NeigeItems、CraftEngine、PlaceholderAPI 等)

---

## 支持与反馈

- **作者**：JiuWu
- **项目地址**：Emaki Plugin Series
- **问题反馈**：请通过项目 Issue 提交

---

## 版本兼容性

| Emaki CoreLib | Emaki Forge | Paper API | Minecraft |
|---------------|-------------|-----------|-----------|
| 1.0.0 | 1.0.0 | 1.21+ | 1.21+ |

---

## 开始使用

- **新手用户**：建议从 [Emaki CoreLib 安装指南](./Emaki_CoreLib/02-安装指南) 开始
- **服主配置**：直接查看 [Emaki Forge 项目概述](./Emaki_Forge/01-项目概述)
- **开发者**：参考 [Emaki CoreLib API 文档](./Emaki_CoreLib/09-API接口文档)
