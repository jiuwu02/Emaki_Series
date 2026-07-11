import {
  conditionGroupField,
  coreEffectDefinition,
  currencyCostListField,
  defineConfigSchema,
  defineSchemaAst,
  effectListField,
  localeText,
  materialCostListField,
  nameLoreActionField,
  numberField,
  objectField,
  objectMapField,
  payloadEffectDefinition,
  registerEffectTypes,
  standardCurrencyCostFields,
  standardMaterialCostFields,
  textField,
  type ConfigMetaFieldEntry,
  type WebManifestPluginConfig
} from 'emaki-web-console';

const MODULE = 'EmakiStrengthen';
const copy = localeText;

const attributeEffectDef = payloadEffectDefinition('ea_attribute', 'EA 属性', [{ key: 'ea_attributes', type: 'map', label: 'EA 属性', defaultValue: {} }]);
const skillEffectDef = payloadEffectDefinition('es_skill', 'ES 技能', [{ key: 'es_skills', type: 'stringList', label: 'ES 技能', defaultValue: [] }]);

const materialFields = standardMaterialCostFields({
  overrides: {
    item_sources: { label: '物品来源', comment: '强化材料的 ItemSource 列表。', defaultValue: ['minecraft-copper_ingot'] },
    amount: { label: '数量', comment: '需要消耗的材料数量；-1 表示只检测不消耗。', defaultValue: 1 },
    optional: { label: '可选', comment: '是否为可选材料。', defaultValue: false },
    protection: { label: '保护材料', comment: '失败时提供保护效果的材料。', defaultValue: false }
  },
  insertAfter: {
    protection: { path: 'temper_boost', label: '锻印提升', comment: '放入后额外增加的锻印等级。', type: 'number', defaultValue: 0 }
  }
});

const currencyFields = standardCurrencyCostFields({
  overrides: {
    provider: { label: '经济提供器', comment: '经济系统提供器，例如 vault。', defaultValue: 'vault' },
    currency_id: { label: '货币 ID', comment: '多货币系统的货币标识；留空使用默认货币。', defaultValue: '' },
    base_cost: { label: '基础费用', comment: '强化经济消耗的基础数值。', defaultValue: 0 },
    cost_formula: { label: '费用公式', comment: '根据星级等变量计算最终费用的公式。', defaultValue: '' },
    display_name: { label: '显示名称', comment: '货币在提示中的显示名称。', defaultValue: '' }
  }
});

const starStageFields = [
  { path: 'name', label: '阶段名称', comment: '该星级的里程碑名称，可留空。', type: 'text', defaultValue: '' },
  { path: 'variables', label: '变量', comment: '表达式变量或属性增量，源码从星级阶段顶层 variables 读取；属性计算应保持数值结果，name/lore actions 文本模板可使用随机文本、随机字符、权重随机字符和条件字符。', type: 'variablesMap', defaultValue: {} },
  { path: 'ea_attributes', label: 'EA 属性', comment: '显式 EmakiAttribute 属性覆盖，源码从星级阶段顶层 ea_attributes 读取。', type: 'dynamic_map', defaultValue: {} },
  { path: 'effects', label: '效果', comment: '技能或显示动作效果列表，按 type 分流为变量、EA 属性、ES 技能、名称/Lore 动作。', type: 'effects', defaultValue: [] },
  { path: 'materials', label: '材料', comment: '强化到该星级需要的材料列表。', type: 'objectList', defaultValue: [], itemFields: materialFields },
  { path: 'economy_override.currencies', label: '经济覆盖', comment: '该星级专属货币消耗；留空时使用配方 economy。', type: 'objectList', defaultValue: [], itemFields: currencyFields },
  { path: 'actions.success', label: '成功动作', comment: '强化成功到该星级后执行的动作。', type: 'stringList', defaultValue: [] },
  { path: 'actions.failure', label: '失败动作', comment: '强化失败后按结果星级读取的动作。', type: 'stringList', defaultValue: [] }
];

const branchFields = [
  { path: 'branch_id', label: '分支 ID', comment: '分支唯一标识，root 节点可使用 root。', type: 'text', defaultValue: 'branch' },
  { path: 'display_name', label: '显示名称', comment: '分支在 GUI 或提示中的显示名称。', type: 'text', defaultValue: copy('<yellow>新分支</yellow>', '<yellow>New branch</yellow>') },
  { path: 'fork_after_star', label: '分叉星级', comment: '-1 表示不再分叉；有 children 时表示完成该星级后选择路线。', type: 'number', defaultValue: -1 },
  { path: 'stars', label: '星级阶段', comment: '该分支内的星级阶段。', type: 'object', defaultValue: {} },
  { path: 'children', label: '子分支', comment: '此分支后续可选择的子路线。', type: 'object', defaultValue: {} }
];

