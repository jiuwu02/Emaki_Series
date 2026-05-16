import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { t } from '../i18n';
import { ActionGroup } from './ActionGroup';
import { Button } from './Button';

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
  const canSave = Boolean(onSave) && dirty && !saving && !loading;
  const finalSaveLabel = saveLabel ?? (count > 0 ? t('core.action.saveCount', { count }) : t('core.action.save'));

  useEffect(() => {
    if (!sourceOpen && !reloadOpen) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setSourceOpen(false);
        setReloadOpen(false);
      }
    };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, [sourceOpen, reloadOpen]);

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
      <ActionGroup className="editor-chrome-actions">
        {children}
        <Button size="sm" onClick={() => setSourceOpen(true)} disabled={!source}>{sourceLabel ?? t('core.item.source')}</Button>
        {onReload && <Button size="sm" onClick={requestReload} disabled={loading || saving}>{reloadLabel ?? t('core.gui.reload')}</Button>}
        <span className="editor-save-wrap" onMouseEnter={() => setChangesOpen(true)} onMouseLeave={() => setChangesOpen(false)} onFocus={() => setChangesOpen(true)} onBlur={() => setChangesOpen(false)}>
          <Button size="sm" variant="primary" ready={dirty} onClick={onSave} disabled={!canSave}>{saving ? t('core.script.saving') : finalSaveLabel}</Button>
          {dirty && changesOpen && <ChangePopover changes={previewChanges} count={count} />}
        </span>
      </ActionGroup>
    </div>
    {sourceOpen && <SourceModal source={source} editable={sourceEditable ?? Boolean(onSourceChange)} error={sourceError} onChange={onSourceChange} onClose={() => setSourceOpen(false)} />}
    {reloadOpen && <ReloadModal onCancel={() => setReloadOpen(false)} onConfirm={confirmReload} />}
  </>;
}

function ChangePopover({ changes, count }: { changes: EditorChange[]; count: number }) {
  return <div className="editor-change-popover" role="status">
    <strong>{t('core.editor.changesTitle', { count })}</strong>
    {changes.length ? <div className="editor-change-list">
      {changes.map(change => <div className="editor-change-row" key={change.path}>
        <code>{change.path}</code>
        <span>{change.label || change.path}</span>
        <small>{formatChangeValue(change.before)} → {formatChangeValue(change.after)}</small>
      </div>)}
    </div> : <p>{t('core.editor.changedSource')}</p>}
  </div>;
}

function SourceModal({ source, editable, error, onChange, onClose }: { source: string; editable?: boolean; error?: string | null; onChange?: (source: string) => void; onClose: () => void }) {
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section className="editor-source-modal" role="dialog" aria-modal="true" aria-labelledby="editor-source-title">
      <header className="editor-modal-head">
        <div><span>{t('core.item.source')}</span><h3 id="editor-source-title">{t('core.editor.sourceTitle')}</h3></div>
        <Button size="sm" onClick={onClose}>{t('core.i18n.close')}</Button>
      </header>
      {editable ? <textarea className="editor-source-code editor-source-textarea" value={source} onChange={event => onChange?.(event.target.value)} spellCheck={false} /> : <pre className="editor-source-code">{source}</pre>}
      {error && <p className="editor-source-error" role="alert">{error}</p>}
    </section>
  </div>;
}

function ReloadModal({ onCancel, onConfirm }: { onCancel: () => void; onConfirm: () => void }) {
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section className="reload-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="editor-reload-title" aria-describedby="editor-reload-desc">
      <div className="reload-confirm-head">
        <span>{t('core.gui.unsavedChanges')}</span>
        <h3 id="editor-reload-title">{t('core.gui.reloadDropsChanges')}</h3>
      </div>
      <div className="reload-confirm-body"><p id="editor-reload-desc">{t('core.editor.reloadDesc')}</p></div>
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
