import { forwardRef, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { t } from '../i18n';
import { CodeEditor } from './CodeEditor';
import { ActionGroup } from './ActionGroup';
import { Button } from './Button';
import { useDialogFocus } from './useDialogFocus';

export type EditorChange = {
  path: string;
  label?: string;
  before?: unknown;
  after?: unknown;
};

export type EditorChromeProps = {
  title: ReactNode;
  subtitle?: ReactNode;
  dirty: boolean;
  changes?: EditorChange[];
  changedCount?: number;
  source?: string;
  sourceOriginal?: string;
  sourceEditable?: boolean;
  sourceError?: string | null;
  sourceLanguage?: string;
  saving?: boolean;
  loading?: boolean;
  saveLabel?: string;
  sourceLabel?: string;
  reloadLabel?: string;
  canUndo?: boolean;
  canRedo?: boolean;
  onUndo?: () => void;
  onRedo?: () => void;
  onSave?: () => void;
  onReload?: () => void;
  onSourceChange?: (source: string) => void;
  className?: string;
  children?: ReactNode;
};

export function EditorChrome({
  title,
  subtitle,
  dirty,
  changes = [],
  changedCount,
  source = '',
  sourceOriginal,
  sourceEditable,
  sourceError,
  sourceLanguage,
  saving = false,
  loading = false,
  saveLabel,
  sourceLabel,
  reloadLabel,
  canUndo = false,
  canRedo = false,
  onUndo,
  onRedo,
  onSave,
  onReload,
  onSourceChange,
  className = '',
  children
}: EditorChromeProps) {
  const [sourceOpen, setSourceOpen] = useState(false);
  const [reloadOpen, setReloadOpen] = useState(false);
  const [saveOpen, setSaveOpen] = useState(false);
  const [changesOpen, setChangesOpen] = useState(false);
  const count = changedCount ?? changes.length;
  const sourceChanged = sourceOriginal !== undefined && sourceOriginal !== source;
  const fieldChangeCount = changes.length ? count : 0;
  const sourceDirtyOnly = dirty && changes.length === 0 && (sourceChanged || count > 0);
  const hasTrackedChanges = fieldChangeCount > 0 || sourceDirtyOnly;
  const canSave = Boolean(onSave) && dirty && !saving && !loading;
  const canOpenSource = Boolean(sourceEditable ?? onSourceChange ?? source);
  const finalSaveLabel = saveLabel ?? (fieldChangeCount > 0 ? t('core.action.saveCount', { count: fieldChangeCount }, 'Save {count} changes') : sourceDirtyOnly ? t('core.action.saveSource', undefined, 'Save source') : t('core.action.save', undefined, 'Save'));

  const previewChanges = useMemo(() => changes.slice(0, 12), [changes]);

  function requestReload() {
    if (!onReload || loading || saving) return;
    setSourceOpen(false);
    if (dirty) setReloadOpen(true);
    else onReload();
  }

  function confirmReload() {
    setReloadOpen(false);
    onReload?.();
  }

  function requestSave() {
    if (!canSave) return;
    setSourceOpen(false);
    setSaveOpen(true);
  }

  function confirmSave() {
    setSaveOpen(false);
    onSave?.();
  }

  return <>
    <div className={`editor-chrome ${className}`.trim()}>
      <div className="editor-chrome-title">
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}{dirty && <span className="dirty-inline">{t('core.item.unsaved', undefined, 'Unsaved')}</span>}</p>}
      </div>
      <ActionGroup className="editor-chrome-actions" role="toolbar" aria-label={t('core.editor.toolbarAria', undefined, 'Editor actions')}>
        {children}
        {onUndo && <Button size="sm" onClick={onUndo} disabled={!canUndo || saving || loading} title={t('core.editor.undoHint', undefined, 'Undo last change')}>{t('core.editor.undo')}</Button>}
        {onRedo && <Button size="sm" onClick={onRedo} disabled={!canRedo || saving || loading} title={t('core.editor.redoHint', undefined, 'Redo last change')}>{t('core.editor.redo')}</Button>}
        <Button size="sm" onClick={() => setSourceOpen(true)} disabled={!canOpenSource}>{sourceLabel ?? t('core.item.source', undefined, 'Source')}</Button>
        {onReload && <Button size="sm" onClick={requestReload} disabled={loading || saving}>{reloadLabel ?? t('core.gui.reload', undefined, 'Reload')}</Button>}
        <span className="editor-save-wrap" onMouseEnter={() => setChangesOpen(true)} onMouseLeave={() => setChangesOpen(false)} onFocus={() => setChangesOpen(true)} onBlur={() => setChangesOpen(false)}>
          <Button size="sm" variant="primary" ready={dirty && hasTrackedChanges} onClick={requestSave} disabled={!canSave}>{saving ? t('core.script.saving', undefined, 'Saving...') : finalSaveLabel}</Button>
          {dirty && hasTrackedChanges && changesOpen && <ChangePopover changes={previewChanges} count={fieldChangeCount || count} source={source} sourceOriginal={sourceOriginal} />}
        </span>
      </ActionGroup>
    </div>
    {sourceOpen && <SourceModal source={source} editable={sourceEditable ?? Boolean(onSourceChange)} error={sourceError} language={sourceLanguage} onChange={onSourceChange} onSave={requestSave} onClose={() => setSourceOpen(false)} />}
    {saveOpen && <SaveModal changes={previewChanges} count={fieldChangeCount || count} source={source} sourceOriginal={sourceOriginal} onCancel={() => setSaveOpen(false)} onConfirm={confirmSave} />}
    {reloadOpen && <ReloadModal changes={previewChanges} count={fieldChangeCount || count} source={source} sourceOriginal={sourceOriginal} onCancel={() => setReloadOpen(false)} onConfirm={confirmReload} />}
  </>;
}

