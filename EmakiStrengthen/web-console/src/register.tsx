import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { installStrengthenRoutePreviewStyles } from './routePreview/StrengthenRoutePreview';
import { registerEmakiStrengthenEffectTypes } from './schema/strengthenSchema';
import { emakiStrengthenWebModule } from './webModule';

let registered = false;

export function registerEmakiStrengthenWebConsole(): void {
  if (registered) return;
  registered = true;

  installStrengthenRoutePreviewStyles();
  registerEmakiStrengthenEffectTypes();
  registerEmakiPluginWebModule(emakiStrengthenWebModule);
}
