import { useRef } from 'react';

export type StableEntry<T> = { _id: number; data: T };

let nextStableId = 1;
function nextId() { return nextStableId++; }

/** Preserve row identity while array editors reorder, add, or remove values. */
export function useStableEntries<T>(items: T[]) {
  const stableRef = useRef<StableEntry<T>[]>([]);

  if (stableRef.current.length !== items.length) {
    stableRef.current = items.map((item, index) => stableRef.current[index] ? { ...stableRef.current[index], data: item } : { _id: nextId(), data: item });
  } else {
    stableRef.current = stableRef.current.map((entry, index) => ({ ...entry, data: items[index] }));
  }

  return stableRef;
}
