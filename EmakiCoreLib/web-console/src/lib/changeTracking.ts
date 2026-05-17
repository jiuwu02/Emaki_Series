import type { EditorChange } from '../components';

export type ChangePathMatchMode = 'exact' | 'subtree' | 'field';
export type ChangePathInput = string | Array<string | number | null | undefined>;

export function diffRecords(after: unknown, before: unknown, prefix = '', limit = 18): EditorChange[] {
  if (limit <= 0 || valuesEqual(after, before)) return [];

  if (Array.isArray(after) && Array.isArray(before)) {
    const changes: EditorChange[] = [];
    const length = Math.max(after.length, before.length);
    for (let index = 0; index < length && changes.length < limit; index += 1) {
      changes.push(...diffRecords(after[index], before[index], appendPath(prefix, index), limit - changes.length));
    }
    return changes;
  }

  if (isPlainObject(after) && isPlainObject(before)) {
    const changes: EditorChange[] = [];
    const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])];
    for (const key of keys) {
      if (changes.length >= limit) break;
      changes.push(...diffRecords(after[key], before[key], appendPath(prefix, key), limit - changes.length));
    }
    return changes;
  }

  return [{ path: prefix || 'root', before, after }];
}

export function changedPathSet(changes: Pick<EditorChange, 'path'>[]): Set<string> {
  return new Set(changes.map(change => normalizePath(change.path)).filter(Boolean));
}

export function isChangedPath(path: ChangePathInput | null | undefined, changedPaths: ReadonlySet<string>, mode: ChangePathMatchMode = 'field'): boolean {
  const normalized = normalizePath(path);
  if (!normalized || changedPaths.size === 0) return false;
  if (changedPaths.has(normalized)) return true;
  if (mode === 'exact') return false;

  const descendantPrefix = `${normalized}.`;
  for (const changedPath of changedPaths) {
    if (changedPath.startsWith(descendantPrefix)) return true;
    if (mode === 'field' && normalized.startsWith(`${changedPath}.`)) return true;
  }
  return false;
}

export function isChangedFieldPath(path: ChangePathInput | null | undefined, changedPaths: ReadonlySet<string>): boolean {
  return isChangedPath(path, changedPaths, 'field');
}

export function getDeepValue(source: unknown, path: ChangePathInput): unknown {
  const parts = normalizePathParts(path);
  return parts.reduce<unknown>((current, key) => {
    if (current == null) return undefined;
    if (Array.isArray(current)) return current[Number(key)];
    if (typeof current === 'object') return (current as Record<string, unknown>)[key];
    return undefined;
  }, source);
}

export function valuesEqual(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) return true;
  if (Array.isArray(left) || Array.isArray(right)) {
    if (!Array.isArray(left) || !Array.isArray(right) || left.length !== right.length) return false;
    return left.every((value, index) => valuesEqual(value, right[index]));
  }
  if (isPlainObject(left) || isPlainObject(right)) {
    if (!isPlainObject(left) || !isPlainObject(right)) return false;
    const leftKeys = Object.keys(left).sort();
    const rightKeys = Object.keys(right).sort();
    if (leftKeys.length !== rightKeys.length) return false;
    return leftKeys.every((key, index) => key === rightKeys[index] && valuesEqual(left[key], right[key]));
  }
  return false;
}

function appendPath(prefix: string, key: string | number): string {
  return prefix ? `${prefix}.${key}` : String(key);
}

function normalizePath(path: ChangePathInput | null | undefined): string {
  return normalizePathParts(path).join('.');
}

function normalizePathParts(path: ChangePathInput | null | undefined): string[] {
  if (Array.isArray(path)) return path.filter(part => part !== undefined && part !== null && part !== '').map(String);
  return String(path ?? '').split('.').map(part => part.trim()).filter(Boolean);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
}
