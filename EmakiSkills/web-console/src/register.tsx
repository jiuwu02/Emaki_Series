import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { emakiSkillsWebModule } from './webModule';

let registered = false;

export function registerEmakiSkillsWebConsole(): void {
  if (registered) return;
  registered = true;

  registerEmakiPluginWebModule(emakiSkillsWebModule);
}
