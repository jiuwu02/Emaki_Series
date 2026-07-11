import { memo, startTransition, useEffect, useMemo, useRef, useState, type CSSProperties, type DependencyList } from 'react';
import { DisclosureChevron } from '../../components';
import { getLocale, t } from '../../i18n';
import { configNodeDisplayComment as resolveConfigNodeComment, valuesEqual } from '../../lib';
import type { SurfaceOutlineItem, SurfaceOutlineState } from '../../registry';
import type { WebConfigNode } from '../../types';
import { draftKey, draftSignatureForScope, type ConfigDraftScope, type DraftMap, type DraftValueSetter } from './ConfigDraftRuntime';
import { isWideConfigNode, renderControl } from './ConfigFieldRuntime';
import { buildNodeChangeState, buildNodeIndex, configNodeDisplayLabel, configSectionHasMeaningfulValue, type ConfigNodeChangeState, type ConfigNodeIndex, type NodeGroup } from './ConfigNodeRuntime';
import type { SourceEditController } from './ConfigSourceRuntime';

const CONFIG_GROUP_PAGINATION = {
  initialGroups: 10,
  batchSize: 12,
} as const;

const CONFIG_SECTION_PAGINATION = {
  initialGroups: 8,
  batchSize: 10,
} as const;

const CONFIG_LAZY_SECTION_THRESHOLD = 10;

export const ConfigNodeTree = memo(function ConfigNodeTree({ scope, nodes, outlineTitle, outlineSubtitle, drafts, setDraftValue, onCreateChild, onDeleteObject, sourceEdit, deletedPaths, setSurfaceOutline }: { scope: ConfigDraftScope; nodes: WebConfigNode[]; outlineTitle: string; outlineSubtitle: string; drafts: DraftMap; setDraftValue: DraftValueSetter; onCreateChild: (node: WebConfigNode) => void; onDeleteObject: (node: WebConfigNode) => void; sourceEdit?: SourceEditController; deletedPaths?: Set<string>; setSurfaceOutline: (state: SurfaceOutlineState) => void }) {
  const nodeIndex = useMemo(() => buildNodeIndex(nodes), [nodes]);
  const scopeDraftKey = useMemo(() => draftSignatureForScope(drafts, scope), [drafts, scope.moduleId, scope.fileId, scope.filePath]);
  const changeState = useMemo(() => buildNodeChangeState(scope, nodes, drafts, sourceEdit?.paths, deletedPaths), [scope.moduleId, scope.fileId, scope.filePath, nodes, scopeDraftKey, sourceEdit?.paths, deletedPaths]);
  const groups = nodeIndex.groupsByParent.get('') ?? [];
  const visibleCount = useProgressiveCount(groups.length, CONFIG_GROUP_PAGINATION.initialGroups, CONFIG_GROUP_PAGINATION.batchSize, [scope.moduleId, scope.fileId, scope.filePath, groups.length]);
  const visibleGroups = groups.slice(0, visibleCount);
  const locale = getLocale();

  useEffect(() => {
    setSurfaceOutline({
      title: outlineTitle || t('core.outline.title'),
      subtitle: outlineSubtitle,
      emptyText: nodes.length ? t('core.outline.empty') : t('core.empty.noConfigNodes'),
      items: groups.map(group => outlineItemForGroup(scope, group, changeState, nodeIndex))
    });
    return () => setSurfaceOutline(null);
  }, [outlineTitle, outlineSubtitle, groups, nodeIndex, changeState, nodes.length, scope.moduleId, scope.fileId, scope.filePath, locale, setSurfaceOutline]);

  if (!nodes.length) return <div className="script-placeholder" role="status">{t('core.empty.noConfigNodes')}</div>;

  return <div className="node-grid">{visibleGroups.map((group, index) => {
    if (group.type === 'leaf') {
      return <ConfigNodeView key={group.node.path} scope={scope} node={group.node} drafts={drafts} setDraftValue={setDraftValue} sourceEdit={sourceEdit} changed={changeState.changedPaths.has(group.node.path)} deletable={false} onDeleteObject={onDeleteObject} />;
    }
    return <ConfigNodeSection key={group.node.path} scope={scope} node={group.node} nodeIndex={nodeIndex} changeState={changeState} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={onCreateChild} onDeleteObject={onDeleteObject} sourceEdit={sourceEdit} deletable={false} depth={0} isLast={index === groups.length - 1} />;
  })}</div>;
});

function configNodeDisplayComment(scope: ConfigDraftScope, node: WebConfigNode): string {
  return resolveConfigNodeComment(scope.moduleId, node.path, node.comment);
}

