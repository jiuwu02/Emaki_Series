import React, { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError, type ApiClient, type ActionTypesResult } from './api';
import { Button, CollapsibleSection, ChangedPathsProvider, DisclosureChevron, EditorChrome, InlineError, MiniText, PropRow as BasePropRow, SectionHead, StandardActionsField, StandardEconomyProviderSelect, StandardEffectsEditor, StringListEditor, ToastNotice, VariablesMapEditor } from './components';
import { asList, asRecord, asStringList, displaySource, firstItemSource, materialFromItemSource, setDeepValue, parseYaml, type AnyMap } from './itemEditor';
import { t, getLocale } from './i18n';
import { changedPathSet, diffRecords, fieldLabel, getDeepValue, humanizeFieldLabel, isChangedFieldPath, materialShortName, materialUrls, optionLabel, subscribeTextureBases, textValue, valuesEqual } from './lib';
import { MINECRAFT_MATERIALS, searchMaterials } from './minecraftMaterials';
import { getSourceDocumentAdapter, isKind, type SurfaceToolbarState } from './registry';
import { fileDisplayTitle } from './lib';
import { CORE_ITEM_FIELD_TYPE_SET, standardDisplayActionFields } from './itemFieldKit';
import { getItemFieldRenderer, getItemPreviewFallback } from './itemFieldRegistry';
import type { ItemPreviewResult, ItemPreviewStep, WebEditorDescriptor, WebEditorField, WebEditorSection, WebRegistryFile, WebRegistryModule } from './types';
import { serializeItemYaml } from './itemEditor';

type Props = { module: WebRegistryModule; file: WebRegistryFile; api: ApiClient; childPath?: string; refreshKey?: number; editor?: WebEditorDescriptor; onReload?: () => void; setToolbar?: (state: SurfaceToolbarState | null) => void; showLocalChrome?: boolean };
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

