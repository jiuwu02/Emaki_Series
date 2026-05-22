import { getLocale, registerConfigListItemSchema, registerConfigListItemSchemaRule, registerConfigNodeMeta, registerConfigNodeRule, registerModuleLocale, registerPluginGuiEditor } from 'emaki-web-console';

const MODULE = 'EmakiSkills';

type FieldSpec = [path: string, label: string, comment: string, type: string, extra?: Record<string, unknown>];

const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;

const fields: FieldSpec[] = [
  ['slots', '技能槽位', '玩家主动技能槽位数量与默认分配。', 'object'],
  ['slots.default_count', '默认槽数', '新玩家或未初始化玩家默认拥有的主动技能槽数量。', 'number'],
  ['cast_mode', '施法模式', '进入/退出施法模式的按键、状态恢复与客户端限制说明。', 'object'],
  ['cast_mode.entry_key', '切换按键', '进入或退出施法模式的按键标识。Spigot 服务端不能监听所有客户端本地键位。', 'text'],
  ['cast_mode.restore_last_state_on_join', '登录恢复状态', '玩家重新登录后是否恢复上次退出时的施法模式状态。', 'boolean'],
  ['cast_timing', '施法时序', '技能释放后的全局延迟与冷却节奏。', 'object'],
  ['cast_timing.forced_global_cast_delay_ticks', '全局施法延迟', '任意技能成功释放后强制施加的全局冷却，单位 tick；0 表示关闭。', 'number'],
  ['actionbar', '技能 ActionBar', '施法模式和普通状态下的 ActionBar 技能栏显示。', 'object'],
  ['actionbar.enabled', '启用显示', '是否启用技能 ActionBar 状态显示。', 'boolean'],
  ['actionbar.refresh_interval_ticks', '刷新间隔', 'ActionBar 内容刷新间隔，单位 tick。', 'number'],
  ['actionbar.template_cast_mode', '施法模板', '施法模式下的 ActionBar 模板，支持技能槽和冷却占位符。', 'text'],
  ['actionbar.template_idle', '待机模板', '非施法模式下的 ActionBar 模板；空字符串表示不显示。', 'text'],
  ['script_engine', '脚本引擎', '原生技能脚本执行模式、错误处理和单阶段安全限制。', 'object'],
  ['script_engine.enabled', '启用脚本引擎', '是否启用 EmakiSkills 原生脚本执行器。', 'boolean'],
  ['script_engine.default_mode', '默认模式', '技能未声明模式时使用的执行模式。', 'enum', { options: ['native', 'mythic', 'hybrid'], optionLabelPrefix: 'script_engine.default_mode' }],
  ['script_engine.stop_on_failure', '失败停止', '某一行动失败时是否停止后续动作。', 'boolean'],
  ['script_engine.max_lines_per_phase', '阶段最大行数', '每个脚本阶段允许的最大行数，防止过长脚本拖慢主线程。', 'number'],
  ['script_engine.max_targets_per_action', '动作目标上限', '单个动作最多处理的目标数量。', 'number'],
  ['script_engine.debug', '脚本调试', '是否输出脚本执行调试信息，生产环境建议关闭。', 'boolean'],
  ['triggers', '主动触发器', '左键、右键、Shift 与数字键等可由玩家主动绑定的触发器。', 'object'],
  ['passive_trigger_settings', '被动触发设置', '被动触发器的全局检查间隔和连击判定时间。', 'object'],
  ['passive_trigger_settings.timer_interval_ticks', '定时间隔', 'timer 被动触发器的检查间隔，单位 tick。', 'number'],
  ['passive_trigger_settings.combo_timeout_ticks', '连击超时', 'combo_attack 连击触发器的重置超时时间，单位 tick。', 'number'],
  ['passive_triggers', '被动触发器', '攻击、受伤、击杀、射箭、方块、登录、潜行、定时等由事件自动触发的技能触发器。', 'object']
];

