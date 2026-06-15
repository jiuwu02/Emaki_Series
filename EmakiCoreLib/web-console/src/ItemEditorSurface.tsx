import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError, type ApiClient, type ActionTypesResult } from './api';
import { Button, CollapsibleSection, ChangedPathsProvider, DisclosureChevron, EditorChrome, InlineError, KvTable, MiniText, NumberListEditor, PropRow as BasePropRow, SectionHead, StandardActionsField, StandardEconomyProviderSelect, StandardEffectsEditor, StringListEditor, ToastNotice, VariablesMapEditor, parseActionList, type ActionEntry } from './components';
import { asList, asRecord, asStringList, displaySource, firstItemSource, materialFromItemSource, setDeepValue, parseYaml, type AnyMap } from './itemEditor';
import { isConcretePath, isGlobPath, resolveSurfaceDocumentPath } from './documentPaths';
import { t, getLocale } from './i18n';
import { changedPathSet, diffRecords, fieldLabel, getDeepValue, humanizeFieldLabel, isChangedFieldPath, materialShortName, materialUrls, optionLabel, subscribeTextureBases, textValue, valuesEqual } from './lib';
import { MINECRAFT_MATERIALS, searchMaterials } from './minecraftMaterials';
import { getSourceDocumentAdapter, isKind, type SurfaceOutlineState, type SurfaceToolbarState } from './registry';
import { fileDisplayTitle } from './lib';
import { CORE_ITEM_FIELD_TYPE_SET, standardDisplayActionFields } from './itemFieldKit';
import { getEffectTypeDefinition } from './effectTypeRegistry';
import { getItemFieldRenderer, getItemPreviewFallback } from './itemFieldRegistry';
import type { ItemPreviewResult, ItemPreviewStep, WebEditorDescriptor, WebEditorField, WebEditorSection, WebRegistryFile, WebRegistryModule } from './types';
import { serializeItemYaml } from './itemEditor';

type Props = { module: WebRegistryModule; file: WebRegistryFile; api: ApiClient; childPath?: string; refreshKey?: number; editor?: WebEditorDescriptor; onReload?: () => void; setToolbar?: (state: SurfaceToolbarState | null) => void; setOutline?: (state: SurfaceOutlineState) => void; showLocalChrome?: boolean };
type PreviewError = { message: string; detail?: string };
type SnapshotHistory = { undo: AnyMap[]; redo: AnyMap[] };
const DEFAULT_BASE_NAME = t('core.item.defaultBaseName');
const DEFAULT_BASE_LORE = t('core.item.defaultBaseLore');
const DEFAULT_ECONOMY_PROVIDERS = ['auto', 'vault', 'excellenteconomy'];
const COLLAPSIBLE_SECTION_FIELD_THRESHOLD = 10;

const EditorContext = React.createContext<{
  moduleId: string;
  editorFields: Record<string, WebEditorField>;
  changedPaths: Set<string>;
  economyProviders: string[];
}>({ moduleId: '', editorFields: {}, changedPaths: new Set(), economyProviders: DEFAULT_ECONOMY_PROVIDERS });

