export type ModuleStatus = {
  id: string;
  present: boolean;
  enabled: boolean;
  version: string;
  main: string;
  authors: string[];
  status: 'OK' | 'DISABLED' | 'MISSING' | string;
};

export type ConfigFile = {
  path: string;
  name: string;
  type: 'file';
  editable: boolean;
  size: number;
  lastModified: number;
  content?: string;
};

export type RuntimeLibrary = {
  path: string;
  fileName: string;
  size: number;
  lastModified: number;
  status: string;
};

export type ConfigNodeType = 'boolean' | 'text' | 'number' | 'list' | 'object' | 'scriptText' | string;

export type WebConfigNode = {
  path: string;
  label: string;
  comment: string;
  type: ConfigNodeType;
  editable: boolean;
  value: unknown;
};

export type WebRegistryFile = {
  id: string;
  moduleId: string;
  path: string;
  title: string;
  kind: 'CONFIG' | 'GUI' | 'ITEM' | 'SCRIPT' | 'config' | 'script' | string;
  comment: string;
  nodes: WebConfigNode[];
  children?: { name: string; relativePath: string; fullPath?: string }[];
};

export type WebRegistryModule = {
  id: string;
  name: string;
  summary: string;
  tone: string;
  icon: string;
  present: boolean;
  enabled: boolean;
  version: string;
  files: WebRegistryFile[];
};

export type RegistryTreeNode = {
  id: string;
  label: string;
  type: string;
  moduleId?: string;
  children?: RegistryTreeNode[];
};

export type WebRegistry = {
  modules: WebRegistryModule[];
  tree: RegistryTreeNode[];
};

export type GuiDocument = {
  moduleId: string;
  path: string;
  content: string;
  data: GuiTemplateData;
};

export type GuiTemplateData = {
  id?: string;
  title?: unknown;
  rows?: number;
  slots?: Record<string, GuiSlotDefinition>;
  virtual_items?: Record<string, GuiSlotDefinition>;
  texts?: Record<string, unknown>;
  [key: string]: unknown;
};

export type GuiSlotDefinition = {
  type?: string;
  slots?: number[] | number | string;
  item?: string;
  display_name?: unknown;
  lore?: unknown;
  hidden_components?: unknown;
  enchantments?: unknown;
  custom_model_data?: unknown;
  item_model?: unknown;
  sounds?: unknown;
  [key: string]: unknown;
};
