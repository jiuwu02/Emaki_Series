import React, { createContext, useContext, useEffect, useState } from 'react';
import type { ActionTypesResult } from '../api';
import { ActionsEditor, parseActionList, serializeActionList } from './ActionsEditor';
import { VariablesMapEditor } from './VariablesMapEditor';
import { StringListEditor } from './StringListEditor';
import { NumberListEditor } from './NumberListEditor';
import { KvTable } from './KvTable';
import { PropRow } from './PropRow';
import { DisclosureChevron } from './SectionHead';
import { asList, asRecord, asStringList } from '../lib/itemUtils';
import { fieldLabel, optionLabel, textValue, isChangedFieldPath } from '../lib';
import { t, getLocale } from '../i18n';
import {
  getEffectTypeDefinitions,
  getEffectTypeDefinition,
  createEffectValue,
  type EffectTypeDefinition,
  type EffectPayloadField
} from '../effectTypeRegistry';
import type { WebEditorField } from '../types';

/**
 * Canonical CoreLib Name/Lore action types. These mirror the server-side
 * NameOperationRegistry / LoreOperationRegistry registered actions and are used
 * as a fallback before the backend `/api/items/action-types` response arrives.
 */
export const CANONICAL_NAME_ACTIONS: string[] = ['replace', 'prepend_prefix', 'append_suffix', 'regex_replace'];
export const CANONICAL_LORE_ACTIONS: string[] = [
  'append', 'prepend', 'insert_above', 'insert_below', 'search_insert', 'search_insert_above', 'search_insert_below',
  'replace_line', 'replace_text', 'replace_text_all', 'delete_line', 'regex_replace'
];

const FALLBACK_ACTION_TYPES: ActionTypesResult = { nameActions: CANONICAL_NAME_ACTIONS, loreActions: CANONICAL_LORE_ACTIONS };

const ActionTypesContext = createContext<ActionTypesResult>(FALLBACK_ACTION_TYPES);

type ActionTypesSource = { actionTypes(): Promise<ActionTypesResult> };

/**
 * Provides CoreLib Name/Lore action types to every standard action/effect editor
 * in the subtree. Fetches once from the backend and falls back to canonical
 * source-of-truth lists, so structured editors never collapse to raw text inputs.
 */
export function ActionTypesProvider({ api, children }: { api: ActionTypesSource; children: React.ReactNode }) {
  const [actionTypes, setActionTypes] = useState<ActionTypesResult>(FALLBACK_ACTION_TYPES);
  useEffect(() => {
    let active = true;
    api.actionTypes()
      .then(result => {
        if (!active) return;
        setActionTypes({
          nameActions: result.nameActions?.length ? result.nameActions : CANONICAL_NAME_ACTIONS,
          loreActions: result.loreActions?.length ? result.loreActions : CANONICAL_LORE_ACTIONS
        });
      })
      .catch(() => { /* keep canonical fallback */ });
    return () => { active = false; };
  }, [api]);
  return <ActionTypesContext.Provider value={actionTypes}>{children}</ActionTypesContext.Provider>;
}

/** Read the current CoreLib Name/Lore action types from context (with canonical fallback). */
export function useActionTypes(): ActionTypesResult {
  return useContext(ActionTypesContext);
}

/**
 * Provides the set of changed config paths to nested standard editors so
 * structured sub-fields (e.g. effect payload rows) can render a change
 * highlight. Surfaces that track diffs wrap their form with the provider;
 * editors without diff tracking fall back to an empty set (no highlight).
 */
const ChangedPathsContext = createContext<ReadonlySet<string>>(new Set());

export function ChangedPathsProvider({ changedPaths, children }: { changedPaths: ReadonlySet<string>; children: React.ReactNode }) {
  return <ChangedPathsContext.Provider value={changedPaths}>{children}</ChangedPathsContext.Provider>;
}

/** Read the set of changed config paths from context. */
export function useChangedPaths(): ReadonlySet<string> {
  return useContext(ChangedPathsContext);
}

export type StandardEditorScope = {
  moduleId?: string;
  namespace?: string;
  editorFields?: Record<string, WebEditorField>;
  /** Optional explicit action types; defaults to context value. */
  actionTypes?: ActionTypesResult;
};

