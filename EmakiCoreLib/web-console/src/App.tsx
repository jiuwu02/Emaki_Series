import { useDeferredValue, useEffect, useMemo, useRef, useState } from 'react';
import type { ComponentType } from 'react';
import { ApiClient } from './api';
import { GuiEditorSurface } from './GuiEditorSurface';
import { ItemEditorSurface } from './ItemEditorSurface';
import { loadWebExtensions } from './extensions';
import { applyEditorDescriptorOverrides, getSourceDocumentAdapter, getSurface, isKind, registerSourceDocumentAdapter, registerSurface } from './registry';
import { getLocale, getRegisteredLocales, setLocale, t } from './i18n';
import { ActionGroup, Button, EditorChrome, InlineError, ToastNotice, type EditorChange } from './components';
import { I18nBundleModal, type I18nTarget } from './I18nBundleModal';
import { highlightJS } from './lib/highlight';
import { fieldLabel, valuesEqual } from './lib';
import { Login, ResizableRail, WorkspaceTree, fileKindLabel } from './shell';
import type { SurfaceProps, SurfaceToolbarState } from './registry';
import type { WebConfigNode, WebRegistry, WebRegistryFile, WebRegistryModule } from './types';

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
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [surfaceToolbar, setSurfaceToolbar] = useState<SurfaceToolbarState | null>(null);

  const api = useMemo(() => new ApiClient(token, () => {
    sessionStorage.removeItem('emaki-web-token');
    setSessionExpired(true);
    setToken(null);
  }), [token]);

  useEffect(() => { if (token) void loadRegistry({ initial: true, clearDrafts: true }); }, [token]);
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
      const extensionStatuses = await loadWebExtensions(next.extensions);
      const failedExtensions = extensionStatuses.filter(status => status.status === 'failed');
      const merged = applyEditorDescriptorOverrides(next);
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
    if (!selectedModule || !selectedFile) return;
    const scope = configDraftScope(selectedModule, selectedFile, selected?.scriptPath);
    const changes = selectedFile.nodes.filter((n) => n.type !== 'object' && draftKey(scope, n.path) in drafts);
    if (!changes.length) {
      setToast({ tone: 'ok', text: t('core.toast.noChanges') });
      return;
    }
    setSaving(true);
    try {
      let revision = selectedFile.revision;
      for (const node of changes) {
        const result = await api.saveRegistryValue(selectedModule.id, scope.filePath, node.path, drafts[draftKey(scope, node.path)], revision);
        revision = result.revision ?? revision;
      }
      clearDraftValues(scope);
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: changes.length }) });
      await loadRegistry({ clearDrafts: false, announceRefresh: false });
    } catch (err) {
      setToast({ tone: 'bad', text: userFacingSaveError(err) });
    } finally {
      setSaving(false);
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
  const fallbackToolbar: SurfaceToolbarState = {
    title: selectedModule ? selectedModule.name : t('core.stage.defaultTitle'),
    subtitle: selectedFile ? `${selectedFile.title}，${selectedFile.path}` : t('core.stage.defaultHint'),
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
  const locales = getRegisteredLocales();
  const currentLocale = getLocale();
  const currentLocaleLabel = localeLabel(currentLocale);
  const changeLocale = (next: string) => { setLocale(next); setLocaleVersion((version) => version + 1); };

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
      {i18nTarget && <I18nBundleModal target={i18nTarget} onClose={() => setI18nTarget(null)} onSaved={() => { setLocaleVersion((version) => version + 1); setToast({ tone: 'ok', text: t('core.i18n.saved') }); }} />}
      <ResizableRail>
        <div className="brand-block">
          <div className="brand-main"><span className="sigil"><EmakiMark /></span><div><strong>{t('core.brand.name')}</strong><small>{t('core.brand.console')}</small></div></div>
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
        <WorkspaceTree registry={registry} selected={selected} expanded={expanded} dirtyKeys={dirtyTreeKeys} setExpanded={setExpanded} onOpenI18n={setI18nTarget} onSelect={(next) => setSelected((current) => sameSelection(current, next) ? { ...next, refreshKey: (current?.refreshKey ?? 0) + 1 } : next)} />
        <button className="rail-action quiet" onClick={() => { sessionStorage.removeItem('emaki-web-token'); setToken(null); }}>{t('core.auth.logout')}</button>
      </ResizableRail>
      <main className="stage">
        <EditorChrome
          className="stage-head"
          title={toolbar.title ?? (selectedModule ? selectedModule.name : t('core.stage.defaultTitle'))}
          subtitle={toolbar.subtitle ?? (selectedFile ? `${selectedFile.title}，${selectedFile.path}` : t('core.stage.defaultHint'))}
          dirty={toolbar.dirty}
          changedCount={toolbar.changedCount}
          changes={toolbar.changes ?? []}
          source={toolbar.source ?? ''}
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

  if (isKind(file.kind, 'SCRIPT')) return <section className="config-surface script-surface"><div className="surface-head"><div><h2>{file.title}</h2><p>{file.comment}</p></div><span className="file-kind script">{fileKindLabel(file.kind)}</span></div>{scriptPath ? <ScriptEditor api={api} scriptPath={scriptPath} module={module} file={file} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} /> : <div className="script-placeholder" role="status">{t('core.empty.selectScript')}</div>}</section>;
  // CONFIG 类型：如果有子文件路径，按需加载子文件内容
  if (isKind(file.kind, 'CONFIG') && scriptPath) return <ConfigChildSurface module={module} file={file} childPath={scriptPath} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} refreshKey={refreshKey} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} />;
  // CONFIG 类型 glob 文件无子文件选中时，显示提示
  if (isKind(file.kind, 'CONFIG') && file.children && file.children.length > 0 && file.nodes.length === 0) return <section className="config-surface"><div className="surface-head"><div><h2>{file.title}</h2><p>{file.comment}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><div className="script-placeholder" role="status">{t('core.empty.selectFile')}</div></section>;
  return <ConfigStructuredSurface module={module} file={file} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} refreshKey={refreshKey} onRefreshRegistry={onRefreshRegistry} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} />;
}

