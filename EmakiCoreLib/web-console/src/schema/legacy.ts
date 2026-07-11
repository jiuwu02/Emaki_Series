import type { EmakiField, EmakiSchemaAst, SchemaFieldKind } from './ast';
import type { ConfigNodeType, WebConfigFieldSchema, WebConfigNode } from '../types';

export type WebConfigNodesToSchemaOptions = {
  id: string;
  moduleId: string;
  pathPrefix?: string;
  pathPattern?: string;
};

export function webConfigNodesToSchemaAst(nodes: WebConfigNode[], options: WebConfigNodesToSchemaOptions): EmakiSchemaAst {
  return {
    id: options.id,
    moduleId: options.moduleId,
    pathPrefix: options.pathPrefix,
    pathPattern: options.pathPattern,
    fields: (nodes ?? []).filter(node => Boolean(node?.path)).map(nodeToField)
  };
}

export function webConfigFieldSchemaToEmakiField(field: WebConfigFieldSchema): EmakiField {
  const kind = schemaFieldKindFromConfigType(field.type, field.path);
  const base = {
    kind,
    path: field.path,
    label: field.label,
    comment: field.comment,
    defaultValue: field.defaultValue
  };
  return withStructuredChildren(base, field.itemFields, field.uniqueBy);
}

function nodeToField(node: WebConfigNode): EmakiField {
  const kind = schemaFieldKindFromConfigType(node.type, node.path);
  const base = {
    kind,
    path: node.path,
    label: node.label,
    comment: node.comment,
    defaultValue: node.value
  };
  return withStructuredChildren(base, node.itemFields, node.uniqueBy, node.creatableChildren, node.createTemplates);
}

function withStructuredChildren(
  base: { kind: SchemaFieldKind; path: string; label?: string; comment?: string; defaultValue?: unknown },
  itemFields?: WebConfigFieldSchema[],
  uniqueBy?: string,
  creatableChildren?: boolean,
  createTemplates?: WebConfigNode['createTemplates']
): EmakiField {
  if (base.kind === 'enum') return { ...base, kind: 'enum', options: [] };
  if (base.kind === 'object') return { ...base, kind: 'object', fields: itemFields?.map(webConfigFieldSchemaToEmakiField), creatableChildren, createTemplates };
  if (base.kind === 'objectMap') return { ...base, kind: 'objectMap', valueFields: itemFields?.map(webConfigFieldSchemaToEmakiField), creatableChildren, createTemplates };
  if (base.kind === 'objectList') return { ...base, kind: 'objectList', itemFields: itemFields?.map(webConfigFieldSchemaToEmakiField), uniqueBy };
  if (base.kind === 'economyProvider') return { ...base, kind: 'economyProvider' };
  return base as EmakiField;
}

function schemaFieldKindFromConfigType(type: ConfigNodeType | WebConfigFieldSchema['type'] | undefined, path: string | undefined): SchemaFieldKind {
  const normalizedType = String(type ?? '').trim();
  const normalizedPath = String(path ?? '').trim();
  if (normalizedType === 'textarea' || normalizedType === 'scriptText') return 'text';
  if (normalizedType === 'number') return 'number';
  if (normalizedType === 'boolean') return 'boolean';
  if (normalizedType === 'enum' || normalizedType.startsWith('enum:') || normalizedType.startsWith('dynamic_enum:')) return normalizedPath.toLowerCase().includes('economy') ? 'economyProvider' : 'enum';
  if (normalizedType === 'numberList') return 'numberList';
  if (normalizedType === 'objectList') return 'objectList';
  if (normalizedType === 'effects') return 'effectList';
  if (normalizedType === 'actions') return 'nameLoreAction';
  if (normalizedType === 'object') return /condition$/i.test(normalizedPath) ? 'conditionGroup' : 'object';
  if (normalizedType === 'dynamic_map' || normalizedType === 'variablesMap' || normalizedType === 'json') return 'objectMap';
  if (normalizedType === 'stringList' || normalizedType === 'list') {
    if (/(^|\.)(name_actions|lore_actions)$/i.test(normalizedPath)) return 'nameLoreAction';
    if (/(^|\.)(actions|success|failure|complete)$/i.test(normalizedPath)) return 'actionStringList';
    return 'stringList';
  }
  if (/item(_source|sources)?$/i.test(normalizedPath)) return 'itemSource';
  return 'text';
}