function ChangePopover({ changes, count, source, sourceOriginal }: { changes: EditorChange[]; count: number; source?: string; sourceOriginal?: string }) {
  return <div className="editor-change-popover" role="status">
    <strong>{changes.length ? t('core.editor.changesTitle', { count }, 'Changes ({count})') : t('core.editor.sourceDiffTitle')}</strong>
    <ChangeList changes={changes} count={count} source={source} sourceOriginal={sourceOriginal} compact />
  </div>;
}

function ChangeList({ changes, count, source, sourceOriginal, compact = false }: { changes: EditorChange[]; count: number; source?: string; sourceOriginal?: string; compact?: boolean }) {
  if (!changes.length) return sourceOriginal !== undefined && source !== undefined
    ? <SourceDiff before={sourceOriginal} after={source} compact={compact} />
    : <p className="editor-change-empty">{t('core.editor.changedSource')}</p>;
  return <>
    <div className="editor-change-list">
      {changes.map(change => <div className="editor-change-row" key={change.path}>
        <code>{change.path}</code>
        <span>{change.label || change.path}</span>
        <FieldValueDiff before={change.before} after={change.after} />
      </div>)}
    </div>
    {count > changes.length && <p>{t('core.editor.changesMore', { count: count - changes.length }, 'Another {count} changes are hidden.')}</p>}
  </>;
}

