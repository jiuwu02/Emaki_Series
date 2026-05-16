import React from 'react';
import { GuiEditorSurface, type SurfaceProps } from 'emaki-web-console';

function EmakiCookingGuiSurface(props: SurfaceProps) { return <GuiEditorSurface {...props} />; }

const host = window.EmakiWebConsole;
if (host) {
  host.registerSurface({ kind: 'GUI', moduleId: 'EmakiCooking', editorId: 'emakicooking:gui', component: EmakiCookingGuiSurface, label: '烹饪 GUI', priority: 100 });
} else {
  console.warn('[EmakiCooking] EmakiWebConsole host is not available; GUI surface was not registered.');
}
