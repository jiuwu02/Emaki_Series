import type { WebEditorField } from '../types';
import { t } from '../i18n';

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
  const namespace = normalizeNamespace(options.namespace);
  const moduleNamespace = normalizeNamespace(options.moduleId);
  const keys = [
    namespace && `${namespace}.field.${exactPath}`,
    namespace && `${namespace}.field.${last}`,
    moduleNamespace && `${moduleNamespace}.field.${exactPath}`,
    moduleNamespace && `${moduleNamespace}.field.${last}`,
    namespace && `${namespace}.item.field.${exactPath}`,
    namespace && `${namespace}.item.field.${last}`,
    moduleNamespace && `${moduleNamespace}.item.field.${exactPath}`,
    moduleNamespace && `${moduleNamespace}.item.field.${last}`,
    `core.field.${exactPath}`,
    `core.field.${last}`
  ].filter(Boolean) as string[];

  for (const key of keys) {
    const value = t(key, undefined, '');
    if (value) return value;
  }

  const fields = options.editorFields;
  const exact = fields?.[exactPath];
  if (exact?.label) return exact.label;
  const loose = fields?.[last];
  if (loose?.label) return loose.label;

  return options.fallback || humanizeFieldLabel(exactPath);
}

export function optionLabel(prefix: string, value: string, options: OptionLabelOptions = {}): string {
  const text = String(value ?? '');
  if (!text) return text;
  const namespace = normalizeNamespace(options.namespace);
  const moduleNamespace = normalizeNamespace(options.moduleId);
  const keys = [
    namespace && `${namespace}.option.${prefix}.${text}`,
    moduleNamespace && `${moduleNamespace}.option.${prefix}.${text}`,
    `core.option.${prefix}.${text}`
  ].filter(Boolean) as string[];
  for (const key of keys) {
    const translated = t(key, undefined, '');
    if (translated) return translated;
  }
  return options.fallback || text;
}

export function humanizeFieldLabel(path: string): string {
  if (/[^\u0000-\u00ff]/.test(path)) return path;
  return lastPathKey(path).replace(/_/g, ' ');
}

export function lastPathKey(path: string): string {
  return path.includes('.') ? path.slice(path.lastIndexOf('.') + 1) : path;
}

function normalizeNamespace(value: string | undefined): string {
  return String(value ?? '').trim().replace(/[^a-zA-Z0-9_.-]+/g, '').toLowerCase();
}
