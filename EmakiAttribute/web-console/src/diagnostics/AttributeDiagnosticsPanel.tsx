import { useEffect, useMemo, useState, type ReactElement } from 'react';
import type { SurfaceProps } from 'emaki-web-console';
import { Button, injectExtensionStyles, t } from 'emaki-web-console';
import diagnosticsStyles from './AttributeDiagnosticsPanel.css?raw';

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
  injectExtensionStyles('emakiattribute-diagnostics', diagnosticsStyles);
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
    setToolbar?.({ title: t('emakiattribute.diagnostics.toolbar.title'), subtitle: t('emakiattribute.diagnostics.toolbar.subtitle'), dirty: false, sourceEditable: false });
    setOutline?.({ title: t('emakiattribute.diagnostics.outline.title'), subtitle: 'Attribute', items: [], emptyText: t('emakiattribute.diagnostics.outline.empty') });
    return () => {
      setToolbar?.(null);
      setOutline?.(null);
    };
  }, [setToolbar, setOutline]);

  const values = useMemo(() => sourceReport?.snapshot?.values ?? {}, [sourceReport]);

  async function loadSources(): Promise<void> {
    if (!player.trim()) {
      setError(t('emakiattribute.diagnostics.error.playerRequired'));
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await api.pluginApi('attribute', 'source-trace', { player: player.trim(), attributeId: attributeId.trim() });
      if (!response?.ok) throw new Error(formatApiError(response?.error, response?.player));
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
      setError(t('emakiattribute.diagnostics.error.playerRequired'));
      return;
    }
    setLoading(true);
    setError('');
    try {
      const response = await api.pluginApi('attribute', 'damage-trace', { player: player.trim(), action });
      if (!response?.ok) throw new Error(formatApiError(response?.error, response?.player));
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
      <h3>{t('emakiattribute.diagnostics.filters.title')}</h3>
      <label>{t('emakiattribute.diagnostics.filters.player')}<input value={player} onChange={(event) => setPlayer(event.target.value)} placeholder="Steve" /></label>
      <label>{t('emakiattribute.diagnostics.filters.attributeId')}<input value={attributeId} onChange={(event) => setAttributeId(event.target.value)} placeholder="physical_attack" /></label>
      <div className="ea-actions">
        <Button size="sm" variant="primary" disabled={loading} onClick={loadSources}>{t('emakiattribute.diagnostics.action.loadSources')}</Button>
        <Button size="sm" disabled={loading} onClick={() => loadTraces('list')}>{t('emakiattribute.diagnostics.action.refreshTraces')}</Button>
        <Button size="sm" variant="danger" disabled={loading} onClick={() => loadTraces('clear')}>{t('emakiattribute.diagnostics.action.clearTraces')}</Button>
      </div>
      <p className="ea-muted">{t('emakiattribute.diagnostics.traceHint')}</p>
      {error ? <p className="ea-muted ea-error">{error}</p> : null}
    </div>

    <div className="ea-diagnostics__grid">
      <section className="ea-card">
        <h3>{t('emakiattribute.diagnostics.finalAttributes.title')}</h3>
        {Object.keys(values).length === 0 ? <p className="ea-muted">{t('emakiattribute.diagnostics.finalAttributes.empty')}</p> : <table className="ea-table"><tbody>
          {Object.entries(values).filter(([key]) => !attributeId.trim() || key === attributeId.trim()).map(([key, value]) => <tr key={key}><th>{key}</th><td>{formatNumber(value)}</td></tr>)}
        </tbody></table>}
      </section>
      <section className="ea-card">
        <h3>{t('emakiattribute.diagnostics.contributions.title')}</h3>
        {!sourceReport?.contributions?.length ? <p className="ea-muted">{t('emakiattribute.diagnostics.contributions.empty')}</p> : <table className="ea-table">
          <thead><tr><th>{t('emakiattribute.diagnostics.column.attribute')}</th><th>{t('emakiattribute.diagnostics.column.value')}</th><th>{t('emakiattribute.diagnostics.column.source')}</th><th>{t('emakiattribute.diagnostics.column.slot')}</th><th>{t('emakiattribute.diagnostics.column.status')}</th></tr></thead>
          <tbody>{sourceReport.contributions.map((entry, index) => <tr key={`${entry.attributeId}-${index}`}>
            <td>{entry.attributeId}</td><td>{formatSigned(entry.value)}</td><td><span className="ea-badge">{entry.sourceType}</span> {entry.sourceLabel || entry.sourceId}</td><td>{entry.slot || '-'}</td><td>{entry.conditionPassed === false ? t('emakiattribute.diagnostics.status.inactive') : t('emakiattribute.diagnostics.status.active')}</td>
          </tr>)}</tbody>
        </table>}
      </section>
    </div>

    <section className="ea-card ea-trace">
      <h3>{t('emakiattribute.diagnostics.traces.title')}</h3>
      {traces.length === 0 ? <p className="ea-muted">{t('emakiattribute.diagnostics.traces.empty')}</p> : traces.map((trace) => <article className="ea-trace__item" key={trace.traceId}>
        <strong>#{trace.traceId} {trace.attackerLabel} → {trace.targetLabel}</strong>
        <div className="ea-muted">{trace.damageTypeId} / {trace.cause} / final={formatNumber(trace.finalDamage)} / mode={trace.applyMode}</div>
        <div className="ea-trace__stages">{trace.stages?.map((stage) => <div key={`${trace.traceId}-${stage.stageId}`}>{t('emakiattribute.diagnostics.stage')} <b>{stage.stageId}</b>: {formatNumber(stage.input)} → {formatNumber(stage.output)}</div>)}</div>
      </article>)}
    </section>

    <section className="ea-card">
      <h3>{t('emakiattribute.diagnostics.json.title')}</h3>
      <pre className="ea-json">{json || t('emakiattribute.diagnostics.json.empty')}</pre>
    </section>
  </div>;
}

function formatApiError(error: unknown, player: unknown): string {
  if (error === 'player_not_found') {
    const playerName = typeof player === 'string' && player.trim() ? player : '';
    return playerName
      ? t('emakiattribute.diagnostics.error.playerNotFound', { player: playerName })
      : t('emakiattribute.diagnostics.error.playerNotFoundUnknown');
  }
  return typeof error === 'string' && error.trim() ? error : t('emakiattribute.diagnostics.error.queryFailed');
}

function formatNumber(value: unknown): string {
  const numeric = Number(value ?? 0);
  return Number.isFinite(numeric) ? numeric.toFixed(2).replace(/\.00$/, '') : '0';
}

function formatSigned(value: unknown): string {
  const numeric = Number(value ?? 0);
  return `${numeric >= 0 ? '+' : ''}${formatNumber(numeric)}`;
}
