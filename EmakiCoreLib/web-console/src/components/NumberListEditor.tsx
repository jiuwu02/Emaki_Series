import { t } from '../i18n';
import { StableListEditor } from './StableListEditor';

/** Editable number list with stable keys. */
export function NumberListEditor({ items, onChange, layout = 'block', addFirst = true }: { items: number[]; onChange: (items: number[]) => void; layout?: 'block' | 'inline'; addFirst?: boolean }) {
  return <StableListEditor
    items={items}
    onChange={onChange}
    createItem={() => 0}
    className={`prop-kv--string-list prop-kv--string-list-${layout}${addFirst ? '' : ' prop-kv--string-list-add-last'}`}
    rowClassName="prop-kv-row prop-kv-row--single prop-list-row"
    addLabel={t('core.config.addItem')}
    addButtonClassName="prop-list-add"
    addButtonContent="+"
    addFirst={addFirst}
    renderItem={({ item, index, update, remove }) => <>
      <input type="number" value={String(item)} onChange={e => update(Number(e.target.value) || 0)} aria-label={t('core.list.numberAria', { index: index + 1 })} />
      <button type="button" className="prop-kv-del" onClick={remove} aria-label={t('core.config.deleteItem', { index: index + 1 })}>×</button>
    </>}
  />;
}
