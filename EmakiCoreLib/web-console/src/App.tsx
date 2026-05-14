import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ApiClient } from './api';
import { GuiEditorSurface } from './GuiEditorSurface';
import type { WebConfigNode, WebRegistry, WebRegistryFile, WebRegistryModule } from './types';

type Selection = { moduleId: string; fileId: string; scriptPath?: string; refreshKey?: number };
type DraftMap = Record<string, unknown>;

type Toast = { tone: 'ok' | 'bad'; text: string } | null;
type ColorTheme = 'dark' | 'light';

const COLOR_THEMES: { id: ColorTheme; label: string }[] = [
  { id: 'dark', label: '深色' },
  { id: 'light', label: '浅色' }
];

export default function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('emaki-web-token'));
  const [registry, setRegistry] = useState<WebRegistry | null>(null);
  const [selected, setSelected] = useState<Selection | null>(null);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [drafts, setDrafts] = useState<DraftMap>({});
  const [toast, setToast] = useState<Toast>(null);
  const [theme, setTheme] = useState<ColorTheme>(() => readTheme());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const api = useMemo(() => new ApiClient(token, () => {
    sessionStorage.removeItem('emaki-web-token');
    setToken(null);
  }), [token]);

  useEffect(() => { if (token) void loadRegistry(true); }, [token]);
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('emaki-color-theme', theme);
  }, [theme]);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  async function loadRegistry(initial = false) {
    setLoading(true);
    try {
      const next = await api.registry();
      setRegistry(next);
      if (initial) setExpanded(Object.fromEntries(next.modules.map((m) => [m.id, true])));
      setSelected((c) => c ?? firstSelection(next));
      setDrafts({});
      if (!initial) setToast({ tone: 'ok', text: '已刷新配置注册表。' });
    } catch (err) {
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : '刷新失败。' });
    } finally {
      setLoading(false);
    }
  }

  async function saveCurrent() {
    if (!selectedModule || !selectedFile) return;
    const changes = selectedFile.nodes.filter((n) => n.type !== 'object' && draftKey(selectedModule.id, n.path) in drafts);
    if (!changes.length) {
      setToast({ tone: 'ok', text: '没有需要保存的改动。' });
      return;
    }
    setSaving(true);
    try {
      for (const node of changes) {
        await api.saveRegistryValue(selectedModule.id, selectedFile.path, node.path, drafts[draftKey(selectedModule.id, node.path)]);
      }
      setToast({ tone: 'ok', text: `已保存 ${changes.length} 项配置，执行 reload 后生效。` });
      await loadRegistry(true);
    } catch (err) {
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : '保存失败。' });
    } finally {
      setSaving(false);
    }
  }

  if (!token) return <Login onLogin={(t) => { sessionStorage.setItem('emaki-web-token', t); setToken(t); }} />;

  const selectedModule = selected && registry ? registry.modules.find((m) => m.id === selected.moduleId) ?? null : null;
  const selectedFile = selectedModule && selected ? selectedModule.files.find((f) => f.id === selected.fileId) ?? null : null;
  const changedCount = selectedModule && selectedFile ? selectedFile.nodes.filter((n) => n.type !== 'object' && draftKey(selectedModule.id, n.path) in drafts).length : 0;
  const activeTheme = COLOR_THEMES.find((entry) => entry.id === theme) ?? COLOR_THEMES[0];
  const nextTheme = () => setTheme((current) => COLOR_THEMES[(COLOR_THEMES.findIndex((entry) => entry.id === current) + 1) % COLOR_THEMES.length].id);

  return (
    <div className="workbench">
      {toast && <div className={`toast ${toast.tone}`}>{toast.text}</div>}
      <ResizableRail>
        <div className="brand-block">
          <div className="brand-main"><span className="sigil">绘</span><div><strong>绘卷核心库</strong><small>配置控制台</small></div></div>
          <button type="button" className={`theme-toggle ${theme}`} onClick={nextTheme} title={`切换颜色主题：${activeTheme.label}`} aria-label={`当前颜色主题 ${activeTheme.label}，点击切换`}>
            <ThemeIcon key={theme} theme={theme} />
            <span>{activeTheme.label}</span>
          </button>
        </div>
        <div className="tree-caption">模块树</div>
        <WorkspaceTree registry={registry} selected={selected} expanded={expanded} setExpanded={setExpanded} onSelect={(next) => setSelected((current) => sameSelection(current, next) ? { ...next, refreshKey: (current?.refreshKey ?? 0) + 1 } : next)} />
        <button className="rail-action quiet" onClick={() => { sessionStorage.removeItem('emaki-web-token'); setToken(null); }}>退出登录</button>
      </ResizableRail>
      <main className="stage">
        <header className="stage-head">
          <div>
            <h1>{selectedModule ? selectedModule.name : '配置控制台'}</h1>
            <p>{selectedFile ? `${selectedFile.title}，${selectedFile.path}` : '选择左侧文件开始编辑。'}</p>
          </div>
          <div className="head-actions">
            <button onClick={() => void loadRegistry()} disabled={loading}>刷新</button>
            <button className={`primary ${changedCount ? 'save-ready' : ''}`} onClick={() => void saveCurrent()} disabled={saving || changedCount === 0}>保存{changedCount ? ` ${changedCount}` : ''}</button>
          </div>
        </header>
        <section className="editor-shell single">
          <ConfigSurface module={selectedModule} file={selectedFile} drafts={drafts} setDrafts={setDrafts} api={api} scriptPath={selected?.scriptPath} refreshKey={selected?.refreshKey ?? 0} />
        </section>
      </main>
    </div>
  );
}

