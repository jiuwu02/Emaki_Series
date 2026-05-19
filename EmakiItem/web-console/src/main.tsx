import { ItemEditorSurface, registerConfigNodeMeta, registerConfigNodeRule, registerEditorDescriptor, registerEditorField, registerModuleLocale, registerPluginSurfaces } from 'emaki-web-console';

const MODULE = 'EmakiItem';
const EDITOR_ID = 'emakiitem:item';
const SET_EDITOR_ID = 'emakiitem:set';

type ConfigSpec = [path: string, label: string, comment: string, type: string];
type ItemFieldSpec = [path: string, label: string, comment: string, type: string, extra?: Record<string, unknown>];

const RARITIES = ['common', 'uncommon', 'rare', 'epic'];
const CONDITION_TYPES = ['all_of', 'any_of'];
const ATTRIBUTE_OPERATIONS = ['add_number', 'add_scalar', 'multiply_scalar_1'];
const EQUIPMENT_SLOTS = ['any', 'hand', 'mainhand', 'offhand', 'head', 'chest', 'legs', 'feet', 'body'];
const ITEM_FLAGS = ['HIDE_ENCHANTS', 'HIDE_ATTRIBUTES', 'HIDE_UNBREAKABLE', 'HIDE_DESTROYS', 'HIDE_PLACED_ON', 'HIDE_ADDITIONAL_TOOLTIP', 'HIDE_DYE', 'HIDE_ARMOR_TRIM'];
const SET_SLOTS = ['main_hand', 'off_hand', 'helmet', 'chestplate', 'leggings', 'boots'];

const configFields: ConfigSpec[] = [
  ['version', '配置版本', '默认配置结构版本，通常不建议手动修改。', 'text'],
  ['language', '语言', '语言文件 ID，对应 lang/<language>.yml。', 'text'],
  ['release_default_data', '释放默认数据', '首次启动或缺失 items/、sets/ 等示例数据时是否释放默认文件。', 'boolean'],
  ['set_bonus', '套装加成', '套装定义和物品所属套装/部位联动后的加成系统配置。', 'object'],
  ['set_bonus.enabled', '启用套装', '是否启用套装加成系统。关闭后物品仍可加载，但不会计算套装件数和加成。', 'boolean'],
  ['set_bonus.refresh_triggers', '刷新触发', '玩家装备、背包、拾取、交互或命令变化时重新计算套装状态的触发开关。', 'object'],
  ['set_bonus.refresh_triggers.join', '进服刷新', '玩家进入服务器时刷新套装状态。', 'boolean'],
  ['set_bonus.refresh_triggers.held_change', '切换手持', '玩家切换手持物品时刷新套装状态。', 'boolean'],
  ['set_bonus.refresh_triggers.inventory_click', '背包点击', '玩家点击背包时刷新套装状态。', 'boolean'],
  ['set_bonus.refresh_triggers.inventory_drag', '背包拖拽', '玩家拖拽物品时刷新套装状态。', 'boolean'],
  ['set_bonus.refresh_triggers.pickup', '拾取物品', '玩家拾取物品时刷新套装状态。', 'boolean'],
  ['set_bonus.refresh_triggers.interact', '交互刷新', '玩家交互时刷新套装状态。', 'boolean'],
  ['set_bonus.refresh_triggers.command', '命令刷新', '执行 EmakiItem 相关命令时允许触发套装刷新。', 'boolean']
];

