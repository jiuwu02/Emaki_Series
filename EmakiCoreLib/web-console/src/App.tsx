import { Component, memo, startTransition, useDeferredValue, useEffect, useMemo, useRef, useState, type CSSProperties, type FormEvent, type ReactNode } from 'react';
import type { Completion, CompletionContext, CompletionResult, CompletionSource } from '@codemirror/autocomplete';
import type { ComponentType } from 'react';
import { ApiClient } from './api';
import { GuiEditorSurface } from './GuiEditorSurface';
import { ItemEditorSurface } from './ItemEditorSurface';
import { loadWebExtensions } from './extensions';
import { applyConfigNodeOverrides, applyConfigRegistryOverrides, applyEditorDescriptorOverrides, getConfigPreview, getSourceDocumentAdapter, getSurface, isKind, registerSourceDocumentAdapter, registerSurface, setRuntimeEnums, type ConfigPreviewProps, type SourceDocumentAdapterContext } from './registry';
import { getLocale, getRegisteredLocales, setLocale, t } from './i18n';
import { ActionGroup, Button, CodeEditor, EditorChrome, DisclosureChevron, InlineError, NumberListEditor, StringListEditor, ToastNotice, type EditorChange } from './components';
import { useDialogFocus } from './components/useDialogFocus';
import { useStableEntries } from './components/useStableEntries';
import { I18nBundleModal, type I18nTarget } from './I18nBundleModal';
import { configNodeDisplayComment as resolveConfigNodeComment, fieldLabel, fileDisplayComment, fileDisplayTitle, humanizeFieldLabel, moduleDisplayName, optionLabel, parseYaml, serializeYaml, setDeepValue, valuesEqual } from './lib';
import { Login, ResizableRail, WorkspaceTree, fileKindLabel } from './shell';
import type { SurfaceProps, SurfaceToolbarState } from './registry';
import type { RegistryTreeNode, WebConfigCreateTemplate, WebConfigFieldSchema, WebConfigNode, WebConsoleExtension, WebConsoleExtensionStatus, WebEditorDescriptor, WebRegistry, WebRegistryFile, WebRegistryModule } from './types';

// Register CoreLib's built-in surfaces through the same registry used by plugin extensions.
registerSurface({ kind: 'GUI', component: GuiEditorSurface as ComponentType<SurfaceProps>, label: t('core.surface.gui.label') });
registerSurface({ kind: 'ITEM', component: ItemEditorSurface as ComponentType<SurfaceProps>, label: t('core.surface.item.label') });
for (const kind of ['CONFIG', 'GUI', 'ITEM', 'SCRIPT']) {
  registerSourceDocumentAdapter({
    kind,
    adapter: {
      read: (api, context) => api.readTextDocument({ kind, moduleId: context.module.id, path: context.path }),
      save: (api, context, content, revision) => api.saveTextDocument({ kind, moduleId: context.module.id, path: context.path }, content, revision),
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
type LoginNotice = 'expired' | 'signedOut' | null;
type ColorTheme = 'dark' | 'light';

const COLOR_THEMES: { id: ColorTheme; labelKey: string }[] = [
  { id: 'dark', labelKey: 'core.theme.dark' },
  { id: 'light', labelKey: 'core.theme.light' }
];
const LOCALE_LABELS: Record<string, string> = { 'zh-CN': '简体中文', zh_CN: '简体中文', 'en-US': 'English', en_US: 'English' };
const CONFIG_INITIAL_GROUPS = 10;
const CONFIG_GROUP_BATCH_SIZE = 12;
const CONFIG_SECTION_INITIAL_GROUPS = 8;
const CONFIG_SECTION_GROUP_BATCH_SIZE = 10;
const CONFIG_LAZY_SECTION_THRESHOLD = 10;
const OBJECT_LIST_COLLAPSE_THRESHOLD = 10;
const OBJECT_LIST_INITIAL_ROWS = 30;
const OBJECT_LIST_ROW_BATCH_SIZE = 30;

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('emaki-web-token'));
  const [loginNotice, setLoginNotice] = useState<LoginNotice>(null);
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
  const [extensionStatuses, setExtensionStatuses] = useState<WebConsoleExtensionStatus[]>([]);
  const [extensionHealth, setExtensionHealth] = useState<'idle' | 'loading' | 'ok' | 'failed'>('idle');
  const registryLoadSeq = useRef(0);

  const clearSession = (notice: LoginNotice) => {
    registryLoadSeq.current += 1;
    sessionStorage.removeItem('emaki-web-token');
    setLoginNotice(notice);
    setToken(null);
    setRegistry(null);
    setSelected(null);
    setDrafts({});
    setDraftHistory({});
    setSurfaceToolbar(null);
    setSurfaceDirtyKeys(new Set());
    setCreateTarget(null);
    setDeleteTarget(null);
    setI18nTarget(null);
    setToast(null);
  };

  const expireSession = () => clearSession('expired');
  const signOut = () => clearSession('signedOut');

  const api = useMemo(() => new ApiClient(token, expireSession), [token]);

  useEffect(() => { if (token) void loadRegistry({ initial: true, clearDrafts: true }); }, [token]);
  useEffect(() => {
    if (!token) return;
    const validateStoredToken = () => {
      if (sessionStorage.getItem('emaki-web-token') !== token) expireSession();
    };
    window.addEventListener('focus', validateStoredToken);
    window.addEventListener('storage', validateStoredToken);
    return () => {
      window.removeEventListener('focus', validateStoredToken);
      window.removeEventListener('storage', validateStoredToken);
    };
  }, [token]);
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
    const loadSeq = registryLoadSeq.current + 1;
    registryLoadSeq.current = loadSeq;
    setLoading(true);
    setExtensionHealth('loading');
    setExtensionStatuses([]);
    try {
      const next = await api.registry();
      if (loadSeq !== registryLoadSeq.current) return null;
      setRuntimeEnums(next.runtimeEnums);
      const merged = enhanceRegistry(next);
      setRegistry(merged);
      if (initial) setExpanded(Object.fromEntries(merged.modules.map((m) => [m.id, true])));
      setSelected((c) => c ?? firstSelection(merged));
      if (clearDrafts) {
        setDrafts({});
        setDraftHistory({});
      }
      if (next.extensions?.length) {
        void loadExtensionsInBackground(next, loadSeq, announceRefresh);
      } else {
        setExtensionHealth('ok');
        if (announceRefresh) setToast({ tone: 'ok', text: t('core.toast.registryRefreshed') });
      }
      return merged;
    } catch (err) {
      if (loadSeq === registryLoadSeq.current) {
        setExtensionHealth('failed');
        setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
      }
      return null;
    } finally {
      if (loadSeq === registryLoadSeq.current) setLoading(false);
    }
  }

  async function loadExtensionsInBackground(baseRegistry: WebRegistry, loadSeq: number, announceRefresh: boolean): Promise<void> {
    try {
      const nextExtensionStatuses = await loadWebExtensions(baseRegistry.extensions, { defer: true });
      if (loadSeq !== registryLoadSeq.current) return;
      const failedExtensions = nextExtensionStatuses.filter(status => status.status === 'failed');
      const merged = enhanceRegistry(baseRegistry);
      setRegistry(merged);
      setExtensionStatuses(nextExtensionStatuses);
      setExtensionHealth(failedExtensions.length ? 'failed' : 'ok');
      setSelected((current) => current && selectionExists(merged, current) ? current : firstSelection(merged));
      if (failedExtensions.length) setToast({ tone: 'bad', text: t('core.toast.extensionLoadFailed', { count: failedExtensions.length }) });
      else if (announceRefresh) setToast({ tone: 'ok', text: t('core.toast.registryRefreshed') });
    } catch (err) {
      if (loadSeq !== registryLoadSeq.current) return;
      setExtensionHealth('failed');
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.extensionLoadFailed', { count: 1 }) });
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
      const createName = normalizeTreeCreateName(target, name);
      const content = createFileDefaultContent(registry, target, createName);
      const created = await api.createFile(target.moduleId, target.fileId, createName, content);
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
  const changedCount = useMemo(() => selectedDraftScope && selectedFile ? selectedFile.nodes.filter((n) => n.type !== 'object' && draftKey(selectedDraftScope, n.path) in drafts).length : 0, [selectedDraftScope?.moduleId, selectedDraftScope?.fileId, selectedDraftScope?.filePath, selectedFile?.nodes, drafts]);
  const dirtyTreeKeys = useMemo(() => dirtyTreeKeysFromDrafts(drafts), [drafts]);
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
    changes: selectedDraftScope && selectedFile && changedCount > 0 ? configChanges(selectedDraftScope, selectedFile.nodes, drafts) : [],
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
  const selectableLocales = locales.includes(currentLocale) ? locales : [currentLocale, ...locales];
  const currentLocaleLabel = localeLabel(currentLocale);
  const pendingExtensionModules = useMemo(() => pendingExtensionModuleIds(registry?.extensions, extensionStatuses, extensionHealth), [registry?.extensions, extensionStatuses, extensionHealth]);
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

  if (!token) return <Login notice={loginNotice} onLogin={(t) => { sessionStorage.setItem('emaki-web-token', t); setLoginNotice(null); setToken(t); }} />;

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
                {selectableLocales.length ? selectableLocales.map((locale) => <option key={locale} value={locale}>{localeLabel(locale)}</option>) : <option>{t('core.i18n.noLocales')}</option>}
              </select>
            </label>
          </div>
        </div>
        <ExtensionHealthBanner
          health={extensionHealth}
          statuses={extensionStatuses}
          onRetry={() => void loadRegistry({ clearDrafts: false, announceRefresh: false })}
        />
        <WorkspaceTree registry={registry} selected={selected} expanded={expanded} dirtyKeys={mergedDirtyKeys} setExpanded={setExpanded} onOpenI18n={setI18nTarget} onCreateFile={setCreateTarget} onDeleteFile={setDeleteTarget} onSelect={(next) => setSelected((current) => sameSelection(current, next) ? { ...next, refreshKey: (current?.refreshKey ?? 0) + 1 } : next)} />
        <button className="rail-action quiet" onClick={signOut}>{t('core.auth.logout')}</button>
      </ResizableRail>
      <main className="stage">
        <SurfaceSummaryStrip module={selectedModule} file={selectedFile} editor={selectedEditor} toolbar={toolbar} loading={loading} />
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
          <ConfigSurface registry={registry} module={selectedModule} file={selectedFile} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} scriptPath={selected?.scriptPath} refreshKey={selected?.refreshKey ?? 0} pendingExtensionModules={pendingExtensionModules} onReload={() => void reloadCurrentSurface()} onRefreshRegistry={() => loadRegistry({ clearDrafts: false, announceRefresh: false })} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} />
        </section>
      </main>
    </div>
  );
}

