import { registerEmakiPluginWebModule } from 'emaki-web-console';
import { installAttributeDiagnosticsStyles } from './diagnostics/AttributeDiagnosticsPanel';
import { emakiAttributeWebModule } from './webModule';

let registered = false;

export function registerEmakiAttributeWebConsole(): void {
  if (registered) return;
  registered = true;

  installAttributeDiagnosticsStyles();
  registerEmakiPluginWebModule(emakiAttributeWebModule);
}
