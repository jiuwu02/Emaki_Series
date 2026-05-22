import { getLocale, registerConfigCreateTemplate, registerConfigListItemSchema, registerConfigNodeMeta, registerConfigNodeRule, registerModuleLocale, getRuntimeEnum } from 'emaki-web-console';

const MODULE = 'EmakiAttribute';

const damageCauses = getRuntimeEnum('bukkit.damageCause');
const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;

type ConfigSpec = [path: string, label: string, comment: string, type: string, extra?: Record<string, unknown>];

const configFields: ConfigSpec[] = [
  ['hard_lock_damage', '接管原版伤害', '开启后未命中白名单的原版伤害也会进入 EmakiAttribute 结算；关闭后只有白名单原因进入。', 'boolean'],
  ['default_damage_type', '默认伤害类型', '未指定伤害类型或环境伤害回退时使用的 damage_types ID。', 'text'],
  ['regen_interval_ticks', '回复间隔', '生命、法力等资源自然回复的间隔，单位 tick。', 'number'],
  ['sync_delay_ticks', '同步延迟', '属性计算后同步到 Bukkit 原生属性的延迟，单位 tick。', 'number'],
  ['default_profile', '默认档案', '玩家默认资源上限、初始属性基础值和新玩家档案模板。', 'object'],
  ['default_profile.resources', '默认资源', '生命、法力等资源的默认最大值、边界与 Bukkit 同步策略。', 'object', { creatableChildren: true }],
  ['default_profile.attributes', '默认属性', '玩家默认拥有的属性基础值，key 为属性 ID。', 'object', { creatableChildren: true }],
  ['synthetic_hit_feedback', '击中反馈', '接管原版伤害后是否补发击退和受伤音效，避免伤害被替换后缺少反馈。', 'object'],
  ['synthetic_hit_feedback.knockback', '补发击退', '接管伤害后是否补发击退。', 'boolean'],
  ['synthetic_hit_feedback.knockback_strength', '击退强度', '补发击退力度系数。', 'number'],
  ['synthetic_hit_feedback.hurt_sound', '受伤音效', '接管伤害后是否补发受伤音效。', 'boolean'],
  ['scaling_curves', '衰减曲线', '属性超过阈值后按曲线衰减，防止数值无限膨胀。', 'object', { creatableChildren: true }],
  ['allowed_damage_causes', '伤害来源白名单', '允许进入 EmakiAttribute 结算的 Bukkit DamageCause 列表。', 'list']
];

