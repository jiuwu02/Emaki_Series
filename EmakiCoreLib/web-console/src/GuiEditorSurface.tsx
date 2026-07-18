import { forwardRef, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ApiClient } from './api';
import { isGlobPath } from './documentPaths';
import { getSourceDocumentAdapter, type SurfaceToolbarState } from './registry';
import type { GuiSlotDefinition, GuiTemplateData, ItemComponentCapability, WebRegistryFile, WebRegistryModule } from './types';
import { buildOccupancy, clampRows, fieldLabel, guiColumns, guiField, guiSlotCount, guiTypeOptions, loreLines, materialShortName, materialUrls, normalizeGuiType, parseSlotList, parseYaml, renderMiniMessageParts, serializeGuiYaml, slotItemComponents, slotItemText, subscribeTextureBases, supportsRows, textValue, withSlotItem } from './guiEditor';
import { canonicalizeGuiSlotItem, canonicalizeGuiTemplateItems, fileDisplayTitle, humanizeFieldLabel, optionLabel } from './lib';
import { Button, DisclosureChevron, EditorChrome, InlineError, InspectorSection, ItemComponentsEditor, ToastNotice } from './components';
import { getLocale, t } from './i18n';
import { diffRecords } from './lib';
import { MATERIAL_CATEGORIES, MINECRAFT_MATERIAL_VERSION, type MaterialCategory, materialCategory, searchMaterials } from './minecraftMaterials';

type Props = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  api: ApiClient;
  childPath?: string;
  refreshKey?: number;
  editor?: import('./types').WebEditorDescriptor;
  onReload?: () => void;
  setToolbar?: (state: SurfaceToolbarState | null) => void;
  showLocalChrome?: boolean;
};

type SnapshotHistory = { undo: GuiTemplateData[]; redo: GuiTemplateData[] };

const INSPECTOR_MIN = 300;
const INSPECTOR_MAX = 620;
const INSPECTOR_STEP = 16;

