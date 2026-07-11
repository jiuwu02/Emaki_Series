import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { type ApiClient } from '../../api';
import { Button, InlineError, type EditorChange } from '../../components';
import { t } from '../../i18n';
import { fileDisplayTitle, moduleDisplayName } from '../../lib';
import { applyConfigNodeOverrides, type SurfaceOutlineState, type SurfaceToolbarState } from '../../registry';
import type { WebConfigNode, WebRegistry, WebRegistryFile, WebRegistryModule } from '../../types';
import { configDraftScope, draftKey, draftScopeId, draftSignatureForScope, emptyDraftHistory, type ConfigDraftScope, type DraftHistoryMap, type DraftMap, type DraftPathsAction, type DraftScopeAction, type DraftValueSetter } from './ConfigDraftRuntime';
import { ConfigNodeTree } from './ConfigNodeTree';
import { ConfigCreateChildModal, ConfigDeleteObjectModal } from './ConfigObjectModals';
import { isDeletedPath, mergeConfigNodes } from './ConfigObjectRuntime';
import { DeferredConfigPreviewZone } from './ConfigPreviewZone';
import { configChanges, saveNodesBatch, type NodeSaveOutcome } from './ConfigSaveRuntime';
import { updateConfigSourceValue, useConfigSourceDocument, userFacingSaveError, type ConfigToast, type SourceEditController } from './ConfigSourceRuntime';

export type SaveConflict = {
  fileLabel: string;
  savedCount: number;
  pendingChanges: EditorChange[];
  onReplay: () => void | Promise<void>;
  onOverwrite: () => void | Promise<void>;
};

export type ConfigSaveSafety = {
  clearDraftPaths: DraftPathsAction;
  reconcileScopeDrafts: (scope: ConfigDraftScope, freshNodes: WebConfigNode[]) => void;
  setSaveConflict: (conflict: SaveConflict | null) => void;
};

