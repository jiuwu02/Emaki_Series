import type { LocaleMessages } from '../i18n';
import type { WebManifestCapability, WebManifestFileKindLabel, WebManifestGuiEditor, WebManifestItemEditor, WebManifestItemFieldRenderer, WebManifestItemPreviewFallback, WebManifestPluginApiRoute, WebManifestPreview, WebManifestSourceDocumentAdapter, WebManifestSurface, WebManifestV2 } from '../platform/manifest';
import { defineWebManifestV2, registerWebManifestV2 } from '../platform/manifest';
import type { EmakiSchemaAst } from '../schema';
import { schemaAstToConfigFields } from '../schema';

export type DefinePluginModuleInput = Omit<WebManifestV2, 'manifestVersion'> & { manifestVersion?: '2' };

export function defineEmakiPluginWebModule(input: DefinePluginModuleInput): WebManifestV2 {
  return defineWebManifestV2(input);
}

export function registerEmakiPluginWebModule(input: DefinePluginModuleInput | WebManifestV2): WebManifestV2 {
  const manifest = defineWebManifestV2(input as WebManifestV2);
  registerWebManifestV2(manifest);
  return manifest;
}

export function defineConfigSchema(schema: EmakiSchemaAst): NonNullable<WebManifestV2['schemas']>[number] {
  return {
    id: schema.id,
    pathPrefix: schema.pathPrefix,
    pathPattern: schema.pathPattern,
    fields: schemaAstToConfigFields(schema).map(field => [field.path, field.label ?? field.path, field.comment ?? '', String(field.type ?? 'text'), field])
  };
}

export function definePreview(preview: WebManifestPreview): WebManifestPreview {
  return { ...preview };
}

export function defineGuiEditor(editor: WebManifestGuiEditor): WebManifestGuiEditor {
  return { ...editor };
}

export function defineSurface(surface: WebManifestSurface): WebManifestSurface {
  return { ...surface };
}

export function defineItemEditor(editor: WebManifestItemEditor): WebManifestItemEditor {
  return { ...editor };
}

export function defineItemFieldRenderer(renderer: WebManifestItemFieldRenderer): WebManifestItemFieldRenderer {
  return { ...renderer };
}

export function defineItemPreviewFallback(fallback: WebManifestItemPreviewFallback): WebManifestItemPreviewFallback {
  return { ...fallback };
}

export function defineSourceDocumentAdapter(adapter: WebManifestSourceDocumentAdapter): WebManifestSourceDocumentAdapter {
  return { ...adapter };
}

export function defineFileKindLabel(label: WebManifestFileKindLabel): WebManifestFileKindLabel {
  return { ...label };
}

export function defineLocales(locale: string, messages: LocaleMessages): NonNullable<WebManifestV2['locales']>[number] {
  return { locale, messages: { ...messages } };
}

export function definePluginApi(route: WebManifestPluginApiRoute): WebManifestPluginApiRoute {
  return { ...route };
}

export function defineCapabilities(capabilities: WebManifestCapability[]): WebManifestCapability[] {
  return [...new Set(capabilities)];
}
