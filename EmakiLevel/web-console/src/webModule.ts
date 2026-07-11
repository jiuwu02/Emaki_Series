import { defineCapabilities, defineEmakiPluginWebModule, defineGuiEditor, defineLocales, definePreview, localeText } from 'emaki-web-console';
import { LevelCurvePreview, dynamicFields, installLevelCurvePreviewStyles, mainConfigFields, sourceFields, typeFields } from './curve/LevelCurvePreview';

const MODULE = 'EmakiLevel';
const copy = localeText;

export const emakiLevelWebModule = (() => {
  installLevelCurvePreviewStyles();

  return defineEmakiPluginWebModule({
    module: { id: MODULE, displayName: 'Level', summaryKey: 'emakilevel.module.summary', icon: 'level', tone: 'level' },
    files: [
      { id: 'config', path: 'config.yml', kind: 'CONFIG', titleKey: 'emakilevel.file.config.title', commentKey: 'emakilevel.file.config.comment' },
      { id: 'requirements', path: 'requirements.yml', kind: 'CONFIG', titleKey: 'emakilevel.file.requirements.title', commentKey: 'emakilevel.file.requirements.comment' },
      { id: 'types', path: 'types/**/*.yml', kind: 'CONFIG', titleKey: 'emakilevel.file.types.title', commentKey: 'emakilevel.file.types.comment' },
      { id: 'sources', path: 'sources/**/*.yml', kind: 'CONFIG', titleKey: 'emakilevel.file.sources.title', commentKey: 'emakilevel.file.sources.comment' },
      { id: 'gui', path: 'gui/**/*.yml', kind: 'GUI', editorId: 'emakilevel:gui', titleKey: 'emakilevel.file.gui.title', commentKey: 'emakilevel.file.gui.comment' }
    ],
    insightDefinitions: [{ pathPrefix: 'types/', idType: 'level_type', idPath: 'id' }],
    config: {
      metaFields: mainConfigFields,
      fileSchemas: [
        { pathPrefix: 'types/', fields: typeFields },
        { pathPrefix: 'sources/', fields: sourceFields }
      ],
      ruleFields: dynamicFields,
      createTemplates: [
        ['sources', {
          id: 'source-rule',
          label: copy('经验来源', 'Experience source'),
          fields: [
            { path: 'enabled', label: copy('启用', 'Enabled'), comment: copy('是否启用该来源。', 'Whether this source is enabled.'), type: 'boolean', defaultValue: true },
            { path: 'type', label: copy('等级类型', 'Level type'), comment: copy('经验写入的等级类型 ID。', 'Target level type id.'), type: 'text', defaultValue: 'main' },
            { path: 'trigger', label: copy('触发器', 'Trigger'), comment: copy('经验来源触发器。', 'Experience source trigger.'), type: 'text', defaultValue: 'entity_kill' },
            { path: 'rules', label: copy('规则', 'Rules'), comment: copy('匹配规则列表。', 'Match rule list.'), type: 'objectList', defaultValue: [] }
          ]
        }]
      ]
    },
    previews: [
      definePreview({ kind: 'CONFIG', pathPattern: 'requirements.yml', component: LevelCurvePreview, label: copy('等级曲线', 'Level curve'), priority: 20 }),
      definePreview({ kind: 'CONFIG', pathPattern: 'types/**/*.yml', component: LevelCurvePreview, label: copy('等级曲线', 'Level curve'), priority: 20 })
    ],
    guiEditors: [defineGuiEditor({
      editorId: 'emakilevel:gui',
      label: copy('等级 GUI', 'Level GUI'),
      fields: [
        ['slots', copy('槽位', 'Slots'), copy('GUI 中所有可渲染槽位配置。', 'Renderable slots in this GUI.'), 'object'],
        ['type', copy('槽位类型', 'Slot type'), copy('等级 GUI 槽位语义，可选预设或自定义。', 'Level GUI slot type; preset or custom values are allowed.'), 'enum', { options: ['filler', 'level_type', 'type_info', 'progress', 'levelup', 'next_page', 'previous_page', 'close'], optionLabelPrefix: 'slotType' }],
        ['item', copy('物品来源', 'Item source'), copy('CoreLib ItemSource 字符串。', 'CoreLib ItemSource string.'), 'text'],
        ['display_name', copy('显示名称', 'Display name'), copy('槽位显示名称。', 'Slot display name.'), 'text'],
        ['lore', copy('Lore', 'Lore'), copy('槽位 Lore。', 'Slot lore.'), 'stringList']
      ]
    })],
    pluginApiRoutes: [{ id: 'curve', route: 'level/curve', readonly: true, description: 'Level curve preview API.' }],
    locales: [
      defineLocales('zh-CN', {
        'emakilevel.module.name': 'Level',
        'emakilevel.module.summary': '多等级类型、经验来源与成长配置',
        'emakilevel.file.config.title': '主配置',
        'emakilevel.file.config.comment': '等级系统主配置，包含语言、PDC、属性桥接、防刷和 MythicMobs 接入。',
        'emakilevel.file.requirements.title': '升级需求',
        'emakilevel.file.requirements.comment': '全局、分组与类型级经验需求公式。',
        'emakilevel.file.types.title': '等级类型',
        'emakilevel.file.types.comment': '主等级、战斗、挖掘、烹饪、锻造等等级类型。',
        'emakilevel.file.sources.title': '经验来源',
        'emakilevel.file.sources.comment': 'Bukkit 与 MythicMobs 事件来源的经验规则。',
        'emakilevel.file.gui.title': '等级 GUI',
        'emakilevel.file.gui.comment': '等级 GUI 模板配置。',
        'emakilevel.field.id': 'ID',
        'emakilevel.field.enabled': '启用',
        'emakilevel.field.display_name': '显示名称',
        'emakilevel.field.description': '描述',
        'emakilevel.field.primary': '主等级',
        'emakilevel.field.start_level': '起始等级',
        'emakilevel.field.max_level': '最高等级',
        'emakilevel.field.requirement': '升级需求',
        'emakilevel.field.upgrade': '升级配置',
        'emakilevel.field.actions': '动作',
        'emakilevel.field.attributes': '属性贡献',
        'emakilevel.field.sources': '来源规则',
        'emakilevel.field.rules': '匹配规则',
        'emakilevel.field.exp_formula': '经验公式',
        'emakilevel.field.type': '等级类型',
        'emakilevel.option.trigger.entity_kill': '实体击杀',
        'emakilevel.option.trigger.mythic_mob_kill': 'Mythic 击杀',
        'emakilevel.option.trigger.block_break': '方块破坏',
        'emakilevel.option.trigger.crop_harvest': '作物收获',
        'emakilevel.option.trigger.player_fish': '钓鱼',
        'emakilevel.option.trigger.craft_item': '合成',
        'emakilevel.option.trigger.brew_complete': '炼药完成',
        'emakilevel.option.trigger.furnace_extract': '冶炼提取',
        'emakilevel.option.trigger.entity_tame': '驯养'
      }),
      defineLocales('en-US', {
        'emakilevel.module.name': 'Level',
        'emakilevel.module.summary': 'Level types, experience sources, and progression configuration',
        'emakilevel.file.config.title': 'Main config',
        'emakilevel.file.config.comment': 'Main level system config: language, PDC, attribute bridge, anti-abuse, and MythicMobs integration.',
        'emakilevel.file.requirements.title': 'Requirements',
        'emakilevel.file.requirements.comment': 'Global, group, and type-specific experience requirements.',
        'emakilevel.file.types.title': 'Level types',
        'emakilevel.file.types.comment': 'Main, combat, mining, cooking, forging, and other level types.',
        'emakilevel.file.sources.title': 'Experience sources',
        'emakilevel.file.sources.comment': 'Experience rules for Bukkit and MythicMobs events.',
        'emakilevel.file.gui.title': 'Level GUI',
        'emakilevel.file.gui.comment': 'Level GUI template configuration.',
        'emakilevel.field.id': 'ID',
        'emakilevel.field.enabled': 'Enabled',
        'emakilevel.field.display_name': 'Display name',
        'emakilevel.field.description': 'Description',
        'emakilevel.field.primary': 'Primary',
        'emakilevel.field.start_level': 'Start level',
        'emakilevel.field.max_level': 'Max level',
        'emakilevel.field.requirement': 'Requirement',
        'emakilevel.field.upgrade': 'Upgrade',
        'emakilevel.field.actions': 'Actions',
        'emakilevel.field.attributes': 'Attribute contributions',
        'emakilevel.field.sources': 'Source rules',
        'emakilevel.field.rules': 'Match rules',
        'emakilevel.field.exp_formula': 'Exp formula',
        'emakilevel.field.type': 'Level type',
        'emakilevel.option.trigger.entity_kill': 'Entity kill',
        'emakilevel.option.trigger.mythic_mob_kill': 'Mythic kill',
        'emakilevel.option.trigger.block_break': 'Block break',
        'emakilevel.option.trigger.crop_harvest': 'Crop harvest',
        'emakilevel.option.trigger.player_fish': 'Fishing',
        'emakilevel.option.trigger.craft_item': 'Craft item',
        'emakilevel.option.trigger.brew_complete': 'Brew complete',
        'emakilevel.option.trigger.furnace_extract': 'Furnace extract',
        'emakilevel.option.trigger.entity_tame': 'Taming'
      })
    ],
    capabilities: defineCapabilities(['config', 'gui', 'preview', 'insight', 'pluginApi', 'diagnostics']),
    diagnostics: [
      { id: 'emakilevel.manifest-v2', description: 'Level registers config, GUI, previews, insight definitions, and plugin API routes through Manifest v2.', severity: 'info' },
      { id: 'emakilevel.curve-preview', description: 'Level requirements and type files keep the curve preview wired through Manifest v2.', severity: 'info' }
    ]
  });
})();

export default emakiLevelWebModule;
