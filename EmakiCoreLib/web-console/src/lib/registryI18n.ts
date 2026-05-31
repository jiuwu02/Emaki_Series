import { getLocale, peekLocaleMessage, t } from '../i18n';
import { humanizeFieldLabel, lastPathKey } from './fieldI18n';
import type { RegistryTreeNode, WebRegistryFile, WebRegistryModule } from '../types';

type BuiltinFileCopy = { title: string; comment: string };

const BUILTIN_FILE_COPY: Record<string, BuiltinFileCopy> = {
  config: { title: '主配置', comment: '插件主配置文件。' },
  plugin: { title: '插件描述', comment: 'plugin.yml 插件描述与依赖声明。' },
  'web-console': { title: 'Web Console 声明', comment: 'Web Console 文件注册表与可编辑资源声明。' },
  lang: { title: '语言文件', comment: '语言文案文件目录。' },
  gui: { title: 'GUI 模板', comment: 'GUI 模板文件目录。' },
  default: { title: '默认模板', comment: '目录内的默认模板文件。' },
  zh_cn: { title: '中文语言文件', comment: '简体中文语言资源文件。' },
  en_us: { title: '英文语言文件', comment: 'English language resource file.' }
};

const ROOT_PATH_COPY: Record<string, BuiltinFileCopy> = {
  lang: { title: '语言文件', comment: '语言资源文件目录。' },
  gui: { title: 'GUI 模板', comment: 'GUI 模板文件目录。' }
};

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

export function registryPathKey(relativePath: string | undefined): string {
  const normalized = String(relativePath ?? '').trim().replace(/\\/g, '/');
  if (!normalized) return 'file';
  const globIndex = normalized.search(/[?*]/);
  let base = globIndex >= 0 ? normalized.slice(0, globIndex) : normalized;
  base = base.replace(/\/+$/g, '');
  if (!base) base = normalized;
  const withoutExtensions = base.split('/').filter(Boolean).map(segment => segment.replace(/\.(ya?ml|json|js|kts|txt)$/i, '')).join('/');
  const cleaned = withoutExtensions.replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').toLowerCase();
  return cleaned || registryFileKey(relativePath);
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
  return resolveFileDisplayText(file.moduleId, file.path, file.title || file.path, 'title');
}

export function fileDisplayComment(file: WebRegistryFile | null | undefined): string {
  if (!file) return '';
  return resolveFileDisplayText(file.moduleId, file.path, file.comment || '', 'comment');
}

export function treeNodeDisplayLabel(node: RegistryTreeNode): string {
  if (node.type === 'module') {
    return t(`${moduleRegistryNamespace(node.moduleId || node.id)}.module.name`, undefined, node.label || node.moduleId || node.id);
  }
  if (node.type === 'folder') {
    return resolveFolderDisplayText(node.moduleId, node.path, node.label || node.path || node.id, 'title');
  }
  if (node.type === 'child') {
    return childFileLabel(node.path, node.label || node.id);
  }
  if (node.fileId && node.moduleId) {
    return resolveFileDisplayText(node.moduleId, node.path, node.label || node.path || node.id, 'title');
  }
  return node.label || node.id;
}

export function treeNodeDisplayComment(node: RegistryTreeNode): string {
  if (node.type === 'module') {
    return t(`${moduleRegistryNamespace(node.moduleId || node.id)}.module.summary`, undefined, node.comment || '');
  }
  if (node.type === 'folder') {
    return resolveFolderDisplayText(node.moduleId, node.path, node.comment || '', 'comment');
  }
  if (node.fileId && node.moduleId) {
    return resolveFileDisplayText(node.moduleId, node.path, node.comment || '', 'comment');
  }
  return node.comment || '';
}

function resolveFileDisplayText(moduleId: string | undefined, path: string | undefined, fallback: string, kind: 'title' | 'comment'): string {
  const namespace = moduleRegistryNamespace(moduleId);
  const key = registryFileKey(path);
  const pathKey = registryPathKey(path);
  const localized = localizedFileDisplayText(namespace, key, pathKey, kind);
  if (localized) return localized;
  const pathCopy = pathBasedFileDisplay(path);
  const builtin = builtinFileDisplay(key);
  if (pathCopy && (key === 'default' || !builtin)) return pathCopy[kind];
  if (builtin) return builtin[kind];
  if (pathCopy) return pathCopy[kind];
  const moduleRootCopy = moduleRootFileDisplay(namespace, path);
  if (moduleRootCopy) return moduleRootCopy[kind];
  return kind === 'title' ? fallbackFileLabel(path, fallback) : fallback;
}

function resolveFolderDisplayText(moduleId: string | undefined, path: string | undefined, fallback: string, kind: 'title' | 'comment'): string {
  const namespace = moduleRegistryNamespace(moduleId);
  const key = registryFileKey(path);
  const pathKey = registryPathKey(path);
  const localized = localizedFileDisplayText(namespace, key, pathKey, kind);
  if (localized) return localized;
  if (kind === 'comment') return fallback;
  return fallbackFileLabel(path, fallback);
}

