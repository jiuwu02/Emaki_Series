export type LocaleMessages = Record<string, string>;
export type TranslationParams = Record<string, string | number | boolean | null | undefined>;
export type RegisterLocaleOptions = { replace?: boolean; moduleId?: string; persist?: boolean };
export type ModuleLocaleBundle = { moduleId: string; locale: string; messages: LocaleMessages; count: number };
export type Translator = (key: string, params?: TranslationParams, fallback?: string) => string;

const DEFAULT_LOCALE = 'zh-CN';
const FALLBACK_LOCALE = 'zh-CN';
const STORAGE_PREFIX = 'emaki-i18n:';
const stores = new Map<string, LocaleMessages>();
const moduleStores = new Map<string, Map<string, LocaleMessages>>();
let currentLocale = readInitialLocale();

const CORE_ZH_CN: LocaleMessages = {
  'core.surface.gui.label': 'GUI',
  'core.surface.item.label': '物品',
  'core.theme.dark': '深色',
  'core.theme.light': '浅色',
  'core.toast.registryRefreshed': '已刷新配置注册表。',
  'core.toast.refreshFailed': '刷新失败。',
  'core.toast.noChanges': '没有需要保存的改动。',
  'core.toast.saveFailed': '保存失败。',
  'core.toast.savedConfig': '已保存 {count} 项配置，执行 reload 后生效。',
  'core.toast.savedGui': '已保存 GUI 文件，执行 reload 后生效。',
  'core.toast.savedItem': '已保存物品文件，执行 reload 后生效。',
  'core.brand.name': '绘卷核心库',
  'core.brand.console': '配置控制台',
  'core.brand.sigil': '绘',
  'core.theme.switchTitle': '切换颜色主题：{theme}',
  'core.theme.switchAria': '当前颜色主题 {theme}，点击切换',
  'core.tree.caption': '模块树',
  'core.tree.aria': '配置模块与文件',
  'core.tree.loading': '正在载入模块树',
  'core.tree.search': '搜索模块、文件或路径',
  'core.tree.noResults': '没有匹配的文件。',
  'core.tree.dirty': '未保存',
  'core.tree.resizeAria': '调整配置树侧栏宽度',
  'core.auth.logout': '退出登录',
  'core.stage.defaultTitle': '配置控制台',
  'core.stage.defaultHint': '选择左侧文件开始编辑。',
  'core.action.refresh': '刷新',
  'core.action.save': '保存',
  'core.action.saveCount': '保存 {count}',
  'core.action.retry': '重试',
  'core.state.loading': '加载中...',
  'core.empty.selectConfig': '选择左侧配置文件。',
  'core.empty.selectScript': '点击左侧脚本文件开始编辑。',
  'core.empty.selectFile': '点击左侧文件开始编辑。',
  'core.empty.noRegistry': '未读取到任何模块。确认插件已完成注册后重试。',
  'core.empty.noConfigNodes': '此配置文件没有可编辑节点。',
  'core.config.childLoadFailed': '加载失败',
  'core.config.groupItems': '{count} 项',
  'core.config.booleanOn': '开启',
  'core.config.booleanOff': '关闭',
  'core.config.delete': '删除',
  'core.config.remove': '移除',
  'core.config.addItem': '添加一项',
  'core.config.addActionRow': '添加动作行',
  'core.config.newTemplateName': '新模板名称',
  'core.config.addTemplate': '添加模板',
  'core.script.loadFallback': '// 无法加载文件',
  'core.script.loading': '加载中...',
  'core.script.saving': '保存中',
  'core.script.editAria': '编辑脚本 {path}',
  'core.script.help': 'Tab 插入缩进，Ctrl 加空格打开补全，方向键选择补全项。',
  'core.script.saveFailed': '脚本保存失败。',
  'core.login.kicker': '绘卷核心库',
  'core.login.title': '配置控制台',
  'core.login.description': '面向管理员团队的深度配置编辑工具。保存后执行 reload 使运行时生效。',
  'core.login.username': '账号',
  'core.login.password': '密码',
  'core.login.submit': '登录',
  'core.login.busy': '验证中',
  'core.login.failed': '登录失败',
  'core.kind.config': '配置',
  'core.kind.gui': 'GUI',
  'core.kind.item': '物品',
  'core.kind.script': '脚本',
  'core.kind.file': '文件',
  'core.item.defaultBaseName': '<gray>预览装备</gray>',
  'core.item.defaultBaseLore': '<gray>原始装备 Lore</gray>',
  'core.item.editorTitle': '物品编辑器',
  'core.item.unsaved': '未保存',
  'core.item.visual': '可视化',
  'core.item.source': '源码',
  'core.item.saving': '保存中...',
  'core.item.loadingAria': '加载中',
  'core.item.previewAria': '物品预览',
  'core.item.iconAlt': '物品图标',
  'core.item.genericKind': '通用物品',
  'core.item.noPreview': '暂无预览',
  'core.item.basic': '基础',
  'core.item.material': '材质',
  'core.item.displayName': '显示名称',
  'core.field.id': 'ID',
  'core.field.material': '材质',
  'core.field.display_name': '显示名称',
  'core.field.lore': 'Lore',
  'core.field.name_actions': '名称动作',
  'core.field.lore_actions': 'Lore 动作',
  'core.field.action': '动作类型',
  'core.field.type': '类型',
  'core.field.value': '文本',
  'core.field.content': '内容',
  'core.field.target_pattern': '目标匹配',
  'core.field.anchor': '锚点',
  'core.field.regex_pattern': '正则表达式',
  'core.field.replacement': '替换为',
  'core.field.move_up': '上移',
  'core.field.move_down': '下移',
  'core.field.delete': '删除',
  'core.field.none': '未选择',
  'core.field.name_actions.add': '添加动作',
  'core.field.lore_actions.add': '添加动作',
  'core.option.actionType.replace': '替换名称',
  'core.option.actionType.prepend_prefix': '添加前缀',
  'core.option.actionType.append_suffix': '添加后缀',
  'core.option.actionType.regex_replace': '正则替换',
  'core.option.actionType.append': '追加到末尾',
  'core.option.actionType.prepend': '插入到开头',
  'core.option.actionType.insert_below': '向下插入',
  'core.option.actionType.search_insert_below': '查找后插入',
  'core.option.actionType.search_insert': '查找插入',
  'core.option.actionType.insert_above': '向上插入',
  'core.option.actionType.search_insert_above': '查找前插入',
  'core.option.actionType.replace_line': '替换匹配行',
  'core.option.actionType.delete_line': '删除匹配行',
  'core.option.effect.variables': '变量',
  'core.option.effect.ea_attribute': 'EA 属性',
  'core.option.effect.es_skill': 'ES 技能',
  'core.option.effect.name_action': '名称动作',
  'core.option.effect.lore_action': 'Lore 动作',
  'core.option.extract.original': '原样返还',
  'core.option.extract.destroy': '销毁',
  'core.option.extract.downgrade': '降级返还',
  'core.option.failure.none': '无惩罚',
  'core.option.failure.downgrade': '降级',
  'core.option.failure.destroy': '销毁',
  'core.option.gemType.attack': '攻击',
  'core.option.gemType.defense': '防御',
  'core.option.gemType.utility': '功能',
  'core.option.gemType.universal': '通用',
  'core.option.economyProvider.auto': '自动选择',
  'core.option.economyProvider.vault': 'Vault',
  'core.option.economyProvider.excellenteconomy': 'ExcellentEconomy',
  'core.gui.loadFailed': 'GUI 文件加载失败',
  'core.gui.saveFailed': '保存失败',
  'core.gui.selectFile': '从左侧选择一个 GUI 模板文件开始预览。',
  'core.gui.loading': '正在载入 Minecraft GUI 预览...',
  'core.gui.unavailable': '无法加载 GUI 文件。',
  'core.gui.metaRows': '{count} 行',
  'core.gui.metaSlots': '{count} 槽位',
  'core.gui.metaSlotDefinitions': '{count} 个 slot 定义',
  'core.gui.sourcePreview': 'GUI YAML 源码预览',
  'core.gui.preview': '预览',
  'core.gui.reload': '重载',
  'core.gui.save': '保存 GUI',
  'core.gui.gridHelp': '方向键移动槽位，Enter 或空格选择。',
  'core.gui.gridAria': '{title} 槽位网格',
  'core.gui.slotAria': '槽位 {index}{suffix}',
  'core.gui.slotEmpty': '，空槽',
  'core.gui.resizeAria': '调整 GUI 预览与编辑区宽度',
  'core.gui.container': '容器',
  'core.gui.type': 'GUI 类型',
  'core.gui.title': '标题',
  'core.gui.rows': '行数',
  'core.gui.slotInspector': '槽位 {value}',
  'core.gui.noSlotSelected': '未选择',
  'core.gui.createSlot': '在 {slot} 创建 slot',
  'core.gui.slotHint': '点击网格槽位编辑，或从材料面板拖入物品。',
  'core.gui.overlayShow': '设为预览可见',
  'core.gui.overlayHide': '取消预览可见',
  'core.gui.materialSource': '材料源',
  'core.gui.materialSearch': '搜索材料',
  'core.gui.materialPlaceholder': '搜索 material，例如 sword、glass pane、diamond',
  'core.gui.materialAll': '全部',
  'core.gui.materialLimit': '仅显示前 {count} 项，继续输入缩小范围。',
  'core.gui.materialEmpty': '没有匹配的材料。',
  'core.gui.unsavedChanges': '未保存更改',
  'core.gui.reloadDropsChanges': '重载会丢弃当前修改',
  'core.gui.reloadDesc': '{title} 已被修改但尚未保存。继续重载会重新从服务器读取 GUI 文件，并覆盖当前本地编辑内容。',
  'core.gui.cancel': '取消',
  'core.gui.continueReload': '继续重载',
  'core.editor.sourceTitle': '当前草稿源码',
  'core.editor.reloadDesc': '当前文件已被修改但尚未保存。继续重载会从服务器重新读取文件，并丢弃本地草稿。下方是将被丢弃的变更摘要。',
  'core.editor.changesTitle': '{count} 项修改',
  'core.editor.changesMore': '还有 {count} 项未在此摘要中显示。',
  'core.editor.reloadChangesAria': '重载将丢弃 {count} 项修改',
  'core.editor.changedSource': '源码内容已修改。',
  'core.editor.undo': '撤销',
  'core.editor.redo': '重做',
  'core.editor.undoHint': '撤销上一次配置修改，Ctrl+Z',
  'core.editor.redoHint': '重做上一次撤销，Ctrl+Y 或 Ctrl+Shift+Z',
  'core.gui.slotDefinition': '槽位',
  'core.gui.slotType': '槽位类型',
  'core.gui.slot': '槽位',
  'core.gui.slotCount': '{count} 个槽位',
  'core.gui.itemSource': '物品来源',
  'core.gui.item': '物品',
  'core.gui.displayText': '显示文本',
  'core.gui.displayName': '显示名',
  'core.gui.modelComponents': '模型与组件',
  'core.gui.itemModel': '物品模型',
  'core.gui.modelData': '模型数据',
  'core.gui.sounds': '声音',
  'core.gui.advancedFields': '高级字段',
  'core.gui.noAdvancedFields': '没有额外字段。',
  'core.gui.add': '添加',
  'core.gui.clear': '清空',
  'core.gui.applyAdvanced': '应用高级字段',
  'core.gui.jsonParseFailed': 'JSON 解析失败',
  'core.gui.tooltipHidden': 'Tooltip 已隐藏',
  'core.api.network': '无法连接到 Web Console 服务，请检查服务器状态或网络连接。',
  'core.api.invalidJson': '服务器返回了无法解析的响应。',
  'core.api.unauthorized': '会话已过期',
  'core.api.forbidden': '没有权限执行此操作。',
  'core.api.notFound': '请求的资源不存在。',
  'core.api.rateLimited': '操作过于频繁，请稍后再试。',
  'core.api.serverError': '服务器处理失败，请查看控制台日志。',
  'core.api.requestFailed': '请求失败',
  'core.locale.label': '语言',
  'core.locale.switchAria': '当前语言 {locale}，点击切换',
  'core.i18n.open': '字段文本',
  'core.i18n.openAria': '管理 {module} 的字段显示文本',
  'core.i18n.title': '字段显示文本',
  'core.i18n.subtitle': '{module} · {locale} · {count} 项',
  'core.i18n.empty': '此模块还没有通过 Web Console 注册语言文本。',
  'core.i18n.search': '搜索 key 或文本',
  'core.i18n.key': '键',
  'core.i18n.value': '文本',
  'core.i18n.addKey': '新增 key',
  'core.i18n.addValue': '新增文本',
  'core.i18n.add': '添加文本',
  'core.i18n.delete': '删除',
  'core.i18n.reset': '重置草稿',
  'core.i18n.close': '关闭',
  'core.i18n.saved': '已保存语言文本。',
  'core.i18n.noLocales': '没有可切换语言',
  'core.i18n.storageHint': '保存会写入浏览器本地覆盖层，前端刷新后仍会应用。'
};

