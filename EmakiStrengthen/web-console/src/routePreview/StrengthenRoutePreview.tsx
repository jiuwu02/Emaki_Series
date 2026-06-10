import React, { useEffect, useMemo, useState } from 'react';
import { localeText, type ConfigPreviewProps } from 'emaki-web-console';

const copy = localeText;

type RouteMaterial = { item: string; amount: number; optional: boolean; protection: boolean; temperBoost: number };
type RouteNode = { id: string; star: number; branchPath: string; branchId: string; branchName: string; stageName: string; successRate: number; materials: RouteMaterial[]; statsDelta: Record<string, number>; cumulativeStats: Record<string, number>; cumulativeAttributes: Record<string, number>; skillIds: string[]; hasSuccessActions: boolean; hasFailureActions: boolean };
type RouteEdge = { from: string; to: string; type: string; label: string };
type RoutePreviewResult = { recipeId: string; displayName: string; branching: boolean; maxStar: number; nodes: RouteNode[]; edges: RouteEdge[]; warnings?: { type: string; message: string }[] };

export function StrengthenRoutePreview({ api, data }: ConfigPreviewProps) {
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