const commonItemFields: Record<string, [string, string, string]> = {
  id: ['物品 ID', '物品定义的唯一标识。', 'text'],
  material: ['材质', 'Bukkit Material，源码要求必须可解析且 isItem。', 'material'],
  display_name: ['显示名称', '物品显示名称，支持 MiniMessage 与变量占位。', 'text'],
  item_name: ['原版名称', 'item_name 组件，不支持变量，影响物品堆叠判断。', 'text'],
  lore: ['Lore', '物品说明文本，每行一条。', 'list'],
  effects: ['效果', '兼容写法，通过 type 区分 variables、ea_attribute、es_skill 等效果。', 'list'],
  variables: ['变量', '表达式变量，会与 ea_attributes 合并用于名称和 Lore 占位。', 'object'],
  ea_attributes: ['EA 属性', '写入 EmakiAttribute PDC 的属性数值。', 'object'],
  ea_attribute_meta: ['EA 属性元数据', '属性附加元信息，键会 normalize。', 'object'],
  es_skills: ['ES 技能', '装备时附加的 EmakiSkills 技能 ID。', 'list'],
  components: ['原版组件', 'Minecraft 1.21+ 物品组件。', 'object'],
  custom_model_data: ['模型数据', 'components.custom_model_data，可为数字或 1.21.4+ 复合结构。', 'object'],
  item_model: ['物品模型', '资源包 item model 标识。', 'text'],
  tooltip_style: ['Tooltip 样式', '资源包 tooltip_style 标识。', 'text'],
  enchantments: ['附魔', '附魔 ID 到等级的映射。', 'object'],
  item_flags: ['物品标志', 'Bukkit ItemFlag 固定值列表。', 'list'],
  hide_tooltip: ['隐藏 Tooltip', '是否完全隐藏物品提示框。', 'boolean'],
  unbreakable: ['不可破坏', '写入 unbreakable 组件。', 'boolean'],
  enchantment_glint_override: ['附魔光效覆盖', '强制开启或关闭附魔光效。', 'boolean'],
  max_stack_size: ['最大堆叠', '最大堆叠数量。', 'number'],
  rarity: ['稀有度', 'Minecraft Rarity 固定值。', 'enum'],
  damage: ['当前损伤', '物品当前 damage 组件。', 'number'],
  max_damage: ['最大耐久', '物品 max_damage 组件。', 'number'],
  enchantable: ['可附魔等级', '物品 enchantable 组件。', 'number'],
  attribute_modifiers: ['原版属性修饰符', 'Minecraft attribute_modifiers 组件列表。', 'list'],
  raw: ['Raw 组件', '直接传给 Bukkit 的原始组件字符串。', 'text'],
  set: ['所属套装', '套装 ID 与部件 ID。', 'object'],
  set_part: ['套装部位', '物品在套装中的部位 ID。', 'text'],
  conditions: ['装备条件', '不满足时属性不生效的条件表达式。', 'object'],
  update: ['更新策略', '已有物品的配置版本更新策略。', 'object'],
  repair: ['修复配置', '耐久耗尽后的材料修复与损坏外观。', 'object'],
  item_sources: ['物品来源', '可识别为该物品的来源列表。', 'list'],
  actions: ['动作', 'give、interact 等触发动作。', 'object']
};

const setEditorFields: ItemFieldSpec[] = [
  ['id', 'ID', '套装唯一标识，加载时会 normalize。', 'text'],
  ['display_name', '显示名称', '套装显示名称，支持 MiniMessage。', 'text'],
  ['pieces', '套装部件', '部件 ID 到 item、slot、display 的映射。', 'setPieces', { wide: true }],
  ['lore.header', '标题行', '套装 Lore 标题，支持 {set_name}、{active}、{total}。', 'text'],
  ['lore.equipped_format', '已装备格式', '已装备部件的 Lore 行格式。', 'text'],
  ['lore.missing_format', '缺失格式', '未装备部件的 Lore 行格式。', 'text'],
  ['lore.active_threshold_format', '已激活阈值格式', '已激活阈值 Lore 包装格式。', 'text'],
  ['lore.inactive_threshold_format', '未激活阈值格式', '未激活阈值 Lore 包装格式。', 'text'],
  ['lore.separator', '分隔行', '部件列表与阈值列表之间的分隔行。', 'text'],
  ['thresholds', '阈值效果', '达到指定装备件数时激活的 Lore、EA 属性和 ES 技能。', 'setThresholds', { wide: true }]
];