const triggerFields: Record<string, [string, string, string]> = {
  display_name: ['显示名称', '触发器在 GUI、ActionBar 或提示文本中显示的名称。', 'text'],
  enabled: ['启用', '是否启用当前触发器或功能项。', 'boolean'],
  incompatible_with: ['互斥触发器', '与当前触发器不能同时绑定或同时生效的触发器 ID 列表。', 'stringList']
};

const localeMessages: Record<string, string> = Object.fromEntries([
  ['emakiskills.module.name', 'Skills'],
  ['emakiskills.module.summary', '槽位、施法模式、触发器'],
  ['emakiskills.file.config.title', '主配置'],
  ['emakiskills.file.config.comment', '技能系统主配置，包含触发器、施法模式、资源和升级设置。'],
  ['emakiskills.file.gui.title', 'GUI 模板'],
  ['emakiskills.file.gui.comment', '技能面板与触发器选择 GUI 模板文件。'],
  ['emakiskills.file.skills.title', '技能定义'],
  ['emakiskills.file.skills.comment', '技能定义文件目录。'],
  ['emakiskills.file.resources.title', '资源定义'],
  ['emakiskills.file.resources.comment', '技能资源定义文件目录。'],
  ['emakiskills.filePath.skills_example_skill.title', '示例技能'],
  ['emakiskills.filePath.skills_example_skill.comment', '示例技能定义文件。'],
  ['emakiskills.filePath.skills_example_combo_skill.title', '连击示例技能'],
  ['emakiskills.filePath.skills_example_combo_skill.comment', '连击示例技能定义文件。'],
  ['emakiskills.filePath.skills_example_projectile_skill.title', '投射物示例技能'],
  ['emakiskills.filePath.skills_example_projectile_skill.comment', '投射物示例技能定义文件。'],
  ['emakiskills.filePath.resources_example_resource.title', '示例资源'],
  ['emakiskills.filePath.resources_example_resource.comment', '示例资源定义文件。'],
  ['emakiskills.filePath.gui_skills_gui.title', '技能 GUI'],
  ['emakiskills.filePath.gui_skills_gui.comment', '技能 GUI 模板文件。'],
  ['emakiskills.filePath.gui_trigger_select_gui.title', '触发器选择 GUI'],
  ['emakiskills.filePath.gui_trigger_select_gui.comment', '触发器选择 GUI 模板文件。'],
  ['emakiskills.file.lang.title', '语言文件'],
  ['emakiskills.file.lang.comment', 'Skills 语言资源文件目录。'],
  ['emakiskills.file.plugin.title', '插件描述'],
  ['emakiskills.file.plugin.comment', 'plugin.yml 插件描述与依赖声明。'],
  ['emakiskills.file.web-console.title', 'Web Console 声明'],
  ['emakiskills.file.web-console.comment', 'Web Console 文件注册与资源入口声明。'],
  ...fields.flatMap(([path, label, comment]) => [[`emakiskills.field.${path}`, label], [`emakiskills.comment.${path}`, comment]]),
  ...Object.entries(triggerFields).flatMap(([key, [label, comment]]) => [[`emakiskills.field.${key}`, label], [`emakiskills.comment.${key}`, comment]])
]);

registerModuleLocale(MODULE, 'zh-CN', {
  ...localeMessages,
  'emakiskills.surface.gui': '技能 GUI',
  'emakiskills.option.script_engine.default_mode.native': '原生脚本',
  'emakiskills.option.script_engine.default_mode.mythic': 'Mythic 技能',
  'emakiskills.option.script_engine.default_mode.hybrid': '混合模式'
});

