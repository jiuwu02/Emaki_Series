import { Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type FormEvent } from 'react';
import type { ComponentType } from 'react';
import { ApiClient, type FrontendDebugEventReport, type HistoryEntry, type HistorySnapshot, type InsightDependencyGraphEdge, type InsightDependencyGraphNode, type InsightDependencyGraphResult, type InsightReferenceResult, type InsightSearchResult } from './api';
import { loadWebExtensions } from './extensions';
import { applyConfigRegistryOverrides, applyEditorDescriptorOverrides, getInsightDefinition, getSourceDocumentAdapter, getSurface, isKind, registerSourceDocumentAdapter, registerSurface, setRuntimeEnums, setServerJavaScriptCompletions, type SourceDocumentAdapterContext } from './registry';
import { isGlobPath, normalizeDocumentPath, normalizeLookupPath, resolveConcreteChildPath, resolveSurfaceDocumentPath, treeDirtyKey } from './documentPaths';
import { getLocale, getRegisteredLocales, setLocale, t } from './i18n';
import { ActionGroup, ActionTypesProvider, Button, EconomyProvidersProvider, EditorChrome, ToastNotice } from './components';
import { UnifiedDiffView, parseUnifiedDiff } from './components/DiffViewer';
import { useDialogFocus } from './components/useDialogFocus';
import type { I18nTarget } from './I18nBundleModal';
import { fileDisplayComment, fileDisplayTitle, moduleDisplayName } from './lib';
import { Login, OUTLINE_DEFAULT, OUTLINE_MAX, OUTLINE_MIN, OUTLINE_STORAGE_KEY, RAIL_DEFAULT, RAIL_MAX, RAIL_MIN, RAIL_STORAGE_KEY, ResizableOutlineRail, ResizableRail, WorkspaceTree, fileKindLabel } from './shell';
import { FieldOutlineRail, jumpToConfigNode } from './shell/FieldOutlineRail';
import { debugInputValue, frontendDebugEvent, interactiveTarget, isFormControl } from './shell/frontendDebug';
import type { SurfaceOutlineState, SurfaceProps, SurfaceToolbarState } from './registry';
import type { RegistryTreeNode, WebConsoleExtension, WebConsoleExtensionStatus, WebEditorDescriptor, WebRegistry, WebRegistryFile, WebRegistryModule } from './types';
import { ConfigSaveConflictModal, ConfigSurface, configDraftScope, useConfigDraftState, type ConfigSaveSafety, type DraftHistoryMap, type DraftMap, type DraftPathsAction, type DraftScopeAction, type DraftValueSetter } from './surfaces/config';
import { ScriptSurface } from './surfaces/script/ScriptSurface';

const GuiEditorSurface = lazy(() => import('./GuiEditorSurface').then(module => ({ default: module.GuiEditorSurface })));
const ItemEditorSurface = lazy(() => import('./ItemEditorSurface').then(module => ({ default: module.ItemEditorSurface })));
const I18nBundleModal = lazy(() => import('./I18nBundleModal').then(module => ({ default: module.I18nBundleModal })));

