import { localeText, registerConfigPreview, registerInsightDefinition, registerModuleLocale, registerPluginConfig } from 'emaki-web-console';
import { AdvancementPreview, installAdvancementPreviewStyles } from './AdvancementPreview';

let registered = false;

export function registerEmakiCodexWebConsole(): void {
  if (registered) return;
  registered = true;

  const MODULE = 'EmakiCodex';
  const copy = localeText;
  installAdvancementPreviewStyles();
  const triggerEvents = ['entity_kill', 'mythic_mob_kill', 'block_break', 'crop_harvest', 'craft_item', 'furnace_extract', 'player_fish', 'brew_complete', 'entity_tame'];
  const advancementNodeFields = [
    { path: 'icon', label: copy('图标', 'Icon'), comment: copy('CoreLib ItemSource 字符串。', 'CoreLib ItemSource string.'), type: 'text', defaultValue: 'minecraft-book' },
    { path: 'title', label: copy('标题', 'Title'), comment: copy('成就标题，支持 MiniMessage。', 'Advancement title with MiniMessage support.'), type: 'text', defaultValue: '<gold>新成就</gold>' },
    { path: 'description', label: copy('描述', 'Description'), comment: copy('成就描述，支持 MiniMessage。', 'Advancement description with MiniMessage support.'), type: 'textarea', defaultValue: '<gray>描述</gray>' },
    { path: 'frame', label: copy('边框', 'Frame'), comment: copy('原版成就边框类型。', 'Vanilla advancement frame type.'), type: 'enum', options: ['task', 'goal', 'challenge'], defaultValue: 'task' },
    { path: 'parent', label: copy('父节点', 'Parent'), comment: copy('父成就的本地 ID；根节点留空。', 'Local parent advancement id; leave empty for the root.'), type: 'text', defaultValue: '' },
    { path: 'x', label: 'X', comment: copy('PacketEvents 坐标横轴。', 'PacketEvents horizontal coordinate.'), type: 'number', defaultValue: 0 },
    { path: 'y', label: 'Y', comment: copy('PacketEvents 坐标纵轴。', 'PacketEvents vertical coordinate.'), type: 'number', defaultValue: 0 },
    { path: 'toast', label: copy('显示 Toast', 'Show toast'), comment: copy('完成时是否显示客户端 Toast。', 'Whether to show a client toast on completion.'), type: 'boolean', defaultValue: true },
    { path: 'announce', label: copy('全服广播', 'Announce'), comment: copy('完成时是否全服广播。', 'Whether completion is announced globally.'), type: 'boolean', defaultValue: false },
    { path: 'hidden', label: copy('隐藏', 'Hidden'), comment: copy('未完成前是否隐藏节点。', 'Whether to hide the node until completed.'), type: 'boolean', defaultValue: false },
    { path: 'actions', label: copy('完成动作', 'Completion actions'), comment: copy('节点完成时执行的 CoreLib Actions。', 'CoreLib Actions executed when the node completes.'), type: 'object', defaultValue: { complete: [] } },
    { path: 'triggers', label: copy('事件触发器', 'Event triggers'), comment: copy('满足任一触发器时自动授予。', 'Automatically grant when any trigger matches.'), type: 'object', defaultValue: { entries: [] } }
  ];

  registerModuleLocale(MODULE, 'zh-CN', {
    'emakicodex.module.name': 'Codex',
    'emakicodex.module.summary': '原版成就页、节点、触发器与完成动作配置',
    'emakicodex.file.config.title': '主配置',
    'emakicodex.file.config.comment': '语言、默认数据、权限绕过与原版成就运行设置。',
    'emakicodex.file.advancements.title': '成就页',
    'emakicodex.file.advancements.comment': '每个 YAML 文件对应一个原版成就标签页。',
    'emakicodex.file.lang.title': '语言文件',
    'emakicodex.file.lang.comment': 'Codex 控制台、命令与运行时消息。'
  });

  registerModuleLocale(MODULE, 'en-US', {
    'emakicodex.module.name': 'Codex',
    'emakicodex.module.summary': 'Vanilla advancement pages, nodes, triggers, and completion actions',
    'emakicodex.file.config.title': 'Main config',
    'emakicodex.file.config.comment': 'Language, default data, permission bypass, and advancement runtime settings.',
    'emakicodex.file.advancements.title': 'Advancement pages',
    'emakicodex.file.advancements.comment': 'Each YAML file defines one vanilla advancement tab.',
    'emakicodex.file.lang.title': 'Language files',
    'emakicodex.file.lang.comment': 'Codex console, command, and runtime messages.'
  });

  registerInsightDefinition({ moduleId: MODULE, pathPrefix: 'advancements/', idType: 'advancement_page', idPath: 'page_id' });
  registerConfigPreview({ moduleId: MODULE, kind: 'CONFIG', pathPrefix: 'advancements/', component: AdvancementPreview, label: copy('原版成就预览', 'Vanilla advancement preview'), priority: 20 });
  registerPluginConfig({
    moduleId: MODULE,
    metaFields: [
      ['version', copy('版本', 'Version'), copy('由资源同步维护的配置版本。', 'Configuration version maintained by resource sync.'), 'text'],
      ['language', copy('语言', 'Language'), copy('使用的语言文件 ID。', 'Language bundle id.'), 'text'],
      ['release_default_data', copy('释放默认数据', 'Release default data'), copy('是否生成示例成就页。', 'Whether to generate the example advancement page.'), 'boolean'],
      ['op_bypass', copy('OP 绕过', 'OP bypass'), copy('OP 是否绕过命令权限。', 'Whether operators bypass command permissions.'), 'boolean'],
      ['advancement', copy('原版成就', 'Vanilla advancements'), copy('动态成就注册、发包坐标与事件触发设置。', 'Dynamic registration, packet coordinates, and event trigger settings.'), 'object'],
      ['advancement.enabled', copy('启用成就', 'Enable advancements'), copy('是否启用动态原版成就。', 'Whether dynamic vanilla advancements are enabled.'), 'boolean'],
      ['advancement.platform', copy('注册平台', 'Registration platform'), copy('当前使用 unsafe 动态注册平台。', 'Currently uses the unsafe dynamic registration platform.'), 'enum', { options: ['unsafe'] }],
      ['advancement.announce-default', copy('默认广播', 'Default announce'), copy('节点未单独配置时是否全服广播。', 'Default global announcement when a node does not override it.'), 'boolean'],
      ['advancement.remove-on-disable', copy('禁用时移除', 'Remove on disable'), copy('禁用或重载时移除动态成就。', 'Remove dynamic advancements on disable or reload.'), 'boolean'],
      ['advancement.packet-coordinates', copy('发包坐标', 'Packet coordinates'), copy('安装 PacketEvents 时使用节点 x/y 坐标。', 'Use node x/y coordinates when PacketEvents is installed.'), 'boolean'],
      ['advancement.triggers-enabled', copy('事件触发器', 'Event triggers'), copy('是否启用 triggers.entries 自动授予。', 'Whether triggers.entries can automatically grant advancements.'), 'boolean'],
      ['debug', 'Debug', copy('是否启用 Codex 调试输出。', 'Whether Codex debug output is enabled.'), 'boolean']
    ],
    fileSchemas: [{
      pathPrefix: 'advancements/',
      fields: [
        ['page_id', copy('页面 ID', 'Page id'), copy('成就 key 的路径前缀。', 'Path prefix used by advancement keys.'), 'text'],
        ['title', copy('标签页标题', 'Tab title'), copy('原版成就标签页标题，支持 MiniMessage。', 'Vanilla advancement tab title with MiniMessage support.'), 'text'],
        ['background', copy('背景', 'Background'), copy('资源包中的成就背景纹理路径。', 'Advancement background texture path.'), 'text'],
        ['root', copy('根节点', 'Root node'), copy('根成就的本地 ID。', 'Local id of the root advancement.'), 'text'],
        ['advancements', copy('成就节点', 'Advancement nodes'), copy('以本地 ID 为键的成就节点。', 'Advancement nodes keyed by local id.'), 'object', { creatableChildren: true }]
      ]
    }],
    rules: [
      [{ suffix: '.actions.complete' }, { label: copy('完成动作', 'Completion actions'), comment: copy('节点完成时执行的 CoreLib Actions。', 'CoreLib Actions executed on completion.'), type: 'actions' }],
      [{ suffix: '.triggers.entries' }, { label: copy('触发器列表', 'Trigger list'), comment: copy('任一触发器匹配即授予该节点。', 'Grant the node when any trigger matches.'), type: 'objectList' }],
      [{ suffix: '.condition.entries' }, { label: copy('条件表达式', 'Condition expressions'), comment: copy('CoreLib 条件组表达式列表。', 'CoreLib condition group expression list.'), type: 'stringList' }],
      [{ key: 'frame' }, { label: copy('边框', 'Frame'), type: 'enum', options: ['task', 'goal', 'challenge'] }],
      [{ key: 'icon' }, { label: copy('图标', 'Icon'), type: 'text' }],
      [{ key: 'description' }, { label: copy('描述', 'Description'), type: 'textarea' }],
      [{ key: 'parent' }, { label: copy('父节点', 'Parent'), type: 'text' }],
      [{ key: 'toast' }, { label: copy('显示 Toast', 'Show toast'), type: 'boolean' }],
      [{ key: 'announce' }, { label: copy('全服广播', 'Announce'), type: 'boolean' }],
      [{ key: 'hidden' }, { label: copy('隐藏', 'Hidden'), type: 'boolean' }]
    ],
    createTemplates: [['advancements', {
      id: 'advancement-node',
      label: copy('成就节点', 'Advancement node'),
      fields: advancementNodeFields
    }]],
    listItemSchemaRules: [[{ suffix: '.triggers.entries' }, [
      { path: 'event', label: copy('事件', 'Event'), comment: copy('触发该节点的游戏事件。', 'Game event that triggers this node.'), type: 'enum', options: triggerEvents, defaultValue: 'entity_kill' },
      { path: 'condition', label: copy('条件', 'Condition'), comment: copy('可选 CoreLib 条件组。', 'Optional CoreLib condition group.'), type: 'object', defaultValue: { type: 'all_of', entries: [] } }
    ]]]
  });
}
