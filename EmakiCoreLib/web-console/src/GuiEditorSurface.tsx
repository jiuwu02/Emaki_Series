import { forwardRef, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ApiClient } from './api';
import type { GuiSlotDefinition, GuiTemplateData, WebRegistryFile, WebRegistryModule } from './types';
import { buildOccupancy, clampRows, guiColumns, guiField, guiSlotCount, guiTypeOptions, loreLines, materialShortName, materialUrls, normalizeGuiType, parseSlotList, renderMiniMessageParts, serializeGuiYaml, subscribeTextureBases, supportsRows, textValue } from './guiEditor';
import { ActionGroup, Button, InlineError, InspectorSection, ToggleChip } from './components';
import { t } from './i18n';
import { MATERIAL_CATEGORIES, MINECRAFT_MATERIAL_VERSION, type MaterialCategory, materialCategory, searchMaterials } from './minecraftMaterials';

type Props = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  api: ApiClient;
  childPath?: string;
  refreshKey?: number;
  editor?: import('./types').WebEditorDescriptor;
};

const INSPECTOR_MIN = 300;
const INSPECTOR_MAX = 620;
const INSPECTOR_STEP = 16;

export function GuiEditorSurface({ module, file, api, childPath, refreshKey = 0, editor }: Props) {
  const [data, setData] = useState<GuiTemplateData | null>(null);
  const [originalText, setOriginalText] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<number[]>([]);
  const [hovered, setHovered] = useState<number | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState<{ x: number; y: number } | null>(null);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<MaterialCategory | '全部'>('全部');
  const [failedImages, setFailedImages] = useState<Record<string, boolean>>({});
  const [, refreshTextureOrder] = useState(0);
  const [mode, setMode] = useState<'preview' | 'source'>('preview');
  const [reloadConfirmOpen, setReloadConfirmOpen] = useState(false);
  const [inspectorWidth, setInspectorWidth] = useState(() => {
    const saved = localStorage.getItem('emaki-gui-inspector-width');
    return saved ? clampInspectorWidth(Number(saved)) : 380;
  });
  const [resizingInspector, setResizingInspector] = useState(false);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const inspectorResizeStartX = useRef(0);
  const inspectorResizeStartW = useRef(380);
  const latestInspectorWidth = useRef(inspectorWidth);

  const path = childPath ?? '';

  useEffect(() => {
    latestInspectorWidth.current = inspectorWidth;
  }, [inspectorWidth]);

  useEffect(() => subscribeTextureBases(() => {
    setFailedImages({});
    refreshTextureOrder((version) => version + 1);
  }), []);

  useEffect(() => {
    if (!resizingInspector) return;
    const onMove = (event: MouseEvent) => {
      const next = clampInspectorWidth(inspectorResizeStartW.current - (event.clientX - inspectorResizeStartX.current));
      latestInspectorWidth.current = next;
      setInspectorWidth(next);
    };
    const onUp = () => {
      setResizingInspector(false);
      localStorage.setItem('emaki-gui-inspector-width', String(latestInspectorWidth.current));
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    return () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [resizingInspector]);

  const setCommittedInspectorWidth = useCallback((next: number) => {
    const clamped = clampInspectorWidth(next);
    latestInspectorWidth.current = clamped;
    setInspectorWidth(clamped);
    localStorage.setItem('emaki-gui-inspector-width', String(clamped));
  }, []);

  const startInspectorResize = useCallback((event: React.MouseEvent) => {
    event.preventDefault();
    inspectorResizeStartX.current = event.clientX;
    inspectorResizeStartW.current = inspectorWidth;
    latestInspectorWidth.current = inspectorWidth;
    setResizingInspector(true);
  }, [inspectorWidth]);

  const handleInspectorResizeKeyDown = useCallback((event: React.KeyboardEvent) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    if (event.key === 'Home') setCommittedInspectorWidth(INSPECTOR_MIN);
    else if (event.key === 'End') setCommittedInspectorWidth(INSPECTOR_MAX);
    else setCommittedInspectorWidth(inspectorWidth + (event.key === 'ArrowLeft' ? INSPECTOR_STEP : -INSPECTOR_STEP));
  }, [inspectorWidth, setCommittedInspectorWidth]);

  const handleSlotMouseMove = useCallback((event: React.MouseEvent) => {
    if (tooltipRef.current) {
      const { clientX, clientY } = event;
      const pos = nextTooltipPosition(clientX, clientY);
      tooltipRef.current.style.left = `${pos.x}px`;
      tooltipRef.current.style.top = `${pos.y}px`;
    }
  }, []);

  useEffect(() => {
    void reloadGui();
  }, [api, module.id, path, refreshKey]);

  useEffect(() => {
    if (!reloadConfirmOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setReloadConfirmOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [reloadConfirmOpen]);

  async function reloadGui() {
    if (!path) return;
    setLoading(true);
    setError('');
    try {
      const doc = await api.readGui(module.id, path);
      const normalizedData = doc.data ?? {};
      setData(normalizedData);
      setOriginalText(serializeGuiYaml(normalizedData));
      setSelected([]);
      setHovered(null);
      setTooltipPosition(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.gui.loadFailed'));
    } finally {
      setLoading(false);
    }
  }

  const guiType = normalizeGuiType(data ?? undefined);
  const rows = clampRows(data?.rows);
  const rowSupported = supportsRows(guiType);
  const slotCount = guiSlotCount(data ?? undefined);
  const columns = guiColumns(data ?? undefined);
  const occupancy = useMemo(() => data ? buildOccupancy(data) : [], [data]);
  const selectedKey = selected.length === 1 ? occupancy.find((cell) => cell.index === selected[0])?.key ?? null : null;
  const selectedSlot = selectedKey && data?.slots ? data.slots[selectedKey] ?? null : null;
  const draftText = data ? serializeGuiYaml(data) : '';
  const dirty = data != null && draftText !== originalText;
  const materialResults = useMemo(() => searchMaterials(query, category), [query, category]);
  const visibleMaterials = materialResults.slice(0, 80);

  function requestReload() {
    if (dirty) {
      setReloadConfirmOpen(true);
      return;
    }
    void reloadGui();
  }

  function confirmReload() {
    setReloadConfirmOpen(false);
    void reloadGui();
  }

  async function save() {
    if (!data || !path || !dirty) return;
    setSaving(true);
    setError('');
    try {
      const content = serializeGuiYaml(data);
      await api.saveGui(module.id, path, content);
      setOriginalText(content);
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.gui.saveFailed'));
    } finally {
      setSaving(false);
    }
  }

  function updateData(mutator: (draft: GuiTemplateData) => GuiTemplateData) {
    setData((current) => mutator({ ...(current ?? {}), slots: { ...((current ?? {}).slots ?? {}) } }));
  }

  function updateSlot(key: string, patch: Partial<GuiSlotDefinition>) {
    updateData((draft) => ({ ...draft, slots: { ...(draft.slots ?? {}), [key]: { ...((draft.slots ?? {})[key] ?? {}), ...patch } } }));
  }

  function createSlot(index: number, material = 'STONE') {
    const base = `slot_${index}`;
    const slots = data?.slots ?? {};
    let key = base;
    let i = 2;
    while (slots[key]) key = `${base}_${i++}`;
    updateData((draft) => ({ ...draft, slots: { ...(draft.slots ?? {}), [key]: { slots: [index], item: material, display_name: `<gray>${material.toLowerCase()}</gray>`, lore: [] } } }));
    setSelected([index]);
  }

  function assignMaterial(material: string) {
    if (!selected.length) return;
    if (selectedKey) {
      updateSlot(selectedKey, { item: material });
      return;
    }
    createSlot(selected[0], material);
  }

  function selectSlot(index: number, additive = false) {
    if (additive) setSelected((current) => current.includes(index) ? current.filter((n) => n !== index) : [...current, index]);
    else setSelected([index]);
  }

  function handleSlotKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, index: number) {
    const navigation: Record<string, number> = {
      ArrowLeft: index - 1,
      ArrowRight: index + 1,
      ArrowUp: index - columns,
      ArrowDown: index + columns,
      Home: 0,
      End: Math.max(0, occupancy.length - 1),
    };
    if (event.key === ' ' || event.key === 'Enter') {
      event.preventDefault();
      selectSlot(index, event.ctrlKey || event.metaKey || event.shiftKey);
      return;
    }
    if (!(event.key in navigation)) return;
    event.preventDefault();
    const nextIndex = Math.max(0, Math.min(occupancy.length - 1, navigation[event.key]));
    document.querySelector<HTMLButtonElement>(`[data-gui-slot="${nextIndex}"]`)?.focus();
    if (event.shiftKey) selectSlot(nextIndex, true);
  }

  if (!path) return <section className="config-surface empty" role="status">{t('core.gui.selectFile')}</section>;
  if (loading) return <section className="config-surface gui-surface"><div className="gui-loading" role="status">{t('core.gui.loading')}</div></section>;
  if (!data) return <section className="config-surface empty"><InlineError>{error || t('core.gui.unavailable')}</InlineError><Button size="sm" onClick={() => void reloadGui()}>{t('core.action.retry')}</Button></section>;

  const hoveredCell = hovered == null ? null : occupancy.find((cell) => cell.index === hovered);

  return <section className="config-surface gui-surface" data-dirty={dirty ? 'true' : undefined}>
    <div className="surface-head gui-head">
      <div>
        <h2>{file.title}</h2>
        <p>{module.id}/{path} · {guiType}{rowSupported ? ` · ${t('core.gui.metaRows', { count: rows })}` : ''} · {t('core.gui.metaSlots', { count: slotCount })} · {t('core.gui.metaSlotDefinitions', { count: Object.keys(data.slots ?? {}).length })} {dirty && <span className="dirty-inline">{t('core.item.unsaved')}</span>}</p>
      </div>
      <ActionGroup>
        <Button onClick={() => setMode(mode === 'preview' ? 'source' : 'preview')}>{mode === 'preview' ? t('core.item.source') : t('core.gui.preview')}</Button>
        <Button onClick={requestReload} disabled={saving || loading}>{t('core.gui.reload')}</Button>
        <Button variant="primary" ready={dirty} onClick={() => void save()} disabled={!dirty || saving}>{saving ? t('core.script.saving') : t('core.gui.save')}</Button>
      </ActionGroup>
    </div>
    {reloadConfirmOpen && <ReloadConfirmDialog fileTitle={file.title} filePath={`${module.id}/${path}`} onConfirm={confirmReload} onCancel={() => setReloadConfirmOpen(false)} />}
    {error && <InlineError className="gui-error">{error}</InlineError>}
    {mode === 'source' ? <pre className="gui-source" aria-label={t('core.gui.sourcePreview')}>{draftText}</pre> : <div className={`gui-workbench ${resizingInspector ? 'is-resizing' : ''}`} style={{ '--gui-inspector-width': `${inspectorWidth}px` } as React.CSSProperties}>
      <div className="gui-preview-pane">
        <div className="minecraft-window">
          <div className="minecraft-titlebar"><MiniText value={data.title ?? 'GUI'} /></div>
          <p className="slot-grid-help" id="gui-slot-help">{t('core.gui.gridHelp')}</p>
          <div className="minecraft-grid" role="grid" aria-label={t('core.gui.gridAria', { title: file.title })} aria-describedby="gui-slot-help" aria-multiselectable="true" data-gui-type={guiType} style={{ gridTemplateColumns: `repeat(${columns}, var(--mc-slot))` }}>
            {occupancy.map((cell) => <button
              key={cell.index}
              className={`minecraft-slot ${cell.key ? 'occupied' : ''} ${selected.includes(cell.index) ? 'selected' : ''} ${cell.conflicts.length ? 'conflict' : ''}`}
              role="gridcell"
              data-gui-slot={cell.index}
              aria-label={t('core.gui.slotAria', { index: cell.index, suffix: cell.key ? `，${cell.key}` : t('core.gui.slotEmpty') })}
              aria-selected={selected.includes(cell.index)}
              onClick={(event) => selectSlot(cell.index, event.ctrlKey || event.metaKey || event.shiftKey)}
              onKeyDown={(event) => handleSlotKeyDown(event, cell.index)}
              onMouseEnter={(event) => { setHovered(cell.index); setTooltipPosition(nextTooltipPosition(event.clientX, event.clientY)); }}
              onMouseMove={handleSlotMouseMove}
              onMouseLeave={() => { setHovered(null); setTooltipPosition(null); }}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => { event.preventDefault(); const material = event.dataTransfer.getData('text/material'); if (material) { setSelected([cell.index]); cell.key ? updateSlot(cell.key, { item: material }) : createSlot(cell.index, material); } }}
            >
              <SlotIcon slot={cell.slot} failed={failedImages} setFailed={setFailedImages} />
              <span className="slot-index">{cell.index}</span>
            </button>)}
          </div>
          {hoveredCell?.slot && tooltipPosition && <MinecraftTooltip ref={tooltipRef} slot={hoveredCell.slot} slotKey={hoveredCell.key ?? ''} position={tooltipPosition} />}
        </div>
      </div>
      <div
        className={`gui-inspector-resize ${resizingInspector ? 'active' : ''}`}
        role="separator"
        tabIndex={0}
        aria-orientation="vertical"
        aria-label={t('core.gui.resizeAria')}
        aria-valuemin={INSPECTOR_MIN}
        aria-valuemax={INSPECTOR_MAX}
        aria-valuenow={inspectorWidth}
        onMouseDown={startInspectorResize}
        onKeyDown={handleInspectorResizeKeyDown}
      />
      <aside className="gui-inspector">
        <InspectorSection title={t('core.gui.container')}>
          <GuiLabel editor={editor} path="id" fallback="ID"><input value={textValue(data.id)} onChange={(e) => updateData((draft) => ({ ...draft, id: e.target.value }))} /></GuiLabel>
          <GuiLabel editor={editor} path="gui_type" fallback={t('core.gui.type')}><select value={guiType} onChange={(e) => updateData((draft) => ({ ...draft, gui_type: e.target.value, inventory_type: undefined, rows: supportsRows(e.target.value) ? clampRows(draft.rows) : undefined }))}>{guiTypeOptions().map((type) => <option key={type} value={type}>{type}</option>)}</select></GuiLabel>
          <GuiLabel editor={editor} path="title" fallback={t('core.gui.title')}><input value={textValue(data.title)} onChange={(e) => updateData((draft) => ({ ...draft, title: e.target.value }))} /></GuiLabel>
          {rowSupported && <GuiLabel editor={editor} path="rows" fallback={t('core.gui.rows')}><input type="number" min={1} max={6} value={rows} onChange={(e) => updateData((draft) => ({ ...draft, rows: clampRows(e.target.value) }))} /></GuiLabel>}
        </InspectorSection>
        <InspectorSection title={t('core.gui.slotInspector', { value: selected.length ? selected.join(', ') : t('core.gui.noSlotSelected') })}>
          {selectedSlot && selectedKey ? <SlotInspector slotKey={selectedKey} slot={selectedSlot} editor={editor} updateSlot={updateSlot} removeSlot={() => updateData((draft) => { const next = { ...(draft.slots ?? {}) }; delete next[selectedKey]; return { ...draft, slots: next }; })} /> : selected.length ? <Button variant="soft" fullWidth onClick={() => createSlot(selected[0])}>{t('core.gui.createSlot', { slot: selected[0] })}</Button> : <p className="muted-copy">{t('core.gui.slotHint')}</p>}
        </InspectorSection>
        <InspectorSection className="material-palette" title={t('core.gui.materialSource')} meta={`MC ${MINECRAFT_MATERIAL_VERSION} · ${t('core.config.groupItems', { count: materialResults.length })}`}>
          <input aria-label={t('core.gui.materialSearch')} placeholder={t('core.gui.materialPlaceholder')} value={query} onChange={(e) => setQuery(e.target.value)} />
          <div className="material-tabs">
            {(['全部', ...MATERIAL_CATEGORIES] as const).map((entry) => <button key={entry} className={category === entry ? 'active' : ''} onClick={() => setCategory(entry)}>{entry === '全部' ? t('core.gui.materialAll') : entry}</button>)}
          </div>
          <div className="material-list">
            {visibleMaterials.map((material) => <button key={material} draggable onDragStart={(e) => e.dataTransfer.setData('text/material', material)} onClick={() => assignMaterial(material)} title={material}>
              <SlotIcon slot={{ item: material }} failed={failedImages} setFailed={setFailedImages} />
              <span><strong>minecraft:{material.toLowerCase()}</strong><small>{materialCategory(material)}</small></span>
            </button>)}
          </div>
          {!materialResults.length && <p className="material-limit">{t('core.gui.materialEmpty')}</p>}
          {materialResults.length > visibleMaterials.length && <p className="material-limit">{t('core.gui.materialLimit', { count: visibleMaterials.length })}</p>}
        </InspectorSection>
      </aside>
    </div>}
  </section>;
}

function ReloadConfirmDialog({ fileTitle, filePath, onConfirm, onCancel }: { fileTitle: string; filePath: string; onConfirm: () => void; onCancel: () => void }) {
  return <div className="reload-confirm-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onCancel(); }}>
    <div className="reload-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="reload-confirm-title" aria-describedby="reload-confirm-desc">
      <div className="reload-confirm-head">
        <span>{t('core.gui.unsavedChanges')}</span>
        <h3 id="reload-confirm-title">{t('core.gui.reloadDropsChanges')}</h3>
      </div>
      <div className="reload-confirm-body">
        <p id="reload-confirm-desc">{t('core.gui.reloadDesc', { title: fileTitle })}</p>
        <code>{filePath}</code>
      </div>
      <ActionGroup className="reload-confirm-actions">
        <Button onClick={onCancel} autoFocus>{t('core.gui.cancel')}</Button>
        <Button variant="danger" onClick={onConfirm}>{t('core.gui.continueReload')}</Button>
      </ActionGroup>
    </div>
  </div>;
}

function GuiLabel({ editor, path, fallback, children }: { editor?: import('./types').WebEditorDescriptor; path: string; fallback: string; children: React.ReactNode }) {
  const field = guiField(editor, path, fallback);
  return <label title={field.comment ? `${field.path}\n${field.comment}` : field.path}>{field.label}{children}</label>;
}

function SlotInspector({ slotKey, slot, updateSlot, removeSlot, editor }: { slotKey: string; slot: GuiSlotDefinition; updateSlot: (key: string, patch: Partial<GuiSlotDefinition>) => void; removeSlot: () => void; editor?: import('./types').WebEditorDescriptor }) {
  const slotsText = parseSlotList(slot.slots).join(', ');
  const setField = (field: string, value: unknown) => updateSlot(slotKey, { [field]: value === '' || value == null ? undefined : value });
  return <div className="slot-form">
    <div className="slot-key"><code>{slotKey}</code><button onClick={removeSlot}>{t('core.config.delete')}</button></div>
    <InspectorPanel title={t('core.gui.slotDefinition')} storageKey="slot-identity"><GuiLabel editor={editor} path="type" fallback={t('core.gui.slotType')}><input value={textValue(slot.type)} onChange={(e) => setField('type', e.target.value)} /></GuiLabel><GuiLabel editor={editor} path="slots" fallback={t('core.gui.slot')}><input value={slotsText} onChange={(e) => setField('slots', e.target.value.split(/[ ,]+/).map((part) => Number(part)).filter(Number.isFinite))} /></GuiLabel><small>{t('core.gui.slotCount', { count: parseSlotList(slot.slots).length })}</small></InspectorPanel>
    <InspectorPanel title={t('core.gui.itemSource')} storageKey="slot-item"><GuiLabel editor={editor} path="item" fallback={t('core.gui.item')}><input value={textValue(slot.item)} onChange={(e) => setField('item', e.target.value)} /></GuiLabel></InspectorPanel>
    <InspectorPanel title={t('core.gui.displayText')} storageKey="slot-display"><GuiLabel editor={editor} path="display_name" fallback={t('core.gui.displayName')}><input value={textValue(slot.display_name)} onChange={(e) => setField('display_name', e.target.value)} /></GuiLabel><GuiLabel editor={editor} path="lore" fallback="Lore"><textarea value={loreLines(slot.lore).join('\n')} onChange={(e) => setField('lore', e.target.value.split('\n'))} /></GuiLabel></InspectorPanel>
    <InspectorPanel title={t('core.gui.modelComponents')} storageKey="slot-model" defaultCollapsed><div className="mini-grid-2"><GuiLabel editor={editor} path="item_model" fallback={t('core.gui.itemModel')}><input value={textValue(slot.item_model ?? slot['item-model'])} onChange={(e) => setField('item_model', e.target.value)} /></GuiLabel><GuiLabel editor={editor} path="custom_model_data" fallback={t('core.gui.modelData')}><input type="number" value={textValue(slot.custom_model_data ?? slot.custommodeldata)} onChange={(e) => setField('custom_model_data', e.target.value === '' ? undefined : Number(e.target.value))} /></GuiLabel></div><EnchantmentsEditor value={slot.enchantments} onChange={(value) => setField('enchantments', value)} /><HiddenComponentsEditor slot={slot} onChange={(patch) => updateSlot(slotKey, patch)} /></InspectorPanel>
    <InspectorPanel title={t('core.gui.sounds')} storageKey="slot-sounds" defaultCollapsed><SoundsEditor value={slot.sounds} onChange={(value) => setField('sounds', value)} /></InspectorPanel>
    <InspectorPanel title={t('core.gui.advancedFields')} storageKey="slot-advanced" defaultCollapsed><AdvancedFieldsEditor slot={slot} onChange={(patch) => updateSlot(slotKey, patch)} /></InspectorPanel>
  </div>;
}

function InspectorPanel({ title, storageKey, defaultCollapsed = false, children }: { title: string; storageKey: string; defaultCollapsed?: boolean; children: React.ReactNode }) {
  const key = `emaki-gui-inspector:${storageKey}`;
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(key) ? localStorage.getItem(key) === '1' : defaultCollapsed);
  const toggle = () => setCollapsed((current) => {
    localStorage.setItem(key, current ? '0' : '1');
    return !current;
  });
  return <div className={`slot-form-section ${collapsed ? 'collapsed' : ''}`}><button type="button" className="slot-section-toggle" onClick={toggle} aria-expanded={!collapsed}><span>{collapsed ? '›' : '⌄'}</span><h4>{title}</h4></button>{!collapsed && <div className="slot-section-body">{children}</div>}</div>;
}

function EnchantmentsEditor({ value, onChange }: { value: unknown; onChange: (value: Record<string, number> | undefined) => void }) {
  const entries = enchantEntries(value);
  const update = (index: number, key: string, level: number) => onChange(entries.map((entry, i) => i === index ? { key, level } : entry).filter((entry) => entry.key.trim()).reduce((map, entry) => ({ ...map, [entry.key.trim()]: entry.level || 1 }), {} as Record<string, number>));
  const remove = (index: number) => onChange(entries.filter((_, i) => i !== index).reduce((map, entry) => ({ ...map, [entry.key]: entry.level }), {} as Record<string, number>));
  return <div className="sub-editor"><div className="sub-editor-head"><span>enchantments</span><button onClick={() => onChange({ ...entries.reduce((map, entry) => ({ ...map, [entry.key]: entry.level }), {} as Record<string, number>), sharpness: 1 })}>{t('core.gui.add')}</button></div>{entries.map((entry, index) => <div className="field-row" key={index}><input value={entry.key} onChange={(e) => update(index, e.target.value, entry.level)} placeholder="minecraft:sharpness" /><input type="number" value={entry.level} onChange={(e) => update(index, entry.key, Number(e.target.value))} /><button onClick={() => remove(index)}>{t('core.config.delete')}</button></div>)}</div>;
}

function enchantEntries(value: unknown): { key: string; level: number }[] {
  if (value && typeof value === 'object' && !Array.isArray(value)) return Object.entries(value as Record<string, unknown>).map(([key, level]) => ({ key, level: Number(level) || 1 }));
  if (Array.isArray(value)) return value.map((entry) => String(entry)).map((entry) => { const [key, level] = entry.split(':'); return { key, level: Number(level) || 1 }; });
  return [];
}

const HIDDEN_COMPONENTS = ['tooltip', 'enchantments', 'attributes', 'unbreakable', 'can_destroy', 'can_place_on', 'trim', 'dye', '*'];

function HiddenComponentsEditor({ slot, onChange }: { slot: GuiSlotDefinition; onChange: (patch: Partial<GuiSlotDefinition>) => void }) {
  const list = Array.isArray(slot.hidden_components) ? slot.hidden_components.map(String) : [];
  const toggle = (entry: string) => onChange({ hidden_components: list.includes(entry) ? list.filter((item) => item !== entry) : [...list, entry] });
  return <div className="sub-editor"><div className="sub-editor-head"><span>hidden_components</span><label className="inline-switch"><input type="checkbox" checked={slot.hide_tooltip === true || slot['hide-tooltip'] === true} onChange={(e) => onChange({ hide_tooltip: e.target.checked || undefined })} /> hide tooltip</label></div><div className="chip-list">{HIDDEN_COMPONENTS.map((entry) => <ToggleChip key={entry} active={list.includes(entry)} onClick={() => toggle(entry)}>{entry}</ToggleChip>)}</div></div>;
}

const SOUND_KEYS = ['click', 'left_click', 'right_click'] as const;

function SoundsEditor({ value, onChange }: { value: unknown; onChange: (value: Record<string, unknown> | undefined) => void }) {
  const sounds = normalizeSounds(value);
  const update = (key: string, patch: Record<string, unknown>) => onChange(cleanMap({ ...sounds, [key]: cleanMap({ ...(sounds[key] as Record<string, unknown> ?? {}), ...patch }) }));
  return <div className="sub-editor sound-editor">{SOUND_KEYS.map((key) => {
    const sound = (sounds[key] ?? {}) as Record<string, unknown>;
    return <div className="sound-row" key={key}><strong>{key}</strong><input value={textValue(sound.sound ?? sound.key ?? sound.type)} onChange={(e) => update(key, { sound: e.target.value })} placeholder="ui.button.click" /><input type="number" step="0.1" value={textValue(sound.volume, '1')} onChange={(e) => update(key, { volume: Number(e.target.value) })} /><input type="number" step="0.1" value={textValue(sound.pitch, '1')} onChange={(e) => update(key, { pitch: Number(e.target.value) })} /><button onClick={() => onChange(cleanMap({ ...sounds, [key]: undefined }))}>{t('core.gui.clear')}</button></div>;
  })}</div>;
}

function normalizeSounds(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? { ...(value as Record<string, unknown>) } : {};
}

const STANDARD_SLOT_FIELDS = new Set(['type', 'slots', 'item', 'display_name', 'lore', 'item_model', 'item-model', 'custom_model_data', 'custommodeldata', 'enchantments', 'hidden_components', 'hide_tooltip', 'hide-tooltip', 'tooltip_display', 'sounds']);

function AdvancedFieldsEditor({ slot, onChange }: { slot: GuiSlotDefinition; onChange: (patch: Partial<GuiSlotDefinition>) => void }) {
  const extras = Object.fromEntries(Object.entries(slot).filter(([key]) => !STANDARD_SLOT_FIELDS.has(key)));
  const [text, setText] = useState(() => JSON.stringify(extras, null, 2));
  const [jsonError, setJsonError] = useState('');
  useEffect(() => { setText(JSON.stringify(extras, null, 2)); setJsonError(''); }, [JSON.stringify(extras)]);
  return <div className="sub-editor"><textarea className="advanced-json" value={text} onChange={(e) => { setText(e.target.value); setJsonError(''); }} spellCheck={false} aria-invalid={!!jsonError} />{jsonError && <small className="json-error">{jsonError}</small>}<Button variant="soft" fullWidth onClick={() => { try { const parsed = JSON.parse(text || '{}'); onChange({ ...Object.fromEntries(Object.keys(extras).map((key) => [key, undefined])), ...parsed }); setJsonError(''); } catch (err) { setJsonError(err instanceof Error ? err.message : t('core.gui.jsonParseFailed')); } }}>{t('core.gui.applyAdvanced')}</Button></div>;
}

function cleanMap<T extends Record<string, unknown>>(value: T): T | undefined {
  const next = Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(entry && typeof entry === 'object' && !Array.isArray(entry) && Object.keys(entry).length === 0))) as T;
  return Object.keys(next).length ? next : undefined;
}

