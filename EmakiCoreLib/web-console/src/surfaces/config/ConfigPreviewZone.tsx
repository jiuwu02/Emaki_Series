import { Component, useDeferredValue, useEffect, useMemo, useState, type DependencyList, type ReactNode } from 'react';
import { type ApiClient } from '../../api';
import { InlineError } from '../../components';
import { t } from '../../i18n';
import { getConfigPreview, type ConfigPreviewProps } from '../../registry';
import type { WebConfigNode, WebRegistryFile, WebRegistryModule } from '../../types';
import { draftKey, draftSignatureForScope, type ConfigDraftScope, type DraftMap } from './ConfigDraftRuntime';
import { configPreviewData, configSourcePreview } from './ConfigPreviewRuntime';
import type { ConfigSourceDocument } from './ConfigSourceRuntime';

export function DeferredConfigPreviewZone(props: { module: WebRegistryModule; file: WebRegistryFile; path: string; childPath?: string; nodes: WebConfigNode[]; scope: ConfigDraftScope; drafts: DraftMap; source: ConfigSourceDocument; api: ApiClient }) {
  const ready = useIdleReady([props.module.id, props.file.id, props.path, props.childPath, props.source.loading, props.source.error], 140);
  const deferredDrafts = useDebouncedValue(props.drafts, 140);
  const deferredNodes = useDeferredValue(props.nodes);
  if (!ready) return null;
  return <ConfigPreviewZone {...props} drafts={deferredDrafts} nodes={deferredNodes} />;
}

function ConfigPreviewZone({ module, file, path, childPath, nodes, scope, drafts, source, api }: { module: WebRegistryModule; file: WebRegistryFile; path: string; childPath?: string; nodes: WebConfigNode[]; scope: ConfigDraftScope; drafts: DraftMap; source: ConfigSourceDocument; api: ApiClient }) {
  const registration = getConfigPreview({ moduleId: module.id, kind: file.kind, path });
  const changedDraftKey = useMemo(() => draftSignatureForScope(drafts, scope), [drafts, scope.moduleId, scope.fileId, scope.filePath]);
  const deferredDraftKey = useDeferredValue(changedDraftKey);
  const sourceContent = useMemo(() => {
    if (source.dirty) return source.content;
    if (!registration) return '';
    return configSourcePreview(source.original, scope, nodes.filter(node => node.type !== 'object' && draftKey(scope, node.path) in drafts), drafts);
  }, [registration, source.dirty, source.content, source.original, scope.moduleId, scope.fileId, scope.filePath, nodes, deferredDraftKey]);
  const data = useMemo(() => registration ? configPreviewData(sourceContent, nodes, scope, drafts) : {}, [registration, sourceContent, nodes, scope.moduleId, scope.fileId, scope.filePath, deferredDraftKey]);
  if (!registration) return null;
  const Preview = registration.component;
  const props: ConfigPreviewProps = { module, file, path, childPath, nodes, data, sourceContent, sourceDirty: source.dirty, sourceError: source.error, api };
  return <ConfigPreviewBoundary previewKey={`${module.id}:${path}`}><div className="config-preview-zone"><Preview {...props} /></div></ConfigPreviewBoundary>;
}

type ConfigPreviewBoundaryProps = { previewKey: string; children: ReactNode };
type ConfigPreviewBoundaryState = { error: Error | null; previewKey: string };

class ConfigPreviewBoundary extends Component<ConfigPreviewBoundaryProps, ConfigPreviewBoundaryState> {
  state: ConfigPreviewBoundaryState = { error: null, previewKey: this.props.previewKey };

  static getDerivedStateFromProps(props: ConfigPreviewBoundaryProps, state: ConfigPreviewBoundaryState): Partial<ConfigPreviewBoundaryState> | null {
    return props.previewKey !== state.previewKey ? { error: null, previewKey: props.previewKey } : null;
  }

  static getDerivedStateFromError(error: Error): Partial<ConfigPreviewBoundaryState> {
    return { error };
  }

  render() {
    if (this.state.error) {
      return <div className="config-preview-zone"><InlineError><span>{t('core.configPreview.unavailable', { message: this.state.error.message })}</span></InlineError></div>;
    }
    return this.props.children;
  }
}

function useDebouncedValue<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

function useIdleReady(deps: DependencyList, delay = 120): boolean {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    setReady(false);
    let cancelled = false;
    const win = window as Window & { requestIdleCallback?: (callback: () => void, options?: { timeout: number }) => number; cancelIdleCallback?: (id: number) => void };
    const handle = win.requestIdleCallback
      ? win.requestIdleCallback(() => { if (!cancelled) setReady(true); }, { timeout: delay + 180 })
      : window.setTimeout(() => { if (!cancelled) setReady(true); }, delay);
    return () => {
      cancelled = true;
      if (win.cancelIdleCallback && typeof handle === 'number') win.cancelIdleCallback(handle);
      else window.clearTimeout(handle);
    };
  }, deps);
  return ready;
}

