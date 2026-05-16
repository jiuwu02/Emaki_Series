import React from 'react';
import type { ComponentType } from 'react';
import type { SurfaceProps } from '../../../EmakiCoreLib/web-console/src/registry';
import { GuiEditorSurface } from '../../../EmakiCoreLib/web-console/src/GuiEditorSurface';

export function PluginGuiSurface(props: SurfaceProps) {
  return <GuiEditorSurface {...props} />;
}

export const pluginGuiSurface = PluginGuiSurface as ComponentType<SurfaceProps>;
