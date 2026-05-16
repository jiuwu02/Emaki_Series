import type { HTMLAttributes, ReactNode } from 'react';

type InspectorSectionProps = HTMLAttributes<HTMLDivElement> & {
  title?: ReactNode;
  meta?: ReactNode;
  children: ReactNode;
};

/** Dense inspector panel for configuration editor sidebars. */
export function InspectorSection({ title, meta, className = '', children, ...props }: InspectorSectionProps) {
  return <div className={`ui-panel ${className}`.trim()} {...props}>
    {(title || meta) && <div className="ui-panel-head">
      {title && <h3>{title}</h3>}
      {meta && <span>{meta}</span>}
    </div>}
    {children}
  </div>;
}