const itemEditorFields: ItemFieldSpec[] = [
  ['id', 'ID', '物品定义唯一标识，加载时会 normalize。', 'text'],
  ['material', 'Material', 'Bukkit Material，必须是原版物品。', 'material'],
  ['display_name', '显示名称', '支持 MiniMessage 与 {变量} 占位。', 'text'],
  ['item_name', '原版 item_name', '原版 item_name 组件，不参与变量渲染。', 'text'],
  ['lore', 'Lore', '支持 MiniMessage 与 {变量} 占位。', 'stringList', { wide: true }],
  ['update.enabled', '启用更新', '关闭时其余 update 字段不会生效。', 'boolean'],
  ['update.version', '配置版本', '启用更新时必须大于 0。', 'number'],
  ['update.preserve_amount', '保留数量', '更新已有物品时是否保留堆叠数量。', 'boolean'],
  ['update.preserve_damage', '保留损伤', '更新已有物品时是否保留当前 damage。', 'boolean'],
  ['update.preserve_unknown_attribute_sources', '保留未知属性源', '更新时是否保留未知来源属性。', 'boolean'],
  ['update.triggers.join', '进服检测', '玩家进服时检测更新。', 'boolean'],
  ['update.triggers.held_change', '切换手持', '切换手持物品时检测更新。', 'boolean'],
  ['update.triggers.inventory_click', '背包点击', '点击背包时检测更新。', 'boolean'],
  ['update.triggers.inventory_drag', '背包拖拽', '拖拽物品时检测更新。', 'boolean'],
  ['update.triggers.pickup', '拾取物品', '拾取物品时检测更新。', 'boolean'],
  ['update.triggers.interact', '交互', '交互时检测更新。', 'boolean'],
  ['update.triggers.command', '命令', '执行相关命令时检测更新。', 'boolean'],
  ['effects', 'effects', '兼容效果列表，按 type 分流为变量、属性、技能和名称/Lore 动作。', 'effects', { wide: true }],
  ['variables', '变量', '表达式变量映射。', 'map', { wide: true }],
  ['ea_attributes', 'EA 属性', 'EmakiAttribute 属性数值映射。', 'map', { wide: true }],
  ['ea_attribute_meta', 'EA 属性元数据', '属性元数据映射。', 'map', { wide: true }],
  ['es_skills', 'ES 技能', 'EmakiSkills 技能 ID 列表。', 'stringList', { wide: true }],
  ['components.custom_model_data', 'Custom Model Data', '数字或 1.21.4+ floats、flags、strings、colors 复合结构。', 'json', { wide: true }],
  ['components.item_model', 'Item Model', '资源包 item model 标识。', 'text'],
  ['components.tooltip_style', 'Tooltip Style', '资源包 tooltip_style 标识。', 'text'],
  ['components.enchantments', '附魔', '附魔 ID 到等级的映射。', 'map', { wide: true }],
  ['components.item_flags', 'ItemFlags', 'Bukkit ItemFlag 固定值。', 'multiEnum', { options: ITEM_FLAGS, optionLabelPrefix: 'itemFlag', wide: true }],
  ['components.hide_tooltip', '隐藏 Tooltip', '写入 hide_tooltip 组件。', 'boolean'],
  ['components.unbreakable', '不可破坏', '写入 unbreakable 组件。', 'boolean'],
  ['components.enchantment_glint_override', '附魔光效', '覆盖附魔光效。', 'boolean'],
  ['components.max_stack_size', '最大堆叠', 'max_stack_size 组件。', 'number'],
  ['components.rarity', '稀有度', 'Minecraft rarity 固定值。', 'enum', { options: RARITIES, optionLabelPrefix: 'rarity' }],
  ['components.damage', '当前损伤', 'damage 组件。', 'number'],
  ['components.max_damage', '最大耐久', 'max_damage 组件。', 'number'],
  ['components.enchantable', '可附魔等级', 'enchantable 组件。', 'number'],
  ['components.attribute_modifiers', '属性修饰符', '原版 attribute_modifiers 列表。', 'attributeModifiers', { wide: true }],
  ['components.raw', 'Raw 组件', '高级用法，直接传递给 Bukkit。', 'textarea', { wide: true, rows: 3 }],
  ['set.id', '套装 ID', '对应 sets/ 目录下的套装定义。', 'text'],
  ['set.piece', '套装部件', '此物品在套装中的部件标识。', 'text'],
  ['conditions.entries', '条件表达式', '支持玩家变量的条件表达式列表。', 'stringList', { wide: true }],
  ['conditions.type', '组合方式', '源码固定读取 all_of 或 any_of。', 'enum', { options: CONDITION_TYPES, optionLabelPrefix: 'conditionType' }],
  ['conditions.required_count', '需要满足数量', 'any_of 场景下需要满足的最少条件数。', 'number'],
  ['conditions.invalid_as_failure', '解析失败视为失败', '表达式解析异常时是否不通过。', 'boolean'],
  ['conditions.deny_message', '拒绝消息', '条件不满足时提示。', 'text'],
  ['conditions.deny_actions', '拒绝动作', '条件不满足时执行的动作。', 'stringList', { wide: true }],
  ['repair.enabled', '启用修复', '关闭时 repair 配置不生效。', 'boolean'],
  ['repair.materials', '修复材料', '修复材料列表，源码读取 item、amount、restore。', 'repairMaterials', { wide: true }],
  ['repair.disabled_display.name_prefix', '损坏名称前缀', '物品损坏时追加到名称前的 MiniMessage。', 'text'],
  ['repair.disabled_display.lore_append', '损坏 Lore', '物品损坏时追加的 Lore。', 'stringList', { wide: true }],
  ['repair.on_disabled', '损坏动作', '物品进入损坏状态时执行。', 'stringList', { wide: true }],
  ['repair.on_repaired', '修复动作', '物品被修复时执行。', 'stringList', { wide: true }],
  ['actions.give', '给予动作', '物品给予玩家时执行。', 'stringList', { wide: true }],
  ['actions.interact', '交互动作', '玩家使用物品交互时执行。', 'stringList', { wide: true }]
];

