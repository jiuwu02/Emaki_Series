import { getLocale, registerModuleLocale, registerPluginConfig, registerPluginGuiEditor, standardCurrencyCostFields, standardMaterialCostFields } from 'emaki-web-console';

const MODULE = 'EmakiSkills';

type FieldSpec = [path: string, label: string, comment: string, type: string, extra?: Record<string, unknown>];

const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;

const materialFields = standardMaterialCostFields({
  overrides: {
    item_sources: { label: '物品来源', comment: '作为材料的 ItemSource 列表。', defaultValue: ['minecraft-iron_ingot'] },
    amount: { label: '数量', comment: '此材料需要的数量。', defaultValue: 1 },
    optional: { label: '可选', comment: '是否可选材料。', defaultValue: false },
    protection: { label: '保护', comment: '是否受保护规则影响。', defaultValue: false }
  }
});

const currencyFields = standardCurrencyCostFields({
  overrides: {
    provider: { label: '提供方', comment: '货币提供方或桥接器。', defaultValue: 'vault' },
    currency_id: { label: '货币 ID', comment: '具体货币标识。', defaultValue: 'currency' },
    base_cost: { label: '基础成本', comment: '未套公式时的基础数值。', defaultValue: 0 },
    cost_formula: { label: '成本公式', comment: '按等级计算成本的公式。', defaultValue: '{base_cost}' },
    display_name: { label: '显示名', comment: 'GUI 中显示的货币名称。', defaultValue: '' }
  }
});

const parameterFields = [
  { path: 'type', label: '类型', comment: '参数类型。数值分布: constant/range/uniform/gaussian/skew_normal/triangle/expression；文本: string/random_text/random_char/weighted_random_char/conditional_char；布尔: boolean。', type: 'enum', options: ['string', 'random_text', 'random_char', 'weighted_random_char', 'conditional_char', 'boolean', 'constant', 'range', 'uniform', 'gaussian', 'skew_normal', 'triangle', 'expression'], optionLabelPrefix: 'skill.parameter.type', defaultValue: 'constant' },
  { path: 'value', label: '值', comment: '常量值或表达式值。', type: 'text', defaultValue: '' },
  { path: 'expression', label: '表达式', comment: '表达式参数内容。', type: 'text', defaultValue: '' },
  { path: 'formula', label: '公式', comment: '资源消耗或效果计算公式。若 expression/value 同时存在，请保持含义一致。', type: 'text', defaultValue: '' },
  { path: 'min', label: '最小值', comment: '范围或数值参数下限。', type: 'number', defaultValue: 0 },
  { path: 'max', label: '最大值', comment: '范围或数值参数上限。', type: 'number', defaultValue: 0 },
  { path: 'decimals', label: '小数位', comment: '数值格式的小数位数。', type: 'number', defaultValue: 0 },
  { path: 'default', label: '默认值', comment: '参数缺省值。', type: 'text', defaultValue: '' },
  { path: 'lines', label: '随机文本行', comment: 'random_text 参数可用文本行。', type: 'stringList', defaultValue: [] },
  { path: 'chars', label: '候选字符', comment: 'random_char/weighted_random_char 的候选字符；random_char 可留空使用 a-z。', type: 'text', defaultValue: '' },
  { path: 'weights', label: '权重列表', comment: 'weighted_random_char 的权重列表，按位置对应候选字符。', type: 'numberList', defaultValue: [] },
  { path: 'count', label: '随机次数', comment: '随机字符抽取次数，默认 1。', type: 'number', defaultValue: 1 },
  { path: 'allow_duplicates', label: '允许重复', comment: '是否允许同一次解析中重复抽到同一个候选字符。', type: 'boolean', defaultValue: false },
  { path: 'condition', label: '条件', comment: 'conditional_char 的二选一布尔表达式。', type: 'text', defaultValue: '' },
  { path: 'true_value', label: '成立输出', comment: 'conditional_char 条件成立时输出的字符或文本。', type: 'text', defaultValue: '' },
  { path: 'false_value', label: '不成立输出', comment: 'conditional_char 条件不成立或无法判断时输出的字符或文本。', type: 'text', defaultValue: '' },
  { path: 'cases', label: '穷举条件', comment: 'conditional_char 穷举条件列表，建议每项包含 condition 和 value。', type: 'objectList', defaultValue: [] },
  { path: 'fallback', label: '兜底输出', comment: '没有任何穷举条件命中时输出的字符或文本。', type: 'text', defaultValue: '' }
];

