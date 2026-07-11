import type { EmakiField } from './ast';

export function defaultValueForField(field: EmakiField): unknown {
  if (field.defaultValue !== undefined) return copyDefaultValue(field.defaultValue);
  switch (field.kind) {
    case 'number':
      return undefined;
    case 'boolean':
      return false;
    case 'stringList':
    case 'numberList':
    case 'objectList':
    case 'actionStringList':
    case 'nameLoreAction':
    case 'effectList':
    case 'materialCostList':
    case 'currencyCostList':
      return [];
    case 'object':
      return defaultObjectValue(field.fields ?? []);
    case 'objectMap':
      return defaultObjectValue(field.valueFields ?? []);
    case 'conditionGroup':
      return { type: 'all_of', entries: [] };
    case 'enum':
      return field.options[0] ?? '';
    case 'economyProvider':
      return field.options?.[0] ?? 'auto';
    case 'itemSource':
    case 'text':
    default:
      return '';
  }
}

export function defaultObjectValue(fields: EmakiField[]): Record<string, unknown> {
  const value: Record<string, unknown> = {};
  for (const field of fields) {
    if (!field.path || field.path.includes('.')) continue;
    value[field.path] = defaultValueForField(field);
  }
  return value;
}

function copyDefaultValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(copyDefaultValue);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([key, entry]) => [key, copyDefaultValue(entry)]));
  return value;
}
