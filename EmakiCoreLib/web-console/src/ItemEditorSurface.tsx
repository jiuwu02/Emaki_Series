import React, { useEffect, useMemo, useState } from 'react';
import type { ApiClient, ActionTypesResult } from './api';
import { ActionsEditor, Button, EditorChrome, InlineError, MiniText, PropRow, SectionHead, StringListEditor, type EditorChange } from './components';
import { asList, asRecord, asStringList, displaySource, firstItemSource, materialFromItemSource, setDeepValue, type AnyMap } from './itemEditor';
import { t } from './i18n';
import { materialShortName, materialUrls, subscribeTextureBases, textValue } from './lib';
import type { ItemPreviewResult, WebEditorDescriptor, WebEditorField, WebEditorSection, WebRegistryFile, WebRegistryModule } from './types';
import { serializeItemYaml } from './itemEditor';

type Props = { module: WebRegistryModule; file: WebRegistryFile; api: ApiClient; childPath?: string; refreshKey?: number; editor?: WebEditorDescriptor; onReload?: () => void };
type EffectType = 'variables' | 'ea_attribute' | 'es_skill' | 'name_action' | 'lore_action';
type ActionEntry = { type: string; params: Record<string, unknown> };

const DEFAULT_BASE_NAME = t('core.item.defaultBaseName');
const DEFAULT_BASE_LORE = t('core.item.defaultBaseLore');
const EFFECT_TYPES: EffectType[] = ['variables', 'ea_attribute', 'es_skill', 'name_action', 'lore_action'];
const EXTRACT_RETURN_MODES = ['original', 'destroy', 'downgrade'];
const FAILURE_PENALTIES = ['none', 'downgrade', 'destroy'];
const GEM_TYPES = ['attack', 'defense', 'utility', 'universal'];

