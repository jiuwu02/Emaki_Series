/**
 * Surface Registry — allows plugins to register custom editor surfaces.
 *
 * Matching order:
 * 1. editorId exact match
 * 2. moduleId + kind match
 * 3. kind match
 *
 * External extension scripts can access this through window.EmakiWebConsole.
 */
import React, { type ComponentType, type ReactNode } from 'react';
import type { ApiClient, TextDocument } from './api';
import * as components from './components';
import * as lib from './lib';
import * as i18n from './i18n';
import type { EditorChange } from './components';
import type { WebEditorDescriptor, WebEditorField, WebRegistry, WebRegistryFile, WebRegistryModule, WebConsoleExtensionStatus } from './types';

export type SourceDocumentAdapterContext = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  childPath?: string;
  editor?: WebEditorDescriptor;
};

export type SourceDocumentAdapter = {
  read: (api: ApiClient, context: SourceDocumentAdapterContext) => Promise<TextDocument>;
  save: (api: ApiClient, context: SourceDocumentAdapterContext, content: string, revision?: number) => Promise<{ revision?: number }>;
  parse?: (content: string) => unknown;
  serialize?: (data: unknown) => string;
  language?: 'yaml' | 'javascript' | 'text' | string;
};

export type SurfaceToolbarState = {
  title?: ReactNode;
  subtitle?: ReactNode;
  dirty: boolean;
  changes?: EditorChange[];
  changedCount?: number;
  source?: string;
  sourceEditable?: boolean;
  sourceError?: string | null;
  sourceLanguage?: string;
  saving?: boolean;
  loading?: boolean;
  saveLabel?: string;
  sourceLabel?: string;
  reloadLabel?: string;
  canUndo?: boolean;
  canRedo?: boolean;
  onUndo?: () => void;
  onRedo?: () => void;
  onSave?: () => void;
  onReload?: () => void;
  onSourceChange?: (source: string) => void;
};

/** Props passed to every registered surface component. */
export type SurfaceProps = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  api: ApiClient;
  childPath?: string;
  refreshKey?: number;
  editor?: WebEditorDescriptor;
  onReload?: () => void;
  setToolbar?: (state: SurfaceToolbarState | null) => void;
  showLocalChrome?: boolean;
};

export type SurfaceRegistration = {
  /** Match against WebRegistryFile.kind (case-insensitive). Optional when editorId is provided. */
  kind?: string;
  /** Match against WebRegistryFile.moduleId or owning module id. */
  moduleId?: string;
  /** Exact match against WebRegistryFile.editorId / editor.id. Best for plugin-specific pages. */
  editorId?: string;
  /** The React component to render for this surface. */
  component: ComponentType<SurfaceProps>;
  /** Optional label shown in the tree (e.g. "GUI", "物品"). */
  label?: string;
  /** Priority: higher wins when multiple registrations match the same scope. Default 0. */
  priority?: number;
};

export type StandardGuiFieldEntry = [path: string, label: string, comment: string, type: string];

export type PluginGuiEditorRegistration = {
  moduleId: string;
  editorId: string;
  label: string;
  title?: string;
  kindLabel?: string;
  fields?: StandardGuiFieldEntry[];
  descriptor?: Partial<WebEditorDescriptor>;
};

export type EmakiWebConsoleHost = typeof lib & typeof components & typeof i18n & {
  apiVersion: string;
  React: typeof React;
  registerSurface: typeof registerSurface;
  getSurface: typeof getSurface;
  getAllSurfaces: typeof getAllSurfaces;
  isKind: typeof isKind;
  registerPluginGuiSurface: typeof registerPluginGuiSurface;
  registerPluginGuiEditor: typeof registerPluginGuiEditor;
  registerPluginSurfaces: typeof registerPluginSurfaces;
  standardGuiFields: typeof standardGuiFields;
  registerEditorDescriptor: typeof registerEditorDescriptor;
  registerEditorField: typeof registerEditorField;
  registerSourceDocumentAdapter: typeof registerSourceDocumentAdapter;
  getSourceDocumentAdapter: typeof getSourceDocumentAdapter;
  registerGuiEditorDescriptor: typeof registerGuiEditorDescriptor;
  registerGuiEditorField: typeof registerGuiEditorField;
  recordExtensionStatus: typeof recordExtensionStatus;
  getExtensionStatuses: typeof getExtensionStatuses;
  components: typeof components;
  lib: typeof lib;
  i18n: typeof i18n;
  t: typeof i18n.t;
  registerLocale: typeof i18n.registerLocale;
  registerModuleLocale: typeof i18n.registerModuleLocale;
};

