import type { HTMLAttributes, ReactNode } from 'react';

export type NoticeTone = 'ok' | 'bad';

export function InlineError({ className = '', children, ...props }: HTMLAttributes<HTMLDivElement> & { children: ReactNode }) {
  return <div className={`ui-inline-error ${className}`.trim()} role="alert" {...props}>{children}</div>;
}

export function ToastNotice({ tone, className = '', children, ...props }: HTMLAttributes<HTMLDivElement> & { tone: NoticeTone; children: ReactNode }) {
  return <div className={`ui-toast ${tone} ${className}`.trim()} role={tone === 'bad' ? 'alert' : 'status'} aria-live={tone === 'bad' ? 'assertive' : 'polite'} {...props}>{children}</div>;
}