export function ItemEditorSurface({ module, file, api, childPath, refreshKey = 0, editor, onReload, setToolbar, setOutline, showLocalChrome = true }: Props) {
  const [data, setData] = useState<AnyMap>({});
  const [originalData, setOriginalData] = useState<AnyMap>({});
  const [originalContent, setOriginalContent] = useState('');
  const [revision, setRevision] = useState<number | undefined>(undefined);
  const [preview, setPreview] = useState<ItemPreviewResult | null>(null);
  const [previewLevel, setPreviewLevel] = useState(1);
  const [previewPending, setPreviewPending] = useState(false);
  const [previewError, setPreviewError] = useState<PreviewError | null>(null);
  const [layerPreview, setLayerPreview] = useState<AnyMap | null>(null);
  const [layerPreviewPending, setLayerPreviewPending] = useState(false);
  const [layerPreviewError, setLayerPreviewError] = useState<string | null>(null);
  const [layerOptions, setLayerOptions] = useState<AnyMap>({});
  const previewRequestId = useRef(0);
  const layerPreviewRequestId = useRef(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sourceText, setSourceText] = useState('');
  const [sourceError, setSourceError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ tone: 'ok' | 'bad'; text: string } | null>(null);
  const [history, setHistory] = useState<SnapshotHistory>({ undo: [], redo: [] });

  const [actionTypesResult, setActionTypesResult] = useState<ActionTypesResult | null>(null);
  const [economyProviders, setEconomyProviders] = useState<string[]>(DEFAULT_ECONOMY_PROVIDERS);

  const filePath = useMemo(() => resolveSurfaceFilePath(file, childPath), [file, childPath]);
  const fileTitle = fileDisplayTitle(file);
  const baseName = editor?.baseName ?? DEFAULT_BASE_NAME;
  const baseLore = useMemo(() => editor?.baseLore ?? [DEFAULT_BASE_LORE], [editor?.baseLore]);
  const sections = useMemo(() => editor?.sections?.length ? editor.sections : defaultSections(), [editor]);
  const editorFields = useMemo(() => editorFieldMap(editor), [editor]);
  const sourceAdapter = getSourceDocumentAdapter(file, editor);
  const itemLikeKind = isKind(file.kind, 'ITEM') || isKind(file.kind, 'GEM');
  const resolvedChildPath = childPath && isConcretePath(childPath) ? childPath : (isGlobPath(file.path) && isConcretePath(filePath) ? filePath : undefined);
  const sourceContext = useMemo(() => ({ module, file, childPath: resolvedChildPath, path: filePath, editor }), [module, file, resolvedChildPath, filePath, editor?.id]);
  const draftContent = useMemo(() => sourceError ? sourceText : serializeItemYaml(data), [sourceError, sourceText, data]);
  const sourceContent = draftContent;
  const changes = useMemo(() => diffRecords(data, originalData, '', 18), [data, originalData]);
  const changedPaths = useMemo(() => changedPathSet(changes), [changes]);
  const semanticDirty = !sourceError && changes.length > 0;
  const editorContext = useMemo(() => ({ moduleId: module.id, editorFields, changedPaths, economyProviders }), [module.id, editorFields, changedPaths, economyProviders]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    let cancelled = false;
    if (!filePath || isGlobPath(filePath)) {
      setLoading(false);
      setError(t('core.empty.selectFile'));
      return;
    }
    setLoading(true);
    setError(null);
    const readDocument = sourceAdapter
      ? sourceAdapter.read(api, sourceContext)
      : api.readTextDocument({ kind: file.kind, moduleId: module.id, path: filePath });
    readDocument.then(doc => {
      if (cancelled) return;
      try {
        const parsed = parseYaml(doc.content) as AnyMap;
        setData(parsed);
        setOriginalData(parsed);
        setOriginalContent(doc.content);
        setRevision(doc.revision);
        setSourceText(doc.content);
        setSourceError(null);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        setData({});
        setOriginalData({});
        setOriginalContent(doc.content);
        setRevision(doc.revision);
        setSourceText(doc.content);
        setSourceError(message);
        void api.reportFrontendError({ message, source: 'item-yaml-parse', detail: `${module.id}/${filePath}` });
      }
      setHistory({ undo: [], redo: [] });
      setLoading(false);
    }).catch(err => {
      if (cancelled) return;
      setError(String(err?.message ?? err));
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [api, module.id, file.kind, filePath, refreshKey, sourceAdapter, sourceContext]);

  useEffect(() => {
    api.actionTypes().then(setActionTypesResult).catch(err => void api.reportFrontendError({ message: err instanceof Error ? err.message : String(err), source: 'item-action-types', detail: module.id }));
    api.economyProviders().then(result => setEconomyProviders(mergeOptions(result.providers, DEFAULT_ECONOMY_PROVIDERS))).catch(() => setEconomyProviders(DEFAULT_ECONOMY_PROVIDERS));
  }, [api]);

  useEffect(() => {
    if (loading) return;
    if (!itemLikeKind) {
      previewRequestId.current += 1;
      setPreview(null);
      setPreviewPending(false);
      return;
    }
    const content = sourceContent;
    const previewBaseLore = resolvePreviewBaseLore(data, baseLore as string[]);
    const requestedLevel = previewLevel;
    const requestId = previewRequestId.current + 1;
    previewRequestId.current = requestId;
    setPreview(null);
    setPreviewError(null);
    setPreviewPending(true);
    let active = true;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      api.previewItem(content, requestedLevel, baseName, previewBaseLore, { signal: controller.signal })
        .then(nextPreview => {
          if (!active || previewRequestId.current !== requestId) return;
          setPreview(nextPreview);
        })
        .catch(err => {
          if (!active || previewRequestId.current !== requestId || isAbortError(err)) return;
          setPreviewError(previewErrorFromUnknown(err));
          setPreview(localItemPreview(module.id, editor?.id, file.kind, data, requestedLevel, baseName, previewBaseLore));
        })
        .finally(() => {
          if (active && previewRequestId.current === requestId) setPreviewPending(false);
        });
    }, 300);
    return () => { active = false; window.clearTimeout(timer); controller.abort(); };
  }, [api, data, sourceContent, previewLevel, loading, baseName, baseLore, file.kind, itemLikeKind]);

  useEffect(() => {
    const levels = configuredPreviewLevels(data, preview);
    if (!levels.length) {
      if (previewLevel !== 1) setPreviewLevel(1);
      return;
    }
    if (!levels.includes(previewLevel)) setPreviewLevel(levels[0]);
  }, [data, preview, previewLevel]);

  useEffect(() => {
    const previewConfig = asRecord(editor?.preview);
    const layeredRoute = textValue(previewConfig.layeredRoute);
    if (loading || !itemLikeKind || !layeredRoute) {
      layerPreviewRequestId.current += 1;
      setLayerPreview(null);
      setLayerPreviewPending(false);
      setLayerPreviewError(null);
      return;
    }
    const layeredModule = textValue(previewConfig.layeredModule || module.id);
    const requestId = layerPreviewRequestId.current + 1;
    layerPreviewRequestId.current = requestId;
    setLayerPreviewPending(true);
    setLayerPreviewError(null);
    let active = true;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      api.pluginApi(layeredModule, layeredRoute, { content: sourceContent, itemId: textValue(data.id), path: filePath, layers: layerOptions }, { signal: controller.signal })
        .then(result => {
          if (!active || layerPreviewRequestId.current !== requestId) return;
          setLayerPreview(compactLayerPreview(result));
        })
        .catch(err => {
          if (!active || layerPreviewRequestId.current !== requestId || isAbortError(err)) return;
          setLayerPreview(null);
          setLayerPreviewError(err instanceof Error ? err.message : String(err));
        })
        .finally(() => {
          if (active && layerPreviewRequestId.current === requestId) setLayerPreviewPending(false);
        });
    }, 350);
    return () => { active = false; window.clearTimeout(timer); controller.abort(); };
  }, [api, data, editor?.preview, file.kind, itemLikeKind, filePath, layerOptions, loading, module.id, sourceContent]);

  const setField = (path: string, value: unknown) => {
    setData(prev => {
      const next = setDeepValue(prev, path.split('.'), value);
      if (!valuesEqual(prev, next)) rememberHistory(prev);
      setSourceText(serializeItemYaml(next));
      setSourceError(null);
      return next;
    });
  };

  const updateSource = (nextSource: string) => {
    setSourceText(nextSource);
    try {
      const parsed = parseYaml(nextSource) as AnyMap;
      if (!valuesEqual(data, parsed)) rememberHistory(data);
      setData(parsed);
      setSourceError(null);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setSourceError(message);
      void api.reportFrontendError({ message, source: 'item-source-yaml-parse', detail: `${module.id}/${filePath}` });
    }
  };

  function rememberHistory(snapshot: AnyMap) {
    setHistory(current => ({ undo: [...current.undo, snapshot].slice(-20), redo: [] }));
  }

  function applySnapshot(snapshot: AnyMap) {
    setData(snapshot);
    setSourceText(serializeItemYaml(snapshot));
    setSourceError(null);
  }

  function undo() {
    const snapshot = history.undo[history.undo.length - 1];
    if (!snapshot) return;
    setHistory(current => ({ undo: current.undo.slice(0, -1), redo: [data, ...current.redo].slice(0, 20) }));
    applySnapshot(snapshot);
  }

  function redo() {
    const snapshot = history.redo[0];
    if (!snapshot) return;
    setHistory(current => ({ undo: [...current.undo, data].slice(-20), redo: current.redo.slice(1) }));
    applySnapshot(snapshot);
  }

  const handleSave = async () => {
    if (saving || sourceError || !semanticDirty) return;
    setSaving(true);
    setError(null);
    try {
      const content = sourceContent;
      const result = await (sourceAdapter?.save(api, sourceContext, content, revision) ?? api.saveTextDocument({ kind: file.kind, moduleId: module.id, path: filePath }, content, revision));
      setOriginalContent(content);
      setRevision(result.revision ?? revision);
      setOriginalData(data);
      setSourceText(content);
      setHistory({ undo: [], redo: [] });
      setToast({ tone: 'ok', text: t('core.toast.savedItem') });
    } catch (err: any) {
      setError(err?.message ?? t('core.toast.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  useEffect(() => {
    if (!setToolbar) return;
    setToolbar({
      title: editor?.title ?? fileTitle ?? t('core.item.editorTitle'),
      subtitle: `${module.id}/${filePath}`,
      dirty: semanticDirty,
      changes,
      source: sourceContent,
      sourceOriginal: originalContent,
      sourceEditable: true,
      sourceError,
      sourceLanguage: 'yaml',
      saving,
      loading,
      canUndo: history.undo.length > 0,
      canRedo: history.redo.length > 0,
      onUndo: undo,
      onRedo: redo,
      onReload,
      onSourceChange: updateSource,
      onSave: handleSave
    });
  }, [setToolbar, editor?.title, fileTitle, module.id, filePath, semanticDirty, changes, sourceContent, sourceError, saving, loading, history.undo.length, history.redo.length, onReload]);

  useEffect(() => () => setToolbar?.(null), [setToolbar]);

  useEffect(() => {
    if (!setOutline) return;
    setOutline({
      title: localizedEditorTitle(editor, fileTitle ?? t('core.item.editorTitle')),
      subtitle: `${module.id}/${filePath}`,
      emptyText: t('core.outline.empty'),
      items: sections.map((section, index) => itemSectionOutline(module.id, section, index, changedPaths))
    });
    return () => setOutline(null);
  }, [setOutline, editor?.title, editor?.titleKey, fileTitle, module.id, filePath, sections, changedPaths]);

  if (isGlobPath(filePath)) return <div className="ie-surface"><InlineError>{t('core.empty.selectFile')}</InlineError></div>;
  if (loading) return <div className="ie-surface"><div className="ie-loading" role="status"><div className="ie-skeleton" aria-label={t('core.item.loadingAria')}><div className="ie-skeleton-line" style={{ width: '60%' }} /><div className="ie-skeleton-line" style={{ width: '80%' }} /><div className="ie-skeleton-line" style={{ width: '45%' }} /><div className="ie-skeleton-line" style={{ width: '70%' }} /></div></div></div>;
  if (error && !data) return <div className="ie-surface"><InlineError>{error}</InlineError>{onReload && <Button size="sm" onClick={onReload}>{t('core.action.retry')}</Button>}</div>;

  return (
    <div className="ie-surface" data-dirty={semanticDirty || undefined} data-original-size={originalContent.length || undefined}>
      {toast && <ToastNotice tone={toast.tone} style={{ position: 'absolute', top: 12, right: 12, zIndex: 50 }}>{toast.text}</ToastNotice>}
      {showLocalChrome && <EditorChrome
        className="ie-header"
        title={localizedEditorTitle(editor, fileTitle ?? t('core.item.editorTitle'))}

        subtitle={`${module.id}/${filePath}`}
        dirty={semanticDirty}
        changes={changes}
        source={sourceContent}
        sourceEditable
        sourceError={sourceError}
        sourceLanguage="yaml"
        saving={saving}
        canUndo={history.undo.length > 0}
        canRedo={history.redo.length > 0}
        onUndo={undo}
        onRedo={redo}
        onReload={onReload}
        onSourceChange={updateSource}
        onSave={handleSave}
      />}

      {error && <InlineError>{error}</InlineError>}

      <EditorContext.Provider value={editorContext}>
        <ChangedPathsProvider changedPaths={changedPaths}>
        <div className="ie-workbench">
          <div className="ie-preview-stack">
            <div className="ie-preview ie-preview-combined" role="complementary" aria-label={t('core.item.previewAria')}>
              <GenericPreviewPane moduleId={module.id} fileKind={file.kind} editor={editor} data={data} preview={preview} layerPreview={layerPreview} previewPending={previewPending} previewError={previewError} previewLevel={previewLevel} setPreviewLevel={setPreviewLevel} baseName={baseName} baseLore={baseLore as string[]} />
              <LayeredPreviewPane preview={layerPreview} pending={layerPreviewPending} error={layerPreviewError} options={layerOptions} onOptionsChange={setLayerOptions} />
              <RenameMigrationPane api={api} editor={editor} moduleId={module.id} currentId={textValue(data.id)} sourceError={sourceError} dirty={semanticDirty} onApplied={() => { setToast({ tone: 'ok', text: '重命名迁移已应用' }); onReload?.(); }} />
            </div>
          </div>
          <div className="ie-props-scroll">
            <div className="ie-props">
              {sections.map((section, sectionIndex) => (
                <div className="ie-outline-anchor" data-config-node-path={itemSectionPath(section, sectionIndex)} key={section.title || sectionIndex}>
                  <CollapsibleSection
                    title={localizedSectionTitle(module.id, section)}
                    comment={localizedSectionComment(module.id, section)}
                    collapsible={section.collapsible ?? true}
                    defaultCollapsed={editorSectionDefaultCollapsed(section, data)}
                    storageKey={`core:item-section:${editor?.id ?? file.editorId ?? file.kind}:${section.title}`}
                  >
                    {section.fields.map(field => <div className="ie-outline-anchor" data-config-node-path={field.path} key={field.path}><FieldEditor field={field} data={data} originalData={originalData} setField={setField} actionTypesResult={actionTypesResult} editorId={editor?.id ?? file.editorId} /></div>)}
                  </CollapsibleSection>
                </div>
              ))}
            </div>
          </div>
        </div>
        </ChangedPathsProvider>
      </EditorContext.Provider>
    </div>
  );
}

function resolveSurfaceFilePath(file: WebRegistryFile, childPath?: string): string {
  return resolveSurfaceDocumentPath(file, childPath) ?? '';
}

function FieldEditor({ field, data, originalData, setField, actionTypesResult, editorId }: { field: WebEditorField; data: AnyMap; originalData: AnyMap; setField: (path: string, value: unknown) => void; actionTypesResult: ActionTypesResult | null; editorId?: string }) {
  const context = React.useContext(EditorContext);
  const value = getDeepValue(data, field.path);
  const changed = !valuesEqual(value, getDeepValue(originalData, field.path));
  const label = fieldLabel(field.path, { moduleId: context.moduleId, namespace: context.moduleId, editorFields: context.editorFields, fallback: getLocale().startsWith('zh') ? (field.label || field.path) : humanizeFieldLabel(field.path) });
  const type = field.type || 'text';
  const editorMeta = (context.editorFields as AnyMap).__meta as AnyMap | undefined;
  const allowedFieldTypes = asStringList((field as AnyMap).allowedFieldTypes ?? editorMeta?.allowedFieldTypes);
  const customRenderer = getItemFieldRenderer(type, context.moduleId, editorId);
  const renderDefault = () => <DefaultFieldEditor field={field} data={data} value={value} changed={changed} setField={setField} actionTypesResult={actionTypesResult} />;
  if (customRenderer) return <>{customRenderer({ data, originalData, field, value, changed, actionTypesResult, economyProviders: context.economyProviders, editorFields: context.editorFields, moduleId: context.moduleId, setField, renderDefault })}</>;

  return renderDefault();
}

function DefaultFieldEditor({ field, data, value, changed, setField, actionTypesResult }: { field: WebEditorField; data: AnyMap; value: unknown; changed: boolean; setField: (path: string, value: unknown) => void; actionTypesResult: ActionTypesResult | null }) {
  const context = React.useContext(EditorContext);
  const label = fieldLabel(field.path, { moduleId: context.moduleId, namespace: context.moduleId, editorFields: context.editorFields, fallback: getLocale().startsWith('zh') ? (field.label || field.path) : humanizeFieldLabel(field.path) });
  const type = field.type || 'text';
  const editorMeta = (context.editorFields as AnyMap).__meta as AnyMap | undefined;
  const allowedFieldTypes = asStringList((field as AnyMap).allowedFieldTypes ?? editorMeta?.allowedFieldTypes);
  const moduleTypeAllowed = CORE_ITEM_FIELD_TYPE_SET.has(type) || allowedFieldTypes.includes(type);

  if (!moduleTypeAllowed) return <PropRow label={label} path={field.path} changed={changed} wide><GenericObjectEditor value={value} onChange={next => setField(field.path, next)} /></PropRow>;

  if (type === 'number') return <PropRow label={label} path={field.path} changed={changed} wide={field.wide}><NumberInput value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'boolean') return <PropRow label={label} path={field.path} changed={changed} wide={field.wide}><ToggleButton checked={value === true} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'enum' && field.options?.length) return <PropRow label={label} path={field.path} changed={changed} wide={field.wide}><SelectInput value={value} options={field.options} labelPrefix={field.optionLabelPrefix} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'economyProvider') return <PropRow label={label} path={field.path} changed={changed} wide={field.wide}><StandardEconomyProviderSelect value={value} onChange={next => setField(field.path, next)} providers={context.economyProviders} moduleId={context.moduleId} optionPrefix={field.optionLabelPrefix || 'economyProvider'} /></PropRow>;
  if (type === 'multiEnum' && field.options?.length) return <PropRow label={label} path={field.path} changed={changed} wide={field.wide ?? true}><MultiEnumEditor value={value} options={field.options} labelPrefix={field.optionLabelPrefix} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'material') return <PropRow label={label} path={field.path} changed={changed} wide={field.wide}><MaterialInput value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'textarea') return <PropRow label={label} path={field.path} changed={changed} wide><textarea rows={field.rows ?? 4} value={textValue(value)} onChange={e => setField(field.path, e.target.value)} placeholder={field.placeholder} /></PropRow>;
  if (type === 'stringList') return <PropRow label={label} path={field.path} changed={changed} wide><StringListEditor items={asEditableStringList(value)} onChange={items => setField(field.path, items)} placeholder={field.placeholder} /></PropRow>;
  if (type === 'numberList') return <PropRow label={label} path={field.path} changed={changed} wide><NumberListEditor items={asList(value).map(item => Number(item) || 0)} onChange={items => setField(field.path, items)} /></PropRow>;
  if (type === 'variablesMap') return <PropRow label={label} path={field.path} changed={changed} wide><VariablesMapEditor value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'map' || type === 'dynamicMap' || type === 'objectMap') return <PropRow label={label} path={field.path} changed={changed} wide><MapEditor value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'actions') return <PropRow label={label} path={field.path} changed={changed} wide><StandardActionsField value={value} onChange={next => setField(field.path, next)} path={field.path} moduleId={context.moduleId} namespace={context.moduleId} editorFields={context.editorFields} actionTypes={actionTypesResult ?? undefined} /></PropRow>;
  if (type === 'effects') return <PropRow label={label} path={field.path} changed={false} wide><StandardEffectsEditor value={value} path={field.path} onChange={next => setField(field.path, next)} moduleId={context.moduleId} namespace={context.moduleId} editorFields={context.editorFields} actionTypes={actionTypesResult ?? undefined} /></PropRow>;
  if (type === 'json') return <PropRow label={label} path={field.path} changed={changed} wide><GenericObjectEditor value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  return <PropRow label={label} path={field.path} changed={changed} wide={field.wide}><input type="text" value={textValue(value)} onChange={e => setField(field.path, e.target.value)} placeholder={field.placeholder} /></PropRow>;
}

