import React, { useEffect, useMemo, useState } from 'react';
import { getLocale, registerConfigCreateTemplate, registerConfigMetaFields, registerConfigPreview, registerConfigRuleFields, registerModuleLocale, registerPluginConfig, registerPluginGuiEditor, type ConfigMetaFieldEntry, type ConfigPreviewProps, type ConfigRuleFieldEntry } from 'emaki-web-console';

const MODULE = 'EmakiLevel';
const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;

registerModuleLocale(MODULE, 'zh-CN', {
  'emakilevel.module.name': 'Level',
  'emakilevel.module.summary': '多等级类型、经验来源与成长配置',
  'emakilevel.file.config.title': '主配置',
  'emakilevel.file.config.comment': '等级系统主配置，包含语言、PDC、属性桥接、防刷和 MythicMobs 接入。',
  'emakilevel.file.requirements.title': '升级需求',
  'emakilevel.file.requirements.comment': '全局、分组与类型级经验需求公式。',
  'emakilevel.file.types.title': '等级类型',
  'emakilevel.file.types.comment': '主等级、战斗、挖掘、烹饪、锻造等等级类型。',
  'emakilevel.file.sources.title': '经验来源',
  'emakilevel.file.sources.comment': 'Bukkit 与 MythicMobs 事件来源的经验规则。',
  'emakilevel.file.gui.title': '等级 GUI',
  'emakilevel.file.gui.comment': '等级 GUI 模板配置。',
  'emakilevel.field.id': 'ID',
  'emakilevel.field.enabled': '启用',
  'emakilevel.field.display_name': '显示名称',
  'emakilevel.field.description': '描述',
  'emakilevel.field.primary': '主等级',
  'emakilevel.field.start_level': '起始等级',
  'emakilevel.field.max_level': '最高等级',
  'emakilevel.field.requirement': '升级需求',
  'emakilevel.field.upgrade': '升级配置',
  'emakilevel.field.actions': '动作',
  'emakilevel.field.attributes': '属性贡献',
  'emakilevel.field.sources': '来源规则',
  'emakilevel.field.rules': '匹配规则',
  'emakilevel.field.exp_formula': '经验公式',
  'emakilevel.field.type': '等级类型',
  'emakilevel.option.trigger.entity_kill': '实体击杀',
  'emakilevel.option.trigger.mythic_mob_kill': 'Mythic 击杀',
  'emakilevel.option.trigger.block_break': '方块破坏',
  'emakilevel.option.trigger.crop_harvest': '作物收获',
  'emakilevel.option.trigger.player_fish': '钓鱼',
  'emakilevel.option.trigger.craft_item': '合成',
  'emakilevel.option.trigger.brew_complete': '炼药完成',
  'emakilevel.option.trigger.furnace_extract': '冶炼提取',
  'emakilevel.option.trigger.entity_tame': '驯养'
});