function ConfigStructuredSurface({ module, file, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, undoDraftScope, redoDraftScope, api, refreshKey, onRefreshRegistry, setSurfaceToolbar, setToast }: { module: WebRegistryModule; file: WebRegistryFile; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; refreshKey: number; onRefreshRegistry: () => Promise<WebRegistry | null>; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: Toast) => void }) {
  const scope = configDraftScope(module, file);
  const scopeHistory = draftHistory[draftScopeId(scope)] ?? emptyDraftHistory();
  const changedNodes = file.nodes.filter(n => n.type !== 'object' && draftKey(scope, n.path) in drafts);
  const source = useConfigSourceDocument({ module, file, api, refreshKey, setToast });
  const [savingNodes, setSavingNodes] = useState(false);

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

  useEffect(() => {
    setSurfaceToolbar({
      title: module.name,
      subtitle: `${file.title}，${file.path}`,
      dirty: changedNodes.length > 0 || source.dirty,
      changedCount: source.dirty ? Math.max(changedNodes.length, 1) : changedNodes.length,
      changes: source.dirty ? [] : configChanges(scope, file.nodes, drafts),
      source: source.content,
      sourceEditable: true,
      sourceError: source.error,
      sourceLanguage: 'yaml',
      saving: source.saving || savingNodes,
      loading: source.loading,
      canUndo: scopeHistory.undo.length > 0,
      canRedo: scopeHistory.redo.length > 0,
      onUndo: () => undoDraftScope(scope),
      onRedo: () => redoDraftScope(scope),
      onReload: () => void source.reload(),
      onSourceChange: source.update,
      onSave: source.dirty ? () => void source.save(async () => { clearDraftValues(scope); await onRefreshRegistry(); }) : () => void saveNodes()
    });
    return () => setSurfaceToolbar(null);
  }, [module.name, file.title, file.path, changedNodes.length, file.nodes, drafts, source.content, source.dirty, source.error, source.saving, source.loading, savingNodes, scopeHistory.undo.length, scopeHistory.redo.length]);

  return <section className="config-surface"><div className="surface-head"><div><h2>{file.title}</h2><p>{file.comment}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><ConfigNodeTree scope={scope} nodes={file.nodes} drafts={drafts} setDraftValue={setDraftValue} /></section>;
}