export const EMAKI_WEB_CONSOLE_API_VERSION = '1.1.0';

const _registry: SurfaceRegistration[] = [];
const _editorOverrides: Record<string, WebEditorDescriptor> = {};
const _sourceAdapters: SourceAdapterRegistration[] = [];
const _extensionStatuses: WebConsoleExtensionStatus[] = [];

type SourceAdapterRegistration = { kind?: string; moduleId?: string; editorId?: string; adapter: SourceDocumentAdapter; priority: number };

declare global {
  interface Window {
    EmakiWebConsole?: EmakiWebConsoleHost;
  }
}

function normalize(value: string | undefined): string {
  return String(value ?? '').toUpperCase();
}

/** Register a surface. Later registrations with higher priority override earlier ones. */
export function registerSurface(reg: SurfaceRegistration): void {
  if (!reg || !reg.component || (!reg.kind && !reg.editorId)) return;
  const next = {
    ...reg,
    kind: reg.kind ? normalize(reg.kind) : undefined,
    moduleId: reg.moduleId ? normalize(reg.moduleId) : undefined,
    editorId: reg.editorId ? String(reg.editorId) : undefined,
  };
  const duplicate = _registry.findIndex(existing =>
    normalize(existing.kind) === normalize(next.kind)
    && normalize(existing.moduleId) === normalize(next.moduleId)
    && String(existing.editorId ?? '') === String(next.editorId ?? '')
  );
  if (duplicate >= 0) _registry.splice(duplicate, 1);
  _registry.push(next);
  _registry.sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0));
}

/** Find the best surface for a file/editor pair. */
export function getSurface(fileOrKind: WebRegistryFile | string | undefined, editor?: WebEditorDescriptor): SurfaceRegistration | undefined {
  if (!fileOrKind) return undefined;
  const file = typeof fileOrKind === 'string' ? undefined : fileOrKind;
  const kind = normalize(typeof fileOrKind === 'string' ? fileOrKind : fileOrKind.kind);
  const moduleId = normalize(file?.moduleId);
  const editorId = String(file?.editorId ?? editor?.id ?? '');

  if (editorId) {
    const byEditor = _registry.find(r => String(r.editorId ?? '') === editorId);
    if (byEditor) return byEditor;
  }
  if (moduleId) {
    const byModuleKind = _registry.find(r => normalize(r.kind) === kind && normalize(r.moduleId) === moduleId);
    if (byModuleKind) return byModuleKind;
  }
  return _registry.find(r => normalize(r.kind) === kind && !r.moduleId && !r.editorId);
}

/** Get all registered surfaces. */
export function getAllSurfaces(): SurfaceRegistration[] {
  return [..._registry];
}

export function recordExtensionStatus(status: WebConsoleExtensionStatus): void {
  const index = _extensionStatuses.findIndex(entry => entry.id === status.id && entry.moduleId === status.moduleId && entry.url === status.url);
  if (index >= 0) _extensionStatuses.splice(index, 1, status);
  else _extensionStatuses.push(status);
}

export function getExtensionStatuses(): WebConsoleExtensionStatus[] {
  return [..._extensionStatuses];
}