export function ItemEditorSurface({ module, file, api, childPath, refreshKey = 0, editor, onReload, setToolbar, showLocalChrome = true }: Props) {
  const [data, setData] = useState<AnyMap>({});
  const [originalData, setOriginalData] = useState<AnyMap>({});
  const [originalContent, setOriginalContent] = useState('');
  const [preview, setPreview] = useState<ItemPreviewResult | null>(null);
  const [previewLevel, setPreviewLevel] = useState(1);
  const [previewPending, setPreviewPending] = useState(false);
  const [previewError, setPreviewError] = useState<PreviewError | null>(null);
  const previewRequestId = useRef(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sourceText, setSourceText] = useState('');
  const [sourceError, setSourceError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ tone: 'ok' | 'bad'; text: string } | null>(null);
  const [history, setHistory] = useState<SnapshotHistory>({ undo: [], redo: [] });

  const [actionTypesResult, setActionTypesResult] = useState<ActionTypesResult | null>(null);
  const [economyProviders, setEconomyProviders] = useState<string[]>(DEFAULT_ECONOMY_PROVIDERS);

  const filePath = childPath || file.path;
  const fileTitle = fileDisplayTitle(file);
  const baseName = editor?.baseName ?? DEFAULT_BASE_NAME;
  const baseLore = useMemo(() => editor?.baseLore ?? [DEFAULT_BASE_LORE], [editor?.baseLore]);
  const sections = useMemo(() => editor?.sections?.length ? editor.sections : defaultSections(), [editor]);
  const editorFields = useMemo(() => editorFieldMap(editor), [editor]);
  const sourceAdapter = getSourceDocumentAdapter(file, editor);
  const sourcePath = childPath || file.path;
  const sourceContext = useMemo(() => ({ module, file, childPath, path: sourcePath, editor }), [module, file, childPath, sourcePath, editor?.id]);
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
    setLoading(true);
    setError(null);
    api.readTextDocument({ kind: file.kind, moduleId: module.id, path: filePath }).then(doc => {
      if (cancelled) return;
      try {
        const parsed = parseYaml(doc.content) as AnyMap;
        setData(parsed);
        setOriginalData(parsed);
        setOriginalContent(doc.content);
        setSourceText(doc.content);
        setSourceError(null);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        setData({});
        setOriginalData({});
        setOriginalContent(doc.content);
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
  }, [api, module.id, filePath, refreshKey]);

  useEffect(() => {
    api.actionTypes().then(setActionTypesResult).catch(err => void api.reportFrontendError({ message: err instanceof Error ? err.message : String(err), source: 'item-action-types', detail: module.id }));
    api.economyProviders().then(result => setEconomyProviders(mergeOptions(result.providers, DEFAULT_ECONOMY_PROVIDERS))).catch(() => setEconomyProviders(DEFAULT_ECONOMY_PROVIDERS));
  }, [api]);

  useEffect(() => {
    if (loading) return;
    if (!isKind(file.kind, 'ITEM')) {
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
    const timer = window.setTimeout(() => {
      api.previewItem(content, requestedLevel, baseName, previewBaseLore)
        .then(nextPreview => {
          if (!active || previewRequestId.current !== requestId) return;
          setPreview(nextPreview);
        })
        .catch(err => {
          if (!active || previewRequestId.current !== requestId) return;
          setPreviewError(previewErrorFromUnknown(err));
          setPreview(localItemPreview(module.id, editor?.id, file.kind, data, requestedLevel, baseName, previewBaseLore));
        })
        .finally(() => {
          if (active && previewRequestId.current === requestId) setPreviewPending(false);
        });
    }, 300);
    return () => { active = false; window.clearTimeout(timer); };
  }, [api, data, sourceContent, previewLevel, loading, baseName, baseLore, file.kind]);

  useEffect(() => {
    const levels = configuredPreviewLevels(data, preview);
    if (!levels.length) {
      if (previewLevel !== 1) setPreviewLevel(1);
      return;
    }
    if (!levels.includes(previewLevel)) setPreviewLevel(levels[0]);
  }, [data, preview, previewLevel]);

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
      await (sourceAdapter?.save(api, sourceContext, content) ?? api.saveTextDocument({ kind: file.kind, moduleId: module.id, path: filePath }, content));
      setOriginalContent(content);
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
          <GenericPreviewPane moduleId={module.id} editor={editor} data={data} preview={preview} previewPending={previewPending} previewError={previewError} previewLevel={previewLevel} setPreviewLevel={setPreviewLevel} baseName={baseName} baseLore={baseLore as string[]} />
          <div className="ie-props-scroll">
            <div className="ie-props">
              {sections.map(section => (
                <CollapsibleSection
                  key={section.title}
                  title={localizedSectionTitle(module.id, section)}
                  comment={localizedSectionComment(module.id, section)}
                  collapsible={section.collapsible ?? true}
                  defaultCollapsed={editorSectionDefaultCollapsed(section, data)}
                  storageKey={`core:item-section:${editor?.id ?? file.editorId ?? file.kind}:${section.title}`}
                >
                  {section.fields.map(field => <FieldEditor key={field.path} field={field} data={data} originalData={originalData} setField={setField} actionTypesResult={actionTypesResult} editorId={editor?.id ?? file.editorId} />)}
                </CollapsibleSection>
              ))}
            </div>
          </div>
        </div>
        </ChangedPathsProvider>
      </EditorContext.Provider>
    </div>
  );
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

function KvTable({ entries, onChange, valuePlaceholder = t('core.kv.value'), addKeyPrefix = 'key' }: { entries: Array<{ key: string; value: unknown }>; onChange: (entries: Array<{ key: string; value: unknown }>) => void; valuePlaceholder?: string; addKeyPrefix?: string }) {
  const update = (index: number, field: 'key' | 'value', value: string) => {
    const next = [...entries];
    next[index] = field === 'key' ? { ...next[index], key: value } : { ...next[index], value: parseLooseScalar(value) };
    onChange(next);
  };
  const remove = (index: number) => onChange(entries.filter((_, itemIndex) => itemIndex !== index));
  const add = () => onChange([...entries, { key: nextUniqueKey(entries.map(entry => entry.key), addKeyPrefix), value: 0 }]);
  return <div className="prop-kv" role="list" aria-label={t('core.kv.aria')}>
    {entries.map((entry, index) => <div className="prop-kv-row" key={index} role="listitem">
      <input type="text" value={entry.key} onChange={event => update(index, 'key', event.target.value)} placeholder={t('core.kv.key')} aria-label={`${t('core.kv.key')} ${index + 1}`} />
      <input type="text" value={entry.value == null ? '' : String(entry.value)} onChange={event => update(index, 'value', event.target.value)} placeholder={valuePlaceholder} aria-label={`${t('core.kv.value')} ${index + 1}`} />
      <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={t('core.kv.delete', { index: index + 1 })}>×</button>
    </div>)}
    <button type="button" className="prop-add" onClick={add}>+ {t('core.kv.add')}</button>
  </div>;
}

function MapEditor({ value, onChange, valuePlaceholder, addKeyPrefix }: { value: unknown; onChange: (value: Record<string, unknown>) => void; valuePlaceholder?: string; addKeyPrefix?: string }) {
  const entries = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: entry }));
  return <KvTable entries={entries} valuePlaceholder={valuePlaceholder} addKeyPrefix={addKeyPrefix} onChange={nextEntries => {
    const next: AnyMap = {};
    nextEntries.forEach(entry => { if (entry.key.trim()) next[entry.key.trim()] = entry.value; });
    onChange(next);
  }} />;
}

function NumberListEditor({ items, onChange }: { items: number[]; onChange: (items: number[]) => void }) {
  const update = (index: number, value: number | undefined) => onChange(items.map((item, itemIndex) => itemIndex === index ? value ?? 0 : item));
  const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
  return <div className="prop-kv" role="list">
    {items.map((item, index) => <div className="prop-kv-row prop-kv-row--single" key={index} role="listitem">
      <input type="number" value={String(item)} onChange={event => update(index, event.target.value === '' ? undefined : Number(event.target.value))} aria-label={t('core.list.numberAria', { index: index + 1 })} />
      <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={t('core.config.deleteItem', { index: index + 1 })}>×</button>
    </div>)}
    <button type="button" className="prop-add" onClick={() => onChange([...items, 0])}>+ {t('core.config.addItem')}</button>
  </div>;
}

