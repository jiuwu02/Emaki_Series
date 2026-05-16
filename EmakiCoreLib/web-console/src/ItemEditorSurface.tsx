import { useEffect, useMemo, useState } from 'react';
import type { ApiClient, ActionTypesResult } from './api';
import { ActionGroup, Button, ActionsEditor, MiniText, PropRow, SectionHead, StringListEditor, parseLoreActions, parseNameActions, serializeActions } from './components';
import { asList, asRecord, asStringList, displaySource, firstItemSource, materialFromItemSource, setDeepValue, type AnyMap } from './itemEditor';
import { materialShortName, materialUrls, textValue } from './lib';
import type { ItemPreviewResult, WebEditorDescriptor, WebEditorField, WebEditorSection, WebRegistryFile, WebRegistryModule } from './types';
import { serializeItemYaml } from './itemEditor';

type Props = { module: WebRegistryModule; file: WebRegistryFile; api: ApiClient; childPath?: string; refreshKey?: number; editor?: WebEditorDescriptor; onReload?: () => void };

const DEFAULT_BASE_NAME = '<gray>预览装备</gray>';
const DEFAULT_BASE_LORE = '<gray>原始装备 Lore</gray>';

export function ItemEditorSurface({ module, file, api, childPath, refreshKey = 0, editor, onReload }: Props) {
  const [data, setData] = useState<AnyMap>({});
  const [originalContent, setOriginalContent] = useState('');
  const [preview, setPreview] = useState<ItemPreviewResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [viewSource, setViewSource] = useState(false);
  const [sourceText, setSourceText] = useState('');
  const [actionTypesResult, setActionTypesResult] = useState<ActionTypesResult | null>(null);
  const [dirty, setDirty] = useState(false);

  const filePath = childPath || file.path;
  const baseName = editor?.baseName ?? DEFAULT_BASE_NAME;
  const baseLore = editor?.baseLore ?? [DEFAULT_BASE_LORE];
  const sections = useMemo(() => editor?.sections?.length ? editor.sections : defaultSections(), [editor]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.readItem(module.id, filePath).then(doc => {
      if (cancelled) return;
      setData(doc.data as AnyMap);
      setOriginalContent(doc.content);
      setSourceText(doc.content);
      setDirty(false);
      setLoading(false);
    }).catch(err => {
      if (cancelled) return;
      setError(String(err?.message ?? err));
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, [api, module.id, filePath, refreshKey]);

  useEffect(() => {
    api.actionTypes().then(setActionTypesResult).catch(() => {});
  }, [api]);

  useEffect(() => {
    if (loading) return;
    const content = viewSource ? sourceText : serializeItemYaml(data);
    const timer = setTimeout(() => {
      api.previewItem(content, 1, baseName, baseLore as string[]).then(setPreview).catch(() => setPreview(null));
    }, 300);
    return () => clearTimeout(timer);
  }, [api, data, sourceText, viewSource, loading, baseName, baseLore]);

  const setField = (path: string, value: unknown) => {
    setData(prev => setDeepValue(prev, path.split('.'), value));
    setDirty(true);
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const content = viewSource ? sourceText : serializeItemYaml(data);
      await api.saveItem(module.id, filePath, content);
      setOriginalContent(content);
      setDirty(false);
    } catch (err: any) {
      setError(err?.message ?? '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const switchToSource = () => {
    if (!viewSource) setSourceText(serializeItemYaml(data));
    setViewSource(!viewSource);
  };

  if (loading) return <div className="ie-surface"><div className="ie-loading"><div className="ie-skeleton" aria-label="加载中"><div className="ie-skeleton-line" style={{ width: '60%' }} /><div className="ie-skeleton-line" style={{ width: '80%' }} /><div className="ie-skeleton-line" style={{ width: '45%' }} /><div className="ie-skeleton-line" style={{ width: '70%' }} /></div></div></div>;
  if (error && !data) return <div className="ie-surface"><div className="ie-error" role="alert">{error}</div></div>;

  return (
    <div className="ie-surface" data-dirty={dirty || undefined}>
      <div className="ie-header">
        <div className="ie-header-left">
          <h1 className="ie-title">{editor?.title ?? file.title ?? '物品编辑器'}</h1>
          {dirty && <span className="ie-dirty-badge">未保存</span>}
        </div>
        <ActionGroup>
          {onReload && <Button size="sm" onClick={onReload}>刷新</Button>}
          <Button size="sm" onClick={switchToSource}>{viewSource ? '可视化' : '源码'}</Button>
          <Button size="sm" variant="primary" ready={dirty} onClick={handleSave} disabled={saving}>{saving ? '保存中...' : '保存'}</Button>
        </ActionGroup>
      </div>

      {error && <div className="ie-error" role="alert">{error}</div>}

      {viewSource ? (
        <div className="ie-source-wrap">
          <textarea className="ie-source" value={sourceText} onChange={e => { setSourceText(e.target.value); setDirty(true); }} rows={28} spellCheck={false} />
        </div>
      ) : (
        <div className="ie-workbench">
          <GenericPreviewPane data={data} preview={preview} />
          <div className="ie-props-scroll">
            <div className="ie-props">
              {sections.map(section => (
                <div key={section.title}>
                  <SectionHead title={section.title} />
                  {section.comment && <p className="muted-copy">{section.comment}</p>}
                  {section.fields.map(field => <FieldEditor key={field.path} field={field} data={data} setField={setField} actionTypesResult={actionTypesResult} />)}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function FieldEditor({ field, data, setField, actionTypesResult }: { field: WebEditorField; data: AnyMap; setField: (path: string, value: unknown) => void; actionTypesResult: ActionTypesResult | null }) {
  const value = getDeepValue(data, field.path);
  const label = field.label || field.path;
  const type = field.type || 'text';

  if (type === 'number') {
    return <PropRow label={label} wide={field.wide}><input type="number" value={textValue(value)} onChange={e => setField(field.path, e.target.value === '' ? undefined : Number(e.target.value))} /></PropRow>;
  }
  if (type === 'enum' && field.options?.length) {
    return <PropRow label={label} wide={field.wide}><select value={textValue(value)} onChange={e => setField(field.path, e.target.value)}>{field.options.map(option => <option key={option} value={option}>{option}</option>)}</select></PropRow>;
  }
  if (type === 'textarea') {
    return <PropRow label={label} wide><textarea rows={field.rows ?? 4} value={textValue(value)} onChange={e => setField(field.path, e.target.value)} placeholder={field.placeholder} /></PropRow>;
  }
  if (type === 'stringList') {
    return <PropRow label={label} wide><StringListEditor items={asStringList(value)} onChange={items => setField(field.path, items)} placeholder={field.placeholder} /></PropRow>;
  }
  if (type === 'actions') {
    const mode = field.path.toLowerCase().includes('lore') ? 'lore' : 'name';
    const actions = mode === 'lore' ? parseLoreActions({ lore_actions: value }) : parseNameActions({ name_actions: value });
    return <PropRow label={label} wide><ActionsEditor actions={actions} onChange={a => setField(field.path, serializeActions(a))} actionTypes={mode === 'lore' ? actionTypesResult?.loreActions ?? [] : actionTypesResult?.nameActions ?? []} mode={mode} /></PropRow>;
  }
  if (type === 'json') {
    return <PropRow label={label} wide><textarea rows={field.rows ?? 6} value={JSON.stringify(value ?? null, null, 2)} onChange={e => { try { setField(field.path, JSON.parse(e.target.value)); } catch {} }} /></PropRow>;
  }
  return <PropRow label={label} wide={field.wide}><input type="text" value={textValue(value)} onChange={e => setField(field.path, e.target.value)} placeholder={field.placeholder} /></PropRow>;
}

function GenericPreviewPane({ data, preview }: { data: AnyMap; preview: ItemPreviewResult | null }) {
  const source = firstItemSource(data.item_sources ?? asRecord(data.match).item_sources ?? preview?.material);
  const material = materialFromItemSource(source || data.material || preview?.material);
  const urls = materialUrls(material);
  const [imgFailed, setImgFailed] = useState(false);
  useEffect(() => setImgFailed(false), [material]);

  return (
    <div className="ie-preview" role="complementary" aria-label="物品预览">
      <div className="ie-preview-icon">
        {urls.length > 0 && !imgFailed ? <img src={urls[0]} alt={material || '物品图标'} onError={e => { const t = e.currentTarget; const next = urls[urls.indexOf(t.src) + 1]; if (next) t.src = next; else setImgFailed(true); }} /> : <span className="ie-preview-fallback">{materialShortName(material) || '?'}</span>}
      </div>
      <div className="ie-preview-meta">
        <span className="ie-preview-kind">通用物品</span>
        {Boolean(preview?.id || data.id) && <code className="ie-preview-id">{textValue(preview?.id ?? data.id)}</code>}
        <span className="ie-preview-source">{displaySource(source || material)}</span>
      </div>
      <div className="ie-tooltip">
        {Boolean(preview?.displayName || data.display_name) && <div className="ie-tooltip-name"><MiniText value={preview?.displayName ?? data.display_name} /></div>}
        {(preview?.lore ?? asStringList(data.lore)).map((line, i) => <div className="ie-tooltip-line" key={i}><MiniText value={line} /></div>)}
        {!preview?.displayName && !data.display_name && !(preview?.lore ?? asList(data.lore)).length && <span className="ie-tooltip-empty">暂无预览</span>}
      </div>
    </div>
  );
}

function getDeepValue(source: AnyMap, path: string): unknown {
  return path.split('.').reduce<unknown>((current, key) => asRecord(current)[key], source);
}

function defaultSections(): WebEditorSection[] {
  return [{
    title: '基础',
    fields: [
      { path: 'id', label: 'ID', type: 'text' },
      { path: 'material', label: '材质', type: 'text' },
      { path: 'display_name', label: '显示名称', type: 'text' },
      { path: 'lore', label: 'Lore', type: 'stringList', wide: true },
      { path: 'name_actions', label: 'Name Actions', type: 'actions', wide: true },
      { path: 'lore_actions', label: 'Lore Actions', type: 'actions', wide: true }
    ]
  }];
}
