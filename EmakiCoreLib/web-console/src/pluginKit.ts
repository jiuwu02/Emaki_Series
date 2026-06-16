import { getLocale } from './i18n';
import { CORE_EFFECT_TYPE_DEFINITIONS, type EffectPayloadField, type EffectTypeDefinition } from './effectTypeRegistry';
import type { ConfigMetaFieldEntry } from './registry';
import type { WebEditorField } from './types';

export type ConfigFieldSpec = ConfigMetaFieldEntry;
export type EditorFieldSpec = [
  path: string,
  label: string,
  comment: string,
  type: string,
  extra?: Partial<WebEditorField> & Record<string, unknown>
];

export function localeText(zh: string, en: string): string {
  return getLocale().startsWith('zh') ? zh : en;
}

export function coreEffectDefinition(type: string): EffectTypeDefinition {
  const definition = CORE_EFFECT_TYPE_DEFINITIONS.find(entry => entry.type === type);
  if (!definition) throw new Error(`Unknown CoreLib effect type: ${type}`);
  return definition;
}

export function payloadEffectDefinition(type: string, label: string, fields: EffectPayloadField[]): EffectTypeDefinition {
  return {
    type,
    label,
    fields: fields.map(field => ({ ...field, options: field.options ? [...field.options] : undefined }))
  };
}

export function injectExtensionStyles(id: string, css: string): HTMLStyleElement | null {
  if (typeof document === 'undefined') return null;
  const normalizedId = String(id ?? '').trim();
  if (!normalizedId || !css) return null;
  const existing = Array.from(document.querySelectorAll<HTMLStyleElement>('style[data-emaki-extension-style]'))
    .find(style => style.getAttribute('data-emaki-extension-style') === normalizedId);
  if (existing) {
    if (existing.textContent !== css) existing.textContent = css;
    return existing;
  }
  const style = document.createElement('style');
  style.setAttribute('data-emaki-extension-style', normalizedId);
  style.textContent = css;
  document.head.appendChild(style);
  return style;
}
