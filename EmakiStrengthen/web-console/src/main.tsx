import { useMemo } from 'react';
import { PreviewMetricStrip, getLocale, registerConfigPreview, registerModuleLocale, registerPluginConfig, registerPluginGuiEditor, type ConfigMetaFieldEntry, type ConfigPreviewProps, type PreviewFact } from 'emaki-web-console';
import blueprintCss from './blueprint.css?inline';

const STRENGTHEN_EFFECT_TYPES = ['variables', 'ea_attribute', 'es_skill'];

const MODULE = 'EmakiStrengthen';
const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;
const actionFields = [
  { path: 'action', label: '动作', comment: '动作类型。', type: 'text', defaultValue: '' },
  { path: 'value', label: '文本值', comment: '动作使用的文本值或格式参数。', type: 'text', defaultValue: '' },
  { path: 'content', label: '内容', comment: '动作写入的多行内容。', type: 'stringList', defaultValue: [] },
  { path: 'anchor', label: '锚点', comment: '定位目标行时使用的锚点。', type: 'text', defaultValue: '' },
  { path: 'target_pattern', label: '目标匹配', comment: '定位目标行时使用的模式。', type: 'text', defaultValue: '' },
  { path: 'regex_pattern', label: '正则', comment: '正则替换动作的匹配表达式。', type: 'text', defaultValue: '' },
  { path: 'replacement', label: '替换内容', comment: '正则替换动作的替换内容。', type: 'text', defaultValue: '' }
];

const effectFields = [
  { path: 'type', label: '类型', comment: '源码实际解析 variables / ea_attribute / es_skill；lore_action/name_action 用于保真编辑显示动作。', type: 'enum', options: STRENGTHEN_EFFECT_TYPES, defaultValue: 'variables' },
  { path: 'variables', label: '变量', comment: '变量对象。', type: 'json', defaultValue: {} },
  { path: 'ea_attributes', label: 'EA 属性', comment: '写入 EmakiAttribute 的属性。', type: 'json', defaultValue: {} },
  { path: 'es_skills', label: 'ES 技能', comment: '技能 ID 列表。', type: 'stringList', defaultValue: [] },
  { path: 'es_skill', label: 'ES 技能简写', comment: '单个技能 ID 简写。', type: 'text', defaultValue: '' },
  { path: 'name_actions', label: '名称动作链', comment: '名称动作对象列表。', type: 'objectList', defaultValue: [], itemFields: actionFields },
  { path: 'lore_actions', label: 'Lore 动作链', comment: 'Lore 动作对象列表。', type: 'objectList', defaultValue: [], itemFields: actionFields }
];

const materialFields = [
  { path: 'item_sources', label: '物品来源', comment: '强化材料的 ItemSource 列表。', type: 'stringList', defaultValue: ['minecraft-copper_ingot'] },
  { path: 'amount', label: '数量', comment: '需要消耗的材料数量；-1 表示只检测不消耗。', type: 'number', defaultValue: 1 },
  { path: 'optional', label: '可选', comment: '是否为可选材料。', type: 'boolean', defaultValue: false },
  { path: 'protection', label: '保护材料', comment: '失败时提供保护效果的材料。', type: 'boolean', defaultValue: false },
  { path: 'temper_boost', label: '锻印提升', comment: '放入后额外增加的锻印等级。', type: 'number', defaultValue: 0 }
];

const currencyFields = [
  { path: 'provider', label: '经济提供器', comment: '经济系统提供器，例如 vault。', type: 'text', defaultValue: 'vault' },
  { path: 'currency_id', label: '货币 ID', comment: '多货币系统的货币标识；留空使用默认货币。', type: 'text', defaultValue: '' },
  { path: 'base_cost', label: '基础费用', comment: '强化经济消耗的基础数值。', type: 'number', defaultValue: 0 },
  { path: 'cost_formula', label: '费用公式', comment: '根据星级等变量计算最终费用的公式。', type: 'text', defaultValue: '' },
  { path: 'display_name', label: '显示名称', comment: '货币在提示中的显示名称。', type: 'text', defaultValue: '' }
];

