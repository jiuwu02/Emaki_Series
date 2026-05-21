import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { ApiClient, ActionTypesResult } from './api';
import { ActionsEditor, Button, CollapsibleSection, DisclosureChevron, EditorChrome, InlineError, MiniText, PropRow as BasePropRow, SectionHead, StringListEditor, ToastNotice, parseActionList, serializeActionList } from './components';
import { asList, asRecord, asStringList, displaySource, firstItemSource, materialFromItemSource, setDeepValue, parseYaml, type AnyMap } from './itemEditor';
import { t, getLocale } from './i18n';
import { changedPathSet, diffRecords, fieldLabel, getDeepValue, humanizeFieldLabel, isChangedFieldPath, materialShortName, materialUrls, optionLabel, subscribeTextureBases, textValue, valuesEqual } from './lib';
import { MINECRAFT_MATERIALS, searchMaterials } from './minecraftMaterials';
import { getSourceDocumentAdapter, isKind, type SurfaceToolbarState } from './registry';
import { fileDisplayTitle } from './lib';
import { CORE_ITEM_FIELD_TYPE_SET, standardDisplayActionFields } from './itemFieldKit';
import { CORE_EFFECT_TYPES, coreEffectTypeLabel, createCoreEffect, getItemFieldRenderer, isCoreEffectType, type CoreEffectType } from './itemFieldRegistry';
import type { ItemPreviewResult, ItemPreviewStep, WebEditorDescriptor, WebEditorField, WebEditorSection, WebRegistryFile, WebRegistryModule } from './types';
import { serializeItemYaml } from './itemEditor';

type Props = { module: WebRegistryModule; file: WebRegistryFile; api: ApiClient; childPath?: string; refreshKey?: number; editor?: WebEditorDescriptor; onReload?: () => void; setToolbar?: (state: SurfaceToolbarState | null) => void; showLocalChrome?: boolean };
type SnapshotHistory = { undo: AnyMap[]; redo: AnyMap[] };
const DEFAULT_BASE_NAME = t('core.item.defaultBaseName');
const DEFAULT_BASE_LORE = t('core.item.defaultBaseLore');
const DEFAULT_ECONOMY_PROVIDERS = ['auto', 'vault', 'excellenteconomy'];

const ITEM_EDITOR_TITLES: Record<string, string> = {
  'emakigem:gem': 'Gem Definition',
  'emakigem:socket-item': 'Gem Socket Item',
  'emakiitem:item': 'Item Definition',
  'emakiitem:set': 'Set Definition'
};