function SurfaceSummaryStrip({ module, file, editor, toolbar, loading }: { module: WebRegistryModule | null; file: WebRegistryFile | null; editor?: WebEditorDescriptor; toolbar: SurfaceToolbarState; loading: boolean }) {
  const moduleName = module ? moduleDisplayName(module) : t('core.stage.defaultTitle');
  const moduleSummary = module?.summary?.trim() || '';
  const fileComment = file ? fileDisplayComment(file).trim() : '';
  const filePath = file?.path || '';
  const fileKind = fileKindLabel(file?.kind);
  const fileNodeCount = file?.nodes?.length ?? 0;
  const fileChildCount = file?.children?.length ?? 0;
  const chips = [
    fileKind,
    editor?.kindLabel || editor?.title || '',
    fileNodeCount ? t('core.stage.nodeCount', { count: fileNodeCount }, '{count} nodes') : '',
    fileChildCount ? t('core.stage.childCount', { count: fileChildCount }, '{count} children') : '',
    toolbar.dirty ? t('core.item.unsaved', undefined, 'Unsaved') : '',
    toolbar.saving ? t('core.script.saving', undefined, 'Saving...') : '',
    loading ? t('core.state.loading') : ''
  ].filter(Boolean);

  return <section className={`surface-summary${toolbar.dirty ? ' dirty' : ''}`.trim()} aria-label={t('core.stage.summaryAria', undefined, 'Current surface summary')}>
    <div className="surface-summary-copy">
      <strong>{moduleName}</strong>
      <p>{moduleSummary || fileComment || t('core.stage.defaultHint')}</p>
      <code>{fileComment ? `${fileComment}${filePath ? ` · ${filePath}` : ''}` : filePath || t('core.stage.defaultHint')}</code>
    </div>
    <div className="surface-summary-meta" aria-live="polite">
      {chips.map((chip, index) => <span key={`${chip}-${index}`} className={`surface-summary-chip${chip === fileKind ? ' kind' : ''}`}>{chip}</span>)}
    </div>
  </section>;
}

function ExtensionHealthBanner({ health, statuses, onRetry }: { health: 'idle' | 'loading' | 'ok' | 'failed'; statuses: WebConsoleExtensionStatus[]; onRetry: () => void }) {
  if (health === 'idle') return null;
  const failed = statuses.filter(status => status.status === 'failed');
  const loadedCount = Math.max(0, statuses.length - failed.length);
  const label = health === 'loading'
    ? t('core.state.loading')
    : health === 'failed'
      ? t('core.toast.extensionLoadFailed', { count: failed.length || statuses.length || 1 })
      : t('core.extension.loaded', { count: statuses.length }, '{count} extensions loaded');

  return <aside className={`extension-health extension-health--${health}`} aria-live="polite">
    <div className="extension-health-copy">
      <strong>{health === 'loading' ? t('core.extension.loading', undefined, 'Loading plugin extensions') : health === 'failed' ? t('core.extension.failed', undefined, 'Some plugin extensions did not load') : t('core.extension.ok', undefined, 'Plugin extensions ready')}</strong>
      <p>{health === 'loading' ? t('core.extension.loadingDesc', undefined, 'Loading plugin extensions and the WebUIEdit host bridge. CoreLib base editors remain available while this finishes.') : health === 'failed' ? t('core.extension.failedDesc', { count: failed.length || statuses.length || 1 }, '{count} plugin extension(s) failed to load. Base editors remain available; fix the extension and retry loading.') : label}</p>
    </div>
    <div className="extension-health-meta">
      {health === 'failed' ? <>
        <div className="extension-health-list" aria-label={t('core.extension.failedList', undefined, 'Failed extensions')}>
          {(failed.length || statuses.length)
            ? (failed.length ? failed : statuses).slice(0, 3).map(status => <code key={`${status.moduleId}:${status.id}`}>{status.moduleId}/{status.id}{status.error ? ` · ${status.error}` : ''}</code>)
            : <code>{t('core.extension.failedFallback', undefined, 'Extension status unavailable')}</code>}
          {(failed.length || statuses.length) > 3 && <span className="extension-health-more">+{(failed.length || statuses.length) - 3}</span>}
        </div>
        <Button size="sm" variant="soft" onClick={onRetry}>{t('core.action.retry')}</Button>
      </> : health === 'loading' ? <span className="extension-health-count">{t('core.extension.loadingShort', undefined, 'Loading...')}</span> : null}
    </div>
  </aside>;
}

function CreateFileModal({ target, onCancel, onCreate }: { target: RegistryTreeNode; onCancel: () => void; onCreate: (target: RegistryTreeNode, name: string) => void | Promise<void> }) {
  const [name, setName] = useState('');
  const validation = validateCreateFileName(name);
  function submit(event: FormEvent) {
    event.preventDefault();
    if (!validation.ok) return;
    void onCreate(target, name.trim());
  }
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <form className="file-action-dialog" role="dialog" aria-modal="true" aria-labelledby="file-create-title" onSubmit={submit}>
      <div className="reload-confirm-head"><span>{fileKindLabel(target.kind)}</span><h3 id="file-create-title">{t('core.file.createTitle')}</h3></div>
      <div className="reload-confirm-body"><p>{t('core.file.createDesc')}</p><label className="file-confirm-field"><span>{t('core.file.createName')}</span><input autoFocus value={name} onChange={event => setName(event.target.value)} placeholder={t('core.file.createPlaceholder')} />{validation.message && <small className="field-error" role="alert">{validation.message}</small>}</label></div>
      <ActionGroup className="reload-confirm-actions"><Button type="button" onClick={onCancel}>{t('core.gui.cancel')}</Button><Button type="submit" variant="primary" disabled={!validation.ok}>{t('core.file.create')}</Button></ActionGroup>
    </form>
  </div>;
}

function normalizeTreeCreateName(target: RegistryTreeNode, name: string): string {
  const normalized = name.trim().replace(/\\/g, '/');
  const prefix = String(target.createPrefix ?? '').replace(/\\/g, '/').replace(/^\/+|\/+$/g, '');
  if (!prefix || normalized.startsWith(`${prefix}/`)) return normalized;
  return `${prefix}/${normalized}`;
}

