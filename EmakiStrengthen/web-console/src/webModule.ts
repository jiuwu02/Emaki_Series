import { defineCapabilities, defineEmakiPluginWebModule, defineGuiEditor, defineLocales, definePreview, localeText } from 'emaki-web-console';
import { StrengthenRoutePreview } from './routePreview/StrengthenRoutePreview';
import { enUS } from './locales/en-US';
import { zhCN } from './locales/zh-CN';
import { strengthenBranchTreeManifestSchema, strengthenEffectTypes, strengthenPluginConfig } from './schema/strengthenSchema';

const MODULE_ID = 'EmakiStrengthen';
const copy = localeText;

export const emakiStrengthenWebModule = defineEmakiPluginWebModule({
  module: { id: MODULE_ID, displayName: 'Strengthen', summaryKey: 'emakistrengthen.module.summary', icon: 'strengthen', tone: 'strengthen' },
  files: [
    { id: 'config', path: 'config.yml', kind: 'CONFIG', titleKey: 'emakistrengthen.file.config.title', commentKey: 'emakistrengthen.file.config.comment' },
    { id: 'recipes', path: 'recipes/**/*.yml', kind: 'CONFIG', titleKey: 'emakistrengthen.file.recipes.title', commentKey: 'emakistrengthen.file.recipes.comment' },
    { id: 'gui', path: 'gui/**/*.yml', kind: 'GUI', editorId: 'emakistrengthen:gui', titleKey: 'emakistrengthen.file.gui.title', commentKey: 'emakistrengthen.file.gui.comment' }
  ],
  schemas: [strengthenBranchTreeManifestSchema],
  config: strengthenPluginConfig,
  effectTypes: strengthenEffectTypes,
  previews: [definePreview({ kind: 'CONFIG', pathPattern: 'recipes/**/*.yml', component: StrengthenRoutePreview, label: copy('强化路线蓝图', 'Strengthen route blueprint'), priority: 20 })],
  guiEditors: [defineGuiEditor({
    editorId: 'emakistrengthen:gui',
    label: copy('强化 GUI', 'Strengthen GUI'),
    fields: [
      ['type', '槽位类型', '强化业务槽位语义。可选预设值，材料输入槽可用 material_input_0/1/2… 自定义。', 'enum', { options: ['target_item', 'preview_display', 'temper_display', 'confirm', 'material_input_0', 'material_input_1', 'material_input_2'], optionLabelPrefix: 'slotType' }],
      ['target_item', '目标物品', '放入待强化物品的槽位。', 'text'],
      ['material', '强化材料', '放入强化材料的槽位。', 'text'],
      ['success_preview', '成功率预览', '显示当前强化成功率与目标星级的槽位。', 'text'],
      ['confirm', '确认按钮', '执行强化操作的按钮槽位。', 'text']
    ]
  })],
  insightDefinitions: [{ pathPrefix: 'recipes/', idType: 'strengthen_recipe', idPath: 'id' }],
  pluginApiRoutes: [{ id: 'route-preview', route: 'strengthen/route-preview', readonly: true, description: 'Strengthen route preview API.' }],
  locales: [defineLocales('zh-CN', zhCN), defineLocales('en-US', enUS)],
  capabilities: defineCapabilities(['config', 'gui', 'preview', 'insight', 'pluginApi', 'diagnostics']),
  diagnostics: [
    { id: 'emakistrengthen.manifest-v2', description: 'Strengthen registers config, preview, and GUI editors through Manifest v2.', severity: 'info' },
    { id: 'emakistrengthen.branch-tree-schema', description: 'Strengthen branch tree, stars, materials, and economy metadata are represented in Schema AST metadata.', severity: 'info' }
  ]
});

export default emakiStrengthenWebModule;
