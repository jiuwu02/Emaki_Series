#!/usr/bin/env node
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const requestedModules = new Set(process.argv.slice(2).map(value => value.trim()).filter(Boolean));
const packageJson = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'));
const workspaces = Array.isArray(packageJson.workspaces) ? packageJson.workspaces : [];

const legacyRegistrationApis = [
  'registerPluginConfig',
  'registerModuleLocale',
  'registerPluginGuiEditor',
  'registerConfigMetaFields',
  'registerConfigRuleFields',
  'registerConfigPreview',
  'registerConfigCreateTemplate',
  'registerConfigListItemSchema',
  'registerConfigListItemSchemaRule',
  'registerEffectTypes',
  'registerEditorDescriptor',
  'registerEditorField',
  'registerFileKindLabel',
  'registerInsightDefinition',
  'registerItemFieldRenderer',
  'registerItemPreviewFallback',
  'registerSourceDocumentAdapter',
  'registerSurface'
];

const expectedModules = new Map([
  ['EmakiCoreLib/web-console', { core: true }],
  ['EmakiCodex/web-console', {}],
  ['EmakiCooking/web-console', {}],
  ['EmakiStrengthen/web-console', {}],
  ['EmakiGem/web-console', {}],
  ['EmakiItem/web-console', {}],
  ['EmakiAttribute/web-console', {}],
  ['EmakiSkills/web-console', {}],
  ['EmakiForge/web-console', {}],
  ['EmakiLevel/web-console', {}]
]);

const results = [];

function add(status, scope, message) {
  results.push({ status, scope, message });
}

function rel(...parts) {
  return path.join(root, ...parts);
}

function readIfExists(file) {
  return existsSync(file) ? readFileSync(file, 'utf8') : '';
}

function moduleNameFromWorkspace(workspace) {
  return workspace.split('/')[0] ?? workspace;
}

function findWebModule(srcDir) {
  for (const file of ['webModule.ts', 'webModule.tsx']) {
    const fullPath = path.join(srcDir, file);
    if (existsSync(fullPath)) return fullPath;
  }
  return null;
}

function walkFiles(dir, predicate, output = []) {
  if (!existsSync(dir)) return output;
  for (const entry of readdirSync(dir)) {
    const fullPath = path.join(dir, entry);
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      if (fullPath.includes(`${path.sep}node_modules${path.sep}`)) continue;
      walkFiles(fullPath, predicate, output);
    } else if (predicate(fullPath)) {
      output.push(fullPath);
    }
  }
  return output;
}

function isRequestedWorkspace(workspace) {
  if (requestedModules.size === 0) return true;
  const moduleName = moduleNameFromWorkspace(workspace);
  return requestedModules.has(moduleName) || requestedModules.has(workspace) || requestedModules.has(moduleName.replace(/^Emaki/, ''));
}

