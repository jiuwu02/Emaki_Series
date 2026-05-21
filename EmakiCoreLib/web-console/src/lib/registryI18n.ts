import { getLocale, getLocaleMessages, t } from '../i18n';
import { humanizeFieldLabel, lastPathKey } from './fieldI18n';
import type { RegistryTreeNode, WebRegistryFile, WebRegistryModule } from '../types';

type BuiltinFileCopy = { title: string; comment: string };

const BUILTIN_FILE_COPY: Record<string, BuiltinFileCopy> = {
  config: { title: '主配置', comment: '插件主配置文件。' },
  plugin: { title: '插件描述', comment: 'plugin.yml 插件描述与依赖声明。' },
  'web-console': { title: 'Web Console 声明', comment: 'Web Console 文件注册表与可编辑资源声明。' },
  lang: { title: '语言文件', comment: '语言文案文件目录。' },
  gui: { title: 'GUI 模板', comment: 'GUI 模板文件目录。' },
  items: { title: '物品定义', comment: '物品定义文件目录。' },
  sets: { title: '套装定义', comment: '套装定义文件目录。' },
  gems: { title: '宝石定义', comment: '宝石定义文件目录。' },
  conditions: { title: '条件定义', comment: '条件定义文件目录。' },
  resonances: { title: '共鸣定义', comment: '共鸣定义文件目录。' },
  recipes: { title: '配方文件', comment: '配方定义文件目录。' },
  resources: { title: '资源文件', comment: '资源定义文件目录。' },
  item_adjustments: { title: '物品调整', comment: '物品调整文件目录。' },
  skills: { title: '技能定义', comment: '技能定义文件目录。' },
  attributes: { title: '属性定义', comment: '属性定义文件目录。' },
  damage_types: { title: '伤害类型', comment: '伤害类型定义文件目录。' },
  lore_formats: { title: 'Lore 格式', comment: 'Lore 格式定义文件目录。' },
  attribute_balance: { title: '属性权重', comment: '属性分组、角色与评分权重设置。' },
  default: { title: '默认模板', comment: '目录内的默认模板文件。' },
  zh_cn: { title: '中文语言文件', comment: '简体中文语言资源文件。' },
  en_us: { title: '英文语言文件', comment: 'English language resource file.' },
  example_recipe: { title: '示例配方', comment: '示例配方文件。' },
  chain_example_recipe: { title: '链式示例配方', comment: '链式示例配方文件。' },
  example_item: { title: '示例物品', comment: '示例物品定义文件。' },
  example_set: { title: '示例套装', comment: '示例套装定义文件。' },
  example_gem: { title: '示例宝石', comment: '示例宝石定义文件。' },
  example_socket_item: { title: '示例插槽物品', comment: '示例插槽物品定义文件。' },
  example_resonance: { title: '示例共鸣', comment: '示例共鸣定义文件。' },
  example_skill: { title: '示例技能', comment: '示例技能定义文件。' },
  example_combo_skill: { title: '连击示例技能', comment: '连击示例技能定义文件。' },
  example_projectile_skill: { title: '投射物示例技能', comment: '投射物示例技能定义文件。' },
  example_resource: { title: '示例资源', comment: '示例资源定义文件。' },
  example_branch_recipe: { title: '示例分支配方', comment: '示例分支配方文件。' },
  editor_gui: { title: '编辑器 GUI', comment: '编辑器 GUI 模板文件。' },
  forge_gui: { title: '锻造 GUI', comment: '锻造 GUI 模板文件。' },
  recipe_book: { title: '配方书 GUI', comment: '配方书 GUI 模板文件。' },
  skills_gui: { title: '技能 GUI', comment: '技能 GUI 模板文件。' },
  trigger_select_gui: { title: '触发器选择 GUI', comment: '触发器选择 GUI 模板文件。' },
  strengthen_gui: { title: '强化 GUI', comment: '强化 GUI 模板文件。' },
  fermentation_barrel: { title: '发酵桶', comment: '发酵桶 GUI 模板文件。' },
  juicer: { title: '榨汁机', comment: '榨汁机 GUI 模板文件。' },
  oven: { title: '烤炉', comment: '烤炉 GUI 模板文件。' },
  steamer: { title: '蒸锅', comment: '蒸锅 GUI 模板文件。' },
  wok: { title: '炒锅', comment: '炒锅 GUI 模板文件。' },
  gem: { title: '宝石条件', comment: '宝石条件定义文件。' },
  forge: { title: '锻造条件', comment: '锻造条件定义文件。' },
  strengthen: { title: '强化条件', comment: '强化条件定义文件。' },
  emakiitem: { title: 'EmakiItem 条件', comment: 'EmakiItem 条件定义文件。' }
};

