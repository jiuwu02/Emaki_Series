import { forwardRef, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { t } from '../i18n';
import { CodeEditor } from './CodeEditor';
import { ActionGroup } from './ActionGroup';
import { Button } from './Button';
import { FieldValueDiff, SourceDiff } from './DiffViewer';
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
  meta?: ReactNode;
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
  meta,
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
        {meta && <div className="editor-chrome-meta">{meta}</div>}
      </div>
      <ActionGroup className="editor-chrome-actions" role="toolbar" aria-label={t('core.editor.toolbarAria', undefined, 'Editor actions')}>
        {children}
        <ShortcutHint />
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

// Keyboard-shortcut discovery affordance. Surfaces the shortcuts that already exist (save, undo,
// redo, tree navigation) so power users find them without trial and error.
function ShortcutHint() {
  const [open, setOpen] = useState(false);
  const shortcuts: { keys: string; desc: string }[] = [
    { keys: 'Ctrl/⌘ + S', desc: t('core.shortcut.save', undefined, 'Save changes') },
    { keys: 'Ctrl/⌘ + Z', desc: t('core.shortcut.undo', undefined, 'Undo') },
    { keys: 'Ctrl/⌘ + Y', desc: t('core.shortcut.redo', undefined, 'Redo') },
    { keys: '↑ ↓ ← →', desc: t('core.shortcut.treeNav', undefined, 'Navigate the file tree') },
    { keys: 'Esc', desc: t('core.shortcut.closeDialog', undefined, 'Close dialog') }
  ];
  return <span className="editor-shortcut-wrap" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)} onFocus={() => setOpen(true)} onBlur={() => setOpen(false)}>
    <button type="button" className="editor-shortcut-button" aria-label={t('core.shortcut.title', undefined, 'Keyboard shortcuts')} aria-expanded={open}>
      <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false"><rect x="1.5" y="4" width="13" height="8" rx="1.5" fill="none" stroke="currentColor" strokeWidth="1.2" /><path d="M4 6.6h.01M6.4 6.6h.01M8.8 6.6h.01M11.2 6.6h.01M4 9.4h6" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" /></svg>
    </button>
    {open && <div className="editor-shortcut-popover" role="status">
      <strong>{t('core.shortcut.title', undefined, 'Keyboard shortcuts')}</strong>
      <dl>
        {shortcuts.map(item => <div className="editor-shortcut-row" key={item.keys}><dt><kbd>{item.keys}</kbd></dt><dd>{item.desc}</dd></div>)}
      </dl>
    </div>}
  </span>;
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

  const errorRef = useRef<HTMLParagraphElement | null>(null);
  useEffect(() => { if (error) errorRef.current?.scrollIntoView({ block: 'nearest' }); }, [error]);
  const errorLine = error ? sourceErrorLine(error) : null;

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
      {error && <p ref={errorRef} className="editor-source-error" role="alert">{errorLine != null && <code className="editor-source-error-line">{t('core.editor.sourceErrorLine', { line: errorLine }, 'Line {line}')}</code>}{error}</p>}
    </section>
  </div>;
}

// Pull a 1-based line number out of common YAML/parser error messages so the modal can flag it
// prominently. Matches "line 12", "第 12 行", and "(12:3)" style coordinates.
function sourceErrorLine(message: string): number | null {
  const match = message.match(/line\s+(\d+)/i) || message.match(/第\s*(\d+)\s*行/) || message.match(/\((\d+):\d+\)/);
  if (!match) return null;
  const line = Number(match[1]);
  return Number.isFinite(line) && line > 0 ? line : null;
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
