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
  'emakicorelib.module.name': 'CoreLib',
  'emakicorelib.module.summary': 'WebUIEdit、动作、脚本与公共运行库',
  'emakicorelib.file.config.title': '主配置',
  'emakicorelib.file.config.comment': '完整 config.yml 结构化配置注册。所有字段均通过前端 i18n 补充说明。',
  'emakicorelib.file.scripts.title': 'JS 脚本',
  'emakicorelib.file.scripts.comment': 'CoreLib JavaScript 脚本目录，当前仅保留文本预览入口。',
  'emakicorelib.field.web_console': 'WebUIEdit',
  'emakicorelib.comment.web_console': '内置 WebUIEdit 开放策略与鉴权配置。',
  'emakicorelib.field.web_console.enabled': '启用前端',
  'emakicorelib.comment.web_console.enabled': '开启后监听 host:port，reload 会先关闭再按新配置启动。',
  'emakicorelib.field.web_console.host': '监听地址',
  'emakicorelib.comment.web_console.host': '127.0.0.1 仅本机，0.0.0.0 表示所有网卡。',
  'emakicorelib.field.web_console.port': '监听端口',
  'emakicorelib.comment.web_console.port': 'WebUIEdit HTTP 端口。',
  'emakicorelib.field.web_console.public_access_warning': '公网提示',
  'emakicorelib.comment.web_console.public_access_warning': '当监听地址可能对外开放时，在登录响应中提示风险。',
  'emakicorelib.field.web_console.auth': '登录鉴权',
  'emakicorelib.comment.web_console.auth': 'WebUIEdit 登录账号、密码和会话有效期。',
  'emakicorelib.comment.web_console.auth.username': 'WebUIEdit 登录账号。',
  'emakicorelib.comment.web_console.auth.password': 'WebUIEdit 登录密码，启用前必须修改默认值。',

  'emakicorelib.field.web_console.auth.session_timeout_minutes': '会话分钟',
  'emakicorelib.comment.web_console.auth.session_timeout_minutes': '登录 Token 的有效分钟数。',
  'emakicorelib.field.web_console.security': '安全限制',
  'emakicorelib.comment.web_console.security': 'WebUIEdit 请求体、写入权限等安全限制。',
  'emakicorelib.comment.web_console.security.allow_config_write': '开启后 WebUIEdit 才允许保存配置变更。',
  'emakicorelib.comment.web_console.security.max_request_body_kb': '单次 WebUIEdit 请求体大小上限，单位 KB。',

  'emakicorelib.field.action': '动作',
  'emakicorelib.comment.action': 'CoreLib 动作系统配置。',
  'emakicorelib.field.action.templates': '动作模板',
  'emakicorelib.comment.action.templates': '可在配方或动作列表中通过 @template=名称 引用的动作模板。每个子键为模板名称，值为动作列表。',
  'emakicorelib.field.script': '脚本',
  'emakicorelib.comment.script': 'CoreLib JavaScript 引擎与脚本安全配置。',
  'emakicorelib.field.script.enabled': '启用脚本',
  'emakicorelib.comment.script.enabled': '是否启用 CoreLib JavaScript 动作能力。',
  'emakicorelib.field.script.engine': '脚本引擎',
  'emakicorelib.comment.script.engine': 'GraalJS 引擎、超时、缓存和宿主访问配置。',
  'emakicorelib.field.script.engine.type': '引擎类型',
  'emakicorelib.comment.script.engine.type': 'GraalJS 引擎 ID，默认值为 graaljs。',
  'emakicorelib.field.script.engine.default_timeout_millis': '默认超时 (ms)',
  'emakicorelib.comment.script.engine.default_timeout_millis': '脚本未显式指定超时时使用的默认毫秒数。',
  'emakicorelib.field.script.engine.max_timeout_millis': '最大超时 (ms)',
  'emakicorelib.comment.script.engine.max_timeout_millis': '单个脚本允许请求的最大超时时间，单位毫秒。',
  'emakicorelib.field.script.engine.cache_enabled': '启用缓存',
  'emakicorelib.comment.script.engine.cache_enabled': '按脚本内容哈希缓存读取结果，减少重复读取。',
  'emakicorelib.field.script.engine.recompile_on_reload': '重载时重编译',
  'emakicorelib.comment.script.engine.recompile_on_reload': 'reload 后是否强制让脚本重新进入编译流程。',
  'emakicorelib.field.script.engine.allow_host_access': '允许宿主访问',
  'emakicorelib.comment.script.engine.allow_host_access': '是否允许脚本访问宿主对象。',
  'emakicorelib.field.script.engine.allow_host_class_lookup': '允许类查找',
  'emakicorelib.comment.script.engine.allow_host_class_lookup': '是否允许脚本解析 Java 类名。',
  'emakicorelib.field.script.engine.allow_io': '允许 IO',
  'emakicorelib.comment.script.engine.allow_io': '是否允许脚本读写文件或流。',
  'emakicorelib.field.script.engine.allow_threads': '允许线程',
  'emakicorelib.comment.script.engine.allow_threads': '是否允许脚本创建额外线程。',
  'emakicorelib.field.script.engine.allow_native_access': '允许原生访问',
  'emakicorelib.comment.script.engine.allow_native_access': '是否允许脚本调用原生接口。',
  'emakicorelib.field.script.engine.allow_environment_access': '允许环境访问',
  'emakicorelib.comment.script.engine.allow_environment_access': '是否允许脚本读取进程环境变量。',
  'emakicorelib.field.script.paths': '脚本目录',
  'emakicorelib.comment.script.paths': '脚本文件根目录和启动时自动创建的子目录。',
  'emakicorelib.field.script.paths.root': '脚本根目录',
  'emakicorelib.comment.script.paths.root': '脚本文件的基准目录，所有脚本路径都从这里解析。',
  'emakicorelib.field.script.paths.create_directories': '预创建目录',
  'emakicorelib.comment.script.paths.create_directories': '启动时会在脚本根目录下自动创建的子目录列表。',
  'emakicorelib.field.script.action': '脚本动作',
  'emakicorelib.comment.script.action': 'RunJavaScriptAction 的动作 ID、别名和默认入口。',
  'emakicorelib.field.script.action.id': '动作 ID',
  'emakicorelib.comment.script.action.id': '注册给动作系统使用的主动作 ID。',
  'emakicorelib.field.script.action.aliases': '动作别名',
  'emakicorelib.comment.script.action.aliases': '会一并注册的别名 ID 列表。',
  'emakicorelib.field.script.action.default_function': '默认函数',
  'emakicorelib.comment.script.action.default_function': '未指定 function 参数时调用的脚本函数名。',
  'emakicorelib.field.script.action.stop_on_failure': '失败即停',
  'emakicorelib.comment.script.action.stop_on_failure': '当前一条动作失败时，是否停止后续执行。',
  'emakicorelib.field.script.context': '脚本上下文',
  'emakicorelib.comment.script.context': '控制脚本可直接访问的 API 容器。',
  'emakicorelib.field.script.context.expose_context': '暴露上下文',
  'emakicorelib.comment.script.context.expose_context': '向脚本公开 ActionContext 和 arguments。',
  'emakicorelib.field.script.context.expose_player': '暴露玩家',
  'emakicorelib.comment.script.context.expose_player': '向脚本公开玩家 API。',
  'emakicorelib.field.script.context.expose_item': '暴露物品',
  'emakicorelib.comment.script.context.expose_item': '向脚本公开物品 API。',
  'emakicorelib.field.script.context.expose_action': '暴露动作',
  'emakicorelib.comment.script.context.expose_action': '向脚本公开 action API，允许脚本派发动作。',
  'emakicorelib.field.script.context.expose_logger': '暴露日志器',
  'emakicorelib.comment.script.context.expose_logger': '向脚本公开按脚本路径和插件来源写日志的入口。',
  'emakicorelib.field.script.context.expose_random': '暴露随机数',
  'emakicorelib.comment.script.context.expose_random': '向脚本公开随机数 API。',
  'emakicorelib.field.script.context.expose_shared_state': '暴露共享状态',
  'emakicorelib.comment.script.context.expose_shared_state': '向脚本公开共享状态读写 API。',
  'emakicorelib.field.script.context.expose_text': '暴露文本工具',
  'emakicorelib.comment.script.context.expose_text': '向脚本公开文本处理工具。',
  'emakicorelib.field.script.security': '脚本安全',
  'emakicorelib.comment.script.security': '脚本路径、动作派发和调用深度限制。',
  'emakicorelib.field.script.security.denied_path_fragments': '禁用路径片段',
  'emakicorelib.comment.script.security.denied_path_fragments': '脚本路径中禁止出现的片段，防止越界或绝对路径。',
  'emakicorelib.field.script.security.denied_actions_from_script': '禁用动作 ID',
  'emakicorelib.comment.script.security.denied_actions_from_script': '脚本里禁止直接派发的动作 ID。',
  'emakicorelib.field.script.security.allow_action_dispatch': '允许动作派发',
  'emakicorelib.comment.script.security.allow_action_dispatch': '是否允许脚本调用 action API 触发动作。',
  'emakicorelib.field.script.security.max_action_depth': '最大动作深度',
  'emakicorelib.comment.script.security.max_action_depth': '脚本递归派发动作的最大深度，0 表示不限制。',
  'emakicorelib.field.script.debug': '调试',
  'emakicorelib.comment.script.debug': '脚本加载和执行时的日志开关。',
  'emakicorelib.field.script.debug.log_script_load': '记录加载日志',
  'emakicorelib.comment.script.debug.log_script_load': '加载脚本时是否输出日志。',
  'emakicorelib.field.script.debug.log_script_execute': '记录执行日志',
  'emakicorelib.comment.script.debug.log_script_execute': '执行脚本时是否输出日志。',
  'emakicorelib.field.script.debug.print_stacktrace': '输出堆栈',
  'emakicorelib.comment.script.debug.print_stacktrace': '发生异常时是否打印完整堆栈。',
  'core.comment.version': '配置结构版本，通常不建议手动修改。',
  'core.comment.language': '语言文件 ID，对应 lang/<language>.yml。',
  'core.comment.release_default_data': '首次启动或缺失数据时写入默认资源。',
  'core.comment.enabled': '控制该功能、条目或子系统是否启用。',
  'core.comment.debug': '调试输出相关配置，生产环境通常建议关闭。',
  'core.comment.permission': '权限与 OP 绕过等访问控制设置。',
  'core.comment.gui': 'GUI 入口或模板相关配置，具体模板文件会注册为 GUI 文件类型。',
  'core.comment.success': '功能成功时触发的动作列表或成功分支配置。',
  'core.comment.failure': '功能失败时触发的动作列表或失败分支配置。',
  'core.comment.item_sources': '支持 minecraft、CraftEngine、ItemsAdder、Nexo、MMOItems 等来源格式的物品 ID 列表。',
  'core.comment.material': 'Minecraft 原版材料或物品来源 ID。',
  'core.comment.amount': '消耗、产出或显示用的数量。',
  'core.comment.title': 'GUI、消息或显示标题文本。',
  'core.comment.size': 'GUI 行数、槽位数或集合容量等数值。',
  'core.comment.slots': 'GUI 槽位列表或槽位规则。',
  'core.comment.items': '物品配置分组；物品定义文件会统一标记为 ITEM 类型。',
  'core.comment.commands': '命令列表，通常按顺序执行。',
  'core.comment.chance': '成功率、触发率或权重概率，按该字段上下文解释。',
  'core.comment.default_chance': '未单独配置时使用的默认概率。',
  'core.comment.op_bypass': '开启后 OP 可跳过对应消耗、权限或条件检查。',
  'core.comment.actions': 'CoreLib Action 动作列表、分组或模板配置，按顺序执行。',
  'core.toast.registryRefreshed': '已刷新模块注册表。',
  'core.toast.extensionLoadFailed': '有 {count} 个插件扩展加载失败，已保留 CoreLib 可用页面。',
  'core.toast.refreshFailed': '刷新注册表失败。',
  'core.toast.reloaded': '已从服务器重新读取当前文件。',
  'core.toast.noChanges': '没有未保存修改。',
  'core.toast.saveFailed': '保存失败。请检查服务器日志或文件权限。',
  'core.toast.savedConfig': '已保存 {count} 项配置。执行插件 reload 后生效。',
  'core.toast.savedGui': '已保存 GUI 文件。执行插件 reload 后生效。',
  'core.toast.savedItem': '已保存物品文件。执行插件 reload 后生效。',
  'core.brand.name': 'Emaki Web Editor',
  'core.brand.subtitle': '配置 · GUI · 物品 · 脚本',
  'core.theme.switchTitle': '切换主题：当前 {theme}',
  'core.theme.switchAria': '当前主题为 {theme}，点击切换',
  'core.tree.caption': '模块树',
  'core.tree.aria': '配置模块与文件',
  'core.tree.loading': '正在读取模块树',
  'core.tree.search': '搜索模块、文件或路径',
  'core.tree.noResults': '没有匹配的模块、文件或路径。',
  'core.tree.dirty': '未保存修改',
  'core.tree.createFile': '新建文件',
  'core.tree.deleteFile': '删除文件',
  'core.tree.resizeAria': '调整配置树侧栏宽度',
  'core.auth.logout': '退出登录',
  'core.stage.defaultTitle': 'WebUIEdit',
  'core.stage.defaultHint': '从左侧模块树选择文件开始编辑。',
  'core.action.refresh': '刷新',
  'core.action.save': '保存',
  'core.action.saveCount': '保存 {count} 项',
  'core.action.saveSource': '保存源码',
  'core.action.retry': '重试',
  'core.state.loading': '正在加载…',
  'core.empty.selectConfig': '从左侧模块树选择配置文件。',
  'core.empty.selectScript': '从左侧模块树选择脚本文件。',
  'core.empty.selectFile': '从左侧模块树选择子文件。',
  'core.empty.noRegistry': '没有读取到已注册模块。确认 Emaki 插件已启动，并且服务端已返回模块注册表后重试。',
  'core.empty.noConfigNodes': '此配置文件没有可结构化编辑的节点。可打开源码查看或修改。',
  'core.config.childLoadFailed': '读取子文件节点失败',
  'core.config.groupItems': '{count} 项',
  'core.config.booleanOn': '开启',
  'core.config.booleanOff': '关闭',
  'core.config.delete': '删除',
  'core.config.remove': '移除',
  'core.config.addItem': '添加一项',
  'core.config.addActionRow': '添加动作行',
  'core.config.newTemplateName': '新键名',
  'core.config.addTemplate': '添加键',
  'core.config.itemIndex': '第 {index} 项',
  'core.config.deleteItem': '删除第 {index} 项',
  'core.config.deleteItemInGroup': '删除 {group} 的第 {index} 项',
  'core.config.removeGroup': '移除 {group}',
  'core.config.sourceLoading': '源码仍在读取，暂时无法新增字段。',
  'core.config.createdSourceField': '已在源码草稿中新增 {key}。检查后保存源码。',
  'core.config.createKicker': '新增子对象',
  'core.config.createTitle': '新建配置子对象',
  'core.config.createDesc': '将在 {path} 下新增一个对象，并写入源码草稿。保存源码后刷新结构化节点。',
  'core.config.createKey': '新键名',
  'core.config.createTemplate': '模板',
  'core.config.createNoTemplate': '此位置没有注册表单模板，将创建一个空对象。',
  'core.config.createEmptyTemplate': '空对象',
  'core.config.createDuplicate': '{key} 已存在，请使用其他键名。',
  'core.config.create': '新增对象',
  'core.config.deleteObject': '删除对象',
  'core.config.deleteObjectKicker': '删除配置对象',
  'core.config.deleteObjectTitle': '删除配置对象',
  'core.config.deleteObjectDesc': '将从源码草稿中删除 {path} 及其所有子字段。保存前可以通过重载放弃此删除。',
  'core.config.deleteObjectConfirmLabel': '完整对象路径',
  'core.config.deleteObjectConfirm': '删除对象',
  'core.config.deletedSourceObject': '已在源码草稿中删除 {path}。检查 diff 后保存源码。',
  'core.file.createTitle': '新建文件',
  'core.file.createDesc': '在当前文件分组下创建文件。可输入子目录，扩展名会按注册规则补全。',
  'core.file.createName': '文件名',
  'core.file.createPlaceholder': 'example.yml',
  'core.file.create': '创建文件',
  'core.file.created': '已创建 {path}。',
  'core.file.createFailed': '创建文件失败。',
  'core.file.deleteTitle': '删除文件',
  'core.file.deleteDesc': '将删除 {path}。此操作无法在 WebUIEdit 内撤销，请输入完整路径确认。',
  'core.file.deleteConfirmLabel': '完整路径',
  'core.file.delete': '永久删除文件',
  'core.file.deleted': '已删除 {path}。',
  'core.file.deleteFailed': '删除文件失败。',
  'core.script.loadFallback': '// 文件读取失败',
  'core.script.loading': '正在读取脚本…',
  'core.script.saving': '保存中…',
  'core.script.editAria': '编辑脚本 {path}',
  'core.script.help': '按 Tab 缩进，Ctrl+空格打开补全，方向键选择补全项。',
  'core.login.kicker': 'CoreLib WebUIEdit',
  'core.login.title': 'Emaki Web Editor',
  'core.login.description': '集中编辑 Emaki 系列配置、GUI、物品与脚本。保存前审查变更，reload 后生效。',
  'core.login.username': '账号',
  'core.login.password': '密码',
  'core.login.submit': '登录',
  'core.login.busy': '验证中',
  'core.login.failed': '账号或密码无效。',
  'core.login.sessionExpired': '会话已过期，请重新登录。',
  'core.kind.config': '配置',
  'core.kind.gui': 'GUI',
  'core.kind.item': '物品',
  'core.kind.set': '套装',
  'core.kind.script': '脚本',
  'core.kind.file': '文件',
  'core.item.defaultBaseName': '<gray>预览装备</gray>',
  'core.item.defaultBaseLore': '<gray>原始装备 Lore</gray>',
  'core.item.editorTitle': '物品编辑器',
  'core.item.unsaved': '未保存修改',
  'core.item.visual': '结构化',
  'core.item.source': '源码',
  'core.item.saving': '保存中…',
  'core.item.loadingAria': '正在读取物品文件',
  'core.item.previewAria': '物品预览',
  'core.item.iconAlt': '物品图标',
  'core.item.genericKind': '物品',
  'core.item.noPreview': '未生成预览',
  'core.item.basic': '基础',
  'core.item.material': '材质',
  'core.item.displayName': '显示名称',
  'core.field.version': '配置版本',
  'core.field.language': '语言',
  'core.field.release_default_data': '释放默认资源',
  'core.field.enabled': '启用',
  'core.field.debug': '调试',
  'core.field.permission': '权限',
  'core.field.gui': 'GUI',
  'core.field.actions': '动作',
  'core.field.success': '成功动作',
  'core.field.failure': '失败动作',
  'core.field.item_sources': '物品来源',
  'core.field.amount': '数量',
  'core.field.title': '标题',
  'core.field.size': '大小',
  'core.field.slots': '槽位',
  'core.field.items': '物品',
  'core.field.commands': '命令',
  'core.field.chance': '概率',
  'core.field.default_chance': '默认概率',
  'core.field.op_bypass': 'OP 绕过',
  'core.field.id': 'ID',
  'core.field.material': '材质',
  'core.field.display_name': '显示名称',
  'core.field.lore': 'Lore',
  'core.field.name_actions': '名称动作链',
  'core.field.lore_actions': 'Lore 动作链',
  'core.field.action': '动作类型',
  'core.field.type': '类型',
  'core.field.value': '文本',
  'core.field.content': '内容',
  'core.field.target_pattern': '匹配文本',
  'core.field.anchor': '锚点文本',
  'core.field.regex_pattern': '正则表达式',
  'core.field.replacement': '替换为',
  'core.field.move_up': '上移',
  'core.field.move_down': '下移',
  'core.field.delete': '删除',
  'core.field.none': '未选择',
  'core.field.name_actions.add': '添加名称动作',
  'core.field.lore_actions.add': '添加 Lore 动作',
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
  'core.option.effect.name_action': '名称动作链',
  'core.option.effect.lore_action': 'Lore 动作链',
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
  'core.gui.loadFailed': '读取 GUI 文件失败',
  'core.gui.saveFailed': '保存 GUI 文件失败',
  'core.gui.selectFile': '从左侧模块树选择 GUI 模板文件。',
  'core.gui.loading': '正在读取 GUI 文件并生成预览…',
  'core.gui.unavailable': '无法显示 GUI 文件。请检查 YAML 或打开源码。',
  'core.gui.metaRows': '{count} 行',
  'core.gui.metaSlots': '{count} 槽位',
  'core.gui.metaSlotDefinitions': '{count} 个 slot 定义',
  'core.gui.sourcePreview': 'GUI YAML 源码',
  'core.gui.preview': '预览',
  'core.gui.reload': '重载文件',
  'core.gui.save': '保存 GUI',
  'core.gui.gridHelp': '方向键移动槽位，Enter 或空格选择。',
  'core.gui.gridAria': '{title} 槽位网格',
  'core.gui.slotAria': '槽位 {index}{suffix}',
  'core.gui.slotEmpty': '，空槽位',
  'core.gui.resizeAria': '调整 GUI 预览与编辑区宽度',
  'core.gui.container': '容器',
  'core.gui.type': 'GUI 类型',
  'core.gui.title': '标题',
  'core.gui.rows': '行数',
  'core.gui.slotInspector': '槽位 {value}',
  'core.gui.noSlotSelected': '未选择槽位',
  'core.gui.createSlot': '在槽位 {slot} 新建 slot',
  'core.gui.slotHint': '选择网格槽位后编辑，也可以从材料列表拖入物品。',
  'core.gui.overlayShow': '设为预览可见',
  'core.gui.overlayHide': '取消预览可见',
  'core.gui.materialSource': '材料列表',
  'core.gui.materialSearch': '搜索材料',
  'core.gui.materialPlaceholder': '搜索 material，例如 diamond_sword',
  'core.gui.materialAll': '全部',
  'core.gui.materialLimit': '已显示前 {count} 项，继续输入可缩小范围。',
  'core.gui.materialEmpty': '没有匹配的 material。',
  'core.gui.unsavedChanges': '未保存修改',
  'core.gui.reloadDropsChanges': '重载会丢弃未保存修改',
  'core.gui.reloadDesc': '{title} 有未保存修改。继续重载会从服务器重新读取 GUI 文件，并覆盖本地草稿。',
  'core.gui.cancel': '取消',
  'core.gui.continueReload': '丢弃修改并重载',
  'core.editor.sourceTitle': '源码草稿',
  'core.editor.sourceKicker': '源码',
  'core.editor.reloadDesc': '当前文件有未保存修改。继续重载会从服务器重新读取文件，并丢弃本地草稿。',
  'core.editor.changesTitle': '{count} 项未保存修改',
  'core.editor.changesMore': '另有 {count} 项未显示。',
  'core.editor.reloadChangesAria': '重载将丢弃 {count} 项未保存修改',
  'core.editor.changedSource': '源码草稿已修改，无法显示字段级差异。',
  'core.editor.saveTitle': '保存修改',
  'core.editor.saveDesc': '保存以下变更到服务器文件。保存后仍需在游戏内执行 reload 才会生效。',
  'core.editor.saveConfirm': '保存修改',
  'core.editor.sourceDiffTitle': '源码 diff',
  'core.editor.sourceDiffEmpty': '源码内容已修改，但没有可显示的行级差异。',
  'core.editor.sourceDiffMore': '另有 {count} 行 diff 未显示。',
  'core.editor.undo': '撤销',
  'core.editor.redo': '重做',
  'core.editor.undoHint': '撤销上一次修改，Ctrl+Z',
  'core.editor.redoHint': '重做已撤销的修改，Ctrl+Y 或 Ctrl+Shift+Z',
  'core.editor.toolbarAria': '编辑操作',
  'core.gui.slotDefinition': '槽位定义',
  'core.gui.slotType': '槽位类型',
  'core.gui.slot': '槽位',
  'core.gui.slotCount': '{count} 个槽位',
  'core.gui.itemSource': '物品来源',
  'core.gui.item': '物品',
  'core.gui.displayText': '显示文本',
  'core.gui.displayName': '显示名称',
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
  'core.api.network': '无法连接到 WebUIEdit 服务，请检查服务器状态或网络连接。',
  'core.api.invalidJson': '服务器返回了无法解析的响应。',
  'core.api.unauthorized': '会话已过期，请重新登录。',
  'core.api.forbidden': '没有权限执行此操作。',
  'core.api.notFound': '请求的资源不存在。',
  'core.api.rateLimited': '操作过于频繁，请稍后再试。',
  'core.api.serverError': '服务器处理失败，请查看控制台日志。',
  'core.api.requestFailed': '请求失败',
  'core.api.missingModule': '缺少模块 ID。',
  'core.locale.label': '语言',
  'core.locale.switchAria': '当前语言为 {locale}，点击切换',
  'core.i18n.open': '字段文案',
  'core.i18n.openAria': '管理 {module} 的字段文案',
  'core.i18n.title': '模块文案',
  'core.i18n.subtitle': '{module} · {locale} · {count} 项',
  'core.i18n.empty': '此模块尚未注册可编辑文案。',
  'core.i18n.search': '搜索键或文本',
  'core.i18n.key': '文案键',
  'core.i18n.value': '文本',
  'core.i18n.valueForKey': '{key} 的文案',
  'core.i18n.deleteKey': '删除 {key}',
  'core.i18n.addKey': '新文案键',
  'core.i18n.addValue': '新文本',
  'core.i18n.add': '添加文案',
  'core.i18n.delete': '删除',
  'core.i18n.reset': '重置本地草稿',
  'core.i18n.close': '关闭',
  'core.i18n.saved': '已保存模块文案到本地覆盖层。',
  'core.i18n.noLocales': '没有可切换语言',
  'core.i18n.storageHint': '保存会写入浏览器本地覆盖层，刷新后仍会覆盖插件默认文案。',
  'core.extension.loading': '正在加载扩展',
  'core.extension.loadingDesc': '正在加载插件扩展与 Web Console 宿主桥接。',
  'core.extension.loadingShort': '加载中…',
  'core.extension.ok': '扩展已就绪',
  'core.extension.loaded': '已加载 {count} 个扩展',
  'core.extension.failed': '扩展加载异常',
  'core.extension.failedDesc': '{count} 个扩展加载失败。',
  'core.extension.failedList': '加载失败的扩展',
  'core.extension.failedFallback': '没有可用的扩展状态。',
  'core.config.emptyListTitle': '空列表',
  'core.config.emptyListHint': '展开后可直接添加第一项，列表会在这里过渡成可编辑条目。',
  'core.list.itemAria': '第 {index} 项',
  'core.list.numberAria': '数值 {index}',
  'core.kv.aria': '键值对列表',
  'core.kv.key': '键',
  'core.kv.value': '值',
  'core.kv.add': '添加键值',
  'core.kv.delete': '删除第 {index} 项',
  'core.item.previewStatus.syncing': '同步预览中',
  'core.item.previewStatus.live': '预览已同步',
  'core.item.previewStatus.failed': '未生成当前预览',
  'core.item.preview.levelTitle': '等级预览',
  'core.item.preview.levelHint': '切换等级会用当前草稿重新生成名称与 Lore，过期响应会被忽略。',
  'core.item.preview.original': '原始名称与 Lore',
  'core.item.preview.result': '动作后结果',
  'core.item.preview.resultForLevel': 'Lv.{level} 动作后',
  'core.item.preview.emptyLore': '没有原始 Lore',
  'core.item.preview.syncing': '正在同步当前等级预览…',
  'core.item.preview.emptyResult': '没有当前等级结果',
  'core.item.preview.debugAria': '变量与动作预览',
  'core.item.preview.variables': '变量解析',
  'core.item.preview.nameSteps': '名称动作链',
  'core.item.preview.loreSteps': 'Lore 动作链',
  'core.item.preview.moreVariables': '+{count} 个变量',
  'core.item.preview.moreSteps': '+{count} 个步骤',
  'core.item.preview.executed': '已执行',
  'core.item.preview.lineChange': '{before} → {after} 行',
  'core.item.preview.writeLines': '，写入 {count} 行',
  'core.item.preview.anchor': '，锚点 {anchor}',
  'core.item.preview.upgradeLevel': '当前 Lv.{level}',
  'core.item.setPieces.add': '添加套装部件',
  'core.item.setThresholds.add': '添加阈值',
  'core.item.attributeModifiers.add': '添加属性修饰符',
  'core.item.repairMaterials.add': '添加修复材料'
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
  try { localStorage.setItem('emaki-locale', currentLocale); } catch { }
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
  try { localStorage.setItem(`${STORAGE_PREFIX}${locale}`, JSON.stringify(messages)); } catch { }
}

