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
import * as previewKit from './ConfigPreviewKit';
import * as lib from './lib';
import * as i18n from './i18n';
import * as itemFieldRegistry from './itemFieldRegistry';
import * as effectTypeRegistry from './effectTypeRegistry';
import * as economyConfig from './economyConfig';
import * as pluginKit from './pluginKit';
import * as webManifest from './platform/manifest';
import { ItemEditorSurface } from './ItemEditorSurface';
import type { EditorChange } from './components';
import type { WebConfigCreateTemplate, WebConfigFieldSchema, WebConfigNode, WebEditorDescriptor, WebEditorField, WebRegistry, WebRegistryFile, WebRegistryModule, WebConsoleExtensionStatus, WebScriptCompletionEntry } from './types';

export type SourceDocumentAdapterContext = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  childPath?: string;
  path: string;
  editor?: WebEditorDescriptor;
};

export type SourceDocumentAdapter = {
  read: (api: ApiClient, context: SourceDocumentAdapterContext) => Promise<TextDocument>;
  save: (api: ApiClient, context: SourceDocumentAdapterContext, content: string, revision?: number) => Promise<{ revision?: number }>;
  parse?: (content: string) => unknown;
  serialize?: (data: unknown) => string;
  defaultContent?: (context: SourceDocumentAdapterContext & { name: string; path: string }) => string;
  language?: 'yaml' | 'javascript' | 'text' | string;
};

export type SurfaceOutlineItem = {
  path: string;
  label: string;
  type: string;
  childCount: number;
  changedCount: number;
  changed: boolean;
};

export type SurfaceOutlineState = {
  title: string;
  subtitle: string;
  items: SurfaceOutlineItem[];
  emptyText?: string;
} | null;