export function standardGuiFields(entries: StandardGuiFieldEntry[] = []): Record<string, WebEditorField> {
  const base: StandardGuiFieldEntry[] = [
    ['id', 'ID', 'GUI 模板唯一标识。', 'text'],
    ['gui_type', 'GUI 类型', 'Bukkit InventoryType。只有 CHEST 支持行数。', 'enum'],
    ['title', '标题', 'GUI 窗口标题，支持 MiniMessage。', 'text'],
    ['rows', '箱子行数', '仅 CHEST 类型可用，范围 1-6。', 'number'],
    ['type', '槽位类型', '插件业务识别的槽位语义。', 'text'],
    ['slots', '槽位', '槽位索引列表或槽位定义。', 'list'],
    ['item', '物品', '槽位显示物品，支持原版材料或 ItemSource。', 'text'],
    ['display_name', '显示名', '槽位物品显示名称，支持 MiniMessage。', 'text'],
    ['lore', 'Lore', '槽位物品描述，每行一条。', 'stringList']
  ];
  return Object.fromEntries([...base, ...entries].map(([path, label, comment, type]) => [path, { path, label, comment, type }]));
}

export function registerSourceDocumentAdapter(reg: { kind?: string; moduleId?: string; editorId?: string; adapter: SourceDocumentAdapter; priority?: number }): void {
  if (!reg?.adapter || (!reg.kind && !reg.editorId)) return;
  const next: SourceAdapterRegistration = {
    kind: reg.kind ? normalize(reg.kind) : undefined,
    moduleId: reg.moduleId ? normalize(reg.moduleId) : undefined,
    editorId: reg.editorId ? String(reg.editorId) : undefined,
    adapter: reg.adapter,
    priority: reg.priority ?? 0
  };
  const duplicate = _sourceAdapters.findIndex(existing =>
    normalize(existing.kind) === normalize(next.kind)
    && normalize(existing.moduleId) === normalize(next.moduleId)
    && String(existing.editorId ?? '') === String(next.editorId ?? '')
  );
  if (duplicate >= 0) _sourceAdapters.splice(duplicate, 1);
  _sourceAdapters.push(next);
  _sourceAdapters.sort((a, b) => b.priority - a.priority);
}

export function getSourceDocumentAdapter(fileOrKind: WebRegistryFile | string | undefined, editor?: WebEditorDescriptor): SourceDocumentAdapter | undefined {
  if (!fileOrKind) return undefined;
  const file = typeof fileOrKind === 'string' ? undefined : fileOrKind;
  const kind = normalize(typeof fileOrKind === 'string' ? fileOrKind : fileOrKind.kind);
  const moduleId = normalize(file?.moduleId);
  const editorId = String(file?.editorId ?? editor?.id ?? '');
  if (editorId) {
    const byEditor = _sourceAdapters.find(r => String(r.editorId ?? '') === editorId);
    if (byEditor) return byEditor.adapter;
  }
  if (moduleId) {
    const byModuleKind = _sourceAdapters.find(r => normalize(r.kind) === kind && normalize(r.moduleId) === moduleId);
    if (byModuleKind) return byModuleKind.adapter;
  }
  return _sourceAdapters.find(r => normalize(r.kind) === kind && !r.moduleId && !r.editorId)?.adapter;
}

/** Check if a kind string matches (case-insensitive). */
export function isKind(fileKind: string | undefined, target: string): boolean {
  return normalize(fileKind) === normalize(target);
}

/** Install the browser global used by plugin extension scripts. */
export function installWebConsoleHost(): EmakiWebConsoleHost {
  const host: EmakiWebConsoleHost = { ...lib, ...components, ...i18n, apiVersion: EMAKI_WEB_CONSOLE_API_VERSION, React, registerSurface, getSurface, getAllSurfaces, isKind, registerPluginGuiSurface, registerPluginGuiEditor, registerPluginSurfaces, standardGuiFields, registerEditorDescriptor, registerEditorField, registerSourceDocumentAdapter, getSourceDocumentAdapter, registerGuiEditorDescriptor, registerGuiEditorField, recordExtensionStatus, getExtensionStatuses, components, lib, i18n, t: i18n.t, registerLocale: i18n.registerLocale, registerModuleLocale: i18n.registerModuleLocale };
  (window as any).React = React;
  window.EmakiWebConsole = host;
  return host;
}

