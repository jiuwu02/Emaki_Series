import type { WebManifestCapability, WebManifestV2 } from './types';

export function manifestHasCapability(manifest: WebManifestV2 | undefined, capability: WebManifestCapability): boolean {
  if (!manifest || !capability) return false;
  return (manifest.capabilities ?? []).map(normalizeCapability).includes(normalizeCapability(capability));
}

export function inferManifestCapabilities(manifest: WebManifestV2): WebManifestCapability[] {
  const capabilities = new Set<WebManifestCapability>((manifest.capabilities ?? []).map(normalizeCapability));
  if ((manifest.config?.metaFields?.length ?? 0) > 0 || (manifest.config?.fileSchemas?.length ?? 0) > 0 || (manifest.schemas?.length ?? 0) > 0) capabilities.add('config');
  if ((manifest.guiEditors?.length ?? 0) > 0) capabilities.add('gui');
  if ((manifest.itemEditors?.length ?? 0) > 0 || (manifest.itemFieldRenderers?.length ?? 0) > 0 || (manifest.itemPreviewFallbacks?.length ?? 0) > 0 || (manifest.effectTypes?.length ?? 0) > 0 || (manifest.sourceDocumentAdapters?.length ?? 0) > 0) capabilities.add('item');
  if ((manifest.previews?.length ?? 0) > 0) capabilities.add('preview');
  if ((manifest.insightDefinitions?.length ?? 0) > 0) capabilities.add('insight');
  if ((manifest.pluginApiRoutes?.length ?? 0) > 0) capabilities.add('pluginApi');
  if ((manifest.diagnostics?.length ?? 0) > 0) capabilities.add('diagnostics');
  return [...capabilities];
}

function normalizeCapability(capability: WebManifestCapability): WebManifestCapability {
  return String(capability ?? '').trim();
}
