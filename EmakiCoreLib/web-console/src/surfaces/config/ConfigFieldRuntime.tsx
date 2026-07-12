import { useEffect, useState, type DependencyList } from 'react';
import { DisclosureChevron, NumberListEditor, StandardActionsField, StandardEconomyProviderSelect, StandardEffectsEditor, StringListEditor, VariablesMapEditor } from '../../components';
import { useStableEntries } from '../../components/useStableEntries';
import { getLocale, t } from '../../i18n';
import { fieldLabel, humanizeFieldLabel, optionLabel } from '../../lib';
import type { WebConfigFieldSchema, WebConfigNode } from '../../types';
import { defaultSchemaFieldValue, inferConfigFieldType } from './ConfigObjectRuntime';
import { hasMeaningfulConfigValue } from './ConfigNodeRuntime';

const OBJECT_LIST_RENDER = {
  collapseThreshold: 10,
  initialRows: 30,
  rowBatchSize: 30,
} as const;

const CONFIG_LAZY_SECTION_THRESHOLD = 10;

type ProgressDeps = DependencyList;

export function renderControl(node: WebConfigNode, value: unknown, setValue: (v: unknown) => void, label: string, moduleId: string) {
  if (node.type === 'boolean') return <BooleanSwitch checked={value === true} label={`${label}: ${value ? t('core.config.booleanOn') : t('core.config.booleanOff')}`} onToggle={() => setValue(!value)} />;
  if (node.type === 'enum' && node.options) return <select value={str(value)} aria-label={label} onChange={(e) => setValue(e.target.value)}>{node.options.map(opt => <option key={opt} value={opt}>{optionLabel(node.optionLabelPrefix || node.path, opt, { moduleId })}</option>)}</select>;
  if (node.type === 'economyProvider') return <StandardEconomyProviderSelect value={value} onChange={setValue} moduleId={moduleId} optionPrefix={node.optionLabelPrefix || 'economyProvider'} />;
  if (node.type === 'number') return <NumberField value={value} onChange={setValue} ariaLabel={label} />;
  if (node.type === 'json') return <JsonField value={value} onChange={setValue} ariaLabel={label} />;
  if (node.type === 'variablesMap') return <VariablesMapEditor value={value} onChange={setValue} />;
  if (node.type === 'actions') return <StandardActionsField value={value} onChange={setValue} path={node.path} moduleId={moduleId} />;
  if (node.type === 'effects') return <StandardEffectsEditor value={value} onChange={setValue} path={node.path} moduleId={moduleId} />;
  if (node.type === 'dynamic_map') return <DynamicMapEditor value={value} setValue={setValue} />;
  if (node.type === 'map') return <ObjectMapEditor value={value} onChange={setValue} />;
  if (node.type === 'object') {
    if (node.itemFields?.length) return <SchemaObjectEditor field={configNodeToSchemaField(node)} value={value} onChange={setValue} moduleId={moduleId} ariaLabel={label} />;
    return <ObjectMapEditor value={value} onChange={setValue} />;
  }
  if (node.type === 'stringList') return <StringListEditor items={asStringListValue(value)} onChange={setValue} layout={isInlineScalarListPath(node.path) ? 'inline' : 'block'} />;
  if (node.type === 'numberList') return <NumberListEditor items={asNumberListValue(value)} onChange={setValue} layout={isInlineScalarListPath(node.path) ? 'inline' : 'block'} />;
  if (node.type === 'objectList') {
    const items = Array.isArray(value) ? value : [];
    return <ObjectListEditor node={node} items={items} setValue={setValue} moduleId={moduleId} headerAdd />;
  }
  if (node.type === 'list') {
    const items = Array.isArray(value) ? value : [];
    const hasObjectSchema = Boolean(node.itemFields?.length) && !node.itemFields?.every(field => field.path === 'value' && field.type === 'text');
    const hasObjectItems = items.some(isPlainObject) || hasObjectSchema;
    if (hasObjectItems) return <ObjectListEditor node={node} items={items} setValue={setValue} moduleId={moduleId} />;
    return <StringListEditor items={items.map(str)} onChange={setValue} />;
  }
  return <input aria-label={label} value={str(value)} onChange={(e) => setValue(e.target.value)} />;
}

export function isWideConfigNode(node: WebConfigNode): boolean {
  return node.type === 'dynamic_map'
    || node.type === 'map'
    || node.type === 'list'
    || (node.type === 'stringList' && !isInlineScalarListPath(node.path))
    || node.type === 'numberList'
    || node.type === 'objectList'
    || node.type === 'object'
    || node.type === 'actions'
    || node.type === 'effects'
    || node.type === 'variablesMap'
    || node.type === 'json';
}

function isInlineScalarListPath(path: string | undefined): boolean {
  const key = String(path ?? '').split('.').pop() ?? '';
  return key === 'item_sources' || key === 'item_source';
}

