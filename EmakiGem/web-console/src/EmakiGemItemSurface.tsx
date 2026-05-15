import { useEffect, useId, useMemo, useRef, useState } from 'react';
import type { ApiClient, ActionTypesResult } from '../../../EmakiCoreLib/web-console/src/api';
import type { ItemPreviewResult, WebEditorDescriptor, WebRegistryFile, WebRegistryModule } from '../../../EmakiCoreLib/web-console/src/types';
import { materialShortName, materialUrls, renderMiniMessageParts, textValue } from '../../../EmakiCoreLib/web-console/src/guiEditor';
import { asList, asRecord, asStringList, displaySource, effectsByType, firstItemSource, itemKind, mapEntries, materialFromItemSource, serializeItemYaml, setDeepValue, type AnyMap } from '../../../EmakiCoreLib/web-console/src/itemEditor';

/* ─── Stable ID counter for list keys ─── */
let _nextStableId = 1;
function nextId() { return _nextStableId++; }
type StableEntry<T> = { _id: number; data: T };

type Props = { module: WebRegistryModule; file: WebRegistryFile; api: ApiClient; childPath?: string; refreshKey?: number; editor?: WebEditorDescriptor; onReload?: () => void };
const DEFAULT_BASE_NAME = '<gray>预览装备</gray>';
const DEFAULT_BASE_LORE = '<gray>原始装备 Lore</gray>';

function MiniText({ value }: { value: unknown }) {
  return <>{renderMiniMessageParts(value).map((part, index) => <span key={index} style={{ color: part.color }} className={part.token ? 'mini-token' : undefined}>{part.text}</span>)}</>;
}

/* ─── Section Header ─── */
function SectionHead({ title, count, actions }: { title: string; count?: number; actions?: React.ReactNode }) {
  return (
    <div className="prop-section-head">
      <span className="prop-section-title">{title}</span>
      {count !== undefined && <span className="prop-section-count">{count}</span>}
      {actions && <span className="prop-section-actions">{actions}</span>}
    </div>
  );
}

/* ─── Property Row ─── */
function PropRow({ label, children, wide }: { label: string; children: React.ReactNode; wide?: boolean }) {
  const id = useId();
  return (
    <div className={`prop-row${wide ? ' prop-row--wide' : ''}`}>
      <label className="prop-label" htmlFor={id}>{label}</label>
      <span className="prop-value" id={`${id}-wrap`}>{children}</span>
    </div>
  );
}

/* ─── KV Table Editor ─── */
function KvTable({ entries, onChange }: { entries: Array<{ key: string; value: unknown }>; onChange: (entries: Array<{ key: string; value: unknown }>) => void }) {
  const stableRef = useRef<StableEntry<{ key: string; value: unknown }>[]>([]);
  // Sync stable IDs with entries length
  if (stableRef.current.length !== entries.length) {
    stableRef.current = entries.map((e, i) => stableRef.current[i] ? { ...stableRef.current[i], data: e } : { _id: nextId(), data: e });
  } else {
    stableRef.current = stableRef.current.map((s, i) => ({ ...s, data: entries[i] }));
  }
  const stable = stableRef.current;

  const update = (i: number, field: 'key' | 'value', v: string) => {
    const next = [...entries];
    if (field === 'key') {
      next[i] = { ...next[i], key: v };
    } else {
      next[i] = { ...next[i], value: v === '' ? '' : isNaN(Number(v)) ? v : Number(v) };
    }
    onChange(next);
  };
  const remove = (i: number) => { stableRef.current.splice(i, 1); onChange(entries.filter((_, idx) => idx !== i)); };
  const add = () => { stableRef.current.push({ _id: nextId(), data: { key: '', value: 0 } }); onChange([...entries, { key: '', value: 0 }]); };
  return (
    <div className="prop-kv" role="list" aria-label="键值对列表">
      {stable.map((entry, i) => (
        <div className="prop-kv-row" key={entry._id} role="listitem">
          <input type="text" value={String(entry.data.key)} onChange={e => update(i, 'key', e.target.value)} placeholder="键" aria-label={`键 ${i + 1}`} />
          <input type="text" value={String(entry.data.value ?? '')} onChange={e => update(i, 'value', e.target.value)} placeholder="值" aria-label={`值 ${i + 1}`} />
          <button className="prop-kv-del" onClick={() => remove(i)} aria-label={`删除第 ${i + 1} 项`}>×</button>
        </div>
      ))}
      <button className="prop-add" onClick={add}>+ 添加</button>
    </div>
  );
}

