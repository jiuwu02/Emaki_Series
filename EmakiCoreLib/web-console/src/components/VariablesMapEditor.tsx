import { useState } from 'react';
import { t } from '../i18n';
import { DisclosureChevron } from './SectionHead';

const NUMERIC_TYPES = ['constant', 'range', 'uniform', 'gaussian', 'skew_normal', 'triangle', 'expression'] as const;
const TEXT_TYPES = ['text', 'random_text', 'random_char', 'weighted_random_char', 'conditional_char', 'boolean'] as const;
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

  return <div className="prop-levels variables-map-editor" role="list" aria-label={t('core.variables.aria')}>
    {entries.map((entry, index) => {
      const type = detectVariableType(entry.value);
      const opened = expanded.has(index);
      return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
        <div className="prop-level-head">
          <button type="button" className="prop-level-toggle" onClick={() => toggle(index)} aria-expanded={opened} aria-controls={`variable-body-${index}`}>
            <span className="prop-level-summary">
              <span className="prop-level-badge"><DisclosureChevron open={opened} className="prop-level-arrow" /> {entry.key || t('core.variables.unnamed')}</span>
            </span>
          </button>
          <span className="prop-level-rate">{variableTypeLabel(type)}</span>
          <span className="prop-action-controls">
            <button type="button" className="prop-action-del" onClick={() => remove(index)} aria-label={t('core.kv.delete', { index: index + 1 })}>×</button>
          </span>
        </div>
        {opened && <div className="prop-level-body" id={`variable-body-${index}`}>
          <label className="prop-param-field"><span>{t('core.variables.name')}</span><input value={entry.key} onChange={event => update(index, { key: event.target.value })} /></label>
          <label className="prop-param-field"><span>{t('core.variables.valueType')}</span><select value={type} onChange={event => update(index, { value: convertVariableValue(entry.value, event.target.value as VariableType) })}>{TYPE_OPTIONS.map(option => <option key={option} value={option}>{variableTypeLabel(option)}</option>)}</select></label>
          <VariableValueEditor value={entry.value} type={type} onChange={next => update(index, { value: next })} />
        </div>}
      </div>;
    })}
    <button type="button" className="prop-add" onClick={add}>+ {t('core.variables.add')}</button>
  </div>;
}

