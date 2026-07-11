import { useCallback, useMemo, useState } from 'react';
import { valuesEqual } from '../../lib';
import type { WebConfigNode } from '../../types';
import {
  applyDraftScopeSnapshot,
  dirtyTreeKeysFromDrafts,
  draftKey,
  draftKeyPath,
  draftScopeId,
  draftScopePrefix,
  draftScopeSnapshot,
  emptyDraftHistory,
  removeDraftHistoryScope,
  removeDraftScope,
  type ConfigDraftScope,
  type DraftHistoryMap,
  type DraftMap,
  type DraftPathsAction,
  type DraftScopeAction,
  type DraftValueSetter
} from './ConfigDraftRuntime';
import type { SaveConflict } from './ConfigStructuredSurface';

export type ConfigDraftState = {
  drafts: DraftMap;
  draftHistory: DraftHistoryMap;
  saveConflict: SaveConflict | null;
  setSaveConflict: (conflict: SaveConflict | null) => void;
  dirtyTreeKeys: Set<string>;
  setDraftValue: DraftValueSetter;
  clearDraftScope: DraftScopeAction;
  clearDraftValues: DraftScopeAction;
  clearDraftPaths: DraftPathsAction;
  undoDraftScope: DraftScopeAction;
  redoDraftScope: DraftScopeAction;
  reconcileScopeDrafts: (scope: ConfigDraftScope, freshNodes: WebConfigNode[]) => void;
  resetConfigDrafts: () => void;
};

export function useConfigDraftState(): ConfigDraftState {
  const [drafts, setDrafts] = useState<DraftMap>({});
  const [draftHistory, setDraftHistory] = useState<DraftHistoryMap>({});
  const [saveConflict, setSaveConflict] = useState<SaveConflict | null>(null);

  const rememberDraftHistory = useCallback((scope: ConfigDraftScope, before: DraftMap, after: DraftMap) => {
    const id = draftScopeId(scope);
    setDraftHistory(current => {
      const history = current[id] ?? emptyDraftHistory();
      return { ...current, [id]: { undo: [...history.undo, { before, after }].slice(-20), redo: [] } };
    });
  }, []);

  const setDraftValue = useCallback<DraftValueSetter>((scope, node, nextValue) => {
    const key = draftKey(scope, node.path);
    setDrafts(current => {
      const hadKey = key in current;
      const shouldDelete = valuesEqual(nextValue, node.value);
      if (shouldDelete && !hadKey) return current;
      if (!shouldDelete && hadKey && valuesEqual(current[key], nextValue)) return current;
      const before = draftScopeSnapshot(current, scope);
      const next = { ...current };
      if (shouldDelete) delete next[key];
      else next[key] = nextValue;
      const after = draftScopeSnapshot(next, scope);
      if (valuesEqual(before, after)) return current;
      rememberDraftHistory(scope, before, after);
      return next;
    });
  }, [rememberDraftHistory]);

  const undoDraftScope = useCallback<DraftScopeAction>((scope) => {
    const id = draftScopeId(scope);
    const history = draftHistory[id];
    const entry = history?.undo[history.undo.length - 1];
    if (!history || !entry) return;
    setDrafts(draftState => applyDraftScopeSnapshot(draftState, scope, entry.before));
    setDraftHistory(current => {
      const latest = current[id] ?? emptyDraftHistory();
      return { ...current, [id]: { undo: latest.undo.slice(0, -1), redo: [entry, ...latest.redo].slice(0, 20) } };
    });
  }, [draftHistory]);

  const redoDraftScope = useCallback<DraftScopeAction>((scope) => {
    const id = draftScopeId(scope);
    const history = draftHistory[id];
    const entry = history?.redo[0];
    if (!history || !entry) return;
    setDrafts(draftState => applyDraftScopeSnapshot(draftState, scope, entry.after));
    setDraftHistory(current => {
      const latest = current[id] ?? emptyDraftHistory();
      return { ...current, [id]: { undo: [...latest.undo, entry].slice(-20), redo: latest.redo.slice(1) } };
    });
  }, [draftHistory]);

  const clearDraftScope = useCallback<DraftScopeAction>((scope) => {
    setDrafts(current => removeDraftScope(current, scope));
    setDraftHistory(current => removeDraftHistoryScope(current, scope));
  }, []);

  const clearDraftValues = useCallback<DraftScopeAction>((scope) => {
    setDrafts(current => removeDraftScope(current, scope));
  }, []);

  const clearDraftPaths = useCallback<DraftPathsAction>((scope, paths) => {
    if (!paths.length) return;
    setDrafts(current => {
      const next = { ...current };
      let changed = false;
      for (const path of paths) {
        const key = draftKey(scope, path);
        if (key in next) { delete next[key]; changed = true; }
      }
      return changed ? next : current;
    });
  }, []);

  const reconcileScopeDrafts = useCallback((scope: ConfigDraftScope, freshNodes: WebConfigNode[]) => {
    const nodeByPath = new Map(freshNodes.map(node => [node.path, node]));
    setDrafts(current => {
      const prefix = draftScopePrefix(scope);
      const next = { ...current };
      let changed = false;
      for (const key of Object.keys(next)) {
        if (!key.startsWith(prefix)) continue;
        const path = draftKeyPath(key);
        const node = path ? nodeByPath.get(path) : undefined;
        if (node && valuesEqual(node.value, next[key])) { delete next[key]; changed = true; }
      }
      return changed ? next : current;
    });
  }, []);

  const resetConfigDrafts = useCallback(() => {
    setDrafts({});
    setDraftHistory({});
    setSaveConflict(null);
  }, []);

  const dirtyTreeKeys = useMemo(() => dirtyTreeKeysFromDrafts(drafts), [drafts]);

  return {
    drafts,
    draftHistory,
    saveConflict,
    setSaveConflict,
    dirtyTreeKeys,
    setDraftValue,
    clearDraftScope,
    clearDraftValues,
    clearDraftPaths,
    undoDraftScope,
    redoDraftScope,
    reconcileScopeDrafts,
    resetConfigDrafts
  };
}