/* ─── String List Editor ─── */
function StringListEditor({ items, onChange, placeholder }: { items: string[]; onChange: (items: string[]) => void; placeholder?: string }) {
  const stableRef = useRef<StableEntry<string>[]>([]);
  if (stableRef.current.length !== items.length) {
    stableRef.current = items.map((item, i) => stableRef.current[i] ? { ...stableRef.current[i], data: item } : { _id: nextId(), data: item });
  } else {
    stableRef.current = stableRef.current.map((s, i) => ({ ...s, data: items[i] }));
  }
  const stable = stableRef.current;

  const update = (i: number, v: string) => { const next = [...items]; next[i] = v; onChange(next); };
  const remove = (i: number) => { stableRef.current.splice(i, 1); onChange(items.filter((_, idx) => idx !== i)); };
  const add = () => { stableRef.current.push({ _id: nextId(), data: '' }); onChange([...items, '']); };
  return (
    <div className="prop-kv" role="list">
      {stable.map((entry, i) => (
        <div className="prop-kv-row prop-kv-row--single" key={entry._id} role="listitem">
          <input type="text" value={entry.data} onChange={e => update(i, e.target.value)} placeholder={placeholder} aria-label={`项 ${i + 1}`} />
          <button className="prop-kv-del" onClick={() => remove(i)} aria-label={`删除第 ${i + 1} 项`}>×</button>
        </div>
      ))}
      <button className="prop-add" onClick={add}>+ 添加</button>
    </div>
  );
}

/* ─── Number List Editor ─── */
function NumberListEditor({ items, onChange }: { items: number[]; onChange: (items: number[]) => void }) {
  const stableRef = useRef<StableEntry<number>[]>([]);
  if (stableRef.current.length !== items.length) {
    stableRef.current = items.map((item, i) => stableRef.current[i] ? { ...stableRef.current[i], data: item } : { _id: nextId(), data: item });
  } else {
    stableRef.current = stableRef.current.map((s, i) => ({ ...s, data: items[i] }));
  }
  const stable = stableRef.current;

  const update = (i: number, v: string) => { const next = [...items]; next[i] = Number(v) || 0; onChange(next); };
  const remove = (i: number) => { stableRef.current.splice(i, 1); onChange(items.filter((_, idx) => idx !== i)); };
  const add = () => { stableRef.current.push({ _id: nextId(), data: 0 }); onChange([...items, 0]); };
  return (
    <div className="prop-kv" role="list">
      {stable.map((entry, i) => (
        <div className="prop-kv-row prop-kv-row--single" key={entry._id} role="listitem">
          <input type="number" value={String(entry.data)} onChange={e => update(i, e.target.value)} aria-label={`数值 ${i + 1}`} />
          <button className="prop-kv-del" onClick={() => remove(i)} aria-label={`删除第 ${i + 1} 项`}>×</button>
        </div>
      ))}
      <button className="prop-add" onClick={add}>+ 添加</button>
    </div>
  );
}

/* ─── Cost Editor (structured, replaces raw JSON) ─── */
function CostEditor({ label, value, onChange }: { label: string; value: unknown; onChange: (v: unknown) => void }) {
  const cost = asRecord(value);
  const currencies = asList(cost.currencies).map(c => asRecord(c));
  const materials = asList(cost.materials).map(m => asRecord(m));

  const setCurrency = (i: number, field: string, v: unknown) => {
    const next = [...currencies]; next[i] = { ...next[i], [field]: v };
    onChange({ ...cost, currencies: next });
  };
  const removeCurrency = (i: number) => onChange({ ...cost, currencies: currencies.filter((_, idx) => idx !== i) });
  const addCurrency = () => onChange({ ...cost, currencies: [...currencies, { provider: 'vault', currency_id: '', base_cost: 0, cost_formula: '{base_cost} * {level}', display_name: '' }] });

  const setMaterial = (i: number, field: string, v: unknown) => {
    const next = [...materials]; next[i] = { ...next[i], [field]: v };
    onChange({ ...cost, materials: next });
  };
  const removeMaterial = (i: number) => onChange({ ...cost, materials: materials.filter((_, idx) => idx !== i) });
  const addMaterial = () => onChange({ ...cost, materials: [...materials, { material: '', amount: 1 }] });

  // If value is null/undefined/empty string, show a simple add button
  if (value == null || value === '') {
    return (
      <div className="prop-cost-empty">
        <span className="prop-label">{label}</span>
        <button className="prop-add" onClick={() => onChange({ currencies: [], materials: [] })}>+ 设置{label}</button>
      </div>
    );
  }

  // For non-standard structures (like extract_return with mode/downgrade_levels), fall back to KV
  const hasMode = 'mode' in cost || 'downgrade_levels' in cost;
  if (hasMode && !cost.currencies && !cost.materials) {
    return (
      <div className="prop-cost-section">
        <span className="prop-cost-label">{label}</span>
        <div className="prop-cost-fields">
          {Object.entries(cost).map(([key, val]) => (
            <PropRow key={key} label={key}>
              <input type="text" value={String(val ?? '')} onChange={e => {
                const v = e.target.value;
                const parsed = v === '' ? '' : isNaN(Number(v)) ? v : Number(v);
                onChange({ ...cost, [key]: parsed });
              }} />
            </PropRow>
          ))}
          <button className="prop-add" onClick={() => onChange({ ...cost, '': '' })}>+ 添加字段</button>
        </div>
      </div>
    );
  }

  return (
    <div className="prop-cost-section">
      <span className="prop-cost-label">{label}</span>
      {/* Currencies */}
      {currencies.length > 0 && <div className="prop-cost-group">
        <span className="prop-cost-group-title">货币</span>
        {currencies.map((c, i) => (
          <div className="prop-cost-entry" key={i}>
            <div className="prop-cost-entry-head">
              <span>{textValue(c.provider, 'vault')}</span>
              <button className="prop-kv-del" onClick={() => removeCurrency(i)}>×</button>
            </div>
            <PropRow label="provider"><input type="text" value={textValue(c.provider)} onChange={e => setCurrency(i, 'provider', e.target.value)} /></PropRow>
            <PropRow label="currency_id"><input type="text" value={textValue(c.currency_id)} onChange={e => setCurrency(i, 'currency_id', e.target.value)} /></PropRow>
            <PropRow label="base_cost"><input type="number" value={textValue(c.base_cost)} onChange={e => setCurrency(i, 'base_cost', Number(e.target.value))} /></PropRow>
            <PropRow label="cost_formula"><input type="text" value={textValue(c.cost_formula)} onChange={e => setCurrency(i, 'cost_formula', e.target.value)} /></PropRow>
            <PropRow label="display_name"><input type="text" value={textValue(c.display_name)} onChange={e => setCurrency(i, 'display_name', e.target.value)} /></PropRow>
          </div>
        ))}
      </div>}
      {/* Materials */}
      {materials.length > 0 && <div className="prop-cost-group">
        <span className="prop-cost-group-title">材料</span>
        {materials.map((m, i) => (
          <div className="prop-cost-entry" key={i}>
            <div className="prop-cost-entry-head">
              <span>{textValue(m.material, '未设置')}</span>
              <button className="prop-kv-del" onClick={() => removeMaterial(i)}>×</button>
            </div>
            <PropRow label="material"><input type="text" value={textValue(m.material)} onChange={e => setMaterial(i, 'material', e.target.value)} /></PropRow>
            <PropRow label="amount"><input type="number" value={textValue(m.amount)} onChange={e => setMaterial(i, 'amount', Number(e.target.value))} /></PropRow>
            {m.display_name !== undefined && <PropRow label="display_name"><input type="text" value={textValue(m.display_name)} onChange={e => setMaterial(i, 'display_name', e.target.value)} /></PropRow>}
          </div>
        ))}
      </div>}
      <div className="prop-cost-actions">
        <button className="prop-add" onClick={addCurrency}>+ 货币</button>
        <button className="prop-add" onClick={addMaterial}>+ 材料</button>
      </div>
    </div>
  );
}

