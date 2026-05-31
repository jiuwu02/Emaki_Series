import React, { createContext, useContext, useEffect, useState } from 'react';
import type { ActionTypesResult } from '../api';
import { ActionsEditor, parseActionList, serializeActionList } from './ActionsEditor';
import { VariablesMapEditor } from './VariablesMapEditor';
import { PropRow } from './PropRow';
import { DisclosureChevron } from './SectionHead';
import { asList, asRecord } from '../lib/itemUtils';
import { fieldLabel, optionLabel, textValue } from '../lib';
import { t, getLocale } from '../i18n';
import {
  CORE_EFFECT_TYPES,
  coreEffectTypeLabel,
  createCoreEffect,
  isCoreEffectType,
  type CoreEffectType
} from '../itemFieldRegistry';
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
 * Unified CoreLib effects-list editor. Splits each entry by `type` into
 * variables / name_action / lore_action structured editors, matching the
 * server-side assembly model. Unknown types fall back to a key/value editor.
 */
export function StandardEffectsEditor({ value, onChange, path, moduleId, namespace, editorFields, actionTypes }: StandardEditorScope & {
  value: unknown;
  onChange: (effects: unknown[]) => void;
  path?: string;
}) {
  const contextTypes = useActionTypes();
  const resolvedTypes = actionTypes ?? contextTypes;
  const scope: StandardEditorScope = { moduleId, namespace: namespace ?? moduleId, editorFields, actionTypes: resolvedTypes };
  const effects = asList(value).map(effect => asRecord(effect));
  const [expanded, setExpanded] = useState<Set<number>>(() => new Set(effects.map((_, index) => index)));
  const updateEffect = (index: number, nextEffect: Record<string, unknown>) => onChange(effects.map((effect, itemIndex) => itemIndex === index ? cleanObject(nextEffect) : effect));
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
        <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggle(index)} onKeyDown={event => toggleByKeyboard(event, () => toggle(index))} aria-expanded={opened} aria-controls={`std-effect-body-${index}`}>
          <span className="prop-level-summary"><span className="prop-level-badge"><DisclosureChevron open={opened} className="prop-level-arrow" /> #{index + 1}</span>{coreEffectTypeLabel(type)}</span>
          <span className="prop-level-rate">{effectSummary(effect)}</span>
          <span className="prop-action-controls" onClick={stopEvent} onKeyDown={stopEvent}>
            <button type="button" onClick={() => moveEffect(index, -1)} disabled={index === 0} aria-label={t('core.field.move_up')}>↑</button>
            <button type="button" onClick={() => moveEffect(index, 1)} disabled={index === effects.length - 1} aria-label={t('core.field.move_down')}>↓</button>
            <button type="button" className="prop-action-del" onClick={() => removeEffect(index)} aria-label={t('core.field.delete')}>×</button>
          </span>
        </div>
        {opened && <div className="prop-level-body" id={`std-effect-body-${index}`}>
          <StandardPropRow label="type" path={joinPath(path, index, 'type')} scope={scope}><EffectTypeSelect value={type} options={typeOptions} moduleId={moduleId} onChange={nextType => updateEffect(index, createCoreEffect(nextType as CoreEffectType))} /></StandardPropRow>
          <EffectPayloadEditor effect={effect} type={coreType} originalType={type} path={joinPath(path, index)} scope={scope} onChange={nextEffect => updateEffect(index, nextEffect)} />
        </div>}
      </div>;
    })}
    <div className="prop-cost-actions">{CORE_EFFECT_TYPES.map(type => <button key={type} type="button" className="prop-add" onClick={() => addEffect(type)}>+ {coreEffectTypeLabel(type)}</button>)}</div>
  </div>;
}

