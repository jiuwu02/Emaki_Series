import { useState, type KeyboardEvent, type SyntheticEvent } from 'react';
import { t, getLocale } from '../i18n';
import { DisclosureChevron } from './SectionHead';

const NUMERIC_TYPES = ['constant', 'range', 'uniform', 'gaussian', 'skew_normal', 'triangle', 'expression'] as const;
const TEXT_TYPES = ['text', 'random_text', 'boolean'] as const;
const TYPE_OPTIONS = ['number', 'formula', ...NUMERIC_TYPES, ...TEXT_TYPES, 'custom'] as const;

type VariableType = typeof TYPE_OPTIONS[number];
type VariableEntry = { key: string; value: unknown };

type AnyMap = Record<string, unknown>;

export function VariablesMapEditor({ value, onChange }: { value: unknown; onChange: (value: Record<string, unknown>) => void }) {
  const entries = Object.entries(isRecord(value) ? value : {}).map(([key, entry]) => ({ key, value: entry }));
  const [expanded, setExpanded] = useState<Set<number>>(() => new Set(entries.map((_, index) => index)));

  const updateEntries = (nextEntries: VariableEntry[]) => {
    const next: AnyMap = {};
    nextEntries.forEach(entry => { if (entry.key.trim()) next[entry.key.trim()] = entry.value; });
    onChange(next);
  };
  const update = (index: number, patch: Partial<VariableEntry>) => updateEntries(entries.map((entry, itemIndex) => itemIndex === index ? { ...entry, ...patch } : entry));
  const remove = (index: number) => updateEntries(entries.filter((_, itemIndex) => itemIndex !== index));
  const add = () => {
    const next = [...entries, { key: nextVariableKey(entries.map(entry => entry.key)), value: 0 }];
    updateEntries(next);
    setExpanded(current => new Set([...current, next.length - 1]));
  };
  const toggle = (index: number) => setExpanded(current => {
    const next = new Set(current);
    next.has(index) ? next.delete(index) : next.add(index);
    return next;
  });

  return <div className="prop-levels variables-map-editor" role="list" aria-label={copy('变量列表', 'Variables')}>
    {entries.map((entry, index) => {
      const type = detectVariableType(entry.value);
      const opened = expanded.has(index);
      return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
        <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggle(index)} onKeyDown={event => toggleByKeyboard(event, () => toggle(index))} aria-expanded={opened} aria-controls={`variable-body-${index}`}>
          <span className="prop-level-summary">
            <span className="prop-level-badge"><DisclosureChevron open={opened} className="prop-level-arrow" /> {entry.key || copy('未命名', 'Unnamed')}</span>
          </span>
          <span className="prop-level-rate">{variableTypeLabel(type)}</span>
          <span className="prop-action-controls" onClick={stopEvent} onKeyDown={stopEvent}>
            <button type="button" className="prop-action-del" onClick={() => remove(index)} aria-label={t('core.kv.delete', { index: index + 1 })}>×</button>
          </span>
        </div>
        {opened && <div className="prop-level-body" id={`variable-body-${index}`}>
          <label className="prop-param-field"><span>{copy('变量名', 'Variable')}</span><input value={entry.key} onChange={event => update(index, { key: event.target.value })} /></label>
          <label className="prop-param-field"><span>{copy('值类型', 'Value type')}</span><select value={type} onChange={event => update(index, { value: convertVariableValue(entry.value, event.target.value as VariableType) })}>{TYPE_OPTIONS.map(option => <option key={option} value={option}>{variableTypeLabel(option)}</option>)}</select></label>
          <VariableValueEditor value={entry.value} type={type} onChange={next => update(index, { value: next })} />
        </div>}
      </div>;
    })}
    <button type="button" className="prop-add" onClick={add}>+ {copy('添加变量', 'Add variable')}</button>
  </div>;
}

function toggleByKeyboard(event: KeyboardEvent, action: () => void) {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  action();
}

function stopEvent(event: SyntheticEvent) {
  event.stopPropagation();
}