export function ConfigStructuredSurface({ module, file, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, clearDraftPaths, reconcileScopeDrafts, setSaveConflict, undoDraftScope, redoDraftScope, api, refreshKey, onRefreshRegistry, setSurfaceToolbar, setSurfaceOutline, setToast }: { module: WebRegistryModule; file: WebRegistryFile; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; clearDraftPaths: DraftPathsAction; reconcileScopeDrafts: ConfigSaveSafety['reconcileScopeDrafts']; setSaveConflict: ConfigSaveSafety['setSaveConflict']; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; refreshKey: number; onRefreshRegistry: () => Promise<WebRegistry | null>; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setSurfaceOutline: (state: SurfaceOutlineState) => void; setToast: (toast: ConfigToast) => void }) {
  const scope = useMemo(() => configDraftScope(module, file), [module.id, file.id, file.path]);
  const scopeHistory = draftHistory[draftScopeId(scope)] ?? emptyDraftHistory();
  const source = useConfigSourceDocument({ module, file, api, refreshKey, setToast });
  const [savingNodes, setSavingNodes] = useState(false);
  useEffect(() => {
    setOptimisticNodes([]);
    setSourceEditedPaths(new Set());
    setDeletedObjectPaths(new Set());
  }, [file.id, refreshKey]);
  const [optimisticNodes, setOptimisticNodes] = useState<WebConfigNode[]>([]);
  const [sourceEditedPaths, setSourceEditedPaths] = useState<Set<string>>(() => new Set());
  const [deletedObjectPaths, setDeletedObjectPaths] = useState<Set<string>>(() => new Set());
  const moduleTitle = moduleDisplayName(module);
  const fileTitle = fileDisplayTitle(file);
  const baseNodes = useMemo(() => applyConfigNodeOverrides(module.id, file.nodes, file.path), [module.id, file.nodes, file.path]);
  const visibleNodes = useMemo(() => mergeConfigNodes(baseNodes, optimisticNodes, deletedObjectPaths), [baseNodes, optimisticNodes, deletedObjectPaths]);
  const scopeDraftKey = useMemo(() => draftSignatureForScope(drafts, scope), [drafts, scope.moduleId, scope.fileId, scope.filePath]);
  const changedNodes = useMemo(() => visibleNodes.filter(n => n.type !== 'object' && draftKey(scope, n.path) in drafts && !isDeletedPath(n.path, deletedObjectPaths)), [visibleNodes, scopeDraftKey, deletedObjectPaths, scope.moduleId, scope.fileId, scope.filePath]);

  const updateSourceNodeValue = (node: WebConfigNode, nextValue: unknown) => {
    updateConfigSourceValue(source, node.path, nextValue, setToast);
    setSourceEditedPaths(current => new Set([...current, node.path]));
    setOptimisticNodes(current => current.map(entry => entry.path === node.path ? { ...entry, value: nextValue } : entry));
  };
  const sourceEdit: SourceEditController = { paths: sourceEditedPaths, update: updateSourceNodeValue };

  async function saveNodes() {
    if (!changedNodes.length) {
      setToast({ tone: 'ok', text: t('core.toast.noChanges') });
      return;
    }
    setSavingNodes(true);
    try {
      const outcome = await saveNodesBatch(api, module.id, scope.filePath, changedNodes, path => drafts[draftKey(scope, path)], file.revision);
      clearDraftPaths(scope, outcome.savedPaths);
      if (outcome.status === 'ok') {
        await onRefreshRegistry();
        await source.reload(false);
        setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: outcome.savedPaths.length }) });
        return;
      }
      if (outcome.status === 'conflict') {
        presentConflict(outcome);
        return;
      }
      setToast({ tone: 'bad', text: userFacingSaveError(outcome.error) });
      if (outcome.savedPaths.length) { await onRefreshRegistry(); await source.reload(false); }
    } finally {
      setSavingNodes(false);
    }
  }

  function presentConflict(outcome: NodeSaveOutcome) {
    const pendingPaths = changedNodes.map(node => node.path).filter(path => !outcome.savedPaths.includes(path));
    const pendingChanges = configChanges(scope, changedNodes.filter(node => pendingPaths.includes(node.path)), drafts);
    const replay = async () => {
      const next = await onRefreshRegistry();
      const freshFile = next?.modules.find(m => m.id === module.id)?.files.find(f => f.id === file.id);
      reconcileScopeDrafts(scope, freshFile?.nodes ?? []);
      await source.reload(false);
      setSaveConflict(null);
      setToast({ tone: 'ok', text: t('core.toast.conflictReloaded') });
    };
    const overwrite = async () => {
      setSaveConflict(null);
      setSavingNodes(true);
      try {
        const retryNodes = changedNodes.filter(node => pendingPaths.includes(node.path));
        const retry = await saveNodesBatch(api, module.id, scope.filePath, retryNodes, path => drafts[draftKey(scope, path)], outcome.conflictRevision);
        clearDraftPaths(scope, retry.savedPaths);
        await onRefreshRegistry();
        await source.reload(false);
        setToast(retry.status === 'ok' ? { tone: 'ok', text: t('core.toast.savedConfig', { count: retry.savedPaths.length }) } : { tone: 'bad', text: userFacingSaveError(retry.error) });
      } finally {
        setSavingNodes(false);
      }
    };
    setSaveConflict({ fileLabel: fileDisplayTitle(file), savedCount: outcome.savedPaths.length, pendingChanges, onReplay: replay, onOverwrite: overwrite });
  }

  async function reloadStructured() {
    clearDraftScope(scope);
    setOptimisticNodes([]);
    setSourceEditedPaths(new Set());
    setDeletedObjectPaths(new Set());
    await onRefreshRegistry();
    await source.reload(false);
    setToast({ tone: 'ok', text: t('core.toast.reloaded') });
  }

  const deferredToolbarDrafts = useDeferredValue(drafts);
  const toolbarChanges = useMemo(() => changedNodes.length ? configChanges(scope, changedNodes, deferredToolbarDrafts) : [], [scope.moduleId, scope.fileId, scope.filePath, changedNodes, deferredToolbarDrafts]);
  const toolbarSource = source.dirty ? source.content : source.original;

  useEffect(() => {
    setSurfaceToolbar({
      title: moduleTitle,
      subtitle: `${fileTitle}，${file.path}`,
      dirty: changedNodes.length > 0 || source.dirty,
      changedCount: changedNodes.length + (source.dirty ? 1 : 0),
      changes: toolbarChanges,
      source: toolbarSource,
      sourceOriginal: source.original,
      sourceEditable: true,
      sourceError: source.error,
      sourceLanguage: 'yaml',
      saving: savingNodes || source.saving,
      loading: source.loading,
      canUndo: scopeHistory.undo.length > 0,
      canRedo: scopeHistory.redo.length > 0,
      onUndo: () => undoDraftScope(scope),
      onRedo: () => redoDraftScope(scope),
      onReload: () => void reloadStructured(),
      onSourceChange: source.update,
      onSave: source.dirty ? () => void source.save(async () => { clearDraftValues(scope); setOptimisticNodes([]); setSourceEditedPaths(new Set()); setDeletedObjectPaths(new Set()); await onRefreshRegistry(); await source.reload(false); }) : () => void saveNodes()
    });
  }, [moduleTitle, fileTitle, file.path, changedNodes.length, toolbarChanges, toolbarSource, savingNodes, source.dirty, source.error, source.saving, source.loading, scopeHistory.undo.length, scopeHistory.redo.length]);

  useEffect(() => () => setSurfaceToolbar(null), [setSurfaceToolbar]);

  const [createNode, setCreateNode] = useState<WebConfigNode | null>(null);
  const [deleteNode, setDeleteNode] = useState<WebConfigNode | null>(null);
  return <section className="config-surface">
    {source.loading && <div className="script-loading" role="status">{t('core.state.loading')}</div>}
    {source.error && <InlineError><span>{source.error}</span><Button size="sm" onClick={() => void reloadStructured()}>{t('core.action.retry')}</Button></InlineError>}
    {!source.loading && !source.error && <DeferredConfigPreviewZone module={module} file={file} path={file.path} nodes={visibleNodes} scope={scope} drafts={drafts} source={source} api={api} />}
    <ConfigNodeTree scope={scope} nodes={visibleNodes} outlineTitle={fileTitle} outlineSubtitle={file.path} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={setCreateNode} onDeleteObject={setDeleteNode} sourceEdit={!source.loading && !source.error ? sourceEdit : undefined} deletedPaths={deletedObjectPaths} setSurfaceOutline={setSurfaceOutline} />
    {createNode && <ConfigCreateChildModal scope={scope} node={createNode} source={source} onCancel={() => setCreateNode(null)} onCreated={nodes => { setOptimisticNodes(current => mergeConfigNodes(current, nodes, new Set())); setCreateNode(null); }} setToast={setToast} />}
    {deleteNode && <ConfigDeleteObjectModal node={deleteNode} source={source} onCancel={() => setDeleteNode(null)} onDeleted={path => { setDeletedObjectPaths(current => new Set([...current, path])); setOptimisticNodes(current => current.filter(entry => !entry.path.startsWith(`${path}.`) && entry.path !== path)); setDeleteNode(null); }} setToast={setToast} />}
  </section>;
}