const ROOT_PATH_COPY: Record<string, BuiltinFileCopy> = {
  lang: { title: '语言文件', comment: '语言资源文件目录。' },
  gui: { title: 'GUI 模板', comment: 'GUI 模板文件目录。' },
  items: { title: '物品定义', comment: '物品定义文件目录。' },
  sets: { title: '套装定义', comment: '套装定义文件目录。' },
  gems: { title: '宝石定义', comment: '宝石定义文件目录。' },
  conditions: { title: '条件定义', comment: '条件定义文件目录。' },
  resonances: { title: '共鸣定义', comment: '共鸣定义文件目录。' },
  recipes: { title: '配方文件', comment: '配方定义文件目录。' },
  resources: { title: '资源文件', comment: '资源定义文件目录。' },
  item_adjustments: { title: '物品调整', comment: '物品调整文件目录。' },
  skills: { title: '技能定义', comment: '技能定义文件目录。' },
  attributes: { title: '属性定义', comment: '属性定义文件目录。' },
  damage_types: { title: '伤害类型', comment: '伤害类型定义文件目录。' },
  lore_formats: { title: '词条格式', comment: '词条格式定义文件目录。' }
};

const RECIPE_SUBDIR_COPY: Record<string, string> = {
  chopping_board: '砧板配方',
  fermentation_barrel: '发酵桶配方',
  grinder: '研磨机配方',
  juicer: '榨汁机配方',
  oven: '烤炉配方',
  steamer: '蒸锅配方',
  wok: '炒锅配方'
};

const CONDITION_SUBDIR_COPY: Record<string, string> = {
  default_bind: '默认绑定条件',
  default_equipment_level: '默认装备等级条件',
  emakiitem: 'EmakiItem 条件',
  forge: '锻造条件',
  gem: '宝石条件',
  strengthen: '强化条件'
};

export function moduleRegistryNamespace(moduleId: string | undefined): string {
  return String(moduleId ?? '').trim().replace(/[^a-zA-Z0-9_.-]+/g, '').toLowerCase();
}

export function registryFileKey(relativePath: string | undefined): string {
  const normalized = String(relativePath ?? '').trim().replace(/\\/g, '/');
  if (!normalized) return 'file';
  const globIndex = normalized.search(/[?*]/);
  let base = globIndex >= 0 ? normalized.slice(0, globIndex) : normalized;
  base = base.replace(/\/+$/g, '');
  if (!base) base = normalized;
  const segment = base.includes('/') ? base.slice(base.lastIndexOf('/') + 1) : base;
  const withoutExtension = segment.replace(/\.(ya?ml|json|js|kts|txt)$/i, '');
  const cleaned = withoutExtension.replace(/[^a-zA-Z0-9_.-]+/g, '_').replace(/^_+|_+$/g, '').toLowerCase();
  return cleaned || 'file';
}

export function moduleDisplayName(module: WebRegistryModule | null | undefined): string {
  if (!module) return t('core.stage.defaultTitle');
  const namespace = moduleRegistryNamespace(module.id);
  return t(`${namespace}.module.name`, undefined, module.name || module.id);
}

