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
  options?: string[];
};

export type WebEditorFieldType = 'text' | 'number' | 'boolean' | 'textarea' | 'stringList' | 'enum' | 'json' | 'actions' | string;

export type WebEditorField = {
  path: string;
  label: string;
  type: WebEditorFieldType;
  comment?: string;
  placeholder?: string;
  options?: string[];
  rows?: number;
  wide?: boolean;
};

export type WebEditorSection = {
  title: string;
  comment?: string;
  fields: WebEditorField[];
};

export type WebEditorDescriptor = {
  id: string;
  moduleId?: string;
  title?: string;
  kindLabel?: string;
  baseName?: string;
  baseLore?: string[];
  sections?: WebEditorSection[];
  preview?: Record<string, unknown>;
  [key: string]: unknown;
};

export type WebRegistryFile = {
  id: string;
  moduleId: string;
  path: string;
  title: string;
  kind: 'CONFIG' | 'GUI' | 'ITEM' | 'SCRIPT' | 'config' | 'script' | string;
  comment: string;
  editorId?: string;
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
  editors?: Record<string, WebEditorDescriptor>;
};

export type GuiDocument = {
  moduleId: string;
  path: string;
  content: string;
  data: GuiTemplateData;
};

export type ItemDocument = {
  moduleId: string;
  path: string;
  content: string;
  data: ItemDocumentData;
};

export type ItemDocumentData = Record<string, unknown>;

export type ItemPreviewStep = {
  action: string;
  value?: string;
  anchor?: string;
  result?: string;
  before?: string[];
  after?: string[];
  [key: string]: unknown;
};

export type ItemPreviewEffect = {
  type: string;
  source?: string;
  payload?: Record<string, unknown>;
  resolved?: Record<string, unknown>;
  attributes?: Record<string, unknown>;
  skills?: unknown;
};

export type ItemPreviewResult = {
  kind: 'gem' | 'gem_socket_item' | 'generic_item' | string;
  id?: string;
  material?: string;
  displayName?: string;
  lore?: string[];
  variables?: Record<string, unknown>;
  nameSteps?: ItemPreviewStep[];
  loreSteps?: ItemPreviewStep[];
  effects?: ItemPreviewEffect[];
  level?: number;
  levels?: number[];
  gemType?: string;
  socketCompatibility?: unknown[];
  upgrade?: Record<string, unknown>;
  costs?: Record<string, unknown>;
  extractReturn?: unknown;
  match?: Record<string, unknown>;
  slots?: unknown;
  defaultOpenSlots?: unknown[];
  allowedGemTypes?: unknown[];
  maxSameType?: unknown;
  maxSameId?: unknown;
  gui?: unknown;
  [key: string]: unknown;
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
