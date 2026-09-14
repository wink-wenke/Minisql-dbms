import { useState, useCallback, useRef, useEffect } from 'react';
import { api } from '../api/client';
import type { ExecuteResponse } from '../api/types';

const EXAMPLES = [
  { label: '建表', sql: "CREATE TABLE student(\n    id INT,\n    name VARCHAR(50),\n    age INT\n);" },
  { label: '插入数据', sql: "DELETE FROM student;\n\nINSERT INTO student(id, name, age)\nVALUES (1, 'Alice', 20);\n\nINSERT INTO student(id, name, age)\nVALUES (2, 'Bob', 17);\n\nINSERT INTO student(id, name, age)\nVALUES (3, 'Charlie', 22);" },
  { label: '查询', sql: "SELECT * FROM student;" },
  { label: 'WHERE 条件', sql: "SELECT id, name\nFROM student\nWHERE age > 18;" },
  { label: '复杂查询', sql: "SELECT name, age\nFROM student\nWHERE age > 18 AND id != 3;" },
  { label: '删除', sql: "DELETE FROM student\nWHERE id = 2;" },
  { label: '错误演示', sql: "SELECT score FROM student;" },
];

export default function Playground() {
  const [sql, setSql] = useState(EXAMPLES[0].sql);
  const [result, setResult] = useState<ExecuteResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const execute = useCallback(async () => {
    if (!sql.trim()) return;
    setLoading(true);
    try {
      // Split multiple SQL statements (separated by semicolons)
      const statements = sql
        .split(';')
        .map(s => s.trim())
        .filter(s => s.length > 0);

      if (statements.length === 1) {
        // Single statement - execute directly
        const res = await api.execute(statements[0]);
        setResult(res);
      } else {
        // Multiple statements - execute one by one
        let lastResult: ExecuteResponse | null = null;
        for (const stmt of statements) {
          const res = await api.execute(stmt);
          lastResult = res;
          if (res.error) {
            setResult(res);
            return;
          }
        }
        // All succeeded - show last result
        if (lastResult) {
          setResult({
            ...lastResult,
            type: 'UPDATE',
            affectedRows: statements.length,
          });
        }
      }
    } catch (e) {
      setResult({ type: 'UPDATE', error: { type: '网络错误', message: String(e) } });
    } finally {
      setLoading(false);
    }
  }, [sql]);

  // Ctrl+Enter to execute
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

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-bg-surface">
        <div className="flex items-center gap-3">
          <span className="text-accent font-mono text-sm">▶</span>
          <span className="text-sm font-semibold">SQL 演练场</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-text-muted font-mono">Ctrl+Enter</span>
          <button
            onClick={execute}
            disabled={loading}
            className="px-3 py-1.5 bg-accent text-bg-primary rounded text-xs font-medium hover:bg-accent-hover transition-colors disabled:opacity-50"
          >
            {loading ? '执行中…' : '执行'}
          </button>
        </div>
      </div>

      <div className="flex-1 flex min-h-0">
        {/* Left: Editor */}
        <div className="w-2/5 flex flex-col border-r border-border">
          {/* Example Queries */}
          <div className="flex flex-wrap gap-1.5 px-3 py-2 border-b border-border bg-bg-surface">
            {EXAMPLES.map((ex) => (
              <button
                key={ex.label}
                onClick={() => setSql(ex.sql)}
                className="px-2 py-0.5 text-xs rounded border border-border text-text-secondary hover:text-accent hover:border-accent/30 transition-colors"
              >
                {ex.label}
              </button>
            ))}
          </div>

          {/* SQL Editor */}
          <div className="flex-1 relative">
            <textarea
              ref={textareaRef}
              value={sql}
              onChange={(e) => setSql(e.target.value)}
              className="w-full h-full resize-none bg-bg-primary text-text-primary p-3 font-mono text-sm leading-relaxed focus:outline-none placeholder:text-text-muted"
              placeholder="输入 SQL 语句..."
              spellCheck={false}
            />
          </div>
        </div>

        {/* Right: Results */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* Tabs */}
          <div className="flex border-b border-border bg-bg-surface">
            <div className="px-4 py-2 text-xs font-medium text-accent border-b-2 border-accent">
              结果
            </div>
            <div className="px-4 py-2 text-xs text-text-secondary">
              输出
            </div>
          </div>

          {/* Result Content */}
          <div className="flex-1 overflow-auto p-3">
            {!result && (
              <div className="text-text-muted text-sm text-center mt-20">
                执行一条 SQL 语句后在这里查看结果。
              </div>
            )}

            {result?.error && (
              <div className="rounded border border-red/30 bg-red/5 p-3">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-red text-xs font-mono font-bold">错误</span>
                  <span className="text-red/70 text-xs font-mono">[{result.error.type}]</span>
                </div>
                <div className="text-sm text-red">{result.error.message}</div>
                {result.error.line && result.error.line > 0 && (
                  <div className="text-xs text-text-muted mt-1 font-mono">
                    at line {result.error.line}, column {result.error.column}
                  </div>
                )}
              </div>
            )}

            {result && !result.error && result.type === 'QUERY' && result.columns && result.rows && (
              <div>
                <div className="text-xs text-text-muted mb-2 font-mono">
                  {result.rows.length} row{result.rows.length !== 1 ? 's' : ''} returned
                  {result.timing != null && ` · ${result.timing}ms`}
                </div>
                <div className="overflow-x-auto rounded border border-border">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b border-border bg-bg-surface">
                        {result.columns.map((col) => (
                          <th key={col} className="px-3 py-1.5 text-left text-xs font-mono text-accent font-medium">
                            {col}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {result.rows.map((row, i) => (
                        <tr key={i} className={`border-b border-border-subtle ${i % 2 === 0 ? 'bg-bg-primary' : 'bg-bg-surface/50'}`}>
                          {row.map((val, j) => (
                            <td key={j} className="px-3 py-1.5 text-xs font-mono text-text-primary">
                              {val === null ? <span className="text-text-muted">空值</span> : String(val)}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {result && !result.error && result.type === 'UPDATE' && (
              <div className="rounded border border-green/30 bg-green/5 p-3">
                <div className="flex items-center gap-2">
                  <span className="text-green text-xs font-mono font-bold">正常</span>
                  <span className="text-sm text-text-secondary">
                    {result.affectedRows} record{result.affectedRows !== 1 ? 's' : ''} processed
                  </span>
                  {result.timing != null && (
                    <span className="text-xs text-text-muted font-mono">· {result.timing}ms</span>
                  )}
                </div>
              </div>
            )}

            {result && !result.error && result.type === 'EXPLAIN' && result.planText && (
              <div>
                <div className="text-xs text-text-muted mb-2 font-mono">执行计划</div>
                <pre className="text-sm font-mono text-text-primary bg-bg-surface border border-border rounded p-3 whitespace-pre-wrap">
                  {result.planText}
                </pre>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