export type SurfaceToolbarState = {
  title?: ReactNode;
  subtitle?: ReactNode;
  dirty: boolean;
  changes?: EditorChange[];
  changedCount?: number;
  source?: string;
  sourceOriginal?: string;
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
  setOutline?: (state: SurfaceOutlineState) => void;
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

export type ConfigPreviewProps = {
  module: WebRegistryModule;
  file: WebRegistryFile;
  path: string;
  childPath?: string;
  nodes: WebConfigNode[];
  data: Record<string, unknown>;
  sourceContent: string;
  sourceDirty: boolean;
  sourceError?: string | null;
  api: ApiClient;
};

export type ConfigPreviewRegistration = {
  moduleId?: string;
  kind?: string;
  pathPrefix?: string;
  pathPattern?: string;
  component: ComponentType<ConfigPreviewProps>;
  label?: string;
  priority?: number;
};

export type InsightDefinitionRegistration = {
  moduleId?: string;
  pathPrefix?: string;
  pathPattern?: string;
  idType: string;
  idPath?: string;
  fallbackId?: 'basename' | 'none';
  priority?: number;
};

export type InsightDefinitionContext = {
  moduleId?: string;
  path?: string;
};

export type StandardGuiFieldEntry = [path: string, label: string, comment: string, type: string, extra?: Partial<WebEditorField>];
export type ConfigMetaFieldEntry = [path: string, label: string, comment: string, type?: string, extra?: ConfigNodeMetaOverride];
export type ConfigRuleFieldEntry = [label: string, comment: string, type?: string, extra?: ConfigNodeMetaOverride];
export type ConfigFileSchemaEntry = { pathPrefix?: string; pathPattern?: string; fields: ConfigMetaFieldEntry[] };
export type ConfigCreateTemplateEntry = [nodePath: string, template: WebConfigCreateTemplate];
export type ConfigListItemSchemaEntry = [listPath: string, fields: WebConfigFieldSchema[], options?: { uniqueBy?: string }];
export type ConfigListItemSchemaRuleEntry = [matcher: ConfigListItemSchemaRuleMatcher, fields: WebConfigFieldSchema[], options?: { uniqueBy?: string }];
export type PluginConfigRegistration = {
  moduleId: string;
  metaFields?: ConfigMetaFieldEntry[];
  fileSchemas?: ConfigFileSchemaEntry[];
  ruleFields?: Record<string, ConfigRuleFieldEntry>;
  rules?: Array<[matcher: ConfigNodeRuleMatcher, meta: ConfigNodeMetaOverride]>;
  createTemplates?: ConfigCreateTemplateEntry[];
  listItemSchemas?: ConfigListItemSchemaEntry[];
  listItemSchemaRules?: ConfigListItemSchemaRuleEntry[];
};

export type PluginGuiEditorRegistration = {
  moduleId: string;
  editorId: string;
  label: string;
  title?: string;
  kindLabel?: string;
  fields?: StandardGuiFieldEntry[];
  descriptor?: Partial<WebEditorDescriptor>;
};

export type ConfigNodeMetaOverride = {
  label?: string;
  comment?: string;
  type?: string;
  options?: string[];
  optionLabelPrefix?: string;
  creatableChildren?: boolean;
  createTemplates?: WebConfigCreateTemplate[];
  itemFields?: WebConfigFieldSchema[];
  uniqueBy?: string;
};

/**
 * @deprecated New plugin modules should model config fields through Schema AST
 * and Manifest v2 `schemas`/`config` declarations. Matcher-based metadata is
 * kept only as a compatibility bridge for older extension bundles and the
 * Manifest v2 loader's internal registry adapter.
 */
export type ConfigNodeRuleMatcher = {
  path?: string;
  key?: string;
  prefix?: string;
  suffix?: string;
  contains?: string;
} | ((path: string, node: WebConfigNode) => boolean);

export type ConfigListItemSchemaRuleMatcher = ConfigNodeRuleMatcher;

type ConfigNodeRuleRegistration = {
  moduleId: string;
  matcher: ConfigNodeRuleMatcher;
  meta: ConfigNodeMetaOverride;
};

type ConfigListItemSchemaRuleRegistration = {
  moduleId: string;
  matcher: ConfigListItemSchemaRuleMatcher;
  fields: WebConfigFieldSchema[];
  uniqueBy?: string;
};

type ConfigFileSchemaRegistration = {
  moduleId: string;
  pathPrefix?: string;
  pathPattern?: string;
  fields: ConfigMetaFieldEntry[];
};

export type EmakiWebConsoleHost = typeof lib & typeof components & typeof previewKit & typeof i18n & typeof itemFieldRegistry & typeof effectTypeRegistry & typeof economyConfig & typeof pluginKit & typeof webManifest & {
  apiVersion: string;
  React: typeof React;
  ItemEditorSurface: typeof ItemEditorSurface;
  registerSurface: typeof registerSurface;
  getSurface: typeof getSurface;
  getAllSurfaces: typeof getAllSurfaces;
  isKind: typeof isKind;
  registerPluginGuiSurface: typeof registerPluginGuiSurface;
  registerPluginGuiEditor: typeof registerPluginGuiEditor;
  registerPluginSurfaces: typeof registerPluginSurfaces;
  registerConfigPreview: typeof registerConfigPreview;
  getConfigPreview: typeof getConfigPreview;
  getAllConfigPreviews: typeof getAllConfigPreviews;
  registerInsightDefinition: typeof registerInsightDefinition;
  getInsightDefinition: typeof getInsightDefinition;
  standardGuiFields: typeof standardGuiFields;
  registerEditorDescriptor: typeof registerEditorDescriptor;
  registerEditorField: typeof registerEditorField;
  registerSourceDocumentAdapter: typeof registerSourceDocumentAdapter;
  getSourceDocumentAdapter: typeof getSourceDocumentAdapter;
  registerGuiEditorDescriptor: typeof registerGuiEditorDescriptor;
  registerGuiEditorField: typeof registerGuiEditorField;
  getRuntimeEnum: typeof getRuntimeEnum;
  registerFileKindLabel: typeof registerFileKindLabel;
  getFileKindLabel: typeof getFileKindLabel;
  registerConfigNodeMeta: typeof registerConfigNodeMeta;
  registerConfigNodeRule: typeof registerConfigNodeRule;
  registerConfigCreateTemplate: typeof registerConfigCreateTemplate;
  registerConfigMetaFields: typeof registerConfigMetaFields;
  registerConfigFileSchema: typeof registerConfigFileSchema;
  registerConfigFileSchemas: typeof registerConfigFileSchemas;
  registerConfigRuleFields: typeof registerConfigRuleFields;
  registerConfigCreateTemplates: typeof registerConfigCreateTemplates;
  registerConfigListItemSchemas: typeof registerConfigListItemSchemas;
  registerConfigListItemSchemaRules: typeof registerConfigListItemSchemaRules;
  registerConfigListItemSchema: typeof registerConfigListItemSchema;
  registerConfigListItemSchemaRule: typeof registerConfigListItemSchemaRule;
  registerPluginConfig: typeof registerPluginConfig;
  registerUniqueListField: typeof registerUniqueListField;
  recordExtensionStatus: typeof recordExtensionStatus;
  getExtensionStatuses: typeof getExtensionStatuses;
  registerJavaScriptMethod: typeof registerJavaScriptMethod;
  registerJavaScriptMethods: typeof registerJavaScriptMethods;
  getJavaScriptCompletionScopes: typeof getJavaScriptCompletionScopes;
  components: typeof components;
  previewKit: typeof previewKit;
  lib: typeof lib;
  i18n: typeof i18n;
  t: typeof i18n.t;
  registerLocale: typeof i18n.registerLocale;
  registerModuleLocale: typeof i18n.registerModuleLocale;
};

export const EMAKI_WEB_CONSOLE_API_VERSION = '1.2.0';

const _registry: SurfaceRegistration[] = [];
const _configPreviews: ConfigPreviewRegistration[] = [];
const _insightDefinitions: InsightDefinitionRegistration[] = [];
const _editorOverrides: Record<string, WebEditorDescriptor> = {};
const _sourceAdapters: SourceAdapterRegistration[] = [];
const _extensionStatuses: WebConsoleExtensionStatus[] = [];
const _serverJavaScriptCompletions: WebScriptCompletionEntry[] = [];
const _extensionJavaScriptCompletions: WebScriptCompletionEntry[] = [];
const _configNodeMeta: Record<string, ConfigNodeMetaOverride> = {};
const _configNodeMetaOrder: Record<string, string[]> = {};
const _configFileSchemas: ConfigFileSchemaRegistration[] = [];
const _configCreateTemplates: Record<string, WebConfigCreateTemplate[]> = {};
const _configListItemFields: Record<string, WebConfigFieldSchema[]> = {};
const _configUniqueListFields: Record<string, string> = {};
const _configNodeRules: ConfigNodeRuleRegistration[] = [];
const _configListItemSchemaRules: ConfigListItemSchemaRuleRegistration[] = [];
const _fileKindLabels: Record<string, string> = {};
let _runtimeEnums: Record<string, string[]> = {};

type SourceAdapterRegistration = { kind?: string; moduleId?: string; editorId?: string; adapter: SourceDocumentAdapter; priority: number };

declare global {
  interface Window {
    EmakiWebConsole?: EmakiWebConsoleHost;
  }
}

function normalize(value: string | undefined): string {
  return String(value ?? '').toUpperCase();
}

function normalizeModuleVariants(value: string | undefined): string[] {
  const normalized = normalize(value).trim();
  if (!normalized) return [];
  const variants = [normalized];
  if (normalized.startsWith('EMAKI') && normalized.length > 'EMAKI'.length) variants.push(normalized.slice('EMAKI'.length));
  else variants.push(`EMAKI${normalized}`);
  return [...new Set(variants)];
}

function moduleIdMatches(a: string | undefined, b: string | undefined): boolean {
  const left = normalizeModuleVariants(a);
  const right = new Set(normalizeModuleVariants(b));
  return left.some(value => right.has(value));
}

function normalizePreviewPath(value: string | undefined): string {
  return String(value ?? '').trim().replace(/\\/g, '/').replace(/^\/+/, '').toLowerCase();
}

function configPreviewScore(reg: ConfigPreviewRegistration): number {
  return (reg.priority ?? 0)
    + (reg.moduleId ? 100 : 0)
    + (reg.kind ? 20 : 0)
    + (reg.pathPattern ? 14 : 0)
    + (reg.pathPrefix ? 10 + reg.pathPrefix.length / 1000 : 0);
}

function globPatternRegex(pattern: string): RegExp {
  const source = String(pattern ?? '').replace(/\\/g, '/').replace(/^\/+/, '');
  let regex = '';
  for (let index = 0; index < source.length; index++) {
    const char = source[index];
    if (char === '*') {
      if (source[index + 1] === '*') {
        if (source[index + 2] === '/') {
          regex += '(?:.*/)?';
          index += 2;
        } else {
          regex += '.*';
          index += 1;
        }
      } else {
        regex += '[^/]*';
      }
    } else if (char === '?') {
      regex += '[^/]';
    } else {
      regex += char.replace(/[.+^${}()|[\]\\]/g, '\\$&');
    }
  }
  return new RegExp(`^${regex}$`);
}

function previewPathPatternMatches(pattern: string, path: string): boolean {
  const regex = globPatternRegex(pattern);
  if (regex.test(path)) return true;
  const prefixed = path.startsWith('/') ? path : `/${path}`;
  return regex.test(prefixed);
}

/**
 * Register a surface. Later registrations with higher priority override earlier ones.
 *
 * @deprecated New plugin extensions should declare `surfaces` through
 * `defineEmakiPluginWebModule(...)`. This low-level registry function remains
 * for CoreLib built-ins, the Manifest v2 loader bridge, and pre-v2 extension
 * compatibility only.
 */
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
    const byEditor = _registry.find(r => String(r.editorId ?? '') === editorId && (!r.moduleId || !moduleId || moduleIdMatches(r.moduleId, moduleId)));
    if (byEditor) return byEditor;
  }
  if (moduleId) {
    const byModuleKind = _registry.find(r => normalize(r.kind) === kind && moduleIdMatches(r.moduleId, moduleId));
    if (byModuleKind) return byModuleKind;
  }
  return _registry.find(r => normalize(r.kind) === kind && !r.moduleId && !r.editorId);
}

