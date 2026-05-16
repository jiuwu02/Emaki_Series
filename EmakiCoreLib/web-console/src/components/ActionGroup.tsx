import type { HTMLAttributes, ReactNode } from 'react';

/** Compact toolbar row for clustered page actions. */
export function ActionGroup({ className = '', children, ...props }: HTMLAttributes<HTMLDivElement> & { children: ReactNode }) {
  return <div className={`ui-actions ${className}`.trim()} {...props}>{children}</div>;
}
