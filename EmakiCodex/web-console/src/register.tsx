import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { installAdvancementPreviewStyles } from './AdvancementPreview';
import { emakiCodexWebModule } from './webModule';

let registered = false;

export function registerEmakiCodexWebConsole(): void {
  if (registered) return;
  registered = true;

  installAdvancementPreviewStyles();
  registerEmakiPluginWebModule(emakiCodexWebModule);
}
