import { BlueprintGraph, PreviewItemResult, PreviewMetricStrip, getLocale, registerConfigPreview, registerModuleLocale, registerPluginConfig, registerPluginGuiEditor, type BlueprintEdge, type BlueprintNode, type ConfigMetaFieldEntry, type ConfigPreviewProps } from 'emaki-web-console';

const STRENGTHEN_EFFECT_TYPES = ['variables', 'ea_attribute', 'es_skill'];

const MODULE = 'EmakiStrengthen';
type AnyMap = Record<string, unknown>;

const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;

const fields = [
  ['language', '语言', '语言文件 ID，对应 lang/<language>.yml。', 'text'],
  ['version', '配置版本', '默认配置结构版本，通常不建议手动修改。', 'text'],
  ['local_broadcast_radius', '本地广播半径', '强化达到本地广播星级时，附近玩家可收到提示的半径，单位方块格。', 'number'],
  ['broadcast', '广播设置', '强化成功时的本地广播与全服广播触发星级设置。', 'object'],
  ['broadcast.local_stars', '本地广播星级', '强化成功达到这些星级时向附近玩家广播。', 'list'],
  ['broadcast.global_stars', '全服广播星级', '强化成功达到这些星级时向全服广播。', 'list'],
  ['success_rates', '全局成功率', '配方未单独覆盖时使用的全局强化成功率表，键为目标星级，值为百分比。', 'object'],
  ['effects', '效果', '强化阶段效果列表；源码当前实际从 effects 中读取 es_skill。', 'objectList']
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
  ['success_rates', '成功率覆盖', '该配方按目标星级覆盖的成功率表。', 'object'],
  ['match', '匹配规则', '可强化物品的来源、槽位、Lore 或属性匹配规则。', 'object'],
  ['match.source_types', '来源类型', '允许匹配的物品来源类型。', 'stringList'],
  ['match.source_ids', '来源 ID', '允许匹配的来源 ID。', 'stringList'],
  ['match.source_patterns', '来源模式', '允许匹配的来源通配或正则模式。', 'stringList'],
  ['match.slot_groups', '槽位组', '允许强化的装备槽位或槽位组。', 'stringList'],
  ['match.lore_contains', 'Lore 包含', '物品 Lore 需要包含的文本。', 'stringList'],
  ['match.stats_any', '任意属性', '物品拥有任意一个属性时允许匹配。', 'stringList'],
  ['stat_lines', '属性行', '强化属性行模板定义。', 'object'],
  ['stars', '星级阶段', '每个目标星级的材料、属性、技能和动作配置。', 'object'],
  ['branch_tree', '分支树', '分支强化路线配置。', 'object'],
  ['condition_type', '条件逻辑', '条件表达式组合方式。', 'enum', { options: ['all_of', 'any_of'], optionLabelPrefix: 'condition_type' }],
  ['condition_required_count', '需要满足数量', 'any_of 场景下需要满足的最少条件数量。', 'number'],
  ['name_actions', '名称动作', '强化成功后对物品显示名称执行的动作。', 'objectList'],
  ['lore_actions', 'Lore 动作', '强化成功后对物品 Lore 执行的动作。', 'objectList'],
  ['effects', '效果', '兼容效果列表，源码读取 variables、ea_attribute、es_skill。', 'objectList']
];

const localeMessages: Record<string, string> = Object.fromEntries([
  ['emakistrengthen.module.name', 'Strengthen'],
  ['emakistrengthen.module.summary', '星级、广播、成功率'],
  ['emakistrengthen.file.config.title', '主配置'],
  ['emakistrengthen.file.config.comment', '强化系统主配置，包含成功率、材料、经济和显示策略。'],
  ['emakistrengthen.file.gui.title', 'GUI 模板'],
  ['emakistrengthen.file.gui.comment', '强化界面 GUI 模板文件。'],
  ['emakistrengthen.file.recipes.title', '配方文件'],
  ['emakistrengthen.file.recipes.comment', '强化配方定义文件目录。'],
  ['emakistrengthen.filePath.recipes_example_recipe.title', '示例配方'],
  ['emakistrengthen.filePath.recipes_example_recipe.comment', '示例配方文件。'],
  ['emakistrengthen.filePath.recipes_example_branch_recipe.title', '示例分支配方'],
  ['emakistrengthen.filePath.recipes_example_branch_recipe.comment', '示例分支配方文件。'],
  ['emakistrengthen.filePath.gui_strengthen_gui.title', '强化 GUI'],
  ['emakistrengthen.filePath.gui_strengthen_gui.comment', '强化 GUI 模板文件。'],
  ['emakistrengthen.file.plugin.title', '插件描述'],
  ['emakistrengthen.file.plugin.comment', 'plugin.yml 插件描述与依赖声明。'],
  ['emakistrengthen.file.web-console.title', 'Web Console 声明'],
  ['emakistrengthen.file.web-console.comment', 'Web Console 文件注册与资源入口声明。'],
  ...fields.flatMap(([path, label, comment]) => [
    [`emakistrengthen.field.${path}`, label],
    [`emakistrengthen.comment.${path}`, comment]
  ]),
  ...recipeFields.flatMap(([path, label, comment]) => [
    [`emakistrengthen.field.${path}`, label],
    [`emakistrengthen.comment.${path}`, comment]
  ])
]);

