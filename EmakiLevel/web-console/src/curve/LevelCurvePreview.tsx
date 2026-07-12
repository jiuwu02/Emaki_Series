import React, { useEffect, useMemo, useState } from 'react';
import { Button, InlineError, injectExtensionStyles, localeText, type ConfigMetaFieldEntry, type ConfigPreviewProps, type ConfigRuleFieldEntry } from 'emaki-web-console';
import levelCurvePreviewStyles from './LevelCurvePreview.css?raw';

export function installLevelCurvePreviewStyles(): void {
  injectExtensionStyles('emakilevel-curve-preview', levelCurvePreviewStyles);
}

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
  ['attributes.values', copy('属性公式', 'Attribute formulas'), copy('属性 ID 到表达式的映射。', 'Map of attribute id to expression.'), 'dynamic_map', { creatableChildren: true, createTemplates: [{ id: 'attribute-formula', label: copy('属性公式', 'Attribute formula'), fields: [{ path: 'value', label: copy('公式', 'Formula'), comment: copy('该属性的等级公式。', 'Level formula for this attribute.'), type: 'text', defaultValue: '0' }] }] }]
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

  return <section className="level-curve-preview">
    <div className="level-curve-preview__header">
      <div>
        <h3 className="level-curve-preview__title">{copy('等级曲线', 'Level curve')}</h3>
        <p className="level-curve-preview__hint">{copy('由服务端基于真实 RequirementService 计算，前端只负责展示。结构化配置仍可继续编辑，预览只反映运行时计算结果。', 'Calculated by the server through the real RequirementService; the frontend only visualizes it. Structured config remains editable; the preview reflects runtime calculation only.')}</p>
      </div>
      <Button size="sm" onClick={load} disabled={loading}>{loading ? copy('加载中...', 'Loading...') : copy('刷新曲线', 'Refresh curve')}</Button>
    </div>
    <div className="level-curve-preview__controls">
      <label className="level-curve-preview__field">{copy('等级类型，逗号分隔', 'Level types, comma separated')}<input value={typeInput} onChange={event => setTypeInput(event.target.value)} placeholder={copy('留空显示全部启用类型', 'Empty = all enabled types')} /></label>
      <label className="level-curve-preview__field">{copy('起始目标等级', 'From target level')}<input type="number" min={1} value={fromLevel} onChange={event => setFromLevel(Number(event.target.value) || 1)} /></label>
      <label className="level-curve-preview__field">{copy('结束目标等级', 'To target level')}<input type="number" min={1} value={toLevel} onChange={event => setToLevel(Number(event.target.value) || 1)} /></label>
      <label className="level-curve-preview__field">{copy('图表指标', 'Metric')}<select value={metric} onChange={event => setMetric(event.target.value as CurveMetric)}>
        <option value="requiredExp">{copy('单级需求经验', 'Required exp')}</option>
        <option value="totalExp">{copy('累计总经验', 'Total exp')}</option>
        <option value="growthRate">{copy('增长率', 'Growth rate')}</option>
      </select></label>
    </div>
    {error ? <PluginApiError route="curve" message={error} /> : null}
    {curves.length ? <>
      <CurveLegend curves={curves} hiddenTypes={hiddenTypes} onToggle={toggleType} />
      <div className="level-curve-preview__chart-wrap">
        <LevelCurveSvg curves={curves} visibleCurves={visibleCurves} hiddenTypes={hiddenTypes} metric={metric} hoverLevel={hoverLevel} onHover={setHoverLevel} />
      </div>
      <CurveLevelInspector level={hoverLevel} rows={hoverRows} />
      <div className="level-curve-preview__summary">
        <span>{copy('曲线数量', 'Curves')}: <strong>{curves.length}</strong></span>
        <span>{copy('显示中', 'Visible')}: <strong>{visibleCurves.length}</strong></span>
        <span>{copy('单类型最多点数', 'Max points/type')}: <strong>{result?.limits?.maxPointsPerType ?? '-'}</strong></span>
        <Button size="sm" variant="soft" onClick={exportCsv}>{copy('导出 CSV', 'Export CSV')}</Button>
      </div>
      <CurveTables curves={curves} hiddenTypes={hiddenTypes} openTypes={openTypes} onToggleOpen={toggleOpen} />
    </> : <div className="level-curve-preview__empty">{copy('暂无曲线数据。', 'No curve data.')}</div>}
  </section>;
}

function PluginApiError({ route, message }: { route: string; message: string }) {
  return <InlineError>
    <strong>{copy('等级曲线预览暂不可用。', 'Level curve preview is unavailable.')}</strong>
    <p>{copy(`插件 API level/${route} 请求失败；当前 YAML 仍可继续编辑，保存不依赖此只读预览。`, `Plugin API level/${route} failed. The current YAML remains editable; saving does not depend on this read-only preview.`)}</p>
    <details className="level-curve-preview__diagnostic">
      <summary>{copy('开发者诊断', 'Developer diagnostics')}</summary>
      <pre>{message}</pre>
    </details>
  </InlineError>;
}

