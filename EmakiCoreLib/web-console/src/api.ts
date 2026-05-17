import { t } from './i18n';
import type { ConfigFile, GuiDocument, ItemDocument, ItemPreviewResult, ModuleStatus, RuntimeLibrary, WebConfigNode, WebRegistry } from './types';

export type ActionTypesResult = { nameActions: string[]; loreActions: string[] };
export type EconomyProvidersResult = { providers: string[]; availableProviders: string[] };
export type RegistrySaveResult = { revision?: number };
export type RegistryFileNodesResult = { nodes: WebConfigNode[]; revision?: number; path?: string };

export class ApiClient {
  private actionTypesCache: ActionTypesResult | null = null;
  private economyProvidersCache: EconomyProvidersResult | null = null;

  constructor(private token: string | null, private onUnauthorized: () => void) {}

  setToken(token: string | null) {
    this.token = token;
  }

  async login(username: string, password: string): Promise<{ token: string; expiresAt: number; publicAccessWarning: boolean }> {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    const data = await parseResponseJson(response);
    if (!response.ok || !data.success) {
      throw new Error(readApiError(response, data, t('core.login.failed')));
    }
    return data;
  }

  async modules(): Promise<ModuleStatus[]> {
    const data = await this.request('/api/modules');
    return data.modules;
  }

  async registry(): Promise<WebRegistry> {
    const data = await this.request('/api/registry');
    return data.registry;
  }

  async saveRegistryValue(moduleId: string, filePath: string, path: string, value: unknown, revision?: number): Promise<RegistrySaveResult> {
    const data = await this.request('/api/registry/save', {
      method: 'POST',
      body: JSON.stringify({ moduleId, filePath, path, value, revision })
    });
    return { revision: typeof data.revision === 'number' ? data.revision : undefined };
  }

  async registryFileNodes(moduleId: string, path: string): Promise<RegistryFileNodesResult> {
    const data = await this.request(`/api/registry/file?module=${encodeURIComponent(moduleId)}&path=${encodeURIComponent(path)}`);
    return { nodes: data.nodes ?? [], revision: typeof data.revision === 'number' ? data.revision : undefined, path: data.path };
  }

  async configTree(module: string): Promise<ConfigFile[]> {
    const data = await this.request(`/api/configs/tree?module=${encodeURIComponent(module)}`);
    return data.files;
  }

  async readConfig(module: string, path: string): Promise<ConfigFile> {
    const data = await this.request(`/api/configs/read?module=${encodeURIComponent(module)}&path=${encodeURIComponent(path)}`);
    return data.file;
  }

  async libraries(): Promise<{ root: string; count: number; libraries: RuntimeLibrary[] }> {
    const data = await this.request('/api/libraries');
    return data.runtime;
  }

  async readScript(path: string): Promise<{ content: string }> {
    const data = await this.request(`/api/scripts/read?path=${encodeURIComponent(path)}`);
    return data as { content: string };
  }

  async saveScript(path: string, content: string): Promise<void> {
    await this.request('/api/scripts/save', {
      method: 'POST',
      body: JSON.stringify({ path, content })
    });
  }

  async readGui(moduleId: string, path: string): Promise<GuiDocument> {
    const data = await this.request(`/api/gui/read?module=${encodeURIComponent(moduleId)}&path=${encodeURIComponent(path)}`);
    return { moduleId: data.moduleId, path: data.path, content: data.content, data: data.data } as GuiDocument;
  }

  async saveGui(moduleId: string, path: string, content: string): Promise<void> {
    await this.request('/api/gui/save', {
      method: 'POST',
      body: JSON.stringify({ moduleId, path, content })
    });
  }

  async readItem(moduleId: string, path: string): Promise<ItemDocument> {
    const data = await this.request(`/api/items/read?module=${encodeURIComponent(moduleId)}&path=${encodeURIComponent(path)}`);
    return { moduleId: data.moduleId, path: data.path, content: data.content, data: data.data } as ItemDocument;
  }

  async saveItem(moduleId: string, path: string, content: string): Promise<void> {
    await this.request('/api/items/save', {
      method: 'POST',
      body: JSON.stringify({ moduleId, path, content })
    });
  }

  async previewItem(content: string, previewLevel: number, baseName = '', baseLore: string[] = []): Promise<ItemPreviewResult> {
    const data = await this.request('/api/items/preview', {
      method: 'POST',
      body: JSON.stringify({ content, previewLevel, baseName, baseLore })
    });
    return data.preview as ItemPreviewResult;
  }

  async actionTypes(): Promise<ActionTypesResult> {
    if (this.actionTypesCache) return this.actionTypesCache;
    const data = await this.request('/api/items/action-types');
    this.actionTypesCache = { nameActions: data.nameActions ?? [], loreActions: data.loreActions ?? [] };
    return this.actionTypesCache;
  }

  async economyProviders(): Promise<EconomyProvidersResult> {
    if (this.economyProvidersCache) return this.economyProvidersCache;
    const data = await this.request('/api/economy/providers');
    this.economyProvidersCache = {
      providers: normalizeOptions(data.providers, ['auto', 'vault', 'excellenteconomy']),
      availableProviders: normalizeOptions(data.availableProviders, ['auto'])
    };
    return this.economyProvidersCache;
  }

  private async request(path: string, init: RequestInit = {}): Promise<any> {
    let response: Response;
    try {
      response = await fetch(path, {
        ...init,
        headers: {
          ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
          ...(init.body ? { 'Content-Type': 'application/json' } : {}),
          ...init.headers
        }
      });
    } catch {
      throw new Error(t('core.api.network'));
    }

    const data = await parseResponseJson(response);
    if (response.status === 401) {
      this.onUnauthorized();
      throw new Error(t('core.api.unauthorized'));
    }
    if (!response.ok || !data.success) {
      throw new Error(readApiError(response, data));
    }
    return data;
  }
}

function normalizeOptions(value: unknown, fallback: string[]): string[] {
  const raw = Array.isArray(value) ? value : fallback;
  const merged = ['auto', ...raw.map(option => String(option ?? '').trim().toLowerCase()).filter(Boolean)];
  return [...new Set(merged)];
}

async function parseResponseJson(response: Response): Promise<any> {
  const text = await response.text();
  if (!text.trim()) return {};
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(t('core.api.invalidJson'));
  }
}

function readApiError(response: Response, data: any, fallback = t('core.api.requestFailed')): string {
  if (typeof data?.error === 'string' && data.error.trim()) return data.error;
  if (typeof data?.message === 'string' && data.message.trim()) return data.message;
  if (response.status === 403) return t('core.api.forbidden');
  if (response.status === 404) return t('core.api.notFound');
  if (response.status === 429) return t('core.api.rateLimited');
  if (response.status >= 500) return t('core.api.serverError');
  return fallback;
}
