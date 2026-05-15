/**
 * GUI template data utilities.
 */
import type { GuiSlotDefinition, GuiTemplateData } from '../types';
import { serializeYaml } from './yaml';

export type SlotOccupancy = {
  index: number;
  key: string | null;
  slot: GuiSlotDefinition | null;
  conflicts: string[];
};

export function clampRows(value: unknown): number {
  const parsed = Number(value ?? 3);
  if (!Number.isFinite(parsed)) return 3;
  return Math.max(1, Math.min(6, Math.round(parsed)));
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
  const rows = clampRows(data.rows);
  const count = rows * 9;
  const occupancy: SlotOccupancy[] = Array.from({ length: count }, (_, index) => ({ index, key: null, slot: null, conflicts: [] }));
  const slots = data.slots ?? {};
  for (const [key, slot] of Object.entries(slots)) {
    for (const index of parseSlotList(slot?.slots)) {
      if (index < 0 || index >= count) continue;
      const cell = occupancy[index];
      if (cell.key) {
        cell.conflicts = [...cell.conflicts, cell.key, key].filter((entry, i, arr) => arr.indexOf(entry) === i);
      } else {
        cell.key = key;
        cell.slot = slot;
      }
    }
  }
  return occupancy;
}

export function loreLines(value: unknown): string[] {
  if (Array.isArray(value)) return value.map((entry) => String(entry));
  if (typeof value === 'string') return value ? [value] : [];
  if (value && typeof value === 'object') return ['<dark_gray>复杂 Lore 配置，请在源码中编辑</dark_gray>'];
  return [];
}

export function serializeGuiYaml(data: GuiTemplateData): string {
  return serializeYaml(data as Record<string, unknown>);
}

export const COMMON_MATERIALS = [
  'STONE', 'BARRIER', 'BOOK', 'WRITABLE_BOOK', 'ARROW', 'ANVIL', 'COMPASS', 'BLAST_FURNACE', 'ENDER_EYE', 'SUNFLOWER',
  'GRAY_STAINED_GLASS_PANE', 'BLACK_STAINED_GLASS_PANE', 'WHITE_STAINED_GLASS_PANE', 'LIGHT_BLUE_STAINED_GLASS_PANE',
  'LIME_STAINED_GLASS_PANE', 'GREEN_STAINED_GLASS_PANE', 'YELLOW_STAINED_GLASS_PANE', 'CHEST', 'DIAMOND', 'EMERALD'
];
