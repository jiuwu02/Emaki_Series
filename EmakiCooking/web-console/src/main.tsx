import { registerConfigNodeMeta, registerConfigNodeRule, registerModuleLocale, registerPluginGuiEditor } from 'emaki-web-console';

const MODULE = 'EmakiCooking';

type FieldSpec = [path: string, label: string, comment: string, type: string, extra?: Record<string, unknown>];

const fields: FieldSpec[] = [
  ['input_rules', '输入限制', '控制工位输入物品是否必须继续匹配当前配方，避免玩家把无关物品塞入工位。', 'object'],
  ['input_rules.only_recipe_items', '严格模式', '开启后只允许当前工位配方可继续匹配的原料进入输入槽；关闭后允许更宽松的放入逻辑。', 'boolean'],
  ['display_entities', '展示实体', '工位上方食材展示实体的全局渲染策略、可视距离和刷新节奏。', 'object'],
  ['display_entities.backend', '渲染后端', '展示实体使用的后端。auto 会优先尝试 PacketEvents，不可用时回退 Bukkit。', 'enum', { options: ['auto', 'packet_events', 'bukkit'], optionLabelPrefix: 'display_entities.backend' }],
  ['display_entities.view_distance_blocks', '可视距离', '玩家距离工位超过该格数后不再接收展示实体刷新。', 'number'],
  ['display_entities.refresh_interval_ticks', '刷新间隔', '展示实体位置、旋转、可见性与状态刷新间隔，单位 tick。', 'number'],
  ['display_entities.wok', '炒锅展示', '炒锅食材环形展示的专用布局参数。', 'object'],
  ['display_entities.wok.layout_radius', '炒锅半径', '炒锅中多份食材围绕中心摆放的半径，单位方块格。', 'number'],
  ['display_adjustments', '展示变换', '默认展示实体的偏移、旋转、缩放，以及各工位专用覆盖。', 'object'],
  ['display_adjustments.defaults', '默认展示', '普通物品和方块展示实体的默认变换参数。', 'object'],
  ['display_adjustments.station_defaults', '工位覆盖', '按工位类型覆盖展示实体变换，例如炒锅食材翻转角度。', 'object'],
  ['stations', '工位设置', '砧板、炒锅、研磨机、蒸锅、烤炉、榨汁机和发酵桶的运行规则。', 'object'],
  ['stations.chopping_board', '砧板', '砧板方块匹配、交互、厨刀识别、切割伤害与动作反馈。', 'object'],
  ['stations.wok', '炒锅', '炒锅方块匹配、翻炒、火候、失败产物、动画与烫伤规则。', 'object'],
  ['stations.grinder', '研磨机', '研磨机方块匹配、启动交互、检查周期和运行反馈。', 'object'],
  ['stations.steamer', '蒸锅', '蒸锅 GUI、燃料、水分、蒸汽效率和热源点亮规则。', 'object'],
  ['stations.oven', '烤炉', '烤炉 GUI、燃料、火力区间与热量衰减规则。', 'object'],
  ['stations.juicer', '榨汁机', '榨汁机 GUI、容器、流体容量、盛取量与完成产物处理。', 'object'],
  ['stations.fermentation_barrel', '发酵桶', '发酵桶 GUI、密封、暂停、发酵启动与收取规则。', 'object']
];

