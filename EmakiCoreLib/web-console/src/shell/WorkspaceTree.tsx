import { useMemo, useRef, useState, type CSSProperties, type Dispatch, type KeyboardEvent, type SetStateAction } from 'react';
import { getModuleLocaleBundles, t } from '../i18n';
import { treeNodeDisplayComment, treeNodeDisplayLabel } from '../lib';
import { DisclosureChevron } from '../components';
import type { RegistryTreeNode, WebRegistry, WebRegistryModule } from '../types';

export type TreeSelection = { moduleId: string; fileId: string; scriptPath?: string; refreshKey?: number };

export function WorkspaceTree({ registry, selected, expanded, dirtyKeys = new Set<string>(), setExpanded, onSelect, onOpenI18n, onCreateFile, onDeleteFile }: {
  registry: WebRegistry | null;
  selected: TreeSelection | null;
  expanded: Record<string, boolean>;
  dirtyKeys?: ReadonlySet<string>;
  setExpanded: Dispatch<SetStateAction<Record<string, boolean>>>;
  onSelect: (v: TreeSelection) => void;
  onOpenI18n?: (target: { moduleId: string }) => void;
  onCreateFile?: (node: RegistryTreeNode) => void;
  onDeleteFile?: (node: RegistryTreeNode) => void;
}) {
  const [query, setQuery] = useState('');
  const normalizedQuery = normalizeQuery(query);
  const roots = useMemo(() => registry ? (registry.tree?.length ? registry.tree : modulesToTree(registry.modules)) : [], [registry]);
  const visibleRoots = useMemo(() => filterTree(roots, normalizedQuery), [roots, normalizedQuery]);
  const treeRef = useRef<HTMLDivElement | null>(null);
  const toggle = (id: string) => setExpanded((current) => ({ ...current, [id]: !current[id] }));
  const openNode = (id: string) => setExpanded((current) => ({ ...current, [id]: true }));
  const closeNode = (id: string) => setExpanded((current) => ({ ...current, [id]: false }));

  if (!registry) return <div className="tree-empty" role="status">{t('core.tree.loading')}</div>;

  return <>
    <label className="tree-search">
      <span className="sr-only">{t('core.tree.search')}</span>
      <SearchIcon />
      <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('core.tree.search')} />
    </label>
    <div ref={treeRef} className="tree" role="tree" aria-label={t('core.tree.aria')} onKeyDown={(event) => handleTreeKeyDown(event, treeRef.current, openNode, closeNode)}>
      {visibleRoots.map((node) => (
        <TreeNodeView key={node.id} node={node} selected={selected} expanded={expanded} dirtyKeys={dirtyKeys} queryActive={Boolean(normalizedQuery)} toggle={toggle} onSelect={onSelect} onOpenI18n={onOpenI18n} onCreateFile={onCreateFile} onDeleteFile={onDeleteFile} level={0} />
      ))}
    </div>
    {normalizedQuery && visibleRoots.length === 0 && <div className="tree-empty" role="status">{t('core.tree.noResults')}</div>}
  </>;
}