registerLocale(DEFAULT_LOCALE, CORE_ZH_CN, { replace: true, moduleId: 'EmakiCoreLib' });
loadPersistedLocales();

export function registerLocale(locale: string, messages: LocaleMessages, options: RegisterLocaleOptions = {}): void {
  const normalized = normalizeLocale(locale);
  if (!normalized || !messages) return;
  const next = options.replace ? { ...messages } : { ...(stores.get(normalized) ?? {}), ...messages };
  stores.set(normalized, next);
  if (options.moduleId) mergeModuleMessages(options.moduleId, normalized, messages, options.replace);
  if (options.persist) persistLocale(normalized, next);
}

export function registerModuleLocale(moduleId: string, locale: string, messages: LocaleMessages, options: RegisterLocaleOptions = {}): void {
  registerLocale(locale, messages, { ...options, moduleId });
}

export function replaceLocaleMessages(locale: string, messages: LocaleMessages, options: RegisterLocaleOptions = {}): void {
  const normalized = normalizeLocale(locale);
  if (!normalized) return;
  if (options.moduleId) {
    mergeModuleMessages(options.moduleId, normalized, messages, true);
    const merged = { ...(stores.get(normalized) ?? {}), ...messages };
    stores.set(normalized, merged);
    if (options.persist ?? true) persistLocale(normalized, merged);
    return;
  }
  registerLocale(normalized, messages, { ...options, replace: true, persist: options.persist ?? true });
}