registerModuleLocale(MODULE, 'en-US', {
  'emakilevel.module.name': 'Level',
  'emakilevel.module.summary': 'Level types, experience sources, and progression configuration',
  'emakilevel.file.config.title': 'Main config',
  'emakilevel.file.config.comment': 'Main level system config: language, PDC, attribute bridge, anti-abuse, and MythicMobs integration.',
  'emakilevel.file.requirements.title': 'Requirements',
  'emakilevel.file.requirements.comment': 'Global, group, and type-specific experience requirements.',
  'emakilevel.file.types.title': 'Level types',
  'emakilevel.file.types.comment': 'Main, combat, mining, cooking, forging, and other level types.',
  'emakilevel.file.sources.title': 'Experience sources',
  'emakilevel.file.sources.comment': 'Experience rules for Bukkit and MythicMobs events.',
  'emakilevel.file.gui.title': 'Level GUI',
  'emakilevel.file.gui.comment': 'Level GUI template configuration.',
  'emakilevel.field.id': 'ID',
  'emakilevel.field.enabled': 'Enabled',
  'emakilevel.field.display_name': 'Display name',
  'emakilevel.field.description': 'Description',
  'emakilevel.field.primary': 'Primary',
  'emakilevel.field.start_level': 'Start level',
  'emakilevel.field.max_level': 'Max level',
  'emakilevel.field.requirement': 'Requirement',
  'emakilevel.field.upgrade': 'Upgrade',
  'emakilevel.field.actions': 'Actions',
  'emakilevel.field.attributes': 'Attribute contributions',
  'emakilevel.field.sources': 'Source rules',
  'emakilevel.field.rules': 'Match rules',
  'emakilevel.field.exp_formula': 'Exp formula',
  'emakilevel.field.type': 'Level type',
  'emakilevel.option.trigger.entity_kill': 'Entity kill',
  'emakilevel.option.trigger.mythic_mob_kill': 'Mythic kill',
  'emakilevel.option.trigger.block_break': 'Block break',
  'emakilevel.option.trigger.crop_harvest': 'Crop harvest',
  'emakilevel.option.trigger.player_fish': 'Fishing',
  'emakilevel.option.trigger.craft_item': 'Craft item',
  'emakilevel.option.trigger.brew_complete': 'Brew complete',
  'emakilevel.option.trigger.furnace_extract': 'Furnace extract',
  'emakilevel.option.trigger.entity_tame': 'Taming'
});

type CurveWarning = { type: string; message: string; targetLevel?: number };
type CurvePoint = { targetLevel: number; requiredExp: number; totalExp: number; growthRate: number; source: string; warnings?: CurveWarning[] };
type Curve = { type: string; displayName: string; startLevel: number; maxLevel: number; fromLevel: number; toLevel: number; points: CurvePoint[]; warnings?: CurveWarning[] };
type CurveResult = { curves: Curve[]; limits?: { maxPointsPerType?: number }; warnings?: CurveWarning[] };
type CurveMetric = 'requiredExp' | 'totalExp' | 'growthRate';

const mainConfigFields: ConfigMetaFieldEntry[] = [
  ['version', copy('配置版本', 'Config version'), copy('默认配置结构版本。', 'Default config schema version.'), 'text'],
  ['language', copy('语言', 'Language'), copy('语言文件 ID，对应 lang/<language>.yml。', 'Language file id under lang/<language>.yml.'), 'text'],
  ['release_default_data', copy('释放默认数据', 'Release default data'), copy('缺失默认 types/sources/gui 时是否释放内置文件。', 'Whether bundled types/sources/gui files should be released when missing.'), 'boolean'],
  ['primary_type', copy('主等级类型', 'Primary type'), copy('默认展示和 Action 未指定类型时使用的等级类型。', 'Default level type for display and actions without type.'), 'text'],
  ['level.default_start_level', copy('默认起始等级', 'Default start level'), copy('类型未配置 start_level 时使用。', 'Used when a type does not define start_level.'), 'number'],
  ['level.default_max_level', copy('默认最高等级', 'Default max level'), copy('类型未配置 max_level 时使用。', 'Used when a type does not define max_level.'), 'number'],
  ['level.max_auto_upgrade_steps', copy('自动升级步数', 'Auto upgrade steps'), copy('一次经验变更最多连升多少级。', 'Maximum auto-upgrade steps in one exp operation.'), 'number'],
  ['pdc.enabled', copy('同步 PDC', 'Sync PDC'), copy('是否向玩家实体写入等级数据。', 'Whether to write level data to player PDC.'), 'boolean'],
  ['pdc.namespace', copy('PDC 命名空间', 'PDC namespace'), copy('等级 PDC 使用的命名空间。', 'Namespace used by level PDC keys.'), 'text'],
  ['attribute.enabled', copy('属性桥接', 'Attribute bridge'), copy('是否向 EmakiAttribute 贡献等级属性。', 'Whether levels contribute EmakiAttribute attributes.'), 'boolean'],
  ['attribute.provider_id', copy('属性来源 ID', 'Attribute provider id'), copy('注册到 EmakiAttribute 的 Provider ID。', 'Provider id registered into EmakiAttribute.'), 'text'],
  ['mythicmobs.enabled', copy('MythicMobs 接入', 'MythicMobs integration'), copy('是否启用 MythicMobs 击杀和 Drop 接入。', 'Whether MythicMobs kill and drop integration is enabled.'), 'boolean'],
  ['mythicmobs.drops.enabled', copy('Mythic Drops', 'Mythic drops'), copy('是否注册 emakilevel_exp/elv_exp 自定义 Drop。', 'Whether to register emakilevel_exp/elv_exp custom drops.'), 'boolean'],
  ['mythicmobs.drops.names', copy('Drop 名称', 'Drop names'), copy('可识别的 MythicMobs 自定义 Drop 名称。', 'Recognized MythicMobs custom drop names.'), 'stringList'],
  ['anti_abuse.placed_block_tracking', copy('记录放置方块', 'Track placed blocks'), copy('记录玩家放置方块用于破坏经验防刷。', 'Track player-placed blocks for break-exp anti-abuse.'), 'boolean'],
  ['anti_abuse.placed_block_exp', copy('放置方块给经验', 'Placed blocks grant exp'), copy('关闭后玩家放置再破坏的方块不给经验。', 'When false, player-placed blocks do not grant break exp.'), 'boolean']
];