function VariableValueEditor({ value, type, onChange }: { value: unknown; type: VariableType; onChange: (value: unknown) => void }) {
  if (type === 'number') return <label className="prop-param-field"><span>{t('core.variables.numberValue')}</span><input type="number" value={typeof value === 'number' ? value : Number(value) || 0} onChange={event => onChange(event.target.value === '' ? 0 : Number(event.target.value))} /></label>;
  if (type === 'formula') return <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.formula')}</span><input value={String(value ?? '')} onChange={event => onChange(event.target.value)} placeholder="{level} * 2 + 5" /></label>;
  if (type === 'constant') return <NumericObjectEditor value={asTypedRecord(value, 'constant')} fields={['value']} onChange={onChange} />;
  if (type === 'range' || type === 'uniform') return <NumericObjectEditor value={asTypedRecord(value, type)} fields={['min', 'max']} onChange={onChange} />;
  if (type === 'gaussian') return <NumericObjectEditor value={asTypedRecord(value, type)} fields={['mean', 'std_dev', 'min', 'max', 'max_attempts']} onChange={onChange} />;
  if (type === 'skew_normal') return <NumericObjectEditor value={asTypedRecord(value, type)} fields={['mean', 'std_dev', 'skewness', 'min', 'max', 'max_attempts']} onChange={onChange} />;
  if (type === 'triangle') return <NumericObjectEditor value={asTypedRecord(value, type)} fields={['mode', 'deviation']} onChange={onChange} />;
  if (type === 'expression') return <ExpressionObjectEditor value={asTypedRecord(value, 'expression')} onChange={onChange} />;
  if (type === 'text') return <TextObjectEditor value={asTypedRecord(value, 'text')} onChange={onChange} />;
  if (type === 'random_text') return <RandomTextObjectEditor value={asTypedRecord(value, 'random_text')} onChange={onChange} />;
  if (type === 'random_char') return <RandomCharObjectEditor value={asTypedRecord(value, 'random_char')} onChange={onChange} />;
  if (type === 'weighted_random_char') return <WeightedRandomCharObjectEditor value={asTypedRecord(value, 'weighted_random_char')} onChange={onChange} />;
  if (type === 'conditional_char') return <ConditionalCharObjectEditor value={asTypedRecord(value, 'conditional_char')} onChange={onChange} />;
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
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.expression')}</span><input value={String(value.expression ?? value.formula ?? '')} onChange={event => onChange(cleanObject({ ...value, expression: event.target.value, formula: undefined }))} placeholder="{base} * 1.2" /></label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function TextObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.text')}</span><input value={String(value.value ?? value.text ?? value.template ?? '')} onChange={event => onChange(cleanObject({ ...value, value: event.target.value, text: undefined, template: undefined }))} /></label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function RandomTextObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  const lines = Array.isArray(value.lines ?? value.values) ? (value.lines ?? value.values) as unknown[] : [];
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.candidates')}</span><textarea rows={4} value={lines.map(String).join('\n')} onChange={event => onChange(cleanObject({ ...value, lines: event.target.value.split('\n'), values: undefined }))} /></label>
    <label className="prop-param-field"><span>{t('core.variables.drawCount')}</span><input type="text" value={String(value.count ?? value.rolls ?? 1)} onChange={event => onChange(cleanObject({ ...value, count: parseLoose(event.target.value), rolls: undefined }))} /></label>
    <label className="inline-switch"><input type="checkbox" checked={value.allow_duplicates === true} onChange={event => onChange(cleanObject({ ...value, allow_duplicates: event.target.checked || undefined }))} /> {t('core.variables.allowDuplicates')}</label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function RandomCharObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.characters')}</span><input value={String(value.chars ?? value.characters ?? value.alphabet ?? '')} onChange={event => onChange(cleanObject({ ...value, chars: event.target.value || undefined, characters: undefined, alphabet: undefined }))} placeholder={t('core.variables.charactersPlaceholder')} /></label>
    <label className="prop-param-field"><span>{t('core.variables.randomCount')}</span><input type="text" value={String(value.count ?? value.rolls ?? 1)} onChange={event => onChange(cleanObject({ ...value, count: parseLoose(event.target.value), rolls: undefined }))} /></label>
    <label className="inline-switch"><input type="checkbox" checked={value.allow_duplicates === true} onChange={event => onChange(cleanObject({ ...value, allow_duplicates: event.target.checked || undefined }))} /> {t('core.variables.allowDuplicates')}</label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function WeightedRandomCharObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  const chars = charRows(value.chars ?? value.characters ?? value.values);
  const weights = listRows(value.weights ?? value.weight);
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.charactersPerLine')}</span><textarea rows={4} value={chars.join('\n')} onChange={event => onChange(cleanObject({ ...value, chars: splitRows(event.target.value), characters: undefined, values: undefined }))} /></label>
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.weightsByRow')}</span><textarea rows={4} value={weights.join('\n')} onChange={event => onChange(cleanObject({ ...value, weights: splitRows(event.target.value).map(parseLoose), weight: undefined }))} /></label>
    <label className="prop-param-field"><span>{t('core.variables.randomCount')}</span><input type="text" value={String(value.count ?? value.rolls ?? 1)} onChange={event => onChange(cleanObject({ ...value, count: parseLoose(event.target.value), rolls: undefined }))} /></label>
    <label className="inline-switch"><input type="checkbox" checked={value.allow_duplicates === true} onChange={event => onChange(cleanObject({ ...value, allow_duplicates: event.target.checked || undefined }))} /> {t('core.variables.allowDuplicates')}</label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function ConditionalCharObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  const cases = conditionalCaseRows(value.cases ?? value.conditions);
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.condition')}</span><input value={String(value.condition ?? value.when ?? value['if'] ?? '')} onChange={event => onChange(cleanObject({ ...value, condition: event.target.value, when: undefined, 'if': undefined }))} placeholder="{level} &gt;= 10" /></label>
    <label className="prop-param-field"><span>{t('core.variables.trueValue')}</span><input value={String(value.true_value ?? value['true'] ?? value.then ?? '')} onChange={event => onChange(cleanObject({ ...value, true_value: event.target.value, 'true': undefined, then: undefined }))} /></label>
    <label className="prop-param-field"><span>{t('core.variables.falseValue')}</span><input value={String(value.false_value ?? value['false'] ?? value['else'] ?? '')} onChange={event => onChange(cleanObject({ ...value, false_value: event.target.value, 'false': undefined, 'else': undefined }))} /></label>
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.cases')}</span><textarea rows={4} value={cases.join('\n')} onChange={event => onChange(cleanObject({ ...value, cases: parseConditionalCases(event.target.value), conditions: undefined }))} placeholder="{level} &gt;= 30 =&gt; S&#10;{level} &gt;= 20 =&gt; A" /></label>
    <label className="prop-param-field"><span>{t('core.variables.fallback')}</span><input value={String(value.fallback ?? value.default ?? '')} onChange={event => onChange(cleanObject({ ...value, fallback: event.target.value, 'default': undefined }))} /></label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function BooleanObjectEditor({ value, onChange }: { value: AnyMap; onChange: (value: unknown) => void }) {
  return <div className="schema-object-editor">
    <label className="prop-param-field prop-param-field--wide"><span>{t('core.variables.booleanExpression')}</span><input value={String(value.expression ?? value.value ?? '')} onChange={event => onChange(cleanObject({ ...value, expression: event.target.value, value: undefined }))} placeholder="{level} >= 10" /></label>
    <LocalVariablesEditor value={value.variables} onChange={variables => onChange(cleanObject({ ...value, variables }))} />
  </div>;
}

