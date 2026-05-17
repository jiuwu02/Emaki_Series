import React from 'react';
import { GuiEditorSurface, registerGuiEditorDescriptor, type SurfaceProps } from 'emaki-web-console';

function EmakiCookingGuiSurface(props: SurfaceProps) { return <GuiEditorSurface {...props} />; }

const host = window.EmakiWebConsole;
if (host) {
  host.registerSurface({ kind: 'GUI', moduleId: 'EmakiCooking', editorId: 'emakicooking:gui', component: EmakiCookingGuiSurface, label: '烹饪 GUI', priority: 100 });
  host.registerSurface({ kind: 'GUI', moduleId: 'EmakiCooking', component: EmakiCookingGuiSurface, label: '烹饪 GUI', priority: 90 });
  host.registerGuiEditorDescriptor('EmakiCooking', 'emakicooking:gui', {
    id: 'emakicooking:gui',
    moduleId: 'EmakiCooking',
    title: '烹饪 GUI',
    kindLabel: '烹饪 GUI',
    fields: guiFields([
      ['id', 'ID', 'GUI 模板唯一标识。', 'text'],
      ['gui_type', 'GUI 类型', 'Bukkit InventoryType。只有 CHEST 支持行数。', 'enum'],
      ['title', '标题', 'GUI 窗口标题，支持 MiniMessage。', 'text'],
      ['rows', '箱子行数', '仅 CHEST 类型可用，范围 1-6。', 'number'],
      ['type', '槽位类型', '烹饪工位槽位语义。', 'text'],
      ['slots', '槽位', '槽位索引列表。', 'list'],
      ['item', '物品', '槽位显示物品。', 'text'],
      ['display_name', '显示名', '槽位物品显示名称。', 'text'],
      ['lore', 'Lore', '槽位物品描述。', 'stringList'],
      ['ingredient', '原料槽', '放入烹饪原料的槽位。', 'text'],
      ['result', '产物槽', '展示或取出产物的槽位。', 'text'],
      ['fuel', '燃料槽', '放入燃料的槽位。', 'text']
    ])
  });
} else {
  console.warn('[EmakiCooking] EmakiWebConsole host is not available; GUI surface was not registered.');
}

void registerGuiEditorDescriptor;

function guiFields(entries: Array<[string, string, string, string]>) {
  return Object.fromEntries(entries.map(([path, label, comment, type]) => [path, { path, label, comment, type }]));
}
