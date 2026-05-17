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
import React, { type ComponentType } from 'react';
import type { ApiClient } from './api';
import * as components from './components';
import * as lib from './lib';
import * as i18n from './i18n';
import type { WebEditorDescriptor, WebEditorField, WebRegistry, WebRegistryFile, WebRegistryModule } from './types';

/** Props passed to every registered surface component. */
export type SurfaceProps = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  api: ApiClient;
  childPath?: string;
  refreshKey?: number;
  editor?: WebEditorDescriptor;
  onReload?: () => void;
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

export type EmakiWebConsoleHost = typeof lib & typeof components & typeof i18n & {
  React: typeof React;
  registerSurface: typeof registerSurface;
  getSurface: typeof getSurface;
  getAllSurfaces: typeof getAllSurfaces;
  isKind: typeof isKind;
  registerPluginGuiSurface: typeof registerPluginGuiSurface;
  registerPluginSurfaces: typeof registerPluginSurfaces;
  registerEditorDescriptor: typeof registerEditorDescriptor;
  registerEditorField: typeof registerEditorField;
  registerGuiEditorDescriptor: typeof registerGuiEditorDescriptor;
  registerGuiEditorField: typeof registerGuiEditorField;
  components: typeof components;
  lib: typeof lib;
  i18n: typeof i18n;
  t: typeof i18n.t;
  registerLocale: typeof i18n.registerLocale;
  registerModuleLocale: typeof i18n.registerModuleLocale;
};

const _registry: SurfaceRegistration[] = [];
const _editorOverrides: Record<string, WebEditorDescriptor> = {};

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

/** Check if a kind string matches (case-insensitive). */
export function isKind(fileKind: string | undefined, target: string): boolean {
  return normalize(fileKind) === normalize(target);
}

/** Install the browser global used by plugin extension scripts. */
export function installWebConsoleHost(): EmakiWebConsoleHost {
  const host: EmakiWebConsoleHost = { ...lib, ...components, ...i18n, React, registerSurface, getSurface, getAllSurfaces, isKind, registerPluginGuiSurface, registerPluginSurfaces, registerEditorDescriptor, registerEditorField, registerGuiEditorDescriptor, registerGuiEditorField, components, lib, i18n, t: i18n.t, registerLocale: i18n.registerLocale, registerModuleLocale: i18n.registerModuleLocale };
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
