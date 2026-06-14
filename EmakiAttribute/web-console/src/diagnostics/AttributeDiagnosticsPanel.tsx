import { useEffect, useMemo, useState, type ReactElement } from 'react';
import type { SurfaceProps } from 'emaki-web-console';
import { Button, injectExtensionStyles } from 'emaki-web-console';

type Contribution = {
  attributeId?: string;
  value?: number;
  sourceModule?: string;
  sourceType?: string;
  sourceId?: string;
  sourceLabel?: string;
  slot?: string;
  layer?: string;
  conditionPassed?: boolean;
  rawValue?: number;
  finalValue?: number;
};

type SourceReport = {
  playerName?: string;
  snapshot?: { values?: Record<string, number> };
  contributions?: Contribution[];
};

type DamageStage = { stageId?: string; input?: number; output?: number; kind?: string; source?: string; mode?: string };
type DamageTrace = {
  traceId?: number;
  createdAtMillis?: number;
  attackerLabel?: string;
  targetLabel?: string;
  damageTypeId?: string;
  cause?: string;
  baseDamage?: number;
  finalDamage?: number;
  critical?: boolean;
  applyMode?: string;
  stages?: DamageStage[];
};

export function installAttributeDiagnosticsStyles(): void {
  injectExtensionStyles('emakiattribute-diagnostics', `
    .ea-diagnostics {
      --text-primary: var(--text);
      --text-secondary: var(--muted);
      --border-subtle: var(--line);
      --surface-card: var(--surface);
      --surface-input: var(--input);
      --surface-muted: var(--surface-2);
      --surface-raised: var(--surface);
      display: grid;
      gap: 14px;
      padding: 14px 18px 28px;
      color: var(--text);
    }
    .ea-diagnostics__grid { display: grid; grid-template-columns: minmax(280px, 360px) minmax(0, 1fr); gap: 14px; align-items: start; }
    .ea-card { border: 1px solid var(--line); background: var(--surface); border-radius: 6px; padding: 12px; }
    .ea-card h3 { margin: 0 0 10px; color: var(--text); font-size: 13px; font-weight: 700; letter-spacing: -.01em; }
    .ea-form { display: grid; gap: 10px; }
    .ea-form label { display: grid; gap: 6px; color: var(--muted); font-size: 11px; font-weight: 700; }
    .ea-form input { min-height: var(--config-field-control-height, 30px); border-radius: 6px; border: 1px solid var(--line); background: var(--input); color: var(--text); padding: 5px 8px; outline: none; font: inherit; font-size: 12px; }
    .ea-form input:focus { border-color: var(--line-2); }
    .ea-form input:focus-visible { outline: 1px solid var(--accent); outline-offset: 2px; }
    .ea-actions { display: flex; flex-wrap: wrap; gap: 8px; }
    .ea-table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: 12px; }
    .ea-table th, .ea-table td { border-bottom: 1px solid var(--line); padding: 8px 8px; text-align: left; vertical-align: top; }
    .ea-table th { color: var(--muted); font-weight: 700; }
    .ea-table tr:last-child th, .ea-table tr:last-child td { border-bottom: 0; }
    .ea-muted { color: var(--muted); font-size: 12px; line-height: 1.45; }
    .ea-error { color: var(--red); }
    .ea-badge { display: inline-flex; align-items: center; border-radius: 999px; padding: 2px 7px; background: var(--surface-2); border: 1px solid var(--line); color: var(--muted); font-size: 11px; }
    .ea-trace { display: grid; gap: 10px; }
    .ea-trace__item { border: 1px solid var(--line); border-radius: 6px; padding: 10px; background: var(--surface); }
    .ea-trace__item strong { color: var(--text); font-weight: 700; }
    .ea-trace__stages { margin-top: 8px; display: grid; gap: 4px; font-size: 12px; }
    pre.ea-json { max-height: 300px; overflow: auto; background: var(--input); border: 1px solid var(--line); border-radius: 6px; padding: 8px; color: var(--text); font-family: ui-monospace, "Cascadia Code", monospace; font-size: 11px; line-height: 1.45; scrollbar-width: thin; }
    @media (max-width: 900px) { .ea-diagnostics__grid { grid-template-columns: 1fr; } }
  `);
}