function EffectPayloadEditor({ effect, type, originalType, onChange, path, scope }: {
  effect: Record<string, unknown>;
  type: CoreEffectType;
  originalType: string;
  onChange: (effect: Record<string, unknown>) => void;
  path?: string;
  scope: StandardEditorScope;
}) {
  const setPayload = (key: string, value: unknown) => onChange(cleanObject({ ...effect, [key]: value }));
  if (!isCoreEffectType(originalType)) return <GenericKeyValueEditor value={effect} reservedKeys={['type']} onChange={next => onChange({ type: originalType, ...next })} />;
  if (type === 'variables') return <StandardPropRow label="variables" path={joinPath(path, 'variables')} scope={scope} wide><VariablesMapEditor value={effect.variables} onChange={value => setPayload('variables', value)} /></StandardPropRow>;
  if (type === 'name_action') return <StandardPropRow label="name_actions" path={joinPath(path, 'name_actions')} scope={scope} wide><StandardActionsField value={effect.name_actions} onChange={value => setPayload('name_actions', value)} mode="name" {...scope} /></StandardPropRow>;
  if (type === 'lore_action') return <StandardPropRow label="lore_actions" path={joinPath(path, 'lore_actions')} scope={scope} wide><StandardActionsField value={effect.lore_actions} onChange={value => setPayload('lore_actions', value)} mode="lore" {...scope} /></StandardPropRow>;
  return <GenericKeyValueEditor value={effect} reservedKeys={['type']} onChange={next => onChange({ type, ...next })} />;
}

function StandardPropRow({ label, path, scope, children, wide }: { label: string; path?: string; scope: StandardEditorScope; children: React.ReactNode; wide?: boolean }) {
  return <PropRow label={label} path={path ?? label} moduleId={scope.moduleId} namespace={scope.namespace ?? scope.moduleId} editorFields={scope.editorFields} wide={wide}>{children}</PropRow>;
}

function EffectTypeSelect({ value, options, onChange, moduleId }: { value: string; options: string[]; onChange: (value: string) => void; moduleId?: string }) {
  const current = textValue(value);
  const merged = current && !options.includes(current) ? [...options, current] : options;
  return <select value={current} onChange={event => onChange(event.target.value)}>
    {merged.map(option => <option key={option} value={option}>{optionLabel('effect', option, { moduleId, namespace: moduleId, fallback: coreEffectTypeLabel(option) })}</option>)}
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
  const update = (index: number, field: 'key' | 'value', raw: string) => {
    const next = entries.map((entry, itemIndex) => itemIndex === index ? (field === 'key' ? { ...entry, key: raw } : { ...entry, value: parseLooseScalar(raw) }) : entry);
    commit(next);
  };
  const remove = (index: number) => commit(entries.filter((_, itemIndex) => itemIndex !== index));
  const add = () => commit([...entries, { key: nextUniqueKey(entries.map(entry => entry.key), 'key'), value: '' }]);
  return <div className="prop-kv" role="list">
    {entries.map((entry, index) => <div className="prop-kv-row" key={index} role="listitem">
      <input type="text" value={entry.key} onChange={event => update(index, 'key', event.target.value)} placeholder={t('core.kv.key')} aria-label={`${t('core.kv.key')} ${index + 1}`} />
      <input type="text" value={entry.value == null ? '' : String(entry.value)} onChange={event => update(index, 'value', event.target.value)} placeholder={t('core.kv.value')} aria-label={`${t('core.kv.value')} ${index + 1}`} />
      <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={t('core.kv.delete', { index: index + 1 })}>×</button>
    </div>)}
    <button type="button" className="prop-add" onClick={add}>+ {t('core.kv.add')}</button>
  </div>;
}

function effectSummary(effect: Record<string, unknown>): string {
  const type = textValue(effect.type);
  const zh = getLocale().startsWith('zh');
  if (type === 'variables') return zh ? `${Object.keys(asRecord(effect.variables)).length} 个变量` : `${Object.keys(asRecord(effect.variables)).length} variables`;
  if (type === 'name_action') return zh ? `${asList(effect.name_actions).length} 个名称动作` : `${asList(effect.name_actions).length} name actions`;
  if (type === 'lore_action') return zh ? `${asList(effect.lore_actions).length} 个 Lore 动作` : `${asList(effect.lore_actions).length} lore actions`;
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
