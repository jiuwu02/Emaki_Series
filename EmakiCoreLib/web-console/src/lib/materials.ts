/**
 * Minecraft material texture URL resolution.
 * Handles CDN latency probing, multi-source fallback, and material name normalization.
 */

export const MINECRAFT_ASSET_VERSION = '26.1.2';

export const MATERIAL_TEXTURE_SOURCES = [
  { id: 'jsdelivr-inventive', base: `https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@${MINECRAFT_ASSET_VERSION}/assets/minecraft/textures` },
  { id: 'jsdelivr-mcmeta', base: `https://cdn.jsdelivr.net/gh/misode/mcmeta@1.21.4-assets/assets/minecraft/textures` },
  { id: 'jsdelivr-pixigeko', base: `https://cdn.jsdelivr.net/gh/PixiGeko/Minecraft-default-assets@latest/assets/minecraft/textures` },
  { id: 'github-inventive', base: `https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/${MINECRAFT_ASSET_VERSION}/assets/minecraft/textures` },
  { id: 'github-mcmeta', base: `https://raw.githubusercontent.com/misode/mcmeta/1.21.4-assets/assets/minecraft/textures` },
];

// Latency probe: test each source once and reorder
let _resolvedBases: string[] | null = null;
let _probePromise: Promise<string[]> | null = null;

function probeLatency(): Promise<string[]> {
  if (_resolvedBases) return Promise.resolve(_resolvedBases);
  if (_probePromise) return _probePromise;

  _probePromise = (async () => {
    const testFile = '/item/diamond.png';
    const results: Array<{ base: string; time: number }> = [];

    const probes = MATERIAL_TEXTURE_SOURCES.map(async (source) => {
      const start = performance.now();
      try {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 4000);
        await fetch(source.base + testFile, { method: 'HEAD', signal: controller.signal });
        clearTimeout(timeout);
        results.push({ base: source.base, time: performance.now() - start });
      } catch {
        results.push({ base: source.base, time: 9999 });
      }
    });

    await Promise.allSettled(probes);
    results.sort((a, b) => a.time - b.time);
    _resolvedBases = results.map(r => r.base);
    return _resolvedBases;
  })();

  return _probePromise;
}

// Start probe immediately on module load
probeLatency();

/** Get texture base URLs ordered by latency (fastest first). */
export function getTextureBases(): string[] {
  return _resolvedBases ?? MATERIAL_TEXTURE_SOURCES.map(s => s.base);
}

/** Legacy export for compatibility. */
export const MATERIAL_TEXTURE_BASES = MATERIAL_TEXTURE_SOURCES.map(s => s.base);

export function normalizeMaterial(value: unknown): string {
  const raw = String(value ?? '').trim();
  if (!raw || raw.toUpperCase() === 'AIR') return 'air';
  const withoutNamespace = raw.includes(':') ? raw.split(':').pop()! : raw;
  return withoutNamespace.toLowerCase().replace(/[^a-z0-9_]/g, '_');
}

/** Get all candidate texture URLs for a material, ordered by CDN speed. */
export function materialUrls(value: unknown): string[] {
  const material = normalizeMaterial(value);
  if (material === 'air') return [];
  const candidates = materialTextureCandidates(material);
  const bases = getTextureBases();
  return candidates.flatMap((candidate) => bases.map((base) => `${base}/${candidate.folder}/${candidate.name}.png`))
    .filter((url, index, arr) => arr.indexOf(url) === index);
}

/** Get the first (best) texture URL for a material. */
export function materialUrl(value: unknown): string | null {
  return materialUrls(value)[0] ?? null;
}

/** Get a short abbreviation for a material name (for fallback display). */
export function materialShortName(value: unknown): string {
  const material = normalizeMaterial(value);
  if (material === 'air') return '';
  const parts = material.split('_').filter(Boolean);
  return parts.slice(-3).map((part) => part[0]?.toUpperCase() ?? '').join('') || material.slice(0, 3).toUpperCase();
}

// --- Internal helpers ---

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
