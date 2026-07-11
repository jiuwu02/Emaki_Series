import type { Completion, CompletionContext, CompletionResult, CompletionSource } from '@codemirror/autocomplete';
import { useEffect, useState } from 'react';
import { type ApiClient } from '../../api';
import { CodeEditor, InlineError } from '../../components';
import { t } from '../../i18n';
import { fileDisplayTitle } from '../../lib';
import { getJavaScriptCompletionScopes, type SurfaceToolbarState } from '../../registry';
import type { WebRegistryFile, WebRegistryModule, WebScriptCompletionEntry } from '../../types';

type ScriptToast = { tone: 'ok' | 'bad'; text: string } | null;

export function ScriptSurface({ api, scriptPath, module, file, setSurfaceToolbar, setToast }: { api: ApiClient; scriptPath: string; module: WebRegistryModule; file: WebRegistryFile; setSurfaceToolbar: (state: SurfaceToolbarState | null) => void; setToast: (toast: ScriptToast) => void }) {
  const [content, setContent] = useState('');
  const [savedContent, setSavedContent] = useState('');
  const [history, setHistory] = useState<{ undo: string[]; redo: string[] }>({ undo: [], redo: [] });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const fileName = scriptPath.split('/').pop() ?? scriptPath;
  const fileTitle = fileDisplayTitle(file);
  const isDirty = content !== savedContent;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.readScript(scriptPath).then(result => {
      if (!active) return;
      setContent(result.content);
      setSavedContent(result.content);
      setHistory({ undo: [], redo: [] });
    }).catch(err => {
      if (!active) return;
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [api, scriptPath]);

  function updateScriptContent(next: string) {
    setContent(previous => {
      setHistory(current => ({ undo: [...current.undo, previous].slice(-20), redo: [] }));
      return next;
    });
  }

  async function save() {
    if (!isDirty) {
      setToast({ tone: 'ok', text: t('core.toast.noChanges') });
      return;
    }
    setSaving(true);
    try {
      await api.saveScript(scriptPath, content);
      setSavedContent(content);
      setHistory({ undo: [], redo: [] });
      setToast({ tone: 'ok', text: t('core.toast.savedConfig', { count: 1 }) });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.file.createFailed'));
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.file.createFailed') });
    } finally {
      setSaving(false);
    }
  }

  function undoScript() {
    const snapshot = history.undo[history.undo.length - 1];
    if (snapshot === undefined) return;
    setContent(current => {
      setHistory(previous => ({ undo: previous.undo.slice(0, -1), redo: [current, ...previous.redo].slice(0, 20) }));
      return snapshot;
    });
    setError('');
  }

  function redoScript() {
    const snapshot = history.redo[0];
    if (snapshot === undefined) return;
    setContent(current => {
      setHistory(previous => ({ undo: [...previous.undo, current].slice(-20), redo: previous.redo.slice(1) }));
      return snapshot;
    });
    setError('');
  }

  async function reload() {
    setLoading(true);
    setError('');
    try {
      const res = await api.readScript(scriptPath);
      setContent(res.content);
      setSavedContent(res.content);
      setHistory({ undo: [], redo: [] });
      setToast({ tone: 'ok', text: t('core.toast.reloaded') });
    } catch (err) {
      setError(err instanceof Error ? err.message : t('core.config.childLoadFailed'));
      setToast({ tone: 'bad', text: err instanceof Error ? err.message : t('core.toast.refreshFailed') });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    setSurfaceToolbar({
      title: fileName,
      subtitle: `${fileTitle} · ${scriptPath}`,
      dirty: isDirty,
      changedCount: isDirty ? 1 : 0,
      changes: [],
      source: content,
      sourceOriginal: savedContent,
      sourceEditable: true,
      sourceError: error || null,
      sourceLanguage: 'javascript',
      saving,
      loading,
      canUndo: history.undo.length > 0,
      canRedo: history.redo.length > 0,
      onUndo: undoScript,
      onRedo: redoScript,
      onReload: () => void reload(),
      onSourceChange: updateScriptContent,
      onSave: () => void save()
    });
  }, [fileName, fileTitle, scriptPath, isDirty, content, error, saving, loading, history.undo.length, history.redo.length]);

  useEffect(() => () => setSurfaceToolbar(null), [setSurfaceToolbar]);

  function handleInput(value: string) {
    updateScriptContent(value);
  }

  if (loading) return <div className="script-loading" role="status">{t('core.script.loading')}</div>;

  return <div className="script-editor">
    {error && <InlineError>{error}</InlineError>}
    <div className="editor-wrapper">
      <CodeEditor
        className="script-code-editor"
        value={content}
        language="javascript"
        ariaLabel={t('core.script.editAria', { path: scriptPath })}
        completionSource={scriptCompletionSource}
        onChange={handleInput}
        onSave={save}
      />
      <span id="script-editor-help" className="sr-only">{t('core.script.help')}</span>
    </div>
  </div>;
}

type ScriptCompletionScope = Record<string, Completion[]>;

const BUILTIN_SCRIPT_COMPLETION_SCOPES: ScriptCompletionScope = {
  global: [
    keywordCompletion('function'), keywordCompletion('const'), keywordCompletion('let'), keywordCompletion('var'), keywordCompletion('if'), keywordCompletion('else'), keywordCompletion('for'), keywordCompletion('while'), keywordCompletion('do'), keywordCompletion('switch'), keywordCompletion('case'), keywordCompletion('break'), keywordCompletion('continue'), keywordCompletion('return'), keywordCompletion('try'), keywordCompletion('catch'), keywordCompletion('finally'), keywordCompletion('throw'), keywordCompletion('new'), keywordCompletion('typeof'), keywordCompletion('instanceof'), keywordCompletion('class'), keywordCompletion('extends'), keywordCompletion('async'), keywordCompletion('await'), keywordCompletion('true'), keywordCompletion('false'), keywordCompletion('null'), keywordCompletion('undefined'), keywordCompletion('this'),
    variableCompletion('emaki', 'EmakiScriptApi'), variableCompletion('args', 'Map<String, Object>'), variableCompletion('console', 'Console'), variableCompletion('Math', 'Math'), variableCompletion('JSON', 'JSON'), variableCompletion('Object', 'Object'), variableCompletion('Array', 'Array'), variableCompletion('String', 'String'), variableCompletion('Number', 'Number'), variableCompletion('Date', 'Date'), variableCompletion('RegExp', 'RegExp'), variableCompletion('Map', 'Map'), variableCompletion('Set', 'Set'), variableCompletion('Promise', 'Promise'),
    functionCompletion('parseInt(str)', 'parseInt'), functionCompletion('parseFloat(str)', 'parseFloat'), functionCompletion('isNaN(value)', 'isNaN'), functionCompletion('isFinite(value)', 'isFinite')
  ],
  emaki: [
    propertyCompletion('context', 'ScriptContextApi'), propertyCompletion('player', 'ScriptPlayerApi'), propertyCompletion('item', 'ScriptItemApi'), propertyCompletion('action', 'ScriptActionApi'), propertyCompletion('logger', 'ScriptLoggerApi'), propertyCompletion('random', 'ScriptRandomApi'), propertyCompletion('state', 'ScriptSharedStateApi'), propertyCompletion('text', 'ScriptTextApi'),
    functionCompletion('runSync(task)', 'runSync'), functionCompletion('runSyncAndWait(task)', 'runSyncAndWait')
  ],
  'emaki.context': [functionCompletion('phase()', 'phase'), functionCompletion('plugin()', 'plugin'), functionCompletion('placeholder(key)', 'placeholder'), functionCompletion('attribute(key)', 'attribute'), functionCompletion('arg(key)', 'arg'), functionCompletion('placeholders()', 'placeholders'), functionCompletion('attributes()', 'attributes'), functionCompletion('args()', 'args')],
  'emaki.player': [functionCompletion('exists()', 'exists'), functionCompletion('name()', 'name'), functionCompletion('uuid()', 'uuid'), functionCompletion('world()', 'world'), functionCompletion('hasPermission(permission)', 'hasPermission'), functionCompletion('sendMessage(message)', 'sendMessage')],
  'emaki.item': [functionCompletion('has(attributeKey)', 'has'), functionCompletion('type(attributeKey)', 'type'), functionCompletion('amount(attributeKey)', 'amount'), functionCompletion('displayName(attributeKey)', 'displayName')],
  'emaki.action': [functionCompletion('run(actionId, arguments)', 'run'), functionCompletion('runLine(line)', 'runLine')],
  'emaki.logger': [functionCompletion('info(message)', 'info'), functionCompletion('warn(message)', 'warn'), functionCompletion('error(message)', 'error')],
  'emaki.random': [functionCompletion('integer(min, max)', 'integer'), functionCompletion('decimal()', 'decimal'), functionCompletion('chance(percent)', 'chance'), functionCompletion('pick(values)', 'pick')],
  'emaki.state': [functionCompletion('set(key, value)', 'set'), functionCompletion('get(key)', 'get'), functionCompletion('has(key)', 'has'), functionCompletion('remove(key)', 'remove')],
  'emaki.text': [functionCompletion('string(value)', 'string'), functionCompletion('blank(value)', 'blank'), functionCompletion('notBlank(value)', 'notBlank'), functionCompletion('lower(value)', 'lower'), functionCompletion('normalizeId(value)', 'normalizeId')],
  console: [functionCompletion('log(message)', 'log'), functionCompletion('warn(message)', 'warn'), functionCompletion('error(message)', 'error'), functionCompletion('info(message)', 'info'), functionCompletion('debug(message)', 'debug')],
  Math: [functionCompletion('abs(x)', 'abs'), functionCompletion('ceil(x)', 'ceil'), functionCompletion('floor(x)', 'floor'), functionCompletion('round(x)', 'round'), functionCompletion('max(...values)', 'max'), functionCompletion('min(...values)', 'min'), functionCompletion('random()', 'random'), functionCompletion('pow(base, exp)', 'pow'), functionCompletion('sqrt(x)', 'sqrt'), propertyCompletion('PI', 'number'), propertyCompletion('E', 'number')],
  JSON: [functionCompletion('parse(text)', 'parse'), functionCompletion('stringify(value)', 'stringify'), functionCompletion('stringify(value, null, 2)', 'stringify')],
  Object: [functionCompletion('keys(obj)', 'keys'), functionCompletion('values(obj)', 'values'), functionCompletion('entries(obj)', 'entries'), functionCompletion('assign(target, ...sources)', 'assign'), functionCompletion('freeze(obj)', 'freeze')],
  Array: [functionCompletion('isArray(value)', 'isArray'), functionCompletion('from(arrayLike)', 'from')],
  String: [functionCompletion('fromCharCode(code)', 'fromCharCode')],
  Number: [functionCompletion('parseInt(str)', 'parseInt'), functionCompletion('parseFloat(str)', 'parseFloat'), functionCompletion('isNaN(value)', 'isNaN'), functionCompletion('isFinite(value)', 'isFinite')]
};

const scriptCompletionSource: CompletionSource = (context: CompletionContext): CompletionResult | null => {
  const scope = resolveScriptCompletionScope(context);
  if (!scope) return null;
  const { scopeName, partial, from } = scope;
  if (!context.explicit && scopeName === 'global' && partial.length < 2) return null;
  const options = scriptCompletionScopes(context.state.doc.toString())[scopeName];
  if (!options) return null;
  return {
    from,
    validFor: /^[A-Za-z_$][\w$]*$/,
    options: options.filter(option => option.label.toLowerCase().startsWith(partial.toLowerCase()))
  };
};

function scriptCompletionScopes(documentText: string): ScriptCompletionScope {
  const scopes: ScriptCompletionScope = {};
  mergeCompletionScopes(scopes, BUILTIN_SCRIPT_COMPLETION_SCOPES);
  const registered = getJavaScriptCompletionScopes();
  for (const [scope, entries] of Object.entries(registered)) {
    const completions = entries.map(scriptCompletionEntryToCompletion);
    if (!completions.length) continue;
    scopes[scope] = mergeCompletions(scopes[scope] ?? [], completions);
  }
  for (const [alias, moduleId] of Object.entries(scriptModuleAliases(documentText))) {
    const moduleScope = scopes[`module:${moduleId}`];
    if (moduleScope) scopes[alias] = mergeCompletions(scopes[alias] ?? [], moduleScope);
  }
  return scopes;
}

function resolveScriptCompletionScope(context: CompletionContext): { scopeName: string; partial: string; from: number } | null {
  const moduleCall = context.matchBefore(/emaki\.module\(\s*["'][A-Za-z0-9_-]+["']\s*\)\.[A-Za-z_$][\w$]*|emaki\.module\(\s*["'][A-Za-z0-9_-]+["']\s*\)\.?/);
  if (moduleCall) {
    const match = moduleCall.text.match(/emaki\.module\(\s*["']([A-Za-z0-9_-]+)["']\s*\)\.(.*)$/) ?? moduleCall.text.match(/emaki\.module\(\s*["']([A-Za-z0-9_-]+)["']\s*\)\.?$/);
    if (match) {
      const partial = match[2] ?? '';
      return { scopeName: `module:${match[1].toLowerCase()}`, partial, from: moduleCall.to - partial.length };
    }
  }
  const token = context.matchBefore(/[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\.?/);
  if (!token || (token.from === token.to && !context.explicit)) return null;
  const expression = token.text;
  const dotIndex = expression.lastIndexOf('.');
  const scopeName = dotIndex >= 0 ? expression.slice(0, dotIndex) : 'global';
  const partial = dotIndex >= 0 ? expression.slice(dotIndex + 1) : expression;
  return { scopeName, partial, from: token.to - partial.length };
}

function mergeCompletionScopes(target: ScriptCompletionScope, source: ScriptCompletionScope): void {
  for (const [scope, completions] of Object.entries(source)) {
    target[scope] = mergeCompletions(target[scope] ?? [], completions);
  }
}

function mergeCompletions(base: Completion[], incoming: Completion[]): Completion[] {
  const byLabel = new Map<string, Completion>();
  [...base, ...incoming].forEach(completion => byLabel.set(completion.label, completion));
  return [...byLabel.values()];
}

function scriptCompletionEntryToCompletion(entry: WebScriptCompletionEntry): Completion {
  return {
    label: entry.label,
    type: entry.type ?? 'function',
    detail: entry.detail,
    apply: entry.apply ?? entry.label
  };
}

function scriptModuleAliases(documentText: string): Record<string, string> {
  const aliases: Record<string, string> = {};
  const pattern = /\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*emaki\.module\(\s*["']([A-Za-z0-9_-]+)["']\s*\)/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(documentText)) !== null) {
    aliases[match[1]] = match[2].toLowerCase();
  }
  return aliases;
}

function keywordCompletion(label: string): Completion { return { label, type: 'keyword' }; }
function variableCompletion(label: string, detail: string): Completion { return { label, type: 'variable', detail }; }
function propertyCompletion(label: string, detail: string): Completion { return { label, type: 'property', detail }; }
function functionCompletion(label: string, apply: string): Completion { return { label: apply, type: 'function', detail: label, apply: label }; }