/** Resolve the action mode (name vs lore) from a config path. */
export function actionModeFromPath(path: string | undefined): 'name' | 'lore' {
  return String(path ?? '').toLowerCase().includes('lore') ? 'lore' : 'name';
}

/**
 * Unified Name/Lore action-list editor. The mode is auto-detected from the field
 * path unless explicitly provided. Reads available action types from the
 * ActionTypesProvider so callers never need to wire them up manually.
 */
export function StandardActionsField({ value, onChange, mode, path, moduleId, namespace, editorFields, actionTypes }: StandardEditorScope & {
  value: unknown;
  onChange: (value: unknown[]) => void;
  mode?: 'name' | 'lore';
  path?: string;
}) {
  const contextTypes = useActionTypes();
  const resolvedTypes = actionTypes ?? contextTypes;
  const resolvedMode = mode ?? actionModeFromPath(path);
  const options = resolvedMode === 'lore' ? resolvedTypes.loreActions : resolvedTypes.nameActions;
  return <ActionsEditor
    actions={parseActionList(value)}
    onChange={actions => onChange(serializeActionList(actions))}
    actionTypes={options}
    mode={resolvedMode}
    moduleId={moduleId}
    namespace={namespace ?? moduleId}
    editorFields={editorFields}
  />;
}

/**
 * Unified CoreLib effects-list editor, driven by the effect type registry.
 * For each effect entry it shows ONLY the payload fields declared by the
 * selected type's definition (registered per module), so a `variables` effect
 * never renders EA attribute / ES skill / action inputs and vice versa. The
 * same component is used by every plugin and page for a consistent style.
 */
export function StandardEffectsEditor({ value, onChange, path, moduleId, namespace, editorFields, actionTypes }: StandardEditorScope & {
  value: unknown;
  onChange: (effects: unknown[]) => void;
  path?: string;
}) {
  const contextTypes = useActionTypes();
  const resolvedTypes = actionTypes ?? contextTypes;
  const scope: StandardEditorScope = { moduleId, namespace: namespace ?? moduleId, editorFields, actionTypes: resolvedTypes };
  const definitions = getEffectTypeDefinitions(moduleId);
  const effects = asList(value).map(effect => asRecord(effect));
  const [expanded, setExpanded] = useState<Set<number>>(() => new Set(effects.map((_, index) => index)));
  const updateEffect = (index: number, nextEffect: Record<string, unknown>) => onChange(effects.map((effect, itemIndex) => itemIndex === index ? cleanObject(nextEffect) : effect));
  const removeEffect = (index: number) => onChange(effects.filter((_, itemIndex) => itemIndex !== index));
  const addEffect = (def: EffectTypeDefinition) => {
    const next = [...effects, createEffectValue(def)];
    onChange(next);
    setExpanded(previous => new Set([...previous, next.length - 1]));
  };
  const changeEffectType = (index: number, nextType: string) => {
    const def = getEffectTypeDefinition(moduleId, nextType);
    updateEffect(index, def ? createEffectValue(def) : { type: nextType });
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
      const type = textValue(effect.type) || definitions[0]?.type || 'variables';
      const known = definitions.some(def => def.type === type);
      const typeOptions = known ? definitions.map(def => def.type) : [...definitions.map(def => def.type), type];
      const definition = getEffectTypeDefinition(moduleId, type);
      const opened = expanded.has(index);
      return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
        <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggle(index)} onKeyDown={event => toggleByKeyboard(event, () => toggle(index))} aria-expanded={opened} aria-controls={`std-effect-body-${index}`}>
          <span className="prop-level-summary"><span className="prop-level-badge"><DisclosureChevron open={opened} className="prop-level-arrow" /> #{index + 1}</span>{effectTypeLabel(type, definition, moduleId)}</span>
          <span className="prop-level-rate">{effectSummary(effect, definition)}</span>
          <span className="prop-action-controls" onClick={stopEvent} onKeyDown={stopEvent}>
            <button type="button" onClick={() => moveEffect(index, -1)} disabled={index === 0} aria-label={t('core.field.move_up')}>↑</button>
            <button type="button" onClick={() => moveEffect(index, 1)} disabled={index === effects.length - 1} aria-label={t('core.field.move_down')}>↓</button>
            <button type="button" className="prop-action-del" onClick={() => removeEffect(index)} aria-label={t('core.field.delete')}>×</button>
          </span>
        </div>
        {opened && <div className="prop-level-body" id={`std-effect-body-${index}`}>
          <StandardPropRow label="type" path={joinPath(path, index, 'type')} scope={scope}><EffectTypeSelect value={type} options={typeOptions} moduleId={moduleId} definitions={definitions} onChange={nextType => changeEffectType(index, nextType)} /></StandardPropRow>
          <EffectPayloadEditor effect={effect} definition={definition} path={joinPath(path, index)} scope={scope} onChange={nextEffect => updateEffect(index, nextEffect)} />
        </div>}
      </div>;
    })}
    <div className="prop-cost-actions">{definitions.map(def => <button key={def.type} type="button" className="prop-add" onClick={() => addEffect(def)}>+ {effectTypeLabel(def.type, def, moduleId)}</button>)}</div>
  </div>;
}

