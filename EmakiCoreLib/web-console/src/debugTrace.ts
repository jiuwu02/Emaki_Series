import type { ApiClient } from './api';

export const ITEM_SET_OPEN_DEBUG_PREFIX = '[DEBUG:ITEM_SET_OPEN:';

const MAX_DEBUG_DETAIL_LENGTH = 3800;

type DebugTraceOptions = {
  api?: Pick<ApiClient, 'reportFrontendEvent'>;
  target?: string;
};

export function debugTrace(step: string, label: string, payload: unknown, options: DebugTraceOptions = {}): void {
  const prefix = `${ITEM_SET_OPEN_DEBUG_PREFIX}${step}]`;
  const safePayload = sanitizeDebugPayload(payload);
  try {
    console.info(prefix, label, safePayload);
  } catch {
    // DEBUG 输出不能影响 Web Console 正常使用。
  }
  if (!options.api) return;
  try {
    void options.api.reportFrontendEvent({
      type: 'item_set_open_debug',
      target: options.target ?? step,
      label,
      detail: trimDebugDetail(JSON.stringify(safePayload) ?? String(safePayload)),
      url: window.location.href
    });
  } catch {
    // DEBUG 上报失败时只保留 F12 控制台输出。
  }
}

export function sanitizeDebugPayload(value: unknown): unknown {
  const seen = new WeakSet<object>();
  const sanitize = (input: unknown, depth: number): unknown => {
    if (input == null) return input;
    if (typeof input === 'string') return input.length > 600 ? `${input.slice(0, 600)}…<trimmed:${input.length}>` : input;
    if (typeof input === 'number' || typeof input === 'boolean') return input;
    if (typeof input === 'bigint') return String(input);
    if (typeof input === 'function') return `[Function ${(input as Function).name || 'anonymous'}]`;
    if (typeof input !== 'object') return String(input);
    if (seen.has(input)) return '[Circular]';
    if (depth >= 5) return '[MaxDepth]';
    seen.add(input);
    if (Array.isArray(input)) return input.slice(0, 30).map(item => sanitize(item, depth + 1));
    const output: Record<string, unknown> = {};
    for (const [key, child] of Object.entries(input as Record<string, unknown>).slice(0, 60)) {
      output[key] = sanitize(child, depth + 1);
    }
    return output;
  };
  return sanitize(value, 0);
}

function trimDebugDetail(value: string): string {
  return value.length > MAX_DEBUG_DETAIL_LENGTH ? `${value.slice(0, MAX_DEBUG_DETAIL_LENGTH)}…<trimmed:${value.length}>` : value;
}