// Register CoreLib's built-in surfaces through the same registry used by plugin extensions.
registerSurface({ kind: 'GUI', component: GuiEditorSurface as ComponentType<SurfaceProps>, label: t('core.surface.gui.label') });
registerSurface({ kind: 'ITEM', component: ItemEditorSurface as ComponentType<SurfaceProps>, label: t('core.surface.item.label') });
registerSurface({ kind: 'GEM', component: ItemEditorSurface as ComponentType<SurfaceProps>, label: t('core.surface.gem.label') });
for (const kind of ['CONFIG', 'GUI', 'ITEM', 'GEM', 'SCRIPT']) {
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
type RegistryLoadOptions = { initial?: boolean; clearDrafts?: boolean; announceRefresh?: boolean };

type InsightReferenceTarget = { idType: string; id: string };
type HistoryTarget = { moduleId: string; path: string; kind: string; revision?: number; title: string };

type Toast = { tone: 'ok' | 'bad'; text: string } | null;
type LoginNotice = 'expired' | 'signedOut' | null;
type ColorTheme = 'dark' | 'light';

const COLOR_THEMES: { id: ColorTheme; labelKey: string }[] = [
  { id: 'dark', labelKey: 'core.theme.dark' },
  { id: 'light', labelKey: 'core.theme.light' }
];
const LOCALE_LABELS: Record<string, string> = { 'zh-CN': '简体中文', zh_CN: '简体中文', 'en-US': 'English', en_US: 'English' };
// 工作区布局
const WORKBENCH_LAYOUT = {
  outlineCollapseWidth: 1180,
} as const;

type WorkbenchLayout = {
  outlineVisible: boolean;
  railWidth: number;
  outlineWidth: number;
  style: CSSProperties;
  setRailRequested: (width: number) => void;
  setOutlineRequested: (width: number) => void;
};

function useWorkbenchLayout(hasOutline: boolean): WorkbenchLayout {
  const [viewportWidth, setViewportWidth] = useState(() => browserViewportWidth());
  const [railRequested, setRailRequestedState] = useState(() => readStoredWorkbenchWidth(RAIL_STORAGE_KEY, RAIL_DEFAULT, RAIL_MIN, RAIL_MAX));
  const [outlineRequested, setOutlineRequestedState] = useState(() => readStoredWorkbenchWidth(OUTLINE_STORAGE_KEY, OUTLINE_DEFAULT, OUTLINE_MIN, OUTLINE_MAX));

  useEffect(() => {
    const onResize = () => setViewportWidth(browserViewportWidth());
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  const layout = useMemo(() => resolveWorkbenchLayout(viewportWidth, hasOutline, railRequested, outlineRequested), [viewportWidth, hasOutline, railRequested, outlineRequested]);

  const setRailRequested = useCallback((width: number) => {
    setRailRequestedState(() => {
      const next = clampWorkbenchWidth(width, RAIL_MIN, RAIL_MAX, RAIL_DEFAULT);
      writeStoredWorkbenchWidth(RAIL_STORAGE_KEY, next);
      return next;
    });
  }, []);

  const setOutlineRequested = useCallback((width: number) => {
    setOutlineRequestedState(() => {
      const next = clampWorkbenchWidth(width, OUTLINE_MIN, OUTLINE_MAX, OUTLINE_DEFAULT);
      writeStoredWorkbenchWidth(OUTLINE_STORAGE_KEY, next);
      return next;
    });
  }, []);

  return {
    ...layout,
    setRailRequested,
    setOutlineRequested
  };
}

function resolveWorkbenchLayout(viewportWidth: number, hasOutline: boolean, railRequested: number, outlineRequested: number): Omit<WorkbenchLayout, 'setRailRequested' | 'setOutlineRequested'> {
  const outlineVisible = hasOutline && viewportWidth > WORKBENCH_LAYOUT.outlineCollapseWidth;
  const railLimit = Math.max(RAIL_MIN, viewportWidth - (outlineVisible ? OUTLINE_MIN : 0));
  const railWidth = Math.round(Math.min(clampWorkbenchWidth(railRequested, RAIL_MIN, RAIL_MAX, RAIL_DEFAULT), railLimit));
  const outlineLimit = Math.max(OUTLINE_MIN, viewportWidth - railWidth);
  const outlineWidth = outlineVisible ? Math.round(Math.min(clampWorkbenchWidth(outlineRequested, OUTLINE_MIN, OUTLINE_MAX, OUTLINE_DEFAULT), outlineLimit)) : 0;
  return {
    outlineVisible,
    railWidth,
    outlineWidth,
    style: {
      '--rail-width': `${railWidth}px`,
      '--outline-width': `${outlineWidth}px`
    } as CSSProperties
  };
}

function browserViewportWidth(): number {
  return typeof window === 'undefined' ? 1440 : Math.max(0, window.innerWidth || document.documentElement.clientWidth || 1440);
}

function readStoredWorkbenchWidth(key: string, fallback: number, min: number, max: number): number {
  try {
    const value = localStorage.getItem(key);
    return value == null ? fallback : clampWorkbenchWidth(Number(value), min, max, fallback);
  } catch (_) {
    return fallback;
  }
}

function writeStoredWorkbenchWidth(key: string, value: number): void {
  try {
    localStorage.setItem(key, String(Math.round(value)));
  } catch (_) {
    // Storage can be unavailable in private mode; layout still works with in-memory state.
  }
}

function clampWorkbenchWidth(value: number, min: number, max: number, fallback: number): number {
  return Math.max(min, Math.min(max, Number.isFinite(value) ? value : fallback));
}

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('emaki-web-token'));
  const [loginNotice, setLoginNotice] = useState<LoginNotice>(null);
  const [registry, setRegistry] = useState<WebRegistry | null>(null);
  const [selected, setSelected] = useState<Selection | null>(null);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const { drafts, draftHistory, saveConflict, setSaveConflict, dirtyTreeKeys, setDraftValue, clearDraftScope, clearDraftValues, clearDraftPaths, undoDraftScope, redoDraftScope, reconcileScopeDrafts, resetConfigDrafts } = useConfigDraftState();
  const [toast, setToast] = useState<Toast>(null);
  const [theme, setTheme] = useState<ColorTheme>(() => readTheme());
  const [localeVersion, setLocaleVersion] = useState(0);
  const [i18nTarget, setI18nTarget] = useState<I18nTarget | null>(null);
  const [createTarget, setCreateTarget] = useState<RegistryTreeNode | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<RegistryTreeNode | null>(null);
  const [insightSearchOpen, setInsightSearchOpen] = useState(false);
  const [referenceTarget, setReferenceTarget] = useState<InsightReferenceTarget | null>(null);
  const [dependencyGraphTarget, setDependencyGraphTarget] = useState<InsightReferenceTarget | null>(null);
  const [historyTarget, setHistoryTarget] = useState<HistoryTarget | null>(null);
  const [loading, setLoading] = useState(false);
  const [surfaceToolbar, setSurfaceToolbar] = useState<SurfaceToolbarState | null>(null);
  const [surfaceOutline, setSurfaceOutline] = useState<SurfaceOutlineState>(null);
  const workbenchLayout = useWorkbenchLayout(Boolean(surfaceOutline));
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
    resetConfigDrafts();
    setSurfaceToolbar(null);
    setSurfaceOutline(null);
    setSurfaceDirtyKeys(new Set());
    setCreateTarget(null);
    setDeleteTarget(null);
    setInsightSearchOpen(false);
    setReferenceTarget(null);
    setDependencyGraphTarget(null);
    setHistoryTarget(null);
    setI18nTarget(null);
    setToast(null);
  };

  const expireSession = () => clearSession('expired');
  const signOut = () => {
    void api.reportFrontendEvent({ type: 'logout_submit', target: 'auth.logout', label: t('core.auth.logout') });
    void api.logout().catch(() => undefined).finally(() => clearSession('signedOut'));
  };

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
    if (!token) return;
    const report = (event: FrontendDebugEventReport) => void api.reportFrontendEvent(event);
    const handleClick = (event: MouseEvent) => {
      const target = interactiveTarget(event.target);
      if (!target) return;
      report(frontendDebugEvent('click', target));
    };
    const handleChange = (event: Event) => {
      const target = event.target instanceof HTMLElement ? event.target : null;
      if (!target || !isFormControl(target)) return;
      report(frontendDebugEvent('change', target, { value: debugInputValue(target) }));
    };
    const handleSubmit = (event: SubmitEvent) => {
      const target = event.target instanceof HTMLElement ? event.target : null;
      if (!target) return;
      report(frontendDebugEvent('submit', target));
    };
    const handleKeydown = (event: KeyboardEvent) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        report({ type: 'shortcut', target: 'document', label: 'Ctrl/Meta+K', detail: 'open insight search' });
      }
    };
    document.addEventListener('click', handleClick, true);
    document.addEventListener('change', handleChange, true);
    document.addEventListener('submit', handleSubmit, true);
    document.addEventListener('keydown', handleKeydown, true);
    return () => {
      document.removeEventListener('click', handleClick, true);
      document.removeEventListener('change', handleChange, true);
      document.removeEventListener('submit', handleSubmit, true);
      document.removeEventListener('keydown', handleKeydown, true);
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
      setServerJavaScriptCompletions(next.scriptCompletions);
      const merged = enhanceRegistry(next);
      setRegistry(merged);
      if (initial) setExpanded(Object.fromEntries(merged.modules.map((m) => [m.id, true])));
      setSelected((current) => current && selectionExists(merged, current) ? current : firstSelection(merged));
      if (clearDrafts) resetConfigDrafts();
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
  const selectedReferenceTarget = useMemo(() => insightDefinitionTarget(selectedModule, selectedFile, selected?.scriptPath), [selectedModule?.id, selectedFile?.path, selectedFile?.nodes, selected?.scriptPath]);
  const selectedHistoryTarget = useMemo(() => historyTargetForSelection(selectedModule, selectedFile, selected?.scriptPath), [selectedModule?.id, selectedFile?.id, selectedFile?.path, selectedFile?.kind, selectedFile?.revision, selected?.scriptPath]);
  const activeTheme = COLOR_THEMES.find((entry) => entry.id === theme) ?? COLOR_THEMES[0];
  const activeThemeLabel = t(activeTheme.labelKey);
  const nextTheme = () => setTheme((current) => COLOR_THEMES[(COLOR_THEMES.findIndex((entry) => entry.id === current) + 1) % COLOR_THEMES.length].id);
  const selectedEditor = selectedFile?.editorId ? registry?.editors?.[selectedFile.editorId] : undefined;
  const selectedDirtyKey = selectedModule && selectedFile ? treeDirtyKey(selectedModule.id, selectedFile.id, selected?.scriptPath ?? selectedFile.path) : null;
  const fallbackToolbar: SurfaceToolbarState = {
    title: selectedModule ? moduleDisplayName(selectedModule) : t('core.stage.defaultTitle'),
    subtitle: selectedFile ? `${fileDisplayTitle(selectedFile)}，${selectedFile.path}` : t('core.stage.defaultHint'),
    dirty: false,
    changedCount: 0,
    loading,
    onReload: () => void reloadCurrentSurface()
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
      if (toolbar.saving || toolbar.loading || sourceEditingElement(event.target)) return;
      const mod = event.ctrlKey || event.metaKey;
      if (!mod) return;
      const key = event.key.toLowerCase();
      if (key === 'z' && !event.shiftKey && toolbar.canUndo && toolbar.onUndo) {
        event.preventDefault();
        toolbar.onUndo();
      } else if ((key === 'y' || (key === 'z' && event.shiftKey)) && toolbar.canRedo && toolbar.onRedo) {
        event.preventDefault();
        toolbar.onRedo();
      }
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [toolbar.canUndo, toolbar.canRedo, toolbar.loading, toolbar.onUndo, toolbar.onRedo, toolbar.saving]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const mod = event.ctrlKey || event.metaKey;
      if (!mod || event.key.toLowerCase() !== 'k') return;
      event.preventDefault();
      setInsightSearchOpen(true);
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, []);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const mod = event.ctrlKey || event.metaKey;
      if (!mod || event.key.toLowerCase() !== 's') return;
      event.preventDefault();
      const target = event.target instanceof HTMLElement ? event.target : null;
      if (target?.closest('.cm-editor')) return;
      if (!toolbar.onSave || !toolbar.dirty || toolbar.saving || toolbar.loading) return;
      toolbar.onSave();
    };
    document.addEventListener('keydown', handleKeyDown, true);
    return () => document.removeEventListener('keydown', handleKeyDown, true);
  }, [toolbar.onSave, toolbar.dirty, toolbar.saving, toolbar.loading]);

  function jumpToConfigPath(path: string) {
    jumpToConfigNode(path);
  }

  function openInsightLocation(location: { moduleId: string; path: string; keyPath?: string }, close: () => void) {
    if (!registry) return;
    const target = findSearchResultSelection(registry, location);
    if (!target) {
      setToast({ tone: 'bad', text: t('core.insight.openFailed') });
      return;
    }
    setSelected({ ...target, refreshKey: Date.now() });
    close();
    if (location.keyPath) {
      window.requestAnimationFrame(() => jumpToConfigNode(location.keyPath ?? ''));
    }
  }

  function openInsightSearchResult(result: InsightSearchResult) {
    openInsightLocation(result, () => setInsightSearchOpen(false));
  }

  function openInsightReferences(target: InsightReferenceTarget) {
    setInsightSearchOpen(false);
    setDependencyGraphTarget(null);
    setReferenceTarget(target);
  }

  function openInsightDependencyGraph(target: InsightReferenceTarget) {
    setInsightSearchOpen(false);
    setReferenceTarget(null);
    setDependencyGraphTarget(target);
  }

  function openInsightReferenceResult(result: InsightReferenceResult) {
    openInsightLocation(result, () => setReferenceTarget(null));
  }

  if (!token) return <Login notice={loginNotice} onLogin={(t) => { sessionStorage.setItem('emaki-web-token', t); setLoginNotice(null); setToken(t); }} />;

  return (
    <ActionTypesProvider api={api}>
    <EconomyProvidersProvider api={api}>
    <div className={`workbench${workbenchLayout.outlineVisible ? '' : ' workbench--no-outline'}`} style={workbenchLayout.style} data-locale-version={localeVersion}>
      {toast && <ToastNotice tone={toast.tone}>{toast.text}</ToastNotice>}
      {createTarget && <CreateFileModal target={createTarget} onCancel={() => setCreateTarget(null)} onCreate={createFileFromTree} />}
      {deleteTarget && <DeleteFileModal target={deleteTarget} onCancel={() => setDeleteTarget(null)} onDelete={deleteFileFromTree} />}
      {i18nTarget && <Suspense fallback={null}><I18nBundleModal target={i18nTarget} onClose={() => setI18nTarget(null)} onSaved={() => { setLocaleVersion((version) => version + 1); setToast({ tone: 'ok', text: t('core.i18n.saved') }); }} /></Suspense>}
      {insightSearchOpen && <InsightSearchModal api={api} registry={registry} onCancel={() => setInsightSearchOpen(false)} onOpen={openInsightSearchResult} onReferences={openInsightReferences} onGraph={openInsightDependencyGraph} />}
      {referenceTarget && <InsightReferenceModal api={api} registry={registry} target={referenceTarget} onCancel={() => setReferenceTarget(null)} onOpen={openInsightReferenceResult} onGraph={openInsightDependencyGraph} />}
      {dependencyGraphTarget && <InsightDependencyGraphModal api={api} registry={registry} target={dependencyGraphTarget} onCancel={() => setDependencyGraphTarget(null)} onOpen={location => openInsightLocation(location, () => setDependencyGraphTarget(null))} />}
      {historyTarget && <HistoryModal api={api} target={historyTarget} onCancel={() => setHistoryTarget(null)} onRolledBack={async () => { setHistoryTarget(null); await reloadCurrentSurface(); }} />}
      {saveConflict && <ConfigSaveConflictModal conflict={saveConflict} onCancel={() => setSaveConflict(null)} />}
      <ResizableRail width={workbenchLayout.railWidth} onWidthChange={workbenchLayout.setRailRequested}>
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
        <button type="button" className="insight-search-trigger" onClick={() => setInsightSearchOpen(true)} aria-label={t('core.insight.searchAria')}>
          <span>{t('core.insight.searchPlaceholder')}</span>
          <kbd>Ctrl K</kbd>
        </button>
        <WorkspaceTree registry={registry} selected={selected} expanded={expanded} dirtyKeys={mergedDirtyKeys} localeVersion={localeVersion} setExpanded={setExpanded} onOpenI18n={setI18nTarget} onCreateFile={setCreateTarget} onDeleteFile={setDeleteTarget} onSelect={(next) => setSelected((current) => {
          const same = sameSelection(current, next);
          const accepted = same ? { ...next, refreshKey: (current?.refreshKey ?? 0) + 1 } : next;
          return accepted;
        })} />
        <ExtensionHealthBanner
          health={extensionHealth}
          statuses={extensionStatuses}
          onRetry={() => void loadRegistry({ clearDrafts: false, announceRefresh: false })}
        />
        <button className="rail-action quiet" onClick={signOut}>{t('core.auth.logout')}</button>
      </ResizableRail>
      <main className="stage">
        <EditorChrome
          className="stage-head"
          title={toolbar.title ?? (selectedModule ? moduleDisplayName(selectedModule) : t('core.stage.defaultTitle'))}
          subtitle={toolbar.subtitle ?? (selectedFile ? `${fileDisplayTitle(selectedFile)}，${selectedFile.path}` : t('core.stage.defaultHint'))}
          meta={<SurfaceHeaderMeta module={selectedModule} file={selectedFile} editor={selectedEditor} toolbar={toolbar} loading={loading} />}
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
        >
          <SurfaceHeaderActions referenceTarget={selectedReferenceTarget} historyTarget={selectedHistoryTarget} onOpenReferences={openInsightReferences} onOpenGraph={openInsightDependencyGraph} onOpenHistory={setHistoryTarget} />
        </EditorChrome>
        <section className="editor-shell single">
          <SurfaceHost registry={registry} module={selectedModule} file={selectedFile} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} clearDraftPaths={clearDraftPaths} reconcileScopeDrafts={reconcileScopeDrafts} setSaveConflict={setSaveConflict} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} scriptPath={selected?.scriptPath} refreshKey={selected?.refreshKey ?? 0} pendingExtensionModules={pendingExtensionModules} onReload={() => void reloadCurrentSurface()} onRefreshRegistry={() => loadRegistry({ clearDrafts: false, announceRefresh: false })} setSurfaceToolbar={setSurfaceToolbar} setSurfaceOutline={setSurfaceOutline} setToast={setToast} />
        </section>
      </main>
      {surfaceOutline && workbenchLayout.outlineVisible && <ResizableOutlineRail width={workbenchLayout.outlineWidth} onWidthChange={workbenchLayout.setOutlineRequested}>
        <FieldOutlineRail outline={surfaceOutline} onJump={jumpToConfigPath} />
      </ResizableOutlineRail>}
    </div>
    </EconomyProvidersProvider>
    </ActionTypesProvider>
  );
}