const fields: FieldSpec[] = [
  ['language', '语言', '语言文件 ID，对应 lang/<language>.yml。', 'text'],
  ['version', '配置版本', '默认配置结构版本，通常不建议手动修改。', 'text'],
  ['release_default_data', '释放默认数据', '首次启动或缺失 skills/、resources/ 等示例数据时是否释放默认文件。', 'boolean'],
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

const FAILURE_PENALTIES = ['none', 'downgrade'];
const SCRIPT_MODES = ['native', 'mythic', 'hybrid'];
const SCRIPT_PHASES = ['cast', 'hit', 'miss', 'fail'];

const skillFields: FieldSpec[] = [
  ['id', 'ID', '技能定义唯一标识。', 'text'],
  ['enabled', '启用', '是否加载并允许使用该技能。', 'boolean'],
  ['display_name', '显示名称', '技能在 GUI、ActionBar 和提示中的显示名称。', 'text'],
  ['description', '描述', '技能说明文本列表。', 'stringList'],
  ['icon_material', '图标材质', '技能在 GUI 中使用的 Bukkit Material。', 'material'],
  ['mythic_skill', 'Mythic 技能', '桥接 MythicMobs 技能 ID；原生脚本可留空。', 'text'],
  ['trigger_type', '触发类型', '技能触发类型，active 表示主动技能。', 'enum', { options: ['active', 'passive'], optionLabelPrefix: 'skill.trigger_type' }],
  ['passive_triggers', '被动触发器', '绑定的被动触发器 ID 列表。', 'stringList'],
  ['skill_parameters', '技能参数', '技能参数定义，支持 type、value/expression/formula、min/max/decimals/default。', 'object', { creatableChildren: true }],
  ['variables', '变量', '变量定义，结构同 skill_parameters。', 'object', { creatableChildren: true }],
  ['script', '技能脚本', '原生技能脚本配置，包含 enabled/mode/stop_on_failure/actions/conditions。', 'object'],
  ['script.enabled', '启用脚本', '是否启用该技能的原生脚本。', 'boolean'],
  ['script.mode', '脚本模式', '该技能的脚本执行模式。', 'enum', { options: SCRIPT_MODES, optionLabelPrefix: 'script_engine.default_mode' }],
  ['script.stop_on_failure', '失败停止', '某个脚本动作失败后是否停止后续阶段。', 'boolean'],
  ['script.actions', '脚本动作', '原生技能脚本的固定动作阶段。', 'object'],
  ['script.actions.cast', '释放动作', '技能释放阶段执行的动作列表。', 'stringList'],
  ['script.actions.hit', '命中动作', '技能命中目标后执行的动作列表。', 'stringList'],
  ['script.actions.miss', '未命中动作', '技能没有命中目标时执行的动作列表。', 'stringList'],
  ['script.actions.fail', '失败动作', '技能脚本执行失败阶段的动作列表。', 'stringList'],
  ['script.conditions', '脚本条件', '原生技能脚本各阶段执行前检查的条件列表。', 'object'],
  ['script.conditions.cast', '释放条件', '释放阶段执行前检查的条件列表。', 'stringList'],
  ['script.conditions.hit', '命中条件', '命中阶段执行前检查的条件列表。', 'stringList'],
  ['script.conditions.miss', '未命中条件', '未命中阶段执行前检查的条件列表。', 'stringList'],
  ['script.conditions.fail', '失败条件', '失败阶段执行前检查的条件列表。', 'stringList'],
  ['upgrade', '升级配置', '技能升级等级、经济、成功率、材料和动作配置。', 'object'],
  ['upgrade.enabled', '启用升级', '是否启用该技能升级系统。', 'boolean'],
  ['upgrade.max_level', '最高等级', '该技能允许升级到的最高等级。', 'number'],
  ['upgrade.gui_template', '升级 GUI', '升级使用的 GUI 模板 ID。', 'text'],
  ['upgrade.economy', '升级经济', '技能升级经济消耗配置。', 'object'],
  ['upgrade.economy.enabled', '启用经济', '是否启用升级经济消耗。', 'boolean'],
  ['upgrade.economy.currencies', '升级货币', '升级经济中各币种的成本列表。', 'objectList'],
  ['upgrade.success_rates', '成功率表', '按目标等级配置的全局升级成功率。', 'object', { creatableChildren: true }],
  ['upgrade.failure_penalty', '失败惩罚', '技能升级失败后的惩罚方式。', 'enum', { options: FAILURE_PENALTIES, optionLabelPrefix: 'upgrade.failure_penalty' }],
  ['upgrade.levels', '等级覆盖', '按目标等级覆盖材料、经济、参数和动作。', 'object', { creatableChildren: true }],
  ['cooldown_ticks', '技能冷却', '该技能自身冷却时间，单位 tick。', 'number'],
  ['global_cooldown_ticks', '全局冷却', '释放后施加的全局冷却时间，单位 tick。', 'number'],
  ['resource_costs', '资源消耗', '释放技能时消耗或检查的资源列表。', 'objectList'],
  ['lore_aliases', 'Lore 别名', '用于从 Lore 或外部文本识别该技能的别名列表。', 'stringList'],
  ['pdc_skill_id', 'PDC 技能 ID', '写入 PDC 或识别时使用的技能 ID；留空默认等于 id。', 'text'],
  ['ui_category', 'UI 分类', '技能在 GUI 中的分类 ID。', 'text'],
  ['sort_order', '排序', '同分类内的排序权重。', 'number'],
  ['condition_type', '条件逻辑', '技能释放条件组合逻辑。', 'enum', { options: ['all_of', 'any_of'], optionLabelPrefix: 'condition_type' }],
  ['conditions', '释放条件', '技能释放前检查的条件表达式列表。', 'stringList'],
  ['condition_required_count', '需要满足数量', 'any_of 条件逻辑下需要满足的最少条件数量；0 表示不额外限制。', 'number']
];

const resourceFields: FieldSpec[] = [
  ['id', 'ID', '本地技能资源唯一标识。', 'text'],
  ['display_name', '显示名称', '资源在 GUI、提示和占位符中的显示名称。', 'text'],
  ['max', '最大值', '该资源的最大值。', 'number'],
  ['default_current', '默认当前值', '玩家初始化时该资源的当前值。', 'number'],
  ['regen_amount', '回复量', '每次自动回复增加的数值。', 'number'],
  ['regen_interval_ticks', '回复间隔', '自动回复间隔，单位 tick；0 表示不自动回复。', 'number'],
  ['clamp_min', '最小钳制', '资源当前值允许的最小值。', 'number'],
  ['clamp_max', '最大钳制', '资源当前值允许的最大值；0 通常表示使用 max。', 'number']
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
  ['emakiskills.file.gui.comment', '技能面板与触发器选择界面的 GUI 模板。'],
  ['emakiskills.file.skills.title', '技能'],
  ['emakiskills.file.skills.comment', '技能定义目录，配置施法模式、触发器、资源消耗、脚本和升级。'],
  ['emakiskills.file.resources.title', '资源'],
  ['emakiskills.file.resources.comment', '技能资源定义目录，配置资源上限、恢复、消耗提示和显示文本。'],
  ['emakiskills.filePath.skills_example_skill.title', '示例技能'],
  ['emakiskills.filePath.skills_example_skill.comment', '基础技能示例，展示施法、脚本动作、资源消耗和升级配置。'],
  ['emakiskills.filePath.skills_example_combo_skill.title', '连击示例技能'],
  ['emakiskills.filePath.skills_example_combo_skill.comment', '连击技能示例，展示多段触发和动作阶段。'],
  ['emakiskills.filePath.skills_example_projectile_skill.title', '投射物示例技能'],
  ['emakiskills.filePath.skills_example_projectile_skill.comment', '投射物技能示例，展示命中、未命中和失败阶段。'],
  ['emakiskills.filePath.resources_example_resource.title', '示例资源'],
  ['emakiskills.filePath.resources_example_resource.comment', '技能资源示例，展示容量、恢复和资源不足提示。'],
  ['emakiskills.filePath.gui_skills_gui.title', '技能 GUI'],
  ['emakiskills.filePath.gui_skills_gui.comment', '技能面板 GUI 模板，控制技能槽位、按钮和提示物品。'],
  ['emakiskills.filePath.gui_trigger_select_gui.title', '触发器选择 GUI'],
  ['emakiskills.filePath.gui_trigger_select_gui.comment', '触发器选择界面模板，控制玩家绑定技能触发方式。'],
  ['emakiskills.file.plugin.title', '插件描述'],
  ['emakiskills.file.plugin.comment', 'plugin.yml 元数据、命令、权限和依赖声明。'],
  ['emakiskills.file.web-console.title', 'WebUIEdit 注册'],
  ['emakiskills.file.web-console.comment', '此插件暴露给 WebUIEdit 的文件分组、编辑器类型和前端扩展入口。'],
  ...fields.flatMap(([path, label, comment]) => [[`emakiskills.field.${path}`, label], [`emakiskills.comment.${path}`, comment]]),
  ...skillFields.flatMap(([path, label, comment]) => [[`emakiskills.field.${path}`, label], [`emakiskills.comment.${path}`, comment]]),
  ...resourceFields.flatMap(([path, label, comment]) => [[`emakiskills.field.${path}`, label], [`emakiskills.comment.${path}`, comment]]),
  ...Object.entries(triggerFields).flatMap(([key, [label, comment]]) => [[`emakiskills.field.${key}`, label], [`emakiskills.comment.${key}`, comment]])
]);

