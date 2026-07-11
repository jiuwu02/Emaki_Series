import { memo, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type Dispatch, type KeyboardEvent, type SetStateAction } from 'react';
import { isGlobPath, treeDirtyKey } from '../documentPaths';
import { getModuleLocaleBundles, t } from '../i18n';
import { getFileKindLabel } from '../registry';
import { treeNodeDisplayComment, treeNodeDisplayLabel } from '../lib';
import { DisclosureChevron } from '../components';
import type { RegistryTreeNode, WebRegistry, WebRegistryModule } from '../types';

export type TreeSelection = { moduleId: string; fileId: string; scriptPath?: string; refreshKey?: number };

type TreeIndex = {
  roots: RegistryTreeNode[];
  rootIds: string[];
  nodeById: Map<string, RegistryTreeNode>;
  childrenById: Map<string, RegistryTreeNode[]>;
  parentById: Map<string, string>;
  searchTextById: Map<string, string>;
};

type VisibleTreeRow = {
  id: string;
  node: RegistryTreeNode;
  level: number;
  isLast: boolean;
  hasChildren: boolean;
  isModule: boolean;
  isFolder: boolean;
  isGlob: boolean;
  isOpen: boolean;
  active: boolean;
  dirty: boolean;
  displayLabel: string;
  displayComment: string;
  kindLabel: string;
  ariaLabel: string;
  i18nCount?: number;
};

type TreeSearchState = {
  query: string;
  visibleIds: Set<string> | null;
};

export function WorkspaceTree({ registry, selected, expanded, dirtyKeys = new Set<string>(), localeVersion = 0, setExpanded, onSelect, onOpenI18n, onCreateFile, onDeleteFile }: {
  registry: WebRegistry | null;
  selected: TreeSelection | null;
  expanded: Record<string, boolean>;
  dirtyKeys?: ReadonlySet<string>;
  localeVersion?: number;
  setExpanded: Dispatch<SetStateAction<Record<string, boolean>>>;
  onSelect: (v: TreeSelection) => void;
  onOpenI18n?: (target: { moduleId: string }) => void;
  onCreateFile?: (node: RegistryTreeNode) => void;
  onDeleteFile?: (node: RegistryTreeNode) => void;
}) {
  const [query, setQuery] = useState('');
  const normalizedQuery = normalizeQuery(query);
  const treeRef = useRef<HTMLDivElement | null>(null);
  const rowRefs = useRef(new Map<string, HTMLButtonElement>());

  const treeIndex = useMemo(() => registry ? buildTreeIndex(registry) : null, [registry, localeVersion]);
  const searchState = useMemo(() => treeIndex ? buildSearchState(treeIndex, normalizedQuery) : { query: normalizedQuery, visibleIds: null }, [treeIndex, normalizedQuery, localeVersion]);
  const dirtyNodeIds = useMemo(() => treeIndex ? collectDirtyNodeIds(treeIndex, dirtyKeys) : new Set<string>(), [treeIndex, dirtyKeys]);
  const rows = useMemo(() => treeIndex ? flattenVisibleRows(treeIndex, expanded, selected, dirtyNodeIds, searchState) : [], [treeIndex, expanded, selected?.moduleId, selected?.fileId, selected?.scriptPath, dirtyNodeIds, searchState, localeVersion]);
  const rowIndexById = useMemo(() => new Map(rows.map((row, index) => [row.id, index])), [rows]);

  const toggle = useCallback((id: string) => setExpanded((current) => ({ ...current, [id]: !current[id] })), [setExpanded]);
  const openNode = useCallback((id: string) => setExpanded((current) => current[id] ? current : ({ ...current, [id]: true })), [setExpanded]);
  const closeNode = useCallback((id: string) => setExpanded((current) => current[id] === false ? current : ({ ...current, [id]: false })), [setExpanded]);

  const focusRow = useCallback((id: string) => {
    const element = rowRefs.current.get(id);
    if (!element) return;
    element.scrollIntoView({ block: 'nearest' });
    element.focus();
  }, []);

  const handleKeyDown = useCallback((event: KeyboardEvent<HTMLDivElement>) => {
    handleTreeKeyDown(event, rows, rowIndexById, openNode, closeNode, focusRow);
  }, [rows, rowIndexById, openNode, closeNode, focusRow]);

  if (!registry || !treeIndex) return <div className="tree-empty" role="status">{t('core.tree.loading')}</div>;

  return <>
    <label className="tree-search">
      <span className="sr-only">{t('core.tree.search')}</span>
      <SearchIcon />
      <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('core.tree.search')} />
    </label>
    <div
      ref={treeRef}
      className="tree"
      role="tree"
      aria-label={t('core.tree.aria')}
      onKeyDown={handleKeyDown}
    >
      {rows.length > 0
        ? <div className="tree-list">
            {rows.map((row) => <TreeRow
              key={row.id}
              row={row}
              setRowRef={setRowRef(rowRefs)}
              onToggle={toggle}
              onSelect={onSelect}
              onOpenI18n={onOpenI18n}
              onCreateFile={onCreateFile}
              onDeleteFile={onDeleteFile}
            />)}
          </div>
        : normalizedQuery ? <div className="tree-empty tree-empty-search" role="status">{t('core.tree.noResults')}</div> : null}
    </div>
  </>;
}

