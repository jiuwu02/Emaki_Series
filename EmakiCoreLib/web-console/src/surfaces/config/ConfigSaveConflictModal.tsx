import { useRef } from 'react';
import { ActionGroup, Button } from '../../components';
import { useDialogFocus } from '../../components/useDialogFocus';
import { t } from '../../i18n';
import type { SaveConflict } from './ConfigStructuredSurface';

export function ConfigSaveConflictModal({ conflict, onCancel }: { conflict: SaveConflict; onCancel: () => void }) {
  const dialogRef = useRef<HTMLElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  const preview = conflict.pendingChanges.slice(0, 12);
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <section ref={dialogRef} className="reload-confirm-dialog diff-dialog" role="dialog" aria-modal="true" aria-labelledby="save-conflict-title" aria-describedby="save-conflict-desc" tabIndex={-1}>
      <div className="reload-confirm-head diff-dialog-head">
        <span>{t('core.conflict.kicker')}</span>
        <h3 id="save-conflict-title">{t('core.conflict.title')}</h3>
      </div>
      <div className="reload-confirm-body diff-dialog-body">
        <p id="save-conflict-desc">{t('core.conflict.desc', { file: conflict.fileLabel })}</p>
        {conflict.savedCount > 0 && <p className="config-create-hint">{t('core.conflict.savedNote', { count: conflict.savedCount })}</p>}
        <div className="reload-change-summary" aria-label={t('core.conflict.pendingAria', { count: conflict.pendingChanges.length })}>
          <strong>{t('core.conflict.pendingTitle', { count: conflict.pendingChanges.length })}</strong>
          <div className="editor-change-list">
            {preview.map(change => <div className="editor-change-row" key={change.path}>
              <code>{change.path}</code>
              <span>{change.label || change.path}</span>
            </div>)}
          </div>
          {conflict.pendingChanges.length > preview.length && <p>{t('core.editor.changesMore', { count: conflict.pendingChanges.length - preview.length })}</p>}
        </div>
      </div>
      <ActionGroup className="reload-confirm-actions diff-dialog-actions">
        <Button onClick={onCancel}>{t('core.gui.cancel')}</Button>
        <Button variant="danger" onClick={() => void conflict.onOverwrite()}>{t('core.conflict.overwrite')}</Button>
        <Button variant="primary" autoFocus onClick={() => void conflict.onReplay()}>{t('core.conflict.replay')}</Button>
      </ActionGroup>
    </section>
  </div>;
}