const typeFields: ConfigMetaFieldEntry[] = [
  ['id', copy('类型 ID', 'Type id'), copy('等级类型唯一 ID，例如 combat。', 'Unique level type id, e.g. combat.'), 'text'],
  ['enabled', copy('启用', 'Enabled'), copy('是否启用该等级类型。', 'Whether this level type is enabled.'), 'boolean'],
  ['display_name', copy('显示名称', 'Display name'), copy('MiniMessage 显示名。', 'MiniMessage display name.'), 'text'],
  ['description', copy('描述', 'Description'), copy('等级类型描述。', 'Level type description.'), 'stringList'],
  ['primary', copy('主等级', 'Primary'), copy('是否作为主等级。', 'Whether this is the primary type.'), 'boolean'],
  ['start_level', copy('起始等级', 'Start level'), copy('玩家初始等级。', 'Initial player level.'), 'number'],
  ['max_level', copy('最高等级', 'Max level'), copy('玩家可达到的最高等级。', 'Maximum reachable level.'), 'number'],
  ['requirement.group', copy('需求分组', 'Requirement group'), copy('requirements.yml 中的需求分组。', 'Requirement group in requirements.yml.'), 'text'],
  ['requirement.formula', copy('类型公式', 'Type formula'), copy('覆盖分组/全局公式的类型专属公式。', 'Type-specific formula overriding group/global formula.'), 'text'],
  ['upgrade.enabled', copy('允许升级', 'Upgrade enabled'), copy('是否允许该类型升级。', 'Whether this type can level up.'), 'boolean'],
  ['upgrade.auto_upgrade', copy('自动升级', 'Auto upgrade'), copy('获得经验后是否自动升级。', 'Whether exp gain triggers auto-upgrade.'), 'boolean'],
  ['upgrade.manual_upgrade', copy('手动升级', 'Manual upgrade'), copy('是否允许 /elv levelup。', 'Whether /elv levelup is allowed.'), 'boolean'],
  ['upgrade.actions.gain', copy('获得经验动作', 'Exp gain actions'), copy('获得经验后执行的 CoreLib Action。', 'CoreLib actions executed after exp gain.'), 'stringList'],
  ['upgrade.actions.success', copy('升级成功动作', 'Success actions'), copy('升级成功后执行的 CoreLib Action。', 'CoreLib actions executed after successful level-up.'), 'stringList'],
  ['upgrade.actions.failure', copy('升级失败动作', 'Failure actions'), copy('升级失败后执行的 CoreLib Action。', 'CoreLib actions executed after failed level-up.'), 'stringList'],
  ['attributes.enabled', copy('启用属性', 'Attributes enabled'), copy('是否将此类型等级转为属性贡献。', 'Whether this type contributes attributes.'), 'boolean'],
  ['attributes.values', copy('属性公式', 'Attribute formulas'), copy('属性 ID 到表达式的映射。', 'Map of attribute id to expression.'), 'object', { creatableChildren: true }]
];

