import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { emakiGemWebModule } from './webModule';

let registered = false;

export function registerEmakiGemWebConsole(): void {
  if (registered) return;
  registered = true;

  registerEmakiPluginWebModule(emakiGemWebModule);
}
