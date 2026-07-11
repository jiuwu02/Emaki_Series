import type { WebConfigCreateTemplate, WebConfigFieldSchema } from '../types';

export type SchemaFieldKind =
  | 'text'
  | 'number'
  | 'boolean'
  | 'enum'
  | 'stringList'
  | 'numberList'
  | 'object'
  | 'objectMap'
  | 'objectList'
  | 'actionStringList'
  | 'nameLoreAction'
  | 'effectList'
  | 'conditionGroup'
  | 'itemSource'
  | 'economyProvider'
  | 'materialCostList'
  | 'currencyCostList';

export type SchemaSeverity = 'info' | 'warning' | 'error';

export type BaseSchemaField<TKind extends SchemaFieldKind = SchemaFieldKind> = {
  kind: TKind;
  path: string;
  label?: string;
  comment?: string;
  defaultValue?: unknown;
  required?: boolean;
};

export type TextField = BaseSchemaField<'text'> & { multiline?: boolean; placeholder?: string };
export type NumberField = BaseSchemaField<'number'> & { min?: number; max?: number; step?: number };
export type BooleanField = BaseSchemaField<'boolean'>;
export type EnumField = BaseSchemaField<'enum'> & { options: string[]; optionLabelPrefix?: string };
export type StringListField = BaseSchemaField<'stringList'>;
export type NumberListField = BaseSchemaField<'numberList'>;
export type ObjectField = BaseSchemaField<'object'> & { fields?: EmakiField[]; creatableChildren?: boolean; createTemplates?: WebConfigCreateTemplate[] };
export type ObjectMapField = BaseSchemaField<'objectMap'> & { valueFields?: EmakiField[]; creatableChildren?: boolean; createTemplates?: WebConfigCreateTemplate[] };
export type ObjectListField = BaseSchemaField<'objectList'> & { itemFields?: EmakiField[]; uniqueBy?: string };
export type ActionStringListField = BaseSchemaField<'actionStringList'>;
export type NameLoreActionField = BaseSchemaField<'nameLoreAction'>;
export type EffectListField = BaseSchemaField<'effectList'>;
export type ConditionGroupField = BaseSchemaField<'conditionGroup'> & { entryOptions?: string[] };
export type ItemSourceField = BaseSchemaField<'itemSource'>;
export type EconomyProviderField = BaseSchemaField<'economyProvider'> & { options?: string[] };
export type MaterialCostListField = BaseSchemaField<'materialCostList'>;
export type CurrencyCostListField = BaseSchemaField<'currencyCostList'>;

export type EmakiField =
  | TextField
  | NumberField
  | BooleanField
  | EnumField
  | StringListField
  | NumberListField
  | ObjectField
  | ObjectMapField
  | ObjectListField
  | ActionStringListField
  | NameLoreActionField
  | EffectListField
  | ConditionGroupField
  | ItemSourceField
  | EconomyProviderField
  | MaterialCostListField
  | CurrencyCostListField;

export type EmakiSchemaAst = {
  id: string;
  moduleId: string;
  pathPrefix?: string;
  pathPattern?: string;
  fields: EmakiField[];
};

export type SchemaDiagnosticEntry = {
  schemaId: string;
  moduleId?: string;
  path?: string;
  ruleId: string;
  severity: SchemaSeverity;
  message: string;
};

export type SchemaToConfigOptions = {
  includeNested?: boolean;
};

export type SchemaConfigField = WebConfigFieldSchema;