function createFileDefaultContent(registry: WebRegistry | null, target: RegistryTreeNode, name: string): string | undefined {
  if (!registry || !target.moduleId || !target.fileId) return undefined;
  const module = registry.modules.find(entry => entry.id === target.moduleId);
  const file = module?.files.find(entry => entry.id === target.fileId);
  if (!module || !file) return undefined;
  const editor = file.editorId ? registry.editors?.[file.editorId] : undefined;
  const adapter = getSourceDocumentAdapter(file, editor);
  if (!adapter?.defaultContent) return undefined;
  const normalizedName = name.trim().replace(/\\/g, '/');
  const path = normalizeCreatedFilePath(file, normalizedName);
  const context: SourceDocumentAdapterContext & { name: string; path: string } = { module, file, childPath: path, path, editor, name: normalizedName };
  return adapter.defaultContent(context);
}

function normalizeCreatedFilePath(file: WebRegistryFile, name: string): string {
  const normalizedName = name.trim().replace(/\\/g, '/');
  const extension = file.path.match(/\.([a-z0-9]+)$/i)?.[0] ?? '.yml';
  const leaf = /\.[a-z0-9]+$/i.test(normalizedName) ? normalizedName : `${normalizedName}${extension}`;
  const globPrefix = file.path.split('**')[0]?.replace(/\/$/, '') ?? '';
  return globPrefix ? `${globPrefix}/${leaf}` : leaf;
}

function validateCreateFileName(name: string): { ok: boolean; message?: string } {
  const value = name.trim().replace(/\\/g, '/');
  if (!value) return { ok: false };
  if (value.startsWith('/') || value.endsWith('/') || value.includes('..')) return { ok: false, message: '文件名不合法' };
  const illegal = Array.from(new Set(Array.from(value).filter(char => /[<>:"|?*]/.test(char) || char.charCodeAt(0) < 32)));
  if (illegal.length) return { ok: false, message: `文件名包含非法字符：${illegal.join(' ')}` };
  if (/<\s*\/?\s*[a-z][^>]*>/i.test(value)) return { ok: false, message: '文件名包含非法 HTML 标签' };
  if (value.split('/').some(part => !part || part === '.' || part === '..')) return { ok: false, message: '文件名不合法' };
  return { ok: true };
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

function ConfigSurface({ registry, module, file, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, undoDraftScope, redoDraftScope, api, scriptPath, refreshKey, pendingExtensionModules, onReload, onRefreshRegistry, setSurfaceToolbar, setToast }: { registry: WebRegistry | null; module: WebRegistryModule | null; file: WebRegistryFile | null; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; scriptPath?: string; refreshKey: number; pendingExtensionModules: ReadonlySet<string>; onReload?: () => void; onRefreshRegistry: () => Promise<WebRegistry | null>; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: Toast) => void }) {
  useEffect(() => {
    setSurfaceToolbar(null);
    return () => setSurfaceToolbar(null);
  }, [module?.id, file?.id, scriptPath]);

  if (registry && registry.modules.length === 0) return <section className="config-surface empty" role="status">{t('core.empty.noRegistry')}</section>;
  if (!module || !file) return <section className="config-surface empty" role="status">{t('core.empty.selectConfig')}</section>;
  const editor = file.editorId ? registry?.editors?.[file.editorId] : undefined;

  // Check registry for a custom surface first
  const registeredSurface = getSurface(file, editor);
  if (extensionSurfacePending(module, file, editor, registeredSurface, pendingExtensionModules)) {
    return <section className="config-surface empty" role="status">{t('core.extension.loadingEditor', undefined, '正在加载插件编辑器…')}</section>;
  }
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
  const fileComment = fileDisplayComment(file);
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
    <ConfigNodeTree scope={scope} nodes={visibleNodes} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={setCreateNode} onDeleteObject={setDeleteNode} sourceEdit={!source.loading && !source.error ? sourceEdit : undefined} deletedPaths={deletedObjectPaths} />
    {createNode && <ConfigCreateChildModal scope={scope} node={createNode} source={source} onCancel={() => setCreateNode(null)} onCreated={nodes => { setOptimisticNodes(current => mergeConfigNodes(current, nodes, new Set())); setCreateNode(null); }} setToast={setToast} />}
    {deleteNode && <ConfigDeleteObjectModal node={deleteNode} source={source} onCancel={() => setDeleteNode(null)} onDeleted={path => { setDeletedObjectPaths(current => new Set([...current, path])); setOptimisticNodes(current => current.filter(entry => !entry.path.startsWith(`${path}.`) && entry.path !== path)); setDeleteNode(null); }} setToast={setToast} />}
  </section>;
}

function DeferredConfigPreviewZone(props: { module: WebRegistryModule; file: WebRegistryFile; path: string; childPath?: string; nodes: WebConfigNode[]; scope: ConfigDraftScope; drafts: DraftMap; source: ConfigSourceDocument; api: ApiClient }) {
  const ready = useIdleReady([props.module.id, props.file.id, props.path, props.childPath, props.source.loading, props.source.error], 140);
  const deferredDrafts = useDebouncedValue(props.drafts, 140);
  const deferredNodes = useDeferredValue(props.nodes);
  if (!ready) return null;
  return <ConfigPreviewZone {...props} drafts={deferredDrafts} nodes={deferredNodes} />;
}

function ConfigPreviewZone({ module, file, path, childPath, nodes, scope, drafts, source, api }: { module: WebRegistryModule; file: WebRegistryFile; path: string; childPath?: string; nodes: WebConfigNode[]; scope: ConfigDraftScope; drafts: DraftMap; source: ConfigSourceDocument; api: ApiClient }) {
  const registration = getConfigPreview({ moduleId: module.id, kind: file.kind, path });
  const changedDraftKey = useMemo(() => draftSignatureForScope(drafts, scope), [drafts, scope.moduleId, scope.fileId, scope.filePath]);
  const deferredDraftKey = useDeferredValue(changedDraftKey);
  const sourceContent = useMemo(() => {
    if (source.dirty) return source.content;
    if (!registration) return '';
    return configSourcePreview(source.original, scope, nodes.filter(node => node.type !== 'object' && draftKey(scope, node.path) in drafts), drafts);
  }, [registration, source.dirty, source.content, source.original, scope.moduleId, scope.fileId, scope.filePath, nodes, deferredDraftKey]);
  const data = useMemo(() => registration ? configPreviewData(sourceContent, nodes, scope, drafts) : {}, [registration, sourceContent, nodes, scope.moduleId, scope.fileId, scope.filePath, deferredDraftKey]);
  if (!registration) return null;
  const Preview = registration.component;
  const props: ConfigPreviewProps = { module, file, path, childPath, nodes, data, sourceContent, sourceDirty: source.dirty, sourceError: source.error, api };
  return <ConfigPreviewBoundary previewKey={`${module.id}:${path}`}><div className="config-preview-zone"><Preview {...props} /></div></ConfigPreviewBoundary>;
}

type ConfigPreviewBoundaryProps = { previewKey: string; children: ReactNode };
type ConfigPreviewBoundaryState = { error: Error | null; previewKey: string };

class ConfigPreviewBoundary extends Component<ConfigPreviewBoundaryProps, ConfigPreviewBoundaryState> {
  state: ConfigPreviewBoundaryState = { error: null, previewKey: this.props.previewKey };

  static getDerivedStateFromProps(props: ConfigPreviewBoundaryProps, state: ConfigPreviewBoundaryState): Partial<ConfigPreviewBoundaryState> | null {
    return props.previewKey !== state.previewKey ? { error: null, previewKey: props.previewKey } : null;
  }

  static getDerivedStateFromError(error: Error): Partial<ConfigPreviewBoundaryState> {
    return { error };
  }

  render() {
    if (this.state.error) {
      return <div className="config-preview-zone"><InlineError><span>{t('core.configPreview.unavailable', { message: this.state.error.message })}</span></InlineError></div>;
    }
    return this.props.children;
  }
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
  const sourcePath = childPath || file.path;
  const context = useMemo(() => ({ module, file, childPath, path: sourcePath, editor }), [module.id, file.id, childPath, sourcePath, editor?.id]);

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
      setContent(current => current === next ? current : next);
      setError(null);
    },
    reload,
    save
  };
}

function ConfigChildSurface({ module, file, childPath, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, undoDraftScope, redoDraftScope, api, refreshKey, setSurfaceToolbar, setToast }: { module: WebRegistryModule; file: WebRegistryFile; childPath: string; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; refreshKey: number; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: Toast) => void }) {
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

  async function reloadChildNodes(announce = true) {
    setLoading(true);
    setError('');
    try {
      const refreshed = await api.registryFileNodes(module.id, childPath);
      setBaseNodes(applyConfigNodeOverrides(module.id, refreshed.nodes, childPath));
      setOptimisticNodes([]);
      setRevision(refreshed.revision);
      clearDraftScope(scope);
      setSourceEditedPaths(new Set());
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
      let nextRevision = revision;
      for (const node of changed) {
        const result = await api.saveRegistryValue(module.id, childPath, node.path, drafts[draftKey(scope, node.path)], nextRevision);
        nextRevision = result.revision ?? nextRevision;
      }
      setRevision(nextRevision);
      await reloadChildNodes(false);
      clearDraftValues(scope);
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: changed.length }) });
    } catch (err) {
      setToast({ tone: 'bad', text: userFacingSaveError(err) });
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
    {!loading && !error && <ConfigNodeTree scope={scope} nodes={visibleNodes} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={setCreateNode} onDeleteObject={setDeleteNode} sourceEdit={!source.loading && !source.error ? sourceEdit : undefined} deletedPaths={deletedObjectPaths} />}
    {createNode && <ConfigCreateChildModal scope={scope} node={createNode} source={source} onCancel={() => setCreateNode(null)} onCreated={nodes => { setOptimisticNodes(current => mergeConfigNodes(current, nodes, new Set())); setCreateNode(null); }} setToast={setToast} />}
    {deleteNode && <ConfigDeleteObjectModal node={deleteNode} source={source} onCancel={() => setDeleteNode(null)} onDeleted={path => { setDeletedObjectPaths(current => new Set([...current, path])); setOptimisticNodes(current => current.filter(entry => !entry.path.startsWith(`${path}.`) && entry.path !== path)); setDeleteNode(null); }} setToast={setToast} />}
  </section>;
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
  const nodes: WebConfigNode[] = [root];
  for (const field of fields) {
    const fullPath = `${rootPath}.${field.path}`;
    const parts = fullPath.split('.');
    for (let index = rootPath.split('.').length + 1; index < parts.length; index++) {
      const parentPath = parts.slice(0, index).join('.');
      if (!nodes.some(node => node.path === parentPath)) {
        nodes.push({ path: parentPath, label: parentPath.split('.').pop() ?? parentPath, comment: '', type: 'object', editable: true, value: getDeepConfigValue(value, parentPath.slice(rootPath.length + 1).split('.')) ?? {} });
      }
    }
    nodes.push(optimisticFieldNode(rootPath, field, getDeepConfigValue(value, field.path.split('.'))));
  }
  return nodes;
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
    itemFields: field.itemFields,
    uniqueBy: field.uniqueBy
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
  if (field.type === 'json') return {};
  if (field.type === 'list' || field.type === 'stringList' || field.type === 'numberList' || field.type === 'objectList') return [];
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

function useDebouncedValue<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

function useProgressiveCount(total: number, initial: number, batch: number, deps: React.DependencyList): number {
  const [count, setCount] = useState(() => Math.min(total, initial));
  useEffect(() => {
    let cancelled = false;
    setCount(Math.min(total, initial));
    if (total <= initial) return;
    const schedule = typeof window.requestAnimationFrame === 'function'
      ? (callback: () => void) => window.requestAnimationFrame(callback)
      : (callback: () => void) => window.setTimeout(callback, 16);
    const cancel = typeof window.cancelAnimationFrame === 'function'
      ? (id: number) => window.cancelAnimationFrame(id)
      : (id: number) => window.clearTimeout(id);
    let handle = 0;
    const step = () => {
      if (cancelled) return;
      setCount(current => {
        const next = Math.min(total, current + batch);
        if (next < total) handle = schedule(step);
        return next;
      });
    };
    handle = schedule(step);
    return () => {
      cancelled = true;
      if (handle) cancel(handle);
    };
  }, [total, initial, batch, ...deps]);
  return Math.min(Math.max(count, Math.min(total, initial)), total);
}

function useIdleReady(deps: React.DependencyList, delay = 120): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    setReady(false);
    let cancelled = false;
    const win = window as Window & { requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number; cancelIdleCallback?: (id: number) => void };
    const handle = win.requestIdleCallback
      ? win.requestIdleCallback(() => { if (!cancelled) setReady(true); }, { timeout: delay + 180 })
      : window.setTimeout(() => { if (!cancelled) setReady(true); }, delay);
    return () => {
      cancelled = true;
      if (win.cancelIdleCallback && typeof handle === 'number') win.cancelIdleCallback(handle);
      else window.clearTimeout(handle);
    };
  }, deps);
  return ready;
}