const ITEM_SECTION_TITLES: Record<string, Record<string, string>> = {
  'emakigem:gem': {
    '基础信息': 'Basic Info',
    '效果与变量': 'Effects and Variables',
    '显示动作链': 'Display Action Chains',
    '费用与返还': 'Cost and Returns',
    '升级设置': 'Upgrade Settings',
    '触发动作': 'Trigger Actions'
  },
  'emakigem:socket-item': {
    '匹配规则': 'Match Rules',
    '插槽结构': 'Slot Structure',
    '宝石限制': 'Gem Limits',
    'GUI 模板': 'GUI Templates',
    '显示动作链': 'Display Action Chains'
  },
  'emakiitem:item': {
    '基础信息': 'Basic Info',
    '显示动作链': 'Display Action Chains',
    '更新策略': 'Update Strategy',
    '效果与变量': 'Effects and Variables',
    '原版组件': 'Vanilla Components',
    '套装归属': 'Set Binding',
    '装备条件': 'Equip Conditions',
    '修复配置': 'Repair Settings',
    '触发动作': 'Trigger Actions'
  },
  'emakiitem:set': {
    '基础信息': 'Basic Info',
    '套装部件': 'Set Pieces',
    '套装 Lore': 'Set Lore',
    '阈值效果': 'Threshold Effects'
  }
};

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
  const sourceContext = useMemo(() => ({ module, file, childPath, editor }), [module, file, childPath, editor]);

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
    api.actionTypes().then(setActionTypesResult).catch(() => { });
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
    const content = serializeItemYaml(data);
    const previewBaseLore = resolvePreviewBaseLore(data, baseLore as string[]);
    const requestedLevel = previewLevel;
    const requestId = previewRequestId.current + 1;
    previewRequestId.current = requestId;
    setPreview(null);
    setPreviewPending(true);
    let active = true;
    const timer = window.setTimeout(() => {
      api.previewItem(content, requestedLevel, baseName, previewBaseLore)
        .then(nextPreview => {
          if (!active || previewRequestId.current !== requestId) return;
          setPreview(nextPreview);
        })
        .catch(() => {
          if (!active || previewRequestId.current !== requestId) return;
          setPreview(localItemPreview(data, requestedLevel, baseName, previewBaseLore));
        })
        .finally(() => {
          if (active && previewRequestId.current === requestId) setPreviewPending(false);
        });
    }, 300);
    return () => { active = false; window.clearTimeout(timer); };
  }, [api, data, previewLevel, loading, baseName, baseLore, file.kind]);

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

  const draftContent = sourceError ? sourceText : serializeItemYaml(data);
  const sourceContent = draftContent;
  const changes = useMemo(() => diffRecords(data, originalData, '', 18), [data, originalData]);
  const changedPaths = useMemo(() => changedPathSet(changes), [changes]);
  const semanticDirty = !sourceError && changes.length > 0;
  const editorContext = useMemo(() => ({ moduleId: module.id, editorFields, changedPaths, economyProviders }), [module.id, editorFields, changedPaths, economyProviders]);

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
    return () => setToolbar(null);
  }, [setToolbar, editor?.title, fileTitle, module.id, filePath, semanticDirty, changes, sourceContent, sourceError, saving, loading, history.undo.length, history.redo.length, onReload]);

  if (loading) return <div className="ie-surface"><div className="ie-loading" role="status"><div className="ie-skeleton" aria-label={t('core.item.loadingAria')}><div className="ie-skeleton-line" style={{ width: '60%' }} /><div className="ie-skeleton-line" style={{ width: '80%' }} /><div className="ie-skeleton-line" style={{ width: '45%' }} /><div className="ie-skeleton-line" style={{ width: '70%' }} /></div></div></div>;
  if (error && !data) return <div className="ie-surface"><InlineError>{error}</InlineError>{onReload && <Button size="sm" onClick={onReload}>{t('core.action.retry')}</Button>}</div>;

  return (
    <div className="ie-surface" data-dirty={semanticDirty || undefined} data-original-size={originalContent.length || undefined}>
      {toast && <ToastNotice tone={toast.tone} style={{ position: 'absolute', top: 12, right: 12, zIndex: 50 }}>{toast.text}</ToastNotice>}
      {showLocalChrome && <EditorChrome
        className="ie-header"
        title={localizedEditorTitle(editor?.id, editor?.title ?? fileTitle ?? t('core.item.editorTitle'))}

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
        <div className="ie-workbench">
          <GenericPreviewPane moduleId={module.id} editorId={editor?.id} data={data} preview={preview} previewPending={previewPending} previewLevel={previewLevel} setPreviewLevel={setPreviewLevel} baseName={baseName} baseLore={baseLore as string[]} />
          <div className="ie-props-scroll">
            <div className="ie-props">
              {sections.map(section => (
                <CollapsibleSection
                  key={section.title}
                  title={localizedSectionTitle(editor?.id ?? file.editorId, section.title)}
                  comment={localizedSectionComment(editor?.id ?? file.editorId, section.title, section.comment)}
                  collapsible={section.collapsible ?? true}
                  defaultCollapsed={section.defaultCollapsed}
                  storageKey={`core:item-section:${editor?.id ?? file.editorId ?? file.kind}:${section.title}`}
                >
                  {section.fields.map(field => <FieldEditor key={field.path} field={field} data={data} originalData={originalData} setField={setField} actionTypesResult={actionTypesResult} editorId={editor?.id ?? file.editorId} />)}
                </CollapsibleSection>
              ))}
            </div>
          </div>
        </div>
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
  if (type === 'multiEnum' && field.options?.length) return <PropRow label={label} path={field.path} changed={changed} wide={field.wide ?? true}><MultiEnumEditor value={value} options={field.options} labelPrefix={field.optionLabelPrefix} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'material') return <PropRow label={label} path={field.path} changed={changed} wide={field.wide}><MaterialInput value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'textarea') return <PropRow label={label} path={field.path} changed={changed} wide><textarea rows={field.rows ?? 4} value={textValue(value)} onChange={e => setField(field.path, e.target.value)} placeholder={field.placeholder} /></PropRow>;
  if (type === 'stringList') return <PropRow label={label} path={field.path} changed={changed} wide><StringListEditor items={asStringList(value)} onChange={items => setField(field.path, items)} placeholder={field.placeholder} /></PropRow>;
  if (type === 'numberList') return <PropRow label={label} path={field.path} changed={changed} wide><NumberListEditor items={asList(value).map(item => Number(item) || 0)} onChange={items => setField(field.path, items)} /></PropRow>;
  if (type === 'map' || type === 'dynamicMap' || type === 'objectMap') return <PropRow label={label} path={field.path} changed={changed} wide><MapEditor value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'actions') {
    const mode = field.path.toLowerCase().includes('lore') ? 'lore' : 'name';
    return <PropRow label={label} path={field.path} changed={changed} wide><ScopedActionsEditor actions={parseActionList(value)} onChange={actions => setField(field.path, serializeActionList(actions))} actionTypes={mode === 'lore' ? actionTypesResult?.loreActions ?? [] : actionTypesResult?.nameActions ?? []} mode={mode} /></PropRow>;
  }
  if (type === 'effects') return <PropRow label={label} path={field.path} changed={false} wide><EffectsEditor value={value} path={field.path} onChange={next => setField(field.path, next)} actionTypesResult={actionTypesResult} /></PropRow>;
  if (type === 'attributeModifiers') return <PropRow label={label} path={field.path} changed={changed} wide><AttributeModifiersEditor value={value} path={field.path} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'repairMaterials') return <PropRow label={label} path={field.path} changed={changed} wide><RepairMaterialsEditor value={value} path={field.path} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'setPieces') return <PropRow label={label} path={field.path} changed={changed} wide><SetPiecesEditor value={value} path={field.path} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'setThresholds') return <PropRow label={label} path={field.path} changed={changed} wide><SetThresholdsEditor value={value} path={field.path} onChange={next => setField(field.path, next)} /></PropRow>;
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