export function getLocaleMessages(locale = currentLocale): LocaleMessages {
  return { ...(stores.get(normalizeLocale(locale)) ?? {}) };
}

export function getAllLocaleMessages(): Record<string, LocaleMessages> {
  return Object.fromEntries([...stores.entries()].map(([locale, messages]) => [locale, { ...messages }]));
}

export function getModuleLocaleBundles(moduleId: string): ModuleLocaleBundle[] {
  const moduleMap = moduleStores.get(normalizeModuleId(moduleId));
  if (!moduleMap) return [];
  return [...moduleMap.entries()].map(([locale, messages]) => ({ moduleId, locale, messages: { ...messages }, count: Object.keys(messages).length }));
}

export function setLocale(locale: string): void {
  const normalized = normalizeLocale(locale);
  currentLocale = normalized || DEFAULT_LOCALE;
  try { localStorage.setItem('emaki-locale', currentLocale); } catch {}
  if (typeof document !== 'undefined') {
    document.documentElement.lang = currentLocale;
    document.documentElement.dir = isRtlLocale(currentLocale) ? 'rtl' : 'ltr';
  }
}

export function getLocale(): string {
  return currentLocale;
}

export function getRegisteredLocales(): string[] {
  return [...stores.keys()];
}

export function t(key: string, params?: TranslationParams, fallback?: string): string {
  const template = lookup(key) ?? fallback ?? key;
  return interpolate(template, params);
}