function ConfigChildSurface({ module, file, childPath, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, undoDraftScope, redoDraftScope, api, refreshKey, setSurfaceToolbar, setToast }: { module: WebRegistryModule; file: WebRegistryFile; childPath: string; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; refreshKey: number; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: Toast) => void }) {
  const [nodes, setNodes] = useState<WebConfigNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState<number | undefined>(undefined);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setLoading(true);
    setError('');
    setNodes([]);
    setRevision(undefined);
    api.registryFileNodes(module.id, childPath).then(result => {
      setNodes(result.nodes);
      setRevision(result.revision);
    }).catch(err => {
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
    }).finally(() => setLoading(false));
  }, [module.id, childPath, refreshKey]);

  const scope = configDraftScope(module, file, childPath);
  const changedNodes = nodes.filter(n => n.type !== 'object' && draftKey(scope, n.path) in drafts);
  const scopeHistory = draftHistory[draftScopeId(scope)] ?? emptyDraftHistory();
  const source = useConfigSourceDocument({ module, file, childPath, api, refreshKey, setToast });

  async function reloadChildNodes(announce = true) {
    setNodes([]);
    setError('');
    setLoading(true);
    try {
      const refreshed = await api.registryFileNodes(module.id, childPath);
      setNodes(refreshed.nodes);
      setRevision(refreshed.revision);
      clearDraftScope(scope);
      if (announce) setToast({ tone: 'ok', text: t('core.toast.reloaded') });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
    } finally {
      setLoading(false);
    }
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
      setNodes(refreshed.nodes);
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
      subtitle: `${file.title} · ${childPath}`,
      dirty: changedNodes.length > 0 || source.dirty,
      changedCount: source.dirty ? Math.max(changedNodes.length, 1) : changedNodes.length,
      changes: source.dirty ? [] : configChanges(scope, nodes, drafts),
      source: source.content,
      sourceEditable: true,
      sourceError: source.error,
      sourceLanguage: 'yaml',
      saving: saving || source.saving,
      loading: loading || source.loading,
      canUndo: scopeHistory.undo.length > 0,
      canRedo: scopeHistory.redo.length > 0,
      onUndo: () => undoDraftScope(scope),
      onRedo: () => redoDraftScope(scope),
      onReload: () => void (source.dirty ? source.reload() : reloadChildNodes()),
      onSourceChange: source.update,
      onSave: source.dirty ? () => void source.save(async () => { clearDraftValues(scope); await reloadChildNodes(false); }) : () => void saveChild()
    });
    return () => setSurfaceToolbar(null);
  }, [fileName, file.title, childPath, changedNodes.length, nodes, drafts, saving, loading, source.content, source.dirty, source.error, source.saving, source.loading, scopeHistory.undo.length, scopeHistory.redo.length, revision]);

  return <section className="config-surface">
    {loading && <div className="script-loading" role="status">{t('core.state.loading')}</div>}
    {error && <InlineError><span>{error}</span><Button size="sm" onClick={() => void reloadChildNodes()}>{t('core.action.retry')}</Button></InlineError>}
    {!loading && !error && <ConfigNodeTree scope={scope} nodes={nodes} drafts={drafts} setDraftValue={setDraftValue} />}
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
  return fieldLabel(node.path, { moduleId: scope.moduleId, namespace: scope.moduleId, fallback: node.label });
}

