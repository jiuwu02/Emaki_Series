import { t } from '../../i18n';
import { parseYaml, serializeYaml, setDeepValue } from '../../lib';
import type { WebConfigCreateTemplate, WebConfigFieldSchema, WebConfigNode } from '../../types';
import type { ConfigSourceDocument, ConfigToast } from './ConfigSourceRuntime';

export function createConfigChild(node: WebConfigNode, source: ConfigSourceDocument, key: string, values: Record<string, unknown>, template: WebConfigCreateTemplate, setToast: (toast: ConfigToast) => void, onCreated?: (nodes: WebConfigNode[]) => void) {
  if (!node.creatableChildren) return;
  if (source.loading) {
    setToast({ tone: 'bad', text: t('core.config.sourceLoading') });
    return;
  }
  if (source.error) {
    setToast({ tone: 'bad', text: source.error });
    return;
  }
  try {
    const data = parseYaml(source.content || '{}');
    const parent = getConfigObject(data, node.path.split('.'));
    if (key in parent) {
      setToast({ tone: 'bad', text: t('core.config.createDuplicate', { key }) });
      return;
    }
    const childValue = templateValuesToObject(values);
    const nextParent = { ...parent, [key]: childValue };
    const nextData = setDeepValue(data, node.path.split('.'), nextParent);
    source.update(serializeYaml(nextData));
    setToast({ tone: 'ok', text: t('core.config.createdSourceField', { key }) });
    onCreated?.(createOptimisticConfigNodes(node, key, childValue, template));
  } catch (err) {
    source.update(`${source.content.replace(/\s*$/, '')}\n# ${err instanceof Error ? err.message : t('core.toast.refreshFailed')}\n`);
    setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
  }
}

export function deleteConfigObject(node: WebConfigNode, source: ConfigSourceDocument, setToast: (toast: ConfigToast) => void, onDeleted: (path: string) => void) {
  if (source.loading) {
    setToast({ tone: 'bad', text: t('core.config.sourceLoading') });
    return;
  }
  if (source.error) {
    setToast({ tone: 'bad', text: source.error });
    return;
  }
  try {
    const data = parseYaml(source.content || '{}');
    const nextData = deleteDeepValue(data, node.path.split('.'));
    source.update(serializeYaml(nextData));
    setToast({ tone: 'ok', text: t('core.config.deletedSourceObject', { path: node.path }) });
    onDeleted(node.path);
  } catch (err) {
    setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
  }
}

export function templateValuesToObject(values: Record<string, unknown>): Record<string, unknown> {
  return Object.entries(values).reduce<Record<string, unknown>>((result, [path, value]) => setDeepValue(result, path.split('.'), value), {});
}

export function createOptimisticConfigNodes(parent: WebConfigNode, key: string, value: Record<string, unknown>, template: WebConfigCreateTemplate): WebConfigNode[] {
  const rootPath = `${parent.path}.${key}`;
  const root: WebConfigNode = {
    path: rootPath,
    label: key,
    comment: template.label || key,
    type: 'object',
    editable: true,
    value,
    creatableChildren: false,
    createTemplates: [],
  };
  const fields = template.fields.length ? template.fields : Object.keys(value).map(fieldKey => ({ path: fieldKey, label: fieldKey, type: inferConfigFieldType(value[fieldKey]) } as WebConfigFieldSchema));
  const nodes: WebConfigNode[] = [root];
  for (const field of fields) {
    const fullPath = `${rootPath}.${field.path}`;
    const parts = fullPath.split('.');
    for (let index = rootPath.split('.').length + 1; index < parts.length; index++) {
      const parentPath = parts.slice(0, index).join('.');
      if (!nodes.some(node => node.path === parentPath)) {
        nodes.push({ path: parentPath, label: parentPath.split('.').pop() ?? parentPath, comment: '', type: 'object', editable: true, value: getDeepConfigValue(value, parentPath.slice(rootPath.length + 1).split('.')) ?? {} });
      }
    }
    nodes.push(optimisticFieldNode(rootPath, field, getDeepConfigValue(value, field.path.split('.'))));
  }
  return nodes;
}