function PropRow({ label, path, children, wide, changed }: { label: string; path?: string; children: React.ReactNode; wide?: boolean; changed?: boolean }) {
  const context = React.useContext(EditorContext);
  const rowPath = path ?? label;
  return <BasePropRow
    label={label}
    path={rowPath}
    moduleId={context.moduleId}
    namespace={context.moduleId}
    editorFields={context.editorFields}
    changed={changed ?? isChangedFieldPath(rowPath, context.changedPaths)}
    wide={wide}
  >{children}</BasePropRow>;
}

function ToggleButton({ id, checked, onChange }: { id?: string; checked: boolean; onChange: (next: boolean) => void }) {
  return <button id={id} type="button" className={`switch ${checked ? 'on' : ''}`} aria-pressed={checked} onClick={() => onChange(!checked)}>
    <span className="switch-icon" aria-hidden="true">
      {checked ? <svg viewBox="0 0 16 16" focusable="false"><path d="M3.5 8.2 6.7 11.2 12.8 4.8" /></svg> : <svg viewBox="0 0 16 16" focusable="false"><path d="M4.7 4.7 11.3 11.3M11.3 4.7 4.7 11.3" /></svg>}
    </span>
    {checked ? t('core.config.booleanOn') : t('core.config.booleanOff')}
  </button>;
}

function NumberInput({ id, value, onChange, step }: { id?: string; value: unknown; onChange: (value: number | undefined) => void; step?: number | string }) {
  return <input id={id} type="number" step={step} value={value == null ? '' : textValue(value)} onChange={event => onChange(event.target.value === '' ? undefined : Number(event.target.value))} />;
}

function SelectInput({ id, value, options, onChange, labelPrefix }: { id?: string; value: unknown; options: string[]; onChange: (value: string) => void; labelPrefix?: string }) {
  const context = React.useContext(EditorContext);
  const current = textValue(value);
  const merged = current && !options.includes(current) ? [...options, current] : options;
  return <select id={id} value={current} onChange={event => onChange(event.target.value)}>{merged.map(option => <option key={option} value={option}>{labelPrefix ? optionLabel(labelPrefix, option, { moduleId: context.moduleId, namespace: context.moduleId, fallback: option }) : option}</option>)}</select>;
}

function MultiEnumEditor({ value, options, onChange, labelPrefix }: { value: unknown; options: string[]; onChange: (value: string[]) => void; labelPrefix?: string }) {
  const context = React.useContext(EditorContext);
  const selected = new Set(asStringList(value).map(entry => entry.trim()).filter(Boolean));
  const unknown = Array.from(selected).filter(entry => !options.includes(entry));
  const toggle = (option: string) => {
    const next = new Set(selected);
    next.has(option) ? next.delete(option) : next.add(option);
    onChange(Array.from(next));
  };
  return <div className="prop-enum-grid" role="group">
    {[...options, ...unknown].map(option => <button key={option} type="button" className={`prop-enum-chip${selected.has(option) ? ' active' : ''}${unknown.includes(option) ? ' unknown' : ''}`} aria-pressed={selected.has(option)} onClick={() => toggle(option)}>
      {labelPrefix ? optionLabel(labelPrefix, option, { moduleId: context.moduleId, namespace: context.moduleId, fallback: option }) : option}
    </button>)}
  </div>;
}