function ConfigNodeTree({ scope, nodes, drafts, setDraftValue }: { scope: ConfigDraftScope; nodes: WebConfigNode[]; drafts: DraftMap; setDraftValue: DraftValueSetter }) {
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const toggle = (path: string) => setCollapsed(c => ({ ...c, [path]: !c[path] }));

  // 构建树结构：顶级节点是 path 中不含 "." 的节点，或者 object 节点作为分组
  const groups = buildNodeGroups(nodes);

  if (!nodes.length) return <div className="script-placeholder" role="status">{t('core.empty.noConfigNodes')}</div>;

  return <div className="node-grid">{groups.map(group => {
    if (group.type === 'leaf') {
      return <ConfigNodeView key={group.node.path} scope={scope} node={group.node} drafts={drafts} setDraftValue={setDraftValue} />;
    }
    const isCollapsed = collapsed[group.node.path] === true;
    const childCount = group.children.length;
    const changedInGroup = group.children.filter(n => n.type !== 'object' && draftKey(scope, n.path) in drafts).length;
    return <ConfigNodeSection key={group.node.path} scope={scope} node={group.node} childrenNodes={group.children} drafts={drafts} setDraftValue={setDraftValue} collapsed={collapsed} toggle={toggle} />;
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

function ConfigNodeSection({ scope, node, childrenNodes, drafts, setDraftValue, collapsed, toggle }: { scope: ConfigDraftScope; node: WebConfigNode; childrenNodes: WebConfigNode[]; drafts: DraftMap; setDraftValue: DraftValueSetter; collapsed: Record<string, boolean>; toggle: (path: string) => void }) {
  const isCollapsed = collapsed[node.path] === true;
  const groups = buildNodeGroups(childrenNodes, node.path);
  const changedInGroup = childrenNodes.filter(n => n.type !== 'object' && draftKey(scope, n.path) in drafts).length;
  const groupLabel = configNodeDisplayLabel(scope, node);
  return <div className="node-section">
    <button type="button" className={`node-section-header ${isCollapsed ? 'collapsed' : ''}`} onClick={() => toggle(node.path)} aria-expanded={!isCollapsed}>
      <span className="section-arrow" aria-hidden="true">{isCollapsed ? '›' : '⌄'}</span>
      <strong>{groupLabel}</strong>
      <code>{node.path}</code>
      <span className="section-comment">{node.comment}</span>
      <span className="section-meta">{changedInGroup > 0 && <span className="section-badge">{changedInGroup}</span>}{t('core.config.groupItems', { count: groups.length })}</span>
    </button>
    {!isCollapsed && <div className="node-section-body">{groups.map(group => group.type === 'section'
      ? <ConfigNodeSection key={group.node.path} scope={scope} node={group.node} childrenNodes={group.children} drafts={drafts} setDraftValue={setDraftValue} collapsed={collapsed} toggle={toggle} />
      : <ConfigNodeView key={group.node.path} scope={scope} node={group.node} drafts={drafts} setDraftValue={setDraftValue} />
    )}</div>}
  </div>;
}

function ConfigNodeView({ scope, node, drafts, setDraftValue }: { scope: ConfigDraftScope; node: WebConfigNode; drafts: DraftMap; setDraftValue: DraftValueSetter }) {
  const key = draftKey(scope, node.path);
  const value = key in drafts ? drafts[key] : node.value;
  const setValue = (next: unknown) => setDraftValue(scope, node, next);
  const isWide = node.type === 'dynamic_map' || node.type === 'list';
  const label = configNodeDisplayLabel(scope, node);
  return <div className={`node ${key in drafts ? 'changed' : ''} ${isWide ? 'node-wide' : ''}`}><div className="node-meta"><strong>{label}</strong><code>{node.path}</code><p>{node.comment}</p></div><div className="node-control">{renderControl(node, value, setValue, label)}</div></div>;
}

function renderControl(node: WebConfigNode, value: unknown, setValue: (v: unknown) => void, label: string) {
  if (node.type === 'boolean') return <button type="button" className={`switch ${value ? 'on' : ''}`} aria-pressed={value === true} aria-label={`${label}: ${value ? t('core.config.booleanOn') : t('core.config.booleanOff')}`} onClick={() => setValue(!value)}><span />{value ? t('core.config.booleanOn') : t('core.config.booleanOff')}</button>;
  if (node.type === 'enum' && node.options) return <select value={str(value)} aria-label={label} onChange={(e) => setValue(e.target.value)}>{node.options.map(opt => <option key={opt} value={opt}>{opt}</option>)}</select>;
  if (node.type === 'number') return <input type="number" aria-label={label} value={value == null ? '' : String(value)} onChange={(e) => setValue(e.target.value === '' ? undefined : Number(e.target.value))} />;
  if (node.type === 'dynamic_map') return <DynamicMapEditor value={value} setValue={setValue} />;
  if (node.type === 'list') {
    const items = Array.isArray(value) ? value : [];
    const update = (i: number, v: string) => setValue(items.map((x, j) => j === i ? parseListValue(x, v) : x));
    return <div className="list-editor">{items.map((item, i) => <div className="list-row" key={i}>{isObjectLike(item) ? <textarea value={str(item)} onChange={(e) => update(i, e.target.value)} aria-label={t('core.config.itemIndex', { index: i + 1 })} /> : <input value={str(item)} onChange={(e) => update(i, e.target.value)} aria-label={t('core.config.itemIndex', { index: i + 1 })} />}<button type="button" onClick={() => setValue(items.filter((_, j) => j !== i))} aria-label={t('core.config.deleteItem', { index: i + 1 })}>{t('core.config.delete')}</button></div>)}<button type="button" className="add-row" onClick={() => setValue([...items, ''])}>{t('core.config.addItem')}</button></div>;
  }
  return <input aria-label={label} value={str(value)} onChange={(e) => setValue(e.target.value)} />;
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
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [completions, setCompletions] = useState<string[]>([]);
  const [completionPos, setCompletionPos] = useState<{ top: number; left: number } | null>(null);
  const [selectedCompletion, setSelectedCompletion] = useState(0);
  const [error, setError] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const highlightRef = useRef<HTMLPreElement>(null);
  const lineNumbersRef = useRef<HTMLDivElement>(null);

  const isDirty = content !== savedContent;
  const fileName = scriptPath.split('/').pop() ?? scriptPath;
  const deferredContent = useDeferredValue(content);
  const highlightDisabled = content.length > 60000;
  const highlightedContent = useMemo(() => highlightDisabled ? '' : highlightJS(deferredContent), [deferredContent, highlightDisabled]);

  useEffect(() => {
    setLoading(true);
    setError('');
    api.readScript(scriptPath).then(res => {
      setContent(res.content);
      setSavedContent(res.content);
    }).catch((err) => {
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
      setContent(t('core.script.loadFallback'));
      setSavedContent('');
    }).finally(() => setLoading(false));
  }, [scriptPath]);

  async function save() {
    if (saving || !isDirty) return;
    setSaving(true);
    setError('');
    try {
      await api.saveScript(scriptPath, content);
      setSavedContent(content);
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: 1 }) });
    } catch (err) {
      const message = err instanceof Error ? err.message : t('core.script.saveFailed');
      setError(message);
      setToast({ tone: 'bad', text: userFacingSaveError(err) });
    } finally {
      setSaving(false);
    }
  }

  async function reload() {
    setLoading(true);
    setError('');
    try {
      const res = await api.readScript(scriptPath);
      setContent(res.content);
      setSavedContent(res.content);
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
      subtitle: `${file.title} · ${scriptPath}`,
      dirty: isDirty,
      changedCount: isDirty ? 1 : 0,
      changes: [],
      source: content,
      sourceEditable: true,
      sourceError: error || null,
      sourceLanguage: 'javascript',
      saving,
      loading,
      canUndo: false,
      canRedo: false,
      onReload: () => void reload(),
      onSourceChange: (next: string) => { setContent(next); setError(''); },
      onSave: () => void save()
    });
    return () => setSurfaceToolbar(null);
  }, [fileName, file.title, scriptPath, isDirty, content, error, saving, loading]);

  function handleInput(e: React.ChangeEvent<HTMLTextAreaElement>) {
    const value = e.target.value;
    setContent(value);
    tryComplete(value, e.target.selectionStart);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (completions.length > 0) {
      if (e.key === 'ArrowDown') { e.preventDefault(); setSelectedCompletion(i => Math.min(i + 1, completions.length - 1)); return; }
      if (e.key === 'ArrowUp') { e.preventDefault(); setSelectedCompletion(i => Math.max(i - 1, 0)); return; }
      if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); applyCompletion(completions[selectedCompletion]); return; }
      if (e.key === 'Escape') { setCompletions([]); return; }
    }
    if (e.key === 'Tab' && completions.length === 0) {
      e.preventDefault();
      const ta = e.currentTarget;
      const start = ta.selectionStart;
      const end = ta.selectionEnd;
      const newValue = content.substring(0, start) + '  ' + content.substring(end);
      setContent(newValue);
      requestAnimationFrame(() => { ta.selectionStart = ta.selectionEnd = start + 2; });
    }
    if (e.key === 's' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      save();
    }
  }

  function tryComplete(value: string, cursor: number) {
    const before = value.substring(0, cursor);
    const match = before.match(/([a-zA-Z_$][\w$.]*)\s*$/);
    if (!match) { setCompletions([]); return; }
    const prefix = match[1];
    const items = getCompletions(prefix);
    if (items.length > 0) {
      setCompletions(items);
      setSelectedCompletion(0);
      const lines = before.split('\n');
      const line = lines.length;
      const col = lines[lines.length - 1].length;
      setCompletionPos({ top: line * 20, left: col * 8.4 });
    } else {
      setCompletions([]);
    }
  }

  function applyCompletion(item: string) {
    const ta = textareaRef.current;
    if (!ta) return;
    const cursor = ta.selectionStart;
    const before = content.substring(0, cursor);
    const after = content.substring(cursor);
    const match = before.match(/\.([a-zA-Z_$][\w]*)$/);
    const keywordMatch = before.match(/([a-zA-Z_$][\w]*)$/);
    let replaceStart = cursor;
    if (match) {
      replaceStart = cursor - match[1].length;
    } else if (keywordMatch) {
      replaceStart = cursor - keywordMatch[1].length;
    }
    const insertText = item;
    const newContent = content.substring(0, replaceStart) + insertText + after;
    setContent(newContent);
    setCompletions([]);
    const newCursor = replaceStart + insertText.length;
    requestAnimationFrame(() => { ta.selectionStart = ta.selectionEnd = newCursor; ta.focus(); });
  }

  function handleScroll() {
    if (highlightRef.current && textareaRef.current) {
      highlightRef.current.scrollTop = textareaRef.current.scrollTop;
      highlightRef.current.scrollLeft = textareaRef.current.scrollLeft;
    }
    if (lineNumbersRef.current && textareaRef.current) {
      lineNumbersRef.current.scrollTop = textareaRef.current.scrollTop;
    }
  }

  const lines = content.split('\n');

  if (loading) return <div className="script-loading" role="status">{t('core.script.loading')}</div>;

  return <div className="script-editor">
    {error && <InlineError>{error}</InlineError>}
    <div className="editor-container">
      <div ref={lineNumbersRef} className="line-numbers">{lines.map((_, i) => <div key={i}>{i + 1}</div>)}</div>
      <div className="editor-wrapper">
        <pre ref={highlightRef} className="editor-highlight" aria-hidden="true"><code dangerouslySetInnerHTML={{ __html: highlightedContent }} /></pre>
        <textarea ref={textareaRef} className="editor-input" value={content} onChange={handleInput} onKeyDown={handleKeyDown} onScroll={handleScroll} spellCheck={false} autoComplete="off" autoCorrect="off" autoCapitalize="off" aria-label={t('core.script.editAria', { path: scriptPath })} aria-describedby="script-editor-help" />
        <span id="script-editor-help" className="sr-only">{t('core.script.help')}</span>
        {completions.length > 0 && completionPos && <div className="completion-popup" style={{ top: completionPos.top + 24, left: completionPos.left + 48 }}>
          {completions.map((item, i) => <div key={item} className={`completion-item ${i === selectedCompletion ? 'selected' : ''}`} onMouseDown={(e) => { e.preventDefault(); applyCompletion(item); }}>{item}</div>)}
        </div>}
      </div>
    </div>
  </div>;
}


