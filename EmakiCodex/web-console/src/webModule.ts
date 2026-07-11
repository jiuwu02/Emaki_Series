import { defineCapabilities, defineEmakiPluginWebModule, defineLocales, definePreview } from 'emaki-web-console';
import { AdvancementPreview } from './AdvancementPreview';
import { enUS } from './locales/en-US';
import { zhCN } from './locales/zh-CN';
import {
  advancementCreateTemplateFields,
  advancementManifestSchema,
  conditionGroupFields,
  triggerEntryFields
} from './schema/advancementSchema';
import { codexConfigManifestSchema } from './schema/configSchema';
import { copy } from './webModuleCopy';

const MODULE_ID = 'EmakiCodex';

export const emakiCodexWebModule = defineEmakiPluginWebModule({
  module: {
    id: MODULE_ID,
    displayName: 'Codex',
    summaryKey: 'emakicodex.module.summary',
    icon: 'book',
    tone: 'codex'
  },
  files: [
    { id: 'config', path: 'config.yml', kind: 'CONFIG', titleKey: 'emakicodex.file.config.title', commentKey: 'emakicodex.file.config.comment' },
    { id: 'advancements', path: 'advancements/**/*.yml', kind: 'CONFIG', titleKey: 'emakicodex.file.advancements.title', commentKey: 'emakicodex.file.advancements.comment' },
    { id: 'lang', path: 'lang/**/*.yml', kind: 'CONFIG', titleKey: 'emakicodex.file.lang.title', commentKey: 'emakicodex.file.lang.comment' }
  ],
  schemas: [advancementManifestSchema],
  config: {
    metaFields: codexConfigManifestSchema.fields,
    createTemplates: [['advancements', {
      id: 'advancement-node',
      label: copy('成就节点', 'Advancement node'),
      fields: advancementCreateTemplateFields
    }]],
    rules: [
      [{ suffix: '.actions.complete' }, { label: copy('完成动作', 'Completion actions'), comment: copy('节点完成时执行的 CoreLib Action 字符串列表。', 'CoreLib action command strings executed on completion.'), type: 'stringList' }],
      [{ suffix: '.triggers.entries' }, { label: copy('触发器列表', 'Trigger list'), comment: copy('任一触发器匹配即授予该节点。', 'Grant the node when any trigger matches.'), type: 'objectList' }],
      [{ suffix: '.condition' }, { label: copy('条件', 'Condition'), comment: copy('CoreLib 条件组。', 'CoreLib condition group.'), type: 'object', itemFields: conditionGroupFields }],
      [{ suffix: '.condition.entries' }, { label: copy('条件表达式', 'Condition expressions'), comment: copy('CoreLib 条件组表达式列表。', 'CoreLib condition group expression list.'), type: 'stringList' }],
      [{ key: 'frame' }, { label: copy('边框', 'Frame'), type: 'enum', options: ['task', 'goal', 'challenge'] }],
      [{ key: 'icon' }, { label: copy('图标', 'Icon'), type: 'text' }],
      [{ key: 'description' }, { label: copy('描述', 'Description'), type: 'textarea' }],
      [{ key: 'parent' }, { label: copy('父节点', 'Parent'), type: 'text' }],
      [{ key: 'toast' }, { label: copy('显示 Toast', 'Show toast'), type: 'boolean' }],
      [{ key: 'announce' }, { label: copy('全服广播', 'Announce'), type: 'boolean' }],
      [{ key: 'hidden' }, { label: copy('隐藏', 'Hidden'), type: 'boolean' }]
    ],
    listItemSchemaRules: [[{ suffix: '.triggers.entries' }, triggerEntryFields]]
  },
  previews: [definePreview({ kind: 'CONFIG', pathPrefix: 'advancements/', component: AdvancementPreview, label: copy('原版成就预览', 'Vanilla advancement preview'), priority: 20 })],
  insightDefinitions: [{ pathPrefix: 'advancements/', idType: 'advancement_page', idPath: 'page_id' }],
  locales: [defineLocales('zh-CN', zhCN), defineLocales('en-US', enUS)],
  capabilities: defineCapabilities(['config', 'preview', 'insight', 'diagnostics']),
  diagnostics: [
    { id: 'emakicodex.manifest-v2', description: 'Codex registers through Manifest v2.', severity: 'info' },
    { id: 'emakicodex.advancement-schema-ast', description: 'Advancement pages are declared from Schema AST.', severity: 'info' }
  ]
});

export default emakiCodexWebModule;