function TreeNodeView({ node, selected, expanded, dirtyKeys, queryActive, toggle, onSelect, onOpenI18n, onCreateFile, onDeleteFile, level }: {
  node: RegistryTreeNode;
  selected: TreeSelection | null;
  expanded: Record<string, boolean>;
  dirtyKeys: ReadonlySet<string>;
  queryActive: boolean;
  toggle: (id: string) => void;
  onSelect: (v: TreeSelection) => void;
  onOpenI18n?: (target: { moduleId: string }) => void;
  onCreateFile?: (node: RegistryTreeNode) => void;
  onDeleteFile?: (node: RegistryTreeNode) => void;
  level: number;
}) {
  const children = node.children ?? [];
  const hasChildren = children.length > 0;
  const isModule = node.type === 'module';
  const isOpen = queryActive ? true : (expanded[node.id] ?? isModule);
  const kindLabel = fileKindLabel(node.kind ?? node.type);
  const active = Boolean(node.moduleId && node.fileId && selected?.moduleId === node.moduleId && selected.fileId === node.fileId && (selected.scriptPath ?? '') === (node.childPath ?? ''));
  const dirty = isNodeDirty(node, dirtyKeys) || children.some(child => isNodeOrDescendantDirty(child, dirtyKeys));
  const displayLabel = treeNodeDisplayLabel(node);
  const displayComment = treeNodeDisplayComment(node);

  if (isModule) {
    const i18nCount = moduleI18nCount(node.id);
    return (
      <div className="tree-module" role="none">
        <div className="tree-module-row">
          <button
            className={`tree-folder ${dirty ? 'dirty' : ''}`}
            role="treeitem"
            aria-level={level + 1}
            aria-expanded={isOpen}
            data-tree-node-id={node.id}
            onClick={() => toggle(node.id)}
          >
            <Icon svg={node.icon} /> <DisclosureChevron open={isOpen} className="folder-arrow" /> <span className="tree-label">{displayLabel}</span><DirtyDot dirty={dirty} />
          </button>
          {onOpenI18n && node.id && <ModuleI18nButton moduleId={node.id} moduleName={displayLabel} count={i18nCount} onOpen={onOpenI18n} />}
        </div>
        {isOpen && <div role="group">{children.map((child) => (
          <TreeNodeView key={child.id} node={child} selected={selected} expanded={expanded} dirtyKeys={dirtyKeys} queryActive={queryActive} toggle={toggle} onSelect={onSelect} onOpenI18n={onOpenI18n} onCreateFile={onCreateFile} onDeleteFile={onDeleteFile} level={level + 1} />
        ))}</div>}
      </div>
    );
  }

  if (hasChildren) {
    return (
      <div className="tree-file-folder" role="none">
        <div className="tree-file-row" style={indentStyle(level)}>
          <button
            className={`tree-file folder-toggle ${active ? 'active' : ''} ${dirty ? 'dirty' : ''}`}
            role="treeitem"
            aria-level={level + 1}
            aria-expanded={isOpen}
            aria-selected={active || undefined}
            aria-label={`${displayLabel}，${kindLabel}${displayComment ? `，${displayComment}` : ''}${dirty ? `，${t('core.tree.dirty')}` : ''}`}
            data-tree-node-id={node.id}
            onClick={() => toggle(node.id)}
          >
            <DisclosureChevron open={isOpen} className="folder-arrow" /><span className="tree-label">{displayLabel}</span><DirtyDot dirty={dirty} />
          </button>
          {onCreateFile && <button type="button" className="tree-file-action" title={t('core.tree.createFile')} aria-label={t('core.tree.createFile')} onClick={(event) => { event.stopPropagation(); onCreateFile(node); }}>+</button>}
        </div>
        {isOpen && (
          <div className="tree-children" role="group">
            {children.map((child) => (
              <TreeNodeView key={child.id} node={child} selected={selected} expanded={expanded} dirtyKeys={dirtyKeys} queryActive={queryActive} toggle={toggle} onSelect={onSelect} onOpenI18n={onOpenI18n} onCreateFile={onCreateFile} onDeleteFile={onDeleteFile} level={level + 1} />
            ))}
          </div>
        )}
      </div>
    );
  }

  const canSelect = Boolean(node.moduleId && node.fileId);
  const rowClass = level > 1 ? 'tree-child-row' : 'tree-file-row';
  return (
    <div className={rowClass} role="none" style={indentStyle(level)}>
      <button
        className={level > 1 ? `tree-child ${active ? 'active' : ''} ${dirty ? 'dirty' : ''}` : `tree-file ${active ? 'active' : ''} ${dirty ? 'dirty' : ''}`}
        role="treeitem"
        aria-level={level + 1}
        aria-selected={active}
        aria-label={`${displayLabel}，${kindLabel}${displayComment ? `，${displayComment}` : ''}${dirty ? `，${t('core.tree.dirty')}` : ''}`}
        data-tree-node-id={node.id}
        onClick={() => {
          if (!canSelect || !node.moduleId || !node.fileId) return;
          onSelect({ moduleId: node.moduleId, fileId: node.fileId, scriptPath: node.childPath });
        }}
        disabled={!canSelect}
      >
        <span className="tree-label">{displayLabel}</span><DirtyDot dirty={dirty} />
      </button>
      {onDeleteFile && node.childPath && <button type="button" className="tree-file-action danger" title={t('core.tree.deleteFile')} aria-label={t('core.tree.deleteFile')} onClick={(event) => { event.stopPropagation(); onDeleteFile(node); }}>×</button>}
    </div>
  );
}

