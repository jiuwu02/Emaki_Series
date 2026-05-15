import type { ComponentType } from 'react';
import { EmakiGemItemSurface } from './EmakiGemItemSurface';

type Host = {
  registerSurface: (registration: {
    kind?: string;
    moduleId?: string;
    editorId?: string;
    component: ComponentType<any>;
    label?: string;
    priority?: number;
  }) => void;
};

declare global {
  interface Window {
    EmakiWebConsole?: Host;
  }
}

const host = window.EmakiWebConsole;
if (host) {
  host.registerSurface({ editorId: 'emakigem:gem', component: EmakiGemItemSurface, label: '宝石定义', priority: 100 });
  host.registerSurface({ editorId: 'emakigem:socket-item', component: EmakiGemItemSurface, label: '宝石插槽物品', priority: 100 });
} else {
  console.warn('[EmakiGem] EmakiWebConsole host is not available; item surface was not registered.');
}
