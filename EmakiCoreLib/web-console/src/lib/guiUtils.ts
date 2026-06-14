/**
 * GUI template data utilities.
 */
import type { GuiSlotDefinition, GuiTemplateData, WebEditorDescriptor, WebEditorField } from '../types';
import { getLocale } from '../i18n';
import { serializeYaml } from './yaml';

export type SlotOccupancy = {
  index: number;
  key: string | null;
  slot: GuiSlotDefinition | null;
  conflicts: string[];
  overlays: Array<{ key: string; slot: GuiSlotDefinition }>;
};

export const DEFAULT_GUI_TYPE = 'CHEST';

export const BUKKIT_GUI_TYPES: Record<string, { size: number; supportsRows: boolean; columns?: number; label: string }> = {
  CHEST: { size: 27, supportsRows: true, columns: 9, label: '箱子' },
  DISPENSER: { size: 9, supportsRows: false, columns: 3, label: '发射器' },
  DROPPER: { size: 9, supportsRows: false, columns: 3, label: '投掷器' },
  FURNACE: { size: 3, supportsRows: false, columns: 3, label: '熔炉' },
  WORKBENCH: { size: 10, supportsRows: false, columns: 5, label: '工作台' },
  CRAFTING: { size: 5, supportsRows: false, columns: 5, label: '玩家合成' },
  ENCHANTING: { size: 2, supportsRows: false, columns: 2, label: '附魔台' },
  BREWING: { size: 5, supportsRows: false, columns: 5, label: '酿造台' },
  PLAYER: { size: 41, supportsRows: false, columns: 9, label: '玩家背包' },
  CREATIVE: { size: 0, supportsRows: false, columns: 9, label: '创造模式' },
  MERCHANT: { size: 3, supportsRows: false, columns: 3, label: '村民交易' },
  ENDER_CHEST: { size: 27, supportsRows: false, columns: 9, label: '末影箱' },
  ANVIL: { size: 3, supportsRows: false, columns: 3, label: '铁砧' },
  SMITHING: { size: 4, supportsRows: false, columns: 4, label: '锻造台' },
  BEACON: { size: 1, supportsRows: false, columns: 1, label: '信标' },
  HOPPER: { size: 5, supportsRows: false, columns: 5, label: '漏斗' },
  SHULKER_BOX: { size: 27, supportsRows: false, columns: 9, label: '潜影盒' },
  BARREL: { size: 27, supportsRows: false, columns: 9, label: '木桶' },
  BLAST_FURNACE: { size: 3, supportsRows: false, columns: 3, label: '高炉' },
  LECTERN: { size: 1, supportsRows: false, columns: 1, label: '讲台' },
  SMOKER: { size: 3, supportsRows: false, columns: 3, label: '烟熏炉' },
  LOOM: { size: 4, supportsRows: false, columns: 4, label: '织布机' },
  CARTOGRAPHY: { size: 3, supportsRows: false, columns: 3, label: '制图台' },
  GRINDSTONE: { size: 3, supportsRows: false, columns: 3, label: '砂轮' },
  STONECUTTER: { size: 2, supportsRows: false, columns: 2, label: '切石机' },
  COMPOSTER: { size: 1, supportsRows: false, columns: 1, label: '堆肥桶' },
  CHISELED_BOOKSHELF: { size: 6, supportsRows: false, columns: 3, label: '雕纹书架' },
  JUKEBOX: { size: 1, supportsRows: false, columns: 1, label: '唱片机' },
  CRAFTER: { size: 10, supportsRows: false, columns: 5, label: '合成器' }
};

export function normalizeGuiType(dataOrType: GuiTemplateData | string | undefined): string {
  const raw = typeof dataOrType === 'string' ? dataOrType : dataOrType?.gui_type;
  const normalized = String(raw || DEFAULT_GUI_TYPE).trim().toUpperCase();
  return BUKKIT_GUI_TYPES[normalized] ? normalized : DEFAULT_GUI_TYPE;
}

export function supportsRows(type: string | undefined): boolean {
  return BUKKIT_GUI_TYPES[normalizeGuiType(type)]?.supportsRows === true;
}

export function clampRows(value: unknown): number {
  const parsed = Number(value ?? 3);
  if (!Number.isFinite(parsed)) return 3;
  return Math.max(1, Math.min(6, Math.round(parsed)));
}

export function guiSlotCount(dataOrType: GuiTemplateData | string | undefined, rowsValue?: unknown): number {
  const type = normalizeGuiType(dataOrType);
  if (supportsRows(type)) {
    const rows = typeof dataOrType === 'string' ? clampRows(rowsValue) : clampRows(dataOrType?.rows ?? rowsValue);
    return rows * 9;
  }
  return BUKKIT_GUI_TYPES[type]?.size ?? 27;
}

