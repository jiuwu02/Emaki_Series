## 更新摘要

- Emaki CoreLib 升级至 `1.3.0`，Action System 正式完成统一重构。
- Emaki Forge 升级至 `1.1`，配方结果、品质动作与 GUI 槽位配置完成新命名切换。
- 仓库新增并完善了统一的 Wiki / Release 发布工具，支持交互式输入与发布日志模板。

## 本次发布模块

- Emaki CoreLib: `1.3.0`
- Emaki Forge: `1.1`

## 主要更新

### Emaki CoreLib

- CoreLib 动作相关包、类、测试、占位符解析器与插件 API 已统一为 `emaki.jiuwu.craft.corelib.action` 与 `Action*` 命名。
- CoreLib 默认配置中的动作配置段已统一为 `action:`，调试开关与模板注册也同步改名。
- 新增 `<show_item>` 内联解析支持，便于 Forge 在 MiniMessage 消息中直接展示最终交付给玩家的成品悬浮物品。
- CoreLib 依赖补充了 NightCore 支持，并同步整理了 CoinsEngine / Vault 相关桥接逻辑。
- 消息输出行为做了调整，消息服务不再强制自动附加前缀。

### Emaki Forge

- Forge 已全面接入 CoreLib Action System，配方执行阶段改为使用 `action.pre`、`action.success`、`action.failure` 与 `result.action`。
- 配方 `result` 部分不再使用 `success_message` 和 `sound`，成功后的提示与音效现在统一通过 Action System 编排。
- 品质系统新增 `quality.item_meta.tiers.<tier>.action`，支持为每个品质配置多行动作，并可直接使用 `<show_item>` 展示最终成品。
- 默认品质池与保底规则已更新为当前新版方案：
  - 概率顺序：`平庸 > 精良 > 有缺 > 优质 > 无暇 > 完美`
  - 保底次数：`60`
  - 保底品质：`无暇`
- 默认配置中已为 `无暇` 与 `完美` 提供全服广播示例，便于直接接入服务器公告效果。
- Forge GUI 槽位定义字段已从 `action` 改为 `type`，默认 `config.yml` / `lang.yml` 版本号也同步更新到 `1.1`。

## 兼容性说明

- 运行环境：Paper `1.21+`
- Java 版本：`21`
- Emaki Forge `1.1` 依赖 Emaki CoreLib `1.3.0`
- 本次为不兼容命名升级，旧版动作系统 API 与旧配置键名不再保留兼容

## 升级提示

- 如果有自定义插件、扩展或二次开发代码，请将 CoreLib 动作相关导入统一切换到 `emaki.jiuwu.craft.corelib.action.*`。
- 如果有自定义 CoreLib 配置，请将动作配置段统一使用 `action:`。
- 如果有自定义 Forge 配置，请重点检查以下字段：
  - 配方执行：统一使用 `action`
  - 配方结果：统一使用 `result.action`
  - 品质动作：统一使用 `quality.item_meta.tiers.<tier>.action`
  - GUI 槽位：`action` -> `type`
- 建议先升级 Emaki CoreLib `1.3.0`，再升级 Emaki Forge `1.1`，并在替换前备份现有配置文件。

## 已知说明

- 无