const TreeRow = memo(function TreeRow({ row, setRowRef, onToggle, onSelect, onOpenI18n, onCreateFile, onDeleteFile }: {
  row: VisibleTreeRow;
  setRowRef: (id: string, element: HTMLButtonElement | null) => void;
  onToggle: (id: string) => void;
  onSelect: (v: TreeSelection) => void;
  onOpenI18n?: (target: { moduleId: string }) => void;
  onCreateFile?: (node: RegistryTreeNode) => void;
  onDeleteFile?: (node: RegistryTreeNode) => void;
}) {
  const { node } = row;
  const rowStyle = indentStyle(row.level);

  if (row.isModule) {
    return <div className="tree-module" role="none">
      <div className="tree-module-row">
        <button
          ref={(element) => setRowRef(row.id, element)}
          className={`tree-folder ${row.dirty ? 'dirty' : ''}`}
          role="treeitem"
          aria-level={row.level + 1}
          aria-expanded={row.isOpen}
          data-tree-node-id={row.id}
          onClick={() => onToggle(row.id)}
        >
          <Icon svg={node.icon} /> <DisclosureChevron open={row.isOpen} className="folder-arrow" /> <span className="tree-label">{row.displayLabel}</span><DirtyDot dirty={row.dirty} />
        </button>
        {onOpenI18n && node.id && <ModuleI18nButton moduleId={node.id} moduleName={row.displayLabel} count={row.i18nCount ?? 0} onOpen={onOpenI18n} />}
      </div>
    </div>;
  }

  if (row.hasChildren) {
    return <div className="tree-file-folder" role="none">
      <div className="tree-file-row" style={rowStyle} data-tree-level={row.level} data-tree-branch={row.level > 1 ? (row.isLast ? 'elbow' : 'tee') : undefined}>
        {row.level > 1 && <IndentGuide branch={row.isLast ? 'elbow' : 'tee'} />}
        <button
          ref={(element) => setRowRef(row.id, element)}
          className={`tree-file folder-toggle ${row.isGlob ? 'glob-node' : ''} ${row.active ? 'active' : ''} ${row.dirty ? 'dirty' : ''}`}
          role="treeitem"
          aria-level={row.level + 1}
          aria-expanded={row.isOpen}
          aria-selected={row.active || undefined}
          aria-label={row.ariaLabel}
          title={row.isGlob ? t('core.tree.globHint') : undefined}
          data-tree-node-id={row.id}
          onClick={() => onToggle(row.id)}
        >
          <DisclosureChevron open={row.isOpen} className="folder-arrow" /><span className="tree-label">{row.displayLabel}</span><DirtyDot dirty={row.dirty} />
        </button>
        {onCreateFile && (row.isGlob || row.isFolder || !row.hasChildren) && <button type="button" className="tree-file-action" title={t('core.tree.createFile')} aria-label={t('core.tree.createFile')} onClick={(event) => { event.stopPropagation(); onCreateFile(node); }}>+</button>}
      </div>
    </div>;
  }

  const childSelectionPath = selectableChildPath(node);
  const canSelect = Boolean(node.moduleId && node.fileId && !row.isGlob && !row.isFolder && (node.type === 'file' || childSelectionPath !== undefined));
  const rowClass = row.level > 1 ? 'tree-child-row' : 'tree-file-row';
  return <div className={rowClass} role="none" style={rowStyle}>
    {row.level > 1 && <IndentGuide branch={row.isLast ? 'elbow' : 'tee'} />}
    <button
      ref={(element) => setRowRef(row.id, element)}
      className={row.level > 1 ? `tree-child ${row.isGlob ? 'glob-node' : ''} ${row.active ? 'active' : ''} ${row.dirty ? 'dirty' : ''}` : `tree-file ${row.isGlob ? 'glob-node' : ''} ${row.active ? 'active' : ''} ${row.dirty ? 'dirty' : ''}`}
      role="treeitem"
      aria-level={row.level + 1}
      aria-selected={row.active}
      aria-label={row.ariaLabel}
      title={row.isGlob ? t('core.tree.globHint') : undefined}
      data-tree-node-id={row.id}
      onClick={() => {
        if (!canSelect || !node.moduleId || !node.fileId) return;
        const selection = { moduleId: node.moduleId, fileId: node.fileId, scriptPath: childSelectionPath };
        onSelect(selection);
      }}
      disabled={!canSelect}
    >
      <span className="tree-label">{row.displayLabel}</span><DirtyDot dirty={row.dirty} />
    </button>
    {onDeleteFile && node.childPath && !isGlobPath(node.childPath) && <button type="button" className="tree-file-action danger" title={t('core.tree.deleteFile')} aria-label={t('core.tree.deleteFile')} onClick={(event) => { event.stopPropagation(); onDeleteFile(node); }}>×</button>}
  </div>;
});

