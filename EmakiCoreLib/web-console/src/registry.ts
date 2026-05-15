/**
 * Surface Registry — allows plugins to register custom editor surfaces.
 *
 * Each surface is matched by `kind` (from WebRegistryFile.kind).
 * CoreLib registers built-in surfaces (CONFIG, GUI, ITEM, SCRIPT).
 * External plugins can call `registerSurface()` to add their own.
 */
import type { ComponentType } from 'react';
import type { ApiClient } from './api';
import type { WebEditorDescriptor, WebRegistryFile, WebRegistryModule } from './types';

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
  /** Match against WebRegistryFile.kind (case-insensitive). */
  kind: string;
  /** The React component to render for this surface. */
  component: ComponentType<SurfaceProps>;
  /** Optional label shown in the tree (e.g. "GUI", "物品"). */
  label?: string;
  /** Priority: higher wins when multiple registrations match the same kind. Default 0. */
  priority?: number;
};

const _registry: SurfaceRegistration[] = [];

/** Register a surface. Later registrations with higher priority override earlier ones. */
export function registerSurface(reg: SurfaceRegistration): void {
  _registry.push(reg);
  _registry.sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0));
}

/** Find the best surface for a given file kind. */
export function getSurface(kind: string): SurfaceRegistration | undefined {
  const normalized = kind.toUpperCase();
  return _registry.find(r => r.kind.toUpperCase() === normalized);
}

/** Get all registered surfaces. */
export function getAllSurfaces(): SurfaceRegistration[] {
  return [..._registry];
}

/** Check if a kind string matches (case-insensitive). */
export function isKind(fileKind: string | undefined, target: string): boolean {
  return fileKind?.toUpperCase() === target.toUpperCase();
}
