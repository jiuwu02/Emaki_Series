import { useEffect, useMemo, useState } from 'react';
import type { ApiClient } from './api';
import type { GuiSlotDefinition, GuiTemplateData, WebRegistryFile, WebRegistryModule } from './types';
import { buildOccupancy, clampRows, COMMON_MATERIALS, loreLines, materialShortName, materialUrl, parseSlotList, renderMiniMessageParts, serializeGuiYaml, textValue } from './guiEditor';

type Props = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  api: ApiClient;
  childPath?: string;
};

export function GuiEditorSurface({ module, file, api, childPath }: Props) {
  const [data, setData] = useState<GuiTemplateData | null>(null);
  const [originalText, setOriginalText] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<number[]>([]);
  const [hovered, setHovered] = useState<number | null>(null);
  const [query, setQuery] = useState('');
  const [failedImages, setFailedImages] = useState<Record<string, boolean>>({});
  const [mode, setMode] = useState<'preview' | 'source'>('preview');

  const path = childPath ?? '';

  useEffect(() => {
    if (!path) return;
    setLoading(true);
    setError('');
    api.readGui(module.id, path).then((doc) => {
      setData(doc.data ?? {});
      setOriginalText(doc.content ?? '');
      setSelected([]);
    }).catch((err) => setError(err instanceof Error ? err.message : 'GUI 文件加载失败'))
      .finally(() => setLoading(false));
  }, [api, module.id, path]);

  const rows = clampRows(data?.rows);
  const occupancy = useMemo(() => data ? buildOccupancy(data) : [], [data]);
  const selectedKey = selected.length === 1 ? occupancy.find((cell) => cell.index === selected[0])?.key ?? null : null;
  const selectedSlot = selectedKey && data?.slots ? data.slots[selectedKey] ?? null : null;
  const draftText = data ? serializeGuiYaml(data) : '';
  const dirty = data != null && draftText !== originalText;

  async function save() {
    if (!data || !path) return;
    setSaving(true);
    setError('');
    try {
      const content = serializeGuiYaml(data);
      await api.saveGui(module.id, path, content);
      setOriginalText(content);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败');
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

  if (!path) return <section className="config-surface empty">从左侧选择一个 GUI 模板文件开始预览。</section>;
  if (loading) return <section className="config-surface gui-surface"><div className="gui-loading">正在载入 Minecraft GUI 预览...</div></section>;
  if (!data) return <section className="config-surface empty">{error || '无法加载 GUI 文件。'}</section>;

  const hoveredCell = hovered == null ? null : occupancy.find((cell) => cell.index === hovered);

  return <section className="config-surface gui-surface">
    <div className="surface-head gui-head">
      <div>
        <h2>{file.title}</h2>
        <p>{module.id}/{path} · {rows} 行 · {Object.keys(data.slots ?? {}).length} 个 slot 定义 {dirty && <span className="dirty-inline">未保存</span>}</p>
      </div>
      <div className="head-actions">
        <button onClick={() => setMode(mode === 'preview' ? 'source' : 'preview')}>{mode === 'preview' ? '源码' : '预览'}</button>
        <button onClick={() => setData(null)} disabled={saving}>重载</button>
        <button className="primary" onClick={() => void save()} disabled={!dirty || saving}>{saving ? '保存中' : '保存 GUI'}</button>
      </div>
    </div>
    {error && <div className="gui-error">{error}</div>}
    {mode === 'source' ? <pre className="gui-source">{draftText}</pre> : <div className="gui-workbench">
      <div className="minecraft-window">
        <div className="minecraft-titlebar"><MiniText value={data.title ?? 'GUI'} /></div>
        <div className="minecraft-grid" style={{ gridTemplateRows: `repeat(${rows}, 44px)` }}>
          {occupancy.map((cell) => <button
            key={cell.index}
            className={`minecraft-slot ${cell.key ? 'occupied' : ''} ${selected.includes(cell.index) ? 'selected' : ''} ${cell.conflicts.length ? 'conflict' : ''}`}
            onClick={(event) => {
              if (event.ctrlKey || event.shiftKey) setSelected((current) => current.includes(cell.index) ? current.filter((n) => n !== cell.index) : [...current, cell.index]);
              else setSelected([cell.index]);
            }}
            onMouseEnter={() => setHovered(cell.index)}
            onMouseLeave={() => setHovered(null)}
            onDragOver={(event) => event.preventDefault()}
            onDrop={(event) => { event.preventDefault(); const material = event.dataTransfer.getData('text/material'); if (material) { setSelected([cell.index]); cell.key ? updateSlot(cell.key, { item: material }) : createSlot(cell.index, material); } }}
          >
            <SlotIcon slot={cell.slot} failed={failedImages} setFailed={setFailedImages} />
            <span className="slot-index">{cell.index}</span>
          </button>)}
        </div>
        {hoveredCell?.slot && <MinecraftTooltip slot={hoveredCell.slot} slotKey={hoveredCell.key ?? ''} />}
      </div>
      <aside className="gui-inspector">
        <div className="inspector-section">
          <h3>容器</h3>
          <label>ID<input value={textValue(data.id)} onChange={(e) => updateData((draft) => ({ ...draft, id: e.target.value }))} /></label>
          <label>标题<input value={textValue(data.title)} onChange={(e) => updateData((draft) => ({ ...draft, title: e.target.value }))} /></label>
          <label>行数<input type="number" min={1} max={6} value={rows} onChange={(e) => updateData((draft) => ({ ...draft, rows: clampRows(e.target.value) }))} /></label>
        </div>
        <div className="inspector-section">
          <h3>槽位 {selected.length ? selected.join(', ') : '未选择'}</h3>
          {selectedSlot && selectedKey ? <SlotInspector slotKey={selectedKey} slot={selectedSlot} updateSlot={updateSlot} removeSlot={() => updateData((draft) => { const next = { ...(draft.slots ?? {}) }; delete next[selectedKey]; return { ...draft, slots: next }; })} /> : selected.length ? <button className="wide-action" onClick={() => createSlot(selected[0])}>在 {selected[0]} 创建 slot</button> : <p className="muted-copy">点击网格槽位编辑，或从材料面板拖入物品。</p>}
        </div>
        <div className="inspector-section material-palette">
          <h3>材料源</h3>
          <input placeholder="搜索 material" value={query} onChange={(e) => setQuery(e.target.value)} />
          <div className="material-list">
            {COMMON_MATERIALS.filter((item) => item.toLowerCase().includes(query.toLowerCase())).map((material) => <button key={material} draggable onDragStart={(e) => e.dataTransfer.setData('text/material', material)} onClick={() => assignMaterial(material)}>
              <SlotIcon slot={{ item: material }} failed={failedImages} setFailed={setFailedImages} />
              <span>{material.toLowerCase()}</span>
            </button>)}
          </div>
        </div>
      </aside>
    </div>}
  </section>;
}

function SlotInspector({ slotKey, slot, updateSlot, removeSlot }: { slotKey: string; slot: GuiSlotDefinition; updateSlot: (key: string, patch: Partial<GuiSlotDefinition>) => void; removeSlot: () => void }) {
  const slotsText = parseSlotList(slot.slots).join(', ');
  return <div className="slot-form">
    <div className="slot-key"><code>{slotKey}</code><button onClick={removeSlot}>删除</button></div>
    <label>type<input value={textValue(slot.type)} onChange={(e) => updateSlot(slotKey, { type: e.target.value })} /></label>
    <label>item<input value={textValue(slot.item)} onChange={(e) => updateSlot(slotKey, { item: e.target.value })} /></label>
    <label>display_name<input value={textValue(slot.display_name)} onChange={(e) => updateSlot(slotKey, { display_name: e.target.value })} /></label>
    <label>slots<input value={slotsText} onChange={(e) => updateSlot(slotKey, { slots: e.target.value.split(/[ ,]+/).map((part) => Number(part)).filter(Number.isFinite) })} /></label>
    <label>lore<textarea value={loreLines(slot.lore).join('\n')} onChange={(e) => updateSlot(slotKey, { lore: e.target.value.split('\n') })} /></label>
  </div>;
}

function SlotIcon({ slot, failed, setFailed }: { slot?: GuiSlotDefinition | null; failed: Record<string, boolean>; setFailed: React.Dispatch<React.SetStateAction<Record<string, boolean>>> }) {
  const material = slot?.item ?? 'AIR';
  const normalized = String(material).toLowerCase();
  const url = materialUrl(material);
  if (!slot || !url || failed[normalized]) return <span className="material-fallback">{materialShortName(material)}</span>;
  return <img className="material-icon" src={url} alt="" draggable={false} onError={() => setFailed((current) => ({ ...current, [normalized]: true }))} />;
}

function MinecraftTooltip({ slot, slotKey }: { slot: GuiSlotDefinition; slotKey: string }) {
  const hidden = String(slot.hidden_components ?? '').includes('tooltip') || slot.hide_tooltip === true;
  if (hidden) return <div className="minecraft-tooltip muted-tooltip">Tooltip 已隐藏 · {slotKey}</div>;
  return <div className="minecraft-tooltip">
    <strong><MiniText value={slot.display_name ?? slot.item ?? slotKey} /></strong>
    {loreLines(slot.lore).map((line, index) => <span key={index}><MiniText value={line} /></span>)}
    {slot.item_model ? <small>item_model: {String(slot.item_model)}</small> : null}
    {slot.custom_model_data ? <small>custom_model_data: {String(slot.custom_model_data)}</small> : null}
  </div>;
}

function MiniText({ value }: { value: unknown }) {
  return <>{renderMiniMessageParts(value).map((part, index) => <span key={index} style={{ color: part.color }} className={part.token ? 'mini-token' : undefined}>{part.text}</span>)}</>;
}