function MaterialInput({ id, value, onChange }: { id?: string; value: unknown; onChange: (value: string) => void }) {
  const current = textValue(value).toUpperCase();
  const [query, setQuery] = useState(current.toLowerCase());
  const suggestions = useMemo(() => searchMaterials(query || current, '全部').slice(0, 80), [query, current]);
  useEffect(() => setQuery(current.toLowerCase()), [current]);
  return <div className="prop-material-picker">
    <input id={id} type="text" value={query} onChange={event => setQuery(event.target.value)} onBlur={() => { if (query.trim()) onChange(query.trim().toUpperCase()); }} placeholder="diamond_sword" list="ie-material-options" />
    <datalist id="ie-material-options">{suggestions.map(material => <option key={material} value={material.toLowerCase()} />)}</datalist>
    <select value={MINECRAFT_MATERIALS.includes(current) ? current : ''} onChange={event => { if (event.target.value) onChange(event.target.value); }} aria-label={uiCopy('材质枚举选择', 'Material enum selector')}>
      <option value="">{current && !MINECRAFT_MATERIALS.includes(current) ? current : uiCopy('选择材质', 'Select material')}</option>
      {suggestions.map(material => <option key={material} value={material}>{material}</option>)}
    </select>
  </div>;
}

function TextInput({ id, value, onChange, placeholder }: { id?: string; value: unknown; onChange: (value: string) => void; placeholder?: string }) {
  return <input id={id} type="text" value={textValue(value)} onChange={event => onChange(event.target.value)} placeholder={placeholder} />;
}

function MapEditor({ value, onChange, valuePlaceholder, addKeyPrefix = 'key' }: { value: unknown; onChange: (value: Record<string, unknown>) => void; valuePlaceholder?: string; addKeyPrefix?: string }) {
  const entries = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: entry }));
  return <KvTable
    entries={entries}
    valuePlaceholder={valuePlaceholder}
    parseValue={parseLooseScalar}
    createEntry={currentEntries => ({ key: nextUniqueKey(currentEntries.map(entry => entry.key), addKeyPrefix), value: 0 })}
    onChange={nextEntries => {
      const next: AnyMap = {};
      nextEntries.forEach(entry => { if (entry.key.trim()) next[entry.key.trim()] = entry.value; });
      onChange(next);
    }}
  />;
}

function GenericObjectEditor({ value, reservedKeys, onChange }: { value: unknown; reservedKeys?: string[]; onChange: (value: AnyMap) => void }) {
  const reserved = new Set(reservedKeys ?? []);
  const entries = Object.entries(asRecord(value)).filter(([key]) => !reserved.has(key)).map(([key, entry]) => ({ key, value: entry }));
  return <PropRow label="字段" wide><KvTable
    entries={entries}
    parseValue={parseLooseScalar}
    createEntry={currentEntries => ({ key: nextUniqueKey(currentEntries.map(entry => entry.key), 'key'), value: 0 })}
    onChange={nextEntries => {
      const next: AnyMap = {};
      nextEntries.forEach(entry => { if (entry.key.trim()) next[entry.key.trim()] = entry.value; });
      onChange(next);
    }}
  /></PropRow>;
}

function resolvePreviewBaseLore(data: AnyMap, fallback: string[]): string[] {
  const configuredLore = asStringList(data.lore);
  return configuredLore.length > 0 ? configuredLore : fallback;
}

function compactLayerPreview(value: unknown): AnyMap | null {
  const preview = asRecord(value);
  if (!Object.keys(preview).length) return null;
  return cleanObject({
    ok: preview.ok === true,
    itemId: textValue(preview.itemId),
    final: compactLayerItemPreview(preview.final),
    setPreview: compactSetPreview(preview.setPreview),
    layers: asList(preview.layers).map(compactLayerRow),
    availableLayers: asStringList(preview.availableLayers),
    warnings: asList(preview.warnings).map(asRecord)
  });
}

function compactLayerRow(value: unknown): AnyMap {
  const layer = asRecord(value);
  return cleanObject({
    id: textValue(layer.id),
    available: layer.available === true,
    reason: textValue(layer.reason),
    status: textValue(layer.status),
    selected: asRecord(layer.selected),
    options: compactLayerOptions(layer.id, layer.options),
    preview: compactLayerItemPreview(layer.preview)
  });
}

function compactLayerItemPreview(value: unknown): AnyMap {
  const preview = asRecord(value);
  return cleanObject({
    id: textValue(preview.id),
    available: preview.available === true,
    reason: textValue(preview.reason),
    displayName: textValue(preview.displayName),
    lore: asStringList(preview.lore)
  });
}

function compactSetPreview(value: unknown): AnyMap {
  const preview = asRecord(value);
  return cleanObject({
    available: preview.available === true,
    reason: textValue(preview.reason),
    setId: textValue(preview.setId),
    pieceId: textValue(preview.pieceId),
    active: preview.active,
    total: preview.total,
    lore: asStringList(preview.lore)
  });
}

