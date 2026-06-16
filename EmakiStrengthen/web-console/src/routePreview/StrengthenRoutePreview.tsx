import React, { useEffect, useMemo, useState } from 'react';
import { Button, InlineError, localeText, type ConfigPreviewProps } from 'emaki-web-console';
import './StrengthenRoutePreview.css';

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

  return <section className="strengthen-route-preview">
    <div className="strengthen-route-preview__header">
      <div>
        <h3 className="strengthen-route-preview__title">{copy('强化路线蓝图', 'Strengthen route blueprint')}</h3>
        <p className="strengthen-route-preview__hint">{copy('基于服务器已加载配方生成，只读预览路线、材料、成功率与属性累计。结构化配置仍可继续编辑，预览只反映运行时计算结果。', 'Generated from the loaded server recipe; read-only preview of route, materials, success rates, and cumulative stats. Structured config remains editable; the preview reflects runtime calculation only.')}</p>
      </div>
      <div className="strengthen-route-preview__actions">
        <Button size="sm" onClick={load} disabled={loading || !recipeId}>{loading ? copy('加载中...', 'Loading...') : copy('刷新预览', 'Refresh preview')}</Button>
        <Button size="sm" variant="soft" onClick={exportJson} disabled={!result}>{copy('导出 JSON', 'Export JSON')}</Button>
      </div>
    </div>
    {!recipeId ? <div className="strengthen-route-preview__empty">{copy('当前文件未配置 id，无法匹配运行时配方。', 'This file has no id, so no loaded recipe can be matched.')}</div> : null}
    {error ? <PluginApiError route="route-preview" message={error} /> : null}
    {result ? <>
      <div className="strengthen-route-preview__summary" aria-label={copy('路线摘要', 'Route summary')}>
        <span>{copy('配方', 'Recipe')}: <strong>{result.displayName || result.recipeId}</strong></span>
        <span>{copy('节点', 'Nodes')}: <strong>{result.nodes.length}</strong></span>
        <span>{copy('连线', 'Edges')}: <strong>{result.edges.length}</strong></span>
        <span>{copy('分支', 'Branching')}: <strong>{result.branching ? copy('是', 'Yes') : copy('否', 'No')}</strong></span>
      </div>
      {(result.warnings ?? []).length ? <div className="strengthen-route-preview__warning" role="status">{result.warnings?.map(warning => warning.message).join(' / ')}</div> : null}
      <div className="strengthen-route-preview__graph-scroll">
        <RouteSvg nodes={result.nodes} edges={result.edges} selectedId={selected?.id ?? ''} />
      </div>
      <RouteNodeSelector nodes={result.nodes} selectedId={selected?.id ?? ''} onSelect={setSelectedId} />
      <div className="strengthen-route-preview__detail-grid">
        <RouteTable nodes={result.nodes} selectedId={selected?.id ?? ''} onSelect={setSelectedId} />
        {selected ? <RouteNodeDetail node={selected} /> : null}
      </div>
    </> : <div className="strengthen-route-preview__empty">{copy('暂无路线数据。', 'No route data.')}</div>}
  </section>;
}

function PluginApiError({ route, message }: { route: string; message: string }) {
  return <InlineError>
    <strong>{copy('强化路线预览暂不可用。', 'Strengthen route preview is unavailable.')}</strong>
    <p>{copy(`插件 API strengthen/${route} 请求失败；当前 YAML 仍可继续编辑，保存不依赖此只读预览。`, `Plugin API strengthen/${route} failed. The current YAML remains editable; saving does not depend on this read-only preview.`)}</p>
    <details className="strengthen-route-preview__diagnostic">
      <summary>{copy('开发者诊断', 'Developer diagnostics')}</summary>
      <pre>{message}</pre>
    </details>
  </InlineError>;
}

