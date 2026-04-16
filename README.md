# Emaki Series

Emaki Series 是一组面向 Minecraft Spigot / Paper / Leaf 服务端的 Java 插件，采用多模块 Maven 工程组织。项目以 `EmakiCoreLib` 为共享基础库，向上承载属性战斗、锻造、强化与装备宝石等 RPG 玩法模块。

当前工作区的主要版本线为 `3.1.0`，并新增 `EmakiGem 1.0.0` 作为装备宝石模块。

## 模块概览

| 模块              | 当前版本 | 角色       | 说明                                                                                  |
| ----------------- | -------- | ---------- | ------------------------------------------------------------------------------------- |
| `EmakiCoreLib`    | `3.1.0`  | 核心基础库 | 提供 GUI、动作系统、物品源桥接、物品装配、表达式、YAML、PDC、经济桥接与共享运行时能力 |
| `EmakiAttribute`  | `3.1.0`  | 属性系统   | 提供 RPG 属性、三系伤害、资源状态、PDC 属性接入、条件检查、快照调试与战斗反馈能力     |
| `EmakiForge`      | `3.1.0`  | 锻造系统   | 提供配方驱动锻造、品质随机、材料贡献、图鉴、编辑器、结果组装与 PDC 属性写入能力       |
| `EmakiStrengthen` | `3.1.0`  | 强化系统   | 提供星级强化、成功率配置、锻印 / 里程碑、强化 GUI、材料消耗与强化层刷新能力           |
| `EmakiGem`        | `1.0.0`  | 宝石系统   | 提供装备开孔、宝石镶嵌、取出、升级、装备模板、宝石定义与可选属性系统接入能力          |

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
└── EmakiGem
```

- `EmakiCoreLib` 是所有业务模块的强依赖，负责共享 GUI、动作、物品源、配置、经济、PDC 与运行时服务。
- `EmakiAttribute` 可独立提供属性与战斗计算，也可接收 Forge / Strengthen / Gem 通过 PDC 写入的属性。
- `EmakiForge` 通过 CoreLib 装配物品、读取材料来源，并可选写入 EmakiAttribute 属性数据。
- `EmakiStrengthen` 通过配方系统为装备附加强化层，并可选把强化属性接入 EmakiAttribute。
- `EmakiGem` 通过装备模板、开孔器和宝石定义提供可组合装备成长，可选把宝石属性接入 EmakiAttribute。

## 仓库结构

```text
Project/
├── EmakiCoreLib/        # 核心基础库
├── EmakiAttribute/      # 属性与战斗系统
├── EmakiForge/          # 锻造系统
├── EmakiStrengthen/     # 强化系统
├── EmakiGem/            # 装备宝石系统
└── pom.xml              # Maven 父工程
```

## 默认资源定位

- `EmakiCoreLib`：共享配置位于 `EmakiCoreLib/src/main/resources/config.yml`，主要提供动作模板、经济、物品源与基础服务配置。
- `EmakiAttribute`：属性、条件与默认档案配置位于 `attributes/*.yml`、`conditions/*.yml` 与 `config.yml > default_profile`；旧的 `profiles/global.yml` 已不再作为默认基线入口。
- `EmakiForge`：锻造配方、GUI 与语言资源位于 `recipes/`、`gui/`、`lang/` 等目录，当前重点围绕材料批次、容量与结果装配链路。
- `EmakiStrengthen`：默认强化内容位于 `recipes/*.yml`，例如 `weapon_physical`、`weapon_projectile`、`weapon_spell`、`armor_guard`、`generic_visual`、`offhand_focus`。
- `EmakiGem`：宝石、装备模板、开孔器与 GUI 资源位于 `gems/`、`items/`、`gui/gem/`、`gui/open/`、`gui/upgrade/` 与 `config.yml`。

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

- 项目 Wiki：[Emaki Series Wiki](https://github.com/jiuwu02/Emaki_Series/wiki)


## 版本与发布策略

- 根 `pom.xml` 负责模块聚合、依赖版本和 Maven 插件配置，当前父工程版本为 `3.0.0`。
- 子模块分别维护各自 `project.version`，当前主要业务模块为 `3.1.0`，宝石模块为 `1.0.0`。

## 开源许可

本项目采用 `GNU General Public License v3.0` 开源发布。

---

> [!NOTE]
> 本项目 Wiki 文档由 Codex 阅读项目源代码后整理生成，使用模型为 GPT-5.4 xhigh 模式。
> 如有问题，请联系项目维护者，或在 Discord 服务器中反馈。

[Join Discord Community](https://discord.gg/FV4GFQbvCM) | [QQ Group](https://qm.qq.com/q/GqGrzHp0wU)