/* ─── Level Sub-Section (KV for variables/attributes) ─── */
function LevelSubSection({ label, value, onChange }: { label: string; value: unknown; onChange: (v: unknown) => void }) {
  const entries = mapEntries(value);
  return (
    <div className="prop-level-subsection">
      <span className="prop-cost-group-title">{label}</span>
      <KvTable entries={entries} onChange={newEntries => {
        const obj: AnyMap = {};
        newEntries.forEach(e => { if (e.key) obj[e.key] = e.value; });
        onChange(obj);
      }} />
    </div>
  );
}

/* ─── Actions Editor ─── */
type ActionEntry = { type: string; params: Record<string, unknown> };

function ActionsEditor({ actions, onChange, actionTypes, mode }: { actions: ActionEntry[]; onChange: (actions: ActionEntry[]) => void; actionTypes: string[]; mode: 'name' | 'lore' }) {
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
      {actions.map((action, i) => (
        <div className="prop-action-item" key={i}>
          <div className="prop-action-head">
            <span className="prop-action-grip">≡</span>
            <select value={action.type} onChange={e => update(i, { type: e.target.value, params: {} })}>
              {actionTypes.map(t => <option key={t} value={t}>{t}</option>)}
              {!actionTypes.includes(action.type) && action.type && <option value={action.type}>{action.type}</option>}
            </select>
            <span className="prop-action-controls">
              <button onClick={() => moveUp(i)} disabled={i === 0} aria-label="上移">↑</button>
              <button onClick={() => moveDown(i)} disabled={i === actions.length - 1} aria-label="下移">↓</button>
              <button className="prop-action-del" onClick={() => remove(i)} aria-label="删除">×</button>
            </span>
          </div>
          <div className="prop-action-params">
            {mode === 'name' && (
              <>
                <input type="text" value={textValue(action.params.value)} onChange={e => updateParam(i, 'value', e.target.value)} placeholder="value" />
                {action.type === 'regex_replace' && (
                  <>
                    <input type="text" value={textValue(action.params.regex_pattern)} onChange={e => updateParam(i, 'regex_pattern', e.target.value)} placeholder="regex_pattern" />
                    <input type="text" value={textValue(action.params.replacement)} onChange={e => updateParam(i, 'replacement', e.target.value)} placeholder="replacement" />
                  </>
                )}
              </>
            )}
            {mode === 'lore' && (
              <>
                <textarea rows={2} value={asStringList(action.params.content).join('\n')} onChange={e => updateParam(i, 'content', e.target.value.split('\n'))} placeholder="content (每行一个)" />
                {(action.type.includes('search') || action.type.includes('insert') || action.type.includes('anchor')) && (
                  <>
                    <input type="text" value={textValue(action.params.target_pattern)} onChange={e => updateParam(i, 'target_pattern', e.target.value)} placeholder="target_pattern" />
                    <input type="text" value={textValue(action.params.anchor)} onChange={e => updateParam(i, 'anchor', e.target.value)} placeholder="anchor" />
                  </>
                )}
              </>
            )}
          </div>
        </div>
      ))}
      <button className="prop-add" onClick={add}>+ 添加动作</button>
    </div>
  );
}

