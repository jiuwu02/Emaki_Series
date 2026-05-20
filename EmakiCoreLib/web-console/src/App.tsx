import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import type { Completion, CompletionContext, CompletionResult, CompletionSource } from '@codemirror/autocomplete';
import type { ComponentType } from 'react';
import { ApiClient } from './api';
import { GuiEditorSurface } from './GuiEditorSurface';
import { ItemEditorSurface } from './ItemEditorSurface';
import { loadWebExtensions } from './extensions';
import { applyConfigNodeOverrides, applyConfigRegistryOverrides, applyEditorDescriptorOverrides, getSourceDocumentAdapter, getSurface, isKind, registerSourceDocumentAdapter, registerSurface, setRuntimeEnums } from './registry';
import { getLocale, getRegisteredLocales, setLocale, t } from './i18n';
import { ActionGroup, Button, CodeEditor, EditorChrome, InlineError, ToastNotice, type EditorChange } from './components';
import { useDialogFocus } from './components/useDialogFocus';
import { I18nBundleModal, type I18nTarget } from './I18nBundleModal';
import { configNodeDisplayComment as resolveConfigNodeComment, fieldLabel, fileDisplayComment, fileDisplayTitle, humanizeFieldLabel, moduleDisplayName, optionLabel, parseYaml, serializeYaml, setDeepValue, valuesEqual } from './lib';
import { Login, ResizableRail, WorkspaceTree, fileKindLabel } from './shell';
import type { SurfaceProps, SurfaceToolbarState } from './registry';
import type { RegistryTreeNode, WebConfigCreateTemplate, WebConfigFieldSchema, WebConfigNode, WebRegistry, WebRegistryFile, WebRegistryModule } from './types';

// Register CoreLib's built-in surfaces through the same registry used by plugin extensions.
registerSurface({ kind: 'GUI', component: GuiEditorSurface as ComponentType<SurfaceProps>, label: t('core.surface.gui.label') });
registerSurface({ kind: 'ITEM', component: ItemEditorSurface as ComponentType<SurfaceProps>, label: t('core.surface.item.label') });
for (const kind of ['CONFIG', 'GUI', 'ITEM', 'SCRIPT']) {
  registerSourceDocumentAdapter({
    kind,
    adapter: {
      read: (api, context) => api.readTextDocument({ kind, moduleId: context.module.id, path: context.childPath || context.file.path }),
      save: (api, context, content, revision) => api.saveTextDocument({ kind, moduleId: context.module.id, path: context.childPath || context.file.path }, content, revision),
      language: kind === 'SCRIPT' ? 'javascript' : 'yaml'
    }
  });
}

type Selection = { moduleId: string; fileId: string; scriptPath?: string; refreshKey?: number };
type DraftMap = Record<string, unknown>;
type ConfigDraftScope = { moduleId: string; fileId: string; filePath: string };
type DraftHistoryEntry = { before: DraftMap; after: DraftMap };
type DraftScopeHistory = { undo: DraftHistoryEntry[]; redo: DraftHistoryEntry[] };
type DraftHistoryMap = Record<string, DraftScopeHistory>;
type DraftValueSetter = (scope: ConfigDraftScope, node: WebConfigNode, next: unknown) => void;
type DraftScopeAction = (scope: ConfigDraftScope) => void;
type RegistryLoadOptions = { initial?: boolean; clearDrafts?: boolean; announceRefresh?: boolean };
type ConfigSourceDocument = ReturnType<typeof useConfigSourceDocument>;
type SourceEditController = { paths: Set<string>; update: (node: WebConfigNode, next: unknown) => void };

type Toast = { tone: 'ok' | 'bad'; text: string } | null;
type ColorTheme = 'dark' | 'light';

