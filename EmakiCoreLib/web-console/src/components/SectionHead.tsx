import { useState, type ReactNode, type SyntheticEvent } from 'react';

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
        <span className="prop-section-arrow" aria-hidden="true">{collapsed ? '›' : '⌄'}</span>
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
  } catch {}
}

function stopPropagation(event: SyntheticEvent) {
  event.stopPropagation();
}
