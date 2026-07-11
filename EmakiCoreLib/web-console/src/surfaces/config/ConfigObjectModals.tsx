import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ActionGroup, Button } from '../../components';
import { useDialogFocus } from '../../components/useDialogFocus';
import { getLocale, t } from '../../i18n';
import { fieldLabel, humanizeFieldLabel } from '../../lib';
import type { WebConfigNode } from '../../types';
import type { ConfigDraftScope } from './ConfigDraftRuntime';
import { renderSchemaField } from './ConfigFieldRuntime';
import { createConfigChild, defaultTemplateValues, deleteConfigObject, emptyCreateTemplate, getConfigObject, nextConfigChildKey, parseSafeYaml } from './ConfigObjectRuntime';
import type { ConfigSourceDocument, ConfigToast } from './ConfigSourceRuntime';

export function ConfigCreateChildModal({ scope, node, source, onCancel, onCreated, setToast }: { scope: ConfigDraftScope; node: WebConfigNode; source: ConfigSourceDocument; onCancel: () => void; onCreated: (nodes: WebConfigNode[]) => void; setToast: (toast: ConfigToast) => void }) {
  const dialogRef = useRef<HTMLFormElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  const templates = node.createTemplates?.length ? node.createTemplates : [emptyCreateTemplate(node)];
  const [templateId, setTemplateId] = useState(templates[0]?.id ?? 'empty');
  const template = templates.find(entry => entry.id === templateId) ?? templates[0];
  const [keyName, setKeyName] = useState('');
  const [values, setValues] = useState<Record<string, unknown>>(() => defaultTemplateValues(template));

  useEffect(() => setValues(defaultTemplateValues(template)), [template?.id]);

  function submit(event: FormEvent) {
    event.preventDefault();
    const key = keyName.trim().replace(/\s+/g, '_');
    if (!key) return;
    createConfigChild(node, source, key, values, template, setToast, onCreated);
  }

  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <form ref={dialogRef} className="config-create-dialog" role="dialog" aria-modal="true" aria-labelledby="config-create-title" onSubmit={submit}>
      <div className="reload-confirm-head"><span>{t('core.config.createKicker')}</span><h3 id="config-create-title">{t('core.config.createTitle')}</h3></div>
      <div className="reload-confirm-body config-create-body">
        <p>{t('core.config.createDesc', { path: node.path })}</p>
        <label className="file-confirm-field"><span>{t('core.config.createKey')}</span><input autoFocus value={keyName} onChange={event => setKeyName(event.target.value)} placeholder={nextConfigChildKey(getConfigObject(parseSafeYaml(source.content), node.path.split('.')))} /></label>
        {templates.length > 1 && <label className="file-confirm-field"><span>{t('core.config.createTemplate')}</span><select value={template.id} onChange={event => setTemplateId(event.target.value)}>{templates.map(entry => <option key={entry.id} value={entry.id}>{entry.label}</option>)}</select></label>}
        {template.fields.length === 0 && <p className="config-create-hint">{t('core.config.createNoTemplate')}</p>}
        <div className="config-create-fields">{template.fields.map(field => <div className="object-list-field" key={field.path}><label>{fieldLabel(field.path, { moduleId: scope.moduleId, namespace: scope.moduleId, fallback: getLocale().startsWith('zh') ? (field.label || field.path) : humanizeFieldLabel(field.path) })}</label>{renderSchemaField(field, values[field.path], next => setValues(current => ({ ...current, [field.path]: next })), scope.moduleId, field.path)}</div>)}</div>
      </div>
      <ActionGroup className="reload-confirm-actions"><Button type="button" onClick={onCancel}>{t('core.gui.cancel')}</Button><Button type="submit" variant="primary" disabled={!keyName.trim() || source.loading || !!source.error}>{t('core.config.create')}</Button></ActionGroup>
    </form>
  </div>;
}

export function ConfigDeleteObjectModal({ node, source, onCancel, onDeleted, setToast }: { node: WebConfigNode; source: ConfigSourceDocument; onCancel: () => void; onDeleted: (path: string) => void; setToast: (toast: ConfigToast) => void }) {
  const dialogRef = useRef<HTMLFormElement | null>(null);
  useDialogFocus(dialogRef, onCancel);
  const [confirmPath, setConfirmPath] = useState('');
  function submit(event: FormEvent) {
    event.preventDefault();
    if (confirmPath !== node.path) return;
    deleteConfigObject(node, source, setToast, onDeleted);
  }
  return <div className="editor-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onCancel(); }}>
    <form ref={dialogRef} className="file-action-dialog danger config-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="config-delete-title" onSubmit={submit}>
      <div className="reload-confirm-head"><span>{t('core.config.deleteObjectKicker')}</span><h3 id="config-delete-title">{t('core.config.deleteObjectTitle')}</h3></div>
      <div className="reload-confirm-body"><p>{t('core.config.deleteObjectDesc', { path: node.path })}</p><code className="file-delete-path">{node.path}</code><label className="file-confirm-field"><span>{t('core.config.deleteObjectConfirmLabel')}</span><input autoFocus value={confirmPath} onChange={event => setConfirmPath(event.target.value)} placeholder={node.path} /></label></div>
      <ActionGroup className="reload-confirm-actions"><Button type="button" onClick={onCancel}>{t('core.gui.cancel')}</Button><Button type="submit" variant="danger" disabled={confirmPath !== node.path || source.loading || !!source.error}>{t('core.config.deleteObjectConfirm')}</Button></ActionGroup>
    </form>
  </div>;
}