/* ─── Parse helpers ─── */
function parseNameActions(data: AnyMap): ActionEntry[] {
  return asList(data.name_actions).map(raw => {
    const r = asRecord(raw);
    return { type: textValue(r.type), params: { value: r.value, regex_pattern: r.regex_pattern, replacement: r.replacement } };
  });
}

function parseLoreActions(data: AnyMap): ActionEntry[] {
  return asList(data.lore_actions).map(raw => {
    const r = asRecord(raw);
    return { type: textValue(r.type), params: { content: r.content, target_pattern: r.target_pattern, anchor: r.anchor } };
  });
}

function serializeActions(actions: ActionEntry[]): unknown[] {
  return actions.map(a => {
    const entry: AnyMap = { type: a.type };
    for (const [k, v] of Object.entries(a.params)) {
      if (v !== undefined && v !== '' && !(Array.isArray(v) && v.length === 0)) entry[k] = v;
    }
    return entry;
  });
}

/* ─── Preview Pane ─── */
function PreviewPane({ preview, kind, previewLevel, setPreviewLevel }: { preview: ItemPreviewResult | null; kind: string; previewLevel: number; setPreviewLevel: (n: number) => void }) {
  const source = firstItemSource(preview?.match?.item_sources ?? (preview as any)?.item_sources);
  const material = materialFromItemSource(source || preview?.material);
  const urls = materialUrls(material);
  const levels = preview?.levels ?? [];
  const [imgFailed, setImgFailed] = useState(false);

  // Reset on material change
  useEffect(() => { setImgFailed(false); }, [material]);

  return (
    <div className="ie-preview" role="complementary" aria-label="物品预览">
      <div className="ie-preview-icon">
        {urls.length > 0 && !imgFailed ? (
          <img src={urls[0]} alt={material || '物品图标'} onError={e => { const t = e.currentTarget; const next = urls[urls.indexOf(t.src) + 1]; if (next) t.src = next; else setImgFailed(true); }} />
        ) : (
          <span className="ie-preview-fallback">{materialShortName(material) || '?'}</span>
        )}
      </div>

      <div className="ie-preview-meta">
        <span className="ie-preview-kind">{kind === 'gem' ? '宝石' : kind === 'socket' ? '插槽物品' : '物品'}</span>
        {preview?.id && <code className="ie-preview-id">{preview.id}</code>}
        <span className="ie-preview-source">{displaySource(source || material)}</span>
      </div>

      {levels.length > 1 && (
        <div className="ie-level-rail">
          {levels.map(lv => (
            <button key={lv} className={lv === previewLevel ? 'active' : ''} onClick={() => setPreviewLevel(lv)}>Lv.{lv}</button>
          ))}
        </div>
      )}

      <div className="ie-tooltip">
        {preview?.displayName && <div className="ie-tooltip-name"><MiniText value={preview.displayName} /></div>}
        {(preview?.lore ?? []).map((line, i) => (
          <div className="ie-tooltip-line" key={i}><MiniText value={line} /></div>
        ))}
        {!preview?.displayName && !preview?.lore?.length && <span className="ie-tooltip-empty">暂无预览</span>}
      </div>
    </div>
  );
}

