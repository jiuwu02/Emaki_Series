import type { ReactNode } from 'react';
import { useStableEntries } from './useStableEntries';

export type StableListEditorRenderArgs<T> = {
  item: T;
  index: number;
  update: (item: T) => void;
  remove: () => void;
};

export type StableListEditorProps<T> = {
  items: T[];
  onChange: (items: T[]) => void;
  createItem: (items: T[]) => T;
  renderItem: (args: StableListEditorRenderArgs<T>) => ReactNode;
  addLabel: ReactNode;
  ariaLabel?: string;
  rowClassName?: string;
};

/** Shared stable list editor shell for add, remove, update, and React row identity. */
export function StableListEditor<T>({ items, onChange, createItem, renderItem, addLabel, ariaLabel, rowClassName = 'prop-kv-row' }: StableListEditorProps<T>) {
  const stableRef = useStableEntries(items);
  const stable = stableRef.current;

  const update = (index: number, item: T) => {
    const next = [...items];
    next[index] = item;
    onChange(next);
  };
  const remove = (index: number) => {
    stableRef.current.splice(index, 1);
    onChange(items.filter((_, itemIndex) => itemIndex !== index));
  };
  const add = () => onChange([...items, createItem(items)]);

  return (
    <div className="prop-kv" role="list" aria-label={ariaLabel}>
      {stable.map((entry, index) => (
        <div className={rowClassName} key={entry._id} role="listitem">
          {renderItem({ item: entry.data, index, update: item => update(index, item), remove: () => remove(index) })}
        </div>
      ))}
      <button type="button" className="prop-add" onClick={add}>+ {addLabel}</button>
    </div>
  );
}