const commonFields: Record<string, [string, string, string]> = {
  display_name: ['显示名称', '资源、属性或 GUI 中展示给玩家看的名称。', 'text'],
  default_max: ['默认最大值', '资源默认最大值。', 'number'],
  min_max: ['最大值下限', '资源最大值允许的最低值。', 'number'],
  max_max: ['最大值上限', '资源最大值允许的最高值。', 'number'],
  sync_to_bukkit: ['同步 Bukkit', '是否把该资源同步到 Bukkit 原生属性。', 'boolean'],
  full_on_init: ['初始满值', '初始化档案时是否把资源填充至最大值。', 'boolean'],
  default_value: ['默认值', '属性或资源的默认基础数值。', 'number'],
  group: ['分组', '属性所属功能分组，例如 physical、spell、utility。', 'text'],
  role: ['角色', '属性定位，例如 offense、defense、sustain。', 'text'],
  summary: ['摘要', '属性或权重条目的短说明。', 'text'],
  id: ['ID', '定义文件的唯一标识。', 'text'],
  value_kind: ['数值类型', '属性数值语义，例如固定值、百分比、概率、回复或资源。', 'enum'],
  target_type: ['目标类型', '属性作用目标，例如通用、原版属性、资源或伤害。', 'enum'],
  target_id: ['目标 ID', 'target_type 为 VANILLA、RESOURCE 或 DAMAGE 时映射的目标 ID。', 'text'],
  min_value: ['最小值', '属性允许的最小值，低于此值会被钳制。', 'number'],
  max_value: ['最大值', '属性允许的最大值，超过此值会被钳制。', 'number'],
  allow_negative: ['允许负值', '是否允许该属性为负数。', 'boolean'],
  priority: ['优先级', '词条读取或匹配优先级，数值越大越先尝试。', 'number'],
  lore_format_id: ['词条格式', '关联 lore_formats 下的格式 ID。', 'text'],
  lore_patterns: ['词条正则', '从物品 Lore 中识别属性数值的正则表达式列表。', 'list'],
  attribute_power: ['属性战力', '该属性每 1 点对应的战力评分系数。', 'number'],
  description: ['说明', '定义文件的详细说明，用于文档和调试输出。', 'text'],
  aliases: ['别名', '伤害类型可被引用的别名列表。', 'list'],
  allowed_events: ['允许事件', '允许触发该伤害类型的 Bukkit DamageCause 列表。', 'list'],
  hard_lock: ['硬锁定', '是否强制接管该伤害类型对应的原版事件。', 'boolean'],
  stages: ['结算阶段', '伤害结算的有序阶段列表。', 'list'],
  recovery: ['恢复规则', '造成伤害后的吸血或资源恢复规则。', 'object'],
  attacker_message: ['攻击者消息', '伤害结算后发送给攻击者的消息模板。', 'text'],
  target_message: ['受击者消息', '伤害结算后发送给受击者的消息模板。', 'text'],
  source: ['来源', '阶段或恢复数据来源。', 'enum'],
  resistance_source: ['抗性来源', '恢复抗性属性的来源。', 'enum'],
  flat_attributes: ['固定属性', '参与固定值计算的属性 ID 列表。', 'list'],
  percent_attributes: ['百分比属性', '参与百分比计算的属性 ID 列表。', 'list'],
  chance_attributes: ['概率属性', '决定阶段是否触发的概率属性 ID 列表。', 'list'],
  multiplier_attributes: ['倍率属性', '触发后的倍率属性 ID 列表。', 'list'],
  expression: ['计算表达式', '自定义计算表达式，支持 {input}、{flat}、{percent} 等变量。', 'text'],
  format: ['格式模板', '词条显示格式，支持 {name}、{value}、{sign} 等占位符。', 'text'],
  precision: ['精度', '数值显示的小数位数。', 'number'],
  read_priority: ['读取优先级', '从 Lore 解析属性时的匹配优先级。', 'number'],
  read_patterns: ['读取正则', '从 Lore 文本中提取数值的正则表达式列表。', 'list'],
  source_id: ['来源 ID', '条件规则来源标识，用于日志和调试追踪。', 'text'],
  condition_type: ['条件逻辑', '多条件组合逻辑。', 'enum'],
  invalid_as_failure: ['解析失败视为失败', '条件表达式解析失败时是否视为不通过。', 'boolean'],
  conditions: ['条件列表', '具体条件项列表，每项包含 type、key/pattern 和 condition 表达式。', 'list'],
  key: ['PDC 键名', '要匹配的 PDC 数据键名。', 'text'],
  pattern: ['正则模式', '用于从 Lore 中提取数值的正则表达式。', 'text'],
  condition: ['判定表达式', '支持 {value}、{player_level}、{player_name} 等变量的判定表达式。', 'text'],
  require_match: ['必须匹配', '是否要求正则必须命中才视为通过。', 'boolean']
};

const damageCauseLabels: Record<string, string> = {
  KILL: '击杀', WORLD_BORDER: '世界边界', CONTACT: '接触', ENTITY_ATTACK: '实体攻击', ENTITY_SWEEP_ATTACK: '横扫攻击', PROJECTILE: '弹射物',
  SUFFOCATION: '窒息', FALL: '摔落', FIRE: '火焰', FIRE_TICK: '燃烧', MELTING: '融化', LAVA: '岩浆', DROWNING: '溺水',
  BLOCK_EXPLOSION: '方块爆炸', ENTITY_EXPLOSION: '实体爆炸', VOID: '虚空', LIGHTNING: '雷击', SUICIDE: '自杀', STARVATION: '饥饿',
  POISON: '中毒', MAGIC: '魔法', WITHER: '凋零', FALLING_BLOCK: '落块', THORNS: '荆棘反伤', DRAGON_BREATH: '龙息',
  FLY_INTO_WALL: '碰撞墙体', HOT_FLOOR: '高温方块', CAMPFIRE: '营火', CRAMMING: '实体挤压', DRYOUT: '脱水', FREEZE: '冻结',
  SONIC_BOOM: '音爆', CUSTOM: '自定义'
};

