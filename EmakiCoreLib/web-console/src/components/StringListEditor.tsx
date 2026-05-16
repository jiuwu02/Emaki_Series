import { useStableEntries } from './useStableEntries';

/** Editable string list with stable keys. */
export function StringListEditor({ items, onChange, placeholder }: { items: string[]; onChange: (items: string[]) => void; placeholder?: string }) {
  const stableRef = useStableEntries(items);
  const stable = stableRef.current;

  const update = (i: number, v: string) => { const next = [...items]; next[i] = v; onChange(next); };
  const remove = (i: number) => { stableRef.current.splice(i, 1); onChange(items.filter((_, idx) => idx !== i)); };
  const add = () => onChange([...items, '']);

  return (
    <div className="prop-kv" role="list">
      {stable.map((entry, i) => (
        <div className="prop-kv-row prop-kv-row--single" key={entry._id} role="listitem">
          <input type="text" value={entry.data} onChange={e => update(i, e.target.value)} placeholder={placeholder} aria-label={`项 ${i + 1}`} />
          <button type="button" className="prop-kv-del" onClick={() => remove(i)} aria-label={`删除第 ${i + 1} 项`}>×</button>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>+ 添加</button>
    </div>
  );
}
