import { useEffect, useMemo, useState } from 'react';
import { type ApiClient } from '../../api';
import { t } from '../../i18n';
import { parseYaml, serializeYaml, setDeepValue } from '../../lib';
import { getSourceDocumentAdapter } from '../../registry';
import type { WebConfigNode, WebRegistryFile, WebRegistryModule } from '../../types';

export type ConfigToast = { tone: 'ok' | 'bad'; text: string } | null;
export type SourceEditController = { paths: Set<string>; update: (node: WebConfigNode, next: unknown) => void };

export type ConfigSourceDocument = ReturnType<typeof useConfigSourceDocument>;

export function useConfigSourceDocument({ module, file, childPath, api, refreshKey, setToast }: { module: WebRegistryModule; file: WebRegistryFile; childPath?: string; api: ApiClient; refreshKey: number; setToast: (toast: ConfigToast) => void }) {
  const [content, setContent] = useState('');
  const [original, setOriginal] = useState('');
  const [revision, setRevision] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const editor = file.editorId ? { id: file.editorId } : undefined;
  const adapter = getSourceDocumentAdapter(file, editor);
  const sourcePath = childPath || file.path;
  const context = useMemo(() => ({ module, file, childPath, path: sourcePath, editor }), [module.id, file.id, childPath, sourcePath, editor?.id]);

  async function reload(announce = true) {
    if (!adapter) return;
    setLoading(true);
    setError(null);
    try {
      const doc = await adapter.read(api, context);
      setContent(doc.content);
      setOriginal(doc.content);
      setRevision(doc.revision);
      if (announce) setToast({ tone: 'ok', text: t('core.toast.reloaded') });
    } catch (err) {
      const message = err instanceof Error ? err.message : t('core.toast.refreshFailed');
      setError(message);
      setToast({ tone: 'bad', text: message });
    } finally {
      setLoading(false);
    }
  }

  async function save(afterSave?: () => void | Promise<void>) {
    if (!adapter || error) return;
    setSaving(true);
    try {
      const result = await adapter.save(api, context, content, revision);
      setOriginal(content);
      setRevision(result.revision ?? revision);
      await afterSave?.();
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: 1 }) });
    } catch (err) {
      const message = userFacingSaveError(err);
      setError(message);
      setToast({ tone: 'bad', text: message });
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => {
    void reload(false);
  }, [api, module.id, file.id, childPath, refreshKey]);

  return {
    content,
    original,
    dirty: content !== original,
    loading,
    saving,
    error,
    update: (next: string) => {
      setContent(current => current === next ? current : next);
      setError(null);
    },
    reload,
    save
  };
}

export function updateConfigSourceValue(source: ConfigSourceDocument, path: string, nextValue: unknown, setToast: (toast: ConfigToast) => void) {
  if (source.loading) {
    setToast({ tone: 'bad', text: t('core.config.sourceLoading') });
    return;
  }
  if (source.error) {
    setToast({ tone: 'bad', text: source.error });
    return;
  }
  try {
    const data = parseYaml(source.content || '{}');
    const nextData = setDeepValue(data, path.split('.'), nextValue);
    source.update(serializeYaml(nextData));
  } catch (err) {
    setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
  }
}

const INTERNAL_ERROR_PATTERNS = ['.tmp', 'FileSystemException', 'AccessDeniedException', 'AtomicMoveNotSupportedException', 'NoSuchFileException', 'DirectoryNotEmptyException'];

export function userFacingSaveError(err: unknown): string {
  const raw = err instanceof Error ? err.message : String(err ?? '');
  if (INTERNAL_ERROR_PATTERNS.some(pattern => raw.includes(pattern))) {
    return t('core.toast.saveFailed');
  }
  return raw || t('core.toast.saveFailed');
}