function SlotIcon({ slot, failed, setFailed }: { slot?: GuiSlotDefinition | null; failed: Record<string, boolean>; setFailed: React.Dispatch<React.SetStateAction<Record<string, boolean>>> }) {
  const material = slot?.item ?? 'AIR';
  const urls = materialUrls(material);
  const failedCount = urls.filter((url) => failed[url]).length;
  const url = urls.find((entry) => !failed[entry]);
  if (!slot || !url) return <span className="material-fallback" data-empty={!slot || !urls.length ? 'true' : undefined}>{materialShortName(material)}</span>;
  return <img className="material-icon" src={url} alt="" loading="lazy" draggable={false} data-attempt={failedCount} onError={() => setFailed((current) => ({ ...current, [url]: true }))} />;
}

function clampInspectorWidth(value: number): number {
  return Math.max(INSPECTOR_MIN, Math.min(INSPECTOR_MAX, Number.isFinite(value) ? value : 380));
}

function nextTooltipPosition(clientX: number, clientY: number) {
  const width = 340;
  const height = 180;
  const margin = 14;
  const x = Math.min(clientX + 18, window.innerWidth - width - margin);
  const y = Math.min(clientY + 18, window.innerHeight - height - margin);
  return { x: Math.max(margin, x), y: Math.max(margin, y) };
}