function setRowRef(ref: React.MutableRefObject<Map<string, HTMLButtonElement>>) {
  return (id: string, element: HTMLButtonElement | null) => {
    if (element) ref.current.set(id, element);
    else ref.current.delete(id);
  };
}

function handleTreeKeyDown(event: KeyboardEvent<HTMLDivElement>, rows: VisibleTreeRow[], rowIndexById: Map<string, number>, openNode: (id: string) => void, closeNode: (id: string) => void, focusRow: (id: string) => void) {
  const current = event.target instanceof HTMLElement ? event.target.closest<HTMLElement>('[role="treeitem"]') : null;
  if (!current) return;
  const id = current.dataset.treeNodeId;
  if (!id) return;
  const index = rowIndexById.get(id);
  if (index == null) return;
  const focusAt = (nextIndex: number) => {
    const clamped = Math.max(0, Math.min(rows.length - 1, nextIndex));
    const next = rows[clamped];
    if (next) focusRow(next.id);
  };
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    focusAt(index + 1);
  } else if (event.key === 'ArrowUp') {
    event.preventDefault();
    focusAt(index - 1);
  } else if (event.key === 'Home') {
    event.preventDefault();
    focusAt(0);
  } else if (event.key === 'End') {
    event.preventDefault();
    focusAt(rows.length - 1);
  } else if (event.key === 'ArrowRight' && current.getAttribute('aria-expanded') === 'false') {
    event.preventDefault();
    openNode(id);
  } else if (event.key === 'ArrowLeft' && current.getAttribute('aria-expanded') === 'true') {
    event.preventDefault();
    closeNode(id);
  }
}

export function fileKindLabel(kind: string | undefined): string {
  if (!kind) return t('core.kind.config');
  const upper = kind.toUpperCase();
  if (upper === 'CONFIG') return t('core.kind.config');
  if (upper === 'GUI') return t('core.kind.gui');
  if (upper === 'ITEM') return t('core.kind.item');
  if (upper === 'GEM') return t('core.kind.gem');
  const registered = getFileKindLabel(upper);
  if (registered) return registered;
  if (upper === 'SCRIPT') return t('core.kind.script');
  if (upper === 'FILE') return t('core.kind.file');
  return kind;
}

function moduleI18nCount(moduleId?: string): number {
  if (!moduleId) return 0;
  return getModuleLocaleBundles(moduleId).reduce((sum, bundle) => sum + bundle.count, 0);
}

function ModuleI18nButton({ moduleId, moduleName, count, onOpen }: { moduleId: string; moduleName: string; count: number; onOpen: (target: { moduleId: string }) => void }) {
  const label = t('core.i18n.openAria', { module: moduleName || moduleId });
  return <button
    type="button"
    className={`tree-i18n-button tree-i18n-button--module ${count > 0 ? 'has-bundle' : ''}`}
    title={label}
    aria-label={label}
    onClick={(event) => { event.stopPropagation(); onOpen({ moduleId }); }}
  ><LanguageFileIcon />{count > 0 && <small>{count}</small>}</button>;
}