const COLOR_THEMES: { id: ColorTheme; labelKey: string }[] = [
  { id: 'dark', labelKey: 'core.theme.dark' },
  { id: 'light', labelKey: 'core.theme.light' }
];
const LOCALE_LABELS: Record<string, string> = { 'zh-CN': '简体中文', zh_CN: '简体中文', zh: '简体中文', 'en-US': 'English', en_US: 'English' };

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('emaki-web-token'));
  const [sessionExpired, setSessionExpired] = useState(false);
  const [registry, setRegistry] = useState<WebRegistry | null>(null);
  const [selected, setSelected] = useState<Selection | null>(null);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [drafts, setDrafts] = useState<DraftMap>({});
  const [draftHistory, setDraftHistory] = useState<DraftHistoryMap>({});
  const [toast, setToast] = useState<Toast>(null);
  const [theme, setTheme] = useState<ColorTheme>(() => readTheme());
  const [localeVersion, setLocaleVersion] = useState(0);
  const [i18nTarget, setI18nTarget] = useState<I18nTarget | null>(null);
  const [createTarget, setCreateTarget] = useState<RegistryTreeNode | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<RegistryTreeNode | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [surfaceToolbar, setSurfaceToolbar] = useState<SurfaceToolbarState | null>(null);
  const [surfaceDirtyKeys, setSurfaceDirtyKeys] = useState<Set<string>>(() => new Set());

  const api = useMemo(() => new ApiClient(token, () => {
    sessionStorage.removeItem('emaki-web-token');
    setSessionExpired(true);
    setToken(null);
  }), [token]);

  useEffect(() => { if (token) void loadRegistry({ initial: true, clearDrafts: true }); }, [token]);
  useEffect(() => {
    if (!token) return;
    const report = (message: string, source: string, detail?: string, stack?: string) => {
      void api.reportFrontendError({ message, source, detail, stack });
    };
    const handleError = (event: ErrorEvent) => {
      report(event.message || 'Unhandled frontend error', 'window.error', `${event.filename ?? ''}:${event.lineno ?? 0}:${event.colno ?? 0}`, event.error?.stack);
    };
    const handleRejection = (event: PromiseRejectionEvent) => {
      const reason = event.reason;
      report(reason instanceof Error ? reason.message : String(reason ?? 'Unhandled promise rejection'), 'unhandledrejection', undefined, reason instanceof Error ? reason.stack : undefined);
    };
    window.addEventListener('error', handleError);
    window.addEventListener('unhandledrejection', handleRejection);
    return () => {
      window.removeEventListener('error', handleError);
      window.removeEventListener('unhandledrejection', handleRejection);
    };
  }, [api, token]);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('emaki-color-theme', theme);
  }, [theme]);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  function setDraftValue(scope: ConfigDraftScope, node: WebConfigNode, nextValue: unknown) {
    const key = draftKey(scope, node.path);
    const before = draftScopeSnapshot(drafts, scope);
    const next = { ...drafts };
    if (valuesEqual(nextValue, node.value)) delete next[key];
    else next[key] = nextValue;
    const after = draftScopeSnapshot(next, scope);
    if (valuesEqual(before, after)) return;
    setDrafts(next);
    rememberDraftHistory(scope, before, after);
  }

  function rememberDraftHistory(scope: ConfigDraftScope, before: DraftMap, after: DraftMap) {
    const id = draftScopeId(scope);
    setDraftHistory(current => {
      const history = current[id] ?? emptyDraftHistory();
      return { ...current, [id]: { undo: [...history.undo, { before, after }].slice(-20), redo: [] } };
    });
  }

  function undoDraftScope(scope: ConfigDraftScope) {
    const id = draftScopeId(scope);
    const history = draftHistory[id];
    const entry = history?.undo[history.undo.length - 1];
    if (!history || !entry) return;
    setDrafts(draftState => applyDraftScopeSnapshot(draftState, scope, entry.before));
    setDraftHistory(current => {
      const latest = current[id] ?? emptyDraftHistory();
      return { ...current, [id]: { undo: latest.undo.slice(0, -1), redo: [entry, ...latest.redo].slice(0, 20) } };
    });
  }

  function redoDraftScope(scope: ConfigDraftScope) {
    const id = draftScopeId(scope);
    const history = draftHistory[id];
    const entry = history?.redo[0];
    if (!history || !entry) return;
    setDrafts(draftState => applyDraftScopeSnapshot(draftState, scope, entry.after));
    setDraftHistory(current => {
      const latest = current[id] ?? emptyDraftHistory();
      return { ...current, [id]: { undo: [...latest.undo, entry].slice(-20), redo: latest.redo.slice(1) } };
    });
  }

  function clearDraftScope(scope: ConfigDraftScope) {
    setDrafts(current => removeDraftScope(current, scope));
    setDraftHistory(current => removeDraftHistoryScope(current, scope));
  }

  function clearDraftValues(scope: ConfigDraftScope) {
    setDrafts(current => removeDraftScope(current, scope));
  }

  async function loadRegistry(options: RegistryLoadOptions = {}): Promise<WebRegistry | null> {
    const { initial = false, clearDrafts = false, announceRefresh = !initial } = options;
    setLoading(true);
    try {
      const next = await api.registry();
      setRuntimeEnums(next.runtimeEnums);
      const extensionStatuses = await loadWebExtensions(next.extensions);
      const failedExtensions = extensionStatuses.filter(status => status.status === 'failed');
      const merged = applyConfigRegistryOverrides(applyEditorDescriptorOverrides(next));
      setRegistry(merged);
      if (initial) setExpanded(Object.fromEntries(merged.modules.map((m) => [m.id, true])));
      setSelected((c) => c ?? firstSelection(merged));
      if (clearDrafts) {
        setDrafts({});
        setDraftHistory({});
      }
      if (failedExtensions.length) setToast({ tone: 'bad', text: t('core.toast.extensionLoadFailed', { count: failedExtensions.length }) });
      else if (announceRefresh) setToast({ tone: 'ok', text: t('core.toast.registryRefreshed') });
      return merged;
    } catch (err) {
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
      return null;
    } finally {
      setLoading(false);
    }
  }

  async function reloadCurrentSurface() {
    const scope = selectedModule && selectedFile && isKind(selectedFile.kind, 'CONFIG') ? configDraftScope(selectedModule, selectedFile, selected?.scriptPath) : null;
    const next = await loadRegistry({ clearDrafts: false, announceRefresh: false });
    if (!next) return;
    if (scope) clearDraftScope(scope);
    setSelected(current => current ? { ...current, refreshKey: (current.refreshKey ?? 0) + 1 } : firstSelection(next));
    setToast({ tone: 'ok', text: t('core.toast.reloaded') });
  }

  async function saveCurrent() {
    if (!selectedModule || !selectedFile || !selectedDraftScope) return;
    const changes = configChanges(selectedDraftScope, selectedFile.nodes, drafts);
    if (!changes.length) return;
    setSaving(true);
    try {
      let revision = selectedFile.revision;
      for (const node of changes) {
        const result = await api.saveRegistryValue(selectedModule.id, selectedDraftScope.filePath, node.path, drafts[draftKey(selectedDraftScope, node.path)], revision);
        revision = result.revision ?? revision;
      }
      clearDraftValues(selectedDraftScope);
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: changes.length }) });
      await loadRegistry({ clearDrafts: false, announceRefresh: false });
    } catch (err) {
      setToast({ tone: 'bad', text: userFacingSaveError(err) });
    } finally {
      setSaving(false);
    }
  }

  async function createFileFromTree(target: RegistryTreeNode, name: string) {
    if (!target.moduleId || !target.fileId) return;
    try {
      const created = await api.createFile(target.moduleId, target.fileId, name);
      setCreateTarget(null);
      const next = await loadRegistry({ clearDrafts: false, announceRefresh: false });
      setSelected({ moduleId: target.moduleId, fileId: target.fileId, scriptPath: created.path, refreshKey: Date.now() });
      setToast({ tone: 'ok', text: t('core.file.created', { path: created.path }) });
      if (next) setExpanded(current => ({ ...current, [target.id]: true }));
    } catch (err) {
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.file.createFailed') });
    }
  }

  async function deleteFileFromTree(target: RegistryTreeNode, confirmPath: string) {
    if (!target.moduleId || !target.childPath) return;
    try {
      await api.deleteFile(target.moduleId, target.fileId, target.childPath, confirmPath);
      setDeleteTarget(null);
      setSurfaceDirtyKeys(current => {
        const next = new Set(current);
        if (target.fileId) next.delete(treeDirtyKey(target.moduleId!, target.fileId, target.childPath!));
        return next;
      });
      await loadRegistry({ clearDrafts: false, announceRefresh: false });
      if (selected?.moduleId === target.moduleId && selected.fileId === target.fileId && selected.scriptPath === target.childPath) setSelected(null);
      setToast({ tone: 'ok', text: t('core.file.deleted', { path: target.childPath }) });
    } catch (err) {
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.file.deleteFailed') });
    }
  }

  const selectedModule = selected && registry ? registry.modules.find((m) => m.id === selected.moduleId) ?? null : null;
  const selectedFile = selectedModule && selected ? selectedModule.files.find((f) => f.id === selected.fileId) ?? null : null;
  const selectedDraftScope = selectedModule && selectedFile && isKind(selectedFile.kind, 'CONFIG') ? configDraftScope(selectedModule, selectedFile, selected?.scriptPath) : null;
  const selectedScopeHistory = selectedDraftScope ? draftHistory[draftScopeId(selectedDraftScope)] ?? emptyDraftHistory() : emptyDraftHistory();
  const changedCount = selectedDraftScope && selectedFile ? selectedFile.nodes.filter((n) => n.type !== 'object' && draftKey(selectedDraftScope, n.path) in drafts).length : 0;
  const dirtyTreeKeys = dirtyTreeKeysFromDrafts(drafts);
  const activeTheme = COLOR_THEMES.find((entry) => entry.id === theme) ?? COLOR_THEMES[0];
  const activeThemeLabel = t(activeTheme.labelKey);
  const nextTheme = () => setTheme((current) => COLOR_THEMES[(COLOR_THEMES.findIndex((entry) => entry.id === current) + 1) % COLOR_THEMES.length].id);
  const selectedEditor = selectedFile?.editorId ? registry?.editors?.[selectedFile.editorId] : undefined;
  const selectedSource = '';
  const selectedDirtyKey = selectedModule && selectedFile ? treeDirtyKey(selectedModule.id, selectedFile.id, selected?.scriptPath ?? selectedFile.path) : null;
  const fallbackToolbar: SurfaceToolbarState = {
    title: selectedModule ? moduleDisplayName(selectedModule) : t('core.stage.defaultTitle'),
    subtitle: selectedFile ? `${fileDisplayTitle(selectedFile)}，${selectedFile.path}` : t('core.stage.defaultHint'),
    dirty: changedCount > 0,
    changedCount,
    changes: selectedDraftScope && selectedFile ? configChanges(selectedDraftScope, selectedFile.nodes, drafts) : [],
    source: selectedSource,
    saving,
    loading,
    canUndo: selectedScopeHistory.undo.length > 0,
    canRedo: selectedScopeHistory.redo.length > 0,
    onUndo: selectedDraftScope ? () => undoDraftScope(selectedDraftScope) : undefined,
    onRedo: selectedDraftScope ? () => redoDraftScope(selectedDraftScope) : undefined,
    onReload: () => void reloadCurrentSurface(),
    onSave: () => void saveCurrent()
  };
  const toolbar = surfaceToolbar ?? fallbackToolbar;
  const mergedDirtyKeys = useMemo(() => new Set([...dirtyTreeKeys, ...surfaceDirtyKeys]), [dirtyTreeKeys, surfaceDirtyKeys]);
  const locales = getRegisteredLocales();
  const currentLocale = getLocale();
  const currentLocaleLabel = localeLabel(currentLocale);
  const changeLocale = (next: string) => { setLocale(next); setLocaleVersion((version) => version + 1); };

  useEffect(() => {
    if (!selectedDirtyKey) return;
    setSurfaceDirtyKeys(current => {
      const next = new Set(current);
      if (toolbar.dirty) next.add(selectedDirtyKey);
      else next.delete(selectedDirtyKey);
      return next;
    });
  }, [selectedDirtyKey, toolbar.dirty]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (!selectedDraftScope || saving || loading || sourceEditingElement(event.target)) return;
      const mod = event.ctrlKey || event.metaKey;
      if (!mod) return;
      const key = event.key.toLowerCase();
      if (key === 'z' && !event.shiftKey && selectedScopeHistory.undo.length > 0) {
        event.preventDefault();
        undoDraftScope(selectedDraftScope);
      } else if ((key === 'y' || (key === 'z' && event.shiftKey)) && selectedScopeHistory.redo.length > 0) {
        event.preventDefault();
        redoDraftScope(selectedDraftScope);
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [selectedDraftScope, selectedScopeHistory.undo.length, selectedScopeHistory.redo.length, saving, loading]);

  if (!token) return <Login sessionExpired={sessionExpired} onLogin={(t) => { sessionStorage.setItem('emaki-web-token', t); setSessionExpired(false); setToken(t); }} />;

  return (
    <div className="workbench" data-locale-version={localeVersion}>
      {toast && <ToastNotice tone={toast.tone}>{toast.text}</ToastNotice>}
      {createTarget && <CreateFileModal target={createTarget} onCancel={() => setCreateTarget(null)} onCreate={createFileFromTree} />}
      {deleteTarget && <DeleteFileModal target={deleteTarget} onCancel={() => setDeleteTarget(null)} onDelete={deleteFileFromTree} />}
      {i18nTarget && <I18nBundleModal target={i18nTarget} onClose={() => setI18nTarget(null)} onSaved={() => { setLocaleVersion((version) => version + 1); setToast({ tone: 'ok', text: t('core.i18n.saved') }); }} />}
      <ResizableRail>
        <div className="brand-block">
          <div className="brand-main">
            <span className="brand-mark" aria-hidden="true"><EmakiParentMark /></span>
            <div className="brand-copy">
              <strong>{t('core.brand.name')}</strong>
              <small>{t('core.brand.subtitle')}</small>
            </div>
          </div>
          <div className="rail-controls">
            <button type="button" className={`theme-toggle icon-only ${theme}`} onClick={nextTheme} title={t('core.theme.switchTitle', { theme: activeThemeLabel })} aria-label={t('core.theme.switchAria', { theme: activeThemeLabel })}>
              <ThemeIcon key={theme} theme={theme} />
            </button>
            <label className="locale-toggle icon-only" title={currentLocaleLabel} aria-label={t('core.locale.switchAria', { locale: currentLocaleLabel })}>
              <LocaleIcon />
              <select value={currentLocale} onChange={(event) => changeLocale(event.target.value)} disabled={!locales.length}>
                {locales.length ? locales.map((locale) => <option key={locale} value={locale}>{localeLabel(locale)}</option>) : <option>{t('core.i18n.noLocales')}</option>}
              </select>
            </label>
          </div>
        </div>
        <WorkspaceTree registry={registry} selected={selected} expanded={expanded} dirtyKeys={mergedDirtyKeys} setExpanded={setExpanded} onOpenI18n={setI18nTarget} onCreateFile={setCreateTarget} onDeleteFile={setDeleteTarget} onSelect={(next) => setSelected((current) => sameSelection(current, next) ? { ...next, refreshKey: (current?.refreshKey ?? 0) + 1 } : next)} />
        <button className="rail-action quiet" onClick={() => { sessionStorage.removeItem('emaki-web-token'); setToken(null); }}>{t('core.auth.logout')}</button>
      </ResizableRail>
      <main className="stage">
        <EditorChrome
          className="stage-head"
          title={toolbar.title ?? (selectedModule ? moduleDisplayName(selectedModule) : t('core.stage.defaultTitle'))}
          subtitle={toolbar.subtitle ?? (selectedFile ? `${fileDisplayTitle(selectedFile)}，${selectedFile.path}` : t('core.stage.defaultHint'))}
          dirty={toolbar.dirty}
          changedCount={toolbar.changedCount}
          changes={toolbar.changes ?? []}
          source={toolbar.source ?? ''}
          sourceOriginal={toolbar.sourceOriginal}
          sourceEditable={toolbar.sourceEditable}
          sourceError={toolbar.sourceError}
          sourceLanguage={toolbar.sourceLanguage}
          saving={toolbar.saving ?? false}
          loading={toolbar.loading ?? false}
          saveLabel={toolbar.saveLabel}
          sourceLabel={toolbar.sourceLabel}
          reloadLabel={toolbar.reloadLabel}
          canUndo={toolbar.canUndo}
          canRedo={toolbar.canRedo}
          onUndo={toolbar.onUndo}
          onRedo={toolbar.onRedo}
          onReload={toolbar.onReload}
          onSourceChange={toolbar.onSourceChange}
          onSave={toolbar.onSave}
        />
        <section className="editor-shell single">
          <ConfigSurface registry={registry} module={selectedModule} file={selectedFile} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} scriptPath={selected?.scriptPath} refreshKey={selected?.refreshKey ?? 0} onReload={() => void reloadCurrentSurface()} onRefreshRegistry={() => loadRegistry({ clearDrafts: false, announceRefresh: false })} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} />
        </section>
      </main>
    </div>
  );
}

function CreateFileModal({ target, onCancel, onCreate }: { target: RegistryTreeNode; onCancel: () => void; onCreate: (target: RegistryTreeNode, name: string) => void | Promise<void> }) {
  const [name, setName] = useState('');
  function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) return;
    void onCreate(target, name.trim());
  }
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <form className="file-action-dialog" role="dialog" aria-modal="true" aria-labelledby="file-create-title" onSubmit={submit}>
      <div className="reload-confirm-head"><span>{fileKindLabel(target.kind)}</span><h3 id="file-create-title">{t('core.file.createTitle')}</h3></div>
      <div className="reload-confirm-body"><p>{t('core.file.createDesc')}</p><label className="file-confirm-field"><span>{t('core.file.createName')}</span><input autoFocus value={name} onChange={event => setName(event.target.value)} placeholder={t('core.file.createPlaceholder')} /></label></div>
      <ActionGroup className="reload-confirm-actions"><Button type="button" onClick={onCancel}>{t('core.gui.cancel')}</Button><Button type="submit" variant="primary" disabled={!name.trim()}>{t('core.file.create')}</Button></ActionGroup>
    </form>
  </div>;
}