function SurfaceHeaderMeta({ module, file, editor, toolbar, loading }: { module: WebRegistryModule | null; file: WebRegistryFile | null; editor?: WebEditorDescriptor; toolbar: SurfaceToolbarState; loading: boolean }) {
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
    toolbar.saving ? t('core.script.saving', undefined, 'Saving...') : '',
    loading ? t('core.state.loading') : ''
  ].filter(Boolean);

  return <div className="surface-header-meta">
    {(moduleSummary || fileComment) && <span className="surface-header-summary">{moduleSummary || fileComment}</span>}
    {filePath && <code>{filePath}</code>}
    {chips.length > 0 && <div className="surface-summary-meta" aria-live="polite">
      {chips.map((chip, index) => <span key={`${chip}-${index}`} className={`surface-summary-chip${chip === fileKind ? ' kind' : ''}`}>{chip}</span>)}
    </div>}
  </div>;
}

function SurfaceHeaderActions({ referenceTarget, historyTarget, onOpenReferences, onOpenGraph, onOpenHistory }: { referenceTarget: InsightReferenceTarget | null; historyTarget: HistoryTarget | null; onOpenReferences: (target: InsightReferenceTarget) => void; onOpenGraph: (target: InsightReferenceTarget) => void; onOpenHistory: (target: HistoryTarget) => void }) {
  return <>
    {historyTarget && <Button size="sm" variant="ghost" onClick={() => onOpenHistory(historyTarget)}>{t('core.history.button', undefined, 'History')}</Button>}
    {referenceTarget && <Button size="sm" variant="ghost" onClick={() => onOpenReferences(referenceTarget)}>{t('core.insight.references')}</Button>}
    {referenceTarget && <Button size="sm" variant="ghost" onClick={() => onOpenGraph(referenceTarget)}>{t('core.insight.graph')}</Button>}
  </>;
}


