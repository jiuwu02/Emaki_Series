import { useEffect, useState } from 'react';
import { MiniText } from './components';
import { asStringList, displaySource, firstItemSource, materialFromItemSource } from './itemEditor';
import { materialShortName, materialUrls, subscribeTextureBases } from './lib';

export type PreviewFact = { label: string; value: unknown; tone?: 'default' | 'good' | 'warn' | 'bad' };

export function PreviewMetricStrip({ facts }: { facts: PreviewFact[] }) {
  if (!facts.length) return null;
  return <div className="preview-metric-strip">
    {facts.map((fact, index) => <div className={`preview-metric ${fact.tone ?? 'default'}`} key={`${fact.label}-${index}`}>
      <span>{fact.label}</span>
      <strong>{formatPreviewValue(fact.value)}</strong>
    </div>)}
  </div>;
}

export function PreviewItemResult({ title = '结果物品', itemSources, name, lore, facts = [], status }: { title?: string; itemSources?: unknown; name?: string; lore?: string[]; facts?: PreviewFact[]; status?: string }) {
  const source = firstItemSource(itemSources);
  const material = materialFromItemSource(source);
  const urls = materialUrls(material);
  const [failed, setFailed] = useState(false);
  const [, refreshTextureOrder] = useState(0);
  useEffect(() => setFailed(false), [material]);
  useEffect(() => subscribeTextureBases(() => { setFailed(false); refreshTextureOrder(version => version + 1); }), []);
  return <div className="config-item-preview">
    <div className="config-item-preview-icon">
      {urls.length > 0 && !failed ? <img src={urls[0]} alt={material || 'item'} onError={event => {
        const target = event.currentTarget;
        const currentIndex = urls.indexOf(target.src);
        const next = urls[currentIndex + 1];
        if (next) target.src = next;
        else setFailed(true);
      }} /> : <span>{materialShortName(material) || '?'}</span>}
    </div>
    <div className="config-item-preview-body">
      <div className="config-item-preview-head">
        <span>{title}</span>
        {status && <code>{status}</code>}
      </div>
      <code className="config-item-preview-source">{displaySource(source || material)}</code>
      <PreviewTooltipBlock name={name || source || material} lore={lore ?? []} emptyText="暂无结果描述" />
      <PreviewMetricStrip facts={facts} />
    </div>
  </div>;
}

export function PreviewTooltipBlock({ name, lore, emptyText }: { name?: string; lore?: string[]; emptyText?: string }) {
  const lines = asStringList(lore);
  return <div className="config-preview-tooltip">
    {name ? <div className="config-preview-tooltip-name"><MiniText value={name} /></div> : null}
    {lines.map((line, index) => <div className="config-preview-tooltip-line" key={`${line}-${index}`}><MiniText value={line} /></div>)}
    {!name && !lines.length && <span className="config-preview-empty">{emptyText ?? '暂无预览'}</span>}
  </div>;
}

function formatPreviewValue(value: unknown): string {
  if (value == null || value === '') return '—';
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2).replace(/\.00$/, '');
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (Array.isArray(value)) return value.length > 3 ? `${value.slice(0, 3).join(', ')} +${value.length - 3}` : value.join(', ');
  if (typeof value === 'object') return `${Object.keys(value as Record<string, unknown>).length} 项`;
  return String(value);
}
