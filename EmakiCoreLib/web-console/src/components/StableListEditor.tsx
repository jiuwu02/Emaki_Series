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
  className?: string;
  rowClassName?: string;
  addButtonClassName?: string;
  addButtonContent?: ReactNode;
  addFirst?: boolean;
};

/** Shared stable list editor shell for add, remove, update, and React row identity. */
export function StableListEditor<T>({ items, onChange, createItem, renderItem, addLabel, ariaLabel, className = '', rowClassName = 'prop-kv-row', addButtonClassName = 'prop-add', addButtonContent, addFirst = false }: StableListEditorProps<T>) {
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

  const addButton = <button type="button" className={addButtonClassName} onClick={add} aria-label={typeof addLabel === 'string' ? addLabel : undefined}>{addButtonContent ?? <>+ {addLabel}</>}</button>;
  const rows = stable.map((entry, index) => (
    <div className={rowClassName} key={entry._id} role="listitem">
      {renderItem({ item: entry.data, index, update: item => update(index, item), remove: () => remove(index) })}
    </div>
  ));
  return (
    <div className={`prop-kv ${className}`.trim()} role="list" aria-label={ariaLabel}>
      {addFirst ? addButton : rows}
      {addFirst ? rows : addButton}
    </div>
  );
}
