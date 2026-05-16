/**
 * Re-export from modular lib for backward compatibility.
 * New code should import from './lib' or './lib/itemUtils' directly.
 */
export type { AnyMap } from './lib/itemUtils';
export { asRecord, asList, asStringList, setDeepValue, cleanMap, itemKind, firstItemSource, materialFromItemSource, displaySource, effectsByType, mapEntries, variableEntries, slotRows, markOpenSlots, upgradeLevels, concise } from './lib/itemUtils';

// serializeItemYaml delegates to serializeGuiYaml
export { serializeGuiYaml as serializeItemYaml } from './lib/guiUtils';
export { parseYaml } from './lib/yaml';