/** Get all registered surfaces. */
export function getAllSurfaces(): SurfaceRegistration[] {
  return [..._registry];
}

/**
 * @deprecated New plugin extensions should declare `previews` through
 * `defineEmakiPluginWebModule(...)`. This remains for the Manifest v2 loader
 * bridge and pre-v2 extension compatibility only.
 */
export function registerConfigPreview(reg: ConfigPreviewRegistration): void {
  if (!reg?.component) return;
  const next: ConfigPreviewRegistration = {
    ...reg,
    kind: reg.kind ? normalize(reg.kind) : undefined,
    moduleId: reg.moduleId ? normalize(reg.moduleId) : undefined,
    pathPrefix: normalizePreviewPath(reg.pathPrefix),
    pathPattern: normalizePreviewPath(reg.pathPattern)
  };
  const duplicate = _configPreviews.findIndex(existing =>
    normalize(existing.kind) === normalize(next.kind)
    && normalize(existing.moduleId) === normalize(next.moduleId)
    && normalizePreviewPath(existing.pathPrefix) === next.pathPrefix
    && normalizePreviewPath(existing.pathPattern) === next.pathPattern
  );
  if (duplicate >= 0) _configPreviews.splice(duplicate, 1);
  _configPreviews.push(next);
  _configPreviews.sort((a, b) => configPreviewScore(b) - configPreviewScore(a));
}

export function getConfigPreview(context: { moduleId?: string; kind?: string; path?: string }): ConfigPreviewRegistration | undefined {
  const moduleId = normalize(context.moduleId);
  const kind = normalize(context.kind);
  const path = normalizePreviewPath(context.path);
  return _configPreviews.find(reg => {
    if (reg.moduleId && !moduleIdMatches(reg.moduleId, moduleId)) return false;
    if (reg.kind && normalize(reg.kind) !== kind) return false;
    if (reg.pathPrefix && !path.startsWith(reg.pathPrefix)) return false;
    if (reg.pathPattern && !previewPathPatternMatches(reg.pathPattern, path)) return false;
    return true;
  });
}

export function getAllConfigPreviews(): ConfigPreviewRegistration[] {
  return [..._configPreviews];
}

function insightDefinitionScore(reg: InsightDefinitionRegistration): number {
  return (reg.priority ?? 0)
    + (reg.moduleId ? 100 : 0)
    + (reg.pathPattern ? 14 : 0)
    + (reg.pathPrefix ? 10 + reg.pathPrefix.length / 1000 : 0);
}

/**
 * @deprecated New plugin extensions should declare `insightDefinitions` through
 * `defineEmakiPluginWebModule(...)`. This remains for the Manifest v2 loader
 * bridge and pre-v2 extension compatibility only.
 */
export function registerInsightDefinition(reg: InsightDefinitionRegistration): void {
  const idType = String(reg?.idType ?? '').trim();
  if (!idType) return;
  const next: InsightDefinitionRegistration = {
    ...reg,
    idType,
    moduleId: reg.moduleId ? normalize(reg.moduleId) : undefined,
    pathPrefix: normalizePreviewPath(reg.pathPrefix),
    pathPattern: normalizePreviewPath(reg.pathPattern),
    idPath: String(reg.idPath ?? 'id').trim() || 'id',
    fallbackId: reg.fallbackId ?? 'basename'
  };
  const duplicate = _insightDefinitions.findIndex(existing =>
    normalize(existing.moduleId) === normalize(next.moduleId)
    && normalizePreviewPath(existing.pathPrefix) === next.pathPrefix
    && normalizePreviewPath(existing.pathPattern) === next.pathPattern
  );
  if (duplicate >= 0) _insightDefinitions.splice(duplicate, 1);
  _insightDefinitions.push(next);
  _insightDefinitions.sort((a, b) => insightDefinitionScore(b) - insightDefinitionScore(a));
}

export function getInsightDefinition(context: InsightDefinitionContext): InsightDefinitionRegistration | undefined {
  const moduleId = normalize(context.moduleId);
  const path = normalizePreviewPath(context.path);
  const match = _insightDefinitions.find(reg => {
    if (reg.moduleId && !moduleIdMatches(reg.moduleId, moduleId)) return false;
    if (reg.pathPrefix && !path.startsWith(reg.pathPrefix)) return false;
    if (reg.pathPattern && !previewPathPatternMatches(reg.pathPattern, path)) return false;
    return true;
  });
  return match ? { ...match } : undefined;
}

export function recordExtensionStatus(status: WebConsoleExtensionStatus): void {
  const index = _extensionStatuses.findIndex(entry => entry.id === status.id && entry.moduleId === status.moduleId && entry.url === status.url);
  if (index >= 0) _extensionStatuses.splice(index, 1, status);
  else _extensionStatuses.push(status);
}