export function ItemEditorSurface({ module, file, api, childPath, refreshKey = 0, editor, onReload }: Props) {
  const [data, setData] = useState<AnyMap>({});
  const [originalData, setOriginalData] = useState<AnyMap>({});
  const [originalContent, setOriginalContent] = useState('');
  const [preview, setPreview] = useState<ItemPreviewResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [actionTypesResult, setActionTypesResult] = useState<ActionTypesResult | null>(null);
  const [dirty, setDirty] = useState(false);

  const filePath = childPath || file.path;
  const baseName = editor?.baseName ?? DEFAULT_BASE_NAME;
  const baseLore = editor?.baseLore ?? [DEFAULT_BASE_LORE];
  const sections = useMemo(() => editor?.sections?.length ? editor.sections : defaultSections(), [editor]);

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
    const timer = setTimeout(() => {
      api.previewItem(content, 1, baseName, baseLore as string[]).then(setPreview).catch(() => setPreview(null));
    }, 300);
    return () => clearTimeout(timer);
  }, [api, data, loading, baseName, baseLore]);

  const setField = (path: string, value: unknown) => {
    setData(prev => setDeepValue(prev, path.split('.'), value));
    setDirty(true);
  };

  const handleSave = async () => {
    if (saving || serializeItemYaml(data) === originalContent) return;
    setSaving(true);
    setError(null);
    try {
      const content = serializeItemYaml(data);
      await api.saveItem(module.id, filePath, content);
      setOriginalContent(content);
      setOriginalData(data);
      setDirty(false);
    } catch (err: any) {
      setError(err?.message ?? t('core.toast.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const draftContent = serializeItemYaml(data);
  const sourceContent = draftContent;
  const actualDirty = sourceContent !== originalContent;
  const changes = useMemo(() => diffRecords(data, originalData, '', 18), [data, originalData]);

  if (loading) return <div className="ie-surface"><div className="ie-loading" role="status"><div className="ie-skeleton" aria-label={t('core.item.loadingAria')}><div className="ie-skeleton-line" style={{ width: '60%' }} /><div className="ie-skeleton-line" style={{ width: '80%' }} /><div className="ie-skeleton-line" style={{ width: '45%' }} /><div className="ie-skeleton-line" style={{ width: '70%' }} /></div></div></div>;
  if (error && !data) return <div className="ie-surface"><InlineError>{error}</InlineError>{onReload && <Button size="sm" onClick={onReload}>{t('core.action.retry')}</Button>}</div>;

  return (
    <div className="ie-surface" data-dirty={actualDirty || undefined} data-original-size={originalContent.length || undefined}>
      <EditorChrome
        className="ie-header"
        title={editor?.title ?? file.title ?? t('core.item.editorTitle')}
        subtitle={`${module.id}/${filePath}`}
        dirty={actualDirty}
        changes={changes}
        source={sourceContent}
        saving={saving}
        onReload={onReload}
        onSave={handleSave}
      />

      {error && <InlineError>{error}</InlineError>}

      <div className="ie-workbench">
          <GenericPreviewPane data={data} preview={preview} />
          <div className="ie-props-scroll">
            <div className="ie-props">
              {sections.map(section => (
                <div key={section.title}>
                  <SectionHead title={section.title} />
                  {section.comment && <p className="muted-copy">{section.comment}</p>}
                  {section.fields.map(field => <FieldEditor key={field.path} field={field} data={data} originalData={originalData} setField={setField} actionTypesResult={actionTypesResult} />)}
                </div>
              ))}
            </div>
          </div>
        </div>
    </div>
  );
}

function FieldEditor({ field, data, originalData, setField, actionTypesResult }: { field: WebEditorField; data: AnyMap; originalData: AnyMap; setField: (path: string, value: unknown) => void; actionTypesResult: ActionTypesResult | null }) {
  const value = getDeepValue(data, field.path);
  const changed = !valuesEqual(value, getDeepValue(originalData, field.path));
  const label = field.label || field.path;
  const type = field.type || 'text';

  if (type === 'number') return <PropRow label={label} changed={changed} wide={field.wide}><NumberInput value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'boolean') return <PropRow label={label} changed={changed} wide={field.wide}><ToggleButton checked={value === true} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'enum' && field.options?.length) return <PropRow label={label} changed={changed} wide={field.wide}><SelectInput value={value} options={field.options} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'textarea') return <PropRow label={label} changed={changed} wide><textarea rows={field.rows ?? 4} value={textValue(value)} onChange={e => setField(field.path, e.target.value)} placeholder={field.placeholder} /></PropRow>;
  if (type === 'stringList') return <PropRow label={label} changed={changed} wide><StringListEditor items={asStringList(value)} onChange={items => setField(field.path, items)} placeholder={field.placeholder} /></PropRow>;
  if (type === 'numberList') return <PropRow label={label} changed={changed} wide><NumberListEditor items={asList(value).map(item => Number(item) || 0)} onChange={items => setField(field.path, items)} /></PropRow>;
  if (type === 'map' || type === 'dynamicMap' || type === 'objectMap') return <PropRow label={label} changed={changed} wide><MapEditor value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'actions') {
    const mode = field.path.toLowerCase().includes('lore') ? 'lore' : 'name';
    return <PropRow label={label} changed={changed} wide><ActionsEditor actions={parseActionList(value)} onChange={actions => setField(field.path, serializeActionList(actions))} actionTypes={mode === 'lore' ? actionTypesResult?.loreActions ?? [] : actionTypesResult?.nameActions ?? []} mode={mode} /></PropRow>;
  }
  if (type === 'effects') return <PropRow label={label} changed={changed} wide><EffectsEditor value={value} onChange={next => setField(field.path, next)} actionTypesResult={actionTypesResult} /></PropRow>;
  if (type === 'cost') return <CostEditor label={label} value={value ?? { currencies: [], materials: [] }} onChange={next => setField(field.path, next)} />;
  if (type === 'extractReturn') return <ExtractReturnEditor value={value} onChange={next => setField(field.path, next)} />;
  if (type === 'gemUpgrade') return <UpgradeEditor value={value ?? { enabled: false, levels: {} }} onChange={next => setField(field.path, next)} actionTypesResult={actionTypesResult} />;
  if (type === 'gemSlots') return <PropRow label={label} changed={changed} wide><GemSlotsEditor value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  if (type === 'json') return <PropRow label={label} changed={changed} wide><GenericObjectEditor value={value} onChange={next => setField(field.path, next)} /></PropRow>;
  return <PropRow label={label} changed={changed} wide={field.wide}><input type="text" value={textValue(value)} onChange={e => setField(field.path, e.target.value)} placeholder={field.placeholder} /></PropRow>;
}

