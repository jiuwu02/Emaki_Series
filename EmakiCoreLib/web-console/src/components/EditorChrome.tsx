import { useMemo, useRef, useState, type ReactNode } from 'react';
import { t } from '../i18n';
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
  sourceEditable?: boolean;
  sourceError?: string | null;
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
  sourceEditable,
  sourceError,
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
  const [changesOpen, setChangesOpen] = useState(false);
  const count = changedCount ?? changes.length;
  const hasTrackedChanges = count > 0;
  const canSave = Boolean(onSave) && dirty && !saving && !loading;
  const sourceDirtyOnly = dirty && !hasTrackedChanges;
  const finalSaveLabel = saveLabel ?? (hasTrackedChanges ? t('core.action.saveCount', { count }) : sourceDirtyOnly ? t('core.action.saveSource') : t('core.action.save'));

  const previewChanges = useMemo(() => changes.slice(0, 12), [changes]);

  function requestReload() {
    if (!onReload || loading || saving) return;
    if (dirty) setReloadOpen(true);
    else onReload();
  }

  function confirmReload() {
    setReloadOpen(false);
    onReload?.();
  }

  return <>
    <div className={`editor-chrome ${className}`.trim()}>
      <div className="editor-chrome-title">
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}{dirty && <span className="dirty-inline">{t('core.item.unsaved')}</span>}</p>}
      </div>
      <ActionGroup className="editor-chrome-actions" role="toolbar" aria-label={t('core.editor.toolbarAria')}>
        {children}
        {onUndo && <Button size="sm" onClick={onUndo} disabled={!canUndo || saving || loading} title={t('core.editor.undoHint')}>{t('core.editor.undo')}</Button>}
        {onRedo && <Button size="sm" onClick={onRedo} disabled={!canRedo || saving || loading} title={t('core.editor.redoHint')}>{t('core.editor.redo')}</Button>}
        <Button size="sm" onClick={() => setSourceOpen(true)} disabled={!source}>{sourceLabel ?? t('core.item.source')}</Button>
        {onReload && <Button size="sm" onClick={requestReload} disabled={loading || saving}>{reloadLabel ?? t('core.gui.reload')}</Button>}
        <span className="editor-save-wrap" onMouseEnter={() => setChangesOpen(true)} onMouseLeave={() => setChangesOpen(false)} onFocus={() => setChangesOpen(true)} onBlur={() => setChangesOpen(false)}>
          <Button size="sm" variant="primary" ready={dirty && hasTrackedChanges} onClick={onSave} disabled={!canSave}>{saving ? t('core.script.saving') : finalSaveLabel}</Button>
          {dirty && hasTrackedChanges && changesOpen && <ChangePopover changes={previewChanges} count={count} />}
        </span>
      </ActionGroup>
    </div>
    {sourceOpen && <SourceModal source={source} editable={sourceEditable ?? Boolean(onSourceChange)} error={sourceError} onChange={onSourceChange} onClose={() => setSourceOpen(false)} />}
    {reloadOpen && <ReloadModal changes={previewChanges} count={count} onCancel={() => setReloadOpen(false)} onConfirm={confirmReload} />}
  </>;
}

function ChangePopover({ changes, count }: { changes: EditorChange[]; count: number }) {
  return <div className="editor-change-popover" role="status">
    <strong>{t('core.editor.changesTitle', { count })}</strong>
    <ChangeList changes={changes} count={count} />
  </div>;
}

function ChangeList({ changes, count }: { changes: EditorChange[]; count: number }) {
  return changes.length ? <>
    <div className="editor-change-list">
      {changes.map(change => <div className="editor-change-row" key={change.path}>
        <code>{change.path}</code>
        <span>{change.label || change.path}</span>
        <div className="editor-change-diff">
          <span className="editor-change-value before"><b>−</b><code>{formatChangeValue(change.before)}</code></span>
          <span className="editor-change-value after"><b>+</b><code>{formatChangeValue(change.after)}</code></span>
        </div>
      </div>)}
    </div>
    {count > changes.length && <p>{t('core.editor.changesMore', { count: count - changes.length })}</p>}
  </> : <p>{t('core.editor.changedSource')}</p>;
}

function SourceModal({ source, editable, error, onChange, onClose }: { source: string; editable?: boolean; error?: string | null; onChange?: (source: string) => void; onClose: () => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  useDialogFocus(dialogRef, onClose);
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={dialogRef} className="editor-source-modal" role="dialog" aria-modal="true" aria-labelledby="editor-source-title" tabIndex={-1}>
      <header className="editor-modal-head">
        <div><span>{t('core.item.source')}</span><h3 id="editor-source-title">{t('core.editor.sourceTitle')}</h3></div>
        <Button size="sm" onClick={onClose}>{t('core.i18n.close')}</Button>
      </header>
      {editable ? <textarea className="editor-source-code editor-source-textarea" value={source} onChange={event => onChange?.(event.target.value)} spellCheck={false} aria-label={t('core.editor.sourceTitle')} /> : <pre className="editor-source-code">{source}</pre>}
      {error && <p className="editor-source-error" role="alert">{error}</p>}
    </section>
  </div>;
}

function ReloadModal({ changes, count, onCancel, onConfirm }: { changes: EditorChange[]; count: number; onCancel: () => void; onConfirm: () => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section ref={dialogRef} className="reload-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="editor-reload-title" aria-describedby="editor-reload-desc" tabIndex={-1}>
      <div className="reload-confirm-head">
        <span>{t('core.gui.unsavedChanges')}</span>
        <h3 id="editor-reload-title">{t('core.gui.reloadDropsChanges')}</h3>
      </div>
      <div className="reload-confirm-body">
        <p id="editor-reload-desc">{t('core.editor.reloadDesc')}</p>
        {count > 0 && <div className="reload-change-summary" aria-label={t('core.editor.reloadChangesAria', { count })}>
          <strong>{t('core.editor.changesTitle', { count })}</strong>
          <ChangeList changes={changes} count={count} />
        </div>}
      </div>
      <ActionGroup className="reload-confirm-actions">
        <Button onClick={onCancel} autoFocus>{t('core.gui.cancel')}</Button>
        <Button variant="danger" onClick={onConfirm}>{t('core.gui.continueReload')}</Button>
      </ActionGroup>
    </section>
  </div>;
}

function formatChangeValue(value: unknown): string {
  if (value === undefined) return '∅';
  if (value === null) return 'null';
  if (typeof value === 'string') return value.length > 42 ? `${value.slice(0, 39)}...` : value;
  try {
    const text = JSON.stringify(value);
    return text.length > 42 ? `${text.slice(0, 39)}...` : text;
  } catch {
    return String(value);
  }
}
