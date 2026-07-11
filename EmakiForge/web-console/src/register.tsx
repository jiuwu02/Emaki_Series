import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { emakiForgeWebModule } from './webModule';

let registered = false;

export function registerEmakiForgeWebConsole(): void {
  if (registered) return;
  registered = true;

  registerEmakiPluginWebModule(emakiForgeWebModule);
}
