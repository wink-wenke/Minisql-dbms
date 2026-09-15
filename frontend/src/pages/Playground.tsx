import { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import { api } from '../api/client';
import type { ExecuteResponse } from '../api/types';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';
import Badge from '../components/Badge';

// ---------------------------------------------------------------------------
// Example queries grouped by category
// ---------------------------------------------------------------------------

interface ExampleEntry {
  label: string;
  sql: string;
  category: 'ddl' | 'dml' | 'query' | 'demo';
}

const EXAMPLES: ExampleEntry[] = [
  {
    label: 'Create Table',
    sql: "CREATE TABLE student(\n    id INT,\n    name VARCHAR(50),\n    age INT\n);",
    category: 'ddl',
  },
  {
    label: 'Insert Data',
    sql: "DELETE FROM student;\n\nINSERT INTO student(id, name, age)\nVALUES (1, 'Alice', 20);\n\nINSERT INTO student(id, name, age)\nVALUES (2, 'Bob', 17);\n\nINSERT INTO student(id, name, age)\nVALUES (3, 'Charlie', 22);",
    category: 'dml',
  },
  {
    label: 'Delete',
    sql: 'DELETE FROM student\nWHERE id = 2;',
    category: 'dml',
  },
  {
    label: 'Select',
    sql: 'SELECT * FROM student;',
    category: 'query',
  },
  {
    label: 'WHERE',
    sql: 'SELECT id, name\nFROM student\nWHERE age > 18;',
    category: 'query',
  },
  {
    label: 'Complex',
    sql: 'SELECT name, age\nFROM student\nWHERE age > 18 AND id != 3;',
    category: 'query',
  },
  {
    label: 'Error Demo',
    sql: 'SELECT score FROM student;',
    category: 'demo',
  },
];

const CATEGORY_ORDER: Array<{ key: ExampleEntry['category']; label: string }> = [
  { key: 'ddl', label: 'DDL' },
  { key: 'dml', label: 'DML' },
  { key: 'query', label: 'Query' },
  { key: 'demo', label: 'Demo' },
];

// ---------------------------------------------------------------------------
// History entry
// ---------------------------------------------------------------------------

interface HistoryEntry {
  id: number;
  sql: string;
  result: ExecuteResponse;
  timestamp: Date;
}

// ---------------------------------------------------------------------------
// Messages entry
// ---------------------------------------------------------------------------

interface MessageEntry {
  id: number;
  text: string;
  kind: 'info' | 'success' | 'error' | 'timing';
  timestamp: Date;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

type TabKey = 'result' | 'messages' | 'history';

export default function Playground() {
  // --- Editor state ---
  const [sql, setSql] = useState(EXAMPLES[0].sql);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // --- Execution state ---
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ExecuteResponse | null>(null);

  // --- Tab state ---
  const [activeTab, setActiveTab] = useState<TabKey>('result');

  // --- History (stored in component state) ---
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const historyIdRef = useRef(0);

  // --- Messages log ---
  const [messages, setMessages] = useState<MessageEntry[]>([]);
  const msgIdRef = useRef(0);

  const pushMessage = useCallback(
    (text: string, kind: MessageEntry['kind'] = 'info') => {
      msgIdRef.current += 1;
      setMessages((prev) => [
        ...prev,
        { id: msgIdRef.current, text, kind, timestamp: new Date() },
      ]);
    },
    [],
  );

  // --- Clear messages before each run ---
  const clearMessages = useCallback(() => {
    setMessages([]);
    msgIdRef.current = 0;
  }, []);

  // ---------------------------------------------------------------------------
  // Execute SQL
  // ---------------------------------------------------------------------------

  const execute = useCallback(async () => {
    const trimmed = sql.trim();
    if (!trimmed) return;

    setLoading(true);
    clearMessages();
    pushMessage('Executing query...', 'info');
    const startTime = performance.now();

    try {
      // Split multiple SQL statements by semicolons (respecting string literals)
      const statements = trimmed
        .split(';')
        .map((s) => s.trim())
        .filter((s) => s.length > 0);

      let lastResult: ExecuteResponse | null = null;
      let anyError = false;

      for (let i = 0; i < statements.length; i++) {
        const stmt = statements[i];
        const stmtStart = performance.now();

        try {
          const res = await api.execute(stmt);
          const elapsed = Math.round(performance.now() - stmtStart);

          if (res.error) {
            pushMessage(
              `Statement ${i + 1}/${statements.length} failed: ${res.error.message}`,
              'error',
            );
            lastResult = res;
            anyError = true;
            break;
          }

          if (res.type === 'QUERY') {
            const rowCount = res.rows?.length ?? 0;
            pushMessage(
              `Statement ${i + 1}/${statements.length}: returned ${rowCount} row${rowCount !== 1 ? 's' : ''} (${elapsed}ms)`,
              'success',
            );
            lastResult = res;
            // For multi-statement: only show QUERY result if it's the only one
            // or the last meaningful one
          } else {
            pushMessage(
              `Statement ${i + 1}/${statements.length}: affected ${res.affectedRows ?? 0} row${(res.affectedRows ?? 0) !== 1 ? 's' : ''} (${elapsed}ms)`,
              'success',
            );
            lastResult = res;
          }
        } catch (stmtErr) {
          pushMessage(
            `Statement ${i + 1}/${statements.length} error: ${String(stmtErr)}`,
            'error',
          );
          lastResult = {
            type: 'UPDATE',
            error: { type: 'NETWORK', message: String(stmtErr) },
          };
          anyError = true;
          break;
        }
      }

      const totalElapsed = Math.round(performance.now() - startTime);
      pushMessage(`Total time: ${totalElapsed}ms`, 'timing');

      if (lastResult) {
        setResult(lastResult);

        // Add to history
        historyIdRef.current += 1;
        setHistory((prev) => [
          {
            id: historyIdRef.current,
            sql: trimmed,
            result: lastResult!,
            timestamp: new Date(),
          },
          ...prev,
        ]);

        // Switch to result tab on success, messages tab on error
        if (anyError) {
          setActiveTab('messages');
        } else if (lastResult.type === 'QUERY') {
          setActiveTab('result');
        } else {
          setActiveTab('messages');
        }
      }
    } catch (e) {
      pushMessage(`Network error: ${String(e)}`, 'error');
      const errResult: ExecuteResponse = {
        type: 'UPDATE',
        error: { type: 'NETWORK', message: String(e) },
      };
      setResult(errResult);
      setActiveTab('messages');

      historyIdRef.current += 1;
      setHistory((prev) => [
        {
          id: historyIdRef.current,
          sql: trimmed,
          result: errResult,
          timestamp: new Date(),
        },
        ...prev,
      ]);
    } finally {
      setLoading(false);
    }
  }, [sql, clearMessages, pushMessage]);

  // ---------------------------------------------------------------------------
  // Ctrl+Enter keyboard shortcut
  // ---------------------------------------------------------------------------

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        execute();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [execute]);

  // ---------------------------------------------------------------------------
  // Tab key support in textarea (insert tab character)
  // ---------------------------------------------------------------------------

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Tab') {
        e.preventDefault();
        const ta = textareaRef.current;
        if (!ta) return;
        const start = ta.selectionStart;
        const end = ta.selectionEnd;
        const value = ta.value;
        setSql(value.substring(0, start) + '    ' + value.substring(end));
        // Restore cursor position after React re-renders
        requestAnimationFrame(() => {
          ta.selectionStart = ta.selectionEnd = start + 4;
        });
      }
    },
    [],
  );

  // ---------------------------------------------------------------------------
  // Line numbers
  // ---------------------------------------------------------------------------

  const lineCount = useMemo(() => {
    return sql.split('\n').length;
  }, [sql]);

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Header bar */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-bg-surface shrink-0">
        <div className="flex items-center gap-3">
          <span className="text-accent font-mono text-sm">&#9654;</span>
          <span className="text-sm font-semibold">SQL Playground</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-[10px] text-text-muted font-mono px-1.5 py-0.5 rounded border border-border-subtle">
            Ctrl+Enter
          </span>
        </div>
      </div>

      <div className="flex-1 flex min-h-0">
        {/* ============================================================= */}
        {/* LEFT SIDE -- SQL Editor                                        */}
        {/* ============================================================= */}
        <div className="w-2/5 flex flex-col border-r border-border">
          {/* Example query buttons */}
          <div className="px-3 py-2 border-b border-border bg-bg-surface space-y-1.5">
            {CATEGORY_ORDER.map((cat) => {
              const items = EXAMPLES.filter((e) => e.category === cat.key);
              if (items.length === 0) return null;
              return (
                <div key={cat.key} className="flex items-center gap-1.5 flex-wrap">
                  <span className="text-[10px] font-mono font-bold uppercase text-text-muted w-10 shrink-0">
                    {cat.label}
                  </span>
                  {items.map((ex) => (
                    <button
                      key={ex.label}
                      onClick={() => {
                        setSql(ex.sql);
                        textareaRef.current?.focus();
                      }}
                      className="px-2 py-0.5 text-xs rounded border border-border text-text-secondary hover:text-accent hover:border-accent/30 transition-colors"
                    >
                      {ex.label}
                    </button>
                  ))}
                </div>
              );
            })}
          </div>

          {/* Editor with line numbers */}
          <div className="flex-1 flex min-h-0 overflow-hidden">
            {/* Line numbers column */}
            <div className="flex-shrink-0 bg-bg-surface border-r border-border-subtle overflow-hidden select-none">
              <div className="py-3 pr-2 pl-3 text-right">
                {Array.from({ length: lineCount }, (_, i) => (
                  <div
                    key={i + 1}
                    className="text-[11px] leading-relaxed font-mono text-text-muted"
                  >
                    {i + 1}
                  </div>
                ))}
              </div>
            </div>

            {/* Textarea */}
            <div className="flex-1 relative min-w-0">
              <textarea
                ref={textareaRef}
                value={sql}
                onChange={(e) => setSql(e.target.value)}
                onKeyDown={handleKeyDown}
                className="absolute inset-0 w-full h-full resize-none bg-transparent text-text-primary font-mono text-sm leading-relaxed p-3 pl-3 focus:outline-none placeholder:text-text-muted/50"
                placeholder="Enter SQL statements..."
                spellCheck={false}
                autoCapitalize="off"
                autoCorrect="off"
              />
            </div>
          </div>

          {/* Run button */}
          <div className="flex items-center justify-end px-3 py-2 border-t border-border bg-bg-surface shrink-0">
            <Button
              variant="primary"
              size="sm"
              onClick={execute}
              disabled={loading || !sql.trim()}
              className="min-w-[80px]"
            >
              {loading ? (
                <span className="flex items-center gap-1.5">
                  <svg
                    className="animate-spin h-3 w-3"
                    viewBox="0 0 24 24"
                    fill="none"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                    />
                  </svg>
                  Running...
                </span>
              ) : (
                'Run'
              )}
            </Button>
          </div>
        </div>

        {/* ============================================================= */}
        {/* RIGHT SIDE -- Results / Messages / History                     */}
        {/* ============================================================= */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* Tab bar */}
          <div className="flex items-center border-b border-border bg-bg-surface shrink-0">
            <TabButton
              label="Result"
              active={activeTab === 'result'}
              onClick={() => setActiveTab('result')}
            />
            <TabButton
              label="Messages"
              active={activeTab === 'messages'}
              onClick={() => setActiveTab('messages')}
            />
            <TabButton
              label="History"
              active={activeTab === 'history'}
              onClick={() => setActiveTab('history')}
            />
          </div>

          {/* Tab content */}
          <div className="flex-1 overflow-auto">
            {/* ---- RESULT TAB ---- */}
            {activeTab === 'result' && (
              <div className="p-3">
                {!result && (
                  <EmptyState
                    icon="&#9654;"
                    title="No results yet"
                    description="Execute a SQL query to see results here."
                  />
                )}

                {/* Error */}
                {result?.error && (
                  <div className="rounded border border-red/30 bg-red/5 p-4">
                    <div className="flex items-center gap-2 mb-2">
                      <svg
                        className="w-4 h-4 text-red shrink-0"
                        fill="none"
                        viewBox="0 0 24 24"
                        strokeWidth={2}
                        stroke="currentColor"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126z"
                        />
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          d="M12 15.75h.007v.008H12v-.008z"
                        />
                      </svg>
                      <Badge variant="error">ERROR</Badge>
                      <span className="text-red/60 text-xs font-mono">
                        [{result.error.type}]
                      </span>
                    </div>
                    <div className="text-sm text-red font-mono">
                      {result.error.message}
                    </div>
                    {result.error.line != null && result.error.line > 0 && (
                      <div className="text-xs text-text-muted mt-2 font-mono">
                        at line {result.error.line}
                        {result.error.column != null &&
                          `, column ${result.error.column}`}
                      </div>
                    )}
                  </div>
                )}

                {/* Query result table */}
                {result &&
                  !result.error &&
                  result.type === 'QUERY' &&
                  result.columns &&
                  result.rows && (
                    <div>
                      <div className="flex items-center gap-2 mb-2">
                        <Badge variant="ok">
                          {result.rows.length} row
                          {result.rows.length !== 1 ? 's' : ''}
                        </Badge>
                        {result.timing != null && (
                          <span className="text-xs text-text-muted font-mono">
                            {result.timing}ms
                          </span>
                        )}
                      </div>
                      <div className="overflow-x-auto rounded border border-border">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="border-b-2 border-border bg-bg-surface">
                              {result.columns.map((col) => (
                                <th
                                  key={col}
                                  className="px-3 py-2 text-left text-[11px] font-mono font-bold text-accent uppercase tracking-wider"
                                >
                                  {col}
                                </th>
                              ))}
                            </tr>
                          </thead>
                          <tbody>
                            {result.rows.map((row, i) => (
                              <tr
                                key={i}
                                className={
                                  i % 2 === 0
                                    ? 'bg-bg-primary'
                                    : 'bg-bg-surface/30'
                                }
                              >
                                {row.map((val, j) => (
                                  <td
                                    key={j}
                                    className="px-3 py-1.5 text-xs font-mono text-text-primary border-b border-border-subtle"
                                  >
                                    {val === null ? (
                                      <span className="italic text-text-muted/60">
                                        NULL
                                      </span>
                                    ) : (
                                      String(val)
                                    )}
                                  </td>
                                ))}
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  )}

                {/* Update result (no error) */}
                {result &&
                  !result.error &&
                  (result.type === 'UPDATE' ||
                    (result.type === 'QUERY' &&
                      (!result.columns || !result.rows))) && (
                    <div className="rounded border border-green/30 bg-green/5 p-3">
                      <div className="flex items-center gap-2">
                        <Badge variant="ok">OK</Badge>
                        <span className="text-sm text-text-secondary">
                          {result.affectedRows != null
                            ? `${result.affectedRows} record${result.affectedRows !== 1 ? 's' : ''} affected`
                            : 'Statement executed successfully'}
                        </span>
                        {result.timing != null && (
                          <span className="text-xs text-text-muted font-mono">
                            {result.timing}ms
                          </span>
                        )}
                      </div>
                    </div>
                  )}

                {/* Explain result */}
                {result &&
                  !result.error &&
                  result.type === 'EXPLAIN' &&
                  result.planText && (
                    <div>
                      <div className="text-xs text-text-muted mb-2 font-mono">
                        Execution Plan
                      </div>
                      <pre className="text-sm font-mono text-text-primary bg-bg-surface border border-border rounded p-3 whitespace-pre-wrap">
                        {result.planText}
                      </pre>
                    </div>
                  )}
              </div>
            )}

            {/* ---- MESSAGES TAB ---- */}
            {activeTab === 'messages' && (
              <div className="p-3">
                {messages.length === 0 ? (
                  <EmptyState
                    icon="&#128196;"
                    title="No messages"
                    description="Messages from query execution will appear here."
                  />
                ) : (
                  <div className="space-y-1 font-mono text-xs">
                    {messages.map((msg) => (
                      <div
                        key={msg.id}
                        className="flex items-start gap-2 py-1 border-b border-border-subtle"
                      >
                        <span className="text-text-muted/50 tabular-nums shrink-0">
                          {msg.timestamp.toLocaleTimeString()}
                        </span>
                        <span
                          className={
                            msg.kind === 'error'
                              ? 'text-red'
                              : msg.kind === 'success'
                                ? 'text-green'
                                : msg.kind === 'timing'
                                  ? 'text-text-muted'
                                  : 'text-text-secondary'
                          }
                        >
                          {msg.text}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* ---- HISTORY TAB ---- */}
            {activeTab === 'history' && (
              <div className="p-3">
                {history.length === 0 ? (
                  <EmptyState
                    icon="&#128336;"
                    title="No history"
                    description="Previously executed queries will be listed here."
                  />
                ) : (
                  <div className="space-y-2">
                    {history.map((entry) => (
                      <div
                        key={entry.id}
                        className="rounded border border-border p-3 hover:border-border-hover transition-colors"
                      >
                        <div className="flex items-center justify-between mb-1.5">
                          <div className="flex items-center gap-2">
                            {entry.result.error ? (
                              <Badge variant="error">ERROR</Badge>
                            ) : entry.result.type === 'QUERY' ? (
                              <Badge variant="info">QUERY</Badge>
                            ) : (
                              <Badge variant="ok">OK</Badge>
                            )}
                            <span className="text-[10px] text-text-muted font-mono">
                              {entry.timestamp.toLocaleTimeString()}
                            </span>
                          </div>
                          <button
                            onClick={() => setSql(entry.sql)}
                            className="text-[10px] text-accent hover:underline font-mono"
                          >
                            Reuse
                          </button>
                        </div>
                        <pre className="text-xs font-mono text-text-secondary whitespace-pre-wrap break-all leading-relaxed bg-bg-primary rounded px-2 py-1.5 border border-border-subtle">
                          {entry.sql}
                        </pre>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Tab button sub-component
// ---------------------------------------------------------------------------

function TabButton({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-2 text-xs font-medium transition-colors border-b-2 ${
        active
          ? 'text-accent border-accent'
          : 'text-text-secondary border-transparent hover:text-text-primary hover:border-border-subtle'
      }`}
    >
      {label}
    </button>
  );
}