registerModuleLocale(MODULE, 'zh-CN', {
  ...localeMessages,
  'emakiskills.surface.gui': '技能 GUI',
  'emakiskills.option.skill.parameter.type.random_char': '随机字符',
  'emakiskills.option.skill.parameter.type.weighted_random_char': '权重随机字符',
  'emakiskills.option.skill.parameter.type.conditional_char': '条件字符',
  'emakiskills.option.script_engine.default_mode.native': '原生脚本',
  'emakiskills.option.script_engine.default_mode.mythic': 'Mythic 技能',
  'emakiskills.option.script_engine.default_mode.hybrid': '混合模式',
  'emakiskills.option.upgrade.failure_penalty.none': '无惩罚',
  'emakiskills.option.upgrade.failure_penalty.downgrade': '降级'
});

registerModuleLocale(MODULE, 'en-US', {
  'emakiskills.module.name': 'Skills',
  'emakiskills.module.summary': 'Slots, cast modes, and triggers',
  'emakiskills.file.config.title': 'Main Config',
  'emakiskills.file.config.comment': 'Main skill system configuration covering triggers, cast modes, resources, and progression.',
  'emakiskills.file.gui.title': 'GUI Templates',
  'emakiskills.file.gui.comment': 'GUI templates for the skill panel and trigger selection screens.',
  'emakiskills.file.skills.title': 'Skills',
  'emakiskills.file.skills.comment': 'Skill definitions covering cast modes, triggers, resource costs, scripts, and upgrades.',
  'emakiskills.file.resources.title': 'Resources',
  'emakiskills.file.resources.comment': 'Skill resource definitions covering capacity, regeneration, shortage messages, and display text.',
  'emakiskills.filePath.skills_example_skill.title': 'Sample Skill',
  'emakiskills.filePath.skills_example_skill.comment': 'Basic skill example showing cast setup, script actions, resource cost, and upgrade settings.',
  'emakiskills.filePath.skills_example_combo_skill.title': 'Sample Combo Skill',
  'emakiskills.filePath.skills_example_combo_skill.comment': 'Combo skill example showing multi-stage triggers and action phases.',
  'emakiskills.filePath.skills_example_projectile_skill.title': 'Sample Projectile Skill',
  'emakiskills.filePath.skills_example_projectile_skill.comment': 'Projectile skill example showing hit, miss, and fail phases.',
  'emakiskills.filePath.resources_example_resource.title': 'Sample Resource',
  'emakiskills.filePath.resources_example_resource.comment': 'Skill resource example showing capacity, regeneration, and insufficient-resource messages.',
  'emakiskills.filePath.gui_skills_gui.title': 'Skills GUI',
  'emakiskills.filePath.gui_skills_gui.comment': 'Skill panel GUI template controlling skill slots, buttons, and hint items.',
  'emakiskills.filePath.gui_trigger_select_gui.title': 'Trigger Selection GUI',
  'emakiskills.filePath.gui_trigger_select_gui.comment': 'Trigger selection GUI template controlling how players bind skill triggers.',
  'emakiskills.file.plugin.title': 'Plugin Description',
  'emakiskills.file.plugin.comment': 'plugin.yml metadata, commands, permissions, and dependency declarations.',
  'emakiskills.file.web-console.title': 'WebUIEdit Registration',
  'emakiskills.file.web-console.comment': 'File groups, editor kinds, and frontend extension entries exposed to WebUIEdit by this plugin.',
  'emakiskills.surface.gui': 'Skills GUI',
  'emakiskills.option.skill.parameter.type.random_char': 'Random char',
  'emakiskills.option.skill.parameter.type.weighted_random_char': 'Weighted random char',
  'emakiskills.option.skill.parameter.type.conditional_char': 'Conditional char',
  'emakiskills.field.slots': 'Skill Slots',
  'emakiskills.field.cast_mode': 'Cast Mode',
  'emakiskills.field.cast_timing': 'Cast Timing',
  'emakiskills.field.actionbar': 'ActionBar',
  'emakiskills.field.script_engine': 'Script Engine',
  'emakiskills.field.script.actions': 'Script Actions',
  'emakiskills.comment.script.actions': 'Fixed action phases for the native skill script.',
  'emakiskills.field.script.actions.cast': 'Cast Actions',
  'emakiskills.comment.script.actions.cast': 'Actions executed when the skill is cast.',
  'emakiskills.field.script.actions.hit': 'Hit Actions',
  'emakiskills.comment.script.actions.hit': 'Actions executed after the skill hits a target.',
  'emakiskills.field.script.actions.miss': 'Miss Actions',
  'emakiskills.comment.script.actions.miss': 'Actions executed when the skill misses its target.',
  'emakiskills.field.script.actions.fail': 'Fail Actions',
  'emakiskills.comment.script.actions.fail': 'Actions executed when the skill script enters the fail phase.',
  'emakiskills.field.script.conditions': 'Script Conditions',
  'emakiskills.comment.script.conditions': 'Conditions checked before each native script phase.',
  'emakiskills.field.script.conditions.cast': 'Cast Conditions',
  'emakiskills.comment.script.conditions.cast': 'Conditions checked before cast actions.',
  'emakiskills.field.script.conditions.hit': 'Hit Conditions',
  'emakiskills.comment.script.conditions.hit': 'Conditions checked before hit actions.',
  'emakiskills.field.script.conditions.miss': 'Miss Conditions',
  'emakiskills.comment.script.conditions.miss': 'Conditions checked before miss actions.',
  'emakiskills.field.script.conditions.fail': 'Fail Conditions',
  'emakiskills.comment.script.conditions.fail': 'Conditions checked before fail actions.',
  'emakiskills.field.triggers': 'Active Triggers',
  'emakiskills.field.passive_trigger_settings': 'Passive Trigger Settings',
  'emakiskills.field.passive_triggers': 'Passive Triggers',
  'emakiskills.field.display_name': 'Display Name',
  'emakiskills.field.incompatible_with': 'Incompatible With',
  'emakiskills.option.script_engine.default_mode.native': 'Native',
  'emakiskills.option.script_engine.default_mode.mythic': 'Mythic',
  'emakiskills.option.script_engine.default_mode.hybrid': 'Hybrid',
  'emakiskills.option.upgrade.failure_penalty.none': 'None',
  'emakiskills.option.upgrade.failure_penalty.downgrade': 'Downgrade'
});

