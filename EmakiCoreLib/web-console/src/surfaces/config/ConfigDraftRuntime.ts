import { treeDirtyKey } from '../../documentPaths';
import type { WebConfigNode } from '../../types';

export type DraftMap = Record<string, unknown>;
export type ConfigDraftScope = { moduleId: string; fileId: string; filePath: string };
export type DraftHistoryEntry = { before: DraftMap; after: DraftMap };
export type DraftScopeHistory = { undo: DraftHistoryEntry[]; redo: DraftHistoryEntry[] };
export type DraftHistoryMap = Record<string, DraftScopeHistory>;
export type DraftValueSetter = (scope: ConfigDraftScope, node: WebConfigNode, next: unknown) => void;
export type DraftScopeAction = (scope: ConfigDraftScope) => void;
export type DraftPathsAction = (scope: ConfigDraftScope, paths: string[]) => void;

export function configDraftScope(module: { id: string }, file: { id: string; path: string }, childPath?: string): ConfigDraftScope {
  return { moduleId: module.id, fileId: file.id, filePath: normalizeDraftPath(childPath || file.path) };
}

export function draftScopeId(scope: ConfigDraftScope) {
  return JSON.stringify([scope.moduleId, scope.fileId, normalizeDraftPath(scope.filePath)]);
}

export function draftKey(scope: ConfigDraftScope, path: string) {
  return JSON.stringify([scope.moduleId, scope.fileId, normalizeDraftPath(scope.filePath), path]);
}

export function draftScopePrefix(scope: ConfigDraftScope) {
  return `${draftScopeId(scope).slice(0, -1)},`;
}

export function draftSignatureForScope(drafts: DraftMap, scope: ConfigDraftScope): string {
  const prefix = draftScopePrefix(scope);
  let signature = '';
  for (const key of Object.keys(drafts)) {
    if (!key.startsWith(prefix)) continue;
    signature += `${key}=${String(drafts[key])}\u001f`;
  }
  return signature;
}

export function emptyDraftHistory(): DraftScopeHistory { return { undo: [], redo: [] }; }

export function draftScopeSnapshot(drafts: DraftMap, scope: ConfigDraftScope): DraftMap {
  const prefix = draftScopePrefix(scope);
  return Object.fromEntries(Object.entries(drafts).filter(([key]) => key.startsWith(prefix)));
}

export function applyDraftScopeSnapshot(drafts: DraftMap, scope: ConfigDraftScope, snapshot: DraftMap): DraftMap {
  return { ...removeDraftScope(drafts, scope), ...snapshot };
}

export function removeDraftScope(drafts: DraftMap, scope: ConfigDraftScope): DraftMap {
  const prefix = draftScopePrefix(scope);
  let changed = false;
  const next = { ...drafts };
  for (const key of Object.keys(next)) {
    if (!key.startsWith(prefix)) continue;
    delete next[key];
    changed = true;
  }
  return changed ? next : drafts;
}

export function removeDraftHistoryScope(history: DraftHistoryMap, scope: ConfigDraftScope): DraftHistoryMap {
  const id = draftScopeId(scope);
  if (!(id in history)) return history;
  const next = { ...history };
  delete next[id];
  return next;
}

export function dirtyTreeKeysFromDrafts(drafts: DraftMap): Set<string> {
  const keys = new Set<string>();
  for (const key of Object.keys(drafts)) {
    const parts = parseDraftKey(key);
    if (!parts) continue;
    keys.add(treeDirtyKey(parts.moduleId, parts.fileId, parts.filePath));
  }
  return keys;
}

export function draftKeyPath(key: string): string | null {
  try {
    const value = JSON.parse(key);
    return Array.isArray(value) && value.length >= 4 ? String(value[3]) : null;
  } catch {
    return null;
  }
}

export function normalizeDraftPath(path: string) { return path.replace(/\\/g, '/'); }

function parseDraftKey(key: string): ConfigDraftScope | null {
  try {
    const value = JSON.parse(key);
    if (!Array.isArray(value) || value.length < 4) return null;
    return { moduleId: String(value[0]), fileId: String(value[1]), filePath: normalizeDraftPath(String(value[2])) };
  } catch {
    return null;
  }
}