function compactLayerOptions(layerId: unknown, value: unknown): AnyMap {
  const options = asRecord(value);
  const id = textValue(layerId).toLowerCase();
  if (id === 'strengthen') return cleanObject({
    recipeId: textValue(options.recipeId),
    recipes: asList(options.recipes).map(entry => {
      const recipe = asRecord(entry);
      return cleanObject({ id: textValue(recipe.id), displayName: textValue(recipe.displayName) });
    }),
    currentStar: options.currentStar,
    currentTemper: options.currentTemper,
    selectedStar: options.selectedStar,
    selectedTemper: options.selectedTemper,
    maxStar: options.maxStar,
    maxTemper: options.maxTemper,
    stars: asList(options.stars).map(Number).filter(Number.isFinite)
  });
  if (id === 'gem') return cleanObject({
    templateId: textValue(options.templateId),
    selectedSlot: options.selectedSlot,
    selectedGemId: textValue(options.selectedGemId),
    selectedLevel: options.selectedLevel,
    slots: asList(options.slots).map(entry => {
      const slot = asRecord(entry);
      return cleanObject({ index: slot.index, type: textValue(slot.type), displayName: textValue(slot.displayName), opened: slot.opened === true, assigned: slot.assigned === true });
    }),
    gems: asList(options.gems).map(entry => {
      const gem = asRecord(entry);
      return cleanObject({ id: textValue(gem.id), displayName: textValue(gem.displayName), type: textValue(gem.type), level: gem.level, maxLevel: gem.maxLevel });
    })
  });
  return {};
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

function uiCopy(zh: string, en: string): string {
  return getLocale().startsWith('zh') ? zh : en;
}

function editorSectionDefaultCollapsed(section: WebEditorSection, data: AnyMap): boolean {
  if (section.defaultCollapsed === true) return true;
  if (section.fields.length > COLLAPSIBLE_SECTION_FIELD_THRESHOLD) return true;
  return section.fields.every(field => !hasMeaningfulEditorValue(getDeepValue(data, field.path)));
}

function hasMeaningfulEditorValue(value: unknown): boolean {
  if (value === undefined || value === null) return false;
  if (typeof value === 'string') return value.trim().length > 0;
  if (typeof value === 'number' || typeof value === 'boolean') return true;
  if (Array.isArray(value)) return value.some(hasMeaningfulEditorValue);
  if (value && typeof value === 'object') return Object.values(value as Record<string, unknown>).some(hasMeaningfulEditorValue);
  return true;
}

function localizedEditorTitle(editor: WebEditorDescriptor | undefined, fallback: string): string {
  return textValue(editor?.titleKey) ? t(textValue(editor?.titleKey), undefined, fallback) : textValue(editor?.title, fallback);
}

function itemSectionPath(section: WebEditorSection, index: number): string {
  const raw = textValue((section as AnyMap).titleKey) || section.title || `section-${index + 1}`;
  return `section:${raw}`;
}

function itemSectionOutline(moduleId: string, section: WebEditorSection, index: number, changedPaths: Set<string>) {
  const changedCount = section.fields.reduce((count, field) => count + (isChangedFieldPath(field.path, changedPaths) ? 1 : 0), 0);
  return {
    path: itemSectionPath(section, index),
    label: localizedSectionTitle(moduleId, section),
    type: 'section',
    childCount: section.fields.length,
    changedCount,
    changed: changedCount > 0
  };
}

function localizedSectionTitle(moduleId: string, section: WebEditorSection): string {
  const key = textValue((section as AnyMap).titleKey);
  return key ? t(key, undefined, section.title) : fieldLabel(section.title, { moduleId, namespace: moduleId, fallback: section.title });
}

function localizedSectionComment(moduleId: string, section: WebEditorSection): string | undefined {
  if (!section.comment) return undefined;
  const key = textValue((section as AnyMap).commentKey);
  return key ? t(key, undefined, section.comment) : fieldLabel(section.comment, { moduleId, namespace: moduleId, fallback: section.comment });
}

function localItemPreview(moduleId: string, editorId: string | undefined, kind: string | undefined, data: AnyMap, previewLevel: number, baseName: string, baseLore: string[]): ItemPreviewResult {
  const pluginFallback = getItemPreviewFallback(moduleId, editorId, kind);
  const pluginPreview = pluginFallback?.({ data, previewLevel, baseName, baseLore, moduleId, editorId, kind });
  if (pluginPreview) return pluginPreview;
  const variables = asRecord(data.variables);
  const displayName = renderLocalTemplate(textValue(data.display_name ?? data.item_name ?? data.id, baseName), variables);
  const lore = resolvePreviewBaseLore(data, baseLore).map(line => renderLocalTemplate(line, variables));
  const textPreview = applyLocalDisplayActions(displayName, lore, collectDisplayActions(data, 'name_actions', 'name_action', 'name_action'), collectDisplayActions(data, 'lore_actions', 'lore_action', 'lore_action'), variables);
  const material = materialFromItemSource(firstItemSource(data.item_sources ?? asRecord(data.match).item_sources) || data.material || data.item || 'stone');
  return {
    kind: 'generic_item',
    id: textValue(data.id),
    material,
    baseName: displayName,
    baseLore: lore,
    displayName: textPreview.name,
    lore: textPreview.lore,
    variables,
    nameSteps: textPreview.nameSteps,
    loreSteps: textPreview.loreSteps,
    level: undefined,
    levels: []
  };
}

function collectDisplayActions(data: AnyMap, topKey: string, effectType: string, effectKey: string): ActionEntry[] {
  const actions: ActionEntry[] = [];
  actions.push(...parseActionList(data[topKey]));
  for (const effect of asList(data.effects)) {
    const row = asRecord(effect);
    if (textValue(row.type).toLowerCase() !== effectType) continue;
    actions.push(...parseActionList(row[topKey]));
    actions.push(...parseActionList(row[effectKey]));
  }
  return actions;
}

function applyLocalDisplayActions(baseName: string, baseLore: string[], nameActions: ActionEntry[], loreActions: ActionEntry[], variables: AnyMap): { name: string; lore: string[]; nameSteps: ItemPreviewResult['nameSteps']; loreSteps: ItemPreviewResult['loreSteps'] } {
  let name = baseName;
  const nameSteps: ItemPreviewResult['nameSteps'] = [];
  for (const action of nameActions) {
    const before = name;
    const value = localActionValue(action, variables);
    if (action.type === 'replace') name = value;
    else if (action.type === 'prepend_prefix') name = `${value}${name}`;
    else if (action.type === 'append_suffix') name = `${name}${value}`;
    else if (action.type === 'regex_replace') name = localRegexReplace(name, textValue(action.params.regex_pattern), renderLocalTemplate(textValue(action.params.replacement), variables));
    nameSteps.push({ action: action.type, value, before, after: name, result: name });
  }

  const lore = [...baseLore];
  const loreSteps: ItemPreviewResult['loreSteps'] = [];
  for (const action of loreActions) {
    const before = [...lore];
    const content = localActionContent(action, variables);
    const anchor = renderLocalTemplate(textValue(action.params.search ?? action.params.anchor ?? action.params.pattern), variables);
    applyLocalLoreAction(lore, action, content, anchor, variables);
    loreSteps.push({ action: action.type, anchor, content, before, after: [...lore] });
  }
  return { name, lore, nameSteps, loreSteps };
}

function localActionValue(action: ActionEntry, variables: AnyMap): string {
  return renderLocalTemplate(textValue(action.params.value ?? action.params.content ?? action.params.text ?? action.params.name ?? action.params.replacement), variables);
}

function localActionContent(action: ActionEntry, variables: AnyMap): string[] {
  const raw = action.params.content ?? action.params.lines ?? action.params.value ?? action.params.text;
  const lines = asStringList(raw);
  return (lines.length ? lines : [textValue(raw)]).filter(line => line.length > 0).map(line => renderLocalTemplate(line, variables));
}

function applyLocalLoreAction(lore: string[], action: ActionEntry, content: string[], anchor: string, variables: AnyMap) {
  switch (action.type) {
    case 'prepend':
      lore.unshift(...content);
      break;
    case 'insert_above':
    case 'search_insert_above':
      lore.splice(findLocalLoreIndex(lore, anchor, false), 0, ...content);
      break;
    case 'insert_below':
    case 'search_insert_below':
    case 'search_insert':
      lore.splice(findLocalLoreIndex(lore, anchor, true), 0, ...content);
      break;
    case 'replace_line': {
      const index = lore.findIndex(line => anchor && line.includes(anchor));
      if (index >= 0) lore.splice(index, 1, content[0] ?? '');
      break;
    }
    case 'replace_text':
    case 'replace_text_all': {
      if (!anchor) break;
      const replacement = content[0] ?? '';
      const replaceAll = action.type === 'replace_text_all';
      for (let i = 0; i < lore.length; i += 1) {
        if (lore[i].includes(anchor)) {
          lore[i] = replaceAll ? lore[i].split(anchor).join(replacement) : lore[i].replace(anchor, replacement);
          if (!replaceAll) break;
        }
      }
      break;
    }
    case 'delete_line':
      for (let i = lore.length - 1; i >= 0; i -= 1) if (anchor && lore[i].includes(anchor)) lore.splice(i, 1);
      break;
    case 'regex_replace':
      for (let i = 0; i < lore.length; i += 1) lore[i] = localRegexReplace(lore[i], textValue(action.params.regex_pattern), renderLocalTemplate(textValue(action.params.replacement), variables));
      break;
    case 'append':
    default:
      lore.push(...content);
      break;
  }
}

function findLocalLoreIndex(lore: string[], anchor: string, below: boolean): number {
  if (!anchor) return below ? lore.length : 0;
  const index = lore.findIndex(line => line.includes(anchor));
  return index < 0 ? lore.length : index + (below ? 1 : 0);
}

function localRegexReplace(value: string, pattern: string, replacement: string): string {
  if (!pattern) return value;
  try { return value.replace(new RegExp(pattern, 'g'), replacement); } catch { return value; }
}

function renderLocalTemplate(value: string, variables: AnyMap): string {
  return textValue(value).replace(/\{([^{}]+)\}/g, (_, key) => textValue(variables[textValue(key).trim()], `{${key}}`));
}

type TooltipSection = { title: string; lines: string[] };
type SetDraftPreview = { id: string; name: string; lore: string[]; pieces: number; thresholds: number; active: number };

function isSetEditor(fileKind: string | undefined, editor?: WebEditorDescriptor): boolean {
  return textValue(fileKind).toUpperCase() === 'SET' || textValue(editor?.id).toLowerCase().includes(':set');
}

function buildSetDraftPreview(data: AnyMap): SetDraftPreview {
  const pieces = Object.entries(asRecord(data.pieces)).map(([key, value]) => ({ key, value: asRecord(value) }));
  const thresholds = Object.entries(asRecord(data.thresholds)).map(([key, value]) => ({ key, value: asRecord(value) })).sort((left, right) => Number(left.key) - Number(right.key));
  const active = pieces.length ? 1 : 0;
  const loreConfig = asRecord(data.lore);
  const id = textValue(data.id, 'set');
  const name = textValue(data.display_name, id);
  const base = { set_id: id, set_name: name, active: String(active), total: String(pieces.length) };
  const lore: string[] = [];
  addOptionalLine(lore, replaceSetTemplate(textValue(loreConfig.header, '<dark_gray>—— %set_name% <gray>(%active%/%total%)</gray> ——</dark_gray>'), base));
  for (const piece of pieces) {
    const pieceId = textValue(piece.key);
    const equipped = pieces.indexOf(piece) < active;
    const display = textValue(piece.value.display || piece.value.item || pieceId, pieceId);
    const row = { ...base, piece: display, piece_id: pieceId, slot: textValue(piece.value.slot) };
    addOptionalLine(lore, replaceSetTemplate(textValue(equipped ? loreConfig.equipped_format : loreConfig.missing_format, equipped ? '<green>✔ %piece%</green>' : '<gray>✘ %piece%</gray>'), row));
  }
  const separator = textValue(loreConfig.separator);
  if (separator) lore.push(separator);
  for (const threshold of thresholds) {
    const required = Math.max(1, Number(threshold.key) || 1);
    const isActive = active >= required;
    const lines = asStringList(threshold.value.lore);
    const format = textValue(isActive ? loreConfig.active_threshold_format : loreConfig.inactive_threshold_format, isActive ? '<green>%line%</green>' : '<dark_gray>%line%</dark_gray>');
    for (const line of lines) addOptionalLine(lore, replaceSetTemplate(format, { ...base, threshold: String(required), line }));
  }
  return {
    id,
    name,
    lore,
    pieces: pieces.length,
    thresholds: thresholds.length,
    active
  };
}

function SetPreviewSummary({ preview }: { preview: SetDraftPreview | null }) {
  return <div className="ie-preview-summary-grid" aria-label={uiCopy('套装摘要', 'Set summary')}>
    <div className="ie-preview-stat"><strong>{preview?.pieces ?? 0}</strong><span>{uiCopy('部件', 'Pieces')}</span></div>
    <div className="ie-preview-stat"><strong>{preview?.thresholds ?? 0}</strong><span>{uiCopy('阈值', 'Thresholds')}</span></div>
    <div className="ie-preview-stat"><strong>{preview?.active ?? 0}</strong><span>{uiCopy('预览已装备', 'Active')}</span></div>
  </div>;
}

function addOptionalLine(lines: string[], line: string) {
  if (line !== '') lines.push(line);
}

function replaceSetTemplate(template: string, values: Record<string, string>): string {
  let result = template;
  for (const [key, rawValue] of Object.entries(values)) {
    const value = rawValue ?? '';
    result = result.split(`%${key}%`).join(value).split(`{${key}}`).join(value);
  }
  return result;
}

function buildPreviewEffectSections(preview: ItemPreviewResult | null, moduleId: string): TooltipSection[] {
  const lines = summarizeEffectList(asList(preview?.effects).map(asRecord), moduleId);
  return lines.length ? [{ title: uiCopy('效果', 'Effects'), lines }] : [];
}

function summarizeEffectList(effects: AnyMap[], moduleId: string): string[] {
  return effects.flatMap(effect => {
    const type = textValue(effect.type);
    if (!type) return [];
    const definition = getEffectTypeDefinition(moduleId, type);
    const fields = definition?.fields?.length ? definition.fields.map(field => field.key) : Object.keys(effect).filter(key => key !== 'type' && key !== 'source');
    const payload = asRecord(effect.payload);
    const parts = fields.flatMap(key => {
      const value = effect[key] ?? payload[key];
      if (!hasPreviewValue(value)) return [];
      return [`${fieldLabel(key, { moduleId, namespace: moduleId, fallback: humanizeFieldLabel(key) })} ${previewValue(value)}`];
    });
    return [`${effectLabel(type, moduleId)}${parts.length ? `: ${parts.join(' · ')}` : ''}`];
  });
}

function effectLabel(type: string, moduleId: string): string {
  const definition = getEffectTypeDefinition(moduleId, type);
  return optionLabel('effect', type, { moduleId, namespace: moduleId, fallback: definition?.label ?? humanizeFieldLabel(type) });
}

function hasPreviewValue(value: unknown): boolean {
  if (value == null || value === '') return false;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === 'object') return Object.keys(value as Record<string, unknown>).length > 0;
  return true;
}