function DeleteFileModal({ target, onCancel, onDelete }: { target: RegistryTreeNode; onCancel: () => void; onDelete: (target: RegistryTreeNode, confirmPath: string) => void | Promise<void> }) {
  const expected = target.childPath ?? target.path ?? '';
  const [confirmPath, setConfirmPath] = useState('');
  function submit(event: FormEvent) {
    event.preventDefault();
    if (confirmPath !== expected) return;
    void onDelete(target, confirmPath);
  }
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <form className="file-action-dialog danger" role="dialog" aria-modal="true" aria-labelledby="file-delete-title" onSubmit={submit}>
      <div className="reload-confirm-head"><span>{t('core.tree.deleteFile')}</span><h3 id="file-delete-title">{t('core.file.deleteTitle')}</h3></div>
      <div className="reload-confirm-body"><p>{t('core.file.deleteDesc', { path: expected })}</p><code className="file-delete-path">{expected}</code><label className="file-confirm-field"><span>{t('core.file.deleteConfirmLabel')}</span><input autoFocus value={confirmPath} onChange={event => setConfirmPath(event.target.value)} placeholder={expected} /></label></div>
      <ActionGroup className="reload-confirm-actions"><Button type="button" onClick={onCancel}>{t('core.gui.cancel')}</Button><Button type="submit" variant="danger" disabled={confirmPath !== expected}>{t('core.file.delete')}</Button></ActionGroup>
    </form>
  </div>;
}

function ConfigSurface({ registry, module, file, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, undoDraftScope, redoDraftScope, api, scriptPath, refreshKey, onReload, onRefreshRegistry, setSurfaceToolbar, setToast }: { registry: WebRegistry | null; module: WebRegistryModule | null; file: WebRegistryFile | null; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; scriptPath?: string; refreshKey: number; onReload?: () => void; onRefreshRegistry: () => Promise<WebRegistry | null>; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: Toast) => void }) {
  useEffect(() => {
    setSurfaceToolbar(null);
    return () => setSurfaceToolbar(null);
  }, [module?.id, file?.id, scriptPath]);

  if (registry && registry.modules.length === 0) return <section className="config-surface empty" role="status">{t('core.empty.noRegistry')}</section>;
  if (!module || !file) return <section className="config-surface empty" role="status">{t('core.empty.selectConfig')}</section>;
  const editor = file.editorId ? registry?.editors?.[file.editorId] : undefined;

  // Check registry for a custom surface first
  const registeredSurface = getSurface(file, editor);
  if (registeredSurface && !isKind(file.kind, 'CONFIG') && !isKind(file.kind, 'SCRIPT')) {
    const SurfaceComponent = registeredSurface.component;
    return <SurfaceComponent module={module} file={file} api={api} childPath={scriptPath} refreshKey={refreshKey} editor={editor} onReload={onReload} setToolbar={setSurfaceToolbar} showLocalChrome={false} />;
  }

  if (isKind(file.kind, 'SCRIPT')) return <section className="config-surface script-surface"><div className="surface-head"><div><h2>{fileDisplayTitle(file)}</h2><p>{fileDisplayComment(file)}</p></div><span className="file-kind script">{fileKindLabel(file.kind)}</span></div>{scriptPath ? <ScriptEditor api={api} scriptPath={scriptPath} module={module} file={file} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} /> : <div className="script-placeholder" role="status">{t('core.empty.selectScript')}</div>}</section>;
  // CONFIG 类型：如果有子文件路径，按需加载子文件内容
  if (isKind(file.kind, 'CONFIG') && scriptPath) return <ConfigChildSurface module={module} file={file} childPath={scriptPath} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} refreshKey={refreshKey} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} />;
  // CONFIG 类型 glob 文件无子文件选中时，显示提示
  if (isKind(file.kind, 'CONFIG') && file.children && file.children.length > 0 && file.nodes.length === 0) return <section className="config-surface"><div className="surface-head"><div><h2>{fileDisplayTitle(file)}</h2><p>{fileDisplayComment(file)}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><div className="script-placeholder" role="status">{t('core.empty.selectFile')}</div></section>;
  return <ConfigStructuredSurface module={module} file={file} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} refreshKey={refreshKey} onRefreshRegistry={onRefreshRegistry} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} />;
}

function ConfigStructuredSurface({ module, file, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, undoDraftScope, redoDraftScope, api, refreshKey, onRefreshRegistry, setSurfaceToolbar, setToast }: { module: WebRegistryModule; file: WebRegistryFile; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; refreshKey: number; onRefreshRegistry: () => Promise<WebRegistry | null>; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: Toast) => void }) {
  const scope = configDraftScope(module, file);
  const scopeHistory = draftHistory[draftScopeId(scope)] ?? emptyDraftHistory();
  const source = useConfigSourceDocument({ module, file, api, refreshKey, setToast });
  const [savingNodes, setSavingNodes] = useState(false);
  useEffect(() => {
    setOptimisticNodes([]);
    setDeletedObjectPaths(new Set());
  }, [file.id, refreshKey]);
  const [optimisticNodes, setOptimisticNodes] = useState<WebConfigNode[]>([]);
  const [deletedObjectPaths, setDeletedObjectPaths] = useState<Set<string>>(() => new Set());
  const moduleTitle = moduleDisplayName(module);
  const fileTitle = fileDisplayTitle(file);
  const fileComment = fileDisplayComment(file);
  const visibleNodes = useMemo(() => mergeConfigNodes(file.nodes, optimisticNodes, deletedObjectPaths), [file.nodes, optimisticNodes, deletedObjectPaths]);
  const optimisticPathSet = useMemo(() => new Set(optimisticNodes.map(node => node.path)), [optimisticNodes]);
  const changedNodes = file.nodes.filter(n => n.type !== 'object' && draftKey(scope, n.path) in drafts && !isDeletedPath(n.path, deletedObjectPaths));

  const updateSourceNodeValue = (node: WebConfigNode, nextValue: unknown) => {
    updateConfigSourceValue(source, node.path, nextValue, setToast);
    setOptimisticNodes(current => current.map(entry => entry.path === node.path ? { ...entry, value: nextValue } : entry));
  };
  const sourceEdit: SourceEditController = { paths: optimisticPathSet, update: updateSourceNodeValue };

  async function saveNodes() {
    if (!changedNodes.length) {
      setToast({ tone: 'ok', text: t('core.toast.noChanges') });
      return;
    }
    setSavingNodes(true);
    try {
      let nextRevision = file.revision;
      for (const node of changedNodes) {
        const result = await api.saveRegistryValue(module.id, scope.filePath, node.path, drafts[draftKey(scope, node.path)], nextRevision);
        nextRevision = result.revision ?? nextRevision;
      }
      clearDraftValues(scope);
      await onRefreshRegistry();
      await source.reload(false);
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: changedNodes.length }) });
    } catch (err) {
      setToast({ tone: 'bad', text: userFacingSaveError(err) });
    } finally {
      setSavingNodes(false);
    }
  }

  async function reloadStructured() {
    clearDraftScope(scope);
    setOptimisticNodes([]);
    setDeletedObjectPaths(new Set());
    await onRefreshRegistry();
    await source.reload(false);
    setToast({ tone: 'ok', text: t('core.toast.reloaded') });
  }

  useEffect(() => {
    setSurfaceToolbar({
      title: moduleTitle,
      subtitle: `${fileTitle}，${file.path}`,
      dirty: changedNodes.length > 0 || source.dirty,
      changedCount: source.dirty ? Math.max(changedNodes.length, 1) : changedNodes.length,
      changes: source.dirty ? [] : configChanges(scope, file.nodes, drafts),
      source: source.content,
      sourceOriginal: source.original,
      sourceEditable: true,
      sourceError: source.error,
      sourceLanguage: 'yaml',
      saving: source.saving || savingNodes,
      loading: source.loading,
      canUndo: scopeHistory.undo.length > 0,
      canRedo: scopeHistory.redo.length > 0,
      onUndo: () => undoDraftScope(scope),
      onRedo: () => redoDraftScope(scope),
      onReload: () => void reloadStructured(),
      onSourceChange: source.update,
      onSave: source.dirty ? () => void source.save(async () => { setOptimisticNodes([]); setDeletedObjectPaths(new Set()); await onRefreshRegistry(); }) : () => void saveNodes()
    });
    return () => setSurfaceToolbar(null);
  }, [moduleTitle, fileTitle, file.path, changedNodes.length, file.nodes, drafts, source.content, source.dirty, source.error, source.saving, source.loading, savingNodes, scopeHistory.undo.length, scopeHistory.redo.length]);

  const [createNode, setCreateNode] = useState<WebConfigNode | null>(null);
  const [deleteNode, setDeleteNode] = useState<WebConfigNode | null>(null);
  return <section className="config-surface"><div className="surface-head"><div><h2>{fileTitle}</h2><p>{fileComment}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><ConfigNodeTree scope={scope} nodes={visibleNodes} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={setCreateNode} onDeleteObject={setDeleteNode} sourceEdit={sourceEdit} deletedPaths={deletedObjectPaths} />{createNode && <ConfigCreateChildModal scope={scope} node={createNode} source={source} onCancel={() => setCreateNode(null)} onCreated={nodes => { setOptimisticNodes(current => mergeConfigNodes(current, nodes, new Set())); setCreateNode(null); }} setToast={setToast} />}{deleteNode && <ConfigDeleteObjectModal node={deleteNode} source={source} onCancel={() => setDeleteNode(null)} onDeleted={path => { setDeletedObjectPaths(current => new Set([...current, path])); setOptimisticNodes(current => current.filter(entry => !entry.path.startsWith(`${path}.`) && entry.path !== path)); setDeleteNode(null); }} setToast={setToast} />}</section>;
}

