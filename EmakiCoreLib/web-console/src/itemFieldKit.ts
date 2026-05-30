import type { WebEditorField } from './types';

export const CORE_ITEM_FIELD_TYPES = ['text', 'number', 'boolean', 'enum', 'multiEnum', 'material', 'textarea', 'stringList', 'numberList', 'map', 'dynamicMap', 'objectMap', 'variablesMap', 'json', 'actions'] as const;

export const STANDARD_ITEM_FIELD_TYPES = ['effects'] as const;

export const STANDARD_ITEM_ALLOWED_FIELD_TYPES = [...CORE_ITEM_FIELD_TYPES, ...STANDARD_ITEM_FIELD_TYPES] as const;

export const CORE_ITEM_FIELD_TYPE_SET = new Set<string>(CORE_ITEM_FIELD_TYPES);

export function standardDisplayActionFields(): WebEditorField[] {
  return [
    { path: 'name_actions', label: 'Name Actions', type: 'actions', wide: true },
    { path: 'lore_actions', label: 'Lore Actions', type: 'actions', wide: true }
  ];
}
