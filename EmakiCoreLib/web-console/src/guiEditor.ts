import type { GuiSlotDefinition, GuiTemplateData } from './types';

export type SlotOccupancy = {
  index: number;
  key: string | null;
  slot: GuiSlotDefinition | null;
  conflicts: string[];
};

export const MINECRAFT_ASSET_VERSION = '26.1.2';

export const MATERIAL_TEXTURE_BASES = [
  `https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@${MINECRAFT_ASSET_VERSION}/assets/minecraft/textures`,
  `https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/${MINECRAFT_ASSET_VERSION}/assets/minecraft/textures`
];

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

export function materialUrls(value: unknown): string[] {
  const material = normalizeMaterial(value);
  if (material === 'air') return [];
  const candidates = materialTextureCandidates(material);
  return MATERIAL_TEXTURE_BASES.flatMap((base) => candidates.map((candidate) => `${base}/${candidate.folder}/${candidate.name}.png`))
    .filter((url, index, arr) => arr.indexOf(url) === index);
}

export function materialUrl(value: unknown): string | null {
  return materialUrls(value)[0] ?? null;
}

type TextureCandidate = { folder: 'item' | 'block'; name: string };

function materialTextureCandidates(material: string): TextureCandidate[] {
  const names = materialAliases(material);
  const blockFirst = isLikelyBlock(material);
  const folders: Array<'item' | 'block'> = forceItemFirst(material) ? ['item', 'block'] : blockFirst ? ['block', 'item'] : ['item', 'block'];
  return names.flatMap((name) => folders.map((folder) => ({ folder, name })))
    .filter((entry, index, arr) => arr.findIndex((other) => other.folder === entry.folder && other.name === entry.name) === index);
}

function materialAliases(material: string): string[] {
  const aliases = [material];
  const direct: Record<string, string[]> = {
    water: ['water_bucket'], lava: ['lava_bucket'], powder_snow: ['powder_snow_bucket'], fire: ['flint_and_steel'], soul_fire: ['soul_torch'],
    cave_air: ['air'], void_air: ['air'], frosted_ice: ['ice'], moving_piston: ['piston'], piston_head: ['piston'],
    wall_torch: ['torch'], soul_wall_torch: ['soul_torch'], redstone_wall_torch: ['redstone_torch'],
    tripwire: ['tripwire_hook'], redstone_wire: ['redstone'], attached_melon_stem: ['melon_seeds'], melon_stem: ['melon_seeds'],
    attached_pumpkin_stem: ['pumpkin_seeds'], pumpkin_stem: ['pumpkin_seeds'], sweet_berry_bush: ['sweet_berries'],
    bubble_column: ['water_bucket'], nether_portal: ['obsidian'], end_portal: ['end_portal_frame'],
    potted_oak_sapling: ['oak_sapling'], potted_spruce_sapling: ['spruce_sapling'], potted_birch_sapling: ['birch_sapling'],
    potted_jungle_sapling: ['jungle_sapling'], potted_acacia_sapling: ['acacia_sapling'], potted_dark_oak_sapling: ['dark_oak_sapling'],
    potted_mangrove_propagule: ['mangrove_propagule'], potted_cherry_sapling: ['cherry_sapling'],
    suspicious_sand: ['sand'], suspicious_gravel: ['gravel']
  };
  aliases.push(...(direct[material] ?? []));
  if (material.endsWith('_stained_glass_pane')) aliases.push(material.replace('_stained_glass_pane', '_stained_glass'));
  if (material.endsWith('_wall_sign')) aliases.push(material.replace('_wall_sign', '_sign'));
  if (material.endsWith('_wall_hanging_sign')) aliases.push(material.replace('_wall_hanging_sign', '_hanging_sign'));
  if (material.endsWith('_wall_banner')) aliases.push(material.replace('_wall_banner', '_banner'));
  if (material.endsWith('_bed')) aliases.push(material);
  if (material.endsWith('_door')) aliases.push(material);
  if (material.endsWith('_trapdoor')) aliases.push(material);
  if (material.endsWith('_boat') || material.endsWith('_chest_boat')) aliases.push(material);
  if (material.endsWith('_spawn_egg')) aliases.push(material);
  if (material.endsWith('_candle_cake')) aliases.push(material.replace('_candle_cake', '_candle'), 'cake');
  if (material.endsWith('_crop')) aliases.push(material.replace('_crop', ''));
  if (material.endsWith('_cauldron')) aliases.push('cauldron');
  return aliases.filter((entry, index, arr) => entry !== 'air' && arr.indexOf(entry) === index);
}

function forceItemFirst(material: string): boolean {
  return /(_door|_boat|_chest_boat|_spawn_egg|_bucket|_sword|_pickaxe|_axe|_shovel|_hoe|_helmet|_chestplate|_leggings|_boots|_sign|_banner|potion|arrow|book|map|compass|clock|elytra|shield|trident|bow|crossbow|template|sherd|disc)$/.test(material);
}

function isLikelyBlock(material: string): boolean {
  return /(_block|_ore|_log|_wood|_planks|_leaves|_sapling|_pane|_glass|_wool|_carpet|_terracotta|_concrete|_bricks|_stairs|_slab|_fence|_gate|_door|_trapdoor|_torch|_lantern|_furnace|_table|_chest|_copper|_grate|_bulb|_tuff|_trial_spawner|_vault|stone|dirt|sand|gravel|obsidian|netherrack|basalt|calcite)$/.test(material);
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