registerModuleLocale(MODULE, 'en-US', {
  'emakiskills.module.name': 'Skills',
  'emakiskills.module.summary': 'Slots, cast modes, and triggers',
  'emakiskills.file.config.title': 'Main Config',
  'emakiskills.file.config.comment': 'Main skill system configuration covering triggers, cast modes, resources, and progression.',
  'emakiskills.file.gui.title': 'GUI Templates',
  'emakiskills.file.gui.comment': 'Skill panel and trigger selection GUI templates.',
  'emakiskills.file.skills.title': 'Skill Definitions',
  'emakiskills.file.skills.comment': 'Directory for skill definition files.',
  'emakiskills.file.resources.title': 'Resource Definitions',
  'emakiskills.file.resources.comment': 'Directory for skill resource files.',
  'emakiskills.filePath.skills_example_skill.title': 'Sample Skill',
  'emakiskills.filePath.skills_example_skill.comment': 'Sample skill definition file.',
  'emakiskills.filePath.skills_example_combo_skill.title': 'Sample Combo Skill',
  'emakiskills.filePath.skills_example_combo_skill.comment': 'Sample combo skill definition file.',
  'emakiskills.filePath.skills_example_projectile_skill.title': 'Sample Projectile Skill',
  'emakiskills.filePath.skills_example_projectile_skill.comment': 'Sample projectile skill definition file.',
  'emakiskills.filePath.resources_example_resource.title': 'Sample Resource',
  'emakiskills.filePath.resources_example_resource.comment': 'Sample resource definition file.',
  'emakiskills.filePath.gui_skills_gui.title': 'Skills GUI',
  'emakiskills.filePath.gui_skills_gui.comment': 'Skills GUI template file.',
  'emakiskills.filePath.gui_trigger_select_gui.title': 'Trigger Selection GUI',
  'emakiskills.filePath.gui_trigger_select_gui.comment': 'Trigger selection GUI template file.',
  'emakiskills.file.lang.title': 'Language Files',
  'emakiskills.file.lang.comment': 'Directory for Skills language resources.',
  'emakiskills.file.plugin.title': 'Plugin Description',
  'emakiskills.file.plugin.comment': 'plugin.yml plugin metadata and dependency declaration.',
  'emakiskills.file.web-console.title': 'Web Console Declaration',
  'emakiskills.file.web-console.comment': 'Web Console file registration and resource entry declaration.',
  'emakiskills.surface.gui': 'Skills GUI',
  'emakiskills.field.slots': 'Skill Slots',
  'emakiskills.field.cast_mode': 'Cast Mode',
  'emakiskills.field.cast_timing': 'Cast Timing',
  'emakiskills.field.actionbar': 'ActionBar',
  'emakiskills.field.script_engine': 'Script Engine',
  'emakiskills.field.triggers': 'Active Triggers',
  'emakiskills.field.passive_trigger_settings': 'Passive Trigger Settings',
  'emakiskills.field.passive_triggers': 'Passive Triggers',
  'emakiskills.field.display_name': 'Display Name',
  'emakiskills.field.incompatible_with': 'Incompatible With',
  'emakiskills.option.script_engine.default_mode.native': 'Native',
  'emakiskills.option.script_engine.default_mode.mythic': 'Mythic',
  'emakiskills.option.script_engine.default_mode.hybrid': 'Hybrid'
});

fields.forEach(([path, label, comment, type, extra]) => registerConfigNodeMeta(MODULE, path, { label, comment, type, ...(extra ?? {}) }));
Object.entries(triggerFields).forEach(([key, [label, comment, type]]) => registerConfigNodeRule(MODULE, { key }, { label, comment, type }));

registerConfigNodeRule(MODULE, { key: 'description' }, { label: '技能描述', comment: '技能说明文本列表。', type: 'stringList' });
registerConfigNodeRule(MODULE, { key: 'lore_aliases' }, { label: 'Lore 别名', comment: '用于 Lore 匹配识别的别名列表。', type: 'stringList' });
registerConfigNodeRule(MODULE, { key: 'conditions' }, { label: '释放条件', comment: '技能释放前检查的条件表达式列表。', type: 'stringList' });
registerConfigNodeRule(MODULE, { key: 'passive_triggers' }, { label: '被动触发器', comment: '被动技能触发器 ID 列表。', type: 'stringList' });
registerConfigNodeRule(MODULE, { key: 'incompatible_with' }, { label: '互斥触发器', comment: '与当前触发器互斥的触发器 ID 列表。', type: 'stringList' });
registerConfigNodeRule(MODULE, { key: 'resource_costs' }, { label: '资源消耗', comment: '释放技能时消耗或检查的资源列表。', type: 'list' });
registerConfigNodeRule(MODULE, { key: 'currencies' }, { label: '升级货币', comment: '升级经济中各币种的成本列表。', type: 'list' });
registerConfigNodeRule(MODULE, { suffix: '.materials' }, { label: '升级材料', comment: '升级等级所需材料列表。', type: 'list' });
registerConfigNodeRule(MODULE, { key: 'cast' }, { label: '释放阶段', comment: '旧版脚本中的 cast 动作列表。', type: 'stringList' });
registerConfigNodeRule(MODULE, { key: 'hit' }, { label: '命中阶段', comment: '旧版脚本中的 hit 动作列表。', type: 'stringList' });
registerConfigNodeRule(MODULE, { key: 'miss' }, { label: '未命中阶段', comment: '旧版脚本中的 miss 动作列表。', type: 'stringList' });
registerConfigNodeRule(MODULE, { key: 'fail' }, { label: '失败阶段', comment: '旧版脚本中的 fail 动作列表。', type: 'stringList' });