function ConfigChildSurface({ module, file, childPath, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, undoDraftScope, redoDraftScope, api, refreshKey, setSurfaceToolbar, setToast }: { module: WebRegistryModule; file: WebRegistryFile; childPath: string; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; refreshKey: number; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: Toast) => void }) {
  const [nodes, setNodes] = useState<WebConfigNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState<number | undefined>(undefined);
  const [saving, setSaving] = useState(false);
  const fileTitle = fileDisplayTitle(file);

  useEffect(() => {
    setLoading(true);
    setError('');
    setNodes([]);
    setRevision(undefined);
    api.registryFileNodes(module.id, childPath).then(result => {
      setNodes(applyConfigNodeOverrides(module.id, result.nodes));
      setRevision(result.revision);
    }).catch(err => {
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
    }).finally(() => setLoading(false));
  }, [module.id, childPath, refreshKey]);

  const scope = configDraftScope(module, file, childPath);
  const [optimisticNodes, setOptimisticNodes] = useState<WebConfigNode[]>([]);
  const [deletedObjectPaths, setDeletedObjectPaths] = useState<Set<string>>(() => new Set());
  const visibleNodes = useMemo(() => mergeConfigNodes(nodes, optimisticNodes, deletedObjectPaths), [nodes, optimisticNodes, deletedObjectPaths]);
  const optimisticPathSet = useMemo(() => new Set(optimisticNodes.map(node => node.path)), [optimisticNodes]);
  const changedNodes = nodes.filter(n => n.type !== 'object' && draftKey(scope, n.path) in drafts && !isDeletedPath(n.path, deletedObjectPaths));
  const scopeHistory = draftHistory[draftScopeId(scope)] ?? emptyDraftHistory();
  const source = useConfigSourceDocument({ module, file, childPath, api, refreshKey, setToast });

  useEffect(() => {
    setOptimisticNodes([]);
    setDeletedObjectPaths(new Set());
  }, [childPath, refreshKey]);

  const updateSourceNodeValue = (node: WebConfigNode, nextValue: unknown) => {
    updateConfigSourceValue(source, node.path, nextValue, setToast);
    setOptimisticNodes(current => current.map(entry => entry.path === node.path ? { ...entry, value: nextValue } : entry));
  };
  const sourceEdit: SourceEditController = { paths: optimisticPathSet, update: updateSourceNodeValue };

  async function reloadChildNodes(announce = true) {
    setNodes([]);
    setError('');
    setLoading(true);
    try {
      const refreshed = await api.registryFileNodes(module.id, childPath);
      setNodes(applyConfigNodeOverrides(module.id, refreshed.nodes));
      setRevision(refreshed.revision);
      clearDraftScope(scope);
      setOptimisticNodes([]);
      setDeletedObjectPaths(new Set());
      if (announce) setToast({ tone: 'ok', text: t('core.toast.reloaded') });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
    } finally {
      setLoading(false);
    }
  }

  async function reloadChildSurface() {
    clearDraftScope(scope);
    await Promise.all([reloadChildNodes(false), source.reload(false)]);
    setToast({ tone: 'ok', text: t('core.toast.reloaded') });
  }

  async function saveChild() {
    if (!changedNodes.length) {
      setToast({ tone: 'ok', text: t('core.toast.noChanges') });
      return;
    }
    setSaving(true);
    try {
      let nextRevision = revision;
      for (const node of changedNodes) {
        const result = await api.saveRegistryValue(module.id, childPath, node.path, drafts[draftKey(scope, node.path)], nextRevision);
        nextRevision = result.revision ?? nextRevision;
      }
      setRevision(nextRevision);
      const refreshed = await api.registryFileNodes(module.id, childPath);
      setNodes(applyConfigNodeOverrides(module.id, refreshed.nodes));
      setRevision(refreshed.revision);
      clearDraftValues(scope);
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: changedNodes.length }) });
    } catch (err) {
      setToast({ tone: 'bad', text: userFacingSaveError(err) });
    } finally {
      setSaving(false);
    }
  }

  const fileName = childPath.split('/').pop() ?? childPath;

  useEffect(() => {
    setSurfaceToolbar({
      title: fileName,
      subtitle: `${fileTitle} · ${childPath}`,
      dirty: changedNodes.length > 0 || source.dirty,
      changedCount: source.dirty ? Math.max(changedNodes.length, 1) : changedNodes.length,
      changes: source.dirty ? [] : configChanges(scope, nodes, drafts),
      source: source.content,
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
      onSave: source.dirty ? () => void source.save(async () => { clearDraftValues(scope); setOptimisticNodes([]); setDeletedObjectPaths(new Set()); await reloadChildNodes(false); }) : () => void saveChild()
    });
    return () => setSurfaceToolbar(null);
  }, [fileName, fileTitle, childPath, changedNodes.length, nodes, drafts, saving, loading, source.content, source.dirty, source.error, source.saving, source.loading, scopeHistory.undo.length, scopeHistory.redo.length, revision]);

  const [createNode, setCreateNode] = useState<WebConfigNode | null>(null);
  const [deleteNode, setDeleteNode] = useState<WebConfigNode | null>(null);
  return <section className="config-surface">
    {loading && <div className="script-loading" role="status">{t('core.state.loading')}</div>}
    {error && <InlineError><span>{error}</span><Button size="sm" onClick={() => void reloadChildNodes()}>{t('core.action.retry')}</Button></InlineError>}
    {!loading && !error && <ConfigNodeTree scope={scope} nodes={visibleNodes} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={setCreateNode} onDeleteObject={setDeleteNode} sourceEdit={sourceEdit} deletedPaths={deletedObjectPaths} />}
    {createNode && <ConfigCreateChildModal scope={scope} node={createNode} source={source} onCancel={() => setCreateNode(null)} onCreated={nodes => { setOptimisticNodes(current => mergeConfigNodes(current, nodes, new Set())); setCreateNode(null); }} setToast={setToast} />}
    {deleteNode && <ConfigDeleteObjectModal node={deleteNode} source={source} onCancel={() => setDeleteNode(null)} onDeleted={path => { setDeletedObjectPaths(current => new Set([...current, path])); setOptimisticNodes(current => current.filter(entry => !entry.path.startsWith(`${path}.`) && entry.path !== path)); setDeleteNode(null); }} setToast={setToast} />}
  </section>;
}

function useConfigSourceDocument({ module, file, childPath, api, refreshKey, setToast }: { module: WebRegistryModule; file: WebRegistryFile; childPath?: string; api: ApiClient; refreshKey: number; setToast: (toast: Toast) => void }) {
  const [content, setContent] = useState('');
  const [original, setOriginal] = useState('');
  const [revision, setRevision] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const editor = file.editorId ? { id: file.editorId } : undefined;
  const adapter = getSourceDocumentAdapter(file, editor);
  const context = useMemo(() => ({ module, file, childPath, editor }), [module, file, childPath, editor?.id]);

  async function reload(announce = true) {
    if (!adapter) return;
    setLoading(true);
    setError(null);
    try {
      const doc = await adapter.read(api, context);
      setContent(doc.content);
      setOriginal(doc.content);
      setRevision(doc.revision);
      if (announce) setToast({ tone: 'ok', text: t('core.toast.reloaded') });
    } catch (err) {
      const message = err instanceof Error ? err.message : t('core.toast.refreshFailed');
      setError(message);
      setToast({ tone: 'bad', text: message });
    } finally {
      setLoading(false);
    }
  }

  async function save(afterSave?: () => void | Promise<void>) {
    if (!adapter || error) return;
    setSaving(true);
    try {
      const result = await adapter.save(api, context, content, revision);
      setOriginal(content);
      setRevision(result.revision ?? revision);
      await afterSave?.();
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: 1 }) });
    } catch (err) {
      const message = userFacingSaveError(err);
      setError(message);
      setToast({ tone: 'bad', text: message });
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => {
    void reload(false);
  }, [api, module.id, file.id, childPath, refreshKey]);

  return {
    content,
    original,
    dirty: content !== original,
    loading,
    saving,
    error,
    update: (next: string) => {
      setContent(next);
      setError(null);
    },
    reload,
    save
  };
}

function ConfigCreateChildModal({ scope, node, source, onCancel, onCreated, setToast }: { scope: ConfigDraftScope; node: WebConfigNode; source: ConfigSourceDocument; onCancel: () => void; onCreated: (nodes: WebConfigNode[]) => void; setToast: (toast: Toast) => void }) {
  const dialogRef = useRef<HTMLFormElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  const templates = node.createTemplates?.length ? node.createTemplates : [emptyCreateTemplate(node)];
  const [templateId, setTemplateId] = useState(templates[0]?.id ?? 'empty');
  const template = templates.find(entry => entry.id === templateId) ?? templates[0];
  const [keyName, setKeyName] = useState('');
  const [values, setValues] = useState<Record<string, unknown>>(() => defaultTemplateValues(template));

  useEffect(() => setValues(defaultTemplateValues(template)), [template?.id]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const key = keyName.trim().replace(/\s+/g, '_');
    if (!key) return;
    createConfigChild(node, source, key, values, template, setToast, onCreated);
  }

  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <form ref={dialogRef} className="config-create-dialog" role="dialog" aria-modal="true" aria-labelledby="config-create-title" onSubmit={submit}>
      <div className="reload-confirm-head"><span>{t('core.config.createKicker')}</span><h3 id="config-create-title">{t('core.config.createTitle')}</h3></div>
      <div className="reload-confirm-body config-create-body">
        <p>{t('core.config.createDesc', { path: node.path })}</p>
        <label className="file-confirm-field"><span>{t('core.config.createKey')}</span><input autoFocus value={keyName} onChange={event => setKeyName(event.target.value)} placeholder={nextConfigChildKey(getConfigObject(parseSafeYaml(source.content), node.path.split('.')))} /></label>
        {templates.length > 1 && <label className="file-confirm-field"><span>{t('core.config.createTemplate')}</span><select value={template.id} onChange={event => setTemplateId(event.target.value)}>{templates.map(entry => <option key={entry.id} value={entry.id}>{entry.label}</option>)}</select></label>}
        {template.fields.length === 0 && <p className="config-create-hint">{t('core.config.createNoTemplate')}</p>}
        <div className="config-create-fields">{template.fields.map(field => <div className="object-list-field" key={field.path}><label>{fieldLabel(field.path, { moduleId: scope.moduleId, namespace: scope.moduleId, fallback: getLocale().startsWith('zh') ? (field.label || field.path) : humanizeFieldLabel(field.path) })}</label>{renderSchemaField(field, values[field.path], next => setValues(current => ({ ...current, [field.path]: next })), scope.moduleId, field.path)}</div>)}</div>
      </div>
      <ActionGroup className="reload-confirm-actions"><Button type="button" onClick={onCancel}>{t('core.gui.cancel')}</Button><Button type="submit" variant="primary" disabled={!keyName.trim() || source.loading || !!source.error}>{t('core.config.create')}</Button></ActionGroup>
    </form>
  </div>;
}

function createConfigChild(node: WebConfigNode, source: ConfigSourceDocument, key: string, values: Record<string, unknown>, template: WebConfigCreateTemplate, setToast: (toast: Toast) => void, onCreated?: (nodes: WebConfigNode[]) => void) {
  if (!node.creatableChildren) return;
  if (source.loading) {
    setToast({ tone: 'bad', text: t('core.config.sourceLoading') });
    return;
  }
  if (source.error) {
    setToast({ tone: 'bad', text: source.error });
    return;
  }
  try {
    const data = parseYaml(source.content || '{}');
    const parent = getConfigObject(data, node.path.split('.'));
    if (key in parent) {
      setToast({ tone: 'bad', text: t('core.config.createDuplicate', { key }) });
      return;
    }
    const childValue = templateValuesToObject(values);
    const nextParent = { ...parent, [key]: childValue };
    const nextData = setDeepValue(data, node.path.split('.'), nextParent);
    source.update(serializeYaml(nextData));
    setToast({ tone: 'ok', text: t('core.config.createdSourceField', { key }) });
    onCreated?.(createOptimisticConfigNodes(node, key, childValue, template));
  } catch (err) {
    source.update(`${source.content.replace(/\s*$/, '')}\n# ${err instanceof Error ? err.message : t('core.toast.refreshFailed')}\n`);
    setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
  }
}