const COMPLETIONS: Record<string, string[]> = {
  'emaki': ['logger', 'player', 'item', 'state', 'text', 'random', 'action', 'context'],
  'emaki.logger': ['info(msg)', 'warn(msg)', 'error(msg)'],
  'emaki.player': ['exists()', 'name()', 'sendMessage(msg)', 'health()', 'setHealth(value)', 'location()', 'hasPermission(perm)', 'uuid()'],
  'emaki.item': ['id()', 'amount()', 'hasTag(key)', 'getTag(key)', 'setTag(key, value)', 'type()'],
  'emaki.state': ['get(key)', 'set(key, value)', 'has(key)', 'remove(key)'],
  'emaki.context': ['phase()', 'plugin()', 'trigger()'],
  'emaki.text': ['color(text)', 'strip(text)'],
  'emaki.random': ['nextInt(bound)', 'nextDouble()', 'chance(percent)'],
  'emaki.action': ['dispatch(actionLine)'],
  'console': ['log(msg)', 'warn(msg)', 'error(msg)', 'info(msg)'],
  'Math': ['abs(x)', 'ceil(x)', 'floor(x)', 'round(x)', 'max(...values)', 'min(...values)', 'random()', 'pow(base, exp)', 'sqrt(x)', 'PI', 'E'],
  'JSON': ['parse(text)', 'stringify(value)', 'stringify(value, null, 2)'],
  'Object': ['keys(obj)', 'values(obj)', 'entries(obj)', 'assign(target, ...sources)', 'freeze(obj)'],
  'Array': ['isArray(value)', 'from(arrayLike)'],
  'String': ['fromCharCode(code)'],
  'Number': ['parseInt(str)', 'parseFloat(str)', 'isNaN(value)', 'isFinite(value)'],
};