function CurveLegend({ curves, hiddenTypes, onToggle }: { curves: Curve[]; hiddenTypes: Set<string>; onToggle: (type: string) => void }) {
  return <div className="level-curve-preview__legend">{curves.map((curve, index) => {
    const color = colorForCurve(curve, curves, index);
    const hidden = hiddenTypes.has(curve.type);
    return <button key={curve.type} type="button" className="level-curve-preview__legend-button" onClick={() => onToggle(curve.type)} style={curveColorStyle(color)} aria-pressed={!hidden}>
      <span className="level-curve-preview__swatch" />
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
    if (level !== hoverLevel) onHover(level);
  };
  return <svg viewBox={`0 0 ${width} ${height}`} className="level-curve-preview__chart" role="img" aria-label={copy('等级曲线图', 'Level curve chart')} onMouseMove={handleMove}>
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
  if (level == null || !rows.length) return <div className="level-curve-preview__inspector-empty">{copy('移动到曲线上的等级位置查看各等级组数值。', 'Move over a level on the chart to inspect every visible group.')}</div>;
  return <aside className="level-curve-preview__inspector" aria-live="polite">
    <div className="level-curve-preview__inspector-head">
      <strong>{copy('目标等级', 'Target level')} Lv.{level}</strong>
      <span>{rows.length} {copy('组', 'groups')}</span>
    </div>
    <div className="level-curve-preview__inspector-rows">
      {rows.map(({ curve, point, color }) => <div key={curve.type} className="level-curve-preview__tooltip-row" style={curveColorStyle(color)}>
        <span className="level-curve-preview__swatch" />
        <div className="level-curve-preview__tooltip-copy">
          <div className="level-curve-preview__tooltip-title"><code>{curve.type}</code>{(point.warnings?.length ?? 0) > 0 ? <small className="level-curve-preview__warning-pill">{point.warnings?.length}</small> : null}</div>
          <div className="level-curve-preview__tooltip-meta">
            <span>{copy('需求', 'Required')} <b className="level-curve-preview__tooltip-number">{formatNumber(point.requiredExp)}</b></span>
            <span>{copy('累计', 'Total')} <b className="level-curve-preview__tooltip-number">{formatNumber(point.totalExp)}</b></span>
            <span>{copy('增长率', 'Growth')} <b className="level-curve-preview__tooltip-number">{formatPercent(point.growthRate)}</b></span>
          </div>
        </div>
      </div>)}
    </div>
  </aside>;
}

function CurveTables({ curves, hiddenTypes, openTypes, onToggleOpen }: { curves: Curve[]; hiddenTypes: Set<string>; openTypes: Set<string>; onToggleOpen: (type: string) => void }) {
  return <div className="level-curve-preview__tables">{curves.map((curve, index) => {
    const open = openTypes.has(curve.type);
    const hidden = hiddenTypes.has(curve.type);
    const warningCount = curve.points.reduce((sum, point) => sum + (point.warnings?.length ?? 0), 0) + (curve.warnings?.length ?? 0);
    const color = colorForCurve(curve, curves, index);
    return <section key={curve.type} className="level-curve-preview__table-group" aria-disabled={hidden}>
      <button type="button" className="level-curve-preview__table-head" onClick={() => onToggleOpen(curve.type)} aria-expanded={open} style={curveColorStyle(color)}>
        <span>{open ? '⌄' : '›'}</span><span className="level-curve-preview__swatch" />
        <code>{curve.type}</code>
        <em>{curve.fromLevel}-{curve.toLevel}</em><small>{curve.points.length} {copy('点', 'points')}</small>{warningCount ? <small className="level-curve-preview__warning-pill">{warningCount}</small> : null}
      </button>
      {open && <CurveTable curve={curve} />}
    </section>;
  })}</div>;
}

function CurveTable({ curve }: { curve: Curve }) {
  return <div className="level-curve-preview__table-scroll">
    <table className="level-curve-preview__table">
      <colgroup><col className="level-curve-preview__col-lv" /><col className="level-curve-preview__col-required" /><col className="level-curve-preview__col-total" /><col className="level-curve-preview__col-growth" /><col /><col className="level-curve-preview__col-warnings" /></colgroup>
      <thead><tr><th className="level-curve-preview__num">Lv</th><th className="level-curve-preview__num">{copy('需求', 'Required')}</th><th className="level-curve-preview__num">{copy('累计', 'Total')}</th><th className="level-curve-preview__num">{copy('增长率', 'Growth')}</th><th>{copy('来源', 'Source')}</th><th>{copy('警告', 'Warnings')}</th></tr></thead>
      <tbody>{curve.points.map(point => <tr key={point.targetLevel}>
        <td className="level-curve-preview__num">{point.targetLevel}</td>
        <td className="level-curve-preview__num">{formatNumber(point.requiredExp)}</td>
        <td className="level-curve-preview__num">{formatNumber(point.totalExp)}</td>
        <td className="level-curve-preview__num">{formatPercent(point.growthRate)}</td>
        <td><code className="level-curve-preview__source">{point.source}</code></td>
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

const palette = [
  'var(--viz-1)',
  'var(--viz-2)',
  'var(--viz-3)',
  'var(--viz-4)',
  'var(--viz-5)',
  'var(--viz-6)',
  'var(--viz-7)',
  'var(--viz-8)'
];
function curveColorStyle(color: string): React.CSSProperties {
  return { '--curve-color': color } as React.CSSProperties;
}