function LocalVariablesEditor({ value, onChange }: { value: unknown; onChange: (value: Record<string, unknown> | undefined) => void }) {
  const [open, setOpen] = useState(false);
  return <div className="local-variables-editor">
    <button type="button" className="prop-add" onClick={() => setOpen(current => !current)}>{open ? '−' : '+'} {t('core.variables.localVariables')}</button>
    {open && <VariablesMapEditor value={value} onChange={next => onChange(Object.keys(next).length ? next : undefined)} />}
  </div>;
}

function JsonObjectField({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  const [text, setText] = useState(() => JSON.stringify(value ?? {}, null, 2));
  const [error, setError] = useState('');
  return <div className="variable-json">
    <textarea rows={8} value={text} aria-label={t('core.variables.customConfig')} onChange={event => {
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
  if (type === 'random_char') return cleanObject({ type, chars: String(record.chars ?? record.characters ?? ''), count: 1 });
  if (type === 'weighted_random_char') return cleanObject({ type, chars: charRows(record.chars ?? record.characters ?? record.values).length ? charRows(record.chars ?? record.characters ?? record.values) : ['A', 'B', 'C'], weights: listRows(record.weights ?? record.weight).length ? listRows(record.weights ?? record.weight).map(parseLoose) : [1, 1, 1], count: 1 });
  if (type === 'conditional_char') return cleanObject({ type, condition: String(record.condition ?? record.when ?? record['if'] ?? ''), true_value: String(record.true_value ?? record['true'] ?? record.then ?? '1'), false_value: String(record.false_value ?? record['false'] ?? record['else'] ?? '2'), fallback: String(record.fallback ?? record['default'] ?? '') });
  if (type === 'boolean') return cleanObject({ type, expression: String(record.expression ?? record.value ?? '') });
  return previous ?? {};
}

function asTypedRecord(value: unknown, type: string): AnyMap {
  return { ...(isRecord(value) ? value : {}), type };
}

function variableTypeLabel(type: VariableType): string {
  return t(`core.variables.type.${type}`, undefined, type);
}

function variableFieldLabel(field: string): string {
  return t(`core.variables.field.${field}`, undefined, field);
}

function conditionalCaseRows(value: unknown): string[] {
  if (!Array.isArray(value)) return splitRows(String(value ?? ''));
  return value.map(entry => {
    if (!isRecord(entry)) return String(entry ?? '');
    const condition = String(entry.condition ?? entry.when ?? entry['if'] ?? entry.expression ?? entry.formula ?? '');
    const result = String(entry.value ?? entry.char ?? entry.text ?? entry.result ?? entry.output ?? '');
    return condition || result ? `${condition} => ${result}` : '';
  }).filter(entry => entry.trim() !== '');
}

function parseConditionalCases(value: string): AnyMap[] {
  return splitRows(value).map(row => {
    const arrowIndex = row.indexOf('=>');
    const thinArrowIndex = arrowIndex < 0 ? row.indexOf('->') : -1;
    const delimiterIndex = arrowIndex >= 0 ? arrowIndex : thinArrowIndex;
    const delimiterLength = arrowIndex >= 0 ? 2 : thinArrowIndex >= 0 ? 2 : 0;
    if (delimiterIndex < 0) return cleanObject({ condition: row, value: '' });
    return cleanObject({ condition: row.slice(0, delimiterIndex).trim(), value: row.slice(delimiterIndex + delimiterLength).trim() });
  }).filter(entry => Boolean(entry.condition || entry.value));
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

function charRows(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(String).filter(entry => entry.trim() !== '');
  const text = String(value ?? '');
  return text ? Array.from(text) : [];
}

function listRows(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(String).filter(entry => entry.trim() !== '');
  return splitRows(String(value ?? ''));
}

function splitRows(value: string): string[] {
  return value.split('\n').map(entry => entry.trim()).filter(Boolean);
}

function cleanObject(value: AnyMap): AnyMap {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(Array.isArray(entry) && entry.length === 0)));
}

function isRecord(value: unknown): value is AnyMap {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
}