function outlineItemForGroup(scope: ConfigDraftScope, group: NodeGroup, changeState: ConfigNodeChangeState, nodeIndex: ConfigNodeIndex): SurfaceOutlineItem {
  const node = group.node;
  const childCount = group.type === 'section' ? (nodeIndex.descendantsByPath.get(node.path)?.length ?? group.children.length) : 0;
  const changedCount = Math.max(changeState.descendantCounts.get(node.path) ?? 0, changeState.changedPaths.has(node.path) ? 1 : 0);
  return {
    path: node.path,
    label: configNodeDisplayLabel(scope, node),
    type: node.type,
    childCount,
    changedCount,
    changed: changedCount > 0
  };
}

const ConfigNodeSection = memo(function ConfigNodeSection({ scope, node, nodeIndex, changeState, drafts, setDraftValue, onCreateChild, onDeleteObject, sourceEdit, deletable, depth = 0, isLast = true, branch }: { scope: ConfigDraftScope; node: WebConfigNode; nodeIndex: ConfigNodeIndex; changeState: ConfigNodeChangeState; drafts: DraftMap; setDraftValue: DraftValueSetter; onCreateChild: (node: WebConfigNode) => void; onDeleteObject: (node: WebConfigNode) => void; sourceEdit?: SourceEditController; deletable: boolean; depth?: number; isLast?: boolean; branch?: 'tee' | 'elbow' }) {
  const groups = nodeIndex.groupsByParent.get(node.path) ?? [];
  const sectionChanged = changeState.changedPaths.has(node.path);
  const changedInGroup = changeState.descendantCounts.get(node.path) ?? 0;
  const defaultCollapsed = changedInGroup === 0 && !sectionChanged && (!configSectionHasMeaningfulValue(node, nodeIndex) || groups.length > CONFIG_LAZY_SECTION_THRESHOLD);
  const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
  const [shouldRenderBody, setShouldRenderBody] = useState(!defaultCollapsed);
  const bodyTimer = useRef<number | null>(null);
  const visibleCount = useProgressiveCount(shouldRenderBody && !isCollapsed ? groups.length : 0, CONFIG_SECTION_PAGINATION.initialGroups, CONFIG_SECTION_PAGINATION.batchSize, [scope.moduleId, scope.fileId, scope.filePath, node.path, shouldRenderBody, isCollapsed, groups.length]);
  const visibleGroups = groups.slice(0, visibleCount);
  const hasSiblingBranches = groups.length > 1;
  const groupLabel = configNodeDisplayLabel(scope, node);

  useEffect(() => () => {
    if (bodyTimer.current !== null) window.clearTimeout(bodyTimer.current);
  }, []);

  useEffect(() => {
    if (bodyTimer.current !== null) {
      window.clearTimeout(bodyTimer.current);
      bodyTimer.current = null;
    }
    setIsCollapsed(defaultCollapsed);
    setShouldRenderBody(!defaultCollapsed);
  }, [scope.moduleId, scope.fileId, scope.filePath, node.path, defaultCollapsed]);

  const toggleSection = () => {
    if (bodyTimer.current !== null) window.clearTimeout(bodyTimer.current);
    if (isCollapsed) {
      setIsCollapsed(false);
      bodyTimer.current = window.setTimeout(() => {
        bodyTimer.current = null;
        startTransition(() => setShouldRenderBody(true));
      }, 35);
    } else {
      setIsCollapsed(true);
      bodyTimer.current = window.setTimeout(() => {
        bodyTimer.current = null;
        startTransition(() => setShouldRenderBody(false));
      }, 120);
    }
  };

  return <div className={`node-section ${isCollapsed ? 'collapsed' : 'expanded'}${depth > 0 ? ' node-section--nested' : ''}`} data-node-depth={depth} data-config-node-path={node.path}>
    <div className={`node-section-header ${isCollapsed ? 'collapsed' : ''} ${sectionChanged ? 'changed' : ''}`}>
      {branch && <IndentGuide branch={branch} />}
      <button type="button" className="node-section-toggle" onClick={toggleSection} aria-expanded={!isCollapsed}>
        <DisclosureChevron open={!isCollapsed} className="section-arrow" />
        <strong>{groupLabel}</strong>
        <code>{node.path}</code>
        <span className="section-comment">{configNodeDisplayComment(scope, node)}</span>
      </button>
      <div className="node-section-actions">
        {node.creatableChildren && <button type="button" className="node-section-create" onClick={() => onCreateChild(node)}>+ {t('core.config.create')}</button>}
        {deletable && <button type="button" className="node-section-delete" onClick={() => onDeleteObject(node)}>{t('core.config.deleteObject')}</button>}
      </div>
      <span className="section-meta">{(sectionChanged || changedInGroup > 0) && <span className="section-badge">{Math.max(changedInGroup, sectionChanged ? 1 : 0)}</span>}{t('core.config.groupItems', { count: groups.length })}</span>
    </div>
    {shouldRenderBody && <div className="node-section-body" hidden={isCollapsed}>{visibleGroups.map((group, index) => {
      const absoluteIndex = index;
      const branch = absoluteIndex === groups.length - 1 ? 'elbow' : 'tee';
      return <div className="node-section-child" key={group.node.path} style={{ '--config-child-depth': depth + 1 } as CSSProperties}>
        {group.type === 'section'
          ? <ConfigNodeSection scope={scope} node={group.node} nodeIndex={nodeIndex} changeState={changeState} drafts={drafts} setDraftValue={setDraftValue} onCreateChild={onCreateChild} onDeleteObject={onDeleteObject} sourceEdit={sourceEdit} deletable={node.creatableChildren === true} depth={depth + 1} isLast={absoluteIndex === groups.length - 1} branch={hasSiblingBranches ? branch : undefined} />
          : <ConfigNodeView scope={scope} node={group.node} drafts={drafts} setDraftValue={setDraftValue} sourceEdit={sourceEdit} changed={changeState.changedPaths.has(group.node.path)} deletable={node.creatableChildren === true} onDeleteObject={onDeleteObject} branch={hasSiblingBranches ? branch : undefined} />}
      </div>;
    })}</div>}
  </div>;
});

