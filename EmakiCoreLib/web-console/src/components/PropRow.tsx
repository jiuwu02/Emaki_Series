import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react';
import { fieldLabel } from '../lib/fieldI18n';
import type { WebEditorField } from '../types';

/** A labeled property row with label-input association for accessibility. */
export function PropRow({ label, path, moduleId, namespace, editorFields, children, wide, changed }: { label?: string; path?: string; moduleId?: string; namespace?: string; editorFields?: Record<string, WebEditorField>; children: ReactNode; wide?: boolean; changed?: boolean }) {
  const id = useId();
  const displayLabel = fieldLabel(path ?? label ?? '', { moduleId, namespace, editorFields, fallback: label });
  const control = bindControlId(children, id);
  return (
    <div className={`prop-row${wide ? ' prop-row--wide' : ''}${changed ? ' changed' : ''}`}>
      <label className="prop-label" htmlFor={id}>{displayLabel}</label>
      <span className="prop-value" id={`${id}-wrap`}>{control}</span>
    </div>
  );
}

function bindControlId(children: ReactNode, id: string): ReactNode {
  if (!isValidElement(children)) return children;
  const element = children as ReactElement<{ id?: string }>;
  return cloneElement(element, { id: element.props.id ?? id });
}
