import React, { useEffect, useMemo, useRef } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { autocompletion, startCompletion } from '@codemirror/autocomplete';
import { defaultHighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { MSSQL, MySQL, PostgreSQL, sql } from '@codemirror/lang-sql';
import { EditorView, keymap, lineNumbers } from '@codemirror/view';

function normalizeSchemaTables(schemaTables) {
  if (!schemaTables || typeof schemaTables !== 'object' || Array.isArray(schemaTables)) {
    return {};
  }

  return Object.entries(schemaTables).reduce((accumulator, [tableName, columns]) => {
    if (typeof tableName !== 'string' || tableName.trim() === '') {
      return accumulator;
    }
    accumulator[tableName] = Array.isArray(columns) ? columns.map((column) => String(column)) : [];
    return accumulator;
  }, {});
}

function resolveDialect(serverType) {
  if (serverType === 'postgresql') {
    return PostgreSQL;
  }
  if (serverType === 'mssql') {
    return MSSQL;
  }
  return MySQL;
}

function buildSelectionSnapshot(viewState, docText = viewState.doc.toString()) {
  const mainSelection = viewState.selection.main;
  const cursor = mainSelection.head;
  const selectionStart = Math.min(mainSelection.anchor, mainSelection.head);
  const selectionEnd = Math.max(mainSelection.anchor, mainSelection.head);
  const safeSql = String(docText || '');
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

function SqlEditor({
  value,
  serverType,
  schemaTables,
  onChange,
  onSelectionChange,
  onMount,
}) {
  const composingRef = useRef(false);
  const pendingValueRef = useRef(null);
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
      onCreateEditor={(view) => {
        onMountRef.current?.(view);
      }}
      value={value}
    />
  );
}

export default SqlEditor;