function IndentGuide({ branch }: { branch: 'tee' | 'elbow' }) {
  return <svg className={`indent-guide indent-guide--${branch}`} viewBox="0 0 16 20" aria-hidden="true" focusable="false">
    <path d={branch === 'tee' ? 'M6 0v20M6 10h8' : 'M6 0v10h8'} />
  </svg>;
}

function ConfigNodeView({ scope, node, drafts, setDraftValue, sourceEdit, changed = false, deletable = false, onDeleteObject, branch }: { scope: ConfigDraftScope; node: WebConfigNode; drafts: DraftMap; setDraftValue: DraftValueSetter; sourceEdit?: SourceEditController; changed?: boolean; deletable?: boolean; onDeleteObject?: (node: WebConfigNode) => void; branch?: 'tee' | 'elbow' }) {
  const key = draftKey(scope, node.path);
  const sourceEdited = sourceEdit?.paths.has(node.path) === true;
  const committedValue = key in drafts ? drafts[key] : node.value;
  const [localValue, setLocalValue] = useState(committedValue);
  const commitTimer = useRef<number | null>(null);
  const pendingValue = useRef<unknown>(committedValue);

  useEffect(() => {
    if (commitTimer.current !== null) return;
    pendingValue.current = committedValue;
    setLocalValue(committedValue);
  }, [committedValue]);

  useEffect(() => () => {
    if (commitTimer.current !== null) window.clearTimeout(commitTimer.current);
  }, []);

  const commitValue = (next: unknown) => {
    startTransition(() => {
      if (valuesEqual(next, node.value)) {
        setDraftValue(scope, node, node.value);
        return;
      }
      if (sourceEdited) sourceEdit?.update(node, next);
      else setDraftValue(scope, node, next);
    });
  };

  const setValue = (next: unknown) => {
    pendingValue.current = next;
    setLocalValue(next);
    if (commitTimer.current !== null) window.clearTimeout(commitTimer.current);
    commitTimer.current = window.setTimeout(() => {
      commitTimer.current = null;
      commitValue(pendingValue.current);
    }, 90);
  };
  const isWide = isWideConfigNode(node);
  const label = configNodeDisplayLabel(scope, node);
  return <div className={`node ${changed || sourceEdited ? 'changed' : ''} ${isWide ? 'node-wide' : ''}`} data-config-node-path={node.path}>{branch && <IndentGuide branch={branch} />}<div className="node-meta"><strong>{label}</strong><code>{node.path}</code><p>{configNodeDisplayComment(scope, node)}</p></div><div className="node-control">{renderControl(node, localValue, setValue, label, scope.moduleId)}{deletable && onDeleteObject && <button type="button" className="node-section-delete" onClick={() => onDeleteObject(node)}>{t('core.config.delete')}</button>}</div></div>;
}

function useProgressiveCount(total: number, initial: number, batch: number, deps: DependencyList): number {
  const [count, setCount] = useState(() => Math.min(total, initial));
  useEffect(() => {
    let cancelled = false;
    setCount(Math.min(total, initial));
    if (total <= initial) return;
    const schedule = typeof window.requestAnimationFrame === 'function'
      ? (callback: () => void) => window.requestAnimationFrame(callback)
      : (callback: () => void) => window.setTimeout(callback, 16);
    const cancel = typeof window.cancelAnimationFrame === 'function'
      ? (id: number) => window.cancelAnimationFrame(id)
      : (id: number) => window.clearTimeout(id);
    let handle = 0;
    const step = () => {
      if (cancelled) return;
      setCount(current => {
        const next = Math.min(total, current + batch);
        if (next < total) handle = schedule(step);
        return next;
      });
    };
    handle = schedule(step);
    return () => {
      cancelled = true;
      if (handle) cancel(handle);
    };
  }, [total, initial, batch, ...deps]);
  return Math.min(Math.max(count, Math.min(total, initial)), total);
}

