import { useCallback, useEffect, useRef, useState } from 'react';

export function ResizableRail({ children }: { children: React.ReactNode }) {
  const [width, setWidth] = useState(() => {
    const saved = localStorage.getItem('emaki-rail-width');
    return saved ? Math.max(180, Math.min(600, Number(saved))) : 272;
  });
  const [dragging, setDragging] = useState(false);
  const startX = useRef(0);
  const startW = useRef(272);
  const latestWidth = useRef(width);

  const onMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    startX.current = e.clientX;
    startW.current = width;
    setDragging(true);
  }, [width]);

  useEffect(() => {
    if (!dragging) return;
    const onMove = (e: MouseEvent) => {
      const next = Math.max(180, Math.min(600, startW.current + (e.clientX - startX.current)));
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
      <div className={`rail-resize ${dragging ? 'active' : ''}`} onMouseDown={onMouseDown} />
    </aside>
  );
}