export function getExtensionStatuses(): WebConsoleExtensionStatus[] {
  return [..._extensionStatuses];
}

export function setServerJavaScriptCompletions(entries: WebScriptCompletionEntry[] | undefined): void {
  _serverJavaScriptCompletions.splice(0, _serverJavaScriptCompletions.length, ...normalizeJavaScriptCompletions(entries ?? []));
}

export function registerJavaScriptMethod(entry: WebScriptCompletionEntry): void {
  const [normalized] = normalizeJavaScriptCompletions([entry]);
  if (!normalized) return;
  const duplicate = _extensionJavaScriptCompletions.findIndex(existing => javaScriptCompletionKey(existing) === javaScriptCompletionKey(normalized));
  if (duplicate >= 0) _extensionJavaScriptCompletions.splice(duplicate, 1, normalized);
  else _extensionJavaScriptCompletions.push(normalized);
}

export function registerJavaScriptMethods(entries: WebScriptCompletionEntry[]): void {
  if (!Array.isArray(entries)) return;
  entries.forEach(registerJavaScriptMethod);
}

export function getJavaScriptCompletionScopes(): Record<string, WebScriptCompletionEntry[]> {
  const scopes: Record<string, WebScriptCompletionEntry[]> = {};
  for (const entry of [..._serverJavaScriptCompletions, ..._extensionJavaScriptCompletions]) {
    const bucket = scopes[entry.scope] ?? (scopes[entry.scope] = []);
    const duplicate = bucket.findIndex(existing => existing.label === entry.label);
    if (duplicate >= 0) bucket.splice(duplicate, 1, entry);
    else bucket.push(entry);
  }
  return Object.fromEntries(Object.entries(scopes).map(([scope, entries]) => [scope, entries.map(copyJavaScriptCompletionEntry)]));
}

function normalizeJavaScriptCompletions(entries: WebScriptCompletionEntry[]): WebScriptCompletionEntry[] {
  return entries.flatMap(entry => {
    const scope = normalizeJavaScriptCompletionScope(entry?.scope);
    const label = String(entry?.label ?? '').trim();
    if (!scope || !label) return [];
    return [{
      moduleId: entry.moduleId ? String(entry.moduleId) : undefined,
      scope,
      label,
      detail: entry.detail ? String(entry.detail) : undefined,
      apply: entry.apply ? String(entry.apply) : label,
      type: entry.type ? String(entry.type) : 'function'
    }];
  });
}

function normalizeJavaScriptCompletionScope(scope: string | undefined): string {
  const text = String(scope ?? '').trim();
  return text.startsWith('module:') ? `module:${text.slice('module:'.length).trim().toLowerCase()}` : text;
}

function javaScriptCompletionKey(entry: WebScriptCompletionEntry): string {
  return `${entry.moduleId ?? ''}:${entry.scope}:${entry.label}`;
}

function copyJavaScriptCompletionEntry(entry: WebScriptCompletionEntry): WebScriptCompletionEntry {
  return { ...entry };
}

export function setRuntimeEnums(enums: Record<string, string[]> | undefined): void {
  _runtimeEnums = { ...(enums ?? {}) };
}

export function getRuntimeEnum(id: string): string[] {
  return [...(_runtimeEnums[String(id ?? '')] ?? [])];
}

/**
 * @deprecated New plugin extensions should declare `fileKindLabels` through
 * `defineEmakiPluginWebModule(...)`. This remains for the Manifest v2 loader
 * bridge and pre-v2 extension compatibility only.
 */
export function registerFileKindLabel(kind: string, label: string): void {
  const normalized = normalize(kind);
  if (!normalized || !label) return;
  _fileKindLabels[normalized] = label;
}

export function getFileKindLabel(kind: string | undefined): string | undefined {
  const normalized = normalize(kind);
  return normalized ? _fileKindLabels[normalized] : undefined;
}

export function registerConfigNodeMeta(moduleId: string, path: string, meta: ConfigNodeMetaOverride): void {
  if (!moduleId || !path || !meta) return;
  const normalizedModuleId = normalizeConfigModuleId(moduleId);
  const normalizedPath = String(path ?? '');
  const key = configOverrideKey(normalizedModuleId, normalizedPath);
  _configNodeMeta[key] = mergeConfigNodeMeta(_configNodeMeta[key], meta);
  const order = _configNodeMetaOrder[normalizedModuleId] ?? (_configNodeMetaOrder[normalizedModuleId] = []);
  if (!order.includes(normalizedPath)) order.push(normalizedPath);
}

export function registerConfigNodeRule(moduleId: string, matcher: ConfigNodeRuleMatcher, meta: ConfigNodeMetaOverride): void {
  if (!moduleId || !matcher || !meta) return;
  _configNodeRules.push({ moduleId: normalizeConfigModuleId(moduleId), matcher, meta });
}

/**
 * @deprecated New plugin extensions should model config metadata through
 * Manifest v2 `config.metaFields` / `schemas`. This remains for the Manifest
 * v2 loader bridge and pre-v2 extension compatibility only.
 */
export function registerConfigMetaFields(moduleId: string, fields: ConfigMetaFieldEntry[]): void {
  if (!moduleId || !Array.isArray(fields)) return;
  fields.forEach(([path, label, comment, type, extra]) => registerConfigNodeMeta(moduleId, path, { label, comment, type, ...(extra ?? {}) }));
}

export function registerConfigFileSchema(moduleId: string, schema: ConfigFileSchemaEntry): void {
  if (!moduleId || !schema || !Array.isArray(schema.fields)) return;
  _configFileSchemas.push({
    moduleId: normalizeConfigModuleId(moduleId),
    pathPrefix: schema.pathPrefix,
    pathPattern: schema.pathPattern,
    fields: schema.fields
  });
}

export function registerConfigFileSchemas(moduleId: string, schemas: ConfigFileSchemaEntry[]): void {
  if (!moduleId || !Array.isArray(schemas)) return;
  schemas.forEach(schema => registerConfigFileSchema(moduleId, schema));
}

/**
 * @deprecated New plugin extensions should model rule-based config metadata
 * through Manifest v2 `config.ruleFields` / `config.rules`. This remains for
 * the Manifest v2 loader bridge and pre-v2 extension compatibility only.
 */
