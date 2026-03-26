# Emaki Series

Emaki Series 是一组面向 Minecraft Paper 服务端的 Java 插件，采用多模块 Maven 工程组织。项目当前包含公共基础库 `Emaki_CoreLib` 与功能插件 `Emaki_Forge`，用于承载通用运行时能力与锻造系统业务能力。

## 模块概览

| 模块 | 当前版本 | 角色 | 说明 |
|------|----------|------|------|
| `Emaki_CoreLib` | `1.1.0` | 基础库 | 提供 GUI、物品源、表达式、条件评估、操作系统、占位符与经济桥接等通用能力 |
| `Emaki_Forge` | `1.0.0` | 功能插件 | 基于 CoreLib 构建的锻造系统，包含图纸、材料、配方、品质与 GUI 交互能力 |

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
```

构建产物默认输出到各模块的 `target/` 目录。

## 版本策略

项目采用“父工程聚合、子模块独立版本”的方式维护版本：

- 根 `pom.xml` 仅负责模块聚合、依赖版本和插件配置。
- `Emaki_CoreLib` 与 `Emaki_Forge` 分别维护各自的 `project.version`。
- `Emaki_Forge` 通过 `emaki.corelib.version` 指定依赖的 CoreLib 版本。

这意味着 `CoreLib` 与 `Forge` 可以在不同发布节奏下独立构建和演进。

## 仓库结构

```text
Project/
├── Emaki_CoreLib/   # 核心基础库
├── Emaki_Forge/     # 锻造系统插件
├── pom.xml          # 聚合父工程
└── README.md
```

## 文档

- 项目 Wiki: [Emaki Series Wiki](https://github.com/jiuwu02/Emaki_Series/wiki)
- 问题反馈: [GitHub Issues](https://github.com/jiuwu02/Emaki_Series/issues)

## 发布工具

仓库根目录提供统一的 Python 工具：

```powershell
python .\emaki_manager.py <command>
```

### Wiki 同步与发布

同步外部 Wiki 目录到本地 `wiki/`：

```powershell
python .\emaki_manager.py sync-wiki --allow-missing-source
```

将本地 `wiki/` 直接发布到 GitHub Wiki：

```powershell
python .\emaki_manager.py publish-wiki
```

### Release 自动发布

发布命令：

```powershell
$env:GH_TOKEN="你的GitHub令牌"
python .\emaki_manager.py publish-release
```

脚本行为：

- 自动读取 `Emaki_CoreLib` 与 `Emaki_Forge` 当前 `pom.xml` 版本号
- 自动检查 GitHub Releases 中已经发布过的 jar 资产
- 如果两个模块当前版本都未发布，则在同一个 Release 中上传两个 jar
- 如果只有一个模块版本未发布，则只上传该模块的 jar
- 如果当前版本都已经发布，则不创建新 Release

调试模式：

```powershell
python .\emaki_manager.py publish-release --dry-run
```

`publish-release` 依赖 GitHub REST API。建议使用 `GH_TOKEN` 或 `GITHUB_TOKEN` 环境变量，以避免匿名请求限流。

## 开源许可

本项目采用 `GNU General Public License v3.0` 开源发布。

- 许可文件位置：`LICENSE`
- SPDX 标识：`GPL-3.0-only`
