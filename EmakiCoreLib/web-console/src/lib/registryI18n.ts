import { getLocale, getLocaleMessages, t } from '../i18n';
import { humanizeFieldLabel, lastPathKey } from './fieldI18n';
import type { RegistryTreeNode, WebRegistryFile, WebRegistryModule } from '../types';

export function moduleRegistryNamespace(moduleId: string | undefined): string {
  return String(moduleId ?? '').trim().replace(/[^a-zA-Z0-9_.-]+/g, '').toLowerCase();
}

export function registryFileKey(relativePath: string | undefined): string {
  const normalized = String(relativePath ?? '').trim().replace(/\\/g, '/');
  if (!normalized) return 'file';
  const globIndex = normalized.search(/[?*]/);
  let base = globIndex >= 0 ? normalized.slice(0, globIndex) : normalized;
  base = base.replace(/\/+$/g, '');
  if (!base) base = normalized;
  const segment = base.includes('/') ? base.slice(base.lastIndexOf('/') + 1) : base;
  const withoutExtension = segment.replace(/\.(ya?ml|json|js|kts|txt)$/i, '');
  const cleaned = withoutExtension.replace(/[^a-zA-Z0-9_.-]+/g, '_').replace(/^_+|_+$/g, '').toLowerCase();
  return cleaned || 'file';
}

export function moduleDisplayName(module: WebRegistryModule | null | undefined): string {
  if (!module) return t('core.stage.defaultTitle');
  const namespace = moduleRegistryNamespace(module.id);
  return t(`${namespace}.module.name`, undefined, module.name || module.id);
}

export function moduleDisplaySummary(module: WebRegistryModule | null | undefined): string {
  if (!module) return '';
  const namespace = moduleRegistryNamespace(module.id);
  return t(`${namespace}.module.summary`, undefined, module.summary || '');
}

export function fileDisplayTitle(file: WebRegistryFile | null | undefined): string {
  if (!file) return t('core.stage.defaultHint');
  const namespace = moduleRegistryNamespace(file.moduleId);
  const key = registryFileKey(file.path);
  return t(`${namespace}.file.${key}.title`, undefined, file.title || file.path);
}

export function fileDisplayComment(file: WebRegistryFile | null | undefined): string {
  if (!file) return '';
  const namespace = moduleRegistryNamespace(file.moduleId);
  const key = registryFileKey(file.path);
  return t(`${namespace}.file.${key}.comment`, undefined, file.comment || '');
}

export function treeNodeDisplayLabel(node: RegistryTreeNode): string {
  if (node.type === 'module') {
    return t(`${moduleRegistryNamespace(node.moduleId || node.id)}.module.name`, undefined, node.label || node.moduleId || node.id);
  }
  if (node.fileId && node.moduleId) {
    return t(`${moduleRegistryNamespace(node.moduleId)}.file.${registryFileKey(node.path)}.title`, undefined, node.label || node.path || node.id);
  }
  return node.label || node.id;
}

export function treeNodeDisplayComment(node: RegistryTreeNode): string {
  if (node.type === 'module') {
    return t(`${moduleRegistryNamespace(node.moduleId || node.id)}.module.summary`, undefined, node.comment || '');
  }
  if (node.fileId && node.moduleId) {
    return t(`${moduleRegistryNamespace(node.moduleId)}.file.${registryFileKey(node.path)}.comment`, undefined, node.comment || '');
  }
  return node.comment || '';
}

export function configNodeDisplayComment(moduleId: string | undefined, path: string | undefined, fallback = ''): string {
  const namespace = moduleRegistryNamespace(moduleId);
  const exactPath = String(path ?? '');
  const last = lastPathKey(exactPath);
  const keys = [
    namespace && `${namespace}.comment.${exactPath}`,
    namespace && `${namespace}.comment.${last}`,
    `core.comment.${exactPath}`,
    `core.comment.${last}`
  ].filter(Boolean) as string[];
  const currentValue = lookupCurrentLocale(keys);
  if (currentValue) return currentValue;
  if (!getLocale().toLowerCase().startsWith('zh')) return englishCommentFallback(exactPath, fallback);
  for (const key of keys) {
    const value = t(key, undefined, '');
    if (value) return value;
  }
  return fallback;
}

function lookupCurrentLocale(keys: string[]): string | undefined {
  const locale = getLocale();
  const language = locale.split('-')[0];
  const messages = [getLocaleMessages(locale), language && language !== locale ? getLocaleMessages(language) : undefined].filter(Boolean) as Record<string, string>[];
  for (const key of keys) {
    for (const bundle of messages) {
      const value = bundle[key];
      if (value) return value;
    }
  }
  return undefined;
}

function englishCommentFallback(path: string, fallback: string): string {
  if (fallback && !/[^\u0000-\u00ff]/.test(fallback)) return fallback;
  const label = humanizeFieldLabel(path);
  return label ? `Configure ${label}.` : '';
}
