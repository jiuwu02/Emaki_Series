/**
 * MiniMessage text parsing and rendering to colored parts.
 */

export type MiniMessagePart = { text: string; color?: string; token?: boolean };

const COLOR_MAP: Record<string, string> = {
  black: 'oklch(22% 0.01 260)', dark_gray: 'oklch(54% 0.01 260)', gray: 'oklch(70% 0.01 260)', white: 'oklch(92% 0.01 260)',
  red: 'oklch(68% 0.18 28)', dark_red: 'oklch(52% 0.16 28)', green: 'oklch(72% 0.16 150)', dark_green: 'oklch(56% 0.13 150)',
  yellow: 'oklch(84% 0.14 90)', gold: 'oklch(78% 0.14 75)', aqua: 'oklch(80% 0.11 200)', dark_aqua: 'oklch(62% 0.1 210)',
  blue: 'oklch(68% 0.14 255)', light_purple: 'oklch(76% 0.14 320)', dark_purple: 'oklch(58% 0.13 315)'
};

/** Parse MiniMessage-style text into colored parts for rendering. */
export function renderMiniMessageParts(text: unknown): MiniMessagePart[] {
  const source = textValue(text);
  const parts: MiniMessagePart[] = [];
  const colorStack: string[] = [];
  let buffer = '';

  const flush = () => {
    if (buffer) parts.push({ text: buffer, color: colorStack[colorStack.length - 1] });
    buffer = '';
  };

  for (let i = 0; i < source.length; i++) {
    if (source[i] === '<') {
      const end = source.indexOf('>', i);
      if (end > i) {
        const tag = source.slice(i + 1, end).replace('/', '').split(':')[0].toLowerCase();
        if (source[i + 1] === '/') {
          flush(); colorStack.pop(); i = end; continue;
        }
        if (COLOR_MAP[tag]) {
          flush(); colorStack.push(COLOR_MAP[tag]); i = end; continue;
        }
        if (/^#[0-9a-f]{6}$/i.test(tag)) { flush(); colorStack.push(tag); i = end; continue; }
        if (tag === 'gradient') { flush(); colorStack.push(COLOR_MAP.light_purple); i = end; continue; }
      }
    }
    if (source[i] === '{') {
      const end = source.indexOf('}', i);
      if (end > i) {
        flush();
        parts.push({ text: source.slice(i, end + 1), color: 'oklch(56% 0.03 255)', token: true });
        i = end;
        continue;
      }
    }
    buffer += source[i];
  }
  flush();
  return parts.length ? parts : [{ text: source }];
}

/** Safe string extraction from unknown values. */
export function textValue(value: unknown, fallback = ''): string {
  if (typeof value === 'string') return value;
  if (value == null) return fallback;
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  return fallback;
}
