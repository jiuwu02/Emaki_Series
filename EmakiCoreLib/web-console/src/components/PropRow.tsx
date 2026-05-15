import { useId } from 'react';

/** A labeled property row with label-input association for accessibility. */
export function PropRow({ label, children, wide }: { label: string; children: React.ReactNode; wide?: boolean }) {
  const id = useId();
  return (
    <div className={`prop-row${wide ? ' prop-row--wide' : ''}`}>
      <label className="prop-label" htmlFor={id}>{label}</label>
      <span className="prop-value" id={`${id}-wrap`}>{children}</span>
    </div>
  );
}