function EffectPayloadEditor({ effect, definition, onChange, path, scope }: {
  effect: Record<string, unknown>;
  definition: EffectTypeDefinition | undefined;
  onChange: (effect: Record<string, unknown>) => void;
  path?: string;
  scope: StandardEditorScope;
}) {
  const setPayload = (key: string, value: unknown) => onChange(cleanObject({ ...effect, [key]: value }));
  // Unknown type (no registered definition): fall back to a key/value editor so
  // data is never lost, but this should be rare with a complete registry.
  if (!definition) return <GenericKeyValueEditor value={effect} reservedKeys={['type']} onChange={next => onChange({ type: textValue(effect.type), ...next })} />;
  return <>{definition.fields.map(field => (
    <StandardPropRow key={field.key} label={field.key} path={joinPath(path, field.key)} scope={scope} wide={isWidePayloadField(field.type)}>
      <EffectPayloadFieldEditor field={field} value={effect[field.key]} scope={scope} moduleId={scope.moduleId} onChange={next => setPayload(field.key, next)} />
    </StandardPropRow>
  ))}</>;
}

function isWidePayloadField(type: EffectPayloadField['type']): boolean {
  return type !== 'text' && type !== 'number' && type !== 'boolean' && type !== 'enum';
}

function EffectPayloadFieldEditor({ field, value, onChange, scope, moduleId }: {
  field: EffectPayloadField;
  value: unknown;
  onChange: (value: unknown) => void;
  scope: StandardEditorScope;
  moduleId?: string;
}) {
  if (field.type === 'variablesMap') return <VariablesMapEditor value={value} onChange={onChange} />;
  if (field.type === 'map') return <EffectMapField value={value} onChange={onChange} />;
  if (field.type === 'stringList') return <StringListEditor items={asStringList(value)} onChange={onChange} />;
  if (field.type === 'numberList') return <NumberListEditor items={asList(value).map(item => Number(item) || 0)} onChange={onChange} />;
  if (field.type === 'actions') return <StandardActionsField value={value} onChange={onChange} mode={field.actionMode ?? 'name'} {...scope} />;
  if (field.type === 'boolean') return <input type="checkbox" checked={value === true} onChange={event => onChange(event.target.checked)} />;
  if (field.type === 'number') return <input type="number" value={value == null ? '' : textValue(value)} onChange={event => onChange(event.target.value === '' ? undefined : Number(event.target.value))} />;
  if (field.type === 'enum' && field.options?.length) {
    const current = textValue(value);
    const merged = current && !field.options.includes(current) ? [...field.options, current] : field.options;
    return <select value={current} onChange={event => onChange(event.target.value)}>
      {merged.map(option => <option key={option} value={option}>{optionLabel(field.optionLabelPrefix || field.key, option, { moduleId, namespace: moduleId, fallback: option })}</option>)}
    </select>;
  }
  return <input type="text" value={textValue(value)} onChange={event => onChange(event.target.value)} />;
}

