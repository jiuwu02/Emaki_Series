import type { ComponentType } from 'react';
import type { LocaleMessages } from '../../i18n';
import type { EffectTypeDefinition } from '../../effectTypeRegistry';
import type { ItemFieldRenderer, ItemPreviewFallback } from '../../itemFieldRegistry';
import type {
  ConfigCreateTemplateEntry,
  ConfigFileSchemaEntry,
  ConfigListItemSchemaEntry,
  ConfigListItemSchemaRuleEntry,
  ConfigMetaFieldEntry,
  ConfigNodeRuleMatcher,
  ConfigNodeMetaOverride,
  ConfigPreviewRegistration,
  InsightDefinitionRegistration,
  PluginGuiEditorRegistration,
  SourceDocumentAdapter,
  SurfaceRegistration
} from '../../registry';
import type { WebConfigFieldSchema, WebEditorDescriptor, WebEditorField } from '../../types';

export type WebManifestV2Version = '2';

export type WebManifestCapability =
  | 'config'
  | 'gui'
  | 'item'
  | 'script'
  | 'preview'
  | 'insight'
  | 'pluginApi'
  | 'diagnostics'
  | string;

export type WebManifestModule = {
  id: string;
  displayName?: string;
  summaryKey?: string;
  icon?: string;
  tone?: string;
};

export type WebManifestFile = {
  id: string;
  path: string;
  kind: string;
  titleKey?: string;
  commentKey?: string;
  editorId?: string;
};

export type WebManifestConfigSchema = ConfigFileSchemaEntry & {
  id?: string;
  description?: string;
};

export type WebManifestPluginConfig = {
  metaFields?: ConfigMetaFieldEntry[];
  fileSchemas?: WebManifestConfigSchema[];
  ruleFields?: Record<string, [label: string, comment: string, type?: string, extra?: ConfigNodeMetaOverride]>;
  rules?: Array<[matcher: ConfigNodeRuleMatcher, meta: ConfigNodeMetaOverride]>;
  createTemplates?: ConfigCreateTemplateEntry[];
  listItemSchemas?: ConfigListItemSchemaEntry[];
  listItemSchemaRules?: ConfigListItemSchemaRuleEntry[];
};

export type WebManifestPreview = Omit<ConfigPreviewRegistration, 'moduleId'> & {
  moduleId?: string;
};

export type WebManifestGuiEditor = Omit<PluginGuiEditorRegistration, 'moduleId'> & {
  moduleId?: string;
};

export type WebManifestSurface = Omit<SurfaceRegistration, 'moduleId'> & {
  moduleId?: string;
};

export type WebManifestInsightDefinition = Omit<InsightDefinitionRegistration, 'moduleId'> & {
  moduleId?: string;
};

export type WebManifestLocale = {
  locale: string;
  messages: LocaleMessages;
};

export type WebManifestPluginApiRoute = {
  id: string;
  route: string;
  description?: string;
  readonly?: boolean;
};

export type WebManifestDiagnosticRule = {
  id: string;
  description?: string;
  severity?: 'info' | 'warning' | 'error';
};

export type WebManifestItemEditor = {
  editorId: string;
  descriptor?: WebEditorDescriptor;
  fields?: WebEditorField[];
};

export type WebManifestItemFieldRenderer = {
  type: string;
  renderer: ItemFieldRenderer;
  editorId?: string;
  priority?: number;
};

export type WebManifestItemPreviewFallback = {
  fallback: ItemPreviewFallback;
  editorId?: string;
  kind?: string;
  priority?: number;
};

export type WebManifestSourceDocumentAdapter = {
  kind?: string;
  editorId?: string;
  adapter: SourceDocumentAdapter;
  priority?: number;
};

export type WebManifestFileKindLabel = {
  kind: string;
  label: string | (() => string);
};

export type WebManifestV2 = {
  manifestVersion: WebManifestV2Version;
  module: WebManifestModule;
  files?: WebManifestFile[];
  schemas?: WebManifestConfigSchema[];
  config?: WebManifestPluginConfig;
  surfaces?: WebManifestSurface[];
  previews?: WebManifestPreview[];
  guiEditors?: WebManifestGuiEditor[];
  itemEditors?: WebManifestItemEditor[];
  itemFieldRenderers?: WebManifestItemFieldRenderer[];
  itemPreviewFallbacks?: WebManifestItemPreviewFallback[];
  effectTypes?: EffectTypeDefinition[];
  sourceDocumentAdapters?: WebManifestSourceDocumentAdapter[];
  fileKindLabels?: WebManifestFileKindLabel[];
  insightDefinitions?: WebManifestInsightDefinition[];
  pluginApiRoutes?: WebManifestPluginApiRoute[];
  locales?: WebManifestLocale[];
  capabilities?: WebManifestCapability[];
  diagnostics?: WebManifestDiagnosticRule[];
};

export type WebManifestRegistrationResult = {
  manifest: WebManifestV2;
  diagnostics: WebManifestDiagnosticEntry[];
};

export type WebManifestDiagnosticEntry = {
  manifestId: string;
  moduleId?: string;
  ruleId: string;
  severity: 'info' | 'warning' | 'error';
  message: string;
  path?: string;
};

export type WebManifestRuntimeSnapshot = {
  manifests: WebManifestV2[];
  diagnostics: WebManifestDiagnosticEntry[];
};

export type DefinePreviewInput = WebManifestPreview & { component: ComponentType<any> };
