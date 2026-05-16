import type { ButtonHTMLAttributes, ReactNode } from 'react';

type ToggleChipProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  active?: boolean;
  children: ReactNode;
};

/** Pill-shaped toggle for dense option sets. */
export function ToggleChip({ active = false, className = '', children, ...props }: ToggleChipProps) {
  const classes = ['ui-chip', active ? 'active' : '', className].filter(Boolean).join(' ');
  return <button type="button" className={classes} aria-pressed={active} {...props}>{children}</button>;
}
