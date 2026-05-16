import type { Dispatch, SetStateAction } from 'react';
import type { RegistryTreeNode, WebRegistry, WebRegistryModule } from '../types';

export type TreeSelection = { moduleId: string; fileId: string; scriptPath?: string; refreshKey?: number };

export function WorkspaceTree({ registry, selected, expanded, setExpanded, onSelect }: {
  registry: WebRegistry | null;
  selected: TreeSelection | null;
  expanded: Record<string, boolean>;
  setExpanded: Dispatch<SetStateAction<Record<string, boolean>>>;
  onSelect: (v: TreeSelection) => void;
}) {
  if (!registry) return <div className="tree-empty" role="status">载入中</div>;
  const roots = registry.tree?.length ? registry.tree : modulesToTree(registry.modules);
  const toggle = (id: string) => setExpanded((current) => ({ ...current, [id]: !current[id] }));

  return (
    <div className="tree" role="tree" aria-label="配置模块与文件">
      {roots.map((node) => (
        <TreeNodeView key={node.id} node={node} selected={selected} expanded={expanded} toggle={toggle} onSelect={onSelect} level={0} />
      ))}
    </div>
  );
}

function TreeNodeView({ node, selected, expanded, toggle, onSelect, level }: {
  node: RegistryTreeNode;
  selected: TreeSelection | null;
  expanded: Record<string, boolean>;
  toggle: (id: string) => void;
  onSelect: (v: TreeSelection) => void;
  level: number;
}) {
  const children = node.children ?? [];
  const hasChildren = children.length > 0;
  const isModule = node.type === 'module';
  const isOpen = expanded[node.id] ?? isModule;
  const kindLabel = fileKindLabel(node.kind ?? node.type);
  const active = Boolean(node.moduleId && node.fileId && selected?.moduleId === node.moduleId && selected.fileId === node.fileId && (selected.scriptPath ?? '') === (node.childPath ?? ''));

  if (isModule) {
    return (
      <div className="tree-module" role="none">
        <button
          className="tree-folder"
          role="treeitem"
          aria-level={level + 1}
          aria-expanded={isOpen}
          onClick={() => toggle(node.id)}
        >
          <Icon svg={node.icon} /> <span aria-hidden="true">{isOpen ? '⌄' : '›'}</span> {node.label}
        </button>
        {isOpen && <div role="group">{children.map((child) => (
          <TreeNodeView key={child.id} node={child} selected={selected} expanded={expanded} toggle={toggle} onSelect={onSelect} level={level + 1} />
        ))}</div>}
      </div>
    );
  }

  if (hasChildren) {
    return (
      <div className="tree-file-folder" role="none">
        <button
          className={`tree-file folder-toggle ${active ? 'active' : ''}`}
          role="treeitem"
          aria-level={level + 1}
          aria-expanded={isOpen}
          aria-selected={active || undefined}
          onClick={() => toggle(node.id)}
          style={indentStyle(level)}
        >
          <span className="folder-arrow" aria-hidden="true">{isOpen ? '⌄' : '›'}</span> {kindLabel} · {node.label}
        </button>
        {isOpen && (
          <div className="tree-children" role="group">
            {children.map((child) => (
              <TreeNodeView key={child.id} node={child} selected={selected} expanded={expanded} toggle={toggle} onSelect={onSelect} level={level + 1} />
            ))}
          </div>
        )}
      </div>
    );
  }

  const canSelect = Boolean(node.moduleId && node.fileId);
  return (
    <button
      className={level > 1 ? `tree-child ${active ? 'active' : ''}` : `tree-file ${active ? 'active' : ''}`}
      role="treeitem"
      aria-level={level + 1}
      aria-selected={active}
      aria-label={level > 1 ? `${node.label}，${kindLabel}` : `${kindLabel}，${node.label}`}
      style={level > 1 ? undefined : indentStyle(level)}
      onClick={() => {
        if (!canSelect || !node.moduleId || !node.fileId) return;
        onSelect({ moduleId: node.moduleId, fileId: node.fileId, scriptPath: node.childPath });
      }}
      disabled={!canSelect}
    >
      {level > 1 ? node.label : `${kindLabel} · ${node.label}`}
    </button>
  );
}

export function fileKindLabel(kind: string | undefined): string {
  if (!kind) return '配置';
  const upper = kind.toUpperCase();
  if (upper === 'CONFIG') return '配置';
  if (upper === 'GUI') return 'GUI';
  if (upper === 'ITEM') return '物品';
  if (upper === 'SCRIPT') return '脚本';
  if (upper === 'FILE') return '文件';
  return kind;
}

function Icon({ svg }: { svg?: string }) {
  if (!svg) return null;
  return <span className="module-icon" aria-hidden="true" dangerouslySetInnerHTML={{ __html: sanitizeSvg(svg) }} />;
}

function sanitizeSvg(svg: string): string {
  if (!svg.trim().startsWith('<svg')) return '';
  return svg
    .replace(/<\/?(?:script|foreignObject|iframe|object|embed|link|meta)[\s\S]*?>/gi, '')
    .replace(/\s+on[a-z]+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/\s+(?:href|xlink:href)\s*=\s*("|')\s*javascript:[\s\S]*?\1/gi, '');
}

function indentStyle(level: number) {
  return level > 1 ? { paddingLeft: `${level * 12}px` } : undefined;
}

function modulesToTree(modules: WebRegistryModule[]): RegistryTreeNode[] {
  return modules.map((module) => ({
    id: module.id,
    label: module.name,
    type: 'module',
    moduleId: module.id,
    icon: module.icon,
    tone: module.tone,
    children: module.files.map((file) => ({
      id: file.id,
      label: file.title,
      type: 'file',
      moduleId: module.id,
      fileId: file.id,
      kind: file.kind,
      path: file.path,
      comment: file.comment,
      children: file.children?.map((child) => {
        const childPath = file.kind?.toUpperCase() === 'SCRIPT' ? child.relativePath : (child.fullPath ?? child.relativePath);
        return {
          id: `${file.id}:${childPath}`,
          label: child.name,
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
