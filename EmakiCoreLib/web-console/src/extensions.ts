import type { WebConsoleExtension } from './types';
import { installWebConsoleHost } from './registry';

const loaded = new Set<string>();
const loading = new Map<string, Promise<void>>();

/** Dynamically load Web Console extension scripts registered by server-side plugins. */
export async function loadWebExtensions(extensions: WebConsoleExtension[] | undefined): Promise<void> {
  installWebConsoleHost();
  if (!extensions?.length) return;
  await Promise.all(extensions.map(loadWebExtension));
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
    script.onload = () => {
      loaded.add(url);
      loading.delete(url);
      resolve();
    };
    script.onerror = () => {
      loading.delete(url);
      reject(new Error(`Web Console 扩展加载失败: ${extension.id} (${url})`));
    };
    document.head.appendChild(script);
  });
  loading.set(url, promise);
  return promise;
}
