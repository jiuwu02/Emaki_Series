import type { GuiSlotDefinition, GuiTemplateData, ItemComponentCapability } from '../types';
import { asRecord, type AnyMap } from './itemUtils';

export type { ItemComponentCapability } from '../types';

type CanonicalItemOptions = {
  sourceKeys: string[];
  amountKeys?: string[];
  componentKeys?: string[];
  legacyComponentsKey?: string;
};

export const LEGACY_ITEM_COMPONENT_KEYS = [
  'display_name', 'item_name', 'lore', 'custom_model_data', 'item_model', 'tooltip_style',
  'enchantments', 'item_flags', 'hidden_components', 'hide_tooltip', 'tooltip_display',
  'unbreakable', 'enchantment_glint_override', 'max_stack_size', 'rarity', 'damage',
  'max_damage', 'enchantable', 'attribute_modifiers', 'can_destroy', 'can_place_on',
  'trim', 'dye', 'raw'
] as const;

const LEGACY_COMPONENT_IDS: Record<string, string> = {
  display_name: 'minecraft:custom_name',
  item_name: 'minecraft:item_name',
  lore: 'minecraft:lore',
  item_model: 'minecraft:item_model',
  tooltip_style: 'minecraft:tooltip_style',
  enchantment_glint_override: 'minecraft:enchantment_glint_override',
  max_stack_size: 'minecraft:max_stack_size',
  rarity: 'minecraft:rarity',
  damage: 'minecraft:damage',
  max_damage: 'minecraft:max_damage',
  enchantable: 'minecraft:enchantable',
  can_destroy: 'minecraft:can_break',
  can_place_on: 'minecraft:can_place_on',
  trim: 'minecraft:trim',
  dye: 'minecraft:dyed_color'
};

const GUI_ITEM_OPTIONS: CanonicalItemOptions = {
  sourceKeys: ['item_source', 'item_sources', 'material', 'item'],
  amountKeys: ['amount'],
  componentKeys: [...LEGACY_ITEM_COMPONENT_KEYS],
  legacyComponentsKey: 'components'
};

const ITEM_DOCUMENT_OPTIONS: CanonicalItemOptions = {
  sourceKeys: ['material'],
  amountKeys: ['amount'],
  componentKeys: [...LEGACY_ITEM_COMPONENT_KEYS],
  legacyComponentsKey: 'components'
};

export function canonicalizeGuiSlotItem(slot: GuiSlotDefinition): GuiSlotDefinition {
  return canonicalizeItemContainer(slot as AnyMap, GUI_ITEM_OPTIONS) as GuiSlotDefinition;
}

export function canonicalizeGuiTemplateItems(data: GuiTemplateData): GuiTemplateData {
  const slots = canonicalizeSlotMap(data.slots);
  const virtualItems = canonicalizeSlotMap(data.virtual_items);
  if (slots === data.slots && virtualItems === data.virtual_items) return data;
  return {
    ...data,
    ...(slots === data.slots ? {} : { slots }),
    ...(virtualItems === data.virtual_items ? {} : { virtual_items: virtualItems })
  };
}

export function canonicalizeItemDocument(data: AnyMap): AnyMap {
  return canonicalizeItemContainer(data, ITEM_DOCUMENT_OPTIONS);
}

export function itemDefinition(container: unknown): AnyMap {
  return asRecord(asRecord(container).item);
}

export function itemSourceValue(container: unknown): unknown {
  const record = asRecord(container);
  const item = asRecord(record.item);
  if (Object.prototype.hasOwnProperty.call(item, 'source')) return item.source;
  for (const key of ['item_source', 'item_sources', 'material']) {
    if (Object.prototype.hasOwnProperty.call(record, key)) return record[key];
  }
  return typeof record.item === 'object' ? undefined : record.item;
}

export function itemAmountValue(container: unknown): unknown {
  const record = asRecord(container);
  const item = asRecord(record.item);
  return Object.prototype.hasOwnProperty.call(item, 'amount') ? item.amount : record.amount;
}

export function itemComponentsValue(container: unknown): AnyMap {
  const record = asRecord(container);
  const item = asRecord(record.item);
  if (Object.prototype.hasOwnProperty.call(item, 'components')) return migrateLegacyComponentMap(asRecord(item.components));
  const legacy = { ...asRecord(record.components) };
  for (const key of LEGACY_ITEM_COMPONENT_KEYS) {
    if (!Object.prototype.hasOwnProperty.call(legacy, key) && Object.prototype.hasOwnProperty.call(record, key)) legacy[key] = record[key];
  }
  return migrateLegacyComponentMap(legacy);
}