/**
 * 一行注册一个直接复用 GuiEditorSurface 的 GUI surface。
 * 适用于只需要通用 GUI 编辑器的插件（如 EmakiSkills、EmakiForge、EmakiStrengthen）。
 */
export function registerPluginGuiSurface(moduleId: string, editorId: string, label: string): void {
  const { GuiEditorSurface } = components;
  registerSurface({ kind: 'GUI', moduleId, editorId, component: GuiEditorSurface as any, label, priority: 100 });
  registerSurface({ kind: 'GUI', moduleId, component: GuiEditorSurface as any, label, priority: 90 });
}

export function registerPluginGuiEditor(registration: PluginGuiEditorRegistration): void {
  if (!registration?.moduleId || !registration.editorId || !registration.label) return;
  const { moduleId, editorId, label, title, kindLabel, fields = [], descriptor = {} } = registration;
  const descriptorFields = {
    ...standardGuiFields(fields),
    ...((descriptor.fields ?? {}) as Record<string, WebEditorField>)
  };
  registerPluginGuiSurface(moduleId, editorId, label);
  registerGuiEditorDescriptor(moduleId, editorId, {
    ...descriptor,
    id: descriptor.id ?? editorId,
    moduleId: descriptor.moduleId ?? moduleId,
    title: descriptor.title ?? title ?? label,
    kindLabel: descriptor.kindLabel ?? kindLabel ?? label,
    fields: descriptorFields
  });
}

export function registerEditorDescriptor(moduleId: string, editorId: string, descriptor: WebEditorDescriptor): void {
  if (!moduleId || !editorId || !descriptor) return;
  const existing = _editorOverrides[editorId];
  _editorOverrides[editorId] = mergeEditorDescriptor(existing, { ...descriptor, id: descriptor.id ?? editorId, moduleId: descriptor.moduleId ?? moduleId });
}

export function registerEditorField(moduleId: string, editorId: string, field: WebEditorField): void {
  if (!moduleId || !editorId || !field?.path || !field.label) return;
  const existing = _editorOverrides[editorId] ?? { id: editorId, moduleId, fields: {} };
  registerEditorDescriptor(moduleId, editorId, {
    ...existing,
    fields: {
      ...(existing.fields ?? {}),
      [field.path]: field
    }
  });
}

export function registerGuiEditorDescriptor(moduleId: string, editorId: string, descriptor: WebEditorDescriptor): void {
  registerEditorDescriptor(moduleId, editorId, descriptor);
}

export function registerGuiEditorField(moduleId: string, editorId: string, path: string, label: string, comment = '', type = 'text'): void {
  registerEditorField(moduleId, editorId, { path, label, comment, type });
}

export function applyEditorDescriptorOverrides(registry: WebRegistry): WebRegistry {
  const overrideEntries = Object.entries(_editorOverrides);
  if (!overrideEntries.length) return registry;
  const editors = { ...(registry.editors ?? {}) };
  for (const [editorId, override] of overrideEntries) {
    editors[editorId] = mergeEditorDescriptor(editors[editorId], override);
  }
  return { ...registry, editors };
}

function mergeEditorDescriptor(base: WebEditorDescriptor | undefined, override: WebEditorDescriptor): WebEditorDescriptor {
  return {
    ...(base ?? {}),
    ...override,
    fields: {
      ...((base?.fields ?? {}) as Record<string, WebEditorField>),
      ...((override.fields ?? {}) as Record<string, WebEditorField>)
    }
  };
}

/**
 * 批量注册多个 surface。
 */
export function registerPluginSurfaces(surfaces: SurfaceRegistration[]): void {
  surfaces.forEach(registerSurface);
}
