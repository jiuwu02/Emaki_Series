/**
 * Shared library re-exports.
 * Import from '@/lib' or '../lib' to access all utilities.
 */
export { materialUrls, materialUrl, materialShortName, normalizeMaterial, getTextureBases, subscribeTextureBases, MATERIAL_TEXTURE_SOURCES, MATERIAL_TEXTURE_BASES, MINECRAFT_ASSET_VERSION, MINECRAFT_TEXTURE_VERSION } from './materials';
export { serializeYaml, parseYaml } from './yaml';
export { fieldLabel, optionLabel, humanizeFieldLabel, lastPathKey, type FieldLabelOptions, type OptionLabelOptions } from './fieldI18n';
export { diffRecords, changedPathSet, isChangedPath, isChangedFieldPath, getDeepValue, valuesEqual, type ChangePathInput, type ChangePathMatchMode } from './changeTracking';
export { renderMiniMessageParts, textValue, type MiniMessagePart } from './miniMessage';
export { asRecord, asList, asStringList, setDeepValue, cleanMap, itemKind, firstItemSource, materialFromItemSource, displaySource, effectsByType, mapEntries, variableEntries, slotRows, markOpenSlots, upgradeLevels, concise, type AnyMap } from './itemUtils';
export { serializeItemYaml } from '../itemEditor';
export { BUKKIT_GUI_TYPES, DEFAULT_GUI_TYPE, clampRows, guiColumns, guiField, guiSlotCount, guiTypeOptions, normalizeGuiType, parseSlotList, buildOccupancy, loreLines, serializeGuiYaml, supportsRows, COMMON_MATERIALS, type SlotOccupancy } from './guiUtils';
