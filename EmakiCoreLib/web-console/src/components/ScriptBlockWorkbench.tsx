import * as Blockly from 'blockly/core';
import * as BlocklyZhHans from 'blockly/msg/zh-hans';
import 'blockly/blocks';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { CompletionSource } from '@codemirror/autocomplete';
import { t } from '../i18n';
import type { WebScriptBlockCategory, WebScriptBlockDefinition } from '../types';
import { createScriptBlocklyToolbox, generateScriptFromWorkspace, loadScriptSourceIntoWorkspace, normalizeScriptBlockCatalog, pruneDetachedValueBlocks, registerScriptBlocklyBlocks, scriptBlockCount } from '../scriptBlocks';
import { CodeEditor } from './CodeEditor';

export type ScriptBlockWorkbenchProps = {
  value: string;
  categories: WebScriptBlockCategory[];
  blocks: WebScriptBlockDefinition[];
  ariaLabel: string;
  completionSource?: CompletionSource;
  onChange: (value: string) => void;
  onSave?: () => void;
};

type ScriptWorkbenchStatus = 'ready' | 'partial' | 'empty' | 'source' | 'error';

Blockly.setLocale(Object.fromEntries(Object.entries(BlocklyZhHans).filter(([, value]) => typeof value === 'string')) as Record<string, string>);

