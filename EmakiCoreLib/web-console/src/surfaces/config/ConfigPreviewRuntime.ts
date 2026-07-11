import { parseYaml, serializeYaml, setDeepValue } from '../../lib';
import type { WebConfigNode } from '../../types';
import { draftKey, type ConfigDraftScope, type DraftMap } from './ConfigDraftRuntime';
import { parseSafeYaml } from './ConfigObjectRuntime';

export function configPreviewData(sourceContent: string, nodes: WebConfigNode[], scope: ConfigDraftScope, drafts: DraftMap): Record<string, unknown> {
  const parsed = parseSafeYaml(sourceContent || '{}');
  let data: Record<string, unknown> = parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  const changedNodes: WebConfigNode[] = [];
  const fallbackNodes: WebConfigNode[] = [];
  for (const node of nodes) {
    if (node.type === 'object') continue;
    fallbackNodes.push(node);
    if (draftKey(scope, node.path) in drafts) changedNodes.push(node);
  }
  const sourceIsUseful = Object.keys(data).length > 0 || sourceContent.trim() === '';
  const nodesToOverlay = sourceIsUseful ? changedNodes : fallbackNodes;
  for (const node of nodesToOverlay) {
    const key = draftKey(scope, node.path);
    const value = key in drafts ? drafts[key] : node.value;
    data = setDeepValue(data, node.path.split('.'), value);
  }
  return data;
}

export function configSourcePreview(original: string, scope: ConfigDraftScope, changedNodes: WebConfigNode[], drafts: DraftMap): string {
  if (!changedNodes.length) return original;
  try {
    let data = parseYaml(original || '{}');
    for (const node of changedNodes) {
      data = setDeepValue(data, node.path.split('.'), drafts[draftKey(scope, node.path)]);
    }
    return serializeYaml(data);
  } catch {
    return changedNodes.reduce((content, node) => replacePreviewLine(content, node.path, drafts[draftKey(scope, node.path)]), original);
  }
}

function replacePreviewLine(content: string, path: string, value: unknown): string {
  const lines = content.split('\n');
  const leaf = path.includes('.') ? path.slice(path.lastIndexOf('.') + 1) : path;
  const index = lines.findIndex(line => line.trimStart().startsWith(`${leaf}:`));
  const nextLine = `${index >= 0 ? lines[index].match(/^\s*/)?.[0] ?? '' : ''}${leaf}: ${formatYamlScalarPreview(value)}`;
  if (index >= 0) lines[index] = nextLine;
  else lines.push(nextLine);
  return lines.join('\n');
}

function formatYamlScalarPreview(value: unknown): string {
  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return JSON.stringify(value);
}

