import { registerModuleLocale } from '../../i18n';
import { registerEffectTypes } from '../../effectTypeRegistry';
import { registerItemFieldRenderer, registerItemPreviewFallback } from '../../itemFieldRegistry';
import {
  registerConfigPreview,
  registerEditorDescriptor,
  registerEditorField,
  registerFileKindLabel,
  registerInsightDefinition,
  registerPluginConfig,
  registerPluginGuiEditor,
  registerSourceDocumentAdapter,
  registerSurface
} from '../../registry';
import type { WebManifestDiagnosticEntry, WebManifestRegistrationResult, WebManifestRuntimeSnapshot, WebManifestV2 } from './types';
import { inferManifestCapabilities } from './capability';
import { validateWebManifestV2 } from './validator';

const manifests = new Map<string, WebManifestV2>();
const diagnostics = new Map<string, WebManifestDiagnosticEntry[]>();

export function registerWebManifestV2(input: WebManifestV2): WebManifestRegistrationResult {
  const manifest = normalizeWebManifestV2(input);
  const validation = storeManifestDiagnostics(manifest);
  if (!validation.some(entry => entry.severity === 'error')) applyWebManifestV2(manifest);
  return { manifest, diagnostics: validation };
}

export function recordLegacyPluginConfigManifest(moduleId: string, config: WebManifestV2['config']): WebManifestRegistrationResult {
  const key = normalizeModuleId(moduleId);
  const existing = manifests.get(key);
  if (existing) return { manifest: copyManifest(existing), diagnostics: getWebManifestDiagnostics(moduleId) };
  const manifest = legacyPluginConfigToManifest(moduleId, config);
  const validation = storeManifestDiagnostics(manifest);
  return { manifest, diagnostics: validation };
}

export function defineWebManifestV2(input: Omit<WebManifestV2, 'manifestVersion'> & { manifestVersion?: '2' }): WebManifestV2 {
  return normalizeWebManifestV2(input as WebManifestV2);
}

export function getWebManifestV2(moduleId: string): WebManifestV2 | undefined {
  return manifests.get(normalizeModuleId(moduleId));
}

export function getAllWebManifestsV2(): WebManifestV2[] {
  return [...manifests.values()].map(copyManifest);
}

export function getWebManifestDiagnostics(moduleId?: string): WebManifestDiagnosticEntry[] {
  if (moduleId) return [...(diagnostics.get(normalizeModuleId(moduleId)) ?? [])];
  return [...diagnostics.values()].flatMap(entries => entries.map(entry => ({ ...entry })));
}

export function getWebManifestRuntimeSnapshot(): WebManifestRuntimeSnapshot {
  return { manifests: getAllWebManifestsV2(), diagnostics: getWebManifestDiagnostics() };
}

export function applyWebManifestV2(manifest: WebManifestV2): void {
  const moduleId = manifest.module.id;
  for (const locale of manifest.locales ?? []) registerModuleLocale(moduleId, locale.locale, locale.messages);
  if (manifest.config || manifest.schemas?.length) {
    registerPluginConfig({
      moduleId,
      metaFields: manifest.config?.metaFields ?? [],
      fileSchemas: [...(manifest.config?.fileSchemas ?? []), ...(manifest.schemas ?? [])],
      ruleFields: manifest.config?.ruleFields ?? {},
      rules: manifest.config?.rules ?? [],
      createTemplates: manifest.config?.createTemplates ?? [],
      listItemSchemas: manifest.config?.listItemSchemas ?? [],
      listItemSchemaRules: manifest.config?.listItemSchemaRules ?? []
    });
  }
  for (const surface of manifest.surfaces ?? []) registerSurface({ ...surface, moduleId: surface.moduleId ?? moduleId });
  for (const preview of manifest.previews ?? []) registerConfigPreview({ ...preview, moduleId: preview.moduleId ?? moduleId });
  for (const editor of manifest.guiEditors ?? []) registerPluginGuiEditor({ ...editor, moduleId: editor.moduleId ?? moduleId });
  for (const editor of manifest.itemEditors ?? []) {
    if (editor.descriptor) registerEditorDescriptor(moduleId, editor.editorId, editor.descriptor);
    for (const field of editor.fields ?? []) registerEditorField(moduleId, editor.editorId, field);
  }
  for (const renderer of manifest.itemFieldRenderers ?? []) registerItemFieldRenderer(renderer.type, renderer.renderer, { moduleId, editorId: renderer.editorId, priority: renderer.priority });
  for (const previewFallback of manifest.itemPreviewFallbacks ?? []) registerItemPreviewFallback(previewFallback.fallback, { moduleId, editorId: previewFallback.editorId, kind: previewFallback.kind, priority: previewFallback.priority });
  if (manifest.effectTypes?.length) registerEffectTypes(moduleId, manifest.effectTypes);
  for (const adapter of manifest.sourceDocumentAdapters ?? []) registerSourceDocumentAdapter({ ...adapter, moduleId });
  for (const label of manifest.fileKindLabels ?? []) registerFileKindLabel(label.kind, typeof label.label === 'function' ? label.label() : label.label);
  for (const definition of manifest.insightDefinitions ?? []) registerInsightDefinition({ ...definition, moduleId: definition.moduleId ?? moduleId });
}

export function legacyPluginConfigToManifest(moduleId: string, config: WebManifestV2['config']): WebManifestV2 {
  return defineWebManifestV2({ module: { id: moduleId }, config, capabilities: ['config', 'legacyRegistration'] });
}

function storeManifestDiagnostics(manifest: WebManifestV2): WebManifestDiagnosticEntry[] {
  const moduleId = manifest.module.id;
  const validation = validateWebManifestV2(manifest);
  manifests.set(normalizeModuleId(moduleId), manifest);
  diagnostics.set(normalizeModuleId(moduleId), validation);
  return validation;
}

function normalizeWebManifestV2(input: WebManifestV2): WebManifestV2 {
  const manifest: WebManifestV2 = {
    ...input,
    manifestVersion: '2',
    module: {
      id: String(input?.module?.id ?? '').trim(),
      displayName: input?.module?.displayName,
      summaryKey: input?.module?.summaryKey,
      icon: input?.module?.icon,
      tone: input?.module?.tone
    },
    files: [...(input.files ?? [])],
    schemas: [...(input.schemas ?? [])],
    config: input.config ? { ...input.config } : undefined,
    surfaces: [...(input.surfaces ?? [])],
    previews: [...(input.previews ?? [])],
    guiEditors: [...(input.guiEditors ?? [])],
    itemEditors: [...(input.itemEditors ?? [])],
    itemFieldRenderers: [...(input.itemFieldRenderers ?? [])],
    itemPreviewFallbacks: [...(input.itemPreviewFallbacks ?? [])],
    effectTypes: [...(input.effectTypes ?? [])],
    sourceDocumentAdapters: [...(input.sourceDocumentAdapters ?? [])],
    fileKindLabels: [...(input.fileKindLabels ?? [])],
    insightDefinitions: [...(input.insightDefinitions ?? [])],
    pluginApiRoutes: [...(input.pluginApiRoutes ?? [])],
    locales: [...(input.locales ?? [])],
    capabilities: [...new Set(inferManifestCapabilities(input))],
    diagnostics: [...(input.diagnostics ?? [])]
  };
  return manifest;
}

function copyManifest(manifest: WebManifestV2): WebManifestV2 {
  return normalizeWebManifestV2(manifest);
}

function normalizeModuleId(moduleId: string): string {
  return String(moduleId ?? '').trim().toUpperCase();
}
