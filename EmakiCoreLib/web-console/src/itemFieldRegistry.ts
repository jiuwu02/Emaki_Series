import React from 'react';
import type { ActionTypesResult } from './api';
import type { AnyMap } from './itemEditor';
import type { WebEditorField } from './types';

export type CoreEffectType = 'variables' | 'name_action' | 'lore_action';

export const CORE_EFFECT_TYPES: CoreEffectType[] = ['variables', 'name_action', 'lore_action'];

export function isCoreEffectType(type: string): type is CoreEffectType {
  return CORE_EFFECT_TYPES.includes(type as CoreEffectType);
}

export function createCoreEffect(type: CoreEffectType): AnyMap {
  if (type === 'variables') return { type, variables: {} };
  if (type === 'name_action') return { type, name_actions: [] };
  if (type === 'lore_action') return { type, lore_actions: [] };
  return { type };
}

export function coreEffectTypeLabel(type: string): string {
  return { variables: '变量', name_action: '名称动作链', lore_action: 'Lore 动作链' }[type] ?? type;
}

export type ItemFieldRendererContext = {
  data: AnyMap;
  originalData: AnyMap;
  field: WebEditorField;
  value: unknown;
  changed: boolean;
  actionTypesResult: ActionTypesResult | null;
  economyProviders: string[];
  editorFields: Record<string, WebEditorField>;
  moduleId: string;
  setField: (path: string, value: unknown) => void;
  renderDefault: () => React.ReactNode;
};

export type ItemFieldRenderer = (context: ItemFieldRendererContext) => React.ReactNode;

type RendererRegistration = {
  type: string;
  moduleId?: string;
  editorId?: string;
  renderer: ItemFieldRenderer;
  priority: number;
};

const renderers: RendererRegistration[] = [];

export function registerItemFieldRenderer(type: string, renderer: ItemFieldRenderer, options: { moduleId?: string; editorId?: string; priority?: number } = {}): void {
  if (!type || !renderer) return;
  const registration = { type, renderer, moduleId: options.moduleId, editorId: options.editorId, priority: options.priority ?? 0 };
  const duplicateIndex = renderers.findIndex(entry => entry.type === type && entry.moduleId === registration.moduleId && entry.editorId === registration.editorId);
  if (duplicateIndex >= 0) renderers.splice(duplicateIndex, 1, registration);
  else renderers.push(registration);
  renderers.sort((left, right) => right.priority - left.priority);
}

export function getItemFieldRenderer(type: string, moduleId?: string, editorId?: string): ItemFieldRenderer | undefined {
  return renderers.find(entry => entry.type === type && (!entry.moduleId || entry.moduleId === moduleId) && (!entry.editorId || entry.editorId === editorId))?.renderer;
}
