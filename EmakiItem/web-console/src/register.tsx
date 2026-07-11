import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { emakiItemWebModule } from './webModule';

let registered = false;

export function registerEmakiItemWebConsole(): void {
  if (registered) return;
  registered = true;

  registerEmakiPluginWebModule(emakiItemWebModule);
}
