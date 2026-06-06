import { t } from '../i18n';
import { StableListEditor } from './StableListEditor';

/** Editable number list with stable keys. */
export function NumberListEditor({ items, onChange }: { items: number[]; onChange: (items: number[]) => void }) {
  return <StableListEditor
    items={items}
    onChange={onChange}
    createItem={() => 0}
    rowClassName="prop-kv-row prop-kv-row--single"
    addLabel={t('core.config.addItem')}
    renderItem={({ item, index, update, remove }) => <>
      <input type="number" value={String(item)} onChange={e => update(Number(e.target.value) || 0)} aria-label={t('core.list.numberAria', { index: index + 1 })} />
      <button type="button" className="prop-kv-del" onClick={remove} aria-label={t('core.config.deleteItem', { index: index + 1 })}>×</button>
    </>}
  />;
}
