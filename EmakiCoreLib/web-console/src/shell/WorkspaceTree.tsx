import { useCallback, useMemo, useRef, useState, type CSSProperties, type Dispatch, type KeyboardEvent, type SetStateAction } from 'react';
import { getModuleLocaleBundles, t } from '../i18n';
import { getFileKindLabel } from '../registry';
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
  const dirtyNodeIds = useMemo(() => collectDirtyNodeIds(roots, dirtyKeys), [roots, dirtyKeys]);
  const treeRef = useRef<HTMLDivElement | null>(null);
  const toggle = useCallback((id: string) => setExpanded((current) => ({ ...current, [id]: !current[id] })), [setExpanded]);
  const openNode = useCallback((id: string) => setExpanded((current) => current[id] ? current : ({ ...current, [id]: true })), [setExpanded]);
  const closeNode = useCallback((id: string) => setExpanded((current) => current[id] === false ? current : ({ ...current, [id]: false })), [setExpanded]);

  if (!registry) return <div className="tree-empty" role="status">{t('core.tree.loading')}</div>;

  return <>
    <label className="tree-search">
      <span className="sr-only">{t('core.tree.search')}</span>
      <SearchIcon />
      <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('core.tree.search')} />
    </label>
    <div ref={treeRef} className="tree" role="tree" aria-label={t('core.tree.aria')} onKeyDown={(event) => handleTreeKeyDown(event, treeRef.current, openNode, closeNode)}>
      {visibleRoots.length > 0 ? visibleRoots.map((node, index) => (
        <TreeNodeView key={node.id} node={node} selected={selected} expanded={expanded} dirtyNodeIds={dirtyNodeIds} queryActive={Boolean(normalizedQuery)} toggle={toggle} onSelect={onSelect} onOpenI18n={onOpenI18n} onCreateFile={onCreateFile} onDeleteFile={onDeleteFile} level={0} isLast={index === visibleRoots.length - 1} />
      )) : normalizedQuery ? <div className="tree-empty tree-empty-search" role="status">{t('core.tree.noResults')}</div> : null}
    </div>
  </>;
}

