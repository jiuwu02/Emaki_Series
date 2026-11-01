// EmakiAttribute 脚本属性示例（attributes 绑定）。
// attributes.register({...}) 以字段声明一个属性定义，注册后与 attributes/*.yml 中的属性同权：
// 可被物品 ea_attribute 效果、Lore 解析、占位符与属性快照收集消费。
//
// 字段说明（与 attributes/*.yml schema 对齐）：
//   id                   必填，属性 ID（同 ID 重复注册时后注册者覆盖并在报告中提示）。
//   display_name         显示名，作为 Lore 关键字与别名匹配的基础。
//   value_kind           数值类型：FLAT / PERCENT / CHANCE / REGEN / RESOURCE / DERIVED。
//   target_type          目标类型：DAMAGE / RESOURCE / GENERIC / VANILLA。
//   target_id            目标 ID（资源或原版属性标识，可选）。
//   mmoitems_stat        MMOItems 统计项映射（可选）。
//   default_value        默认值；min_value / max_value 为可选上下限。
//   allow_negative       是否允许负值，默认 true。
//   priority             Lore 读取优先级，数值越大越优先，默认 0。
//   lore_format_id       词条格式（default_flat / default_percent / default_regen / default_resource）。
//   lore_patterns        自定义 Lore 正则模板（可选，%Key% 与 %Value% 占位）。
//   description          描述文本（可选）。
//   attribute_power      战力系数，默认 1。
//   tags                 标签列表（可选）。
//   temporary_stack_mode 临时属性叠加模式：REPLACE / STACK。
//
// 本文件随插件首次释放；已存在的 scripts/ 目录不会被升级覆盖，可自由修改或删除。
attributes.register({
    id: "script_example_mana_cost",
    display_name: "施法消耗减免",
    value_kind: "FLAT",
    target_type: "GENERIC",
    target_id: "mana_cost",
    default_value: 0.0,
    min_value: 0.0,
    max_value: 100.0,
    allow_negative: false,
    priority: 10,
    lore_format_id: "default_flat",
    lore_patterns: ["%Key%.*?: ?%Value%$"],
    description: "脚本示例属性：降低施法消耗。",
    tags: ["script", "example"],
    temporary_stack_mode: "REPLACE"
});
