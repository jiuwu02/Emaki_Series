import { t } from './i18n';
import type { ConfigFile, GuiDocument, ItemDocument, ItemPreviewResult, ModuleStatus, RuntimeLibrary, WebConfigNode, WebRegistry } from './types';

export type ActionTypesResult = { nameActions: string[]; loreActions: string[] };
export type EconomyProvidersResult = { providers: string[]; availableProviders: string[] };
export type RegistrySaveResult = { revision?: number };
export type RegistryFileNodesResult = { nodes: WebConfigNode[]; revision?: number; path?: string };
export type TextDocumentKind = 'CONFIG' | 'GUI' | 'ITEM' | 'SCRIPT' | string;
export type TextDocumentTarget = { kind: TextDocumentKind; moduleId?: string; path: string };
export type TextDocument = { moduleId?: string; path: string; content: string; revision?: number };
export type FrontendErrorReport = { message: string; source: string; detail?: string; stack?: string; url?: string };

export class ApiClient {
  private actionTypesCache: ActionTypesResult | null = null;
  private economyProvidersCache: EconomyProvidersResult | null = null;

  constructor(private token: string | null, private onUnauthorized: () => void) { }

  async reportFrontendError(error: FrontendErrorReport): Promise<void> {
    try {
      await this.request('/api/debug/frontend-error', {
        method: 'POST',
        body: JSON.stringify({
          message: trimLogText(error.message, 1000),
          source: trimLogText(error.source, 180),
          detail: trimLogText(error.detail, 2000),
          stack: trimLogText(error.stack, 3000),
          url: trimLogText(error.url ?? window.location.href, 500)
        })
      });
    } catch {
      // 前端错误上报不能制造新的页面错误或递归噪声。
    }
  }

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

  async createFile(moduleId: string, fileId: string, name: string): Promise<{ path: string; name: string; revision?: number }> {
    const data = await this.request('/api/files/create', {
      method: 'POST',
      body: JSON.stringify({ moduleId, fileId, name })
    });
    return { path: data.path, name: data.name, revision: typeof data.revision === 'number' ? data.revision : undefined };
  }

  async deleteFile(moduleId: string, fileId: string | undefined, path: string, confirmPath: string): Promise<void> {
    await this.request('/api/files/delete', {
      method: 'POST',
      body: JSON.stringify({ moduleId, fileId, path, confirmPath })
    });
  }

  async readTextDocument(target: TextDocumentTarget): Promise<TextDocument> {
    const kind = normalizeKind(target.kind);
    if (kind === 'SCRIPT') {
      const doc = await this.readScript(target.path);
      return { path: target.path, ...doc };
    }
    if (!target.moduleId) throw new Error(t('core.api.missingModule'));
    if (kind === 'GUI') return this.readGui(target.moduleId, target.path);
    if (kind === 'ITEM') return this.readItem(target.moduleId, target.path);
    if (kind !== 'CONFIG') return this.readResource(target.moduleId, target.path);
    const data = await this.request(`/api/configs/read?module=${encodeURIComponent(target.moduleId)}&path=${encodeURIComponent(target.path)}`);
    const file = data.file ?? {};
    return { moduleId: target.moduleId, path: file.path ?? target.path, content: file.content ?? '', revision: typeof file.lastModified === 'number' ? file.lastModified : undefined };
  }

  async saveTextDocument(target: TextDocumentTarget, content: string, revision?: number): Promise<{ revision?: number }> {
    const kind = normalizeKind(target.kind);
    if (kind === 'SCRIPT') return this.saveScript(target.path, content, revision);
    if (!target.moduleId) throw new Error(t('core.api.missingModule'));
    if (kind === 'GUI') return this.saveGui(target.moduleId, target.path, content, revision);
    if (kind === 'ITEM') return this.saveItem(target.moduleId, target.path, content, revision);
    if (kind !== 'CONFIG') return this.saveResource(target.moduleId, target.path, content, revision);
    const data = await this.request('/api/configs/save', {
      method: 'POST',
      body: JSON.stringify({ moduleId: target.moduleId, path: target.path, content, revision })
    });
    return { revision: typeof data.revision === 'number' ? data.revision : undefined };
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

  async readScript(path: string): Promise<{ content: string; revision?: number }> {
    const data = await this.request(`/api/scripts/read?path=${encodeURIComponent(path)}`);
    return { content: data.content, revision: typeof data.revision === 'number' ? data.revision : undefined };
  }

  async saveScript(path: string, content: string, revision?: number): Promise<{ revision?: number }> {
    const data = await this.request('/api/scripts/save', {
      method: 'POST',
      body: JSON.stringify({ path, content, revision })
    });
    return { revision: typeof data.revision === 'number' ? data.revision : undefined };
  }

  async readGui(moduleId: string, path: string): Promise<GuiDocument> {
    const data = await this.request(`/api/gui/read?module=${encodeURIComponent(moduleId)}&path=${encodeURIComponent(path)}`);
    return { moduleId: data.moduleId, path: data.path, content: data.content, data: data.data, revision: data.revision } as GuiDocument;
  }

  async saveGui(moduleId: string, path: string, content: string, revision?: number): Promise<{ revision?: number }> {
    const data = await this.request('/api/gui/save', {
      method: 'POST',
      body: JSON.stringify({ moduleId, path, content, revision })
    });
    return { revision: typeof data.revision === 'number' ? data.revision : undefined };
  }

  async readItem(moduleId: string, path: string): Promise<ItemDocument> {
    const data = await this.request(`/api/items/read?module=${encodeURIComponent(moduleId)}&path=${encodeURIComponent(path)}`);
    return { moduleId: data.moduleId, path: data.path, content: data.content, data: data.data, revision: data.revision } as ItemDocument;
  }

  async saveItem(moduleId: string, path: string, content: string, revision?: number): Promise<{ revision?: number }> {
    const data = await this.request('/api/items/save', {
      method: 'POST',
      body: JSON.stringify({ moduleId, path, content, revision })
    });
    return { revision: typeof data.revision === 'number' ? data.revision : undefined };
  }

  async readResource(moduleId: string, path: string): Promise<ItemDocument> {
    const data = await this.request(`/api/resources/read?module=${encodeURIComponent(moduleId)}&path=${encodeURIComponent(path)}`);
    return { moduleId: data.moduleId, path: data.path, content: data.content, data: data.data, revision: data.revision } as ItemDocument;
  }

  async saveResource(moduleId: string, path: string, content: string, revision?: number): Promise<{ revision?: number }> {
    const data = await this.request('/api/resources/save', {
      method: 'POST',
      body: JSON.stringify({ moduleId, path, content, revision })
    });
    return { revision: typeof data.revision === 'number' ? data.revision : undefined };
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

function normalizeKind(kind: string | undefined): string {
  return String(kind ?? '').toUpperCase();
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

function trimLogText(value: unknown, maxLength: number): string {
  const text = String(value ?? '');
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text;
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
