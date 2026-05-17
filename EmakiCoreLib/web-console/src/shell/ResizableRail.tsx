import { useCallback, useEffect, useRef, useState } from 'react';
import { t } from '../i18n';

const RAIL_MIN = 180;
const RAIL_MAX = 600;
const RAIL_STEP = 16;

export function ResizableRail({ children }: { children: React.ReactNode }) {
  const [width, setWidth] = useState(() => {
    const saved = localStorage.getItem('emaki-rail-width');
    return saved ? clampRailWidth(Number(saved)) : 272;
  });
  const [dragging, setDragging] = useState(false);
  const startX = useRef(0);
  const startW = useRef(272);
  const latestWidth = useRef(width);

  const commitWidth = useCallback((next: number) => {
    const clamped = clampRailWidth(next);
    latestWidth.current = clamped;
    setWidth(clamped);
    localStorage.setItem('emaki-rail-width', String(clamped));
  }, []);

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    startX.current = e.clientX;
    startW.current = width;
    setDragging(true);
  }, [width]);

  const onKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return;
    e.preventDefault();
    if (e.key === 'Home') commitWidth(RAIL_MIN);
    else if (e.key === 'End') commitWidth(RAIL_MAX);
    else commitWidth(width + (e.key === 'ArrowRight' ? RAIL_STEP : -RAIL_STEP));
  }, [commitWidth, width]);

  useEffect(() => {
    if (!dragging) return;
    const onMove = (e: MouseEvent) => {
      const next = clampRailWidth(startW.current + (e.clientX - startX.current));
      latestWidth.current = next;
      setWidth(next);
    };
    const onUp = () => {
      setDragging(false);
      localStorage.setItem('emaki-rail-width', String(latestWidth.current));
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
  }, [dragging]);

  useEffect(() => {
    latestWidth.current = width;
    document.documentElement.style.setProperty('--rail-width', `${width}px`);
  }, [width]);

  return (
    <aside className="tree-rail">
      {children}
      <div
        className={`rail-resize ${dragging ? 'active' : ''}`}
        role="separator"
        tabIndex={0}
        aria-orientation="vertical"
        aria-label={t('core.tree.resizeAria')}
        aria-valuemin={RAIL_MIN}
        aria-valuemax={RAIL_MAX}
        aria-valuenow={width}
        onMouseDown={onMouseDown}
        onKeyDown={onKeyDown}
      />
    </aside>
  );
}

function clampRailWidth(value: number): number {
  return Math.max(RAIL_MIN, Math.min(RAIL_MAX, Number.isFinite(value) ? value : 272));
}
