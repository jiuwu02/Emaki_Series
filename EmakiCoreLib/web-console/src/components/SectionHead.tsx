import { useEffect, useState, type ReactNode, type SyntheticEvent } from 'react';

/** Section header with optional count badge and action buttons. */
export function SectionHead({ title, count, actions }: { title: string; count?: number; actions?: ReactNode }) {
  return (
    <div className="prop-section-head">
      <span className="prop-section-title">{title}</span>
      {count !== undefined && <span className="prop-section-count">{count}</span>}
      {actions && <span className="prop-section-actions">{actions}</span>}
    </div>
  );
}

export type DisclosureChevronProps = {
  open: boolean;
  className?: string;
};

export function DisclosureChevron({ open, className = '' }: DisclosureChevronProps) {
  return <svg viewBox="0 0 16 16" aria-hidden="true" focusable="false" className={`disclosure-chevron ${open ? 'is-open' : 'is-closed'} ${className}`.trim()}>
    <path d="M5.2 6.2 8 9l2.8-2.8" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
  </svg>;
}

export type CollapsibleSectionProps = {
  title: string;
  count?: number;
  actions?: ReactNode;
  comment?: ReactNode;
  children: ReactNode;
  storageKey?: string;
  collapsible?: boolean;
  defaultCollapsed?: boolean;
  className?: string;
};

/** Standard top-level collapsible section used by webEdit pages. */
export function CollapsibleSection({
  title,
  count,
  actions,
  comment,
  children,
  storageKey,
  collapsible = true,
  defaultCollapsed = false,
  className = ''
}: CollapsibleSectionProps) {
  const [collapsed, setCollapsed] = useState(() => readCollapsed(storageKey, defaultCollapsed));

  useEffect(() => {
    setCollapsed(readCollapsed(storageKey, defaultCollapsed));
  }, [storageKey, defaultCollapsed]);

  const toggle = () => {
    if (!collapsible) return;
    setCollapsed(current => {
      const next = !current;
      writeCollapsed(storageKey, next);
      return next;
    });
  };

  if (!collapsible) {
    return <section className={`prop-section ${className}`.trim()}>
      <SectionHead title={title} count={count} actions={actions} />
      {comment && <p className="muted-copy">{comment}</p>}
      <div className="prop-section-body">{children}</div>
    </section>;
  }

  return <section className={`prop-section prop-section--collapsible${collapsed ? ' collapsed' : ''} ${className}`.trim()}>
    <div className="prop-section-head prop-section-head--collapsible">
      <button type="button" className="prop-section-toggle" onClick={toggle} aria-expanded={!collapsed}>
        <DisclosureChevron open={!collapsed} className="prop-section-arrow" />
        <span className="prop-section-title">{title}</span>
      </button>
      {count !== undefined && <span className="prop-section-count">{count}</span>}
      {actions && <span className="prop-section-actions" onClick={stopPropagation} onKeyDown={stopPropagation}>{actions}</span>}
    </div>
    {comment && !collapsed && <p className="muted-copy">{comment}</p>}
    {!collapsed && <div className="prop-section-body">{children}</div>}
  </section>;
}

function readCollapsed(storageKey: string | undefined, fallback: boolean): boolean {
  if (!storageKey || typeof localStorage === 'undefined') return fallback;
  try {
    const stored = localStorage.getItem(storageKey);
    return stored == null ? fallback : stored === '1';
  } catch {
    return fallback;
  }
}

function writeCollapsed(storageKey: string | undefined, collapsed: boolean): void {
  if (!storageKey || typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(storageKey, collapsed ? '1' : '0');
  } catch { }
}

function stopPropagation(event: SyntheticEvent) {
  event.stopPropagation();
}
