import React, { useContext, useEffect, useId, useMemo, useState } from 'react';
import {
  asList,
  asRecord,
  asStringList,
  displaySource,
  firstItemSource,
  itemKind,
  materialFromItemSource,
  materialShortName,
  materialUrls,
  renderMiniMessageParts,
  serializeItemYaml,
  setDeepValue,
  textValue,
  t,
  EditorChrome,
  type EditorChange,
  type ActionTypesResult,
  type AnyMap,
  type ApiClient,
  type ItemPreviewResult,
  type WebEditorDescriptor,
  type WebEditorField,
  type WebRegistryFile,
  type WebRegistryModule,
} from 'emaki-web-console';

type Props = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  api: ApiClient;
  childPath?: string;
  refreshKey?: number;
  editor?: WebEditorDescriptor;
  onReload?: () => void;
};

type ActionEntry = { action: string; params: Record<string, unknown> };
type EffectType = 'variables' | 'ea_attribute' | 'es_skill' | 'name_action' | 'lore_action';

const DEFAULT_BASE_NAME = '<gray>预览装备</gray>';
const DEFAULT_BASE_LORE = '<gray>原始装备 Lore</gray>';
const EFFECT_TYPES: EffectType[] = ['variables', 'ea_attribute', 'es_skill', 'name_action', 'lore_action'];
const EXTRACT_RETURN_MODES = ['original', 'destroy', 'downgrade'];
const FAILURE_PENALTIES = ['none', 'downgrade', 'destroy'];
const GEM_TYPES = ['attack', 'defense', 'utility', 'universal'];

const FieldMetaContext = React.createContext<Record<string, WebEditorField>>({});
const ChangedPathContext = React.createContext<Set<string>>(new Set());

function MiniText({ value }: { value: unknown }) {
  return <>{renderMiniMessageParts(value).map((part, index) => <span key={index} style={{ color: part.color }} className={part.token ? 'mini-token' : undefined}>{part.text}</span>)}</>;
}

function SectionHead({ title, count, actions }: { title: string; count?: number; actions?: React.ReactNode }) {
  return (
    <div className="prop-section-head">
      <span className="prop-section-title">{title}</span>
      {count !== undefined && <span className="prop-section-count">{count}</span>}
      {actions && <span className="prop-section-actions">{actions}</span>}
    </div>
  );
}

function PropRow({ label, children, wide, changed }: { label: string; children: React.ReactNode; wide?: boolean; changed?: boolean }) {
  const id = useId();
  const fields = useContext(FieldMetaContext);
  const changedPaths = useContext(ChangedPathContext);
  const meta = fieldMeta(fields, label);
  const displayLabel = translateFieldLabel(label, meta?.label);
  const isChanged = changed ?? isChangedPath(label, changedPaths);
  const title = meta?.comment ? `${label}\n${meta.comment}` : label;
  return (
    <div className={`prop-row${wide ? ' prop-row--wide' : ''}${isChanged ? ' changed' : ''}`}>
      <label className="prop-label" htmlFor={id} title={title}>{displayLabel}</label>
      <span className="prop-value" id={`${id}-wrap`}>{children}</span>
    </div>
  );
}

function ToggleButton({ checked, onChange }: { checked: boolean; onChange: (next: boolean) => void }) {
  return <button type="button" className={`switch ${checked ? 'on' : ''}`} onClick={() => onChange(!checked)}><span />{checked ? '开启' : '关闭'}</button>;
}

function TextInput({ value, onChange, placeholder }: { value: unknown; onChange: (value: string) => void; placeholder?: string }) {
  return <input type="text" value={textValue(value)} onChange={event => onChange(event.target.value)} placeholder={placeholder} />;
}

function NumberInput({ value, onChange, step }: { value: unknown; onChange: (value: number | undefined) => void; step?: number | string }) {
  return <input type="number" step={step} value={value == null ? '' : textValue(value)} onChange={event => onChange(event.target.value === '' ? undefined : Number(event.target.value))} />;
}

function SelectInput({ value, options, onChange, labelPrefix }: { value: unknown; options: string[]; onChange: (value: string) => void; labelPrefix?: string }) {
  const current = textValue(value);
  const merged = current && !options.includes(current) ? [...options, current] : options;
  return <select value={current} onChange={event => onChange(event.target.value)}>{merged.map(option => <option key={option} value={option}>{translateOption(labelPrefix, option)}</option>)}</select>;
}