export function guiColumns(dataOrType: GuiTemplateData | string | undefined): number {
  const type = normalizeGuiType(dataOrType);
  return BUKKIT_GUI_TYPES[type]?.columns ?? 9;
}

export function guiTypeOptions(): string[] {
  return Object.keys(BUKKIT_GUI_TYPES);
}

export function guiField(editor: WebEditorDescriptor | undefined, path: string, fallbackLabel?: string, fallbackType = 'text'): WebEditorField {
  const exact = editor?.fields?.[path];
  if (exact) return exact;
  const last = path.includes('.') ? path.substring(path.lastIndexOf('.') + 1) : path;
  const loose = editor?.fields?.[last];
  if (loose) return { ...loose, path };
  return { path, label: fallbackLabel || last.replace(/_/g, ' '), type: fallbackType };
}

export function parseSlotList(value: unknown): number[] {
  if (typeof value === 'number') return [value];
  if (Array.isArray(value)) return value.flatMap(parseSlotList);
  if (typeof value === 'string') {
    return value.split(/[ ,]+/).flatMap((part) => {
      const range = part.match(/^(\d+)\s*-\s*(\d+)$/);
      if (range) {
        const start = Number(range[1]);
        const end = Number(range[2]);
        const min = Math.min(start, end);
        const max = Math.max(start, end);
        return Array.from({ length: max - min + 1 }, (_, i) => min + i);
      }
      const parsed = Number(part);
      return Number.isFinite(parsed) ? [parsed] : [];
    });
  }
  return [];
}

export function buildOccupancy(data: GuiTemplateData): SlotOccupancy[] {
  const count = guiSlotCount(data);
  const occupancy: SlotOccupancy[] = Array.from({ length: count }, (_, index) => ({ index, key: null, slot: null, conflicts: [], overlays: [] }));
  const slots = data.slots ?? {};
  for (const [key, slot] of Object.entries(slots)) {
    for (const index of parseSlotList(slot?.slots)) {
      if (index < 0 || index >= count) continue;
      const cell = occupancy[index];
      cell.overlays.push({ key, slot });
      if (cell.key == null || (!slotDiscriminator(cell.slot) && slotDiscriminator(slot))) {
        cell.key = key;
        cell.slot = slot;
      }
    }
  }
  for (const cell of occupancy) {
    const staticKeys = cell.overlays
      .filter((overlay) => !slotDiscriminator(overlay.slot))
      .map((overlay) => overlay.key);
    cell.conflicts = staticKeys.length > 1 ? staticKeys : [];
  }
  return occupancy;
}

function slotDiscriminator(slot: GuiSlotDefinition | null | undefined): string {
  return String(slot?.type ?? '').trim();
}

export function loreLines(value: unknown): string[] {
  if (Array.isArray(value)) return value.map((entry) => String(entry));
  if (typeof value === 'string') return value ? [value] : [];
  if (value && typeof value === 'object') return [getLocale().startsWith('zh') ? '<dark_gray>复杂 Lore 配置，请在源码中编辑</dark_gray>' : '<dark_gray>Complex lore config. Edit it in source mode.</dark_gray>'];
  return [];
}

export function slotItemText(slot: GuiSlotDefinition | null | undefined): string {
  if (!slot) return '';
  return itemSourceText(slot.item_source) || itemSourceText(slot.item_sources) || itemSourceText(slot.material) || itemSourceText(slot.item);
}

export function withSlotItem(slot: GuiSlotDefinition, item: unknown): GuiSlotDefinition {
  const next = { ...slot };
  delete next.item;
  return { ...next, item_source: item == null ? undefined : String(item) };
}

function itemSourceText(value: unknown): string {
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) return value.map(itemSourceText).find(Boolean) ?? '';
  return '';
}

export function serializeGuiYaml(data: GuiTemplateData): string {
  return serializeYaml(data as Record<string, unknown>);
}

export const COMMON_MATERIALS = [
  'STONE', 'BARRIER', 'BOOK', 'WRITABLE_BOOK', 'ARROW', 'ANVIL', 'COMPASS', 'BLAST_FURNACE', 'ENDER_EYE', 'SUNFLOWER',
  'GRAY_STAINED_GLASS_PANE', 'BLACK_STAINED_GLASS_PANE', 'WHITE_STAINED_GLASS_PANE', 'LIGHT_BLUE_STAINED_GLASS_PANE',
  'LIME_STAINED_GLASS_PANE', 'GREEN_STAINED_GLASS_PANE', 'YELLOW_STAINED_GLASS_PANE', 'CHEST', 'DIAMOND', 'EMERALD'
];