registerModuleLocale(MODULE, 'zh-CN', {
  'emakiitem.surface.item': 'EmakiItem 物品',
  'emakiitem.preview.aria': 'EmakiItem 物品预览',
  'emakiitem.preview.kind': '自定义物品',
  'emakiitem.section.basic': '基础字段',
  'emakiitem.section.update': '更新策略',
  'emakiitem.section.effects': '效果与变量',
  'emakiitem.section.components': '原版组件',
  'emakiitem.section.set': '套装归属',
  'emakiitem.section.conditions': '装备条件',
  'emakiitem.section.repair': '修复配置',
  'emakiitem.section.actions': '触发动作',
  ...Object.fromEntries(configFields.flatMap(([path, label, comment]) => [[`emakiitem.field.${path}`, label], [`emakiitem.comment.${path}`, comment]])),
  ...Object.fromEntries(Object.entries(commonItemFields).flatMap(([key, [label, comment]]) => [[`emakiitem.field.${key}`, label], [`emakiitem.comment.${key}`, comment]])),
  ...Object.fromEntries(itemEditorFields.flatMap(([path, label, comment]) => [[`emakiitem.field.${path}`, label], [`emakiitem.comment.${path}`, comment]])),
  ...Object.fromEntries(setEditorFields.flatMap(([path, label, comment]) => [[`emakiitem.field.${path}`, label], [`emakiitem.comment.${path}`, comment]])),
  'emakiitem.option.conditionType.all_of': '全部满足',
  'emakiitem.option.conditionType.any_of': '任一满足',
  'emakiitem.option.rarity.common': '普通',
  'emakiitem.option.rarity.uncommon': '罕见',
  'emakiitem.option.rarity.rare': '稀有',
  'emakiitem.option.rarity.epic': '史诗',
  'emakiitem.option.attributeOperation.add_number': '加法数值',
  'emakiitem.option.attributeOperation.add_scalar': '加法倍率',
  'emakiitem.option.attributeOperation.multiply_scalar_1': '乘法倍率 +1',
  'emakiitem.option.equipmentSlot.any': '任意',
  'emakiitem.option.equipmentSlot.hand': '任意手',
  'emakiitem.option.equipmentSlot.mainhand': '主手',
  'emakiitem.option.equipmentSlot.offhand': '副手',
  'emakiitem.option.equipmentSlot.head': '头盔',
  'emakiitem.option.equipmentSlot.chest': '胸甲',
  'emakiitem.option.equipmentSlot.legs': '护腿',
  'emakiitem.option.equipmentSlot.feet': '靴子',
  'emakiitem.option.equipmentSlot.body': '身体',
  'emakiitem.option.setSlot.main_hand': '主手',
  'emakiitem.option.setSlot.off_hand': '副手',
  'emakiitem.option.setSlot.helmet': '头盔',
  'emakiitem.option.setSlot.chestplate': '胸甲',
  'emakiitem.option.setSlot.leggings': '护腿',
  'emakiitem.option.setSlot.boots': '靴子'
});

registerModuleLocale(MODULE, 'en-US', {
  'emakiitem.surface.item': 'EmakiItem Item',
  'emakiitem.field.version': 'Config Version',
  'emakiitem.field.language': 'Language',
  'emakiitem.field.release_default_data': 'Release Default Data',
  'emakiitem.field.set_bonus': 'Set Bonuses',
  'emakiitem.field.set_bonus.enabled': 'Enable Sets',
  'emakiitem.field.set_bonus.refresh_triggers': 'Refresh Triggers',
  'emakiitem.field.display_name': 'Display Name',
  'emakiitem.field.lore': 'Lore',
  'emakiitem.field.item_sources': 'Item Sources',
  'emakiitem.option.conditionType.all_of': 'All of',
  'emakiitem.option.conditionType.any_of': 'Any of'
});

registerPluginSurfaces([
  { kind: 'ITEM', moduleId: MODULE, editorId: EDITOR_ID, component: ItemEditorSurface, label: 'EmakiItem 物品', priority: 120 },
  { kind: 'ITEM', moduleId: MODULE, component: ItemEditorSurface, label: 'EmakiItem 物品', priority: 110 },
  { kind: 'SET', moduleId: MODULE, editorId: SET_EDITOR_ID, component: ItemEditorSurface, label: 'EmakiItem 套装', priority: 120 },
  { kind: 'SET', moduleId: MODULE, component: ItemEditorSurface, label: 'EmakiItem 套装', priority: 110 }
]);