function Login({ onLogin }: { onLogin: (token: string) => void }) {
  const [username, setUsername] = useState('EmakiAdmin');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const api = new ApiClient(null, () => {});
  async function submit(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setError('');
    try { onLogin((await api.login(username, password)).token); }
    catch (err) { setError(err instanceof Error ? err.message : '登录失败'); }
    finally { setBusy(false); }
  }
  return <main className="login-scene"><section className="login-panel"><div className="login-kicker">绘卷核心库</div><h1>配置控制台</h1><p>面向管理员团队的深度配置编辑工具。保存后执行 reload 使运行时生效。</p><form onSubmit={submit}><label>账号<input value={username} onChange={(e) => setUsername(e.target.value)} /></label><label>密码<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} /></label>{error && <div className="inline-error">{error}</div>}<button type="submit" disabled={busy}>{busy ? '验证中' : '登录'}</button></form></section></main>;
}

function ResizableRail({ children }: { children: React.ReactNode }) {
  const [width, setWidth] = useState(() => {
    const saved = localStorage.getItem('emaki-rail-width');
    return saved ? Math.max(180, Math.min(600, Number(saved))) : 272;
  });
  const [dragging, setDragging] = useState(false);
  const startX = useRef(0);
  const startW = useRef(272);

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    startX.current = e.clientX;
    startW.current = width;
    setDragging(true);
  }, [width]);

  useEffect(() => {
    if (!dragging) return;
    const onMove = (e: MouseEvent) => {
      const next = Math.max(180, Math.min(600, startW.current + (e.clientX - startX.current)));
      setWidth(next);
    };
    const onUp = () => {
      setDragging(false);
      localStorage.setItem('emaki-rail-width', String(width));
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    return () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [dragging, width]);

  useEffect(() => {
    document.documentElement.style.setProperty('--rail-width', `${width}px`);
  }, [width]);

  return <aside className="tree-rail">
    {children}
    <div className={`rail-resize ${dragging ? 'active' : ''}`} onMouseDown={onMouseDown} />
  </aside>;
}

function WorkspaceTree({ registry, selected, expanded, setExpanded, onSelect }: { registry: WebRegistry | null; selected: Selection | null; expanded: Record<string, boolean>; setExpanded: React.Dispatch<React.SetStateAction<Record<string, boolean>>>; onSelect: (v: Selection) => void }) {
  if (!registry) return <div className="tree-empty">载入中</div>;
  const toggle = (id: string) => setExpanded((c) => ({ ...c, [id]: !c[id] }));
  return <div className="tree">{registry.modules.map((m) => <div key={m.id} className="tree-module"><button className="tree-folder" onClick={() => toggle(m.id)}><Icon svg={m.icon} /> <span>{expanded[m.id] ? '⌄' : '›'}</span> {m.name}</button>{expanded[m.id] && m.files.map((f) => {
    const hasChildren = f.children && f.children.length > 0;
    if (hasChildren) {
      const folderId = `folder:${f.id}`;
      return <div key={f.id} className="tree-file-folder">
        <button className={`tree-file folder-toggle ${selected?.moduleId === m.id && selected.fileId === f.id ? 'active' : ''}`} onClick={() => toggle(folderId)}>
          <span className="folder-arrow">{expanded[folderId] ? '⌄' : '›'}</span> {fileKindLabel(f.kind)} · {f.title}
        </button>
        {expanded[folderId] && <div className="tree-children">{f.children!.map((child) => {
          const childPath = isKind(f.kind, 'SCRIPT') ? child.relativePath : (child.fullPath ?? child.relativePath);
          return <button key={child.relativePath} className="tree-child" onClick={() => onSelect({ moduleId: m.id, fileId: f.id, scriptPath: childPath })}>{child.name}</button>;
        })}</div>}
      </div>;
    }
    return <button key={f.id} className={`tree-file ${selected?.moduleId === m.id && selected.fileId === f.id ? 'active' : ''}`} onClick={() => onSelect({ moduleId: m.id, fileId: f.id })}>{fileKindLabel(f.kind)} · {f.title}</button>;
  })}</div>)}</div>;
}

function ConfigSurface({ module, file, drafts, setDrafts, api, scriptPath, refreshKey }: { module: WebRegistryModule | null; file: WebRegistryFile | null; drafts: DraftMap; setDrafts: React.Dispatch<React.SetStateAction<DraftMap>>; api: ApiClient; scriptPath?: string; refreshKey: number }) {
  if (!module || !file) return <section className="config-surface empty">选择左侧配置文件。</section>;
  if (isKind(file.kind, 'SCRIPT')) return <section className="config-surface script-surface"><div className="surface-head"><div><h2>{file.title}</h2><p>{file.comment}</p></div><span className="file-kind script">{fileKindLabel(file.kind)}</span></div>{scriptPath ? <ScriptEditor api={api} scriptPath={scriptPath} /> : <div className="script-placeholder">点击左侧脚本文件开始编辑。</div>}</section>;
  if (isKind(file.kind, 'GUI')) return <GuiEditorSurface module={module} file={file} api={api} childPath={scriptPath} refreshKey={refreshKey} />;
  // CONFIG 类型：如果有子文件路径，按需加载子文件内容
  if (isKind(file.kind, 'CONFIG') && scriptPath) return <ConfigChildSurface module={module} file={file} childPath={scriptPath} drafts={drafts} setDrafts={setDrafts} api={api} refreshKey={refreshKey} />;
  // CONFIG 类型 glob 文件无子文件选中时，显示提示
  if (isKind(file.kind, 'CONFIG') && file.children && file.children.length > 0 && file.nodes.length === 0) return <section className="config-surface"><div className="surface-head"><div><h2>{file.title}</h2><p>{file.comment}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><div className="script-placeholder">点击左侧文件开始编辑。</div></section>;
  return <section className="config-surface"><div className="surface-head"><div><h2>{file.title}</h2><p>{file.comment}</p></div><span className={`file-kind ${String(file.kind).toLowerCase()}`}>{fileKindLabel(file.kind)}</span></div><ConfigNodeTree moduleId={module.id} nodes={file.nodes} drafts={drafts} setDrafts={setDrafts} /></section>;
}

function ConfigChildSurface({ module, file, childPath, drafts, setDrafts, api, refreshKey }: { module: WebRegistryModule; file: WebRegistryFile; childPath: string; drafts: DraftMap; setDrafts: React.Dispatch<React.SetStateAction<DraftMap>>; api: ApiClient; refreshKey: number }) {
  const [nodes, setNodes] = useState<WebConfigNode[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [toast, setToast] = useState<{ tone: 'ok' | 'bad'; text: string } | null>(null);

  useEffect(() => {
    setLoading(true);
    setError('');
    setNodes([]);
    api.registryFileNodes(module.id, childPath).then(result => {
      setNodes(result);
    }).catch(err => {
      setError(err instanceof Error ? err.message : '加载失败');
    }).finally(() => setLoading(false));
  }, [module.id, childPath, refreshKey]);

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 2600);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const changedNodes = nodes.filter(n => n.type !== 'object' && draftKey(module.id, n.path) in drafts);

  async function saveChild() {
    if (!changedNodes.length) {
      setToast({ tone: 'ok', text: '没有需要保存的改动。' });
      return;
    }
    setSaving(true);
    try {
      for (const node of changedNodes) {
        await api.saveRegistryValue(module.id, childPath, node.path, drafts[draftKey(module.id, node.path)]);
      }
      setToast({ tone: 'ok', text: `已保存 ${changedNodes.length} 项配置，执行 reload 后生效。` });
      // 清除已保存的 drafts
      setDrafts(prev => {
        const copy = { ...prev };
        for (const node of changedNodes) {
          delete copy[draftKey(module.id, node.path)];
        }
        return copy;
      });
      // 重新加载节点
      const refreshed = await api.registryFileNodes(module.id, childPath);
      setNodes(refreshed);
    } catch (err) {
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : '保存失败。' });
    } finally {
      setSaving(false);
    }
  }

  const fileName = childPath.split('/').pop() ?? childPath;

  return <section className="config-surface">
    {toast && <div className={`toast ${toast.tone}`} style={{ position: 'absolute', top: 12, right: 12 }}>{toast.text}</div>}
    <div className="surface-head">
      <div><h2>{fileName}</h2><p>{file.title} · {childPath}</p></div>
      <div className="head-actions">
        <button className={`primary ${changedNodes.length ? 'save-ready' : ''}`} onClick={() => void saveChild()} disabled={saving || changedNodes.length === 0}>保存{changedNodes.length ? ` ${changedNodes.length}` : ''}</button>
      </div>
    </div>
    {loading && <div className="script-loading">加载中...</div>}
    {error && <div className="inline-error">{error}</div>}
    {!loading && !error && <ConfigNodeTree moduleId={module.id} nodes={nodes} drafts={drafts} setDrafts={setDrafts} />}
  </section>;
}