export function ConfigChildSurface({ module, file, childPath, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, clearDraftPaths, reconcileScopeDrafts, setSaveConflict, undoDraftScope, redoDraftScope, api, refreshKey, setSurfaceToolbar, setSurfaceOutline, setToast }: { module: WebRegistryModule; file: WebRegistryFile; childPath: string; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; clearDraftPaths: DraftPathsAction; reconcileScopeDrafts: ConfigSaveSafety['reconcileScopeDrafts']; setSaveConflict: ConfigSaveSafety['setSaveConflict']; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; refreshKey: number; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setSurfaceOutline: (state: SurfaceOutlineState) => void; setToast: (toast: ConfigToast) => void }) {
  const scope = useMemo(() => configDraftScope(module, file, childPath), [module.id, file.id, file.path, childPath]);
  const scopeHistory = draftHistory[draftScopeId(scope)] ?? emptyDraftHistory();
  const source = useConfigSourceDocument({ module, file, childPath, api, refreshKey, setToast });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState<number | undefined>(undefined);
  const [saving, setSaving] = useState(false);
  const [baseNodes, setBaseNodes] = useState<WebConfigNode[]>([]);
  const [optimisticNodes, setOptimisticNodes] = useState<WebConfigNode[]>([]);
  const [sourceEditedPaths, setSourceEditedPaths] = useState<Set<string>>(() => new Set());
  const [deletedObjectPaths, setDeletedObjectPaths] = useState<Set<string>>(() => new Set());
  const fileTitle = fileDisplayTitle(file);
  const fileName = childPath.split('/').pop() ?? childPath;
  const visibleNodes = useMemo(() => mergeConfigNodes(baseNodes, optimisticNodes, deletedObjectPaths), [baseNodes, optimisticNodes, deletedObjectPaths]);
  const scopeDraftKey = useMemo(() => draftSignatureForScope(drafts, scope), [drafts, scope.moduleId, scope.fileId, scope.filePath]);
  const changedNodes = useMemo(() => visibleNodes.filter(n => n.type !== 'object' && draftKey(scope, n.path) in drafts && !isDeletedPath(n.path, deletedObjectPaths)), [visibleNodes, scopeDraftKey, deletedObjectPaths, scope.moduleId, scope.fileId, scope.filePath]);
  const updateSourceNodeValue = (node: WebConfigNode, nextValue: unknown) => {
    updateConfigSourceValue(source, node.path, nextValue, setToast);
    setSourceEditedPaths(current => new Set([...current, node.path]));
    setOptimisticNodes(current => current.map(entry => entry.path === node.path ? { ...entry, value: nextValue } : entry));
  };
  const sourceEdit: SourceEditController = { paths: sourceEditedPaths, update: updateSourceNodeValue };

  async function reloadChildNodes(announce = true, keepDrafts = false): Promise<WebConfigNode[]> {
    setLoading(true);
    setError('');
    try {
      const refreshed = await api.registryFileNodes(module.id, childPath);
      const overridden = applyConfigNodeOverrides(module.id, refreshed.nodes, childPath);
      setBaseNodes(overridden);
      setOptimisticNodes([]);
      setRevision(refreshed.revision);
      if (!keepDrafts) clearDraftScope(scope);
      setSourceEditedPaths(new Set());
      setDeletedObjectPaths(new Set());
      if (announce) setToast({ tone: 'ok', text: t('core.toast.reloaded') });
      return overridden;
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
      return [];
    } finally {
      setLoading(false);
    }
  }

  async function reloadChildSurface() {
    clearDraftScope(scope);
    setDeletedObjectPaths(new Set());
    setSourceEditedPaths(new Set());
    setBaseNodes([]);
    setOptimisticNodes([]);
    await Promise.all([reloadChildNodes(false), source.reload(false)]);
    setToast({ tone: 'ok', text: t('core.toast.reloaded') });
  }

  async function saveChild() {
    const changed = changedNodes.filter(node => draftKey(scope, node.path) in drafts);
    if (!changed.length) {
      setToast({ tone: 'ok', text: t('core.toast.noChanges') });
      return;
    }
    setSaving(true);
    try {
      const outcome = await saveNodesBatch(api, module.id, childPath, changed, path => drafts[draftKey(scope, path)], revision);
      setRevision(outcome.revision);
      clearDraftPaths(scope, outcome.savedPaths);
      if (outcome.status === 'ok') {
        await reloadChildNodes(false);
        setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: outcome.savedPaths.length }) });
        return;
      }
      if (outcome.status === 'conflict') {
        const pendingPaths = changed.map(node => node.path).filter(path => !outcome.savedPaths.includes(path));
        const pendingChanges = configChanges(scope, changed.filter(node => pendingPaths.includes(node.path)), drafts);
        const replay = async () => {
          const fresh = await reloadChildNodes(false, true);
          reconcileScopeDrafts(scope, fresh);
          await source.reload(false);
          setSaveConflict(null);
          setToast({ tone: 'ok', text: t('core.toast.conflictReloaded') });
        };
        const overwrite = async () => {
          setSaveConflict(null);
          setSaving(true);
          try {
            const retryNodes = changed.filter(node => pendingPaths.includes(node.path));
            const retry = await saveNodesBatch(api, module.id, childPath, retryNodes, path => drafts[draftKey(scope, path)], outcome.conflictRevision);
            setRevision(retry.revision);
            clearDraftPaths(scope, retry.savedPaths);
            await reloadChildNodes(false);
            setToast(retry.status === 'ok' ? { tone: 'ok', text: t('core.toast.savedConfig', { count: retry.savedPaths.length }) } : { tone: 'bad', text: userFacingSaveError(retry.error) });
          } finally {
            setSaving(false);
          }
        };
        setSaveConflict({ fileLabel: fileDisplayTitle(file), savedCount: outcome.savedPaths.length, pendingChanges, onReplay: replay, onOverwrite: overwrite });
        return;
      }
      setToast({ tone: 'bad', text: userFacingSaveError(outcome.error) });
      if (outcome.savedPaths.length) await reloadChildNodes(false);
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => {
    void reloadChildNodes(false);
  }, [module.id, childPath, refreshKey]);

  const deferredToolbarDrafts = useDeferredValue(drafts);
  const toolbarChanges = useMemo(() => changedNodes.length ? configChanges(scope, changedNodes, deferredToolbarDrafts) : [], [scope.moduleId, scope.fileId, scope.filePath, changedNodes, deferredToolbarDrafts]);
  const toolbarSource = source.dirty ? source.content : source.original;

  useEffect(() => {
    setSurfaceToolbar({
      title: fileName,
      subtitle: `${fileTitle} · ${childPath}`,
      dirty: changedNodes.length > 0 || source.dirty,
      changedCount: changedNodes.length + (source.dirty ? 1 : 0),
      changes: toolbarChanges,
      source: toolbarSource,
      sourceOriginal: source.original,
      sourceEditable: true,
      sourceError: source.error,
      sourceLanguage: 'yaml',
      saving: saving || source.saving,
      loading: loading || source.loading,
      canUndo: scopeHistory.undo.length > 0,
      canRedo: scopeHistory.redo.length > 0,
      onUndo: () => undoDraftScope(scope),
      onRedo: () => redoDraftScope(scope),
      onReload: () => void reloadChildSurface(),
      onSourceChange: source.update,
      onSave: source.dirty ? () => void source.save(async () => { clearDraftValues(scope); setDeletedObjectPaths(new Set()); setSourceEditedPaths(new Set()); setBaseNodes([]); setOptimisticNodes([]); await reloadChildNodes(false); }) : () => void saveChild()
    });
  }, [fileName, fileTitle, childPath, changedNodes.length, toolbarChanges, toolbarSource, saving, loading, source.dirty, source.error, source.saving, source.loading, scopeHistory.undo.length, scopeHistory.redo.length, revision]);

  useEffect(() => () => setSurfaceToolbar(null), [setSurfaceToolbar]);

  const [createNode, setCreateNode] = useState<WebConfigNode | null>(null);
  const [deleteNode, setDeleteNode] = useState<WebConfigNode | null>(null);
  return <section className="config-surface">
    {loading && <div className="script-loading" role="status">{t('core.state.loading')}</div>}
    {error && <InlineError><span>{error}</span><Button size="sm" onClick={() => void reloadChildNodes()}>{t('core.action.retry')}</Button></InlineError>}
    {!loading && !error && source.loading && <div className="script-loading" role="status">{t('core.state.loading')}</div>}
    {!loading && !error && source.error && <InlineError><span>{source.error}</span><Button size="sm" onClick={() => void source.reload(false)}>{t('core.action.retry')}</Button></InlineError>}
    {!loading && !error && !source.loading && !source.error && <DeferredConfigPreviewZone module={module} file={file} path={childPath} childPath={childPath} nodes={visibleNodes} scope={scope} drafts={drafts} source={source} api={api} />}
    {!loading && !error && <ConfigNodeTree scope={scope} nodes={visibleNodes} outlineTitle={fileName} outlineSubtitle={`${fileTitle} · ${childPath}`} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={setCreateNode} onDeleteObject={setDeleteNode} sourceEdit={!source.loading && !source.error ? sourceEdit : undefined} deletedPaths={deletedObjectPaths} setSurfaceOutline={setSurfaceOutline} />}
    {createNode && <ConfigCreateChildModal scope={scope} node={createNode} source={source} onCancel={() => setCreateNode(null)} onCreated={nodes => { setOptimisticNodes(current => mergeConfigNodes(current, nodes, new Set())); setCreateNode(null); }} setToast={setToast} />}
    {deleteNode && <ConfigDeleteObjectModal node={deleteNode} source={source} onCancel={() => setDeleteNode(null)} onDeleted={path => { setDeletedObjectPaths(current => new Set([...current, path])); setOptimisticNodes(current => current.filter(entry => !entry.path.startsWith(`${path}.`) && entry.path !== path)); setDeleteNode(null); }} setToast={setToast} />}
  </section>;
}
