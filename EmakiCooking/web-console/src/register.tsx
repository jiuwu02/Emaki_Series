import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { emakiCookingWebModule } from './webModule';

let registered = false;

export function registerEmakiCookingWebConsole(): void {
  if (registered) return;
  registered = true;

  registerEmakiPluginWebModule(emakiCookingWebModule);
}