function ConfigNodeTree({ moduleId, nodes, drafts, setDrafts }: { moduleId: string; nodes: WebConfigNode[]; drafts: DraftMap; setDrafts: React.Dispatch<React.SetStateAction<DraftMap>> }) {
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>(() => {
    // 默认全部折叠
    const initial: Record<string, boolean> = {};
    for (const node of nodes) {
      if (node.type === 'object' && !node.path.includes('.')) {
        initial[node.path] = true;
      }
    }
    return initial;
  });
  const toggle = (path: string) => setCollapsed(c => ({ ...c, [path]: !c[path] }));

  // 构建树结构：顶级节点是 path 中不含 "." 的节点，或者 object 节点作为分组
  const groups = buildNodeGroups(nodes);

  return <div className="node-grid">{groups.map(group => {
    if (group.type === 'leaf') {
      return <ConfigNodeView key={group.node.path} moduleId={moduleId} node={group.node} drafts={drafts} setDrafts={setDrafts} />;
    }
    const isCollapsed = collapsed[group.node.path] === true;
    const childCount = group.children.length;
    const changedInGroup = group.children.filter(n => n.type !== 'object' && draftKey(moduleId, n.path) in drafts).length;
    return <div key={group.node.path} className="node-section">
      <button className={`node-section-header ${isCollapsed ? 'collapsed' : ''}`} onClick={() => toggle(group.node.path)}>
        <span className="section-arrow">{isCollapsed ? '›' : '⌄'}</span>
        <strong>{group.node.label}</strong>
        <code>{group.node.path}</code>
        <span className="section-comment">{group.node.comment}</span>
        <span className="section-meta">{changedInGroup > 0 && <span className="section-badge">{changedInGroup}</span>}{childCount} 项</span>
      </button>
      {!isCollapsed && <div className="node-section-body">{group.children.map(child =>
        child.type === 'object'
          ? <div key={child.path} className="node-group-inner"><strong>{child.label}</strong><code>{child.path}</code><span>{child.comment}</span></div>
          : <ConfigNodeView key={child.path} moduleId={moduleId} node={child} drafts={drafts} setDrafts={setDrafts} />
      )}</div>}
    </div>;
  })}</div>;
}

