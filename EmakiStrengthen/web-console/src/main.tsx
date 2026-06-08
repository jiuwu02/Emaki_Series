import React, { useEffect, useMemo, useState } from 'react';
import { getLocale, registerConfigPreview, registerEffectTypes, registerModuleLocale, registerPluginConfig, registerPluginGuiEditor, standardCurrencyCostFields, standardMaterialCostFields, CORE_EFFECT_TYPE_DEFINITIONS, type ConfigMetaFieldEntry, type ConfigPreviewProps, type EffectTypeDefinition } from 'emaki-web-console';

const MODULE = 'EmakiStrengthen';
const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;

const coreEffectDef = (type: string): EffectTypeDefinition => CORE_EFFECT_TYPE_DEFINITIONS.find(def => def.type === type)!;
const attributeEffectDef: EffectTypeDefinition = { type: 'ea_attribute', label: 'EA 属性', fields: [{ key: 'ea_attributes', type: 'map', label: 'EA 属性', defaultValue: {} }] };
const skillEffectDef: EffectTypeDefinition = { type: 'es_skill', label: 'ES 技能', fields: [{ key: 'es_skills', type: 'stringList', label: 'ES 技能', defaultValue: [] }] };

// Unified effect types for EmakiStrengthen star-stage / completion effects.
registerEffectTypes(MODULE, [
  coreEffectDef('variables'),
  attributeEffectDef,
  skillEffectDef,
  coreEffectDef('name_action'),
  coreEffectDef('lore_action')
]);

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
  { path: 'display_name', label: '显示名称', comment: '分支在 GUI 或提示中的显示名称。', type: 'text', defaultValue: '<yellow>新分支</yellow>' },
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
  ['branch_tree.stars', '根分支星级', '分支树根节点内的星级阶段。', 'object', { creatableChildren: true, createTemplates: [starStageTemplate] }],
  ['branch_tree.children', '子分支', '根分支后可选择的路线。', 'object', { creatableChildren: true, createTemplates: [branchTemplate] }],
  ['condition_type', '条件逻辑', '条件表达式组合方式。', 'enum', { options: ['all_of', 'any_of'], optionLabelPrefix: 'condition_type' }],
  ['condition_required_count', '需要满足数量', 'any_of 场景下需要满足的最少条件数量。', 'number'],
  ['name_actions', '名称动作', '强化成功后对物品显示名称执行的动作。', 'actions'],
  ['lore_actions', 'Lore 动作', '强化成功后对物品 Lore 执行的动作。', 'actions'],
  ['effects', '效果', '强化完成后追加的效果列表，支持变量、EA 属性和 ES 技能。', 'effects']
];

