import {
  actionStringListField,
  booleanField,
  conditionGroupField,
  defineConfigSchema,
  defineSchemaAst,
  enumField,
  fieldToConfigField,
  itemSourceField,
  numberField,
  objectField,
  objectListField,
  objectMapField,
  textField,
  type EmakiField,
  type WebConfigFieldSchema
} from 'emaki-web-console';
import { copy } from '../webModuleCopy';

const MODULE_ID = 'EmakiCodex';

export const triggerEvents = ['entity_kill', 'mythic_mob_kill', 'block_break', 'crop_harvest', 'craft_item', 'furnace_extract', 'player_fish', 'brew_complete', 'entity_tame'];

export const conditionGroupFields: WebConfigFieldSchema[] = [
  { path: 'type', label: copy('类型', 'Type'), comment: copy('条件组匹配方式。', 'Condition group matching mode.'), type: 'enum', options: ['all_of', 'any_of', 'none_of', 'at_least', 'exactly'], defaultValue: 'all_of' },
  { path: 'required_count', label: copy('需要数量', 'Required count'), comment: copy('at_least / exactly 条件组需要满足的条件数量。', 'Required successful condition count for at_least / exactly groups.'), type: 'number', defaultValue: 0 },
  { path: 'entries', label: copy('表达式', 'Expressions'), comment: copy('CoreLib 条件表达式列表。', 'CoreLib condition expressions.'), type: 'stringList', defaultValue: [] }
];

const triggerEntrySchemaFields = [
  enumField({ path: 'event', label: copy('事件', 'Event'), comment: copy('触发该节点的游戏事件。', 'Game event that triggers this node.'), options: triggerEvents, defaultValue: 'entity_kill' }),
  conditionGroupField({ path: 'condition', label: copy('条件', 'Condition'), comment: copy('可选 CoreLib 条件组。', 'Optional CoreLib condition group.'), defaultValue: { type: 'all_of', entries: [] } })
] satisfies EmakiField[];

export const triggerEntryFields: WebConfigFieldSchema[] = [
  fieldToConfigField(triggerEntrySchemaFields[0])[0],
  { ...fieldToConfigField(triggerEntrySchemaFields[1])[0], itemFields: conditionGroupFields }
];

export const advancementNodeFields = [
  itemSourceField({ path: 'icon', label: copy('图标', 'Icon'), comment: copy('CoreLib ItemSource 字符串。', 'CoreLib ItemSource string.'), defaultValue: 'minecraft-book' }),
  textField({ path: 'title', label: copy('标题', 'Title'), comment: copy('成就标题，支持 MiniMessage。', 'Advancement title with MiniMessage support.'), defaultValue: '<gold>新成就</gold>' }),
  textField({ path: 'description', label: copy('描述', 'Description'), comment: copy('成就描述，支持 MiniMessage。', 'Advancement description with MiniMessage support.'), multiline: true, defaultValue: '<gray>描述</gray>' }),
  enumField({ path: 'frame', label: copy('边框', 'Frame'), comment: copy('原版成就边框类型。', 'Vanilla advancement frame type.'), options: ['task', 'goal', 'challenge'], defaultValue: 'task' }),
  textField({ path: 'parent', label: copy('父节点', 'Parent'), comment: copy('父成就的本地 ID；根节点留空。', 'Local parent advancement id; leave empty for the root.'), defaultValue: '' }),
  numberField({ path: 'x', label: 'X', comment: copy('PacketEvents 坐标横轴。', 'PacketEvents horizontal coordinate.'), defaultValue: 0 }),
  numberField({ path: 'y', label: 'Y', comment: copy('PacketEvents 坐标纵轴。', 'PacketEvents vertical coordinate.'), defaultValue: 0 }),
  booleanField({ path: 'toast', label: copy('显示 Toast', 'Show toast'), comment: copy('完成时是否显示客户端 Toast。', 'Whether to show a client toast on completion.'), defaultValue: true }),
  booleanField({ path: 'announce', label: copy('全服广播', 'Announce'), comment: copy('完成时是否全服广播。', 'Whether completion is announced globally.'), defaultValue: false }),
  booleanField({ path: 'hidden', label: copy('隐藏', 'Hidden'), comment: copy('未完成前是否隐藏节点。', 'Whether to hide the node until completed.'), defaultValue: false }),
  objectField({
    path: 'actions',
    label: copy('完成动作', 'Completion actions'),
    comment: copy('节点完成时执行的 CoreLib Actions。', 'CoreLib Actions executed when the node completes.'),
    fields: [
      actionStringListField({ path: 'complete', label: copy('完成动作', 'Completion actions'), comment: copy('节点完成时执行的 CoreLib Action 字符串列表。', 'CoreLib action command strings executed on completion.'), defaultValue: [] })
    ],
    defaultValue: { complete: [] }
  }),
  objectField({
    path: 'triggers',
    label: copy('事件触发器', 'Event triggers'),
    comment: copy('满足任一触发器时自动授予。', 'Automatically grant when any trigger matches.'),
    fields: [
      objectListField({
        path: 'entries',
        label: copy('触发器列表', 'Trigger list'),
        comment: copy('任一触发器匹配即授予该节点。', 'Grant the node when any trigger matches.'),
        itemFields: triggerEntrySchemaFields,
        defaultValue: []
      })
    ],
    defaultValue: { entries: [] }
  })
] satisfies EmakiField[];

export const advancementCreateTemplateFields = advancementNodeFields.flatMap(fieldToConfigField) satisfies WebConfigFieldSchema[];

export const advancementSchema = defineSchemaAst({
  id: 'emakicodex-advancement-page',
  moduleId: MODULE_ID,
  pathPrefix: 'advancements/',
  fields: [
    textField({ path: 'page_id', label: copy('页面 ID', 'Page id'), comment: copy('成就 key 的路径前缀。', 'Path prefix used by advancement keys.') }),
    textField({ path: 'title', label: copy('标签页标题', 'Tab title'), comment: copy('原版成就标签页标题，支持 MiniMessage。', 'Vanilla advancement tab title with MiniMessage support.') }),
    textField({ path: 'background', label: copy('背景', 'Background'), comment: copy('资源包中的成就背景纹理路径。', 'Advancement background texture path.') }),
    textField({ path: 'root', label: copy('根节点', 'Root node'), comment: copy('根成就的本地 ID。', 'Local id of the root advancement.') }),
    objectMapField({
      path: 'advancements',
      label: copy('成就节点', 'Advancement nodes'),
      comment: copy('以本地 ID 为键的成就节点。', 'Advancement nodes keyed by local id.'),
      valueFields: advancementNodeFields,
      creatableChildren: true
    })
  ]
});

export const advancementManifestSchema = defineConfigSchema(advancementSchema);
