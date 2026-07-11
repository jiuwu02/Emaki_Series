import { ApiError, type ApiClient, type RegistryValueChange } from '../../api';
import type { EditorChange } from '../../components';
import type { WebConfigNode } from '../../types';
import { draftKey, type ConfigDraftScope, type DraftMap } from './ConfigDraftRuntime';
import { configNodeDisplayLabel } from './ConfigNodeRuntime';

// Persist all changed config nodes in one file-level request so the backend checks the
// expected revision once, loads YAML once and writes the file once.
export type NodeSaveOutcome = {
  savedPaths: string[];
  revision: number | undefined;
  status: 'ok' | 'conflict' | 'error';
  conflictRevision?: number;
  error?: unknown;
};

export async function saveNodesBatch(api: ApiClient, moduleId: string, filePath: string, nodes: { path: string }[], draftValueFor: (path: string) => unknown, startRevision: number | undefined): Promise<NodeSaveOutcome> {
  const changes: RegistryValueChange[] = nodes.map(node => ({ path: node.path, value: draftValueFor(node.path) }));
  try {
    const result = await api.saveRegistryValues(moduleId, filePath, changes, startRevision);
    return { savedPaths: result.savedPaths?.length ? result.savedPaths : changes.map(change => change.path), revision: result.revision ?? startRevision, status: 'ok' };
  } catch (err) {
    if (isRevisionConflict(err)) {
      return { savedPaths: [], revision: startRevision, status: 'conflict', conflictRevision: revisionFromError(err), error: err };
    }
    return { savedPaths: [], revision: startRevision, status: 'error', error: err };
  }
}

export function configChanges(scope: ConfigDraftScope, nodes: WebConfigNode[], drafts: DraftMap): EditorChange[] {
  return nodes
    .filter(node => node.type !== 'object' && draftKey(scope, node.path) in drafts)
    .map(node => ({ path: node.path, label: configNodeDisplayLabel(scope, node), before: node.value, after: drafts[draftKey(scope, node.path)] }));
}

function isRevisionConflict(err: unknown): boolean {
  return err instanceof ApiError && (err.status === 409 && (err.errorType === 'revision_conflict' || typeof err.data?.revision === 'number'));
}

function revisionFromError(err: unknown): number | undefined {
  if (!(err instanceof ApiError)) return undefined;
  if (typeof err.data?.currentRevision === 'number') return err.data.currentRevision;
  return typeof err.data?.revision === 'number' ? err.data.revision : undefined;
}
