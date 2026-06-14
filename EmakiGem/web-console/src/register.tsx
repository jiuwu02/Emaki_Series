import React, { useState } from 'react';
import { PropRow, SectionHead, StandardEconomyProviderSelect, StandardEffectsEditor, StringListEditor, asList, asRecord, asStringList, coreEffectDefinition, payloadEffectDefinition, fieldLabel, firstItemSource, getLocale, humanizeFieldLabel, localeText, materialFromItemSource, registerConfigCreateTemplate, registerConfigMetaFields, registerConfigRuleFields, registerEditorDescriptor, registerEditorField, registerEffectTypes, registerItemFieldRenderer, registerItemPreviewFallback, registerModuleLocale, registerPluginConfig, registerPluginGuiEditor, parseActionList, standardEconomyRuleFields, textValue, type AnyMap, type ConfigRuleFieldEntry, type ItemFieldRendererContext, type ItemPreviewResult } from 'emaki-web-console';
import { installEmakiGemStyles } from './styles';

let registered = false;

export function registerEmakiGemWebConsole(): void {
  if (registered) return;
  registered = true;
  installEmakiGemStyles();
  registerModuleLocale('EmakiGem', 'zh-CN', {
    'emakigem.module.name': 'Gem',
    'emakigem.module.summary': '开槽、镶嵌、升级与 GUI',
    'emakigem.file.config.title': '主配置',
    'emakigem.file.config.comment': '宝石系统主配置，包含开槽道具、镶嵌、升级和 GUI 入口设置。',
    'emakigem.file.gui.title': 'GUI 模板',
    'emakigem.file.gui.comment': '宝石镶嵌、开槽和升级界面的 GUI 模板。',
    'emakigem.file.items.title': '插槽物品',
    'emakigem.file.items.comment': '可镶嵌宝石的装备或道具定义，包含插槽、限制和获得动作。',
    'emakigem.file.gems.title': '宝石',
    'emakigem.file.gems.comment': '宝石定义，包含物品来源、效果、适用插槽和升级配置。',
    'emakigem.surface.gem': '宝石',
    'emakigem.surface.socketItem': '插槽物品',
    'emakigem.surface.gui': '宝石 GUI',
    'emakigem.preview.aria': '宝石物品预览',
    'emakigem.preview.kind.gem': '宝石',
    'emakigem.preview.kind.socket': '插槽物品',
    'emakigem.preview.kind.generic': '物品',
    'emakigem.preview.levelTitle': '等级预览',
    'emakigem.preview.levelHint': '切换等级会用当前草稿重新生成名称、Lore 与效果。',
    'emakigem.preview.levelCurrent': '当前 Lv.{level}',
    'emakigem.preview.levelBase': '基础等级',
    'emakigem.preview.status.syncing': '同步中',
    'emakigem.preview.status.live': '实时',
    'emakigem.preview.status.failed': '预览失败',
    'emakigem.preview.empty': '暂无预览',
    'emakigem.section.basic': '基础信息',
    'emakigem.section.effects': '效果与变量',
    'emakigem.section.costReturn': '费用与返还',
    'emakigem.section.upgrade': '升级设置',
    'emakigem.section.gemActions': '触发动作',
    'emakigem.section.levels': '升级等级',
    'emakigem.section.levelEffects': '等级效果',
    'emakigem.section.upgradeMaterials': '升级材料',
    'emakigem.section.slots': '插槽结构',
    'emakigem.section.gemLimit': '宝石限制',
    'emakigem.section.guiTemplate': 'GUI 模板',
    'emakigem.section.displayActions': '显示动作链',
    'emakigem.section.matchRules': '匹配规则',
    'emakigem.section.genericBasic': '基础信息',
    'emakigem.cost.globalUpgrade': '全局升级经济',
    'emakigem.cost.levelOverride': '等级经济覆盖',
    'emakigem.cost.inlay': '镶嵌费用',
    'emakigem.cost.extract': '拆卸费用',
    'emakigem.cost.extractReturn': '拆卸返还',
    'emakigem.cost.currency': '货币',
    'emakigem.cost.material': '材料',
    'emakigem.action.add': '+ 添加',
    'emakigem.action.addAction': '+ 添加动作',
    'emakigem.action.addSlot': '+ 插槽',
    'emakigem.action.addCurrency': '+ 货币',
    'emakigem.action.addMaterial': '+ 材料',
    'emakigem.action.setCost': '+ 设置{label}',
    'emakigem.action.delete': '删除',
    'emakigem.action.moveUp': '上移',
    'emakigem.action.moveDown': '下移',
    'emakigem.option.effect.variables': '变量',
    'emakigem.option.effect.ea_attribute': 'EA 属性',
    'emakigem.option.effect.es_skill': 'ES 技能',
    'emakigem.option.effect.name_action': '名称动作链',
    'emakigem.option.effect.lore_action': 'Lore 动作链',
    'emakigem.option.extract.original': '原样返还',
    'emakigem.option.extract.destroy': '销毁',
    'emakigem.option.extract.downgrade': '降级返还',
    'emakigem.option.failure.none': '无惩罚',
    'emakigem.option.failure.downgrade': '降级',
    'emakigem.option.failure.destroy': '销毁',
    'emakigem.option.gemType.attack': '攻击',
    'emakigem.option.gemType.defense': '防御',
    'emakigem.option.gemType.utility': '功能',
    'emakigem.option.gemType.universal': '通用',
    'emakigem.option.economyProvider.auto': '自动选择',
    'emakigem.option.economyProvider.vault': 'Vault',
    'emakigem.option.economyProvider.excellenteconomy': 'ExcellentEconomy',
    'emakigem.editor.gem.title': '宝石',
    'emakigem.editor.socketItem.title': '宝石插槽物品',
    'emakigem.field.id': 'ID',
    'emakigem.field.display_name': '显示名称',
    'emakigem.field.lore': 'Lore',
    'emakigem.field.gem_type': '宝石类型',
    'emakigem.field.level': '基础等级',
    'emakigem.field.item_sources': '物品来源',
    'emakigem.field.custom_model_data': '模型数据',
    'emakigem.field.socket_compatibility': '兼容插槽',
    'emakigem.field.type': '类型',
    'emakigem.field.variables': '变量',
    'emakigem.field.ea_attributes': 'EA 属性',
    'emakigem.field.es_skills': 'ES 技能',
    'emakigem.field.name_actions': '名称动作链',
    'emakigem.field.lore_actions': 'Lore 动作链',
    'emakigem.field.value': '文本',
    'emakigem.field.regex_pattern': '正则表达式',
    'emakigem.field.replacement': '替换为',
    'emakigem.field.content': '内容',
    'emakigem.field.target_pattern': '目标匹配',
    'emakigem.field.anchor': '锚点',
    'emakigem.field.inlay_cost': '镶嵌费用',
    'emakigem.field.extract_cost': '拆卸费用',
    'emakigem.field.extract_return': '拆卸返还',
    'emakigem.field.economy': '经济消耗',
    'emakigem.field.currencies': '货币消耗',
    'emakigem.field.materials': '材料消耗',
    'emakigem.field.provider': '经济提供器',
    'emakigem.field.currency_id': '货币 ID',
    'emakigem.field.amount': '数量',
    'emakigem.field.base_cost': '基础费用',
    'emakigem.field.cost_formula': '费用公式',
    'emakigem.field.enabled': '启用',
    'emakigem.field.mode': '返还模式',
    'emakigem.field.downgrade_levels': '降级等级',
    'emakigem.field.degraded_chance': '降级概率',
    'emakigem.field.upgrade': '升级配置',
    'emakigem.field.max_level': '最高等级',
    'emakigem.field.gui_template': '升级 GUI 模板',
    'emakigem.field.failure_penalty': '失败惩罚',
    'emakigem.field.success_rates': '成功率表',
    'emakigem.field.levels': '等级配置',
    'emakigem.field.success_rate': '成功率',
    'emakigem.field.actions': '动作',
    'emakigem.field.actions.success': '成功动作',
    'emakigem.field.actions.failure': '失败动作',
    'emakigem.field.inlay_success': '镶嵌成功动作',
    'emakigem.field.extract_success': '拆卸成功动作',
    'emakigem.field.index': '索引',
    'emakigem.field.match.item_sources': '匹配物品来源',
    'emakigem.field.match.slot_groups': '装备分组',
    'emakigem.field.match.lore_contains': 'Lore 包含',
    'emakigem.field.default_open_slots': '默认开放插槽',
    'emakigem.field.allowed_gem_types': '允许宝石类型',
    'emakigem.field.max_same_type': '同类型上限',
    'emakigem.field.max_same_id': '同 ID 上限',
    'emakigem.field.gui.gem_template': '宝石 GUI 模板',
    'emakigem.field.gui.open_template': '开槽 GUI 模板',
    'emakigem.field.material': '材质',
    'emakigem.item.field.id': 'ID',
    'emakigem.item.field.display_name': '显示名称',
    'emakigem.item.field.lore': 'Lore',
    'emakigem.item.field.gem_type': '宝石类型',
    'emakigem.item.field.level': '基础等级',
    'emakigem.item.field.item_sources': '物品来源',
    'emakigem.item.field.custom_model_data': '模型数据',
    'emakigem.item.field.socket_compatibility': '兼容插槽',
    'emakigem.item.field.type': '类型',
    'emakigem.item.field.variables': '变量',
    'emakigem.item.field.ea_attributes': 'EA 属性',
    'emakigem.item.field.es_skills': 'ES 技能',
    'emakigem.item.field.name_actions': '名称动作链',
    'emakigem.item.field.lore_actions': 'Lore 动作链',
    'emakigem.item.field.enabled': '启用',
    'emakigem.item.field.max_level': '最大等级',
    'emakigem.item.field.gui_template': 'GUI 模板',
    'emakigem.item.field.failure_penalty': '失败惩罚',
    'emakigem.item.field.success_rates': '成功率表',
    'emakigem.item.field.success_rate': '成功率',
    'emakigem.item.field.provider': '经济提供器',
    'emakigem.item.field.currency_id': '货币 ID',
    'emakigem.item.field.amount': '数量',
    'emakigem.item.field.base_cost': '基础费用',
    'emakigem.item.field.cost_formula': '费用公式',
    'emakigem.item.field.mode': '模式',
    'emakigem.item.field.downgrade_levels': '降级等级',
    'emakigem.item.field.degraded_chance': '降级概率',
    'emakigem.item.field.actions.success': '成功动作',
    'emakigem.item.field.actions.failure': '失败动作',
    'emakigem.item.field.inlay_success': '镶嵌成功动作',
    'emakigem.item.field.extract_success': '拆卸成功动作',
    'emakigem.item.field.index': '索引',
    'emakigem.item.field.match.item_sources': '匹配物品来源',
    'emakigem.item.field.match.slot_groups': '匹配插槽组',
    'emakigem.item.field.match.lore_contains': 'Lore 包含',
    'emakigem.item.field.default_open_slots': '默认开放插槽',
    'emakigem.item.field.allowed_gem_types': '允许宝石类型',
    'emakigem.item.field.max_same_type': '同类型上限',
    'emakigem.item.field.max_same_id': '同 ID 上限',
    'emakigem.item.field.gui.gem_template': '宝石 GUI 模板',
    'emakigem.item.field.gui.open_template': '开槽 GUI 模板',
    'emakigem.item.field.material': '材质',
    'emakigem.actionParam.value': '文本',
    'emakigem.actionParam.regex_pattern': '正则表达式',
    'emakigem.actionParam.replacement': '替换为',
    'emakigem.actionParam.content': '内容，每行一条',
    'emakigem.actionParam.target_pattern': '目标匹配',
    'emakigem.actionParam.anchor': '锚点'
  });

  registerPluginGuiEditor({
    moduleId: 'EmakiGem',
    editorId: 'emakigem:gui',
    label: getLocale().startsWith('zh') ? '宝石 GUI' : 'Gem GUI',
    fields: [
      ['slots', '槽位', 'GUI 中所有可渲染槽位配置。', 'object'],
      ['type', '槽位类型', '插件业务识别的槽位语义，例如 target_item、confirm。可选预设值，也可填自定义/填充槽位。', 'enum', { options: ['target_item', 'socket_info', 'socket_summary', 'socket_slot', 'preview_display', 'mode_inlay', 'mode_extract', 'confirm'], optionLabelPrefix: 'slotType' }],
      ['hidden_components', '隐藏组件', '隐藏 tooltip、附魔、属性等原版组件。', 'stringList'],
      ['item_model', '物品模型', '资源包 item model 标识。', 'text'],
      ['custom_model_data', '模型数据', 'Custom Model Data 数值。', 'number'],
      ['sounds', '声音', '点击槽位时播放的声音配置。', 'object'],
      ['target_item', '目标装备槽', '放入待镶嵌或查看的装备。', 'text'],
      ['socket_slot', '宝石槽位', '展示或操作装备上的宝石槽。', 'text'],
      ['confirm', '确认按钮', '确认当前宝石操作。', 'text']
    ]
  });

  const MODULE = 'EmakiGem';
  const EXTRACT_RETURN_MODES = ['original', 'destroy', 'downgrade'];
  const FAILURE_PENALTIES = ['none', 'downgrade', 'destroy'];
  const DEFAULT_ECONOMY_PROVIDERS = ['auto', 'vault', 'excellenteconomy'];
  const copy = localeText;

  type ConfigSpec = [path: string, label: string, comment: string, type: string, extra?: Record<string, unknown>];

  const configFields: ConfigSpec[] = [
    ['language', '语言', '语言文件 ID，对应 lang/<language>.yml。', 'text'],
    ['version', '配置版本', '默认配置结构版本，通常不建议手动修改。', 'text'],
    ['release_default_data', '释放默认数据', '首次启动或缺失 gems/、items/ 等示例数据时是否释放默认文件。', 'boolean'],
    ['socket_openers', '开槽道具', '按插槽类型配置开槽道具、开槽消耗和默认开放插槽规则。', 'object', { creatableChildren: true }],
    ['inlay_success', '镶嵌成功率', '宝石镶嵌成功率、公式变量和失败处理策略。', 'object'],
    ['inlay_success.enabled', '启用成功率', '关闭后镶嵌默认必定成功。', 'boolean'],
    ['inlay_success.default_chance', '默认成功率', '未在宝石或插槽上覆盖时使用的默认成功率百分比。', 'number'],
    ['inlay_success.rate_formula', '成功率公式', '根据宝石等级、插槽、玩家等变量计算最终成功率的表达式。', 'text'],
    ['inlay_success.failure_action', '失败处理', '镶嵌失败时对宝石和装备的处理方式。', 'enum', { options: ['return_gem', 'destroy_gem', 'destroy_both'], optionLabelPrefix: 'inlay_success.failure_action' }],
    ['upgrade', '升级配置', '宝石升级全局成功率、失败惩罚与等级覆盖。', 'object'],
    ['upgrade.global_success_rates', '全局升级成功率', '按目标等级配置的全局升级成功率表。', 'object', { creatableChildren: true }],
    ['upgrade.global_failure_penalty', '全局失败惩罚', '宝石升级失败时默认使用的惩罚方式。', 'enum', { options: ['none', 'downgrade', 'destroy'], optionLabelPrefix: 'upgrade.global_failure_penalty' }],
    ['number_format', '数值格式', '宝石属性、概率和消耗数值在 Lore 与预览中的格式化规则。', 'object'],
    ['number_format.default', '默认格式', '普通数值的默认格式，例如 0.##。', 'text'],
    ['permission', '权限', '宝石操作权限和 OP 绕过策略。', 'object'],
    ['permission.op_bypass', 'OP 跳过', '开启后 OP 可跳过宝石操作条件。', 'boolean'],
    ['gui', 'GUI', '宝石 GUI 默认模式、关闭保存和模板入口。', 'object'],
    ['gui.default_mode', '默认页面', '打开宝石 GUI 时默认显示的页面。', 'enum', { options: ['inlay', 'open', 'upgrade'], optionLabelPrefix: 'gui.default_mode' }],
    ['gui.save_on_close', '关闭保存', '关闭 GUI 时是否自动保存未提交的宝石操作。', 'boolean'],
    ['condition', '条件配置', '宝石操作条件表达式的组合方式与失败处理策略；仅用于判定，不执行 on_pass/on_fail 动作。', 'object'],
    ['condition.type', '条件逻辑', '条件表达式组合方式。', 'enum', { options: ['all_of', 'any_of', 'none_of', 'at_least', 'exactly'], optionLabelPrefix: 'conditionType' }],
    ['condition.required_count', '需要满足数量', 'at_least / exactly 场景下需要满足的最少条件数量。', 'number'],
    ['condition.entries', '条件表达式', 'CoreLib 条件表达式字符串列表。', 'stringList'],
    ['condition.invalid_as_failure', '解析失败视为失败', '条件表达式解析失败时是否视为条件不通过。', 'boolean']
  ];

  const conditionFields: ConfigSpec[] = [
    ['enabled', '启用', '是否启用此条件源。', 'boolean'],
    ['source_id', '来源 ID', '供 EmakiAttribute 或其他系统引用的条件源标识。', 'text'],
    ['condition', '条件块', '条件表达式组合方式与失败处理策略；仅用于判定，不执行 on_pass/on_fail 动作。', 'object'],
    ['condition.type', '条件逻辑', '条件表达式组合方式。', 'enum', { options: ['all_of', 'any_of', 'none_of', 'at_least', 'exactly'], optionLabelPrefix: 'conditionType' }],
    ['condition.invalid_as_failure', '解析失败视为失败', '表达式解析失败时是否视为不满足条件。', 'boolean'],
    ['condition.entries', '条件表达式', '条件表达式列表；空列表表示无额外条件，仅由代码逻辑判断。', 'stringList']
  ];

  const resonanceFields: ConfigSpec[] = [
    ['id', 'ID', '宝石共鸣唯一标识。', 'text'],
    ['display_name', '显示名称', '共鸣展示名称。', 'text'],
    ['priority', '优先级', '多个共鸣同时满足时的排序优先级。', 'number'],
    ['exclusive_group', '互斥组', '同一互斥组内只保留一个共鸣。', 'text'],
    ['chain', '共鸣链', '共鸣匹配模式和宝石需求列表。', 'object'],
    ['chain.mode', '匹配模式', '共鸣链匹配模式，例如 unordered。', 'text'],
    ['chain.pattern', '匹配条目', '共鸣要求的宝石 ID、类型和最低等级列表。', 'objectList'],
    ['effects', '共鸣效果', '共鸣触发后的动作、EA 属性、ES 技能和显示动作。', 'object'],
    ['effects.actions', '动作', '共鸣触发后执行的动作列表。', 'stringList'],
    ['effects.ea_attributes', 'EA 属性', '共鸣提供的 EmakiAttribute 属性映射。', 'object'],
    ['effects.es_skills', 'ES 技能', '共鸣提供的 EmakiSkills 技能 ID 列表。', 'stringList'],
    ['effects.name_actions', '名称动作链', '共鸣对物品名称执行的动作。', 'actions'],
    ['effects.lore_actions', 'Lore 动作链', '共鸣对物品 Lore 执行的动作。', 'actions']
  ];

  const dynamicFields: Record<string, ConfigRuleFieldEntry> = {
    item_sources: ['物品来源', '识别物品、材料或开槽道具的 ItemSource 列表。', 'list'],
    name_actions: ['名称动作链', '镶嵌、开槽或品质变化后对物品名称执行的动作列表。', 'actions'],
    lore_actions: ['Lore 动作链', '镶嵌、开槽或品质变化后对物品 Lore 执行的动作列表。', 'actions'],
    actions: ['动作', '操作成功、失败或展示时执行的 Action 配置。', 'object'],
    ...standardEconomyRuleFields({
      omit: ['economy', 'display_name'],
      overrides: {
        materials: ['材料消耗', '升级、镶嵌或开孔所需材料列表。', 'list'],
        currencies: ['货币消耗', 'Vault 或其他经济提供器消耗列表。', 'list'],
        provider: ['经济提供器', '经济消耗使用的提供器，auto 会按 currency_id 自动推断。', 'economyProvider', { optionLabelPrefix: 'economyProvider' }],
        currency_id: ['货币 ID', '多货币系统中的货币标识。', 'text'],
        amount: ['数量', '材料数量、货币数量或当前条目的数值。', 'number'],
        base_cost: ['基础费用', '费用公式中的基础值。', 'number'],
        cost_formula: ['费用公式', '根据等级、品质或上下文计算最终费用的表达式。', 'text'],
        enabled: ['启用', '是否启用当前功能或条目。', 'boolean']
      }
    }),
    opens_gem_types: ['开放宝石类型', '该开槽道具允许开放的宝石插槽类型，any 表示任意类型。', 'stringList'],
    consume_on_success: ['成功后消耗', '开槽成功后是否消耗此道具。', 'boolean'],
    success: ['成功动作', '开槽、镶嵌或升级成功时执行的动作列表。', 'list'],
    failure: ['失败动作', '开槽、镶嵌或升级失败时执行的动作列表。', 'list'],
    max_level: ['最高等级', '宝石可升级到的最高等级。', 'number'],
    success_rate: ['成功率', '升级到该等级或执行该操作的成功率。', 'number'],
    failure_penalty: ['失败惩罚', '升级失败后的惩罚方式。', 'enum'],
    default_open_slots: ['默认开放插槽', '物品初始已开放的插槽索引列表。', 'list'],
    allowed_gem_types: ['允许宝石类型', '该物品允许镶嵌的宝石类型白名单。', 'stringList'],
    max_same_type: ['同类型上限', '同类型宝石最大数量；0 表示不限制。', 'number'],
    max_same_id: ['同 ID 上限', '同一宝石 ID 可镶嵌数量；0 表示不限制。', 'number']
  };

  registerModuleLocale(MODULE, 'zh-CN', {
    'emakigem.module.name': 'Gem',
    'emakigem.module.summary': '开槽、镶嵌、升级与 GUI',
    'emakigem.file.config.title': '主配置',
    'emakigem.file.config.comment': '宝石系统主配置，包含开槽道具、镶嵌、升级和 GUI 入口设置。',
    'emakigem.file.gui.title': 'GUI 模板',
    'emakigem.file.gui.comment': '宝石镶嵌、开槽和升级界面的 GUI 模板。',
    'emakigem.file.items.title': '插槽物品',
    'emakigem.file.items.comment': '可镶嵌宝石的装备或道具定义，包含插槽、限制和获得动作。',
    'emakigem.file.gems.title': '宝石',
    'emakigem.file.gems.comment': '宝石定义，包含物品来源、效果、适用插槽和升级配置。',
    'emakigem.file.resonances.title': '共鸣',
    'emakigem.file.resonances.comment': '宝石共鸣定义目录，用于配置成组属性、技能、优先级和互斥组。',
    'emakigem.file.conditions.title': '条件',
    'emakigem.file.conditions.comment': '宝石操作条件目录，控制开槽、镶嵌和升级何时允许执行。',
    'emakigem.filePath.gems_example_gem.title': '示例宝石',
    'emakigem.filePath.gems_example_gem.comment': '宝石定义示例，展示等级效果、升级费用和动作链。',
    'emakigem.filePath.items_example_socket_item.title': '示例插槽物品',
    'emakigem.filePath.items_example_socket_item.comment': '插槽物品示例，展示插槽、默认开放状态和宝石限制。',
    'emakigem.filePath.resonances_example_resonance.title': '示例共鸣',
    'emakigem.filePath.resonances_example_resonance.comment': '共鸣定义示例，展示触发条件、属性奖励和互斥组。',
    'emakigem.filePath.conditions_gem.title': '宝石条件',
    'emakigem.filePath.conditions_gem.comment': '宝石操作条件，控制目标物品或宝石是否满足规则。',
    'emakigem.filePath.gui_gem_default.title': '宝石 GUI',
    'emakigem.filePath.gui_gem_default.comment': '镶嵌宝石界面模板，控制插槽、按钮和提示物品。',
    'emakigem.filePath.gui_open_default.title': '开槽 GUI',
    'emakigem.filePath.gui_open_default.comment': '开槽界面模板，控制开槽道具、目标装备和结果反馈。',
    'emakigem.filePath.gui_upgrade_default.title': '升级 GUI',
    'emakigem.filePath.gui_upgrade_default.comment': '宝石升级界面模板，控制材料、费用、成功率和结果按钮。',
    'emakigem.file.plugin.title': '插件描述',
    'emakigem.file.plugin.comment': 'plugin.yml 元数据、命令、权限和依赖声明。',
    'emakigem.file.web-console.title': 'WebUIEdit 注册',
    'emakigem.file.web-console.comment': '此插件暴露给 WebUIEdit 的文件分组、编辑器类型和前端扩展入口。',
    ...Object.fromEntries(configFields.flatMap(([path, label, comment]) => [[`emakigem.field.${path}`, label], [`emakigem.comment.${path}`, comment]])),
    ...Object.fromEntries(Object.entries(dynamicFields).flatMap(([key, [label, comment]]) => [[`emakigem.field.${key}`, label], [`emakigem.comment.${key}`, comment]])),
    'emakigem.option.inlay_success.failure_action.return_gem': '返还宝石',
    'emakigem.option.inlay_success.failure_action.destroy_gem': '销毁宝石',
    'emakigem.option.inlay_success.failure_action.destroy_both': '销毁宝石和装备',
    'emakigem.option.upgrade.global_failure_penalty.none': '无惩罚',
    'emakigem.option.upgrade.global_failure_penalty.downgrade': '降级',
    'emakigem.option.upgrade.global_failure_penalty.destroy': '销毁',
    'emakigem.option.gui.default_mode.inlay': '镶嵌',
    'emakigem.option.gui.default_mode.open': '开槽',
    'emakigem.option.gui.default_mode.upgrade': '升级'
  });

  registerModuleLocale(MODULE, 'en-US', {
    'emakigem.module.name': 'Gem',
    'emakigem.module.summary': 'Socketing, inlay, upgrade, and GUI',
    'emakigem.file.config.title': 'Main Config',
    'emakigem.file.config.comment': 'Main gem system configuration covering socket items, inlay, upgrades, and GUI entry points.',
    'emakigem.file.gui.title': 'GUI Templates',
    'emakigem.file.gui.comment': 'GUI templates for gem inlay, socket opening, and upgrades.',
    'emakigem.file.items.title': 'Socket Items',
    'emakigem.file.items.comment': 'Equipment or item definitions that can hold gems, including slots, limits, and obtain actions.',
    'emakigem.file.gems.title': 'Gems',
    'emakigem.file.gems.comment': 'Gem definitions covering item sources, effects, compatible slots, and upgrade settings.',
    'emakigem.file.resonances.title': 'Resonances',
    'emakigem.file.resonances.comment': 'Gem resonance definitions covering grouped attributes, skills, priority, and exclusive groups.',
    'emakigem.file.conditions.title': 'Conditions',
    'emakigem.file.conditions.comment': 'Gem operation conditions controlling when socketing, inlay, and upgrade actions are allowed.',
    'emakigem.filePath.gems_example_gem.title': 'Sample Gem',
    'emakigem.filePath.gems_example_gem.comment': 'Gem definition example showing level effects, upgrade costs, and action chains.',
    'emakigem.filePath.items_example_socket_item.title': 'Sample Socket Item',
    'emakigem.filePath.items_example_socket_item.comment': 'Socket item example showing slots, default open state, and gem limits.',
    'emakigem.filePath.resonances_example_resonance.title': 'Sample Resonance',
    'emakigem.filePath.resonances_example_resonance.comment': 'Resonance definition example showing trigger conditions, attribute rewards, and exclusive groups.',
    'emakigem.filePath.conditions_gem.title': 'Gem Condition',
    'emakigem.filePath.conditions_gem.comment': 'Gem operation condition controlling whether the target item or gem matches the rule.',
    'emakigem.filePath.gui_gem_default.title': 'Gem GUI',
    'emakigem.filePath.gui_gem_default.comment': 'Gem inlay GUI template controlling slots, buttons, and hint items.',
    'emakigem.filePath.gui_open_default.title': 'Socket Opening GUI',
    'emakigem.filePath.gui_open_default.comment': 'Socket opening GUI template controlling opener items, target equipment, and result feedback.',
    'emakigem.filePath.gui_upgrade_default.title': 'Upgrade GUI',
    'emakigem.filePath.gui_upgrade_default.comment': 'Gem upgrade GUI template controlling materials, costs, success rate, and result buttons.',
    'emakigem.file.plugin.title': 'Plugin Description',
    'emakigem.file.plugin.comment': 'plugin.yml metadata, commands, permissions, and dependency declarations.',
    'emakigem.file.web-console.title': 'WebUIEdit Registration',
    'emakigem.file.web-console.comment': 'File groups, editor kinds, and frontend extension entries exposed to WebUIEdit by this plugin.',
    'emakigem.editor.gem.title': 'Gem',
    'emakigem.editor.socketItem.title': 'Gem Socket Item',
    'emakigem.section.basic': 'Basic Info',
    'emakigem.section.effects': 'Effects and Variables',
    'emakigem.section.displayActions': 'Display Action Chains',
    'emakigem.section.costReturn': 'Cost and Returns',
    'emakigem.section.upgrade': 'Upgrade Settings',
    'emakigem.section.gemActions': 'Trigger Actions',
    'emakigem.section.matchRules': 'Match Rules',
    'emakigem.section.slots': 'Slot Structure',
    'emakigem.section.gemLimit': 'Gem Limits',
    'emakigem.section.guiTemplate': 'GUI Templates',
    'emakigem.preview.kind.gem': 'Gem',
    'emakigem.preview.kind.gem_socket_item': 'Socket Item',
    'emakigem.preview.kind.generic_item': 'Item',
    'emakigem.field.socket_openers': 'Socket Openers',
    'emakigem.field.inlay_success': 'Inlay Success',
    'emakigem.field.upgrade': 'Upgrade',
    'emakigem.field.number_format': 'Number Format',
    'emakigem.field.permission.op_bypass': 'OP Bypass',
    'emakigem.field.gui.default_mode': 'Default Mode',
    'emakigem.field.opens_gem_types': 'Opened Gem Types',
    'emakigem.field.consume_on_success': 'Consume on Success',
    'emakigem.field.actions.success': 'Success Actions',
    'emakigem.field.actions.failure': 'Failure Actions',
    'emakigem.field.default_open_slots': 'Default Open Slots',
    'emakigem.field.allowed_gem_types': 'Allowed Gem Types',
    'emakigem.field.max_same_type': 'Same Type Limit',
    'emakigem.field.max_same_id': 'Same ID Limit',
    'emakigem.option.inlay_success.failure_action.return_gem': 'Return gem',
    'emakigem.option.inlay_success.failure_action.destroy_gem': 'Destroy gem',
    'emakigem.option.inlay_success.failure_action.destroy_both': 'Destroy both',
    'emakigem.option.gui.default_mode.inlay': 'Inlay',
    'emakigem.option.gui.default_mode.open': 'Open socket',
    'emakigem.option.gui.default_mode.upgrade': 'Upgrade'
  });

  registerConfigMetaFields(MODULE, configFields);
  registerPluginConfig({
    moduleId: MODULE,
    fileSchemas: [
      { pathPrefix: 'conditions/', fields: conditionFields },
      { pathPrefix: 'resonances/', fields: resonanceFields }
    ],
    listItemSchemas: [
      ['chain.pattern', [
        { path: 'id', label: '宝石 ID', comment: '要求的宝石 ID；留空时可按 type 匹配。', type: 'text', defaultValue: '' },
        { path: 'type', label: '宝石类型', comment: '要求的宝石类型。', type: 'text', defaultValue: 'universal' },
        { path: 'min_level', label: '最低等级', comment: '要求的最低宝石等级。', type: 'number', defaultValue: 0 }
      ]]
    ]
  });
  registerConfigRuleFields(MODULE, dynamicFields);
  registerConfigCreateTemplate(MODULE, 'socket_openers', {
    id: 'socket-opener',
    label: copy('开槽道具', 'Socket opener'),
    fields: [
      { path: 'enabled', label: '启用', comment: '是否启用此开槽道具。', type: 'boolean', defaultValue: true },
      { path: 'item_sources', label: '物品来源', comment: '识别为该开槽道具的物品来源。', type: 'stringList', defaultValue: [] },
      { path: 'opens_gem_types', label: '开放宝石类型', comment: '该道具可开放的宝石插槽类型，any 表示任意类型。', type: 'stringList', defaultValue: ['any'] },
      { path: 'consume_on_success', label: '成功后消耗', comment: '开槽成功后是否消耗此道具。', type: 'boolean', defaultValue: true },
      { path: 'actions.success', label: '成功动作', comment: '开槽成功后执行的动作列表。', type: 'stringList', defaultValue: [] },
      { path: 'actions.failure', label: '失败动作', comment: '开槽失败后执行的动作列表。', type: 'stringList', defaultValue: [] }
    ]
  });
  registerConfigCreateTemplate(MODULE, 'upgrade.global_success_rates', {
    id: 'upgrade-rate',
    label: copy('目标等级成功率', 'Target level success rate'),
    fields: [
      { path: 'value', label: '成功率', comment: '该目标等级的升级成功率百分比。', type: 'number', defaultValue: 100 }
    ]
  });
  registerConfigCreateTemplate(MODULE, 'chain.pattern', {
    id: 'resonance-pattern',
    label: copy('共鸣匹配条目', 'Resonance pattern entry'),
    fields: [
      { path: 'id', label: '宝石 ID', comment: '要求的宝石 ID；留空时可按 type 匹配。', type: 'text', defaultValue: '' },
      { path: 'type', label: '宝石类型', comment: '要求的宝石类型。', type: 'text', defaultValue: 'universal' },
      { path: 'min_level', label: '最低等级', comment: '要求的最低宝石等级。', type: 'number', defaultValue: 0 }
    ]
  });

  registerEmakiGemItemRenderers();
  registerEmakiGemPreviewFallbacks();

  registerEditorDescriptor(MODULE, 'emakigem:gem', {
    id: 'emakigem:gem',
    moduleId: MODULE,
    title: '宝石',
    titleKey: 'emakigem.editor.gem.title',
    kindLabel: '宝石',
    baseName: copy('<gray>预览装备</gray>', '<gray>Preview Equipment</gray>'),
    baseLore: [copy('<gray>原始装备 Lore</gray>', '<gray>Original equipment lore</gray>')],
    preview: { showPipelineSummary: false, kindLabels: { gem: 'emakigem.preview.kind.gem', gem_socket_item: 'emakigem.preview.kind.gem_socket_item', generic_item: 'emakigem.preview.kind.generic_item' } },
    allowedFieldTypes: ['effects', 'cost', 'extractReturn', 'gemUpgrade'],
    sections: [
      {
        title: '基础信息', titleKey: 'emakigem.section.basic', fields: [
          { path: 'id', label: 'ID', type: 'text' },
          { path: 'display_name', label: '显示名称', type: 'text' },
          { path: 'lore', label: 'Lore', type: 'stringList', wide: true },
          { path: 'gem_type', label: '宝石类型', type: 'enum', options: ['attack', 'defense', 'utility', 'universal'] },
          { path: 'level', label: '基础等级', type: 'number' },
          { path: 'item_sources', label: '物品来源', type: 'stringList', wide: true },
          { path: 'custom_model_data', label: '模型数据', type: 'number' },
          { path: 'socket_compatibility', label: '兼容插槽', type: 'stringList', wide: true }
        ]
      },
      { title: '效果与变量', titleKey: 'emakigem.section.effects', collapsible: true, defaultCollapsed: true, fields: [{ path: 'effects', label: '宝石效果', type: 'effects', wide: true }] },
      {
        title: '显示动作链', titleKey: 'emakigem.section.displayActions', collapsible: true, defaultCollapsed: true, fields: [
          { path: 'name_actions', label: '名称动作链', type: 'actions', wide: true },
          { path: 'lore_actions', label: 'Lore 动作链', type: 'actions', wide: true }
        ]
      },
      {
        title: '费用与返还', titleKey: 'emakigem.section.costReturn', collapsible: true, defaultCollapsed: true, fields: [
          { path: 'inlay_cost', label: '镶嵌费用', type: 'cost', wide: true },
          { path: 'extract_cost', label: '拆卸费用', type: 'cost', wide: true },
          { path: 'extract_return', label: '拆卸返还', type: 'extractReturn', wide: true }
        ]
      },
      { title: '升级设置', titleKey: 'emakigem.section.upgrade', collapsible: true, defaultCollapsed: true, fields: [{ path: 'upgrade', label: '升级配置', type: 'gemUpgrade', wide: true }] },
      {
        title: '触发动作', titleKey: 'emakigem.section.gemActions', collapsible: true, defaultCollapsed: true, fields: [
          { path: 'actions.inlay_success', label: '镶嵌成功动作', type: 'stringList', wide: true },
          { path: 'actions.extract_success', label: '拆卸成功动作', type: 'stringList', wide: true }
        ]
      }
    ]
  });

  registerEditorDescriptor(MODULE, 'emakigem:socket-item', {
    id: 'emakigem:socket-item',
    moduleId: MODULE,
    title: '宝石插槽物品',
    titleKey: 'emakigem.editor.socketItem.title',
    kindLabel: '宝石插槽物品',
    baseName: copy('<gray>预览装备</gray>', '<gray>Preview Equipment</gray>'),
    baseLore: [copy('<gray>原始装备 Lore</gray>', '<gray>Original equipment lore</gray>')],
    preview: { showPipelineSummary: false, kindLabels: { gem: 'emakigem.preview.kind.gem', gem_socket_item: 'emakigem.preview.kind.gem_socket_item', generic_item: 'emakigem.preview.kind.generic_item' } },
    allowedFieldTypes: ['gemSlots'],
    sections: [
      {
        title: '匹配规则', titleKey: 'emakigem.section.matchRules', fields: [
          { path: 'id', label: 'ID', type: 'text' },
          { path: 'match.item_sources', label: '匹配物品来源', type: 'stringList', wide: true },
          { path: 'match.slot_groups', label: '装备分组', type: 'stringList', wide: true },
          { path: 'match.lore_contains', label: 'Lore 包含', type: 'stringList', wide: true }
        ]
      },
      { title: '插槽结构', titleKey: 'emakigem.section.slots', collapsible: true, defaultCollapsed: true, fields: [{ path: 'slots', label: '插槽列表', type: 'gemSlots', wide: true }] },
      {
        title: '宝石限制', titleKey: 'emakigem.section.gemLimit', collapsible: true, defaultCollapsed: true, fields: [
          { path: 'default_open_slots', label: '默认开放插槽', type: 'numberList', wide: true },
          { path: 'allowed_gem_types', label: '允许宝石类型', type: 'stringList', wide: true },
          { path: 'max_same_type', label: '同类型上限', type: 'number' },
          { path: 'max_same_id', label: '同 ID 上限', type: 'number' }
        ]
      },
      {
        title: 'GUI 模板', titleKey: 'emakigem.section.guiTemplate', collapsible: true, defaultCollapsed: true, fields: [
          { path: 'gui.gem_template', label: '镶嵌模板', type: 'text' },
          { path: 'gui.open_template', label: '开槽模板', type: 'text' }
        ]
      },
      {
        title: '获得装备时', titleKey: 'emakigem.section.displayActions', collapsible: true, defaultCollapsed: true, fields: [
          { path: 'obtain.name_actions', label: '名称动作链', type: 'actions', wide: true },
          { path: 'obtain.lore_actions', label: 'Lore 动作链', type: 'actions', wide: true },
          { path: 'obtain.actions', label: '获得动作', type: 'stringList', wide: true }
        ]
      }
    ]
  });

  [
    ['emakigem:gem', 'effects', '宝石效果', '实际写入属性、技能和名称/Lore 动作的效果列表。', 'effects'],
    ['emakigem:gem', 'name_actions', '名称动作链', '宝石展示时对名称执行的标准动作链。', 'actions'],
    ['emakigem:gem', 'lore_actions', 'Lore 动作链', '宝石展示时对 Lore 执行的标准动作链。', 'actions'],
    ['emakigem:gem', 'inlay_cost', '镶嵌费用', '镶嵌宝石时消耗的货币与材料。', 'object'],
    ['emakigem:gem', 'extract_cost', '拆卸费用', '拆卸宝石时消耗的货币与材料。', 'object'],
    ['emakigem:gem', 'extract_return', '拆卸返还', '拆卸后宝石原样返还、销毁或降级返还。', 'object'],
    ['emakigem:socket-item', 'slots', '插槽列表', '该物品拥有的宝石插槽。', 'list'],
    ['emakigem:socket-item', 'default_open_slots', '默认开放插槽', '物品初始已开放的插槽索引列表。', 'numberList'],
    ['emakigem:socket-item', 'obtain.name_actions', '名称动作链', '获得该装备时对名称执行的动作。', 'actions'],
    ['emakigem:socket-item', 'obtain.lore_actions', 'Lore 动作链', '获得该装备时对 Lore 执行的动作。', 'actions'],
    ['emakigem:socket-item', 'obtain.actions', '获得动作', '首次识别并写入宝石层时执行的动作。', 'list']
  ].forEach(([editorId, path, label, comment, type]) => registerEditorField(MODULE, editorId, { path, label, comment, type }));

  function registerEmakiGemItemRenderers() {
    registerItemFieldRenderer('cost', context => <CostEditor label={fieldLabel(context.field.path, { moduleId: MODULE, namespace: MODULE, fallback: getLocale().startsWith('zh') ? context.field.label : humanizeFieldLabel(context.field.path) })} path={context.field.path} value={context.value ?? { currencies: [], materials: [] }} economyProviders={context.economyProviders} onChange={next => context.setField(context.field.path, next)} />, { moduleId: MODULE, priority: 100 });
    registerItemFieldRenderer('extractReturn', context => <ExtractReturnEditor path={context.field.path} value={context.value} onChange={next => context.setField(context.field.path, next)} />, { moduleId: MODULE, priority: 100 });
    registerItemFieldRenderer('gemUpgrade', context => <UpgradeEditor context={context} />, { moduleId: MODULE, editorId: 'emakigem:gem', priority: 100 });
    registerItemFieldRenderer('gemSlots', context => <GemSlotsEditor context={context} />, { moduleId: MODULE, editorId: 'emakigem:socket-item', priority: 100 });
  }

  const attributeEffectDef = payloadEffectDefinition('ea_attribute', 'EA 属性', [{ key: 'ea_attributes', type: 'map', label: 'EA 属性', defaultValue: {} }]);
  const skillEffectDef = payloadEffectDefinition('es_skill', 'ES 技能', [{ key: 'es_skills', type: 'stringList', label: 'ES 技能', defaultValue: [] }]);

  // Unified effect types for EmakiGem gem editor, handled by StandardEffectsEditor.
  registerEffectTypes(MODULE, [
    coreEffectDefinition('variables'),
    attributeEffectDef,
    skillEffectDef,
    coreEffectDefinition('name_action'),
    coreEffectDefinition('lore_action')
  ]);


  function registerEmakiGemPreviewFallbacks() {
    registerItemPreviewFallback(context => localGemPreview(context.data, context.previewLevel, context.baseName, context.baseLore), { moduleId: MODULE, editorId: 'emakigem:gem', priority: 100 });
    registerItemPreviewFallback(context => localSocketItemPreview(context.data, context.baseName, context.baseLore), { moduleId: MODULE, editorId: 'emakigem:socket-item', priority: 100 });
  }

  function localGemPreview(data: AnyMap, previewLevel: number, baseName: string, baseLore: string[]): ItemPreviewResult {
    const levels = configuredPreviewLevels(data, null);
    const level = levels.includes(previewLevel) ? previewLevel : Number(data.level) || 1;
    const levelData = asRecord(asRecord(asRecord(data.upgrade).levels)[String(level)]);
    const effectiveData = asList(levelData.effects).length ? levelData : data;
    const variables = resolveLocalVariables(effectiveData, { id: textValue(data.id), level, current_level: level, target_level: level, display_name: textValue(levelData.display_name ?? data.display_name ?? data.id) });
    const nameActions = localSectionActions(effectiveData, 'name_action', 'name_actions');
    const loreActions = localSectionActions(effectiveData, 'lore_action', 'lore_actions');
    const material = materialFromItemSource(firstItemSource(data.item_sources) || 'stone');
    const initialLore = previewBaseLore(data, baseLore);
    const displayName = applyLocalNameActions(baseName || textValue(variables.display_name), nameActions, variables);
    const lore = applyLocalLoreActions(initialLore, loreActions, variables);
    return { kind: 'gem', id: textValue(data.id), material, baseName, baseLore, displayName, lore, variables, nameSteps: [], loreSteps: [], level, levels };
  }

  function localSocketItemPreview(data: AnyMap, baseName: string, baseLore: string[]): ItemPreviewResult {
    const material = materialFromItemSource(firstItemSource(data.item_sources ?? asRecord(data.match).item_sources) || 'stone');
    const defaultOpenSlots = asList(data.default_open_slots).map(value => Number(value)).filter(value => Number.isFinite(value));
    const variables: AnyMap = {
      item_definition_id: textValue(data.id),
      opened_slots: defaultOpenSlots.length,
      inlaid_slots: 0,
      total_slots: asList(data.slots).length
    };
    const obtain = asRecord(data.obtain);
    const nameActions = obtain.name_actions ?? [];
    const loreActions = obtain.lore_actions ?? [];
    return {
      kind: 'gem_socket_item',
      id: textValue(data.id),
      material,
      baseName,
      baseLore,
      displayName: applyLocalNameActions(textValue(data.display_name ?? data.id, baseName), nameActions, variables),
      lore: applyLocalLoreActions(previewBaseLore(data, baseLore), loreActions, variables),
      variables,
      nameSteps: [],
      loreSteps: [],
      levels: [],
      match: asRecord(data.match),
      slots: data.slots,
      defaultOpenSlots,
      allowedGemTypes: asList(data.allowed_gem_types),
      maxSameType: data.max_same_type,
      maxSameId: data.max_same_id,
      gui: data.gui
    };
  }

  function resolveLocalVariables(data: AnyMap, context: AnyMap): AnyMap {
    return { ...context, ...asRecord(data.variables), ...localEffectMap(data, 'variables', 'variables') };
  }

  function localEffectMap(data: AnyMap, type: string, key: string): AnyMap {
    return asList(data.effects).map(effect => asRecord(effect)).filter(effect => textValue(effect.type).toLowerCase() === type).reduce<AnyMap>((result, effect) => ({ ...result, ...asRecord(effect[key]) }), {});
  }

  function localSectionActions(data: AnyMap, type: string, key: string): unknown {
    if (data[key] !== undefined) return data[key];
    const effect = asList(data.effects).map(entry => asRecord(entry)).find(entry => textValue(entry.type).toLowerCase() === type);
    return effect?.[key] ?? [];
  }

  function applyLocalNameActions(originalName: string, rawActions: unknown, variables: AnyMap): string {
    let current = originalName;
    for (const action of parseActionList(rawActions)) {
      const value = renderLocalTemplate(textValue(action.params.value), variables);
      if (action.type === 'replace') current = value;
      else if (action.type === 'prepend_prefix') current = `${value}${current}`;
      else if (action.type === 'append_suffix') current = `${current}${value}`;
      else if (action.type === 'regex_replace') {
        try { current = current.replace(new RegExp(textValue(action.params.regex_pattern), 'g'), renderLocalTemplate(textValue(action.params.replacement), variables)); } catch { }
      }
    }
    return current;
  }

  function applyLocalLoreActions(originalLore: string[], rawActions: unknown, variables: AnyMap): string[] {
    let current = [...originalLore];
    for (const action of parseActionList(rawActions)) {
      const lines = localActionLines(action.params.content).map(line => renderLocalTemplate(line, variables));
      if (action.type === 'append') current = [...current, ...lines];
      else if (action.type === 'prepend') current = [...lines, ...current];
      else if (action.type === 'replace_line') current = lines;
      else if (action.type === 'delete_line') current = [];
      else if (action.type === 'regex_replace') {
        try { current = current.map(line => line.replace(new RegExp(textValue(action.params.regex_pattern), 'g'), renderLocalTemplate(textValue(action.params.replacement), variables))); } catch { }
      }
    }
    return current;
  }

  function localActionLines(value: unknown): string[] {
    if (Array.isArray(value)) return value.map(entry => textValue(entry));
    const text = textValue(value);
    return text ? text.split('\n') : [];
  }

  function renderLocalTemplate(template: string, variables: AnyMap): string {
    return template.replace(/\{([^}]+)\}/g, (_, key) => textValue(variables[String(key).trim()]));
  }

  function previewBaseLore(data: AnyMap, fallback: string[]): string[] {
    const lore = asStringList(data.lore);
    return lore.length ? lore : fallback;
  }

  function configuredPreviewLevels(data: AnyMap, preview: ItemPreviewResult | null): number[] {
    const upgrade = asRecord(data.upgrade);
    if (!truthy(upgrade.enabled)) return [];
    const previewLevels = (preview?.levels ?? []).map(level => Number(level)).filter(level => Number.isFinite(level) && level > 0);
    const maxLevel = Math.max(1, toNumber(upgrade.max_level, Math.max(1, ...previewLevels)));
    return Array.from({ length: maxLevel }, (_, index) => index + 1);
  }

  function truthy(value: unknown): boolean {
    return value === true || value === 'true' || value === 1 || value === '1';
  }

  function CostEditor({ label, value, onChange, showEnabled, path, economyProviders = DEFAULT_ECONOMY_PROVIDERS }: { label: string; value: unknown; onChange: (value: AnyMap) => void; showEnabled?: boolean; path?: string; economyProviders?: string[] }) {
    const cost = asRecord(value);
    const currencies = asList(cost.currencies).map(currency => asRecord(currency));
    const materials = asList(cost.materials).map(material => asRecord(material));
    const setCost = (patch: AnyMap) => onChange(cleanObject({ ...cost, ...patch }));
    return <div className="prop-cost-section">
      <span className="prop-cost-label">{label}</span>
      {showEnabled && <FormRow label="enabled" path={joinPath(path, 'enabled')}><ToggleButton checked={cost.enabled !== false} onChange={enabled => setCost({ enabled })} /></FormRow>}
      <CurrencyCostList items={currencies} path={joinPath(path, 'currencies')} economyProviders={economyProviders} onChange={items => setCost({ currencies: items })} />
      <MaterialCostList items={materials} path={joinPath(path, 'materials')} onChange={items => setCost({ materials: items })} />
    </div>;
  }

  function CurrencyCostList({ items, onChange, path, economyProviders = DEFAULT_ECONOMY_PROVIDERS }: { items: AnyMap[]; onChange: (items: AnyMap[]) => void; path?: string; economyProviders?: string[] }) {
    const update = (index: number, patch: AnyMap) => onChange(items.map((item, itemIndex) => itemIndex === index ? cleanObject({ ...item, ...patch }) : item));
    const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
    return <div className="prop-cost-group">
      <span className="prop-cost-group-title">{copy('货币', 'Currencies')}</span>
      {items.map((currency, index) => <div className="prop-cost-entry" key={index}>
        <div className="prop-cost-entry-head"><span>{textValue(currency.display_name) || textValue(currency.provider, 'vault')}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={copy(`删除货币 ${index + 1}`, `Delete currency ${index + 1}`)}>×</button></div>
        <FormRow label="provider" path={joinPath(path, index, 'provider')}><StandardEconomyProviderSelect value={currency.provider ?? 'auto'} providers={economyProviders} moduleId={MODULE} onChange={provider => update(index, { provider })} /></FormRow>
        <FormRow label="currency_id" path={joinPath(path, index, 'currency_id')}><TextInput value={currency.currency_id} onChange={currency_id => update(index, { currency_id })} /></FormRow>
        <FormRow label="amount" path={joinPath(path, index, 'amount')}><NumberInput value={currency.amount} onChange={amount => update(index, { amount })} /></FormRow>
        <FormRow label="base_cost" path={joinPath(path, index, 'base_cost')}><NumberInput value={currency.base_cost} onChange={base_cost => update(index, { base_cost })} /></FormRow>
        <FormRow label="cost_formula" path={joinPath(path, index, 'cost_formula')}><TextInput value={currency.cost_formula} onChange={cost_formula => update(index, { cost_formula })} placeholder="{base_cost} * {level}" /></FormRow>
        <FormRow label="display_name" path={joinPath(path, index, 'display_name')}><TextInput value={currency.display_name} onChange={display_name => update(index, { display_name })} /></FormRow>
      </div>)}
      <button type="button" className="prop-add" onClick={() => onChange([...items, { provider: 'vault', currency_id: '', base_cost: 0, cost_formula: '', display_name: '' }])}>+ {copy('货币', 'Currency')}</button>
    </div>;
  }

  function MaterialCostList({ items, onChange, path }: { items: AnyMap[]; onChange: (items: AnyMap[]) => void; path?: string }) {
    const update = (index: number, patch: AnyMap) => onChange(items.map((item, itemIndex) => itemIndex === index ? cleanObject({ ...item, ...patch }) : item));
    const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
    return <div className="prop-cost-group">
      <span className="prop-cost-group-title">{copy('材料', 'Materials')}</span>
      {items.map((material, index) => <div className="prop-cost-entry" key={index}>
        <div className="prop-cost-entry-head"><span>{firstItemSource(material.item_sources) || copy('未设置材料', 'No material')}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={copy(`删除材料 ${index + 1}`, `Delete material ${index + 1}`)}>×</button></div>
        <FormRow label="item_sources" path={joinPath(path, index, 'item_sources')} wide><StringListEditor items={editableStringList(material.item_sources)} onChange={item_sources => update(index, cleanObject({ item_sources }))} placeholder="minecraft-gold_nugget" /></FormRow>
        <FormRow label="amount" path={joinPath(path, index, 'amount')}><NumberInput value={material.amount} onChange={amount => update(index, { amount: amount ?? 1 })} /></FormRow>
      </div>)}
      <button type="button" className="prop-add" onClick={() => onChange([...items, { item_sources: ['minecraft-stone'], amount: 1 }])}>+ {copy('材料', 'Material')}</button>
    </div>;
  }

  function ExtractReturnEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap) => void; path?: string }) {
    const data = asRecord(value);
    const update = (patch: AnyMap) => onChange(cleanObject({ mode: 'original', downgrade_levels: 1, degraded_chance: 0, ...data, ...patch }));
    return <div className="prop-cost-section">
      <span className="prop-cost-label">{copy('拆卸返还', 'Extract returns')}</span>
      <FormRow label="mode" path={joinPath(path, 'mode')}><SelectInput value={data.mode ?? 'original'} options={EXTRACT_RETURN_MODES} labelPrefix="extract" onChange={mode => update({ mode })} /></FormRow>
      <FormRow label="downgrade_levels" path={joinPath(path, 'downgrade_levels')}><NumberInput value={data.downgrade_levels ?? 1} onChange={downgrade_levels => update({ downgrade_levels: downgrade_levels ?? 1 })} /></FormRow>
      <FormRow label="degraded_chance" path={joinPath(path, 'degraded_chance')}><NumberInput value={data.degraded_chance ?? 0} step="0.01" onChange={degraded_chance => update({ degraded_chance: degraded_chance ?? 0 })} /></FormRow>
    </div>;
  }

  function UpgradeEditor({ context }: { context: ItemFieldRendererContext }) {
    const path = context.field.path;
    const upgrade = asRecord(context.value);
    const levels = levelMap(upgrade.levels);
    const levelEntries = Object.entries(levels).sort(([left], [right]) => Number(left) - Number(right));
    const [expandedLevels, setExpandedLevels] = useState<Set<string>>(() => new Set(levelEntries.map(([key]) => key)));
    const updateUpgrade = (patch: AnyMap) => context.setField(path, cleanObject({ ...upgrade, ...patch }));
    const updateLevel = (levelKey: string, patch: AnyMap) => updateUpgrade({ levels: { ...levels, [levelKey]: cleanObject({ ...levels[levelKey], ...patch }) } });
    const removeLevel = (levelKey: string) => { const next = { ...levels }; delete next[levelKey]; updateUpgrade({ levels: next }); };
    const addLevel = () => {
      const nextLevel = nextNumericKey(Object.keys(levels), 2);
      updateUpgrade({ max_level: Math.max(toNumber(upgrade.max_level, 1), Number(nextLevel)), levels: { ...levels, [nextLevel]: { display_name: '', effects: [], materials: [], success_rate: 100, actions: { success: [], failure: [] } } } });
      setExpandedLevels(previous => new Set([...previous, nextLevel]));
    };
    const toggleLevel = (levelKey: string) => setExpandedLevels(previous => { const next = new Set(previous); next.has(levelKey) ? next.delete(levelKey) : next.add(levelKey); return next; });
    return <div className="prop-cost-section">
      <FormRow label="enabled" path={joinPath(path, 'enabled')}><ToggleButton checked={upgrade.enabled === true} onChange={enabled => updateUpgrade({ enabled })} /></FormRow>
      <FormRow label="max_level" path={joinPath(path, 'max_level')}><NumberInput value={upgrade.max_level ?? 1} onChange={max_level => updateUpgrade({ max_level: max_level ?? 1 })} /></FormRow>
      <FormRow label="gui_template" path={joinPath(path, 'gui_template')}><TextInput value={upgrade.gui_template} onChange={gui_template => updateUpgrade({ gui_template })} placeholder="upgrade/default" /></FormRow>
      <FormRow label="failure_penalty" path={joinPath(path, 'failure_penalty')}><SelectInput value={upgrade.failure_penalty ?? 'none'} options={FAILURE_PENALTIES} labelPrefix="failure" onChange={failure_penalty => updateUpgrade({ failure_penalty })} /></FormRow>
      <CostEditor label={copy('全局升级经济', 'Global upgrade economy')} path={joinPath(path, 'economy')} value={upgrade.economy ?? { enabled: true, currencies: [], materials: [] }} onChange={economy => updateUpgrade({ economy })} showEnabled economyProviders={context.economyProviders} />
      <MapRow label="success_rates" path={joinPath(path, 'success_rates')} value={upgrade.success_rates} valuePlaceholder={copy('成功率', 'Success rate')} addKeyPrefix="2" onChange={success_rates => updateUpgrade({ success_rates })} />
      <SectionHead title={copy('升级等级', 'Upgrade levels')} count={levelEntries.length} actions={<button type="button" className="prop-add-inline" onClick={addLevel}>+</button>} />
      <div className="prop-levels" role="list">
        {levelEntries.map(([levelKey, level]) => {
          const opened = expandedLevels.has(levelKey);
          const actions = asRecord(level.actions);
          return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={levelKey} role="listitem">
            <div className="prop-level-head">
              <button type="button" className="prop-level-toggle" onClick={() => toggleLevel(levelKey)} aria-expanded={opened} aria-controls={`level-body-${levelKey}`}>
                <span className="prop-level-summary"><span className="prop-level-badge">{opened ? '⌄' : '›'} Lv.{levelKey}</span>{textValue(level.display_name) || copy('未命名', 'Unnamed')}</span>
                <span className="prop-level-rate">{textValue(level.success_rate, copy('继承', 'Inherited'))}%</span>
              </button>
              <button type="button" className="prop-kv-del" onClick={event => { event.stopPropagation(); removeLevel(levelKey); }} onKeyDown={stopEvent} aria-label={copy(`删除等级 ${levelKey}`, `Delete level ${levelKey}`)}>×</button>
            </div>
            {opened && <div className="prop-level-body" id={`level-body-${levelKey}`}>
              <FormRow label="display_name" path={joinPath(path, 'levels', levelKey, 'display_name')}><TextInput value={level.display_name} onChange={display_name => updateLevel(levelKey, { display_name })} /></FormRow>
              <FormRow label="success_rate" path={joinPath(path, 'levels', levelKey, 'success_rate')}><NumberInput value={level.success_rate} onChange={success_rate => updateLevel(levelKey, { success_rate })} /></FormRow>
              <FormRow label="failure_penalty" path={joinPath(path, 'levels', levelKey, 'failure_penalty')}><SelectInput value={level.failure_penalty ?? ''} options={['', ...FAILURE_PENALTIES]} labelPrefix="failure" onChange={failure_penalty => updateLevel(levelKey, { failure_penalty })} /></FormRow>
              <SectionHead title={copy('等级效果', 'Level effects')} count={asList(level.effects).length} />
              <StandardEffectsEditor value={level.effects} onChange={effects => updateLevel(levelKey, { effects })} actionTypes={context.actionTypesResult ?? undefined} moduleId={MODULE} namespace={MODULE} path={joinPath(path, 'levels', levelKey, 'effects')} />
              <SectionHead title={copy('升级材料', 'Upgrade materials')} count={asList(level.materials).length} />
              <MaterialCostList items={asList(level.materials).map(material => asRecord(material))} path={joinPath(path, 'levels', levelKey, 'materials')} onChange={materials => updateLevel(levelKey, { materials })} />
              <CostEditor label={copy('等级经济覆盖', 'Level economy override')} path={joinPath(path, 'levels', levelKey, 'economy')} value={level.economy ?? { currencies: [] }} onChange={economy => updateLevel(levelKey, { economy })} showEnabled economyProviders={context.economyProviders} />
              <ActionLinesEditor label="actions.success" path={joinPath(path, 'levels', levelKey, 'actions', 'success')} value={actions.success} onChange={success => updateLevel(levelKey, { actions: cleanObject({ ...actions, success }) })} />
              <ActionLinesEditor label="actions.failure" path={joinPath(path, 'levels', levelKey, 'actions', 'failure')} value={actions.failure} onChange={failure => updateLevel(levelKey, { actions: cleanObject({ ...actions, failure }) })} />
            </div>}
          </div>;
        })}
      </div>
    </div>;
  }

  function GemSlotsEditor({ context }: { context: ItemFieldRendererContext }) {
    const path = context.field.path;
    const slots = asList(context.value).map(slot => asRecord(slot));
    const openSlots = normalizedNumberSet(context.data.default_open_slots);
    const [expanded, setExpanded] = useState<Set<number>>(() => new Set(slots.map((_, index) => index)));
    const setOpenSlots = (next: Set<number>) => context.setField('default_open_slots', Array.from(next).sort((left, right) => left - right));
    const updateSlot = (index: number, patch: AnyMap) => {
      const oldIndex = toNumber(slots[index]?.index, index);
      const nextIndex = 'index' in patch ? toNumber(patch.index, oldIndex) : oldIndex;
      context.setField(path, slots.map((slot, itemIndex) => itemIndex === index ? cleanObject({ ...slot, ...patch }) : slot));
      if (oldIndex !== nextIndex && openSlots.has(oldIndex)) {
        const nextOpen = new Set(openSlots);
        nextOpen.delete(oldIndex);
        nextOpen.add(nextIndex);
        setOpenSlots(nextOpen);
      }
    };
    const removeSlot = (index: number) => {
      const removedIndex = toNumber(slots[index]?.index, index);
      context.setField(path, slots.filter((_, itemIndex) => itemIndex !== index));
      if (openSlots.has(removedIndex)) {
        const nextOpen = new Set(openSlots);
        nextOpen.delete(removedIndex);
        setOpenSlots(nextOpen);
      }
    };
    const toggleOpen = (slotIndex: number) => {
      const nextOpen = new Set(openSlots);
      nextOpen.has(slotIndex) ? nextOpen.delete(slotIndex) : nextOpen.add(slotIndex);
      setOpenSlots(nextOpen);
    };
    const addSlot = () => { const next = [...slots, { index: nextSlotIndex(slots), type: 'universal', display_name: '' }]; context.setField(path, next); setExpanded(previous => new Set([...previous, next.length - 1])); };
    const toggle = (index: number) => setExpanded(previous => { const next = new Set(previous); next.has(index) ? next.delete(index) : next.add(index); return next; });
    return <PropRow label={fieldLabel(context.field.path, { moduleId: MODULE, namespace: MODULE, fallback: getLocale().startsWith('zh') ? context.field.label : humanizeFieldLabel(context.field.path) })} path={path} moduleId={MODULE} namespace={MODULE} editorFields={context.editorFields} changed={context.changed} wide>
      <div className="prop-levels" role="list">
        {slots.map((slot, index) => {
          const opened = expanded.has(index);
          const slotIndex = toNumber(slot.index, index);
          const isOpen = openSlots.has(slotIndex);
          return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
            <div className="prop-level-head">
              <button type="button" className="prop-level-toggle" onClick={() => toggle(index)} aria-expanded={opened} aria-controls={`slot-body-${index}`}>
                <span className="prop-level-summary"><span className="prop-level-badge">{opened ? '⌄' : '›'} #{textValue(slot.index, String(index))}</span>{textValue(slot.type, 'universal')}</span>
                <span className="prop-level-rate">{textValue(slot.display_name) || copy('未命名', 'Unnamed')}</span>
              </button>
              <button type="button" className={`prop-slot-open${isOpen ? ' active' : ''}`} onClick={event => { event.stopPropagation(); toggleOpen(slotIndex); }} onKeyDown={stopEvent} aria-pressed={isOpen}>{isOpen ? copy('默认开放', 'Open by default') : copy('默认关闭', 'Closed by default')}</button>
              <button type="button" className="prop-kv-del" onClick={event => { event.stopPropagation(); removeSlot(index); }} onKeyDown={stopEvent} aria-label={copy(`删除插槽 ${index + 1}`, `Delete slot ${index + 1}`)}>×</button>
            </div>
            {opened && <div className="prop-level-body" id={`slot-body-${index}`}>
              <FormRow label="index" path={joinPath(path, index, 'index')}><NumberInput value={slot.index ?? index} onChange={slotIndexValue => updateSlot(index, { index: slotIndexValue ?? index })} /></FormRow>
              <FormRow label="type" path={joinPath(path, index, 'type')}><TextInput value={slot.type} onChange={slotType => updateSlot(index, { type: slotType })} /></FormRow>
              <FormRow label="display_name" path={joinPath(path, index, 'display_name')}><TextInput value={slot.display_name} onChange={display_name => updateSlot(index, { display_name })} /></FormRow>
            </div>}
          </div>;
        })}
        <button type="button" className="prop-add" onClick={addSlot}>+ {copy('插槽', 'Slot')}</button>
      </div>
    </PropRow>;
  }

  function ActionLinesEditor({ label, value, onChange, path }: { label: string; value: unknown; onChange: (value: string[]) => void; path?: string }) {
    return <FormRow label={label} path={path} wide><StringListEditor items={asStringList(value)} onChange={onChange} placeholder="sendmessage text=&quot;...&quot;" /></FormRow>;
  }

  function MapRow({ label, path, value, onChange, valuePlaceholder = copy('值', 'Value'), addKeyPrefix = 'key' }: { label: string; path?: string; value: unknown; onChange: (value: AnyMap) => void; valuePlaceholder?: string; addKeyPrefix?: string }) {
    const entries = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: entry }));
    const update = (index: number, field: 'key' | 'value', nextValue: string) => {
      const next = [...entries];
      next[index] = field === 'key' ? { ...next[index], key: nextValue } : { ...next[index], value: parseLooseScalar(nextValue) };
      onChange(Object.fromEntries(next.filter(entry => entry.key.trim()).map(entry => [entry.key.trim(), entry.value])));
    };
    const remove = (index: number) => onChange(Object.fromEntries(entries.filter((_, itemIndex) => itemIndex !== index).map(entry => [entry.key, entry.value])));
    const add = () => onChange({ ...asRecord(value), [nextUniqueKey(entries.map(entry => entry.key), addKeyPrefix)]: 0 });
    return <FormRow label={label} path={path} wide><div className="prop-kv">
      {entries.map((entry, index) => <div className="prop-kv-row" key={index}>
        <input value={entry.key} onChange={event => update(index, 'key', event.target.value)} placeholder={copy('键', 'Key')} />
        <input value={entry.value == null ? '' : String(entry.value)} onChange={event => update(index, 'value', event.target.value)} placeholder={valuePlaceholder} />
        <button type="button" className="prop-kv-del" onClick={() => remove(index)}>×</button>
      </div>)}
      <button type="button" className="prop-add" onClick={add}>+ {copy('添加键值', 'Add key/value')}</button>
    </div></FormRow>;
  }

  function FormRow({ label, path, children, wide }: { label: string; path?: string; children: React.ReactNode; wide?: boolean }) {
    return <PropRow label={label} path={path ?? label} moduleId={MODULE} namespace={MODULE} wide={wide}>{children}</PropRow>;
  }

  function ToggleButton({ checked, onChange }: { checked: boolean; onChange: (next: boolean) => void }) {
    return <button type="button" className={`switch ${checked ? 'on' : ''}`} aria-pressed={checked} onClick={() => onChange(!checked)}>
      <span className="switch-icon" aria-hidden="true">
        {checked ? <svg viewBox="0 0 16 16" focusable="false"><path d="M3.5 8.2 6.7 11.2 12.8 4.8" /></svg> : <svg viewBox="0 0 16 16" focusable="false"><path d="M4.7 4.7 11.3 11.3M11.3 4.7 4.7 11.3" /></svg>}
      </span>
      {checked ? copy('开启', 'On') : copy('关闭', 'Off')}
    </button>;
  }

  function NumberInput({ value, onChange, step }: { value: unknown; onChange: (value: number | undefined) => void; step?: number | string }) {
    return <input type="number" step={step} value={value == null ? '' : textValue(value)} onChange={event => onChange(event.target.value === '' ? undefined : Number(event.target.value))} />;
  }

  function SelectInput({ value, options, onChange }: { value: unknown; options: string[]; labelPrefix?: string; onChange: (value: string) => void }) {
    const current = textValue(value);
    const merged = current && !options.includes(current) ? [...options, current] : options;
    return <select value={current} onChange={event => onChange(event.target.value)}>{merged.map(option => <option key={option} value={option}>{option}</option>)}</select>;
  }

  function TextInput({ value, onChange, placeholder }: { value: unknown; onChange: (value: string) => void; placeholder?: string }) {
    return <input type="text" value={textValue(value)} onChange={event => onChange(event.target.value)} placeholder={placeholder} />;
  }

  function joinPath(...parts: Array<string | number | undefined>): string | undefined {
    const filtered = parts.filter(part => part !== undefined && part !== '').map(String);
    return filtered.length ? filtered.join('.') : undefined;
  }

  function editableStringList(value: unknown): string[] {
    if (Array.isArray(value)) return value.map(item => item == null ? '' : String(item));
    if (value == null) return [];
    return [String(value)];
  }

  function levelMap(value: unknown): Record<string, AnyMap> {
    if (Array.isArray(value)) return Object.fromEntries(value.map((entry, index) => [String(index + 2), asRecord(entry)]));
    return Object.fromEntries(Object.entries(asRecord(value)).map(([key, entry]) => [key, asRecord(entry)]));
  }

  function nextNumericKey(keys: string[], fallback: number): string {
    const numeric = keys.map(key => Number(key)).filter(value => Number.isFinite(value));
    return String(Math.max(fallback - 1, ...numeric) + 1);
  }

  function nextUniqueKey(keys: string[], prefix: string): string {
    const normalizedPrefix = prefix.trim() || 'key';
    let index = keys.length + 1;
    let key = `${normalizedPrefix}_${index}`;
    while (keys.includes(key)) key = `${normalizedPrefix}_${++index}`;
    return key;
  }

  function nextSlotIndex(slots: AnyMap[]): number {
    const indexes = slots.map(slot => Number(slot.index)).filter(index => Number.isFinite(index));
    return Math.max(-1, ...indexes) + 1;
  }

  function normalizedNumberSet(value: unknown): Set<number> {
    return new Set(asList(value).map(entry => Number(entry)).filter(entry => Number.isFinite(entry)));
  }

  function toNumber(value: unknown, fallback: number): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
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
    return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '')) as T;
  }

  function stopEvent(event: React.SyntheticEvent) {
    event.stopPropagation();
  }
}
