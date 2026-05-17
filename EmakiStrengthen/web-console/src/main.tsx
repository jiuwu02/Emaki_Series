import { registerGuiEditorDescriptor, registerPluginGuiSurface } from 'emaki-web-console';

registerPluginGuiSurface('EmakiStrengthen', 'emakistrengthen:gui', '强化 GUI');
registerGuiEditorDescriptor('EmakiStrengthen', 'emakistrengthen:gui', {
  id: 'emakistrengthen:gui',
  moduleId: 'EmakiStrengthen',
  title: '强化 GUI',
  kindLabel: '强化 GUI',
  fields: guiFields([
    ['id', 'ID', 'GUI 模板唯一标识。', 'text'],
    ['gui_type', 'GUI 类型', 'Bukkit InventoryType。只有 CHEST 支持行数。', 'enum'],
    ['title', '标题', 'GUI 窗口标题，支持 MiniMessage。', 'text'],
    ['rows', '箱子行数', '仅 CHEST 类型可用，范围 1-6。', 'number'],
    ['type', '槽位类型', '强化业务槽位语义。', 'text'],
    ['slots', '槽位', '槽位索引列表。', 'list'],
    ['item', '物品', '槽位显示物品。', 'text'],
    ['display_name', '显示名', '槽位物品显示名称。', 'text'],
    ['lore', 'Lore', '槽位物品描述。', 'stringList'],
    ['target_item', '目标物品', '放入待强化物品的槽位。', 'text'],
    ['material', '强化材料', '放入强化材料的槽位。', 'text'],
    ['confirm', '确认强化', '执行强化操作按钮。', 'text']
  ])
});

function guiFields(entries: Array<[string, string, string, string]>) {
  return Object.fromEntries(entries.map(([path, label, comment, type]) => [path, { path, label, comment, type }]));
}
