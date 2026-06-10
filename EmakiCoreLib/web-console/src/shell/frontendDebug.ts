import type { FrontendDebugEventReport } from '../api';

export function interactiveTarget(target: EventTarget | null): HTMLElement | null {
  if (!(target instanceof HTMLElement)) return null;
  return target.closest('button, a, input, select, textarea, [role="button"], [role="menuitem"], [tabindex]');
}

export function isFormControl(target: HTMLElement): target is HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement {
  return target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement;
}

export function frontendDebugEvent(type: string, target: HTMLElement, extra: Partial<FrontendDebugEventReport> = {}): FrontendDebugEventReport {
  return {
    type,
    target: describeDebugTarget(target),
    label: debugElementLabel(target),
    ...extra
  };
}

export function debugInputValue(target: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement): string {
  const name = `${target.getAttribute('name') ?? ''} ${target.id ?? ''} ${target.getAttribute('autocomplete') ?? ''} ${target.getAttribute('aria-label') ?? ''}`.toLowerCase();
  if (/password|token|secret|key|authorization/.test(name) || (target instanceof HTMLInputElement && target.type === 'password')) {
    return '<masked>';
  }
  const value = target instanceof HTMLInputElement && (target.type === 'checkbox' || target.type === 'radio') ? String(target.checked) : target.value;
  if (target instanceof HTMLTextAreaElement || value.length > 180) {
    return `${trimDebugText(value, 120)} (length=${value.length})`;
  }
  return trimDebugText(value, 120);
}

function describeDebugTarget(target: HTMLElement): string {
  const tag = target.tagName.toLowerCase();
  const id = target.id ? `#${target.id}` : '';
  const className = String(target.getAttribute('class') ?? '').trim().split(/\s+/).filter(Boolean).slice(0, 3).map(part => `.${part}`).join('');
  const name = target.getAttribute('name') ? `[name=${target.getAttribute('name')}]` : '';
  const role = target.getAttribute('role') ? `[role=${target.getAttribute('role')}]` : '';
  return `${tag}${id}${className}${name}${role}` || tag;
}

function debugElementLabel(target: HTMLElement): string {
  const ownLabel = target.getAttribute('aria-label') || target.getAttribute('title') || target.getAttribute('placeholder');
  if (ownLabel?.trim()) return trimDebugText(ownLabel, 120);
  const label = target.closest('label');
  if (label?.textContent?.trim()) return trimDebugText(label.textContent, 120);
  return trimDebugText(target.textContent ?? '', 120);
}

function trimDebugText(value: string, maxLength: number): string {
  const normalized = String(value ?? '').replace(/[\r\n]+/g, ' ').replace(/\s+/g, ' ').trim();
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}…` : normalized;
}
