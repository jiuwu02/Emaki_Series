import type { WebConfigFieldSchema } from './types';
import type { ConfigRuleFieldEntry } from './registry';

export type StandardEconomyFieldOptions = {
  omit?: string[];
  overrides?: Record<string, Partial<WebConfigFieldSchema>>;
  insertAfter?: Record<string, WebConfigFieldSchema | WebConfigFieldSchema[]>;
  append?: WebConfigFieldSchema[];
};

export type StandardEconomyRuleOptions = {
  omit?: string[];
  overrides?: Record<string, ConfigRuleFieldEntry>;
  append?: Record<string, ConfigRuleFieldEntry>;
};

const STANDARD_CURRENCY_COST_FIELDS: WebConfigFieldSchema[] = [
  { path: 'provider', label: '经济提供器', comment: '经济系统提供器；auto 会按 currency_id 自动推断，其余为 CoreLib 已注册的经济提供器。', type: 'economyProvider', optionLabelPrefix: 'economyProvider', defaultValue: 'auto' },
  { path: 'currency_id', label: '货币 ID', comment: '多货币系统的货币标识；留空使用默认货币。', type: 'text', defaultValue: '' },
  { path: 'base_cost', label: '基础费用', comment: '经济消耗的基础数值。', type: 'number', defaultValue: 0 },
  { path: 'cost_formula', label: '费用公式', comment: '根据上下文变量计算最终费用的公式。', type: 'text', defaultValue: '' },
  { path: 'display_name', label: '显示名称', comment: '货币在提示中的显示名称。', type: 'text', defaultValue: '' }
];

const STANDARD_MATERIAL_COST_FIELDS: WebConfigFieldSchema[] = [
  { path: 'item_sources', label: '物品来源', comment: '作为材料的 ItemSource 列表。', type: 'stringList', defaultValue: ['minecraft-stone'] },
  { path: 'amount', label: '数量', comment: '需要消耗或检查的材料数量。', type: 'number', defaultValue: 1 },
  { path: 'optional', label: '可选', comment: '是否为可选材料。', type: 'boolean', defaultValue: false },
  { path: 'protection', label: '保护材料', comment: '是否作为失败保护材料。', type: 'boolean', defaultValue: false }
];

const STANDARD_ECONOMY_RULE_FIELDS: Record<string, ConfigRuleFieldEntry> = {
  economy: ['经济消耗', '经济消耗配置。', 'object'],
  enabled: ['启用', '是否启用当前功能或经济消耗。', 'boolean'],
  currencies: ['货币消耗', '经济消耗中的货币列表。', 'list'],
  materials: ['材料消耗', '经济消耗中的材料列表。', 'list'],
  provider: ['经济提供器', '经济消耗使用的提供器；auto 会按 currency_id 自动推断。', 'economyProvider', { optionLabelPrefix: 'economyProvider' }],
  currency_id: ['货币 ID', '多货币系统中的货币标识；留空使用默认货币。', 'text'],
  amount: ['数量', '材料数量、货币数量或当前条目的数值。', 'number'],
  base_cost: ['基础费用', '费用公式中的基础值。', 'number'],
  cost_formula: ['费用公式', '根据等级、品质或上下文计算最终费用的表达式。', 'text'],
  display_name: ['显示名称', '货币、材料或当前条目的显示名称。', 'text']
};

export function standardCurrencyCostFields(options: StandardEconomyFieldOptions = {}): WebConfigFieldSchema[] {
  return buildStandardEconomyFields(STANDARD_CURRENCY_COST_FIELDS, options);
}

export function standardMaterialCostFields(options: StandardEconomyFieldOptions = {}): WebConfigFieldSchema[] {
  return buildStandardEconomyFields(STANDARD_MATERIAL_COST_FIELDS, options);
}

export function standardEconomyRuleFields(options: StandardEconomyRuleOptions = {}): Record<string, ConfigRuleFieldEntry> {
  const omitted = new Set(options.omit ?? []);
  const entries = Object.entries(STANDARD_ECONOMY_RULE_FIELDS)
    .filter(([key]) => !omitted.has(key))
    .map(([key, value]) => [key, options.overrides?.[key] ?? value] as const);
  return { ...Object.fromEntries(entries), ...(options.append ?? {}) };
}

function buildStandardEconomyFields(baseFields: WebConfigFieldSchema[], options: StandardEconomyFieldOptions): WebConfigFieldSchema[] {
  const omitted = new Set(options.omit ?? []);
  const insertAfter = options.insertAfter ?? {};
  const inserted = new Set<string>();
  const fields: WebConfigFieldSchema[] = [];

  for (const field of baseFields) {
    if (omitted.has(field.path)) continue;
    fields.push(cloneField({ ...field, ...(options.overrides?.[field.path] ?? {}) }));
    const additions = toFieldList(insertAfter[field.path]);
    if (additions.length) {
      fields.push(...additions.map(cloneField));
      inserted.add(field.path);
    }
  }

  for (const [anchor, additions] of Object.entries(insertAfter)) {
    if (inserted.has(anchor)) continue;
    fields.push(...toFieldList(additions).map(cloneField));
  }

  if (options.append?.length) fields.push(...options.append.map(cloneField));
  return fields;
}

function toFieldList(field: WebConfigFieldSchema | WebConfigFieldSchema[] | undefined): WebConfigFieldSchema[] {
  if (!field) return [];
  return Array.isArray(field) ? field : [field];
}

function cloneField(field: WebConfigFieldSchema): WebConfigFieldSchema {
  return {
    ...field,
    options: field.options ? [...field.options] : undefined,
    itemFields: field.itemFields ? field.itemFields.map(cloneField) : undefined
  };
}