export function GuiEditorSurface({ module, file, api, childPath, refreshKey = 0, editor, onReload, setToolbar, showLocalChrome = true }: Props) {
  const [data, setData] = useState<GuiTemplateData | null>(null);
  const [originalData, setOriginalData] = useState<GuiTemplateData | null>(null);
  const [originalText, setOriginalText] = useState('');
  const [revision, setRevision] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [sourceText, setSourceText] = useState('');
  const [sourceError, setSourceError] = useState<string | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [hovered, setHovered] = useState<number | null>(null);
  const [tooltipPosition, setTooltipPosition] = useState<{ x: number; y: number } | null>(null);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState<MaterialCategory | '全部'>('全部');
  const [failedImages, setFailedImages] = useState<Record<string, boolean>>({});
  const [, refreshTextureOrder] = useState(0);
  const [inspectorWidth, setInspectorWidth] = useState(() => {
    const saved = localStorage.getItem('emaki-gui-inspector-width');
    return saved ? clampInspectorWidth(Number(saved)) : 380;
  });
  const [resizingInspector, setResizingInspector] = useState(false);
  const [toast, setToast] = useState<{ tone: 'ok' | 'bad'; text: string } | null>(null);
  const [history, setHistory] = useState<SnapshotHistory>({ undo: [], redo: [] });
  const [visibleOverlay, setVisibleOverlay] = useState<Record<number, string>>({});
  const [componentCapabilities, setComponentCapabilities] = useState<ItemComponentCapability[]>([]);
  const tooltipRef = useRef<HTMLDivElement>(null);
  const inspectorResizeStartX = useRef(0);
  const inspectorResizeStartW = useRef(380);
  const latestInspectorWidth = useRef(inspectorWidth);

  const path = childPath || file.path;
  const fileTitle = fileDisplayTitle(file);
  const sourceAdapter = getSourceDocumentAdapter(file, editor);
  const sourcePath = childPath || file.path;
  const sourceContext = useMemo(() => ({ module, file, childPath, path: sourcePath, editor }), [module, file, childPath, sourcePath, editor?.id]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

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
    api.itemComponentCapabilities().then(setComponentCapabilities).catch(() => setComponentCapabilities([]));
  }, [api]);

  async function reloadGui() {
    if (!path) return;
    if (isGlobPath(path)) {
      setError(t('core.empty.selectFile'));
      setLoading(false);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const doc = await api.readGui(module.id, path);
      const original = doc.data ?? {};
      const normalizedData = canonicalizeGuiTemplateItems(original);
      setData(normalizedData);
      setOriginalData(original);
      const serialized = serializeGuiYaml(normalizedData);
      setOriginalText(serialized);
      setRevision(doc.revision);
      setSourceText(serialized);
      setSourceError(null);
      setHistory({ undo: [], redo: [] });
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
  const selectedAnchor = selected[0];
  const selectedCell = selectedAnchor == null ? null : occupancy.find((cell) => cell.index === selectedAnchor) ?? null;
  const selectedVisible = selectedCell ? visibleSlotForCell(selectedCell) : null;
  const selectedOverlays = selectedCell?.overlays ?? [];
  const selectedKey = selectedVisible?.key ?? selectedCell?.key ?? null;
  const selectedSlot = selectedKey && data?.slots ? data.slots[selectedKey] ?? null : null;
  const hasOverlays = selectedOverlays.length > 1;
  const draftText = useMemo(() => sourceError ? sourceText : data ? serializeGuiYaml(data) : '', [sourceError, sourceText, data]);
  const changes = useMemo(() => diffRecords(data ?? {}, originalData ?? {}, '', 18), [data, originalData]);
  const dirty = data != null && !sourceError && changes.length > 0;
  const materialResults = useMemo(() => searchMaterials(query, category), [query, category]);
  const visibleMaterials = materialResults.slice(0, 80);

  function visibleSlotForCell(cell: typeof occupancy[number]): { key: string; slot: import('./types').GuiSlotDefinition } | null {
    if (cell.overlays.length === 0) return cell.key ? { key: cell.key, slot: cell.slot! } : null;
    const visKey = visibleOverlay[cell.index];
    if (visKey === '') return null; // explicitly none visible
    const found = cell.overlays.find((o) => o.key === visKey);
    if (found) return found;
    // default: first overlay
    return cell.overlays[0] ?? null;
  }

  function setOverlayVisible(slotIndex: number, key: string | null) {
    setVisibleOverlay((current) => ({ ...current, [slotIndex]: key ?? '' }));
  }

  async function save() {
    if (!data || !path || !dirty) return;
    setSaving(true);
    setError('');
    try {
      if (sourceError) return;
      const content = draftText;
      const result = await (sourceAdapter?.save(api, sourceContext, content, revision) ?? api.saveGui(module.id, path, content, revision));
      setOriginalText(content);
      setRevision(result.revision ?? revision);
      setOriginalData(data);
      setSourceText(content);
      setHistory({ undo: [], redo: [] });
      setToast({ tone: 'ok', text: t('core.toast.savedGui') });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.gui.saveFailed'));
    } finally {
      setSaving(false);
    }
  }

  function updateSource(nextSource: string) {
    setSourceText(nextSource);
    try {
      const parsed = canonicalizeGuiTemplateItems(parseYaml(nextSource) as GuiTemplateData);
      if (data && !recordsEqual(data, parsed)) rememberHistory(data);
      setData(parsed);
      setSourceError(null);
    } catch (err) {
      setSourceError(err instanceof Error ? err.message : String(err));
    }
  }

  function rememberHistory(snapshot: GuiTemplateData) {
    setHistory(current => ({ undo: [...current.undo, snapshot].slice(-20), redo: [] }));
  }

  function applySnapshot(snapshot: GuiTemplateData) {
    setData(snapshot);
    setSourceText(serializeGuiYaml(snapshot));
    setSourceError(null);
  }

  function undo() {
    if (!data) return;
    const snapshot = history.undo[history.undo.length - 1];
    if (!snapshot) return;
    setHistory(current => ({ undo: current.undo.slice(0, -1), redo: [data, ...current.redo].slice(0, 20) }));
    applySnapshot(snapshot);
  }

  function redo() {
    if (!data) return;
    const snapshot = history.redo[0];
    if (!snapshot) return;
    setHistory(current => ({ undo: [...current.undo, data].slice(-20), redo: current.redo.slice(1) }));
    applySnapshot(snapshot);
  }

  function updateData(mutator: (draft: GuiTemplateData) => GuiTemplateData) {
    setData((current) => {
      const base = current ?? {};
      const mutated = mutator({ ...base, slots: { ...(base.slots ?? {}) } });
      const next = canonicalizeGuiTemplateItems(pruneUndefined(mutated) as GuiTemplateData);
      if (!recordsEqual(base, next)) rememberHistory(base);
      setSourceText(serializeGuiYaml(next));
      setSourceError(null);
      return next;
    });
  }

  function updateSlot(key: string, patch: Partial<GuiSlotDefinition>) {
    updateData((draft) => {
      const currentSlot = canonicalizeGuiSlotItem((draft.slots ?? {})[key] ?? {});
      let merged: Record<string, unknown> = { ...currentSlot };
      for (const [field, value] of Object.entries(patch)) {
        if (field === 'item' && value && typeof value === 'object' && !Array.isArray(value)) {
          const currentItem = currentSlot.item && typeof currentSlot.item === 'object' && !Array.isArray(currentSlot.item) ? currentSlot.item : {};
          const itemPatch = value as Record<string, unknown>;
          const patchComponents = itemPatch.components && typeof itemPatch.components === 'object' && !Array.isArray(itemPatch.components) ? itemPatch.components : undefined;
          merged.item = pruneUndefined({ ...currentItem, ...itemPatch, ...(patchComponents ? { components: patchComponents } : {}) });
        } else if (field === 'item_source') {
          merged = withSlotItem(merged as GuiSlotDefinition, value) as Record<string, unknown>;
        } else if (value === undefined) delete merged[field];
        else merged[field] = value;
      }
      return { ...draft, slots: { ...(draft.slots ?? {}), [key]: canonicalizeGuiSlotItem(merged as GuiSlotDefinition) } };
    });
  }

  function createSlot(index: number, material = 'STONE') {
    const base = `slot_${index}`;
    const slots = data?.slots ?? {};
    let key = base;
    let i = 2;
    while (slots[key]) key = `${base}_${i++}`;
    updateData((draft) => ({ ...draft, slots: { ...(draft.slots ?? {}), [key]: withSlotItem({ slots: [index], item: { components: { 'minecraft:custom_name': `<gray>${material.toLowerCase()}</gray>`, 'minecraft:lore': [] } } }, material) } }));
    setSelected([index]);
  }

  function assignMaterial(material: string) {
    if (!selected.length) return;
    const visible = selectedCell ? visibleSlotForCell(selectedCell) : null;
    if (visible?.key) {
      updateSlot(visible.key, { item: { source: material } });
      return;
    }
    createSlot(selected[0], material);
  }

  function selectSlot(index: number) {
    const cell = occupancy.find((entry) => entry.index === index);
    const visible = cell ? visibleSlotForCell(cell) : null;
    const group = visible ? parseSlotList(visible.slot.slots).filter((slotIndex) => slotIndex >= 0 && slotIndex < occupancy.length) : [];
    setSelected(uniqueNumbers([index, ...group]));
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
      selectSlot(index);
      return;
    }
    if (!(event.key in navigation)) return;
    event.preventDefault();
    const nextIndex = Math.max(0, Math.min(occupancy.length - 1, navigation[event.key]));
    document.querySelector<HTMLButtonElement>(`[data-gui-slot="${nextIndex}"]`)?.focus();
  }

  const subtitle = data ? `${module.id}/${path} · ${guiType}${rowSupported ? ` · ${t('core.gui.metaRows', { count: rows })}` : ''} · ${t('core.gui.metaSlots', { count: slotCount })} · ${t('core.gui.metaSlotDefinitions', { count: Object.keys(data.slots ?? {}).length })}` : `${module.id}/${path}`;

  useEffect(() => {
    if (!setToolbar) return;
    if (!path || !data) {
      setToolbar(null);
      return;
    }
    setToolbar({
      title: fileTitle,
      subtitle,
      dirty,
      changes,
      source: draftText,
      sourceOriginal: originalText,
      sourceEditable: true,
      sourceError,
      sourceLanguage: 'yaml',
      saving,
      loading,
      saveLabel: t('core.gui.save'),
      canUndo: history.undo.length > 0,
      canRedo: history.redo.length > 0,
      onUndo: undo,
      onRedo: redo,
      onReload: () => void (onReload ? onReload() : reloadGui()),
      onSourceChange: updateSource,
      onSave: () => void save()
    });
  }, [setToolbar, path, data, fileTitle, subtitle, dirty, changes, draftText, sourceError, saving, loading, history.undo.length, history.redo.length, onReload]);

  useEffect(() => () => setToolbar?.(null), [setToolbar]);

  if (!path) return <section className="config-surface empty" role="status">{t('core.gui.selectFile')}</section>;
  if (isGlobPath(path)) return <section className="config-surface empty" role="status"><InlineError>{t('core.empty.selectFile')}</InlineError></section>;
  if (loading) return <section className="config-surface gui-surface"><div className="gui-loading" role="status">{t('core.gui.loading')}</div></section>;
  if (!data) return <section className="config-surface empty"><InlineError>{error || t('core.gui.unavailable')}</InlineError><Button size="sm" onClick={() => void reloadGui()}>{t('core.action.retry')}</Button></section>;

  const hoveredCell = hovered == null ? null : occupancy.find((cell) => cell.index === hovered);

  return <section className="config-surface gui-surface" data-dirty={dirty ? 'true' : undefined}>
    {toast && <ToastNotice tone={toast.tone} style={{ position: 'absolute', top: 12, right: 12, zIndex: 50 }}>{toast.text}</ToastNotice>}
    {showLocalChrome && <EditorChrome
      className="surface-head gui-head"
      title={fileTitle}
      subtitle={subtitle}
      dirty={dirty}
      changes={changes}
      source={draftText}
      sourceEditable
      sourceError={sourceError}
      sourceLanguage="yaml"
      saving={saving}
      loading={loading}
      saveLabel={t('core.gui.save')}
      canUndo={history.undo.length > 0}
      canRedo={history.redo.length > 0}
      onUndo={undo}
      onRedo={redo}
      onReload={() => void reloadGui()}
      onSourceChange={updateSource}
      onSave={() => void save()}
    />}
    {error && <InlineError className="gui-error">{error}</InlineError>}
    <div className={`gui-workbench ${resizingInspector ? 'is-resizing' : ''}`} style={{ '--gui-inspector-width': `${inspectorWidth}px` } as React.CSSProperties}>
      <div className="gui-preview-pane">
        <div className="minecraft-window">
          <div className="minecraft-titlebar"><MiniText value={data.title ?? 'GUI'} /></div>
          <p className="slot-grid-help" id="gui-slot-help">{t('core.gui.gridHelp')}</p>
          <div className="minecraft-grid" role="grid" aria-label={t('core.gui.gridAria', { title: fileTitle })} aria-describedby="gui-slot-help" data-gui-type={guiType} style={{ gridTemplateColumns: `repeat(${columns}, var(--mc-slot))` }}>
            {occupancy.map((cell) => <button
              key={cell.index}
              className={`minecraft-slot ${cell.key ? 'occupied' : ''} ${selected.includes(cell.index) ? 'selected' : ''} ${cell.conflicts.length ? 'conflict' : ''}`}
              role="gridcell"
              data-gui-slot={cell.index}
              aria-label={t('core.gui.slotAria', { index: cell.index, suffix: cell.key ? `，${cell.key}` : t('core.gui.slotEmpty') })}
              aria-selected={selected.includes(cell.index)}
              onClick={() => selectSlot(cell.index)}
              onKeyDown={(event) => handleSlotKeyDown(event, cell.index)}
              onMouseEnter={(event) => { setHovered(cell.index); setTooltipPosition(nextTooltipPosition(event.clientX, event.clientY)); }}
              onMouseMove={handleSlotMouseMove}
              onMouseLeave={() => { setHovered(null); setTooltipPosition(null); }}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => { event.preventDefault(); const material = event.dataTransfer.getData('text/material'); if (material) { selectSlot(cell.index); const visible = visibleSlotForCell(cell); visible?.key ? updateSlot(visible.key, { item: { source: material } }) : createSlot(cell.index, material); } }}
            >
              <SlotIcon slot={visibleSlotForCell(cell)?.slot ?? null} failed={failedImages} setFailed={setFailedImages} />
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
          <GuiLabel editor={editor} path="gui_type" fallback={t('core.gui.type')}><select value={guiType} onChange={(e) => updateData((draft) => ({ ...draft, gui_type: e.target.value, rows: supportsRows(e.target.value) ? clampRows(draft.rows) : undefined }))}>{guiTypeOptions().map((type) => <option key={type} value={type}>{type}</option>)}</select></GuiLabel>
          <GuiLabel editor={editor} path="title" fallback={t('core.gui.title')}><input value={textValue(data.title)} onChange={(e) => updateData((draft) => ({ ...draft, title: e.target.value }))} /></GuiLabel>
          {rowSupported && <GuiLabel editor={editor} path="rows" fallback={t('core.gui.rows')}><input type="number" min={1} max={6} value={rows} onChange={(e) => updateData((draft) => ({ ...draft, rows: clampRows(e.target.value) }))} /></GuiLabel>}
        </InspectorSection>
        <InspectorSection title={t('core.gui.slotInspector', { value: selected.length ? selected.join(', ') : t('core.gui.noSlotSelected') })}>
          {hasOverlays && selectedCell ? <OverlaySlotInspector
            cell={selectedCell}
            visibleKey={visibleOverlay[selectedCell.index] ?? selectedOverlays[0]?.key ?? ''}
            onVisibilityChange={(key) => setOverlayVisible(selectedCell.index, key)}
            editor={editor}
            componentCapabilities={componentCapabilities}
            updateSlot={updateSlot}
            removeSlot={(key) => updateData((draft) => { const next = { ...(draft.slots ?? {}) }; delete next[key]; return { ...draft, slots: next }; })}
          /> : selectedSlot && selectedKey ? <SlotInspector slotKey={selectedKey} slot={selectedSlot} editor={editor} componentCapabilities={componentCapabilities} updateSlot={updateSlot} removeSlot={() => updateData((draft) => { const next = { ...(draft.slots ?? {}) }; delete next[selectedKey]; return { ...draft, slots: next }; })} /> : selected.length ? <Button variant="soft" fullWidth onClick={() => createSlot(selected[0])}>{t('core.gui.createSlot', { slot: selected[0] })}</Button> : <p className="muted-copy">{t('core.gui.slotHint')}</p>}
        </InspectorSection>
        <InspectorSection className="material-palette" title={t('core.gui.materialSource')} meta={`MC ${MINECRAFT_MATERIAL_VERSION} · ${t('core.config.groupItems', { count: materialResults.length })}`}>
          <input aria-label={t('core.gui.materialSearch')} placeholder={t('core.gui.materialPlaceholder')} value={query} onChange={(e) => setQuery(e.target.value)} />
          <div className="material-tabs">
            {(['全部', ...MATERIAL_CATEGORIES] as const).map((entry) => <button key={entry} className={category === entry ? 'active' : ''} onClick={() => setCategory(entry)}>{entry === '全部' ? t('core.gui.materialAll') : t(`core.material.category.${entry}`, undefined, entry)}</button>)}
          </div>
          <div className="material-list">
            {visibleMaterials.map((material) => <button key={material} draggable onDragStart={(e) => e.dataTransfer.setData('text/material', material)} onClick={() => assignMaterial(material)} title={material}>
              <SlotIcon slot={withSlotItem({}, material)} failed={failedImages} setFailed={setFailedImages} />
              <span><strong>minecraft:{material.toLowerCase()}</strong><small>{materialCategory(material)}</small></span>
            </button>)}
          </div>
          {!materialResults.length && <p className="material-limit">{t('core.gui.materialEmpty')}</p>}
          {materialResults.length > visibleMaterials.length && <p className="material-limit">{t('core.gui.materialLimit', { count: visibleMaterials.length })}</p>}
        </InspectorSection>
      </aside>
    </div>
  </section>;
}


function OverlaySlotInspector({ cell, visibleKey, onVisibilityChange, editor, componentCapabilities, updateSlot, removeSlot }: {
  cell: import('./lib/guiUtils').SlotOccupancy;
  visibleKey: string;
  onVisibilityChange: (key: string | null) => void;
  editor?: import('./types').WebEditorDescriptor;
  componentCapabilities: ItemComponentCapability[];
  updateSlot: (key: string, patch: Partial<import('./types').GuiSlotDefinition>) => void;
  removeSlot: (key: string) => void;
}) {
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const toggle = (key: string) => setCollapsed((c) => ({ ...c, [key]: !c[key] }));

  return <div className="overlay-inspector">
    {cell.overlays.map((overlay) => {
      const isVisible = visibleKey === overlay.key;
      const isCollapsed = collapsed[overlay.key] === true;
      return <div key={overlay.key} className={`overlay-entry ${isVisible ? 'is-visible' : ''}`}>
        <div className="overlay-entry-head">
          <button
            type="button"
            className={`overlay-visibility ${isVisible ? 'active' : ''}`}
            title={isVisible ? t('core.gui.overlayHide') : t('core.gui.overlayShow')}
            aria-pressed={isVisible}
            onClick={() => onVisibilityChange(isVisible ? null : overlay.key)}
          />
          <button type="button" className="overlay-toggle" onClick={() => toggle(overlay.key)} aria-expanded={!isCollapsed}>
            <DisclosureChevron open={!isCollapsed} className="overlay-arrow" />
            <span className="overlay-summary">
              <span className="overlay-discriminator">{slotDiscriminator(overlay.slot)}</span>
              <code>{overlay.key}</code>
              <small>{slotMetaSummary(overlay.slot)}</small>
            </span>
          </button>
          <button type="button" className="overlay-delete" onClick={() => removeSlot(overlay.key)}>{t('core.config.delete')}</button>
        </div>
        {!isCollapsed && <div className="overlay-entry-body">
          <SlotInspector slotKey={overlay.key} slot={overlay.slot} editor={editor} componentCapabilities={componentCapabilities} updateSlot={updateSlot} removeSlot={() => removeSlot(overlay.key)} hideHeader />
        </div>}
      </div>;
    })}
  </div>;
}


function GuiLabel({ editor, path, fallback, children }: { editor?: import('./types').WebEditorDescriptor; path: string; fallback: string; children: React.ReactNode }) {
  const field = guiField(editor, path, fallback);
  const label = fieldLabel(path, { moduleId: editor?.moduleId, namespace: editor?.moduleId, editorFields: editor?.fields, fallback: getLocale().startsWith('zh') ? field.label : humanizeFieldLabel(path) });
  return <label className="gui-prop-row" title={field.comment ? `${field.path}\n${field.comment}` : field.path}>
    <span className="gui-prop-label">{label}</span>
    <span className="gui-prop-value">{children}</span>
  </label>;
}

function SlotTypeInput({ editor, value, onChange }: { editor?: import('./types').WebEditorDescriptor; value: unknown; onChange: (value: string) => void }) {
  const field = guiField(editor, 'type', undefined, 'text');
  const options = field.type === 'enum' && field.options?.length ? field.options : [];
  const current = textValue(value);
  if (!options.length) return <input value={current} onChange={(e) => onChange(e.target.value)} />;
  // Datalist keeps known slot types as suggestions while still allowing dynamic
  // custom values (e.g. material_input_0, filler) that are valid for some GUIs.
  const listId = `slot-type-${editor?.moduleId ?? 'core'}-${editor?.id ?? 'gui'}`;
  const labelFor = (option: string) => field.optionLabelPrefix
    ? optionLabel(field.optionLabelPrefix, option, { moduleId: editor?.moduleId, namespace: editor?.moduleId, fallback: option })
    : option;
  return <span className="slot-type-input">
    <input value={current} list={listId} onChange={(e) => onChange(e.target.value)} />
    <datalist id={listId}>{options.map((option) => <option key={option} value={option}>{labelFor(option)}</option>)}</datalist>
  </span>;
}

function SlotInspector({ slotKey, slot, updateSlot, removeSlot, editor, componentCapabilities, hideHeader = false }: { slotKey: string; slot: GuiSlotDefinition; updateSlot: (key: string, patch: Partial<GuiSlotDefinition>) => void; removeSlot: () => void; editor?: import('./types').WebEditorDescriptor; componentCapabilities: ItemComponentCapability[]; hideHeader?: boolean }) {
  const canonical = canonicalizeGuiSlotItem(slot);
  const item = canonical.item && typeof canonical.item === 'object' && !Array.isArray(canonical.item) ? canonical.item : {};
  const components = slotItemComponents(canonical);
  const componentField = guiField(editor, 'item.components', t('core.item.components'), 'itemComponents');
  const setField = (field: string, value: unknown) => updateSlot(slotKey, { [field]: value === '' || value == null ? undefined : value });
  const setItemField = (field: string, value: unknown) => updateSlot(slotKey, { item: { [field]: value === '' || value == null ? undefined : value } });
  const setComponent = (field: string, value: unknown) => updateSlot(slotKey, { item: { components: { ...components, [field]: value === '' || value == null ? undefined : value } } });
  return <div className="slot-form">
    {!hideHeader && <div className="slot-key"><code>{slotKey}</code><button onClick={removeSlot}>{t('core.config.delete')}</button></div>}
    <InspectorPanel title={t('core.gui.slotDefinition')} storageKey="slot-identity"><GuiLabel editor={editor} path="type" fallback={t('core.gui.slotType')}><SlotTypeInput editor={editor} value={canonical.type} onChange={(value) => setField('type', value)} /></GuiLabel><GuiLabel editor={editor} path="slots" fallback={t('core.gui.slot')}><DeferredSlotsInput value={canonical.slots} onApply={(value) => setField('slots', value)} /></GuiLabel><small>{t('core.gui.slotCount', { count: parseSlotList(canonical.slots).length })}</small></InspectorPanel>
    <InspectorPanel title={t('core.gui.itemSource')} storageKey="slot-item"><GuiLabel editor={editor} path="item.source" fallback={t('core.gui.item')}><input value={textValue(slotItemText(canonical))} onChange={(e) => setItemField('source', e.target.value)} /></GuiLabel><GuiLabel editor={editor} path="item.amount" fallback={t('core.item.amount')}><input type="number" min={1} value={item.amount == null ? '' : textValue(item.amount)} onChange={(e) => setItemField('amount', e.target.value === '' ? undefined : Number(e.target.value))} /></GuiLabel></InspectorPanel>
    <InspectorPanel title={t('core.gui.displayText')} storageKey="slot-display"><GuiLabel editor={editor} path="item.components.minecraft:custom_name" fallback={t('core.gui.displayName')}><input value={textValue(components['minecraft:custom_name'])} onChange={(e) => setComponent('minecraft:custom_name', e.target.value)} /></GuiLabel><GuiLabel editor={editor} path="item.components.minecraft:item_name" fallback={t('core.item.itemName')}><input value={textValue(components['minecraft:item_name'])} onChange={(e) => setComponent('minecraft:item_name', e.target.value)} /></GuiLabel><GuiLabel editor={editor} path="item.components.minecraft:lore" fallback="Lore"><textarea value={loreLines(components['minecraft:lore']).join('\n')} onChange={(e) => setComponent('minecraft:lore', e.target.value.split('\n'))} /></GuiLabel></InspectorPanel>
    <InspectorPanel title={t('core.item.components')} storageKey="slot-components" defaultCollapsed><ItemComponentsEditor value={components} onChange={(value) => updateSlot(slotKey, { item: { components: value } })} capabilities={componentField.componentCapabilities?.length ? componentField.componentCapabilities : componentCapabilities} reservedIds={['minecraft:custom_name', 'minecraft:item_name', 'minecraft:lore']} /></InspectorPanel>
    <InspectorPanel title={t('core.gui.sounds')} storageKey="slot-sounds" defaultCollapsed><SoundsEditor value={canonical.sounds} onChange={(value) => setField('sounds', value)} /></InspectorPanel>
    <InspectorPanel title={t('core.gui.advancedFields')} storageKey="slot-advanced" defaultCollapsed><AdvancedFieldsEditor slot={canonical} editor={editor} onChange={(patch) => updateSlot(slotKey, patch)} /></InspectorPanel>
  </div>;
}

function DeferredSlotsInput({ value, onApply }: { value: unknown; onApply: (value: number[]) => void }) {
  const committed = parseSlotList(value).join(', ');
  const [draft, setDraft] = useState(committed);
  useEffect(() => setDraft(committed), [committed]);
  const pending = draft.trim() !== committed;
  const parsed = parseSlotList(draft);
  return <span className="deferred-slot-input">
    <input value={draft} onChange={(event) => setDraft(event.target.value)} />
    <Button size="sm" variant="soft" disabled={!pending} onClick={() => onApply(parsed)}>{t('core.gui.applySlots')}</Button>
    {pending && <small className="slot-pending-note">{t('core.gui.slotsPreviewAfterSave')}</small>}
  </span>;
}

function InspectorPanel({ title, storageKey, defaultCollapsed = false, children }: { title: string; storageKey: string; defaultCollapsed?: boolean; children: React.ReactNode }) {
  const key = `emaki-gui-inspector:${storageKey}`;
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(key) ? localStorage.getItem(key) === '1' : defaultCollapsed);
  const toggle = () => setCollapsed((current) => {
    localStorage.setItem(key, current ? '0' : '1');
    return !current;
  });
  return <section className={`slot-form-section prop-section ${collapsed ? 'collapsed' : ''}`}>
    <div className="prop-section-head prop-section-head--collapsible slot-section-head">
      <button type="button" className="prop-section-toggle slot-section-toggle" onClick={toggle} aria-expanded={!collapsed}>
        <DisclosureChevron open={!collapsed} className="prop-section-arrow" />
        <span className="prop-section-title">{title}</span>
      </button>
    </div>
    {!collapsed && <div className="prop-section-body slot-section-body">{children}</div>}
  </section>;
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

const STANDARD_SLOT_FIELDS = new Set(['type', 'slots', 'item', 'sounds']);

function AdvancedFieldsEditor({ slot, editor, onChange }: { slot: GuiSlotDefinition; editor?: import('./types').WebEditorDescriptor; onChange: (patch: Partial<GuiSlotDefinition>) => void }) {
  const extras = Object.entries(slot).filter(([key]) => !STANDARD_SLOT_FIELDS.has(key));
  if (!extras.length) return <p className="muted-copy">{t('core.gui.noAdvancedFields')}</p>;
  return <div className="sub-editor advanced-field-list">
    {extras.map(([key, value]) => <AdvancedFieldRow key={key} fieldKey={key} value={value} editor={editor} onChange={(next) => onChange({ [key]: next })} />)}
  </div>;
}

function AdvancedFieldRow({ fieldKey, value, editor, onChange }: { fieldKey: string; value: unknown; editor?: import('./types').WebEditorDescriptor; onChange: (value: unknown) => void }) {
  const field = guiField(editor, fieldKey, fieldKey);
  const label = fieldLabel(fieldKey, { moduleId: editor?.moduleId, namespace: editor?.moduleId, editorFields: editor?.fields, fallback: field.label });
  return <div className="gui-prop-row advanced-field-row" title={field.comment ? `${field.path}\n${field.comment}` : field.path}>
    <span className="gui-prop-label">{label}</span>
    <span className="gui-prop-value advanced-field-control">
      <AdvancedFieldControl value={value} onChange={onChange} />
      <button type="button" className="advanced-field-delete" onClick={() => onChange(undefined)}>{t('core.config.delete')}</button>
    </span>
  </div>;
}

function AdvancedFieldControl({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  if (typeof value === 'boolean') return <label className="inline-switch"><input type="checkbox" checked={value} onChange={(event) => onChange(event.target.checked)} /> {value ? 'true' : 'false'}</label>;
  if (typeof value === 'number') return <input type="number" value={Number.isFinite(value) ? value : 0} onChange={(event) => onChange(event.target.value === '' ? undefined : Number(event.target.value))} />;
  if (Array.isArray(value) && value.every((entry) => ['string', 'number', 'boolean'].includes(typeof entry))) {
    return <textarea value={value.map(String).join('\n')} onChange={(event) => onChange(event.target.value.split('\n').map(parsePrimitiveLine).filter((entry) => entry !== ''))} />;
  }
  if (typeof value === 'string' || value == null) return <input value={textValue(value)} onChange={(event) => onChange(event.target.value)} />;
  return <JsonFieldEditor value={value} onChange={onChange} />;
}

function JsonFieldEditor({ value, onChange }: { value: unknown; onChange: (value: unknown) => void }) {
  const [text, setText] = useState(() => JSON.stringify(value, null, 2));
  const [jsonError, setJsonError] = useState('');
  useEffect(() => { setText(JSON.stringify(value, null, 2)); setJsonError(''); }, [JSON.stringify(value)]);
  return <div className="advanced-json-field">
    <textarea className="advanced-json" value={text} onChange={(event) => { setText(event.target.value); setJsonError(''); }} spellCheck={false} aria-invalid={!!jsonError} />
    {jsonError && <small className="json-error">{jsonError}</small>}
    <Button variant="soft" fullWidth onClick={() => { try { onChange(JSON.parse(text || 'null')); setJsonError(''); } catch (err) { setJsonError(err instanceof Error ? err.message : t('core.gui.jsonParseFailed')); } }}>{t('core.gui.applyAdvanced')}</Button>
  </div>;
}

function parsePrimitiveLine(value: string): string | number | boolean {
  const trimmed = value.trim();
  if (trimmed === 'true') return true;
  if (trimmed === 'false') return false;
  if (trimmed !== '' && Number.isFinite(Number(trimmed))) return Number(trimmed);
  return value;
}

function slotDiscriminator(slot: GuiSlotDefinition): string {
  const type = textValue(slot.type).trim();
  return type || t('core.gui.slotTypeStatic');
}

function slotMetaSummary(slot: GuiSlotDefinition): string {
  const parts = [
    `${t('core.gui.slot')}: ${parseSlotList(slot.slots).join(', ') || '-'}`,
    slotItemText(slot) ? `${t('core.gui.item')}: ${slotItemText(slot)}` : ''
  ].filter(Boolean);
  return parts.join(' · ');
}

function uniqueNumbers(values: number[]): number[] {
  return values.filter((value, index, array) => Number.isFinite(value) && array.indexOf(value) === index);
}

function cleanMap<T extends Record<string, unknown>>(value: T): T | undefined {
  const next = Object.fromEntries(Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== '' && !(entry && typeof entry === 'object' && !Array.isArray(entry) && Object.keys(entry).length === 0))) as T;
  return Object.keys(next).length ? next : undefined;
}

function SlotIcon({ slot, failed, setFailed }: { slot?: GuiSlotDefinition | null; failed: Record<string, boolean>; setFailed: React.Dispatch<React.SetStateAction<Record<string, boolean>>> }) {
  const material = slotItemText(slot) || 'AIR';
  const urls = materialUrls(material);
  const failedCount = urls.filter((url) => failed[url]).length;
  const url = urls.find((entry) => !failed[entry]);
  if (!slot || !url) return <span className="material-fallback" data-empty={!slot || !urls.length ? 'true' : undefined}>{materialShortName(material)}</span>;
  return <img className="material-icon" src={url} alt="" loading="lazy" draggable={false} data-attempt={failedCount} onError={() => setFailed((current) => ({ ...current, [url]: true }))} />;
}

function clampInspectorWidth(value: number): number {
  return Math.max(INSPECTOR_MIN, Math.min(INSPECTOR_MAX, Number.isFinite(value) ? value : 380));
}

function recordsEqual(a: unknown, b: unknown): boolean {
  try {
    return JSON.stringify(a) === JSON.stringify(b);
  } catch {
    return Object.is(a, b);
  }
}

function pruneUndefined(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(pruneUndefined);
  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {};
    for (const [key, entry] of Object.entries(value as Record<string, unknown>)) {
      if (entry === undefined) continue;
      result[key] = pruneUndefined(entry);
    }
    return result;
  }
  return value;
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
  const components = slotItemComponents(slot);
  const tooltipDisplay = components['minecraft:tooltip_display'] && typeof components['minecraft:tooltip_display'] === 'object' && !Array.isArray(components['minecraft:tooltip_display']) ? components['minecraft:tooltip_display'] as Record<string, unknown> : {};
  const hiddenComponents = Array.isArray(tooltipDisplay.hidden_components) ? tooltipDisplay.hidden_components.map(String) : [];
  const hidden = hiddenComponents.includes('minecraft:tooltip') || tooltipDisplay.hide_tooltip === true;
  if (hidden) return <div ref={ref} className="minecraft-tooltip muted-tooltip" style={{ left: position.x, top: position.y }}>{t('core.gui.tooltipHidden')} · {slotKey}</div>;
  return <div ref={ref} className="minecraft-tooltip" style={{ left: position.x, top: position.y }}>
    <strong><MiniText value={components['minecraft:custom_name'] ?? components['minecraft:item_name'] ?? slotItemText(slot) ?? slotKey} /></strong>
    {loreLines(components['minecraft:lore']).map((line, index) => <span key={index}><MiniText value={line} /></span>)}
    {components['minecraft:item_model'] ? <small>minecraft:item_model: {String(components['minecraft:item_model'])}</small> : null}
    {components['minecraft:custom_model_data'] ? <small>minecraft:custom_model_data: {String(components['minecraft:custom_model_data'])}</small> : null}
  </div>;
});

function MiniText({ value }: { value: unknown }) {
  return <>{renderMiniMessageParts(value).map((part, index) => <span key={index} style={{ color: part.color }} className={part.token ? 'mini-token' : undefined}>{part.text}</span>)}</>;
}
