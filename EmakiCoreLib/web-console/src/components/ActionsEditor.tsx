import { fieldLabel, optionLabel, type FieldLabelOptions, type OptionLabelOptions } from '../lib/fieldI18n';
import { textValue } from '../lib/miniMessage';
import { asList, asStringList } from '../lib/itemUtils';
import type { WebEditorField } from '../types';

/** Structured editor for CoreLib Name/Lore action lists. */
export type ActionEntry = { type: string; params: Record<string, unknown> };

export type ActionsEditorProps = {
  actions: ActionEntry[];
  onChange: (actions: ActionEntry[]) => void;
  actionTypes: string[];
  mode: 'name' | 'lore';
  namespace?: string;
  moduleId?: string;
  editorFields?: Record<string, WebEditorField>;
  optionPrefix?: string;
};

export function ActionsEditor({
  actions,
  onChange,
  actionTypes,
  mode,
  namespace = 'core',
  moduleId = 'EmakiCoreLib',
  editorFields,
  optionPrefix = 'actionType'
}: ActionsEditorProps) {
  const labelOptions = { namespace, moduleId, editorFields };
  const optionOptions = { namespace, moduleId };
  const update = (i: number, patch: Partial<ActionEntry>) => {
    const next = [...actions];
    next[i] = { ...next[i], ...patch };
    onChange(next);
  };
  const updateParam = (i: number, key: string, value: unknown) => {
    const next = [...actions];
    next[i] = { ...next[i], params: { ...next[i].params, [key]: value } };
    onChange(next);
  };
  const remove = (i: number) => onChange(actions.filter((_, idx) => idx !== i));
  const add = () => onChange([...actions, { type: actionTypes[0] ?? '', params: {} }]);
  const moveUp = (i: number) => { if (i <= 0) return; const next = [...actions]; [next[i - 1], next[i]] = [next[i], next[i - 1]]; onChange(next); };
  const moveDown = (i: number) => { if (i >= actions.length - 1) return; const next = [...actions]; [next[i], next[i + 1]] = [next[i + 1], next[i]]; onChange(next); };

  return (
    <div className="prop-actions">
      {actions.map((action, i) => {
        const normalizedType = action.type || '';
        const mergedTypes = normalizedType && !actionTypes.includes(normalizedType) ? [...actionTypes, normalizedType] : actionTypes;
        return (
          <div className="prop-action-item" key={i}>
            <div className="prop-action-head">
              <span className="prop-action-grip">≡</span>
              <ParamSelect
                paramKey="action"
                value={normalizedType}
                options={mergedTypes}
                labelOptions={labelOptions}
                optionPrefix={optionPrefix}
                optionOptions={optionOptions}
                onChange={value => update(i, { type: value, params: {} })}
              />
              <span className="prop-action-controls">
                <button type="button" onClick={() => moveUp(i)} disabled={i === 0} aria-label={fieldLabel('move_up', { ...labelOptions, fallback: '上移' })}>↑</button>
                <button type="button" onClick={() => moveDown(i)} disabled={i === actions.length - 1} aria-label={fieldLabel('move_down', { ...labelOptions, fallback: '下移' })}>↓</button>
                <button type="button" className="prop-action-del" onClick={() => remove(i)} aria-label={fieldLabel('delete', { ...labelOptions, fallback: '删除' })}>×</button>
              </span>
            </div>
            <div className="prop-action-params">
              {mode === 'name' && renderNameParams(action, i, updateParam, labelOptions)}
              {mode === 'lore' && renderLoreParams(action, i, updateParam, labelOptions)}
            </div>
          </div>
        );
      })}
      <button type="button" className="prop-add" onClick={add}>+ {fieldLabel(`${mode}_actions.add`, { ...labelOptions, fallback: '添加动作' })}</button>
    </div>
  );
}

function renderNameParams(
  action: ActionEntry,
  index: number,
  updateParam: (index: number, key: string, value: unknown) => void,
  labelOptions: FieldLabelOptions
) {
  if (action.type === 'regex_replace') {
    return <>
      <ParamInput paramKey="regex_pattern" value={textValue(action.params.regex_pattern)} labelOptions={labelOptions} onChange={value => updateParam(index, 'regex_pattern', value)} />
      <ParamInput paramKey="replacement" value={textValue(action.params.replacement)} labelOptions={labelOptions} onChange={value => updateParam(index, 'replacement', value)} />
    </>;
  }
  return <ParamInput paramKey="value" value={textValue(action.params.value)} labelOptions={labelOptions} onChange={value => updateParam(index, 'value', value)} />;
}