const sourceFields: ConfigMetaFieldEntry[] = [
  ['id', copy('来源文件 ID', 'Source file id'), copy('来源配置文件标识。', 'Source config file id.'), 'text'],
  ['enabled', copy('启用', 'Enabled'), copy('是否启用此来源文件。', 'Whether this source file is enabled.'), 'boolean'],
  ['sources', copy('经验来源', 'Experience sources'), copy('按来源 ID 配置的经验规则。', 'Experience source rules keyed by source id.'), 'object', { creatableChildren: true }]
];

const dynamicFields: Record<string, ConfigRuleFieldEntry> = {
  enabled: [copy('启用', 'Enabled'), copy('是否启用该节点。', 'Whether this node is enabled.'), 'boolean'],
  type: [copy('等级类型', 'Level type'), copy('经验写入的等级类型 ID。', 'Target level type id.'), 'text'],
  trigger: [copy('触发器', 'Trigger'), copy('经验来源触发器。', 'Experience source trigger.'), 'enum', { options: ['entity_kill', 'mythic_mob_kill', 'block_break', 'crop_harvest', 'player_fish', 'craft_item', 'brew_complete', 'furnace_extract', 'entity_tame'], optionLabelPrefix: 'trigger' }],
  exp_formula: [copy('经验公式', 'Exp formula'), copy('使用 %变量% 计算本次经验。', 'Formula using %variables% to calculate exp.'), 'text'],
  blocks: [copy('方块', 'Blocks'), copy('匹配 Bukkit Material 名称。', 'Matching Bukkit material names.'), 'stringList'],
  entity_types: [copy('实体类型', 'Entity types'), copy('匹配 Bukkit EntityType 名称。', 'Matching Bukkit EntityType names.'), 'stringList'],
  mob_ids: [copy('MythicMob ID', 'MythicMob IDs'), copy('匹配 MythicMobs 内部 ID。', 'Matching MythicMobs internal ids.'), 'stringList'],
  result_item_sources: [copy('结果物品源', 'Result item sources'), copy('匹配 CoreLib ItemSource。', 'Matching CoreLib ItemSource values.'), 'stringList'],
  states: [copy('状态', 'States'), copy('事件状态名称。', 'Event state names.'), 'stringList'],
  potion_types: [copy('药水类型', 'Potion types'), copy('药水类型名称。', 'Potion type names.'), 'stringList']
};