registerConfigListItemSchema(MODULE, 'resource_costs', [
  { path: 'type', label: '资源类型', comment: '资源消耗类型。', type: 'enum', options: ['ea-resource', 'attribute-check', 'local-resource'], defaultValue: 'local-resource', optionLabelPrefix: 'skill.resource_cost.type' },
  { path: 'target_id', label: '目标 ID', comment: '资源或属性标识。', type: 'text', defaultValue: 'mana' },
  { path: 'amount', label: '数量', comment: '消耗或检查的数量。', type: 'number', defaultValue: 1 },
  { path: 'operation', label: '操作', comment: 'consume 为消耗，require 为检查。', type: 'enum', options: ['consume', 'require'], defaultValue: 'consume', optionLabelPrefix: 'skill.resource_cost.operation' },
  { path: 'failure_message', label: '失败提示', comment: '资源不足时显示的提示消息。', type: 'text', defaultValue: '资源不足' }
], { uniqueBy: 'target_id' });

registerConfigListItemSchema(MODULE, 'upgrade.economy.currencies', [
  { path: 'provider', label: '提供方', comment: '货币提供方或桥接器。', type: 'text', defaultValue: 'vault' },
  { path: 'currency_id', label: '货币 ID', comment: '具体货币标识。', type: 'text', defaultValue: 'currency' },
  { path: 'base_cost', label: '基础成本', comment: '未套公式时的基础数值。', type: 'number', defaultValue: 0 },
  { path: 'cost_formula', label: '成本公式', comment: '按等级计算成本的公式。', type: 'text', defaultValue: '{base_cost}' },
  { path: 'display_name', label: '显示名', comment: 'GUI 中显示的货币名称。', type: 'text', defaultValue: '' }
], { uniqueBy: 'currency_id' });

registerConfigListItemSchemaRule(MODULE, { suffix: 'materials' }, [
  { path: 'item_sources', label: '物品来源', comment: '作为材料的 ItemSource 列表。', type: 'stringList', defaultValue: ['minecraft-iron_ingot'] },
  { path: 'amount', label: '数量', comment: '此材料需要的数量。', type: 'number', defaultValue: 1 },
  { path: 'optional', label: '可选', comment: '是否可选材料。', type: 'boolean', defaultValue: false },
  { path: 'protection', label: '保护', comment: '是否受保护规则影响。', type: 'boolean', defaultValue: false }
]);

registerPluginGuiEditor({
  moduleId: MODULE,
  editorId: 'emakiskills:gui',
  label: copy('技能 GUI', 'Skills GUI'),
  fields: [
    ['type', '槽位类型', '技能业务槽位语义。', 'text'],
    ['active_slot', '主动技能槽位', '玩家主动技能槽位。', 'text'],
    ['skill_pool', '技能池', '可装配技能列表区域。', 'text'],
    ['cast_mode_toggle', '施法模式按钮', '切换技能施法模式的按钮槽位。', 'text'],
    ['trigger_selector', '触发器选择槽', '用于选择主动触发器的槽位。', 'text'],
    ['page_prev', '上一页', '向前翻页按钮。', 'text'],
    ['page_next', '下一页', '向后翻页按钮。', 'text']
  ]
});