function VariableValueEditor({ value, type, onChange }: { value: unknown; type: VariableType; onChange: (value: unknown) => void }) {
  if (type === 'number') return <label className="prop-param-field"><span>{copy('数字值', 'Number')}</span><input type="number" value={typeof value === 'number' ? value : Number(value) || 0} onChange={event => onChange(event.target.value === '' ? 0 : Number(event.target.value))} /></label>;
  if (type === 'formula') return <label className="prop-param-field prop-param-field--wide"><span>{copy('公式', 'Formula')}</span><input value={String(value ?? '')} onChange={event => onChange(event.target.value)} placeholder="{level} * 2 + 5" /></label>;
  if (type === 'constant') return <NumericObjectEditor value={asTypedRecord(value, 'constant')} fields={['value']} onChange={onChange} />;
  if (type === 'range' || type === 'uniform') return <NumericObjectEditor value={asTypedRecord(value, type)} fields={['min', 'max']} onChange={onChange} />;
  if (type === 'gaussian') return <NumericObjectEditor value={asTypedRecord(value, type)} fields={['mean', 'std_dev', 'min', 'max', 'max_attempts']} onChange={onChange} />;
  if (type === 'skew_normal') return <NumericObjectEditor value={asTypedRecord(value, type)} fields={['mean', 'std_dev', 'skewness', 'min', 'max', 'max_attempts']} onChange={onChange} />;
  if (type === 'triangle') return <NumericObjectEditor value={asTypedRecord(value, type)} fields={['mode', 'deviation']} onChange={onChange} />;
  if (type === 'expression') return <ExpressionObjectEditor value={asTypedRecord(value, 'expression')} onChange={onChange} />;
  if (type === 'text') return <TextObjectEditor value={asTypedRecord(value, 'text')} onChange={onChange} />;
  if (type === 'random_text') return <RandomTextObjectEditor value={asTypedRecord(value, 'random_text')} onChange={onChange} />;
  if (type === 'boolean') return <BooleanObjectEditor value={asTypedRecord(value, 'boolean')} onChange={onChange} />;
  return <JsonObjectField value={value} onChange={onChange} />;
}

