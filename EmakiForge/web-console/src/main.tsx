import { registerGuiEditorDescriptor, registerPluginGuiSurface } from 'emaki-web-console';

registerPluginGuiSurface('EmakiForge', 'emakiforge:gui', '锻造 GUI');
registerGuiEditorDescriptor('EmakiForge', 'emakiforge:gui', {
  id: 'emakiforge:gui',
  moduleId: 'EmakiForge',
  title: '锻造 GUI',
  kindLabel: '锻造 GUI',
  fields: guiFields([
    ['id', 'ID', 'GUI 模板唯一标识。', 'text'],
    ['gui_type', 'GUI 类型', 'Bukkit InventoryType。只有 CHEST 支持行数。', 'enum'],
    ['title', '标题', 'GUI 窗口标题，支持 MiniMessage。', 'text'],
    ['rows', '箱子行数', '仅 CHEST 类型可用，范围 1-6。', 'number'],
    ['type', '槽位类型', '锻造业务槽位语义。', 'text'],
    ['slots', '槽位', '槽位索引列表。', 'list'],
    ['item', '物品', '槽位显示物品。', 'text'],
    ['display_name', '显示名', '槽位物品显示名称。', 'text'],
    ['lore', 'Lore', '槽位物品描述。', 'stringList'],
    ['hidden_components', '隐藏组件', '隐藏原版组件。', 'stringList'],
    ['sounds', '声音', '槽位交互声音。', 'object'],
    ['blueprint_inputs', '图纸输入', '放入锻造图纸的槽位。', 'text'],
    ['required_materials', '必需材料', '配方必需材料槽位。', 'text'],
    ['optional_materials', '可选材料', '可选加成材料槽位。', 'text'],
    ['confirm', '确认锻造', '执行锻造操作按钮。', 'text']
  ])
});

function guiFields(entries: Array<[string, string, string, string]>) {
  return Object.fromEntries(entries.map(([path, label, comment, type]) => [path, { path, label, comment, type }]));
}
