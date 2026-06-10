import React, { useEffect, useMemo, useState } from 'react';
import { localeText, type ConfigMetaFieldEntry, type ConfigPreviewProps, type ConfigRuleFieldEntry } from 'emaki-web-console';

const copy = localeText;

type CurveWarning = { type: string; message: string; targetLevel?: number };
type CurvePoint = { targetLevel: number; requiredExp: number; totalExp: number; growthRate: number; source: string; warnings?: CurveWarning[] };
type Curve = { type: string; displayName: string; startLevel: number; maxLevel: number; fromLevel: number; toLevel: number; points: CurvePoint[]; warnings?: CurveWarning[] };
type CurveResult = { curves: Curve[]; limits?: { maxPointsPerType?: number }; warnings?: CurveWarning[] };
type CurveMetric = 'requiredExp' | 'totalExp' | 'growthRate';

export const mainConfigFields: ConfigMetaFieldEntry[] = [
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

export const typeFields: ConfigMetaFieldEntry[] = [
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

export const sourceFields: ConfigMetaFieldEntry[] = [
  ['id', copy('来源文件 ID', 'Source file id'), copy('来源配置文件标识。', 'Source config file id.'), 'text'],
  ['enabled', copy('启用', 'Enabled'), copy('是否启用此来源文件。', 'Whether this source file is enabled.'), 'boolean'],
  ['sources', copy('经验来源', 'Experience sources'), copy('按来源 ID 配置的经验规则。', 'Experience source rules keyed by source id.'), 'object', { creatableChildren: true }]
];

export const dynamicFields: Record<string, ConfigRuleFieldEntry> = {
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

export function LevelCurvePreview({ api, file, data }: ConfigPreviewProps) {
  const fileTypeId = typeof data?.id === 'string' ? data.id : '';
  const [typeInput, setTypeInput] = useState(file.path.startsWith('types/') ? fileTypeId : '');
  const [fromLevel, setFromLevel] = useState(1);
  const [toLevel, setToLevel] = useState(80);
  const [metric, setMetric] = useState<CurveMetric>('requiredExp');
  const [result, setResult] = useState<CurveResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [hiddenTypes, setHiddenTypes] = useState<Set<string>>(new Set());
  const [openTypes, setOpenTypes] = useState<Set<string>>(new Set());
  const [hoverLevel, setHoverLevel] = useState<number | null>(null);

  const selectedTypes = useMemo(() => typeInput.split(',').map(value => value.trim()).filter(Boolean), [typeInput]);
  const curves = result?.curves ?? [];
  const visibleCurves = curves.filter(curve => !hiddenTypes.has(curve.type));

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const response = await api.pluginApi('level', 'curve', { types: selectedTypes, fromLevel, toLevel });
      const nextCurves = Array.isArray(response.curves) ? response.curves : [];
      setResult({ curves: nextCurves, limits: response.limits, warnings: response.warnings });
      setHiddenTypes(new Set());
      setOpenTypes(new Set(nextCurves[0]?.type ? [nextCurves[0].type] : []));
      setHoverLevel(null);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : String(exception));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const toggleType = (type: string) => setHiddenTypes(current => {
    const next = new Set(current);
    if (next.has(type)) next.delete(type);
    else next.add(type);
    return next;
  });
  const toggleOpen = (type: string) => setOpenTypes(current => {
    const next = new Set(current);
    if (next.has(type)) next.delete(type);
    else next.add(type);
    return next;
  });

  const exportCsv = () => {
    if (!curves.length) return;
    const rows = ['type,target_level,required_exp,total_exp,growth_rate,source,warnings'];
    curves.forEach(curve => curve.points.forEach(point => rows.push([
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

  const hoverRows = hoverLevel == null ? [] : visibleCurves.map((curve, index) => ({ curve, point: curve.points.find(point => point.targetLevel === hoverLevel) ?? null, color: colorForCurve(curve, curves, index) })).filter(entry => entry.point) as { curve: Curve; point: CurvePoint; color: string }[];

  return <section style={cardStyle}>
    <div style={headerStyle}>
      <div>
        <h3 style={{ margin: 0 }}>{copy('等级曲线', 'Level curve')}</h3>
        <p style={hintStyle}>{copy('由服务端基于真实 RequirementService 计算，前端只负责展示。', 'Calculated by the server through the real RequirementService; the frontend only visualizes it.')}</p>
      </div>
      <button type="button" onClick={load} disabled={loading} style={buttonStyle}>{loading ? copy('加载中...', 'Loading...') : copy('刷新曲线', 'Refresh')}</button>
    </div>
    <div style={controlGridStyle}>
      <label style={labelStyle}>{copy('等级类型，逗号分隔', 'Level types, comma separated')}<input value={typeInput} onChange={event => setTypeInput(event.target.value)} placeholder={copy('留空显示全部启用类型', 'Empty = all enabled types')} style={inputStyle} /></label>
      <label style={labelStyle}>{copy('起始目标等级', 'From target level')}<input type="number" min={1} value={fromLevel} onChange={event => setFromLevel(Number(event.target.value) || 1)} style={inputStyle} /></label>
      <label style={labelStyle}>{copy('结束目标等级', 'To target level')}<input type="number" min={1} value={toLevel} onChange={event => setToLevel(Number(event.target.value) || 1)} style={inputStyle} /></label>
      <label style={labelStyle}>{copy('图表指标', 'Metric')}<select value={metric} onChange={event => setMetric(event.target.value as CurveMetric)} style={inputStyle}>
        <option value="requiredExp">{copy('单级需求经验', 'Required exp')}</option>
        <option value="totalExp">{copy('累计总经验', 'Total exp')}</option>
        <option value="growthRate">{copy('增长率', 'Growth rate')}</option>
      </select></label>
    </div>
    {error ? <div style={errorStyle}>{error}</div> : null}
    {curves.length ? <>
      <CurveLegend curves={curves} hiddenTypes={hiddenTypes} onToggle={toggleType} />
      <div style={chartWrapStyle}>
        <LevelCurveSvg curves={curves} visibleCurves={visibleCurves} hiddenTypes={hiddenTypes} metric={metric} hoverLevel={hoverLevel} onHover={setHoverLevel} />
      </div>
      <CurveLevelInspector level={hoverLevel} rows={hoverRows} />
      <div style={summaryStyle}>
        <span>{copy('曲线数量', 'Curves')}: <strong>{curves.length}</strong></span>
        <span>{copy('显示中', 'Visible')}: <strong>{visibleCurves.length}</strong></span>
        <span>{copy('单类型最多点数', 'Max points/type')}: <strong>{result?.limits?.maxPointsPerType ?? '-'}</strong></span>
        <button type="button" onClick={exportCsv} style={secondaryButtonStyle}>{copy('导出 CSV', 'Export CSV')}</button>
      </div>
      <CurveTables curves={curves} hiddenTypes={hiddenTypes} openTypes={openTypes} onToggleOpen={toggleOpen} />
    </> : <div style={emptyStyle}>{copy('暂无曲线数据。', 'No curve data.')}</div>}
  </section>;
}

function CurveLegend({ curves, hiddenTypes, onToggle }: { curves: Curve[]; hiddenTypes: Set<string>; onToggle: (type: string) => void }) {
  return <div style={legendStyle}>{curves.map((curve, index) => {
    const color = colorForCurve(curve, curves, index);
    const hidden = hiddenTypes.has(curve.type);
    return <button key={curve.type} type="button" onClick={() => onToggle(curve.type)} style={{ ...legendButtonStyle, opacity: hidden ? .42 : 1 }} aria-pressed={!hidden}>
      <span style={{ ...legendSwatchStyle, background: color }} />
      <code>{curve.type}</code>
      <small>{curve.points.length}</small>
    </button>;
  })}</div>;
}

function LevelCurveSvg({ curves, visibleCurves, hiddenTypes, metric, hoverLevel, onHover }: { curves: Curve[]; visibleCurves: Curve[]; hiddenTypes: Set<string>; metric: CurveMetric; hoverLevel: number | null; onHover: (level: number) => void }) {
  const width = 820;
  const height = 300;
  const pad = { left: 42, right: 28, top: 26, bottom: 34 };
  const points = visibleCurves.flatMap(curve => curve.points.map(point => ({ curve, point, value: pointMetric(point, metric) })));
  const minLevel = points.length ? Math.min(...points.map(entry => entry.point.targetLevel)) : 1;
  const maxLevel = points.length ? Math.max(...points.map(entry => entry.point.targetLevel)) : 1;
  const maxValue = Math.max(1, ...points.map(entry => entry.value));
  const chartWidth = width - pad.left - pad.right;
  const chartHeight = height - pad.top - pad.bottom;
  const x = (level: number) => pad.left + ((level - minLevel) / Math.max(1, maxLevel - minLevel)) * chartWidth;
  const y = (value: number) => pad.top + chartHeight - (value / maxValue) * chartHeight;
  const levels = Array.from(new Set(points.map(entry => entry.point.targetLevel))).sort((a, b) => a - b);
  const nearestLevel = (clientX: number, rect: DOMRect) => {
    const localX = ((clientX - rect.left) / rect.width) * width;
    const raw = minLevel + ((localX - pad.left) / Math.max(1, chartWidth)) * Math.max(1, maxLevel - minLevel);
    return levels.reduce((best, level) => Math.abs(level - raw) < Math.abs(best - raw) ? level : best, levels[0] ?? minLevel);
  };
  const handleMove = (event: React.MouseEvent<SVGSVGElement>) => {
    if (!levels.length) return;
    const rect = event.currentTarget.getBoundingClientRect();
    const level = nearestLevel(event.clientX, rect);
    onHover(level);
  };
  return <svg viewBox={`0 0 ${width} ${height}`} style={svgStyle} role="img" aria-label={copy('等级曲线图', 'Level curve chart')} onMouseMove={handleMove}>
    {[0, .25, .5, .75, 1].map(step => <line key={step} x1={pad.left} x2={width - pad.right} y1={pad.top + chartHeight * step} y2={pad.top + chartHeight * step} stroke="color-mix(in oklch, var(--line) 58%, transparent)" />)}
    <line x1={pad.left} y1={height - pad.bottom} x2={width - pad.right} y2={height - pad.bottom} stroke="color-mix(in oklch, var(--line-2) 78%, transparent)" />
    <line x1={pad.left} y1={pad.top} x2={pad.left} y2={height - pad.bottom} stroke="color-mix(in oklch, var(--line-2) 78%, transparent)" />
    {hoverLevel != null && <line x1={x(hoverLevel)} y1={pad.top} x2={x(hoverLevel)} y2={height - pad.bottom} stroke="color-mix(in oklch, var(--text) 52%, transparent)" strokeDasharray="4 4" />}
    {curves.map((curve, index) => {
      if (hiddenTypes.has(curve.type)) return null;
      const color = colorForCurve(curve, curves, index);
      const d = curve.points.map((point, pointIndex) => `${pointIndex === 0 ? 'M' : 'L'} ${x(point.targetLevel)} ${y(pointMetric(point, metric))}`).join(' ');
      return <g key={curve.type}>
        <path d={d} fill="none" stroke={color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
        {curve.points.map(point => {
          const active = hoverLevel === point.targetLevel;
          const warning = (point.warnings?.length ?? 0) > 0;
          return <circle key={`${curve.type}-${point.targetLevel}`} cx={x(point.targetLevel)} cy={y(pointMetric(point, metric))} r={active ? 5 : warning ? 4 : 2.6} fill={warning ? 'var(--amber)' : color} stroke={active ? 'var(--surface)' : 'transparent'} strokeWidth={active ? 2 : 0} />;
        })}
      </g>;
    })}
    <text x={pad.left} y={height - 8} fill="var(--muted)" fontSize="11">Lv.{minLevel}</text>
    <text x={width - pad.right} y={height - 8} fill="var(--muted)" fontSize="11" textAnchor="end">Lv.{maxLevel}</text>
  </svg>;
}

function CurveLevelInspector({ level, rows }: { level: number | null; rows: { curve: Curve; point: CurvePoint; color: string }[] }) {
  if (level == null || !rows.length) return <div style={levelInspectorEmptyStyle}>{copy('移动到曲线上的等级位置查看各等级组数值。', 'Move over a level on the chart to inspect every visible group.')}</div>;
  return <aside style={levelInspectorStyle} aria-live="polite">
    <div style={levelInspectorHeadStyle}>
      <strong>{copy('目标等级', 'Target level')} Lv.{level}</strong>
      <span>{rows.length} {copy('组', 'groups')}</span>
    </div>
    <div style={levelInspectorRowsStyle}>
      {rows.map(({ curve, point, color }) => <div key={curve.type} style={tooltipRowStyle}>
        <span style={{ ...legendSwatchStyle, background: color }} />
        <div style={{ minWidth: 0 }}>
          <div style={tooltipTitleStyle}><code>{curve.type}</code>{(point.warnings?.length ?? 0) > 0 ? <small style={warningPillStyle}>{point.warnings?.length}</small> : null}</div>
          <div style={tooltipMetaStyle}>
            <span>{copy('需求', 'Required')} <b style={tooltipNumberStyle}>{formatNumber(point.requiredExp)}</b></span>
            <span>{copy('累计', 'Total')} <b style={tooltipNumberStyle}>{formatNumber(point.totalExp)}</b></span>
            <span>{copy('增长率', 'Growth')} <b style={tooltipNumberStyle}>{formatPercent(point.growthRate)}</b></span>
          </div>
        </div>
      </div>)}
    </div>
  </aside>;
}

function CurveTables({ curves, hiddenTypes, openTypes, onToggleOpen }: { curves: Curve[]; hiddenTypes: Set<string>; openTypes: Set<string>; onToggleOpen: (type: string) => void }) {
  return <div style={tablesWrapStyle}>{curves.map((curve, index) => {
    const open = openTypes.has(curve.type);
    const hidden = hiddenTypes.has(curve.type);
    const warningCount = curve.points.reduce((sum, point) => sum + (point.warnings?.length ?? 0), 0) + (curve.warnings?.length ?? 0);
    const color = colorForCurve(curve, curves, index);
    return <section key={curve.type} style={{ ...tableGroupStyle, opacity: hidden ? .5 : 1 }}>
      <button type="button" style={tableGroupHeadStyle} onClick={() => onToggleOpen(curve.type)} aria-expanded={open}>
        <span>{open ? '⌄' : '›'}</span><span style={{ ...legendSwatchStyle, background: color }} />
        <code>{curve.type}</code>
        <em>{curve.fromLevel}-{curve.toLevel}</em><small>{curve.points.length} {copy('点', 'points')}</small>{warningCount ? <small style={warningPillStyle}>{warningCount}</small> : null}
      </button>
      {open && <CurveTable curve={curve} />}
    </section>;
  })}</div>;
}

function CurveTable({ curve }: { curve: Curve }) {
  return <div style={tableScrollStyle}>
    <table style={tableStyle}>
      <colgroup><col style={{ width: '64px' }} /><col style={{ width: '120px' }} /><col style={{ width: '130px' }} /><col style={{ width: '100px' }} /><col /><col style={{ width: '120px' }} /></colgroup>
      <thead><tr><th style={{ ...headCellStyle, ...numCellStyle }}>Lv</th><th style={{ ...headCellStyle, ...numCellStyle }}>{copy('需求', 'Required')}</th><th style={{ ...headCellStyle, ...numCellStyle }}>{copy('累计', 'Total')}</th><th style={{ ...headCellStyle, ...numCellStyle }}>{copy('增长率', 'Growth')}</th><th style={headCellStyle}>{copy('来源', 'Source')}</th><th style={headCellStyle}>{copy('警告', 'Warnings')}</th></tr></thead>
      <tbody>{curve.points.map(point => <tr key={point.targetLevel}>
        <td style={{ ...bodyCellStyle, ...numCellStyle }}>{point.targetLevel}</td>
        <td style={{ ...bodyCellStyle, ...numCellStyle }}>{formatNumber(point.requiredExp)}</td>
        <td style={{ ...bodyCellStyle, ...numCellStyle }}>{formatNumber(point.totalExp)}</td>
        <td style={{ ...bodyCellStyle, ...numCellStyle }}>{formatPercent(point.growthRate)}</td>
        <td style={bodyCellStyle}><code style={sourceCellStyle}>{point.source}</code></td>
        <td style={bodyCellStyle}>{(point.warnings ?? []).map(warning => warning.type).join(', ')}</td>
      </tr>)}</tbody>
    </table>
  </div>;
}

function pointMetric(point: CurvePoint, metric: CurveMetric): number {
  if (metric === 'totalExp') return point.totalExp;
  if (metric === 'growthRate') return Math.max(0, point.growthRate * 100);
  return point.requiredExp;
}

function colorForCurve(curve: Curve, curves: Curve[], fallbackIndex: number): string {
  const index = Math.max(0, curves.findIndex(entry => entry.type === curve.type));
  return palette[(index >= 0 ? index : fallbackIndex) % palette.length];
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

const palette = ['#60a5fa', '#a78bfa', '#34d399', '#f59e0b', '#f472b6', '#22d3ee', '#fb7185', '#2dd4bf'];
const cardStyle: React.CSSProperties = { border: '1px solid var(--line)', borderRadius: 16, padding: 16, marginTop: 16, background: 'linear-gradient(180deg, color-mix(in oklch, var(--surface-2) 78%, transparent), color-mix(in oklch, var(--surface) 88%, transparent))', color: 'var(--text)' };
const headerStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'flex-start' };
const hintStyle: React.CSSProperties = { margin: '6px 0 0', color: 'var(--muted)', fontSize: 13 };
const controlGridStyle: React.CSSProperties = { display: 'grid', gridTemplateColumns: 'minmax(220px,2fr) repeat(3,minmax(120px,1fr))', gap: 10, marginTop: 14 };
const labelStyle: React.CSSProperties = { display: 'grid', gap: 6, fontSize: 12, color: 'var(--muted)' };
const inputStyle: React.CSSProperties = { border: '1px solid var(--line-2)', borderRadius: 10, background: 'var(--input)', color: 'var(--text)', padding: '8px 10px' };
const buttonStyle: React.CSSProperties = { border: 0, borderRadius: 10, padding: '9px 13px', background: 'var(--accent)', color: 'var(--bg)', fontWeight: 700, cursor: 'pointer' };
const secondaryButtonStyle: React.CSSProperties = { ...buttonStyle, background: 'var(--accent-soft)', color: 'var(--accent-strong)', border: '1px solid color-mix(in oklch, var(--accent) 44%, var(--line) 56%)' };
const errorStyle: React.CSSProperties = { marginTop: 12, color: 'var(--danger-ink)', background: 'var(--danger-soft)', border: '1px solid var(--danger-line)', borderRadius: 10, padding: 10 };
const emptyStyle: React.CSSProperties = { marginTop: 12, color: 'var(--muted)' };
const svgStyle: React.CSSProperties = { width: '100%', display: 'block', background: 'color-mix(in oklch, var(--input) 76%, transparent)', borderRadius: 12 };
const summaryStyle: React.CSSProperties = { display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap', marginTop: 12, color: 'var(--muted)' };
const legendStyle: React.CSSProperties = { display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 14 };
const legendButtonStyle: React.CSSProperties = { display: 'inline-flex', alignItems: 'center', gap: 6, border: '1px solid var(--line)', borderRadius: 999, padding: '4px 8px', color: 'var(--text)', background: 'color-mix(in oklch, var(--surface-2) 78%, transparent)', fontSize: 11 };
const legendSwatchStyle: React.CSSProperties = { width: 10, height: 10, borderRadius: 999, flex: '0 0 auto' };
const chartWrapStyle: React.CSSProperties = { position: 'relative', marginTop: 14 };
const levelInspectorStyle: React.CSSProperties = { marginTop: 10, display: 'grid', gap: 8, padding: 10, border: '1px solid var(--line-2)', borderRadius: 12, background: 'color-mix(in oklch, var(--surface) 96%, var(--bg) 4%)', boxShadow: '0 10px 28px oklch(0% 0 0 / .16)', color: 'var(--text)' };
const levelInspectorEmptyStyle: React.CSSProperties = { marginTop: 10, padding: 10, border: '1px dashed var(--line)', borderRadius: 12, background: 'color-mix(in oklch, var(--input) 76%, transparent)', color: 'var(--faint)', fontSize: 12 };
const levelInspectorHeadStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', color: 'var(--text)', fontSize: 12 };
const levelInspectorRowsStyle: React.CSSProperties = { maxHeight: 220, overflowY: 'auto', display: 'grid', gap: 8, paddingRight: 2, scrollbarWidth: 'thin' };
const tooltipRowStyle: React.CSSProperties = { display: 'grid', gridTemplateColumns: '10px minmax(0,1fr)', gap: 8, alignItems: 'start' };
const tooltipTitleStyle: React.CSSProperties = { display: 'flex', gap: 6, alignItems: 'baseline', minWidth: 0, fontSize: 12, fontWeight: 700 };
const tooltipMetaStyle: React.CSSProperties = { marginTop: 3, display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 6, color: 'var(--muted)', fontSize: 11, lineHeight: 1.35 };
const tooltipNumberStyle: React.CSSProperties = { display: 'block', marginTop: 1, color: 'var(--accent-strong)', fontWeight: 800, fontVariantNumeric: 'tabular-nums' };
const tablesWrapStyle: React.CSSProperties = { display: 'grid', gap: 8, marginTop: 12 };
const tableGroupStyle: React.CSSProperties = { border: '1px solid var(--line)', borderRadius: 10, overflow: 'hidden', background: 'color-mix(in oklch, var(--surface-2) 54%, transparent)' };
const tableGroupHeadStyle: React.CSSProperties = { width: '100%', minHeight: 36, display: 'grid', gridTemplateColumns: '18px 10px minmax(120px,1fr) minmax(80px,.6fr) auto auto', alignItems: 'center', justifyItems: 'center', gap: 8, padding: '7px 10px', color: 'var(--text)', textAlign: 'center', borderBottom: '1px solid var(--line)' };
const warningPillStyle: React.CSSProperties = { color: 'var(--amber)', border: '1px solid color-mix(in oklch, var(--amber) 44%, var(--line) 56%)', borderRadius: 999, padding: '1px 6px' };
const tableScrollStyle: React.CSSProperties = { overflowX: 'auto' };
const tableStyle: React.CSSProperties = { width: '100%', minWidth: 720, borderCollapse: 'collapse', tableLayout: 'fixed', fontSize: 12, color: 'var(--text)' };
const headCellStyle: React.CSSProperties = { padding: '8px 10px', borderBottom: '1px solid var(--line)', color: 'var(--muted)', fontWeight: 700, textAlign: 'center' };
const bodyCellStyle: React.CSSProperties = { padding: '7px 10px', borderBottom: '1px solid color-mix(in oklch, var(--line) 72%, transparent)', verticalAlign: 'middle', textAlign: 'center' };
const numCellStyle: React.CSSProperties = { textAlign: 'center', fontVariantNumeric: 'tabular-nums' };
const sourceCellStyle: React.CSSProperties = { display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--faint)', textAlign: 'center' };
