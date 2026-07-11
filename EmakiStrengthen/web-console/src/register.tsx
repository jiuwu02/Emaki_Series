import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { installStrengthenRoutePreviewStyles } from './routePreview/StrengthenRoutePreview';
import { emakiStrengthenWebModule } from './webModule';

let registered = false;

export function registerEmakiStrengthenWebConsole(): void {
  if (registered) return;
  registered = true;

  installStrengthenRoutePreviewStyles();
  registerEmakiPluginWebModule(emakiStrengthenWebModule);
}
