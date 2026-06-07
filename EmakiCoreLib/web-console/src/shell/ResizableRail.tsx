import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { t } from '../i18n';

const RAIL_MIN = 180;
const RAIL_MAX = 600;
const RAIL_STEP = 16;
const OUTLINE_MIN = 180;
const OUTLINE_MAX = 440;
const OUTLINE_STEP = 16;

type ResizableSideRailProps = {
  children: ReactNode;
  className: string;
  resizeClassName: string;
  storageKey: string;
  cssVariable: string;
  defaultWidth: number;
  min: number;
  max: number;
  step: number;
  dragDirection: 1 | -1;
  ariaLabel: string;
};

export function ResizableRail({ children }: { children: ReactNode }) {
  return <ResizableSideRail
    className="tree-rail"
    resizeClassName="rail-resize"
    storageKey="emaki-rail-width"
    cssVariable="--rail-width"
    defaultWidth={272}
    min={RAIL_MIN}
    max={RAIL_MAX}
    step={RAIL_STEP}
    dragDirection={1}
    ariaLabel={t('core.tree.resizeAria')}
  >{children}</ResizableSideRail>;
}

export function ResizableOutlineRail({ children }: { children: ReactNode }) {
  return <ResizableSideRail
    className="field-outline-rail"
    resizeClassName="outline-resize"
    storageKey="emaki-outline-width"
    cssVariable="--outline-width"
    defaultWidth={240}
    min={OUTLINE_MIN}
    max={OUTLINE_MAX}
    step={OUTLINE_STEP}
    dragDirection={-1}
    ariaLabel={t('core.outline.resizeAria')}
  >{children}</ResizableSideRail>;
}

function ResizableSideRail({ children, className, resizeClassName, storageKey, cssVariable, defaultWidth, min, max, step, dragDirection, ariaLabel }: ResizableSideRailProps) {
  const [width, setWidth] = useState(() => {
    const saved = localStorage.getItem(storageKey);
    return saved ? clampRailWidth(Number(saved), min, max, defaultWidth) : defaultWidth;
  });
  const [dragging, setDragging] = useState(false);
  const startX = useRef(0);
  const startW = useRef(defaultWidth);
  const latestWidth = useRef(width);

  const commitWidth = useCallback((next: number) => {
    const clamped = clampRailWidth(next, min, max, defaultWidth);
    latestWidth.current = clamped;
    setWidth(clamped);
    localStorage.setItem(storageKey, String(clamped));
  }, [defaultWidth, max, min, storageKey]);

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    startX.current = e.clientX;
    startW.current = width;
    setDragging(true);
  }, [width]);

  const onKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return;
    e.preventDefault();
    if (e.key === 'Home') commitWidth(min);
    else if (e.key === 'End') commitWidth(max);
    else commitWidth(width + (e.key === 'ArrowRight' ? step : -step) * dragDirection);
  }, [commitWidth, dragDirection, max, min, step, width]);

  useEffect(() => {
    if (!dragging) return;
    const onMove = (e: MouseEvent) => {
      const next = clampRailWidth(startW.current + (e.clientX - startX.current) * dragDirection, min, max, defaultWidth);
      latestWidth.current = next;
      setWidth(next);
    };
    const onUp = () => {
      setDragging(false);
      localStorage.setItem(storageKey, String(latestWidth.current));
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
    return () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [defaultWidth, dragDirection, dragging, max, min, storageKey]);

  useEffect(() => {
    latestWidth.current = width;
    document.documentElement.style.setProperty(cssVariable, `${width}px`);
  }, [cssVariable, width]);

  return (
    <aside className={className}>
      {children}
      <div
        className={`${resizeClassName} ${dragging ? 'active' : ''}`}
        role="separator"
        tabIndex={0}
        aria-orientation="vertical"
        aria-label={ariaLabel}
        aria-valuemin={min}
        aria-valuemax={max}
        aria-valuenow={width}
        onMouseDown={onMouseDown}
        onKeyDown={onKeyDown}
      />
    </aside>
  );
}

function clampRailWidth(value: number, min: number, max: number, fallback: number): number {
  return Math.max(min, Math.min(max, Number.isFinite(value) ? value : fallback));
}