export function moduleDisplaySummary(module: WebRegistryModule | null | undefined): string {
  if (!module) return '';
  const namespace = moduleRegistryNamespace(module.id);
  return t(`${namespace}.module.summary`, undefined, module.summary || '');
}

export function fileDisplayTitle(file: WebRegistryFile | null | undefined): string {
  if (!file) return t('core.stage.defaultHint');
  return resolveFileDisplayText(file.moduleId, file.path, file.title || file.path, 'title');
}

export function fileDisplayComment(file: WebRegistryFile | null | undefined): string {
  if (!file) return '';
  return resolveFileDisplayText(file.moduleId, file.path, file.comment || '', 'comment');
}

export function treeNodeDisplayLabel(node: RegistryTreeNode): string {
  if (node.type === 'module') {
    return t(`${moduleRegistryNamespace(node.moduleId || node.id)}.module.name`, undefined, node.label || node.moduleId || node.id);
  }
  if (node.fileId && node.moduleId) {
    return resolveFileDisplayText(node.moduleId, node.path, node.label || node.path || node.id, 'title');
  }
  return node.label || node.id;
}

export function treeNodeDisplayComment(node: RegistryTreeNode): string {
  if (node.type === 'module') {
    return t(`${moduleRegistryNamespace(node.moduleId || node.id)}.module.summary`, undefined, node.comment || '');
  }
  if (node.fileId && node.moduleId) {
    return resolveFileDisplayText(node.moduleId, node.path, node.comment || '', 'comment');
  }
  return node.comment || '';
}

function resolveFileDisplayText(moduleId: string | undefined, path: string | undefined, fallback: string, kind: 'title' | 'comment'): string {
  const namespace = moduleRegistryNamespace(moduleId);
  const key = registryFileKey(path);
  const localized = t(`${namespace}.file.${key}.${kind}`, undefined, '');
  if (localized) return localized;
  const normalized = String(path ?? '').trim().replace(/\\/g, '/');
  const segments = normalized.split('/').filter(Boolean);
  const root = (segments[0] ?? '').toLowerCase();
  const pathCopy = pathBasedFileDisplay(path);
  const builtin = builtinFileDisplay(key);
  const pathIsMoreSpecific = root === 'gui' || (root === 'recipes' && segments.length > 2);
  if (pathCopy && (key === 'default' || !builtin || pathIsMoreSpecific)) return pathCopy[kind];
  if (builtin) return builtin[kind];
  if (pathCopy) return pathCopy[kind];
  return kind === 'title' ? fallbackFileLabel(path, fallback) : fallback;
}

function builtinFileDisplay(key: string | undefined): BuiltinFileCopy | undefined {
  if (!key) return undefined;
  return BUILTIN_FILE_COPY[key.toLowerCase()];
}

