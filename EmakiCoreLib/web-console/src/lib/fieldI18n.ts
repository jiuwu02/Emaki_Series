import type { WebEditorField } from '../types';
import { getLocale, peekLocaleMessage, t } from '../i18n';

export type FieldLabelOptions = {
  moduleId?: string;
  namespace?: string;
  fallback?: string;
  editorFields?: Record<string, WebEditorField>;
};

export type OptionLabelOptions = {
  moduleId?: string;
  namespace?: string;
  fallback?: string;
};

export function fieldLabel(path: string, options: FieldLabelOptions = {}): string {
  const exactPath = String(path || '');
  const last = lastPathKey(exactPath);
  const namespaces = uniqueStrings([...namespaceVariants(options.namespace), ...namespaceVariants(options.moduleId)]);
  const keys = [
    ...namespaces.flatMap(namespace => [
      `${namespace}.field.${exactPath}`,
      `${namespace}.field.${last}`,
      `${namespace}.item.field.${exactPath}`,
      `${namespace}.item.field.${last}`
    ]),
    `core.field.${exactPath}`,
    `core.field.${last}`
  ];

  const currentValue = lookupCurrentLocale(keys);
  if (currentValue) return currentValue;

  const fields = options.editorFields;
  const exact = fields?.[exactPath];
  const loose = fields?.[last];

  if (!isZhLocale()) return exact?.label || loose?.label || englishFallback(exactPath, options.fallback);

  for (const key of keys) {
    const value = t(key, undefined, '');
    if (value) return value;
  }

  if (exact?.label) return exact.label;
  if (loose?.label) return loose.label;

  return options.fallback || humanizeFieldLabel(exactPath);
}

// Resolve a field's help comment (the YAML comment shown as a tooltip). Returns '' when none is
// registered, so callers can omit the tooltip rather than show an empty bubble.
export function fieldComment(path: string, options: FieldLabelOptions = {}): string {
  const exactPath = String(path || '');
  const last = lastPathKey(exactPath);
  const namespaces = uniqueStrings([...namespaceVariants(options.namespace), ...namespaceVariants(options.moduleId)]);
  const keys = [
    ...namespaces.flatMap(namespace => [
      `${namespace}.comment.${exactPath}`,
      `${namespace}.comment.${last}`
    ]),
    `core.comment.${exactPath}`,
    `core.comment.${last}`
  ];
  const currentValue = lookupCurrentLocale(keys);
  if (currentValue) return currentValue;
  const fields = options.editorFields;
  const exact = fields?.[exactPath]?.comment;
  if (exact) return exact;
  const loose = fields?.[last]?.comment;
  if (loose) return loose;
  return '';
}

export function optionLabel(prefix: string, value: string, options: OptionLabelOptions = {}): string {  const text = String(value ?? '');
  if (!text) return text;
  const namespaces = uniqueStrings([...namespaceVariants(options.namespace), ...namespaceVariants(options.moduleId)]);
  const keys = [
    ...namespaces.map(namespace => `${namespace}.option.${prefix}.${text}`),
    `core.option.${prefix}.${text}`
  ];
  const currentValue = lookupCurrentLocale(keys);
  if (currentValue) return currentValue;
  if (!isZhLocale()) return options.fallback || text;
  for (const key of keys) {
    const translated = t(key, undefined, '');
    if (translated) return translated;
  }
  return options.fallback || text;
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

function isZhLocale(): boolean {
  return getLocale().toLowerCase().startsWith('zh');
}

function englishFallback(path: string, fallback: string | undefined): string {
  if (fallback && !/[^\u0000-\u00ff]/.test(fallback)) return fallback;
  return humanizeFieldLabel(path);
}

export function humanizeFieldLabel(path: string): string {
  if (/[^\u0000-\u00ff]/.test(path)) return path;
  return lastPathKey(path).replace(/_/g, ' ');
}

export function lastPathKey(path: string): string {
  return path.includes('.') ? path.slice(path.lastIndexOf('.') + 1) : path;
}

function namespaceVariants(value: string | undefined): string[] {
  const normalized = normalizeNamespace(value);
  if (!normalized) return [];
  if (normalized === 'core') return [normalized];
  const variants = [normalized];
  if (normalized.startsWith('emaki') && normalized.length > 'emaki'.length) variants.push(normalized.slice('emaki'.length));
  else variants.push(`emaki${normalized}`);
  return uniqueStrings(variants);
}

function uniqueStrings(values: string[]): string[] {
  return [...new Set(values.filter(Boolean))];
}

function normalizeNamespace(value: string | undefined): string {
  return String(value ?? '').trim().replace(/[^a-zA-Z0-9_.-]+/g, '').toLowerCase();
}