function loadPersistedLocales(): void {
  try {
    const storageKeys = Array.from({ length: localStorage.length }, (_, index) => localStorage.key(index)).filter((key): key is string => Boolean(key?.startsWith(STORAGE_PREFIX)));
    for (const key of storageKeys) {
      const locale = key.substring(STORAGE_PREFIX.length);
      const raw = localStorage.getItem(key);
      if (!raw) continue;
      const parsed = JSON.parse(raw) as LocaleMessages;
      const filteredEntries = Object.entries(parsed).filter(([messageKey]) => !isLockedCoreCopyKey(messageKey));
      const filtered = Object.fromEntries(filteredEntries);
      if (filteredEntries.length !== Object.keys(parsed).length) {
        if (filteredEntries.length === 0) localStorage.removeItem(key);
        else localStorage.setItem(key, JSON.stringify(filtered));
      }
      if (filteredEntries.length > 0) {
        stores.set(locale, { ...(stores.get(locale) ?? {}), ...filtered });
      }
    }
  } catch { }
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

function isLockedCoreCopyKey(key: string): boolean {
  return key.startsWith('core.brand.')
    || key === 'core.login.kicker'
    || key === 'core.login.title'
    || key === 'core.login.description'
    || key === 'core.stage.defaultTitle';
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
  } catch { }
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