function DirtyDot({ dirty }: { dirty: boolean }) {
  return dirty ? <span className="tree-dirty-dot" title={t('core.tree.dirty')} aria-hidden="true" /> : null;
}

function IndentGuide({ branch }: { branch: 'tee' | 'elbow' }) {
  return <svg className={`indent-guide indent-guide--${branch}`} viewBox="0 0 16 20" aria-hidden="true" focusable="false">
    <path d={branch === 'tee' ? 'M6 0v20M6 10h8' : 'M6 0v10h8'} />
  </svg>;
}

function SearchIcon() {
  return <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M7.1 2.4a4.7 4.7 0 1 1 0 9.4 4.7 4.7 0 0 1 0-9.4Zm3.35 8.05 3.15 3.15" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" /></svg>;
}

function LanguageFileIcon() {
  return <svg viewBox="0 0 18 18" aria-hidden="true"><path d="M4.5 2.5h5.9l3.1 3.1v9.9h-9z" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinejoin="round" /><path d="M10.2 2.8v3.1h3" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" /><path d="M6.2 8.4h5.4M6.2 10.8h3.6" fill="none" stroke="currentColor" strokeWidth="1.3" strokeLinecap="round" /></svg>;
}

function Icon({ svg }: { svg?: string }) {
  if (!svg) return null;
  return <span className="module-icon" aria-hidden="true" dangerouslySetInnerHTML={{ __html: sanitizeSvg(svg) }} />;
}

// Sanitize a module-provided SVG icon with a DOM allowlist rather than regex string-stripping.
// Plugin icons are semi-trusted (declared in web-console.yml), but parsing + walking the tree and
// dropping any non-allowlisted element/attribute is far harder to bypass than pattern replacement.
const SVG_ALLOWED_TAGS = new Set(['svg', 'g', 'path', 'circle', 'ellipse', 'rect', 'line', 'polyline', 'polygon', 'defs', 'lineargradient', 'radialgradient', 'stop', 'title', 'desc', 'use', 'clippath', 'mask']);
const SVG_EVENT_ATTR = /^on/i;

function sanitizeSvg(svg: string): string {
  if (typeof window === 'undefined' || typeof DOMParser === 'undefined') return '';
  if (!svg.trim().toLowerCase().startsWith('<svg')) return '';
  try {
    const doc = new DOMParser().parseFromString(svg, 'image/svg+xml');
    if (doc.querySelector('parsererror')) return '';
    const root = doc.documentElement;
    if (!root || root.tagName.toLowerCase() !== 'svg') return '';
    if (!scrubSvgNode(root)) return '';
    return root.outerHTML;
  } catch {
    return '';
  }
}

function scrubSvgNode(element: Element): boolean {
  if (!SVG_ALLOWED_TAGS.has(element.tagName.toLowerCase())) {
    element.remove();
    return false;
  }
  for (const attr of Array.from(element.attributes)) {
    const name = attr.name.toLowerCase();
    const value = attr.value.trim().toLowerCase();
    const isHref = name === 'href' || name === 'xlink:href';
    if (SVG_EVENT_ATTR.test(name) || (isHref && !value.startsWith('#')) || value.includes('javascript:') || value.includes('data:text/html')) {
      element.removeAttribute(attr.name);
    }
  }
  for (const child of Array.from(element.children)) scrubSvgNode(child);
  return true;
}

function buildTreeIndex(registry: WebRegistry): TreeIndex {
  const pathIndex = buildGlobChildPathIndex(registry.modules);
  const roots = sortModuleRoots(registry.tree?.length ? normalizeRegistryTree(registry.tree, pathIndex) : modulesToTree(registry.modules));
  const rootIds = roots.map(node => node.id);
  const nodeById = new Map<string, RegistryTreeNode>();
  const childrenById = new Map<string, RegistryTreeNode[]>();
  const parentById = new Map<string, string>();
  const searchTextById = new Map<string, string>();

  const visit = (node: RegistryTreeNode, parentId?: string) => {
    nodeById.set(node.id, node);
    if (parentId) parentById.set(node.id, parentId);
    const children = visibleChildren(node);
    childrenById.set(node.id, children);
    searchTextById.set(node.id, nodeSearchText(node));
    children.forEach(child => visit(child, node.id));
  };
  roots.forEach(root => visit(root));
  return { roots, rootIds, nodeById, childrenById, parentById, searchTextById };
}