const starStageFields = [
  { path: 'name', label: '阶段名称', comment: '该星级的里程碑名称，可留空。', type: 'text', defaultValue: '' },
  { path: 'variables', label: '变量', comment: '表达式变量或属性增量，源码从星级阶段顶层 variables 读取。', type: 'json', defaultValue: {} },
  { path: 'ea_attributes', label: 'EA 属性', comment: '显式 EmakiAttribute 属性覆盖，源码从星级阶段顶层 ea_attributes 读取。', type: 'json', defaultValue: {} },
  { path: 'effects', label: '效果', comment: '技能或显示动作效果列表。', type: 'objectList', defaultValue: [], itemFields: effectFields },
  { path: 'materials', label: '材料', comment: '强化到该星级需要的材料列表。', type: 'objectList', defaultValue: [], itemFields: materialFields },
  { path: 'economy_override.currencies', label: '经济覆盖', comment: '该星级专属货币消耗；留空时使用配方 economy。', type: 'objectList', defaultValue: [], itemFields: currencyFields },
  { path: 'actions.success', label: '成功动作', comment: '强化成功到该星级后执行的动作。', type: 'stringList', defaultValue: [] },
  { path: 'actions.failure', label: '失败动作', comment: '强化失败后按结果星级读取的动作。', type: 'stringList', defaultValue: [] }
];

const branchFields = [
  { path: 'branch_id', label: '分支 ID', comment: '分支唯一标识，root 节点可使用 root。', type: 'text', defaultValue: 'branch' },
  { path: 'display_name', label: '显示名称', comment: '分支在 GUI 或提示中的显示名称。', type: 'text', defaultValue: '<yellow>新分支</yellow>' },
  { path: 'fork_after_star', label: '分叉星级', comment: '-1 表示不再分叉；有 children 时表示完成该星级后选择路线。', type: 'number', defaultValue: -1 },
  { path: 'stars', label: '星级阶段', comment: '该分支内的星级阶段，源码兼容 stars/stages。', type: 'object', defaultValue: {} },
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
type AnyMap = Record<string, unknown>;

type BlueprintNode = {
  id: string;
  title: string;
  subtitle?: string;
  meta?: string;
  tone?: 'default' | 'accent' | 'good' | 'warn' | 'bad';
  column?: number;
  row?: number;
  facts?: PreviewFact[];
};

type BlueprintEdge = { from: string; to: string; tone?: 'default' | 'accent' | 'good' | 'warn' | 'bad'; label?: string };

installStrengthenBlueprintStyles();

function installStrengthenBlueprintStyles() {
  const id = 'emakistrengthen-blueprint-styles';
  if (document.getElementById(id)) return;
  const style = document.createElement('style');
  style.id = id;
  style.textContent = blueprintCss;
  document.head.appendChild(style);
}

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
  ['success_rates', '成功率覆盖', '该配方按目标星级覆盖的成功率表。', 'object', { creatableChildren: true, createTemplates: [successRateTemplate] }],
  ['match', '匹配规则', '可强化物品的来源、槽位、Lore 或属性匹配规则。', 'object'],
  ['match.source_types', '来源类型', '允许匹配的物品来源类型。', 'stringList'],
  ['match.source_ids', '来源 ID', '允许匹配的来源 ID。', 'stringList'],
  ['match.source_patterns', '来源模式', '允许匹配的来源通配或正则模式。', 'stringList'],
  ['match.slot_groups', '槽位组', '允许强化的装备槽位或槽位组。', 'stringList'],
  ['match.lore_contains', 'Lore 包含', '物品 Lore 需要包含的文本。', 'stringList'],
  ['match.stats_any', '任意属性', '物品拥有任意一个属性时允许匹配。', 'stringList'],
  ['stat_lines', '属性行', '强化属性行模板定义。', 'object'],
  ['stars', '星级阶段', '每个目标星级的材料、属性、技能和动作配置。', 'object', { creatableChildren: true, createTemplates: [starStageTemplate] }],
  ['branch_tree', '分支树', '分支强化路线配置。', 'object'],
  ['branch_tree.stars', '根分支星级', '分支树根节点内的星级阶段；源码兼容 stars/stages。', 'object', { creatableChildren: true, createTemplates: [starStageTemplate] }],
  ['branch_tree.children', '子分支', '根分支后可选择的路线。', 'object', { creatableChildren: true, createTemplates: [branchTemplate] }],
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
  ['emakistrengthen.file.recipes.comment', '强化配方文件目录。'],
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
  'emakistrengthen.file.recipes.comment': 'Directory for strengthen recipe files.',
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
    ['success_rates', successRateTemplate],
    ['stars', starStageTemplate],
    ['branch_tree.stars', starStageTemplate],
    ['branch_tree.children', branchTemplate]
  ],
  rules: [
    [{ key: 'effects' }, { label: '效果', comment: '强化阶段效果列表；新增类型以源码实际解析为准。', type: 'objectList' }],
    [{ key: 'stars' }, { label: '星级阶段', comment: '按目标星级添加阶段配置；源码解析为 Map<Integer, StarStage>。', type: 'object', creatableChildren: true, createTemplates: [starStageTemplate] }],
    [{ key: 'children' }, { label: '子分支', comment: '按分支 ID 添加子路线；源码解析为分支节点映射。', type: 'object', creatableChildren: true, createTemplates: [branchTemplate] }]
  ],
  listItemSchemaRules: [
    [{ key: 'effects' }, effectFields],
    [{ key: 'materials' }, materialFields],
    [{ key: 'currencies' }, currencyFields],
    [{ key: 'name_actions' }, actionFields],
    [{ key: 'lore_actions' }, actionFields]
  ]
});