const successRateTemplate = {
  id: 'star-success-rate',
  label: copy('目标星级成功率', 'Target star success rate'),
  fields: [
    { path: 'value', label: '成功率', comment: '该目标星级的强化成功率百分比，例如 75.0。', type: 'number', defaultValue: 100 }
  ]
};

const starStageTemplate = {
  id: 'star-stage',
  label: copy('星级阶段', 'Star stage'),
  fields: starStageFields
};

const branchTemplate = {
  id: 'branch-node',
  label: copy('分支节点', 'Branch node'),
  fields: branchFields
};
const fields = [
  ['language', '语言', '语言文件 ID，对应 lang/<language>.yml。', 'text'],
  ['version', '配置版本', '默认配置结构版本，通常不建议手动修改。', 'text'],
  ['local_broadcast_radius', '本地广播半径', '强化达到本地广播星级时，附近玩家可收到提示的半径，单位方块格。', 'number'],
  ['broadcast', '广播设置', '强化成功时的本地广播与全服广播触发星级设置。', 'object'],
  ['broadcast.local_stars', '本地广播星级', '强化成功达到这些星级时向附近玩家广播。', 'list'],
  ['broadcast.global_stars', '全服广播星级', '强化成功达到这些星级时向全服广播。', 'list'],
  ['success_rates', '全局成功率', '配方未单独覆盖时使用的全局强化成功率表，键为目标星级，值为百分比。', 'object'],
  ['effects', '效果', '强化阶段效果列表，用于追加变量、EA 属性或 ES 技能。', 'effects']
] as const;

const recipeFields: ConfigMetaFieldEntry[] = [
  ['id', 'ID', '强化配方唯一标识。', 'text'],
  ['display_name', '显示名称', '配方在 GUI、日志或提示中显示的名称。', 'text'],
  ['gui_template', 'GUI 模板', '使用的强化 GUI 模板 ID。', 'text'],
  ['economy', '经济消耗', '强化经济消耗配置。', 'object'],
  ['economy.enabled', '启用经济', '是否启用该配方的经济消耗。', 'boolean'],
  ['economy.currencies', '货币消耗', '强化消耗的货币列表。', 'objectList'],
  ['limits', '限制', '强化等级、星级或次数限制。', 'object'],
  ['limits.max_star', '最大星级', '该配方允许强化到的最高星级。', 'number'],
  ['limits.max_temper', '最大锻印', '失败累积锻印的最大等级。', 'number'],
  ['limits.temper_chance_bonus_per_level', '锻印成功率加成', '每级锻印提供的成功率加成百分比。', 'number'],
  ['limits.success_chance_cap', '成功率上限', '基础成功率和锻印加成后的最高成功率。', 'number'],
  ['success_rates', '成功率覆盖', '该配方按目标星级覆盖的成功率表。', 'object', { creatableChildren: true, createTemplates: [successRateTemplate] }],
  ['match', '匹配规则', '可强化物品的来源、槽位组、Lore 或属性匹配规则。推荐优先用 source_ids 精确绑定物品源。', 'object'],
  ['match.source_types', '来源类型', '允许匹配的物品来源类型。', 'stringList'],
  ['match.source_ids', '来源 ID', '推荐的精确绑定方式；填写 EmakiItem / ItemSource 的来源 ID，避免泛匹配所有同类装备。', 'stringList'],
  ['match.source_patterns', '来源模式', '允许匹配的来源通配或正则模式。', 'stringList'],
  ['match.slot_groups', '槽位组', '可选的粗粒度类型组兜底，可选值 weapon / armor / offhand / generic；不是 main_hand / helmet 这类具体装备槽位。', 'stringList'],
  ['match.lore_contains', 'Lore 包含', '物品 Lore 需要包含的文本。', 'stringList'],
  ['match.stats_any', '任意属性', '物品拥有任意一个属性时允许匹配。', 'stringList'],
  ['stat_lines', '属性行', '强化属性行模板定义。', 'object'],
  ['stars', '星级阶段', '每个目标星级的材料、属性、技能和动作配置。', 'object', { creatableChildren: true, createTemplates: [starStageTemplate] }],
  ['branch_tree', '分支树', '分支强化路线配置。', 'object'],
  ['branch_tree.stars', '根分支星级', '分支树根节点内的星级阶段。', 'object', { creatableChildren: true, createTemplates: [starStageTemplate] }],
  ['branch_tree.children', '子分支', '根分支后可选择的路线。', 'object', { creatableChildren: true, createTemplates: [branchTemplate] }],
  ['condition', '强化条件', '执行强化前检查的条件判定块；仅用于判定，不执行 on_pass/on_fail 动作。', 'object'],
  ['condition.type', '条件逻辑', '条件表达式组合方式。', 'enum', { options: ['all_of', 'any_of', 'none_of', 'at_least', 'exactly'], optionLabelPrefix: 'conditionType' }],
  ['condition.entries', '条件表达式', 'CoreLib 条件表达式字符串列表。', 'stringList'],
  ['condition.required_count', '需要满足数量', 'at_least / exactly 场景下需要满足的最少条件数量。', 'number'],
  ['name_actions', '名称动作', '强化成功后对物品显示名称执行的动作。', 'actions'],
  ['lore_actions', 'Lore 动作', '强化成功后对物品 Lore 执行的动作。', 'actions'],
  ['effects', '效果', '强化完成后追加的效果列表，支持变量、EA 属性和 ES 技能。', 'effects']
];

