/**
 * YAML serialization for GUI templates and item data.
 */

export function serializeYaml(data: Record<string, unknown>): string {
  return dumpYaml(data).trimEnd() + '\n';
}

function dumpYaml(value: unknown, indent = 0): string {
  const space = ' '.repeat(indent);
  if (Array.isArray(value)) {
    if (value.every((entry) => typeof entry !== 'object' || entry == null)) return `[${value.map(formatScalar).join(', ')}]`;
    return value.map((entry) => `${space}- ${dumpYaml(entry, indent + 2).trimStart()}`).join('\n');
  }
  if (value && typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).map(([key, entry]) => {
      if (entry && typeof entry === 'object' && !Array.isArray(entry)) return `${space}${key}:\n${dumpYaml(entry, indent + 2)}`;
      if (Array.isArray(entry) && !entry.every((item) => typeof item !== 'object' || item == null)) return `${space}${key}:\n${dumpYaml(entry, indent + 2)}`;
      return `${space}${key}: ${dumpYaml(entry, indent + 2).trimStart()}`;
    }).join('\n');
  }
  return formatScalar(value);
}

function formatScalar(value: unknown): string {
  if (value == null) return 'null';
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  const text = String(value);
  if (!text) return '""';
  if (/^[a-zA-Z0-9_./:-]+$/.test(text) && !text.includes(': ')) return text;
  return JSON.stringify(text);
}
