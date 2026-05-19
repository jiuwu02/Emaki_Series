import { autocompletion, completionKeymap, type CompletionSource } from '@codemirror/autocomplete';
import { javascript } from '@codemirror/lang-javascript';
import { yaml } from '@codemirror/lang-yaml';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { EditorSelection, EditorState, type Extension } from '@codemirror/state';
import { tags as t } from '@lezer/highlight';
import { EditorView, drawSelection, dropCursor, highlightActiveLine, highlightActiveLineGutter, highlightSpecialChars, keymap, lineNumbers } from '@codemirror/view';
import { useEffect, useMemo, useRef } from 'react';

export type CodeEditorLanguage = 'javascript' | 'js' | 'yaml' | string | undefined;

const codeHighlightStyle = HighlightStyle.define([
  { tag: [t.keyword, t.controlKeyword, t.definitionKeyword, t.moduleKeyword, t.bool, t.null], color: 'var(--code-keyword)' },
  { tag: [t.string, t.special(t.string), t.regexp, t.character], color: 'var(--code-string)' },
  { tag: [t.number, t.integer, t.float], color: 'var(--code-number)' },
  { tag: [t.comment, t.lineComment, t.blockComment], color: 'var(--code-comment)', fontStyle: 'italic' },
  { tag: [t.variableName, t.definition(t.variableName), t.self, t.atom], color: 'var(--code-variable)' },
  { tag: [t.propertyName, t.definition(t.propertyName), t.attributeName, t.tagName], color: 'var(--code-property)' },
  { tag: [t.operator, t.operatorKeyword, t.compareOperator, t.logicOperator, t.arithmeticOperator, t.derefOperator, t.separator, t.punctuation], color: 'var(--code-operator)' },
  { tag: [t.className, t.typeName, t.namespace], color: 'var(--code-property)' },
  { tag: [t.invalid], color: 'var(--red)', textDecoration: 'underline wavy var(--red)' }
]);

const codeEditorTheme = EditorView.theme({
  '&': {
    backgroundColor: 'var(--input)',
    color: 'var(--muted)'
  },
  '.cm-content': {
    caretColor: 'var(--text)'
  },
  '&.cm-focused .cm-cursor': {
    borderLeftColor: 'var(--text)'
  },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection': {
    backgroundColor: 'var(--code-selection)'
  },
  '.cm-activeLine': {
    backgroundColor: 'var(--code-active-line)'
  },
  '.cm-activeLineGutter': {
    backgroundColor: 'var(--code-active-line)',
    color: 'var(--muted)'
  },
  '.cm-gutters': {
    backgroundColor: 'var(--code-gutter)',
    color: 'var(--faint)',
    borderRightColor: 'var(--line)'
  }
});

export type CodeEditorProps = {
  value: string;
  language?: CodeEditorLanguage;
  readOnly?: boolean;
  ariaLabel: string;
  className?: string;
  completionSource?: CompletionSource;
  onChange?: (value: string) => void;
  onSave?: () => void;
  onTab?: () => void;
  onCompletionKeyDown?: (event: KeyboardEvent) => boolean;
  onCursorChange?: (cursor: number) => void;
  onScroll?: (scrollTop: number) => void;
};

export function CodeEditor({
  value,
  language,
  readOnly = false,
  ariaLabel,
  className = '',
  completionSource,
  onChange,
  onSave,
  onTab,
  onCompletionKeyDown,
  onCursorChange,
  onScroll
}: CodeEditorProps) {
  const hostRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView | null>(null);
  const valueRef = useRef(value);
  const onChangeRef = useRef(onChange);
  const onSaveRef = useRef(onSave);
  const onTabRef = useRef(onTab);
  const onCompletionKeyDownRef = useRef(onCompletionKeyDown);
  const onCursorChangeRef = useRef(onCursorChange);
  const onScrollRef = useRef(onScroll);

  valueRef.current = value;
  onChangeRef.current = onChange;
  onSaveRef.current = onSave;
  onTabRef.current = onTab;
  onCompletionKeyDownRef.current = onCompletionKeyDown;
  onCursorChangeRef.current = onCursorChange;
  onScrollRef.current = onScroll;

  const extensions = useMemo(() => buildExtensions(language, readOnly, completionSource, {
    ariaLabel,
    onChange: (next) => onChangeRef.current?.(next),
    onSave: () => onSaveRef.current?.(),
    onTab: () => onTabRef.current?.(),
    onCompletionKeyDown: (event) => onCompletionKeyDownRef.current?.(event) ?? false,
    onCursorChange: (cursor) => onCursorChangeRef.current?.(cursor),
    onScroll: (scrollTop) => onScrollRef.current?.(scrollTop)
  }), [ariaLabel, completionSource, language, readOnly]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    const view = new EditorView({
      parent: host,
      state: EditorState.create({ doc: valueRef.current, extensions })
    });
    viewRef.current = view;
    return () => {
      view.destroy();
      viewRef.current = null;
    };
  }, [extensions]);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;
    const current = view.state.doc.toString();
    if (current === value) return;
    const head = Math.min(view.state.selection.main.head, value.length);
    view.dispatch({
      changes: { from: 0, to: current.length, insert: value },
      selection: EditorSelection.cursor(head)
    });
  }, [value]);

  return <div ref={hostRef} className={`code-editor ${className}`.trim()} />;
}

function buildExtensions(language: CodeEditorLanguage, readOnly: boolean, completionSource: CompletionSource | undefined, handlers: {
  ariaLabel: string;
  onChange: (value: string) => void;
  onSave: () => void;
  onTab: () => void;
  onCompletionKeyDown: (event: KeyboardEvent) => boolean;
  onCursorChange: (cursor: number) => void;
  onScroll: (scrollTop: number) => void;
}): Extension[] {
  const languageExtension = language === 'yaml' ? yaml() : language === 'javascript' || language === 'js' ? javascript() : [];

  return [
    lineNumbers(),
    highlightActiveLineGutter(),
    highlightSpecialChars(),
    drawSelection(),
    dropCursor(),
    highlightActiveLine(),
    codeEditorTheme,
    syntaxHighlighting(codeHighlightStyle),
    languageExtension,
    autocompletion({ override: completionSource ? [completionSource] : undefined }),
    EditorView.lineWrapping,
    EditorState.readOnly.of(readOnly),
    EditorView.editable.of(!readOnly),
    EditorView.contentAttributes.of({ 'aria-label': handlers.ariaLabel }),
    keymap.of([
      {
        key: 'Ctrl-s',
        mac: 'Mod-s',
        preventDefault: true,
        run: () => {
          handlers.onSave();
          return true;
        }
      },
      ...completionKeymap,
      {
        key: 'Tab',
        preventDefault: true,
        run: (view) => {
          handlers.onTab();
          view.dispatch(view.state.replaceSelection('  '));
          return true;
        }
      }
    ]),
    EditorView.domEventHandlers({
      keydown(event) {
        return handlers.onCompletionKeyDown(event);
      },
      scroll(event) {
        if (event.target instanceof HTMLElement && event.target.classList.contains('cm-scroller')) {
          handlers.onScroll(event.target.scrollTop);
        }
        return false;
      }
    }),
    EditorView.updateListener.of((update) => {
      if (update.docChanged && update.transactions.some(transaction => transaction.isUserEvent('input'))) handlers.onChange(update.state.doc.toString());
      if (update.selectionSet || update.docChanged) handlers.onCursorChange(update.state.selection.main.head);
    })
  ];
}
