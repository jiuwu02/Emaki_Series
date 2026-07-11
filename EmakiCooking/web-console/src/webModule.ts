import { defineCapabilities, defineEmakiPluginWebModule, defineGuiEditor, defineLocales, localeText } from 'emaki-web-console';
import { enUS } from './locales/en-US';
import { zhCN } from './locales/zh-CN';
import { cookingPluginConfig, cookingRecipeStructureManifestSchema } from './schema/cookingSchema';

const MODULE_ID = 'EmakiCooking';
const copy = localeText;

export const emakiCookingWebModule = defineEmakiPluginWebModule({
  module: { id: MODULE_ID, displayName: 'Cooking', summaryKey: 'emakicooking.module.summary', icon: 'cooking', tone: 'cooking' },
  files: [
    { id: 'config', path: 'config.yml', kind: 'CONFIG', titleKey: 'emakicooking.file.config.title', commentKey: 'emakicooking.file.config.comment' },
    { id: 'recipes', path: 'recipes/**/*.yml', kind: 'CONFIG', titleKey: 'emakicooking.file.recipes.title', commentKey: 'emakicooking.file.recipes.comment' },
    { id: 'item-adjustments', path: 'item_adjustments/**/*.yml', kind: 'CONFIG', titleKey: 'emakicooking.file.item_adjustments.title', commentKey: 'emakicooking.file.item_adjustments.comment' },
    { id: 'gui', path: 'gui/**/*.yml', kind: 'GUI', editorId: 'emakicooking:gui', titleKey: 'emakicooking.file.gui.title', commentKey: 'emakicooking.file.gui.comment' }
  ],
  schemas: [cookingRecipeStructureManifestSchema],
  config: cookingPluginConfig,
  guiEditors: [defineGuiEditor({
    editorId: 'emakicooking:gui',
    label: copy('烹饪 GUI', 'Cooking GUI'),
    fields: [
      ['ingredient', '原料槽', '放入烹饪原料的槽位。', 'text'],
      ['result', '产物槽', '展示或取出产物的槽位。', 'text'],
      ['fuel', '燃料槽', '放入燃料的槽位。', 'text'],
      ['moisture', '水分槽', '蒸锅水分输入或展示槽位。', 'text'],
      ['container', '容器槽', '榨汁机盛取容器槽位。', 'text'],
      ['progress', '进度槽', '显示烹饪、蒸制、烘烤或发酵进度的槽位。', 'text']
    ]
  })],
  locales: [defineLocales('zh-CN', zhCN), defineLocales('en-US', enUS)],
  capabilities: defineCapabilities(['config', 'gui', 'diagnostics']),
  diagnostics: [
    { id: 'emakicooking.manifest-v2', description: 'Cooking registers config and GUI editors through Manifest v2.', severity: 'info' },
    { id: 'emakicooking.recipe-schema', description: 'Cooking result branches, conditions, and outputs are represented in Schema AST metadata.', severity: 'info' }
  ]
});

export default emakiCookingWebModule;
