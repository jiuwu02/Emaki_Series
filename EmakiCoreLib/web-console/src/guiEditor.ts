import type { GuiSlotDefinition, GuiTemplateData } from './types';

export type SlotOccupancy = {
  index: number;
  key: string | null;
  slot: GuiSlotDefinition | null;
  conflicts: string[];
};

export const MATERIAL_CDN = 'https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.20.4/assets/minecraft/textures/item';

export function clampRows(value: unknown): number {
  const parsed = Number(value ?? 3);
  if (!Number.isFinite(parsed)) return 3;
  return Math.max(1, Math.min(6, Math.round(parsed)));
}

export function normalizeMaterial(value: unknown): string {
  const raw = String(value ?? '').trim();
  if (!raw || raw.toUpperCase() === 'AIR') return 'air';
  const withoutNamespace = raw.includes(':') ? raw.split(':').pop()! : raw;
  return withoutNamespace.toLowerCase().replace(/[^a-z0-9_]/g, '_');
}

export function materialUrl(value: unknown): string | null {
  const material = normalizeMaterial(value);
  if (material === 'air') return null;
  return `${MATERIAL_CDN}/${material}.png`;
}

export function materialShortName(value: unknown): string {
  const material = normalizeMaterial(value);
  if (material === 'air') return '';
  const parts = material.split('_').filter(Boolean);
  return parts.slice(-3).map((part) => part[0]?.toUpperCase() ?? '').join('') || material.slice(0, 3).toUpperCase();
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

export function textValue(value: unknown, fallback = ''): string {
  if (typeof value === 'string') return value;
  if (value == null) return fallback;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return fallback;
}

export function renderMiniMessageParts(text: unknown): { text: string; color?: string; token?: boolean }[] {
  const source = textValue(text);
  const parts: { text: string; color?: string; token?: boolean }[] = [];
  const colorStack: string[] = [];
  const colorMap: Record<string, string> = {
    black: 'oklch(22% 0.01 260)', dark_gray: 'oklch(54% 0.01 260)', gray: 'oklch(70% 0.01 260)', white: 'oklch(92% 0.01 260)',
    red: 'oklch(68% 0.18 28)', dark_red: 'oklch(52% 0.16 28)', green: 'oklch(72% 0.16 150)', dark_green: 'oklch(56% 0.13 150)',
    yellow: 'oklch(84% 0.14 90)', gold: 'oklch(78% 0.14 75)', aqua: 'oklch(80% 0.11 200)', dark_aqua: 'oklch(62% 0.1 210)',
    blue: 'oklch(68% 0.14 255)', light_purple: 'oklch(76% 0.14 320)', dark_purple: 'oklch(58% 0.13 315)'
  };
  let buffer = '';
  const flush = () => {
    if (buffer) parts.push({ text: buffer, color: colorStack[colorStack.length - 1] });
    buffer = '';
  };
  for (let i = 0; i < source.length; i++) {
    if (source[i] === '<') {
      const end = source.indexOf('>', i);
      if (end > i) {
        const tag = source.slice(i + 1, end).replace('/', '').split(':')[0].toLowerCase();
        if (source[i + 1] === '/') {
          flush(); colorStack.pop(); i = end; continue;
        }
        if (colorMap[tag]) {
          flush(); colorStack.push(colorMap[tag]); i = end; continue;
        }
        if (tag === 'gradient') { flush(); colorStack.push(colorMap.light_purple); i = end; continue; }
      }
    }
    if (source[i] === '{') {
      const end = source.indexOf('}', i);
      if (end > i) {
        flush();
        parts.push({ text: source.slice(i, end + 1), color: 'oklch(56% 0.03 255)', token: true });
        i = end;
        continue;
      }
    }
    buffer += source[i];
  }
  flush();
  return parts.length ? parts : [{ text: source }];
}

export function serializeGuiYaml(data: GuiTemplateData): string {
  return dumpYaml(data).trimEnd() + '\n';
}

function dumpYaml(value: unknown, indent = 0): string {
  const space = ' '.repeat(indent);
  if (Array.isArray(value)) {
    if (value.every((entry) => typeof entry !== 'object' || entry == null)) return `[${value.map(formatScalar).join(', ')}]`;
    return value.map((entry) => `${space}- ${dumpYaml(entry, indent + 2).trimStart()}`).join('\n');
  }
  if (value && typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).map(([key, entry]) => {
      if (entry && typeof entry === 'object' && !Array.isArray(entry)) return `${space}${key}:\n${dumpYaml(entry, indent + 2)}`;
      if (Array.isArray(entry) && !entry.every((item) => typeof item !== 'object' || item == null)) return `${space}${key}:\n${dumpYaml(entry, indent + 2)}`;
      return `${space}${key}: ${dumpYaml(entry, indent + 2).trimStart()}`;
    }).join('\n');
  }
  return formatScalar(value);
}

function formatScalar(value: unknown): string {
  if (value == null) return 'null';
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  const text = String(value);
  if (!text) return '""';
  if (/^[a-zA-Z0-9_./:-]+$/.test(text) && !text.includes(': ')) return text;
  return JSON.stringify(text);
}

export const COMMON_MATERIALS = [
  'STONE', 'BARRIER', 'BOOK', 'WRITABLE_BOOK', 'ARROW', 'ANVIL', 'COMPASS', 'BLAST_FURNACE', 'ENDER_EYE', 'SUNFLOWER',
  'GRAY_STAINED_GLASS_PANE', 'BLACK_STAINED_GLASS_PANE', 'WHITE_STAINED_GLASS_PANE', 'LIGHT_BLUE_STAINED_GLASS_PANE',
  'LIME_STAINED_GLASS_PANE', 'GREEN_STAINED_GLASS_PANE', 'YELLOW_STAINED_GLASS_PANE', 'CHEST', 'DIAMOND', 'EMERALD'
];
