import { t } from '../i18n';
import { StableListEditor } from './StableListEditor';

export type KvEntry = { key: string; value: unknown };
export type KvTableProps = {
  entries: KvEntry[];
  onChange: (entries: KvEntry[]) => void;
  valuePlaceholder?: string;
  addKeyPrefix?: string;
  createEntry?: (entries: KvEntry[]) => KvEntry;
  parseValue?: (value: string) => unknown;
};

function defaultParseValue(value: string) {
  return value === '' ? '' : isNaN(Number(value)) ? value : Number(value);
}

function nextUniqueKey(keys: string[], prefix: string): string {
  const used = new Set(keys.map(key => key.trim()).filter(Boolean));
  if (!used.has(prefix)) return prefix;
  let index = 2;
  while (used.has(`${prefix}_${index}`)) index++;
  return `${prefix}_${index}`;
}

/** Editable key-value pair table with stable keys for React reconciliation. */
export function KvTable({ entries, onChange, valuePlaceholder = t('core.kv.value'), addKeyPrefix, createEntry, parseValue = defaultParseValue }: KvTableProps) {
  const createDefaultEntry = (currentEntries: KvEntry[]) => ({
    key: addKeyPrefix ? nextUniqueKey(currentEntries.map(entry => entry.key), addKeyPrefix) : '',
    value: 0
  });

  return <StableListEditor
    items={entries}
    onChange={onChange}
    createItem={createEntry ?? createDefaultEntry}
    addLabel={t('core.kv.add')}
    ariaLabel={t('core.kv.aria')}
    renderItem={({ item, index, update, remove }) => <>
      <input type="text" value={item.key} onChange={e => update({ ...item, key: e.target.value })} placeholder={t('core.kv.key')} aria-label={`${t('core.kv.key')} ${index + 1}`} />
      <input type="text" value={item.value == null ? '' : String(item.value)} onChange={e => update({ ...item, value: parseValue(e.target.value) })} placeholder={valuePlaceholder} aria-label={`${t('core.kv.value')} ${index + 1}`} />
      <button type="button" className="prop-kv-del" onClick={remove} aria-label={t('core.kv.delete', { index: index + 1 })}>×</button>
    </>}
  />;
}
