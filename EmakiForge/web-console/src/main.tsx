import React from 'react';
import type { SurfaceProps } from '../../../EmakiCoreLib/web-console/src/registry';
import { GuiEditorSurface } from '../../../EmakiCoreLib/web-console/src/GuiEditorSurface';

function EmakiForgeGuiSurface(props: SurfaceProps) {
  return <GuiEditorSurface {...props} />;
}

const host = window.EmakiWebConsole;
if (host) {
  host.registerSurface({ kind: 'GUI', moduleId: 'EmakiForge', editorId: 'emakiforge:gui', component: EmakiForgeGuiSurface, label: '锻造 GUI', priority: 100 });
} else {
  console.warn('[EmakiForge] EmakiWebConsole host is not available; GUI surface was not registered.');
}