export function optimisticFieldNode(rootPath: string, field: WebConfigFieldSchema, value: unknown): WebConfigNode {
  return {
    path: `${rootPath}.${field.path}`,
    label: field.label || field.path,
    comment: field.comment || '',
    type: field.type || inferConfigFieldType(value),
    editable: true,
    value,
    options: field.options,
    optionLabelPrefix: field.optionLabelPrefix,
    itemFields: field.itemFields,
    uniqueBy: field.uniqueBy
  };
}

export function inferConfigFieldType(value: unknown): string {
  if (typeof value === 'boolean') return 'boolean';
  if (typeof value === 'number') return 'number';
  if (Array.isArray(value)) return 'list';
  if (value && typeof value === 'object') return 'object';
  return 'text';
}

export function getDeepConfigValue(source: Record<string, unknown>, path: string[]): unknown {
  return path.reduce<unknown>((current, part) => current && typeof current === 'object' && !Array.isArray(current) ? (current as Record<string, unknown>)[part] : undefined, source);
}

export function deleteDeepValue(source: Record<string, unknown>, path: string[]): Record<string, unknown> {
  if (!path.length) return source;
  const [head, ...tail] = path;
  if (!tail.length) {
    const next = { ...source };
    delete next[head];
    return next;
  }
  return setDeepValue(source, [head], deleteDeepValue(getConfigObject(source, [head]), tail));
}

export function mergeConfigNodes(baseNodes: WebConfigNode[], optimisticNodes: WebConfigNode[], deletedPaths: Set<string>): WebConfigNode[] {
  const optimisticByPath = new Map<string, WebConfigNode>();
  for (const node of optimisticNodes) {
    if (!isDeletedPath(node.path, deletedPaths)) optimisticByPath.set(node.path, node);
  }
  const result: WebConfigNode[] = [];
  const emitted = new Set<string>();
  for (const baseNode of baseNodes) {
    if (isDeletedPath(baseNode.path, deletedPaths)) continue;
    const optimistic = optimisticByPath.get(baseNode.path);
    result.push(optimistic ?? baseNode);
    emitted.add(baseNode.path);
  }
  for (const optimisticNode of optimisticNodes) {
    if (emitted.has(optimisticNode.path) || isDeletedPath(optimisticNode.path, deletedPaths)) continue;
    result.push(optimisticNode);
    emitted.add(optimisticNode.path);
  }
  return result;
}

export function isDeletedPath(path: string, deletedPaths: Set<string>): boolean {
  for (const deletedPath of deletedPaths) if (path === deletedPath || path.startsWith(`${deletedPath}.`)) return true;
  return false;
}

export function emptyCreateTemplate(node: WebConfigNode): WebConfigCreateTemplate {
  return { id: 'empty', label: t('core.config.createEmptyTemplate'), fields: node.type === 'dynamic_map' ? [] : [] };
}

export function defaultTemplateValues(template: WebConfigCreateTemplate): Record<string, unknown> {
  return Object.fromEntries(template.fields.map(field => [field.path, field.defaultValue ?? defaultSchemaFieldValue(field)]));
}

export function defaultSchemaFieldValue(field: WebConfigFieldSchema): unknown {
  if (field.type === 'number') return 0;
  if (field.type === 'boolean') return false;
  if (field.type === 'json') return {};
  if (field.type === 'list' || field.type === 'stringList' || field.type === 'numberList' || field.type === 'objectList') return [];
  if (field.type === 'enum') return field.options?.[0] ?? '';
  return '';
}

export function parseSafeYaml(content: string): Record<string, unknown> {
  try { return parseYaml(content || '{}'); } catch { return {}; }
}

export function getConfigObject(data: Record<string, unknown>, path: string[]): Record<string, unknown> {
  let current: unknown = data;
  for (const part of path) {
    current = current && typeof current === 'object' && !Array.isArray(current) ? (current as Record<string, unknown>)[part] : undefined;
  }
  return current && typeof current === 'object' && !Array.isArray(current) ? current as Record<string, unknown> : {};
}

export function nextConfigChildKey(parent: Record<string, unknown>): string {
  const used = new Set(Object.keys(parent));
  if (!used.has('new_field')) return 'new_field';
  let index = 1;
  while (used.has(`new_field_${index}`)) index += 1;
  return `new_field_${index}`;
}