function canonicalizeSlotMap(value: Record<string, GuiSlotDefinition> | undefined): Record<string, GuiSlotDefinition> | undefined {
  if (!value) return value;
  let changed = false;
  const next = Object.fromEntries(Object.entries(value).map(([key, slot]) => {
    const canonical = canonicalizeGuiSlotItem(slot);
    if (canonical !== slot) changed = true;
    return [key, canonical];
  }));
  return changed ? next : value;
}

function canonicalizeItemContainer(container: AnyMap, options: CanonicalItemOptions): AnyMap {
  const rawItem = container.item;
  const canonicalItem = asRecord(rawItem);
  const legacyComponents = asRecord(options.legacyComponentsKey ? container[options.legacyComponentsKey] : undefined);
  const canonicalComponents = asRecord(canonicalItem.components);
  const hasLegacySource = options.sourceKeys.some(key => key === 'item' ? rawItem != null && (typeof rawItem !== 'object' || Array.isArray(rawItem)) : Object.prototype.hasOwnProperty.call(container, key));
  const hasLegacyAmount = (options.amountKeys ?? []).some(key => Object.prototype.hasOwnProperty.call(container, key));
  const hasLegacyComponents = Object.keys(legacyComponents).length > 0 || (options.componentKeys ?? []).some(key => Object.prototype.hasOwnProperty.call(container, key));
  const hasCanonicalItem = rawItem != null && typeof rawItem === 'object' && !Array.isArray(rawItem);
  if (!hasCanonicalItem && !hasLegacySource && !hasLegacyAmount && !hasLegacyComponents) return container;

  const source = Object.prototype.hasOwnProperty.call(canonicalItem, 'source')
    ? canonicalItem.source
    : firstOwnValue(container, options.sourceKeys, rawItem);
  const amount = Object.prototype.hasOwnProperty.call(canonicalItem, 'amount')
    ? canonicalItem.amount
    : firstOwnValue(container, options.amountKeys ?? []);
  const rawLegacyComponents: AnyMap = { ...legacyComponents };
  for (const key of options.componentKeys ?? []) {
    if (!Object.prototype.hasOwnProperty.call(rawLegacyComponents, key) && Object.prototype.hasOwnProperty.call(container, key)) rawLegacyComponents[key] = container[key];
  }
  const components = migrateLegacyComponentMap(rawLegacyComponents);
  Object.assign(components, migrateLegacyComponentMap(canonicalComponents));

  const item: AnyMap = { ...canonicalItem };
  if (source !== undefined) item.source = source;
  if (amount !== undefined) item.amount = amount;
  if (Object.keys(components).length || Object.prototype.hasOwnProperty.call(canonicalItem, 'components') || hasLegacyComponents) item.components = components;

  const next: AnyMap = { ...container, item };
  for (const key of options.sourceKeys) if (key !== 'item') delete next[key];
  for (const key of options.amountKeys ?? []) delete next[key];
  for (const key of options.componentKeys ?? []) delete next[key];
  if (options.legacyComponentsKey) delete next[options.legacyComponentsKey];
  return recordsEqual(container, next) ? container : next;
}

function firstOwnValue(container: AnyMap, keys: string[], rawItem?: unknown): unknown {
  for (const key of keys) {
    if (key === 'item') {
      if (rawItem != null && (typeof rawItem !== 'object' || Array.isArray(rawItem))) return rawItem;
    } else if (Object.prototype.hasOwnProperty.call(container, key)) {
      const value = container[key];
      if (Array.isArray(value)) return value.find(entry => entry != null && entry !== '') ?? value[0];
      return value;
    }
  }
  return undefined;
}

