/** Section header with optional count badge and action buttons. */
export function SectionHead({ title, count, actions }: { title: string; count?: number; actions?: React.ReactNode }) {
  return (
    <div className="prop-section-head">
      <span className="prop-section-title">{title}</span>
      {count !== undefined && <span className="prop-section-count">{count}</span>}
      {actions && <span className="prop-section-actions">{actions}</span>}
    </div>
  );
}