function configPreviewData(sourceContent: string, nodes: WebConfigNode[], scope: ConfigDraftScope, drafts: DraftMap): Record<string, unknown> {
  const parsed = parseSafeYaml(sourceContent || '{}');
  let data: Record<string, unknown> = parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  const changedNodes: WebConfigNode[] = [];
  const fallbackNodes: WebConfigNode[] = [];
  for (const node of nodes) {
    if (node.type === 'object') continue;
    fallbackNodes.push(node);
    if (draftKey(scope, node.path) in drafts) changedNodes.push(node);
  }
  const sourceIsUseful = Object.keys(data).length > 0 || sourceContent.trim() === '';
  const nodesToOverlay = sourceIsUseful ? changedNodes : fallbackNodes;
  for (const node of nodesToOverlay) {
    const key = draftKey(scope, node.path);
    const value = key in drafts ? drafts[key] : node.value;
    data = setDeepValue(data, node.path.split('.'), value);
  }
  return data;
}

function configSourcePreview(original: string, scope: ConfigDraftScope, changedNodes: WebConfigNode[], drafts: DraftMap): string {
  if (!changedNodes.length) return original;
  try {
    let data = parseYaml(original || '{}');
    for (const node of changedNodes) {
      data = setDeepValue(data, node.path.split('.'), drafts[draftKey(scope, node.path)]);
    }
    return serializeYaml(data);
  } catch {
    return changedNodes.reduce((content, node) => replacePreviewLine(content, node.path, drafts[draftKey(scope, node.path)]), original);
  }
}

function replacePreviewLine(content: string, path: string, value: unknown): string {
  const lines = content.split('\n');
  const leaf = path.includes('.') ? path.slice(path.lastIndexOf('.') + 1) : path;
  const index = lines.findIndex(line => line.trimStart().startsWith(`${leaf}:`));
  const nextLine = `${index >= 0 ? lines[index].match(/^\s*/)?.[0] ?? '' : ''}${leaf}: ${formatYamlScalarPreview(value)}`;
  if (index >= 0) lines[index] = nextLine;
  else lines.push(nextLine);
  return lines.join('\n');
}