export function ScriptBlockWorkbench({ value, categories, blocks, ariaLabel, completionSource, onChange, onSave }: ScriptBlockWorkbenchProps) {
  const catalogSignature = stableCatalogSignature(categories, blocks);
  const catalog = useMemo(() => normalizeScriptBlockCatalog(categories, blocks), [catalogSignature]);
  const toolbox = useMemo(() => createScriptBlocklyToolbox(catalog), [catalog]);
  const blocklyHostRef = useRef<HTMLDivElement | null>(null);
  const workspaceRef = useRef<Blockly.WorkspaceSvg | null>(null);
  const onChangeRef = useRef(onChange);
  const initializingRef = useRef(false);
  const hasBlockEditsRef = useRef(false);
  const synchronizingRef = useRef(false);
  const [generatedCode, setGeneratedCode] = useState(value);
  const [blockCount, setBlockCount] = useState(0);
  const [rawBlockCount, setRawBlockCount] = useState(0);
  const [status, setStatus] = useState<ScriptWorkbenchStatus>(() => value.trim() ? 'source' : 'empty');
  const [error, setError] = useState('');

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    registerScriptBlocklyBlocks(catalog);
    const host = blocklyHostRef.current;
    if (!host) return;

    initializingRef.current = true;
    hasBlockEditsRef.current = false;

    const workspace = Blockly.inject(host, {
      toolbox,
      renderer: 'zelos',
      theme: createThemeFromHost(host),
      trashcan: true,
      scrollbars: true,
      grid: {
        spacing: 20,
        length: 3,
        colour: readCssValue(host, '--script-blockly-grid', 'rgba(148, 163, 184, 0.35)'),
        snap: true
      },
      zoom: {
        controls: true,
        wheel: true,
        startScale: 0.92,
        maxScale: 2.4,
        minScale: 0.35,
        scaleSpeed: 1.16,
        pinch: true
      },
      move: {
        scrollbars: true,
        drag: true,
        wheel: false
      }
    });

    workspaceRef.current = workspace;

    const emitCode = (writeBack: boolean) => {
      synchronizingRef.current = true;
      try {
        pruneDetachedShadowBlocks(workspace);
        const count = scriptBlockCount(workspace);
        const next = generateScriptFromWorkspace(workspace);
        setGeneratedCode(next);
        setBlockCount(count);
        setStatus(count > 0 ? 'ready' : 'empty');
        setError('');
        if (writeBack) onChangeRef.current(next);
      } catch (exception) {
        const message = exception instanceof Error ? exception.message : String(exception);
        setStatus('error');
        setError(message);
        setGeneratedCode(t('core.script.generateError', { message }, `// 代码生成出错: ${message}\n`));
      } finally {
        synchronizingRef.current = false;
      }
    };

    try {
      const importResult = loadScriptSourceIntoWorkspace(workspace, value, catalog);
      pruneDetachedValueBlocks(workspace);
      const count = scriptBlockCount(workspace);
      const generated = count > 0 ? generateScriptFromWorkspace(workspace) : value;
      setGeneratedCode(generated || value);
      setBlockCount(count);
      setRawBlockCount(importResult.rawBlocks);
      setStatus(count > 0 ? importResult.rawBlocks > 0 ? 'partial' : 'ready' : value.trim() ? 'source' : 'empty');
      setError(importResult.errors[0] ?? '');
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : String(exception);
      setStatus('source');
      setError(message);
      setGeneratedCode(value);
    }

    workspace.addChangeListener(event => {
      if (isExternalWorkspaceEvent(event, workspace)) return;
      if (event.type === Blockly.Events.BLOCK_DRAG) {
        const dragEvent = event as { isStart?: boolean };
        if (dragEvent.isStart) hideToolboxFlyout(workspace);
        return;
      }
      if (event.isUiEvent || initializingRef.current || synchronizingRef.current) return;
      if (event.type === Blockly.Events.BLOCK_CREATE) hideToolboxFlyout(workspace);
      hasBlockEditsRef.current = true;
      synchronizingRef.current = true;
      try {
        pruneDetachedShadowBlocks(workspace);
        pruneDetachedValueBlocks(workspace);
      } finally {
        synchronizingRef.current = false;
      }
      setRawBlockCount(countRawBlocks(workspace));
      emitCode(true);
      if (event.type === Blockly.Events.BLOCK_CREATE) window.setTimeout(() => hideToolboxFlyout(workspace), 0);
    });

    const resize = () => Blockly.svgResize(workspace);
    const resizeObserver = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize);
    resizeObserver?.observe(host);
    window.addEventListener('resize', resize);

    window.setTimeout(() => {
      resize();
      selectFirstToolboxCategory(workspace);
      resize();
      initializingRef.current = false;
    }, 0);

    const themeObserver = new MutationObserver(() => {
      try {
        workspace.setTheme(createThemeFromHost(host));
        resize();
      } catch {
        // Blockly 主题切换失败时保持当前工作区，避免丢失用户已编辑内容。
      }
    });
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

    return () => {
      resizeObserver?.disconnect();
      themeObserver.disconnect();
      window.removeEventListener('resize', resize);
      initializingRef.current = false;
      workspace.dispose();
      if (workspaceRef.current === workspace) workspaceRef.current = null;
    };
  }, [catalog, toolbox]);

  useEffect(() => {
    if (hasBlockEditsRef.current) return;
    const workspace = workspaceRef.current;
    if (!workspace) {
      setGeneratedCode(value);
      setStatus(value.trim() ? 'source' : 'empty');
      return;
    }
    initializingRef.current = true;
    try {
      const importResult = loadScriptSourceIntoWorkspace(workspace, value, catalog);
      pruneDetachedValueBlocks(workspace);
      const count = scriptBlockCount(workspace);
      setGeneratedCode(count > 0 ? generateScriptFromWorkspace(workspace) : value);
      setBlockCount(count);
      setRawBlockCount(importResult.rawBlocks);
      setStatus(count > 0 ? importResult.rawBlocks > 0 ? 'partial' : 'ready' : value.trim() ? 'source' : 'empty');
      setError(importResult.errors[0] ?? '');
      window.setTimeout(() => {
        Blockly.svgResize(workspace);
        initializingRef.current = false;
      }, 0);
    } catch (exception) {
      const message = exception instanceof Error ? exception.message : String(exception);
      setGeneratedCode(value);
      setStatus(value.trim() ? 'source' : 'empty');
      setError(message);
      initializingRef.current = false;
    }
  }, [catalog, value]);

  function clearWorkspace() {
    const workspace = workspaceRef.current;
    if (!workspace) return;
    hasBlockEditsRef.current = true;
    workspace.clear();
    const next = generateScriptFromWorkspace(workspace);
    setGeneratedCode(next);
    setBlockCount(0);
    setRawBlockCount(0);
    setStatus('empty');
    onChange(next);
  }

  function refreshWorkspaceSize() {
    const workspace = workspaceRef.current;
    if (!workspace) return;
    synchronizingRef.current = true;
    try {
      pruneDetachedShadowBlocks(workspace);
      workspace.cleanUp();
    } finally {
      synchronizingRef.current = false;
    }
    Blockly.svgResize(workspace);
  }

  const statusLabel = status === 'error'
    ? t('core.script.sync.sourceOnly')
    : status === 'ready'
      ? t('core.script.sync.ok')
      : status === 'source'
        ? t('core.script.sync.sourceOnly')
        : status === 'partial'
          ? t('core.script.sync.partial')
          : t('core.script.sync.empty');
  const note = status === 'source'
    ? t('core.script.note.sourceOnly')
    : status === 'partial'
      ? t('core.script.note.partial', { count: rawBlockCount })
      : t('core.script.note.blocksPrimary');

  return <div className="script-visual-layout script-blockly-workbench">
    <section className="script-blockly-canvas-wrap" aria-label={t('core.script.workspace')}>
      {error && <div className="script-block-warning" role="status"><p>{error}</p></div>}
      <div className="script-blockly-head">
        <span className={`script-sync-status ${status}`}>{statusLabel}</span>
        <button type="button" onClick={refreshWorkspaceSize}>{t('core.script.autoLayout')}</button>
        <button type="button" onClick={clearWorkspace} disabled={blockCount === 0}>{t('core.script.clearBlocks')}</button>
      </div>
      <div ref={blocklyHostRef} className="script-blockly-host" />
    </section>

    <section className="script-live-code-pane script-puzzle-code-pane" aria-label={t('core.script.liveCode')}>
      <div className="script-puzzle-code-head">
        <span>{t('core.script.liveCode')}</span>
        <span className="script-block-count">{blockCount}</span>
      </div>
      <div className="script-blockly-code-note">{note}</div>
      <CodeEditor
        className="script-code-editor"
        value={generatedCode}
        language="javascript"
        readOnly
        ariaLabel={ariaLabel}
        completionSource={completionSource}
        onSave={onSave}
      />
      <span id="script-editor-help" className="sr-only">{t('core.script.help')}</span>
    </section>

    <footer className="script-blockly-statusbar">
      <span>{t('core.script.blockStatus', { count: blockCount })}</span>
      <span>{t('core.script.dragHint')}</span>
    </footer>
  </div>;
}

