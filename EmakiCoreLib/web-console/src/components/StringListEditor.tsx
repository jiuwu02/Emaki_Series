import { t } from '../i18n';
import { StableListEditor } from './StableListEditor';

/** Editable string list with stable keys. */
export function StringListEditor({ items, onChange, placeholder, ariaLabel, layout = 'block', addFirst = true }: { items: string[]; onChange: (items: string[]) => void; placeholder?: string; ariaLabel?: string; layout?: 'block' | 'inline'; addFirst?: boolean }) {
  return <StableListEditor
    items={items}
    onChange={onChange}
    createItem={() => ''}
    className={`prop-kv--string-list prop-kv--string-list-${layout}${addFirst ? '' : ' prop-kv--string-list-add-last'}`}
    rowClassName="prop-kv-row prop-kv-row--single prop-list-row"
    addLabel={t('core.config.addItem')}
    ariaLabel={ariaLabel}
    addButtonClassName="prop-list-add"
    addButtonContent="+"
    addFirst={addFirst}
    renderItem={({ item, index, update, remove }) => <>
      <input type="text" value={item} onChange={e => update(e.target.value)} placeholder={placeholder} aria-label={t('core.list.itemAria', { index: index + 1 })} />
      <button type="button" className="prop-kv-del" onClick={remove} aria-label={t('core.config.deleteItem', { index: index + 1 })}>×</button>
    </>}
  />;
}