function ToggleButton({ checked, onChange }: { checked: boolean; onChange: (next: boolean) => void }) {
  return <button type="button" className={`switch ${checked ? 'on' : ''}`} onClick={() => onChange(!checked)}><span />{checked ? '开启' : '关闭'}</button>;
}

function NumberInput({ value, onChange, step }: { value: unknown; onChange: (value: number | undefined) => void; step?: number | string }) {
  return <input type="number" step={step} value={value == null ? '' : textValue(value)} onChange={event => onChange(event.target.value === '' ? undefined : Number(event.target.value))} />;
}

function SelectInput({ value, options, onChange }: { value: unknown; options: string[]; onChange: (value: string) => void }) {
  const current = textValue(value);
  const merged = current && !options.includes(current) ? [...options, current] : options;
  return <select value={current} onChange={event => onChange(event.target.value)}>{merged.map(option => <option key={option} value={option}>{option}</option>)}</select>;
}

function TextInput({ value, onChange, placeholder }: { value: unknown; onChange: (value: string) => void; placeholder?: string }) {
  return <input type="text" value={textValue(value)} onChange={event => onChange(event.target.value)} placeholder={placeholder} />;
}

function KvTable({ entries, onChange, valuePlaceholder = '值' }: { entries: Array<{ key: string; value: unknown }>; onChange: (entries: Array<{ key: string; value: unknown }>) => void; valuePlaceholder?: string }) {
  const update = (index: number, field: 'key' | 'value', value: string) => {
    const next = [...entries];
    next[index] = field === 'key' ? { ...next[index], key: value } : { ...next[index], value: parseLooseScalar(value) };
    onChange(next);
  };
  const remove = (index: number) => onChange(entries.filter((_, itemIndex) => itemIndex !== index));
  return <div className="prop-kv" role="list" aria-label="键值对列表">
    {entries.map((entry, index) => <div className="prop-kv-row" key={index} role="listitem">
      <input type="text" value={entry.key} onChange={event => update(index, 'key', event.target.value)} placeholder="键" aria-label={`键 ${index + 1}`} />
      <input type="text" value={entry.value == null ? '' : String(entry.value)} onChange={event => update(index, 'value', event.target.value)} placeholder={valuePlaceholder} aria-label={`值 ${index + 1}`} />
      <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除第 ${index + 1} 项`}>×</button>
    </div>)}
    <button type="button" className="prop-add" onClick={() => onChange([...entries, { key: '', value: 0 }])}>+ 添加</button>
  </div>;
}

function MapEditor({ value, onChange, valuePlaceholder }: { value: unknown; onChange: (value: Record<string, unknown>) => void; valuePlaceholder?: string }) {
  const entries = Object.entries(asRecord(value)).map(([key, entry]) => ({ key, value: entry }));
  return <KvTable entries={entries} valuePlaceholder={valuePlaceholder} onChange={nextEntries => {
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
      <input type="number" value={String(item)} onChange={event => update(index, event.target.value === '' ? undefined : Number(event.target.value))} aria-label={`数值 ${index + 1}`} />
      <button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除第 ${index + 1} 项`}>×</button>
    </div>)}
    <button type="button" className="prop-add" onClick={() => onChange([...items, 0])}>+ 添加</button>
  </div>;
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
  return <div className="prop-levels" role="list">
    {effects.map((effect, index) => {
      const type = textValue(effect.type) || 'variables';
      const opened = expanded.has(index);
      return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
        <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggle(index)} onKeyDown={event => toggleByKeyboard(event, () => toggle(index))} aria-expanded={opened} aria-controls={`effect-body-${index}`}>
          <span className="prop-level-summary"><span className="prop-level-badge">{opened ? '⌄' : '›'} #{index + 1}</span>{effectTypeLabel(type)}</span>
          <span className="prop-level-rate">{effectSummary(effect)}</span>
          <span className="prop-action-controls" onClick={stopEvent} onKeyDown={stopEvent}>
            <button type="button" onClick={() => moveEffect(index, -1)} disabled={index === 0} aria-label="上移">↑</button>
            <button type="button" onClick={() => moveEffect(index, 1)} disabled={index === effects.length - 1} aria-label="下移">↓</button>
            <button type="button" className="prop-action-del" onClick={() => removeEffect(index)} aria-label="删除">×</button>
          </span>
        </div>
        {opened && <div className="prop-level-body" id={`effect-body-${index}`}>
          <PropRow label="type"><SelectInput value={type} options={EFFECT_TYPES} onChange={nextType => updateEffect(index, defaultEffect(nextType as EffectType))} /></PropRow>
          <EffectPayloadEditor effect={effect} onChange={nextEffect => updateEffect(index, nextEffect)} actionTypesResult={actionTypesResult} />
        </div>}
      </div>;
    })}
    <div className="prop-cost-actions">{EFFECT_TYPES.map(type => <button key={type} type="button" className="prop-add" onClick={() => addEffect(type)}>+ {effectTypeLabel(type)}</button>)}</div>
  </div>;
}

