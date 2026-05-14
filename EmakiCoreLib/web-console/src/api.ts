import type { ConfigFile, GuiDocument, ModuleStatus, RuntimeLibrary, WebConfigNode, WebRegistry } from './types';

export class ApiClient {
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
    const data = await response.json();
    if (!response.ok || !data.success) {
      throw new Error(data.error || '登录失败');
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

  async saveRegistryValue(moduleId: string, filePath: string, path: string, value: unknown): Promise<void> {
    await this.request('/api/registry/save', {
      method: 'POST',
      body: JSON.stringify({ moduleId, filePath, path, value })
    });
  }

  async registryFileNodes(moduleId: string, path: string): Promise<WebConfigNode[]> {
    const data = await this.request(`/api/registry/file?module=${encodeURIComponent(moduleId)}&path=${encodeURIComponent(path)}`);
    return data.nodes;
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

  private async request(path: string, init: RequestInit = {}): Promise<any> {
    const response = await fetch(path, {
      ...init,
      headers: {
        ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers
      }
    });
    const data = await response.json();
    if (response.status === 401) {
      this.onUnauthorized();
      throw new Error('会话已过期');
    }
    if (!response.ok || !data.success) {
      throw new Error(data.error || '请求失败');
    }
    return data;
  }
}
