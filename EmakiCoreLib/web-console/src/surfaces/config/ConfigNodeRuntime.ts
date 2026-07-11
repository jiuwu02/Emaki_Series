import { getLocale } from '../../i18n';
import { fieldLabel, humanizeFieldLabel } from '../../lib';
import type { WebConfigNode } from '../../types';
import { draftKey, type ConfigDraftScope, type DraftMap } from './ConfigDraftRuntime';

export type NodeGroup = { type: 'section'; node: WebConfigNode; children: WebConfigNode[] } | { type: 'leaf'; node: WebConfigNode };
export type ConfigNodeIndex = { groupsByParent: Map<string, NodeGroup[]>; descendantsByPath: Map<string, WebConfigNode[]> };
export type ConfigNodeChangeState = { changedPaths: Set<string>; descendantCounts: Map<string, number> };

export function buildNodeIndex(nodes: WebConfigNode[]): ConfigNodeIndex {
  const directByParent = new Map<string, WebConfigNode[]>();
  const descendantsByPath = new Map<string, WebConfigNode[]>();
  for (const node of nodes) {
    pushMapList(directByParent, parentConfigPath(node.path), node);
    for (const ancestor of configAncestorPaths(node.path)) {
      pushMapList(descendantsByPath, ancestor, node);
    }
  }

  const groupsByParent = new Map<string, NodeGroup[]>();
  for (const parentPath of new Set(['', ...directByParent.keys()])) {
    const prefix = parentPath ? `${parentPath}.` : '';
    const groups = (directByParent.get(parentPath) ?? []).map<NodeGroup>(node => {
      if (node.type !== 'object') return { type: 'leaf', node };
      const children = descendantsByPath.get(node.path) ?? [];
      return children.length || node.creatableChildren || node.itemFields?.length ? { type: 'section', node, children } : { type: 'leaf', node };
    });
    groupsByParent.set(parentPath, groups.filter(group => group.type === 'leaf' || group.children.length > 0 || group.node.creatableChildren || group.node.itemFields?.length || !prefix));
  }
  return { groupsByParent, descendantsByPath };
}

export function buildNodeChangeState(scope: ConfigDraftScope, nodes: WebConfigNode[], drafts: DraftMap, sourceEditedPaths?: Set<string>, deletedPaths?: Set<string>): ConfigNodeChangeState {
  const changedPaths = new Set<string>();
  const descendantCounts = new Map<string, number>();
  for (const node of nodes) {
    const changed = node.type !== 'object' && (draftKey(scope, node.path) in drafts || sourceEditedPaths?.has(node.path) === true);
    const deleted = deletedPaths?.has(node.path) === true;
    if (!changed && !deleted) continue;
    changedPaths.add(node.path);
    for (const ancestor of configAncestorPaths(node.path)) {
      descendantCounts.set(ancestor, (descendantCounts.get(ancestor) ?? 0) + 1);
    }
  }
  return { changedPaths, descendantCounts };
}

export function configNodeDisplayLabel(scope: ConfigDraftScope, node: WebConfigNode): string {
  return fieldLabel(node.path, { moduleId: scope.moduleId, namespace: scope.moduleId, fallback: getLocale().startsWith('zh') ? node.label : humanizeFieldLabel(node.path) });
}

export function configSectionHasMeaningfulValue(node: WebConfigNode, nodeIndex: ConfigNodeIndex): boolean {
  if (hasMeaningfulConfigValue(node.value)) return true;
  return (nodeIndex.descendantsByPath.get(node.path) ?? []).some(descendant => hasMeaningfulConfigValue(descendant.value));
}

export function hasMeaningfulConfigValue(value: unknown): boolean {
  if (value === undefined || value === null) return false;
  if (typeof value === 'string') return value.trim().length > 0;
  if (typeof value === 'number' || typeof value === 'boolean') return true;
  if (Array.isArray(value)) return value.some(hasMeaningfulConfigValue);
  if (isPlainObject(value)) return Object.values(value).some(hasMeaningfulConfigValue);
  return true;
}

export function configAncestorPaths(path: string): string[] {
  const ancestors: string[] = [];
  let index = path.indexOf('.');
  while (index > 0) {
    ancestors.push(path.slice(0, index));
    index = path.indexOf('.', index + 1);
  }
  return ancestors;
}

function pushMapList<T>(map: Map<string, T[]>, key: string, value: T): void {
  const list = map.get(key);
  if (list) list.push(value);
  else map.set(key, [value]);
}

function parentConfigPath(path: string): string {
  const index = path.lastIndexOf('.');
  return index <= 0 ? '' : path.slice(0, index);
}

function isPlainObject(v: unknown): v is Record<string, unknown> { return typeof v === 'object' && v !== null && !Array.isArray(v); }
