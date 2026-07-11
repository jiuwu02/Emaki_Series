import type {
  ActionStringListField,
  BooleanField,
  ConditionGroupField,
  CurrencyCostListField,
  EconomyProviderField,
  EffectListField,
  EmakiField,
  EmakiSchemaAst,
  EnumField,
  ItemSourceField,
  MaterialCostListField,
  NameLoreActionField,
  NumberField,
  NumberListField,
  ObjectField,
  ObjectListField,
  ObjectMapField,
  StringListField,
  TextField
} from './ast';
import { defaultValueForField } from './defaults';
import type { WebConfigCreateTemplate, WebConfigFieldSchema } from '../types';

const CONDITION_GROUP_TYPES = ['all_of', 'any_of', 'none_of', 'at_least', 'exactly'];

export function defineSchemaAst(schema: EmakiSchemaAst): EmakiSchemaAst {
  return { ...schema, fields: schema.fields.map(copyField) };
}

export function textField(field: Omit<TextField, 'kind'>): TextField { return { ...field, kind: 'text' }; }
export function numberField(field: Omit<NumberField, 'kind'>): NumberField { return { ...field, kind: 'number' }; }
export function booleanField(field: Omit<BooleanField, 'kind'>): BooleanField { return { ...field, kind: 'boolean' }; }
export function enumField(field: Omit<EnumField, 'kind'>): EnumField { return { ...field, kind: 'enum', options: [...(field.options ?? [])] }; }
export function stringListField(field: Omit<StringListField, 'kind'>): StringListField { return { ...field, kind: 'stringList' }; }
export function numberListField(field: Omit<NumberListField, 'kind'>): NumberListField { return { ...field, kind: 'numberList' }; }
export function objectField(field: Omit<ObjectField, 'kind'>): ObjectField { return { ...field, kind: 'object', fields: field.fields?.map(copyField) }; }
export function objectMapField(field: Omit<ObjectMapField, 'kind'>): ObjectMapField { return { ...field, kind: 'objectMap', valueFields: field.valueFields?.map(copyField) }; }
export function objectListField(field: Omit<ObjectListField, 'kind'>): ObjectListField { return { ...field, kind: 'objectList', itemFields: field.itemFields?.map(copyField) }; }
export function actionStringListField(field: Omit<ActionStringListField, 'kind'>): ActionStringListField { return { ...field, kind: 'actionStringList' }; }
export function nameLoreActionField(field: Omit<NameLoreActionField, 'kind'>): NameLoreActionField { return { ...field, kind: 'nameLoreAction' }; }
export function effectListField(field: Omit<EffectListField, 'kind'>): EffectListField { return { ...field, kind: 'effectList' }; }
export function conditionGroupField(field: Omit<ConditionGroupField, 'kind'>): ConditionGroupField { return { ...field, kind: 'conditionGroup' }; }
export function itemSourceField(field: Omit<ItemSourceField, 'kind'>): ItemSourceField { return { ...field, kind: 'itemSource' }; }
export function economyProviderField(field: Omit<EconomyProviderField, 'kind'>): EconomyProviderField { return { ...field, kind: 'economyProvider' }; }
export function materialCostListField(field: Omit<MaterialCostListField, 'kind'>): MaterialCostListField { return { ...field, kind: 'materialCostList' }; }
export function currencyCostListField(field: Omit<CurrencyCostListField, 'kind'>): CurrencyCostListField { return { ...field, kind: 'currencyCostList' }; }

export function schemaAstToConfigFields(schema: EmakiSchemaAst): WebConfigFieldSchema[] {
  return schema.fields.flatMap(field => fieldToConfigField(field));
}

export function fieldToConfigField(field: EmakiField): WebConfigFieldSchema[] {
  const configField: WebConfigFieldSchema = {
    path: field.path,
    label: field.label,
    comment: field.comment,
    type: configTypeForField(field),
    defaultValue: defaultValueForField(field)
  };
  if ('options' in field && Array.isArray(field.options)) configField.options = [...field.options];
  if ('optionLabelPrefix' in field && field.optionLabelPrefix) configField.optionLabelPrefix = field.optionLabelPrefix;
  if (field.kind === 'object' && field.fields?.length) configField.itemFields = field.fields.flatMap(child => fieldToConfigField(child));
  if (field.kind === 'objectMap' && field.valueFields?.length) configField.itemFields = field.valueFields.flatMap(child => fieldToConfigField(child));
  if (field.kind === 'objectList' && field.itemFields?.length) configField.itemFields = field.itemFields.flatMap(child => fieldToConfigField(child));
  if (field.kind === 'conditionGroup') configField.itemFields = conditionGroupConfigFields(field);
  if ('uniqueBy' in field && field.uniqueBy) configField.uniqueBy = field.uniqueBy;
  if ('creatableChildren' in field && field.creatableChildren !== undefined) configField.creatableChildren = field.creatableChildren;
  if ('createTemplates' in field && field.createTemplates?.length) configField.createTemplates = field.createTemplates.map(copyCreateTemplate);
  return [configField];
}

export function configTypeForField(field: EmakiField): string {
  switch (field.kind) {
    case 'text': return field.multiline ? 'textarea' : 'text';
    case 'number': return 'number';
    case 'boolean': return 'boolean';
    case 'enum': return 'enum';
    case 'stringList': return 'stringList';
    case 'numberList': return 'numberList';
    case 'object': return 'object';
    case 'objectMap': return 'dynamic_map';
    case 'objectList': return 'objectList';
    case 'actionStringList': return 'stringList';
    case 'nameLoreAction': return 'actions';
    case 'effectList': return 'effects';
    case 'conditionGroup': return 'object';
    case 'itemSource': return 'text';
    case 'economyProvider': return 'enum';
    case 'materialCostList': return 'objectList';
    case 'currencyCostList': return 'objectList';
    default: return 'text';
  }
}

function conditionGroupConfigFields(field: ConditionGroupField): WebConfigFieldSchema[] {
  const options = field.entryOptions?.length ? [...field.entryOptions] : CONDITION_GROUP_TYPES;
  return [
    { path: 'type', label: 'Type', comment: 'Condition group matching mode.', type: 'enum', options, defaultValue: 'all_of' },
    { path: 'required_count', label: 'Required count', comment: 'Required successful condition count for at_least/exactly groups.', type: 'number', defaultValue: 0 },
    { path: 'entries', label: 'Entries', comment: 'CoreLib condition expressions.', type: 'stringList', defaultValue: [] }
  ];
}

function copyCreateTemplate(template: WebConfigCreateTemplate): WebConfigCreateTemplate {
  return { ...template, fields: (template.fields ?? []).map(copyWebConfigField) };
}

function copyWebConfigField(field: WebConfigFieldSchema): WebConfigFieldSchema {
  return {
    ...field,
    options: field.options ? [...field.options] : undefined,
    itemFields: field.itemFields ? field.itemFields.map(copyWebConfigField) : undefined,
    createTemplates: field.createTemplates ? field.createTemplates.map(copyCreateTemplate) : undefined
  };
}

function copyField<T extends EmakiField>(field: T): T {
  return {
    ...field,
    ...(field.kind === 'enum' ? { options: [...field.options] } : {}),
    ...(field.kind === 'object' ? { fields: field.fields?.map(copyField) } : {}),
    ...(field.kind === 'objectMap' ? { valueFields: field.valueFields?.map(copyField) } : {}),
    ...(field.kind === 'objectList' ? { itemFields: field.itemFields?.map(copyField) } : {})
  } as T;
}
