export { GuiEditorSurface } from './GuiEditorSurface';
export { ItemEditorSurface } from './ItemEditorSurface';
export { EMAKI_WEB_CONSOLE_API_VERSION, registerSurface, getSurface, getAllSurfaces, installWebConsoleHost, isKind, registerPluginGuiSurface, registerPluginGuiEditor, registerPluginSurfaces, standardGuiFields, registerEditorDescriptor, registerEditorField, registerGuiEditorDescriptor, registerGuiEditorField, recordExtensionStatus, getExtensionStatuses, applyEditorDescriptorOverrides, type EmakiWebConsoleHost, type SurfaceProps, type SurfaceRegistration, type PluginGuiEditorRegistration, type StandardGuiFieldEntry } from './registry';
export * from './components';
export * from './lib';
export * from './i18n';
export type { ApiClient, ActionTypesResult, EconomyProvidersResult, RegistryFileNodesResult, RegistrySaveResult } from './api';
export type { ItemPreviewResult, WebEditorDescriptor, WebEditorField, WebRegistry, WebRegistryFile, WebRegistryModule } from './types';