registerModuleLocale(MODULE, 'zh-CN', {
  ...localeMessages,
  'emakistrengthen.surface.gui': '强化 GUI',
  'emakistrengthen.field.target_item': '目标物品',
  'emakistrengthen.field.material': '强化材料',
  'emakistrengthen.field.confirm': '确认按钮'
});

registerModuleLocale(MODULE, 'en-US', {
  'emakistrengthen.module.name': 'Strengthen',
  'emakistrengthen.module.summary': 'Stars, broadcasts, and success rates',
  'emakistrengthen.file.config.title': 'Main Config',
  'emakistrengthen.file.config.comment': 'Main strengthen configuration covering success rates, materials, economy, and display strategy.',
  'emakistrengthen.file.gui.title': 'GUI Templates',
  'emakistrengthen.file.gui.comment': 'Strengthen GUI template files.',
  'emakistrengthen.file.recipes.title': 'Recipe Files',
  'emakistrengthen.file.recipes.comment': 'Directory for strengthen recipe definition files.',
  'emakistrengthen.filePath.recipes_example_recipe.title': 'Sample Recipe',
  'emakistrengthen.filePath.recipes_example_recipe.comment': 'Sample recipe file.',
  'emakistrengthen.filePath.recipes_example_branch_recipe.title': 'Sample Branch Recipe',
  'emakistrengthen.filePath.recipes_example_branch_recipe.comment': 'Sample branch recipe file.',
  'emakistrengthen.filePath.gui_strengthen_gui.title': 'Strengthen GUI',
  'emakistrengthen.filePath.gui_strengthen_gui.comment': 'Strengthen GUI template file.',
  'emakistrengthen.file.plugin.title': 'Plugin Description',
  'emakistrengthen.file.plugin.comment': 'plugin.yml plugin metadata and dependency declaration.',
  'emakistrengthen.file.web-console.title': 'Web Console Declaration',
  'emakistrengthen.file.web-console.comment': 'Web Console file registration and resource entry declaration.',
  'emakistrengthen.surface.gui': 'Strengthen GUI',
  'emakistrengthen.field.local_broadcast_radius': 'Local Broadcast Radius',
  'emakistrengthen.field.broadcast': 'Broadcast',
  'emakistrengthen.field.broadcast.local_stars': 'Local Stars',
  'emakistrengthen.field.broadcast.global_stars': 'Global Stars',
  'emakistrengthen.field.success_rates': 'Success Rates',
  'emakistrengthen.field.target_item': 'Target Item',
  'emakistrengthen.field.material': 'Material',
  'emakistrengthen.field.confirm': 'Confirm'
});

