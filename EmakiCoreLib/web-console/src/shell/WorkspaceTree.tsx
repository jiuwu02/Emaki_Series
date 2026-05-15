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
  if (!registry) return <div className="tree-empty">载入中</div>;
  const roots = registry.tree?.length ? registry.tree : modulesToTree(registry.modules);
  const toggle = (id: string) => setExpanded((current) => ({ ...current, [id]: !current[id] }));

  return (
    <div className="tree">
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
  const active = Boolean(node.moduleId && node.fileId && selected?.moduleId === node.moduleId && selected.fileId === node.fileId && (selected.scriptPath ?? '') === (node.childPath ?? ''));

  if (isModule) {
    return (
      <div className="tree-module">
        <button className="tree-folder" onClick={() => toggle(node.id)}>
          <Icon svg={node.icon} /> <span>{isOpen ? '⌄' : '›'}</span> {node.label}
        </button>
        {isOpen && children.map((child) => (
          <TreeNodeView key={child.id} node={child} selected={selected} expanded={expanded} toggle={toggle} onSelect={onSelect} level={level + 1} />
        ))}
      </div>
    );
  }

  if (hasChildren) {
    return (
      <div className="tree-file-folder">
        <button className={`tree-file folder-toggle ${active ? 'active' : ''}`} onClick={() => toggle(node.id)} style={indentStyle(level)}>
          <span className="folder-arrow">{isOpen ? '⌄' : '›'}</span> {fileKindLabel(node.kind ?? node.type)} · {node.label}
        </button>
        {isOpen && (
          <div className="tree-children">
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
      style={level > 1 ? undefined : indentStyle(level)}
      onClick={() => {
        if (!canSelect || !node.moduleId || !node.fileId) return;
        onSelect({ moduleId: node.moduleId, fileId: node.fileId, scriptPath: node.childPath });
      }}
      disabled={!canSelect}
    >
      {level > 1 ? node.label : `${fileKindLabel(node.kind ?? node.type)} · ${node.label}`}
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
  return <span className="module-icon" dangerouslySetInnerHTML={{ __html: svg }} />;
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