function SourceModal({ source, editable, error, language, onChange, onSave, onClose }: { source: string; editable?: boolean; error?: string | null; language?: string; onChange?: (source: string) => void; onSave?: () => void; onClose: () => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  const [localSource, setLocalSource] = useState(source);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const stableOnClose = useMemo(() => () => onCloseRef.current(), []);
  useDialogFocus(dialogRef, stableOnClose);

  useEffect(() => { setLocalSource(source); }, [source]);

  function handleInput(value: string) {
    setLocalSource(value);
    if (value !== source) onChange?.(value);
  }

  function handleTab() {
    // CodeMirror owns indentation and cursor placement; keeping Tab handled inside the editor avoids browser focus jumps.
  }

  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={dialogRef} className="editor-source-modal" role="dialog" aria-modal="true" aria-labelledby="editor-source-title" tabIndex={-1}>
      <header className="editor-modal-head">
        <div><span>{t('core.editor.sourceKicker', undefined, 'Source')}</span><h3 id="editor-source-title">{t('core.editor.sourceTitle', undefined, 'Source editor')}</h3></div>
        <Button size="sm" onClick={onClose}>{t('core.i18n.close', undefined, 'Close')}</Button>
      </header>
      <CodeEditor
        className="source-code-editor"
        value={localSource}
        language={language}
        readOnly={!editable}
        ariaLabel={t('core.editor.sourceTitle', undefined, 'Source editor')}
        onChange={handleInput}
        onSave={onSave}
        onTab={handleTab}
      />
      {error && <p className="editor-source-error" role="alert">{error}</p>}
    </section>
  </div>;
}

function SaveModal({ changes, count, source, sourceOriginal, onCancel, onConfirm }: { changes: EditorChange[]; count: number; source?: string; sourceOriginal?: string; onCancel: () => void; onConfirm: () => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  return <DiffDecisionModal
    ref={dialogRef}
    titleId="editor-save-title"
    descId="editor-save-desc"
    kicker={t('core.gui.unsavedChanges')}
    title={t('core.editor.saveTitle')}
    desc={t('core.editor.saveDesc')}
    changes={changes}
    count={count}
    source={source}
    sourceOriginal={sourceOriginal}
    compact
    confirmLabel={t('core.editor.saveConfirm')}
    confirmVariant="primary"
    onCancel={onCancel}
    onConfirm={onConfirm}
  />;
}

function ReloadModal({ changes, count, source, sourceOriginal, onCancel, onConfirm }: { changes: EditorChange[]; count: number; source?: string; sourceOriginal?: string; onCancel: () => void; onConfirm: () => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  return <DiffDecisionModal
    ref={dialogRef}
    titleId="editor-reload-title"
    descId="editor-reload-desc"
    kicker={t('core.gui.unsavedChanges')}
    title={t('core.gui.reloadDropsChanges')}
    desc={t('core.editor.reloadDesc')}
    changes={changes}
    count={count}
    source={source}
    sourceOriginal={sourceOriginal}
    compact
    confirmLabel={t('core.gui.continueReload')}
    confirmVariant="danger"
    onCancel={onCancel}
    onConfirm={onConfirm}
  />;
}

const DiffDecisionModal = forwardRef<HTMLElement, { titleId: string; descId: string; kicker: string; title: string; desc: string; changes: EditorChange[]; count: number; source?: string; sourceOriginal?: string; compact?: boolean; confirmLabel: string; confirmVariant: 'primary' | 'danger'; onCancel: () => void; onConfirm: () => void }>(function DiffDecisionModal({ titleId, descId, kicker, title, desc, changes, count, source, sourceOriginal, compact = false, confirmLabel, confirmVariant, onCancel, onConfirm }, dialogRef) {
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section ref={dialogRef} className="reload-confirm-dialog diff-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId} aria-describedby={descId} tabIndex={-1}>
      <div className="reload-confirm-head diff-dialog-head">
        <span>{kicker}</span>
        <h3 id={titleId}>{title}</h3>
      </div>
      <div className="reload-confirm-body diff-dialog-body">
        <p id={descId}>{desc}</p>
        <div className="reload-change-summary" aria-label={t('core.editor.reloadChangesAria', { count })}>
          <strong>{changes.length ? t('core.editor.changesTitle', { count }, 'Changes ({count})') : t('core.editor.sourceDiffTitle')}</strong>
          <ChangeList changes={changes} count={count} source={source} sourceOriginal={sourceOriginal} compact={compact} />
        </div>
      </div>
      <ActionGroup className="reload-confirm-actions diff-dialog-actions">
        <Button onClick={onCancel} autoFocus>{t('core.gui.cancel')}</Button>
        <Button variant={confirmVariant} onClick={onConfirm}>{confirmLabel}</Button>
      </ActionGroup>
    </section>
  </div>;
});

function FieldValueDiff({ before, after }: { before: unknown; after: unknown }) {
  const diff = useMemo(() => buildValueLineDiff(before, after), [before, after]);
  if (!diff.changed) return null;
  const visible = diff.lines.slice(0, 16);
  const omitted = Math.max(0, diff.lines.length - visible.length);
  return <div className="editor-change-diff source-diff field-value-diff compact" role="list">
    {visible.map((line, index) => <DiffLineView line={line} key={`${line.type}-${line.beforeLine ?? ''}-${line.afterLine ?? ''}-${index}`} />)}
    {omitted > 0 && <p className="source-diff-more">{t('core.editor.sourceDiffMore', { count: omitted })}</p>}
  </div>;
}

function SourceDiff({ before, after, compact = false }: { before: string; after: string; compact?: boolean }) {
  const diff = useMemo(() => buildLineDiff(before, after), [before, after]);
  const visible = compact ? diff.lines.slice(0, 24) : diff.lines.slice(0, 120);
  const omitted = Math.max(0, diff.lines.length - visible.length);
  if (!diff.changed) return <p>{t('core.editor.sourceDiffEmpty')}</p>;
  return <div className={`source-diff ${compact ? 'compact' : ''}`} role="list" aria-label={t('core.editor.sourceDiffTitle')}>
    {visible.map((line, index) => <DiffLineView line={line} key={`${line.type}-${line.beforeLine ?? ''}-${line.afterLine ?? ''}-${index}`} />)}
    {omitted > 0 && <p className="source-diff-more">{t('core.editor.sourceDiffMore', { count: omitted })}</p>}
  </div>;
}

function DiffLineView({ line }: { line: DiffLine }) {
  return <div className={`source-diff-line ${line.type}`} role="listitem">
    <code className="source-diff-no">{line.type === 'add' ? line.afterLine : line.beforeLine}</code>
    <code className="source-diff-sign">{line.type === 'add' ? '+' : line.type === 'remove' ? '−' : ' '}</code>
    <code className="source-diff-text">{line.text || ' '}</code>
  </div>;
}

type DiffLine = { type: 'context' | 'add' | 'remove'; text: string; beforeLine?: number; afterLine?: number };

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

function pushContext(lines: DiffLine[], line: DiffLine) {
  const previous = lines[lines.length - 1];
  if (previous?.type === 'context') {
    const contextRun = lines.slice(Math.max(0, lines.length - 3)).filter(entry => entry.type === 'context').length;
    if (contextRun >= 3) return;
  }
  lines.push(line);
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