registerPluginConfig({
  moduleId: MODULE,
  metaFields: fields.map(([path, label, comment, type]) => [path, label, comment, type, path === 'success_rates' ? { creatableChildren: true } : undefined]),
  fileSchemas: [
    {
      pathPrefix: 'recipes/',
      fields: recipeFields
    }
  ],
  createTemplates: [
    ['success_rates', {
      id: 'star-success-rate',
      label: copy('目标星级成功率', 'Target star success rate'),
      fields: [
        { path: 'value', label: '成功率', comment: '该目标星级的强化成功率百分比，例如 75.0。', type: 'number', defaultValue: 100 }
      ]
    }]
  ],
  rules: [
    [{ key: 'effects' }, { label: '效果', comment: '强化阶段效果列表；新增类型以源码实际解析为准。', type: 'objectList' }]
  ],
  listItemSchemaRules: [
    [{ key: 'effects' }, [
      { path: 'type', label: '类型', comment: '源码实际解析 variables / ea_attribute / es_skill；es_skill 会从 effects 中生效。', type: 'enum', options: STRENGTHEN_EFFECT_TYPES, defaultValue: 'variables' },
      { path: 'variables', label: '变量', comment: '保真编辑变量对象；源码当前主要读取阶段顶层 variables。', type: 'json', defaultValue: {} },
      { path: 'ea_attributes', label: 'EA 属性', comment: '保真编辑属性对象；源码当前主要读取阶段顶层 ea_attributes。', type: 'json', defaultValue: {} },
      { path: 'es_skills', label: 'ES 技能', comment: '源码会从 effects 中读取的技能 ID 列表。', type: 'stringList', defaultValue: [] },
      { path: 'es_skill', label: 'ES 技能简写', comment: '单个技能 ID 简写，源码会与 es_skills 合并读取。', type: 'text', defaultValue: '' },
      { path: 'name_actions', label: '名称动作链', comment: '保真编辑名称动作效果。', type: 'objectList', defaultValue: [] },
      { path: 'lore_actions', label: 'Lore 动作链', comment: '保真编辑 Lore 动作效果。', type: 'objectList', defaultValue: [] }
    ]],
    [{ key: 'materials' }, [
      { path: 'item_sources', label: '物品来源', comment: '强化材料的 ItemSource 列表。', type: 'stringList', defaultValue: ['minecraft-copper_ingot'] },
      { path: 'amount', label: '数量', comment: '需要消耗的材料数量；-1 表示只检测不消耗。', type: 'number', defaultValue: 1 },
      { path: 'optional', label: '可选', comment: '是否为可选材料。', type: 'boolean', defaultValue: false },
      { path: 'protection', label: '保护材料', comment: '失败时提供保护效果的材料。', type: 'boolean', defaultValue: false },
      { path: 'temper_boost', label: '锻印提升', comment: '放入后额外增加的锻印等级。', type: 'number', defaultValue: 0 }
    ]],
    [{ key: 'currencies' }, [
      { path: 'provider', label: '经济提供器', comment: '经济系统提供器，例如 vault。', type: 'text', defaultValue: 'vault' },
      { path: 'currency_id', label: '货币 ID', comment: '多货币系统的货币标识；留空使用默认货币。', type: 'text', defaultValue: '' },
      { path: 'base_cost', label: '基础费用', comment: '强化经济消耗的基础数值。', type: 'number', defaultValue: 0 },
      { path: 'cost_formula', label: '费用公式', comment: '根据星级等变量计算最终费用的公式。', type: 'text', defaultValue: '' },
      { path: 'display_name', label: '显示名称', comment: '货币在提示中的显示名称。', type: 'text', defaultValue: '' }
    ]]
  ]
});

registerConfigPreview({
  moduleId: MODULE,
  kind: 'CONFIG',
  pathPrefix: 'recipes/',
  priority: 120,
  component: StrengthenRecipePreview
});

registerPluginGuiEditor({
  moduleId: MODULE,
  editorId: 'emakistrengthen:gui',
  label: copy('强化 GUI', 'Strengthen GUI'),
  fields: [
    ['type', '槽位类型', '强化业务槽位语义。', 'text'],
    ['target_item', '目标物品', '放入待强化物品的槽位。', 'text'],
    ['material', '强化材料', '放入强化材料的槽位。', 'text'],
    ['success_preview', '成功率预览', '显示当前强化成功率与目标星级的槽位。', 'text'],
    ['confirm', '确认按钮', '执行强化操作的按钮槽位。', 'text']
  ]
});

function StrengthenRecipePreview({ data, path }: ConfigPreviewProps) {
  const stars = asRecord(data.stars);
  const tree = asRecord(data.branch_tree);
  const hasTree = Object.keys(tree).length > 0;
  const graph = hasTree ? strengthenBranchGraph(tree, asRecord(data.success_rates)) : linearStarsGraph(stars, asRecord(data.success_rates));
  const starCount = graph.starCount;
  const branches = graph.branchCount;
  const maxStar = Number(asRecord(data.limits).max_star ?? Math.max(0, ...graph.starLevels));
  const firstMaterial = graph.firstMaterial;
  return <div className="config-preview-shell">
    <div className="config-preview-head">
      <div>
        <h3>{copy('强化预览', 'Strengthen Preview')}</h3>
        <p>{String(data.display_name ?? data.id ?? path)} · {copy('字段变更会实时反映到下方路线图。', 'Field changes are reflected in the route graph.')}</p>
      </div>
      <code>{path}</code>
    </div>
    <PreviewMetricStrip facts={[
      { label: copy('星级', 'Stars'), value: starCount || maxStar },
      { label: copy('分支', 'Branches'), value: branches || 1, tone: branches > 1 ? 'warn' : 'default' },
      { label: copy('最高', 'Max'), value: maxStar ? `+${maxStar}` : '—' },
      { label: copy('成功率', 'Rates'), value: Object.keys(asRecord(data.success_rates)).length }
    ]} />
    <PreviewItemResult title={copy('强化结果摘要', 'Strengthened Result')} itemSources={firstMaterial ? [firstMaterial] : []} name={String(data.display_name ?? data.id ?? copy('强化物品', 'Strengthened Item'))} lore={strengthenLoreSummary(data, graph)} status={hasTree ? copy('分支配方', 'Branch recipe') : copy('线性配方', 'Linear recipe')} />
    <BlueprintGraph title={copy('强化节点蓝图', 'Strengthen Blueprint')} summary={`${graph.nodes.length} nodes / ${graph.edges.length} links`} nodes={graph.nodes} edges={graph.edges} />
  </div>;
}

