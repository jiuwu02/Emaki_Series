import { useEffect, useMemo, useState } from 'react';
import { MiniText } from './components';
import { asStringList, displaySource, firstItemSource, materialFromItemSource } from './itemEditor';
import { materialShortName, materialUrls, subscribeTextureBases } from './lib';

export type PreviewFact = { label: string; value: unknown; tone?: 'default' | 'good' | 'warn' | 'bad' };

export type BlueprintNode = {
  id: string;
  title: string;
  subtitle?: string;
  meta?: string;
  tone?: 'default' | 'accent' | 'good' | 'warn' | 'bad';
  column?: number;
  row?: number;
  facts?: PreviewFact[];
};

export type BlueprintEdge = { from: string; to: string; tone?: 'default' | 'accent' | 'good' | 'warn' | 'bad'; label?: string };

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

export function BlueprintGraph({ title = '节点蓝图', summary, nodes, edges }: { title?: string; summary?: string; nodes: BlueprintNode[]; edges: BlueprintEdge[] }) {
  const visibleNodes = nodes.slice(0, 96);
  const visibleNodeIds = useMemo(() => new Set(visibleNodes.map(node => node.id)), [visibleNodes]);
  const visibleEdges = edges.filter(edge => visibleNodeIds.has(edge.from) && visibleNodeIds.has(edge.to)).slice(0, 160);
  const hiddenCount = Math.max(0, nodes.length - visibleNodes.length);
  const layout = useMemo(() => layoutBlueprint(visibleNodes), [visibleNodes]);
  if (!nodes.length) return <div className="blueprint-panel empty"><div className="blueprint-head"><span>{title}</span></div><p>没有可预览的节点。</p></div>;
  const width = Math.max(520, Math.min(3600, (layout.maxColumn + 1) * 190 + 40));
  const height = Math.max(180, Math.min(2600, (layout.maxRow + 1) * 112 + 34));
  return <div className="blueprint-panel">
    <div className="blueprint-head">
      <span>{title}</span>
      {summary && <code>{hiddenCount ? `${summary} · 显示前 ${visibleNodes.length} 个` : summary}</code>}
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
  if (typeof value === 'boolean') return value ? '是' : '否';
  if (Array.isArray(value)) return value.length > 3 ? `${value.slice(0, 3).join(', ')} +${value.length - 3}` : value.join(', ');
  if (typeof value === 'object') return `${Object.keys(value as Record<string, unknown>).length} 项`;
  return String(value);
}
