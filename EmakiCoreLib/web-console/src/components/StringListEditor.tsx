import { t } from '../i18n';
import { StableListEditor } from './StableListEditor';

/** Editable string list with stable keys. */
export function StringListEditor({ items, onChange, placeholder }: { items: string[]; onChange: (items: string[]) => void; placeholder?: string }) {
  return <StableListEditor
    items={items}
    onChange={onChange}
    createItem={() => ''}
    rowClassName="prop-kv-row prop-kv-row--single"
    addLabel={t('core.config.addItem')}
    renderItem={({ item, index, update, remove }) => <>
      <input type="text" value={item} onChange={e => update(e.target.value)} placeholder={placeholder} aria-label={t('core.list.itemAria', { index: index + 1 })} />
      <button type="button" className="prop-kv-del" onClick={remove} aria-label={t('core.config.deleteItem', { index: index + 1 })}>×</button>
    </>}
  />;
}
