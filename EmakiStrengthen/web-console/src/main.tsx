import React from 'react';
import type { SurfaceProps } from '../../../EmakiCoreLib/web-console/src/registry';
import { GuiEditorSurface } from '../../../EmakiCoreLib/web-console/src/GuiEditorSurface';

function EmakiStrengthenGuiSurface(props: SurfaceProps) { return <GuiEditorSurface {...props} />; }

const host = window.EmakiWebConsole;
if (host) {
  host.registerSurface({ kind: 'GUI', moduleId: 'EmakiStrengthen', editorId: 'emakistrengthen:gui', component: EmakiStrengthenGuiSurface, label: '强化 GUI', priority: 100 });
} else {
  console.warn('[EmakiStrengthen] EmakiWebConsole host is not available; GUI surface was not registered.');
}
