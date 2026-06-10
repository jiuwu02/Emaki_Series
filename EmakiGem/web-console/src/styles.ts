import { injectExtensionStyles } from 'emaki-web-console';

const GEM_EXTENSION_STYLES = `
.prop-add-inline {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--accent);
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
}

.prop-add-inline:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
}

.prop-level-toggle {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.prop-level-toggle:focus-visible {
  outline: 2px solid var(--focus);
  outline-offset: 3px;
  border-radius: 6px;
}

.prop-slot-open {
  flex: 0 0 auto;
  border: 1px solid var(--line);
  border-radius: 999px;
  padding: 2px 7px;
  color: var(--faint);
  font-size: 10px;
  font-weight: 700;
  white-space: nowrap;
}

.prop-slot-open.active {
  border-color: var(--success-line);
  color: var(--green);
  background: var(--success-soft);
}

.prop-slot-open:hover {
  border-color: var(--line-2);
  color: var(--text);
}
`;

export function installEmakiGemStyles(): void {
  injectExtensionStyles('emakigem-web-console', GEM_EXTENSION_STYLES);
}
