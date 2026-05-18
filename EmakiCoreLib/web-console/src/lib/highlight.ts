/**
 * Lightweight syntax highlighting for JS and YAML.
 * Returns an HTML string with <span class="hl-*"> tokens.
 */

export function esc(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

type Token = { start: number; end: number; cls: string };

function overlaps(tokens: Token[], index: number, end: number): boolean {
  return tokens.some(t => index >= t.start && index < t.end);
}

function buildHTML(src: string, tokens: Token[]): string {
  tokens.sort((a, b) => a.start - b.start);
  let result = '';
  let cursor = 0;
  for (const t of tokens) {
    if (t.start < cursor) continue;
    if (t.start > cursor) result += esc(src.substring(cursor, t.start));
    result += `<span class="${t.cls}">${esc(src.substring(t.start, t.end))}</span>`;
    cursor = t.end;
  }
  if (cursor < src.length) result += esc(src.substring(cursor));
  return result;
}

export function highlightJS(code: string): string {
  const tokens: Token[] = [];
  const src = code;

  // 多行注释
  for (const m of src.matchAll(/\/\*[\s\S]*?\*\//g)) {
    tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-comment' });
  }
  // 单行注释
  for (const m of src.matchAll(/\/\/[^\n]*/g)) {
    if (!overlaps(tokens, m.index!, m.index! + m[0].length)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-comment' });
    }
  }
  // 字符串
  for (const m of src.matchAll(/(["'`])(?:(?!\1|\\).|\\.)*?\1/g)) {
    if (!overlaps(tokens, m.index!, m.index! + m[0].length)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-string' });
    }
  }
  // 数字
  for (const m of src.matchAll(/\b(\d+\.?\d*)\b/g)) {
    if (!overlaps(tokens, m.index!, m.index! + m[0].length)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-number' });
    }
  }
  // 关键字
  const kwRe = /\b(function|const|let|var|if|else|return|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|typeof|instanceof|in|of|class|extends|import|export|default|this|true|false|null|undefined|void|delete|yield|await|async)\b/g;
  for (const m of src.matchAll(kwRe)) {
    if (!overlaps(tokens, m.index!, m.index! + m[0].length)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-keyword' });
    }
  }
  // emaki
  for (const m of src.matchAll(/\b(emaki)\b/g)) {
    if (!overlaps(tokens, m.index!, m.index! + m[0].length)) {
      tokens.push({ start: m.index!, end: m.index! + m[0].length, cls: 'hl-emaki' });
    }
  }

  return buildHTML(src, tokens);
}

export function highlightYAML(code: string): string {
  const tokens: Token[] = [];
  const src = code;

  // 注释 (# ...)
  for (const m of src.matchAll(/^([ \t]*)#[^\n]*/gm)) {
    const start = m.index! + m[1].length;
    tokens.push({ start, end: m.index! + m[0].length, cls: 'hl-comment' });
  }
  // 键名 (key:) — 行首可选缩进后的非空白字符到冒号
  for (const m of src.matchAll(/^([ \t]*)([\w][\w.\-/]*)\s*:/gm)) {
    const keyStart = m.index! + m[1].length;
    const keyEnd = keyStart + m[2].length;
    if (!overlaps(tokens, keyStart, keyEnd)) {
      tokens.push({ start: keyStart, end: keyEnd, cls: 'hl-key' });
    }
  }
  // 带引号的键名
  for (const m of src.matchAll(/^([ \t]*)(["'])(.+?)\2\s*:/gm)) {
    const keyStart = m.index! + m[1].length;
    const keyEnd = keyStart + m[2].length + m[3].length + m[2].length;
    if (!overlaps(tokens, keyStart, keyEnd)) {
      tokens.push({ start: keyStart, end: keyEnd, cls: 'hl-key' });
    }
  }
  // 字符串值 (引号包裹)
  for (const m of src.matchAll(/:\s*(["'])(?:(?!\1|\\).|\\.)*?\1/g)) {
    const valStart = m.index! + m[0].indexOf(m[1]);
    const valEnd = m.index! + m[0].length;
    if (!overlaps(tokens, valStart, valEnd)) {
      tokens.push({ start: valStart, end: valEnd, cls: 'hl-string' });
    }
  }
  // 数字值
  for (const m of src.matchAll(/:\s*(-?\d+\.?\d*)\s*$/gm)) {
    const valStart = m.index! + m[0].indexOf(m[1]);
    const valEnd = valStart + m[1].length;
    if (!overlaps(tokens, valStart, valEnd)) {
      tokens.push({ start: valStart, end: valEnd, cls: 'hl-number' });
    }
  }
  // 布尔和 null
  for (const m of src.matchAll(/:\s*(true|false|null|yes|no|on|off)\s*$/gm)) {
    const valStart = m.index! + m[0].indexOf(m[1]);
    const valEnd = valStart + m[1].length;
    if (!overlaps(tokens, valStart, valEnd)) {
      tokens.push({ start: valStart, end: valEnd, cls: 'hl-keyword' });
    }
  }
  // 列表项标记 (- )
  for (const m of src.matchAll(/^([ \t]*)- /gm)) {
    const dashStart = m.index! + m[1].length;
    if (!overlaps(tokens, dashStart, dashStart + 1)) {
      tokens.push({ start: dashStart, end: dashStart + 1, cls: 'hl-keyword' });
    }
  }

  return buildHTML(src, tokens);
}
