import React from 'react';
import type { ComponentType } from 'react';
import { GuiEditorSurface, type SurfaceProps } from 'emaki-web-console';

export function PluginGuiSurface(props: SurfaceProps) {
  return <GuiEditorSurface {...props} />;
}

export const pluginGuiSurface = PluginGuiSurface as ComponentType<SurfaceProps>;