type NodeGroup = { type: 'section'; node: WebConfigNode; children: WebConfigNode[] } | { type: 'leaf'; node: WebConfigNode };

function buildNodeGroups(nodes: WebConfigNode[]): NodeGroup[] {
  const groups: NodeGroup[] = [];
  let i = 0;
  while (i < nodes.length) {
    const node = nodes[i];
    if (node.type === 'object' && !node.path.includes('.')) {
      // 顶级 object：收集其下所有子节点（path 以 node.path + "." 开头的）
      const prefix = node.path + '.';
      const children: WebConfigNode[] = [];
      i++;
      while (i < nodes.length && nodes[i].path.startsWith(prefix)) {
        children.push(nodes[i]);
        i++;
      }
      groups.push({ type: 'section', node, children });
    } else if (node.type === 'object') {
      // 非顶级 object：跳过（会被父级 section 收集）
      i++;
    } else {
      // 顶级叶子节点（如 version, language）
      groups.push({ type: 'leaf', node });
      i++;
    }
  }
  return groups;
}

function ConfigNodeView({ moduleId, node, drafts, setDrafts }: { moduleId: string; node: WebConfigNode; drafts: DraftMap; setDrafts: React.Dispatch<React.SetStateAction<DraftMap>> }) {
  const key = draftKey(moduleId, node.path);
  const value = key in drafts ? drafts[key] : node.value;
  const setValue = (next: unknown) => setDrafts((c) => {
    if (valuesEqual(next, node.value)) {
      const copy = { ...c };
      delete copy[key];
      return copy;
    }
    return { ...c, [key]: next };
  });
  const isWide = node.type === 'dynamic_map' || node.type === 'list';
  return <div className={`node ${key in drafts ? 'changed' : ''} ${isWide ? 'node-wide' : ''}`}><div className="node-meta"><strong>{node.label}</strong><code>{node.path}</code><p>{node.comment}</p></div><div className="node-control">{renderControl(node, value, setValue)}</div></div>;
}

