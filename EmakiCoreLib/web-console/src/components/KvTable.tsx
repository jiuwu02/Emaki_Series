import { useRef } from 'react';

type StableEntry<T> = { _id: number; data: T };
let _nextId = 1;
function nextId() { return _nextId++; }

/** Editable key-value pair table with stable keys for React reconciliation. */
export function KvTable({ entries, onChange }: { entries: Array<{ key: string; value: unknown }>; onChange: (entries: Array<{ key: string; value: unknown }>) => void }) {
  const stableRef = useRef<StableEntry<{ key: string; value: unknown }>[]>([]);
  if (stableRef.current.length !== entries.length) {
    stableRef.current = entries.map((e, i) => stableRef.current[i] ? { ...stableRef.current[i], data: e } : { _id: nextId(), data: e });
  } else {
    stableRef.current = stableRef.current.map((s, i) => ({ ...s, data: entries[i] }));
  }
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
  const add = () => { stableRef.current.push({ _id: nextId(), data: { key: '', value: 0 } }); onChange([...entries, { key: '', value: 0 }]); };

  return (
    <div className="prop-kv" role="list" aria-label="键值对列表">
      {stable.map((entry, i) => (
        <div className="prop-kv-row" key={entry._id} role="listitem">
          <input type="text" value={String(entry.data.key)} onChange={e => update(i, 'key', e.target.value)} placeholder="键" aria-label={`键 ${i + 1}`} />
          <input type="text" value={String(entry.data.value ?? '')} onChange={e => update(i, 'value', e.target.value)} placeholder="值" aria-label={`值 ${i + 1}`} />
          <button type="button" className="prop-kv-del" onClick={() => remove(i)} aria-label={`删除第 ${i + 1} 项`}>×</button>
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>+ 添加</button>
    </div>
  );
}
