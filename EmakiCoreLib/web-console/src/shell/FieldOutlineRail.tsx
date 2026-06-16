import { t } from '../i18n';
import type { SurfaceOutlineState } from '../registry';

export function FieldOutlineRail({ outline, onJump }: { outline: SurfaceOutlineState; onJump: (path: string) => void }) {
  const items = outline?.items ?? [];
  const title = outline?.title ?? t('core.outline.title');
  const subtitle = outline?.subtitle || t('core.outline.noConfig');
  const emptyText = outline?.emptyText || t('core.outline.noConfig');
  return <div className="field-outline">
    <div className="field-outline-head">
      <span>{t('core.outline.subtitle')}</span>
      <strong>{title}</strong>
      <code>{subtitle}</code>
    </div>
    {items.length > 0
      ? <nav className="field-outline-list" aria-label={title}>{items.map(item => <button
          key={item.path}
          type="button"
          className={`field-outline-item${item.changed ? ' changed' : ''}`}
          onClick={() => onJump(item.path)}
          aria-label={t('core.outline.itemAria', { path: item.path })}
        >
          <span className="field-outline-item-main"><strong>{item.label}</strong>{item.changedCount > 0 && <em>{t('core.outline.changed', { count: item.changedCount })}</em>}</span>
          <code>{item.path}</code>
          <span className="field-outline-item-meta">{item.childCount > 0 ? t('core.outline.childCount', { count: item.childCount }) : item.type}</span>
        </button>)}</nav>
      : <div className="field-outline-empty" role="status">{emptyText}</div>}
  </div>;
}

export function jumpToConfigNode(path: string, attempt = 0) {
  const stage = document.querySelector<HTMLElement>('.stage');
  const target = findConfigNodeTarget(stage ?? document, path);
  if (!target) {
    if (attempt < 14) window.requestAnimationFrame(() => jumpToConfigNode(path, attempt + 1));
    return;
  }
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const scrollRoot = target.closest<HTMLElement>('.editor-shell.single') ?? stage;
  scrollElementToStageTop(target, scrollRoot, reduceMotion ? 'auto' : 'smooth');
  target.classList.remove('config-node-locate');
  void target.offsetWidth;
  target.classList.add('config-node-locate');
  window.setTimeout(() => target.classList.remove('config-node-locate'), reduceMotion ? 900 : 1500);
}

function findConfigNodeTarget(root: ParentNode, path: string): HTMLElement | null {
  for (const candidate of configNodePathCandidates(path)) {
    const target = root.querySelector<HTMLElement>(`[data-config-node-path="${cssSelectorEscape(candidate)}"]`);
    if (target) return visibleConfigNodeTarget(target);
  }
  return null;
}

function visibleConfigNodeTarget(target: HTMLElement): HTMLElement {
  const hiddenParent = target.closest<HTMLElement>('[hidden]');
  return hiddenParent?.closest<HTMLElement>('.node-section') ?? target;
}

function configNodePathCandidates(path: string): string[] {
  const raw = String(path ?? '').trim();
  const candidates: string[] = [];
  const push = (candidate: string) => {
    const normalized = candidate.replace(/^\.+|\.+$/g, '').replace(/\.\.+/g, '.');
    if (normalized && !candidates.includes(normalized)) candidates.push(normalized);
  };
  const pushWithParents = (candidate: string) => {
    push(candidate);
    let parent = candidate;
    while (parent.includes('.')) {
      parent = parent.slice(0, parent.lastIndexOf('.'));
      push(parent);
    }
  };
  const bracketAsDot = raw.replace(/\[(\d+)\]/g, '.$1');
  const withoutBracketIndexes = raw.replace(/\[\d+\]/g, '');
  const withoutDotIndexes = bracketAsDot.split('.').filter(segment => segment && !/^\d+$/.test(segment)).join('.');
  [raw, bracketAsDot, withoutBracketIndexes, withoutDotIndexes].forEach(pushWithParents);
  return candidates;
}

function scrollElementToStageTop(target: HTMLElement, stage: HTMLElement | null, behavior: ScrollBehavior) {
  const headerOffset = 18;
  if (stage && isScrollable(stage)) {
    const stageRect = stage.getBoundingClientRect();
    const targetRect = target.getBoundingClientRect();
    const top = stage.scrollTop + targetRect.top - stageRect.top - headerOffset;
    stage.scrollTo({ top: Math.max(0, top), behavior });
    return;
  }
  const top = window.scrollY + target.getBoundingClientRect().top - headerOffset;
  window.scrollTo({ top: Math.max(0, top), behavior });
}

function isScrollable(element: HTMLElement): boolean {
  const style = window.getComputedStyle(element);
  return /(auto|scroll|overlay)/.test(`${style.overflowY} ${style.overflow}`) && element.scrollHeight > element.clientHeight;
}

function cssSelectorEscape(value: string): string {
  const css = globalThis.CSS;
  if (css && typeof css.escape === 'function') return css.escape(value);
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\0/g, '\uFFFD');
}
