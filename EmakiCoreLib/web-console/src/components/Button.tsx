import type { ButtonHTMLAttributes, ReactNode } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'soft' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'icon';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  ready?: boolean;
  fullWidth?: boolean;
  children: ReactNode;
};

/** Shared console button with the compact, line-driven Emaki visual vocabulary. */
export function Button({ variant = 'secondary', size = 'md', ready = false, fullWidth = false, className = '', children, ...props }: ButtonProps) {
  const classes = [
    'ui-button',
    `ui-button--${variant}`,
    `ui-button--${size}`,
    ready ? 'ui-button--ready' : '',
    fullWidth ? 'ui-button--full' : '',
    className
  ].filter(Boolean).join(' ');

  return <button type="button" className={classes} {...props}>{children}</button>;
}
