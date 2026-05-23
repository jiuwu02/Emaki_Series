import { useEffect, useMemo, useRef, useState } from 'react';
import { Button } from './components';
import { useDialogFocus } from './components/useDialogFocus';
import { getLocale, getModuleLocaleBundles, getRegisteredLocales, replaceLocaleMessages, t, type LocaleMessages } from './i18n';

export type I18nTarget = { moduleId: string };

export function I18nBundleModal({ target, onClose, onSaved }: { target: I18nTarget; onClose: () => void; onSaved?: (locale: string) => void }) {
  const bundles = useMemo(() => getModuleLocaleBundles(target.moduleId), [target.moduleId]);
  const registeredLocales = getRegisteredLocales();
  const [locale, setLocaleDraft] = useState(() => bundles[0]?.locale ?? getLocale());
  const [query, setQuery] = useState('');
  const sourceMessages = bundles.find((bundle) => bundle.locale === locale)?.messages ?? {};
  const [draft, setDraft] = useState<LocaleMessages>(() => ({ ...sourceMessages }));
  const [newKey, setNewKey] = useState('');
  const [newValue, setNewValue] = useState('');
  const dialogRef = useRef<HTMLElement | null>(null);
  useDialogFocus(dialogRef, onClose);

  useEffect(() => {
    setDraft({ ...(bundles.find((bundle) => bundle.locale === locale)?.messages ?? {}) });
    setQuery('');
  }, [locale, target.moduleId]);


  const entries = Object.entries(draft).sort(([left], [right]) => left.localeCompare(right));
  const visibleEntries = entries.filter(([key, value]) => {
    const term = query.trim().toLowerCase();
    return !term || key.toLowerCase().includes(term) || value.toLowerCase().includes(term);
  });
  const dirty = JSON.stringify(draft) !== JSON.stringify(sourceMessages);

  function updateValue(key: string, value: string) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function removeKey(key: string) {
    setDraft((current) => {
      const next = { ...current };
      delete next[key];
      return next;
    });
  }

  function addEntry() {
    const key = newKey.trim();
    if (!key) return;
    setDraft((current) => ({ ...current, [key]: newValue }));
    setNewKey('');
    setNewValue('');
  }

  function save() {
    replaceLocaleMessages(locale, { ...draft }, { persist: true, moduleId: target.moduleId });
    onSaved?.(locale);
    onClose();
  }

  return (
    <div className="i18n-modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <section ref={dialogRef} className="i18n-modal" role="dialog" aria-modal="true" aria-labelledby="i18n-modal-title" tabIndex={-1}>
        <header className="i18n-modal-head">
          <div>
            <span>{t('core.i18n.title')}</span>
            <h2 id="i18n-modal-title">{target.moduleId}</h2>
            <p>{t('core.i18n.subtitle', { module: target.moduleId, locale, count: entries.length })}</p>
          </div>
          <Button size="sm" onClick={onClose}>{t('core.i18n.close')}</Button>
        </header>
        <div className="i18n-modal-toolbar">
          <label>
            <span>{t('core.locale.label')}</span>
            <select value={locale} onChange={(event) => setLocaleDraft(event.target.value)}>
              {[...new Set([...bundles.map((bundle) => bundle.locale), ...registeredLocales])].map((entry) => <option key={entry} value={entry}>{localeLabel(entry)}</option>)}
            </select>
          </label>
          <label className="i18n-search">
            <span>{t('core.i18n.search')}</span>
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="core.gui.save" />
          </label>
        </div>
        <p className="i18n-hint">{t('core.i18n.storageHint')}</p>
        {entries.length === 0 ? <div className="i18n-empty" role="status">{t('core.i18n.empty')}</div> : (
          <div className="i18n-table" role="table" aria-label={t('core.i18n.title')}>
            <div className="i18n-table-head" role="row">
              <span role="columnheader">{t('core.i18n.key')}</span>
              <span role="columnheader">{t('core.i18n.value')}</span>
              <span />
            </div>
            {visibleEntries.map(([key, value]) => (
              <div className="i18n-row" role="row" key={key}>
                <code title={key}>{key}</code>
                <textarea value={value} onChange={(event) => updateValue(key, event.target.value)} rows={value.length > 72 ? 3 : 1} aria-label={t('core.i18n.valueForKey', { key })} />
                <button type="button" className="i18n-row-delete" onClick={() => removeKey(key)} aria-label={t('core.i18n.deleteKey', { key })}>{t('core.i18n.delete')}</button>
              </div>
            ))}
          </div>
        )}
        <div className="i18n-add-row">
          <input value={newKey} onChange={(event) => setNewKey(event.target.value)} placeholder={t('core.i18n.addKey')} aria-label={t('core.i18n.addKey')} />
          <input value={newValue} onChange={(event) => setNewValue(event.target.value)} placeholder={t('core.i18n.addValue')} aria-label={t('core.i18n.addValue')} />
          <Button size="sm" onClick={addEntry} disabled={!newKey.trim()}>{t('core.i18n.add')}</Button>
        </div>
        <footer className="i18n-modal-actions">
          <Button onClick={() => setDraft({ ...sourceMessages })} disabled={!dirty}>{t('core.i18n.reset')}</Button>
          <Button variant="primary" ready={dirty} onClick={save} disabled={!dirty}>{t('core.action.save')}</Button>
        </footer>
      </section>
    </div>
  );
}

function localeLabel(locale: string): string {
  const normalized = String(locale ?? '').replace('_', '-');
  if (normalized === 'zh-CN') return '简体中文';
  if (normalized === 'en-US') return 'English';
  return normalized;
}