const fieldComments: Record<string, [string, string, string]> = {
  block_item_sources: ['方块来源', '识别为该工位本体方块的 ItemSource 列表，可使用 minecraft-/CraftEngine/ItemsAdder/Nexo 等格式。', 'list'],
  only_recipe_items: ['只允许配方物', '是否只允许能继续匹配工位配方的物品进入输入。省略时通常继承全局输入规则。', 'boolean'],
  interactions: ['交互绑定', '不同业务动作对应的玩家点击方式，例如 shift_left_click、right_click。', 'object'],
  drop_result: ['掉落产物', '完成后是否把产物直接掉落到世界，而不是仅写入内部 GUI 或容器。', 'boolean'],
  interaction_delay_ms: ['交互间隔', '同一玩家连续触发该工位交互的最小间隔，单位毫秒。', 'number'],
  tool_item_sources: ['工具来源', '识别为厨刀、锅铲等工具的 ItemSource 列表。', 'list'],
  cut_damage: ['切割伤害', '砧板切割失败或误伤时的伤害开关、概率与数值。', 'object'],
  scald_damage: ['烫伤伤害', '炒锅等热工位对玩家造成烫伤的开关与伤害值。', 'object'],
  value: ['数值', '当前条目的数值，语义由父节点决定。', 'number'],
  chance: ['概率', '触发概率，通常按百分比填写。', 'number'],
  need_bowl: ['需要碗', '出锅时是否要求玩家持有或提供碗类容器。', 'boolean'],
  stir_delay_ms: ['翻炒间隔', '两次翻炒动作之间的最小间隔，单位毫秒。', 'number'],
  timeout_ms: ['超时毫秒', '炒锅状态超过该时间未处理时按过火或失败处理。', 'number'],
  ignite_heat_source: ['点亮热源', '放入燃料或食材后是否尝试把下方热源切换为点亮外观。', 'boolean'],
  invalid_result_item_sources: ['无效产物', '未命中配方或结果无效时使用的兜底产物来源。', 'list'],
  spatula_item_sources: ['锅铲来源', '识别为锅铲的 ItemSource 列表。', 'list'],
  heat_levels: ['火候等级', '按下方热源 ItemSource 映射到的火候等级列表。', 'list'],
  stir_animation: ['翻炒动画', '翻炒时食材抛起、旋转和持续时间的动画参数。', 'object'],
  duration_ticks: ['持续 tick', '动画或运行阶段的持续时间，20 tick 约等于 1 秒。', 'number'],
  height: ['高度', '动画抛起高度或展示高度，单位方块格。', 'number'],
  rotation_axis: ['旋转轴', '动画旋转轴，可选 x、y、z。', 'enum'],
  rotation_degrees: ['旋转角度', '动画旋转角度，单位度。', 'number'],
  failure: ['失败规则', '工位处理失败时的概率、产物与反馈配置。', 'object'],
  item_sources: ['物品来源', '参与匹配、消耗、返还或产出的 ItemSource 列表。', 'list'],
  actions: ['阶段动作', '工位流程中各阶段触发的 CoreLib Action 列表。', 'object'],
  check_delay_ticks: ['检查周期', '后台运行工位的检查间隔，单位 tick。', 'number'],
  heat_item_sources: ['热源来源', '识别为热源的方块 ItemSource 列表。', 'list'],
  fuels: ['燃料', '可投入燃料及其燃烧时间、火力等参数列表。', 'list'],
  duration_seconds: ['持续秒数', '燃料增加的燃烧时间，单位秒。', 'number'],
  moisture_rules: ['水分规则', '可转化为水分的输入物和返还物配置。', 'list'],
  input_item_sources: ['输入来源', '触发规则所需的输入物品来源。', 'list'],
  moisture: ['水分', '增加或消耗的水分数值。', 'number'],
  reset_progress_when_steam_empty: ['蒸汽耗尽重置', '蒸汽耗尽时是否重置当前蒸制进度。', 'boolean'],
  steam_production_efficiency: ['产汽效率', '每周期最多把多少水分转换为蒸汽。', 'number'],
  steam_conversion_efficiency: ['蒸汽转进度', '每次蒸汽消耗换算成多少烹饪进度。', 'number'],
  steam_consumption_efficiency: ['蒸汽消耗', '每个周期基础蒸汽消耗量。', 'number'],
  heat: ['火力', '烤炉火力区间、衰减或燃料提供的火力。', 'object'],
  min: ['最小值', '允许推进流程的最小阈值。', 'number'],
  max: ['最大值', '允许推进流程的最大阈值。', 'number'],
  decay_per_second: ['每秒衰减', '火力每秒自然下降的数值。', 'number'],
  require_container: ['需要容器', '榨汁或收取时是否要求玩家提供容器。', 'boolean'],
  max_fluid_ml: ['最大容量 ml', '榨汁机内部流体容量上限，单位毫升。', 'number'],
  default_serving_ml: ['默认盛取 ml', '每次盛取默认消耗的流体量，单位毫升。', 'number'],
  container_item_sources: ['容器来源', '允许作为容器的 ItemSource 列表。', 'list'],
  drop_completed_result_on_break: ['破坏掉落成品', '工位被破坏时是否掉落已经完成但未取出的产物。', 'boolean'],
  pause_when_open: ['打开时暂停', 'GUI 打开时是否暂停发酵或后台处理进度。', 'boolean'],
  require_sealed: ['需要密封', '发酵桶开始或推进时是否要求处于密封状态。', 'boolean'],
  offset: ['偏移', '展示实体相对工位方块的 x/y/z 偏移。', 'object'],
  rotation: ['旋转', '展示实体的 x/y/z 旋转角度。', 'object'],
  scale: ['缩放', '展示实体的 x/y/z 缩放倍率。', 'object'],
  x: ['X', 'X 轴数值。', 'number'],
  y: ['Y', 'Y 轴数值。', 'number'],
  z: ['Z', 'Z 轴数值。', 'number']
};

