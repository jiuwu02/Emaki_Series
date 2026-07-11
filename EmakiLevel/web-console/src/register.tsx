import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { emakiLevelWebModule } from './webModule';

let registered = false;

export function registerEmakiLevelWebConsole(): void {
  if (registered) return;
  registered = true;

  registerEmakiPluginWebModule(emakiLevelWebModule);
}