const MODULE_NAME_COLLATOR = new Intl.Collator('en', { numeric: true, sensitivity: 'base' });

function sortModuleRoots(roots: RegistryTreeNode[]): RegistryTreeNode[] {
  return roots
    .map((node, index) => ({ node, index }))
    .sort((left, right) => MODULE_NAME_COLLATOR.compare(moduleSortKey(left.node), moduleSortKey(right.node)) || left.index - right.index)
    .map(entry => entry.node);
}

function moduleSortKey(node: RegistryTreeNode): string {
  const name = String(node.label || node.moduleId || node.id).trim();
  return name.replace(/^emaki[\s_-]*/i, '');
}

function buildSearchState(index: TreeIndex, query: string): TreeSearchState {
  if (!query) return { query, visibleIds: null };
  const visibleIds = new Set<string>();
  for (const [id, text] of index.searchTextById) {
    if (!text.includes(query)) continue;
    visibleIds.add(id);
    let parent = index.parentById.get(id);
    while (parent) {
      visibleIds.add(parent);
      parent = index.parentById.get(parent);
    }
  }
  return { query, visibleIds };
}

function flattenVisibleRows(index: TreeIndex, expanded: Record<string, boolean>, selected: TreeSelection | null, dirtyNodeIds: ReadonlySet<string>, search: TreeSearchState): VisibleTreeRow[] {
  const rows: VisibleTreeRow[] = [];
  const queryActive = Boolean(search.query);
  const visit = (node: RegistryTreeNode, level: number, isLast: boolean) => {
    if (search.visibleIds && !search.visibleIds.has(node.id)) return;
    const children = index.childrenById.get(node.id) ?? [];
    const childRows = search.visibleIds ? children.filter(child => search.visibleIds?.has(child.id)) : children;
    const hasChildren = childRows.length > 0;
    const isModule = node.type === 'module';
    const isFolder = node.type === 'folder';
    const isOpen = queryActive ? true : (expanded[node.id] ?? isModule);
    const isGlob = isGlobTreeNode(node);
    const childSelectionPath = selectableChildPath(node);
    const active = Boolean(node.moduleId && node.fileId && !isGlob && selected?.moduleId === node.moduleId && selected.fileId === node.fileId && (selected.scriptPath ?? '') === (childSelectionPath ?? ''));
    const dirty = dirtyNodeIds.has(node.id);
    const displayLabel = treeNodeDisplayLabel(node);
    const displayComment = treeNodeDisplayComment(node);
    const kindLabel = fileKindLabel(node.kind ?? node.type);
    rows.push({
      id: node.id,
      node,
      level,
      isLast,
      hasChildren,
      isModule,
      isFolder,
      isGlob,
      isOpen,
      active,
      dirty,
      displayLabel,
      displayComment,
      kindLabel,
      ariaLabel: treeRowAriaLabel(displayLabel, kindLabel, displayComment, isGlob, dirty),
      i18nCount: isModule ? moduleI18nCount(node.id) : undefined
    });
    if (hasChildren && isOpen) {
      childRows.forEach((child, index) => visit(child, level + 1, index === childRows.length - 1));
    }
  };
  index.roots.forEach((root, rootIndex) => visit(root, 0, rootIndex === index.roots.length - 1));
  return rows;
}

function treeRowAriaLabel(label: string, kindLabel: string, comment: string, isGlob: boolean, dirty: boolean): string {
  return `${label}，${kindLabel}${comment ? `，${comment}` : ''}${isGlob ? `，${t('core.tree.globHint')}` : ''}${dirty ? `，${t('core.tree.dirty')}` : ''}`;
}

function visibleChildren(node: RegistryTreeNode): RegistryTreeNode[] {
  return (node.children ?? []).filter(child => !isEmptyGlobPlaceholder(child) && !isLanguageTreeNode(child));
}

function nodeSearchText(node: RegistryTreeNode): string {
  return [treeNodeDisplayLabel(node), treeNodeDisplayComment(node), node.path, node.childPath, node.kind, node.type, node.moduleId].map(value => String(value ?? '').toLowerCase()).join(' ');
}

