import { registerPluginGuiEditor } from 'emaki-web-console';

registerPluginGuiEditor({
  moduleId: 'EmakiCooking',
  editorId: 'emakicooking:gui',
  label: '烹饪 GUI',
  fields: [
    ['type', '槽位类型', '烹饪工位槽位语义。', 'text'],
    ['ingredient', '原料槽', '放入烹饪原料的槽位。', 'text'],
    ['result', '产物槽', '展示或取出产物的槽位。', 'text'],
    ['fuel', '燃料槽', '放入燃料的槽位。', 'text']
  ]
});
