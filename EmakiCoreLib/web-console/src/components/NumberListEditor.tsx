import { useStableEntries } from './useStableEntries';

/** Editable number list with stable keys. */
export function NumberListEditor({ items, onChange }: { items: number[]; onChange: (items: number[]) => void }) {
  const stableRef = useStableEntries(items);
  const stable = stableRef.current;

  const update = (i: number, v: string) => { const next = [...items]; next[i] = Number(v) || 0; onChange(next); };
  const remove = (i: number) => { stableRef.current.splice(i, 1); onChange(items.filter((_, idx) => idx !== i)); };
  const add = () => onChange([...items, 0]);

  return (
    <div className="prop-kv" role="list">
      {stable.map((entry, i) => (
        <div className="prop-kv-row prop-kv-row--single" key={entry._id} role="listitem">
          <input type="number" value={String(entry.data)} onChange={e => update(i, e.target.value)} aria-label={`数值 ${i + 1}`} />
          <button type="button" className="prop-kv-del" onClick={() => remove(i)} aria-label={`删除第 ${i + 1} 项`}>×</button>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>+ 添加</button>
    </div>
  );
}