function ConfigDeleteObjectModal({ node, source, onCancel, onDeleted, setToast }: { node: WebConfigNode; source: ConfigSourceDocument; onCancel: () => void; onDeleted: (path: string) => void; setToast: (toast: Toast) => void }) {
  const dialogRef = useRef<HTMLFormElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  const [confirmPath, setConfirmPath] = useState('');
  function submit(event: FormEvent) {
    event.preventDefault();
    if (confirmPath !== node.path) return;
    deleteConfigObject(node, source, setToast, onDeleted);
  }
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <form ref={dialogRef} className="file-action-dialog danger config-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="config-delete-title" onSubmit={submit}>
      <div className="reload-confirm-head"><span>{t('core.config.deleteObjectKicker')}</span><h3 id="config-delete-title">{t('core.config.deleteObjectTitle')}</h3></div>
      <div className="reload-confirm-body"><p>{t('core.config.deleteObjectDesc', { path: node.path })}</p><code className="file-delete-path">{node.path}</code><label className="file-confirm-field"><span>{t('core.config.deleteObjectConfirmLabel')}</span><input autoFocus value={confirmPath} onChange={event => setConfirmPath(event.target.value)} placeholder={node.path} /></label></div>
      <ActionGroup className="reload-confirm-actions"><Button type="button" onClick={onCancel}>{t('core.gui.cancel')}</Button><Button type="submit" variant="danger" disabled={confirmPath !== node.path || source.loading || !!source.error}>{t('core.config.deleteObjectConfirm')}</Button></ActionGroup>
    </form>
  </div>;
}

function deleteConfigObject(node: WebConfigNode, source: ConfigSourceDocument, setToast: (toast: Toast) => void, onDeleted: (path: string) => void) {
  if (source.loading) {
    setToast({ tone: 'bad', text: t('core.config.sourceLoading') });
    return;
  }
  if (source.error) {
    setToast({ tone: 'bad', text: source.error });
    return;
  }
  try {
    const data = parseYaml(source.content || '{}');
    const nextData = deleteDeepValue(data, node.path.split('.'));
    source.update(serializeYaml(nextData));
    setToast({ tone: 'ok', text: t('core.config.deletedSourceObject', { path: node.path }) });
    onDeleted(node.path);
  } catch (err) {
    setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
  }
}

function updateConfigSourceValue(source: ConfigSourceDocument, path: string, nextValue: unknown, setToast: (toast: Toast) => void) {
  if (source.loading) {
    setToast({ tone: 'bad', text: t('core.config.sourceLoading') });
    return;
  }
  if (source.error) {
    setToast({ tone: 'bad', text: source.error });
    return;
  }
  try {
    const data = parseYaml(source.content || '{}');
    const nextData = setDeepValue(data, path.split('.'), nextValue);
    source.update(serializeYaml(nextData));
  } catch (err) {
    setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
  }
}

function templateValuesToObject(values: Record<string, unknown>): Record<string, unknown> {
  return Object.entries(values).reduce<Record<string, unknown>>((result, [path, value]) => setDeepValue(result, path.split('.'), value), {});
}

function createOptimisticConfigNodes(parent: WebConfigNode, key: string, value: Record<string, unknown>, template: WebConfigCreateTemplate): WebConfigNode[] {
  const rootPath = `${parent.path}.${key}`;
  const root: WebConfigNode = {
    path: rootPath,
    label: key,
    comment: template.label || key,
    type: 'object',
    editable: true,
    value,
    creatableChildren: false,
    createTemplates: [],
  };
  const fields = template.fields.length ? template.fields : Object.keys(value).map(fieldKey => ({ path: fieldKey, label: fieldKey, type: inferConfigFieldType(value[fieldKey]) } as WebConfigFieldSchema));
  return [root, ...fields.map(field => optimisticFieldNode(rootPath, field, getDeepConfigValue(value, field.path.split('.'))))];
}

function optimisticFieldNode(rootPath: string, field: WebConfigFieldSchema, value: unknown): WebConfigNode {
  return {
    path: `${rootPath}.${field.path}`,
    label: field.label || field.path,
    comment: field.comment || '',
    type: field.type || inferConfigFieldType(value),
    editable: true,
    value,
    options: field.options,
    optionLabelPrefix: field.optionLabelPrefix,
  };
}

function inferConfigFieldType(value: unknown): string {
  if (typeof value === 'boolean') return 'boolean';
  if (typeof value === 'number') return 'number';
  if (Array.isArray(value)) return 'list';
  if (value && typeof value === 'object') return 'object';
  return 'text';
}

function getDeepConfigValue(source: Record<string, unknown>, path: string[]): unknown {
  return path.reduce<unknown>((current, part) => current && typeof current === 'object' && !Array.isArray(current) ? (current as Record<string, unknown>)[part] : undefined, source);
}

function deleteDeepValue(source: Record<string, unknown>, path: string[]): Record<string, unknown> {
  if (!path.length) return source;
  const [head, ...tail] = path;
  if (!tail.length) {
    const next = { ...source };
    delete next[head];
    return next;
  }
  return setDeepValue(source, [head], deleteDeepValue(getConfigObject(source, [head]), tail));
}

function mergeConfigNodes(baseNodes: WebConfigNode[], optimisticNodes: WebConfigNode[], deletedPaths: Set<string>): WebConfigNode[] {
  const byPath = new Map<string, WebConfigNode>();
  for (const node of baseNodes) if (!isDeletedPath(node.path, deletedPaths)) byPath.set(node.path, node);
  for (const node of optimisticNodes) if (!isDeletedPath(node.path, deletedPaths)) byPath.set(node.path, node);
  return Array.from(byPath.values()).sort((left, right) => compareConfigPath(left.path, right.path));
}

function isDeletedPath(path: string, deletedPaths: Set<string>): boolean {
  for (const deletedPath of deletedPaths) if (path === deletedPath || path.startsWith(`${deletedPath}.`)) return true;
  return false;
}

function compareConfigPath(left: string, right: string): number {
  const leftParts = left.split('.');
  const rightParts = right.split('.');
  for (let index = 0; index < Math.min(leftParts.length, rightParts.length); index++) {
    if (leftParts[index] === rightParts[index]) continue;
    return leftParts[index].localeCompare(rightParts[index], undefined, { numeric: true });
  }
  return leftParts.length - rightParts.length;
}

function emptyCreateTemplate(node: WebConfigNode): WebConfigCreateTemplate {
  return { id: 'empty', label: t('core.config.createEmptyTemplate'), fields: node.type === 'dynamic_map' ? [] : [] };
}

function defaultTemplateValues(template: WebConfigCreateTemplate): Record<string, unknown> {
  return Object.fromEntries(template.fields.map(field => [field.path, field.defaultValue ?? defaultSchemaFieldValue(field)]));
}

function defaultSchemaFieldValue(field: WebConfigFieldSchema): unknown {
  if (field.type === 'number') return 0;
  if (field.type === 'boolean') return false;
  if (field.type === 'list' || field.type === 'stringList') return [];
  if (field.type === 'enum') return field.options?.[0] ?? '';
  return '';
}

function parseSafeYaml(content: string): Record<string, unknown> {
  try { return parseYaml(content || '{}'); } catch { return {}; }
}

function getConfigObject(data: Record<string, unknown>, path: string[]): Record<string, unknown> {
  let current: unknown = data;
  for (const part of path) {
    current = current && typeof current === 'object' && !Array.isArray(current) ? (current as Record<string, unknown>)[part] : undefined;
  }
  return current && typeof current === 'object' && !Array.isArray(current) ? current as Record<string, unknown> : {};
}

function nextConfigChildKey(parent: Record<string, unknown>): string {
  const used = new Set(Object.keys(parent));
  if (!used.has('new_field')) return 'new_field';
  let index = 1;
  while (used.has(`new_field_${index}`)) index += 1;
  return `new_field_${index}`;
}

const INTERNAL_ERROR_PATTERNS = ['.tmp', 'FileSystemException', 'AccessDeniedException', 'AtomicMoveNotSupportedException', 'NoSuchFileException', 'DirectoryNotEmptyException'];

function userFacingSaveError(err: unknown): string {
  const raw = err instanceof Error ? err.message : String(err ?? '');
  if (INTERNAL_ERROR_PATTERNS.some(pattern => raw.includes(pattern))) {
    return t('core.toast.saveFailed');
  }
  return raw || t('core.toast.saveFailed');
}

function configChanges(scope: ConfigDraftScope, nodes: WebConfigNode[], drafts: DraftMap): EditorChange[] {
  return nodes
    .filter(node => node.type !== 'object' && draftKey(scope, node.path) in drafts)
    .map(node => ({ path: node.path, label: configNodeDisplayLabel(scope, node), before: node.value, after: drafts[draftKey(scope, node.path)] }));
}

function configSourcePreview(nodes: WebConfigNode[], scope: ConfigDraftScope, drafts: DraftMap): string {
  return nodes
    .filter(node => node.type !== 'object')
    .map(node => `${node.path}: ${formatPreviewValue(draftKey(scope, node.path) in drafts ? drafts[draftKey(scope, node.path)] : node.value)}`)
    .join('\n');
}

function formatPreviewValue(value: unknown): string {
  if (typeof value === 'string') return JSON.stringify(value);
  try { return JSON.stringify(value); } catch { return String(value); }
}

function configNodeDisplayLabel(scope: ConfigDraftScope, node: WebConfigNode): string {
  return fieldLabel(node.path, { moduleId: scope.moduleId, namespace: scope.moduleId, fallback: getLocale().startsWith('zh') ? node.label : humanizeFieldLabel(node.path) });
}

function configNodeDisplayComment(scope: ConfigDraftScope, node: WebConfigNode): string {
  return resolveConfigNodeComment(scope.moduleId, node.path, node.comment);
}

function ConfigNodeTree({ scope, nodes, drafts, setDraftValue, onCreateChild, onDeleteObject, sourceEdit, deletedPaths }: { scope: ConfigDraftScope; nodes: WebConfigNode[]; drafts: DraftMap; setDraftValue: DraftValueSetter; onCreateChild: (node: WebConfigNode) => void; onDeleteObject: (node: WebConfigNode) => void; sourceEdit?: SourceEditController; deletedPaths?: Set<string> }) {
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const toggle = (path: string) => setCollapsed(c => ({ ...c, [path]: !c[path] }));

  // 构建树结构：顶级节点是 path 中不含 "." 的节点，或者 object 节点作为分组
  const groups = buildNodeGroups(nodes);

  if (!nodes.length) return <div className="script-placeholder" role="status">{t('core.empty.noConfigNodes')}</div>;

  return <div className="node-grid">{groups.map(group => {
    if (group.type === 'leaf') {
      return <ConfigNodeView key={group.node.path} scope={scope} node={group.node} drafts={drafts} setDraftValue={setDraftValue} sourceEdit={sourceEdit} />;
    }
    return <ConfigNodeSection key={group.node.path} scope={scope} node={group.node} childrenNodes={group.children} drafts={drafts} setDraftValue={setDraftValue} collapsed={collapsed} toggle={toggle} onCreateChild={onCreateChild} onDeleteObject={onDeleteObject} sourceEdit={sourceEdit} deletedPaths={deletedPaths} deletable={false} />;
  })}</div>;
}