function GenericObjectEditor({ value, reservedKeys, onChange }: { value: unknown; reservedKeys?: string[]; onChange: (value: AnyMap) => void }) {
  const reserved = new Set(reservedKeys ?? []);
  const entries = Object.entries(asRecord(value)).filter(([key]) => !reserved.has(key)).map(([key, entry]) => ({ key, value: entry }));
  return <PropRow label="字段" wide><KvTable entries={entries} onChange={nextEntries => {
    const next: AnyMap = {};
    nextEntries.forEach(entry => { if (entry.key.trim()) next[entry.key.trim()] = entry.value; });
    onChange(next);
  }} /></PropRow>;
}

function resolvePreviewBaseLore(data: AnyMap, fallback: string[]): string[] {
  const configuredLore = asStringList(data.lore);
  return configuredLore.length > 0 ? configuredLore : fallback;
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
  const displayName = textValue(data.display_name ?? data.item_name ?? data.id, baseName);
  const lore = resolvePreviewBaseLore(data, baseLore);
  const material = materialFromItemSource(firstItemSource(data.item_sources ?? asRecord(data.match).item_sources) || data.material || data.item || 'stone');
  return {
    kind: 'generic_item',
    id: textValue(data.id),
    material,
    baseName,
    baseLore,
    displayName,
    lore,
    variables: asRecord(data.variables),
    nameSteps: [],
    loreSteps: [],
    level: undefined,
    levels: []
  };
}

function GenericPreviewPane({ moduleId, editor, data, preview, previewPending, previewError, previewLevel, setPreviewLevel, baseName, baseLore }: { moduleId: string; editor?: WebEditorDescriptor; data: AnyMap; preview: ItemPreviewResult | null; previewPending: boolean; previewError: PreviewError | null; previewLevel: number; setPreviewLevel: (level: number) => void; baseName: string; baseLore: string[] }) {
  const source = firstItemSource(data.item_sources ?? asRecord(data.match).item_sources ?? preview?.material);
  const material = materialFromItemSource(source || data.material || preview?.material);
  const levels = configuredPreviewLevels(data, preview);
  const hasLevels = levels.length > 0;
  const [, refreshTextureOrder] = useState(0);
  const urls = materialUrls(material);
  const [imgFailed, setImgFailed] = useState(false);
  const previewResultLevel = Number(preview?.level);
  const previewMatchesLevel = !hasLevels || !Number.isFinite(previewResultLevel) || previewResultLevel === previewLevel;
  const livePreview = previewMatchesLevel ? preview : null;
  const resultName = textValue(livePreview?.displayName);
  const resultLore = livePreview ? asStringList(livePreview.lore) : [];
  const status = previewError ? { tone: 'failed' as const, text: t('core.item.preview.failedTitle') } : previewStatus(livePreview, previewPending);
  useEffect(() => setImgFailed(false), [material]);
  useEffect(() => subscribeTextureBases(() => { setImgFailed(false); refreshTextureOrder((version) => version + 1); }), []);

  return (
    <div className="ie-preview" role="complementary" aria-label={t('core.item.previewAria')}>
      <div className="ie-preview-icon">
        {urls.length > 0 && !imgFailed ? <img src={urls[0]} alt={material || t('core.item.iconAlt')} onError={e => { const target = e.currentTarget; const next = urls[urls.indexOf(target.src) + 1]; if (next) target.src = next; else setImgFailed(true); }} /> : <span className="ie-preview-fallback">{materialShortName(material) || '?'}</span>}
      </div>
      <div className="ie-preview-meta">
        <span className="ie-preview-kind">{previewKindLabel(livePreview, moduleId, editor)}</span>
        {Boolean(livePreview?.id || data.id) && <code className="ie-preview-id">{textValue(livePreview?.id ?? data.id)}</code>}
        <span className="ie-preview-source">{displaySource(source || material)}</span>
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
        <PreviewTooltipBlock title={hasLevels ? t('core.item.preview.resultForLevel', { level: previewLevel }) : t('core.item.preview.result')} name={resultName} lore={resultLore} refreshing={previewPending} emptyText={previewPending ? t('core.item.preview.syncing') : t('core.item.preview.emptyResult')} />
      </div>
    </div>
  );
}

function PreviewTooltipBlock({ title, name, lore, emptyText, refreshing }: { title: string; name: string; lore: string[]; emptyText: string; refreshing?: boolean }) {
  return <div className="ie-preview-tooltip-block">
    <div className="ie-tooltip-label">{title}</div>
    <div className={`ie-tooltip${refreshing ? ' is-refreshing' : ''}`}>
      {name ? <div className="ie-tooltip-name"><MiniText value={name} /></div> : null}
      {lore.map((line, i) => <div className="ie-tooltip-line" key={i}><MiniText value={line} /></div>)}
      {!name && !lore.length && <span className="ie-tooltip-empty">{emptyText}</span>}
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

