import type { WebConsoleExtension, WebConsoleExtensionStatus } from './types';
import { installWebConsoleHost, recordExtensionStatus } from './registry';

const loaded = new Set<string>();
const loading = new Map<string, Promise<void>>();

type LoadWebExtensionsOptions = { defer?: boolean };

/** Dynamically load Web Console extension scripts registered by server-side plugins. */
export async function loadWebExtensions(extensions: WebConsoleExtension[] | undefined, options: LoadWebExtensionsOptions = {}): Promise<WebConsoleExtensionStatus[]> {
  installWebConsoleHost();
  if (!extensions?.length) return [];
  if (!options.defer) {
    const results = await Promise.allSettled(extensions.map(loadWebExtension));
    return results.map((result, index) => recordStatusFromResult(extensions[index], result));
  }

  const statuses: WebConsoleExtensionStatus[] = [];
  for (const extension of extensions) {
    if (!isLoaded(extension)) await waitForIdle();
    const result = await Promise.allSettled([loadWebExtension(extension)]);
    statuses.push(recordStatusFromResult(extension, result[0]));
  }
  return statuses;
}

function recordStatusFromResult(extension: WebConsoleExtension, result: PromiseSettledResult<void>): WebConsoleExtensionStatus {
  const status: WebConsoleExtensionStatus = result.status === 'fulfilled'
    ? { moduleId: extension.moduleId, id: extension.id, url: extension.url, status: 'loaded' }
    : { moduleId: extension.moduleId, id: extension.id, url: extension.url, status: 'failed', error: result.reason instanceof Error ? result.reason.message : String(result.reason) };
  recordExtensionStatus(status);
  return status;
}

function isLoaded(extension: WebConsoleExtension): boolean {
  return Boolean(extension.url && loaded.has(extension.url));
}

function waitForIdle(): Promise<void> {
  return new Promise(resolve => {
    const requestIdle = window.requestIdleCallback;
    if (typeof requestIdle === 'function') {
      requestIdle(() => resolve(), { timeout: 120 });
      return;
    }
    window.setTimeout(resolve, 16);
  });
}

function loadWebExtension(extension: WebConsoleExtension): Promise<void> {
  const url = extension.url;
  if (!url || loaded.has(url)) return Promise.resolve();
  const existing = loading.get(url);
  if (existing) return existing;

  const promise = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = url;
    script.async = false;
    script.dataset.emakiExtensionId = extension.id;
    script.dataset.emakiModuleId = extension.moduleId;
    if (extension.apiVersion) script.dataset.emakiApiVersion = extension.apiVersion;
    script.onload = () => {
      loaded.add(url);
      loading.delete(url);
      resolve();
    };
    script.onerror = () => {
      loading.delete(url);
      reject(new Error(`WebUIEdit 扩展加载失败: ${extension.id} (${url})`));
    };
    document.head.appendChild(script);
  });
  loading.set(url, promise);
  return promise;
}