function renderLoreParams(
  action: ActionEntry,
  index: number,
  updateParam: (index: number, key: string, value: unknown) => void,
  labelOptions: FieldLabelOptions
) {
  if (action.type === 'regex_replace') {
    return <>
      <ParamInput paramKey="regex_pattern" value={textValue(action.params.regex_pattern)} labelOptions={labelOptions} onChange={value => updateParam(index, 'regex_pattern', value)} />
      <ParamInput paramKey="replacement" value={textValue(action.params.replacement)} labelOptions={labelOptions} onChange={value => updateParam(index, 'replacement', value)} />
    </>;
  }
  const search = requiresSearchPattern(action.type);
  return <>
    {action.type !== 'delete_line' && <ParamTextarea paramKey="content" rows={2} value={asStringList(action.params.content).join('\n')} labelOptions={labelOptions} onChange={value => updateParam(index, 'content', splitLines(value))} />}
    {search && <>
      <ParamInput paramKey="target_pattern" value={textValue(action.params.target_pattern)} labelOptions={labelOptions} onChange={value => updateParam(index, 'target_pattern', value)} />
      <ParamInput paramKey="anchor" value={textValue(action.params.anchor)} labelOptions={labelOptions} onChange={value => updateParam(index, 'anchor', value)} />
    </>}
  </>;
}

function ParamSelect({ paramKey, value, options, onChange, labelOptions, optionPrefix, optionOptions }: {
  paramKey: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
  labelOptions: FieldLabelOptions;
  optionPrefix: string;
  optionOptions: OptionLabelOptions;
}) {
  const label = fieldLabel(paramKey, { ...labelOptions, fallback: paramKey });
  return <label className="prop-param-field prop-param-field--type"><span>{label}</span><select value={value} onChange={event => onChange(event.target.value)} aria-label={label}>
    {options.map(option => <option key={option} value={option}>{optionLabel(optionPrefix, option, { ...optionOptions, fallback: option })}</option>)}
    {!options.length && <option value="">{fieldLabel('none', { ...labelOptions, fallback: '未选择' })}</option>}
  </select></label>;
}

function ParamInput({ paramKey, value, onChange, labelOptions }: { paramKey: string; value: string; onChange: (value: string) => void; labelOptions: FieldLabelOptions }) {
  const label = fieldLabel(paramKey, { ...labelOptions, fallback: paramKey });
  return <label className="prop-param-field"><span>{label}</span><input type="text" value={value} onChange={event => onChange(event.target.value)} aria-label={label} /></label>;
}

function ParamTextarea({ paramKey, value, onChange, labelOptions, rows = 2 }: { paramKey: string; value: string; onChange: (value: string) => void; labelOptions: FieldLabelOptions; rows?: number }) {
  const label = fieldLabel(paramKey, { ...labelOptions, fallback: paramKey });
  return <label className="prop-param-field prop-param-field--wide"><span>{label}</span><textarea rows={rows} value={value} onChange={event => onChange(event.target.value)} aria-label={label} /></label>;
}

function requiresSearchPattern(type: string): boolean {
  const normalized = String(type || '').toLowerCase();
  return normalized.includes('insert') || normalized.includes('search') || normalized === 'replace_line' || normalized === 'delete_line';
}

function splitLines(value: string): string[] {
  return value.split('\n');
}

/** Parse raw action list from any CoreLib name_actions/lore_actions value. */
export function parseActionList(value: unknown): ActionEntry[] {
  return asList(value).map(raw => {
    const row = (raw && typeof raw === 'object' && !Array.isArray(raw)) ? raw as Record<string, unknown> : {};
    const type = textValue(row.action ?? row.type);
    const params: Record<string, unknown> = {};
    Object.entries(row).forEach(([key, entry]) => {
      if (key !== 'action' && key !== 'type') params[key] = entry;
    });
    return { type, params };
  });
}

/** Serialize action entries back to CoreLib standard format. */
export function serializeActionList(actions: ActionEntry[]): unknown[] {
  return actions.map(action => cleanActionObject({ action: action.type, ...action.params }));
}

/** Parse raw name_actions from data map. */
export function parseNameActions(data: Record<string, unknown>): ActionEntry[] {
  return parseActionList(data.name_actions);
}

/** Parse raw lore_actions from data map. */
export function parseLoreActions(data: Record<string, unknown>): ActionEntry[] {
  return parseActionList(data.lore_actions);
}

/** Backward-compatible alias for serializeActionList. */
export function serializeActions(actions: ActionEntry[]): unknown[] {
  return serializeActionList(actions);
}

function cleanActionObject(value: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(Array.isArray(entry) && entry.length === 0)));
}