function EffectMapField({ value, onChange }: { value: unknown; onChange: (value: Record<string, unknown>) => void }) {
  const entries = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: entry }));
  return <KvTable entries={entries} onChange={nextEntries => {
    const next: Record<string, unknown> = {};
    nextEntries.forEach(entry => { if (String(entry.key).trim()) next[String(entry.key).trim()] = entry.value; });
    onChange(next);
  }} />;
}

function StandardPropRow({ label, path, scope, children, wide }: { label: string; path?: string; scope: StandardEditorScope; children: React.ReactNode; wide?: boolean }) {
  const rowPath = path ?? label;
  const changedPaths = useChangedPaths();
  const changed = isChangedFieldPath(rowPath, changedPaths);
  return <PropRow label={label} path={rowPath} moduleId={scope.moduleId} namespace={scope.namespace ?? scope.moduleId} editorFields={scope.editorFields} wide={wide} changed={changed}>{children}</PropRow>;
}

function effectTypeLabel(type: string, definition: EffectTypeDefinition | undefined, moduleId?: string): string {
  return optionLabel('effect', type, { moduleId, namespace: moduleId, fallback: definition?.label ?? type });
}

function EffectTypeSelect({ value, options, onChange, moduleId, definitions }: { value: string; options: string[]; onChange: (value: string) => void; moduleId?: string; definitions: EffectTypeDefinition[] }) {
  const current = textValue(value);
  const merged = current && !options.includes(current) ? [...options, current] : options;
  const labelFor = (option: string) => effectTypeLabel(option, definitions.find(def => def.type === option), moduleId);
  return <select value={current} onChange={event => onChange(event.target.value)}>
    {merged.map(option => <option key={option} value={option}>{labelFor(option)}</option>)}
  </select>;
}

function GenericKeyValueEditor({ value, reservedKeys, onChange }: { value: unknown; reservedKeys?: string[]; onChange: (value: Record<string, unknown>) => void }) {
  const reserved = new Set(reservedKeys ?? []);
  const entries = Object.entries(asRecord(value)).filter(([key]) => !reserved.has(key)).map(([key, entry]) => ({ key, value: entry }));
  const commit = (nextEntries: Array<{ key: string; value: unknown }>) => {
    const next: Record<string, unknown> = {};
    nextEntries.forEach(entry => { if (entry.key.trim()) next[entry.key.trim()] = entry.value; });
    onChange(next);
  };
  return <KvTable
    entries={entries}
    parseValue={parseLooseScalar}
    createEntry={currentEntries => ({ key: nextUniqueKey(currentEntries.map(entry => entry.key), 'key'), value: '' })}
    onChange={commit}
  />;
}

function effectSummary(effect: Record<string, unknown>, definition: EffectTypeDefinition | undefined): string {
  const zh = getLocale().startsWith('zh');
  const field = definition?.fields[0];
  if (field) {
    const payload = effect[field.key];
    if (field.type === 'variablesMap' || field.type === 'map') {
      const count = Object.keys(asRecord(payload)).length;
      return zh ? `${count} 项` : `${count} entries`;
    }
    if (field.type === 'stringList' || field.type === 'numberList' || field.type === 'actions') {
      const count = asList(payload).length;
      return zh ? `${count} 项` : `${count} entries`;
    }
    return textValue(payload);
  }
  const count = Math.max(0, Object.keys(effect).length - 1);
  return zh ? `${count} 个字段` : `${count} fields`;
}

function joinPath(...parts: Array<string | number | undefined>): string | undefined {
  const filtered = parts.filter(part => part !== undefined && part !== '').map(String);
  return filtered.length ? filtered.join('.') : undefined;
}

function cleanObject<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '')) as T;
}

function stopEvent(event: React.SyntheticEvent) {
  event.stopPropagation();
}

function toggleByKeyboard(event: React.KeyboardEvent, action: () => void) {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  action();
}

function parseLooseScalar(value: string): unknown {
  const trimmed = value.trim();
  if (trimmed === '') return '';
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
  return value;
}

function nextUniqueKey(keys: string[], prefix: string): string {
  const normalizedPrefix = prefix.trim() || 'key';
  let index = keys.length + 1;
  let key = `${normalizedPrefix}_${index}`;
  while (keys.includes(key)) key = `${normalizedPrefix}_${++index}`;
  return key;
}