function GenericPreviewPane({ moduleId, fileKind, editor, data, preview, layerPreview, previewPending, previewError, previewLevel, setPreviewLevel, baseName, baseLore }: { moduleId: string; fileKind?: string; editor?: WebEditorDescriptor; data: AnyMap; preview: ItemPreviewResult | null; layerPreview: AnyMap | null; previewPending: boolean; previewError: PreviewError | null; previewLevel: number; setPreviewLevel: (level: number) => void; baseName: string; baseLore: string[] }) {
  const isSetPreview = isSetEditor(fileKind, editor);
  const setDraftPreview = isSetPreview ? buildSetDraftPreview(data) : null;
  const source = firstItemSource(data.item_sources ?? asRecord(data.match).item_sources ?? preview?.material);
  const material = materialFromItemSource(source || data.material || preview?.material);
  const levels = configuredPreviewLevels(data, preview);
  const hasLevels = !isSetPreview && levels.length > 0;
  const [, refreshTextureOrder] = useState(0);
  const urls = isSetPreview ? [] : materialUrls(material);
  const [imgFailed, setImgFailed] = useState(false);
  const previewResultLevel = Number(preview?.level);
  const previewMatchesLevel = !hasLevels || !Number.isFinite(previewResultLevel) || previewResultLevel === previewLevel;
  const livePreview = previewMatchesLevel ? preview : null;
  const finalPreview = asRecord(layerPreview?.final);
  const finalName = textValue(finalPreview.displayName);
  const finalLore = asStringList(finalPreview.lore);
  const setLayerPreview = asRecord(layerPreview?.setPreview);
  const resultName = setDraftPreview?.name ?? (finalName || textValue(livePreview?.displayName));
  const resultLore = setDraftPreview?.lore ?? (finalLore.length ? finalLore : livePreview ? asStringList(livePreview.lore) : []);
  const effectSections = setDraftPreview ? [] : buildPreviewEffectSections(livePreview, moduleId);
  const setSection = !isSetPreview && setLayerPreview.available && !finalLore.length ? [{ title: uiCopy('套装 Lore', 'Set Lore'), lines: asStringList(setLayerPreview.lore) }] : [];
  const status = isSetPreview ? { tone: 'live' as const, text: uiCopy('草稿预览', 'Draft preview') } : previewError ? { tone: 'failed' as const, text: t('core.item.preview.failedTitle') } : previewStatus(livePreview, previewPending);
  useEffect(() => setImgFailed(false), [material]);
  useEffect(() => subscribeTextureBases(() => { setImgFailed(false); refreshTextureOrder((version) => version + 1); }), []);

  return (
    <section className={`ie-preview-section ie-preview-primary${isSetPreview ? ' ie-preview--set' : ''}`} aria-label={t('core.item.previewAria')}>
      {isSetPreview ? <SetPreviewSummary preview={setDraftPreview} /> : <div className="ie-preview-icon">
        {urls.length > 0 && !imgFailed ? <img src={urls[0]} alt={material || t('core.item.iconAlt')} onError={e => { const target = e.currentTarget; const next = urls[urls.indexOf(target.src) + 1]; if (next) target.src = next; else setImgFailed(true); }} /> : <span className="ie-preview-fallback">{materialShortName(material) || '?'}</span>}
      </div>}
      <div className="ie-preview-meta">
        <span className="ie-preview-kind">{setDraftPreview ? uiCopy('套装预览', 'Set preview') : previewKindLabel(livePreview, moduleId, editor)}</span>
        {Boolean(setDraftPreview?.id || livePreview?.id || data.id) && <code className="ie-preview-id">{textValue(setDraftPreview?.id ?? livePreview?.id ?? data.id)}</code>}
        {!isSetPreview && <span className="ie-preview-source">{displaySource(source || material)}</span>}
        {!isSetPreview && Boolean(setLayerPreview.setId) && <span className="ie-preview-source">{uiCopy('套装', 'Set')}: {textValue(setLayerPreview.setId)} / {textValue(setLayerPreview.pieceId)}</span>}
        <span className={`ie-preview-status ${status.tone}`}>{status.text}</span>
      </div>
      {previewError && <InlineError className="ie-preview-error">
        <strong>{previewError.message}</strong>
        <p>{t('core.item.preview.formatHint')}</p>
        {previewError.detail && <details>
          <summary>{t('core.item.preview.technicalDetails')}</summary>
          <pre>{previewError.detail}</pre>
        </details>}
      </InlineError>}
      {hasLevels && <div className="ie-level-panel">
        <div className="ie-level-head"><span>{t('core.item.preview.levelTitle')}</span><code>{t('core.item.preview.upgradeLevel', { level: previewLevel })}</code></div>
        <div className="ie-level-rail">
          {levels.map(level => <button key={level} type="button" className={level === previewLevel ? 'active' : ''} onClick={() => setPreviewLevel(level)} aria-pressed={level === previewLevel}>Lv.{level}</button>)}
        </div>
        <p className="ie-level-hint">{t('core.item.preview.levelHint')}</p>
      </div>}
      <div className="ie-preview-compare">
        <PreviewTooltipBlock title={hasLevels ? t('core.item.preview.resultForLevel', { level: previewLevel }) : t('core.item.preview.result')} name={resultName} lore={resultLore} sections={[...setSection, ...effectSections]} refreshing={previewPending} emptyText={previewPending ? t('core.item.preview.syncing') : t('core.item.preview.emptyResult')} />
      </div>
    </section>
  );
}