function isGlobTreeNode(node: RegistryTreeNode): boolean {
  return isGlobPath(node.childPath) || isGlobPath(node.path);
}

function selectableChildPath(node: RegistryTreeNode): string | undefined {
  const childPath = normalizeTreePath(node.childPath);
  if (childPath && !isGlobPath(childPath)) return childPath;
  return undefined;
}

function isEmptyGlobPlaceholder(node: RegistryTreeNode): boolean {
  return isGlobTreeNode(node) && !(node.children ?? []).length;
}

function normalizeQuery(value: string): string {
  return value.trim().toLowerCase();
}

function collectDirtyNodeIds(index: TreeIndex, dirtyKeys: ReadonlySet<string>): Set<string> {
  const dirtyNodeIds = new Set<string>();
  const visit = (id: string): boolean => {
    const node = index.nodeById.get(id);
    if (!node) return false;
    let dirty = isNodeDirty(node, dirtyKeys);
    for (const child of index.childrenById.get(id) ?? []) {
      if (visit(child.id)) dirty = true;
    }
    if (dirty) dirtyNodeIds.add(id);
    return dirty;
  };
  index.rootIds.forEach(visit);
  return dirtyNodeIds;
}

function isNodeDirty(node: RegistryTreeNode, dirtyKeys: ReadonlySet<string>): boolean {
  if (!node.moduleId || !node.fileId) return false;
  const filePath = node.childPath ?? node.path;
  return Boolean(filePath && dirtyKeys.has(treeDirtyKey(node.moduleId, node.fileId, filePath)));
}

function indentStyle(level: number): CSSProperties | undefined {
  return level > 0 ? ({ '--tree-level': level } as CSSProperties) : undefined;
}

function modulesToTree(modules: WebRegistryModule[]): RegistryTreeNode[] {
  return modules.map((module) => ({
    id: module.id,
    label: module.name,
    type: 'module',
    moduleId: module.id,
    icon: module.icon,
    tone: module.tone,
    children: module.files.filter(file => !isLanguageFilePath(file.path)).map((file) => ({
      id: file.id,
      label: file.title,
      type: 'file',
      moduleId: module.id,
      fileId: file.id,
      kind: file.kind,
      path: file.path,
      comment: file.comment,
      children: globChildrenToTree(module.id, file)
    }))
  }));
}

function buildGlobChildPathIndex(modules: WebRegistryModule[]): Map<string, string> {
  const index = new Map<string, string>();
  for (const module of modules) {
    for (const file of module.files) {
      for (const child of file.children ?? []) {
        const relativePath = normalizeTreePath(child.relativePath);
        const fullPath = normalizeTreePath(file.kind?.toUpperCase() === 'SCRIPT' ? child.relativePath : (child.fullPath ?? child.relativePath));
        if (!relativePath || !fullPath || isGlobPath(fullPath)) continue;
        const keys = new Set([
          treeChildPathKey(module.id, file.id, relativePath),
          treeChildPathKey(module.id, file.id, fullPath),
          treeChildPathKey(module.id, file.id, leafFileName(relativePath)),
          treeChildPathKey(module.id, file.id, leafFileName(fullPath))
        ]);
        for (const key of keys) index.set(key, fullPath);
      }
    }
  }
  return index;
}

function normalizeRegistryTree(nodes: RegistryTreeNode[], pathIndex: Map<string, string>): RegistryTreeNode[] {
  return nodes.map(node => normalizeRegistryTreeNode(node, pathIndex));
}

function normalizeRegistryTreeNode(node: RegistryTreeNode, pathIndex: Map<string, string>): RegistryTreeNode {
  const nextChildren = node.children?.map(child => normalizeRegistryTreeNode(child, pathIndex));
  let next: RegistryTreeNode = nextChildren ? { ...node, children: nextChildren } : { ...node };
  if (next.type === 'child' && next.moduleId && next.fileId) {
    const currentPath = normalizeTreePath(next.childPath ?? next.path);
    const indexedPath = pathIndex.get(treeChildPathKey(next.moduleId, next.fileId, currentPath))
      ?? pathIndex.get(treeChildPathKey(next.moduleId, next.fileId, leafFileName(currentPath)))
      ?? pathIndex.get(treeChildPathKey(next.moduleId, next.fileId, normalizeTreePath(next.label)));
    const safePath = indexedPath && !isGlobPath(indexedPath) ? indexedPath : (!isGlobPath(currentPath) ? currentPath : '');
    if (safePath) next = { ...next, path: safePath, childPath: safePath, id: next.id && !isGlobPath(next.id) ? next.id : `${next.fileId}:${safePath}` };
  }
  return next;
}