type NodeGroup = { type: 'section'; node: WebConfigNode; children: WebConfigNode[] } | { type: 'leaf'; node: WebConfigNode };

function buildNodeGroups(nodes: WebConfigNode[], parentPath = ''): NodeGroup[] {
  const groups: NodeGroup[] = [];
  const prefix = parentPath ? `${parentPath}.` : '';
  for (const node of nodes) {
    if (!isDirectChildPath(node.path, parentPath)) continue;
    if (node.type === 'object') {
      const childPrefix = `${node.path}.`;
      groups.push({ type: 'section', node, children: nodes.filter(child => child.path.startsWith(childPrefix)) });
    } else {
      groups.push({ type: 'leaf', node });
    }
  }
  return groups.filter(group => group.type === 'leaf' || group.children.length > 0 || !prefix);
}

function isDirectChildPath(path: string, parentPath: string): boolean {
  if (!parentPath) return !path.includes('.');
  if (!path.startsWith(`${parentPath}.`)) return false;
  return !path.slice(parentPath.length + 1).includes('.');
}

function ConfigNodeSection({ scope, node, childrenNodes, drafts, setDraftValue, collapsed, toggle, onCreateChild, onDeleteObject, sourceEdit, deletedPaths, deletable }: { scope: ConfigDraftScope; node: WebConfigNode; childrenNodes: WebConfigNode[]; drafts: DraftMap; setDraftValue: DraftValueSetter; collapsed: Record<string, boolean>; toggle: (path: string) => void; onCreateChild: (node: WebConfigNode) => void; onDeleteObject: (node: WebConfigNode) => void; sourceEdit?: SourceEditController; deletedPaths?: Set<string>; deletable: boolean }) {
  const isCollapsed = collapsed[node.path] === true;
  const groups = buildNodeGroups(childrenNodes, node.path);
  const sectionChanged = draftKey(scope, node.path) in drafts || sourceEdit?.paths.has(node.path) === true || deletedPaths?.has(node.path) === true;
  const changedInGroup = childrenNodes.filter(n => n.type !== 'object' && (draftKey(scope, n.path) in drafts || sourceEdit?.paths.has(n.path))).length;
  const groupLabel = configNodeDisplayLabel(scope, node);
  return <div className="node-section">
    <div className={`node-section-header ${isCollapsed ? 'collapsed' : ''} ${sectionChanged ? 'changed' : ''}`}>
      <button type="button" className="node-section-toggle" onClick={() => toggle(node.path)} aria-expanded={!isCollapsed}>
        <span className="section-arrow" aria-hidden="true">{isCollapsed ? '›' : '⌄'}</span>
        <strong>{groupLabel}</strong>
        <code>{node.path}</code>
        <span className="section-comment">{configNodeDisplayComment(scope, node)}</span>
      </button>
      <div className="node-section-actions">
        {node.creatableChildren && <button type="button" className="node-section-create" onClick={() => onCreateChild(node)}>+ {t('core.config.create')}</button>}
        {deletable && <button type="button" className="node-section-delete" onClick={() => onDeleteObject(node)}>{t('core.config.deleteObject')}</button>}
      </div>
      <span className="section-meta">{(sectionChanged || changedInGroup > 0) && <span className="section-badge">{Math.max(changedInGroup, sectionChanged ? 1 : 0)}</span>}{t('core.config.groupItems', { count: groups.length })}</span>
    </div>
    {!isCollapsed && <div className="node-section-body">{groups.map(group => group.type === 'section'
      ? <ConfigNodeSection key={group.node.path} scope={scope} node={group.node} childrenNodes={group.children} drafts={drafts} setDraftValue={setDraftValue} collapsed={collapsed} toggle={toggle} onCreateChild={onCreateChild} onDeleteObject={onDeleteObject} sourceEdit={sourceEdit} deletedPaths={deletedPaths} deletable={node.creatableChildren === true} />
      : <ConfigNodeView key={group.node.path} scope={scope} node={group.node} drafts={drafts} setDraftValue={setDraftValue} sourceEdit={sourceEdit} />
    )}</div>}
  </div>;
}

function ConfigNodeView({ scope, node, drafts, setDraftValue, sourceEdit }: { scope: ConfigDraftScope; node: WebConfigNode; drafts: DraftMap; setDraftValue: DraftValueSetter; sourceEdit?: SourceEditController }) {
  const key = draftKey(scope, node.path);
  const sourceEdited = sourceEdit?.paths.has(node.path) === true;
  const value = key in drafts ? drafts[key] : node.value;
  const setValue = (next: unknown) => sourceEdited ? sourceEdit?.update(node, next) : setDraftValue(scope, node, next);
  const isWide = node.type === 'dynamic_map' || node.type === 'list';
  const label = configNodeDisplayLabel(scope, node);
  return <div className={`node ${key in drafts || sourceEdited ? 'changed' : ''} ${isWide ? 'node-wide' : ''}`}><div className="node-meta"><strong>{label}</strong><code>{node.path}</code><p>{configNodeDisplayComment(scope, node)}</p></div><div className="node-control">{renderControl(node, value, setValue, label, scope.moduleId)}</div></div>;
}

function renderControl(node: WebConfigNode, value: unknown, setValue: (v: unknown) => void, label: string, moduleId: string) {
  if (node.type === 'boolean') return <button type="button" className={`switch ${value ? 'on' : ''}`} aria-pressed={value === true} aria-label={`${label}: ${value ? t('core.config.booleanOn') : t('core.config.booleanOff')}`} onClick={() => setValue(!value)}><span />{value ? t('core.config.booleanOn') : t('core.config.booleanOff')}</button>;
  if (node.type === 'enum' && node.options) return <select value={str(value)} aria-label={label} onChange={(e) => setValue(e.target.value)}>{node.options.map(opt => <option key={opt} value={opt}>{optionLabel(node.optionLabelPrefix || node.path, opt, { moduleId })}</option>)}</select>;
  if (node.type === 'number') return <input type="number" aria-label={label} value={value == null ? '' : String(value)} onChange={(e) => setValue(e.target.value === '' ? undefined : Number(e.target.value))} />;
  if (node.type === 'dynamic_map') return <DynamicMapEditor value={value} setValue={setValue} />;
  if (node.type === 'list') {
    const items = Array.isArray(value) ? value : [];
    const hasObjectItems = node.path === 'allowed_damage_causes' || items.some(isPlainObject);
    if (hasObjectItems) return <ObjectListEditor node={node} items={items} setValue={setValue} moduleId={moduleId} />;
    const update = (i: number, v: string) => setValue(items.map((x, j) => j === i ? parseListValue(x, v) : x));
    return <div className="list-editor">{items.map((item, i) => <div className="list-row" key={i}><input value={str(item)} onChange={(e) => update(i, e.target.value)} aria-label={t('core.config.itemIndex', { index: i + 1 })} /><button type="button" onClick={() => setValue(items.filter((_, j) => j !== i))} aria-label={t('core.config.deleteItem', { index: i + 1 })}>{t('core.config.delete')}</button></div>)}<button type="button" className="add-row" onClick={() => setValue([...items, ''])}>{t('core.config.addItem')}</button></div>;
  }
  return <input aria-label={label} value={str(value)} onChange={(e) => setValue(e.target.value)} />;
}

function ObjectListEditor({ node, items, setValue, moduleId }: { node: WebConfigNode; items: unknown[]; setValue: (v: unknown) => void; moduleId: string }) {
  const objectItems: Record<string, unknown>[] = items.map(item => isPlainObject(item) ? item : {});
  const keys = objectListKeys(node, objectItems);

  function updateField(index: number, key: string, nextValue: unknown) {
    setValue(items.map((item, itemIndex) => itemIndex === index ? { ...(isPlainObject(item) ? item : {}), [key]: nextValue } : item));
  }

  function addEntry() {
    setValue([...items, objectListTemplate({ ...node, value: items }, objectItems[0])]);
  }

  return <div className="object-list-editor">
    {objectItems.map((item, index) => <div className="object-list-entry" key={index}>
      <div className="object-list-head">
        <strong>#{index + 1}</strong>
        <code>{objectListSummary(item, index)}</code>
        <button type="button" onClick={() => setValue(items.filter((_, itemIndex) => itemIndex !== index))} aria-label={t('core.config.deleteItem', { index: index + 1 })}>{t('core.config.delete')}</button>
      </div>
      <div className="object-list-fields">
        {keys.map(key => <div className="object-list-field" key={key}>
          <label>{fieldLabel(`${node.path}.${key}`, { moduleId, namespace: moduleId, fallback: getLocale().startsWith('zh') ? (fieldSchemaForKey(node, key)?.label || key.replace(/_/g, ' ')) : humanizeFieldLabel(key) })}</label>
          {renderSchemaField(fieldSchemaForKey(node, key), item[key], next => updateField(index, key, next), moduleId, `${node.path}.${index}.${key}`, objectItems, index)}
        </div>)}
      </div>
    </div>)}
    <button type="button" className="add-row" onClick={addEntry}>{t('core.config.addItem')}</button>
  </div>;
}

function renderSchemaField(field: WebConfigFieldSchema | undefined, value: unknown, onChange: (value: unknown) => void, moduleId: string, ariaLabel: string, siblingItems: Record<string, unknown>[] = [], currentIndex = -1) {
  const type = field?.type;
  if (type === 'boolean' || typeof value === 'boolean') return <button type="button" className={`switch ${value ? 'on' : ''}`} aria-pressed={value === true} aria-label={ariaLabel} onClick={() => onChange(!value)}><span />{value ? t('core.config.booleanOn') : t('core.config.booleanOff')}</button>;
  if (type === 'number' || typeof value === 'number') return <input type="number" aria-label={ariaLabel} value={Number.isFinite(value as number) ? String(value) : ''} onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))} />;
  if (type === 'enum' && field?.options) {
    const used = new Set(siblingItems.map((item, index) => index === currentIndex ? '' : String(item[field.path] ?? '')).filter(Boolean));
    const current = str(value);
    const options = field.options.filter(option => option === current || !used.has(option));
    return <select aria-label={ariaLabel} value={current} onChange={(e) => onChange(e.target.value)}>{options.map(option => <option key={option} value={option}>{optionLabel(field.optionLabelPrefix || field.path, option, { moduleId })}</option>)}</select>;
  }
  return <input aria-label={ariaLabel} value={value == null ? '' : String(value)} onChange={(e) => onChange(e.target.value)} />;
}

function objectListKeys(node: WebConfigNode, items: Record<string, unknown>[]) {
  const keys = Array.from(new Set(items.flatMap(item => Object.keys(item))));
  const schemaKeys = node.itemFields?.map(field => field.path) ?? [];
  return schemaKeys.length ? mergeKeys(schemaKeys, keys) : (keys.length ? keys : ['key']);
}

function fieldSchemaForKey(node: WebConfigNode, key: string): WebConfigFieldSchema | undefined {
  return node.itemFields?.find(field => field.path === key);
}

