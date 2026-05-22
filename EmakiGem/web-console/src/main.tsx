import React, { useState } from 'react';
import { ActionsEditor, CORE_EFFECT_TYPES, PropRow, SectionHead, StringListEditor, asList, asRecord, asStringList, coreEffectTypeLabel, createCoreEffect, fieldLabel, firstItemSource, getLocale, humanizeFieldLabel, isCoreEffectType, registerConfigCreateTemplate, registerConfigNodeMeta, registerConfigNodeRule, registerEditorDescriptor, registerEditorField, registerItemFieldRenderer, registerModuleLocale, registerPluginGuiEditor, serializeActionList, parseActionList, textValue, type AnyMap, type CoreEffectType, type ItemFieldRendererContext } from 'emaki-web-console';

registerModuleLocale('EmakiGem', 'zh-CN', {
  'emakigem.module.name': 'Gem',
  'emakigem.module.summary': '开槽、镶嵌、升级与 GUI',
  'emakigem.file.config.title': '主配置',
  'emakigem.file.config.comment': '宝石系统主配置，包含开槽道具、镶嵌、升级和 GUI 入口设置。',
  'emakigem.file.gui.title': 'GUI 模板',
  'emakigem.file.gui.comment': '宝石镶嵌、开槽、升级 GUI 模板文件。',
  'emakigem.file.items.title': '插槽物品',
  'emakigem.file.items.comment': '宝石插件物品定义文件。',
  'emakigem.file.gems.title': '宝石定义',
  'emakigem.file.gems.comment': '宝石定义文件，包含宝石物品来源、效果、插槽兼容和升级配置。',
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
  'emakigem.section.legacy': '兼容旧字段',
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
  'emakigem.field.success_chance': '成功概率',
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
  'emakigem.item.field.success_chance': '成功概率',
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
    ['type', '槽位类型', '插件业务识别的槽位语义，例如 target_item、confirm。', 'text'],
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
const GEM_EFFECT_TYPES = [...CORE_EFFECT_TYPES, 'ea_attribute', 'es_skill'];
const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;

type ConfigSpec = [path: string, label: string, comment: string, type: string, extra?: Record<string, unknown>];

const configFields: ConfigSpec[] = [
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
  ['gui.save_on_close', '关闭保存', '关闭 GUI 时是否自动保存未提交的宝石操作。', 'boolean']
];

const dynamicFields: Record<string, [string, string, string]> = {
  item_sources: ['物品来源', '识别物品、材料或开槽道具的 ItemSource 列表。', 'list'],
  name_actions: ['名称动作链', '镶嵌、开槽或品质变化后对物品名称执行的动作列表。', 'actions'],
  lore_actions: ['Lore 动作链', '镶嵌、开槽或品质变化后对物品 Lore 执行的动作列表。', 'actions'],
  actions: ['动作', '操作成功、失败或展示时执行的 Action 配置。', 'object'],
  materials: ['材料消耗', '升级、镶嵌或开孔所需材料列表。', 'list'],
  currencies: ['货币消耗', 'Vault 或其他经济提供器消耗列表。', 'list'],
  provider: ['经济提供器', '经济消耗使用的提供器，auto 会按 currency_id 自动推断。', 'enum'],
  currency_id: ['货币 ID', '多货币系统中的货币标识。', 'text'],
  amount: ['数量', '材料数量、货币数量或当前条目的数值。', 'number'],
  base_cost: ['基础费用', '费用公式中的基础值。', 'number'],
  cost_formula: ['费用公式', '根据等级、品质或上下文计算最终费用的表达式。', 'text'],
  enabled: ['启用', '是否启用当前功能或条目。', 'boolean'],
  max_level: ['最高等级', '宝石可升级到的最高等级。', 'number'],
  success_rate: ['成功率', '升级到该等级或执行该操作的成功率。', 'number'],
  success_chance: ['成功概率', '兼容字段：升级到该等级的成功概率。', 'number'],
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
  'emakigem.file.gui.comment': '宝石镶嵌、开槽、升级 GUI 模板文件。',
  'emakigem.file.items.title': '插槽物品',
  'emakigem.file.items.comment': '宝石插件物品定义文件。',
  'emakigem.file.gems.title': '宝石定义',
  'emakigem.file.gems.comment': '宝石定义文件，包含宝石物品来源、效果、插槽兼容和升级配置。',
  'emakigem.file.resonances.title': '共鸣定义',
  'emakigem.file.resonances.comment': '宝石共鸣定义文件目录。',
  'emakigem.file.conditions.title': '条件定义',
  'emakigem.file.conditions.comment': '宝石条件定义文件目录。',
  'emakigem.file.lang.title': '语言文件',
  'emakigem.file.lang.comment': '宝石插件语言资源文件目录。',
  'emakigem.file.plugin.title': '插件描述',
  'emakigem.file.plugin.comment': 'plugin.yml 插件描述与依赖声明。',
  'emakigem.file.web-console.title': 'Web Console 声明',
  'emakigem.file.web-console.comment': 'Web Console 文件注册与资源入口声明。',
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
  'emakigem.file.gui.comment': 'Gem inlay, socketing, and upgrade GUI template files.',
  'emakigem.file.items.title': 'Socket Items',
  'emakigem.file.items.comment': 'Item definition files for the gem plugin.',
  'emakigem.file.gems.title': 'Gem Definitions',
  'emakigem.file.gems.comment': 'Gem definition files covering item sources, effects, socket compatibility, and upgrade settings.',
  'emakigem.file.resonances.title': 'Resonance Definitions',
  'emakigem.file.resonances.comment': 'Directory for gem resonance definition files.',
  'emakigem.file.conditions.title': 'Condition Definitions',
  'emakigem.file.conditions.comment': 'Directory for gem condition definition files.',
  'emakigem.file.lang.title': 'Language Files',
  'emakigem.file.lang.comment': 'Directory for gem language resources.',
  'emakigem.file.plugin.title': 'Plugin Description',
  'emakigem.file.plugin.comment': 'plugin.yml plugin metadata and dependency declaration.',
  'emakigem.file.web-console.title': 'Web Console Declaration',
  'emakigem.file.web-console.comment': 'Web Console file registration and resource entry declaration.',
  'emakigem.field.socket_openers': 'Socket Openers',
  'emakigem.field.inlay_success': 'Inlay Success',
  'emakigem.field.upgrade': 'Upgrade',
  'emakigem.field.number_format': 'Number Format',
  'emakigem.field.permission.op_bypass': 'OP Bypass',
  'emakigem.field.gui.default_mode': 'Default Mode',
  'emakigem.option.inlay_success.failure_action.return_gem': 'Return gem',
  'emakigem.option.inlay_success.failure_action.destroy_gem': 'Destroy gem',
  'emakigem.option.inlay_success.failure_action.destroy_both': 'Destroy both',
  'emakigem.option.gui.default_mode.inlay': 'Inlay',
  'emakigem.option.gui.default_mode.open': 'Open socket',
  'emakigem.option.gui.default_mode.upgrade': 'Upgrade'
});

configFields.forEach(([path, label, comment, type, extra]) => registerConfigNodeMeta(MODULE, path, { label, comment, type, ...(extra ?? {}) }));
Object.entries(dynamicFields).forEach(([key, [label, comment, type]]) => registerConfigNodeRule(MODULE, { key }, { label, comment, type }));
registerConfigCreateTemplate(MODULE, 'socket_openers', {
  id: 'socket-opener',
  label: copy('开槽道具', 'Socket opener'),
  fields: [
    { path: 'item_sources', label: '物品来源', comment: '识别为该开槽道具的物品来源。', type: 'stringList', defaultValue: [] },
    { path: 'socket_type', label: '插槽类型', comment: '成功开槽后写入的插槽类型。', type: 'text', defaultValue: 'universal' },
    { path: 'consume', label: '消耗物品', comment: '开槽成功后是否消耗此道具。', type: 'boolean', defaultValue: true }
  ]
});
registerConfigCreateTemplate(MODULE, 'upgrade.global_success_rates', {
  id: 'upgrade-rate',
  label: copy('目标等级成功率', 'Target level success rate'),
  fields: [
    { path: 'value', label: '成功率', comment: '该目标等级的升级成功率百分比。', type: 'number', defaultValue: 100 }
  ]
});

registerEmakiGemItemRenderers();

registerEditorDescriptor(MODULE, 'emakigem:gem', {
  id: 'emakigem:gem',
  moduleId: MODULE,
  title: '宝石定义',
  kindLabel: '宝石定义',
  baseName: copy('<gray>预览装备</gray>', '<gray>Preview Equipment</gray>'),
  baseLore: [copy('<gray>原始装备 Lore</gray>', '<gray>Original equipment lore</gray>')],
  allowedFieldTypes: ['effects', 'cost', 'extractReturn', 'gemUpgrade'],
  sections: [
    {
      title: '基础信息', fields: [
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
    { title: '效果与变量', collapsible: true, defaultCollapsed: true, fields: [{ path: 'effects', label: '宝石效果', type: 'effects', wide: true }] },
    {
      title: '显示动作链', collapsible: true, defaultCollapsed: true, fields: [
        { path: 'name_actions', label: '名称动作链', type: 'actions', wide: true },
        { path: 'lore_actions', label: 'Lore 动作链', type: 'actions', wide: true }
      ]
    },
    {
      title: '费用与返还', collapsible: true, defaultCollapsed: true, fields: [
        { path: 'inlay_cost', label: '镶嵌费用', type: 'cost', wide: true },
        { path: 'extract_cost', label: '拆卸费用', type: 'cost', wide: true },
        { path: 'extract_return', label: '拆卸返还', type: 'extractReturn', wide: true }
      ]
    },
    { title: '升级设置', collapsible: true, defaultCollapsed: true, fields: [{ path: 'upgrade', label: '升级配置', type: 'gemUpgrade', wide: true }] },
    {
      title: '触发动作', collapsible: true, defaultCollapsed: true, fields: [
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
  kindLabel: '宝石物品定义',
  baseName: copy('<gray>预览装备</gray>', '<gray>Preview Equipment</gray>'),
  baseLore: [copy('<gray>原始装备 Lore</gray>', '<gray>Original equipment lore</gray>')],
  allowedFieldTypes: ['gemSlots'],
  sections: [
    {
      title: '匹配规则', fields: [
        { path: 'id', label: 'ID', type: 'text' },
        { path: 'match.item_sources', label: '匹配物品来源', type: 'stringList', wide: true },
        { path: 'match.slot_groups', label: '装备分组', type: 'stringList', wide: true },
        { path: 'match.lore_contains', label: 'Lore 包含', type: 'stringList', wide: true }
      ]
    },
    { title: '插槽结构', collapsible: true, defaultCollapsed: true, fields: [{ path: 'slots', label: '插槽列表', type: 'gemSlots', wide: true }] },
    {
      title: '宝石限制', collapsible: true, defaultCollapsed: true, fields: [
        { path: 'allowed_gem_types', label: '允许宝石类型', type: 'stringList', wide: true },
        { path: 'max_same_type', label: '同类型上限', type: 'number' },
        { path: 'max_same_id', label: '同 ID 上限', type: 'number' }
      ]
    },
    {
      title: 'GUI 模板', collapsible: true, defaultCollapsed: true, fields: [
        { path: 'gui.gem_template', label: '镶嵌模板', type: 'text' },
        { path: 'gui.open_template', label: '开槽模板', type: 'text' }
      ]
    },
    {
      title: '显示动作链', collapsible: true, defaultCollapsed: true, fields: [
        { path: 'name_actions', label: '名称动作链', type: 'actions', wide: true },
        { path: 'lore_actions', label: 'Lore 动作链', type: 'actions', wide: true }
      ]
    }
  ]
});

[
  ['emakigem:gem', 'effects', '宝石效果', '实际写入属性、技能和名称/Lore 动作的效果列表。', 'list'],
  ['emakigem:gem', 'name_actions', '名称动作链', '宝石展示时对名称执行的标准动作链。', 'actions'],
  ['emakigem:gem', 'lore_actions', 'Lore 动作链', '宝石展示时对 Lore 执行的标准动作链。', 'actions'],
  ['emakigem:gem', 'inlay_cost', '镶嵌费用', '镶嵌宝石时消耗的货币与材料。', 'object'],
  ['emakigem:gem', 'extract_cost', '拆卸费用', '拆卸宝石时消耗的货币与材料。', 'object'],
  ['emakigem:gem', 'extract_return', '拆卸返还', '拆卸后宝石原样返还、销毁或降级返还。', 'object'],
  ['emakigem:socket-item', 'slots', '插槽列表', '该物品拥有的宝石插槽。', 'list'],
  ['emakigem:socket-item', 'name_actions', '名称动作链', '插槽激活后对物品名称执行的动作。', 'actions'],
  ['emakigem:socket-item', 'lore_actions', 'Lore 动作链', '插槽激活后对物品 Lore 执行的动作。', 'actions']
].forEach(([editorId, path, label, comment, type]) => registerEditorField(MODULE, editorId, { path, label, comment, type }));

function registerEmakiGemItemRenderers() {
  registerItemFieldRenderer('effects', context => <GemEffectsEditor context={context} />, { moduleId: MODULE, editorId: 'emakigem:gem', priority: 100 });
  registerItemFieldRenderer('cost', context => <CostEditor label={fieldLabel(context.field.path, { moduleId: MODULE, namespace: MODULE, fallback: getLocale().startsWith('zh') ? context.field.label : humanizeFieldLabel(context.field.path) })} path={context.field.path} value={context.value ?? { currencies: [], materials: [] }} economyProviders={context.economyProviders} onChange={next => context.setField(context.field.path, next)} />, { moduleId: MODULE, priority: 100 });
  registerItemFieldRenderer('extractReturn', context => <ExtractReturnEditor path={context.field.path} value={context.value} onChange={next => context.setField(context.field.path, next)} />, { moduleId: MODULE, priority: 100 });
  registerItemFieldRenderer('gemUpgrade', context => <UpgradeEditor context={context} />, { moduleId: MODULE, editorId: 'emakigem:gem', priority: 100 });
  registerItemFieldRenderer('gemSlots', context => <GemSlotsEditor context={context} />, { moduleId: MODULE, editorId: 'emakigem:socket-item', priority: 100 });
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
      <div className="prop-cost-entry-head"><span>{textValue(currency.display_name) || textValue(currency.provider, 'vault')}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除货币 ${index + 1}`}>×</button></div>
      <FormRow label="provider" path={joinPath(path, index, 'provider')}><SelectInput value={currency.provider ?? 'auto'} options={economyProviders} labelPrefix="economyProvider" onChange={provider => update(index, { provider })} /></FormRow>
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
      <div className="prop-cost-entry-head"><span>{firstItemSource(material.item_sources) || textValue(material.item, copy('未设置材料', 'No material'))}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={copy(`删除材料 ${index + 1}`, `Delete material ${index + 1}`)}>×</button></div>
      <FormRow label="item_sources" path={joinPath(path, index, 'item_sources')} wide><StringListEditor items={materialSources(material)} onChange={item_sources => update(index, cleanObject({ item_sources, item: undefined, material: undefined }))} placeholder="minecraft-gold_nugget" /></FormRow>
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
              <span className="prop-level-rate">{textValue(level.success_rate ?? level.success_chance, copy('继承', 'Inherited'))}%</span>
            </button>
            <button type="button" className="prop-kv-del" onClick={event => { event.stopPropagation(); removeLevel(levelKey); }} onKeyDown={stopEvent} aria-label={copy(`删除等级 ${levelKey}`, `Delete level ${levelKey}`)}>×</button>
          </div>
          {opened && <div className="prop-level-body" id={`level-body-${levelKey}`}>
            <FormRow label="display_name" path={joinPath(path, 'levels', levelKey, 'display_name')}><TextInput value={level.display_name} onChange={display_name => updateLevel(levelKey, { display_name })} /></FormRow>
            <FormRow label="success_rate" path={joinPath(path, 'levels', levelKey, 'success_rate')}><NumberInput value={level.success_rate ?? level.success_chance} onChange={success_rate => updateLevel(levelKey, { success_rate })} /></FormRow>
            <FormRow label="failure_penalty" path={joinPath(path, 'levels', levelKey, 'failure_penalty')}><SelectInput value={level.failure_penalty ?? ''} options={['', ...FAILURE_PENALTIES]} labelPrefix="failure" onChange={failure_penalty => updateLevel(levelKey, { failure_penalty })} /></FormRow>
            <SectionHead title={copy('等级效果', 'Level effects')} count={asList(level.effects).length} />
            <ActionEffectList value={level.effects} onChange={effects => updateLevel(levelKey, { effects })} actionTypesResult={context.actionTypesResult} path={joinPath(path, 'levels', levelKey, 'effects')} />
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

function GemEffectsEditor({ context }: { context: ItemFieldRendererContext }) {
  return <PropRow label={fieldLabel(context.field.path, { moduleId: MODULE, namespace: MODULE, fallback: getLocale().startsWith('zh') ? context.field.label : humanizeFieldLabel(context.field.path) })} path={context.field.path} moduleId={MODULE} namespace={MODULE} editorFields={context.editorFields} changed={context.changed} wide>
    <ActionEffectList value={context.value} onChange={effects => context.setField(context.field.path, effects)} actionTypesResult={context.actionTypesResult} path={context.field.path} />
  </PropRow>;
}

function ActionEffectList({ value, onChange, actionTypesResult, path }: { value: unknown; onChange: (value: unknown[]) => void; actionTypesResult: ItemFieldRendererContext['actionTypesResult']; path?: string }) {
  const effects = asList(value).map(effect => asRecord(effect));
  const update = (index: number, nextEffect: AnyMap) => onChange(effects.map((entry, itemIndex) => itemIndex === index ? cleanObject(nextEffect) : entry));
  const remove = (index: number) => onChange(effects.filter((_, itemIndex) => itemIndex !== index));
  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= effects.length) return;
    const next = [...effects];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };
  const add = (type: string) => onChange([...effects, defaultGemEffect(type)]);
  return <div className="prop-levels" role="list">
    {effects.map((effect, index) => {
      const type = textValue(effect.type) || 'variables';
      const options = GEM_EFFECT_TYPES.includes(type) ? GEM_EFFECT_TYPES : [...GEM_EFFECT_TYPES, type];
      return <div className="prop-cost-entry" key={index} role="listitem">
        <div className="prop-cost-entry-head"><span>{gemEffectTypeLabel(type)}</span><span className="prop-action-controls"><button type="button" onClick={() => move(index, -1)} disabled={index === 0} aria-label={copy('上移', 'Move up')}>↑</button><button type="button" onClick={() => move(index, 1)} disabled={index === effects.length - 1} aria-label={copy('下移', 'Move down')}>↓</button><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={copy(`删除效果 ${index + 1}`, `Delete effect ${index + 1}`)}>×</button></span></div>
        <FormRow label="type" path={joinPath(path, index, 'type')}><SelectInput value={type} options={options} onChange={nextType => update(index, defaultGemEffect(nextType))} /></FormRow>
        <GemEffectPayload effect={effect} type={type} path={joinPath(path, index)} onChange={nextEffect => update(index, nextEffect)} actionTypesResult={actionTypesResult} />
      </div>;
    })}
    <div className="prop-cost-actions">{GEM_EFFECT_TYPES.map(type => <button key={type} type="button" className="prop-add" onClick={() => add(type)}>+ {gemEffectTypeLabel(type)}</button>)}</div>
  </div>;
}

function GemEffectPayload({ effect, type, path, onChange, actionTypesResult }: { effect: AnyMap; type: string; path?: string; onChange: (value: AnyMap) => void; actionTypesResult: ItemFieldRendererContext['actionTypesResult'] }) {
  const setPayload = (key: string, value: unknown) => onChange(cleanObject({ ...effect, [key]: value }));
  if (type === 'variables') return <MapRow label="variables" path={joinPath(path, 'variables')} value={effect.variables} valuePlaceholder={copy('数值/公式', 'Value or formula')} addKeyPrefix="variable" onChange={variables => setPayload('variables', variables)} />;
  if (type === 'ea_attribute') return <MapRow label="ea_attributes" path={joinPath(path, 'ea_attributes')} value={effect.ea_attributes} valuePlaceholder={copy('属性值', 'Attribute value')} addKeyPrefix="attribute" onChange={ea_attributes => setPayload('ea_attributes', ea_attributes)} />;
  if (type === 'es_skill') return <FormRow label="es_skills" path={joinPath(path, 'es_skills')} wide><StringListEditor items={gemSkillList(effect)} onChange={items => onChange(cleanObject({ ...effect, es_skills: items, es_skill: undefined }))} placeholder={copy('技能 ID', 'Skill ID')} /></FormRow>;
  if (type === 'name_action') return <FormRow label="name_actions" path={joinPath(path, 'name_actions')} wide><ActionsEditor actions={parseActionList(effect.name_actions)} onChange={actions => setPayload('name_actions', serializeActionList(actions))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" moduleId={MODULE} namespace={MODULE} /></FormRow>;
  if (type === 'lore_action') return <FormRow label="lore_actions" path={joinPath(path, 'lore_actions')} wide><ActionsEditor actions={parseActionList(effect.lore_actions)} onChange={actions => setPayload('lore_actions', serializeActionList(actions))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" moduleId={MODULE} namespace={MODULE} /></FormRow>;
  const payload = Object.fromEntries(Object.entries(effect).filter(([key]) => key !== 'type'));
  return <MapRow label="fields" path={path} value={payload} onChange={fields => onChange(cleanObject({ type, ...fields }))} />;
}

function defaultGemEffect(type: string): AnyMap {
  if (isCoreEffectType(type)) return createCoreEffect(type as CoreEffectType);
  if (type === 'ea_attribute') return { type, ea_attributes: {} };
  if (type === 'es_skill') return { type, es_skills: [] };
  return { type };
}

function gemEffectTypeLabel(type: string): string {
  return { ea_attribute: copy('EA 属性', 'EA Attribute'), es_skill: copy('ES 技能', 'ES Skill') }[type] ?? coreEffectTypeLabel(type);
}

function gemSkillList(effect: AnyMap): string[] {
  const skills = asStringList(effect.es_skills);
  const single = textValue(effect.es_skill);
  return single ? [...skills, single] : skills;
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

function materialSources(material: AnyMap): string[] {
  const sources = editableStringList(material.item_sources);
  const legacy = textValue(material.item || material.material);
  return sources.length > 0 ? sources : legacy ? [legacy] : [];
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