function pathBasedFileDisplay(path: string | undefined): BuiltinFileCopy | undefined {
  const normalized = String(path ?? '').trim().replace(/\\/g, '/');
  if (!normalized) return undefined;
  const segments = normalized.split('/').filter(Boolean);
  const root = (segments[0] ?? '').toLowerCase();
  const leaf = segments[segments.length - 1] ?? '';
  const leafKey = registryFileKey(leaf);
  const humanLeaf = humanizeFilePath(leaf);

  if (root === 'lang') {
    if (leafKey === 'zh_cn') return { title: '中文语言文件', comment: '简体中文语言资源文件。' };
    if (leafKey === 'en_us') return { title: '英文语言文件', comment: 'English language resource file.' };
    return ROOT_PATH_COPY.lang;
  }

  if (root === 'recipes') {
    const recipeKind = RECIPE_SUBDIR_COPY[(segments[1] ?? '').toLowerCase()];
    if (recipeKind) return { title: humanLeaf ? `${recipeKind} · ${humanLeaf}` : recipeKind, comment: `${recipeKind}文件。` };
    return ROOT_PATH_COPY.recipes;
  }

  if (root === 'conditions') {
    const conditionKind = CONDITION_SUBDIR_COPY[(segments[1] ?? '').toLowerCase()] ?? CONDITION_SUBDIR_COPY[leafKey] ?? ROOT_PATH_COPY.conditions.title;
    return { title: humanLeaf ? `${conditionKind} · ${humanLeaf}` : conditionKind, comment: `${conditionKind}文件。` };
  }

  if (root === 'gui') {
    const guiKind = {
      editor_gui: '编辑器 GUI',
      forge_gui: '锻造 GUI',
      fermentation_barrel: '发酵桶 GUI',
      gem: '宝石 GUI',
      juicer: '榨汁机 GUI',
      open: '开槽 GUI',
      oven: '烤炉 GUI',
      recipe_book: '配方书 GUI',
      skills_gui: '技能 GUI',
      steamer: '蒸锅 GUI',
      strengthen_gui: '强化 GUI',
      trigger_select_gui: '触发器选择 GUI',
      upgrade: '升级 GUI',
      wok: '炒锅 GUI'
    }[(segments[1] ?? leafKey).toLowerCase()] ?? ROOT_PATH_COPY.gui.title;
    return { title: guiKind, comment: `${guiKind}文件。` };
  }

  if (root in ROOT_PATH_COPY) {
    const base = ROOT_PATH_COPY[root];
    return { title: humanLeaf ? `${base.title} · ${humanLeaf}` : base.title, comment: base.comment };
  }

  return undefined;
}

function fallbackFileLabel(path: string | undefined, fallback: string): string {
  const preferred = String(fallback ?? '').trim();
  if (preferred && !/[\\/]/.test(preferred) && !/\.(ya?ml|json|js|kts|txt)$/i.test(preferred)) return preferred;
  return humanizeFilePath(path) || preferred;
}

function humanizeFilePath(path: string | undefined): string {
  const normalized = String(path ?? '').trim().replace(/\\/g, '/').replace(/\.(ya?ml|json|js|kts|txt)$/i, '');
  if (!normalized) return '';
  const segments = normalized.split('/').filter(Boolean);
  const leaf = segments[segments.length - 1] || normalized;
  return leaf.replace(/[_-]+/g, ' ').trim();
}

export function configNodeDisplayComment(moduleId: string | undefined, path: string | undefined, fallback = ''): string {
  const namespace = moduleRegistryNamespace(moduleId);
  const exactPath = String(path ?? '');
  const last = lastPathKey(exactPath);
  const keys = [
    namespace && `${namespace}.comment.${exactPath}`,
    namespace && `${namespace}.comment.${last}`,
    `core.comment.${exactPath}`,
    `core.comment.${last}`
  ].filter(Boolean) as string[];
  const currentValue = lookupCurrentLocale(keys);
  if (currentValue) return currentValue;
  if (!getLocale().toLowerCase().startsWith('zh')) return englishCommentFallback(exactPath, fallback);
  for (const key of keys) {
    const value = t(key, undefined, '');
    if (value) return value;
  }
  return fallback;
}

function lookupCurrentLocale(keys: string[]): string | undefined {
  const locale = getLocale();
  const language = locale.split('-')[0];
  const messages = [getLocaleMessages(locale), language && language !== locale ? getLocaleMessages(language) : undefined].filter(Boolean) as Record<string, string>[];
  for (const key of keys) {
    for (const bundle of messages) {
      const value = bundle[key];
      if (value) return value;
    }
  }
  return undefined;
}

function englishCommentFallback(path: string, fallback: string): string {
  if (fallback && !/[^\u0000-\u00ff]/.test(fallback)) return fallback;
  const label = humanizeFieldLabel(path);
  return label ? `Configure ${label}.` : '';
}