function RenameMigrationPane({ api, editor, moduleId, currentId, sourceError, dirty, onApplied }: { api: ApiClient; editor?: WebEditorDescriptor; moduleId: string; currentId: string; sourceError: string | null; dirty: boolean; onApplied: () => void }) {
  const config = asRecord(editor?.rename);
  const previewRoute = textValue(config.previewRoute);
  const applyRoute = textValue(config.applyRoute);
  const apiModule = textValue(config.module || config.moduleId || 'item');
  const [newId, setNewId] = useState('');
  const [mode, setMode] = useState('replace_and_alias');
  const [preview, setPreview] = useState<AnyMap | null>(null);
  const [pending, setPending] = useState(false);
  const [applying, setApplying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const disabledReason = sourceError ? '当前 YAML 解析失败，请先修复。' : dirty ? '当前文件有未保存改动，请先保存。' : '';
  const normalizedNewId = newId.trim();

  useEffect(() => {
    setNewId('');
    setPreview(null);
    setError(null);
  }, [currentId]);

  if (!previewRoute || !applyRoute || !currentId) return null;

  const runPreview = async () => {
    if (!normalizedNewId || disabledReason) return;
    setPending(true);
    setError(null);
    try {
      const result = await api.pluginApi(apiModule, previewRoute, { oldId: currentId, newId: normalizedNewId });
      setPreview(asRecord(result));
    } catch (err) {
      setPreview(null);
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setPending(false);
    }
  };

  const apply = async () => {
    if (!preview || !normalizedNewId || disabledReason) return;
    setApplying(true);
    setError(null);
    try {
      await api.pluginApi(apiModule, applyRoute, {
        oldId: currentId,
        newId: normalizedNewId,
        mode,
        revisions: renameRevisionMap(preview, moduleId)
      });
      setPreview(null);
      onApplied();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setApplying(false);
    }
  };

  const files = asList(preview?.files).map(asRecord);
  const replacementCount = Number(preview?.replacementCount ?? 0);
  const newExists = preview ? Boolean(preview.newExists) : false;
  const aliasExists = preview ? Boolean(preview.aliasExists) : false;
  const canApply = Boolean(preview && normalizedNewId && !disabledReason && (newExists || mode === 'alias_only'));

  return <section className="ie-preview-section ie-rename-pane" aria-label="物品 ID 重命名迁移">
    <div className="ie-preview-meta">
      <span className="ie-preview-kind">ID 重命名迁移</span>
      <code className="ie-preview-id">{currentId}</code>
      <span className={`ie-preview-status ${pending || applying ? 'syncing' : error ? 'failed' : preview ? 'live' : 'syncing'}`}>{pending ? '预览中' : applying ? '应用中' : error ? '失败' : preview ? '已预览' : '待预览'}</span>
    </div>
    <div className="ie-rename-form">
      <label>
        <span>新 ID</span>
        <input value={newId} onChange={event => { setNewId(event.target.value); setPreview(null); }} placeholder="flame_sword" disabled={Boolean(disabledReason) || pending || applying} />
      </label>
      <label>
        <span>处理方式</span>
        <select value={mode} onChange={event => setMode(event.target.value)} disabled={pending || applying}>
          <option value="replace_and_alias">替换引用并保留 alias</option>
          <option value="replace_only">仅替换引用</option>
          <option value="alias_only">仅保留 alias</option>
        </select>
      </label>
      {disabledReason && <MiniText value={disabledReason} />}
      <div className="ie-rename-actions">
        <Button size="sm" onClick={runPreview} disabled={!normalizedNewId || Boolean(disabledReason) || pending || applying}>{pending ? '预览中…' : '预览影响'}</Button>
        <Button size="sm" variant="primary" onClick={apply} disabled={!canApply || applying}>{applying ? '应用中…' : '应用迁移'}</Button>
      </div>
    </div>
    {error && <InlineError className="ie-preview-error">{error}</InlineError>}
    {preview && <div className="ie-rename-summary">
      <div><strong>{replacementCount}</strong><span>处引用将被替换</span></div>
      <div><strong>{files.length}</strong><span>个文件受影响</span></div>
      <div><strong>{newExists ? '存在' : '不存在'}</strong><span>目标 ID</span></div>
      <div><strong>{aliasExists ? '已存在' : '将创建'}</strong><span>旧 ID alias</span></div>
    </div>}
    {preview && !newExists && mode !== 'alias_only' && <InlineError className="ie-preview-error">目标物品 ID 尚不存在。请先保存新 ID 的物品定义，或改用“仅保留 alias”。</InlineError>}
    {files.length > 0 && <div className="ie-layer-list">
      {files.slice(0, 8).map(file => <div className="ie-layer-row" key={`${textValue(file.moduleId)}:${textValue(file.path)}`}>
        <div><strong>{textValue(file.moduleId)}</strong><p><code>{textValue(file.path)}</code></p></div>
        <span className="ie-preview-status syncing">{Number(file.replacements ?? 0)} 处</span>
      </div>)}
      {files.length > 8 && <MiniText value={`另有 ${files.length - 8} 个文件未展开显示。`} />}
    </div>}
  </section>;
}

function renameRevisionMap(preview: AnyMap, moduleId: string): Record<string, number> {
  const revisions: Record<string, number> = {};
  for (const file of asList(preview.files).map(asRecord)) {
    const fileModule = textValue(file.moduleId);
    const path = textValue(file.path);
    const revision = Number(file.revision ?? 0);
    if (fileModule && path && revision > 0) revisions[`${fileModule}:${path}`] = revision;
  }
  const aliasRevision = Number(preview.aliasRevision ?? 0);
  if (aliasRevision > 0) revisions[`${moduleId}:id_aliases.yml`] = aliasRevision;
  return revisions;
}

function LayeredPreviewPane({ preview, pending, error, options, onOptionsChange }: { preview: AnyMap | null; pending: boolean; error: string | null; options: AnyMap; onOptionsChange: (options: AnyMap) => void }) {
  if (!preview && !pending && !error) return null;
  const layers = asList(preview?.layers).map(asRecord);
  const finalPreview = asRecord(preview?.final);
  const updateLayerOption = (layerId: string, key: string, value: unknown) => {
    const current = asRecord(options[layerId]);
    onOptionsChange({ ...options, [layerId]: { ...current, [key]: value } });
  };
  return <section className="ie-preview-section ie-layer-preview" aria-label="分层预览">
    <div className="ie-preview-meta">
      <span className="ie-preview-kind">分层预览</span>
      <span className={`ie-preview-status ${pending ? 'syncing' : error ? 'failed' : 'live'}`}>{pending ? '同步中' : error ? '失败' : '已同步'}</span>
    </div>
    {error && <InlineError className="ie-preview-error">{error}</InlineError>}
    {layers.length > 0 && <div className="ie-layer-list">
      {layers.map(layer => <LayerPreviewRow key={textValue(layer.id)} layer={layer} layerOptions={asRecord(options[textValue(layer.id)])} onOptionChange={updateLayerOption} pending={pending} />)}
    </div>}
    {finalPreview.displayName || asStringList(finalPreview.lore).length ? <PreviewTooltipBlock title="最终预览" name={textValue(finalPreview.displayName)} lore={asStringList(finalPreview.lore)} emptyText="暂无最终预览" refreshing={pending} /> : null}
  </section>;
}

function LayerPreviewRow({ layer, layerOptions, onOptionChange, pending }: { layer: AnyMap; layerOptions: AnyMap; onOptionChange: (layerId: string, key: string, value: unknown) => void; pending: boolean }) {
  const layerId = textValue(layer.id);
  const preview = asRecord(layer.preview);
  const reason = textValue(layer.reason || layer.message);
  return <div className={`ie-layer-row ${layer.available ? '' : 'is-unavailable'}`}>
    <div className="ie-layer-main">
      <div className="ie-layer-heading"><strong>{layerLabel(layerId)}</strong><span className={`ie-preview-status ${layer.available ? 'live' : 'failed'}`}>{layer.available ? '可用' : '不可用'}</span></div>
      {reason && <p>{reason}</p>}
      <LayerPreviewControls layer={layer} layerOptions={layerOptions} onOptionChange={onOptionChange} />
      {(preview.displayName || asStringList(preview.lore).length) && <PreviewTooltipBlock title="层预览" name={textValue(preview.displayName)} lore={asStringList(preview.lore)} emptyText="暂无层预览" refreshing={pending} />}
    </div>
  </div>;
}

function LayerPreviewControls({ layer, layerOptions, onOptionChange }: { layer: AnyMap; layerOptions: AnyMap; onOptionChange: (layerId: string, key: string, value: unknown) => void }) {
  const layerId = textValue(layer.id);
  if (layerId === 'strengthen') return <StrengthenLayerControls layer={layer} layerOptions={layerOptions} onOptionChange={onOptionChange} />;
  if (!layer.available) return null;
  if (layerId === 'gem') return <GemLayerControls layer={layer} layerOptions={layerOptions} onOptionChange={onOptionChange} />;
  return null;
}

function StrengthenLayerControls({ layer, layerOptions, onOptionChange }: { layer: AnyMap; layerOptions: AnyMap; onOptionChange: (layerId: string, key: string, value: unknown) => void }) {
  const layerId = textValue(layer.id);
  const layerConfig = asRecord(layer.options);
  const selected = asRecord(layer.selected);
  const details = asRecord(layer.details);
  const recipes = asList(layerConfig.recipes).map(asRecord).filter(recipe => textValue(recipe.id));
  const selectedRecipeId = textValue(layerOptions.recipeId) || textValue(selected.recipeId) || textValue(layerConfig.recipeId) || textValue(details.recipeId);
  const selectedRecipe = recipes.find(recipe => textValue(recipe.id) === selectedRecipeId);
  const stars = asList(layerConfig.stars).map(value => Number(value)).filter(value => Number.isFinite(value) && value > 0);
  const maxStar = Math.max(1, Number(layerConfig.maxStar || Math.max(1, ...stars)) || 1);
  const starChoices = stars.length ? stars : (layer.available ? Array.from({ length: maxStar }, (_, index) => index + 1) : []);
  const selectedStar = String(layerOptions.star ?? selected.star ?? layerConfig.selectedStar ?? starChoices[0] ?? '');
  const maxTemper = Math.max(0, Number(layerConfig.maxTemper || 0) || 0);
  const selectedTemper = String(layerOptions.temper ?? selected.temper ?? layerConfig.selectedTemper ?? 0);
  if (!recipes.length && !layer.available) return null;
  return <div className="ie-layer-controls">
    {recipes.length > 0 && <label><span>强化配方</span><select value={selectedRecipeId} onChange={event => onOptionChange(layerId, 'recipeId', event.target.value)}>
      <option value="">自动匹配</option>
      {recipes.map(recipe => <option key={textValue(recipe.id)} value={textValue(recipe.id)}>{recipeLabel(recipe)}</option>)}
    </select></label>}
    {selectedRecipeId && <span className="ie-layer-current">当前配方：<code>{selectedRecipeId}</code>{selectedRecipe && textValue(selectedRecipe.displayName) ? ` · ${textValue(selectedRecipe.displayName)}` : ''}</span>}
    {starChoices.length > 0 && <label><span>星级</span><select value={selectedStar} onChange={event => onOptionChange(layerId, 'star', Number(event.target.value))}>{starChoices.map(star => <option key={star} value={star}>{star} 星</option>)}</select></label>}
    {maxTemper > 0 && <label><span>淬炼</span><select value={selectedTemper} onChange={event => onOptionChange(layerId, 'temper', Number(event.target.value))}>{Array.from({ length: maxTemper + 1 }, (_, value) => <option key={value} value={value}>{value}</option>)}</select></label>}
  </div>;
}

function recipeLabel(recipe: AnyMap): string {
  return textValue(recipe.id);
}

function GemLayerControls({ layer, layerOptions, onOptionChange }: { layer: AnyMap; layerOptions: AnyMap; onOptionChange: (layerId: string, key: string, value: unknown) => void }) {
  const layerId = textValue(layer.id);
  const layerConfig = asRecord(layer.options);
  const selected = asRecord(layer.selected);
  const slots = asList(layerConfig.slots).map(asRecord);
  const gems = asList(layerConfig.gems).map(asRecord);
  const selectedSlot = String(layerOptions.slot ?? selected.slot ?? layerConfig.selectedSlot ?? (slots[0]?.index ?? -1));
  const selectedGemId = textValue(layerOptions.gemId ?? selected.gemId ?? layerConfig.selectedGemId ?? gems[0]?.id);
  const currentGem = gems.find(gem => textValue(gem.id) === selectedGemId) || gems[0];
  const maxLevel = Math.max(1, Number(currentGem?.maxLevel || currentGem?.level || 1) || 1);
  const selectedLevel = String(layerOptions.level ?? selected.level ?? layerConfig.selectedLevel ?? currentGem?.level ?? 1);
  return <div className="ie-layer-controls">
    {slots.length > 0 && <label><span>槽位</span><select value={selectedSlot} onChange={event => onOptionChange(layerId, 'slot', Number(event.target.value))}>{slots.map(slot => <option key={textValue(slot.index)} value={textValue(slot.index)}>{slotLabel(slot)}</option>)}</select></label>}
    {gems.length > 0 && <label><span>宝石</span><select value={selectedGemId} onChange={event => onOptionChange(layerId, 'gemId', event.target.value)}>{gems.map(gem => <option key={textValue(gem.id)} value={textValue(gem.id)}>{textValue(gem.displayName || gem.id)}</option>)}</select></label>}
    {gems.length > 0 && maxLevel > 1 && <label><span>等级</span><select value={selectedLevel} onChange={event => onOptionChange(layerId, 'level', Number(event.target.value))}>{Array.from({ length: maxLevel }, (_, index) => index + 1).map(level => <option key={level} value={level}>{level}</option>)}</select></label>}
  </div>;
}

function slotLabel(slot: AnyMap): string {
  const index = textValue(slot.index);
  const name = textValue(slot.displayName || slot.type);
  const tags = [slot.opened ? '已开' : '未开', slot.assigned ? '已镶嵌' : '空'].join(' · ');
  return `#${index} ${name}（${tags}）`;
}

function layerLabel(id: string): string {
  if (id === 'strengthen') return 'Strengthen 强化层';
  if (id === 'gem') return 'Gem 宝石层';
  return id || 'Layer';
}

function PreviewTooltipBlock({ title, name, lore, sections, emptyText, refreshing }: { title: string; name: string; lore: string[]; sections?: TooltipSection[]; emptyText: string; refreshing?: boolean }) {
  const visibleSections = (sections ?? []).filter(section => section.lines.length > 0);
  return <div className="ie-preview-tooltip-block">
    <div className="ie-tooltip-label">{title}</div>
    <div className={`ie-tooltip${refreshing ? ' is-refreshing' : ''}`}>
      {name ? <div className="ie-tooltip-name"><MiniText value={name} /></div> : null}
      {lore.map((line, i) => <div className="ie-tooltip-line" key={i}><MiniText value={line} /></div>)}
      {visibleSections.map(section => <div className="ie-tooltip-section" key={section.title}>
        <div className="ie-tooltip-section-title">{section.title}</div>
        {section.lines.map((line, index) => <div className="ie-tooltip-line" key={index}><MiniText value={line} /></div>)}
      </div>)}
      {!name && !lore.length && !visibleSections.length && <span className="ie-tooltip-empty">{emptyText}</span>}
    </div>
  </div>;
}

function asEditableStringList(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(item => item == null ? '' : String(item));
  if (value == null) return [];
  return [String(value)];
}

function previewStatus(preview: ItemPreviewResult | null, pending: boolean): { tone: 'live' | 'syncing' | 'failed'; text: string } {
  if (pending) return { tone: 'syncing', text: t('core.item.previewStatus.syncing') };
  if (preview) return { tone: 'live', text: t('core.item.previewStatus.live') };
  return { tone: 'failed', text: t('core.item.previewStatus.failed') };
}

function previewErrorFromUnknown(error: unknown): PreviewError {
  if (error instanceof ApiError) {
    const detail = error.technicalDetails || error.message;
    if (error.errorType === 'yaml_parse_error') return { message: t('core.item.preview.yamlFailed'), detail };
    if (error.errorType === 'lore_type_error') return { message: t('core.item.preview.loreLineInvalid'), detail };
    if (error.errorType === 'preview_error') return { message: t('core.item.preview.failedTitle'), detail };
    return { message: error.message || t('core.item.preview.failedRequest'), detail };
  }
  if (error instanceof Error) return { message: error.message || t('core.item.preview.failedRequest') };
  return { message: t('core.item.preview.failedRequest') };
}

function previewKindLabel(preview: ItemPreviewResult | null, moduleId: string, editor?: WebEditorDescriptor): string {
  const kind = textValue(preview?.kind);
  const labels = asRecord(asRecord(editor?.preview).kindLabels);
  const labelKey = textValue(labels[kind] ?? labels.default);
  if (labelKey) return t(labelKey, undefined, humanizeFieldLabel(kind || 'item'));
  return kind ? t(`${moduleId.toLowerCase()}.preview.kind.${kind}`, undefined, humanizeFieldLabel(kind)) : t('core.item.genericKind');
}

function previewValue(value: unknown): string {
  if (value == null) return '';
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function editorFieldMap(editor: WebEditorDescriptor | undefined): Record<string, WebEditorField> {
  const fields = editor?.fields;
  const result = !fields || typeof fields !== 'object' || Array.isArray(fields) ? {} : { ...(fields as Record<string, WebEditorField>) };
  const allowedFieldTypes = asStringList(editor?.allowedFieldTypes);
  if (allowedFieldTypes.length) {
    (result as AnyMap).__meta = { path: '__meta', label: '__meta', type: 'json', allowedFieldTypes };
  }
  return result;
}

function configuredPreviewLevels(data: AnyMap, preview: ItemPreviewResult | null): number[] {
  const upgrade = asRecord(data.upgrade);
  if (!truthy(upgrade.enabled)) return [];
  const previewLevels = normalizedPreviewLevels(preview);
  const maxLevel = Math.max(1, toNumber(upgrade.max_level, Math.max(1, ...previewLevels)));
  return Array.from({ length: maxLevel }, (_, index) => index + 1);
}

function normalizedPreviewLevels(preview: ItemPreviewResult | null): number[] {
  return (preview?.levels ?? []).map(level => Number(level)).filter(level => Number.isFinite(level) && level > 0);
}

function defaultSections(): WebEditorSection[] {
  return [{
    title: t('core.item.basic'),
    fields: [
      { path: 'id', label: 'ID', type: 'text' },
      { path: 'material', label: t('core.item.material'), type: 'text' },
      { path: 'display_name', label: t('core.item.displayName'), type: 'text' },
      { path: 'lore', label: 'Lore', type: 'stringList', wide: true },
      ...standardDisplayActionFields()
    ]
  }];
}

function nextUniqueKey(keys: string[], prefix: string): string {
  const normalizedPrefix = prefix.trim() || 'key';
  let index = keys.length + 1;
  let key = `${normalizedPrefix}_${index}`;
  while (keys.includes(key)) key = `${normalizedPrefix}_${++index}`;
  return key;
}

function toNumber(value: unknown, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function truthy(value: unknown): boolean {
  if (typeof value === 'boolean') return value;
  const text = textValue(value).trim().toLowerCase();
  return text === 'true' || text === 'yes' || text === '1' || text === 'on';
}

function joinPath(...parts: Array<string | number | undefined>): string | undefined {
  const filtered = parts.filter(part => part !== undefined && part !== '').map(String);
  return filtered.length ? filtered.join('.') : undefined;
}

function mergeOptions(options: string[], fallback: string[]): string[] {
  return [...new Set([...options, ...fallback].map(option => textValue(option).trim().toLowerCase()).filter(Boolean))];
}

function parseLooseScalar(value: string): unknown {
  const trimmed = value.trim();
  if (trimmed === '') return '';
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
  return value;
}

function cleanObject<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '')) as T;
}