function renderControl(node: WebConfigNode, value: unknown, setValue: (v: unknown) => void) {
  if (node.type === 'boolean') return <button type="button" className={`switch ${value ? 'on' : ''}`} onClick={() => setValue(!value)}><span />{value ? '开启' : '关闭'}</button>;
  if (node.type === 'enum' && node.options) return <select value={str(value)} onChange={(e) => setValue(e.target.value)}>{node.options.map(opt => <option key={opt} value={opt}>{opt}</option>)}</select>;
  if (node.type === 'number') return <input type="number" value={String(value ?? 0)} onChange={(e) => setValue(Number(e.target.value))} />;
  if (node.type === 'dynamic_map') return <DynamicMapEditor value={value} setValue={setValue} />;
  if (node.type === 'list') {
    const items = Array.isArray(value) ? value : [];
    const update = (i: number, v: string) => setValue(items.map((x, j) => j === i ? parseListValue(x, v) : x));
    return <div className="list-editor">{items.map((item, i) => <div className="list-row" key={i}>{isObjectLike(item) ? <textarea value={str(item)} onChange={(e) => update(i, e.target.value)} /> : <input value={str(item)} onChange={(e) => update(i, e.target.value)} />}<button type="button" onClick={() => setValue(items.filter((_, j) => j !== i))}>删除</button></div>)}<button type="button" className="add-row" onClick={() => setValue([...items, ''])}>添加一项</button></div>;
  }
  return <input value={str(value)} onChange={(e) => setValue(e.target.value)} />;
}

function DynamicMapEditor({ value, setValue }: { value: unknown; setValue: (v: unknown) => void }) {
  const [newKey, setNewKey] = useState('');
  const map: Record<string, string[]> = (isObjectLike(value) ? value : {}) as Record<string, string[]>;
  const keys = Object.keys(map);

  function addKey() {
    const trimmed = newKey.trim().replace(/\s+/g, '_').toLowerCase();
    if (!trimmed || trimmed in map) return;
    setValue({ ...map, [trimmed]: [] });
    setNewKey('');
  }

  function removeKey(k: string) {
    const copy = { ...map };
    delete copy[k];
    setValue(copy);
  }

  function updateList(k: string, items: string[]) {
    setValue({ ...map, [k]: items });
  }

  function addItem(k: string) {
    updateList(k, [...(map[k] || []), '']);
  }

  function updateItem(k: string, i: number, v: string) {
    updateList(k, (map[k] || []).map((x, j) => j === i ? v : x));
  }

  function removeItem(k: string, i: number) {
    updateList(k, (map[k] || []).filter((_, j) => j !== i));
  }

  return <div className="dynamic-map-editor">
    {keys.map(k => <div key={k} className="dmap-entry">
      <div className="dmap-header">
        <code>{k}</code>
        <button type="button" className="dmap-remove" onClick={() => removeKey(k)}>移除</button>
      </div>
      <div className="dmap-list">
        {(Array.isArray(map[k]) ? map[k] : []).map((item, i) => <div key={i} className="dmap-row">
          <input value={String(item)} onChange={(e) => updateItem(k, i, e.target.value)} />
          <button type="button" onClick={() => removeItem(k, i)}>删除</button>
        </div>)}
        <button type="button" className="add-row" onClick={() => addItem(k)}>添加动作行</button>
      </div>
    </div>)}
    <div className="dmap-add">
      <input value={newKey} onChange={(e) => setNewKey(e.target.value)} placeholder="新模板名称" onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addKey(); } }} />
      <button type="button" onClick={addKey} disabled={!newKey.trim()}>添加模板</button>
    </div>
  </div>;
}

