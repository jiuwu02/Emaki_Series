import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { t } from '../i18n';

export const RAIL_MIN = 180;
export const RAIL_MAX = 600;
export const RAIL_DEFAULT = 272;
export const RAIL_STEP = 16;
export const OUTLINE_MIN = 168;
export const OUTLINE_MAX = 320;
export const OUTLINE_DEFAULT = 216;
export const OUTLINE_STEP = 16;
export const RAIL_STORAGE_KEY = 'emaki-rail-width';
export const OUTLINE_STORAGE_KEY = 'emaki-outline-width';

type ResizableRailProps = {
  children: ReactNode;
  width: number;
  onWidthChange: (width: number) => void;
};

type ResizableSideRailProps = ResizableRailProps & {
  className: string;
  resizeClassName: string;
  min: number;
  max: number;
  step: number;
  dragDirection: 1 | -1;
  ariaLabel: string;
};

export function ResizableRail({ children, width, onWidthChange }: ResizableRailProps) {
  return <ResizableSideRail
    className="tree-rail"
    resizeClassName="rail-resize"
    width={width}
    onWidthChange={onWidthChange}
    min={RAIL_MIN}
    max={RAIL_MAX}
    step={RAIL_STEP}
    dragDirection={1}
    ariaLabel={t('core.tree.resizeAria')}
  >{children}</ResizableSideRail>;
}

export function ResizableOutlineRail({ children, width, onWidthChange }: ResizableRailProps) {
  return <ResizableSideRail
    className="field-outline-rail"
    resizeClassName="outline-resize"
    width={width}
    onWidthChange={onWidthChange}
    min={OUTLINE_MIN}
    max={OUTLINE_MAX}
    step={OUTLINE_STEP}
    dragDirection={-1}
    ariaLabel={t('core.outline.resizeAria')}
  >{children}</ResizableSideRail>;
}

function ResizableSideRail({ children, className, resizeClassName, width, onWidthChange, min, max, step, dragDirection, ariaLabel }: ResizableSideRailProps) {
  const [dragging, setDragging] = useState(false);
  const startX = useRef(0);
  const startW = useRef(width);
  const latestWidth = useRef(width);

  const commitWidth = useCallback((next: number) => {
    const clamped = clampRailWidth(next, min, max, width);
    latestWidth.current = clamped;
    onWidthChange(clamped);
  }, [max, min, onWidthChange, width]);

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    startX.current = e.clientX;
    startW.current = width;
    latestWidth.current = width;
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
    latestWidth.current = width;
  }, [width]);

  useEffect(() => {
    if (!dragging) return;
    const onMove = (e: MouseEvent) => {
      const next = clampRailWidth(startW.current + (e.clientX - startX.current) * dragDirection, min, max, width);
      latestWidth.current = next;
      onWidthChange(next);
    };
    const onUp = () => {
      setDragging(false);
      onWidthChange(latestWidth.current);
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
  }, [dragDirection, dragging, max, min, onWidthChange, width]);

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
        aria-valuenow={Math.round(width)}
        onMouseDown={onMouseDown}
        onKeyDown={onKeyDown}
      />
    </aside>
  );
}

function clampRailWidth(value: number, min: number, max: number, fallback: number): number {
  return Math.max(min, Math.min(max, Number.isFinite(value) ? value : fallback));
}