const MinecraftTooltip = forwardRef<HTMLDivElement, { slot: GuiSlotDefinition; slotKey: string; position: { x: number; y: number } }>(function MinecraftTooltip({ slot, slotKey, position }, ref) {
  const hidden = String(slot.hidden_components ?? '').includes('tooltip') || slot.hide_tooltip === true;
  if (hidden) return <div ref={ref} className="minecraft-tooltip muted-tooltip" style={{ left: position.x, top: position.y }}>{t('core.gui.tooltipHidden')} · {slotKey}</div>;
  return <div ref={ref} className="minecraft-tooltip" style={{ left: position.x, top: position.y }}>
    <strong><MiniText value={slot.display_name ?? slot.item ?? slotKey} /></strong>
    {loreLines(slot.lore).map((line, index) => <span key={index}><MiniText value={line} /></span>)}
    {slot.item_model ? <small>item_model: {String(slot.item_model)}</small> : null}
    {slot.custom_model_data ? <small>custom_model_data: {String(slot.custom_model_data)}</small> : null}
  </div>;
});

function MiniText({ value }: { value: unknown }) {
  return <>{renderMiniMessageParts(value).map((part, index) => <span key={index} style={{ color: part.color }} className={part.token ? 'mini-token' : undefined}>{part.text}</span>)}</>;
}