export function registerConfigRuleFields(moduleId: string, fields: Record<string, ConfigRuleFieldEntry>): void {
  if (!moduleId || !fields) return;
  Object.entries(fields).forEach(([key, [label, comment, type, extra]]) => registerConfigNodeRule(moduleId, { key }, { label, comment, type, ...(extra ?? {}) }));
}

export function registerConfigCreateTemplate(moduleId: string, nodePath: string, template: WebConfigCreateTemplate): void {
  if (!moduleId || !nodePath || !template?.id) return;
  const key = configOverrideKey(moduleId, nodePath);
  const next = { ...template, fields: [...(template.fields ?? [])] };
  const existing = _configCreateTemplates[key] ?? [];
  const duplicate = existing.findIndex(entry => entry.id === next.id);
  if (duplicate >= 0) existing.splice(duplicate, 1, next);
  else existing.push(next);
  _configCreateTemplates[key] = existing;
}

export function registerConfigCreateTemplates(moduleId: string, entries: ConfigCreateTemplateEntry[]): void {
  if (!moduleId || !Array.isArray(entries)) return;
  entries.forEach(([nodePath, template]) => registerConfigCreateTemplate(moduleId, nodePath, template));
}

export function registerConfigListItemSchema(moduleId: string, listPath: string, fields: WebConfigFieldSchema[], options: { uniqueBy?: string } = {}): void {
  if (!moduleId || !listPath || !Array.isArray(fields)) return;
  const key = configOverrideKey(moduleId, listPath);
  _configListItemFields[key] = fields.filter(field => field?.path).map(field => ({ ...field, options: field.options ? [...field.options] : undefined }));
  if (options.uniqueBy) registerUniqueListField(moduleId, listPath, options.uniqueBy);
}

export function registerConfigListItemSchemas(moduleId: string, entries: ConfigListItemSchemaEntry[]): void {
  if (!moduleId || !Array.isArray(entries)) return;
  entries.forEach(([listPath, fields, options]) => registerConfigListItemSchema(moduleId, listPath, fields, options));
}

export function registerConfigListItemSchemaRule(moduleId: string, matcher: ConfigListItemSchemaRuleMatcher, fields: WebConfigFieldSchema[], options: { uniqueBy?: string } = {}): void {
  if (!moduleId || !matcher || !Array.isArray(fields)) return;
  _configListItemSchemaRules.push({ moduleId: normalizeConfigModuleId(moduleId), matcher, fields: fields.filter(field => field?.path).map(field => ({ ...field, options: field.options ? [...field.options] : undefined })), uniqueBy: options.uniqueBy });
}

export function registerConfigListItemSchemaRules(moduleId: string, entries: ConfigListItemSchemaRuleEntry[]): void {
  if (!moduleId || !Array.isArray(entries)) return;
  entries.forEach(([matcher, fields, options]) => registerConfigListItemSchemaRule(moduleId, matcher, fields, options));
}

/**
 * @deprecated Prefer `defineEmakiPluginWebModule({ config, schemas })` and
 * `registerEmakiPluginWebModule(...)`. This bridge remains for CoreLib's
 * Manifest v2 loader and pre-v2 extension compatibility only.
 */
export function registerPluginConfig(registration: PluginConfigRegistration): void {
  if (!registration?.moduleId) return;
  const { moduleId } = registration;
  webManifest.recordLegacyPluginConfigManifest(moduleId, {
    metaFields: registration.metaFields ?? [],
    fileSchemas: registration.fileSchemas ?? [],
    ruleFields: registration.ruleFields ?? {},
    rules: registration.rules ?? [],
    createTemplates: registration.createTemplates ?? [],
    listItemSchemas: registration.listItemSchemas ?? [],
    listItemSchemaRules: registration.listItemSchemaRules ?? []
  });
  registerConfigMetaFields(moduleId, registration.metaFields ?? []);
  registerConfigFileSchemas(moduleId, registration.fileSchemas ?? []);
  registerConfigRuleFields(moduleId, registration.ruleFields ?? {});
  (registration.rules ?? []).forEach(([matcher, meta]) => registerConfigNodeRule(moduleId, matcher, meta));
  registerConfigCreateTemplates(moduleId, registration.createTemplates ?? []);
  registerConfigListItemSchemas(moduleId, registration.listItemSchemas ?? []);
  registerConfigListItemSchemaRules(moduleId, registration.listItemSchemaRules ?? []);
}

export function registerUniqueListField(moduleId: string, listPath: string, fieldPath: string): void {
  if (!moduleId || !listPath || !fieldPath) return;
  _configUniqueListFields[configOverrideKey(moduleId, listPath)] = fieldPath;
}

export function applyConfigRegistryOverrides(registry: WebRegistry): WebRegistry {
  return {
    ...registry,
    modules: registry.modules.map(module => ({
      ...module,
      files: module.files.map(file => ({
        ...file,
        nodes: applyConfigNodeOverrides(module.id, file.nodes ?? [], file.path)
      }))
    }))
  };
}

export function applyConfigNodeOverrides(moduleId: string, nodes: WebConfigNode[], filePath?: string): WebConfigNode[] {
  const schemaFields = dedupeConfigSchemaFields(configSchemaFieldsForFile(moduleId, filePath));
  const virtualNodes = dedupeVirtualNodes(schemaFields.flatMap(field => createVirtualConfigNodesFromField(moduleId, field)));
  const schemaNodesByPath = new Map(virtualNodes.map(node => [node.path, node]));
  const existing = nodes.map(node => {
    const schemaNode = schemaNodesByPath.get(node.path);
    return applySingleConfigNodeOverride(moduleId, schemaNode ? mergeConfigSchemaNodeMetadata(node, schemaNode) : node);
  });
  let merged = existing;
  if (virtualNodes.length) {
    const schemaPaths = virtualNodes.map(node => node.path);
    const existingPaths = new Set(merged.map(node => node.path));
    const missing = virtualNodes.filter(node => !existingPaths.has(node.path));
    if (missing.length) merged = mergeMissingConfigNodes(merged, missing, schemaPaths);
  }
  return mergeSchemaObjectChildNodes(moduleId, merged);
}