function NumericObjectEditor({ value, fields, onChange }: { value: AnyMap; fields: string[]; onChange: (value: unknown) => void }) {
  return <div className="schema-object-editor">
    {fields.map(field => <label className="prop-param-field" key={field}><span>{variableFieldLabel(field)}</span><input type={field === 'max_attempts' ? 'number' : 'text'} value={String(value[field] ?? '')} onChange={event => onChange(cleanObject({ ...value, [field]: parseLoose(event.target.value) }))} /></label>)}
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function ExpressionObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{copy('表达式', 'Expression')}</span><input value={String(value.expression ?? value.formula ?? '')} onChange={event => onChange(cleanObject({ ...value, expression: event.target.value, formula: undefined }))} placeholder="{base} * 1.2" /></label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function TextObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{copy('文本', 'Text')}</span><input value={String(value.value ?? value.text ?? value.template ?? '')} onChange={event => onChange(cleanObject({ ...value, value: event.target.value, text: undefined, template: undefined }))} /></label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function RandomTextObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  const lines = Array.isArray(value.lines ?? value.values) ? (value.lines ?? value.values) as unknown[] : [];
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{copy('候选文本', 'Candidates')}</span><textarea rows={4} value={lines.map(String).join('\n')} onChange={event => onChange(cleanObject({ ...value, lines: event.target.value.split('\n'), values: undefined }))} /></label>
    <label className="prop-param-field"><span>{copy('抽取数量', 'Count')}</span><input type="text" value={String(value.count ?? value.rolls ?? 1)} onChange={event => onChange(cleanObject({ ...value, count: parseLoose(event.target.value), rolls: undefined }))} /></label>
    <label className="inline-switch"><input type="checkbox" checked={value.allow_duplicates === true} onChange={event => onChange(cleanObject({ ...value, allow_duplicates: event.target.checked || undefined }))} /> {copy('允许重复', 'Allow duplicates')}</label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function BooleanObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{copy('布尔表达式', 'Boolean expression')}</span><input value={String(value.expression ?? value.value ?? '')} onChange={event => onChange(cleanObject({ ...value, expression: event.target.value, value: undefined }))} placeholder="{level} >= 10" /></label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function LocalVariablesEditor({ value, onChange }: { value: unknown; onChange: (value: Record<string, unknown> | undefined) => void }) {
  const [open, setOpen] = useState(false);
  return <div className="local-variables-editor">
    <button type="button" className="prop-add" onClick={() => setOpen(current => !current)}>{open ? '−' : '+'} {copy('局部变量', 'Local variables')}</button>
    {open && <VariablesMapEditor value={value} onChange={next => onChange(Object.keys(next).length ? next : undefined)} />}
  </div>;
}

function JsonObjectField({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  const [text, setText] = useState(() => JSON.stringify(value ?? {}, null, 2));
  const [error, setError] = useState('');
  return <div className="variable-json">
    <textarea rows={8} value={text} aria-label={copy('自定义变量配置', 'Custom variable config')} onChange={event => {
      const next = event.target.value;
      setText(next);
      try {
        onChange(JSON.parse(next));
        setError('');
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
      }
    }} />
    {error && <small className="field-error" role="alert">{error}</small>}
  </div>;
}

function detectVariableType(value: unknown): VariableType {
  if (typeof value === 'number') return 'number';
  if (typeof value === 'string') return 'formula';
  if (!isRecord(value)) return 'custom';
  const type = String(value.type ?? '').replace('-', '_').toLowerCase();
  if ((TYPE_OPTIONS as readonly string[]).includes(type)) return type as VariableType;
  if (!type && ('min' in value) && ('max' in value)) return 'uniform';
  if (!type && ('value' in value)) return 'constant';
  return 'custom';
}

function convertVariableValue(previous: unknown, type: VariableType): unknown {
  if (type === 'number') return numericValue(previous, 0);
  if (type === 'formula') return isRecord(previous) ? String(previous.expression ?? previous.formula ?? previous.value ?? '') : String(previous ?? '');
  const record = isRecord(previous) ? previous : {};
  if (type === 'constant') return cleanObject({ type, value: numericValue(record.value ?? previous, 0) });
  if (type === 'range' || type === 'uniform') return cleanObject({ type, min: numericValue(record.min, 0), max: numericValue(record.max, 1) });
  if (type === 'gaussian') return cleanObject({ type, mean: numericValue(record.mean, 0), std_dev: numericValue(record.std_dev, 1), min: record.min, max: record.max });
  if (type === 'skew_normal') return cleanObject({ type, mean: numericValue(record.mean, 0), std_dev: numericValue(record.std_dev, 1), skewness: numericValue(record.skewness, 0), min: record.min, max: record.max });
  if (type === 'triangle') return cleanObject({ type, mode: numericValue(record.mode, 0), deviation: numericValue(record.deviation, 1) });
  if (type === 'expression') return cleanObject({ type, expression: String(record.expression ?? record.formula ?? previous ?? '') });
  if (type === 'text') return cleanObject({ type, value: String(record.value ?? record.text ?? previous ?? '') });
  if (type === 'random_text') return cleanObject({ type, lines: Array.isArray(record.lines ?? record.values) ? record.lines ?? record.values : [''], count: 1 });
  if (type === 'boolean') return cleanObject({ type, expression: String(record.expression ?? record.value ?? '') });
  return previous ?? {};
}

function asTypedRecord(value: unknown, type: string): AnyMap {
  return { ...(isRecord(value) ? value : {}), type };
}

function variableTypeLabel(type: VariableType): string {
  const zh: Record<string, string> = { number: '数字简写', formula: '公式简写', constant: '固定数值', range: '范围随机', uniform: '均匀随机', gaussian: '正态随机', skew_normal: '偏态正态', triangle: '三角分布', expression: '表达式', text: '文本', random_text: '随机文本', boolean: '布尔', custom: '自定义对象' };
  const en: Record<string, string> = { number: 'Number shorthand', formula: 'Formula shorthand', constant: 'Constant', range: 'Range random', uniform: 'Uniform random', gaussian: 'Gaussian', skew_normal: 'Skew normal', triangle: 'Triangle', expression: 'Expression', text: 'Text', random_text: 'Random text', boolean: 'Boolean', custom: 'Custom object' };
  return (getLocale().startsWith('zh') ? zh : en)[type] ?? type;
}

function variableFieldLabel(field: string): string {
  const zh: Record<string, string> = { value: '值', min: '最小值', max: '最大值', mean: '均值', std_dev: '标准差', skewness: '偏度', mode: '众数/中心', deviation: '偏移', max_attempts: '最大尝试次数' };
  const en: Record<string, string> = { value: 'Value', min: 'Min', max: 'Max', mean: 'Mean', std_dev: 'Std dev', skewness: 'Skewness', mode: 'Mode', deviation: 'Deviation', max_attempts: 'Max attempts' };
  return (getLocale().startsWith('zh') ? zh : en)[field] ?? field;
}

function nextVariableKey(keys: string[]): string {
  let index = 1;
  while (keys.includes(`variable_${index}`)) index += 1;
  return `variable_${index}`;
}

function numericValue(value: unknown, fallback: number): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function parseLoose(value: string): unknown {
  if (value === '') return undefined;
  const number = Number(value);
  return Number.isFinite(number) && /^-?\d+(\.\d+)?$/.test(value.trim()) ? number : value;
}

function cleanObject(value: AnyMap): AnyMap {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(Array.isArray(entry) && entry.length === 0)));
}

function isRecord(value: unknown): value is AnyMap {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
}

function copy(zh: string, en: string): string {
  return getLocale().startsWith('zh') ? zh : en;
}