export function registerEmakiStrengthenEffectTypes(): void {
  registerEffectTypes(MODULE, [
    coreEffectDefinition('variables'),
    attributeEffectDef,
    skillEffectDef,
    coreEffectDefinition('name_action'),
    coreEffectDefinition('lore_action')
  ]);
}

export const localeMessages: Record<string, string> = Object.fromEntries([
  ['emakistrengthen.module.name', 'Strengthen'],
  ['emakistrengthen.module.summary', '星级、广播、成功率'],
  ['emakistrengthen.file.config.title', '主配置'],
  ['emakistrengthen.file.config.comment', '强化系统主配置，包含成功率、材料、经济和显示策略。'],
  ['emakistrengthen.file.gui.title', 'GUI 模板'],
  ['emakistrengthen.file.gui.comment', '强化界面 GUI 模板，控制目标物品、材料、确认按钮和提示物品。'],
  ['emakistrengthen.file.recipes.title', '配方文件'],
  ['emakistrengthen.file.recipes.comment', '强化配方目录，配置星级阶段、分支路线、材料、成功率和动作。'],
  ['emakistrengthen.filePath.recipes_example_recipe.title', '示例配方'],
  ['emakistrengthen.filePath.recipes_example_recipe.comment', '线性强化配方示例，展示星级阶段、材料、锻印、失败处理和 source_ids 精确绑定。'],
  ['emakistrengthen.filePath.recipes_example_branch_recipe.title', '示例分支配方'],
  ['emakistrengthen.filePath.recipes_example_branch_recipe.comment', '分支强化配方示例，展示路线选择、子分支、阶段继承和演示用 source_ids 绑定。'],
  ['emakistrengthen.filePath.gui_strengthen_gui.title', '强化 GUI'],
  ['emakistrengthen.filePath.gui_strengthen_gui.comment', '强化界面 GUI 模板，控制目标物品、材料和确认槽位。'],
  ['emakistrengthen.file.plugin.title', '插件描述'],
  ['emakistrengthen.file.plugin.comment', 'plugin.yml 元数据、命令、权限和依赖声明。'],
  ['emakistrengthen.file.web-console.title', 'WebUIEdit 注册'],
  ['emakistrengthen.file.web-console.comment', '此插件暴露给 WebUIEdit 的文件分组、编辑器类型和前端扩展入口。'],
  ...fields.flatMap(([path, label, comment]) => [
    [`emakistrengthen.field.${path}`, label],
    [`emakistrengthen.comment.${path}`, comment]
  ]),
  ...recipeFields.flatMap(([path, label, comment]) => [
    [`emakistrengthen.field.${path}`, label],
    [`emakistrengthen.comment.${path}`, comment]
  ])
]);