function BooleanSwitch({ checked, label, onToggle }: { checked: boolean; label: string; onToggle: () => void }) {
  return <button type="button" className={`switch ${checked ? 'on' : ''}`} aria-pressed={checked} aria-label={label} onClick={onToggle}>
    <span className="switch-icon" aria-hidden="true">
      {checked ? <svg viewBox="0 0 16 16" focusable="false"><path d="M3.5 8.2 6.7 11.2 12.8 4.8" /></svg> : <svg viewBox="0 0 16 16" focusable="false"><path d="M4.7 4.7 11.3 11.3M11.3 4.7 4.7 11.3" /></svg>}
    </span>
    {checked ? t('core.config.booleanOn') : t('core.config.booleanOff')}
  </button>;
}

function ObjectMapEditor({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  return <KeyValueMapEditor value={value} onChange={onChange} addKeyPrefix="key" />;
}

function KeyValueMapEditor({ value, onChange, addKeyPrefix }: { value: unknown; onChange: (value: unknown) => void; addKeyPrefix: string }) {
  const entries = Object.entries(isPlainObject(value) ? value : {});
  const keys = entries.map(([key]) => key);

  const updateKey = (index: number, nextKey: string) => {
    const normalized = nextKey.trim().replace(/\s+/g, '_');
    const next: Record<string, unknown> = {};
    entries.forEach(([key, entry], itemIndex) => {
      const targetKey = itemIndex === index ? normalized : key;
      if (targetKey) next[targetKey] = entry;
    });
    onChange(next);
  };

  const updateValue = (index: number, nextValue: unknown) => {
    const next: Record<string, unknown> = {};
    entries.forEach(([key, entry], itemIndex) => {
      next[key] = itemIndex === index ? nextValue : entry;
    });
    onChange(next);
  };

  const remove = (index: number) => onChange(Object.fromEntries(entries.filter((_, itemIndex) => itemIndex !== index)));

  const add = () => {
    let index = 1;
    while (keys.includes(`${addKeyPrefix}_${index}`)) index += 1;
    onChange({ ...(isPlainObject(value) ? value : {}), [`${addKeyPrefix}_${index}`]: '' });
  };

  return <div className="dynamic-map-editor dynamic-map-editor--kv">
    {entries.map(([key, entry], index) => <div className="dynamic-map-row" key={`${key}:${index}`}>
      <input className="dynamic-map-key" value={key} onChange={event => updateKey(index, event.target.value)} aria-label={t('core.kv.key')} />
      <MapValueField value={entry} onChange={next => updateValue(index, next)} ariaLabel={`${key} ${t('core.kv.value')}`} />
      <button type="button" onClick={() => remove(index)}>{t('core.config.delete')}</button>
    </div>)}
    <button type="button" className="add-row" onClick={add}>{t('core.config.create')}</button>
  </div>;
}

function MapValueField({ value, onChange, ariaLabel }: { value: unknown; onChange: (value: unknown) => void; ariaLabel: string }) {
  if (isPlainObject(value) || Array.isArray(value)) return <JsonInlineField value={value} onChange={onChange} ariaLabel={ariaLabel} />;
  return <input className="dynamic-map-value" value={scalarMapText(value)} onChange={event => onChange(parseLooseConfigValue(event.target.value))} aria-label={ariaLabel} />;
}

function JsonInlineField({ value, onChange, ariaLabel }: { value: unknown; onChange: (value: unknown) => void; ariaLabel: string }) {
  const [text, setText] = useState(() => formatJsonFieldValue(value));
  const [error, setError] = useState('');
  useEffect(() => { setText(formatJsonFieldValue(value)); setError(''); }, [value]);
  function handleChange(nextText: string) {
    setText(nextText);
    try {
      onChange(nextText.trim() ? JSON.parse(nextText) : {});
      setError('');
    } catch {
      setError(configInlineText('JSON 格式无效，修正后才会写入。', 'Invalid JSON; fix it before saving.'));
    }
  }
  return <div className="map-json-field"><textarea rows={Math.min(8, Math.max(2, text.split('\n').length))} value={text} onChange={event => handleChange(event.target.value)} aria-label={ariaLabel} aria-invalid={error ? 'true' : undefined} />{error && <small className="field-error" role="alert">{error}</small>}</div>;
}

function ObjectListEditor({ node, items, setValue, moduleId, compact = false, headerAdd = false }: { node: WebConfigNode; items: unknown[]; setValue: (v: unknown) => void; moduleId: string; compact?: boolean; headerAdd?: boolean }) {
  const objectItems: Record<string, unknown>[] = items.map(item => isPlainObject(item) ? item : {});
  const stableRef = useStableEntries(objectItems);
  const stable = stableRef.current;
  const keys = objectListKeys(node, objectItems);
  const defaultCollapsedRows = stable.filter(entry => stable.length > OBJECT_LIST_RENDER.collapseThreshold || keys.length > CONFIG_LAZY_SECTION_THRESHOLD || !hasMeaningfulConfigValue(entry.data)).map(entry => entry._id);
  const largeList = stable.length > OBJECT_LIST_RENDER.collapseThreshold;
  const [collapsed, setCollapsed] = useState<Set<number>>(() => new Set(defaultCollapsedRows));
  const [emptyExpanded, setEmptyExpanded] = useState(false);
  const visibleCount = useProgressiveCount(stable.length, OBJECT_LIST_RENDER.initialRows, OBJECT_LIST_RENDER.rowBatchSize, [node.path, stable.length]);
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
      <span>{fieldLabel(node.path, { moduleId, namespace: moduleId, fallback: getLocale().startsWith('zh') ? node.label : humanizeFieldLabel(node.path) })}</span>
    </button>
    {emptyExpanded && <div className="object-list-empty-body"><p>{t('core.config.emptyListHint')}</p><button type="button" className="add-row" onClick={addEntry}>{t('core.config.addItem')}</button></div>}
    {!emptyExpanded && <button type="button" className="add-row" onClick={addEntry}>{t('core.config.addItem')}</button>}
  </div>;

  return <div className={`object-list-editor${compact ? ' object-list-editor--compact' : ''}${largeList ? ' object-list-editor--large' : ''}${headerAdd ? ' object-list-editor--header-add' : ''}`}>
    {headerAdd && <button type="button" className="object-list-header-add" onClick={addEntry}>{t('core.config.addItem')}</button>}
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
    {!headerAdd && <button type="button" className="add-row" onClick={addEntry}>{t('core.config.addItem')}</button>}
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

export function renderSchemaField(field: WebConfigFieldSchema | undefined, value: unknown, onChange: (value: unknown) => void, moduleId: string, ariaLabel: string, siblingItems: Record<string, unknown>[] = [], currentIndex = -1) {
  const type = field?.type;
  void currentIndex;
  if (type === 'boolean' || typeof value === 'boolean') return <BooleanSwitch checked={value === true} label={ariaLabel} onToggle={() => onChange(!value)} />;
  if (type === 'number' || typeof value === 'number') return <NumberField value={value} onChange={onChange} ariaLabel={ariaLabel} />;
  if (type === 'list' || type === 'stringList') return <StringListEditor items={asStringListValue(value)} onChange={onChange} />;
  if (type === 'numberList') return <NumberListEditor items={asNumberListValue(value)} onChange={onChange} />;
  if (type === 'objectList') {
    const childNode = schemaFieldToConfigNode(field, ariaLabel, value, siblingItems);
    return <ObjectListEditor node={childNode} items={Array.isArray(value) ? value : []} setValue={onChange} moduleId={moduleId} compact />;
  }
  if (type === 'variablesMap') return <VariablesMapEditor value={value} onChange={onChange} />;
  if (type === 'actions') return <StandardActionsField value={value} onChange={onChange} path={field?.path} moduleId={moduleId} />;
  if (type === 'effects') return <StandardEffectsEditor value={value} onChange={onChange} path={field?.path} moduleId={moduleId} />;
  if (type === 'dynamic_map') return <DynamicMapEditor value={value} setValue={onChange} />;
  if (type === 'map') return <ObjectMapEditor value={value} onChange={onChange} />;
  if (type === 'economyProvider') return <StandardEconomyProviderSelect value={value} onChange={onChange} moduleId={moduleId} optionPrefix={field?.optionLabelPrefix || 'economyProvider'} />;
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
  return Object.fromEntries(keys.map(key => [key, defaultObjectListValue(sample?.[key])]))
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
  const entries = Object.entries(item).slice(0, 2).map(([key, value]) => `${key}: ${summaryScalar(value)}`);
  return entries.length ? entries.join(' · ') : `#${index + 1}`;
}

function summaryScalar(value: unknown): string {
  if (value == null) return '';
  if (Array.isArray(value)) return `[${value.length}]`;
  if (typeof value === 'object') return `{${Object.keys(value as Record<string, unknown>).length}}`;
  return String(value);
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
  if (field?.type === 'objectList' || field?.type === 'list' || field?.type === 'stringList' || field?.type === 'numberList') return true;
  return Array.isArray(value);
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
  return <KeyValueMapEditor value={value} onChange={setValue} addKeyPrefix="field" />;
}

function useProgressiveCount(total: number, initial: number, batch: number, deps: ProgressDeps): number {
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

function isPlainObject(v: unknown): v is Record<string, unknown> { return typeof v === 'object' && v !== null && !Array.isArray(v); }
function scalarMapText(value: unknown): string { return value == null ? '' : String(value); }
function parseLooseConfigValue(text: string): unknown {
  const trimmed = text.trim();
  if (!trimmed) return '';
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  if (trimmed === 'null') return null;
  if (/^-?(?:0|[1-9]\d*)(?:\.\d+)?$/.test(trimmed)) return Number(trimmed);
  if (/^[\[{]/.test(trimmed)) {
    try { return JSON.parse(trimmed); } catch { return text; }
  }
  return text;
}
function str(v: unknown): string { if (v == null) return ''; if (typeof v === 'object') try { return JSON.stringify(v, null, 2); } catch { return ''; } return String(v); }