function objectListTemplate(node: WebConfigNode, sample: Record<string, unknown> | undefined) {
  if (node.itemFields?.length) return Object.fromEntries(node.itemFields.map(field => [field.path, defaultListFieldValue(node, field, sample)]));
  const keys = objectListKeys(node, sample ? [sample] : []);
  return Object.fromEntries(keys.map(key => [key, defaultObjectListValue(sample?.[key])]));
}

function defaultListFieldValue(node: WebConfigNode, field: WebConfigFieldSchema, sample: Record<string, unknown> | undefined) {
  if (field.type === 'enum' && field.options?.length) {
    const used = new Set((Array.isArray(node.value) ? node.value : []).map(item => isPlainObject(item) ? String(item[field.path] ?? '') : '').filter(Boolean));
    return field.options.find(option => !used.has(option)) ?? field.options[0] ?? '';
  }
  return field.defaultValue ?? defaultObjectListValue(sample?.[field.path]);
}

function defaultObjectListValue(sample: unknown) {
  if (typeof sample === 'number') return 0;
  if (typeof sample === 'boolean') return false;
  return '';
}

function objectListSummary(item: Record<string, unknown>, index: number) {
  const primary = item.cause ?? item.id ?? item.key ?? item.name ?? item.type;
  return primary == null || primary === '' ? t('core.config.itemIndex', { index: index + 1 }) : String(primary);
}

function mergeKeys(preferred: string[], keys: string[]) {
  return [...preferred, ...keys.filter(key => !preferred.includes(key))];
}

function DynamicMapEditor({ value, setValue }: { value: unknown; setValue: (v: unknown) => void }) {
  const [newKey, setNewKey] = useState('');
  const map: Record<string, string[]> = (isObjectLike(value) ? value : {}) as Record<string, string[]>;
  const keys = Object.keys(map);

  function addKey() {
    const trimmed = newKey.trim().replace(/\s+/g, '_').toLowerCase();
    if (!trimmed || trimmed in map) return;
    setValue({ ...map, [trimmed]: [] });
    setNewKey('');
  }

  function removeKey(k: string) {
    const copy = { ...map };
    delete copy[k];
    setValue(copy);
  }

  function updateList(k: string, items: string[]) {
    setValue({ ...map, [k]: items });
  }

  function addItem(k: string) {
    updateList(k, [...(map[k] || []), '']);
  }

  function updateItem(k: string, i: number, v: string) {
    updateList(k, (map[k] || []).map((x, j) => j === i ? v : x));
  }

  function removeItem(k: string, i: number) {
    updateList(k, (map[k] || []).filter((_, j) => j !== i));
  }

  return <div className="dynamic-map-editor">
    {keys.map(k => <div key={k} className="dmap-entry">
      <div className="dmap-header">
        <code>{k}</code>
        <button type="button" className="dmap-remove" onClick={() => removeKey(k)} aria-label={t('core.config.removeGroup', { group: k })}>{t('core.config.remove')}</button>
      </div>
      <div className="dmap-list">
        {(Array.isArray(map[k]) ? map[k] : []).map((item, i) => <div key={i} className="dmap-row">
          <input value={String(item)} onChange={(e) => updateItem(k, i, e.target.value)} aria-label={`${k} ${t('core.config.itemIndex', { index: i + 1 })}`} />
          <button type="button" onClick={() => removeItem(k, i)} aria-label={t('core.config.deleteItemInGroup', { group: k, index: i + 1 })}>{t('core.config.delete')}</button>
        </div>)}
        <button type="button" className="add-row" onClick={() => addItem(k)}>{t('core.config.addActionRow')}</button>
      </div>
    </div>)}
    <div className="dmap-add">
      <input value={newKey} onChange={(e) => setNewKey(e.target.value)} placeholder={t('core.config.newTemplateName')} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addKey(); } }} />
      <button type="button" onClick={addKey} disabled={!newKey.trim()}>{t('core.config.addTemplate')}</button>
    </div>
  </div>;
}

function ScriptEditor({ api, scriptPath, module, file, setSurfaceToolbar, setToast }: { api: ApiClient; scriptPath: string; module: WebRegistryModule; file: WebRegistryFile; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: Toast) => void }) {
  const [content, setContent] = useState('');
  const [savedContent, setSavedContent] = useState('');
  const [history, setHistory] = useState<{ undo: string[]; redo: string[] }>({ undo: [], redo: [] });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const fileName = scriptPath.split('/').pop() ?? scriptPath;
  const fileTitle = fileDisplayTitle(file);
  const isDirty = content !== savedContent;
  const sourceEditingElement = (target: EventTarget | null) => target instanceof HTMLElement && ['INPUT', 'TEXTAREA'].includes(target.tagName);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.readScript(scriptPath).then(result => {
      if (!active) return;
      setContent(result.content);
      setSavedContent(result.content);
      setHistory({ undo: [], redo: [] });
    }).catch(err => {
      if (!active) return;
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [api, scriptPath]);

  function updateScriptContent(next: string) {
    setContent(previous => {
      setHistory(current => ({ undo: [...current.undo, previous].slice(-20), redo: [] }));
      return next;
    });
  }

  async function save() {
    if (!isDirty) {
      setToast({ tone: 'ok', text: t('core.toast.noChanges') });
      return;
    }
    setSaving(true);
    try {
      await api.saveScript(scriptPath, content);
      setSavedContent(content);
      setHistory({ undo: [], redo: [] });
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: 1 }) });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.file.createFailed'));
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.file.createFailed') });
    } finally {
      setSaving(false);
    }
  }

  function undoScript() {
    const snapshot = history.undo[history.undo.length - 1];
    if (snapshot === undefined) return;
    setContent(current => {
      setHistory(previous => ({ undo: previous.undo.slice(0, -1), redo: [current, ...previous.redo].slice(0, 20) }));
      return snapshot;
    });
    setError('');
  }

  function redoScript() {
    const snapshot = history.redo[0];
    if (snapshot === undefined) return;
    setContent(current => {
      setHistory(previous => ({ undo: [...previous.undo, current].slice(-20), redo: previous.redo.slice(1) }));
      return snapshot;
    });
    setError('');
  }

  async function reload() {
    setLoading(true);
    setError('');
    try {
      const res = await api.readScript(scriptPath);
      setContent(res.content);
      setSavedContent(res.content);
      setHistory({ undo: [], redo: [] });
      setToast({ tone: 'ok', text: t('core.toast.reloaded') });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    setSurfaceToolbar({
      title: fileName,
      subtitle: `${fileTitle} · ${scriptPath}`,
      dirty: isDirty,
      changedCount: isDirty ? 1 : 0,
      changes: [],
      source: content,
      sourceOriginal: savedContent,
      sourceEditable: true,
      sourceError: error || null,
      sourceLanguage: 'javascript',
      saving,
      loading,
      canUndo: history.undo.length > 0,
      canRedo: history.redo.length > 0,
      onUndo: undoScript,
      onRedo: redoScript,
      onReload: () => void reload(),
      onSourceChange: updateScriptContent,
      onSave: () => void save()
    });
    return () => setSurfaceToolbar(null);
  }, [fileName, fileTitle, scriptPath, isDirty, content, error, saving, loading, history.undo.length, history.redo.length]);

  function handleInput(value: string) {
    updateScriptContent(value);
  }

  if (loading) return <div className="script-loading" role="status">{t('core.script.loading')}</div>;

  return <div className="script-editor">
    {error && <InlineError>{error}</InlineError>}
    <div className="editor-wrapper">
      <CodeEditor
        className="script-code-editor"
        value={content}
        language="javascript"
        ariaLabel={t('core.script.editAria', { path: scriptPath })}
        completionSource={scriptCompletionSource}
        onChange={handleInput}
        onSave={save}
      />
      <span id="script-editor-help" className="sr-only">{t('core.script.help')}</span>
    </div>
  </div>;
}

type ScriptCompletionScope = Record<string, Completion[]>;

const SCRIPT_COMPLETION_SCOPES: ScriptCompletionScope = {
  global: [
    keywordCompletion('function'), keywordCompletion('const'), keywordCompletion('let'), keywordCompletion('var'), keywordCompletion('if'), keywordCompletion('else'), keywordCompletion('for'), keywordCompletion('while'), keywordCompletion('do'), keywordCompletion('switch'), keywordCompletion('case'), keywordCompletion('break'), keywordCompletion('continue'), keywordCompletion('return'), keywordCompletion('try'), keywordCompletion('catch'), keywordCompletion('finally'), keywordCompletion('throw'), keywordCompletion('new'), keywordCompletion('typeof'), keywordCompletion('instanceof'), keywordCompletion('class'), keywordCompletion('extends'), keywordCompletion('async'), keywordCompletion('await'), keywordCompletion('true'), keywordCompletion('false'), keywordCompletion('null'), keywordCompletion('undefined'), keywordCompletion('this'),
    variableCompletion('emaki', 'EmakiScriptApi'), variableCompletion('args', 'Map<String, Object>'), variableCompletion('console', 'Console'), variableCompletion('Math', 'Math'), variableCompletion('JSON', 'JSON'), variableCompletion('Object', 'Object'), variableCompletion('Array', 'Array'), variableCompletion('String', 'String'), variableCompletion('Number', 'Number'), variableCompletion('Date', 'Date'), variableCompletion('RegExp', 'RegExp'), variableCompletion('Map', 'Map'), variableCompletion('Set', 'Set'), variableCompletion('Promise', 'Promise'),
    functionCompletion('parseInt(str)', 'parseInt'), functionCompletion('parseFloat(str)', 'parseFloat'), functionCompletion('isNaN(value)', 'isNaN'), functionCompletion('isFinite(value)', 'isFinite')
  ],
  emaki: [
    propertyCompletion('context', 'ScriptContextApi'), propertyCompletion('player', 'ScriptPlayerApi'), propertyCompletion('item', 'ScriptItemApi'), propertyCompletion('action', 'ScriptActionApi'), propertyCompletion('logger', 'ScriptLoggerApi'), propertyCompletion('random', 'ScriptRandomApi'), propertyCompletion('state', 'ScriptSharedStateApi'), propertyCompletion('text', 'ScriptTextApi'),
    functionCompletion('runSync(task)', 'runSync'), functionCompletion('runSyncAndWait(task)', 'runSyncAndWait')
  ],
  'emaki.context': [functionCompletion('phase()', 'phase'), functionCompletion('plugin()', 'plugin'), functionCompletion('placeholder(key)', 'placeholder'), functionCompletion('attribute(key)', 'attribute'), functionCompletion('arg(key)', 'arg'), functionCompletion('placeholders()', 'placeholders'), functionCompletion('attributes()', 'attributes'), functionCompletion('args()', 'args')],
  'emaki.player': [functionCompletion('exists()', 'exists'), functionCompletion('name()', 'name'), functionCompletion('uuid()', 'uuid'), functionCompletion('world()', 'world'), functionCompletion('hasPermission(permission)', 'hasPermission'), functionCompletion('sendMessage(message)', 'sendMessage')],
  'emaki.item': [functionCompletion('has(attributeKey)', 'has'), functionCompletion('type(attributeKey)', 'type'), functionCompletion('amount(attributeKey)', 'amount'), functionCompletion('displayName(attributeKey)', 'displayName')],
  'emaki.action': [functionCompletion('run(actionId, arguments)', 'run'), functionCompletion('runLine(line)', 'runLine')],
  'emaki.logger': [functionCompletion('info(message)', 'info'), functionCompletion('warn(message)', 'warn'), functionCompletion('error(message)', 'error')],
  'emaki.random': [functionCompletion('integer(min, max)', 'integer'), functionCompletion('decimal()', 'decimal'), functionCompletion('chance(percent)', 'chance'), functionCompletion('pick(values)', 'pick')],
  'emaki.state': [functionCompletion('set(key, value)', 'set'), functionCompletion('get(key)', 'get'), functionCompletion('has(key)', 'has'), functionCompletion('remove(key)', 'remove')],
  'emaki.text': [functionCompletion('string(value)', 'string'), functionCompletion('blank(value)', 'blank'), functionCompletion('notBlank(value)', 'notBlank'), functionCompletion('lower(value)', 'lower'), functionCompletion('normalizeId(value)', 'normalizeId')],
  console: [functionCompletion('log(message)', 'log'), functionCompletion('warn(message)', 'warn'), functionCompletion('error(message)', 'error'), functionCompletion('info(message)', 'info'), functionCompletion('debug(message)', 'debug')],
  Math: [functionCompletion('abs(x)', 'abs'), functionCompletion('ceil(x)', 'ceil'), functionCompletion('floor(x)', 'floor'), functionCompletion('round(x)', 'round'), functionCompletion('max(...values)', 'max'), functionCompletion('min(...values)', 'min'), functionCompletion('random()', 'random'), functionCompletion('pow(base, exp)', 'pow'), functionCompletion('sqrt(x)', 'sqrt'), propertyCompletion('PI', 'number'), propertyCompletion('E', 'number')],
  JSON: [functionCompletion('parse(text)', 'parse'), functionCompletion('stringify(value)', 'stringify'), functionCompletion('stringify(value, null, 2)', 'stringify')],
  Object: [functionCompletion('keys(obj)', 'keys'), functionCompletion('values(obj)', 'values'), functionCompletion('entries(obj)', 'entries'), functionCompletion('assign(target, ...sources)', 'assign'), functionCompletion('freeze(obj)', 'freeze')],
  Array: [functionCompletion('isArray(value)', 'isArray'), functionCompletion('from(arrayLike)', 'from')],
  String: [functionCompletion('fromCharCode(code)', 'fromCharCode')],
  Number: [functionCompletion('parseInt(str)', 'parseInt'), functionCompletion('parseFloat(str)', 'parseFloat'), functionCompletion('isNaN(value)', 'isNaN'), functionCompletion('isFinite(value)', 'isFinite')]
};