function ScopedActionsEditor(props: { actions: ReturnType<typeof parseActionList>; onChange: (actions: ReturnType<typeof parseActionList>) => void; actionTypes: string[]; mode: 'name' | 'lore' }) {
  const context = React.useContext(EditorContext);
  return <ActionsEditor {...props} moduleId={context.moduleId} namespace={context.moduleId} editorFields={context.editorFields} />;
}

function ToggleButton({ id, checked, onChange }: { id?: string; checked: boolean; onChange: (next: boolean) => void }) {
  return <button id={id} type="button" className={`switch ${checked ? 'on' : ''}`} aria-pressed={checked} onClick={() => onChange(!checked)}><span />{checked ? t('core.config.booleanOn') : t('core.config.booleanOff')}</button>;
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

function EffectsEditor({ value, onChange, actionTypesResult, path }: { value: unknown; onChange: (effects: unknown[]) => void; actionTypesResult: ActionTypesResult | null; path?: string }) {
  const effects = asList(value).map(effect => asRecord(effect));
  const [expanded, setExpanded] = useState<Set<number>>(() => new Set(effects.map((_, index) => index)));
  const updateEffect = (index: number, nextEffect: AnyMap) => onChange(effects.map((effect, itemIndex) => itemIndex === index ? cleanObject(nextEffect) : effect));
  const removeEffect = (index: number) => onChange(effects.filter((_, itemIndex) => itemIndex !== index));
  const addEffect = (type: CoreEffectType) => {
    const next = [...effects, createCoreEffect(type)];
    onChange(next);
    setExpanded(previous => new Set([...previous, next.length - 1]));
  };
  const moveEffect = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= effects.length) return;
    const next = [...effects];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };
  const toggle = (index: number) => setExpanded(previous => {
    const next = new Set(previous);
    next.has(index) ? next.delete(index) : next.add(index);
    return next;
  });
  return <div className="prop-levels" role="list">
    {effects.map((effect, index) => {
      const type = textValue(effect.type) || 'variables';
      const coreType = isCoreEffectType(type) ? type : 'variables';
      const typeOptions = isCoreEffectType(type) ? CORE_EFFECT_TYPES : [...CORE_EFFECT_TYPES, type];
      const opened = expanded.has(index);
      return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
        <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggle(index)} onKeyDown={event => toggleByKeyboard(event, () => toggle(index))} aria-expanded={opened} aria-controls={`effect-body-${index}`}>
          <span className="prop-level-summary"><span className="prop-level-badge"><DisclosureChevron open={opened} className="prop-level-arrow" /> #{index + 1}</span>{coreEffectTypeLabel(type)}</span>
          <span className="prop-level-rate">{effectSummary(effect)}</span>
          <span className="prop-action-controls" onClick={stopEvent} onKeyDown={stopEvent}>
            <button type="button" onClick={() => moveEffect(index, -1)} disabled={index === 0} aria-label={t('core.field.move_up')}>↑</button>
            <button type="button" onClick={() => moveEffect(index, 1)} disabled={index === effects.length - 1} aria-label={t('core.field.move_down')}>↓</button>
            <button type="button" className="prop-action-del" onClick={() => removeEffect(index)} aria-label={t('core.field.delete')}>×</button>
          </span>
        </div>
        {opened && <div className="prop-level-body" id={`effect-body-${index}`}>
          <PropRow label="type" path={joinPath(path, index, 'type')}><SelectInput value={type} options={typeOptions} labelPrefix="effect" onChange={nextType => updateEffect(index, createCoreEffect(nextType as CoreEffectType))} /></PropRow>
          <EffectPayloadEditor effect={effect} type={coreType} originalType={type} path={joinPath(path, index)} onChange={nextEffect => updateEffect(index, nextEffect)} actionTypesResult={actionTypesResult} />
        </div>}
      </div>;
    })}
    <div className="prop-cost-actions">{CORE_EFFECT_TYPES.map(type => <button key={type} type="button" className="prop-add" onClick={() => addEffect(type)}>+ {coreEffectTypeLabel(type)}</button>)}</div>
  </div>;
}

