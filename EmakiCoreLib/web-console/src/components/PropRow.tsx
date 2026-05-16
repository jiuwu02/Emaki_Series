import { useId } from 'react';

/** A labeled property row with label-input association for accessibility. */
export function PropRow({ label, children, wide, changed }: { label: string; children: React.ReactNode; wide?: boolean; changed?: boolean }) {
  const id = useId();
  return (
    <div className={`prop-row${wide ? ' prop-row--wide' : ''}${changed ? ' changed' : ''}`}>
      <label className="prop-label" htmlFor={id}>{label}</label>
      <span className="prop-value" id={`${id}-wrap`}>{children}</span>
    </div>
  );
}
