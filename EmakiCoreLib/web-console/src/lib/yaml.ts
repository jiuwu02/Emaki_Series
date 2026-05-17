/**
 * YAML serialization for GUI templates and item data.
 */

export function serializeYaml(data: Record<string, unknown>): string {
  return dumpYaml(data).trimEnd() + '\n';
}

export function parseYaml(content: string): Record<string, unknown> {
  const root: Record<string, unknown> = {};
  const stack: Array<{ indent: number; value: Record<string, unknown> }> = [{ indent: -1, value: root }];
  const lines = content.replace(/\r\n?/g, '\n').split('\n');
  for (let index = 0; index < lines.length; index++) {
    const raw = lines[index];
    if (!raw.trim() || raw.trimStart().startsWith('#')) continue;
    const indent = raw.match(/^\s*/)?.[0].length ?? 0;
    const trimmed = raw.trim();
    const match = trimmed.match(/^([^:#][^:]*):(?:\s*(.*))?$/);
    if (!match) throw new Error(`YAML 第 ${index + 1} 行无法解析：${trimmed}`);
    const key = match[1].trim();
    const rest = match[2] ?? '';
    while (stack.length > 1 && indent <= stack[stack.length - 1].indent) stack.pop();
    const parent = stack[stack.length - 1].value;
    if (rest === '') {
      const child: Record<string, unknown> = {};
      parent[key] = child;
      stack.push({ indent, value: child });
    } else {
      parent[key] = parseScalar(rest.trim());
    }
  }
  return root;
}

function dumpYaml(value: unknown, indent = 0): string {
  const space = ' '.repeat(indent);
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]';
    if (value.every((entry) => typeof entry !== 'object' || entry == null)) return `[${value.map(formatScalar).join(', ')}]`;
    return value.map((entry) => `${space}- ${dumpYaml(entry, indent + 2).trimStart()}`).join('\n');
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>).filter(([, entry]) => entry !== undefined && entry !== null);
    if (entries.length === 0) return '{}';
    return entries.map(([key, entry]) => {
      if (entry && typeof entry === 'object' && !Array.isArray(entry)) {
        const childEntries = Object.entries(entry as Record<string, unknown>).filter(([, child]) => child !== undefined && child !== null);
        if (childEntries.length === 0) return `${space}${key}: {}`;
        return `${space}${key}:\n${dumpYaml(entry, indent + 2)}`;
      }
      if (Array.isArray(entry) && entry.length > 0 && !entry.every((item) => typeof item !== 'object' || item == null)) return `${space}${key}:\n${dumpYaml(entry, indent + 2)}`;
      return `${space}${key}: ${dumpYaml(entry, indent + 2).trimStart()}`;
    }).join('\n');
  }
  return formatScalar(value);
}

function parseScalar(value: string): unknown {
  if (value === 'null') return null;
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value);
  if (value.startsWith('[') && value.endsWith(']')) {
    const inner = value.slice(1, -1).trim();
    if (!inner) return [];
    return inner.split(',').map(part => parseScalar(part.trim()));
  }
  if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
    try { return JSON.parse(value); } catch { return value.slice(1, -1); }
  }
  return value;
}

function formatScalar(value: unknown): string {
  if (value == null) return 'null';
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  const text = String(value);
  if (!text) return '""';
  if (/^[a-zA-Z0-9_./:-]+$/.test(text) && !text.includes(': ')) return text;
  return JSON.stringify(text);
}