function LevelCurvePreview({ api, file, data }: ConfigPreviewProps) {
  const fileTypeId = typeof data?.id === 'string' ? data.id : '';
  const [typeInput, setTypeInput] = useState(file.path.startsWith('types/') ? fileTypeId : '');
  const [fromLevel, setFromLevel] = useState(1);
  const [toLevel, setToLevel] = useState(80);
  const [metric, setMetric] = useState<CurveMetric>('requiredExp');
  const [result, setResult] = useState<CurveResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const selectedTypes = useMemo(() => typeInput.split(',').map(value => value.trim()).filter(Boolean), [typeInput]);
  const firstCurve = result?.curves?.[0];

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await api.pluginApi('level', 'curve', { types: selectedTypes, fromLevel, toLevel });
      setResult({ curves: Array.isArray(response.curves) ? response.curves : [], limits: response.limits, warnings: response.warnings });
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : String(exception));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const exportCsv = () => {
    if (!result?.curves?.length) return;
    const rows = ['type,target_level,required_exp,total_exp,growth_rate,source,warnings'];
    result.curves.forEach(curve => curve.points.forEach(point => rows.push([
      curve.type,
      point.targetLevel,
      point.requiredExp,
      point.totalExp,
      point.growthRate,
      point.source,
      (point.warnings ?? []).map(warning => warning.type).join('|')
    ].map(csvCell).join(','))));
    const blob = new Blob([rows.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'emakilevel-curve.csv';
    link.click();
    URL.revokeObjectURL(url);
  };

  return <section style={cardStyle}>
    <div style={headerStyle}>
      <div>
        <h3 style={{ margin: 0 }}>{copy('等级曲线', 'Level curve')}</h3>
        <p style={hintStyle}>{copy('由服务端基于真实 RequirementService 计算，前端只负责展示。', 'Calculated by the server through the real RequirementService; the frontend only visualizes it.')}</p>
      </div>
      <button type="button" onClick={load} disabled={loading} style={buttonStyle}>{loading ? copy('加载中...', 'Loading...') : copy('刷新曲线', 'Refresh')}</button>
    </div>
    <div style={controlGridStyle}>
      <label style={labelStyle}>{copy('等级类型，逗号分隔', 'Level types, comma separated')}<input value={typeInput} onChange={event => setTypeInput(event.target.value)} placeholder={copy('留空显示前 6 个启用类型', 'Empty = first 6 enabled types')} style={inputStyle} /></label>
      <label style={labelStyle}>{copy('起始目标等级', 'From target level')}<input type="number" min={1} value={fromLevel} onChange={event => setFromLevel(Number(event.target.value) || 1)} style={inputStyle} /></label>
      <label style={labelStyle}>{copy('结束目标等级', 'To target level')}<input type="number" min={1} value={toLevel} onChange={event => setToLevel(Number(event.target.value) || 1)} style={inputStyle} /></label>
      <label style={labelStyle}>{copy('图表指标', 'Metric')}<select value={metric} onChange={event => setMetric(event.target.value as CurveMetric)} style={inputStyle}>
        <option value="requiredExp">{copy('单级需求经验', 'Required exp')}</option>
        <option value="totalExp">{copy('累计总经验', 'Total exp')}</option>
        <option value="growthRate">{copy('增长率', 'Growth rate')}</option>
      </select></label>
    </div>
    {error ? <div style={errorStyle}>{error}</div> : null}
    {result?.curves?.length ? <>
      <LevelCurveSvg curves={result.curves} metric={metric} />
      <div style={summaryStyle}>
        <span>{copy('曲线数量', 'Curves')}: <strong>{result.curves.length}</strong></span>
        <span>{copy('单类型最多点数', 'Max points/type')}: <strong>{result.limits?.maxPointsPerType ?? '-'}</strong></span>
        <button type="button" onClick={exportCsv} style={secondaryButtonStyle}>{copy('导出 CSV', 'Export CSV')}</button>
      </div>
      {firstCurve ? <CurveTable curve={firstCurve} /> : null}
    </> : <div style={emptyStyle}>{copy('暂无曲线数据。', 'No curve data.')}</div>}
  </section>;
}

function LevelCurveSvg({ curves, metric }: { curves: Curve[]; metric: CurveMetric }) {
  const width = 760;
  const height = 260;
  const padding = 32;
  const points = curves.flatMap(curve => curve.points.map(point => ({ curve, point, value: pointMetric(point, metric) })));
  const minLevel = Math.min(...points.map(entry => entry.point.targetLevel));
  const maxLevel = Math.max(...points.map(entry => entry.point.targetLevel));
  const maxValue = Math.max(1, ...points.map(entry => entry.value));
  const x = (level: number) => padding + ((level - minLevel) / Math.max(1, maxLevel - minLevel)) * (width - padding * 2);
  const y = (value: number) => height - padding - (value / maxValue) * (height - padding * 2);
  return <svg viewBox={`0 0 ${width} ${height}`} style={svgStyle} role="img" aria-label={copy('等级曲线图', 'Level curve chart')}>
    <line x1={padding} y1={height - padding} x2={width - padding} y2={height - padding} stroke="rgba(148,163,184,.45)" />
    <line x1={padding} y1={padding} x2={padding} y2={height - padding} stroke="rgba(148,163,184,.45)" />
    {curves.map((curve, index) => {
      const d = curve.points.map((point, pointIndex) => `${pointIndex === 0 ? 'M' : 'L'} ${x(point.targetLevel)} ${y(pointMetric(point, metric))}`).join(' ');
      const color = palette[index % palette.length];
      return <g key={curve.type}>
        <path d={d} fill="none" stroke={color} strokeWidth="3" />
        {curve.points.map(point => <circle key={`${curve.type}-${point.targetLevel}`} cx={x(point.targetLevel)} cy={y(pointMetric(point, metric))} r={(point.warnings?.length ?? 0) > 0 ? 4 : 2.5} fill={(point.warnings?.length ?? 0) > 0 ? '#f97316' : color}><title>{`${curve.type} Lv.${point.targetLevel}: ${formatNumber(pointMetric(point, metric))}`}</title></circle>)}
      </g>;
    })}
  </svg>;
}

function CurveTable({ curve }: { curve: Curve }) {
  return <div style={{ overflowX: 'auto' }}>
    <h4 style={{ margin: '12px 0 8px' }}>{curve.displayName || curve.type} · {curve.type}</h4>
    <table style={tableStyle}>
      <thead><tr><th>Lv</th><th>{copy('需求', 'Required')}</th><th>{copy('累计', 'Total')}</th><th>{copy('增长率', 'Growth')}</th><th>{copy('来源', 'Source')}</th><th>{copy('警告', 'Warnings')}</th></tr></thead>
      <tbody>{curve.points.slice(0, 80).map(point => <tr key={point.targetLevel}>
        <td>{point.targetLevel}</td>
        <td>{formatNumber(point.requiredExp)}</td>
        <td>{formatNumber(point.totalExp)}</td>
        <td>{formatPercent(point.growthRate)}</td>
        <td><code>{point.source}</code></td>
        <td>{(point.warnings ?? []).map(warning => warning.type).join(', ')}</td>
      </tr>)}</tbody>
    </table>
  </div>;
}

function pointMetric(point: CurvePoint, metric: CurveMetric): number {
  if (metric === 'totalExp') return point.totalExp;
  if (metric === 'growthRate') return Math.max(0, point.growthRate * 100);
  return point.requiredExp;
}

function csvCell(value: unknown): string {
  const text = String(value ?? '');
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function formatNumber(value: number): string {
  return Number.isFinite(value) ? value.toLocaleString(undefined, { maximumFractionDigits: 2 }) : '-';
}

function formatPercent(value: number): string {
  return Number.isFinite(value) ? `${(value * 100).toFixed(2)}%` : '-';
}

const palette = ['#60a5fa', '#a78bfa', '#34d399', '#f59e0b', '#f472b6', '#22d3ee'];
const cardStyle: React.CSSProperties = { border: '1px solid rgba(148,163,184,.24)', borderRadius: 16, padding: 16, marginTop: 16, background: 'linear-gradient(180deg, rgba(15,23,42,.72), rgba(15,23,42,.44))' };
const headerStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' };
const hintStyle: React.CSSProperties = { margin: '6px 0 0', color: 'rgba(203,213,225,.78)', fontSize: 13 };
const controlGridStyle: React.CSSProperties = { display: 'grid', gridTemplateColumns: 'minmax(220px,2fr) repeat(3,minmax(120px,1fr))', gap: 10, marginTop: 14 };
const labelStyle: React.CSSProperties = { display: 'grid', gap: 6, fontSize: 12, color: 'rgba(226,232,240,.82)' };
const inputStyle: React.CSSProperties = { border: '1px solid rgba(148,163,184,.35)', borderRadius: 10, background: 'rgba(2,6,23,.48)', color: 'inherit', padding: '8px 10px' };
const buttonStyle: React.CSSProperties = { border: 0, borderRadius: 10, padding: '9px 13px', background: '#60a5fa', color: '#07111f', fontWeight: 700, cursor: 'pointer' };
const secondaryButtonStyle: React.CSSProperties = { ...buttonStyle, background: 'rgba(96,165,250,.18)', color: '#bfdbfe', border: '1px solid rgba(96,165,250,.35)' };
const errorStyle: React.CSSProperties = { marginTop: 12, color: '#fecaca', background: 'rgba(127,29,29,.25)', border: '1px solid rgba(248,113,113,.35)', borderRadius: 10, padding: 10 };
const emptyStyle: React.CSSProperties = { marginTop: 12, color: 'rgba(203,213,225,.75)' };
const svgStyle: React.CSSProperties = { width: '100%', marginTop: 16, background: 'rgba(2,6,23,.28)', borderRadius: 12 };
const summaryStyle: React.CSSProperties = { display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginTop: 12, color: 'rgba(226,232,240,.86)' };
const tableStyle: React.CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: 13 };

registerPluginGuiEditor({
  moduleId: MODULE,
  editorId: 'emakilevel:gui',
  label: copy('等级 GUI', 'Level GUI'),
  fields: [
    ['slots', copy('槽位', 'Slots'), copy('GUI 中所有可渲染槽位配置。', 'Renderable slots in this GUI.'), 'object'],
    ['type', copy('槽位类型', 'Slot type'), copy('等级 GUI 槽位语义，可选预设或自定义。', 'Level GUI slot type; preset or custom values are allowed.'), 'enum', { options: ['filler', 'level_type', 'type_info', 'progress', 'levelup', 'next_page', 'previous_page', 'close'], optionLabelPrefix: 'slotType' }],
    ['item', copy('物品来源', 'Item source'), copy('CoreLib ItemSource 字符串。', 'CoreLib ItemSource string.'), 'text'],
    ['display_name', copy('显示名称', 'Display name'), copy('槽位显示名称。', 'Slot display name.'), 'text'],
    ['lore', copy('Lore', 'Lore'), copy('槽位 Lore。', 'Slot lore.'), 'stringList']
  ]
});

registerConfigMetaFields(MODULE, mainConfigFields);
registerPluginConfig({
  moduleId: MODULE,
  fileSchemas: [
    { pathPrefix: 'types/', fields: typeFields },
    { pathPrefix: 'sources/', fields: sourceFields }
  ]
});
registerConfigRuleFields(MODULE, dynamicFields);
registerConfigPreview({ moduleId: MODULE, kind: 'CONFIG', pathPattern: 'requirements.yml', component: LevelCurvePreview, label: copy('等级曲线', 'Level curve'), priority: 20 });
registerConfigPreview({ moduleId: MODULE, kind: 'CONFIG', pathPattern: 'types/**/*.yml', component: LevelCurvePreview, label: copy('等级曲线', 'Level curve'), priority: 20 });
registerConfigCreateTemplate(MODULE, 'sources', {
  id: 'source-rule',
  label: copy('经验来源', 'Experience source'),
  fields: [
    { path: 'enabled', label: copy('启用', 'Enabled'), comment: copy('是否启用该来源。', 'Whether this source is enabled.'), type: 'boolean', defaultValue: true },
    { path: 'type', label: copy('等级类型', 'Level type'), comment: copy('经验写入的等级类型 ID。', 'Target level type id.'), type: 'text', defaultValue: 'main' },
    { path: 'trigger', label: copy('触发器', 'Trigger'), comment: copy('经验来源触发器。', 'Experience source trigger.'), type: 'text', defaultValue: 'entity_kill' },
    { path: 'rules', label: copy('规则', 'Rules'), comment: copy('匹配规则列表。', 'Match rule list.'), type: 'objectList', defaultValue: [] }
  ]
});
