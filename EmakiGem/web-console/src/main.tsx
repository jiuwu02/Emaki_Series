import { EmakiGemItemSurface } from './EmakiGemItemSurface';
import { PluginGuiSurface } from './PluginGuiSurface';

const host = window.EmakiWebConsole;
if (host) {
  host.registerSurface({ kind: 'ITEM', moduleId: 'EmakiGem', editorId: 'emakigem:gem', component: EmakiGemItemSurface, label: '宝石定义', priority: 100 });
  host.registerSurface({ kind: 'ITEM', moduleId: 'EmakiGem', editorId: 'emakigem:socket-item', component: EmakiGemItemSurface, label: '宝石插槽物品', priority: 100 });
  host.registerSurface({ kind: 'GUI', moduleId: 'EmakiGem', editorId: 'emakigem:gui', component: PluginGuiSurface, label: '宝石 GUI', priority: 100 });
} else {
  console.warn('[EmakiGem] EmakiWebConsole host is not available; item surface was not registered.');
}
