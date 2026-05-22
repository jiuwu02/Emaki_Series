import { getLocale, registerConfigCreateTemplate, registerConfigNodeMeta, registerModuleLocale, registerPluginGuiEditor } from 'emaki-web-console';

const MODULE = 'EmakiStrengthen';

const copy = (zh: string, en: string) => getLocale().startsWith('zh') ? zh : en;

const fields = [
  ['local_broadcast_radius', '本地广播半径', '强化达到本地广播星级时，附近玩家可收到提示的半径，单位方块格。', 'number'],
  ['broadcast', '广播设置', '强化成功时的本地广播与全服广播触发星级设置。', 'object'],
  ['broadcast.local_stars', '本地广播星级', '强化成功达到这些星级时向附近玩家广播。', 'list'],
  ['broadcast.global_stars', '全服广播星级', '强化成功达到这些星级时向全服广播。', 'list'],
  ['success_rates', '全局成功率', '配方未单独覆盖时使用的全局强化成功率表，键为目标星级，值为百分比。', 'object']
] as const;

const localeMessages: Record<string, string> = Object.fromEntries([
  ['emakistrengthen.module.name', 'Strengthen'],
  ['emakistrengthen.module.summary', '星级、广播、成功率'],
  ['emakistrengthen.file.config.title', '主配置'],
  ['emakistrengthen.file.config.comment', '强化系统主配置，包含成功率、材料、经济和显示策略。'],
  ['emakistrengthen.file.gui.title', 'GUI 模板'],
  ['emakistrengthen.file.gui.comment', '强化界面 GUI 模板文件。'],
  ['emakistrengthen.file.recipes.title', '配方文件'],
  ['emakistrengthen.file.recipes.comment', '强化配方定义文件目录。'],
  ['emakistrengthen.filePath.recipes_example_recipe.title', '示例配方'],
  ['emakistrengthen.filePath.recipes_example_recipe.comment', '示例配方文件。'],
  ['emakistrengthen.filePath.recipes_example_branch_recipe.title', '示例分支配方'],
  ['emakistrengthen.filePath.recipes_example_branch_recipe.comment', '示例分支配方文件。'],
  ['emakistrengthen.filePath.gui_strengthen_gui.title', '强化 GUI'],
  ['emakistrengthen.filePath.gui_strengthen_gui.comment', '强化 GUI 模板文件。'],
  ['emakistrengthen.file.lang.title', '语言文件'],
  ['emakistrengthen.file.lang.comment', 'Strengthen 语言资源文件目录。'],
  ['emakistrengthen.file.plugin.title', '插件描述'],
  ['emakistrengthen.file.plugin.comment', 'plugin.yml 插件描述与依赖声明。'],
  ['emakistrengthen.file.web-console.title', 'Web Console 声明'],
  ['emakistrengthen.file.web-console.comment', 'Web Console 文件注册与资源入口声明。'],
  ...fields.flatMap(([path, label, comment]) => [
    [`emakistrengthen.field.${path}`, label],
    [`emakistrengthen.comment.${path}`, comment]
  ])
]);

registerModuleLocale(MODULE, 'zh-CN', {
  ...localeMessages,
  'emakistrengthen.surface.gui': '强化 GUI',
  'emakistrengthen.field.target_item': '目标物品',
  'emakistrengthen.field.material': '强化材料',
  'emakistrengthen.field.confirm': '确认按钮'
});

registerModuleLocale(MODULE, 'en-US', {
  'emakistrengthen.module.name': 'Strengthen',
  'emakistrengthen.module.summary': 'Stars, broadcasts, and success rates',
  'emakistrengthen.file.config.title': 'Main Config',
  'emakistrengthen.file.config.comment': 'Main strengthen configuration covering success rates, materials, economy, and display strategy.',
  'emakistrengthen.file.gui.title': 'GUI Templates',
  'emakistrengthen.file.gui.comment': 'Strengthen GUI template files.',
  'emakistrengthen.file.recipes.title': 'Recipe Files',
  'emakistrengthen.file.recipes.comment': 'Directory for strengthen recipe definition files.',
  'emakistrengthen.filePath.recipes_example_recipe.title': 'Sample Recipe',
  'emakistrengthen.filePath.recipes_example_recipe.comment': 'Sample recipe file.',
  'emakistrengthen.filePath.recipes_example_branch_recipe.title': 'Sample Branch Recipe',
  'emakistrengthen.filePath.recipes_example_branch_recipe.comment': 'Sample branch recipe file.',
  'emakistrengthen.filePath.gui_strengthen_gui.title': 'Strengthen GUI',
  'emakistrengthen.filePath.gui_strengthen_gui.comment': 'Strengthen GUI template file.',
  'emakistrengthen.file.lang.title': 'Language Files',
  'emakistrengthen.file.lang.comment': 'Directory for Strengthen language resources.',
  'emakistrengthen.file.plugin.title': 'Plugin Description',
  'emakistrengthen.file.plugin.comment': 'plugin.yml plugin metadata and dependency declaration.',
  'emakistrengthen.file.web-console.title': 'Web Console Declaration',
  'emakistrengthen.file.web-console.comment': 'Web Console file registration and resource entry declaration.',
  'emakistrengthen.surface.gui': 'Strengthen GUI',
  'emakistrengthen.field.local_broadcast_radius': 'Local Broadcast Radius',
  'emakistrengthen.field.broadcast': 'Broadcast',
  'emakistrengthen.field.broadcast.local_stars': 'Local Stars',
  'emakistrengthen.field.broadcast.global_stars': 'Global Stars',
  'emakistrengthen.field.success_rates': 'Success Rates',
  'emakistrengthen.field.target_item': 'Target Item',
  'emakistrengthen.field.material': 'Material',
  'emakistrengthen.field.confirm': 'Confirm'
});

fields.forEach(([path, label, comment, type]) => registerConfigNodeMeta(MODULE, path, {
  label,
  comment,
  type,
  ...(path === 'success_rates' ? { creatableChildren: true } : {})
}));

registerConfigCreateTemplate(MODULE, 'success_rates', {
  id: 'star-success-rate',
  label: copy('目标星级成功率', 'Target star success rate'),
  fields: [
    { path: 'value', label: '成功率', comment: '该目标星级的强化成功率百分比，例如 75.0。', type: 'number', defaultValue: 100 }
  ]
});

registerPluginGuiEditor({
  moduleId: MODULE,
  editorId: 'emakistrengthen:gui',
  label: copy('强化 GUI', 'Strengthen GUI'),
  fields: [
    ['type', '槽位类型', '强化业务槽位语义。', 'text'],
    ['target_item', '目标物品', '放入待强化物品的槽位。', 'text'],
    ['material', '强化材料', '放入强化材料的槽位。', 'text'],
    ['success_preview', '成功率预览', '显示当前强化成功率与目标星级的槽位。', 'text'],
    ['confirm', '确认按钮', '执行强化操作的按钮槽位。', 'text']
  ]
});