const localeMessages: Record<string, string> = Object.fromEntries([
  ['emakistrengthen.module.name', 'Strengthen'],
  ['emakistrengthen.module.summary', '星级、广播、成功率'],
  ['emakistrengthen.file.config.title', '主配置'],
  ['emakistrengthen.file.config.comment', '强化系统主配置，包含成功率、材料、经济和显示策略。'],
  ['emakistrengthen.file.gui.title', 'GUI 模板'],
  ['emakistrengthen.file.gui.comment', '强化界面 GUI 模板，控制目标物品、材料、确认按钮和提示物品。'],
  ['emakistrengthen.file.recipes.title', '配方文件'],
  ['emakistrengthen.file.recipes.comment', '强化配方目录，配置星级阶段、分支路线、材料、成功率和动作。'],
  ['emakistrengthen.filePath.recipes_example_recipe.title', '示例配方'],
  ['emakistrengthen.filePath.recipes_example_recipe.comment', '线性强化配方示例，展示星级阶段、材料、锻印和失败处理。'],
  ['emakistrengthen.filePath.recipes_example_branch_recipe.title', '示例分支配方'],
  ['emakistrengthen.filePath.recipes_example_branch_recipe.comment', '分支强化配方示例，展示路线选择、子分支和阶段继承。'],
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
  'emakistrengthen.file.gui.comment': 'Strengthen GUI template controlling target item, materials, confirm button, and hint items.',
  'emakistrengthen.file.recipes.title': 'Recipe Files',
  'emakistrengthen.file.recipes.comment': 'Strengthen recipe directory covering star stages, branch paths, materials, success rates, and actions.',
  'emakistrengthen.filePath.recipes_example_recipe.title': 'Sample Recipe',
  'emakistrengthen.filePath.recipes_example_recipe.comment': 'Linear strengthen recipe example showing star stages, materials, temper, and failure handling.',
  'emakistrengthen.filePath.recipes_example_branch_recipe.title': 'Sample Branch Recipe',
  'emakistrengthen.filePath.recipes_example_branch_recipe.comment': 'Branch strengthen recipe example showing route choices, child branches, and stage inheritance.',
  'emakistrengthen.filePath.gui_strengthen_gui.title': 'Strengthen GUI',
  'emakistrengthen.filePath.gui_strengthen_gui.comment': 'Strengthen GUI template controlling target item, materials, and confirm slots.',
  'emakistrengthen.file.plugin.title': 'Plugin Description',
  'emakistrengthen.file.plugin.comment': 'plugin.yml metadata, commands, permissions, and dependencies declarations.',
  'emakistrengthen.file.web-console.title': 'WebUIEdit Registration',
  'emakistrengthen.file.web-console.comment': 'File groups, editor kinds, and frontend extension entries exposed to WebUIEdit by this plugin.',
  'emakistrengthen.surface.gui': 'Strengthen GUI',
  'emakistrengthen.field.local_broadcast_radius': 'Local Broadcast Radius',
  'emakistrengthen.field.broadcast': 'Broadcast',
  'emakistrengthen.field.broadcast.local_stars': 'Local Stars',
  'emakistrengthen.field.broadcast.global_stars': 'Global Stars',
  'emakistrengthen.field.success_rates': 'Success Rates',
  'emakistrengthen.field.target_item': 'Target Item',
  'emakistrengthen.field.material': 'Material',
  'emakistrengthen.field.confirm': 'Confirm',
  'emakistrengthen.field.effects': 'Effects',
  'emakistrengthen.field.variables': 'Variables',
  'emakistrengthen.field.ea_attributes': 'EA Attributes',
  'emakistrengthen.field.es_skills': 'ES Skills',
  'emakistrengthen.field.name_actions': 'Name Actions',
  'emakistrengthen.field.lore_actions': 'Lore Actions',
  'emakistrengthen.field.stars': 'Star Stages',
  'emakistrengthen.field.children': 'Child Branches'
});

type RouteMaterial = { item: string; amount: number; optional: boolean; protection: boolean; temperBoost: number };
type RouteNode = { id: string; star: number; branchPath: string; branchId: string; branchName: string; stageName: string; successRate: number; materials: RouteMaterial[]; statsDelta: Record<string, number>; cumulativeStats: Record<string, number>; cumulativeAttributes: Record<string, number>; skillIds: string[]; hasSuccessActions: boolean; hasFailureActions: boolean };
type RouteEdge = { from: string; to: string; type: string; label: string };
type RoutePreviewResult = { recipeId: string; displayName: string; branching: boolean; maxStar: number; nodes: RouteNode[]; edges: RouteEdge[]; warnings?: { type: string; message: string }[] };

