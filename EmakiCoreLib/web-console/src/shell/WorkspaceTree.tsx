import type { WebRegistry, WebRegistryFile } from '../types';
import { isKind } from '../registry';

type Selection = { moduleId: string; fileId: string; scriptPath?: string; refreshKey?: number };

export function WorkspaceTree({ registry, selected, expanded, setExpanded, onSelect }: {
  registry: WebRegistry | null;
  selected: Selection | null;
  expanded: Record<string, boolean>;
  setExpanded: React.Dispatch<React.SetStateAction<Record<string, boolean>>>;
  onSelect: (v: Selection) => void;
}) {
  if (!registry) return <div className="tree-empty">载入中</div>;
  const toggle = (id: string) => setExpanded((c) => ({ ...c, [id]: !c[id] }));

  return (
    <div className="tree">
      {registry.modules.map((m) => (
        <div key={m.id} className="tree-module">
          <button className="tree-folder" onClick={() => toggle(m.id)}>
            <Icon svg={m.icon} /> <span>{expanded[m.id] ? '⌄' : '›'}</span> {m.name}
          </button>
          {expanded[m.id] && m.files.map((f) => {
            const hasChildren = f.children && f.children.length > 0;
            if (hasChildren) {
              const folderId = `folder:${f.id}`;
              return (
                <div key={f.id} className="tree-file-folder">
                  <button className={`tree-file folder-toggle ${selected?.moduleId === m.id && selected.fileId === f.id ? 'active' : ''}`} onClick={() => toggle(folderId)}>
                    <span className="folder-arrow">{expanded[folderId] ? '⌄' : '›'}</span> {fileKindLabel(f.kind)} · {f.title}
                  </button>
                  {expanded[folderId] && (
                    <div className="tree-children">
                      {f.children!.map((child) => {
                        const childPath = isKind(f.kind, 'SCRIPT') ? child.relativePath : (child.fullPath ?? child.relativePath);
                        return <button key={child.relativePath} className="tree-child" onClick={() => onSelect({ moduleId: m.id, fileId: f.id, scriptPath: childPath })}>{child.name}</button>;
                      })}
                    </div>
                  )}
                </div>
              );
            }
            return <button key={f.id} className={`tree-file ${selected?.moduleId === m.id && selected.fileId === f.id ? 'active' : ''}`} onClick={() => onSelect({ moduleId: m.id, fileId: f.id })}>{fileKindLabel(f.kind)} · {f.title}</button>;
          })}
        </div>
      ))}
    </div>
  );
}

export function fileKindLabel(kind: string | undefined): string {
  if (!kind) return '配置';
  const upper = kind.toUpperCase();
  if (upper === 'GUI') return 'GUI';
  if (upper === 'ITEM') return '物品';
  if (upper === 'SCRIPT') return '脚本';
  return '配置';
}

function Icon({ svg }: { svg?: string }) {
  if (!svg) return null;
  return <span className="module-icon" dangerouslySetInnerHTML={{ __html: svg }} />;
}