const KEYWORD_COMPLETIONS = ['function', 'const', 'let', 'var', 'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'break', 'continue', 'return', 'try', 'catch', 'finally', 'throw', 'new', 'typeof', 'instanceof', 'class', 'extends', 'import', 'export', 'async', 'await', 'yield', 'true', 'false', 'null', 'undefined', 'this', 'console', 'Math', 'JSON', 'Object', 'Array', 'String', 'Number', 'Date', 'RegExp', 'Map', 'Set', 'Promise', 'parseInt', 'parseFloat', 'isNaN', 'isFinite', 'setTimeout', 'clearTimeout', 'emaki'];

function getCompletions(prefix: string): string[] {
  const dotIndex = prefix.lastIndexOf('.');
  if (dotIndex > 0) {
    const obj = prefix.substring(0, dotIndex);
    const partial = prefix.substring(dotIndex + 1).toLowerCase();
    const methods = COMPLETIONS[obj];
    if (methods) {
      return partial ? methods.filter(m => m.toLowerCase().startsWith(partial)) : methods;
    }
    return [];
  }
  const lower = prefix.toLowerCase();
  if (lower.length < 2) return [];
  return KEYWORD_COMPLETIONS.filter(k => k.toLowerCase().startsWith(lower) && k.toLowerCase() !== lower).slice(0, 12);
}

function sameSelection(a: Selection | null, b: Selection) { return a?.moduleId === b.moduleId && a.fileId === b.fileId && (a.scriptPath ?? '') === (b.scriptPath ?? ''); }
function readTheme(): ColorTheme { const saved = localStorage.getItem('emaki-color-theme'); return COLOR_THEMES.some((entry) => entry.id === saved) ? saved as ColorTheme : 'dark'; }
function localeLabel(locale: string): string { return LOCALE_LABELS[locale] ?? LOCALE_LABELS[locale.replace('-', '_')] ?? LOCALE_LABELS[locale.replace('_', '-')] ?? locale; }
function EmakiMark() {
  return <svg className="emaki-mark" viewBox="0 0 32 32" aria-hidden="true" focusable="false">
    <path d="M7 8.5c2.8-2.7 6-4 9.4-4 3.7 0 6.5 1.3 8.6 3.8-2.5.2-4.7.9-6.5 2.1 2.8.3 5 1.6 6.7 3.9-3.4-.2-6.1.6-8.1 2.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
    <path d="M15.6 7.2c-1.3 4.2-1.7 8-.9 11.5.7 3.2 2.5 5.7 5.3 7.5" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
    <path d="M8.2 18.3c4.7-.5 9.1-.2 13.2.9M6.2 24.8c5.6-1.4 11.5-1.6 17.6-.6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity=".72"/>
  </svg>;
}

function LocaleIcon() {
  return <svg className="locale-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M8 1.6a6.4 6.4 0 1 0 0 12.8A6.4 6.4 0 0 0 8 1.6Zm0 0c1.65 1.6 2.45 3.72 2.4 6.4-.05 2.68-.85 4.8-2.4 6.4M8 1.6C6.35 3.2 5.55 5.32 5.6 8c.05 2.68.85 4.8 2.4 6.4M2.4 8h11.2M3.7 4.8h8.6M3.7 11.2h8.6" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"/></svg>;
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
function parseListValue(original: unknown, text: string) { if (isObjectLike(original)) { try { return JSON.parse(text); } catch { return text; } } return text; }
function str(v: unknown): string { if (v == null) return ''; if (typeof v === 'object') try { return JSON.stringify(v, null, 2); } catch { return ''; } return String(v); }
function firstSelection(r: WebRegistry): Selection | null { const m = r.modules[0]; return m?.files[0] ? { moduleId: m.id, fileId: m.files[0].id } : null; }
