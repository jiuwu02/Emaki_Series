import { registerPluginGuiEditor } from 'emaki-web-console';

registerPluginGuiEditor({
  moduleId: 'EmakiStrengthen',
  editorId: 'emakistrengthen:gui',
  label: '强化 GUI',
  fields: [
    ['type', '槽位类型', '强化业务槽位语义。', 'text'],
    ['target_item', '目标物品', '放入待强化物品的槽位。', 'text'],
    ['material', '强化材料', '放入强化材料的槽位。', 'text'],
    ['confirm', '确认强化', '执行强化操作按钮。', 'text']
  ]
});
