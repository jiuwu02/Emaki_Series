/**
 * Minecraft material texture URL resolution.
 * Handles CDN latency probing, multi-source fallback, and material name normalization.
 */

export const MINECRAFT_ASSET_VERSION = '26.1.2';
export const MINECRAFT_TEXTURE_VERSION = MINECRAFT_ASSET_VERSION;

export type MaterialTextureSource = {
  id: string;
  base: string;
  region?: 'cn' | 'global';
  priority: number;
};

export const MATERIAL_TEXTURE_SOURCES: MaterialTextureSource[] = [
  // mcasset.cloud proxies the official InventivetalentDev asset tree and serves real PNGs without a GitHub raw redirect.
  // It was the only tested public source that stayed usable when raw.githubusercontent.com could not be resolved.
  { id: 'mcasset-proxy', region: 'global', priority: 10, base: `https://assets.mcasset.cloud/${MINECRAFT_TEXTURE_VERSION}/assets/minecraft/textures` },
  // Mainland-friendly GitHub raw proxies. Kept after mcasset so cache misses do not block first paint.
  { id: 'ghfast-inventive', region: 'cn', priority: 20, base: `https://ghfast.top/https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/${MINECRAFT_TEXTURE_VERSION}/assets/minecraft/textures` },
  { id: 'gh-proxy-inventive', region: 'cn', priority: 30, base: `https://gh-proxy.com/raw.githubusercontent.com/InventivetalentDev/minecraft-assets/${MINECRAFT_TEXTURE_VERSION}/assets/minecraft/textures` },
  // jsDelivr can be quick when it is warm, but this repository redirects misses to raw.githubusercontent.com.
  { id: 'jsdelivr-inventive', region: 'global', priority: 80, base: `https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@${MINECRAFT_ASSET_VERSION}/assets/minecraft/textures` },
  // Direct GitHub raw stays as last-resort only. In China mainland it commonly fails DNS or stalls.
  { id: 'github-inventive', region: 'global', priority: 500, base: `https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/${MINECRAFT_TEXTURE_VERSION}/assets/minecraft/textures` },
];

const TEXTURE_SOURCE_STORAGE_KEY = 'emaki-material-texture-bases:v2';
const PROBE_TIMEOUT_MS = 1800;
const PROBE_TEST_FILES = ['/item/diamond.png', '/block/acacia_planks.png'];

let _resolvedBases: string[] | null = readCachedBases();
let _probePromise: Promise<string[]> | null = null;
let _listeners: Array<() => void> = [];

function notifyTextureBasesChanged() {
  _listeners.forEach((listener) => listener());
}

export function subscribeTextureBases(listener: () => void): () => void {
  _listeners = [..._listeners, listener];
  return () => { _listeners = _listeners.filter((entry) => entry !== listener); };
}

function readCachedBases(): string[] | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    const cached = JSON.parse(localStorage.getItem(TEXTURE_SOURCE_STORAGE_KEY) || 'null') as { bases?: string[]; savedAt?: number } | null;
    if (!cached?.bases?.length || !cached.savedAt) return null;
    if (Date.now() - cached.savedAt > 1000 * 60 * 60 * 24 * 7) return null;
    const known = new Set(MATERIAL_TEXTURE_SOURCES.map((source) => source.base));
    const valid = cached.bases.filter((base) => known.has(base));
    return valid.length ? valid : null;
  } catch {
    return null;
  }
}

function writeCachedBases(bases: string[]) {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(TEXTURE_SOURCE_STORAGE_KEY, JSON.stringify({ bases, savedAt: Date.now() }));
  } catch {
    // Ignore storage quota or private-mode failures. The default source order remains safe.
  }
}

async function probeSource(source: MaterialTextureSource): Promise<{ base: string; score: number }> {
  const start = performance.now();
  try {
    const results = await Promise.all(PROBE_TEST_FILES.map((file) => loadProbeImage(source.base + file, PROBE_TIMEOUT_MS).then(() => true, () => false)));
    if (!results.some(Boolean)) throw new Error('texture source unavailable');
    return { base: source.base, score: performance.now() - start + source.priority };
  } catch {
    return { base: source.base, score: 100000 + source.priority };
  }
}

function loadProbeImage(url: string, timeoutMs: number): Promise<void> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const timer = window.setTimeout(() => {
      image.onload = null;
      image.onerror = null;
      image.src = '';
      reject(new Error('texture probe timeout'));
    }, timeoutMs);
    image.onload = () => { window.clearTimeout(timer); resolve(); };
    image.onerror = () => { window.clearTimeout(timer); reject(new Error('texture probe failed')); };
    image.decoding = 'async';
    image.referrerPolicy = 'no-referrer';
    image.src = url;
  });
}