function localizedFileDisplayText(namespace: string, key: string, pathKey: string, kind: 'title' | 'comment'): string | undefined {
  return lookupLocale([
    namespace && `${namespace}.filePath.${pathKey}.${kind}`,
    namespace && `${namespace}.file.${key}.${kind}`
  ].filter(Boolean) as string[]);
}

function builtinFileDisplay(key: string | undefined): BuiltinFileCopy | undefined {
  if (!key) return undefined;
  return BUILTIN_FILE_COPY[key.toLowerCase()];
}

function moduleRootFileDisplay(namespace: string, path: string | undefined): BuiltinFileCopy | undefined {
  if (!namespace) return undefined;
  const normalized = String(path ?? '').trim().replace(/\\/g, '/');
  if (!normalized || /[?*]/.test(normalized)) return undefined;
  const segments = normalized.split('/').filter(Boolean);
  if (segments.length < 2) return undefined;
  const rootKey = registryFileKey(segments[0]);
  const title = lookupLocale([`${namespace}.file.${rootKey}.title`]);
  if (!title) return undefined;
  const leaf = segments[segments.length - 1] ?? '';
  const humanLeaf = humanizeFilePath(leaf);
  const comment = lookupLocale([`${namespace}.file.${rootKey}.comment`]) ?? '';
  return { title: humanLeaf ? `${title} · ${humanLeaf}` : title, comment };
}

function pathBasedFileDisplay(path: string | undefined): BuiltinFileCopy | undefined {
  const normalized = String(path ?? '').trim().replace(/\\/g, '/');
  if (!normalized) return undefined;
  const segments = normalized.split('/').filter(Boolean);
  const root = (segments[0] ?? '').toLowerCase();
  const leaf = segments[segments.length - 1] ?? '';
  const leafKey = registryFileKey(leaf);
  const humanLeaf = humanizeFilePath(leaf);

  if (root === 'lang') {
    if (leafKey === 'zh_cn') return { title: '中文语言文件', comment: '简体中文语言资源文件。' };
    if (leafKey === 'en_us') return { title: '英文语言文件', comment: 'English language resource file.' };
  }

  if (root in ROOT_PATH_COPY) {
    const base = ROOT_PATH_COPY[root];
    const isPattern = /[?*]/.test(normalized);
    return { title: !isPattern && humanLeaf ? `${base.title} · ${humanLeaf}` : base.title, comment: base.comment };
  }

  return undefined;
}

function fallbackFileLabel(path: string | undefined, fallback: string): string {
  const preferred = String(fallback ?? '').trim();
  if (preferred && !/[\\/]/.test(preferred) && !/[*?]/.test(preferred) && !/\.(ya?ml|json|js|kts|txt)$/i.test(preferred)) return preferred;
  const humanized = humanizeFilePath(path);
  if (humanized) return humanized;
  const cleanedPreferred = preferred.replace(/[*?]/g, '').replace(/\/+/g, ' ').trim();
  return cleanedPreferred || preferred;
}

function childFileLabel(path: string | undefined, fallback: string): string {
  const preferred = String(fallback ?? '').trim();
  const leaf = String(path ?? '').trim().replace(/\\/g, '/').split('/').filter(Boolean).pop() ?? '';
  const cleanLeaf = leaf.replace(/\.(ya?ml|json|js|kts|txt)$/i, '');
  return cleanLeaf || preferred;
}

function humanizeFilePath(path: string | undefined): string {
  const normalized = String(path ?? '').trim().replace(/\\/g, '/').replace(/\.(ya?ml|json|js|kts|txt)$/i, '');
  if (!normalized) return '';
  const segments = normalized.split('/').filter(Boolean);
  // For glob folder nodes (e.g. "recipes/*"), the leaf is a wildcard; prefer the last
  // meaningful (non-glob) segment so the label reads "recipes" instead of "*".
  const meaningful = segments.filter(segment => !/[*?]/.test(segment));
  const leaf = meaningful[meaningful.length - 1] || segments[segments.length - 1] || normalized;
  return leaf.replace(/[*?]/g, '').replace(/[_-]+/g, ' ').trim();
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

function lookupLocale(keys: string[]): string | undefined {
  const currentValue = lookupCurrentLocale(keys);
  if (currentValue) return currentValue;
  for (const key of keys) {
    const value = t(key, undefined, '');
    if (value) return value;
  }
  return undefined;
}

function lookupCurrentLocale(keys: string[]): string | undefined {
  const locale = getLocale();
  const language = locale.split('-')[0];
  const useLanguage = Boolean(language) && language !== locale;
  for (const key of keys) {
    const value = peekLocaleMessage(locale, key);
    if (value) return value;
    if (useLanguage) {
      const langValue = peekLocaleMessage(language, key);
      if (langValue) return langValue;
    }
  }
  return undefined;
}

function englishCommentFallback(path: string, fallback: string): string {
  if (fallback && !/[^\u0000-\u00ff]/.test(fallback)) return fallback;
  const label = humanizeFieldLabel(path);
  return label ? `Configure ${label}.` : '';
}
