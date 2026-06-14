import React from 'react';
import { ItemEditorSurface, PropRow, StringListEditor, asList, asRecord, asStringList, coreEffectDefinition, debugTrace, payloadEffectDefinition, fieldLabel, getLocale, humanizeFieldLabel, localeText, optionLabel, registerEffectTypes, registerFileKindLabel, registerConfigNodeMeta, registerConfigNodeRule, registerEditorDescriptor, registerEditorField, registerItemFieldRenderer, registerModuleLocale, registerPluginSurfaces, registerSourceDocumentAdapter, textValue, type AnyMap, type ItemFieldRendererContext } from 'emaki-web-console';

let registered = false;

export function registerEmakiItemWebConsole(): void {
  if (registered) return;
  registered = true;
  const MODULE = 'EmakiItem';
  const EDITOR_ID = 'emakiitem:item';
  const SET_EDITOR_ID = 'emakiitem:set';

  registerFileKindLabel('SET', getLocale().startsWith('zh') ? '套装' : 'Set');

  type ConfigSpec = [path: string, label: string, comment: string, type: string];
  type ItemFieldSpec = [path: string, label: string, comment: string, type: string, extra?: Record<string, unknown>];

  const RARITIES = ['common', 'uncommon', 'rare', 'epic'];
  const CONDITION_TYPES = ['all_of', 'any_of', 'none_of', 'at_least', 'exactly'];
  const ATTRIBUTE_OPERATIONS = ['add_number', 'add_scalar', 'multiply_scalar_1'];
  const EQUIPMENT_SLOTS = ['any', 'hand', 'mainhand', 'offhand', 'head', 'chest', 'legs', 'feet', 'body'];
  const EQUIP_SLOTS = ['all', 'hand', 'main_hand', 'off_hand', 'helmet', 'chestplate', 'leggings', 'boots'];
  const ITEM_FLAGS = ['HIDE_ENCHANTS', 'HIDE_ATTRIBUTES', 'HIDE_UNBREAKABLE', 'HIDE_DESTROYS', 'HIDE_PLACED_ON', 'HIDE_ADDITIONAL_TOOLTIP', 'HIDE_DYE', 'HIDE_ARMOR_TRIM'];
  const SET_SLOTS = ['main_hand', 'off_hand', 'helmet', 'chestplate', 'leggings', 'boots'];
  const copy = localeText;
  type SetDocumentContext = { path?: string; childPath?: string };
  const normalizeDocumentPath = (path: unknown): string => textValue(path).trim().replace(/\\/g, '/').replace(/^\/+/, '');
  const containsGlob = (path: string): boolean => /[?*]/.test(path);
  const globDirectory = (path: string): string => {
    const wildcardIndex = path.search(/[?*]/);
    if (wildcardIndex < 0) return '';
    const slashIndex = path.slice(0, wildcardIndex).lastIndexOf('/');
    return slashIndex >= 0 ? path.slice(0, slashIndex + 1) : '';
  };
  const withYamlExtension = (path: string): string => /\.ya?ml$/i.test(path) ? path : `${path}.yml`;
  const concreteSetPath = (context: SetDocumentContext): string | null => {
    const parentPath = normalizeDocumentPath(context.path);
    const childPath = normalizeDocumentPath(context.childPath);
    const basePath = globDirectory(parentPath);

    debugTrace('10', 'EmakiItem concreteSetPath input', {
      contextPath: context.path,
      contextChildPath: context.childPath,
      parentPath,
      childPath,
      basePath,
      parentContainsGlob: containsGlob(parentPath),
      childContainsGlob: containsGlob(childPath)
    });

    let rawPath: string;
    let branch: string;
    if (childPath && !containsGlob(childPath)) {
      if (!childPath.includes('/')) {
        rawPath = basePath ? `${basePath}${childPath}` : childPath;
        branch = 'child-leaf-with-base';
      } else if (basePath && !childPath.startsWith(basePath)) {
        rawPath = `${basePath}${childPath.split('/').pop()}`;
        branch = 'child-path-outside-base-use-leaf';
      } else {
        rawPath = childPath;
        branch = 'child-concrete-path';
      }
    } else {
      rawPath = parentPath;
      branch = childPath ? 'child-glob-fallback-parent' : 'no-child-fallback-parent';
    }

    const finalPath = rawPath && !containsGlob(rawPath) ? withYamlExtension(rawPath) : null;
    debugTrace('11', 'EmakiItem concreteSetPath result', {
      contextPath: context.path,
      contextChildPath: context.childPath,
      parentPath,
      childPath,
      basePath,
      branch,
      rawPath,
      rawPathContainsGlob: containsGlob(rawPath),
      finalPath,
      nullReason: !rawPath ? 'empty-raw-path' : containsGlob(rawPath) ? 'raw-path-still-glob' : undefined
    });
    return finalPath;
  };
  const missingSetPathError = (context?: SetDocumentContext) => {
    const path = normalizeDocumentPath(context?.childPath || context?.path) || 'unknown';
    debugTrace('11', 'EmakiItem missingSetPathError', {
      contextPath: context?.path,
      contextChildPath: context?.childPath,
      displayedPath: path
    });
    return new Error(copy(`请选择具体套装文件。当前路径：${path}`, `Select a concrete set file. Current path: ${path}`));
  };

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
    equip_slot: ['生效槽位', '属性与技能生效的装备槽位。', 'enum'],
    display_name: ['显示名称', '物品显示名称，支持 MiniMessage 与变量占位。', 'text'],
    item_name: ['原版名称', 'item_name 组件，不支持变量，影响物品堆叠判断。', 'text'],
    lore: ['Lore', '物品说明文本，每行一条。', 'list'],
    effects: ['效果', '通过 type 区分 variables、ea_attribute、es_skill 等效果。', 'list'],
    variables: ['变量', '表达式变量，可用于名称、Lore 和动作模板占位。', 'object'],
    ea_attributes: ['EA 属性', '写入 EmakiAttribute PDC 的属性数值。', 'object'],
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
    ['lore.header', '标题行', '套装 Lore 标题，支持 %set_name%、%active%、%total%。', 'text'],
    ['lore.equipped_format', '已装备格式', '已装备部件的 Lore 行格式。', 'text'],
    ['lore.missing_format', '缺失格式', '未装备部件的 Lore 行格式。', 'text'],
    ['lore.active_threshold_format', '已激活阈值格式', '已激活阈值 Lore 包装格式。', 'text'],
    ['lore.inactive_threshold_format', '未激活阈值格式', '未激活阈值 Lore 包装格式。', 'text'],
    ['lore.separator', '分隔行', '部件列表与阈值列表之间的分隔行。', 'text'],
    ['thresholds', '阈值效果', '达到指定装备件数时激活的 Lore、EA 属性和 ES 技能。', 'setThresholds', { wide: true }]
  ];

  const itemEditorFields: ItemFieldSpec[] = [
    ['id', 'ID', '物品定义唯一标识，加载时会 normalize。', 'text'],
    ['material', '材质', 'Bukkit Material，必须是原版物品。', 'material'],
    ['equip_slot', '生效槽位', '控制属性与技能在哪个装备槽位生效，不负责原版穿戴拦截。', 'enum', { options: EQUIP_SLOTS, optionLabelPrefix: 'equipSlot' }],
    ['display_name', '显示名称', '支持 MiniMessage 与 %变量% 占位。', 'text'],
    ['item_name', '原版 item_name', '原版 item_name 组件，不参与变量渲染。', 'text'],
    ['lore', 'Lore', '支持 MiniMessage 与 %变量% 占位。', 'stringList', { wide: true }],
    ['name_actions', '名称动作链', '生成显示名称时执行的标准动作链。', 'actions', { wide: true }],
    ['lore_actions', 'Lore 动作链', '生成 Lore 时执行的标准动作链。', 'actions', { wide: true }],
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
    ['effects', '效果', '按 type 分流为变量、属性、技能和名称/Lore 动作。', 'effects', { wide: true }],
    ['variables', '变量', '表达式变量映射，支持固定值、公式、随机数值、随机文本、随机字符、权重随机字符和条件字符。', 'variablesMap', { wide: true }],
    ['ea_attributes', 'EA 属性', 'EmakiAttribute 属性数值映射。', 'map', { wide: true }],
    ['es_skills', 'ES 技能', 'EmakiSkills 技能 ID 列表。', 'stringList', { wide: true }],
    ['components.custom_model_data', '模型数据', '数字或 1.21.4+ floats、flags、strings、colors 复合结构。', 'json', { wide: true }],
    ['components.item_model', '物品模型', '资源包 item model 标识。', 'text'],
    ['components.tooltip_style', 'Tooltip 样式', '资源包 tooltip_style 标识。', 'text'],
    ['components.enchantments', '附魔', '附魔 ID 到等级的映射。', 'map', { wide: true }],
    ['components.item_flags', '物品标志', 'Bukkit ItemFlag 固定值。', 'multiEnum', { options: ITEM_FLAGS, optionLabelPrefix: 'itemFlag', wide: true }],
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
    ['condition.entries', '条件表达式', '支持玩家变量的条件表达式列表。', 'stringList', { wide: true }],
    ['condition.type', '组合方式', '条件表达式组合方式。', 'enum', { options: CONDITION_TYPES, optionLabelPrefix: 'conditionType' }],
    ['condition.required_count', '需要满足数量', 'at_least / exactly 场景下需要满足的条件数。', 'number'],
    ['condition.invalid_as_failure', '解析失败视为失败', '表达式解析异常时是否不通过。', 'boolean'],
    ['condition.on_fail.message', '拒绝消息', '条件不满足时提示。', 'text'],
    ['condition.on_pass.actions', '满足动作', '条件满足时执行的动作。', 'stringList', { wide: true }],
    ['condition.on_fail.actions', '不满足动作', '条件不满足时执行的动作。', 'stringList', { wide: true }],
    ['repair.enabled', '启用修复', '关闭时 repair 配置不生效。', 'boolean'],
    ['repair.materials', '修复材料', '每种修复材料的物品来源、消耗数量和恢复耐久值。', 'repairMaterials', { wide: true }],
    ['repair.economy.enabled', '启用经济修复', '是否允许通过 CoreLib 经济系统扣款修复。', 'boolean'],
    ['repair.economy.restore', '经济修复耐久', '经济修复恢复的耐久值，支持固定值或百分比。', 'text'],
    ['repair.economy.currencies', '经济修复货币', '经济修复消耗的货币列表，支持 provider、currency/currency_id、amount/base_cost/formula。', 'json', { wide: true }],
    ['repair.disabled_display.name_prefix', '损坏名称前缀', '物品损坏时追加到名称前的 MiniMessage。', 'text'],
    ['repair.disabled_display.lore_append', '损坏 Lore', '物品损坏时追加的 Lore。', 'stringList', { wide: true }],
    ['repair.on_disabled', '损坏动作', '物品进入损坏状态时执行。', 'stringList', { wide: true }],
    ['repair.on_repaired', '修复动作', '物品被修复时执行。', 'stringList', { wide: true }],
    ['actions.give', '给予动作', '物品给予玩家时执行。', 'stringList', { wide: true }],
    ['actions.interact', '交互动作', '玩家使用物品交互时执行。', 'stringList', { wide: true }]
  ];

  registerModuleLocale(MODULE, 'zh-CN', {
    'emakiitem.module.name': 'Item',
    'emakiitem.module.summary': '物品、套装刷新与触发',
    'emakiitem.file.config.title': '主配置',
    'emakiitem.file.config.comment': '物品系统主配置，包含套装、刷新、耐久和修复等设置。',
    'emakiitem.file.items.title': '物品',
    'emakiitem.file.items.comment': '自定义物品定义，包含显示文本、属性、技能、条件、修复和触发动作。',
    'emakiitem.file.sets.title': '套装',
    'emakiitem.file.sets.comment': '套装定义，配置部件、阈值效果、套装 Lore 和刷新规则。',
    'emakiitem.file.id_aliases.title': 'ID 别名',
    'emakiitem.file.id_aliases.comment': '重命名迁移使用的旧物品 ID 到目标 ID 映射。',
    'emakiitem.filePath.items_example_item.comment': '自定义物品示例，展示属性、技能、条件、耐久和动作链。',
    'emakiitem.filePath.sets_example_set.comment': '套装示例，展示部件绑定、阈值属性和套装 Lore。',
    'emakiitem.file.plugin.title': '插件描述',
    'emakiitem.file.plugin.comment': 'plugin.yml 元数据、命令、权限和依赖声明。',
    'emakiitem.file.web-console.title': 'WebUIEdit 注册',
    'emakiitem.file.web-console.comment': '此插件暴露给 WebUIEdit 的文件分组、编辑器类型和前端扩展入口。',

    'emakiitem.surface.item': 'EmakiItem 物品',
    'emakiitem.editor.item.title': 'EmakiItem 物品',
    'emakiitem.editor.set.title': 'EmakiItem 套装',
    'emakiitem.section.basic': '基础信息',
    'emakiitem.section.displayActions': '显示动作链',
    'emakiitem.section.update': '更新策略',
    'emakiitem.section.effects': '效果与变量',
    'emakiitem.section.components': '原版组件',
    'emakiitem.section.setBinding': '套装归属',
    'emakiitem.section.conditions': '装备条件',
    'emakiitem.section.repair': '修复配置',
    'emakiitem.section.actions': '触发动作',
    'emakiitem.section.setPieces': '套装部件',
    'emakiitem.section.setLore': '套装 Lore',
    'emakiitem.section.thresholds': '阈值效果',
    'emakiitem.preview.aria': 'EmakiItem 物品预览',
    'emakiitem.preview.kind': '自定义物品',
    'emakiitem.preview.kind.generic_item': '自定义物品',
    'emakiitem.action.addSetPiece': '添加套装部件',
    'emakiitem.action.addThreshold': '添加阈值',
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
    'emakiitem.option.equipSlot.all': '全部槽位',
    'emakiitem.option.equipSlot.hand': '任意手',
    'emakiitem.option.equipSlot.main_hand': '主手',
    'emakiitem.option.equipSlot.off_hand': '副手',
    'emakiitem.option.equipSlot.helmet': '头盔',
    'emakiitem.option.equipSlot.chestplate': '胸甲',
    'emakiitem.option.equipSlot.leggings': '护腿',
    'emakiitem.option.equipSlot.boots': '靴子',
    'emakiitem.option.setSlot.main_hand': '主手',
    'emakiitem.option.setSlot.off_hand': '副手',
    'emakiitem.option.setSlot.helmet': '头盔',
    'emakiitem.option.setSlot.chestplate': '胸甲',
    'emakiitem.option.setSlot.leggings': '护腿',
    'emakiitem.option.setSlot.boots': '靴子'
  });

  registerModuleLocale(MODULE, 'en-US', {
    'emakiitem.module.name': 'Item',
    'emakiitem.module.summary': 'Items, set refresh, and triggers',
    'emakiitem.file.config.title': 'Main Config',
    'emakiitem.file.config.comment': 'Main item system configuration covering sets, refresh, durability, and repair settings.',
    'emakiitem.file.items.title': 'Items',
    'emakiitem.file.items.comment': 'Custom item definitions covering display text, attributes, skills, conditions, repair, and trigger actions.',
    'emakiitem.file.sets.title': 'Sets',
    'emakiitem.file.sets.comment': 'Set definitions covering pieces, threshold effects, set lore, and refresh rules.',
    'emakiitem.file.id_aliases.title': 'ID Aliases',
    'emakiitem.file.id_aliases.comment': 'Old item ID to target ID mappings used by rename migration.',
    'emakiitem.filePath.items_example_item.comment': 'Custom item example showing attributes, skills, conditions, durability, and action chains.',
    'emakiitem.filePath.sets_example_set.comment': 'Set example showing piece bindings, threshold attributes, and set lore.',
    'emakiitem.file.plugin.title': 'Plugin Description',
    'emakiitem.file.plugin.comment': 'plugin.yml metadata, commands, permissions, and dependency declarations.',
    'emakiitem.file.web-console.title': 'WebUIEdit Registration',
    'emakiitem.file.web-console.comment': 'File groups, editor kinds, and frontend extension entries exposed to WebUIEdit by this plugin.',

    'emakiitem.surface.item': 'EmakiItem Item',
    'emakiitem.editor.item.title': 'EmakiItem Item',
    'emakiitem.editor.set.title': 'EmakiItem Set',
    'emakiitem.section.basic': 'Basic Info',
    'emakiitem.section.displayActions': 'Display Action Chains',
    'emakiitem.section.update': 'Update Strategy',
    'emakiitem.section.effects': 'Effects and Variables',
    'emakiitem.section.components': 'Vanilla Components',
    'emakiitem.section.setBinding': 'Set Binding',
    'emakiitem.section.conditions': 'Equip Conditions',
    'emakiitem.section.repair': 'Repair Settings',
    'emakiitem.section.actions': 'Trigger Actions',
    'emakiitem.section.setPieces': 'Set Pieces',
    'emakiitem.section.setLore': 'Set Lore',
    'emakiitem.section.thresholds': 'Threshold Effects',
    'emakiitem.preview.kind.generic_item': 'Custom Item',
    'emakiitem.action.addSetPiece': 'Add set piece',
    'emakiitem.action.addThreshold': 'Add threshold',
    'emakiitem.field.version': 'Config Version',
    'emakiitem.field.language': 'Language',
    'emakiitem.field.release_default_data': 'Release Default Data',
    'emakiitem.field.set_bonus': 'Set Bonuses',
    'emakiitem.field.set_bonus.enabled': 'Enable Sets',
    'emakiitem.field.set_bonus.refresh_triggers': 'Refresh Triggers',
    'emakiitem.field.display_name': 'Display Name',
    'emakiitem.field.lore': 'Lore',
    'emakiitem.field.item_sources': 'Item Sources',
    'emakiitem.option.equipSlot.all': 'All slots',
    'emakiitem.option.equipSlot.hand': 'Either hand',
    'emakiitem.option.equipSlot.main_hand': 'Main hand',
    'emakiitem.option.equipSlot.off_hand': 'Off hand',
    'emakiitem.option.equipSlot.helmet': 'Helmet',
    'emakiitem.option.equipSlot.chestplate': 'Chestplate',
    'emakiitem.option.equipSlot.leggings': 'Leggings',
    'emakiitem.option.equipSlot.boots': 'Boots',
    'emakiitem.option.conditionType.all_of': 'All of',
    'emakiitem.option.conditionType.any_of': 'Any of'
  });

  registerPluginSurfaces([
    { kind: 'ITEM', moduleId: MODULE, editorId: EDITOR_ID, component: ItemEditorSurface, label: copy('EmakiItem 物品', 'EmakiItem Item'), priority: 120 },
    { kind: 'ITEM', moduleId: MODULE, component: ItemEditorSurface, label: copy('EmakiItem 物品', 'EmakiItem Item'), priority: 110 },
    { kind: 'SET', moduleId: MODULE, editorId: SET_EDITOR_ID, component: ItemEditorSurface, label: copy('EmakiItem 套装', 'EmakiItem Set'), priority: 120 },
    { kind: 'SET', moduleId: MODULE, component: ItemEditorSurface, label: copy('EmakiItem 套装', 'EmakiItem Set'), priority: 110 }
  ]);

  registerSourceDocumentAdapter({
    kind: 'SET',
    moduleId: MODULE,
    editorId: SET_EDITOR_ID,
    priority: 120,
    adapter: {
      read: (api, context) => {
        debugTrace('12', 'EmakiItem SET adapter read start', {
          moduleId: context.module.id,
          fileId: context.file.id,
          fileKind: context.file.kind,
          filePath: context.file.path,
          contextPath: context.path,
          contextChildPath: context.childPath,
          editorId: context.editor?.id
        }, { api });
        const path = concreteSetPath(context);
        if (!path) {
          debugTrace('12', 'EmakiItem SET adapter read rejected', {
            moduleId: context.module.id,
            fileId: context.file.id,
            fileKind: context.file.kind,
            filePath: context.file.path,
            contextPath: context.path,
            contextChildPath: context.childPath
          }, { api });
          return Promise.reject(missingSetPathError(context));
        }
        debugTrace('12', 'EmakiItem SET adapter read request', {
          kind: context.file.kind,
          moduleId: context.module.id,
          path
        }, { api });
        return api.readTextDocument({ kind: context.file.kind, moduleId: context.module.id, path })
          .then(doc => {
            debugTrace('12', 'EmakiItem SET adapter read success', {
              requestedPath: path,
              docPath: doc.path,
              revision: doc.revision,
              contentLength: doc.content?.length ?? 0,
              contentPreview: String(doc.content ?? '').slice(0, 240)
            }, { api });
            return doc;
          })
          .catch(err => {
            debugTrace('12', 'EmakiItem SET adapter read failed', {
              requestedPath: path,
              message: String(err?.message ?? err),
              stack: err instanceof Error ? err.stack : undefined
            }, { api });
            throw err;
          });
      },
      save: (api, context, content, revision) => {
        debugTrace('12', 'EmakiItem SET adapter save start', {
          moduleId: context.module.id,
          fileId: context.file.id,
          fileKind: context.file.kind,
          filePath: context.file.path,
          contextPath: context.path,
          contextChildPath: context.childPath,
          editorId: context.editor?.id,
          revision,
          contentLength: content.length
        }, { api });
        const path = concreteSetPath(context);
        if (!path) {
          debugTrace('12', 'EmakiItem SET adapter save rejected', {
            moduleId: context.module.id,
            fileId: context.file.id,
            fileKind: context.file.kind,
            filePath: context.file.path,
            contextPath: context.path,
            contextChildPath: context.childPath
          }, { api });
          return Promise.reject(missingSetPathError(context));
        }
        debugTrace('12', 'EmakiItem SET adapter save request', {
          kind: context.file.kind,
          moduleId: context.module.id,
          path,
          revision,
          contentLength: content.length
        }, { api });
        return api.saveTextDocument({ kind: context.file.kind, moduleId: context.module.id, path }, content, revision)
          .then(result => {
            debugTrace('12', 'EmakiItem SET adapter save success', {
              requestedPath: path,
              previousRevision: revision,
              nextRevision: result.revision
            }, { api });
            return result;
          })
          .catch(err => {
            debugTrace('12', 'EmakiItem SET adapter save failed', {
              requestedPath: path,
              message: String(err?.message ?? err),
              stack: err instanceof Error ? err.stack : undefined
            }, { api });
            throw err;
          });
      },
      language: 'yaml',
      defaultContent: context => defaultSetContent(context.name)
    }
  });

  registerEmakiItemRenderers();

  registerEditorDescriptor(MODULE, EDITOR_ID, {
    id: EDITOR_ID,
    moduleId: MODULE,
    title: 'EmakiItem 物品',
    titleKey: 'emakiitem.editor.item.title',
    kindLabel: '物品',
    baseName: copy('<gray>EmakiItem 预览</gray>', '<gray>EmakiItem Preview</gray>'),
    baseLore: [copy('<dark_gray>根据当前 YAML 草稿生成的物品预览</dark_gray>', '<dark_gray>Preview generated from the current YAML draft</dark_gray>')],
    preview: { kindLabels: { generic_item: 'emakiitem.preview.kind.generic_item', default: 'emakiitem.preview.kind.generic_item' }, layeredModule: 'item', layeredRoute: 'preview-layered' },
    rename: { module: 'item', previewRoute: 'rename-preview', applyRoute: 'rename-apply', aliasRoute: 'alias-list' },
    allowedFieldTypes: ['effects', 'attributeModifiers', 'repairMaterials'],
    sections: [
      { title: '基础信息', titleKey: 'emakiitem.section.basic', fields: fields(['id', 'material', 'equip_slot', 'display_name', 'item_name', 'lore']) },
      { title: '显示动作链', titleKey: 'emakiitem.section.displayActions', collapsible: true, defaultCollapsed: true, fields: fields(['name_actions', 'lore_actions']) },
      { title: '更新策略', titleKey: 'emakiitem.section.update', collapsible: true, defaultCollapsed: true, fields: fields(['update.enabled', 'update.version', 'update.preserve_amount', 'update.preserve_damage', 'update.preserve_unknown_attribute_sources', 'update.triggers.join', 'update.triggers.held_change', 'update.triggers.inventory_click', 'update.triggers.inventory_drag', 'update.triggers.pickup', 'update.triggers.interact', 'update.triggers.command']) },
      { title: '效果与变量', titleKey: 'emakiitem.section.effects', collapsible: true, defaultCollapsed: true, fields: fields(['effects']) },
      { title: '原版组件', titleKey: 'emakiitem.section.components', collapsible: true, defaultCollapsed: true, fields: fields(['components.custom_model_data', 'components.item_model', 'components.tooltip_style', 'components.enchantments', 'components.item_flags', 'components.hide_tooltip', 'components.unbreakable', 'components.enchantment_glint_override', 'components.max_stack_size', 'components.rarity', 'components.damage', 'components.max_damage', 'components.enchantable', 'components.attribute_modifiers', 'components.raw']) },
      { title: '套装归属', titleKey: 'emakiitem.section.setBinding', collapsible: true, defaultCollapsed: true, fields: fields(['set.id', 'set.piece']) },
      { title: '装备条件', titleKey: 'emakiitem.section.conditions', collapsible: true, defaultCollapsed: true, fields: fields(['condition.entries', 'condition.type', 'condition.required_count', 'condition.invalid_as_failure', 'condition.on_fail.message', 'condition.on_pass.actions', 'condition.on_fail.actions']) },
      { title: '修复配置', titleKey: 'emakiitem.section.repair', collapsible: true, defaultCollapsed: true, fields: fields(['repair.enabled', 'repair.materials', 'repair.economy.enabled', 'repair.economy.restore', 'repair.economy.currencies', 'repair.disabled_display.name_prefix', 'repair.disabled_display.lore_append', 'repair.on_disabled', 'repair.on_repaired']) },
      { title: '触发动作', titleKey: 'emakiitem.section.actions', collapsible: true, defaultCollapsed: true, fields: fields(['actions.give', 'actions.interact']) }
    ]
  });

  registerEditorDescriptor(MODULE, SET_EDITOR_ID, {
    id: SET_EDITOR_ID,
    moduleId: MODULE,
    title: 'EmakiItem 套装',
    titleKey: 'emakiitem.editor.set.title',
    kindLabel: '套装',
    allowedFieldTypes: ['setPieces', 'setThresholds'],
    sections: [
      { title: '基础信息', titleKey: 'emakiitem.section.basic', fields: setFields(['id', 'display_name']) },
      { title: '套装部件', titleKey: 'emakiitem.section.setPieces', collapsible: true, fields: setFields(['pieces']) },
      { title: '套装 Lore', titleKey: 'emakiitem.section.setLore', collapsible: true, defaultCollapsed: true, fields: setFields(['lore.header', 'lore.equipped_format', 'lore.missing_format', 'lore.active_threshold_format', 'lore.inactive_threshold_format', 'lore.separator']) },
      { title: '阈值效果', titleKey: 'emakiitem.section.thresholds', collapsible: true, fields: setFields(['thresholds']) }
    ]
  });

  itemEditorFields.forEach(([path, label, comment, type, extra]) => registerEditorField(MODULE, EDITOR_ID, { path, label, comment, type, ...(extra ?? {}) }));
  setEditorFields.forEach(([path, label, comment, type, extra]) => registerEditorField(MODULE, SET_EDITOR_ID, { path, label, comment, type, ...(extra ?? {}) }));

  function defaultSetContent(name: string): string {
    const id = name.split('/').pop()?.replace(/\.(ya?ml)$/i, '').trim() || 'new_set';
    return `id: "${escapeYamlString(id)}"\ndisplay_name: "<aqua>${escapeYamlString(id)}</aqua>"\npieces: {}\nlore:\n  header: "<dark_gray>—— <aqua>%set_name%</aqua> <gray>(%active%/%total%)</gray> ——</dark_gray>"\n  equipped_format: "<green>✔ %piece%</green>"\n  missing_format: "<gray>✘ %piece%</gray>"\n  active_threshold_format: "<green>%line%</green>"\n  inactive_threshold_format: "<dark_gray>%line%</dark_gray>"\n  separator: ""\nthresholds: {}\n`;
  }

  function escapeYamlString(value: string): string {
    return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  }

  function registerEmakiItemRenderers() {
    registerItemFieldRenderer('attributeModifiers', context => <AttributeModifiersFieldEditor context={context} />, { moduleId: MODULE, editorId: EDITOR_ID, priority: 100 });
    registerItemFieldRenderer('repairMaterials', context => <RepairMaterialsFieldEditor context={context} />, { moduleId: MODULE, editorId: EDITOR_ID, priority: 100 });
    registerItemFieldRenderer('setPieces', context => <ItemSetPiecesEditor context={context} />, { moduleId: MODULE, editorId: SET_EDITOR_ID, priority: 100 });
    registerItemFieldRenderer('setThresholds', context => <ItemSetThresholdsEditor context={context} />, { moduleId: MODULE, editorId: SET_EDITOR_ID, priority: 100 });
  }

  const attributeEffectDef = payloadEffectDefinition('ea_attribute', 'EA 属性', [{ key: 'ea_attributes', type: 'map', label: 'EA 属性', defaultValue: {} }]);
  const skillEffectDef = payloadEffectDefinition('es_skill', 'ES 技能', [{ key: 'es_skills', type: 'stringList', label: 'ES 技能', defaultValue: [] }]);

  // Unified effect types for EmakiItem item editor: only each type's own payload
  // fields are shown, handled by the shared StandardEffectsEditor.
  registerEffectTypes(MODULE, [
    coreEffectDefinition('variables'),
    attributeEffectDef,
    skillEffectDef,
    coreEffectDefinition('name_action'),
    coreEffectDefinition('lore_action')
  ]);


  function AttributeModifiersFieldEditor({ context }: { context: ItemFieldRendererContext }) {
    return <PropRow label={fieldLabel(context.field.path, { moduleId: MODULE, namespace: MODULE, fallback: getLocale().startsWith('zh') ? context.field.label : humanizeFieldLabel(context.field.path) })} path={context.field.path} moduleId={MODULE} namespace={MODULE} editorFields={context.editorFields} changed={context.changed} wide>
      <AttributeModifiersEditor value={context.value} path={context.field.path} onChange={modifiers => context.setField(context.field.path, modifiers)} />
    </PropRow>;
  }

  function RepairMaterialsFieldEditor({ context }: { context: ItemFieldRendererContext }) {
    return <PropRow label={fieldLabel(context.field.path, { moduleId: MODULE, namespace: MODULE, fallback: getLocale().startsWith('zh') ? context.field.label : humanizeFieldLabel(context.field.path) })} path={context.field.path} moduleId={MODULE} namespace={MODULE} editorFields={context.editorFields} changed={context.changed} wide>
      <RepairMaterialsEditor value={context.value} path={context.field.path} onChange={materials => context.setField(context.field.path, materials)} />
    </PropRow>;
  }

  function ItemSetPiecesEditor({ context }: { context: ItemFieldRendererContext }) {
    return <PropRow label={fieldLabel(context.field.path, { moduleId: MODULE, namespace: MODULE, fallback: getLocale().startsWith('zh') ? context.field.label : humanizeFieldLabel(context.field.path) })} path={context.field.path} moduleId={MODULE} namespace={MODULE} editorFields={context.editorFields} changed={context.changed} wide>
      <SetPiecesEditor value={context.value} path={context.field.path} onChange={pieces => context.setField(context.field.path, pieces)} />
    </PropRow>;
  }

  function ItemSetThresholdsEditor({ context }: { context: ItemFieldRendererContext }) {
    return <PropRow label={fieldLabel(context.field.path, { moduleId: MODULE, namespace: MODULE, fallback: getLocale().startsWith('zh') ? context.field.label : humanizeFieldLabel(context.field.path) })} path={context.field.path} moduleId={MODULE} namespace={MODULE} editorFields={context.editorFields} changed={context.changed} wide>
      <SetThresholdsEditor value={context.value} path={context.field.path} onChange={thresholds => context.setField(context.field.path, thresholds)} />
    </PropRow>;
  }

  function AttributeModifiersEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap[]) => void; path?: string }) {
    const modifiers = asList(value).map(entry => asRecord(entry));
    const update = (index: number, patch: AnyMap) => onChange(modifiers.map((modifier, itemIndex) => itemIndex === index ? cleanObject({ ...modifier, ...patch }) : modifier));
    const remove = (index: number) => onChange(modifiers.filter((_, itemIndex) => itemIndex !== index));
    return <div className="prop-levels" role="list">
      {modifiers.map((modifier, index) => <div className="prop-cost-entry" key={index} role="listitem">
        <div className="prop-cost-entry-head"><span>{textValue(modifier.attribute, `attribute_${index + 1}`)}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={copy(`删除属性修饰符 ${index + 1}`, `Delete attribute modifier ${index + 1}`)}>×</button></div>
        <ItemFormRow label="attribute" path={joinPath(path, index, 'attribute')}><TextInput value={modifier.attribute} onChange={attribute => update(index, { attribute })} placeholder="attack_damage" /></ItemFormRow>
        <ItemFormRow label="amount" path={joinPath(path, index, 'amount')}><TextInput value={modifier.amount} onChange={amount => update(index, { amount: parseLooseScalar(amount) })} placeholder={copy('12.0 或 {range}', '12.0 or {range}')} /></ItemFormRow>
        <ItemFormRow label="operation" path={joinPath(path, index, 'operation')}><ItemOptionSelectInput value={modifier.operation ?? 'add_number'} options={ATTRIBUTE_OPERATIONS} labelPrefix="attributeOperation" onChange={operation => update(index, { operation })} /></ItemFormRow>
        <ItemFormRow label="slot" path={joinPath(path, index, 'slot')}><ItemOptionSelectInput value={modifier.slot ?? 'any'} options={EQUIPMENT_SLOTS} labelPrefix="equipmentSlot" onChange={slot => update(index, { slot })} /></ItemFormRow>
        <ItemFormRow label="name" path={joinPath(path, index, 'name')}><TextInput value={modifier.name} onChange={name => update(index, { name })} placeholder="namespace:key" /></ItemFormRow>
      </div>)}
      <button type="button" className="prop-add" onClick={() => onChange([...modifiers, { attribute: 'attack_damage', amount: 1, operation: 'add_number', slot: 'any', name: '' }])}>+ {copy('添加属性修饰符', 'Add attribute modifier')}</button>
    </div>;
  }

  function RepairMaterialsEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap[]) => void; path?: string }) {
    const materials = asList(value).map(entry => asRecord(entry));
    const update = (index: number, patch: AnyMap) => onChange(materials.map((material, itemIndex) => itemIndex === index ? cleanObject({ ...material, ...patch }) : material));
    const remove = (index: number) => onChange(materials.filter((_, itemIndex) => itemIndex !== index));
    return <div className="prop-levels" role="list">
      {materials.map((material, index) => {
        const itemSources = repairMaterialSources(material);
        return <div className="prop-cost-entry" key={index} role="listitem">
          <div className="prop-cost-entry-head"><span>{itemSources[0] || `material_${index + 1}`}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={copy(`删除修复材料 ${index + 1}`, `Delete repair material ${index + 1}`)}>×</button></div>
          <ItemFormRow label="item_sources" path={joinPath(path, index, 'item_sources')} wide><StringListEditor items={itemSources} onChange={item_sources => update(index, { item_sources, item_source: undefined, item: undefined })} placeholder="minecraft-diamond" /></ItemFormRow>
          <ItemFormRow label="amount" path={joinPath(path, index, 'amount')}><NumberInput value={material.amount ?? 1} onChange={amount => update(index, { amount: amount ?? 1 })} /></ItemFormRow>
          <ItemFormRow label="restore" path={joinPath(path, index, 'restore')}><TextInput value={material.restore} onChange={restore => update(index, { restore })} placeholder={copy('250 或 25%', '250 or 25%')} /></ItemFormRow>
        </div>;
      })}
      <button type="button" className="prop-add" onClick={() => onChange([...materials, { item_sources: ['minecraft-diamond'], amount: 1, restore: 100 }])}>+ {copy('添加修复材料', 'Add repair material')}</button>
    </div>;
  }

  function repairMaterialSources(material: AnyMap): string[] {
    const sources = asStringList(material.item_sources);
    if (sources.length > 0) return sources;
    const single = textValue(material.item_source || material.item).trim();
    return single ? [single] : [];
  }

  function SetPiecesEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap) => void; path?: string }) {
    const pieces = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: asRecord(entry) }));
    const update = (index: number, key: string, patch: AnyMap) => {
      const nextEntries = pieces.map((piece, itemIndex) => itemIndex === index ? { key, value: cleanObject({ ...piece.value, ...patch }) } : piece);
      onChange(Object.fromEntries(nextEntries.filter(piece => piece.key.trim()).map(piece => [piece.key.trim(), piece.value])));
    };
    const remove = (index: number) => onChange(Object.fromEntries(pieces.filter((_, itemIndex) => itemIndex !== index).map(piece => [piece.key, piece.value])));
    const add = () => onChange({ ...asRecord(value), [nextUniqueKey(pieces.map(piece => piece.key), 'piece')]: { item: '', slot: 'main_hand', display: '' } });
    return <div className="prop-levels" role="list">
      {pieces.map((piece, index) => <div className="prop-cost-entry" key={index} role="listitem">
        <div className="prop-cost-entry-head"><span>{piece.key}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={copy(`删除套装部件 ${index + 1}`, `Delete set piece ${index + 1}`)}>×</button></div>
        <ItemFormRow label="piece_id" path={joinPath(path, piece.key)}><TextInput value={piece.key} onChange={nextKey => update(index, nextKey, {})} /></ItemFormRow>
        <ItemFormRow label="item" path={joinPath(path, piece.key, 'item')}><TextInput value={piece.value.item} onChange={item => update(index, piece.key, { item })} placeholder="example_item" /></ItemFormRow>
        <ItemFormRow label="slot" path={joinPath(path, piece.key, 'slot')}><SetSlotSelectInput value={piece.value.slot ?? 'main_hand'} onChange={slot => update(index, piece.key, { slot })} /></ItemFormRow>
        <ItemFormRow label="display" path={joinPath(path, piece.key, 'display')}><TextInput value={piece.value.display} onChange={display => update(index, piece.key, { display })} placeholder={piece.key} /></ItemFormRow>
      </div>)}
      <button type="button" className="prop-add" onClick={add}>+ {copy('添加套装部件', 'Add set piece')}</button>
    </div>;
  }

  function SetThresholdsEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap) => void; path?: string }) {
    const thresholds = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: asRecord(entry) })).sort((left, right) => Number(left.key) - Number(right.key));
    const update = (index: number, key: string, patch: AnyMap) => {
      const nextEntries = thresholds.map((threshold, itemIndex) => itemIndex === index ? { key, value: cleanObject({ ...threshold.value, ...patch }) } : threshold);
      onChange(Object.fromEntries(nextEntries.filter(threshold => threshold.key.trim()).map(threshold => [threshold.key.trim(), threshold.value])));
    };
    const remove = (index: number) => onChange(Object.fromEntries(thresholds.filter((_, itemIndex) => itemIndex !== index).map(threshold => [threshold.key, threshold.value])));
    const add = () => onChange({ ...asRecord(value), [nextNumericKey(thresholds.map(threshold => threshold.key), 2)]: { lore: [], ea_attributes: {}, es_skills: [] } });
    return <div className="prop-levels" role="list">
      {thresholds.map((threshold, index) => <div className="prop-cost-entry" key={index} role="listitem">
        <div className="prop-cost-entry-head"><span>{copy(`${threshold.key} 件套`, `${threshold.key}-piece`)}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={copy(`删除阈值 ${threshold.key}`, `Delete threshold ${threshold.key}`)}>×</button></div>
        <ItemFormRow label="required" path={joinPath(path, threshold.key)}><NumberInput value={Number(threshold.key)} onChange={required => update(index, String(Math.max(1, required ?? 1)), {})} /></ItemFormRow>
        <ItemFormRow label="lore" path={joinPath(path, threshold.key, 'lore')} wide><StringListEditor items={asStringList(threshold.value.lore)} onChange={lore => update(index, threshold.key, { lore })} placeholder={copy('[2件套] 物理攻击 +5', '[2-piece] Physical Attack +5')} /></ItemFormRow>
        <ItemMapRow label="ea_attributes" path={joinPath(path, threshold.key, 'ea_attributes')} value={threshold.value.ea_attributes} valuePlaceholder={copy('属性值', 'Attribute value')} addKeyPrefix="attribute" onChange={ea_attributes => update(index, threshold.key, { ea_attributes })} />
        <ItemFormRow label="es_skills" path={joinPath(path, threshold.key, 'es_skills')} wide><StringListEditor items={asStringList(threshold.value.es_skills)} onChange={es_skills => update(index, threshold.key, { es_skills })} placeholder="guardian_aura" /></ItemFormRow>
      </div>)}
      <button type="button" className="prop-add" onClick={add}>+ {copy('添加阈值', 'Add threshold')}</button>
    </div>;
  }

  function SetSlotSelectInput({ value, onChange }: { value: unknown; onChange: (value: string) => void }) {
    const current = textValue(value);
    const merged = current && !SET_SLOTS.includes(current) ? [...SET_SLOTS, current] : SET_SLOTS;
    return <select value={current} onChange={event => onChange(event.target.value)}>{merged.map(slot => <option key={slot} value={slot}>{optionLabel('setSlot', slot, { moduleId: MODULE, namespace: MODULE, fallback: slot })}</option>)}</select>;
  }

  function TextInput({ value, onChange, placeholder }: { value: unknown; onChange: (value: string) => void; placeholder?: string }) {
    return <input type="text" value={textValue(value)} onChange={event => onChange(event.target.value)} placeholder={placeholder} />;
  }

  function NumberInput({ value, onChange }: { value: unknown; onChange: (value: number | undefined) => void }) {
    return <input type="number" value={value == null ? '' : textValue(value)} onChange={event => onChange(event.target.value === '' ? undefined : Number(event.target.value))} />;
  }

  function ItemMapRow({ label, path, value, onChange, valuePlaceholder = copy('值', 'Value'), addKeyPrefix = 'key' }: { label: string; path?: string; value: unknown; onChange: (value: AnyMap) => void; valuePlaceholder?: string; addKeyPrefix?: string }) {
    const entries = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: entry }));
    const update = (index: number, field: 'key' | 'value', nextValue: string) => {
      const next = [...entries];
      next[index] = field === 'key' ? { ...next[index], key: nextValue } : { ...next[index], value: parseLooseScalar(nextValue) };
      onChange(Object.fromEntries(next.filter(entry => entry.key.trim()).map(entry => [entry.key.trim(), entry.value])));
    };
    const remove = (index: number) => onChange(Object.fromEntries(entries.filter((_, itemIndex) => itemIndex !== index).map(entry => [entry.key, entry.value])));
    const add = () => onChange({ ...asRecord(value), [nextUniqueKey(entries.map(entry => entry.key), addKeyPrefix)]: 0 });
    return <ItemFormRow label={label} path={path} wide><div className="prop-kv">
      {entries.map((entry, index) => <div className="prop-kv-row" key={index}>
        <input value={entry.key} onChange={event => update(index, 'key', event.target.value)} placeholder={copy('键名', 'Key')} />
        <input value={entry.value == null ? '' : String(entry.value)} onChange={event => update(index, 'value', event.target.value)} placeholder={valuePlaceholder} />
        <button type="button" className="prop-kv-del" onClick={() => remove(index)}>×</button>
      </div>)}
      <button type="button" className="prop-add" onClick={add}>+ {copy('添加键值', 'Add key/value')}</button>
    </div></ItemFormRow>;
  }

  function ItemFormRow({ label, path, children, wide }: { label: string; path?: string; children: React.ReactNode; wide?: boolean }) {
    return <PropRow label={label} path={path ?? label} moduleId={MODULE} namespace={MODULE} wide={wide}>{children}</PropRow>;
  }

  function ItemOptionSelectInput({ value, options, labelPrefix, onChange }: { value: unknown; options: string[]; labelPrefix: string; onChange: (value: string) => void }) {
    const current = textValue(value);
    const merged = current && !options.includes(current) ? [...options, current] : options;
    return <select value={current} onChange={event => onChange(event.target.value)}>{merged.map(option => <option key={option} value={option}>{optionLabel(labelPrefix, option, { moduleId: MODULE, namespace: MODULE, fallback: option })}</option>)}</select>;
  }

  function parseLooseScalar(value: string): unknown {
    const trimmed = value.trim();
    if (trimmed === '') return '';
    if (trimmed === 'true') return true;
    if (trimmed === 'false') return false;
    if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
    return value;
  }

  function cleanObject<T extends Record<string, unknown>>(value: T): T {
    return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(Array.isArray(entry) && entry.length === 0))) as T;
  }

  function joinPath(...parts: Array<string | number | undefined>): string | undefined {
    const filtered = parts.filter(part => part !== undefined && part !== '').map(String);
    return filtered.length ? filtered.join('.') : undefined;
  }

  function nextUniqueKey(keys: string[], prefix: string): string {
    const normalizedPrefix = prefix.trim() || 'key';
    let index = keys.length + 1;
    let key = `${normalizedPrefix}_${index}`;
    while (keys.includes(key)) key = `${normalizedPrefix}_${++index}`;
    return key;
  }

  function nextNumericKey(keys: string[], fallback: number): string {
    const numeric = keys.map(key => Number(key)).filter(value => Number.isFinite(value));
    return String(Math.max(fallback - 1, ...numeric) + 1);
  }

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
  registerConfigNodeMeta(MODULE, 'aliases', { label: copy('ID 别名', 'ID Aliases'), comment: copy('旧物品 ID 到目标 ID 的迁移映射。', 'Old item ID to target ID mappings used by rename migration.'), type: 'dynamic_map', creatableChildren: true });
  Object.entries(commonItemFields).forEach(([key, [label, comment, type]]) => registerConfigNodeRule(MODULE, { key }, { label, comment, type }));
}
