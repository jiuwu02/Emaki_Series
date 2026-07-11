import type { EmakiField, EmakiSchemaAst, SchemaDiagnosticEntry } from './ast';

export function validateSchemaAst(schema: EmakiSchemaAst): SchemaDiagnosticEntry[] {
  const diagnostics: SchemaDiagnosticEntry[] = [];
  const add = (ruleId: string, severity: 'info' | 'warning' | 'error', message: string, path?: string) => diagnostics.push({ schemaId: schema?.id ?? '<unknown>', moduleId: schema?.moduleId, path, ruleId, severity, message });

  if (!schema?.id) add('schema.id', 'error', 'Schema id is required.');
  if (!schema?.moduleId) add('schema.moduleId', 'error', 'Schema moduleId is required.');
  if (!Array.isArray(schema?.fields)) add('schema.fields', 'error', 'Schema fields must be an array.');

  const paths = new Set<string>();
  for (const field of schema.fields ?? []) validateField(field, add, paths);
  if (!diagnostics.length) add('schema.valid', 'info', 'Schema AST validation passed.');
  return diagnostics;
}

export function validateActionFieldSemantics(schema: EmakiSchemaAst): SchemaDiagnosticEntry[] {
  const diagnostics: SchemaDiagnosticEntry[] = [];
  const add = (ruleId: string, severity: 'info' | 'warning' | 'error', message: string, path?: string) => diagnostics.push({ schemaId: schema.id, moduleId: schema.moduleId, path, ruleId, severity, message });
  forEachField(schema.fields, field => {
    const { kind, path } = field;
    if (kind === 'nameLoreAction' && !/name|lore/i.test(path)) add('action.nameLore.scope', 'warning', 'NameLoreActionField should only be used for name/lore action chains.', path);
    if (kind === 'actionStringList' && /name_actions|lore_actions/i.test(path)) add('action.string.scope', 'warning', 'Name/lore action chains should use NameLoreActionField instead of ActionStringListField.', path);
  });
  return diagnostics;
}

function validateField(field: EmakiField, add: (ruleId: string, severity: 'info' | 'warning' | 'error', message: string, path?: string) => void, paths: Set<string>): void {
  if (!field?.path) add('field.path', 'error', 'Field path is required.');
  else if (paths.has(field.path)) add('field.path.duplicate', 'error', `Duplicate field path: ${field.path}.`, field.path);
  else paths.add(field.path);
  const fieldLike = field as Partial<EmakiField>;
  if (!fieldLike.kind) add('field.kind', 'error', 'Field kind is required.', fieldLike.path);
  if (field.kind === 'enum' && !field.options?.length) add('field.enum.options', 'error', 'EnumField requires options.', field.path);
  if (field.kind === 'economyProvider' && field.options && !field.options.length) add('field.economy.options', 'warning', 'EconomyProviderField options are empty.', field.path);
  if (field.kind === 'object') for (const child of field.fields ?? []) validateField(child, add, new Set<string>());
  if (field.kind === 'objectMap') for (const child of field.valueFields ?? []) validateField(child, add, new Set<string>());
  if (field.kind === 'objectList') for (const child of field.itemFields ?? []) validateField(child, add, new Set<string>());
}

function forEachField(fields: EmakiField[], visitor: (field: EmakiField) => void): void {
  for (const field of fields) {
    visitor(field);
    if (field.kind === 'object') forEachField(field.fields ?? [], visitor);
    if (field.kind === 'objectMap') forEachField(field.valueFields ?? [], visitor);
    if (field.kind === 'objectList') forEachField(field.itemFields ?? [], visitor);
  }
}