function migrateLegacyComponentMap(raw: AnyMap): AnyMap {
  const next: AnyMap = {};
  for (const [key, value] of Object.entries(raw)) {
    if (!LEGACY_ITEM_COMPONENT_KEYS.includes(key as typeof LEGACY_ITEM_COMPONENT_KEYS[number])) next[key] = value;
  }
  for (const [legacyId, componentId] of Object.entries(LEGACY_COMPONENT_IDS)) {
    if (!Object.prototype.hasOwnProperty.call(next, componentId) && Object.prototype.hasOwnProperty.call(raw, legacyId)) next[componentId] = raw[legacyId];
  }
  if (!Object.prototype.hasOwnProperty.call(next, 'minecraft:custom_model_data') && Object.prototype.hasOwnProperty.call(raw, 'custom_model_data')) {
    const value = raw.custom_model_data;
    const numeric = typeof value === 'number' ? value : typeof value === 'string' && value.trim() !== '' && Number.isFinite(Number(value)) ? Number(value) : undefined;
    next['minecraft:custom_model_data'] = numeric === undefined || isRecord(value) ? value : { floats: [numeric] };
  }
  if (!Object.prototype.hasOwnProperty.call(next, 'minecraft:enchantments') && Object.prototype.hasOwnProperty.call(raw, 'enchantments')) {
    const value = raw.enchantments;
    next['minecraft:enchantments'] = isRecord(value) && Object.prototype.hasOwnProperty.call(value, 'levels') ? value : { levels: normalizeEnchantments(value) };
  }
  if (!Object.prototype.hasOwnProperty.call(next, 'minecraft:unbreakable') && raw.unbreakable === true) next['minecraft:unbreakable'] = {};
  if (!Object.prototype.hasOwnProperty.call(next, 'minecraft:attribute_modifiers') && Object.prototype.hasOwnProperty.call(raw, 'attribute_modifiers')) {
    const value = raw.attribute_modifiers;
    next['minecraft:attribute_modifiers'] = isRecord(value) && Object.prototype.hasOwnProperty.call(value, 'modifiers') ? value : { modifiers: value };
  }
  if (!Object.prototype.hasOwnProperty.call(next, 'minecraft:tooltip_display')) {
    const tooltip = normalizeTooltipDisplay(raw);
    if (Object.keys(tooltip).length) next['minecraft:tooltip_display'] = tooltip;
  }
  if (Object.prototype.hasOwnProperty.call(raw, 'raw')) next.raw = raw.raw;
  return next;
}

function normalizeEnchantments(value: unknown): AnyMap {
  if (isRecord(value)) return Object.fromEntries(Object.entries(value).map(([id, level]) => [namespaced(id), level]));
  const result: AnyMap = {};
  if (!Array.isArray(value)) return result;
  for (const entry of value) {
    const text = String(entry).trim();
    if (!text) continue;
    const separator = text.lastIndexOf(':');
    const possibleLevel = separator > 0 ? Number(text.slice(separator + 1)) : NaN;
    const id = Number.isFinite(possibleLevel) ? text.slice(0, separator) : text;
    result[namespaced(id)] = Number.isFinite(possibleLevel) ? possibleLevel : 1;
  }
  return result;
}

function normalizeTooltipDisplay(raw: AnyMap): AnyMap {
  const existing = raw.tooltip_display;
  const tooltip: AnyMap = isRecord(existing) ? { ...existing } : {};
  const hidden = new Set<string>(Array.isArray(tooltip.hidden_components) ? tooltip.hidden_components.map(entry => namespaced(String(entry))) : []);
  const legacyHidden = Array.isArray(raw.hidden_components) ? raw.hidden_components : raw.hidden_components == null ? [] : [raw.hidden_components];
  for (const entry of legacyHidden) {
    const id = String(entry).trim().toLowerCase();
    if (id === 'tooltip' || id === '*') tooltip.hide_tooltip = true;
    else if (id) hidden.add(namespaced(id));
  }
  const flags = Array.isArray(raw.item_flags) ? raw.item_flags : raw.item_flags == null ? [] : [raw.item_flags];
  for (const flag of flags) {
    const componentId = hiddenComponentForFlag(String(flag));
    if (componentId) hidden.add(componentId);
  }
  if (hidden.size) tooltip.hidden_components = [...hidden];
  if (raw.hide_tooltip === true || existing === true) tooltip.hide_tooltip = true;
  return tooltip;
}

function hiddenComponentForFlag(value: string): string {
  const normalized = value.trim().toUpperCase();
  const known: Record<string, string> = {
    HIDE_ENCHANTS: 'minecraft:enchantments',
    HIDE_ATTRIBUTES: 'minecraft:attribute_modifiers',
    HIDE_UNBREAKABLE: 'minecraft:unbreakable',
    HIDE_DESTROYS: 'minecraft:can_break',
    HIDE_PLACED_ON: 'minecraft:can_place_on',
    HIDE_ADDITIONAL_TOOLTIP: 'minecraft:potion_contents',
    HIDE_POTION_EFFECTS: 'minecraft:potion_contents',
    HIDE_DYE: 'minecraft:dyed_color',
    HIDE_ARMOR_TRIM: 'minecraft:trim'
  };
  return known[normalized] ?? (normalized ? namespaced(normalized.toLowerCase()) : '');
}

function namespaced(value: string): string {
  const normalized = value.trim().toLowerCase();
  return !normalized || normalized.includes(':') ? normalized : `minecraft:${normalized}`;
}

function isRecord(value: unknown): value is AnyMap {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

function recordsEqual(left: unknown, right: unknown): boolean {
  try { return JSON.stringify(left) === JSON.stringify(right); } catch { return Object.is(left, right); }
}