function stableCatalogSignature(categories: WebScriptBlockCategory[], blocks: WebScriptBlockDefinition[]): string {
  return JSON.stringify({
    categories: categories.map(category => [category.id, category.moduleId, category.label, category.comment, category.order]),
    blocks: blocks.map(block => [block.id, block.categoryId, block.moduleId, block.scope, block.label, block.comment, block.codeTemplate, block.callPattern, block.type, block.order])
  });
}

function selectFirstToolboxCategory(workspace: Blockly.WorkspaceSvg): void {
  const toolbox = workspace.getToolbox() as unknown as {
    getToolboxItems?: () => Array<{ isSelectable?: () => boolean }>;
    setSelectedItem?: (item: unknown) => void;
  } | null;
  if (!toolbox?.getToolboxItems || !toolbox.setSelectedItem) return;
  const first = toolbox.getToolboxItems().find(item => item.isSelectable ? item.isSelectable() : true);
  if (!first) return;
  try {
    toolbox.setSelectedItem(first);
  } catch {
    // 某些 Blockly toolbox 实现会在当前项不可选时抛错；忽略即可，不影响工作区本身。
  }
}

function countRawBlocks(workspace: Blockly.WorkspaceSvg): number {
  return workspace.getAllBlocks(false).filter(block => !isShadowBlock(block) && (block.type === 'emaki_raw_statement' || block.type === 'emaki_raw_value')).length;
}

function isExternalWorkspaceEvent(event: Blockly.Events.Abstract, workspace: Blockly.WorkspaceSvg): boolean {
  const eventWorkspaceId = (event as { workspaceId?: string }).workspaceId;
  return Boolean(eventWorkspaceId && eventWorkspaceId !== workspace.id);
}

function pruneDetachedShadowBlocks(workspace: Blockly.WorkspaceSvg): void {
  for (const block of workspace.getAllBlocks(false) as Blockly.BlockSvg[]) {
    if (isShadowBlock(block) && !block.getParent()) block.dispose(false);
  }
}

function hideToolboxFlyout(workspace: Blockly.WorkspaceSvg): void {
  const toolbox = workspace.getToolbox() as unknown as { getFlyout?: () => { hide?: () => void } | null } | null;
  try {
    toolbox?.getFlyout?.()?.hide?.();
  } catch {
    // 仅收起拖拽后的分类飞出面板；失败时不影响主工作区。
  }
}

function isShadowBlock(block: Blockly.Block): boolean {
  return typeof block.isShadow === 'function' && block.isShadow();
}

function createThemeFromHost(host: HTMLElement): Blockly.Theme {
  const suffix = document.documentElement.dataset.theme === 'light' ? 'light' : 'dark';
  return Blockly.Theme.defineTheme(`emaki-script-${suffix}`, {
    name: `emaki-script-${suffix}`,
    base: Blockly.Themes.Zelos,
    componentStyles: {
      workspaceBackgroundColour: readCssValue(host, '--script-blockly-workspace', readCssValue(host, '--input', '#0f172a')),
      toolboxBackgroundColour: readCssValue(host, '--script-blockly-toolbox', readCssValue(host, '--surface', '#111827')),
      toolboxForegroundColour: readCssValue(host, '--text', '#e5e7eb'),
      flyoutBackgroundColour: readCssValue(host, '--script-blockly-flyout', readCssValue(host, '--surface-2', '#1f2937')),
      flyoutForegroundColour: readCssValue(host, '--text', '#e5e7eb'),
      flyoutOpacity: 1,
      scrollbarColour: readCssValue(host, '--script-blockly-scrollbar', readCssValue(host, '--line-2', '#64748b')),
      insertionMarkerColour: readCssValue(host, '--accent', '#60a5fa'),
      insertionMarkerOpacity: 0.42,
      markerColour: readCssValue(host, '--accent', '#60a5fa'),
      cursorColour: readCssValue(host, '--accent', '#60a5fa'),
      selectedGlowColour: readCssValue(host, '--accent', '#60a5fa'),
      selectedGlowOpacity: 0.42
    }
  });
}

function readCssValue(host: HTMLElement, name: string, fallback: string): string {
  const value = getComputedStyle(host).getPropertyValue(name).trim() || getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}