function formatYamlScalarPreview(value: unknown): string {
  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

function configNodeDisplayLabel(scope: ConfigDraftScope, node: WebConfigNode): string {
  return fieldLabel(node.path, { moduleId: scope.moduleId, namespace: scope.moduleId, fallback: getLocale().startsWith('zh') ? node.label : humanizeFieldLabel(node.path) });
}

function scopeFromModuleId(moduleId: string): ConfigDraftScope {
  return { moduleId, fileId: '', filePath: '' };
}

function configNodeDisplayComment(scope: ConfigDraftScope, node: WebConfigNode): string {
  return resolveConfigNodeComment(scope.moduleId, node.path, node.comment);
}

const ConfigNodeTree = memo(function ConfigNodeTree({ scope, nodes, drafts, setDraftValue, onCreateChild, onDeleteObject, sourceEdit, deletedPaths }: { scope: ConfigDraftScope; nodes: WebConfigNode[]; drafts: DraftMap; setDraftValue: DraftValueSetter; onCreateChild: (node: WebConfigNode) => void; onDeleteObject: (node: WebConfigNode) => void; sourceEdit?: SourceEditController; deletedPaths?: Set<string> }) {
  const nodeIndex = useMemo(() => buildNodeIndex(nodes), [nodes]);
  const scopeDraftKey = useMemo(() => draftSignatureForScope(drafts, scope), [drafts, scope.moduleId, scope.fileId, scope.filePath]);
  const changeState = useMemo(() => buildNodeChangeState(scope, nodes, drafts, sourceEdit?.paths, deletedPaths), [scope.moduleId, scope.fileId, scope.filePath, nodes, scopeDraftKey, sourceEdit?.paths, deletedPaths]);
  const groups = nodeIndex.groupsByParent.get('') ?? [];
  const visibleCount = useProgressiveCount(groups.length, CONFIG_INITIAL_GROUPS, CONFIG_GROUP_BATCH_SIZE, [scope.moduleId, scope.fileId, scope.filePath, groups.length]);
  const visibleGroups = groups.slice(0, visibleCount);

  if (!nodes.length) return <div className="script-placeholder" role="status">{t('core.empty.noConfigNodes')}</div>;

  return <div className="node-grid">{visibleGroups.map((group, index) => {
    if (group.type === 'leaf') {
      return <ConfigNodeView key={group.node.path} scope={scope} node={group.node} drafts={drafts} setDraftValue={setDraftValue} sourceEdit={sourceEdit} changed={changeState.changedPaths.has(group.node.path)} deletable={false} onDeleteObject={onDeleteObject} />;
    }
    return <ConfigNodeSection key={group.node.path} scope={scope} node={group.node} nodeIndex={nodeIndex} changeState={changeState} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={onCreateChild} onDeleteObject={onDeleteObject} sourceEdit={sourceEdit} deletable={false} depth={0} isLast={index === groups.length - 1} />;
  })}</div>;
});

type NodeGroup = { type: 'section'; node: WebConfigNode; children: WebConfigNode[] } | { type: 'leaf'; node: WebConfigNode };
type ConfigNodeIndex = { groupsByParent: Map<string, NodeGroup[]>; descendantsByPath: Map<string, WebConfigNode[]> };
type ConfigNodeChangeState = { changedPaths: Set<string>; descendantCounts: Map<string, number> };

function buildNodeIndex(nodes: WebConfigNode[]): ConfigNodeIndex {
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

function buildNodeChangeState(scope: ConfigDraftScope, nodes: WebConfigNode[], drafts: DraftMap, sourceEditedPaths?: Set<string>, deletedPaths?: Set<string>): ConfigNodeChangeState {
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

function configSectionHasMeaningfulValue(node: WebConfigNode, nodeIndex: ConfigNodeIndex): boolean {
  if (hasMeaningfulConfigValue(node.value)) return true;
  return (nodeIndex.descendantsByPath.get(node.path) ?? []).some(descendant => hasMeaningfulConfigValue(descendant.value));
}

function hasMeaningfulConfigValue(value: unknown): boolean {
  if (value === undefined || value === null) return false;
  if (typeof value === 'string') return value.trim().length > 0;
  if (typeof value === 'number' || typeof value === 'boolean') return true;
  if (Array.isArray(value)) return value.some(hasMeaningfulConfigValue);
  if (isPlainObject(value)) return Object.values(value).some(hasMeaningfulConfigValue);
  return true;
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

function configAncestorPaths(path: string): string[] {
  const ancestors: string[] = [];
  let index = path.indexOf('.');
  while (index > 0) {
    ancestors.push(path.slice(0, index));
    index = path.indexOf('.', index + 1);
  }
  return ancestors;
}

const ConfigNodeSection = memo(function ConfigNodeSection({ scope, node, nodeIndex, changeState, drafts, setDraftValue, onCreateChild, onDeleteObject, sourceEdit, deletable, depth = 0, isLast = true, branch }: { scope: ConfigDraftScope; node: WebConfigNode; nodeIndex: ConfigNodeIndex; changeState: ConfigNodeChangeState; drafts: DraftMap; setDraftValue: DraftValueSetter; onCreateChild: (node: WebConfigNode) => void; onDeleteObject: (node: WebConfigNode) => void; sourceEdit?: SourceEditController; deletable: boolean; depth?: number; isLast?: boolean; branch?: 'tee' | 'elbow' }) {
  const groups = nodeIndex.groupsByParent.get(node.path) ?? [];
  const sectionChanged = changeState.changedPaths.has(node.path);
  const changedInGroup = changeState.descendantCounts.get(node.path) ?? 0;
  const defaultCollapsed = changedInGroup === 0 && !sectionChanged && (!configSectionHasMeaningfulValue(node, nodeIndex) || groups.length > CONFIG_LAZY_SECTION_THRESHOLD);
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const [shouldRenderBody, setShouldRenderBody] = useState(!defaultCollapsed);
  const bodyTimer = useRef<number | null>(null);
  const visibleCount = useProgressiveCount(shouldRenderBody && !isCollapsed ? groups.length : 0, CONFIG_SECTION_INITIAL_GROUPS, CONFIG_SECTION_GROUP_BATCH_SIZE, [scope.moduleId, scope.fileId, scope.filePath, node.path, shouldRenderBody, isCollapsed, groups.length]);
  const visibleGroups = groups.slice(0, visibleCount);
  const hasSiblingBranches = groups.length > 1;
  const groupLabel = configNodeDisplayLabel(scope, node);

  useEffect(() => () => {
    if (bodyTimer.current !== null) window.clearTimeout(bodyTimer.current);
  }, []);

  useEffect(() => {
    if (bodyTimer.current !== null) {
      window.clearTimeout(bodyTimer.current);
      bodyTimer.current = null;
    }
    setIsCollapsed(defaultCollapsed);
    setShouldRenderBody(!defaultCollapsed);
  }, [scope.moduleId, scope.fileId, scope.filePath, node.path, defaultCollapsed]);

  const toggleSection = () => {
    if (bodyTimer.current !== null) window.clearTimeout(bodyTimer.current);
    if (isCollapsed) {
      setIsCollapsed(false);
      bodyTimer.current = window.setTimeout(() => {
        bodyTimer.current = null;
        startTransition(() => setShouldRenderBody(true));
      }, 35);
    } else {
      setIsCollapsed(true);
      bodyTimer.current = window.setTimeout(() => {
        bodyTimer.current = null;
        startTransition(() => setShouldRenderBody(false));
      }, 120);
    }
  };

  return <div className={`node-section ${isCollapsed ? 'collapsed' : 'expanded'}${depth > 0 ? ' node-section--nested' : ''}`} data-node-depth={depth}>
    <div className={`node-section-header ${isCollapsed ? 'collapsed' : ''} ${sectionChanged ? 'changed' : ''}`}>
      {branch && <IndentGuide branch={branch} />}
      <button type="button" className="node-section-toggle" onClick={toggleSection} aria-expanded={!isCollapsed}>
        <DisclosureChevron open={!isCollapsed} className="section-arrow" />
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
    {shouldRenderBody && <div className="node-section-body" hidden={isCollapsed}>{visibleGroups.map((group, index) => {
      const absoluteIndex = index;
      const branch = absoluteIndex === groups.length - 1 ? 'elbow' : 'tee';
      return <div className="node-section-child" key={group.node.path} style={{ '--config-child-depth': depth + 1 } as CSSProperties}>
        {group.type === 'section'
          ? <ConfigNodeSection scope={scope} node={group.node} nodeIndex={nodeIndex} changeState={changeState} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={onCreateChild} onDeleteObject={onDeleteObject} sourceEdit={sourceEdit} deletable={node.creatableChildren === true} depth={depth + 1} isLast={absoluteIndex === groups.length - 1} branch={hasSiblingBranches ? branch : undefined} />
          : <ConfigNodeView scope={scope} node={group.node} drafts={drafts} setDraftValue={setDraftValue} sourceEdit={sourceEdit} changed={changeState.changedPaths.has(group.node.path)} deletable={node.creatableChildren === true} onDeleteObject={onDeleteObject} branch={hasSiblingBranches ? branch : undefined} />}
      </div>;
    })}</div>}
  </div>;
});

function IndentGuide({ branch }: { branch: 'tee' | 'elbow' }) {
  return <svg className={`indent-guide indent-guide--${branch}`} viewBox="0 0 16 20" aria-hidden="true" focusable="false">
    <path d={branch === 'tee' ? 'M6 0v20M6 10h8' : 'M6 0v10h8'} />
  </svg>;
}

function ConfigNodeView({ scope, node, drafts, setDraftValue, sourceEdit, changed = false, deletable = false, onDeleteObject, branch }: { scope: ConfigDraftScope; node: WebConfigNode; drafts: DraftMap; setDraftValue: DraftValueSetter; sourceEdit?: SourceEditController; changed?: boolean; deletable?: boolean; onDeleteObject?: (node: WebConfigNode) => void; branch?: 'tee' | 'elbow' }) {
  const key = draftKey(scope, node.path);
  const sourceEdited = sourceEdit?.paths.has(node.path) === true;
  const committedValue = key in drafts ? drafts[key] : node.value;
  const [localValue, setLocalValue] = useState(committedValue);
  const commitTimer = useRef<number | null>(null);
  const pendingValue = useRef<unknown>(committedValue);

  useEffect(() => {
    if (commitTimer.current !== null) return;
    pendingValue.current = committedValue;
    setLocalValue(committedValue);
  }, [committedValue]);

  useEffect(() => () => {
    if (commitTimer.current !== null) window.clearTimeout(commitTimer.current);
  }, []);

  const commitValue = (next: unknown) => {
    startTransition(() => {
      if (valuesEqual(next, node.value)) {
        setDraftValue(scope, node, node.value);
        return;
      }
      if (sourceEdited) sourceEdit?.update(node, next);
      else setDraftValue(scope, node, next);
    });
  };

  const setValue = (next: unknown) => {
    pendingValue.current = next;
    setLocalValue(next);
    if (commitTimer.current !== null) window.clearTimeout(commitTimer.current);
    commitTimer.current = window.setTimeout(() => {
      commitTimer.current = null;
      commitValue(pendingValue.current);
    }, 90);
  };
  const isWide = isWideConfigNodeType(node.type);
  const label = configNodeDisplayLabel(scope, node);
  return <div className={`node ${changed || sourceEdited ? 'changed' : ''} ${isWide ? 'node-wide' : ''}`}>{branch && <IndentGuide branch={branch} />}<div className="node-meta"><strong>{label}</strong><code>{node.path}</code><p>{configNodeDisplayComment(scope, node)}</p></div><div className="node-control">{renderControl(node, localValue, setValue, label, scope.moduleId)}{deletable && onDeleteObject && <button type="button" className="node-section-delete" onClick={() => onDeleteObject(node)}>{t('core.config.delete')}</button>}</div></div>;
}

function isWideConfigNodeType(type: string | undefined): boolean {
  return type === 'dynamic_map' || type === 'list' || type === 'stringList' || type === 'numberList' || type === 'objectList' || type === 'object';
}

function renderControl(node: WebConfigNode, value: unknown, setValue: (v: unknown) => void, label: string, moduleId: string) {
  if (node.type === 'boolean') return <BooleanSwitch checked={value === true} label={`${label}: ${value ? t('core.config.booleanOn') : t('core.config.booleanOff')}`} onToggle={() => setValue(!value)} />;
  if (node.type === 'enum' && node.options) return <select value={str(value)} aria-label={label} onChange={(e) => setValue(e.target.value)}>{node.options.map(opt => <option key={opt} value={opt}>{optionLabel(node.optionLabelPrefix || node.path, opt, { moduleId })}</option>)}</select>;
  if (node.type === 'number') return <NumberField value={value} onChange={setValue} ariaLabel={label} />;
  if (node.type === 'json') return <JsonField value={value} onChange={setValue} ariaLabel={label} />;
  if (node.type === 'dynamic_map') return <DynamicMapEditor value={value} setValue={setValue} />;
  if (node.type === 'object') {
    if (node.itemFields?.length) return <SchemaObjectEditor field={configNodeToSchemaField(node)} value={value} onChange={setValue} moduleId={moduleId} ariaLabel={label} />;
    return <ObjectMapEditor value={value} onChange={setValue} />;
  }
  if (node.type === 'stringList') return <StringListEditor items={asStringListValue(value)} onChange={setValue} />;
  if (node.type === 'numberList') return <NumberListEditor items={asNumberListValue(value)} onChange={setValue} />;
  if (node.type === 'objectList') {
    const items = Array.isArray(value) ? value : [];
    return <ObjectListEditor node={node} items={items} setValue={setValue} moduleId={moduleId} />;
  }
  if (node.type === 'list') {
    const items = Array.isArray(value) ? value : [];
    const hasObjectSchema = Boolean(node.itemFields?.length) && !node.itemFields?.every(field => field.path === 'value' && field.type === 'text');
    const hasObjectItems = items.some(isPlainObject) || hasObjectSchema;
    if (hasObjectItems) return <ObjectListEditor node={node} items={items} setValue={setValue} moduleId={moduleId} />;
    const update = (i: number, v: string) => setValue(items.map((x, j) => j === i ? parseListValue(x, v) : x));
    return <div className="list-editor">{items.map((item, i) => <div className="list-row" key={i}><input value={str(item)} onChange={(e) => update(i, e.target.value)} aria-label={t('core.config.itemIndex', { index: i + 1 })} /><button type="button" onClick={() => setValue(items.filter((_, j) => j !== i))} aria-label={t('core.config.deleteItem', { index: i + 1 })}>{t('core.config.delete')}</button></div>)}<button type="button" className="add-row" onClick={() => setValue([...items, ''])}>{t('core.config.addItem')}</button></div>;
  }
  return <input aria-label={label} value={str(value)} onChange={(e) => setValue(e.target.value)} />;
}

function BooleanSwitch({ checked, label, onToggle }: { checked: boolean; label: string; onToggle: () => void }) {
  return <button type="button" className={`switch ${checked ? 'on' : ''}`} aria-pressed={checked} aria-label={label} onClick={onToggle}>
    <span className="switch-icon" aria-hidden="true">
      {checked ? <svg viewBox="0 0 16 16" focusable="false"><path d="M3.5 8.2 6.7 11.2 12.8 4.8" /></svg> : <svg viewBox="0 0 16 16" focusable="false"><path d="M4.7 4.7 11.3 11.3M11.3 4.7 4.7 11.3" /></svg>}
    </span>
    {checked ? t('core.config.booleanOn') : t('core.config.booleanOff')}
  </button>;
}

function ObjectValuePreview({ value }: { value: unknown }) {
  const size = isPlainObject(value) ? Object.keys(value).length : 0;
  return <div className="object-value-preview" aria-label={t('core.config.objectPreview', { count: size }, '{count} nested fields')}>
    <code>{'{}'}</code>
    <span>{t('core.config.groupItems', { count: size })}</span>
  </div>;
}

function ObjectMapEditor({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  const entries = Object.entries(isPlainObject(value) ? value : {});
  const updateKey = (index: number, nextKey: string) => {
    const next: Record<string, unknown> = {};
    entries.forEach(([key, entry], itemIndex) => {
      next[itemIndex === index ? nextKey : key] = entry;
    });
    onChange(next);
  };
  const updateValue = (index: number, nextValue: string) => {
    const next: Record<string, unknown> = {};
    entries.forEach(([key, entry], itemIndex) => {
      next[key] = itemIndex === index ? parseListValue(entry, nextValue) : entry;
    });
    onChange(next);
  };
  const remove = (index: number) => onChange(Object.fromEntries(entries.filter((_, itemIndex) => itemIndex !== index)));
  const add = () => {
    const keys = entries.map(([key]) => key);
    let index = 1;
    while (keys.includes(`key_${index}`)) index += 1;
    onChange({ ...(isPlainObject(value) ? value : {}), [`key_${index}`]: '' });
  };
  return <div className="dynamic-map-editor">
    {entries.map(([key, entry], index) => <div className="dynamic-map-row" key={`${key}:${index}`}>
      <input value={key} onChange={event => updateKey(index, event.target.value)} aria-label={t('core.kv.key')} />
      <input value={entry == null || isPlainObject(entry) || Array.isArray(entry) ? JSON.stringify(entry ?? '') : String(entry)} onChange={event => updateValue(index, event.target.value)} aria-label={t('core.kv.value')} />
      <button type="button" onClick={() => remove(index)}>{t('core.config.delete')}</button>
    </div>)}
    <button type="button" className="add-row" onClick={add}>{t('core.config.create')}</button>
  </div>;
}

function ObjectListEditor({ node, items, setValue, moduleId, compact = false }: { node: WebConfigNode; items: unknown[]; setValue: (v: unknown) => void; moduleId: string; compact?: boolean }) {
  const objectItems: Record<string, unknown>[] = items.map(item => isPlainObject(item) ? item : {});
  const stableRef = useStableEntries(objectItems);
  const stable = stableRef.current;
  const keys = objectListKeys(node, objectItems);
  const defaultCollapsedRows = stable.filter(entry => stable.length > OBJECT_LIST_COLLAPSE_THRESHOLD || keys.length > CONFIG_LAZY_SECTION_THRESHOLD || !hasMeaningfulConfigValue(entry.data)).map(entry => entry._id);
  const largeList = stable.length > OBJECT_LIST_COLLAPSE_THRESHOLD;
  const [collapsed, setCollapsed] = useState<Set<number>>(() => new Set(defaultCollapsedRows));
  const [emptyExpanded, setEmptyExpanded] = useState(false);
  const visibleCount = useProgressiveCount(stable.length, OBJECT_LIST_INITIAL_ROWS, OBJECT_LIST_ROW_BATCH_SIZE, [node.path, stable.length]);
  const visibleStable = stable.slice(0, visibleCount);
  const duplicateValues = duplicateUniqueValues(node, objectItems);

  function updateField(index: number, key: string, nextValue: unknown) {
    setValue(items.map((item, itemIndex) => itemIndex === index ? { ...(isPlainObject(item) ? item : {}), [key]: nextValue } : item));
  }

  function removeEntry(index: number) {
    stableRef.current.splice(index, 1);
    setValue(items.filter((_, itemIndex) => itemIndex !== index));
  }

  function toggleEntry(rowId: number) {
    setCollapsed(current => {
      const next = new Set(current);
      if (next.has(rowId)) next.delete(rowId);
      else next.add(rowId);
      return next;
    });
  }

  function addEntry() {
    setEmptyExpanded(true);
    setValue([...items, objectListTemplate({ ...node, value: items }, objectItems[0])]);
  }

  if (!stable.length) return <div className={`object-list-editor object-list-editor--empty${compact ? ' object-list-editor--compact' : ''}`}>
    <button type="button" className={`object-list-empty ${emptyExpanded ? 'expanded' : ''}`} onClick={() => setEmptyExpanded(current => !current)} aria-expanded={emptyExpanded}>
      <DisclosureChevron open={emptyExpanded} className="object-list-arrow" />
      <strong>{t('core.config.emptyListTitle')}</strong>
      <span>{configNodeDisplayLabel(scopeFromModuleId(moduleId), node)}</span>
    </button>
    {emptyExpanded && <div className="object-list-empty-body"><p>{t('core.config.emptyListHint')}</p><button type="button" className="add-row" onClick={addEntry}>{t('core.config.addItem')}</button></div>}
    {!emptyExpanded && <button type="button" className="add-row" onClick={addEntry}>{t('core.config.addItem')}</button>}
  </div>;

  return <div className={`object-list-editor${compact ? ' object-list-editor--compact' : ''}${largeList ? ' object-list-editor--large' : ''}`}>
    {largeList && <div className="object-list-scale-hint">{configInlineText(`大型列表：已默认折叠 ${stable.length} 项，展开单项后编辑。`, `Large list: ${stable.length} items are collapsed by default. Expand one item to edit.`)}</div>}
    {visibleStable.map((entry, index) => {
      const item = entry.data;
      const rowId = entry._id;
      const expanded = !collapsed.has(rowId);
      const summary = objectListSummary(node, item, index);
      const duplicate = isDuplicateUniqueValue(node, item, duplicateValues);
      return <div className={`object-list-entry ${expanded ? 'expanded' : 'collapsed'} ${duplicate ? 'duplicate' : ''}`} key={rowId}>
        <div className="object-list-head">
          <button type="button" className="object-list-toggle" onClick={() => toggleEntry(rowId)} aria-expanded={expanded}>
            <DisclosureChevron open={expanded} className="object-list-arrow" />
            <strong>#{index + 1}</strong>
            <code>{summary}</code>
            {duplicate && <span className="object-list-badge">{configInlineText('重复', 'Duplicate')}</span>}
          </button>
          <button type="button" className="object-list-remove" onClick={() => removeEntry(index)} aria-label={t('core.config.deleteItem', { index: index + 1 })}>{t('core.config.delete')}</button>
        </div>
        {expanded && <div className="object-list-fields">
          {keys.map(key => {
            const field = fieldSchemaForKey(node, key);
            const wide = isListSchemaField(field, item[key]);
            return <div className={`object-list-field ${wide ? 'object-list-field--wide' : ''}`} key={key}>
              <label>{fieldLabel(`${node.path}.${key}`, { moduleId, namespace: moduleId, fallback: getLocale().startsWith('zh') ? (field?.label || key.replace(/_/g, ' ')) : humanizeFieldLabel(key) })}</label>
              {renderSchemaField(field, item[key], next => updateField(index, key, next), moduleId, `${node.path}.${index}.${key}`, objectItems, index)}
            </div>;
          })}
        </div>}
      </div>;
    })}
    <button type="button" className="add-row" onClick={addEntry}>{t('core.config.addItem')}</button>
  </div>;
}

function configNodeToSchemaField(node: WebConfigNode): WebConfigFieldSchema {
  return {
    path: node.path,
    label: node.label,
    comment: node.comment,
    type: node.type,
    options: node.options,
    optionLabelPrefix: node.optionLabelPrefix,
    itemFields: node.itemFields,
    uniqueBy: node.uniqueBy
  };
}

function renderSchemaField(field: WebConfigFieldSchema | undefined, value: unknown, onChange: (value: unknown) => void, moduleId: string, ariaLabel: string, siblingItems: Record<string, unknown>[] = [], currentIndex = -1) {
  const type = field?.type;
  if (type === 'boolean' || typeof value === 'boolean') return <BooleanSwitch checked={value === true} label={ariaLabel} onToggle={() => onChange(!value)} />;
  if (type === 'number' || typeof value === 'number') return <NumberField value={value} onChange={onChange} ariaLabel={ariaLabel} />;
  if (type === 'list' || type === 'stringList') return <StringListEditor items={asStringListValue(value)} onChange={onChange} />;
  if (type === 'numberList') return <NumberListEditor items={asNumberListValue(value)} onChange={onChange} />;
  if (type === 'objectList') {
    const childNode = schemaFieldToConfigNode(field, ariaLabel, value, siblingItems);
    return <ObjectListEditor node={childNode} items={Array.isArray(value) ? value : []} setValue={onChange} moduleId={moduleId} compact />;
  }
  if (type === 'object' && field?.itemFields?.length) {
    return <SchemaObjectEditor field={field} value={value} onChange={onChange} moduleId={moduleId} ariaLabel={ariaLabel} />;
  }
  if (type === 'json') return <JsonField value={value} onChange={onChange} ariaLabel={ariaLabel} />;
  if (type === 'enum' && field?.options) {
    const current = str(value);
    return <select aria-label={ariaLabel} value={current} onChange={(e) => onChange(e.target.value)}>{field.options.map(option => <option key={option} value={option}>{optionLabel(field.optionLabelPrefix || field.path, option, { moduleId })}</option>)}</select>;
  }
  return <input aria-label={ariaLabel} value={value == null ? '' : String(value)} onChange={(e) => onChange(e.target.value)} />;
}

function SchemaObjectEditor({ field, value, onChange, moduleId, ariaLabel }: { field: WebConfigFieldSchema; value: unknown; onChange: (value: unknown) => void; moduleId: string; ariaLabel: string }) {
  const record = isPlainObject(value) ? value : {};
  const keys = mergeKeys(field.itemFields?.map(entry => entry.path) ?? [], Object.keys(record));
  function updateField(key: string, nextValue: unknown) {
    onChange({ ...record, [key]: nextValue });
  }
  return <div className="schema-object-editor" aria-label={ariaLabel}>
    {keys.map(key => {
      const childField = field.itemFields?.find(entry => entry.path === key) ?? { path: key, label: key, type: inferConfigFieldType(record[key]) };
      const wide = isListSchemaField(childField, record[key]) || childField.type === 'object' || childField.type === 'json';
      return <div className={`object-list-field ${wide ? 'object-list-field--wide' : ''}`} key={key}>
        <label>{fieldLabel(`${field.path}.${key}`, { moduleId, namespace: moduleId, fallback: getLocale().startsWith('zh') ? (childField.label || key.replace(/_/g, ' ')) : humanizeFieldLabel(key) })}</label>
        {renderSchemaField(childField, record[key], next => updateField(key, next), moduleId, `${ariaLabel}.${key}`)}
      </div>;
    })}
  </div>;
}

function cleanObject(value: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(Array.isArray(entry) && entry.length === 0)));
}

function schemaFieldToConfigNode(field: WebConfigFieldSchema | undefined, path: string, value: unknown, siblingItems: Record<string, unknown>[]): WebConfigNode {
  return {
    path: field?.path || path,
    label: field?.label || field?.path || path,
    comment: field?.comment || '',
    type: 'objectList',
    editable: true,
    value: Array.isArray(value) ? value : siblingItems,
    options: field?.options,
    optionLabelPrefix: field?.optionLabelPrefix,
    itemFields: field?.itemFields,
    uniqueBy: field?.uniqueBy
  };
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
    const next = field.options.find(option => !used.has(option)) ?? field.options[0] ?? '';
    return field.defaultValue !== undefined ? resolveUniqueListDefault(node, field, field.defaultValue) : next;
  }
  const baseValue = field.defaultValue !== undefined ? field.defaultValue : (field.type ? defaultSchemaFieldValue(field) : defaultObjectListValue(sample?.[field.path]));
  return resolveUniqueListDefault(node, field, baseValue);
}

function defaultObjectListValue(sample: unknown) {
  if (Array.isArray(sample)) return [];
  if (typeof sample === 'number') return 0;
  if (typeof sample === 'boolean') return false;
  return '';
}

function duplicateUniqueValues(node: WebConfigNode, items: Record<string, unknown>[]): Set<string> {
  const uniqueField = uniqueListField(node);
  if (!uniqueField) return new Set();
  const counts = new Map<string, number>();
  for (const item of items) {
    const value = String(item[uniqueField] ?? '');
    if (!value) continue;
    counts.set(value, (counts.get(value) ?? 0) + 1);
  }
  return new Set([...counts.entries()].filter(([, count]) => count > 1).map(([value]) => value));
}

function objectListSummary(node: WebConfigNode, item: Record<string, unknown>, index: number): string {
  const uniqueField = uniqueListField(node);
  if (uniqueField) return String(item[uniqueField] ?? `${index + 1}`);
  const entries = Object.entries(item).slice(0, 2).map(([key, value]) => `${key}: ${String(value ?? '')}`);
  return entries.length ? entries.join(' · ') : `#${index + 1}`;
}

function isDuplicateUniqueValue(node: WebConfigNode, item: Record<string, unknown>, duplicates: Set<string>): boolean {
  const uniqueField = uniqueListField(node);
  if (!uniqueField) return false;
  return duplicates.has(String(item[uniqueField] ?? ''));
}

function configInlineText(zh: string, en: string): string {
  return getLocale().startsWith('zh') ? zh : en;
}

function isListSchemaField(field: WebConfigFieldSchema | undefined, value: unknown): boolean {
  return field?.type === 'list' || field?.type === 'stringList' || field?.type === 'numberList' || field?.type === 'objectList' || Array.isArray(value);
}

function asStringListValue(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(item => item == null ? '' : String(item));
  if (value == null) return [];
  return [String(value)];
}

function asNumberListValue(value: unknown): number[] {
  if (!Array.isArray(value)) return [];
  return value.map(item => Number(item)).filter(item => Number.isFinite(item));
}

function resolveUniqueListDefault(node: WebConfigNode, field: WebConfigFieldSchema, value: unknown): unknown {
  const uniqueField = uniqueListField(node);
  if (!uniqueField || field.path !== uniqueField || typeof value !== 'string') return value;
  const used = new Set((Array.isArray(node.value) ? node.value : []).map(item => isPlainObject(item) ? String(item[uniqueField] ?? '') : '').filter(Boolean));
  if (!used.has(value)) return value;
  let index = 1;
  while (used.has(`${value}_${index}`)) index += 1;
  return `${value}_${index}`;
}

function uniqueListField(node: WebConfigNode): string | null {
  return node.uniqueBy || node.itemFields?.find(field => Boolean((field as any).unique))?.path || null;
}

function JsonField({ value, onChange, ariaLabel }: { value: unknown; onChange: (value: unknown) => void; ariaLabel: string }) {
  const [text, setText] = useState(() => formatJsonFieldValue(value));
  const [error, setError] = useState('');

  useEffect(() => { setText(formatJsonFieldValue(value)); setError(''); }, [value]);

  function handleChange(nextText: string) {
    setText(nextText);
    if (!nextText.trim()) {
      setError('');
      onChange(undefined);
      return;
    }
    try {
      const parsed = JSON.parse(nextText);
      setError('');
      onChange(parsed);
    } catch {
      setError(configInlineText('JSON 格式无效，修正后才会写入。', 'Invalid JSON; fix it before saving.'));
    }
  }

  return <div className="json-field"><textarea aria-label={ariaLabel} value={text} rows={Math.min(12, Math.max(4, text.split('\n').length))} onChange={(e) => handleChange(e.target.value)} aria-invalid={error ? 'true' : undefined} /><small className="field-error" role="alert">{error}</small></div>;
}

function formatJsonFieldValue(value: unknown): string {
  if (value === undefined || value === null || value === '') return '';
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function NumberField({ value, onChange, ariaLabel }: { value: unknown; onChange: (value: unknown) => void; ariaLabel: string }) {
  const [text, setText] = useState(() => numberFieldText(value));
  const [error, setError] = useState('');

  useEffect(() => { setText(numberFieldText(value)); setError(''); }, [value]);

  function handleChange(nextText: string) {
    setText(nextText);
    if (nextText.trim() === '') {
      setError('');
      onChange(undefined);
      return;
    }
    const parsed = Number(nextText);
    if (!Number.isFinite(parsed)) {
      setError(t('core.config.numberInvalid'));
      return;
    }
    setError('');
    onChange(parsed);
  }

  return <div className="number-field"><input type="text" inputMode="decimal" aria-label={ariaLabel} value={text} onChange={(e) => handleChange(e.target.value)} aria-invalid={error ? 'true' : undefined} /><small className="field-error" role="alert">{error}</small></div>;
}

function numberFieldText(value: unknown): string {
  return typeof value === 'number' && Number.isFinite(value) ? String(value) : '';
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
  }, [fileName, fileTitle, scriptPath, isDirty, content, error, saving, loading, history.undo.length, history.redo.length]);

  useEffect(() => () => setSurfaceToolbar(null), [setSurfaceToolbar]);

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

function draftSignatureForScope(drafts: DraftMap, scope: ConfigDraftScope): string {
  const prefix = draftScopePrefix(scope);
  let signature = '';
  for (const key of Object.keys(drafts)) {
    if (!key.startsWith(prefix)) continue;
    signature += `${key}=${String(drafts[key])}\u001f`;
  }
  return signature;
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
function isNumericInputValue(value: unknown): boolean { return value == null || value === '' || (typeof value === 'number' && Number.isFinite(value)); }
function numberInputValue(value: unknown): string { return typeof value === 'number' && Number.isFinite(value) ? String(value) : ''; }
function parseNumberInputValue(value: string): number | undefined { if (value === '') return undefined; const parsed = Number(value); return Number.isFinite(parsed) ? parsed : undefined; }
function str(v: unknown): string { if (v == null) return ''; if (typeof v === 'object') try { return JSON.stringify(v, null, 2); } catch { return ''; } return String(v); }
function enhanceRegistry(registry: WebRegistry): WebRegistry { return applyConfigRegistryOverrides(applyEditorDescriptorOverrides(registry)); }
function firstSelection(r: WebRegistry): Selection | null { const m = r.modules[0]; return m?.files[0] ? { moduleId: m.id, fileId: m.files[0].id } : null; }
function selectionExists(registry: WebRegistry, selection: Selection): boolean {
  const module = registry.modules.find(entry => entry.id === selection.moduleId);
  const file = module?.files.find(entry => entry.id === selection.fileId);
  if (!file) return false;
  if (!selection.scriptPath) return true;
  return Boolean(file.children?.some(child => (child.fullPath ?? child.relativePath) === selection.scriptPath || child.relativePath === selection.scriptPath));
}
function pendingExtensionModuleIds(extensions: WebConsoleExtension[] | undefined, statuses: WebConsoleExtensionStatus[], health: 'idle' | 'loading' | 'ok' | 'failed'): Set<string> {
  if (health !== 'loading' || !extensions?.length) return new Set();
  const settled = new Set(statuses.map(status => status.url));
  return new Set(extensions.filter(extension => !settled.has(extension.url)).map(extension => extension.moduleId));
}
function extensionSurfacePending(module: WebRegistryModule, file: WebRegistryFile, editor: WebEditorDescriptor | undefined, registeredSurface: ReturnType<typeof getSurface>, pendingModules: ReadonlySet<string>): boolean {
  if (!pendingModules.has(module.id) || isKind(file.kind, 'CONFIG') || isKind(file.kind, 'SCRIPT')) return false;
  if (!file.editorId) return !registeredSurface;
  return !editor;
}