function RouteSvg({ nodes, edges, selectedId }: { nodes: RouteNode[]; edges: RouteEdge[]; selectedId: string }) {
  const width = 920;
  const height = Math.max(240, 90 + nodes.length * 18);
  const positions = routePositions(nodes, width, height);
  return <svg viewBox={`0 0 ${width} ${height}`} className="strengthen-route-preview__graph" role="img" aria-label={copy('强化路线图，节点可在下方列表和表格中选择。', 'Strengthen route graph. Nodes can be selected in the list and table below.')}>
    {edges.map((edge, index) => {
      const from = positions.get(edge.from);
      const to = positions.get(edge.to);
      if (!from || !to) return null;
      const branch = edge.type === 'branch';
      return <g key={`${edge.from}-${edge.to}-${index}`}>
        <line x1={from.x} y1={from.y} x2={to.x} y2={to.y} stroke={branch ? 'var(--amber)' : 'color-mix(in oklch, var(--muted) 62%, transparent)'} strokeWidth={branch ? 3 : 2} strokeDasharray={branch ? '5 4' : undefined} />
        {edge.label ? <text x={(from.x + to.x) / 2} y={(from.y + to.y) / 2 - 6} fill="var(--amber)" fontSize="12" textAnchor="middle">{stripMini(edge.label)}</text> : null}
      </g>;
    })}
    {nodes.map(node => {
      const point = positions.get(node.id)!;
      const selected = node.id === selectedId;
      return <g key={node.id} aria-hidden="true">
        <circle cx={point.x} cy={point.y} r={selected ? 18 : 15} fill={selected ? 'var(--accent)' : 'var(--surface-2)'} stroke={node.branchPath ? 'var(--amber)' : 'var(--accent-strong)'} strokeWidth={selected ? 3 : 2} />
        <text x={point.x} y={point.y + 4} textAnchor="middle" fontSize="12" fill={selected ? 'var(--bg)' : 'var(--text)'} fontWeight={700}>{node.star}</text>
        <text x={point.x} y={point.y + 34} textAnchor="middle" fontSize="11" fill="var(--muted)">{shortBranch(node.branchPath || 'root')}</text>
      </g>;
    })}
  </svg>;
}

function RouteNodeSelector({ nodes, selectedId, onSelect }: { nodes: RouteNode[]; selectedId: string; onSelect: (id: string) => void }) {
  if (!nodes.length) return null;
  return <div className="strengthen-route-preview__node-actions" aria-label={copy('选择路线节点', 'Select route node')}>
    {nodes.map(node => <button key={node.id} type="button" className="strengthen-route-preview__node-button" aria-pressed={node.id === selectedId} onClick={() => onSelect(node.id)}>
      ★{node.star} · {shortBranch(node.branchPath || 'root')}
    </button>)}
  </div>;
}

function RouteTable({ nodes, selectedId, onSelect }: { nodes: RouteNode[]; selectedId: string; onSelect: (id: string) => void }) {
  return <div className="strengthen-route-preview__table-scroll"><table className="strengthen-route-preview__table">
    <thead><tr><th className="strengthen-route-preview__select-cell">{copy('选择', 'Select')}</th><th>★</th><th>{copy('分支', 'Branch')}</th><th>{copy('成功率', 'Rate')}</th><th>{copy('材料', 'Materials')}</th><th>{copy('增量', 'Delta')}</th></tr></thead>
    <tbody>{nodes.map(node => <tr key={node.id} className={node.id === selectedId ? 'strengthen-route-preview__row--selected' : ''}>
      <td className="strengthen-route-preview__select-cell"><button type="button" className="strengthen-route-preview__select-button" aria-pressed={node.id === selectedId} onClick={() => onSelect(node.id)}>{node.id === selectedId ? copy('当前', 'Now') : copy('选择', 'Pick')}</button></td>
      <td>{node.star}</td><td><code>{node.branchPath || 'root'}</code></td><td>{formatNumber(node.successRate)}%</td><td>{node.materials.map(material => `${material.item} x${material.amount}`).join(', ') || '-'}</td><td>{kvSummary(node.statsDelta)}</td>
    </tr>)}</tbody>
  </table></div>;
}

function RouteNodeDetail({ node }: { node: RouteNode }) {
  return <aside className="strengthen-route-preview__detail">
    <h4>★{node.star} · {node.stageName || node.branchName || node.branchId}</h4>
    <p className="strengthen-route-preview__detail-note">{copy('分支路径', 'Branch path')}: <code>{node.branchPath || 'root'}</code></p>
    <div className="strengthen-route-preview__detail-meta"><span>{copy('成功率', 'Success rate')}: <strong>{formatNumber(node.successRate)}%</strong></span><span>{copy('成功动作', 'Success actions')}: {node.hasSuccessActions ? copy('有', 'Yes') : copy('无', 'No')}</span><span>{copy('失败动作', 'Failure actions')}: {node.hasFailureActions ? copy('有', 'Yes') : copy('无', 'No')}</span></div>
    <h5>{copy('材料', 'Materials')}</h5><ul>{node.materials.map((material, index) => <li key={index}><code>{material.item}</code> x{material.amount}{material.optional ? ` · ${copy('可选', 'Optional')}` : ''}{material.protection ? ` · ${copy('保护', 'Protection')}` : ''}{material.temperBoost ? ` · +${material.temperBoost} ${copy('锻印', 'Temper')}` : ''}</li>)}</ul>
    <h5>{copy('本级变量增量', 'Stage stat delta')}</h5><pre>{JSON.stringify(node.statsDelta, null, 2)}</pre>
    <h5>{copy('累计变量', 'Cumulative stats')}</h5><pre>{JSON.stringify(node.cumulativeStats, null, 2)}</pre>
    <h5>{copy('累计 EA 属性', 'Cumulative EA attributes')}</h5><pre>{JSON.stringify(node.cumulativeAttributes, null, 2)}</pre>
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
