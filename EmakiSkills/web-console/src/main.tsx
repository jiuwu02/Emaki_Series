import { registerGuiEditorDescriptor, registerPluginGuiSurface } from 'emaki-web-console';

registerPluginGuiSurface('EmakiSkills', 'emakiskills:gui', '技能 GUI');
registerGuiEditorDescriptor('EmakiSkills', 'emakiskills:gui', {
  id: 'emakiskills:gui',
  moduleId: 'EmakiSkills',
  title: '技能 GUI',
  kindLabel: '技能 GUI',
  fields: guiFields([
    ['id', 'ID', 'GUI 模板唯一标识。', 'text'],
    ['gui_type', 'GUI 类型', 'Bukkit InventoryType。只有 CHEST 支持行数。', 'enum'],
    ['title', '标题', 'GUI 窗口标题，支持 MiniMessage。', 'text'],
    ['rows', '箱子行数', '仅 CHEST 类型可用，范围 1-6。', 'number'],
    ['type', '槽位类型', '技能业务槽位语义。', 'text'],
    ['slots', '槽位', '槽位索引列表。', 'list'],
    ['item', '物品', '槽位显示物品。', 'text'],
    ['display_name', '显示名', '槽位物品显示名称。', 'text'],
    ['lore', 'Lore', '槽位物品描述。', 'stringList'],
    ['active_slot', '主动技能槽', '玩家主动技能槽位。', 'text'],
    ['skill_pool', '技能池', '可装配技能列表区域。', 'text'],
    ['cast_mode_toggle', '施法模式', '切换技能施法模式。', 'text'],
    ['page_prev', '上一页', '向前翻页按钮。', 'text'],
    ['page_next', '下一页', '向后翻页按钮。', 'text']
  ])
});

function guiFields(entries: Array<[string, string, string, string]>) {
  return Object.fromEntries(entries.map(([path, label, comment, type]) => [path, { path, label, comment, type }]));
}