function StrengthenRoutePreview({ api, data }: ConfigPreviewProps) {
  const recipeId = typeof data?.id === 'string' ? data.id : '';
  const [result, setResult] = useState<RoutePreviewResult | null>(null);
  const [selectedId, setSelectedId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const selected = useMemo(() => result?.nodes.find(node => node.id === selectedId) ?? result?.nodes[0], [result, selectedId]);

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await api.pluginApi('strengthen', 'route-preview', { recipeId });
      const normalized: RoutePreviewResult = {
        recipeId: String(response.recipeId ?? recipeId),
        displayName: String(response.displayName ?? ''),
        branching: Boolean(response.branching),
        maxStar: Number(response.maxStar ?? 0),
        nodes: Array.isArray(response.nodes) ? response.nodes : [],
        edges: Array.isArray(response.edges) ? response.edges : [],
        warnings: Array.isArray(response.warnings) ? response.warnings : []
      };
      setResult(normalized);
      setSelectedId(normalized.nodes[0]?.id ?? '');
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : String(exception));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (recipeId) void load();
  }, [recipeId]);

  const exportJson = () => {
    if (!result) return;
    const blob = new Blob([JSON.stringify(result, null, 2)], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${result.recipeId || 'strengthen-route'}-preview.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return <section style={routeCardStyle}>
    <div style={routeHeaderStyle}>
      <div>
        <h3 style={{ margin: 0 }}>{copy('强化路线蓝图', 'Strengthen route blueprint')}</h3>
        <p style={routeHintStyle}>{copy('基于服务器已加载配方生成，只读预览路线、材料、成功率与属性累计。', 'Generated from the loaded server recipe; read-only preview of route, materials, success rates, and cumulative stats.')}</p>
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <button type="button" onClick={load} disabled={loading || !recipeId} style={routeButtonStyle}>{loading ? copy('加载中...', 'Loading...') : copy('刷新', 'Refresh')}</button>
        <button type="button" onClick={exportJson} disabled={!result} style={routeSecondaryButtonStyle}>{copy('导出 JSON', 'Export JSON')}</button>
      </div>
    </div>
    {!recipeId ? <div style={routeEmptyStyle}>{copy('当前文件未配置 id，无法匹配运行时配方。', 'This file has no id, so no loaded recipe can be matched.')}</div> : null}
    {error ? <div style={routeErrorStyle}>{error}</div> : null}
    {result ? <>
      <div style={routeSummaryStyle}>
        <span>{copy('配方', 'Recipe')}: <strong>{result.displayName || result.recipeId}</strong></span>
        <span>{copy('节点', 'Nodes')}: <strong>{result.nodes.length}</strong></span>
        <span>{copy('连线', 'Edges')}: <strong>{result.edges.length}</strong></span>
        <span>{copy('分支', 'Branching')}: <strong>{result.branching ? copy('是', 'Yes') : copy('否', 'No')}</strong></span>
      </div>
      {(result.warnings ?? []).length ? <div style={routeWarningStyle}>{result.warnings?.map(warning => warning.message).join(' / ')}</div> : null}
      <RouteSvg nodes={result.nodes} edges={result.edges} selectedId={selected?.id ?? ''} onSelect={setSelectedId} />
      <div style={routeDetailGridStyle}>
        <RouteTable nodes={result.nodes} selectedId={selected?.id ?? ''} onSelect={setSelectedId} />
        {selected ? <RouteNodeDetail node={selected} /> : null}
      </div>
    </> : <div style={routeEmptyStyle}>{copy('暂无路线数据。', 'No route data.')}</div>}
  </section>;
}

function RouteSvg({ nodes, edges, selectedId, onSelect }: { nodes: RouteNode[]; edges: RouteEdge[]; selectedId: string; onSelect: (id: string) => void }) {
  const width = 920;
  const height = Math.max(240, 90 + nodes.length * 18);
  const positions = routePositions(nodes, width, height);
  return <svg viewBox={`0 0 ${width} ${height}`} style={routeSvgStyle} role="img" aria-label={copy('强化路线图', 'Strengthen route graph')}>
    {edges.map((edge, index) => {
      const from = positions.get(edge.from);
      const to = positions.get(edge.to);
      if (!from || !to) return null;
      const branch = edge.type === 'branch';
      return <g key={`${edge.from}-${edge.to}-${index}`}>
        <line x1={from.x} y1={from.y} x2={to.x} y2={to.y} stroke={branch ? '#f59e0b' : 'rgba(148,163,184,.55)'} strokeWidth={branch ? 3 : 2} strokeDasharray={branch ? '5 4' : undefined} />
        {edge.label ? <text x={(from.x + to.x) / 2} y={(from.y + to.y) / 2 - 6} fill="#fbbf24" fontSize="12" textAnchor="middle">{stripMini(edge.label)}</text> : null}
      </g>;
    })}
    {nodes.map(node => {
      const point = positions.get(node.id)!;
      const selected = node.id === selectedId;
      return <g key={node.id} onClick={() => onSelect(node.id)} style={{ cursor: 'pointer' }}>
        <circle cx={point.x} cy={point.y} r={selected ? 18 : 15} fill={selected ? '#60a5fa' : '#1e293b'} stroke={node.branchPath ? '#f59e0b' : '#93c5fd'} strokeWidth={selected ? 3 : 2} />
        <text x={point.x} y={point.y + 4} textAnchor="middle" fontSize="12" fill={selected ? '#06111f' : '#e2e8f0'} fontWeight={700}>{node.star}</text>
        <text x={point.x} y={point.y + 34} textAnchor="middle" fontSize="11" fill="rgba(226,232,240,.78)">{shortBranch(node.branchPath || 'root')}</text>
      </g>;
    })}
  </svg>;
}

function RouteTable({ nodes, selectedId, onSelect }: { nodes: RouteNode[]; selectedId: string; onSelect: (id: string) => void }) {
  return <div style={{ overflowX: 'auto' }}><table style={routeTableStyle}>
    <thead><tr><th>★</th><th>{copy('分支', 'Branch')}</th><th>{copy('成功率', 'Rate')}</th><th>{copy('材料', 'Materials')}</th><th>{copy('增量', 'Delta')}</th></tr></thead>
    <tbody>{nodes.map(node => <tr key={node.id} onClick={() => onSelect(node.id)} style={{ background: node.id === selectedId ? 'rgba(96,165,250,.16)' : undefined, cursor: 'pointer' }}>
      <td>{node.star}</td><td>{node.branchPath || 'root'}</td><td>{formatNumber(node.successRate)}%</td><td>{node.materials.map(material => `${material.item} x${material.amount}`).join(', ')}</td><td>{kvSummary(node.statsDelta)}</td>
    </tr>)}</tbody>
  </table></div>;
}

function RouteNodeDetail({ node }: { node: RouteNode }) {
  return <aside style={routeDetailStyle}>
    <h4 style={{ margin: '0 0 8px' }}>★{node.star} · {node.stageName || node.branchName || node.branchId}</h4>
    <p style={routeHintStyle}>{copy('分支路径', 'Branch path')}: <code>{node.branchPath || 'root'}</code></p>
    <p>{copy('成功率', 'Success rate')}: <strong>{formatNumber(node.successRate)}%</strong></p>
    <p>{copy('成功动作', 'Success actions')}: {node.hasSuccessActions ? copy('有', 'Yes') : copy('无', 'No')} · {copy('失败动作', 'Failure actions')}: {node.hasFailureActions ? copy('有', 'Yes') : copy('无', 'No')}</p>
    <h5>{copy('材料', 'Materials')}</h5><ul>{node.materials.map((material, index) => <li key={index}><code>{material.item}</code> x{material.amount}{material.optional ? ` · ${copy('可选', 'Optional')}` : ''}{material.protection ? ` · ${copy('保护', 'Protection')}` : ''}{material.temperBoost ? ` · +${material.temperBoost} ${copy('锻印', 'Temper')}` : ''}</li>)}</ul>
    <h5>{copy('本级变量增量', 'Stage stat delta')}</h5><pre style={routePreStyle}>{JSON.stringify(node.statsDelta, null, 2)}</pre>
    <h5>{copy('累计变量', 'Cumulative stats')}</h5><pre style={routePreStyle}>{JSON.stringify(node.cumulativeStats, null, 2)}</pre>
    <h5>{copy('累计 EA 属性', 'Cumulative EA attributes')}</h5><pre style={routePreStyle}>{JSON.stringify(node.cumulativeAttributes, null, 2)}</pre>
    {node.skillIds.length ? <><h5>{copy('技能', 'Skills')}</h5><p>{node.skillIds.join(', ')}</p></> : null}
  </aside>;
}

function routePositions(nodes: RouteNode[], width: number, height: number) {
  const byStar = new Map<number, RouteNode[]>();
  nodes.forEach(node => byStar.set(node.star, [...(byStar.get(node.star) ?? []), node]));
  const stars = [...byStar.keys()].sort((a, b) => a - b);
  const positions = new Map<string, { x: number; y: number }>();
  stars.forEach((star, starIndex) => {
    const group = byStar.get(star) ?? [];
    group.forEach((node, nodeIndex) => positions.set(node.id, {
      x: 52 + (starIndex / Math.max(1, stars.length - 1)) * (width - 104),
      y: 58 + ((nodeIndex + 1) / (group.length + 1)) * (height - 116)
    }));
  });
  return positions;
}

function kvSummary(value: Record<string, number>) {
  const entries = Object.entries(value ?? {});
  return entries.length ? entries.slice(0, 3).map(([key, number]) => `${key}+${formatNumber(number)}`).join(', ') : '-';
}

function shortBranch(path: string) {
  return path.length > 16 ? `…${path.slice(-15)}` : path;
}

function stripMini(value: string) {
  return String(value ?? '').replace(/<[^>]+>/g, '');
}

function formatNumber(value: number) {
  return Number.isFinite(value) ? value.toLocaleString(undefined, { maximumFractionDigits: 2 }) : '-';
}

const routeCardStyle: React.CSSProperties = { border: '1px solid rgba(167,139,250,.28)', borderRadius: 16, padding: 16, marginTop: 16, background: 'linear-gradient(180deg, rgba(30,27,75,.72), rgba(15,23,42,.46))' };
const routeHeaderStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' };
const routeHintStyle: React.CSSProperties = { margin: '6px 0 0', color: 'rgba(226,232,240,.72)', fontSize: 13 };
const routeButtonStyle: React.CSSProperties = { border: 0, borderRadius: 10, padding: '9px 13px', background: '#a78bfa', color: '#120c2d', fontWeight: 700, cursor: 'pointer' };
const routeSecondaryButtonStyle: React.CSSProperties = { ...routeButtonStyle, background: 'rgba(167,139,250,.16)', color: '#ddd6fe', border: '1px solid rgba(167,139,250,.35)' };
const routeSummaryStyle: React.CSSProperties = { display: 'flex', gap: 14, flexWrap: 'wrap', marginTop: 12, color: 'rgba(226,232,240,.86)' };
const routeSvgStyle: React.CSSProperties = { width: '100%', marginTop: 16, background: 'rgba(2,6,23,.28)', borderRadius: 12 };
const routeDetailGridStyle: React.CSSProperties = { display: 'grid', gridTemplateColumns: 'minmax(360px, 1.2fr) minmax(280px, .8fr)', gap: 14, marginTop: 14 };
const routeTableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: 13 };
const routeDetailStyle: React.CSSProperties = { border: '1px solid rgba(148,163,184,.2)', borderRadius: 12, padding: 12, background: 'rgba(15,23,42,.45)' };
const routePreStyle: React.CSSProperties = { margin: 0, whiteSpace: 'pre-wrap', background: 'rgba(2,6,23,.4)', borderRadius: 8, padding: 8, fontSize: 12 };
const routeErrorStyle: React.CSSProperties = { marginTop: 12, color: '#fecaca', background: 'rgba(127,29,29,.25)', border: '1px solid rgba(248,113,113,.35)', borderRadius: 10, padding: 10 };
const routeWarningStyle: React.CSSProperties = { marginTop: 12, color: '#fde68a', background: 'rgba(120,53,15,.22)', border: '1px solid rgba(245,158,11,.35)', borderRadius: 10, padding: 10 };
const routeEmptyStyle: React.CSSProperties = { marginTop: 12, color: 'rgba(203,213,225,.75)' };

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
});

registerConfigPreview({ moduleId: MODULE, kind: 'CONFIG', pathPattern: 'recipes/**/*.yml', component: StrengthenRoutePreview, label: copy('强化路线蓝图', 'Strengthen route blueprint'), priority: 20 });

registerPluginGuiEditor({
  moduleId: MODULE,
  editorId: 'emakistrengthen:gui',
  label: copy('强化 GUI', 'Strengthen GUI'),
  fields: [
    ['type', '槽位类型', '强化业务槽位语义。可选预设值，材料输入槽可用 material_input_0/1/2… 自定义。', 'enum', { options: ['target_item', 'preview_display', 'temper_display', 'confirm', 'material_input_0', 'material_input_1', 'material_input_2'], optionLabelPrefix: 'slotType' }],
    ['target_item', '目标物品', '放入待强化物品的槽位。', 'text'],
    ['material', '强化材料', '放入强化材料的槽位。', 'text'],
    ['success_preview', '成功率预览', '显示当前强化成功率与目标星级的槽位。', 'text'],
    ['confirm', '确认按钮', '执行强化操作的按钮槽位。', 'text']
  ]
});

