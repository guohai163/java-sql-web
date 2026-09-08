import React, { forwardRef, useEffect, useImperativeHandle, useMemo, useRef } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { autocompletion, startCompletion } from '@codemirror/autocomplete';
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { MSSQL, MySQL, PostgreSQL, sql } from '@codemirror/lang-sql';
import { EditorSelection } from '@codemirror/state';
import { EditorView, keymap, lineNumbers } from '@codemirror/view';

export interface SelectionSnapshot {
  beforeSql: string;
  rearSql: string;
  selectedSql: string;
}

interface SqlEditorProps {
  value: string;
  serverType?: string;
  schemaTables?: Record<string, string[]>;
  onChange?: (value: string, snapshot: SelectionSnapshot) => void;
  onSelectionChange?: (snapshot: SelectionSnapshot) => void;
  onMount?: (view: EditorView) => void;
}

export interface SqlEditorHandle {
  resetViewport: () => void;
}

function normalizeSchemaTables(schemaTables: unknown): Record<string, string[]> {
  if (!schemaTables || typeof schemaTables !== 'object' || Array.isArray(schemaTables)) {
    return {};
  }

  return Object.entries(schemaTables as Record<string, unknown>).reduce<Record<string, string[]>>(
    (accumulator, [tableName, columns]) => {
      if (typeof tableName !== 'string' || tableName.trim() === '') {
        return accumulator;
      }
      accumulator[tableName] = Array.isArray(columns) ? columns.map((column) => String(column)) : [];
      return accumulator;
    },
    {},
  );
}

function resolveDialect(serverType?: string) {
  if (serverType === 'postgresql') {
    return PostgreSQL;
  }
  if (serverType === 'mssql') {
    return MSSQL;
  }
  return MySQL;
}

function buildSelectionSnapshot(viewState: { doc: { toString: () => string }; selection: { main: { head: number; anchor: number } } }, docText?: string): SelectionSnapshot {
  const text = docText ?? viewState.doc.toString();
  const mainSelection = viewState.selection.main;
  const cursor = mainSelection.head;
  const selectionStart = Math.min(mainSelection.anchor, mainSelection.head);
  const selectionEnd = Math.max(mainSelection.anchor, mainSelection.head);
  const safeSql = String(text || '');
  return {
    beforeSql: safeSql.substring(0, cursor),
    rearSql: safeSql.substring(cursor),
    selectedSql: safeSql.substring(selectionStart, selectionEnd),
  };
}

const sqlEditorTheme = EditorView.theme({
  '&': {
    border: '1px solid rgba(15, 23, 42, 0.08)',
    borderRadius: '14px',
    backgroundColor: '#fbfdff',
    fontSize: '14px',
  },
  '.cm-scroller': {
    minHeight: '300px',
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, Courier New, monospace',
  },
  '.cm-content': {
    lineHeight: '1.55',
  },
});

const SqlEditor = forwardRef<SqlEditorHandle, SqlEditorProps>(function SqlEditor({
  value,
  serverType,
  schemaTables,
  onChange,
  onSelectionChange,
  onMount,
}: SqlEditorProps, ref): React.JSX.Element {
  const composingRef = useRef(false);
  const editorViewRef = useRef<EditorView | null>(null);
  const pendingValueRef = useRef<string | null>(null);
  const onChangeRef = useRef(onChange);
  const onSelectionChangeRef = useRef(onSelectionChange);
  const onMountRef = useRef(onMount);
  const normalizedSchema = useMemo(
    () => normalizeSchemaTables(schemaTables),
    [schemaTables],
  );

  useEffect(() => {
    onChangeRef.current = onChange;
  }, [onChange]);

  useEffect(() => {
    onSelectionChangeRef.current = onSelectionChange;
  }, [onSelectionChange]);

  useEffect(() => {
    onMountRef.current = onMount;
  }, [onMount]);

  useImperativeHandle(
    ref,
    () => ({
      resetViewport: () => {
        const editorView = editorViewRef.current;
        if (!editorView) {
          return;
        }
        editorView.dispatch({
          selection: EditorSelection.cursor(0),
          effects: EditorView.scrollIntoView(0, { y: 'start' }),
        });
        if (editorView.scrollDOM) {
          editorView.scrollDOM.scrollTop = 0;
        }
        editorView.focus();
      },
    }),
    [],
  );

  const extensions = useMemo(
    () => [
      lineNumbers(),
      sql({
        dialect: resolveDialect(serverType),
        schema: normalizedSchema,
      }),
      autocompletion({
        activateOnTyping: true,
      }),
      keymap.of([
        {
          key: 'Ctrl-Space',
          run: startCompletion,
        },
      ]),
      syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
      EditorView.updateListener.of((update) => {
        const nextValue = update.state.doc.toString();
        const selectionSnapshot = buildSelectionSnapshot(update.state, nextValue);

        if (update.docChanged) {
          if (composingRef.current) {
            pendingValueRef.current = nextValue;
          } else {
            onChangeRef.current?.(nextValue, selectionSnapshot);
          }
        }

        if (update.selectionSet && !composingRef.current) {
          onSelectionChangeRef.current?.(selectionSnapshot);
        }
      }),
      EditorView.domEventHandlers({
        compositionstart: () => {
          composingRef.current = true;
          return false;
        },
        compositionend: (_event, view) => {
          composingRef.current = false;
          const pendingValue = pendingValueRef.current;
          pendingValueRef.current = null;
          const nextValue = pendingValue == null ? view.state.doc.toString() : pendingValue;
          const selectionSnapshot = buildSelectionSnapshot(view.state, nextValue);
          onChangeRef.current?.(nextValue, selectionSnapshot);
          onSelectionChangeRef.current?.(selectionSnapshot);
          return false;
        },
      }),
      sqlEditorTheme,
    ],
    [normalizedSchema, serverType],
  );

  return (
    <CodeMirror
      basicSetup={false}
      extensions={extensions}
      onCreateEditor={(view: EditorView) => {
        editorViewRef.current = view;
        if (view.scrollDOM) {
          view.scrollDOM.scrollTop = 0;
        }
        onMountRef.current?.(view);
      }}
      value={value}
    />
  );
});

export default SqlEditor;
