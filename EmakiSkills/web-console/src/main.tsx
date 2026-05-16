import React from 'react';
import type { SurfaceProps } from '../../../EmakiCoreLib/web-console/src/registry';
import { GuiEditorSurface } from '../../../EmakiCoreLib/web-console/src/GuiEditorSurface';

function EmakiSkillsGuiSurface(props: SurfaceProps) { return <GuiEditorSurface {...props} />; }

const host = window.EmakiWebConsole;
if (host) {
  host.registerSurface({ kind: 'GUI', moduleId: 'EmakiSkills', editorId: 'emakiskills:gui', component: EmakiSkillsGuiSurface, label: '技能 GUI', priority: 100 });
} else {
  console.warn('[EmakiSkills] EmakiWebConsole host is not available; GUI surface was not registered.');
}
