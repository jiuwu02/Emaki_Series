import { setValueAtSchemaPath } from './path';

export type YamlPatchOperation = {
  path: string;
  value: unknown;
};

export function applyYamlObjectPatch<T extends Record<string, unknown>>(source: T, operations: YamlPatchOperation[]): T {
  const next = cloneObject(source) as T;
  for (const operation of operations) setValueAtSchemaPath(next, operation.path, operation.value);
  return next;
}

export function createYamlPatch(path: string, value: unknown): YamlPatchOperation {
  return { path, value };
}

function cloneObject(value: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value ?? {})) as Record<string, unknown>;
}