registerModuleLocale(MODULE, 'zh-CN', {
  'emakiattribute.module.name': 'Attribute',
  'emakiattribute.module.summary': '属性、资源、伤害接管与曲线',
  'emakiattribute.file.config.title': '主配置',
  'emakiattribute.file.config.comment': '属性系统主配置，包含伤害接管、资源恢复和属性曲线。',
  'emakiattribute.file.attribute_balance.title': '属性权重',
  'emakiattribute.file.attribute_balance.comment': '属性语义分组、角色定位与评分权重配置。',
  'emakiattribute.file.attributes.title': '属性定义',
  'emakiattribute.file.attributes.comment': '属性定义文件目录，每个文件定义一个属性的 ID、类型、范围和词条格式。',
  'emakiattribute.file.damage_types.title': '伤害类型',
  'emakiattribute.file.damage_types.comment': '伤害类型定义文件目录，每个文件定义一种伤害的结算阶段和恢复规则。',
  'emakiattribute.file.lore_formats.title': '词条格式',
  'emakiattribute.file.lore_formats.comment': '词条格式定义文件目录，每个文件定义一种属性在物品 Lore 中的显示模板。',
  'emakiattribute.file.conditions.title': 'PDC 条件',
  'emakiattribute.file.conditions.comment': 'PDC 属性读取条件定义文件目录，控制物品属性在何种条件下生效。',
  'emakiattribute.filePath.conditions_default_bind.title': '默认绑定条件',
  'emakiattribute.filePath.conditions_default_bind.comment': '默认绑定条件定义文件。',
  'emakiattribute.filePath.conditions_default_equipment_level.title': '默认装备等级条件',
  'emakiattribute.filePath.conditions_default_equipment_level.comment': '默认装备等级条件定义文件。',
  'emakiattribute.filePath.conditions_emakiitem.title': 'EmakiItem 条件',
  'emakiattribute.filePath.conditions_emakiitem.comment': 'EmakiItem 条件定义文件。',
  'emakiattribute.filePath.conditions_forge.title': '锻造条件',
  'emakiattribute.filePath.conditions_forge.comment': '锻造条件定义文件。',
  'emakiattribute.filePath.conditions_strengthen.title': '强化条件',
  'emakiattribute.filePath.conditions_strengthen.comment': '强化条件定义文件。',
  'emakiattribute.file.lang.title': '语言文件',
  'emakiattribute.file.lang.comment': 'Attribute 语言资源文件目录。',
  'emakiattribute.file.plugin.title': '插件描述',
  'emakiattribute.file.plugin.comment': 'plugin.yml 插件描述与依赖声明。',
  'emakiattribute.file.web-console.title': 'Web Console 声明',
  'emakiattribute.file.web-console.comment': 'Web Console 文件注册与资源入口声明。',
  ...Object.fromEntries(configFields.flatMap(([path, label, comment]) => [[`emakiattribute.field.${path}`, label], [`emakiattribute.comment.${path}`, comment]])),
  ...Object.fromEntries(Object.entries(commonFields).flatMap(([key, [label, comment]]) => [[`emakiattribute.field.${key}`, label], [`emakiattribute.comment.${key}`, comment]])),
  'emakiattribute.field.allowed_damage_causes.cause': '伤害来源',
  'emakiattribute.field.allowed_damage_causes.damage_type': '伤害类型',
  'emakiattribute.field.allowed_damage_causes.damage': '基础伤害',
  'emakiattribute.field.allowed_damage_causes.enabled': '启用',
  ...Object.fromEntries(Object.entries(damageCauseLabels).map(([key, label]) => [`emakiattribute.option.damageCause.${key}`, label])),
  'emakiattribute.option.value_kind.FLAT': '固定值',
  'emakiattribute.option.value_kind.PERCENT': '百分比',
  'emakiattribute.option.value_kind.CHANCE': '概率',
  'emakiattribute.option.value_kind.REGEN': '回复',
  'emakiattribute.option.value_kind.RESOURCE': '资源',
  'emakiattribute.option.target_type.GENERIC': '通用',
  'emakiattribute.option.target_type.VANILLA': '原版属性',
  'emakiattribute.option.target_type.RESOURCE': '资源',
  'emakiattribute.option.target_type.DAMAGE': '伤害',
  'emakiattribute.option.condition_type.all_of': '全部满足',
  'emakiattribute.option.condition_type.any_of': '任一满足'
});