function mergeConfigSchemaNodeMetadata(node: WebConfigNode, schemaNode: WebConfigNode): WebConfigNode {
  const next: WebConfigNode = {
    ...node,
    label: schemaNode.label,
    comment: schemaNode.comment,
    type: resolveConfigNodeType(node.type, schemaNode.type)
  };
  if (schemaNode.options !== undefined) next.options = [...schemaNode.options];
  if (schemaNode.optionLabelPrefix !== undefined) next.optionLabelPrefix = schemaNode.optionLabelPrefix;
  if (schemaNode.creatableChildren !== undefined) next.creatableChildren = schemaNode.creatableChildren;
  if (schemaNode.createTemplates !== undefined) next.createTemplates = schemaNode.createTemplates.map(copyCreateTemplate);
  if (schemaNode.itemFields !== undefined) next.itemFields = schemaNode.itemFields.map(copyFieldSchema);
  if (schemaNode.uniqueBy !== undefined) next.uniqueBy = schemaNode.uniqueBy;
  return next;
}

function mergeSchemaObjectChildNodes(moduleId: string, nodes: WebConfigNode[]): WebConfigNode[] {
  const virtualNodes = dedupeVirtualNodes(nodes.flatMap(node => createVirtualConfigNodesFromObjectItemFields(moduleId, node)));
  if (!virtualNodes.length) return nodes;
  const schemaPaths = virtualNodes.map(node => node.path);
  const existingPaths = new Set(nodes.map(node => node.path));
  const missing = virtualNodes.filter(node => !existingPaths.has(node.path));
  return missing.length ? mergeMissingConfigNodes(nodes, missing, schemaPaths) : nodes;
}

function createVirtualConfigNodesFromObjectItemFields(moduleId: string, node: WebConfigNode): WebConfigNode[] {
  if (node.type !== 'object' || !node.itemFields?.length) return [];
  const nodes = node.itemFields.flatMap(field => {
    if (!field?.path) return [];
    const childPath = `${node.path}.${field.path}`;
    return createVirtualConfigNodesFromField(moduleId, [
      childPath,
      field.label ?? lastConfigPathKey(field.path).replace(/[_-]+/g, ' '),
      field.comment ?? '',
      String(field.type ?? 'text'),
      field
    ]);
  });
  return dedupeVirtualNodes(nodes);
}

export function standardGuiFields(entries: StandardGuiFieldEntry[] = []): Record<string, WebEditorField> {
  const base: StandardGuiFieldEntry[] = [
    ['id', 'ID', 'GUI 模板唯一标识。', 'text'],
    ['gui_type', 'GUI 类型', 'Bukkit InventoryType。只有 CHEST 支持行数。', 'enum'],
    ['title', '标题', 'GUI 窗口标题，支持 MiniMessage。', 'text'],
    ['rows', '箱子行数', '仅 CHEST 类型可用，范围 1-6。', 'number'],
    ['type', '槽位类型', '插件业务识别的槽位语义。', 'text'],
    ['slots', '槽位', '槽位索引列表或槽位配置。', 'list'],
    ['item.source', '物品来源', '槽位基础物品来源，支持原版材料或 ItemSource。', 'text'],
    ['item.amount', '数量', '槽位基础物品堆叠数量。', 'number'],
    ['item.components', '物品组件', 'namespaced component id 到任意 YAML/JSON 等价值的映射。', 'itemComponents', { reservedComponentIds: ['minecraft:custom_name', 'minecraft:item_name', 'minecraft:lore'] }],
    ['item.components.minecraft:custom_name', '显示名', '槽位物品显示名称，支持 MiniMessage。', 'text'],
    ['item.components.minecraft:item_name', '原版名称', '影响堆叠判断的原版 item_name 组件。', 'text'],
    ['item.components.minecraft:lore', 'Lore', '槽位物品描述，每行一条。', 'stringList']
  ];
  return Object.fromEntries([...base, ...entries].map(([path, label, comment, type, extra]) => [path, { ...(extra ?? {}), path, label, comment, type, options: extra?.options ? [...extra.options] : undefined }]));
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
    const byModuleKind = _sourceAdapters.find(r => normalize(r.kind) === kind && moduleIdMatches(r.moduleId, moduleId));
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
  const host: EmakiWebConsoleHost = { ...lib, ...components, ...previewKit, ...i18n, ...itemFieldRegistry, ...effectTypeRegistry, ...economyConfig, ...pluginKit, ...webManifest, apiVersion: EMAKI_WEB_CONSOLE_API_VERSION, React, ItemEditorSurface, registerSurface, getSurface, getAllSurfaces, isKind, registerPluginGuiSurface, registerPluginGuiEditor, registerPluginSurfaces, registerConfigPreview, getConfigPreview, getAllConfigPreviews, registerInsightDefinition, getInsightDefinition, standardGuiFields, registerEditorDescriptor, registerEditorField, registerSourceDocumentAdapter, getSourceDocumentAdapter, registerGuiEditorDescriptor, registerGuiEditorField, getRuntimeEnum, registerFileKindLabel, getFileKindLabel, registerConfigNodeMeta, registerConfigNodeRule, registerConfigCreateTemplate, registerConfigMetaFields, registerConfigFileSchema, registerConfigFileSchemas, registerConfigRuleFields, registerConfigCreateTemplates, registerConfigListItemSchemas, registerConfigListItemSchemaRules, registerConfigListItemSchema, registerConfigListItemSchemaRule, registerPluginConfig, registerUniqueListField, recordExtensionStatus, getExtensionStatuses, registerJavaScriptMethod, registerJavaScriptMethods, getJavaScriptCompletionScopes, components, previewKit, lib, i18n, t: i18n.t, registerLocale: i18n.registerLocale, registerModuleLocale: i18n.registerModuleLocale };
  (window as any).React = React;
  window.EmakiWebConsole = host;
  return host;
}