/* ─── Gem Property Panel ─── */
function GemPanel({ data, setField, preview, actionTypesResult }: { data: AnyMap; setField: (path: string[], value: unknown) => void; preview: ItemPreviewResult | null; actionTypesResult: ActionTypesResult | null }) {
  // Variables/EA/Skills: read from data if present at top level, otherwise from preview effects
  const hasTopLevelVars = data.variables != null && Object.keys(asRecord(data.variables)).length > 0;
  const varsFromEffects = effectsByType(preview, 'variables');
  const varsData = hasTopLevelVars
    ? mapEntries(data.variables)
    : varsFromEffects.length > 0
      ? Object.entries(varsFromEffects.reduce((acc, e) => ({ ...acc, ...asRecord(asRecord(e.payload).variables) }), {} as AnyMap)).map(([key, value]) => ({ key, value }))
      : mapEntries(data.variables);

  const hasTopLevelEa = data.ea_attributes != null && Object.keys(asRecord(data.ea_attributes)).length > 0;
  const eaFromEffects = effectsByType(preview, 'ea_attribute');
  const eaData = hasTopLevelEa
    ? mapEntries(data.ea_attributes)
    : eaFromEffects.length > 0
      ? Object.entries(eaFromEffects.reduce((acc, e) => ({ ...acc, ...asRecord(e.attributes) }), {} as AnyMap)).map(([key, value]) => ({ key, value }))
      : mapEntries(data.ea_attributes);

  const hasTopLevelSkills = data.skills != null && asStringList(data.skills).length > 0;
  const skillsFromEffects = effectsByType(preview, 'es_skill');
  const skillsData = hasTopLevelSkills
    ? asStringList(data.skills)
    : skillsFromEffects.length > 0
      ? skillsFromEffects.flatMap(e => asStringList(asRecord(e.payload).es_skills))
      : asStringList(data.skills);

  const nameActions = parseNameActions(data);
  const loreActions = parseLoreActions(data);

  // Upgrade levels: can be an array or a map keyed by level number
  const upgradeRaw = asRecord(data.upgrade).levels;
  const levelEntries: Array<{ levelKey: string; data: AnyMap }> = useMemo(() => {
    if (Array.isArray(upgradeRaw)) {
      return upgradeRaw.map((entry, i) => ({ levelKey: String(i + 2), data: asRecord(entry) }));
    }
    if (upgradeRaw && typeof upgradeRaw === 'object' && !Array.isArray(upgradeRaw)) {
      return Object.entries(upgradeRaw as Record<string, unknown>)
        .map(([key, val]) => ({ levelKey: key, data: asRecord(val) }))
        .sort((a, b) => Number(a.levelKey) - Number(b.levelKey));
    }
    return [];
  }, [upgradeRaw]);

  const [expandedLevels, setExpandedLevels] = useState<Set<string>>(new Set());
  const toggleLevel = (key: string) => setExpandedLevels(prev => { const s = new Set(prev); s.has(key) ? s.delete(key) : s.add(key); return s; });

  return (
    <div className="ie-props">
      {/* 基础 */}
      <SectionHead title="基础" />
      <PropRow label="ID"><input type="text" value={textValue(data.id)} onChange={e => setField(['id'], e.target.value)} /></PropRow>
      <PropRow label="显示名称"><input type="text" value={textValue(data.display_name)} onChange={e => setField(['display_name'], e.target.value)} /></PropRow>
      <PropRow label="宝石类型"><input type="text" value={textValue(data.gem_type)} onChange={e => setField(['gem_type'], e.target.value)} /></PropRow>
      <PropRow label="等级"><input type="number" value={textValue(data.level)} onChange={e => setField(['level'], Number(e.target.value))} /></PropRow>
      <PropRow label="物品来源" wide><textarea rows={2} value={asStringList(data.item_sources).join('\n')} onChange={e => setField(['item_sources'], e.target.value.split('\n').filter(Boolean))} placeholder="每行一个" /></PropRow>
      <PropRow label="Model Data"><input type="number" value={textValue(data.custom_model_data)} onChange={e => setField(['custom_model_data'], e.target.value ? Number(e.target.value) : undefined)} /></PropRow>
      <PropRow label="兼容插槽" wide><textarea rows={2} value={asStringList(data.socket_compatibility).join('\n')} onChange={e => setField(['socket_compatibility'], e.target.value.split('\n').filter(Boolean))} placeholder="每行一个" /></PropRow>

      {/* Variables */}
      <SectionHead title="Variables" count={varsData.length} actions={<button className="prop-add-inline" onClick={() => setField(['variables'], { ...asRecord(data.variables), '': 0 })}>+</button>} />
      <KvTable entries={varsData} onChange={entries => { const obj: AnyMap = {}; entries.forEach(e => { if (e.key) obj[e.key] = e.value; }); setField(['variables'], obj); }} />

      {/* EA Attributes */}
      <SectionHead title="EA Attributes" count={eaData.length} actions={<button className="prop-add-inline" onClick={() => setField(['ea_attributes'], { ...asRecord(data.ea_attributes), '': 0 })}>+</button>} />
      <KvTable entries={eaData} onChange={entries => { const obj: AnyMap = {}; entries.forEach(e => { if (e.key) obj[e.key] = e.value; }); setField(['ea_attributes'], obj); }} />

      {/* Skills */}
      <SectionHead title="Skills" count={skillsData.length} />
      <StringListEditor items={skillsData} onChange={items => setField(['skills'], items)} placeholder="技能 ID" />

      {/* Name Actions */}
      <SectionHead title="Name Actions" count={nameActions.length} />
      <ActionsEditor actions={nameActions} onChange={a => setField(['name_actions'], serializeActions(a))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" />

      {/* Lore Actions */}
      <SectionHead title="Lore Actions" count={loreActions.length} />
      <ActionsEditor actions={loreActions} onChange={a => setField(['lore_actions'], serializeActions(a))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" />

      {/* 费用 */}
      <SectionHead title="费用" />
      <CostEditor label="镶嵌费用" value={data.inlay_cost} onChange={v => setField(['inlay_cost'], v)} />
      <CostEditor label="拆卸费用" value={data.extract_cost} onChange={v => setField(['extract_cost'], v)} />
      <CostEditor label="拆卸返还" value={data.extract_return} onChange={v => setField(['extract_return'], v)} />

      {/* 升级等级 */}
      <SectionHead title="升级等级" count={levelEntries.length} actions={<button className="prop-add-inline" onClick={() => {
        const nextKey = String(levelEntries.length > 0 ? Math.max(...levelEntries.map(e => Number(e.levelKey))) + 1 : 2);
        const raw = asRecord(data.upgrade);
        const levelsObj = (raw.levels && typeof raw.levels === 'object' && !Array.isArray(raw.levels)) ? { ...raw.levels as Record<string, unknown> } : {};
        (levelsObj as any)[nextKey] = { display_name: '', success_rate: 100 };
        setField(['upgrade', 'levels'], levelsObj);
      }}>+</button>} />
      <div className="prop-levels" role="list">
        {levelEntries.map(({ levelKey, data: lv }) => {
          const expanded = expandedLevels.has(levelKey);
          return (
            <div className={`prop-level-item${expanded ? ' expanded' : ''}`} key={levelKey} role="listitem">
              <button type="button" className="prop-level-head" onClick={() => toggleLevel(levelKey)} aria-expanded={expanded} aria-controls={`level-body-${levelKey}`}>
                <span className="prop-level-badge">Lv.{levelKey}</span>
                <span className="prop-level-summary">{textValue(lv.display_name) || '未命名'}</span>
                <span className="prop-level-rate">{textValue(lv.success_rate, '100')}%</span>
                <span className="prop-kv-del" role="button" tabIndex={0} onClick={e => { e.stopPropagation(); const raw = asRecord(asRecord(data.upgrade).levels); const copy = { ...raw }; delete copy[levelKey]; setField(['upgrade', 'levels'], copy); }} onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); const raw = asRecord(asRecord(data.upgrade).levels); const copy = { ...raw }; delete copy[levelKey]; setField(['upgrade', 'levels'], copy); } }} aria-label={`删除等级 ${levelKey}`}>×</span>
              </button>
              {expanded && (
                <div className="prop-level-body" id={`level-body-${levelKey}`}>
                  <PropRow label="显示名称"><input type="text" value={textValue(lv.display_name)} onChange={e => setField(['upgrade', 'levels', levelKey, 'display_name'], e.target.value)} /></PropRow>
                  <PropRow label="成功率"><input type="number" value={textValue(lv.success_rate)} onChange={e => setField(['upgrade', 'levels', levelKey, 'success_rate'], Number(e.target.value))} /></PropRow>
                  <PropRow label="失败惩罚"><input type="text" value={textValue(lv.failure_penalty)} onChange={e => setField(['upgrade', 'levels', levelKey, 'failure_penalty'], e.target.value)} /></PropRow>
                  <LevelSubSection label="Variables" value={lv.variables} onChange={v => setField(['upgrade', 'levels', levelKey, 'variables'], v)} />
                  <LevelSubSection label="EA Attributes" value={lv.ea_attributes} onChange={v => setField(['upgrade', 'levels', levelKey, 'ea_attributes'], v)} />
                  <PropRow label="Skills" wide><StringListEditor items={asStringList(lv.skills)} onChange={items => setField(['upgrade', 'levels', levelKey, 'skills'], items)} placeholder="技能 ID" /></PropRow>
                  <SectionHead title="Name Actions" count={parseNameActions(asRecord(lv)).length} />
                  <ActionsEditor actions={parseNameActions(asRecord(lv))} onChange={a => setField(['upgrade', 'levels', levelKey, 'name_actions'], serializeActions(a))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" />
                  <SectionHead title="Lore Actions" count={parseLoreActions(asRecord(lv)).length} />
                  <ActionsEditor actions={parseLoreActions(asRecord(lv))} onChange={a => setField(['upgrade', 'levels', levelKey, 'lore_actions'], serializeActions(a))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" />
                  <CostEditor label="升级费用" value={lv.economy} onChange={v => setField(['upgrade', 'levels', levelKey, 'economy'], v)} />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ─── Socket Property Panel ─── */
function SocketPanel({ data, setField, preview, actionTypesResult }: { data: AnyMap; setField: (path: string[], value: unknown) => void; preview: ItemPreviewResult | null; actionTypesResult: ActionTypesResult | null }) {
  const match = asRecord(data.match);
  const slots = asList(data.slots);
  const defaultOpenSlots = asList(data.default_open_slots).map(Number);
  const nameActions = parseNameActions(data);
  const loreActions = parseLoreActions(data);
  const [expandedSlots, setExpandedSlots] = useState<Set<number>>(new Set());
  const toggleSlot = (i: number) => setExpandedSlots(prev => { const s = new Set(prev); s.has(i) ? s.delete(i) : s.add(i); return s; });

  return (
    <div className="ie-props">
      {/* 基础 */}
      <SectionHead title="基础" />
      <PropRow label="ID"><input type="text" value={textValue(data.id)} onChange={e => setField(['id'], e.target.value)} /></PropRow>
      <PropRow label="物品来源" wide><textarea rows={2} value={asStringList(match.item_sources).join('\n')} onChange={e => setField(['match', 'item_sources'], e.target.value.split('\n').filter(Boolean))} placeholder="每行一个" /></PropRow>
      <PropRow label="允许宝石类型" wide><textarea rows={2} value={asStringList(data.allowed_gem_types).join('\n')} onChange={e => setField(['allowed_gem_types'], e.target.value.split('\n').filter(Boolean))} placeholder="每行一个" /></PropRow>
      <PropRow label="同类型上限"><input type="number" value={textValue(data.max_same_type)} onChange={e => setField(['max_same_type'], Number(e.target.value))} /></PropRow>
      <PropRow label="同ID上限"><input type="number" value={textValue(data.max_same_id)} onChange={e => setField(['max_same_id'], Number(e.target.value))} /></PropRow>

      {/* 插槽 */}
      <SectionHead title="插槽" count={slots.length} actions={<button className="prop-add-inline" onClick={() => setField(['slots'], [...slots, { index: slots.length, type: 'universal', display_name: '' }])}>+</button>} />
      <div className="prop-levels" role="list">
        {slots.map((slotRaw, i) => {
          const slot = asRecord(slotRaw);
          const expanded = expandedSlots.has(i);
          return (
            <div className={`prop-level-item${expanded ? ' expanded' : ''}`} key={i} role="listitem">
              <button type="button" className="prop-level-head" onClick={() => toggleSlot(i)} aria-expanded={expanded} aria-controls={`slot-body-${i}`}>
                <span className="prop-level-badge">#{String(slot.index ?? i)}</span>
                <span className="prop-level-summary">{textValue(slot.type, 'universal')}</span>
                <span className="prop-level-rate">{textValue(slot.display_name) || '—'}</span>
                <span className="prop-kv-del" role="button" tabIndex={0} onClick={e => { e.stopPropagation(); const next = [...slots]; next.splice(i, 1); setField(['slots'], next); }} onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); e.stopPropagation(); const next = [...slots]; next.splice(i, 1); setField(['slots'], next); } }} aria-label={`删除插槽 ${i}`}>×</span>
              </button>
              {expanded && (
                <div className="prop-level-body" id={`slot-body-${i}`}>
                  <PropRow label="索引"><input type="number" value={textValue(slot.index, String(i))} onChange={e => { const next = [...slots]; next[i] = { ...slot, index: Number(e.target.value) }; setField(['slots'], next); }} /></PropRow>
                  <PropRow label="类型"><input type="text" value={textValue(slot.type)} onChange={e => { const next = [...slots]; next[i] = { ...slot, type: e.target.value }; setField(['slots'], next); }} /></PropRow>
                  <PropRow label="显示名称"><input type="text" value={textValue(slot.display_name)} onChange={e => { const next = [...slots]; next[i] = { ...slot, display_name: e.target.value }; setField(['slots'], next); }} /></PropRow>
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* 默认开放插槽 */}
      <SectionHead title="默认开放插槽" count={defaultOpenSlots.length} />
      <NumberListEditor items={defaultOpenSlots} onChange={items => setField(['default_open_slots'], items)} />

      {/* Name Actions */}
      <SectionHead title="Name Actions" count={nameActions.length} />
      <ActionsEditor actions={nameActions} onChange={a => setField(['name_actions'], serializeActions(a))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" />

      {/* Lore Actions */}
      <SectionHead title="Lore Actions" count={loreActions.length} />
      <ActionsEditor actions={loreActions} onChange={a => setField(['lore_actions'], serializeActions(a))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" />

      {/* GUI */}
      <SectionHead title="GUI" />
      {data.gui != null && typeof data.gui === 'object' && !Array.isArray(data.gui) ? (
        <div className="prop-cost-section">
          {Object.entries(asRecord(data.gui)).map(([key, val]) => (
            <PropRow key={key} label={key}>
              <input type="text" value={String(val ?? '')} onChange={e => {
                const v = e.target.value;
                const parsed = v === '' ? '' : isNaN(Number(v)) ? v : Number(v);
                setField(['gui', key], parsed);
              }} />
            </PropRow>
          ))}
          <button className="prop-add" onClick={() => setField(['gui'], { ...asRecord(data.gui), '': '' })}>+ 添加字段</button>
        </div>
      ) : (
        <PropRow label="GUI 配置" wide><textarea rows={6} value={JSON.stringify(data.gui ?? null, null, 2)} onChange={e => { try { setField(['gui'], JSON.parse(e.target.value)); } catch {} }} aria-label="GUI 配置 JSON" /></PropRow>
      )}
    </div>
  );
}

/* ─── Generic Property Panel ─── */
function GenericPanel({ data, setField, preview, actionTypesResult }: { data: AnyMap; setField: (path: string[], value: unknown) => void; preview: ItemPreviewResult | null; actionTypesResult: ActionTypesResult | null }) {
  const nameActions = parseNameActions(data);
  const loreActions = parseLoreActions(data);

  return (
    <div className="ie-props">
      <SectionHead title="基础" />
      <PropRow label="ID"><input type="text" value={textValue(data.id)} onChange={e => setField(['id'], e.target.value)} /></PropRow>
      <PropRow label="材质"><input type="text" value={textValue(data.material)} onChange={e => setField(['material'], e.target.value)} /></PropRow>
      <PropRow label="显示名称"><input type="text" value={textValue(data.display_name)} onChange={e => setField(['display_name'], e.target.value)} /></PropRow>
      <PropRow label="Lore" wide><textarea rows={4} value={asStringList(data.lore).join('\n')} onChange={e => setField(['lore'], e.target.value.split('\n'))} placeholder="每行一个" /></PropRow>

      <SectionHead title="Name Actions" count={nameActions.length} />
      <ActionsEditor actions={nameActions} onChange={a => setField(['name_actions'], serializeActions(a))} actionTypes={actionTypesResult?.nameActions ?? []} mode="name" />

      <SectionHead title="Lore Actions" count={loreActions.length} />
      <ActionsEditor actions={loreActions} onChange={a => setField(['lore_actions'], serializeActions(a))} actionTypes={actionTypesResult?.loreActions ?? []} mode="lore" />
    </div>
  );
}

/* ─── Main Export ─── */
export function EmakiGemItemSurface({ module, file, api, childPath, refreshKey = 0, editor, onReload }: Props) {
  const [data, setData] = useState<AnyMap>({});
  const [originalContent, setOriginalContent] = useState('');
  const [preview, setPreview] = useState<ItemPreviewResult | null>(null);
  const [previewLevel, setPreviewLevel] = useState(1);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewSource, setViewSource] = useState(false);
  const [sourceText, setSourceText] = useState('');
  const [actionTypesResult, setActionTypesResult] = useState<ActionTypesResult | null>(null);
  const [dirty, setDirty] = useState(false);

  const filePath = childPath || file.path;
  const kind = useMemo(() => itemKind(filePath, preview), [filePath, preview]);
  const baseName = editor?.baseName ?? DEFAULT_BASE_NAME;
  const baseLore = editor?.baseLore ?? [DEFAULT_BASE_LORE];

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.readItem(module.id, filePath).then(doc => {
      if (cancelled) return;
      setData(doc.data as AnyMap);
      setOriginalContent(doc.content);
      setSourceText(doc.content);
      setDirty(false);
      setLoading(false);
    }).catch(err => {
      if (cancelled) return;
      setError(String(err?.message ?? err));
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [module.id, filePath, refreshKey]);

  useEffect(() => {
    api.actionTypes().then(setActionTypesResult).catch(() => {});
  }, []);

  useEffect(() => {
    if (loading) return;
    const content = viewSource ? sourceText : serializeItemYaml(data);
    const timer = setTimeout(() => {
      api.previewItem(content, previewLevel, baseName, baseLore as string[]).then(setPreview).catch(() => setPreview(null));
    }, 300);
    return () => clearTimeout(timer);
  }, [data, sourceText, viewSource, previewLevel, loading, baseName, baseLore]);

  const setField = (path: string[], value: unknown) => {
    setData(prev => setDeepValue(prev, path, value));
    setDirty(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const content = viewSource ? sourceText : serializeItemYaml(data);
      await api.saveItem(module.id, filePath, content);
      setOriginalContent(content);
      setDirty(false);
    } catch (err: any) {
      setError(err?.message ?? '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const switchToSource = () => {
    if (!viewSource) setSourceText(serializeItemYaml(data));
    setViewSource(!viewSource);
  };

  const handleSourceChange = (text: string) => {
    setSourceText(text);
    setDirty(true);
  };

  useEffect(() => {
    if (!viewSource && sourceText && sourceText !== originalContent) {
      api.previewItem(sourceText, previewLevel, baseName, baseLore as string[]).then(p => setPreview(p)).catch(() => {});
    }
  }, [viewSource]);

  if (loading) return <div className="ie-surface"><div className="ie-loading"><div className="ie-skeleton" aria-label="加载中"><div className="ie-skeleton-line" style={{ width: '60%' }} /><div className="ie-skeleton-line" style={{ width: '80%' }} /><div className="ie-skeleton-line" style={{ width: '45%' }} /><div className="ie-skeleton-line" style={{ width: '70%' }} /></div></div></div>;
  if (error && !data) return <div className="ie-surface"><div className="ie-error" role="alert">{error}</div></div>;

  return (
    <div className="ie-surface" data-dirty={dirty || undefined}>
      <div className="ie-header">
        <div className="ie-header-left">
          <h1 className="ie-title">{editor?.title ?? file.title ?? '物品编辑器'}</h1>
          {dirty && <span className="ie-dirty-badge">未保存</span>}
        </div>
        <div className="ie-header-actions">
          {onReload && <button className="ie-btn" onClick={onReload}>刷新</button>}
          <button className="ie-btn" onClick={switchToSource}>{viewSource ? '可视化' : '源码'}</button>
          <button className={`ie-btn ie-btn--primary${dirty ? ' ie-btn--ready' : ''}`} onClick={handleSave} disabled={saving}>
            {saving ? '保存中...' : '保存'}
          </button>
        </div>
      </div>

      {error && <div className="ie-error">{error}</div>}

      {viewSource ? (
        <div className="ie-source-wrap">
          <textarea className="ie-source" value={sourceText} onChange={e => handleSourceChange(e.target.value)} rows={28} spellCheck={false} />
        </div>
      ) : (
        <div className="ie-workbench">
          <PreviewPane preview={preview} kind={kind} previewLevel={previewLevel} setPreviewLevel={setPreviewLevel} />
          <div className="ie-props-scroll">
            {kind === 'gem' && <GemPanel data={data} setField={setField} preview={preview} actionTypesResult={actionTypesResult} />}
            {kind === 'socket' && <SocketPanel data={data} setField={setField} preview={preview} actionTypesResult={actionTypesResult} />}
            {kind === 'generic' && <GenericPanel data={data} setField={setField} preview={preview} actionTypesResult={actionTypesResult} />}
          </div>
        </div>
      )}
    </div>
  );
}