registerModuleLocale(MODULE, 'en-US', {
  'emakiattribute.module.name': 'Attribute',
  'emakiattribute.module.summary': 'Attributes, resources, hard damage handling, and curves',
  'emakiattribute.file.config.title': 'Main Config',
  'emakiattribute.file.config.comment': 'Main attribute system configuration covering damage handling, resource recovery, and scaling curves.',
  'emakiattribute.file.attribute_balance.title': 'Attribute Weights',
  'emakiattribute.file.attribute_balance.comment': 'Semantic grouping, role positioning, and scoring weights for attributes.',
  'emakiattribute.file.attributes.title': 'Attribute Definitions',
  'emakiattribute.file.attributes.comment': 'Directory of attribute definition files. Each file defines an attribute ID, type, range, and lore format.',
  'emakiattribute.file.damage_types.title': 'Damage Types',
  'emakiattribute.file.damage_types.comment': 'Directory of damage type definition files. Each file defines a damage settlement stage and recovery rules.',
  'emakiattribute.file.lore_formats.title': 'Lore Formats',
  'emakiattribute.file.lore_formats.comment': 'Directory of lore format files. Each file defines the display template for an attribute in item lore.',
  'emakiattribute.file.conditions.title': 'PDC Conditions',
  'emakiattribute.file.conditions.comment': 'Directory of PDC attribute read conditions that control when item attributes take effect.',
  'emakiattribute.filePath.conditions_default_bind.title': 'Default Bind Condition',
  'emakiattribute.filePath.conditions_default_bind.comment': 'Default bind condition definition file.',
  'emakiattribute.filePath.conditions_default_equipment_level.title': 'Default Equipment Level Condition',
  'emakiattribute.filePath.conditions_default_equipment_level.comment': 'Default equipment level condition definition file.',
  'emakiattribute.filePath.conditions_emakiitem.title': 'EmakiItem Condition',
  'emakiattribute.filePath.conditions_emakiitem.comment': 'EmakiItem condition definition file.',
  'emakiattribute.filePath.conditions_forge.title': 'Forge Condition',
  'emakiattribute.filePath.conditions_forge.comment': 'Forge condition definition file.',
  'emakiattribute.filePath.conditions_strengthen.title': 'Strengthen Condition',
  'emakiattribute.filePath.conditions_strengthen.comment': 'Strengthen condition definition file.',
  'emakiattribute.file.lang.title': 'Language Files',
  'emakiattribute.file.lang.comment': 'Directory for Attribute language resources.',
  'emakiattribute.file.plugin.title': 'Plugin Description',
  'emakiattribute.file.plugin.comment': 'plugin.yml plugin metadata and dependency declaration.',
  'emakiattribute.file.web-console.title': 'Web Console Declaration',
  'emakiattribute.file.web-console.comment': 'Web Console file registration and resource entry declaration.',
  'emakiattribute.field.hard_lock_damage': 'Hard-lock Damage',
  'emakiattribute.field.default_damage_type': 'Default Damage Type',
  'emakiattribute.field.default_profile': 'Default Profile',
  'emakiattribute.field.scaling_curves': 'Scaling Curves',
  'emakiattribute.field.allowed_damage_causes': 'Allowed Damage Causes',
  'emakiattribute.field.allowed_damage_causes.cause': 'Cause',
  'emakiattribute.field.allowed_damage_causes.damage_type': 'Damage Type',
  'emakiattribute.field.allowed_damage_causes.damage': 'Base Damage',
  'emakiattribute.field.allowed_damage_causes.enabled': 'Enabled'
});