function ScriptEditor({ api, scriptPath }: { api: ApiClient; scriptPath: string }) {
  const [content, setContent] = useState('');
  const [savedContent, setSavedContent] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [completions, setCompletions] = useState<string[]>([]);
  const [completionPos, setCompletionPos] = useState<{ top: number; left: number } | null>(null);
  const [selectedCompletion, setSelectedCompletion] = useState(0);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const highlightRef = useRef<HTMLPreElement>(null);

  const isDirty = content !== savedContent;

  useEffect(() => {
    setLoading(true);
    api.readScript(scriptPath).then(res => {
      setContent(res.content);
      setSavedContent(res.content);
    }).catch(() => {
      setContent('// 无法加载文件');
      setSavedContent('');
    }).finally(() => setLoading(false));
  }, [scriptPath]);

  async function save() {
    setSaving(true);
    try {
      await api.saveScript(scriptPath, content);
      setSavedContent(content);
    } finally {
      setSaving(false);
    }
  }

  function handleInput(e: React.ChangeEvent<HTMLTextAreaElement>) {
    const value = e.target.value;
    setContent(value);
    tryComplete(value, e.target.selectionStart);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (completions.length > 0) {
      if (e.key === 'ArrowDown') { e.preventDefault(); setSelectedCompletion(i => Math.min(i + 1, completions.length - 1)); return; }
      if (e.key === 'ArrowUp') { e.preventDefault(); setSelectedCompletion(i => Math.max(i - 1, 0)); return; }
      if (e.key === 'Enter' || e.key === 'Tab') { e.preventDefault(); applyCompletion(completions[selectedCompletion]); return; }
      if (e.key === 'Escape') { setCompletions([]); return; }
    }
    if (e.key === 'Tab' && completions.length === 0) {
      e.preventDefault();
      const ta = e.currentTarget;
      const start = ta.selectionStart;
      const end = ta.selectionEnd;
      const newValue = content.substring(0, start) + '  ' + content.substring(end);
      setContent(newValue);
      requestAnimationFrame(() => { ta.selectionStart = ta.selectionEnd = start + 2; });
    }
    if (e.key === 's' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      save();
    }
  }

  function tryComplete(value: string, cursor: number) {
    const before = value.substring(0, cursor);
    const match = before.match(/([a-zA-Z_$][\w$.]*)\s*$/);
    if (!match) { setCompletions([]); return; }
    const prefix = match[1];
    const items = getCompletions(prefix);
    if (items.length > 0) {
      setCompletions(items);
      setSelectedCompletion(0);
      const lines = before.split('\n');
      const line = lines.length;
      const col = lines[lines.length - 1].length;
      setCompletionPos({ top: line * 20, left: col * 8.4 });
    } else {
      setCompletions([]);
    }
  }

  function applyCompletion(item: string) {
    const ta = textareaRef.current;
    if (!ta) return;
    const cursor = ta.selectionStart;
    const before = content.substring(0, cursor);
    const after = content.substring(cursor);
    const match = before.match(/\.([a-zA-Z_$][\w]*)$/);
    const keywordMatch = before.match(/([a-zA-Z_$][\w]*)$/);
    let replaceStart = cursor;
    if (match) {
      replaceStart = cursor - match[1].length;
    } else if (keywordMatch) {
      replaceStart = cursor - keywordMatch[1].length;
    }
    const insertText = item;
    const newContent = content.substring(0, replaceStart) + insertText + after;
    setContent(newContent);
    setCompletions([]);
    const newCursor = replaceStart + insertText.length;
    requestAnimationFrame(() => { ta.selectionStart = ta.selectionEnd = newCursor; ta.focus(); });
  }

  function handleScroll() {
    if (highlightRef.current && textareaRef.current) {
      highlightRef.current.scrollTop = textareaRef.current.scrollTop;
      highlightRef.current.scrollLeft = textareaRef.current.scrollLeft;
    }
  }

  const lines = content.split('\n');

  if (loading) return <div className="script-loading">加载中...</div>;

  return <div className="script-editor">
    <div className="script-toolbar">
      <span className="script-path">{scriptPath}{isDirty && <span className="dirty-dot">●</span>}</span>
      <button onClick={save} disabled={saving || !isDirty}>{saving ? '保存中' : '保存'}</button>
    </div>
    <div className="editor-container">
      <div className="line-numbers">{lines.map((_, i) => <div key={i}>{i + 1}</div>)}</div>
      <div className="editor-wrapper">
        <pre ref={highlightRef} className="editor-highlight" aria-hidden="true"><code dangerouslySetInnerHTML={{ __html: highlightJS(content) }} /></pre>
        <textarea ref={textareaRef} className="editor-input" value={content} onChange={handleInput} onKeyDown={handleKeyDown} onScroll={handleScroll} spellCheck={false} autoComplete="off" autoCorrect="off" autoCapitalize="off" />
        {completions.length > 0 && completionPos && <div className="completion-popup" style={{ top: completionPos.top + 24, left: completionPos.left + 48 }}>
          {completions.map((item, i) => <div key={item} className={`completion-item ${i === selectedCompletion ? 'selected' : ''}`} onMouseDown={(e) => { e.preventDefault(); applyCompletion(item); }}>{item}</div>)}
        </div>}
      </div>
    </div>
  </div>;
}

