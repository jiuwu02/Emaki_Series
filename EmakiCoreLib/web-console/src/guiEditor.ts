/**
 * Re-export from modular lib for backward compatibility.
 * New code should import from './lib' or './lib/materials' directly.
 */
export type { SlotOccupancy } from './lib/guiUtils';
export { MINECRAFT_ASSET_VERSION, MINECRAFT_TEXTURE_VERSION, MATERIAL_TEXTURE_SOURCES, getTextureBases, MATERIAL_TEXTURE_BASES, subscribeTextureBases, materialUrls, materialUrl, materialShortName, normalizeMaterial } from './lib/materials';
export { BUKKIT_GUI_TYPES, DEFAULT_GUI_TYPE, clampRows, guiColumns, guiField, guiSlotCount, guiTypeOptions, normalizeGuiType, parseSlotList, buildOccupancy, loreLines, slotItemText, serializeGuiYaml, supportsRows, COMMON_MATERIALS, withSlotItem } from './lib/guiUtils';
export { textValue, renderMiniMessageParts } from './lib/miniMessage';
export { serializeYaml, parseYaml } from './lib/yaml';
export { fieldLabel, optionLabel } from './lib/fieldI18n';