function EffectPayloadEditor({ effect, onChange, actionTypesResult }: { effect: AnyMap; onChange: (effect: AnyMap) => void; actionTypesResult: ActionTypesResult | null }) {
  const type = textValue(effect.type) || 'variables';
  const setPayload = (key: string, value: unknown) => onChange(cleanObject({ ...effect, [key]: value }));
  if (type === 'variables') return <PropRow label="variables" wide><MapEditor value={effect.variables} valuePlaceholder="数值/公式" onChange={value => setPayload('variables', value)} /></PropRow>;
  if (type === 'ea_attribute') return <PropRow label="ea_attributes" wide><MapEditor value={effect.ea_attributes} valuePlaceholder="属性值" onChange={value => setPayload('ea_attributes', value)} /></PropRow>;
  if (type === 'es_skill') return <PropRow label="es_skills" wide><StringListEditor items={skillList(effect)} onChange={items => onChange(cleanObject({ ...effect, es_skills: items, es_skill: undefined }))} placeholder="技能 ID" /></PropRow>;
  if (type === 'name_action') return <PropRow label="name_actions" wide><ActionsEditor actions={parseActionList(effect.name_actions)} onChange={actions => setPayload('name_actions', serializeActionList(actions))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" /></PropRow>;
  if (type === 'lore_action') return <PropRow label="lore_actions" wide><ActionsEditor actions={parseActionList(effect.lore_actions)} onChange={actions => setPayload('lore_actions', serializeActionList(actions))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" /></PropRow>;
  return <GenericObjectEditor value={effect} reservedKeys={['type']} onChange={next => onChange({ type, ...next })} />;
}

function CostEditor({ label, value, onChange, showEnabled }: { label: string; value: unknown; onChange: (value: AnyMap) => void; showEnabled?: boolean }) {
  const cost = asRecord(value);
  const currencies = asList(cost.currencies).map(currency => asRecord(currency));
  const materials = asList(cost.materials).map(material => asRecord(material));
  const setCost = (patch: AnyMap) => onChange(cleanObject({ ...cost, ...patch }));
  return <div className="prop-cost-section">
    <span className="prop-cost-label">{label}</span>
    {showEnabled && <PropRow label="enabled"><ToggleButton checked={cost.enabled !== false} onChange={enabled => setCost({ enabled })} /></PropRow>}
    <CurrencyCostList items={currencies} onChange={items => setCost({ currencies: items })} />
    <MaterialCostList items={materials} onChange={items => setCost({ materials: items })} />
  </div>;
}

function CurrencyCostList({ items, onChange }: { items: AnyMap[]; onChange: (items: AnyMap[]) => void }) {
  const update = (index: number, patch: AnyMap) => onChange(items.map((item, itemIndex) => itemIndex === index ? cleanObject({ ...item, ...patch }) : item));
  const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
  return <div className="prop-cost-group">
    <span className="prop-cost-group-title">货币</span>
    {items.map((currency, index) => <div className="prop-cost-entry" key={index}>
      <div className="prop-cost-entry-head"><span>{textValue(currency.display_name) || textValue(currency.provider, 'vault')}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除货币 ${index + 1}`}>×</button></div>
      <PropRow label="provider"><TextInput value={currency.provider} onChange={provider => update(index, { provider })} /></PropRow>
      <PropRow label="currency_id"><TextInput value={currency.currency_id} onChange={currency_id => update(index, { currency_id })} /></PropRow>
      <PropRow label="amount"><NumberInput value={currency.amount} onChange={amount => update(index, { amount })} /></PropRow>
      <PropRow label="base_cost"><NumberInput value={currency.base_cost} onChange={base_cost => update(index, { base_cost })} /></PropRow>
      <PropRow label="cost_formula"><TextInput value={currency.cost_formula} onChange={cost_formula => update(index, { cost_formula })} placeholder="{base_cost} * {level}" /></PropRow>
      <PropRow label="display_name"><TextInput value={currency.display_name} onChange={display_name => update(index, { display_name })} /></PropRow>
    </div>)}
    <button type="button" className="prop-add" onClick={() => onChange([...items, { provider: 'vault', currency_id: '', base_cost: 0, cost_formula: '', display_name: '' }])}>+ 货币</button>
  </div>;
}

function MaterialCostList({ items, onChange }: { items: AnyMap[]; onChange: (items: AnyMap[]) => void }) {
  const update = (index: number, patch: AnyMap) => onChange(items.map((item, itemIndex) => itemIndex === index ? cleanObject({ ...item, ...patch }) : item));
  const remove = (index: number) => onChange(items.filter((_, itemIndex) => itemIndex !== index));
  return <div className="prop-cost-group">
    <span className="prop-cost-group-title">材料</span>
    {items.map((material, index) => <div className="prop-cost-entry" key={index}>
      <div className="prop-cost-entry-head"><span>{firstItemSource(material.item_sources) || textValue(material.item, '未设置材料')}</span><button type="button" className="prop-kv-del" onClick={() => remove(index)} aria-label={`删除材料 ${index + 1}`}>×</button></div>
      <PropRow label="item_sources" wide><StringListEditor items={materialSources(material)} onChange={item_sources => update(index, cleanObject({ item_sources, item: undefined, material: undefined }))} placeholder="minecraft-gold_nugget" /></PropRow>
      <PropRow label="amount"><NumberInput value={material.amount} onChange={amount => update(index, { amount: amount ?? 1 })} /></PropRow>
    </div>)}
    <button type="button" className="prop-add" onClick={() => onChange([...items, { item_sources: ['minecraft-stone'], amount: 1 }])}>+ 材料</button>
  </div>;
}

function ExtractReturnEditor({ value, onChange }: { value: unknown; onChange: (value: AnyMap) => void }) {
  const data = asRecord(value);
  const update = (patch: AnyMap) => onChange(cleanObject({ mode: 'original', downgrade_levels: 1, degraded_chance: 0, ...data, ...patch }));
  return <div className="prop-cost-section">
    <span className="prop-cost-label">拆卸返还</span>
    <PropRow label="mode"><SelectInput value={data.mode ?? 'original'} options={EXTRACT_RETURN_MODES} onChange={mode => update({ mode })} /></PropRow>
    <PropRow label="downgrade_levels"><NumberInput value={data.downgrade_levels ?? 1} onChange={downgrade_levels => update({ downgrade_levels: downgrade_levels ?? 1 })} /></PropRow>
    <PropRow label="degraded_chance"><NumberInput value={data.degraded_chance ?? 0} step="0.01" onChange={degraded_chance => update({ degraded_chance: degraded_chance ?? 0 })} /></PropRow>
  </div>;
}

function UpgradeEditor({ value, onChange, actionTypesResult }: { value: unknown; onChange: (value: AnyMap) => void; actionTypesResult: ActionTypesResult | null }) {
  const upgrade = asRecord(value);
  const levels = levelMap(upgrade.levels);
  const levelEntries = Object.entries(levels).sort(([left], [right]) => Number(left) - Number(right));
  const [expandedLevels, setExpandedLevels] = useState<Set<string>>(() => new Set(levelEntries.map(([key]) => key)));
  const updateUpgrade = (patch: AnyMap) => onChange(cleanObject({ ...upgrade, ...patch }));
  const updateLevel = (levelKey: string, patch: AnyMap) => updateUpgrade({ levels: { ...levels, [levelKey]: cleanObject({ ...levels[levelKey], ...patch }) } });
  const removeLevel = (levelKey: string) => { const next = { ...levels }; delete next[levelKey]; updateUpgrade({ levels: next }); };
  const addLevel = () => {
    const nextLevel = nextNumericKey(Object.keys(levels), 2);
    updateUpgrade({ max_level: Math.max(toNumber(upgrade.max_level, 1), Number(nextLevel)), levels: { ...levels, [nextLevel]: { display_name: '', effects: [], materials: [], success_rate: 100, actions: { success: [], failure: [] } } } });
    setExpandedLevels(previous => new Set([...previous, nextLevel]));
  };
  const toggleLevel = (levelKey: string) => setExpandedLevels(previous => { const next = new Set(previous); next.has(levelKey) ? next.delete(levelKey) : next.add(levelKey); return next; });
  return <div className="prop-cost-section">
    <PropRow label="enabled"><ToggleButton checked={upgrade.enabled === true} onChange={enabled => updateUpgrade({ enabled })} /></PropRow>
    <PropRow label="max_level"><NumberInput value={upgrade.max_level ?? 1} onChange={max_level => updateUpgrade({ max_level: max_level ?? 1 })} /></PropRow>
    <PropRow label="gui_template"><TextInput value={upgrade.gui_template} onChange={gui_template => updateUpgrade({ gui_template })} placeholder="upgrade/default" /></PropRow>
    <PropRow label="failure_penalty"><SelectInput value={upgrade.failure_penalty ?? 'none'} options={FAILURE_PENALTIES} onChange={failure_penalty => updateUpgrade({ failure_penalty })} /></PropRow>
    <CostEditor label="全局升级经济" value={upgrade.economy ?? { enabled: true, currencies: [], materials: [] }} onChange={economy => updateUpgrade({ economy })} showEnabled />
    <div className="prop-level-subsection"><span className="prop-cost-group-title">success_rates</span><MapEditor value={upgrade.success_rates} valuePlaceholder="成功率" onChange={success_rates => updateUpgrade({ success_rates })} /></div>
    <SectionHead title="升级等级" count={levelEntries.length} actions={<button type="button" className="prop-add-inline" onClick={addLevel}>+</button>} />
    <div className="prop-levels" role="list">
      {levelEntries.map(([levelKey, level]) => {
        const opened = expandedLevels.has(levelKey);
        const actions = asRecord(level.actions);
        return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={levelKey} role="listitem">
          <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggleLevel(levelKey)} onKeyDown={event => toggleByKeyboard(event, () => toggleLevel(levelKey))} aria-expanded={opened} aria-controls={`level-body-${levelKey}`}>
            <span className="prop-level-summary"><span className="prop-level-badge">{opened ? '⌄' : '›'} Lv.{levelKey}</span>{textValue(level.display_name) || '未命名'}</span>
            <span className="prop-level-rate">{textValue(level.success_rate ?? level.success_chance, '继承')}%</span>
            <button type="button" className="prop-kv-del" onClick={event => { event.stopPropagation(); removeLevel(levelKey); }} onKeyDown={stopEvent} aria-label={`删除等级 ${levelKey}`}>×</button>
          </div>
          {opened && <div className="prop-level-body" id={`level-body-${levelKey}`}>
            <PropRow label="display_name"><TextInput value={level.display_name} onChange={display_name => updateLevel(levelKey, { display_name })} /></PropRow>
            <PropRow label="success_rate"><NumberInput value={level.success_rate ?? level.success_chance} onChange={success_rate => updateLevel(levelKey, { success_rate })} /></PropRow>
            <PropRow label="failure_penalty"><SelectInput value={level.failure_penalty ?? ''} options={['', ...FAILURE_PENALTIES]} onChange={failure_penalty => updateLevel(levelKey, { failure_penalty })} /></PropRow>
            <SectionHead title="等级效果" count={asList(level.effects).length} />
            <EffectsEditor value={level.effects} onChange={effects => updateLevel(levelKey, { effects })} actionTypesResult={actionTypesResult} />
            <SectionHead title="升级材料" count={asList(level.materials).length} />
            <MaterialCostList items={asList(level.materials).map(material => asRecord(material))} onChange={materials => updateLevel(levelKey, { materials })} />
            <CostEditor label="等级经济覆盖" value={level.economy ?? { currencies: [] }} onChange={economy => updateLevel(levelKey, { economy })} showEnabled />
            <ActionLinesEditor label="actions.success" value={actions.success} onChange={success => updateLevel(levelKey, { actions: cleanObject({ ...actions, success }) })} />
            <ActionLinesEditor label="actions.failure" value={actions.failure} onChange={failure => updateLevel(levelKey, { actions: cleanObject({ ...actions, failure }) })} />
          </div>}
        </div>;
      })}
    </div>
  </div>;
}

function GemSlotsEditor({ value, onChange }: { value: unknown; onChange: (value: unknown[]) => void }) {
  const slots = asList(value).map(slot => asRecord(slot));
  const [expanded, setExpanded] = useState<Set<number>>(() => new Set(slots.map((_, index) => index)));
  const updateSlot = (index: number, patch: AnyMap) => onChange(slots.map((slot, itemIndex) => itemIndex === index ? cleanObject({ ...slot, ...patch }) : slot));
  const removeSlot = (index: number) => onChange(slots.filter((_, itemIndex) => itemIndex !== index));
  const addSlot = () => { const next = [...slots, { index: nextSlotIndex(slots), type: 'universal', display_name: '' }]; onChange(next); setExpanded(previous => new Set([...previous, next.length - 1])); };
  const toggle = (index: number) => setExpanded(previous => { const next = new Set(previous); next.has(index) ? next.delete(index) : next.add(index); return next; });
  return <div className="prop-levels" role="list">
    {slots.map((slot, index) => {
      const opened = expanded.has(index);
      return <div className={`prop-level-item${opened ? ' expanded' : ''}`} key={index} role="listitem">
        <div className="prop-level-head" role="button" tabIndex={0} onClick={() => toggle(index)} onKeyDown={event => toggleByKeyboard(event, () => toggle(index))} aria-expanded={opened} aria-controls={`slot-body-${index}`}>
          <span className="prop-level-summary"><span className="prop-level-badge">{opened ? '⌄' : '›'} #{textValue(slot.index, String(index))}</span>{textValue(slot.type, 'universal')}</span>
          <span className="prop-level-rate">{textValue(slot.display_name) || '未命名'}</span>
          <button type="button" className="prop-kv-del" onClick={event => { event.stopPropagation(); removeSlot(index); }} onKeyDown={stopEvent} aria-label={`删除插槽 ${index + 1}`}>×</button>
        </div>
        {opened && <div className="prop-level-body" id={`slot-body-${index}`}>
          <PropRow label="index"><NumberInput value={slot.index ?? index} onChange={slotIndex => updateSlot(index, { index: slotIndex ?? index })} /></PropRow>
          <PropRow label="type"><TextInput value={slot.type} onChange={slotType => updateSlot(index, { type: slotType })} /></PropRow>
          <PropRow label="display_name"><TextInput value={slot.display_name} onChange={display_name => updateSlot(index, { display_name })} /></PropRow>
        </div>}
      </div>;
    })}
    <button type="button" className="prop-add" onClick={addSlot}>+ 插槽</button>
  </div>;
}

function ActionLinesEditor({ label, value, onChange }: { label: string; value: unknown; onChange: (value: string[]) => void }) {
  return <PropRow label={label} wide><StringListEditor items={asStringList(value)} onChange={onChange} placeholder="sendmessage text=&quot;...&quot;" /></PropRow>;
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

function GenericPreviewPane({ data, preview }: { data: AnyMap; preview: ItemPreviewResult | null }) {
  const source = firstItemSource(data.item_sources ?? asRecord(data.match).item_sources ?? preview?.material);
  const material = materialFromItemSource(source || data.material || preview?.material);
  const [, refreshTextureOrder] = useState(0);
  const urls = materialUrls(material);
  const [imgFailed, setImgFailed] = useState(false);
  useEffect(() => setImgFailed(false), [material]);
  useEffect(() => subscribeTextureBases(() => { setImgFailed(false); refreshTextureOrder((version) => version + 1); }), []);

  return (
    <div className="ie-preview" role="complementary" aria-label={t('core.item.previewAria')}>
      <div className="ie-preview-icon">
        {urls.length > 0 && !imgFailed ? <img src={urls[0]} alt={material || t('core.item.iconAlt')} onError={e => { const target = e.currentTarget; const next = urls[urls.indexOf(target.src) + 1]; if (next) target.src = next; else setImgFailed(true); }} /> : <span className="ie-preview-fallback">{materialShortName(material) || '?'}</span>}
      </div>
      <div className="ie-preview-meta">
        <span className="ie-preview-kind">{t('core.item.genericKind')}</span>
        {Boolean(preview?.id || data.id) && <code className="ie-preview-id">{textValue(preview?.id ?? data.id)}</code>}
        <span className="ie-preview-source">{displaySource(source || material)}</span>
      </div>
      <div className="ie-tooltip">
        {Boolean(preview?.displayName || data.display_name) && <div className="ie-tooltip-name"><MiniText value={preview?.displayName ?? data.display_name} /></div>}
        {(preview?.lore ?? asStringList(data.lore)).map((line, i) => <div className="ie-tooltip-line" key={i}><MiniText value={line} /></div>)}
        {!preview?.displayName && !data.display_name && !(preview?.lore ?? asList(data.lore)).length && <span className="ie-tooltip-empty">{t('core.item.noPreview')}</span>}
      </div>
    </div>
  );
}

function parseActionList(value: unknown): ActionEntry[] {
  return asList(value).map(raw => {
    const row = asRecord(raw);
    const type = textValue(row.action ?? row.type);
    const params: Record<string, unknown> = {};
    Object.entries(row).forEach(([key, entry]) => { if (key !== 'action' && key !== 'type') params[key] = entry; });
    return { type, params };
  });
}

function serializeActionList(actions: ActionEntry[]): unknown[] {
  return actions.map(entry => cleanObject({ action: entry.type, ...entry.params }));
}

function getDeepValue(source: AnyMap, path: string): unknown {
  return path.split('.').reduce<unknown>((current, key) => asRecord(current)[key], source);
}

function diffRecords(after: unknown, before: unknown, prefix = '', limit = 18): EditorChange[] {
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
    else if (!valuesEqual(left, right)) changes.push({ path, before: left, after: right });
  }
  return changes;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
}

function valuesEqual(left: unknown, right: unknown): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}

function defaultSections(): WebEditorSection[] {
  return [{
    title: t('core.item.basic'),
    fields: [
      { path: 'id', label: 'ID', type: 'text' },
      { path: 'material', label: t('core.item.material'), type: 'text' },
      { path: 'display_name', label: t('core.item.displayName'), type: 'text' },
      { path: 'lore', label: 'Lore', type: 'stringList', wide: true },
      { path: 'name_actions', label: 'Name Actions', type: 'actions', wide: true },
      { path: 'lore_actions', label: 'Lore Actions', type: 'actions', wide: true }
    ]
  }];
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
  return { variables: '变量', ea_attribute: '属性', es_skill: '技能', name_action: '名称动作', lore_action: 'Lore 动作' }[type] ?? type;
}

function effectSummary(effect: AnyMap): string {
  const type = textValue(effect.type);
  if (type === 'variables') return `${Object.keys(asRecord(effect.variables)).length} 变量`;
  if (type === 'ea_attribute') return `${Object.keys(asRecord(effect.ea_attributes)).length} 属性`;
  if (type === 'es_skill') return `${skillList(effect).length} 技能`;
  if (type === 'name_action') return `${asList(effect.name_actions).length} 动作`;
  if (type === 'lore_action') return `${asList(effect.lore_actions).length} 动作`;
  return `${Math.max(0, Object.keys(effect).length - 1)} 字段`;
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
  if (Array.isArray(value)) return Object.fromEntries(value.map((entry, index) => [String(index + 2), asRecord(entry)]));
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

function parseLooseScalar(value: string): unknown {
  const trimmed = value.trim();
  if (trimmed === '') return '';
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
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