function EffectPayloadEditor({ effect, type, originalType, onChange, actionTypesResult, path }: { effect: AnyMap; type: CoreEffectType; originalType: string; onChange: (effect: AnyMap) => void; actionTypesResult: ActionTypesResult | null; path?: string }) {
  const setPayload = (key: string, value: unknown) => onChange(cleanObject({ ...effect, [key]: value }));
  if (!isCoreEffectType(originalType)) return <GenericObjectEditor value={effect} reservedKeys={['type']} onChange={next => onChange({ type: originalType, ...next })} />;
  if (type === 'variables') return <PropRow label="variables" path={joinPath(path, 'variables')} wide><MapEditor value={effect.variables} valuePlaceholder="数值/公式" addKeyPrefix="variable" onChange={value => setPayload('variables', value)} /></PropRow>;
  if (type === 'name_action') return <PropRow label="name_actions" path={joinPath(path, 'name_actions')} wide><ScopedActionsEditor actions={parseActionList(effect.name_actions)} onChange={actions => setPayload('name_actions', serializeActionList(actions))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" /></PropRow>;
  if (type === 'lore_action') return <PropRow label="lore_actions" path={joinPath(path, 'lore_actions')} wide><ScopedActionsEditor actions={parseActionList(effect.lore_actions)} onChange={actions => setPayload('lore_actions', serializeActionList(actions))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" /></PropRow>;
  return <GenericObjectEditor value={effect} reservedKeys={['type']} onChange={next => onChange({ type, ...next })} />;
}

function SetPiecesEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap) => void; path?: string }) {
  const pieces = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: asRecord(entry) }));
  const slots = ['main_hand', 'off_hand', 'helmet', 'chestplate', 'leggings', 'boots'];
  const update = (index: number, key: string, patch: AnyMap) => {
    const nextEntries = pieces.map((piece, itemIndex) => itemIndex === index ? { key, value: cleanObject({ ...piece.value, ...patch }) } : piece);
    onChange(Object.fromEntries(nextEntries.filter(piece => piece.key.trim()).map(piece => [piece.key.trim(), piece.value])));
  };
  const remove = (index: number) => onChange(Object.fromEntries(pieces.filter((_, itemIndex) => itemIndex !== index).map(piece => [piece.key, piece.value])));
  const add = () => onChange({ ...asRecord(value), [nextUniqueKey(pieces.map(piece => piece.key), 'piece')]: { item: '', slot: 'main_hand', display: '' } });
  return <div className="prop-levels" role="list">
    {pieces.map((piece, index) => <div className="prop-cost-entry" key={index} role="listitem">
      <div className="prop-cost-entry-head"><span>{piece.key}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除套装部件 ${index + 1}`}>×</button></div>
      <PropRow label="piece_id" path={joinPath(path, piece.key)}><TextInput value={piece.key} onChange={nextKey => update(index, nextKey, {})} /></PropRow>
      <PropRow label="item" path={joinPath(path, piece.key, 'item')}><TextInput value={piece.value.item} onChange={item => update(index, piece.key, { item })} placeholder="example_item" /></PropRow>
      <PropRow label="slot" path={joinPath(path, piece.key, 'slot')}><SelectInput value={piece.value.slot ?? 'main_hand'} options={slots} labelPrefix="setSlot" onChange={slot => update(index, piece.key, { slot })} /></PropRow>
      <PropRow label="display" path={joinPath(path, piece.key, 'display')}><TextInput value={piece.value.display} onChange={display => update(index, piece.key, { display })} placeholder={piece.key} /></PropRow>
    </div>)}
    <button type="button" className="prop-add" onClick={add}>+ {t('core.item.setPieces.add')}</button>
  </div>;
}

function SetThresholdsEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap) => void; path?: string }) {
  const thresholds = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: asRecord(entry) })).sort((left, right) => Number(left.key) - Number(right.key));
  const update = (index: number, key: string, patch: AnyMap) => {
    const nextEntries = thresholds.map((threshold, itemIndex) => itemIndex === index ? { key, value: cleanObject({ ...threshold.value, ...patch }) } : threshold);
    onChange(Object.fromEntries(nextEntries.filter(threshold => threshold.key.trim()).map(threshold => [threshold.key.trim(), threshold.value])));
  };
  const remove = (index: number) => onChange(Object.fromEntries(thresholds.filter((_, itemIndex) => itemIndex !== index).map(threshold => [threshold.key, threshold.value])));
  const add = () => onChange({ ...asRecord(value), [nextNumericKey(thresholds.map(threshold => threshold.key), 2)]: { lore: [], ea_attributes: {}, es_skills: [] } });
  return <div className="prop-levels" role="list">
    {thresholds.map((threshold, index) => <div className="prop-cost-entry" key={index} role="listitem">
      <div className="prop-cost-entry-head"><span>{threshold.key} 件套</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除阈值 ${threshold.key}`}>×</button></div>
      <PropRow label="required" path={joinPath(path, threshold.key)}><NumberInput value={Number(threshold.key)} onChange={required => update(index, String(Math.max(1, required ?? 1)), {})} /></PropRow>
      <PropRow label="lore" path={joinPath(path, threshold.key, 'lore')} wide><StringListEditor items={asStringList(threshold.value.lore)} onChange={lore => update(index, threshold.key, { lore })} placeholder={uiCopy('[2件套] 物理攻击 +5', '[2-piece] Physical Attack +5')} /></PropRow>
      <PropRow label="ea_attributes" path={joinPath(path, threshold.key, 'ea_attributes')} wide><MapEditor value={threshold.value.ea_attributes} valuePlaceholder={uiCopy('属性值', 'Attribute value')} addKeyPrefix="attribute" onChange={ea_attributes => update(index, threshold.key, { ea_attributes })} /></PropRow>
      <PropRow label="es_skills" path={joinPath(path, threshold.key, 'es_skills')} wide><StringListEditor items={asStringList(threshold.value.es_skills)} onChange={es_skills => update(index, threshold.key, { es_skills })} placeholder="guardian_aura" /></PropRow>
    </div>)}
    <button type="button" className="prop-add" onClick={add}>+ {t('core.item.setThresholds.add')}</button>
  </div>;
}

function AttributeModifiersEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap[]) => void; path?: string }) {
  const modifiers = asList(value).map(entry => asRecord(entry));
  const operations = ['add_number', 'add_scalar', 'multiply_scalar_1'];
  const slots = ['any', 'hand', 'mainhand', 'offhand', 'head', 'chest', 'legs', 'feet', 'body'];
  const update = (index: number, patch: AnyMap) => onChange(modifiers.map((modifier, itemIndex) => itemIndex === index ? cleanObject({ ...modifier, ...patch }) : modifier));
  const remove = (index: number) => onChange(modifiers.filter((_, itemIndex) => itemIndex !== index));
  return <div className="prop-levels" role="list">
    {modifiers.map((modifier, index) => <div className="prop-cost-entry" key={index} role="listitem">
      <div className="prop-cost-entry-head"><span>{textValue(modifier.attribute, `attribute_${index + 1}`)}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除属性修饰符 ${index + 1}`}>×</button></div>
      <PropRow label="attribute" path={joinPath(path, index, 'attribute')}><TextInput value={modifier.attribute} onChange={attribute => update(index, { attribute })} placeholder="attack_damage" /></PropRow>
      <PropRow label="amount" path={joinPath(path, index, 'amount')}><TextInput value={modifier.amount} onChange={amount => update(index, { amount: parseLooseScalar(amount) })} placeholder={uiCopy('12.0 或 {range}', '12.0 or {range}')} /></PropRow>
      <PropRow label="operation" path={joinPath(path, index, 'operation')}><SelectInput value={modifier.operation ?? 'add_number'} options={operations} labelPrefix="attributeOperation" onChange={operation => update(index, { operation })} /></PropRow>
      <PropRow label="slot" path={joinPath(path, index, 'slot')}><SelectInput value={modifier.slot ?? 'any'} options={slots} labelPrefix="equipmentSlot" onChange={slot => update(index, { slot })} /></PropRow>
      <PropRow label="name" path={joinPath(path, index, 'name')}><TextInput value={modifier.name} onChange={name => update(index, { name })} placeholder="emakiitem:item/attribute" /></PropRow>
    </div>)}
    <button type="button" className="prop-add" onClick={() => onChange([...modifiers, { attribute: 'attack_damage', amount: 1, operation: 'add_number', slot: 'any', name: '' }])}>+ {t('core.item.attributeModifiers.add')}</button>
  </div>;
}

function RepairMaterialsEditor({ value, onChange, path }: { value: unknown; onChange: (value: AnyMap[]) => void; path?: string }) {
  const materials = asList(value).map(entry => asRecord(entry));
  const update = (index: number, patch: AnyMap) => onChange(materials.map((material, itemIndex) => itemIndex === index ? cleanObject({ ...material, ...patch }) : material));
  const remove = (index: number) => onChange(materials.filter((_, itemIndex) => itemIndex !== index));
  return <div className="prop-levels" role="list">
    {materials.map((material, index) => <div className="prop-cost-entry" key={index} role="listitem">
      <div className="prop-cost-entry-head"><span>{textValue(material.item) || firstItemSource(material.item_sources) || `material_${index + 1}`}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除修复材料 ${index + 1}`}>×</button></div>
      <PropRow label="item" path={joinPath(path, index, 'item')}><TextInput value={material.item} onChange={item => update(index, { item })} placeholder="minecraft-diamond" /></PropRow>
      <PropRow label="amount" path={joinPath(path, index, 'amount')}><NumberInput value={material.amount ?? 1} onChange={amount => update(index, { amount: amount ?? 1 })} /></PropRow>
      <PropRow label="restore" path={joinPath(path, index, 'restore')}><TextInput value={material.restore ?? material.repair_amount} onChange={restore => update(index, { restore, repair_amount: undefined })} placeholder={uiCopy('250 或 {max_damage} * .25', '250 or {max_damage} * .25')} /></PropRow>
    </div>)}
    <button type="button" className="prop-add" onClick={() => onChange([...materials, { item: 'minecraft-diamond', amount: 1, restore: 100 }])}>+ {t('core.item.repairMaterials.add')}</button>
  </div>;
}