configFields.forEach(([path, label, comment, type, extra]) => registerConfigNodeMeta(MODULE, path, { label, comment, type, ...(extra ?? {}) }));
Object.entries(commonFields).forEach(([key, [label, comment, type]]) => registerConfigNodeRule(MODULE, { key }, { label, comment, type }));
registerConfigNodeRule(MODULE, { key: 'value_kind' }, { label: copy('数值类型', 'Value kind'), comment: '属性数值语义。', type: 'enum', options: ['FLAT', 'PERCENT', 'CHANCE', 'REGEN', 'RESOURCE'], optionLabelPrefix: 'value_kind' });
registerConfigNodeRule(MODULE, { key: 'target_type' }, { label: copy('目标类型', 'Target type'), comment: '属性作用目标类型。', type: 'enum', options: ['GENERIC', 'VANILLA', 'RESOURCE', 'DAMAGE'], optionLabelPrefix: 'target_type' });
registerConfigNodeRule(MODULE, { key: 'condition_type' }, { label: copy('条件逻辑', 'Condition logic'), comment: '多条件组合逻辑。', type: 'enum', options: ['all_of', 'any_of'], optionLabelPrefix: 'condition_type' });

registerConfigCreateTemplate(MODULE, 'default_profile.resources', { id: 'resource', label: copy('资源模板', 'Resource template'), fields: [
  { path: 'display_name', label: '显示名称', comment: '资源在界面中显示的名称。', type: 'text', defaultValue: '新资源' },
  { path: 'default_max', label: '默认最大值', comment: '资源默认最大值。', type: 'number', defaultValue: 100 },
  { path: 'min_max', label: '最大值下限', comment: '资源最大值允许的最低值。', type: 'number', defaultValue: 0 },
  { path: 'max_max', label: '最大值上限', comment: '资源最大值允许的最高值。', type: 'number', defaultValue: 1000 },
  { path: 'sync_to_bukkit', label: '同步 Bukkit', comment: '是否同步到 Bukkit 原生属性。', type: 'boolean', defaultValue: false },
  { path: 'full_on_init', label: '初始满值', comment: '初始化时是否填充至最大值。', type: 'boolean', defaultValue: true }
] });
registerConfigCreateTemplate(MODULE, 'default_profile.attributes', { id: 'attribute', label: copy('属性默认值', 'Attribute default value'), fields: [
  { path: 'default_value', label: '默认值', comment: '属性默认基础数值。', type: 'number', defaultValue: 0 }
] });
registerConfigCreateTemplate(MODULE, 'scaling_curves', { id: 'curve', label: copy('衰减曲线模板', 'Scaling curve template'), fields: [
  { path: 'attribute', label: '属性 ID', comment: '需要应用衰减的属性 ID。', type: 'text', defaultValue: 'physical_attack' },
  { path: 'threshold', label: '阈值', comment: '超过该值后开始衰减。', type: 'number', defaultValue: 100 },
  { path: 'curve_type', label: '曲线类型', comment: '超过阈值后使用的衰减函数类型。', type: 'enum', options: ['logarithmic', 'sqrt', 'piecewise_linear'], defaultValue: 'logarithmic' },
  { path: 'factor', label: '系数', comment: '衰减计算系数。', type: 'number', defaultValue: 1 }
] });

registerConfigListItemSchema(MODULE, 'allowed_damage_causes', [
  { path: 'cause', label: '伤害来源', comment: 'Bukkit DamageCause，选项来自当前服务端编译期 API。', type: 'enum', options: damageCauses, optionLabelPrefix: 'damageCause' },
  { path: 'damage_type', label: '伤害类型', comment: '对应 damage_types/ 下的伤害类型 ID。', type: 'text', defaultValue: 'physical' },
  { path: 'damage', label: '基础伤害', comment: '进入 EmakiAttribute 结算时使用的基础伤害值。', type: 'number', defaultValue: 1 },
  { path: 'enabled', label: '启用', comment: '是否启用此伤害来源规则。', type: 'boolean', defaultValue: true }
], { uniqueBy: 'cause' });