const scriptCompletionSource: CompletionSource = (context: CompletionContext): CompletionResult | null => {
  const token = context.matchBefore(/[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\.?/);
  if (!token || (token.from === token.to && !context.explicit)) return null;
  const expression = token.text;
  const dotIndex = expression.lastIndexOf('.');
  const scopeName = dotIndex >= 0 ? expression.slice(0, dotIndex) : 'global';
  const partial = dotIndex >= 0 ? expression.slice(dotIndex + 1) : expression;
  if (!context.explicit && scopeName === 'global' && partial.length < 2) return null;
  const options = SCRIPT_COMPLETION_SCOPES[scopeName];
  if (!options) return null;
  return {
    from: token.to - partial.length,
    validFor: /^[A-Za-z_$][\w$]*$/,
    options: options.filter(option => option.label.toLowerCase().startsWith(partial.toLowerCase()))
  };
};

function keywordCompletion(label: string): Completion { return { label, type: 'keyword' }; }
function variableCompletion(label: string, detail: string): Completion { return { label, type: 'variable', detail }; }
function propertyCompletion(label: string, detail: string): Completion { return { label, type: 'property', detail }; }
function functionCompletion(label: string, apply: string): Completion { return { label: apply, type: 'function', detail: label, apply: label }; }

function sameSelection(a: Selection | null, b: Selection) { return a?.moduleId === b.moduleId && a.fileId === b.fileId && (a.scriptPath ?? '') === (b.scriptPath ?? ''); }
function readTheme(): ColorTheme { const saved = localStorage.getItem('emaki-color-theme'); return COLOR_THEMES.some((entry) => entry.id === saved) ? saved as ColorTheme : 'dark'; }
function localeLabel(locale: string): string { return LOCALE_LABELS[locale] ?? LOCALE_LABELS[locale.replace('-', '_')] ?? LOCALE_LABELS[locale.replace('_', '-')] ?? locale; }
function EmakiParentMark() {
  return <svg className="emaki-mark" viewBox="0 0 40 40" aria-hidden="true" focusable="false">
    <path d="M12.2 11.8v16.4m0-16.4h8.8m-8.8 6.9h7.4m-7.4 6.6h8.2" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    <path d="M24.5 11.8h3.4c4.1 0 6.9 2.1 6.9 5.1 0 2.4-1.8 4.2-4.9 4.9l4.2 6.4h-3.7l-3.7-6h-2.2v6h-3.4V11.8Zm3.4 2.9v4.4h3.2c2.1 0 3.6-.8 3.6-2.1 0-1.4-1.5-2.3-3.6-2.3h-3.2Z" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
  </svg>;
}

function LocaleIcon() {
  return <svg className="locale-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M8 1.6a6.4 6.4 0 1 0 0 12.8A6.4 6.4 0 0 0 8 1.6Zm0 0c1.65 1.6 2.45 3.72 2.4 6.4-.05 2.68-.85 4.8-2.4 6.4M8 1.6C6.35 3.2 5.55 5.32 5.6 8c.05 2.68.85 4.8 2.4 6.4M2.4 8h11.2M3.7 4.8h8.6M3.7 11.2h8.6" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" /></svg>;
}

function ThemeIcon({ theme }: { theme: ColorTheme }) {
  if (theme === 'light') {
    return <svg className="theme-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path fillRule="evenodd" d="M8 1.2a.7.7 0 0 1 .7.7v1.02a.7.7 0 1 1-1.4 0V1.9a.7.7 0 0 1 .7-.7Zm4.81 1.99a.7.7 0 0 1 0 .99l-.72.72a.7.7 0 1 1-.99-.99l.72-.72a.7.7 0 0 1 .99 0ZM5.08 8a2.92 2.92 0 1 1 5.84 0 2.92 2.92 0 0 1-5.84 0Zm8 .7a.7.7 0 1 0 0-1.4h-1.02a.7.7 0 1 0 0 1.4h1.02Zm-.27 3.12a.7.7 0 0 1-.99.99l-.72-.72a.7.7 0 1 1 .99-.99l.72.72ZM8 12.38a.7.7 0 0 1 .7.7v1.02a.7.7 0 1 1-1.4 0v-1.02a.7.7 0 0 1 .7-.7ZM4.9 12.09a.7.7 0 1 0-.99-.99l-.72.72a.7.7 0 1 0 .99.99l.72-.72ZM3.94 8.7a.7.7 0 0 0 0-1.4H2.92a.7.7 0 0 0 0 1.4h1.02Zm.96-3.8a.7.7 0 0 1-.99 0l-.72-.72a.7.7 0 1 1 .99-.99l.72.72a.7.7 0 0 1 0 .99Z" /></svg>;
  }
  return <svg className="theme-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M12.92 9.66a.64.64 0 0 1 .78.8A6.28 6.28 0 1 1 5.54 2.3a.64.64 0 0 1 .8.78 5.32 5.32 0 0 0 6.58 6.58Z" /></svg>;
}
function configDraftScope(module: WebRegistryModule, file: WebRegistryFile, childPath?: string): ConfigDraftScope {
  return { moduleId: module.id, fileId: file.id, filePath: normalizeDraftPath(childPath || file.path) };
}

function draftScopeId(scope: ConfigDraftScope) {
  return JSON.stringify([scope.moduleId, scope.fileId, normalizeDraftPath(scope.filePath)]);
}

function draftKey(scope: ConfigDraftScope, path: string) {
  return JSON.stringify([scope.moduleId, scope.fileId, normalizeDraftPath(scope.filePath), path]);
}

function draftScopePrefix(scope: ConfigDraftScope) {
  return `${draftScopeId(scope).slice(0, -1)},`;
}

function emptyDraftHistory(): DraftScopeHistory { return { undo: [], redo: [] }; }

function draftScopeSnapshot(drafts: DraftMap, scope: ConfigDraftScope): DraftMap {
  const prefix = draftScopePrefix(scope);
  return Object.fromEntries(Object.entries(drafts).filter(([key]) => key.startsWith(prefix)));
}

function applyDraftScopeSnapshot(drafts: DraftMap, scope: ConfigDraftScope, snapshot: DraftMap): DraftMap {
  return { ...removeDraftScope(drafts, scope), ...snapshot };
}

function removeDraftScope(drafts: DraftMap, scope: ConfigDraftScope): DraftMap {
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

function removeDraftHistoryScope(history: DraftHistoryMap, scope: ConfigDraftScope): DraftHistoryMap {
  const id = draftScopeId(scope);
  if (!(id in history)) return history;
  const next = { ...history };
  delete next[id];
  return next;
}

function dirtyTreeKeysFromDrafts(drafts: DraftMap): Set<string> {
  const keys = new Set<string>();
  for (const key of Object.keys(drafts)) {
    const parts = parseDraftKey(key);
    if (!parts) continue;
    keys.add(treeDirtyKey(parts.moduleId, parts.fileId, parts.filePath));
  }
  return keys;
}

function parseDraftKey(key: string): ConfigDraftScope | null {
  try {
    const value = JSON.parse(key);
    if (!Array.isArray(value) || value.length < 4) return null;
    return { moduleId: String(value[0]), fileId: String(value[1]), filePath: normalizeDraftPath(String(value[2])) };
  } catch {
    return null;
  }
}

function treeDirtyKey(moduleId: string, fileId: string, filePath: string) {
  return JSON.stringify([moduleId, fileId, normalizeDraftPath(filePath)]);
}

function sourceEditingElement(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName.toLowerCase();
  return tag === 'input' || tag === 'textarea' || tag === 'select' || target.isContentEditable;
}

function normalizeDraftPath(path: string) { return path.replace(/\\/g, '/'); }
function isObjectLike(v: unknown) { return typeof v === 'object' && v !== null; }
function isPlainObject(v: unknown): v is Record<string, unknown> { return typeof v === 'object' && v !== null && !Array.isArray(v); }
function parseListValue(original: unknown, text: string) { if (isObjectLike(original)) { try { return JSON.parse(text); } catch { return text; } } return text; }
function str(v: unknown): string { if (v == null) return ''; if (typeof v === 'object') try { return JSON.stringify(v, null, 2); } catch { return ''; } return String(v); }
function firstSelection(r: WebRegistry): Selection | null { const m = r.modules[0]; return m?.files[0] ? { moduleId: m.id, fileId: m.files[0].id } : null; }
