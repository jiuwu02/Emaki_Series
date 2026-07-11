import type { WebManifestDiagnosticEntry, WebManifestV2 } from './types';

export function validateWebManifestV2(manifest: WebManifestV2): WebManifestDiagnosticEntry[] {
  const moduleId = manifest?.module?.id;
  const manifestId = moduleId || '<unknown>';
  const entries: WebManifestDiagnosticEntry[] = [];
  const add = (ruleId: string, severity: 'info' | 'warning' | 'error', message: string, path?: string) => {
    entries.push({ manifestId, moduleId, ruleId, severity, message, path });
  };

  if (!manifest || typeof manifest !== 'object') {
    return [{ manifestId, moduleId, ruleId: 'manifest.object', severity: 'error', message: 'Manifest must be an object.' }];
  }
  if (manifest.manifestVersion !== '2') add('manifest.version', 'error', 'manifestVersion must be "2".', 'manifestVersion');
  if (!moduleId || !String(moduleId).trim()) add('module.id', 'error', 'module.id is required.', 'module.id');

  for (const [index, schema] of (manifest.schemas ?? []).entries()) {
    if (!Array.isArray(schema.fields)) add('schema.fields', 'error', 'Schema fields must be an array.', `schemas[${index}].fields`);
    if (!schema.pathPrefix && !schema.pathPattern) add('schema.path', 'warning', 'Schema should declare pathPrefix or pathPattern.', `schemas[${index}]`);
  }
  for (const [index, preview] of (manifest.previews ?? []).entries()) {
    if (!preview.component) add('preview.component', 'error', 'Preview registration requires a component.', `previews[${index}].component`);
    if (!preview.pathPrefix && !preview.pathPattern && !preview.kind) add('preview.scope', 'warning', 'Preview should declare kind, pathPrefix, or pathPattern.', `previews[${index}]`);
  }
  for (const [index, editor] of (manifest.guiEditors ?? []).entries()) {
    if (!editor.editorId) add('guiEditor.editorId', 'error', 'GUI editor requires editorId.', `guiEditors[${index}].editorId`);
    if (!editor.label) add('guiEditor.label', 'error', 'GUI editor requires label.', `guiEditors[${index}].label`);
  }
  for (const [index, editor] of (manifest.itemEditors ?? []).entries()) {
    if (!editor.editorId) add('itemEditor.editorId', 'error', 'Item editor requires editorId.', `itemEditors[${index}].editorId`);
    if (!editor.descriptor && !(editor.fields?.length)) add('itemEditor.content', 'warning', 'Item editor should declare descriptor or fields.', `itemEditors[${index}]`);
  }
  for (const [index, renderer] of (manifest.itemFieldRenderers ?? []).entries()) {
    if (!renderer.type) add('itemFieldRenderer.type', 'error', 'Item field renderer requires type.', `itemFieldRenderers[${index}].type`);
    if (!renderer.renderer) add('itemFieldRenderer.renderer', 'error', 'Item field renderer requires renderer.', `itemFieldRenderers[${index}].renderer`);
  }
  for (const [index, previewFallback] of (manifest.itemPreviewFallbacks ?? []).entries()) {
    if (!previewFallback.fallback) add('itemPreviewFallback.fallback', 'error', 'Item preview fallback requires fallback.', `itemPreviewFallbacks[${index}].fallback`);
  }
  for (const [index, adapter] of (manifest.sourceDocumentAdapters ?? []).entries()) {
    if (!adapter.adapter) add('sourceDocumentAdapter.adapter', 'error', 'Source document adapter requires adapter.', `sourceDocumentAdapters[${index}].adapter`);
    if (!adapter.kind && !adapter.editorId) add('sourceDocumentAdapter.scope', 'warning', 'Source document adapter should declare kind or editorId.', `sourceDocumentAdapters[${index}]`);
  }
  for (const [index, label] of (manifest.fileKindLabels ?? []).entries()) {
    if (!label.kind) add('fileKindLabel.kind', 'error', 'File kind label requires kind.', `fileKindLabels[${index}].kind`);
    if (!label.label) add('fileKindLabel.label', 'error', 'File kind label requires label.', `fileKindLabels[${index}].label`);
  }
  for (const [index, locale] of (manifest.locales ?? []).entries()) {
    if (!locale.locale) add('locale.id', 'error', 'Locale bundle requires locale.', `locales[${index}].locale`);
    if (!locale.messages || typeof locale.messages !== 'object') add('locale.messages', 'error', 'Locale bundle requires messages.', `locales[${index}].messages`);
  }
  for (const [index, route] of (manifest.pluginApiRoutes ?? []).entries()) {
    if (!route.id) add('pluginApi.id', 'error', 'Plugin API route requires id.', `pluginApiRoutes[${index}].id`);
    if (!route.route) add('pluginApi.route', 'error', 'Plugin API route requires route.', `pluginApiRoutes[${index}].route`);
  }

  if (entries.length === 0) add('manifest.valid', 'info', 'Manifest v2 validation passed.');
  return entries;
}

export function hasManifestErrors(entries: WebManifestDiagnosticEntry[]): boolean {
  return entries.some(entry => entry.severity === 'error');
}