function groupInsightResults(results: InsightSearchResult[], registry: WebRegistry | null): { moduleId: string; module?: WebRegistryModule; results: InsightSearchResult[] }[] {
  const groups = new Map<string, { moduleId: string; module?: WebRegistryModule; results: InsightSearchResult[] }>();
  for (const result of results) {
    const module = registry?.modules.find(entry => entry.id === result.moduleId);
    const entry = groups.get(result.moduleId) ?? { moduleId: result.moduleId, module, results: [] };
    entry.results.push(result);
    groups.set(result.moduleId, entry);
  }
  return [...groups.values()];
}

function findSearchResultSelection(registry: WebRegistry, result: { moduleId: string; path: string }): Selection | null {
  const module = registry.modules.find(entry => entry.id === result.moduleId);
  if (!module) return null;
  const resolved = resolveConcreteChildPath(module, result.path);
  if (!resolved) return null;
  const filePath = normalizeLookupPath(resolved.file.path);
  const concretePath = normalizeDocumentPath(resolved.path);
  return { moduleId: module.id, fileId: resolved.file.id, scriptPath: filePath === normalizeLookupPath(concretePath) ? undefined : concretePath };
}

function normalizeInsightPath(path: string | undefined): string {
  return normalizeLookupPath(path);
}

function historyTargetForSelection(module: WebRegistryModule | null, file: WebRegistryFile | null, childPath?: string): HistoryTarget | null {
  if (!module || !file) return null;
  const path = normalizeDocumentPath(childPath || file.path);
  if (!path || isGlobPath(path)) return null;
  return { moduleId: module.id, path, kind: String(file.kind ?? 'CONFIG').toUpperCase(), revision: file.revision, title: `${fileDisplayTitle(file)} · ${path}` };
}

function insightDefinitionTarget(module: WebRegistryModule | null, file: WebRegistryFile | null, childPath?: string): InsightReferenceTarget | null {
  if (!module || !file) return null;
  const path = normalizeInsightPath(childPath || file.path);
  const registeredDefinition = getInsightDefinition({ moduleId: module.id, path });
  const idType = registeredDefinition?.idType || inferInsightIdType(module.id, path);
  if (!idType) return null;
  const idPath = registeredDefinition?.idPath || 'id';
  const idNode = file.nodes?.find(node => node.path === idPath);
  const nodeId = scalarText(idNode?.value).trim();
  const fallbackId = registeredDefinition?.fallbackId === 'none' ? '' : basenameWithoutExtension(path);
  const id = nodeId || fallbackId;
  return id ? { idType, id } : null;
}

function referenceTargetFromSearchResult(result: InsightSearchResult): InsightReferenceTarget | null {
  const idType = String(result.idType ?? '').trim();
  const id = String(result.id ?? '').trim();
  if (!idType || !id) return null;
  const matchType = String(result.matchType ?? '').toLowerCase();
  if (matchType !== 'definition' && matchType !== 'reference') return null;
  return { idType, id };
}

function inferInsightIdType(moduleId: string, path: string): string {
  const module = String(moduleId ?? '').toLowerCase();
  const normalizedPath = normalizeInsightPath(path);
  if (module === 'emakiattribute' && normalizedPath.startsWith('attributes/')) return 'attribute';
  if (module === 'emakiitem' && normalizedPath.startsWith('items/')) return 'emaki_item';
  if (module === 'emakigem' && normalizedPath.startsWith('gems/')) return 'gem';
  if (module === 'emakiskills' && normalizedPath.startsWith('skills/')) return 'skill';
  if (module === 'emakilevel' && normalizedPath.startsWith('types/')) return 'level_type';
  if (module === 'emakiforge' && normalizedPath.startsWith('recipes/')) return 'forge_recipe';
  if (module === 'emakistrengthen' && normalizedPath.startsWith('recipes/')) return 'strengthen_recipe';
  return '';
}

function basenameWithoutExtension(path: string): string {
  const normalized = normalizeInsightPath(path);
  const name = normalized.substring(normalized.lastIndexOf('/') + 1);
  return name.replace(/\.(ya?ml|json)$/i, '');
}

function scalarText(value: unknown): string {
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
  return '';
}

function insightMatchLabel(matchType: string): string {
  const normalized = String(matchType ?? '').toLowerCase();
  return t(`core.insight.match.${normalized}`, undefined, normalized || t('core.kind.file'));
}

