import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { MiniText, asRecord, injectExtensionStyles, localeText, materialFromItemSource, materialShortName, materialUrls, subscribeTextureBases, type ConfigPreviewProps } from 'emaki-web-console';
import advancementPreviewStyles from './AdvancementPreview.css?raw';

const copy = localeText;
const NODE_SIZE = 46;
const X_STEP = 96;
const Y_STEP = 76;

type AdvancementFrame = 'task' | 'goal' | 'challenge';
type AdvancementNode = {
  id: string;
  icon: string;
  title: string;
  description: string;
  frame: AdvancementFrame;
  parent: string;
  x: number;
  y: number;
  hasCoordinates: boolean;
  toast: boolean;
  announce: boolean;
  hidden: boolean;
};
type PositionedNode = AdvancementNode & { left: number; top: number };
type AdvancementLink = { id: string; d: string };
type AdvancementLayout = {
  nodes: PositionedNode[];
  links: AdvancementLink[];
  width: number;
  height: number;
  mode: 'coordinates' | 'tree';
};

export function installAdvancementPreviewStyles(): void {
  injectExtensionStyles('emakicodex-advancement-preview', advancementPreviewStyles);
}

export function AdvancementPreview({ data, sourceDirty }: ConfigPreviewProps) {
  const pageId = textValue(data.page_id, 'advancement_page');
  const pageTitle = textValue(data.title, copy('未命名成就页', 'Untitled advancement page'));
  const background = textValue(data.background, 'minecraft:textures/gui/advancements/backgrounds/stone.png');
  const configuredRoot = textValue(data.root);
  const nodes = useMemo(() => advancementEntries(data.advancements), [data.advancements]);
  const layout = useMemo(() => createAdvancementLayout(nodes, configuredRoot), [nodes, configuredRoot]);
  const rootId = configuredRoot && nodes.some(node => node.id === configuredRoot) ? configuredRoot : nodes.find(node => !node.parent)?.id ?? nodes[0]?.id ?? '';
  const nodeIdentity = nodes.map(node => node.id).join('\u0000');
  const [selectedId, setSelectedId] = useState('');

  useEffect(() => {
    if (selectedId && !nodes.some(node => node.id === selectedId)) setSelectedId('');
  }, [nodeIdentity, selectedId]);

  const selectedNode = nodes.find(node => node.id === selectedId);
  const theme = backgroundTheme(background);

  return <section className="advancement-preview">
    <header className="advancement-preview__header">
      <div>
        <h3>{copy('原版成就预览', 'Vanilla advancement preview')}</h3>
        <p>{copy('使用当前草稿实时生成节点、父子连线与坐标布局；点击节点可固定物品提示。', 'Uses the current draft to render nodes, parent links, and coordinates in real time. Select a node to pin its item tooltip.')}</p>
      </div>
      <div className="advancement-preview__summary" aria-label={copy('成就页摘要', 'Advancement page summary')}>
        <code>{pageId}</code>
        <span>{nodes.length} {copy('节点', 'nodes')}</span>
        <span>{layout.mode === 'coordinates' ? copy('坐标布局', 'Coordinate layout') : copy('父子树布局', 'Parent tree layout')}</span>
        {sourceDirty ? <span className="is-dirty">{copy('源码草稿', 'Source draft')}</span> : null}
      </div>
    </header>

    <div className="advancement-preview__window" data-background-theme={theme}>
      <div className="advancement-preview__window-title"><span>{copy('成就', 'Advancements')}</span><strong><MiniText value={pageTitle} /></strong></div>
      <div className="advancement-preview__tabs" aria-hidden="true">
        <span className="advancement-preview__tab is-active"><ItemMaterialIcon source={nodes.find(node => node.id === rootId)?.icon ?? 'minecraft-book'} /></span>
      </div>
      <div className="advancement-preview__viewport">
        {nodes.length ? <div className="advancement-preview__canvas" style={{ width: layout.width, height: layout.height }}>
          <svg className="advancement-preview__links" viewBox={`0 0 ${layout.width} ${layout.height}`} aria-hidden="true">
            {layout.links.map(link => <g key={link.id}>
              <path className="advancement-preview__link-border" d={link.d} />
              <path className="advancement-preview__link-core" d={link.d} />
            </g>)}
          </svg>
          {layout.nodes.map(node => <button
            key={node.id}
            type="button"
            className={`advancement-preview__node frame-${node.frame}${node.id === selectedId ? ' is-selected' : ''}${node.hidden ? ' is-hidden-node' : ''}`}
            style={{ left: node.left, top: node.top } as CSSProperties}
            onClick={() => setSelectedId(current => current === node.id ? '' : node.id)}
            aria-label={`${plainMiniText(node.title) || node.id} · ${node.frame}`}
            aria-pressed={node.id === selectedId}
          >
            <span className="advancement-preview__node-bevel"><ItemMaterialIcon source={node.icon} /></span>
            <AdvancementTooltip node={node} pinned={node.id === selectedId} />
          </button>)}
        </div> : <div className="advancement-preview__empty">
          <strong>{copy('还没有成就节点', 'No advancement nodes yet')}</strong>
          <span>{copy('在下方“成就节点”对象中新增节点后，这里会立即显示原版布局预览。', 'Add a child under the Advancement nodes object below to render it immediately in this vanilla-style preview.')}</span>
        </div>}
      </div>
      <footer className="advancement-preview__status">
        <code>{background}</code>
        {selectedNode ? <span>{selectedNode.id} · {frameLabel(selectedNode.frame)}</span> : null}
      </footer>
    </div>
  </section>;
}