/**
 * 一行注册一个直接复用 GuiEditorSurface 的 GUI surface。
 * 适用于只需要通用 GUI 编辑器的扩展。
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

function configSchemaFieldsForFile(moduleId: string, filePath: string | undefined): ConfigMetaFieldEntry[] {
  const normalizedModuleId = normalizeConfigModuleId(moduleId);
  const normalizedPath = normalizeConfigFilePath(filePath);
  if (!normalizedPath || normalizedPath === 'config.yml' || normalizedPath === 'config.yaml') {
    return configNodeMetaOrder(moduleId)
      .map(path => metaFieldFromRegisteredNode(moduleId, path))
      .filter((field): field is ConfigMetaFieldEntry => Boolean(field));
  }
  const matched = _configFileSchemas
    .filter(schema => schema.moduleId === normalizedModuleId && configFileSchemaMatches(schema, normalizedPath))
    .map(schema => ({ schema, score: configFileSchemaScore(schema) }));
  if (!matched.length) return [];
  const bestScore = Math.max(...matched.map(entry => entry.score));
  return matched.filter(entry => entry.score === bestScore).flatMap(entry => entry.schema.fields);
}

function configFileSchemaMatches(schema: ConfigFileSchemaRegistration, filePath: string): boolean {
  if (schema.pathPrefix && filePath.startsWith(normalizeConfigFilePath(schema.pathPrefix))) return true;
  if (schema.pathPattern) return globPatternRegex(normalizeConfigFilePath(schema.pathPattern)).test(filePath);
  return false;
}

function configFileSchemaScore(schema: ConfigFileSchemaRegistration): number {
  const prefix = normalizeConfigFilePath(schema.pathPrefix);
  const pattern = normalizeConfigFilePath(schema.pathPattern);
  if (pattern) return 20000 + pattern.replace(/[*?]/g, '').length;
  if (prefix) return 10000 + prefix.length;
  return 0;
}

function normalizeConfigFilePath(filePath: string | undefined): string {
  return String(filePath ?? '').trim().replace(/\\/g, '/').replace(/^\/+/, '').toLowerCase();
}

function configNodeMetaOrder(moduleId: string): string[] {
  return [...(_configNodeMetaOrder[normalizeConfigModuleId(moduleId)] ?? [])];
}

function metaFieldFromRegisteredNode(moduleId: string, path: string): ConfigMetaFieldEntry | null {
  const meta = _configNodeMeta[configOverrideKey(moduleId, path)];
  if (!meta) return null;
  return [path, meta.label ?? lastConfigPathKey(path).replace(/[_-]+/g, ' '), meta.comment ?? '', meta.type ?? 'text', meta];
}

function dedupeConfigSchemaFields(fields: ConfigMetaFieldEntry[]): ConfigMetaFieldEntry[] {
  const byPath = new Map<string, ConfigMetaFieldEntry>();
  for (const field of fields) {
    const path = field?.[0];
    if (!path || byPath.has(path)) continue;
    byPath.set(path, field);
  }
  return [...byPath.values()];
}

function createVirtualConfigNodesFromField(moduleId: string, field: ConfigMetaFieldEntry): WebConfigNode[] {
  const [path, label, comment, type = 'text', extra] = field;
  if (!path) return [];
  const nodes: WebConfigNode[] = [];
  const ensureNode = (node: WebConfigNode) => {
    if (!nodes.some(entry => entry.path === node.path)) nodes.push(applySingleConfigNodeOverride(moduleId, node));
  };
  const parts = path.split('.').filter(Boolean);
  for (let index = 1; index < parts.length; index++) {
    const parentPath = parts.slice(0, index).join('.');
    ensureNode({
      path: parentPath,
      label: lastConfigPathKey(parentPath).replace(/[_-]+/g, ' '),
      comment: '',
      type: 'object',
      editable: true,
      value: {}
    });
  }
  ensureNode(virtualConfigNode(moduleId, path, label, comment, type, extra));
  if (extra?.itemFields?.length && virtualConfigNodeType(type) === 'object') {
    for (const child of extra.itemFields) {
      if (!child?.path) continue;
      const childPath = `${path}.${child.path}`;
      nodes.push(...createVirtualConfigNodesFromField(moduleId, [childPath, child.label ?? lastConfigPathKey(child.path).replace(/[_-]+/g, ' '), child.comment ?? '', String(child.type ?? 'text'), child]));
    }
  }
  return dedupeVirtualNodes(nodes);
}

function dedupeVirtualNodes(nodes: WebConfigNode[]): WebConfigNode[] {
  const byPath = new Map<string, WebConfigNode>();
  for (const node of nodes) if (!byPath.has(node.path)) byPath.set(node.path, node);
  return [...byPath.values()];
}

function virtualConfigNode(moduleId: string, path: string, label: string, comment: string, type: string, extra?: ConfigNodeMetaOverride | WebConfigFieldSchema): WebConfigNode {
  return applySingleConfigNodeOverride(moduleId, {
    path,
    label,
    comment,
    type: virtualConfigNodeType(type),
    editable: true,
    value: emptyConfigValueForType(type),
    options: extra?.options,
    optionLabelPrefix: extra?.optionLabelPrefix,
    creatableChildren: (extra as ConfigNodeMetaOverride)?.creatableChildren,
    createTemplates: (extra as ConfigNodeMetaOverride)?.createTemplates,
    itemFields: extra?.itemFields,
    uniqueBy: extra?.uniqueBy
  });
}

function mergeMissingConfigNodes(existing: WebConfigNode[], missing: WebConfigNode[], order: string[]): WebConfigNode[] {
  const result = [...existing];
  const resultPaths = new Set(result.map(node => node.path));
  const pathIndex = () => new Map(result.map((node, index) => [node.path, index]));
  for (const node of missing) {
    if (!node.path || resultPaths.has(node.path)) continue;
    const orderIndex = order.indexOf(node.path);
    const indexes = pathIndex();
    let insertAt = result.length;
    for (let index = orderIndex - 1; index >= 0; index--) {
      const anchor = indexes.get(order[index]);
      if (anchor !== undefined) {
        insertAt = anchor + 1;
        break;
      }
    }
    if (insertAt === result.length) {
      for (let index = orderIndex + 1; index < order.length; index++) {
        const anchor = indexes.get(order[index]);
        if (anchor !== undefined) {
          insertAt = anchor;
          break;
        }
      }
    }
    result.splice(insertAt, 0, node);
    resultPaths.add(node.path);
  }
  return result;
}

function virtualConfigNodeType(type: string): string {
  if (type.startsWith('enum:') || type.startsWith('dynamic_enum:')) return 'enum';
  return type || 'text';
}

function emptyConfigValueForType(type: string): unknown {
  const normalized = virtualConfigNodeType(type);
  if (normalized === 'number') return undefined;
  if (normalized === 'boolean') return false;
  if (normalized === 'list' || normalized === 'stringList' || normalized === 'numberList' || normalized === 'objectList' || normalized === 'actions' || normalized === 'effects') return [];
  if (normalized === 'object' || normalized === 'dynamic_map' || normalized === 'map' || normalized === 'variablesMap' || normalized === 'json') return {};
  return '';
}

function applySingleConfigNodeOverride(moduleId: string, node: WebConfigNode): WebConfigNode {
  const exact = _configNodeMeta[configOverrideKey(moduleId, node.path)];
  const ruleMeta = _configNodeRules
    .filter(rule => rule.moduleId === normalizeConfigModuleId(moduleId) && configRuleMatches(rule.matcher, node.path, node))
    .reduce<ConfigNodeMetaOverride>((merged, rule) => mergeConfigNodeMeta(merged, rule.meta), {});
  const key = configOverrideKey(moduleId, node.path);
  const templates = _configCreateTemplates[key];
  const itemFields = _configListItemFields[key];
  const uniqueBy = _configUniqueListFields[key];
  const listRule = _configListItemSchemaRules
    .filter(rule => rule.moduleId === normalizeConfigModuleId(moduleId) && configRuleMatches(rule.matcher, node.path, node))
    .reduce<{ fields?: WebConfigFieldSchema[]; uniqueBy?: string }>((merged, rule) => ({
      fields: rule.fields,
      uniqueBy: rule.uniqueBy ?? merged.uniqueBy
    }), {});
  const meta = mergeConfigNodeMeta(ruleMeta, exact);
  const next: WebConfigNode = { ...node };
  if (meta.label !== undefined) next.label = meta.label;
  if (meta.comment !== undefined) next.comment = meta.comment;
  if (meta.type !== undefined) next.type = resolveConfigNodeType(node.type, meta.type);
  if (meta.options !== undefined) next.options = [...meta.options];
  if (meta.optionLabelPrefix !== undefined) next.optionLabelPrefix = meta.optionLabelPrefix;
  if (meta.creatableChildren !== undefined) next.creatableChildren = meta.creatableChildren;
  if (meta.createTemplates !== undefined) next.createTemplates = meta.createTemplates.map(copyCreateTemplate);
  if (meta.itemFields !== undefined) next.itemFields = meta.itemFields.map(copyFieldSchema);
  if (meta.uniqueBy !== undefined) next.uniqueBy = meta.uniqueBy;
  if (templates !== undefined) next.createTemplates = templates.map(copyCreateTemplate);
  if (listRule.fields !== undefined && meta.itemFields === undefined) next.itemFields = listRule.fields.map(copyFieldSchema);
  if (itemFields !== undefined) next.itemFields = itemFields.map(copyFieldSchema);
  if (listRule.uniqueBy !== undefined && meta.uniqueBy === undefined) next.uniqueBy = listRule.uniqueBy;
  if (uniqueBy !== undefined) next.uniqueBy = uniqueBy;
  return next;
}

function resolveConfigNodeType(detectedType: string | undefined, metaType: string): string {
  if (detectedType === 'list') return isListUiType(metaType) ? metaType : detectedType;
  if (detectedType === 'boolean') return 'boolean';
  if (detectedType === 'number') return 'number';
  if (detectedType === 'object') return metaType === 'dynamic_map' || metaType === 'map' || metaType === 'json' ? metaType : 'object';
  return metaType;
}

function isListUiType(type: string): boolean {
  return type === 'list' || type === 'stringList' || type === 'numberList' || type === 'objectList' || type === 'actions' || type === 'effects';
}

function mergeConfigNodeMeta(base: ConfigNodeMetaOverride | undefined, override: ConfigNodeMetaOverride | undefined): ConfigNodeMetaOverride {
  if (!base && !override) return {};
  return {
    ...(base ?? {}),
    ...(override ?? {}),
    options: override?.options ? [...override.options] : base?.options ? [...base.options] : undefined,
    createTemplates: override?.createTemplates ? override.createTemplates.map(copyCreateTemplate) : base?.createTemplates?.map(copyCreateTemplate),
    itemFields: override?.itemFields ? override.itemFields.map(copyFieldSchema) : base?.itemFields?.map(copyFieldSchema)
  };
}

function copyCreateTemplate(template: WebConfigCreateTemplate): WebConfigCreateTemplate {
  return { ...template, fields: (template.fields ?? []).map(copyFieldSchema) };
}

function copyFieldSchema(field: WebConfigFieldSchema): WebConfigFieldSchema {
  return {
    ...field,
    options: field.options ? [...field.options] : undefined,
    itemFields: field.itemFields ? field.itemFields.map(copyFieldSchema) : undefined,
    createTemplates: field.createTemplates ? field.createTemplates.map(copyCreateTemplate) : undefined
  };
}

function configRuleMatches(matcher: ConfigNodeRuleMatcher, path: string, node: WebConfigNode): boolean {
  if (typeof matcher === 'function') return matcher(path, node);
  if (matcher.path && matcher.path !== path) return false;
  if (matcher.key && lastConfigPathKey(path) !== matcher.key) return false;
  if (matcher.prefix && !path.startsWith(matcher.prefix)) return false;
  if (matcher.suffix && !path.endsWith(matcher.suffix)) return false;
  if (matcher.contains && !path.includes(matcher.contains)) return false;
  return Boolean(matcher.path || matcher.key || matcher.prefix || matcher.suffix || matcher.contains);
}

function configOverrideKey(moduleId: string, path: string): string {
  return `${normalizeConfigModuleId(moduleId)}:${String(path ?? '')}`;
}

function normalizeConfigModuleId(moduleId: string): string {
  const normalized = String(moduleId ?? '').trim().toLowerCase();
  return normalized.startsWith('emaki') && normalized.length > 'emaki'.length ? normalized.slice('emaki'.length) : normalized;
}

function lastConfigPathKey(path: string): string {
  const text = String(path ?? '');
  return text.includes('.') ? text.slice(text.lastIndexOf('.') + 1) : text;
}

/**
 * 批量注册多个 surface。
 */
export function registerPluginSurfaces(surfaces: SurfaceRegistration[]): void {
  surfaces.forEach(registerSurface);
}