export function createTranslator(namespace = ''): Translator {
  const prefix = namespace ? `${namespace}.` : '';
  return (key, params, fallback) => t(`${prefix}${key}`, params, fallback);
}

export const withNamespace = createTranslator;

export const i18n = {
  registerLocale,
  registerModuleLocale,
  replaceLocaleMessages,
  getLocaleMessages,
  getAllLocaleMessages,
  getModuleLocaleBundles,
  setLocale,
  getLocale,
  getRegisteredLocales,
  t,
  createTranslator,
  withNamespace
};

function mergeModuleMessages(moduleId: string, locale: string, messages: LocaleMessages, replace = false): void {
  const normalizedModule = normalizeModuleId(moduleId);
  if (!normalizedModule) return;
  const moduleMap = moduleStores.get(normalizedModule) ?? new Map<string, LocaleMessages>();
  const current = moduleMap.get(locale) ?? {};
  moduleMap.set(locale, replace ? { ...messages } : { ...current, ...messages });
  moduleStores.set(normalizedModule, moduleMap);
}

function persistLocale(locale: string, messages: LocaleMessages): void {
  try { localStorage.setItem(`${STORAGE_PREFIX}${locale}`, JSON.stringify(messages)); } catch {}
}

function loadPersistedLocales(): void {
  try {
    for (let index = 0; index < localStorage.length; index++) {
      const key = localStorage.key(index);
      if (!key?.startsWith(STORAGE_PREFIX)) continue;
      const locale = key.substring(STORAGE_PREFIX.length);
      const raw = localStorage.getItem(key);
      if (!raw) continue;
      const parsed = JSON.parse(raw) as LocaleMessages;
      stores.set(locale, { ...(stores.get(locale) ?? {}), ...parsed });
    }
  } catch {}
}

