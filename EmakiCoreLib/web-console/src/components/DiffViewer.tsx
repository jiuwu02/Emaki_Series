import { useMemo } from 'react';
import { t } from '../i18n';

export type DiffLine = { type: 'context' | 'add' | 'remove'; text: string; beforeLine?: number; afterLine?: number };

export function FieldValueDiff({ before, after }: { before: unknown; after: unknown }) {
  const diff = useMemo(() => buildValueLineDiff(before, after), [before, after]);
  if (!diff.changed) return null;
  return <UnifiedDiffView diff={diff.lines} compact maxLines={16} className="editor-change-diff field-value-diff" />;
}

export function SourceDiff({ before, after, compact = false }: { before: string; after: string; compact?: boolean }) {
  const diff = useMemo(() => buildLineDiff(before, after), [before, after]);
  if (!diff.changed) return <p>{t('core.editor.sourceDiffEmpty')}</p>;
  return <UnifiedDiffView diff={diff.lines} compact={compact} maxLines={compact ? 24 : 120} hideContext={compact} />;
}

export function UnifiedDiffView({ diff, compact = false, maxLines, hideContext = false, className = '' }: { diff: string | DiffLine[]; compact?: boolean; maxLines?: number; hideContext?: boolean; className?: string }) {
  const lines = useMemo(() => typeof diff === 'string' ? parseUnifiedDiff(diff) : diff, [diff]);
  const visibleSource = hideContext ? lines.filter(line => line.type !== 'context') : lines;
  const limit = maxLines ?? (compact ? 80 : 160);
  const visible = visibleSource.slice(0, limit);
  const omitted = Math.max(0, visibleSource.length - visible.length);
  if (!visible.length) return <p>{t('core.history.noDiff')}</p>;
  return <div className={`source-diff ${compact ? 'compact' : ''}${className ? ` ${className}` : ''}`} role="list" aria-label={t('core.editor.sourceDiffTitle')}>
    {visible.map((line, index) => <DiffLineView line={line} key={`${line.type}-${line.beforeLine ?? ''}-${line.afterLine ?? ''}-${index}`} />)}
    {omitted > 0 && <p className="source-diff-more">{t('core.editor.sourceDiffMore', { count: omitted })}</p>}
  </div>;
}

export function parseUnifiedDiff(diff: string): DiffLine[] {
  const lines: DiffLine[] = [];
  let beforeLine = 0;
  let afterLine = 0;
  for (const rawLine of String(diff ?? '').replace(/\r\n?/g, '\n').split('\n')) {
    const hunk = rawLine.match(/^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/);
    if (hunk) {
      beforeLine = Number(hunk[1]);
      afterLine = Number(hunk[2]);
      continue;
    }
    if (rawLine.startsWith('---') || rawLine.startsWith('+++') || rawLine.startsWith('diff ') || rawLine.startsWith('index ')) continue;
    if (rawLine.startsWith('-')) {
      lines.push({ type: 'remove', text: rawLine.slice(1), beforeLine: beforeLine || undefined });
      beforeLine += 1;
      continue;
    }
    if (rawLine.startsWith('+')) {
      lines.push({ type: 'add', text: rawLine.slice(1), afterLine: afterLine || undefined });
      afterLine += 1;
      continue;
    }
    if (rawLine.startsWith(' ')) {
      lines.push({ type: 'context', text: rawLine.slice(1), beforeLine: beforeLine || undefined, afterLine: afterLine || undefined });
    }
    if (beforeLine > 0) beforeLine += 1;
    if (afterLine > 0) afterLine += 1;
  }
  return lines;
}

function DiffLineView({ line }: { line: DiffLine }) {
  const lineNo = line.type === 'add' ? line.afterLine : line.beforeLine;
  return <div className={`source-diff-line ${line.type}`} role="listitem">
    <code className="source-diff-no">{lineNo}</code>
    <code className="source-diff-sign">{line.type === 'add' ? '+' : line.type === 'remove' ? '−' : ' '}</code>
    <code className="source-diff-text">{line.text || ' '}</code>
  </div>;
}

function buildValueLineDiff(before: unknown, after: unknown): { changed: boolean; lines: DiffLine[] } {
  return buildLineDiff(formatDiffValue(before), formatDiffValue(after));
}

function buildLineDiff(before: string, after: string): { changed: boolean; lines: DiffLine[] } {
  if (before === after) return { changed: false, lines: [] };
  return buildCompactLineDiff(before.split('\n'), after.split('\n'));
}

function buildCompactLineDiff(beforeLines: string[], afterLines: string[]): { changed: boolean; lines: DiffLine[] } {
  let start = 0;
  while (start < beforeLines.length && start < afterLines.length && beforeLines[start] === afterLines[start]) start++;
  let beforeEnd = beforeLines.length - 1;
  let afterEnd = afterLines.length - 1;
  while (beforeEnd >= start && afterEnd >= start && beforeLines[beforeEnd] === afterLines[afterEnd]) {
    beforeEnd--;
    afterEnd--;
  }
  const lines: DiffLine[] = [];
  for (let i = Math.max(0, start - 3); i < start; i++) lines.push({ type: 'context', text: beforeLines[i], beforeLine: i + 1, afterLine: i + 1 });
  for (let i = start; i <= beforeEnd; i++) lines.push({ type: 'remove', text: beforeLines[i], beforeLine: i + 1 });
  for (let i = start; i <= afterEnd; i++) lines.push({ type: 'add', text: afterLines[i], afterLine: i + 1 });
  for (let i = beforeEnd + 1; i <= Math.min(beforeLines.length - 1, beforeEnd + 3); i++) {
    const afterLine = afterEnd + 1 + (i - beforeEnd - 1);
    lines.push({ type: 'context', text: beforeLines[i], beforeLine: i + 1, afterLine: afterLine + 1 });
  }
  return { changed: true, lines };
}

function formatDiffValue(value: unknown): string {
  if (value === undefined) return '∅';
  if (value === null) return 'null';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') return String(value);
  if (Array.isArray(value)) return formatArrayValue(value);
  if (isPlainObject(value)) return formatObjectValue(value);
  return String(value);
}

function formatArrayValue(values: unknown[], depth = 0): string {
  if (!values.length) return '[]';
  return values.map(value => `${indent(depth)}- ${formatNestedDiffValue(value, depth)}`).join('\n');
}

function formatObjectValue(value: Record<string, unknown>, depth = 0): string {
  const entries = Object.entries(value);
  if (!entries.length) return '{}';
  return entries.map(([key, entry]) => `${indent(depth)}${key}: ${formatNestedDiffValue(entry, depth)}`).join('\n');
}

function formatNestedDiffValue(value: unknown, depth: number): string {
  if (Array.isArray(value)) return value.length ? `\n${formatArrayValue(value, depth + 1)}` : '[]';
  if (isPlainObject(value)) return Object.keys(value).length ? `\n${formatObjectValue(value, depth + 1)}` : '{}';
  return formatDiffValue(value);
}

function indent(depth: number): string {
  return '  '.repeat(depth);
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value && typeof value === 'object' && !Array.isArray(value));
}