const localeMessages: Record<string, string> = Object.fromEntries([
  ['emakicooking.module.name', 'Cooking'],
  ['emakicooking.module.summary', '工位、展示实体、输入规则'],
  ['emakicooking.file.config.title', '主配置'],
  ['emakicooking.file.config.comment', '烹饪系统主配置，包含工位、展示实体和输入规则。'],
  ['emakicooking.file.gui.title', 'GUI 模板'],
  ['emakicooking.file.gui.comment', '烹饪工位 GUI 模板文件。'],
  ...fields.flatMap(([path, label, comment]) => [[`emakicooking.field.${path}`, label], [`emakicooking.comment.${path}`, comment]]),
  ...Object.entries(fieldComments).flatMap(([key, [label, comment]]) => [[`emakicooking.field.${key}`, label], [`emakicooking.comment.${key}`, comment]])
]);

registerModuleLocale(MODULE, 'zh-CN', {
  ...localeMessages,
  'emakicooking.surface.gui': '烹饪 GUI',
  'emakicooking.option.display_entities.backend.auto': '自动选择',
  'emakicooking.option.display_entities.backend.packet_events': 'PacketEvents 虚拟实体',
  'emakicooking.option.display_entities.backend.bukkit': 'Bukkit 真实实体'
});

registerModuleLocale(MODULE, 'en-US', {
  'emakicooking.module.name': 'Cooking',
  'emakicooking.module.summary': 'Stations, display entities, and input rules',
  'emakicooking.file.config.title': 'Main Config',
  'emakicooking.file.config.comment': 'Main cooking configuration covering stations, display entities, and input rules.',
  'emakicooking.file.gui.title': 'GUI Templates',
  'emakicooking.file.gui.comment': 'Cooking station GUI template files.',
  'emakicooking.surface.gui': 'Cooking GUI',
  'emakicooking.field.input_rules': 'Input Rules',
  'emakicooking.field.display_entities': 'Display Entities',
  'emakicooking.field.display_adjustments': 'Display Adjustments',
  'emakicooking.field.stations': 'Stations',
  'emakicooking.field.only_recipe_items': 'Recipe-only Input',
  'emakicooking.field.block_item_sources': 'Block Sources',
  'emakicooking.field.interactions': 'Interactions',
  'emakicooking.field.actions': 'Actions',
  'emakicooking.field.item_sources': 'Item Sources',
  'emakicooking.option.display_entities.backend.auto': 'Auto',
  'emakicooking.option.display_entities.backend.packet_events': 'PacketEvents',
  'emakicooking.option.display_entities.backend.bukkit': 'Bukkit'
});

fields.forEach(([path, label, comment, type, extra]) => registerConfigNodeMeta(MODULE, path, { label, comment, type, ...(extra ?? {}) }));
Object.entries(fieldComments).forEach(([key, [label, comment, type]]) => registerConfigNodeRule(MODULE, { key }, { label, comment, type }));
registerConfigNodeRule(MODULE, { key: 'rotation_axis' }, { label: '旋转轴', comment: '旋转轴，可选 x、y、z。', type: 'enum', options: ['x', 'y', 'z'], optionLabelPrefix: 'axis' });

registerPluginGuiEditor({
  moduleId: MODULE,
  editorId: 'emakicooking:gui',
  label: '烹饪 GUI',
  fields: [
    ['type', '槽位类型', '烹饪工位槽位语义。', 'text'],
    ['ingredient', '原料槽', '放入烹饪原料的槽位。', 'text'],
    ['result', '产物槽', '展示或取出产物的槽位。', 'text'],
    ['fuel', '燃料槽', '放入燃料的槽位。', 'text'],
    ['moisture', '水分槽', '蒸锅水分输入或展示槽位。', 'text'],
    ['container', '容器槽', '榨汁机盛取容器槽位。', 'text'],
    ['progress', '进度槽', '显示烹饪、蒸制、烘烤或发酵进度的槽位。', 'text']
  ]
});