function lookup(key: string): string | undefined {
  const locales = localeFallbacks(currentLocale);
  for (const locale of locales) {
    const value = stores.get(locale)?.[key];
    if (value != null) return value;
  }
  return undefined;
}

function localeFallbacks(locale: string): string[] {
  const normalized = normalizeLocale(locale) || DEFAULT_LOCALE;
  const language = normalized.split('-')[0];
  return [...new Set([normalized, language, FALLBACK_LOCALE, DEFAULT_LOCALE])];
}

function interpolate(template: string, params?: TranslationParams): string {
  if (!params) return template;
  return template.replace(/\{([\w.-]+)\}/g, (_, name: string) => {
    const value = params[name];
    return value == null ? '' : String(value);
  });
}

function readInitialLocale(): string {
  try {
    const saved = localStorage.getItem('emaki-locale');
    if (saved) return normalizeLocale(saved) || DEFAULT_LOCALE;
  } catch {}
  if (typeof navigator !== 'undefined') return normalizeLocale(navigator.language) || DEFAULT_LOCALE;
  return DEFAULT_LOCALE;
}

function normalizeLocale(locale: string | undefined): string {
  return String(locale ?? '').trim().replace('_', '-');
}

function normalizeModuleId(moduleId: string | undefined): string {
  return String(moduleId ?? '').trim().toUpperCase();
}

function isRtlLocale(locale: string): boolean {
  return /^(ar|fa|he|ur)(-|$)/i.test(locale);
}

setLocale(currentLocale);
