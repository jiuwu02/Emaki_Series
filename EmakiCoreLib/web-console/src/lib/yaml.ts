/**
 * YAML serialization for GUI templates and item data.
 */

type ParsedLine = { line: number; indent: number; trimmed: string };

export function serializeYaml(data: Record<string, unknown>): string {
  return dumpYaml(data).trimEnd() + '\n';
}

export function parseYaml(content: string): Record<string, unknown> {
  const lines = content.replace(/\r\n?/g, '\n').split('\n')
    .map((raw, index) => ({ line: index + 1, indent: raw.match(/^\s*/)?.[0].length ?? 0, trimmed: raw.trim() }))
    .filter((line) => line.trimmed && !line.trimmed.startsWith('#'));
  if (!lines.length) return {};
  const [value] = parseBlock(lines, 0, lines[0].indent);
  if (value && typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>;
  throw new Error('YAML 根节点必须是对象');
}

function parseBlock(lines: ParsedLine[], start: number, indent: number): [unknown, number] {
  if (lines[start]?.trimmed.startsWith('- ')) return parseList(lines, start, indent);
  return parseMap(lines, start, indent);
}

function parseMap(lines: ParsedLine[], start: number, indent: number): [Record<string, unknown>, number] {
  const result: Record<string, unknown> = {};
  let index = start;
  while (index < lines.length) {
    const line = lines[index];
    if (line.indent < indent) break;
    if (line.indent > indent) throw new Error(`YAML 第 ${line.line} 行缩进无法解析：${line.trimmed}`);
    if (line.trimmed.startsWith('- ')) break;
    const match = line.trimmed.match(/^([^:#][^:]*):(\s*(.*))?$/);
    if (!match) throw new Error(`YAML 第 ${line.line} 行无法解析：${line.trimmed}`);
    const key = match[1].trim();
    const rest = match[3] ?? '';
    if (rest === '') {
      const next = lines[index + 1];
      if (!next || next.indent <= line.indent) {
        result[key] = {};
        index += 1;
      } else {
        const [child, nextIndex] = parseBlock(lines, index + 1, next.indent);
        result[key] = child;
        index = nextIndex;
      }
    } else {
      result[key] = parseScalar(rest.trim());
      index += 1;
    }
  }
  return [result, index];
}

function parseList(lines: ParsedLine[], start: number, indent: number): [unknown[], number] {
  const result: unknown[] = [];
  let index = start;
  while (index < lines.length) {
    const line = lines[index];
    if (line.indent < indent) break;
    if (line.indent > indent) throw new Error(`YAML 第 ${line.line} 行缩进无法解析：${line.trimmed}`);
    if (!line.trimmed.startsWith('- ')) break;
    const rest = line.trimmed.slice(2).trim();
    if (rest === '') {
      const next = lines[index + 1];
      if (!next || next.indent <= line.indent) {
        result.push(null);
        index += 1;
      } else {
        const [child, nextIndex] = parseBlock(lines, index + 1, next.indent);
        result.push(child);
        index = nextIndex;
      }
    } else if (rest.includes(':') && /^([^:#][^:]*):(\s*(.*))?$/.test(rest)) {
      const [inlineMap] = parseMap([{ line: line.line, indent: indent + 2, trimmed: rest }], 0, indent + 2);
      const next = lines[index + 1];
      if (next && next.indent > line.indent) {
        const [nestedMap, nextIndex] = parseMap(lines, index + 1, next.indent);
        result.push({ ...inlineMap, ...nestedMap });
        index = nextIndex;
      } else {
        result.push(inlineMap);
        index += 1;
      }
    } else {
      result.push(parseScalar(rest));
      index += 1;
    }
  }
  return [result, index];
}

function dumpYaml(value: unknown, indent = 0): string {
  const space = ' '.repeat(indent);
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]';
    return value.map((entry) => {
      if (entry && typeof entry === 'object' && !Array.isArray(entry)) return dumpYamlListObject(entry as Record<string, unknown>, indent);
      if (Array.isArray(entry)) return `${space}-\n${dumpYaml(entry, indent + 2)}`;
      return `${space}- ${formatScalar(entry)}`;
    }).join('\n');
  }
  if (value && typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>).filter(([, entry]) => entry !== undefined && entry !== null);
    if (entries.length === 0) return '{}';
    return entries.map(([key, entry]) => {
      if (entry && typeof entry === 'object') {
        const childEntries = Array.isArray(entry) ? entry : Object.entries(entry as Record<string, unknown>).filter(([, child]) => child !== undefined && child !== null);
        if (childEntries.length === 0) return `${space}${key}: ${Array.isArray(entry) ? '[]' : '{}'}`;
        return `${space}${key}:\n${dumpYaml(entry, indent + 2)}`;
      }
      return `${space}${key}: ${formatScalar(entry)}`;
    }).join('\n');
  }
  return formatScalar(value);
}

function dumpYamlListObject(value: Record<string, unknown>, indent: number): string {
  const space = ' '.repeat(indent);
  const childSpace = ' '.repeat(indent + 2);
  const entries = Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== null);
  if (entries.length === 0) return `${space}- {}`;
  const [firstKey, firstValue] = entries[0];
  const lines: string[] = [];
  if (firstValue && typeof firstValue === 'object') {
    const childEntries = Array.isArray(firstValue) ? firstValue : Object.entries(firstValue as Record<string, unknown>).filter(([, child]) => child !== undefined && child !== null);
    lines.push(childEntries.length === 0 ? `${space}- ${firstKey}: ${Array.isArray(firstValue) ? '[]' : '{}'}` : `${space}- ${firstKey}:\n${dumpYaml(firstValue, indent + 4)}`);
  } else {
    lines.push(`${space}- ${firstKey}: ${formatScalar(firstValue)}`);
  }
  for (const [key, entry] of entries.slice(1)) {
    if (entry && typeof entry === 'object') {
      const childEntries = Array.isArray(entry) ? entry : Object.entries(entry as Record<string, unknown>).filter(([, child]) => child !== undefined && child !== null);
      lines.push(childEntries.length === 0 ? `${childSpace}${key}: ${Array.isArray(entry) ? '[]' : '{}'}` : `${childSpace}${key}:\n${dumpYaml(entry, indent + 4)}`);
    } else {
      lines.push(`${childSpace}${key}: ${formatScalar(entry)}`);
    }
  }
  return lines.join('\n');
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