function probeLatency(): Promise<string[]> {
  if (_probePromise) return _probePromise;

  _probePromise = (async () => {
    const results = await Promise.all(MATERIAL_TEXTURE_SOURCES.map(probeSource));
    results.sort((a, b) => a.score - b.score);
    _resolvedBases = results.map((result) => result.base);
    writeCachedBases(_resolvedBases);
    notifyTextureBasesChanged();
    return _resolvedBases;
  })();

  return _probePromise;
}

if (typeof window !== 'undefined') {
  window.setTimeout(() => { void probeLatency(); }, 0);
}

/** Get texture base URLs ordered by measured image load speed. */
export function getTextureBases(): string[] {
  return _resolvedBases ?? MATERIAL_TEXTURE_SOURCES
    .slice()
    .sort((a, b) => a.priority - b.priority)
    .map(s => s.base);
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
    suspicious_sand: ['sand'], suspicious_gravel: ['gravel'],
    bamboo_boat: ['bamboo_raft', 'bamboo'], bamboo_chest_boat: ['bamboo_chest_raft', 'bamboo_raft', 'bamboo'],
    bamboo_log: ['bamboo_block', 'stripped_bamboo_block', 'bamboo_planks'], bamboo_wood: ['bamboo_block', 'stripped_bamboo_block', 'bamboo_planks'],
    bamboo_leaves: ['bamboo_large_leaves', 'bamboo_small_leaves', 'bamboo'], bamboo_sapling: ['bamboo']
  };
  aliases.push(...(direct[material] ?? []));
  if (material.endsWith('_stained_glass_pane')) aliases.push(material.replace('_stained_glass_pane', '_stained_glass'));
  if (material.endsWith('_wall_sign')) aliases.push(material.replace('_wall_sign', '_sign'));
  if (material.endsWith('_wall_hanging_sign')) aliases.push(material.replace('_wall_hanging_sign', '_hanging_sign'));
  if (material.endsWith('_wall_banner')) aliases.push(material.replace('_wall_banner', '_banner'));
  if (material.endsWith('_bed')) aliases.push(material);
  if (material.endsWith('_door')) aliases.push(material, material.replace('_door', '_planks'));
  if (material.endsWith('_trapdoor')) aliases.push(material, material.replace('_trapdoor', '_planks'));
  if (material.endsWith('_fence')) aliases.push(material.replace('_fence', '_planks'));
  if (material.endsWith('_fence_gate')) aliases.push(material.replace('_fence_gate', '_planks'));
  if (material.endsWith('_stairs')) aliases.push(material.replace('_stairs', '_planks'));
  if (material.endsWith('_slab')) aliases.push(material.replace('_slab', '_planks'));
  if (material.endsWith('_boat') || material.endsWith('_chest_boat')) aliases.push(material);
  if (material.endsWith('_spawn_egg')) aliases.push(material);
  if (material.endsWith('_candle_cake')) aliases.push(material.replace('_candle_cake', '_candle'), 'cake');
  if (material.endsWith('_crop')) aliases.push(material.replace('_crop', ''));
  if (material.endsWith('_cauldron')) aliases.push('cauldron');
  return aliases.filter((entry, index, arr) => entry !== 'air' && arr.indexOf(entry) === index);
}

function forceItemFirst(material: string): boolean {
  return /(_door|_boat|_chest_boat|_spawn_egg|_bucket|_sword|_pickaxe|_axe|_shovel|_hoe|_helmet|_chestplate|_leggings|_boots|_sign|_hanging_sign|_banner|barrier|light|structure_void|potion|arrow|book|map|compass|clock|elytra|shield|trident|bow|crossbow|template|sherd|disc)$/.test(material);
}

function isLikelyBlock(material: string): boolean {
  return /(_block|_ore|_log|_wood|_planks|_leaves|_sapling|_pane|_glass|_wool|_carpet|_terracotta|_concrete|_bricks|_stairs|_slab|_fence|_gate|_door|_trapdoor|_torch|_lantern|_furnace|_table|_chest|_copper|_grate|_bulb|_tuff|_trial_spawner|_vault|stone|dirt|sand|gravel|obsidian|netherrack|basalt|calcite)$/.test(material);
}