registerPluginConfig({
  moduleId: MODULE,
  metaFields: fields,
  fileSchemas: [
    { pathPrefix: 'skills/', fields: skillFields },
    { pathPrefix: 'resources/', fields: resourceFields }
  ],
  ruleFields: triggerFields,
  rules: [
    [{ key: 'description' }, { label: '技能描述', comment: '技能说明文本列表。', type: 'stringList' }],
    [{ key: 'lore_aliases' }, { label: 'Lore 别名', comment: '用于 Lore 匹配识别的别名列表。', type: 'stringList' }],
    [{ key: 'conditions' }, { label: '释放条件', comment: '技能释放前检查的条件表达式列表。', type: 'stringList' }],
    [{ key: 'passive_triggers' }, { label: '被动触发器', comment: '被动技能触发器 ID 列表。', type: 'stringList' }],
    [{ key: 'incompatible_with' }, { label: '互斥触发器', comment: '与当前触发器互斥的触发器 ID 列表。', type: 'stringList' }],
    [{ suffix: '.incompatible_with' }, { label: '互斥触发器', comment: '与当前触发器互斥的触发器 ID 列表。', type: 'stringList' }],
    [{ key: 'resource_costs' }, { label: '资源消耗', comment: '释放技能时消耗或检查的资源列表。', type: 'list' }],
    [{ key: 'currencies' }, { label: '升级货币', comment: '升级经济中各币种的成本列表。', type: 'list' }],
    [{ suffix: '.materials' }, { label: '升级材料', comment: '升级等级所需材料列表。', type: 'list' }],
    [{ path: 'upgrade.failure_penalty' }, { label: '失败惩罚', comment: '技能升级失败后的惩罚方式。', type: 'enum', options: FAILURE_PENALTIES, optionLabelPrefix: 'upgrade.failure_penalty' }],
    [{ key: 'script' }, { label: '技能脚本', comment: '原生技能脚本配置，包含启用状态、执行模式、动作阶段和阶段条件。', type: 'object' }],
    [{ path: 'script.enabled' }, { label: '启用脚本', comment: '是否启用该技能的原生脚本。', type: 'boolean' }],
    [{ path: 'script.mode' }, { label: '脚本模式', comment: '该技能的脚本执行模式。', type: 'enum', options: SCRIPT_MODES, optionLabelPrefix: 'script_engine.default_mode' }],
    [{ path: 'script.stop_on_failure' }, { label: '失败停止', comment: '某个脚本动作失败后是否停止后续阶段。', type: 'boolean' }],
    ...SCRIPT_PHASES.flatMap(phase => [
      [{ path: `script.actions.${phase}` }, { label: `${phase} 动作`, comment: `原生技能脚本 actions.${phase} 阶段动作列表。`, type: 'stringList' }],
      [{ path: `script.conditions.${phase}` }, { label: `${phase} 条件`, comment: `原生技能脚本 conditions.${phase} 阶段条件列表。`, type: 'stringList' }]
    ] as Array<[Record<string, string>, { label: string; comment: string; type: string }]>)
  ],
  createTemplates: [
    ['skill_parameters', { id: 'skill-parameter', label: copy('技能参数', 'Skill parameter'), fields: parameterFields }],
    ['variables', { id: 'skill-variable', label: copy('技能变量', 'Skill variable'), fields: parameterFields }],
    ['upgrade.success_rates', { id: 'upgrade-success-rate', label: copy('目标等级成功率', 'Target level success rate'), fields: [
      { path: 'value', label: '成功率', comment: '该目标等级的升级成功率百分比。', type: 'number', defaultValue: 100 }
    ] }],
    ['upgrade.levels', { id: 'upgrade-level', label: copy('升级等级', 'Upgrade level'), fields: [
      { path: 'success_rate', label: '成功率', comment: '该目标等级的成功率覆盖。', type: 'number', defaultValue: 100 },
      { path: 'materials', label: '材料', comment: '该等级需要的升级材料。', type: 'objectList', defaultValue: [], itemFields: materialFields },
      { path: 'economy', label: '经济覆盖', comment: '该等级专属经济消耗。', type: 'object', defaultValue: {} },
      { path: 'economy.enabled', label: '启用经济', comment: '是否启用该等级专属经济消耗。', type: 'boolean', defaultValue: true },
      { path: 'economy.currencies', label: '货币', comment: '该等级专属货币成本列表。', type: 'objectList', defaultValue: [], itemFields: currencyFields },
      { path: 'parameters', label: '参数覆盖', comment: '升级到该等级后覆盖的技能参数。', type: 'map', defaultValue: {} },
      { path: 'actions.success', label: '成功动作', comment: '升级成功动作列表。', type: 'stringList', defaultValue: [] },
      { path: 'actions.failure', label: '失败动作', comment: '升级失败动作列表。', type: 'stringList', defaultValue: [] }
    ] }]
  ],
  listItemSchemas: [
    ['resource_costs', [
      { path: 'type', label: '资源类型', comment: '资源消耗类型。', type: 'enum', options: ['ea-resource', 'attribute-check', 'local-resource', 'auraskills-mana', 'mmocore-mana', 'mythiclib-mana'], defaultValue: 'local-resource', optionLabelPrefix: 'skill.resource_cost.type' },
      { path: 'target_id', label: '目标 ID', comment: '资源或属性标识。', type: 'text', defaultValue: 'mana' },
      { path: 'amount', label: '数量', comment: '消耗或检查的数量。', type: 'number', defaultValue: 1 },
      { path: 'operation', label: '操作', comment: 'consume 为消耗，require 为检查。', type: 'enum', options: ['consume', 'require'], defaultValue: 'consume', optionLabelPrefix: 'skill.resource_cost.operation' },
      { path: 'failure_message', label: '失败提示', comment: '资源不足时显示的提示消息。', type: 'text', defaultValue: '资源不足' }
    ], { uniqueBy: 'target_id' }],
    ['upgrade.economy.currencies', currencyFields, { uniqueBy: 'currency_id' }],
    ['currencies', currencyFields, { uniqueBy: 'currency_id' }],
    ['materials', materialFields]
  ],
  listItemSchemaRules: [
    [{ suffix: 'materials' }, materialFields]
  ]
});

registerPluginGuiEditor({
  moduleId: MODULE,
  editorId: 'emakiskills:gui',
  label: copy('技能 GUI', 'Skills GUI'),
  fields: [
    ['type', '槽位类型', '技能业务槽位语义。可选预设值，也可填自定义/填充槽位。', 'enum', { options: ['active_slot', 'skill_pool', 'cast_mode_toggle', 'trigger_selector', 'page_prev', 'page_next', 'trigger_option', 'back'], optionLabelPrefix: 'slotType' }],
    ['active_slot', '主动技能槽位', '玩家主动技能槽位。', 'text'],
    ['skill_pool', '技能池', '可装配技能列表区域。', 'text'],
    ['cast_mode_toggle', '施法模式按钮', '切换技能施法模式的按钮槽位。', 'text'],
    ['trigger_selector', '触发器选择槽', '用于选择主动触发器的槽位。', 'text'],
    ['page_prev', '上一页', '向前翻页按钮。', 'text'],
    ['page_next', '下一页', '向后翻页按钮。', 'text']
  ]
});
