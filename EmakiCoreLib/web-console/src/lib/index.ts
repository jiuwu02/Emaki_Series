/**
 * Shared library re-exports.
 * Import from '@/lib' or '../lib' to access all utilities.
 */
export { materialUrls, materialUrl, materialShortName, normalizeMaterial, getTextureBases, subscribeTextureBases, MATERIAL_TEXTURE_SOURCES, MINECRAFT_ASSET_VERSION, MINECRAFT_TEXTURE_VERSION } from './materials';
export { serializeYaml, parseYaml } from './yaml';
export { fieldLabel, fieldComment, optionLabel, humanizeFieldLabel, lastPathKey, type FieldLabelOptions, type OptionLabelOptions } from './fieldI18n';
export { moduleDisplayName, moduleDisplaySummary, fileDisplayTitle, fileDisplayComment, treeNodeDisplayLabel, treeNodeDisplayComment, configNodeDisplayComment, registryFileKey, registryPathKey, moduleRegistryNamespace } from './registryI18n';
export { diffRecords, changedPathSet, isChangedPath, isChangedFieldPath, getDeepValue, valuesEqual, type ChangePathInput, type ChangePathMatchMode } from './changeTracking';
export { renderMiniMessageParts, textValue, type MiniMessagePart } from './miniMessage';
export { asRecord, asList, asStringList, setDeepValue, cleanMap, itemKind, firstItemSource, materialFromItemSource, displaySource, effectsByType, mapEntries, variableEntries, slotRows, markOpenSlots, upgradeLevels, concise, type AnyMap } from './itemUtils';
export { canonicalizeGuiSlotItem, canonicalizeGuiTemplateItems, canonicalizeItemDocument, itemDefinition, itemSourceValue, itemAmountValue, itemComponentsValue, LEGACY_ITEM_COMPONENT_KEYS, type ItemComponentCapability } from './itemStructure';
export { serializeItemYaml } from '../itemEditor';
export { BUKKIT_GUI_TYPES, DEFAULT_GUI_TYPE, clampRows, guiColumns, guiField, guiSlotCount, guiTypeOptions, normalizeGuiType, parseSlotList, buildOccupancy, loreLines, slotItemText, slotItemComponents, withSlotItem, serializeGuiYaml, supportsRows, COMMON_MATERIALS, type SlotOccupancy } from './guiUtils';