registerEditorDescriptor(MODULE, EDITOR_ID, {
  id: EDITOR_ID,
  moduleId: MODULE,
  title: 'EmakiItem 物品定义',
  kindLabel: '物品定义',
  baseName: '<gray>EmakiItem 预览</gray>',
  baseLore: ['<dark_gray>当前 YAML 草稿生成的物品预览</dark_gray>'],
  allowedFieldTypes: ['effects', 'attributeModifiers', 'repairMaterials'],
  sections: [
    { title: '基础字段', fields: fields(['id', 'material', 'display_name', 'item_name', 'lore']) },
    { title: '更新策略', collapsible: true, defaultCollapsed: true, fields: fields(['update.enabled', 'update.version', 'update.preserve_amount', 'update.preserve_damage', 'update.preserve_unknown_attribute_sources', 'update.triggers.join', 'update.triggers.held_change', 'update.triggers.inventory_click', 'update.triggers.inventory_drag', 'update.triggers.pickup', 'update.triggers.interact', 'update.triggers.command']) },
    { title: '效果与变量', collapsible: true, defaultCollapsed: true, fields: fields(['effects', 'variables', 'ea_attributes', 'ea_attribute_meta', 'es_skills']) },
    { title: '原版组件', collapsible: true, defaultCollapsed: true, fields: fields(['components.custom_model_data', 'components.item_model', 'components.tooltip_style', 'components.enchantments', 'components.item_flags', 'components.hide_tooltip', 'components.unbreakable', 'components.enchantment_glint_override', 'components.max_stack_size', 'components.rarity', 'components.damage', 'components.max_damage', 'components.enchantable', 'components.attribute_modifiers', 'components.raw']) },
    { title: '套装归属', collapsible: true, defaultCollapsed: true, fields: fields(['set.id', 'set.piece']) },
    { title: '装备条件', collapsible: true, defaultCollapsed: true, fields: fields(['conditions.entries', 'conditions.type', 'conditions.required_count', 'conditions.invalid_as_failure', 'conditions.deny_message', 'conditions.deny_actions']) },
    { title: '修复配置', collapsible: true, defaultCollapsed: true, fields: fields(['repair.enabled', 'repair.materials', 'repair.disabled_display.name_prefix', 'repair.disabled_display.lore_append', 'repair.on_disabled', 'repair.on_repaired']) },
    { title: '触发动作', collapsible: true, defaultCollapsed: true, fields: fields(['actions.give', 'actions.interact']) }
  ]
});

registerEditorDescriptor(MODULE, SET_EDITOR_ID, {
  id: SET_EDITOR_ID,
  moduleId: MODULE,
  title: 'EmakiItem 套装定义',
  kindLabel: '套装定义',
  allowedFieldTypes: ['setPieces', 'setThresholds'],
  sections: [
    { title: '基础字段', fields: setFields(['id', 'display_name']) },
    { title: '套装部件', collapsible: true, fields: setFields(['pieces']) },
    { title: 'Lore 显示', collapsible: true, defaultCollapsed: true, fields: setFields(['lore.header', 'lore.equipped_format', 'lore.missing_format', 'lore.active_threshold_format', 'lore.inactive_threshold_format', 'lore.separator']) },
    { title: '阈值效果', collapsible: true, fields: setFields(['thresholds']) }
  ]
});

itemEditorFields.forEach(([path, label, comment, type, extra]) => registerEditorField(MODULE, EDITOR_ID, { path, label, comment, type, ...(extra ?? {}) }));
setEditorFields.forEach(([path, label, comment, type, extra]) => registerEditorField(MODULE, SET_EDITOR_ID, { path, label, comment, type, ...(extra ?? {}) }));

function setFields(paths: string[]) {
  return paths.map(path => {
    const spec = setEditorFields.find(([fieldPath]) => fieldPath === path);
    if (!spec) return { path, label: path, type: 'text' };
    const [, label, comment, type, extra] = spec;
    return { path, label, comment, type, ...(extra ?? {}) };
  });
}

function fields(paths: string[]) {
  return paths.map(path => {
    const spec = itemEditorFields.find(([fieldPath]) => fieldPath === path);
    if (!spec) return { path, label: path, type: 'text' };
    const [, label, comment, type, extra] = spec;
    return { path, label, comment, type, ...(extra ?? {}) };
  });
}

configFields.forEach(([path, label, comment, type]) => registerConfigNodeMeta(MODULE, path, { label, comment, type }));
Object.entries(commonItemFields).forEach(([key, [label, comment, type]]) => registerConfigNodeRule(MODULE, { key }, { label, comment, type }));