function strengthenBranchGraph(root: AnyMap, rates: AnyMap) {
  const nodes: BlueprintNode[] = [];
  const edges: BlueprintEdge[] = [];
  const starLevels: number[] = [];
  let branchCount = 0;
  let firstMaterial = '';
  const visit = (branch: AnyMap, parentId: string | null, depth: number, row: number, pathLabel: string) => {
    const id = String(branch.branch_id ?? (pathLabel || `branch_${nodes.length}`));
    const branchStars = sortedStarEntries(asRecord(branch.stars));
    branchStars.forEach(([level]) => starLevels.push(level));
    const materials = branchStars.flatMap(([, star]) => asList(asRecord(star).materials));
    if (!firstMaterial) firstMaterial = firstSource(materials[0]);
    nodes.push({
      id,
      title: stripMini(String(branch.display_name ?? id)),
      subtitle: branchStars.length ? `+${branchStars[0][0]} → +${branchStars[branchStars.length - 1][0]}` : copy('无星级阶段', 'No star stages'),
      meta: Number(branch.fork_after_star) >= 0 ? `fork @ +${branch.fork_after_star}` : 'leaf',
      tone: parentId ? 'accent' : 'warn',
      column: depth,
      row,
      facts: [{ label: copy('阶段', 'Stages'), value: branchStars.length }, { label: copy('材料', 'Materials'), value: materials.length }, { label: copy('成功率', 'Rate'), value: branchStars.map(([level]) => rates[String(level)] ?? rates[level]).filter(Boolean)[0] ?? '—' }]
    });
    if (parentId) edges.push({ from: parentId, to: id, tone: 'accent' });
    const children = asRecord(branch.children);
    Object.entries(children).forEach(([key, child], index) => {
      branchCount += 1;
      visit(asRecord(child), id, depth + 1, row + index, key);
    });
  };
  visit(root, null, 0, 0, 'root');
  return { nodes, edges, starCount: new Set(starLevels).size, starLevels, branchCount, firstMaterial };
}

function linearStarsGraph(stars: AnyMap, rates: AnyMap) {
  const entries = sortedStarEntries(stars);
  const nodes: BlueprintNode[] = entries.map(([level, star], index) => {
    const record = asRecord(star);
    return { id: `star_${level}`, title: `+${level}`, subtitle: stripMini(String(record.name ?? copy('强化阶段', 'Strengthen stage'))), meta: `${rates[String(level)] ?? rates[level] ?? '—'}%`, tone: index === entries.length - 1 ? 'good' : 'default', column: index, row: 0, facts: [{ label: copy('材料', 'Materials'), value: asList(record.materials).length }, { label: copy('效果', 'Effects'), value: asList(record.effects).length }] };
  });
  return { nodes, edges: nodes.slice(1).map((node, index) => ({ from: nodes[index].id, to: node.id })), starCount: entries.length, starLevels: entries.map(([level]) => level), branchCount: 0, firstMaterial: firstSource(asList(asRecord(entries[0]?.[1]).materials)[0]) };
}

function strengthenLoreSummary(data: AnyMap, graph: ReturnType<typeof linearStarsGraph>) {
  return [
    `${copy('路线节点', 'Route nodes')}: ${graph.nodes.length}`,
    `${copy('分支数量', 'Branches')}: ${graph.branchCount || 0}`,
    `${copy('名称动作', 'Name actions')}: ${asList(data.name_actions).length}`,
    `${copy('Lore 动作', 'Lore actions')}: ${asList(data.lore_actions).length}`
  ];
}

function sortedStarEntries(stars: AnyMap): Array<[number, AnyMap]> {
  return Object.entries(stars).map(([key, value]) => [Number(key), asRecord(value)] as [number, AnyMap]).filter(([level]) => Number.isFinite(level)).sort((a, b) => a[0] - b[0]);
}

function firstSource(material: unknown): string {
  return asStringList(asRecord(material).item_sources)[0] ?? '';
}

function asRecord(value: unknown): AnyMap {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as AnyMap : {};
}

function asList(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  return value == null || value === '' ? [] : [value];
}

function asStringList(value: unknown): string[] {
  return asList(value).flatMap(entry => Array.isArray(entry) ? asStringList(entry) : entry == null ? [] : [String(entry)]);
}

function stripMini(value: string): string {
  return value.replace(/<[^>]+>/g, '').trim();
}
