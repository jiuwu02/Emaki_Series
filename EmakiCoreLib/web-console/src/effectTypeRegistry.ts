import type { AnyMap } from './lib/itemUtils';

/**
 * Effect type registry — lets every plugin describe the effect types its
 * `effects` lists support and exactly which payload fields each type owns.
 * The unified StandardEffectsEditor renders ONLY the fields of the selected
 * type, so e.g. a `variables` effect never shows EA attribute / ES skill inputs.
 *
 * Keyed by (normalized) moduleId. Core built-in types are the fallback when a
 * module registers nothing.
 */

export type EffectPayloadFieldType =
  | 'variablesMap'
  | 'map'
  | 'stringList'
  | 'numberList'
  | 'actions'
  | 'enum'
  | 'text'
  | 'number'
  | 'boolean';

export type EffectPayloadField = {
  /** Payload key written into the effect object, e.g. 'variables', 'ea_attributes'. */
  key: string;
  type: EffectPayloadFieldType;
  /** Fallback label when no i18n key resolves. */
  label?: string;
  /** enum options. */
  options?: string[];
  optionLabelPrefix?: string;
  /** Default value when the effect type is created. */
  defaultValue?: unknown;
  /** For type 'actions': which action chain mode. */
  actionMode?: 'name' | 'lore';
};

export type EffectTypeDefinition = {
  /** Discriminator value stored in `type`. */
  type: string;
  /** Fallback label for the type selector. */
  label?: string;
  fields: EffectPayloadField[];
};

/** Core built-in effect types provided by CoreLib (assembly model). */
export const CORE_EFFECT_TYPE_DEFINITIONS: EffectTypeDefinition[] = [
  { type: 'variables', label: '变量', fields: [{ key: 'variables', type: 'variablesMap', label: '变量', defaultValue: {} }] },
  { type: 'name_action', label: '名称动作链', fields: [{ key: 'name_actions', type: 'actions', actionMode: 'name', label: '名称动作链', defaultValue: [] }] },
  { type: 'lore_action', label: 'Lore 动作链', fields: [{ key: 'lore_actions', type: 'actions', actionMode: 'lore', label: 'Lore 动作链', defaultValue: [] }] }
];

/** Reusable definition: EmakiAttribute attribute map effect. */
export const EA_ATTRIBUTE_EFFECT_DEFINITION: EffectTypeDefinition = {
  type: 'ea_attribute',
  label: 'EA 属性',
  fields: [{ key: 'ea_attributes', type: 'map', label: 'EA 属性', defaultValue: {} }]
};

/** Reusable definition: EmakiSkills skill id list effect. */
export const ES_SKILL_EFFECT_DEFINITION: EffectTypeDefinition = {
  type: 'es_skill',
  label: 'ES 技能',
  fields: [{ key: 'es_skills', type: 'stringList', label: 'ES 技能', defaultValue: [] }]
};

const _byModule: Record<string, EffectTypeDefinition[]> = {};

function normalizeModuleId(moduleId: string | undefined): string {
  return String(moduleId ?? '').trim().toLowerCase();
}

function cloneField(field: EffectPayloadField): EffectPayloadField {
  return { ...field, options: field.options ? [...field.options] : undefined };
}

function cloneDefinition(def: EffectTypeDefinition): EffectTypeDefinition {
  return { type: def.type, label: def.label, fields: def.fields.map(cloneField) };
}

/**
 * Register the ordered list of effect types a module's `effects` lists support.
 * Replaces any previous registration for the same module.
 */
export function registerEffectTypes(moduleId: string, definitions: EffectTypeDefinition[]): void {
  const key = normalizeModuleId(moduleId);
  if (!key || !Array.isArray(definitions)) return;
  _byModule[key] = definitions.filter(def => def?.type).map(cloneDefinition);
}

/** Get the effect type definitions for a module (falls back to core types). */
export function getEffectTypeDefinitions(moduleId?: string): EffectTypeDefinition[] {
  const key = normalizeModuleId(moduleId);
  const registered = key ? _byModule[key] : undefined;
  return (registered && registered.length ? registered : CORE_EFFECT_TYPE_DEFINITIONS).map(cloneDefinition);
}

/** Find a specific effect type definition for a module. */
export function getEffectTypeDefinition(moduleId: string | undefined, type: string): EffectTypeDefinition | undefined {
  return getEffectTypeDefinitions(moduleId).find(def => def.type === type);
}

/** Build a fresh effect object with default payload for the given type definition. */
export function createEffectValue(def: EffectTypeDefinition): AnyMap {
  const effect: AnyMap = { type: def.type };
  for (const field of def.fields) {
    effect[field.key] = field.defaultValue !== undefined ? field.defaultValue : defaultForFieldType(field.type);
  }
  return effect;
}

function defaultForFieldType(type: EffectPayloadFieldType): unknown {
  if (type === 'variablesMap' || type === 'map') return {};
  if (type === 'stringList' || type === 'numberList' || type === 'actions') return [];
  if (type === 'number') return 0;
  if (type === 'boolean') return false;
  return '';
}
