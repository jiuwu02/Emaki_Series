import { useEffect, useMemo, useState } from 'react';
import { t } from '../i18n';
import { asRecord, type AnyMap } from '../lib/itemUtils';
import type { ItemComponentCapability } from '../lib/itemStructure';
import { Button } from './Button';

export type ItemComponentsEditorProps = {
  value: unknown;
  onChange: (value: AnyMap) => void;
  capabilities?: ItemComponentCapability[];
  reservedIds?: string[];
};

export function ItemComponentsEditor({ value, onChange, capabilities, reservedIds = [] }: ItemComponentsEditorProps) {
  const reserved = new Set(reservedIds);
  const components = asRecord(value);
  const entries = Object.entries(components).filter(([id]) => !reserved.has(id));
  const capabilityMap = useMemo(() => new Map((capabilities ?? []).map(entry => [normalizeId(entry.id), entry])), [capabilities]);
  const replaceEntries = (nextEntries: Array<[string, unknown]>) => {
    const next = { ...components };
    for (const id of Object.keys(next)) if (!reserved.has(id)) delete next[id];
    for (const [id, componentValue] of nextEntries) next[id] = componentValue;
    onChange(next);
  };
  const updateId = (index: number, id: string) => replaceEntries(entries.map<[string, unknown]>((entry, entryIndex) => entryIndex === index ? [id, entry[1]] : entry));
  const updateValue = (index: number, componentValue: unknown) => replaceEntries(entries.map<[string, unknown]>((entry, entryIndex) => entryIndex === index ? [entry[0], componentValue] : entry));
  const remove = (index: number) => replaceEntries(entries.filter((_, entryIndex) => entryIndex !== index));
  const add = () => replaceEntries([...entries, [nextComponentId(entries.map(([id]) => id)), {}]]);

  return <div className="sub-editor advanced-field-list">
    <div className="sub-editor-head"><span>{t('core.item.components')}</span><button type="button" onClick={add}>{t('core.gui.add')}</button></div>
    <p className="muted-copy">{t('core.item.componentNamespaceHint')}</p>
    {entries.map(([id, componentValue], index) => {
      const capability = capabilityMap.get(normalizeId(id));
      return <div className="advanced-field-row" key={index}>
        <div className="field-row">
          <input value={id} onChange={event => updateId(index, event.target.value)} onBlur={() => id.trim() ? updateId(index, id.trim()) : remove(index)} placeholder="custom_model_data" aria-label={t('core.item.componentId')} />
          <button type="button" className="advanced-field-delete" onClick={() => remove(index)}>{t('core.config.delete')}</button>
        </div>
        <ComponentValueEditor value={componentValue} onApply={next => updateValue(index, next)} />
        <ComponentCapabilityStatus capability={capability} hasCatalog={Boolean(capabilities?.length)} />
      </div>;
    })}
    {!entries.length && <p className="muted-copy">{t('core.item.componentsEmpty')}</p>}
  </div>;
}

function ComponentValueEditor({ value, onApply }: { value: unknown; onApply: (value: unknown) => void }) {
  const serialized = serializeComponentValue(value);
  const [text, setText] = useState(serialized);
  const [error, setError] = useState('');
  useEffect(() => { setText(serialized); setError(''); }, [serialized]);
  return <div className="advanced-json-field">
    <textarea className="advanced-json" value={text} onChange={event => { setText(event.target.value); setError(''); }} spellCheck={false} aria-label={t('core.item.componentValue')} aria-invalid={Boolean(error)} />
    {error && <small className="json-error">{error}</small>}
    <Button variant="soft" fullWidth disabled={text === serialized} onClick={() => {
      try { onApply(parseComponentValue(text)); setError(''); } catch (err) { setError(err instanceof Error ? err.message : t('core.gui.jsonParseFailed')); }
    }}>{t('core.item.componentApply')}</Button>
  </div>;
}

function ComponentCapabilityStatus({ capability, hasCatalog }: { capability?: ItemComponentCapability; hasCatalog: boolean }) {
  if (!hasCatalog) return <small className="muted-copy">{t('core.item.componentCapabilityFallback')}</small>;
  if (!capability) return <small className="muted-copy">{t('core.item.componentCapabilityUnknown')}</small>;
  const minimumVersion = capability.minimumVersion ?? capability.minVersion;
  const unsupported = capabilityIsUnsupported(capability);
  const status = unsupported ? t('core.item.componentUnsupported') : t('core.item.componentSupported');
  return <small className="muted-copy">{status}{minimumVersion ? ` · ${t('core.item.componentMinimumVersion', { version: minimumVersion })}` : ''}{capability.reason ? ` · ${capability.reason}` : ''}</small>;
}

function capabilityIsUnsupported(capability: ItemComponentCapability | undefined): boolean {
  if (!capability) return false;
  if (capability.supported === false || capability.currentSupport === false) return true;
  return String(capability.currentSupport ?? '').toLowerCase() === 'unsupported';
}

function normalizeId(value: string): string {
  const normalized = value.trim().toLowerCase();
  return !normalized || normalized.includes(':') ? normalized : `minecraft:${normalized}`;
}

function nextComponentId(ids: string[]): string {
  let index = ids.length + 1;
  let id = `component_${index}`;
  while (ids.some(existing => normalizeId(existing) === normalizeId(id))) id = `component_${++index}`;
  return id;
}

function serializeComponentValue(value: unknown): string {
  const serialized = JSON.stringify(value, null, 2);
  return serialized === undefined ? 'null' : serialized;
}

function parseComponentValue(value: string): unknown {
  const trimmed = value.trim();
  if (!trimmed) return '';
  try { return JSON.parse(trimmed); } catch {
    if (/^[\[{]/.test(trimmed)) throw new Error(t('core.gui.jsonParseFailed'));
    if (trimmed === 'true') return true;
    if (trimmed === 'false') return false;
    if (trimmed === 'null') return null;
    if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
    return value;
  }
}