export function AttributeDiagnosticsPanel({ api, setToolbar, setOutline }: SurfaceProps): ReactElement {
  const [player, setPlayer] = useState('');
  const [attributeId, setAttributeId] = useState('');
  const [sourceReport, setSourceReport] = useState<SourceReport | null>(null);
  const [traces, setTraces] = useState<DamageTrace[]>([]);
  const [json, setJson] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setToolbar?.({ title: '玩家属性追踪 / 伤害调试器', subtitle: '查询在线玩家的属性来源与最近伤害 Trace', dirty: false, sourceEditable: false });
    setOutline?.({ title: '诊断', subtitle: 'Attribute', items: [], emptyText: '输入玩家名后查询。' });
    return () => {
      setToolbar?.(null);
      setOutline?.(null);
    };
  }, [setToolbar, setOutline]);

  const values = useMemo(() => sourceReport?.snapshot?.values ?? {}, [sourceReport]);

  async function loadSources(): Promise<void> {
    if (!player.trim()) {
      setError('请输入在线玩家名。');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await api.pluginApi('attribute', 'source-trace', { player: player.trim(), attributeId: attributeId.trim() });
      if (!response?.ok) throw new Error(response?.error || '查询失败');
      setSourceReport(response.report as SourceReport);
      setJson(JSON.stringify(response.report, null, 2));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }

  async function loadTraces(action = 'list'): Promise<void> {
    if (!player.trim()) {
      setError('请输入在线玩家名。');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await api.pluginApi('attribute', 'damage-trace', { player: player.trim(), action });
      if (!response?.ok) throw new Error(response?.error || '查询失败');
      if (action === 'clear') {
        setTraces([]);
        setJson(JSON.stringify(response, null, 2));
      } else {
        const next = Array.isArray(response.records) ? response.records as DamageTrace[] : [];
        setTraces(next);
        setJson(JSON.stringify(response.last && Object.keys(response.last).length ? response.last : response, null, 2));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setLoading(false);
    }
  }

  return <div className="ea-diagnostics">
    <div className="ea-card ea-form">
      <h3>查询条件</h3>
      <label>玩家名<input value={player} onChange={(event) => setPlayer(event.target.value)} placeholder="Steve" /></label>
      <label>属性 ID（可选）<input value={attributeId} onChange={(event) => setAttributeId(event.target.value)} placeholder="physical_attack" /></label>
      <div className="ea-actions">
        <Button size="sm" variant="primary" disabled={loading} onClick={loadSources}>加载属性来源</Button>
        <Button size="sm" disabled={loading} onClick={() => loadTraces('list')}>刷新伤害 Trace</Button>
        <Button size="sm" variant="danger" disabled={loading} onClick={() => loadTraces('clear')}>清空 Trace</Button>
      </div>
      <p className="ea-muted">伤害 Trace 需要先在游戏内执行 /ea debug &lt;player&gt; on 开启。</p>
      {error ? <p className="ea-muted ea-error">{error}</p> : null}
    </div>

    <div className="ea-diagnostics__grid">
      <section className="ea-card">
        <h3>最终属性</h3>
        {Object.keys(values).length === 0 ? <p className="ea-muted">暂无属性快照。</p> : <table className="ea-table"><tbody>
          {Object.entries(values).filter(([key]) => !attributeId.trim() || key === attributeId.trim()).map(([key, value]) => <tr key={key}><th>{key}</th><td>{formatNumber(value)}</td></tr>)}
        </tbody></table>}
      </section>
      <section className="ea-card">
        <h3>来源贡献</h3>
        {!sourceReport?.contributions?.length ? <p className="ea-muted">暂无来源记录。</p> : <table className="ea-table">
          <thead><tr><th>属性</th><th>值</th><th>来源</th><th>槽位</th><th>状态</th></tr></thead>
          <tbody>{sourceReport.contributions.map((entry, index) => <tr key={`${entry.attributeId}-${index}`}>
            <td>{entry.attributeId}</td><td>{formatSigned(entry.value)}</td><td><span className="ea-badge">{entry.sourceType}</span> {entry.sourceLabel || entry.sourceId}</td><td>{entry.slot || '-'}</td><td>{entry.conditionPassed === false ? '未生效' : '生效'}</td>
          </tr>)}</tbody>
        </table>}
      </section>
    </div>

    <section className="ea-card ea-trace">
      <h3>最近伤害 Trace</h3>
      {traces.length === 0 ? <p className="ea-muted">暂无 Trace。</p> : traces.map((trace) => <article className="ea-trace__item" key={trace.traceId}>
        <strong>#{trace.traceId} {trace.attackerLabel} → {trace.targetLabel}</strong>
        <div className="ea-muted">{trace.damageTypeId} / {trace.cause} / final={formatNumber(trace.finalDamage)} / mode={trace.applyMode}</div>
        <div className="ea-trace__stages">{trace.stages?.map((stage) => <div key={`${trace.traceId}-${stage.stageId}`}>Stage <b>{stage.stageId}</b>: {formatNumber(stage.input)} → {formatNumber(stage.output)}</div>)}</div>
      </article>)}
    </section>

    <section className="ea-card">
      <h3>JSON 导出</h3>
      <pre className="ea-json">{json || '查询后显示最近一次结果。'}</pre>
    </section>
  </div>;
}

function formatNumber(value: unknown): string {
  const numeric = Number(value ?? 0);
  return Number.isFinite(numeric) ? numeric.toFixed(2).replace(/\.00$/, '') : '0';
}

function formatSigned(value: unknown): string {
  const numeric = Number(value ?? 0);
  return `${numeric >= 0 ? '+' : ''}${formatNumber(numeric)}`;
}