function treeChildPathKey(moduleId: string | undefined, fileId: string | undefined, path: string | undefined): string {
  return `${String(moduleId ?? '').toLowerCase()}\u0000${String(fileId ?? '').toLowerCase()}\u0000${normalizeTreePath(path).toLowerCase()}`;
}

function globChildrenToTree(moduleId: string, file: WebRegistryModule['files'][number]): RegistryTreeNode[] | undefined {
  const children = file.children?.filter(child => !isLanguageFilePath(child.fullPath ?? child.relativePath));
  if (!children?.length) return undefined;
  const roots: RegistryTreeNode[] = [];
  const folderByPath = new Map<string, RegistryTreeNode>();
  for (const child of children) {
    const childPath = normalizeTreePath(file.kind?.toUpperCase() === 'SCRIPT' ? child.relativePath : (child.fullPath ?? child.relativePath));
    const relativePath = normalizeGlobChildRelativePath(file, child, childPath);
    const parts = relativePath.split('/').filter(Boolean);
    if (!parts.length) continue;
    let siblings = roots;
    let createPrefix = normalizeGlobBaseDir(file.path);
    for (let index = 0; index < parts.length - 1; index++) {
      const folderName = parts[index];
      createPrefix = createPrefix ? `${createPrefix}/${folderName}` : folderName;
      const folderId = `${file.id}:folder:${createPrefix}`;
      let folder = folderByPath.get(folderId);
      if (!folder) {
        folder = { id: folderId, label: folderName, type: 'folder', moduleId, fileId: file.id, kind: file.kind, path: createPrefix, childPath: createPrefix, createPrefix, children: [] };
        folderByPath.set(folderId, folder);
        siblings.push(folder);
      }
      siblings = folder.children ?? (folder.children = []);
    }
    const childNode = { id: `${file.id}:${childPath}`, label: leafFileName(childPath), type: 'child' as const, moduleId, fileId: file.id, kind: file.kind, path: childPath, childPath };
    siblings.push(childNode);
  }
  return roots;
}

function normalizeGlobChildRelativePath(file: WebRegistryModule['files'][number], child: { relativePath: string; fullPath?: string }, childPath: string): string {
  const rawRelative = normalizeTreePath(child.relativePath || childPath);
  if (file.kind?.toUpperCase() === 'SCRIPT') return rawRelative;
  const baseDir = normalizeGlobBaseDir(file.path);
  if (!baseDir) return rawRelative;
  if (rawRelative && rawRelative !== leafFileName(rawRelative)) return rawRelative;
  return stripPathPrefix(childPath, baseDir);
}

function normalizeGlobBaseDir(path: string | undefined): string {
  const normalized = normalizeTreePath(path);
  const starIndex = normalized.search(/[?*]/);
  const base = starIndex >= 0 ? normalized.slice(0, starIndex) : normalized;
  return base.replace(/\/+$/g, '');
}

function stripPathPrefix(path: string, prefix: string): string {
  const normalizedPath = normalizeTreePath(path);
  const normalizedPrefix = normalizeTreePath(prefix).replace(/\/+$/g, '');
  if (!normalizedPrefix) return normalizedPath;
  return normalizedPath.startsWith(`${normalizedPrefix}/`) ? normalizedPath.slice(normalizedPrefix.length + 1) : normalizedPath;
}

function normalizeTreePath(path: string | undefined): string {
  return String(path ?? '').replace(/\\/g, '/').replace(/^\/+|\/+$/g, '');
}

function isLanguageTreeNode(node: RegistryTreeNode): boolean {
  return isLanguageFilePath(node.childPath ?? node.path);
}

function isLanguageFilePath(path: string | undefined): boolean {
  const normalized = normalizeTreePath(path).toLowerCase();
  return normalized === 'lang' || normalized.startsWith('lang/');
}

function leafFileName(path: string | undefined): string {
  const leaf = normalizeTreePath(path).split('/').filter(Boolean).pop() ?? '';
  return leaf.replace(/\.(ya?ml|json|js|kts|txt)$/i, '') || String(path ?? '');
}