function ActionLinesEditor({ label, value, onChange, path }: { label: string; value: unknown; onChange: (value: string[]) => void; path?: string }) {
  return <PropRow label={label} path={path} wide><StringListEditor items={asStringList(value)} onChange={onChange} placeholder="sendmessage text=&quot;...&quot;" /></PropRow>;
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

function localizedEditorTitle(editorId: string | undefined, fallback: string): string {
  const locale = getLocale();
  if (locale.startsWith('zh')) return fallback;
  return editorId ? ITEM_EDITOR_TITLES[editorId] ?? fallback : fallback;
}

function localizedSectionTitle(editorId: string | undefined, title: string): string {
  const locale = getLocale();
  const sectionMap = editorId ? ITEM_SECTION_TITLES[editorId] : undefined;
  if (locale.startsWith('zh')) return title;
  return sectionMap?.[title] ?? humanizeFieldLabel(title);
}

function localizedSectionComment(editorId: string | undefined, title: string, comment?: string): string | undefined {
  if (!comment) return undefined;
  const locale = getLocale();
  if (locale.startsWith('zh')) return comment;
  const commentMap: Record<string, string> | undefined = undefined;
  return commentMap?.[title] ?? humanizeFieldLabel(comment);
}

function localItemPreview(data: AnyMap, previewLevel: number, baseName: string, baseLore: string[]): ItemPreviewResult {
  const kind = inferLocalPreviewKind(data);
  const displayName = textValue(data.display_name ?? data.item_name ?? data.id, baseName);
  const lore = resolvePreviewBaseLore(data, baseLore);
  const material = materialFromItemSource(firstItemSource(data.item_sources ?? asRecord(data.match).item_sources) || data.material || data.item || 'stone');
  return {
    kind,
    id: textValue(data.id),
    material,
    baseName,
    baseLore,
    displayName,
    lore,
    variables: asRecord(data.variables),
    nameSteps: [],
    loreSteps: [],
    level: kind === 'gem' ? previewLevel : undefined,
    levels: kind === 'gem' ? configuredPreviewLevels(data, null) : []
  };
}

function inferLocalPreviewKind(data: AnyMap): string {
  if ('gem_type' in data || 'socket_compatibility' in data || 'inlay_cost' in data) return 'gem';
  if ('slots' in data && ('default_open_slots' in data || 'allowed_gem_types' in data)) return 'gem_socket_item';
  return 'generic_item';
}

function GenericPreviewPane({ moduleId, editorId, data, preview, previewPending, previewLevel, setPreviewLevel, baseName, baseLore }: { moduleId: string; editorId?: string; data: AnyMap; preview: ItemPreviewResult | null; previewPending: boolean; previewLevel: number; setPreviewLevel: (level: number) => void; baseName: string; baseLore: string[] }) {
  const source = firstItemSource(data.item_sources ?? asRecord(data.match).item_sources ?? preview?.material);
  const material = materialFromItemSource(source || data.material || preview?.material);
  const levels = configuredPreviewLevels(data, preview);
  const hasLevels = levels.length > 0;
  const [, refreshTextureOrder] = useState(0);
  const urls = materialUrls(material);
  const [imgFailed, setImgFailed] = useState(false);
  const previewMatchesLevel = preview?.kind !== 'gem' || !hasLevels || Number(preview.level) === previewLevel;
  const livePreview = previewMatchesLevel ? preview : null;
  const originalName = textValue(livePreview?.baseName) || baseName;
  const originalLore = previewStringList(livePreview?.baseLore, resolvePreviewBaseLore(data, baseLore));
  const resultName = textValue(livePreview?.displayName);
  const resultLore = livePreview ? asStringList(livePreview.lore) : [];
  const status = previewStatus(livePreview, previewPending);
  useEffect(() => setImgFailed(false), [material]);
  useEffect(() => subscribeTextureBases(() => { setImgFailed(false); refreshTextureOrder((version) => version + 1); }), []);

  return (
    <div className="ie-preview" role="complementary" aria-label={t('core.item.previewAria')}>
      <div className="ie-preview-icon">
        {urls.length > 0 && !imgFailed ? <img src={urls[0]} alt={material || t('core.item.iconAlt')} onError={e => { const target = e.currentTarget; const next = urls[urls.indexOf(target.src) + 1]; if (next) target.src = next; else setImgFailed(true); }} /> : <span className="ie-preview-fallback">{materialShortName(material) || '?'}</span>}
      </div>
      <div className="ie-preview-meta">
        <span className="ie-preview-kind">{previewKindLabel(livePreview, moduleId, editorId)}</span>
        {Boolean(livePreview?.id || data.id) && <code className="ie-preview-id">{textValue(livePreview?.id ?? data.id)}</code>}
        <span className="ie-preview-source">{displaySource(source || material)}</span>
        <span className={`ie-preview-status ${status.tone}`}>{status.text}</span>
      </div>
      {hasLevels && <div className="ie-level-panel">
        <div className="ie-level-head"><span>{t('core.item.preview.levelTitle')}</span><code>{t('core.item.preview.upgradeLevel', { level: previewLevel })}</code></div>
        <div className="ie-level-rail">
          {levels.map(level => <button key={level} type="button" className={level === previewLevel ? 'active' : ''} onClick={() => setPreviewLevel(level)} aria-pressed={level === previewLevel}>Lv.{level}</button>)}
        </div>
        <p className="ie-level-hint">{t('core.item.preview.levelHint')}</p>
      </div>}
      <div className="ie-preview-compare">
        <PreviewTooltipBlock title={t('core.item.preview.original')} name={originalName} lore={originalLore} emptyText={t('core.item.preview.emptyLore')} />
        <PreviewTooltipBlock title={hasLevels ? t('core.item.preview.resultForLevel', { level: previewLevel }) : t('core.item.preview.result')} name={resultName} lore={resultLore} refreshing={previewPending} emptyText={previewPending ? t('core.item.preview.syncing') : t('core.item.preview.emptyResult')} />
      </div>
      <PreviewPipelineSummary preview={livePreview} editorId={editorId} />
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

function previewStringList(value: unknown, fallback: string[]): string[] {
  const lines = asStringList(value);
  return lines.length ? lines : fallback;
}

function previewStatus(preview: ItemPreviewResult | null, pending: boolean): { tone: 'live' | 'syncing' | 'failed'; text: string } {
  if (pending) return { tone: 'syncing', text: t('core.item.previewStatus.syncing') };
  if (preview) return { tone: 'live', text: t('core.item.previewStatus.live') };
  return { tone: 'failed', text: t('core.item.previewStatus.failed') };
}

function previewKindLabel(preview: ItemPreviewResult | null, moduleId: string, editorId?: string): string {
  if (preview?.kind === 'gem') return t('emakigem.preview.kind.gem', undefined, 'Gem');
  if (preview?.kind === 'gem_socket_item') return t('emakigem.preview.kind.socket', undefined, 'Socket Item');
  if (editorId?.startsWith('emakiitem:')) return t('emakiitem.preview.kind', undefined, 'Custom Item');
  return t('core.item.genericKind');
}

function PreviewPipelineSummary({ preview, editorId }: { preview: ItemPreviewResult | null; editorId?: string }) {
  if (editorId?.startsWith('emakigem:')) return null;
  const variables = Object.entries(preview?.variables ?? {});
  const nameSteps = preview?.nameSteps ?? [];
  const loreSteps = preview?.loreSteps ?? [];
  if (!variables.length && !nameSteps.length && !loreSteps.length) return null;
  return <div className="ie-preview-debug" aria-label={t('core.item.preview.debugAria')}>
    {variables.length > 0 && <PreviewVariableList entries={variables} />}
    {nameSteps.length > 0 && <PreviewStepList title={t('core.item.preview.nameSteps')} steps={nameSteps} />}
    {loreSteps.length > 0 && <PreviewStepList title={t('core.item.preview.loreSteps')} steps={loreSteps} />}
  </div>;
}

function PreviewVariableList({ entries }: { entries: [string, unknown][] }) {
  return <div className="ie-preview-debug-block">
    <div className="ie-preview-debug-head"><span>{t('core.item.preview.variables')}</span><code>{entries.length}</code></div>
    <div className="ie-preview-vars">
      {entries.slice(0, 6).map(([key, value]) => <div className="ie-preview-var" key={key}><code>{key}</code><span>{previewValue(value)}</span></div>)}
      {entries.length > 6 && <span className="ie-preview-more">{t('core.item.preview.moreVariables', { count: entries.length - 6 })}</span>}
    </div>
  </div>;
}

function PreviewStepList({ title, steps }: { title: string; steps: ItemPreviewStep[] }) {
  return <div className="ie-preview-debug-block">
    <div className="ie-preview-debug-head"><span>{title}</span><code>{steps.length}</code></div>
    <div className="ie-preview-steps">
      {steps.slice(0, 5).map((step, index) => <div className="ie-preview-step" key={`${step.action}-${index}`}>
        <code>{step.action || `#${index + 1}`}</code>
        <span>{previewStepSummary(step)}</span>
      </div>)}
      {steps.length > 5 && <span className="ie-preview-more">{t('core.item.preview.moreSteps', { count: steps.length - 5 })}</span>}
    </div>
  </div>;
}

function previewStepSummary(step: ItemPreviewStep): string {
  if (Array.isArray(step.before) || Array.isArray(step.after)) {
    const before = Array.isArray(step.before) ? step.before.length : 0;
    const after = Array.isArray(step.after) ? step.after.length : 0;
    const content = Array.isArray(step.content) && step.content.length ? t('core.item.preview.writeLines', { count: step.content.length }) : '';
    const anchorValue = textValue(step.anchor);
    const anchor = anchorValue ? t('core.item.preview.anchor', { anchor: anchorValue }) : '';
    return `${t('core.item.preview.lineChange', { before, after })}${content}${anchor}`;
  }
  const after = textValue(step.after ?? step.result);
  const before = textValue(step.before);
  if (before || after) return before ? `${before} → ${after}` : after;
  return textValue(step.value, t('core.item.preview.executed'));
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

function effectSummary(effect: AnyMap): string {
  const type = textValue(effect.type);
  if (type === 'variables') return `${Object.keys(asRecord(effect.variables)).length} 个变量`;
  if (type === 'name_action') return `${asList(effect.name_actions).length} 个名称动作`;
  if (type === 'lore_action') return `${asList(effect.lore_actions).length} 个 Lore 动作`;
  return `${Math.max(0, Object.keys(effect).length - 1)} 个字段`;
}

function nextNumericKey(keys: string[], fallback: number): string {
  const numeric = keys.map(key => Number(key)).filter(value => Number.isFinite(value));
  return String(Math.max(fallback - 1, ...numeric) + 1);
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
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(Array.isArray(entry) && entry.length === 0))) as T;
}

function stopEvent(event: React.SyntheticEvent) {
  event.stopPropagation();
}

function toggleByKeyboard(event: React.KeyboardEvent, action: () => void) {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  action();
}
