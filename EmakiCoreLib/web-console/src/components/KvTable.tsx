import { t } from '../i18n';
import { useStableEntries } from './useStableEntries';

/** Editable key-value pair table with stable keys for React reconciliation. */
export function KvTable({ entries, onChange }: { entries: Array<{ key: string; value: unknown }>; onChange: (entries: Array<{ key: string; value: unknown }>) => void }) {
  const stableRef = useStableEntries(entries);
  const stable = stableRef.current;

  const update = (i: number, field: 'key' | 'value', v: string) => {
    const next = [...entries];
    if (field === 'key') {
      next[i] = { ...next[i], key: v };
    } else {
      next[i] = { ...next[i], value: v === '' ? '' : isNaN(Number(v)) ? v : Number(v) };
    }
    onChange(next);
  };
  const remove = (i: number) => { stableRef.current.splice(i, 1); onChange(entries.filter((_, idx) => idx !== i)); };
  const add = () => onChange([...entries, { key: '', value: 0 }]);

  return (
    <div className="prop-kv" role="list" aria-label={t('core.kv.aria')}>
      {stable.map((entry, i) => (
        <div className="prop-kv-row" key={entry._id} role="listitem">
          <input type="text" value={String(entry.data.key)} onChange={e => update(i, 'key', e.target.value)} placeholder={t('core.kv.key')} aria-label={`${t('core.kv.key')} ${i + 1}`} />
          <input type="text" value={String(entry.data.value ?? '')} onChange={e => update(i, 'value', e.target.value)} placeholder={t('core.kv.value')} aria-label={`${t('core.kv.value')} ${i + 1}`} />
          <button type="button" className="prop-kv-del" onClick={() => remove(i)} aria-label={t('core.kv.delete', { index: i + 1 })}>×</button>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>+ {t('core.kv.add')}</button>
    </div>
  );
}
