import { registerModuleLocale, type LocaleMessages } from '../../i18n';
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
  const derivedLocales = deriveManifestLocaleMessages(manifest);
  for (const [locale, messages] of Object.entries(derivedLocales)) registerModuleLocale(moduleId, locale, messages);
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

function deriveManifestLocaleMessages(manifest: WebManifestV2): Record<string, LocaleMessages> {
  const moduleId = manifest.module.id;
  const namespace = moduleId.toLowerCase();
  const localeMessages: Record<string, LocaleMessages> = { 'zh-CN': {}, 'en-US': {} };
  const put = (key: string, value: unknown) => {
    const text = typeof value === 'function' ? undefined : String(value ?? '').trim();
    if (!key || !text) return;
    localeMessages['zh-CN'][key] ??= text;
    localeMessages['en-US'][key] ??= text;
  };
  put(`${namespace}.module.name`, manifest.module.displayName ?? moduleId);
  for (const file of manifest.files ?? []) {
    const id = String(file.id ?? '').trim();
    if (!id) continue;
    if (file.titleKey) put(file.titleKey, file.path || id);
    else put(`${namespace}.file.${id}.title`, file.path || id);
    if (file.commentKey) put(file.commentKey, file.path || id);
  }
  for (const field of manifest.config?.metaFields ?? []) collectConfigFieldLocale(namespace, field, put);
  for (const schema of [...(manifest.config?.fileSchemas ?? []), ...(manifest.schemas ?? [])]) for (const field of schema.fields ?? []) collectConfigFieldLocale(namespace, field, put);
  for (const [path, field] of Object.entries(manifest.config?.ruleFields ?? {})) collectRuleFieldLocale(namespace, path, field, put);
  for (const [, template] of manifest.config?.createTemplates ?? []) collectCreateTemplateLocale(namespace, template, put);
  for (const [, fields] of manifest.config?.listItemSchemas ?? []) for (const field of fields ?? []) collectWebFieldLocale(namespace, field, put);
  for (const [, fields] of manifest.config?.listItemSchemaRules ?? []) for (const field of fields ?? []) collectWebFieldLocale(namespace, field, put);
  for (const editor of manifest.itemEditors ?? []) {
    if (editor.descriptor) collectEditorDescriptorLocale(namespace, editor.editorId, editor.descriptor, put);
    for (const field of editor.fields ?? []) collectEditorFieldLocale(namespace, field, put);
  }
  for (const editor of manifest.guiEditors ?? []) {
    if (editor.label) put(`${namespace}.editor.${editor.editorId}.title`, editor.label);
    for (const field of editor.fields ?? []) collectStandardGuiFieldLocale(namespace, field, put);
    for (const field of Object.values(editor.descriptor?.fields ?? {})) collectEditorFieldLocale(namespace, field, put);
  }
  return Object.fromEntries(Object.entries(localeMessages).filter(([, messages]) => Object.keys(messages).length > 0));
}

function collectConfigFieldLocale(namespace: string, field: unknown, put: (key: string, value: unknown) => void): void {
  if (!Array.isArray(field)) return;
  const [path, label, comment, , extra] = field;
  collectPathLocale(namespace, path, label, comment, put);
  if (extra && typeof extra === 'object') collectWebFieldExtrasLocale(namespace, extra as { itemFields?: unknown[]; createTemplates?: unknown[] }, put);
}

function collectRuleFieldLocale(namespace: string, path: string, field: unknown, put: (key: string, value: unknown) => void): void {
  if (!Array.isArray(field)) return;
  const [label, comment, , extra] = field;
  const normalizedPath = String(path ?? '').trim();
  if (normalizedPath) collectPathLocale(namespace, normalizedPath, label, comment, put);
  if (extra && typeof extra === 'object') collectWebFieldExtrasLocale(namespace, extra as { itemFields?: unknown[]; createTemplates?: unknown[] }, put);
}

function collectWebFieldLocale(namespace: string, field: unknown, put: (key: string, value: unknown) => void): void {
  if (!field || typeof field !== 'object') return;
  const entry = field as { path?: unknown; label?: unknown; comment?: unknown };
  collectPathLocale(namespace, entry.path, entry.label, entry.comment, put);
  collectWebFieldExtrasLocale(namespace, field as { itemFields?: unknown[]; createTemplates?: unknown[] }, put);
}

function collectEditorFieldLocale(namespace: string, field: unknown, put: (key: string, value: unknown) => void): void {
  collectWebFieldLocale(namespace, field, put);
}

function collectStandardGuiFieldLocale(namespace: string, field: unknown, put: (key: string, value: unknown) => void): void {
  if (!Array.isArray(field)) return;
  const [path, label, comment] = field;
  collectPathLocale(namespace, path, label, comment, put);
}

function collectEditorDescriptorLocale(namespace: string, editorId: string, descriptor: { title?: unknown; kindLabel?: unknown; sections?: Array<{ title?: unknown; titleKey?: string; comment?: unknown; commentKey?: string; fields?: unknown[] }>; fields?: Record<string, unknown> }, put: (key: string, value: unknown) => void): void {
  const normalizedEditorId = editorId.replace(/[^a-z0-9_-]+/gi, '.');
  put(`${namespace}.editor.${normalizedEditorId}.title`, descriptor.title);
  put(`${namespace}.editor.${normalizedEditorId}.kind`, descriptor.kindLabel);
  for (const section of descriptor.sections ?? []) {
    if (section.titleKey) put(section.titleKey, section.title);
    if (section.commentKey) put(section.commentKey, section.comment);
    for (const field of section.fields ?? []) collectEditorFieldLocale(namespace, field, put);
  }
  for (const field of Object.values(descriptor.fields ?? {})) collectEditorFieldLocale(namespace, field, put);
}

function collectWebFieldExtrasLocale(namespace: string, field: { itemFields?: unknown[]; createTemplates?: unknown[] }, put: (key: string, value: unknown) => void): void {
  for (const child of field.itemFields ?? []) collectWebFieldLocale(namespace, child, put);
  for (const template of field.createTemplates ?? []) collectCreateTemplateLocale(namespace, template, put);
}

function collectCreateTemplateLocale(namespace: string, template: unknown, put: (key: string, value: unknown) => void): void {
  if (!template || typeof template !== 'object') return;
  const entry = template as { id?: unknown; label?: unknown; fields?: unknown[] };
  const templateId = String(entry.id ?? '').trim();
  if (templateId) put(`${namespace}.template.${templateId}.label`, entry.label);
  for (const field of entry.fields ?? []) collectWebFieldLocale(namespace, field, put);
}

function collectPathLocale(namespace: string, path: unknown, label: unknown, comment: unknown, put: (key: string, value: unknown) => void): void {
  const normalizedPath = String(path ?? '').trim();
  if (!normalizedPath) return;
  put(`${namespace}.field.${normalizedPath}`, label);
  put(`${namespace}.comment.${normalizedPath}`, comment);
}

function normalizeModuleId(moduleId: string): string {
  return String(moduleId ?? '').trim().toUpperCase();
}