for (const [workspace, policy] of expectedModules) {
  if (!isRequestedWorkspace(workspace)) continue;
  if (!workspaces.includes(workspace)) add('error', workspace, 'missing from package.json workspaces');

  const workspaceDir = rel(workspace);
  if (!existsSync(workspaceDir)) {
    add('error', workspace, 'workspace directory is missing');
    continue;
  }

  if (policy.core) {
    add('ok', workspace, 'core web-console workspace present');
    continue;
  }

  const moduleName = moduleNameFromWorkspace(workspace);
  const srcDir = path.join(workspaceDir, 'src');
  const mainPath = path.join(srcDir, 'main.tsx');
  const registerPath = path.join(srcDir, 'register.tsx');
  const webModulePath = findWebModule(srcDir);
  const manifestPath = rel(moduleName, 'src', 'main', 'resources', 'web-console.yml');

  if (!existsSync(mainPath)) add('error', workspace, 'src/main.tsx is missing');
  else {
    const mainSource = readIfExists(mainPath);
    if (/registerEmaki[A-Za-z0-9]+WebConsole\(\)/.test(mainSource)) add('ok', workspace, 'main.tsx calls the plugin web-console register entry');
    else add('error', workspace, 'main.tsx does not call a registerEmaki*WebConsole entry');
  }

  if (!existsSync(registerPath)) add('error', workspace, 'src/register.tsx is missing');
  else {
    const registerSource = readIfExists(registerPath);
    if (registerSource.includes('registerEmakiPluginWebModule')) add('ok', workspace, 'register.tsx uses Manifest v2 registration');
    else add('error', workspace, 'register.tsx does not use registerEmakiPluginWebModule');

    const legacyHits = legacyRegistrationApis.filter(api => new RegExp(`\\b${api}\\b`).test(registerSource));
    if (legacyHits.length) add('error', workspace, `register.tsx still references legacy APIs: ${legacyHits.join(', ')}`);
    else add('ok', workspace, 'register.tsx contains no legacy registration API calls');
  }

  if (!webModulePath) add('error', workspace, 'src/webModule.ts or src/webModule.tsx is missing');
  else {
    const webModuleSource = readIfExists(webModulePath);
    const moduleExportPattern = new RegExp(`emaki${moduleName.replace(/^Emaki/, '')}WebModule`, 'i');
    if (webModuleSource.includes('defineEmakiPluginWebModule')) add('ok', workspace, `${path.relative(root, webModulePath)} defines a Manifest v2 module`);
    else add('error', workspace, `${path.relative(root, webModulePath)} does not call defineEmakiPluginWebModule`);
    if (moduleExportPattern.test(webModuleSource)) add('ok', workspace, 'webModule export name matches module convention');
    else add('warning', workspace, 'webModule export name could not be convention-matched');
    if (webModuleSource.includes('defineCapabilities')) add('ok', workspace, 'webModule declares capabilities');
    else add('warning', workspace, 'webModule does not declare capabilities');
  }

  if (existsSync(manifestPath)) {
    const manifest = readIfExists(manifestPath);
    if (/module:\s*/.test(manifest) && /files:\s*/.test(manifest)) add('ok', workspace, 'web-console.yml keeps declarative module/file structure');
    else add('warning', workspace, 'web-console.yml exists but module/files structure was not detected');
  } else {
    add('error', workspace, 'src/main/resources/web-console.yml is missing');
  }
}

const sourceFiles = walkFiles(root, file => /web-console[\\/]src[\\/].+\.(ts|tsx)$/.test(file) && !file.endsWith(`${path.sep}register.tsx`));
for (const file of sourceFiles) {
  const relative = path.relative(root, file);
  const workspace = relative.split(path.sep).slice(0, 2).join('/');
  if (!isRequestedWorkspace(workspace)) continue;

  const source = readIfExists(file);
  const legacyHits = legacyRegistrationApis.filter(api => new RegExp(`\\b${api}\\s*\\(`).test(source));
  if (legacyHits.length) {
    const allowedCorePlatform = relative.replace(/\\/g, '/').startsWith('EmakiCoreLib/web-console/src/');
    if (allowedCorePlatform) add('ok', relative, 'core platform owns legacy registry bridge APIs used by Manifest v2 loader');
    else add('error', relative, `legacy registration call remains outside Manifest v2 loader: ${legacyHits.join(', ')}`);
  }
}

const counts = results.reduce((acc, result) => {
  acc[result.status] = (acc[result.status] ?? 0) + 1;
  return acc;
}, {});

for (const result of results) {
  const marker = result.status === 'ok' ? 'OK' : result.status.toUpperCase();
  console.log(`[${marker}] ${result.scope}: ${result.message}`);
}

console.log(`\nSummary: ${counts.ok ?? 0} ok, ${counts.warning ?? 0} warning, ${counts.error ?? 0} error`);

if ((counts.error ?? 0) > 0) process.exitCode = 1;