function TreeNodeView({ node, selected, expanded, dirtyNodeIds, queryActive, toggle, onSelect, onOpenI18n, onCreateFile, onDeleteFile, level, isLast }: {
  node: RegistryTreeNode;
  selected: TreeSelection | null;
  expanded: Record<string, boolean>;
  dirtyNodeIds: ReadonlySet<string>;
  queryActive: boolean;
  toggle: (id: string) => void;
  onSelect: (v: TreeSelection) => void;
  onOpenI18n?: (target: { moduleId: string }) => void;
  onCreateFile?: (node: RegistryTreeNode) => void;
  onDeleteFile?: (node: RegistryTreeNode) => void;
  level: number;
  isLast: boolean;
}) {
  const children = (node.children ?? []).filter(child => !isEmptyGlobPlaceholder(child));
  const hasChildren = children.length > 0;
  const isModule = node.type === 'module';
  const isFolder = node.type === 'folder';
  const isOpen = queryActive ? true : (expanded[node.id] ?? isModule);
  const kindLabel = fileKindLabel(node.kind ?? node.type);
  const isGlob = isGlobTreeNode(node);
  const active = Boolean(node.moduleId && node.fileId && !isGlob && selected?.moduleId === node.moduleId && selected.fileId === node.fileId && (selected.scriptPath ?? '') === (node.childPath ?? ''));
  const dirty = dirtyNodeIds.has(node.id);
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
        {isOpen && <div role="group">{children.map((child, index) => (
          <TreeNodeView key={child.id} node={child} selected={selected} expanded={expanded} dirtyNodeIds={dirtyNodeIds} queryActive={queryActive} toggle={toggle} onSelect={onSelect} onOpenI18n={onOpenI18n} onCreateFile={onCreateFile} onDeleteFile={onDeleteFile} level={level + 1} isLast={index === children.length - 1} />
        ))}</div>}
      </div>
    );
  }

  if (hasChildren) {
    return (
      <div className="tree-file-folder" role="none">
        <div className="tree-file-row" style={indentStyle(level)} data-tree-level={level} data-tree-branch={isLast ? 'elbow' : 'tee'}>
          <IndentGuide branch={isLast ? 'elbow' : 'tee'} />
          <button
            className={`tree-file folder-toggle ${isGlob ? 'glob-node' : ''} ${active ? 'active' : ''} ${dirty ? 'dirty' : ''}`}
            role="treeitem"
            aria-level={level + 1}
            aria-expanded={isOpen}
            aria-selected={active || undefined}
            aria-label={`${displayLabel}，${kindLabel}${displayComment ? `，${displayComment}` : ''}${isGlob ? `，${t('core.tree.globHint')}` : ''}${dirty ? `，${t('core.tree.dirty')}` : ''}`}
            title={isGlob ? t('core.tree.globHint') : undefined}
            data-tree-node-id={node.id}
            onClick={() => toggle(node.id)}
          >
            <DisclosureChevron open={isOpen} className="folder-arrow" /><span className="tree-label">{displayLabel}</span><DirtyDot dirty={dirty} />
          </button>
          {onCreateFile && (isGlob || isFolder || !hasChildren) && <button type="button" className="tree-file-action" title={t('core.tree.createFile')} aria-label={t('core.tree.createFile')} onClick={(event) => { event.stopPropagation(); onCreateFile(node); }}>+</button>}
        </div>
        {isOpen && (
          <div className="tree-children" role="group">
            {children.map((child, index) => (
              <TreeNodeView key={child.id} node={child} selected={selected} expanded={expanded} dirtyNodeIds={dirtyNodeIds} queryActive={queryActive} toggle={toggle} onSelect={onSelect} onOpenI18n={onOpenI18n} onCreateFile={onCreateFile} onDeleteFile={onDeleteFile} level={level + 1} isLast={index === children.length - 1} />
            ))}
          </div>
        )}
      </div>
    );
  }

  const canSelect = Boolean(node.moduleId && node.fileId && !isGlob && !isFolder);
  const rowClass = level > 1 ? 'tree-child-row' : 'tree-file-row';
  return (
    <div className={rowClass} role="none" style={indentStyle(level)}>
      <button
        className={level > 1 ? `tree-child ${isGlob ? 'glob-node' : ''} ${active ? 'active' : ''} ${dirty ? 'dirty' : ''}` : `tree-file ${isGlob ? 'glob-node' : ''} ${active ? 'active' : ''} ${dirty ? 'dirty' : ''}`}
        role="treeitem"
        aria-level={level + 1}
        aria-selected={active}
        aria-label={`${displayLabel}，${kindLabel}${displayComment ? `，${displayComment}` : ''}${isGlob ? `，${t('core.tree.globHint')}` : ''}${dirty ? `，${t('core.tree.dirty')}` : ''}`}
        title={isGlob ? t('core.tree.globHint') : undefined}
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
    const children = (node.children ?? []).filter(child => !isEmptyGlobPlaceholder(child));
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

function isGlobTreeNode(node: RegistryTreeNode): boolean {
  return /[*?]/.test(String(node.childPath ?? node.path ?? ''));
}

function isEmptyGlobPlaceholder(node: RegistryTreeNode): boolean {
  return isGlobTreeNode(node) && !(node.children ?? []).length;
}

function normalizeQuery(value: string): string {
  return value.trim().toLowerCase();
}

function collectDirtyNodeIds(nodes: RegistryTreeNode[], dirtyKeys: ReadonlySet<string>): Set<string> {
  const dirtyNodeIds = new Set<string>();
  const visit = (node: RegistryTreeNode): boolean => {
    let dirty = isNodeDirty(node, dirtyKeys);
    for (const child of node.children ?? []) {
      if (visit(child)) dirty = true;
    }
    if (dirty) dirtyNodeIds.add(node.id);
    return dirty;
  };
  nodes.forEach(visit);
  return dirtyNodeIds;
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
    siblings.push({ id: `${file.id}:${childPath}`, label: leafFileName(childPath), type: 'child', moduleId, fileId: file.id, kind: file.kind, path: childPath, childPath });
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

function isLanguageFilePath(path: string | undefined): boolean {
  return normalizeTreePath(path).toLowerCase().startsWith('lang/');
}

function leafFileName(path: string | undefined): string {
  const leaf = normalizeTreePath(path).split('/').filter(Boolean).pop() ?? '';
  return leaf.replace(/\.(ya?ml|json|js|kts|txt)$/i, '') || String(path ?? '');
}