function handleTreeKeyDown(event: KeyboardEvent<HTMLDivElement>, tree: HTMLDivElement | null, openNode: (id: string) => void, closeNode: (id: string) => void) {
  if (!tree) return;
  const current = event.target instanceof HTMLElement ? event.target.closest<HTMLElement>('[role="treeitem"]') : null;
  if (!current || !tree.contains(current)) return;
  const items = Array.from(tree.querySelectorAll<HTMLElement>('[role="treeitem"]')).filter(item => !item.hasAttribute('disabled'));
  const index = items.indexOf(current);
  if (index < 0) return;
  const focusAt = (nextIndex: number) => items[Math.max(0, Math.min(items.length - 1, nextIndex))]?.focus();
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
    focusAt(items.length - 1);
  } else if (event.key === 'ArrowRight' && current.getAttribute('aria-expanded') === 'false') {
    const id = current.dataset.treeNodeId;
    if (!id) return;
    event.preventDefault();
    openNode(id);
  } else if (event.key === 'ArrowLeft' && current.getAttribute('aria-expanded') === 'true') {
    const id = current.dataset.treeNodeId;
    if (!id) return;
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
  if (upper === 'SET') return t('core.kind.set');
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

function sanitizeSvg(svg: string): string {
  if (!svg.trim().startsWith('<svg')) return '';
  return svg
    .replace(/<\/?(?:script|foreignObject|iframe|object|embed|link|meta)[\s\S]*?>/gi, '')
    .replace(/\s+on[a-z]+\s*=("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/\s+(?:href|xlink:href)\s*=("|')\s*javascript:[\s\S]*?\1/gi, '');
}

function filterTree(nodes: RegistryTreeNode[], query: string): RegistryTreeNode[] {
  if (!query) return nodes;
  return nodes.flatMap((node) => {
    const children = node.children ?? [];
    const filteredChildren = filterTree(children, query);
    if (matchesNode(node, query)) return [{ ...node, children }];
    if (filteredChildren.length) return [{ ...node, children: filteredChildren }];
    return [];
  });
}

function matchesNode(node: RegistryTreeNode, query: string): boolean {
  const haystack = [treeNodeDisplayLabel(node), treeNodeDisplayComment(node), node.path, node.childPath, node.kind, node.type, node.moduleId].map(value => String(value ?? '').toLowerCase()).join(' ');
  return haystack.includes(query);
}

function normalizeQuery(value: string): string {
  return value.trim().toLowerCase();
}

function isNodeOrDescendantDirty(node: RegistryTreeNode, dirtyKeys: ReadonlySet<string>): boolean {
  return isNodeDirty(node, dirtyKeys) || (node.children ?? []).some(child => isNodeOrDescendantDirty(child, dirtyKeys));
}

function isNodeDirty(node: RegistryTreeNode, dirtyKeys: ReadonlySet<string>): boolean {
  if (!node.moduleId || !node.fileId) return false;
  const filePath = node.childPath ?? node.path;
  return Boolean(filePath && dirtyKeys.has(treeDirtyKey(node.moduleId, node.fileId, filePath)));
}

function treeDirtyKey(moduleId: string, fileId: string, filePath: string) {
  return JSON.stringify([moduleId, fileId, filePath.replace(/\\/g, '/')]);
}

function indentStyle(level: number): CSSProperties | undefined {
  return level > 0 ? { paddingLeft: `${level * 12}px` } : undefined;
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
      children: file.children?.filter(child => !isLanguageFilePath(child.fullPath ?? child.relativePath)).map((child) => {
        const childPath = file.kind?.toUpperCase() === 'SCRIPT' ? child.relativePath : (child.fullPath ?? child.relativePath);
        return {
          id: `${file.id}:${childPath}`,
          label: child.name || leafFileName(childPath),
          type: 'child',
          moduleId: module.id,
          fileId: file.id,
          kind: file.kind,
          path: childPath,
          childPath
        };
      })
    }))
  }));
}

function isLanguageFilePath(path: string | undefined): boolean {
  return String(path ?? '').replace(/\\/g, '/').toLowerCase().startsWith('lang/');
}

function leafFileName(path: string | undefined): string {
  const leaf = String(path ?? '').replace(/\\/g, '/').split('/').filter(Boolean).pop() ?? '';
  return leaf.replace(/\.(ya?ml|json|js|kts|txt)$/i, '') || String(path ?? '');
}