function highlightJS(code: string): string {
  const tokens: { start: number; end: number; cls: string }[] = [];
  const src = code;

  // 多行注释
  for (const m of src.matchAll(/\/\*[\s\S]*?\*\//g)) {
    tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-comment' });
  }
  // 单行注释
  for (const m of src.matchAll(/\/\/[^\n]*/g)) {
    if (!tokens.some(t => m.index! >= t.start && m.index! < t.end)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-comment' });
    }
  }
  // 字符串
  for (const m of src.matchAll(/(["'`])(?:(?!\1|\\).|\\.)*?\1/g)) {
    if (!tokens.some(t => m.index! >= t.start && m.index! < t.end)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-string' });
    }
  }
  // 数字
  for (const m of src.matchAll(/\b(\d+\.?\d*)\b/g)) {
    if (!tokens.some(t => m.index! >= t.start && m.index! < t.end)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-number' });
    }
  }
  // 关键字
  const kwRe = /\b(function|const|let|var|if|else|return|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|typeof|instanceof|in|of|class|extends|import|export|default|this|true|false|null|undefined|void|delete|yield|await|async)\b/g;
  for (const m of src.matchAll(kwRe)) {
    if (!tokens.some(t => m.index! >= t.start && m.index! < t.end)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-keyword' });
    }
  }
  // emaki
  for (const m of src.matchAll(/\b(emaki)\b/g)) {
    if (!tokens.some(t => m.index! >= t.start && m.index! < t.end)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-emaki' });
    }
  }

  tokens.sort((a, b) => a.start - b.start);

  let result = '';
  let cursor = 0;
  for (const t of tokens) {
    if (t.start < cursor) continue;
    if (t.start > cursor) result += esc(src.substring(cursor, t.start));
    result += `<span class="${t.cls}">${esc(src.substring(t.start, t.end))}</span>`;
    cursor = t.end;
  }
  if (cursor < src.length) result += esc(src.substring(cursor));
  return result;
}

function esc(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

const COMPLETIONS: Record<string, string[]> = {
  'emaki': ['logger', 'player', 'item', 'state', 'text', 'random', 'action', 'context'],
  'emaki.logger': ['info(msg)', 'warn(msg)', 'error(msg)'],
  'emaki.player': ['exists()', 'name()', 'sendMessage(msg)', 'health()', 'setHealth(value)', 'location()', 'hasPermission(perm)', 'uuid()'],
  'emaki.item': ['id()', 'amount()', 'hasTag(key)', 'getTag(key)', 'setTag(key, value)', 'type()'],
  'emaki.state': ['get(key)', 'set(key, value)', 'has(key)', 'remove(key)'],
  'emaki.context': ['phase()', 'plugin()', 'trigger()'],
  'emaki.text': ['color(text)', 'strip(text)'],
  'emaki.random': ['nextInt(bound)', 'nextDouble()', 'chance(percent)'],
  'emaki.action': ['dispatch(actionLine)'],
  'console': ['log(msg)', 'warn(msg)', 'error(msg)', 'info(msg)'],
  'Math': ['abs(x)', 'ceil(x)', 'floor(x)', 'round(x)', 'max(...values)', 'min(...values)', 'random()', 'pow(base, exp)', 'sqrt(x)', 'PI', 'E'],
  'JSON': ['parse(text)', 'stringify(value)', 'stringify(value, null, 2)'],
  'Object': ['keys(obj)', 'values(obj)', 'entries(obj)', 'assign(target, ...sources)', 'freeze(obj)'],
  'Array': ['isArray(value)', 'from(arrayLike)'],
  'String': ['fromCharCode(code)'],
  'Number': ['parseInt(str)', 'parseFloat(str)', 'isNaN(value)', 'isFinite(value)'],
};

const KEYWORD_COMPLETIONS = ['function', 'const', 'let', 'var', 'if', 'else', 'for', 'while', 'do', 'switch', 'case', 'break', 'continue', 'return', 'try', 'catch', 'finally', 'throw', 'new', 'typeof', 'instanceof', 'class', 'extends', 'import', 'export', 'async', 'await', 'yield', 'true', 'false', 'null', 'undefined', 'this', 'console', 'Math', 'JSON', 'Object', 'Array', 'String', 'Number', 'Date', 'RegExp', 'Map', 'Set', 'Promise', 'parseInt', 'parseFloat', 'isNaN', 'isFinite', 'setTimeout', 'clearTimeout', 'emaki'];

function getCompletions(prefix: string): string[] {
  const dotIndex = prefix.lastIndexOf('.');
  if (dotIndex > 0) {
    const obj = prefix.substring(0, dotIndex);
    const partial = prefix.substring(dotIndex + 1).toLowerCase();
    const methods = COMPLETIONS[obj];
    if (methods) {
      return partial ? methods.filter(m => m.toLowerCase().startsWith(partial)) : methods;
    }
    return [];
  }
  const lower = prefix.toLowerCase();
  if (lower.length < 2) return [];
  return KEYWORD_COMPLETIONS.filter(k => k.toLowerCase().startsWith(lower) && k.toLowerCase() !== lower).slice(0, 12);
}

function sameSelection(a: Selection | null, b: Selection) { return a?.moduleId === b.moduleId && a.fileId === b.fileId && (a.scriptPath ?? '') === (b.scriptPath ?? ''); }
function readTheme(): ColorTheme { const saved = localStorage.getItem('emaki-color-theme'); return COLOR_THEMES.some((entry) => entry.id === saved) ? saved as ColorTheme : 'dark'; }
function Icon({ svg }: { svg: string }) { return <span className="module-icon" dangerouslySetInnerHTML={{ __html: svg }} />; }
function ThemeIcon({ theme }: { theme: ColorTheme }) {
  if (theme === 'light') {
    return <svg className="theme-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path fillRule="evenodd" d="M8 1.2a.7.7 0 0 1 .7.7v1.02a.7.7 0 1 1-1.4 0V1.9a.7.7 0 0 1 .7-.7Zm4.81 1.99a.7.7 0 0 1 0 .99l-.72.72a.7.7 0 1 1-.99-.99l.72-.72a.7.7 0 0 1 .99 0ZM5.08 8a2.92 2.92 0 1 1 5.84 0 2.92 2.92 0 0 1-5.84 0Zm8 .7a.7.7 0 1 0 0-1.4h-1.02a.7.7 0 1 0 0 1.4h1.02Zm-.27 3.12a.7.7 0 0 1-.99.99l-.72-.72a.7.7 0 1 1 .99-.99l.72.72ZM8 12.38a.7.7 0 0 1 .7.7v1.02a.7.7 0 1 1-1.4 0v-1.02a.7.7 0 0 1 .7-.7ZM4.9 12.09a.7.7 0 1 0-.99-.99l-.72.72a.7.7 0 1 0 .99.99l.72-.72ZM3.94 8.7a.7.7 0 0 0 0-1.4H2.92a.7.7 0 0 0 0 1.4h1.02Zm.96-3.8a.7.7 0 0 1-.99 0l-.72-.72a.7.7 0 1 1 .99-.99l.72.72a.7.7 0 0 1 0 .99Z" /></svg>;
  }
  return <svg className="theme-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M12.92 9.66a.64.64 0 0 1 .78.8A6.28 6.28 0 1 1 5.54 2.3a.64.64 0 0 1 .8.78 5.32 5.32 0 0 0 6.58 6.58Z" /></svg>;
}
function normalizeKind(kind: string) { return String(kind).toUpperCase(); }
function isKind(kind: string, expected: string) { return normalizeKind(kind) === expected; }
function fileKindLabel(kind: string) {
  const normalized = normalizeKind(kind);
  if (normalized === 'CONFIG') return '配置';
  if (normalized === 'GUI') return 'GUI';
  if (normalized === 'ITEM') return '物品';
  if (normalized === 'SCRIPT') return '脚本';
  return '文件';
}
function draftKey(moduleId: string, path: string) { return `${moduleId}::${path}`; }
function valuesEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (typeof a !== typeof b) return false;
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((v, i) => valuesEqual(v, b[i]));
  if (isObjectLike(a) && isObjectLike(b)) return JSON.stringify(a) === JSON.stringify(b);
  return String(a) === String(b);
}
function isObjectLike(v: unknown) { return typeof v === 'object' && v !== null; }
function parseListValue(original: unknown, text: string) { if (isObjectLike(original)) { try { return JSON.parse(text); } catch { return text; } } return text; }
function str(v: unknown): string { if (v == null) return ''; if (typeof v === 'object') try { return JSON.stringify(v, null, 2); } catch { return ''; } return String(v); }
function firstSelection(r: WebRegistry): Selection | null { const m = r.modules[0]; return m?.files[0] ? { moduleId: m.id, fileId: m.files[0].id } : null; }
