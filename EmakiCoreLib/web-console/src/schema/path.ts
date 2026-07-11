export function normalizeSchemaPath(path: string | undefined): string {
  return String(path ?? '').trim().replace(/\\/g, '/').replace(/^\/+/, '');
}

export function splitSchemaPath(path: string): string[] {
  return String(path ?? '').split('.').map(part => part.trim()).filter(Boolean);
}

export function schemaPathMatches(pattern: string | undefined, path: string | undefined): boolean {
  const normalizedPattern = normalizeSchemaPath(pattern).toLowerCase();
  const normalizedPath = normalizeSchemaPath(path).toLowerCase();
  if (!normalizedPattern) return false;
  const regex = normalizedPattern.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*\*/g, '.*').replace(/\*/g, '[^/]*');
  return new RegExp(`^${regex}$`).test(normalizedPath);
}

export function schemaPathStartsWith(prefix: string | undefined, path: string | undefined): boolean {
  const normalizedPrefix = normalizeSchemaPath(prefix).toLowerCase();
  const normalizedPath = normalizeSchemaPath(path).toLowerCase();
  return Boolean(normalizedPrefix) && normalizedPath.startsWith(normalizedPrefix);
}

export function getValueAtSchemaPath(source: unknown, path: string): unknown {
  return splitSchemaPath(path).reduce<unknown>((current, part) => {
    if (!current || typeof current !== 'object') return undefined;
    return (current as Record<string, unknown>)[part];
  }, source);
}

export function setValueAtSchemaPath(source: Record<string, unknown>, path: string, value: unknown): Record<string, unknown> {
  const parts = splitSchemaPath(path);
  if (!parts.length) return source;
  let cursor = source;
  for (const part of parts.slice(0, -1)) {
    const next = cursor[part];
    if (!next || typeof next !== 'object' || Array.isArray(next)) cursor[part] = {};
    cursor = cursor[part] as Record<string, unknown>;
  }
  cursor[parts[parts.length - 1]] = value;
  return source;
}
