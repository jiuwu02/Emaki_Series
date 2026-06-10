import { fieldLabel, optionLabel, type FieldLabelOptions, type OptionLabelOptions } from '../lib/fieldI18n';
import { textValue } from '../lib/miniMessage';
import { asList } from '../lib/itemUtils';
import type { WebEditorField } from '../types';
import { StringListEditor } from './StringListEditor';

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
  const moveUp = (i: number) => { if (i <= 0) return; const next = [...actions];[next[i - 1], next[i]] = [next[i], next[i - 1]]; onChange(next); };
  const moveDown = (i: number) => { if (i >= actions.length - 1) return; const next = [...actions];[next[i], next[i + 1]] = [next[i + 1], next[i]]; onChange(next); };

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
      <button type="button" className="prop-add" onClick={add}>+ {fieldLabel(`${mode}_actions.add`, { ...labelOptions, fallback: mode === 'name' ? '添加名称动作' : '添加 Lore 动作' })}</button>
    </div>
  );
}

function renderNameParams(
  action: ActionEntry,
  index: number,
  updateParam: (index: number, key: string, value: unknown) => void,
  labelOptions: FieldLabelOptions
) {
  const updateTextParam = (key: string) => (value: string) => updateParam(index, key, updateTextConfig(action.params[key], value));
  if (action.type === 'regex_replace') {
    return <>
      <ParamInput paramKey="regex_pattern" value={actionTextValue(action.params.regex_pattern)} labelOptions={labelOptions} onChange={updateTextParam('regex_pattern')} />
      <ParamInput paramKey="replacement" value={actionTextValue(action.params.replacement)} labelOptions={labelOptions} onChange={updateTextParam('replacement')} />
    </>;
  }
  if (action.type === 'replace_text' || action.type === 'replace_text_all') {
    return <>
      <ParamInput paramKey="anchor" value={actionTextValue(action.params.anchor)} labelOptions={labelOptions} onChange={updateTextParam('anchor')} />
      {action.type === 'replace_text' && <ParamInput paramKey="index" value={actionTextValue(action.params.index)} labelOptions={labelOptions} onChange={updateTextParam('index')} />}
      <ParamInput paramKey="replacement" value={actionTextValue(action.params.replacement)} labelOptions={labelOptions} onChange={updateTextParam('replacement')} />
    </>;
  }
  return <ParamInput paramKey="value" value={actionTextValue(action.params.value)} labelOptions={labelOptions} onChange={updateTextParam('value')} />;
}

function renderLoreParams(
  action: ActionEntry,
  index: number,
  updateParam: (index: number, key: string, value: unknown) => void,
  labelOptions: FieldLabelOptions
) {
  const updateTextParam = (key: string) => (value: string) => updateParam(index, key, updateTextConfig(action.params[key], value));
  if (action.type === 'regex_replace') {
    return <>
      <ParamInput paramKey="regex_pattern" value={actionTextValue(action.params.regex_pattern)} labelOptions={labelOptions} onChange={updateTextParam('regex_pattern')} />
      <ParamInput paramKey="replacement" value={actionTextValue(action.params.replacement)} labelOptions={labelOptions} onChange={updateTextParam('replacement')} />
    </>;
  }
  const search = requiresSearchPattern(action.type);
  return <>
    {action.type !== 'delete_line' && <ParamLineList paramKey="content" value={actionTextLines(action.params.content)} labelOptions={labelOptions} onChange={lines => updateParam(index, 'content', updateTextLinesConfig(action.params.content, lines))} />}
    {search && <>
      <ParamInput paramKey="target_pattern" value={actionTextValue(action.params.target_pattern)} labelOptions={labelOptions} onChange={updateTextParam('target_pattern')} />
      <ParamInput paramKey="anchor" value={actionTextValue(action.params.anchor)} labelOptions={labelOptions} onChange={updateTextParam('anchor')} />
    </>}
  </>;
}

const TEXT_VALUE_KEYS = ['value', 'text', 'template', 'expression', 'formula'];
const TEXT_LINE_KEYS = ['content', 'lines', 'values', 'candidates'];

function actionTextLines(value: unknown): string[] {
  if (value == null || value === '') return [];
  if (Array.isArray(value)) return value.flatMap(entry => actionTextLines(entry));
  if (isRecord(value)) {
    const lineKey = firstExistingKey(value, TEXT_LINE_KEYS);
    if (lineKey) return actionTextLines(value[lineKey]);
  }
  const text = actionTextValue(value);
  return text.includes('\n') ? text.split('\n') : [text];
}

function actionTextValue(value: unknown): string {
  const scalar = textValue(value);
  if (scalar || value == null) return scalar;
  if (Array.isArray(value)) return actionTextLines(value).join('\n');
  if (isRecord(value)) {
    const valueKey = firstExistingKey(value, TEXT_VALUE_KEYS);
    if (valueKey) return actionTextValue(value[valueKey]);
    const lineKey = firstExistingKey(value, TEXT_LINE_KEYS);
    if (lineKey) return actionTextLines(value[lineKey]).join('\n');
    return '';
  }
  return String(value);
}

function updateTextConfig(previous: unknown, text: string, multiline = false): unknown {
  const lines = splitLines(text);
  if (Array.isArray(previous)) {
    return multiline ? lines.map((line, index) => updateTextConfig(previous[index], line)) : text;
  }
  if (isRecord(previous)) {
    const valueKey = firstExistingKey(previous, TEXT_VALUE_KEYS);
    if (valueKey) return { ...previous, [valueKey]: text };
    const lineKey = firstExistingKey(previous, TEXT_LINE_KEYS);
    if (lineKey) return { ...previous, [lineKey]: lines };
    if (multiline) return { ...previous, lines };
  }
  if (multiline) return typeof previous === 'string' && lines.length <= 1 ? text : lines;
  return text;
}

function updateTextLinesConfig(previous: unknown, lines: string[]): unknown {
  const text = lines.join('\n');
  if (Array.isArray(previous)) {
    return lines.map((line, index) => updateTextConfig(previous[index], line));
  }
  if (isRecord(previous)) {
    const valueKey = firstExistingKey(previous, TEXT_VALUE_KEYS);
    if (valueKey) return { ...previous, [valueKey]: text };
    const lineKey = firstExistingKey(previous, TEXT_LINE_KEYS);
    if (lineKey) return { ...previous, [lineKey]: lines };
    return { ...previous, lines };
  }
  return typeof previous === 'string' && lines.length <= 1 ? text : lines;
}

function firstExistingKey(record: Record<string, unknown>, keys: string[]): string | undefined {
  return keys.find(key => Object.prototype.hasOwnProperty.call(record, key));
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
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

function ParamLineList({ paramKey, value, onChange, labelOptions }: { paramKey: string; value: string[]; onChange: (value: string[]) => void; labelOptions: FieldLabelOptions }) {
  const label = fieldLabel(paramKey, { ...labelOptions, fallback: paramKey });
  return <div className="prop-param-field"><span>{label}</span><StringListEditor items={value} onChange={onChange} ariaLabel={label} /></div>;
}

function requiresSearchPattern(type: string): boolean {
  const normalized = String(type || '').toLowerCase();
  return normalized.includes('insert') || normalized.includes('search') || normalized === 'replace_line' || normalized === 'replace_text' || normalized === 'replace_text_all' || normalized === 'delete_line';
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

function cleanActionObject(value: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(Array.isArray(entry) && entry.length === 0)));
}