registerConfigPreview({
  moduleId: MODULE,
  kind: 'CONFIG',
  pathPrefix: 'recipes/',
  priority: 120,
  component: StrengthenBlueprintPanel
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

function StrengthenBlueprintPanel({ data, path }: ConfigPreviewProps) {
  const stars = asRecord(data.stars);
  const tree = asRecord(data.branch_tree);
  const hasTree = Object.keys(tree).length > 0;
  const graph = hasTree ? strengthenBranchGraph(tree, asRecord(data.success_rates)) : linearStarsGraph(stars, asRecord(data.success_rates));
  const starCount = graph.starCount;
  const branches = graph.branchCount;
  const maxStar = Number(asRecord(data.limits).max_star ?? Math.max(0, ...graph.starLevels));
  return <div className="config-preview-shell strengthen-blueprint-shell">
    <div className="config-preview-head">
      <div>
        <h3>{copy('强化蓝图', 'Strengthen Blueprint')}</h3>
        <p>{String(data.display_name ?? data.id ?? path)} · {copy('只保留强化路线蓝图，其他插件不再挂载此类面板。', 'Only the strengthen route blueprint is kept; other plugins no longer mount this panel.')}</p>
      </div>
      <code>{path}</code>
    </div>
    <PreviewMetricStrip facts={[
      { label: copy('星级', 'Stars'), value: starCount || maxStar },
      { label: copy('分支', 'Branches'), value: branches || 1, tone: branches > 1 ? 'warn' : 'default' },
      { label: copy('最高', 'Max'), value: maxStar ? `+${maxStar}` : '—' },
      { label: copy('成功率', 'Rates'), value: Object.keys(asRecord(data.success_rates)).length }
    ]} />
    <BlueprintGraph title={copy('强化节点蓝图', 'Strengthen Blueprint')} summary={`${graph.nodes.length} nodes / ${graph.edges.length} links`} nodes={graph.nodes} edges={graph.edges} />
  </div>;
}

function BlueprintGraph({ title = '节点蓝图', summary, nodes, edges }: { title?: string; summary?: string; nodes: BlueprintNode[]; edges: BlueprintEdge[] }) {
  const visibleNodes = nodes.slice(0, 96);
  const visibleNodeIds = useMemo(() => new Set(visibleNodes.map(node => node.id)), [visibleNodes]);
  const visibleEdges = edges.filter(edge => visibleNodeIds.has(edge.from) && visibleNodeIds.has(edge.to)).slice(0, 160);
  const hiddenCount = Math.max(0, nodes.length - visibleNodes.length);
  const layout = useMemo(() => layoutBlueprint(visibleNodes), [visibleNodes]);
  if (!nodes.length) return <div className="blueprint-panel empty"><div className="blueprint-head"><span>{title}</span></div><p>{copy('没有可预览的节点。', 'No nodes to preview.')}</p></div>;
  const width = Math.max(520, Math.min(3600, (layout.maxColumn + 1) * 190 + 40));
  const height = Math.max(180, Math.min(2600, (layout.maxRow + 1) * 112 + 34));
  return <div className="blueprint-panel">
    <div className="blueprint-head">
      <span>{title}</span>
      {summary && <code>{hiddenCount ? `${summary} · ${copy('显示前', 'showing first')} ${visibleNodes.length}` : summary}</code>}
    </div>
    <div className="blueprint-scroll" role="img" aria-label={title}>
      <div className="blueprint-canvas" style={{ width, height }}>
        <svg className="blueprint-wires" width={width} height={height} aria-hidden="true">
          {visibleEdges.map((edge, index) => {
            const from = layout.positions.get(edge.from);
            const to = layout.positions.get(edge.to);
            if (!from || !to) return null;
            const x1 = from.x + 150;
            const y1 = from.y + 38;
            const x2 = to.x;
            const y2 = to.y + 38;
            const mid = Math.max(x1 + 28, (x1 + x2) / 2);
            return <g key={`${edge.from}-${edge.to}-${index}`} className={`blueprint-wire ${edge.tone ?? 'default'}`}>
              <path d={`M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2} ${y2}`} />
              {edge.label && <text x={(x1 + x2) / 2} y={(y1 + y2) / 2 - 6}>{edge.label}</text>}
            </g>;
          })}
        </svg>
        {layout.nodes.map(node => <div key={node.id} className={`blueprint-node ${node.tone ?? 'default'}`} style={{ left: node.x, top: node.y }}>
          <div className="blueprint-node-title">{node.title}</div>
          {node.subtitle && <div className="blueprint-node-subtitle">{node.subtitle}</div>}
          {node.meta && <code>{node.meta}</code>}
          {node.facts?.length ? <div className="blueprint-node-facts">
            {node.facts.slice(0, 4).map((fact, index) => <span key={`${fact.label}-${index}`}>{fact.label}: <b>{formatPreviewValue(fact.value)}</b></span>)}
          </div> : null}
        </div>)}
      </div>
    </div>
  </div>;
}

function layoutBlueprint(nodes: BlueprintNode[]) {
  const used = new Map<string, number>();
  let maxColumn = 0;
  let maxRow = 0;
  const positioned = nodes.map((node, index) => {
    const column = Number.isFinite(node.column) ? Number(node.column) : index;
    const key = String(column);
    const fallbackRow = used.get(key) ?? 0;
    const row = Number.isFinite(node.row) ? Number(node.row) : fallbackRow;
    used.set(key, Math.max(fallbackRow, row + 1));
    maxColumn = Math.max(maxColumn, column);
    maxRow = Math.max(maxRow, row);
    return { ...node, x: 20 + column * 190, y: 18 + row * 112 };
  });
  return { nodes: positioned, positions: new Map(positioned.map(node => [node.id, node])), maxColumn, maxRow };
}

function formatPreviewValue(value: unknown): string {
  if (value == null || value === '') return '—';
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/\.00$/, '');
  if (typeof value === 'boolean') return value ? copy('是', 'yes') : copy('否', 'no');
  if (Array.isArray(value)) return value.length > 3 ? `${value.slice(0, 3).join(', ')} +${value.length - 3}` : value.join(', ');
  if (typeof value === 'object') return `${Object.keys(value as Record<string, unknown>).length} ${copy('项', 'items')}`;
  return String(value);
}

function strengthenBranchGraph(root: AnyMap, rates: AnyMap) {
  const nodes: BlueprintNode[] = [];
  const edges: BlueprintEdge[] = [];
  const starLevels: number[] = [];
  const visited = new Set<AnyMap>();
  let branchCount = 0;
  let firstMaterial = '';
  const visit = (branch: AnyMap, parentId: string | null, depth: number, row: number, pathLabel: string) => {
    if (visited.has(branch) || nodes.length >= 160) return;
    visited.add(branch);
    const id = uniqueNodeId(String(branch.branch_id ?? (pathLabel || `branch_${nodes.length}`)), nodes);
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
  const entries = sortedStarEntries(stars).slice(0, 160);
  const nodes: BlueprintNode[] = entries.map(([level, star], index) => {
    const record = asRecord(star);
    return { id: `star_${level}`, title: `+${level}`, subtitle: stripMini(String(record.name ?? copy('强化阶段', 'Strengthen stage'))), meta: `${rates[String(level)] ?? rates[level] ?? '—'}%`, tone: index === entries.length - 1 ? 'good' : 'default', column: index, row: 0, facts: [{ label: copy('材料', 'Materials'), value: asList(record.materials).length }, { label: copy('效果', 'Effects'), value: asList(record.effects).length }] };
  });
  return { nodes, edges: nodes.slice(1).map((node, index) => ({ from: nodes[index].id, to: node.id })), starCount: entries.length, starLevels: entries.map(([level]) => level), branchCount: 0, firstMaterial: firstSource(asList(asRecord(entries[0]?.[1]).materials)[0]) };
}

function sortedStarEntries(stars: AnyMap): Array<[number, AnyMap]> {
  return Object.entries(stars).map(([key, value]) => [Number(key), asRecord(value)] as [number, AnyMap]).filter(([level]) => Number.isFinite(level)).sort((a, b) => a[0] - b[0]);
}

function firstSource(material: unknown): string {
  return asStringList(asRecord(material).item_sources)[0] ?? '';
}

function uniqueNodeId(base: string, nodes: BlueprintNode[]): string {
  const clean = base || `node_${nodes.length}`;
  if (!nodes.some(node => node.id === clean)) return clean;
  let index = 2;
  while (nodes.some(node => node.id === `${clean}_${index}`)) index += 1;
  return `${clean}_${index}`;
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
