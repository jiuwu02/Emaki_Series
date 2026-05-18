import { registerPluginGuiEditor } from 'emaki-web-console';

registerPluginGuiEditor({
  moduleId: 'EmakiForge',
  editorId: 'emakiforge:gui',
  label: '锻造 GUI',
  fields: [
    ['type', '槽位类型', '锻造业务槽位语义。', 'text'],
    ['hidden_components', '隐藏组件', '隐藏原版组件。', 'stringList'],
    ['sounds', '声音', '槽位交互声音。', 'object'],
    ['blueprint_inputs', '图纸输入', '放入锻造图纸的槽位。', 'text'],
    ['required_materials', '必需材料', '配方必需材料槽位。', 'text'],
    ['optional_materials', '可选材料', '可选加成材料槽位。', 'text'],
    ['confirm', '确认锻造', '执行锻造操作按钮。', 'text']
  ]
});