function KvTable({ entries, onChange, valuePlaceholder = '值' }: { entries: Array<{ key: string; value: unknown }>; onChange: (entries: Array<{ key: string; value: unknown }>) => void; valuePlaceholder?: string }) {
  const update = (index: number, field: 'key' | 'value', value: string) => {
    const next = [...entries];
    next[index] = field === 'key'
      ? { ...next[index], key: value }
      : { ...next[index], value: parseLooseScalar(value) };
    onChange(next);
  };
  const remove = (index: number) => onChange(entries.filter((_, itemIndex) => itemIndex !== index));
  const add = () => onChange([...entries, { key: '', value: 0 }]);

  return (
    <div className="prop-kv" role="list" aria-label="键值对列表">
      {entries.map((entry, index) => (
        <div className="prop-kv-row" key={index} role="listitem">
          <input type="text" value={entry.key} onChange={event => update(index, 'key', event.target.value)} placeholder="键" aria-label={`键 ${index + 1}`} />
          <input type="text" value={entry.value == null ? '' : String(entry.value)} onChange={event => update(index, 'value', event.target.value)} placeholder={valuePlaceholder} aria-label={`值 ${index + 1}`} />
          <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除第 ${index + 1} 项`}>×</button>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>{t('emakigem.action.add')}</button>
    </div>
  );
}

function MapEditor({ value, onChange, valuePlaceholder }: { value: unknown; onChange: (value: Record<string, unknown>) => void; valuePlaceholder?: string }) {
  const entries = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: entry }));
  return <KvTable entries={entries} valuePlaceholder={valuePlaceholder} onChange={nextEntries => {
    const next: AnyMap = {};
    nextEntries.forEach(entry => {
      if (entry.key.trim()) next[entry.key.trim()] = entry.value;
    });
    onChange(next);
  }} />;
}

function StringListEditor({ items, onChange, placeholder }: { items: string[]; onChange: (items: string[]) => void; placeholder?: string }) {
  const update = (index: number, value: string) => onChange(items.map((item, itemIndex) => itemIndex === index ? value : item));
  const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
  const add = () => onChange([...items, '']);
  return (
    <div className="prop-kv" role="list">
      {items.map((item, index) => (
        <div className="prop-kv-row prop-kv-row--single" key={index} role="listitem">
          <input type="text" value={item} onChange={event => update(index, event.target.value)} placeholder={placeholder} aria-label={`项 ${index + 1}`} />
          <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除第 ${index + 1} 项`}>×</button>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>{t('emakigem.action.add')}</button>
    </div>
  );
}

function NumberListEditor({ items, onChange }: { items: number[]; onChange: (items: number[]) => void }) {
  const update = (index: number, value: number | undefined) => onChange(items.map((item, itemIndex) => itemIndex === index ? value ?? 0 : item));
  const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
  const add = () => onChange([...items, 0]);
  return (
    <div className="prop-kv" role="list">
      {items.map((item, index) => (
        <div className="prop-kv-row prop-kv-row--single" key={index} role="listitem">
          <input type="number" value={String(item)} onChange={event => update(index, event.target.value === '' ? undefined : Number(event.target.value))} aria-label={`数值 ${index + 1}`} />
          <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除第 ${index + 1} 项`}>×</button>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>{t('emakigem.action.add')}</button>
    </div>
  );
}

function ActionsEditor({ actions, onChange, actionTypes, mode }: { actions: ActionEntry[]; onChange: (actions: ActionEntry[]) => void; actionTypes: string[]; mode: 'name' | 'lore' }) {
  const update = (index: number, patch: Partial<ActionEntry>) => {
    const next = [...actions];
    next[index] = { ...next[index], ...patch };
    onChange(next);
  };
  const updateParam = (index: number, key: string, value: unknown) => {
    const next = [...actions];
    next[index] = { ...next[index], params: cleanObject({ ...next[index].params, [key]: value }) };
    onChange(next);
  };
  const remove = (index: number) => onChange(actions.filter((_, itemIndex) => itemIndex !== index));
  const add = () => onChange([...actions, { action: actionTypes[0] ?? '', params: {} }]);
  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= actions.length) return;
    const next = [...actions];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };

  return (
    <div className="prop-actions">
      {actions.map((action, index) => {
        const options = action.action && !actionTypes.includes(action.action) ? [...actionTypes, action.action] : actionTypes;
        return <div className="prop-action-item" key={index}>
          <div className="prop-action-head">
            <span className="prop-action-grip">≡</span>
            <select value={action.action} onChange={event => update(index, { action: event.target.value, params: {} })} aria-label={`动作类型 ${index + 1}`}>
              {options.map(type => <option key={type} value={type}>{translateOption('actionType', type)}</option>)}
              {!options.length && <option value="">未选择</option>}
            </select>
            <span className="prop-action-controls">
              <button type="button" onClick={() => move(index, -1)} disabled={index === 0} aria-label="上移">↑</button>
              <button type="button" onClick={() => move(index, 1)} disabled={index === actions.length - 1} aria-label="下移">↓</button>
              <button type="button" className="prop-action-del" onClick={() => remove(index)} aria-label="删除">×</button>
            </span>
          </div>
          <div className="prop-action-params">
            {mode === 'name' && <>
              <LabeledParamInput paramKey="value" value={textValue(action.params.value)} onChange={value => updateParam(index, 'value', value)} />
              {action.action === 'regex_replace' && <>
                <LabeledParamInput paramKey="regex_pattern" value={textValue(action.params.regex_pattern)} onChange={value => updateParam(index, 'regex_pattern', value)} />
                <LabeledParamInput paramKey="replacement" value={textValue(action.params.replacement)} onChange={value => updateParam(index, 'replacement', value)} />
              </>}
            </>}
            {mode === 'lore' && <>
              <LabeledParamTextarea paramKey="content" rows={2} value={asStringList(action.params.content).join('\n')} onChange={value => updateParam(index, 'content', splitLines(value))} />
              <LabeledParamInput paramKey="target_pattern" value={textValue(action.params.target_pattern)} onChange={value => updateParam(index, 'target_pattern', value)} />
              <LabeledParamInput paramKey="anchor" value={textValue(action.params.anchor)} onChange={value => updateParam(index, 'anchor', value)} />
            </>}
          </div>
        </div>;
      })}
      <button type="button" className="prop-add" onClick={add}>{t('emakigem.action.addAction')}</button>
    </div>
  );
}

function LabeledParamInput({ paramKey, value, onChange }: { paramKey: string; value: string; onChange: (value: string) => void }) {
  const label = t(`emakigem.actionParam.${paramKey}`, undefined, paramKey);
  return <label className="prop-param-field"><span>{label}</span><input type="text" value={value} onChange={event => onChange(event.target.value)} placeholder={label} aria-label={label} /></label>;
}

function LabeledParamTextarea({ paramKey, value, onChange, rows = 2 }: { paramKey: string; value: string; onChange: (value: string) => void; rows?: number }) {
  const label = t(`emakigem.actionParam.${paramKey}`, undefined, paramKey);
  return <label className="prop-param-field prop-param-field--wide"><span>{label}</span><textarea rows={rows} value={value} onChange={event => onChange(event.target.value)} placeholder={label} aria-label={label} /></label>;
}

function EffectsEditor({ value, onChange, actionTypesResult }: { value: unknown; onChange: (effects: unknown[]) => void; actionTypesResult: ActionTypesResult | null }) {
  const effects = asList(value).map(effect => asRecord(effect));
  const [expanded, setExpanded] = useState<Set<number>>(() => new Set(effects.map((_, index) => index)));
  const updateEffect = (index: number, nextEffect: AnyMap) => onChange(effects.map((effect, itemIndex) => itemIndex === index ? cleanObject(nextEffect) : effect));
  const removeEffect = (index: number) => onChange(effects.filter((_, itemIndex) => itemIndex !== index));
  const addEffect = (type: EffectType) => {
    const next = [...effects, defaultEffect(type)];
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

  return (
    <div className="prop-levels" role="list">
      {effects.map((effect, index) => {
        const type = textValue(effect.type) || 'variables';
        const opened = expanded.has(index);
        return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
          <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggle(index)} onKeyDown={event => toggleByKeyboard(event, () => toggle(index))} aria-expanded={opened} aria-controls={`effect-body-${index}`}>
            <span className="prop-level-summary">
              <span className="prop-level-badge">{opened ? '⌄' : '›'} #{index + 1}</span>
              {effectTypeLabel(type)}
            </span>
            <span className="prop-level-rate">{effectSummary(effect)}</span>
            <span className="prop-action-controls" onClick={stopEvent} onKeyDown={stopEvent}>
              <button type="button" onClick={() => moveEffect(index, -1)} disabled={index === 0} aria-label="上移">↑</button>
              <button type="button" onClick={() => moveEffect(index, 1)} disabled={index === effects.length - 1} aria-label="下移">↓</button>
              <button type="button" className="prop-action-del" onClick={() => removeEffect(index)} aria-label="删除">×</button>
            </span>
          </div>
          {opened && <div className="prop-level-body" id={`effect-body-${index}`}>
            <PropRow label="type"><SelectInput value={type} options={EFFECT_TYPES} labelPrefix="effect" onChange={nextType => updateEffect(index, defaultEffect(nextType as EffectType))} /></PropRow>
            <EffectPayloadEditor effect={effect} onChange={nextEffect => updateEffect(index, nextEffect)} actionTypesResult={actionTypesResult} />
          </div>}
        </div>;
      })}
      <div className="prop-cost-actions">
        {EFFECT_TYPES.map(type => <button key={type} type="button" className="prop-add" onClick={() => addEffect(type)}>+ {effectTypeLabel(type)}</button>)}
      </div>
    </div>
  );
}

function EffectPayloadEditor({ effect, onChange, actionTypesResult }: { effect: AnyMap; onChange: (effect: AnyMap) => void; actionTypesResult: ActionTypesResult | null }) {
  const type = textValue(effect.type) || 'variables';
  const setPayload = (key: string, value: unknown) => onChange(cleanObject({ ...effect, [key]: value }));

  if (type === 'variables') {
    return <PropRow label="variables" wide><MapEditor value={effect.variables} valuePlaceholder="数值/公式" onChange={value => setPayload('variables', value)} /></PropRow>;
  }
  if (type === 'ea_attribute') {
    return <PropRow label="ea_attributes" wide><MapEditor value={effect.ea_attributes} valuePlaceholder="属性值" onChange={value => setPayload('ea_attributes', value)} /></PropRow>;
  }
  if (type === 'es_skill') {
    return <PropRow label="es_skills" wide><StringListEditor items={skillList(effect)} onChange={items => onChange(cleanObject({ ...effect, es_skills: items, es_skill: undefined }))} placeholder="技能 ID" /></PropRow>;
  }
  if (type === 'name_action') {
    return <PropRow label="name_actions" wide><ActionsEditor actions={parseActionList(effect.name_actions)} onChange={actions => setPayload('name_actions', serializeActionList(actions))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" /></PropRow>;
  }
  if (type === 'lore_action') {
    return <PropRow label="lore_actions" wide><ActionsEditor actions={parseActionList(effect.lore_actions)} onChange={actions => setPayload('lore_actions', serializeActionList(actions))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" /></PropRow>;
  }
  return <GenericObjectEditor value={effect} reservedKeys={['type']} onChange={next => onChange({ type, ...next })} />;
}

function GenericObjectEditor({ value, reservedKeys, onChange }: { value: unknown; reservedKeys?: string[]; onChange: (value: AnyMap) => void }) {
  const reserved = new Set(reservedKeys ?? []);
  const entries = Object.entries(asRecord(value)).filter(([key]) => !reserved.has(key)).map(([key, entry]) => ({ key, value: entry }));
  return <PropRow label="字段" wide><KvTable entries={entries} onChange={nextEntries => {
    const next: AnyMap = {};
    nextEntries.forEach(entry => {
      if (entry.key.trim()) next[entry.key.trim()] = entry.value;
    });
    onChange(next);
  }} /></PropRow>;
}

function CostEditor({ label, value, onChange, showEnabled }: { label: string; value: unknown; onChange: (value: AnyMap) => void; showEnabled?: boolean }) {
  const hasValue = value != null && value !== '';
  const cost = hasValue ? asRecord(value) : {};
  const currencies = asList(cost.currencies).map(currency => asRecord(currency));
  const materials = asList(cost.materials).map(material => asRecord(material));
  const setCost = (patch: AnyMap) => onChange(cleanObject({ ...cost, ...patch }));

  if (!hasValue) {
    return (
      <div className="prop-cost-empty">
        <span className="prop-label">{label}</span>
        <button type="button" className="prop-add" onClick={() => onChange(showEnabled ? { enabled: true, currencies: [], materials: [] } : { currencies: [], materials: [] })}>{t('emakigem.action.setCost', { label })}</button>
      </div>
    );
  }

  return (
    <div className="prop-cost-section">
      <span className="prop-cost-label">{label}</span>
      {showEnabled && <PropRow label="enabled"><ToggleButton checked={cost.enabled !== false} onChange={checked => setCost({ enabled: checked })} /></PropRow>}
      <CurrencyCostList items={currencies} onChange={items => setCost({ currencies: items })} />
      <MaterialCostList items={materials} onChange={items => setCost({ materials: items })} />
    </div>
  );
}

function CurrencyCostList({ items, onChange }: { items: AnyMap[]; onChange: (items: AnyMap[]) => void }) {
  const update = (index: number, patch: AnyMap) => onChange(items.map((item, itemIndex) => itemIndex === index ? cleanObject({ ...item, ...patch }) : item));
  const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
  const add = () => onChange([...items, { provider: 'vault', currency_id: '', base_cost: 0, cost_formula: '', display_name: '' }]);

  return (
    <div className="prop-cost-group">
      <span className="prop-cost-group-title">{t('emakigem.cost.currency')}</span>
      {items.map((currency, index) => (
        <div className="prop-cost-entry" key={index}>
          <div className="prop-cost-entry-head">
            <span>{textValue(currency.display_name) || textValue(currency.provider, 'vault')}</span>
            <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除货币 ${index + 1}`}>×</button>
          </div>
          <PropRow label="provider"><TextInput value={currency.provider} onChange={value => update(index, { provider: value })} /></PropRow>
          <PropRow label="currency_id"><TextInput value={currency.currency_id} onChange={value => update(index, { currency_id: value })} /></PropRow>
          <PropRow label="amount"><NumberInput value={currency.amount} onChange={value => update(index, { amount: value })} /></PropRow>
          <PropRow label="base_cost"><NumberInput value={currency.base_cost} onChange={value => update(index, { base_cost: value })} /></PropRow>
          <PropRow label="cost_formula"><TextInput value={currency.cost_formula} onChange={value => update(index, { cost_formula: value })} placeholder="{base_cost} * {level}" /></PropRow>
          <PropRow label="display_name"><TextInput value={currency.display_name} onChange={value => update(index, { display_name: value })} /></PropRow>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>{t('emakigem.action.addCurrency')}</button>
    </div>
  );
}

function MaterialCostList({ items, onChange }: { items: AnyMap[]; onChange: (items: AnyMap[]) => void }) {
  const update = (index: number, patch: AnyMap) => onChange(items.map((item, itemIndex) => itemIndex === index ? cleanObject({ ...item, ...patch }) : item));
  const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
  const add = () => onChange([...items, { item_sources: ['minecraft-stone'], amount: 1 }]);

  return (
    <div className="prop-cost-group">
      <span className="prop-cost-group-title">{t('emakigem.cost.material')}</span>
      {items.map((material, index) => (
        <div className="prop-cost-entry" key={index}>
          <div className="prop-cost-entry-head">
            <span>{firstItemSource(material.item_sources) || textValue(material.item, '未设置材料')}</span>
            <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除材料 ${index + 1}`}>×</button>
          </div>
          <PropRow label="item_sources" wide><StringListEditor items={materialSources(material)} onChange={items => update(index, cleanObject({ item_sources: items, item: undefined, material: undefined }))} placeholder="minecraft-gold_nugget" /></PropRow>
          <PropRow label="amount"><NumberInput value={material.amount} onChange={value => update(index, { amount: value ?? 1 })} /></PropRow>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>{t('emakigem.action.addMaterial')}</button>
    </div>
  );
}

function ExtractReturnEditor({ value, onChange }: { value: unknown; onChange: (value: AnyMap) => void }) {
  const data = asRecord(value);
  const update = (patch: AnyMap) => onChange(cleanObject({ mode: 'original', downgrade_levels: 1, degraded_chance: 0, ...data, ...patch }));
  return (
    <div className="prop-cost-section">
      <span className="prop-cost-label">{t('emakigem.cost.extractReturn')}</span>
      <PropRow label="mode"><SelectInput value={data.mode ?? 'original'} options={EXTRACT_RETURN_MODES} labelPrefix="extract" onChange={mode => update({ mode })} /></PropRow>
      <PropRow label="downgrade_levels"><NumberInput value={data.downgrade_levels ?? 1} onChange={value => update({ downgrade_levels: value ?? 1 })} /></PropRow>
      <PropRow label="degraded_chance"><NumberInput value={data.degraded_chance ?? 0} step="0.01" onChange={value => update({ degraded_chance: value ?? 0 })} /></PropRow>
    </div>
  );
}

function UpgradeEditor({ value, onChange, actionTypesResult }: { value: unknown; onChange: (value: AnyMap) => void; actionTypesResult: ActionTypesResult | null }) {
  const upgrade = asRecord(value);
  const levels = levelMap(upgrade.levels);
  const levelEntries = Object.entries(levels).sort(([left], [right]) => Number(left) - Number(right));
  const [expandedLevels, setExpandedLevels] = useState<Set<string>>(() => new Set(levelEntries.map(([key]) => key)));
  const updateUpgrade = (patch: AnyMap) => onChange(cleanObject({ ...upgrade, ...patch }));
  const updateLevel = (levelKey: string, patch: AnyMap) => updateUpgrade({ levels: { ...levels, [levelKey]: cleanObject({ ...levels[levelKey], ...patch }) } });
  const removeLevel = (levelKey: string) => {
    const next = { ...levels };
    delete next[levelKey];
    updateUpgrade({ levels: next });
  };
  const addLevel = () => {
    const nextLevel = nextNumericKey(Object.keys(levels), 2);
    updateUpgrade({
      max_level: Math.max(toNumber(upgrade.max_level, 1), Number(nextLevel)),
      levels: {
        ...levels,
        [nextLevel]: {
          display_name: '',
          effects: [],
          materials: [],
          success_rate: 100,
          actions: { success: [], failure: [] }
        }
      }
    });
    setExpandedLevels(previous => new Set([...previous, nextLevel]));
  };
  const toggleLevel = (levelKey: string) => setExpandedLevels(previous => {
    const next = new Set(previous);
    next.has(levelKey) ? next.delete(levelKey) : next.add(levelKey);
    return next;
  });

  return (
    <div className="prop-cost-section">
      <PropRow label="enabled"><ToggleButton checked={upgrade.enabled === true} onChange={enabled => updateUpgrade({ enabled })} /></PropRow>
      <PropRow label="max_level"><NumberInput value={upgrade.max_level ?? 1} onChange={value => updateUpgrade({ max_level: value ?? 1 })} /></PropRow>
      <PropRow label="gui_template"><TextInput value={upgrade.gui_template} onChange={gui_template => updateUpgrade({ gui_template })} placeholder="upgrade/default" /></PropRow>
      <PropRow label="failure_penalty"><SelectInput value={upgrade.failure_penalty ?? 'none'} options={FAILURE_PENALTIES} labelPrefix="failure" onChange={failure_penalty => updateUpgrade({ failure_penalty })} /></PropRow>
      <CostEditor label={t('emakigem.cost.globalUpgrade')} value={upgrade.economy ?? { enabled: true, currencies: [], materials: [] }} onChange={economy => updateUpgrade({ economy })} showEnabled />
      <div className="prop-level-subsection">
        <span className="prop-cost-group-title">success_rates</span>
        <MapEditor value={upgrade.success_rates} valuePlaceholder="成功率" onChange={success_rates => updateUpgrade({ success_rates })} />
      </div>
      <SectionHead title={t('emakigem.section.levels')} count={levelEntries.length} actions={<button type="button" className="prop-add-inline" onClick={addLevel}>+</button>} />
      <div className="prop-levels" role="list">
        {levelEntries.map(([levelKey, level]) => {
          const opened = expandedLevels.has(levelKey);
          const actions = asRecord(level.actions);
          return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={levelKey} role="listitem">
            <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggleLevel(levelKey)} onKeyDown={event => toggleByKeyboard(event, () => toggleLevel(levelKey))} aria-expanded={opened} aria-controls={`level-body-${levelKey}`}>
              <span className="prop-level-summary">
                <span className="prop-level-badge">{opened ? '⌄' : '›'} Lv.{levelKey}</span>
                {textValue(level.display_name) || '未命名'}
              </span>
              <span className="prop-level-rate">{textValue(level.success_rate ?? level.success_chance, '继承')}%</span>
              <button type="button" className="prop-kv-del" onClick={event => { event.stopPropagation(); removeLevel(levelKey); }} onKeyDown={stopEvent} aria-label={`删除等级 ${levelKey}`}>×</button>
            </div>
            {opened && <div className="prop-level-body" id={`level-body-${levelKey}`}>
              <PropRow label="display_name"><TextInput value={level.display_name} onChange={display_name => updateLevel(levelKey, { display_name })} /></PropRow>
              <PropRow label="success_rate"><NumberInput value={level.success_rate ?? level.success_chance} onChange={success_rate => updateLevel(levelKey, { success_rate })} /></PropRow>
              <PropRow label="failure_penalty"><SelectInput value={level.failure_penalty ?? ''} options={['', ...FAILURE_PENALTIES]} labelPrefix="failure" onChange={failure_penalty => updateLevel(levelKey, { failure_penalty })} /></PropRow>
              <SectionHead title={t('emakigem.section.levelEffects')} count={asList(level.effects).length} />
              <EffectsEditor value={level.effects} onChange={effects => updateLevel(levelKey, { effects })} actionTypesResult={actionTypesResult} />
              <SectionHead title={t('emakigem.section.upgradeMaterials')} count={asList(level.materials).length} />
              <MaterialCostList items={asList(level.materials).map(material => asRecord(material))} onChange={materials => updateLevel(levelKey, { materials })} />
              <CostEditor label={t('emakigem.cost.levelOverride')} value={level.economy} onChange={economy => updateLevel(levelKey, { economy })} showEnabled />
              <ActionLinesEditor label="actions.success" value={actions.success} onChange={success => updateLevel(levelKey, { actions: cleanObject({ ...actions, success }) })} />
              <ActionLinesEditor label="actions.failure" value={actions.failure} onChange={failure => updateLevel(levelKey, { actions: cleanObject({ ...actions, failure }) })} />
            </div>}
          </div>;
        })}
      </div>
    </div>
  );
}

function ActionLinesEditor({ label, value, onChange }: { label: string; value: unknown; onChange: (value: string[]) => void }) {
  return <PropRow label={label} wide><StringListEditor items={asStringList(value)} onChange={onChange} placeholder="sendmessage text=&quot;...&quot;" /></PropRow>;
}

function PreviewPane({ preview, kind, previewLevel, setPreviewLevel, loading, error }: { preview: ItemPreviewResult | null; kind: string; previewLevel: number; setPreviewLevel: (level: number) => void; loading?: boolean; error?: string | null }) {
  const source = firstItemSource(preview?.match?.item_sources ?? (preview as any)?.item_sources);
  const material = materialFromItemSource(source || preview?.material);
  const urls = materialUrls(material);
  const levels = preview?.levels ?? [];
  const hasLevels = levels.length > 0;
  const statusKey = error ? 'failed' : loading ? 'syncing' : 'live';
  const [imgFailed, setImgFailed] = useState(false);
  useEffect(() => { setImgFailed(false); }, [material]);

  return (
    <div className="ie-preview" role="complementary" aria-label={t('emakigem.preview.aria')}>
      <div className="ie-preview-icon">
        {urls.length > 0 && !imgFailed ? (
          <img src={urls[0]} alt={material || '物品图标'} onError={event => {
            const target = event.currentTarget;
            const next = urls[urls.indexOf(target.src) + 1];
            if (next) target.src = next;
            else setImgFailed(true);
          }} />
        ) : <span className="ie-preview-fallback">{materialShortName(material) || '?'}</span>}
      </div>
      <div className="ie-preview-meta">
        <span className="ie-preview-kind">{t(`emakigem.preview.kind.${kind}`, undefined, kind)}</span>
        {preview?.id && <code className="ie-preview-id">{preview.id}</code>}
        <span className="ie-preview-source">{displaySource(source || material)}</span>
        <span className={`ie-preview-status ${statusKey}`}>{t(`emakigem.preview.status.${statusKey}`)}</span>
      </div>
      <div className="ie-level-panel">
        <div className="ie-level-head">
          <span>{t('emakigem.preview.levelTitle')}</span>
          <code>{hasLevels ? t('emakigem.preview.levelCurrent', { level: previewLevel }) : t('emakigem.preview.levelBase')}</code>
        </div>
        {hasLevels ? <div className="ie-level-rail">
          {levels.map(level => <button key={level} type="button" className={level === previewLevel ? 'active' : ''} onClick={() => setPreviewLevel(level)} aria-pressed={level === previewLevel}>Lv.{level}</button>)}
        </div> : <p className="ie-level-empty">{t('emakigem.preview.levelHint')}</p>}
        {hasLevels && <p className="ie-level-hint">{t('emakigem.preview.levelHint')}</p>}
      </div>
      <div className={`ie-tooltip ${loading ? 'is-refreshing' : ''}`}>
        {preview?.displayName && <div className="ie-tooltip-name"><MiniText value={preview.displayName} /></div>}
        {(preview?.lore ?? []).map((line, index) => <div className="ie-tooltip-line" key={index}><MiniText value={line} /></div>)}
        {!preview?.displayName && !preview?.lore?.length && <span className="ie-tooltip-empty">{error || t('emakigem.preview.empty')}</span>}
      </div>
    </div>
  );
}

function GemPanel({ data, setField, actionTypesResult }: { data: AnyMap; setField: (path: string[], value: unknown) => void; actionTypesResult: ActionTypesResult | null }) {
  const legacyVisible = ['variables', 'ea_attributes', 'skills', 'name_actions', 'lore_actions'].some(key => data[key] != null);
  return (
    <div className="ie-props">
      <SectionHead title={t('emakigem.section.basic')} />
      <PropRow label="id"><TextInput value={data.id} onChange={value => setField(['id'], value)} /></PropRow>
      <PropRow label="display_name"><TextInput value={data.display_name} onChange={value => setField(['display_name'], value)} /></PropRow>
      <PropRow label="lore" wide><StringListEditor items={asStringList(data.lore)} onChange={items => setField(['lore'], items)} placeholder="宝石自身 Lore 行" /></PropRow>
      <PropRow label="gem_type"><SelectInput value={data.gem_type ?? 'universal'} options={GEM_TYPES} labelPrefix="gemType" onChange={value => setField(['gem_type'], value)} /></PropRow>
      <PropRow label="level"><NumberInput value={data.level ?? 1} onChange={value => setField(['level'], value ?? 1)} /></PropRow>
      <PropRow label="item_sources" wide><StringListEditor items={asStringList(data.item_sources)} onChange={items => setField(['item_sources'], items)} placeholder="minecraft-redstone" /></PropRow>
      <PropRow label="custom_model_data"><NumberInput value={data.custom_model_data} onChange={value => setField(['custom_model_data'], value)} /></PropRow>
      <PropRow label="socket_compatibility" wide><StringListEditor items={asStringList(data.socket_compatibility)} onChange={items => setField(['socket_compatibility'], items)} placeholder="attack / universal" /></PropRow>

      <SectionHead title={t('emakigem.section.effects')} count={asList(data.effects).length} />
      <EffectsEditor value={data.effects} onChange={effects => setField(['effects'], effects)} actionTypesResult={actionTypesResult} />

      {legacyVisible && <>
        <SectionHead title={t('emakigem.section.legacy')} />
        {data.variables != null && <PropRow label="variables" wide><MapEditor value={data.variables} onChange={value => setField(['variables'], value)} /></PropRow>}
        {data.ea_attributes != null && <PropRow label="ea_attributes" wide><MapEditor value={data.ea_attributes} onChange={value => setField(['ea_attributes'], value)} /></PropRow>}
        {data.skills != null && <PropRow label="skills" wide><StringListEditor items={asStringList(data.skills)} onChange={items => setField(['skills'], items)} placeholder="技能 ID" /></PropRow>}
        {data.name_actions != null && <PropRow label="name_actions" wide><ActionsEditor actions={parseActionList(data.name_actions)} onChange={actions => setField(['name_actions'], serializeActionList(actions))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" /></PropRow>}
        {data.lore_actions != null && <PropRow label="lore_actions" wide><ActionsEditor actions={parseActionList(data.lore_actions)} onChange={actions => setField(['lore_actions'], serializeActionList(actions))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" /></PropRow>}
      </>}

      <SectionHead title={t('emakigem.section.costReturn')} />
      <CostEditor label={t('emakigem.cost.inlay')} value={data.inlay_cost ?? { currencies: [], materials: [] }} onChange={value => setField(['inlay_cost'], value)} />
      <CostEditor label={t('emakigem.cost.extract')} value={data.extract_cost ?? { currencies: [], materials: [] }} onChange={value => setField(['extract_cost'], value)} />
      <ExtractReturnEditor value={data.extract_return} onChange={value => setField(['extract_return'], value)} />

      <SectionHead title={t('emakigem.section.upgrade')} />
      <UpgradeEditor value={data.upgrade ?? { enabled: false, levels: {} }} onChange={value => setField(['upgrade'], value)} actionTypesResult={actionTypesResult} />

      <SectionHead title={t('emakigem.section.gemActions')} />
      <ActionLinesEditor label="inlay_success" value={asRecord(data.actions).inlay_success} onChange={items => setField(['actions', 'inlay_success'], items)} />
      <ActionLinesEditor label="extract_success" value={asRecord(data.actions).extract_success} onChange={items => setField(['actions', 'extract_success'], items)} />
    </div>
  );
}

function SlotsEditor({ value, onChange }: { value: unknown; onChange: (value: unknown[]) => void }) {
  const slots = asList(value).map(slot => asRecord(slot));
  const [expanded, setExpanded] = useState<Set<number>>(() => new Set(slots.map((_, index) => index)));
  const updateSlot = (index: number, patch: AnyMap) => onChange(slots.map((slot, itemIndex) => itemIndex === index ? cleanObject({ ...slot, ...patch }) : slot));
  const removeSlot = (index: number) => onChange(slots.filter((_, itemIndex) => itemIndex !== index));
  const addSlot = () => {
    const next = [...slots, { index: nextSlotIndex(slots), type: 'universal', display_name: '' }];
    onChange(next);
    setExpanded(previous => new Set([...previous, next.length - 1]));
  };
  const toggle = (index: number) => setExpanded(previous => {
    const next = new Set(previous);
    next.has(index) ? next.delete(index) : next.add(index);
    return next;
  });

  return (
    <div className="prop-levels" role="list">
      {slots.map((slot, index) => {
        const opened = expanded.has(index);
        return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
          <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggle(index)} onKeyDown={event => toggleByKeyboard(event, () => toggle(index))} aria-expanded={opened} aria-controls={`slot-body-${index}`}>
            <span className="prop-level-summary">
              <span className="prop-level-badge">{opened ? '⌄' : '›'} #{textValue(slot.index, String(index))}</span>
              {textValue(slot.type, 'universal')}
            </span>
            <span className="prop-level-rate">{textValue(slot.display_name) || '未命名'}</span>
            <button type="button" className="prop-kv-del" onClick={event => { event.stopPropagation(); removeSlot(index); }} onKeyDown={stopEvent} aria-label={`删除插槽 ${index + 1}`}>×</button>
          </div>
          {opened && <div className="prop-level-body" id={`slot-body-${index}`}>
            <PropRow label="index"><NumberInput value={slot.index ?? index} onChange={value => updateSlot(index, { index: value ?? index })} /></PropRow>
            <PropRow label="type"><SelectInput value={slot.type ?? 'universal'} options={GEM_TYPES} labelPrefix="gemType" onChange={type => updateSlot(index, { type })} /></PropRow>
            <PropRow label="display_name"><TextInput value={slot.display_name} onChange={display_name => updateSlot(index, { display_name })} /></PropRow>
          </div>}
        </div>;
      })}
      <button type="button" className="prop-add" onClick={addSlot}>{t('emakigem.action.addSlot')}</button>
    </div>
  );
}

function SocketPanel({ data, setField, actionTypesResult }: { data: AnyMap; setField: (path: string[], value: unknown) => void; actionTypesResult: ActionTypesResult | null }) {
  const match = asRecord(data.match);
  return (
    <div className="ie-props">
      <SectionHead title={t('emakigem.section.matchRules')} />
      <PropRow label="id"><TextInput value={data.id} onChange={value => setField(['id'], value)} /></PropRow>
      <PropRow label="match.item_sources" wide><StringListEditor items={asStringList(match.item_sources)} onChange={items => setField(['match', 'item_sources'], items)} placeholder="minecraft-diamond_sword" /></PropRow>
      <PropRow label="match.slot_groups" wide><StringListEditor items={asStringList(match.slot_groups)} onChange={items => setField(['match', 'slot_groups'], items)} placeholder="weapon / armor / offhand" /></PropRow>
      <PropRow label="match.lore_contains" wide><StringListEditor items={asStringList(match.lore_contains)} onChange={items => setField(['match', 'lore_contains'], items)} placeholder="Lore 包含文本" /></PropRow>

      <SectionHead title={t('emakigem.section.slots')} count={asList(data.slots).length} />
      <SlotsEditor value={data.slots} onChange={slots => setField(['slots'], slots)} />
      <PropRow label="default_open_slots" wide><NumberListEditor items={asList(data.default_open_slots).map(entry => Number(entry) || 0)} onChange={items => setField(['default_open_slots'], items)} /></PropRow>

      <SectionHead title={t('emakigem.section.gemLimit')} />
      <PropRow label="allowed_gem_types" wide><StringListEditor items={asStringList(data.allowed_gem_types)} onChange={items => setField(['allowed_gem_types'], items)} placeholder="attack / defense / utility / universal" /></PropRow>
      <PropRow label="max_same_type"><NumberInput value={data.max_same_type ?? 0} onChange={value => setField(['max_same_type'], value ?? 0)} /></PropRow>
      <PropRow label="max_same_id"><NumberInput value={data.max_same_id ?? 1} onChange={value => setField(['max_same_id'], value ?? 1)} /></PropRow>

      <SectionHead title={t('emakigem.section.guiTemplate')} />
      <PropRow label="gui.gem_template"><TextInput value={asRecord(data.gui).gem_template} onChange={value => setField(['gui', 'gem_template'], value)} placeholder="gem/default" /></PropRow>
      <PropRow label="gui.open_template"><TextInput value={asRecord(data.gui).open_template} onChange={value => setField(['gui', 'open_template'], value)} placeholder="open/default" /></PropRow>

      <SectionHead title={t('emakigem.section.displayActions')} />
      <PropRow label="name_actions" wide><ActionsEditor actions={parseActionList(data.name_actions)} onChange={actions => setField(['name_actions'], serializeActionList(actions))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" /></PropRow>
      <PropRow label="lore_actions" wide><ActionsEditor actions={parseActionList(data.lore_actions)} onChange={actions => setField(['lore_actions'], serializeActionList(actions))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" /></PropRow>
    </div>
  );
}

function GenericPanel({ data, setField, actionTypesResult }: { data: AnyMap; setField: (path: string[], value: unknown) => void; actionTypesResult: ActionTypesResult | null }) {
  return (
    <div className="ie-props">
      <SectionHead title={t('emakigem.section.genericBasic')} />
      <PropRow label="id"><TextInput value={data.id} onChange={value => setField(['id'], value)} /></PropRow>
      <PropRow label="material"><TextInput value={data.material} onChange={value => setField(['material'], value)} /></PropRow>
      <PropRow label="display_name"><TextInput value={data.display_name} onChange={value => setField(['display_name'], value)} /></PropRow>
      <PropRow label="lore" wide><StringListEditor items={asStringList(data.lore)} onChange={items => setField(['lore'], items)} placeholder="每行一个" /></PropRow>
      <SectionHead title={t('emakigem.section.displayActions')} />
      <PropRow label="name_actions" wide><ActionsEditor actions={parseActionList(data.name_actions)} onChange={actions => setField(['name_actions'], serializeActionList(actions))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" /></PropRow>
      <PropRow label="lore_actions" wide><ActionsEditor actions={parseActionList(data.lore_actions)} onChange={actions => setField(['lore_actions'], serializeActionList(actions))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" /></PropRow>
    </div>
  );
}

export function EmakiGemItemSurface({ module, file, api, childPath, refreshKey = 0, editor, onReload }: Props) {
  const [data, setData] = useState<AnyMap>({});
  const [originalData, setOriginalData] = useState<AnyMap>({});
  const [originalContent, setOriginalContent] = useState('');
  const [preview, setPreview] = useState<ItemPreviewResult | null>(null);
  const [previewLevel, setPreviewLevel] = useState(1);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [actionTypesResult, setActionTypesResult] = useState<ActionTypesResult | null>(null);
  const [dirty, setDirty] = useState(false);

  const filePath = childPath || file.path;
  const surfaceEditorId = editor?.id ?? file.editorId;
  const kind = useMemo(() => resolveSurfaceKind(surfaceEditorId, filePath, preview), [surfaceEditorId, filePath, preview]);
  const baseName = editor?.baseName ?? DEFAULT_BASE_NAME;
  const baseLore = Array.isArray(editor?.baseLore) ? editor.baseLore as string[] : [DEFAULT_BASE_LORE];
  const editorFields = useMemo(() => editorFieldMap(editor), [editor]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.readItem(module.id, filePath).then(doc => {
      if (cancelled) return;
      setData(doc.data as AnyMap);
      setOriginalData(doc.data as AnyMap);
      setOriginalContent(doc.content);
      setDirty(false);
      setLoading(false);
    }).catch(err => {
      if (cancelled) return;
      setError(String(err?.message ?? err));
      setData({});
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [api, module.id, filePath, refreshKey]);

  useEffect(() => {
    api.actionTypes().then(setActionTypesResult).catch(() => {});
  }, [api]);

  useEffect(() => {
    if (loading) return;
    const content = serializeItemYaml(data);
    setPreviewLoading(true);
    setPreviewError(null);
    const timer = window.setTimeout(() => {
      api.previewItem(content, previewLevel, String(baseName), baseLore)
        .then(nextPreview => {
          setPreview(nextPreview);
          setPreviewError(null);
        })
        .catch(err => {
          setPreview(null);
          setPreviewError(String(err?.message ?? err ?? t('emakigem.preview.status.failed')));
        })
        .finally(() => setPreviewLoading(false));
    }, 300);
    return () => {
      window.clearTimeout(timer);
      setPreviewLoading(false);
    };
  }, [api, data, previewLevel, loading, baseName, baseLore]);

  const setField = (path: string[], value: unknown) => {
    setData(previous => setDeepValue(previous, path, value));
    setDirty(true);
  };

  const handleSave = async () => {
    if (!actualDirty || saving) return;
    setSaving(true);
    setError(null);
    try {
      const content = serializeItemYaml(data);
      await api.saveItem(module.id, filePath, content);
      setOriginalContent(content);
      setOriginalData(data);
      setDirty(false);
    } catch (err: any) {
      setError(err?.message ?? '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const draftContent = serializeItemYaml(data);
  const actualDirty = draftContent !== originalContent;
  const changes = useMemo(() => diffRecords(data, originalData, '', 24), [data, originalData]);
  const changedPaths = useMemo(() => new Set(changes.map(change => change.path)), [changes]);

  if (loading) return <div className="ie-surface"><div className="ie-loading"><div className="ie-skeleton" aria-label="加载中"><div className="ie-skeleton-line" style={{ width: '60%' }} /><div className="ie-skeleton-line" style={{ width: '80%' }} /><div className="ie-skeleton-line" style={{ width: '45%' }} /><div className="ie-skeleton-line" style={{ width: '70%' }} /></div></div></div>;
  if (error && Object.keys(data).length === 0) return <div className="ie-surface"><div className="ie-error" role="alert">{error}</div></div>;

  return (
    <div className="ie-surface" data-dirty={actualDirty || undefined}>
      <EditorChrome
        className="ie-header"
        title={editor?.title ?? file.title ?? 'EmakiGem 物品编辑器'}
        subtitle={`${module.id}/${filePath}`}
        dirty={actualDirty}
        changes={changes}
        source={draftContent}
        saving={saving}
        onReload={onReload}
        onSave={handleSave}
      />

      {error && <div className="ie-error" role="alert">{error}</div>}

      <FieldMetaContext.Provider value={editorFields}>
        <ChangedPathContext.Provider value={changedPaths}>
          <div className="ie-workbench">
            <PreviewPane preview={preview} kind={kind} previewLevel={previewLevel} setPreviewLevel={setPreviewLevel} loading={previewLoading} error={previewError} />
            <div className="ie-props-scroll">
              {kind === 'gem' && <GemPanel data={data} setField={setField} actionTypesResult={actionTypesResult} />}
              {kind === 'socket' && <SocketPanel data={data} setField={setField} actionTypesResult={actionTypesResult} />}
              {kind === 'generic' && <GenericPanel data={data} setField={setField} actionTypesResult={actionTypesResult} />}
            </div>
          </div>
        </ChangedPathContext.Provider>
      </FieldMetaContext.Provider>
    </div>
  );
}

function resolveSurfaceKind(editorId: string | undefined, path: string, preview?: ItemPreviewResult | null): 'gem' | 'socket' | 'generic' {
  if (editorId === 'emakigem:gem') return 'gem';
  if (editorId === 'emakigem:socket-item') return 'socket';
  return itemKind(path, preview);
}

function editorFieldMap(editor: WebEditorDescriptor | undefined): Record<string, WebEditorField> {
  const fields = editor?.fields;
  if (!fields || typeof fields !== 'object' || Array.isArray(fields)) return {};
  return fields as Record<string, WebEditorField>;
}

function fieldMeta(fields: Record<string, WebEditorField>, path: string): WebEditorField | undefined {
  if (fields[path]) return fields[path];
  const key = lastPathKey(path);
  if (fields[key]) return fields[key];
  return undefined;
}

function diffRecords(after: unknown, before: unknown, prefix = '', limit = 24): EditorChange[] {
  if (limit <= 0) return [];
  if (!isPlainObject(after) || !isPlainObject(before)) return valuesEqual(after, before) ? [] : [{ path: prefix || 'root', before, after }];
  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])];
  const changes: EditorChange[] = [];
  for (const key of keys) {
    if (changes.length >= limit) break;
    const path = prefix ? `${prefix}.${key}` : key;
    const left = (before as Record<string, unknown>)[key];
    const right = (after as Record<string, unknown>)[key];
    if (isPlainObject(left) && isPlainObject(right)) changes.push(...diffRecords(right, left, path, limit - changes.length));
    else if (!valuesEqual(left, right)) changes.push({ path, label: translateFieldLabel(path), before: left, after: right });
  }
  return changes;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
}

function valuesEqual(left: unknown, right: unknown): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}

function isChangedPath(path: string, changedPaths: Set<string>): boolean {
  return changedPaths.has(path) || [...changedPaths].some(changed => changed === path || changed.startsWith(`${path}.`) || path.startsWith(`${changed}.`));
}

function translateFieldLabel(path: string, fallback?: string): string {
  const exact = t(`emakigem.item.field.${path}`, undefined, '');
  if (exact) return exact;
  const key = lastPathKey(path);
  const byKey = t(`emakigem.item.field.${key}`, undefined, '');
  return byKey || fallback || humanizeFieldLabel(path);
}

function translateOption(prefix: string | undefined, option: string): string {
  if (!option) return option;
  if (!prefix) return option;
  return t(`emakigem.option.${prefix}.${option}`, undefined, option);
}

function humanizeFieldLabel(path: string): string {
  if (/[^\u0000-\u00ff]/.test(path)) return path;
  return lastPathKey(path).replace(/_/g, ' ');
}

function lastPathKey(path: string): string {
  return path.includes('.') ? path.slice(path.lastIndexOf('.') + 1) : path;
}

function stopEvent(event: React.SyntheticEvent) {
  event.stopPropagation();
}

function toggleByKeyboard(event: React.KeyboardEvent, action: () => void) {
  if (event.key !== 'Enter' && event.key !== ' ') return;
  event.preventDefault();
  action();
}

function parseActionList(value: unknown): ActionEntry[] {
  return asList(value).map(raw => {
    const row = asRecord(raw);
    const action = textValue(row.action ?? row.type);
    const params: Record<string, unknown> = {};
    Object.entries(row).forEach(([key, entry]) => {
      if (key !== 'action' && key !== 'type') params[key] = entry;
    });
    return { action, params };
  });
}

function serializeActionList(actions: ActionEntry[]): unknown[] {
  return actions.map(entry => cleanObject({ action: entry.action, ...entry.params }));
}

function defaultEffect(type: EffectType): AnyMap {
  if (type === 'variables') return { type, variables: {} };
  if (type === 'ea_attribute') return { type, ea_attributes: {} };
  if (type === 'es_skill') return { type, es_skills: [] };
  if (type === 'name_action') return { type, name_actions: [] };
  if (type === 'lore_action') return { type, lore_actions: [] };
  return { type };
}

function effectTypeLabel(type: string): string {
  return {
    variables: '变量',
    ea_attribute: '属性',
    es_skill: '技能',
    name_action: '名称动作',
    lore_action: 'Lore 动作'
  }[type] ?? type;
}

function effectSummary(effect: AnyMap): string {
  const type = textValue(effect.type);
  if (type === 'variables') return `${Object.keys(asRecord(effect.variables)).length} 变量`;
  if (type === 'ea_attribute') return `${Object.keys(asRecord(effect.ea_attributes)).length} 属性`;
  if (type === 'es_skill') return `${skillList(effect).length} 技能`;
  if (type === 'name_action') return `${asList(effect.name_actions).length} 动作`;
  if (type === 'lore_action') return `${asList(effect.lore_actions).length} 动作`;
  return `${Object.keys(effect).length - 1} 字段`;
}

function skillList(effect: AnyMap): string[] {
  const skills = asStringList(effect.es_skills);
  const single = textValue(effect.es_skill);
  return single ? [...skills, single] : skills;
}

function materialSources(material: AnyMap): string[] {
  const sources = asStringList(material.item_sources);
  const legacy = textValue(material.item || material.material);
  return sources.length > 0 ? sources : legacy ? [legacy] : [];
}

function levelMap(value: unknown): Record<string, AnyMap> {
  if (Array.isArray(value)) {
    return Object.fromEntries(value.map((entry, index) => [String(index + 2), asRecord(entry)]));
  }
  return Object.fromEntries(Object.entries(asRecord(value)).map(([key, entry]) => [key, asRecord(entry)]));
}

function nextNumericKey(keys: string[], fallback: number): string {
  const numeric = keys.map(key => Number(key)).filter(value => Number.isFinite(value));
  return String(numeric.length ? Math.max(...numeric) + 1 : fallback);
}

function nextSlotIndex(slots: AnyMap[]): number {
  const indexes = slots.map(slot => Number(slot.index)).filter(index => Number.isFinite(index));
  return indexes.length ? Math.max(...indexes) + 1 : 0;
}

function toNumber(value: unknown, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function splitLines(value: string): string[] {
  return value.split('\n');
}

function parseLooseScalar(value: string): unknown {
  const trimmed = value.trim();
  if (trimmed === '') return '';
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  return value;
}

function cleanObject<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && !(Array.isArray(entry) && entry.length === 0))) as T;
}