export const strengthenBranchTreeSchemaAst = defineSchemaAst({
  id: 'emakistrengthen-branch-tree',
  moduleId: MODULE,
  pathPrefix: 'recipes/',
  fields: [
    objectField({
      path: 'branch_tree',
      label: copy('分支树', 'Branch tree'),
      comment: copy('分支强化路线配置。', 'Branching strengthen route configuration.'),
      fields: [
        textField({ path: 'branch_id', label: copy('分支 ID', 'Branch id'), comment: copy('分支唯一标识，root 节点可使用 root。', 'Unique branch id; root may use root.') }),
        textField({ path: 'display_name', label: copy('显示名称', 'Display name'), comment: copy('分支在 GUI 或提示中的显示名称。', 'Display name used in GUI or prompts.') }),
        numberField({ path: 'fork_after_star', label: copy('分叉星级', 'Fork after star'), comment: copy('-1 表示不再分叉。', '-1 means no further fork.'), defaultValue: -1 }),
        objectMapField({ path: 'stars', label: copy('星级阶段', 'Star stages'), comment: copy('该分支内的星级阶段。', 'Star stages inside this branch.'), creatableChildren: true }),
        objectMapField({ path: 'children', label: copy('子分支', 'Child branches'), comment: copy('此分支后续可选择的子路线。', 'Selectable child routes after this branch.'), creatableChildren: true })
      ]
    }),
    objectMapField({ path: 'stars', label: copy('星级阶段', 'Star stages'), comment: copy('线性配方的星级阶段。', 'Star stages for linear recipes.'), creatableChildren: true }),
    materialCostListField({ path: 'materials', label: copy('材料', 'Materials'), comment: copy('强化材料列表。', 'Strengthen material list.') }),
    currencyCostListField({ path: 'economy.currencies', label: copy('货币消耗', 'Currency costs'), comment: copy('强化经济消耗中的货币列表。', 'Currency list used by strengthen economy costs.') }),
    effectListField({ path: 'effects', label: copy('效果', 'Effects'), comment: copy('强化阶段效果列表。', 'Strengthen stage effects.') }),
    conditionGroupField({ path: 'condition', label: copy('强化条件', 'Strengthen condition'), comment: copy('执行强化前检查的 CoreLib 条件组。', 'CoreLib condition group checked before strengthening.') }),
    nameLoreActionField({ path: 'name_actions', label: copy('名称动作', 'Name actions'), comment: copy('强化成功后对物品显示名称执行的动作。', 'Actions applied to item display name after success.') }),
    nameLoreActionField({ path: 'lore_actions', label: copy('Lore 动作', 'Lore actions'), comment: copy('强化成功后对物品 Lore 执行的动作。', 'Actions applied to item lore after success.') })
  ]
});

export const strengthenBranchTreeManifestSchema = defineConfigSchema(strengthenBranchTreeSchemaAst);
export const strengthenPluginConfig: WebManifestPluginConfig = {
    metaFields: fields.map(([path, label, comment, type]) => [path, label, comment, type, path === 'success_rates' ? { creatableChildren: true } : undefined] as ConfigMetaFieldEntry),
    fileSchemas: [
      {
        pathPrefix: 'recipes/',
        fields: recipeFields
      }
    ],
    createTemplates: [
      ['success_rates', successRateTemplate],
      ['stars', starStageTemplate],
      ['branch_tree.stars', starStageTemplate],
      ['branch_tree.children', branchTemplate]
    ],
    listItemSchemas: [],
    rules: [
      [{ key: 'effects' }, { label: '效果', comment: '强化阶段效果列表，按 type 分流为变量、EA 属性、ES 技能、名称/Lore 动作。', type: 'effects' }],
      [{ key: 'variables' }, { label: '变量', comment: '变量键值；属性计算应保持数值结果，name/lore actions 文本模板可使用随机文本、随机字符、权重随机字符和条件字符。', type: 'variablesMap' }],
      [{ key: 'ea_attributes' }, { label: 'EA 属性', comment: 'EmakiAttribute 属性数值映射。', type: 'dynamic_map' }],
      [{ key: 'es_skills' }, { label: 'ES 技能', comment: 'EmakiSkills 技能 ID 列表。', type: 'stringList' }],
      [{ key: 'name_actions' }, { label: '名称动作链', comment: '对物品显示名称执行的 CoreLib Action 列表。', type: 'actions' }],
      [{ key: 'lore_actions' }, { label: 'Lore 动作链', comment: '对物品 Lore 执行的 CoreLib Action 列表。', type: 'actions' }],
      [{ key: 'stars' }, { label: '星级阶段', comment: '按目标星级添加阶段配置。每个子键应为星级数字。', type: 'object', creatableChildren: true, createTemplates: [starStageTemplate] }],
      [{ key: 'children' }, { label: '子分支', comment: '按分支 ID 添加后续路线，用于分支强化选择。', type: 'object', creatableChildren: true, createTemplates: [branchTemplate] }]
    ],
    listItemSchemaRules: [
      [{ key: 'materials' }, materialFields],
      [{ key: 'currencies' }, currencyFields]
    ]
  };