function AdvancementTooltip({ node, pinned }: { node: AdvancementNode; pinned: boolean }) {
  const flags = [node.toast ? copy('Toast', 'Toast') : '', node.announce ? copy('广播', 'Announce') : '', node.hidden ? copy('隐藏', 'Hidden') : ''].filter(Boolean);
  return <span className={`advancement-preview__tooltip${pinned ? ' is-pinned' : ''}`} role="tooltip">
    <strong><MiniText value={node.title || node.id} /></strong>
    <span className="advancement-preview__tooltip-description"><MiniText value={node.description || copy('暂无描述', 'No description')} /></span>
    <span className="advancement-preview__tooltip-flags">{flags.length ? flags.join(' · ') : frameLabel(node.frame)}</span>
  </span>;
}

function ItemMaterialIcon({ source }: { source: string }) {
  const material = materialFromItemSource(source);
  const urls = materialUrls(material);
  const [failedIndex, setFailedIndex] = useState(0);
  const [, refreshTextures] = useState(0);

  useEffect(() => setFailedIndex(0), [material]);
  useEffect(() => subscribeTextureBases(() => { setFailedIndex(0); refreshTextures(version => version + 1); }), []);

  const url = urls[failedIndex];
  return <span className="advancement-preview__item" title={material}>
    {url ? <img src={url} alt="" draggable={false} onError={() => setFailedIndex(index => index + 1)} /> : <span>{materialShortName(material) || '?'}</span>}
  </span>;
}

function advancementEntries(value: unknown): AdvancementNode[] {
  return Object.entries(asRecord(value)).map(([id, raw]) => {
    const node = asRecord(raw);
    const frame = textValue(node.frame, 'task').toLowerCase();
    return {
      id,
      icon: textValue(node.icon, 'minecraft-book'),
      title: textValue(node.title, id),
      description: textValue(node.description),
      frame: frame === 'goal' || frame === 'challenge' ? frame : 'task',
      parent: textValue(node.parent),
      x: numberValue(node.x),
      y: numberValue(node.y),
      hasCoordinates: Object.prototype.hasOwnProperty.call(node, 'x') || Object.prototype.hasOwnProperty.call(node, 'y'),
      toast: booleanValue(node.toast, true),
      announce: booleanValue(node.announce, false),
      hidden: booleanValue(node.hidden, false)
    };
  });
}