function InsightSearchModal({ api, registry, onCancel, onOpen, onReferences, onGraph }: { api: ApiClient; registry: WebRegistry | null; onCancel: () => void; onOpen: (result: InsightSearchResult) => void; onReferences: (target: InsightReferenceTarget) => void; onGraph: (target: InsightReferenceTarget) => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<InsightSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useDialogFocus(dialogRef, onCancel);

  useEffect(() => { window.setTimeout(() => inputRef.current?.focus(), 0); }, []);
  useEffect(() => {
    const trimmed = query.trim();
    setError('');
    if (!trimmed) {
      setResults([]);
      setLoading(false);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setLoading(true);
      try {
        const next = await api.insightSearch(trimmed);
        if (!cancelled) setResults(next);
      } catch (err) {
        if (!cancelled) {
          setResults([]);
          setError(err instanceof Error ? err.message : t('core.api.requestFailed'));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }, 180);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [api, query]);

  const grouped = useMemo(() => groupInsightResults(results, registry), [results, registry]);
  const canOpen = (result: InsightSearchResult) => Boolean(registry && findSearchResultSelection(registry, result));

  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section ref={dialogRef} className="insight-search-dialog" role="dialog" aria-modal="true" aria-labelledby="insight-search-title" tabIndex={-1}>
      <div className="insight-search-head">
        <span>{t('core.insight.kicker')}</span>
        <h3 id="insight-search-title">{t('core.insight.title')}</h3>
        <button type="button" className="insight-search-close" onClick={onCancel} aria-label={t('core.i18n.close')}>×</button>
      </div>
      <label className="insight-search-input">
        <span>{t('core.insight.inputLabel')}</span>
        <input ref={inputRef} value={query} onChange={event => setQuery(event.target.value)} placeholder={t('core.insight.searchPlaceholder')} />
      </label>
      <div className="insight-search-status" aria-live="polite">
        {loading ? t('core.state.loading') : error || (query.trim() ? t('core.insight.resultCount', { count: results.length }) : t('core.insight.emptyQuery'))}
      </div>
      <div className="insight-search-results">
        {grouped.length > 0 ? grouped.map(group => <section className="insight-search-group" key={group.moduleId}>
          <h4>{group.module ? moduleDisplayName(group.module) : group.moduleId}</h4>
          {group.results.map((result, index) => {
            const target = referenceTargetFromSearchResult(result);
            return <div className="insight-search-result-row" key={`${result.moduleId}:${result.path}:${result.keyPath}:${index}`}>
              <button
                type="button"
                className="insight-search-result"
                disabled={!canOpen(result)}
                onClick={() => onOpen(result)}
              >
                <span className={`insight-search-badge ${result.matchType}`}>{insightMatchLabel(result.matchType)}</span>
                {result.idType && <span className="insight-search-idtype">{result.idType}</span>}
                <strong>{result.path}</strong>
                {result.keyPath && <code>{result.keyPath}</code>}
                {result.alias && <span className="insight-search-idtype alias">alias {aliasLabel(result)}</span>}
                <small>{result.snippet || result.path}</small>
              </button>
              {target && <div className="insight-result-actions">
                <button type="button" className="insight-reference-mini" onClick={() => onReferences(target)}>{t('core.insight.references')}</button>
                <button type="button" className="insight-reference-mini" onClick={() => onGraph(target)}>{t('core.insight.graph')}</button>
              </div>}
            </div>;
          })}
        </section>) : !loading && query.trim() && !error ? <div className="insight-search-empty">{t('core.insight.noResults')}</div> : null}
      </div>
    </section>
  </div>;
}

function groupInsightReferences(results: InsightReferenceResult[], registry: WebRegistry | null): { moduleId: string; module?: WebRegistryModule; results: InsightReferenceResult[] }[] {
  const groups = new Map<string, { moduleId: string; module?: WebRegistryModule; results: InsightReferenceResult[] }>();
  for (const result of results) {
    const module = registry?.modules.find(entry => entry.id === result.moduleId);
    const entry = groups.get(result.moduleId) ?? { moduleId: result.moduleId, module, results: [] };
    entry.results.push(result);
    groups.set(result.moduleId, entry);
  }
  return [...groups.values()];
}

function referenceEdgeLabel(edgeType: string): string {
  const normalized = String(edgeType ?? '').toLowerCase();
  return t(`core.insight.edge.${normalized}`, undefined, normalized || t('core.insight.edge.uses'));
}

function aliasLabel(result: { aliasSourceId?: string; aliasTargetId?: string }): string {
  const source = String(result.aliasSourceId ?? '').trim();
  const target = String(result.aliasTargetId ?? '').trim();
  return source && target ? `${source} → ${target}` : source || target;
}

function InsightReferenceModal({ api, registry, target, onCancel, onOpen, onGraph }: { api: ApiClient; registry: WebRegistry | null; target: InsightReferenceTarget; onCancel: () => void; onOpen: (result: InsightReferenceResult) => void; onGraph: (target: InsightReferenceTarget) => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  const [results, setResults] = useState<InsightReferenceResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  useDialogFocus(dialogRef, onCancel);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    setResults([]);
    void api.insightReferences(target.idType, target.id)
      .then(next => { if (!cancelled) setResults(next); })
      .catch(err => {
        if (!cancelled) setError(err instanceof Error ? err.message : t('core.api.requestFailed'));
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [api, target.idType, target.id]);

  const grouped = useMemo(() => groupInsightReferences(results, registry), [results, registry]);
  const canOpen = (result: InsightReferenceResult) => Boolean(registry && findSearchResultSelection(registry, result));
  const title = t('core.insight.referencesTitle', { id: target.id, idType: target.idType });

  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section ref={dialogRef} className="insight-search-dialog insight-reference-dialog" role="dialog" aria-modal="true" aria-labelledby="insight-reference-title" tabIndex={-1}>
      <div className="insight-search-head">
        <span>{t('core.insight.referencesKicker')}</span>
        <h3 id="insight-reference-title">{title}</h3>
        <button type="button" className="insight-search-close" onClick={onCancel} aria-label={t('core.i18n.close')}>×</button>
      </div>
      <div className="insight-search-status" aria-live="polite">
        <span>{loading ? t('core.state.loading') : error || t('core.insight.referencesCount', { count: results.length })}</span>
        <button type="button" className="insight-reference-mini" onClick={() => onGraph(target)}>{t('core.insight.graph')}</button>
      </div>
      <div className="insight-search-results">
        {grouped.length > 0 ? grouped.map(group => <section className="insight-search-group" key={group.moduleId}>
          <h4>{group.module ? moduleDisplayName(group.module) : group.moduleId}</h4>
          {group.results.map((result, index) => <button
            key={`${result.moduleId}:${result.path}:${result.keyPath}:${index}`}
            type="button"
            className="insight-search-result insight-reference-result"
            disabled={!canOpen(result)}
            onClick={() => onOpen(result)}
          >
            <span className="insight-search-badge reference">{referenceEdgeLabel(result.edgeType)}</span>
            {result.idType && <span className="insight-search-idtype">{result.idType}</span>}
            <strong>{result.path}</strong>
            {result.keyPath && <code>{result.keyPath}</code>}
            {result.alias && <span className="insight-search-idtype alias">alias {aliasLabel(result)}</span>}
            <small>{result.snippet || result.referenceValue || result.path}</small>
          </button>)}
        </section>) : !loading && !error ? <div className="insight-search-empty">{t('core.insight.referencesEmpty')}</div> : null}
      </div>
    </section>
  </div>;
}

function InsightDependencyGraphModal({ api, registry, target, onCancel, onOpen }: { api: ApiClient; registry: WebRegistry | null; target: InsightReferenceTarget; onCancel: () => void; onOpen: (location: { moduleId: string; path: string; keyPath?: string }) => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  const [graph, setGraph] = useState<InsightDependencyGraphResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [moduleFilter, setModuleFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  useDialogFocus(dialogRef, onCancel);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    setGraph(null);
    void api.insightDependencyGraph(target.idType, target.id, { depth: 1, direction: 'both' })
      .then(next => { if (!cancelled) setGraph(next); })
      .catch(err => { if (!cancelled) setError(err instanceof Error ? err.message : t('core.api.requestFailed')); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [api, target.idType, target.id]);

  const nodes = graph?.nodes ?? [];
  const edges = graph?.edges ?? [];
  const modules = useMemo(() => [...new Set(nodes.map(node => node.moduleId).filter(Boolean))].sort(), [nodes]);
  const types = useMemo(() => [...new Set(nodes.map(node => node.idType).filter(Boolean))].sort(), [nodes]);
  const filtered = useMemo(() => filterDependencyGraph(nodes, edges, moduleFilter, typeFilter), [nodes, edges, moduleFilter, typeFilter]);
  const title = t('core.insight.graphTitle', { id: target.id, idType: target.idType });

  function openNode(node: InsightDependencyGraphNode) {
    if (node.moduleId && node.path) onOpen({ moduleId: node.moduleId, path: node.path });
  }

  function openEdge(edge: InsightDependencyGraphEdge) {
    if (edge.moduleId && edge.path) onOpen({ moduleId: edge.moduleId, path: edge.path, keyPath: edge.keyPath });
  }

  function exportJson() {
    if (!graph) return;
    const blob = new Blob([JSON.stringify(graph, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `emaki-dependency-${target.idType}-${target.id}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section ref={dialogRef} className="insight-search-dialog insight-graph-dialog" role="dialog" aria-modal="true" aria-labelledby="insight-graph-title" tabIndex={-1}>
      <div className="insight-search-head">
        <span>{t('core.insight.graphKicker')}</span>
        <h3 id="insight-graph-title">{title}</h3>
        <button type="button" className="insight-search-close" onClick={onCancel} aria-label={t('core.i18n.close')}>×</button>
      </div>
      <div className="insight-graph-toolbar">
        <span className="insight-graph-depth">{t('core.insight.graphDepthOne')}</span>
        <span className="insight-graph-depth">{t('core.insight.graphDirectionBoth')}</span>
        <label><span>{t('core.insight.graphModuleFilter')}</span><select value={moduleFilter} onChange={event => setModuleFilter(event.target.value)}><option value="">{t('core.insight.graphAllModules')}</option>{modules.map(moduleId => <option key={moduleId} value={moduleId}>{moduleId}</option>)}</select></label>
        <label><span>{t('core.insight.graphTypeFilter')}</span><select value={typeFilter} onChange={event => setTypeFilter(event.target.value)}><option value="">{t('core.insight.graphAllTypes')}</option>{types.map(type => <option key={type} value={type}>{type}</option>)}</select></label>
        <button type="button" className="insight-reference-mini" disabled={!graph} onClick={exportJson}>{t('core.insight.graphExportJson')}</button>
      </div>
      <div className="insight-search-status" aria-live="polite">
        {loading ? t('core.state.loading') : error || (graph ? t('core.insight.graphCount', { nodes: filtered.nodes.length, edges: filtered.edges.length }) : t('core.insight.graphEmpty'))}
      </div>
      <div className="insight-graph-body">
        <aside className="insight-graph-nodes" aria-label={t('core.insight.graphNodes')}>
          {filtered.nodes.map(node => <button key={node.key} type="button" className={`insight-graph-node-row ${node.role}`} disabled={!node.moduleId || !node.path} onClick={() => openNode(node)}>
            <strong>{node.label || node.id || node.path}</strong>
            <code>{node.key}</code>
            <span>{node.moduleId || node.idType}</span>
          </button>)}
        </aside>
        <DependencyGraphSvg nodes={filtered.nodes} edges={filtered.edges} onNode={openNode} onEdge={openEdge} />
      </div>
      <div className="insight-graph-details" aria-label={t('core.insight.graphDetails')}>
        {filtered.edges.length > 0 ? filtered.edges.map((edge, index) => <button key={`${edge.from}:${edge.to}:${edge.keyPath}:${index}`} type="button" className="insight-graph-edge-row" onClick={() => openEdge(edge)}>
          <span>{referenceEdgeLabel(edge.edgeType)}</span>
          <strong>{edge.path}</strong>
          <code>{edge.keyPath}</code>
          <small>{edge.snippet}</small>
        </button>) : !loading && !error ? <div className="insight-search-empty">{t('core.insight.graphEmpty')}</div> : null}
      </div>
    </section>
  </div>;
}

function filterDependencyGraph(nodes: InsightDependencyGraphNode[], edges: InsightDependencyGraphEdge[], moduleFilter: string, typeFilter: string): { nodes: InsightDependencyGraphNode[]; edges: InsightDependencyGraphEdge[] } {
  const nextNodes = nodes.filter(node => (!moduleFilter || !node.moduleId || node.moduleId === moduleFilter) && (!typeFilter || node.idType === typeFilter || node.role === 'root'));
  const keys = new Set(nextNodes.map(node => node.key));
  const nextEdges = edges.filter(edge => keys.has(edge.from) && keys.has(edge.to));
  return { nodes: nextNodes, edges: nextEdges };
}

function DependencyGraphSvg({ nodes, edges, onNode, onEdge }: { nodes: InsightDependencyGraphNode[]; edges: InsightDependencyGraphEdge[]; onNode: (node: InsightDependencyGraphNode) => void; onEdge: (edge: InsightDependencyGraphEdge) => void }) {
  const root = nodes.find(node => node.role === 'root') ?? nodes[0];
  const refs = nodes.filter(node => node.key !== root?.key);
  const width = 760;
  const rowHeight = 54;
  const height = Math.max(260, refs.length * rowHeight + 64);
  const rootPoint = { x: width - 190, y: height / 2 };
  const points = new Map<string, { x: number; y: number }>();
  if (root) points.set(root.key, rootPoint);
  refs.forEach((node, index) => points.set(node.key, { x: 170, y: 42 + index * rowHeight }));

  return <div className="insight-graph-canvas">
    <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={t('core.insight.graphCanvas')}>
      {edges.map((edge, index) => {
        const from = points.get(edge.from);
        const to = points.get(edge.to);
        if (!from || !to) return null;
        const mid = (from.x + to.x) / 2;
        return <g key={`${edge.from}:${edge.to}:${edge.keyPath}:${index}`} className="insight-graph-edge" onClick={() => onEdge(edge)}>
          <path d={`M ${from.x + 112} ${from.y} C ${mid} ${from.y}, ${mid} ${to.y}, ${to.x - 112} ${to.y}`} />
          <text x={mid} y={(from.y + to.y) / 2 - 6}>{referenceEdgeLabel(edge.edgeType)}</text>
        </g>;
      })}
      {nodes.map(node => {
        const point = points.get(node.key);
        if (!point) return null;
        const clickable = Boolean(node.moduleId && node.path);
        return <g key={node.key} className={`insight-graph-svg-node ${node.role}${clickable ? ' clickable' : ''}`} onClick={() => clickable && onNode(node)}>
          <rect x={point.x - 112} y={point.y - 18} width="224" height="36" rx="6" />
          <text x={point.x - 98} y={point.y - 3}>{node.label || node.id || node.path}</text>
          <text className="meta" x={point.x - 98} y={point.y + 12}>{node.moduleId || node.idType}</text>
        </g>;
      })}
    </svg>
  </div>;
}

function HistoryModal({ api, target, onCancel, onRolledBack }: { api: ApiClient; target: HistoryTarget; onCancel: () => void; onRolledBack: () => void | Promise<void> }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  const [entries, setEntries] = useState<HistoryEntry[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [snapshot, setSnapshot] = useState<HistorySnapshot | null>(null);
  const [diff, setDiff] = useState('');
  const parsedDiff = useMemo(() => parseUnifiedDiff(diff), [diff]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [currentRevision, setCurrentRevision] = useState<number | undefined>(target.revision);
  const [rollingBack, setRollingBack] = useState(false);
  const [rollbackConfirmOpen, setRollbackConfirmOpen] = useState(false);
  useDialogFocus(dialogRef, onCancel);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    setEntries([]);
    setSelectedId('');
    setSnapshot(null);
    setDiff('');
    void api.historyList(target.moduleId, target.path, target.kind)
      .then(result => {
        if (cancelled) return;
        setEntries(result.history);
        setCurrentRevision(result.revision ?? target.revision);
        const first = result.history[0]?.id ?? '';
        setSelectedId(first);
      })
      .catch(err => { if (!cancelled) setError(err instanceof Error ? err.message : t('core.api.requestFailed')); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [api, target.moduleId, target.path, target.kind]);

  useEffect(() => {
    if (!selectedId) {
      setSnapshot(null);
      setDiff('');
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError('');
    Promise.all([
      api.historySnapshot(target.moduleId, target.path, target.kind, selectedId),
      api.historyDiff(target.moduleId, target.path, target.kind, selectedId)
    ]).then(([nextSnapshot, nextDiff]) => {
      if (cancelled) return;
      setSnapshot(nextSnapshot);
      setDiff(nextDiff.diff);
    }).catch(err => {
      if (!cancelled) setError(err instanceof Error ? err.message : t('core.api.requestFailed'));
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [api, target.moduleId, target.path, target.kind, selectedId]);

  const selectedEntry = entries.find(entry => entry.id === selectedId) ?? snapshot?.entry;
  const rollbackAllowed = Boolean(selectedEntry?.rollbackAllowed && snapshot?.rollbackAllowed);

  function requestRollback() {
    if (!selectedId || !rollbackAllowed || rollingBack) return;
    setRollbackConfirmOpen(true);
  }

  async function confirmRollback() {
    if (!selectedId || !rollbackAllowed || rollingBack) return;
    setRollbackConfirmOpen(false);
    setRollingBack(true);
    setError('');
    try {
      await api.historyRollback(target.moduleId, target.path, target.kind, selectedId, currentRevision);
      await onRolledBack();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.history.rollbackFailed'));
    } finally {
      setRollingBack(false);
    }
  }

  return <>
  <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section ref={dialogRef} className="insight-search-dialog insight-reference-dialog history-dialog" role="dialog" aria-modal="true" aria-labelledby="history-title" tabIndex={-1}>
      <div className="insight-search-head">
        <span>{t('core.history.kicker')}</span>
        <h3 id="history-title">{t('core.history.title', { file: target.title })}</h3>
        <button type="button" className="insight-search-close" onClick={onCancel} aria-label={t('core.i18n.close')}>×</button>
      </div>
      <div className="insight-search-status" aria-live="polite">{loading ? t('core.state.loading') : error || t('core.history.count', { count: entries.length })}</div>
      <div className="insight-graph-body">
        <aside className="insight-graph-nodes" aria-label={t('core.history.entries')}>
          {entries.length > 0 ? entries.map(entry => <button key={entry.id} type="button" className={`insight-graph-node-row ${entry.id === selectedId ? 'root' : ''}`} onClick={() => setSelectedId(entry.id)}>
            <strong>{historyOperationLabel(entry.operation)}</strong>
            <code>{entry.id}</code>
            <span>{formatHistoryTime(entry.createdAt)} · {entry.actor || 'web'}{entry.rollbackAllowed === false ? ` · ${t('core.history.protected')}` : ''}</span>
          </button>) : !loading && <div className="insight-search-empty">{t('core.history.empty')}</div>}
        </aside>
        <div className="insight-graph-canvas history-preview">
          {selectedEntry ? <div className="history-preview-head">
            <strong>{historyOperationLabel(selectedEntry.operation)} · {selectedEntry.id}</strong>
            <span>{formatHistoryTime(selectedEntry.createdAt)} · {selectedEntry.actor || 'web'}</span>
            {!rollbackAllowed && <small>{t('core.history.rollbackDisabled')}</small>}
          </div> : null}
          {parsedDiff.length > 0 ? <UnifiedDiffView diff={parsedDiff} className="history-diff history-diff--changes-only" maxLines={240} hideContext /> : <pre>{snapshot?.content || t('core.history.noDiff')}</pre>}
        </div>
      </div>
      <ActionGroup className="reload-confirm-actions">
        <Button type="button" onClick={onCancel}>{t('core.gui.cancel')}</Button>
        <Button type="button" variant="danger" disabled={!rollbackAllowed || rollingBack} onClick={requestRollback}>{rollingBack ? t('core.state.loading') : t('core.history.rollback')}</Button>
      </ActionGroup>
    </section>
  </div>
  {rollbackConfirmOpen && selectedEntry && <RollbackConfirmModal
    target={target}
    entry={selectedEntry}
    revision={currentRevision}
    rollingBack={rollingBack}
    onCancel={() => setRollbackConfirmOpen(false)}
    onConfirm={() => void confirmRollback()}
  />}
  </>;
}

function RollbackConfirmModal({ target, entry, revision, rollingBack, onCancel, onConfirm }: { target: HistoryTarget; entry: HistoryEntry; revision?: number; rollingBack: boolean; onCancel: () => void; onConfirm: () => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section ref={dialogRef} className="reload-confirm-dialog diff-dialog history-rollback-dialog" role="dialog" aria-modal="true" aria-labelledby="history-rollback-title" aria-describedby="history-rollback-desc" tabIndex={-1}>
      <div className="reload-confirm-head diff-dialog-head">
        <span>{t('core.history.rollbackKicker')}</span>
        <h3 id="history-rollback-title">{t('core.history.rollbackTitle')}</h3>
      </div>
      <div className="reload-confirm-body diff-dialog-body">
        <p id="history-rollback-desc">{t('core.history.rollbackDesc')}</p>
        <dl className="history-rollback-summary">
          <div><dt>{t('core.history.rollbackFile')}</dt><dd><code>{target.path}</code></dd></div>
          <div><dt>{t('core.history.rollbackEntry')}</dt><dd><code>{entry.id}</code></dd></div>
          <div><dt>{t('core.history.rollbackOperation')}</dt><dd>{historyOperationLabel(entry.operation)}</dd></div>
          <div><dt>{t('core.history.rollbackActor')}</dt><dd>{entry.actor || 'web'}</dd></div>
          <div><dt>{t('core.history.rollbackRevision')}</dt><dd>{revision ?? '-'}</dd></div>
          <div><dt>{t('core.history.rollbackTime')}</dt><dd>{formatHistoryTime(entry.createdAt)}</dd></div>
        </dl>
      </div>
      <ActionGroup className="reload-confirm-actions diff-dialog-actions">
        <Button type="button" onClick={onCancel} autoFocus>{t('core.gui.cancel')}</Button>
        <Button type="button" variant="danger" onClick={onConfirm} disabled={rollingBack}>{rollingBack ? t('core.state.loading') : t('core.history.rollbackConfirmAction')}</Button>
      </ActionGroup>
    </section>
  </div>;
}

function historyOperationLabel(operation: string | undefined): string {
  const key = String(operation ?? 'save').toLowerCase();
  return t(`core.history.operation.${key}`, undefined, key);
}

function formatHistoryTime(value: unknown): string {
  const time = typeof value === 'number' ? value : Number(value ?? 0);
  if (!Number.isFinite(time) || time <= 0) return '-';
  return new Date(time).toLocaleString();
}

function ExtensionHealthBanner({ health, statuses, onRetry }: { health: 'idle' | 'loading' | 'ok' | 'failed'; statuses: WebConsoleExtensionStatus[]; onRetry: () => void }) {
  if (health === 'idle' || health === 'ok') return null;
  const failed = statuses.filter(status => status.status === 'failed');
  const label = health === 'loading'
    ? t('core.state.loading')
    : t('core.toast.extensionLoadFailed', { count: failed.length || statuses.length || 1 });

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
  if (value.startsWith('/') || value.endsWith('/') || value.includes('..')) return { ok: false, message: t('core.file.nameInvalid') };
  const illegal = Array.from(new Set(Array.from(value).filter(char => /[<>:"|?*]/.test(char) || char.charCodeAt(0) < 32)));
  if (illegal.length) return { ok: false, message: t('core.file.nameIllegalChars', { chars: illegal.join(' ') }) };
  if (/<\s*\/?\s*[a-z][^>]*>/i.test(value)) return { ok: false, message: t('core.file.nameHtmlTag') };
  if (value.split('/').some(part => !part || part === '.' || part === '..')) return { ok: false, message: t('core.file.nameInvalid') };
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

function SurfaceHost({ registry, module, file, drafts, draftHistory, setDraftValue, clearDraftScope, clearDraftValues, clearDraftPaths, reconcileScopeDrafts, setSaveConflict, undoDraftScope, redoDraftScope, api, scriptPath, refreshKey, pendingExtensionModules, onReload, onRefreshRegistry, setSurfaceToolbar, setSurfaceOutline, setToast }: { registry: WebRegistry | null; module: WebRegistryModule | null; file: WebRegistryFile | null; drafts: DraftMap; draftHistory: DraftHistoryMap; setDraftValue: DraftValueSetter; clearDraftScope: DraftScopeAction; clearDraftValues: DraftScopeAction; clearDraftPaths: DraftPathsAction; reconcileScopeDrafts: ConfigSaveSafety['reconcileScopeDrafts']; setSaveConflict: ConfigSaveSafety['setSaveConflict']; undoDraftScope: DraftScopeAction; redoDraftScope: DraftScopeAction; api: ApiClient; scriptPath?: string; refreshKey: number; pendingExtensionModules: ReadonlySet<string>; onReload?: () => void; onRefreshRegistry: () => Promise<WebRegistry | null>; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setSurfaceOutline: (state: SurfaceOutlineState) => void; setToast: (toast: Toast) => void }) {
  useEffect(() => {
    setSurfaceToolbar(null);
    setSurfaceOutline(null);
    return () => { setSurfaceToolbar(null); setSurfaceOutline(null); };
  }, [module?.id, file?.id, scriptPath]);


  if (registry && registry.modules.length === 0) return <section className="config-surface empty" role="status">{t('core.empty.noRegistry')}</section>;
  if (!module || !file) return <section className="config-surface empty" role="status">{t('core.empty.selectConfig')}</section>;
  const editor = file.editorId ? registry?.editors?.[file.editorId] : undefined;

  // Check registry for a custom surface first
  const registeredSurface = getSurface({ ...file, moduleId: module.id }, editor);
  if (extensionSurfacePending(module, file, editor, registeredSurface, pendingExtensionModules)) {
    return <section className="config-surface empty" role="status">{t('core.extension.loadingEditor', undefined, 'Loading plugin editor…')}</section>;
  }
  if (registeredSurface && !isKind(file.kind, 'CONFIG') && !isKind(file.kind, 'SCRIPT')) {
    const SurfaceComponent = registeredSurface.component;
    const outlineSetter = isKind(file.kind, 'GUI') ? undefined : setSurfaceOutline;
    const surfacePath = resolveSurfaceDocumentPath(file, scriptPath);
    if (isGlobPath(file.path) && !surfacePath) {
      return <section className="config-surface"><div className="surface-head"><div><h2>{fileDisplayTitle(file)}</h2><p>{fileDisplayComment(file)}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><div className="script-placeholder" role="status">{t('core.empty.selectFile')}</div></section>;
    }
    const surfaceChildPath = isGlobPath(file.path) ? surfacePath : scriptPath;
    return <Suspense fallback={<section className="config-surface empty" role="status">{t('core.extension.loadingEditor', undefined, 'Loading editor…')}</section>}><SurfaceComponent module={module} file={file} api={api} childPath={surfaceChildPath} refreshKey={refreshKey} editor={editor} onReload={onReload} setToolbar={setSurfaceToolbar} setOutline={outlineSetter} showLocalChrome={false} /></Suspense>;
  }
  if (isKind(file.kind, 'CONFIG')) return <ConfigSurface module={module} file={file} drafts={drafts} draftHistory={draftHistory} setDraftValue={setDraftValue} clearDraftScope={clearDraftScope} clearDraftValues={clearDraftValues} clearDraftPaths={clearDraftPaths} reconcileScopeDrafts={reconcileScopeDrafts} setSaveConflict={setSaveConflict} undoDraftScope={undoDraftScope} redoDraftScope={redoDraftScope} api={api} scriptPath={scriptPath} refreshKey={refreshKey} onRefreshRegistry={onRefreshRegistry} setSurfaceToolbar={setSurfaceToolbar} setSurfaceOutline={setSurfaceOutline} setToast={setToast} />;
  if (!scriptPath && isGlobPath(file.path)) {
    return <section className="config-surface"><div className="surface-head"><div><h2>{fileDisplayTitle(file)}</h2><p>{fileDisplayComment(file)}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><div className="script-placeholder" role="status">{t('core.empty.selectFile')}</div></section>;
  }

  if (isKind(file.kind, 'SCRIPT')) return <section className="config-surface script-surface"><div className="surface-head"><div><h2>{fileDisplayTitle(file)}</h2><p>{fileDisplayComment(file)}</p></div><span className="file-kind script">{fileKindLabel(file.kind)}</span></div>{scriptPath ? <ScriptSurface api={api} scriptPath={scriptPath} module={module} file={file} setSurfaceToolbar={setSurfaceToolbar} setToast={setToast} /> : <div className="script-placeholder" role="status">{t('core.empty.selectScript')}</div>}</section>;
  return <section className="config-surface empty" role="status">{t('core.empty.selectConfig')}</section>;
}


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
function sourceEditingElement(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  const tag = target.tagName.toLowerCase();
  return tag === 'input' || tag === 'textarea' || tag === 'select' || target.isContentEditable;
}

function enhanceRegistry(registry: WebRegistry): WebRegistry { return applyConfigRegistryOverrides(applyEditorDescriptorOverrides(registry)); }
function firstSelection(r: WebRegistry): Selection | null {
  for (const module of r.modules) {
    for (const file of module.files) {
      if (!isGlobPath(file.path)) return { moduleId: module.id, fileId: file.id };
      for (const child of file.children ?? []) {
        const resolved = resolveConcreteChildPath(module, child.relativePath);
        if (resolved?.file.id === file.id) return { moduleId: module.id, fileId: file.id, scriptPath: resolved.path };
      }
    }
  }
  return null;
}
function selectionExists(registry: WebRegistry, selection: Selection): boolean {
  const module = registry.modules.find(entry => entry.id === selection.moduleId);
  const file = module?.files.find(entry => entry.id === selection.fileId);
  if (!module || !file) return false;
  if (!selection.scriptPath) return !isGlobPath(file.path);
  const resolved = resolveConcreteChildPath(module, selection.scriptPath);
  return Boolean(resolved && resolved.file.id === file.id);
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