function createAdvancementLayout(nodes: AdvancementNode[], rootId: string): AdvancementLayout {
  if (!nodes.length) return { nodes: [], links: [], width: 680, height: 270, mode: 'tree' };
  const coordinateMode = nodes.some(node => node.hasCoordinates);
  const logical = coordinateMode ? coordinatePositions(nodes) : treePositions(nodes, rootId);
  const xs = [...logical.values()].map(position => position.x);
  const ys = [...logical.values()].map(position => position.y);
  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const positioned = nodes.map(node => {
    const point = logical.get(node.id) ?? { x: 0, y: 0 };
    return { ...node, left: 78 + (point.x - minX) * X_STEP, top: 54 + (point.y - minY) * Y_STEP };
  });
  const byId = new Map(positioned.map(node => [node.id, node]));
  const maxLeft = Math.max(...positioned.map(node => node.left));
  const maxTop = Math.max(...positioned.map(node => node.top));
  const width = Math.max(680, maxLeft + 180);
  const height = Math.max(270, maxTop + 140);
  const links = positioned.flatMap(node => {
    const parent = byId.get(node.parent);
    if (!parent) return [];
    const startX = parent.left + NODE_SIZE / 2;
    const startY = parent.top + NODE_SIZE / 2;
    const endX = node.left + NODE_SIZE / 2;
    const endY = node.top + NODE_SIZE / 2;
    const middleX = startX + (endX - startX) / 2;
    return [{ id: `${parent.id}:${node.id}`, d: `M ${startX} ${startY} H ${middleX} V ${endY} H ${endX}` }];
  });
  return { nodes: positioned, links, width, height, mode: coordinateMode ? 'coordinates' : 'tree' };
}

function coordinatePositions(nodes: AdvancementNode[]): Map<string, { x: number; y: number }> {
  return new Map(nodes.map(node => [node.id, { x: node.x, y: node.y }]));
}

function treePositions(nodes: AdvancementNode[], configuredRoot: string): Map<string, { x: number; y: number }> {
  const byId = new Map(nodes.map(node => [node.id, node]));
  const children = new Map<string, string[]>();
  nodes.forEach(node => {
    if (!node.parent || !byId.has(node.parent)) return;
    children.set(node.parent, [...(children.get(node.parent) ?? []), node.id]);
  });
  const positions = new Map<string, { x: number; y: number }>();
  const visiting = new Set<string>();
  let nextRow = 0;

  const place = (id: string, depth: number): number => {
    const current = positions.get(id);
    if (current) return current.y;
    if (visiting.has(id)) {
      const row = nextRow++;
      positions.set(id, { x: depth, y: row });
      return row;
    }
    visiting.add(id);
    const rows = (children.get(id) ?? []).map(childId => place(childId, depth + 1));
    const row = rows.length ? rows.reduce((sum, value) => sum + value, 0) / rows.length : nextRow++;
    positions.set(id, { x: depth, y: row });
    visiting.delete(id);
    return row;
  };

  const roots = nodes.filter(node => node.id === configuredRoot || !node.parent || !byId.has(node.parent));
  roots.forEach(node => place(node.id, 0));
  nodes.forEach(node => { if (!positions.has(node.id)) place(node.id, 0); });
  return positions;
}

function backgroundTheme(background: string): string {
  const normalized = background.toLowerCase();
  if (normalized.includes('nether')) return 'nether';
  if (normalized.includes('end')) return 'end';
  if (normalized.includes('adventure')) return 'adventure';
  if (normalized.includes('husbandry')) return 'husbandry';
  return 'stone';
}

function frameLabel(frame: AdvancementFrame): string {
  if (frame === 'goal') return copy('目标', 'Goal');
  if (frame === 'challenge') return copy('挑战', 'Challenge');
  return copy('进度', 'Task');
}

function textValue(value: unknown, fallback = ''): string {
  const text = String(value ?? '').trim();
  return text || fallback;
}

function numberValue(value: unknown): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function booleanValue(value: unknown, fallback: boolean): boolean {
  return typeof value === 'boolean' ? value : fallback;
}

function plainMiniText(value: string): string {
  return value.replace(/<[^>]*>/g, '').trim();
}
