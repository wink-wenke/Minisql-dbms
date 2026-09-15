import { useState, useCallback, useRef } from 'react';
import { api } from '../api/client';
import type { ExecuteResponse, ExplainResponse } from '../api/types';
import SqlEditor from '../components/SqlEditor';
import ResultTable from '../components/ResultTable';
import Button from '../components/Button';
import Badge from '../components/Badge';

interface HistoryEntry {
  sql: string;
  result: ExecuteResponse;
  timing: number;
  timestamp: string;
}

const EXAMPLES = [
  { group: 'DDL', items: [
    { label: '建表', sql: 'CREATE TABLE student(id INT, name VARCHAR(50), age INT);' },
    { label: '建索引', sql: 'CREATE INDEX idx_name ON student(name);' },
    { label: '建视图', sql: 'CREATE VIEW adult AS SELECT id, name FROM student WHERE age >= 18;' },
    { label: '删表', sql: 'DROP TABLE IF EXISTS student;' },
  ]},
  { group: 'DML', items: [
    { label: '插入', sql: "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);" },
    { label: '批量插入', sql: "INSERT INTO student(id, name, age) VALUES (2, 'Bob', 22);\nINSERT INTO student(id, name, age) VALUES (3, 'Carol', 19);\nINSERT INTO student(id, name, age) VALUES (4, 'Dave', 17);" },
    { label: '更新', sql: "UPDATE student SET age = 21 WHERE name = 'Alice';" },
    { label: '删除', sql: 'DELETE FROM student WHERE id = 1;' },
  ]},
  { group: 'Query', items: [
    { label: '全表', sql: 'SELECT * FROM student;' },
    { label: '条件', sql: 'SELECT id, name FROM student WHERE age > 18;' },
    { label: '排序', sql: 'SELECT * FROM student ORDER BY age DESC;' },
    { label: '聚合', sql: "SELECT name, age FROM student WHERE age >= 18 ORDER BY age;" },
  ]},
];

type Tab = 'result' | 'plan' | 'messages';

export default function Console() {
  const [sql, setSql] = useState('SELECT * FROM student;');
  const [result, setResult] = useState<ExecuteResponse | null>(null);
  const [explainResult, setExplainResult] = useState<ExplainResponse | null>(null);
  const [messages, setMessages] = useState<string[]>([]);
  const [running, setRunning] = useState(false);
  const [activeTab, setActiveTab] = useState<Tab>('result');
  const runIdRef = useRef(0);
  const sqlRef = useRef(sql);
  sqlRef.current = sql;

  const execute = useCallback(async (query?: string) => {
    const q = (query ?? sqlRef.current).trim();
    if (!q || running) return;


    const runId = ++runIdRef.current;
    setRunning(true);
    setResult(null);
    setExplainResult(null);
    setMessages([]);
    setActiveTab('result');

    const ts = new Date().toLocaleTimeString();
    const logs: string[] = [];

    const addMsg = (msg: string) => {
      logs.push(`[${ts}] ${msg}`);
      setMessages([...logs]);
    };

    // Split multi-statement SQL by semicolons
    const statements = q.split(';').map((s) => s.trim()).filter((s) => s.length > 0);

    if (statements.length === 1) {
      addMsg(`Executing: ${q.split('\n')[0]}${q.includes('\n') ? '...' : ''}`);
    } else {
      addMsg(`Executing ${statements.length} statements...`);
    }

    try {
      const start = Date.now();
      let lastResult: ExecuteResponse | null = null;
      let anyError = false;

      for (let i = 0; i < statements.length; i++) {
        const stmt = statements[i];
        const stmtStart = Date.now();

        try {
          const res = await api.execute(stmt);
          const elapsed = Date.now() - stmtStart;

          if (runId !== runIdRef.current) return;

          if (res.error) {
            addMsg(`Statement ${i + 1}/${statements.length} ERROR: ${res.error.message}`);
            lastResult = res;
            anyError = true;
            break;
          }

          if (res.type === 'QUERY') {
            addMsg(`Statement ${i + 1}/${statements.length}: ${res.rows?.length ?? 0} rows (${res.timing ?? elapsed}ms)`);
          } else {
            addMsg(`Statement ${i + 1}/${statements.length}: ${res.affectedRows ?? 0} rows affected (${res.timing ?? elapsed}ms)`);
          }
          lastResult = res;
        } catch (stmtErr) {
          if (runId !== runIdRef.current) return;
          addMsg(`Statement ${i + 1}/${statements.length} EXCEPTION: ${stmtErr}`);
          lastResult = { type: 'UPDATE', error: { type: 'Exception', message: String(stmtErr) } };
          anyError = true;
          break;
        }
      }

      const elapsed = Date.now() - start;
      if (lastResult) setResult(lastResult);

      // Save to history (save the full SQL, not individual statements)
      if (!anyError) {
        const entry: HistoryEntry = {
          sql: q,
          result: lastResult!,
          timing: elapsed,
          timestamp: ts,
        };
        try {
          const existing: HistoryEntry[] = JSON.parse(localStorage.getItem('minisql-history') || '[]');
          existing.unshift(entry);
          localStorage.setItem('minisql-history', JSON.stringify(existing.slice(0, 100)));
        } catch { /* ignore quota errors */ }
      }
    } catch (e) {
      if (runId !== runIdRef.current) return;
      addMsg(`EXCEPTION: ${e}`);
      setResult({ type: 'UPDATE', error: { type: 'Exception', message: String(e) } });
    } finally {
      if (runId === runIdRef.current) setRunning(false);
    }
  }, [running]);

  const explain = useCallback(async () => {
    const q = sqlRef.current.trim();
    if (!q || running) return;

    setRunning(true);
    setExplainResult(null);
    setActiveTab('plan');

    try {
      const res = await api.explain(q);
      setExplainResult(res);
    } catch (e) {
      setExplainResult({ sql: q, planBefore: '', planAfter: '', blocksAccessed: 0, recordsOutput: 0, error: { type: 'Exception', message: String(e) } });
    } finally {
      setRunning(false);
    }
  }, [running]);

  const loadExample = (exampleSql: string) => {
    setSql(exampleSql);
    setResult(null);
    setExplainResult(null);
    setMessages([]);
  };

  return (
    <div className="flex flex-col gap-4 h-full">
      {/* Example chips */}
      <div className="flex items-center gap-3 flex-wrap">
        {EXAMPLES.map((group) => (
          <div key={group.group} className="flex items-center gap-1">
            <span className="text-[10px] font-mono font-bold text-text-muted/40 uppercase tracking-wider mr-1 select-none">{group.group}</span>
            {group.items.map((item) => (
              <button
                key={item.label}
                onClick={() => loadExample(item.sql)}
                className="text-[12px] font-mono text-text-secondary hover:text-accent px-2.5 py-1 rounded-md hover:bg-accent-muted/60 transition-all duration-150 hover:scale-[1.02] active:scale-[0.98]"
              >
                {item.label}
              </button>
            ))}
          </div>
        ))}
      </div>

      {/* Editor + buttons — flex-0 for fixed height */}
      <div className="bg-white rounded-xl border border-border overflow-hidden flex-shrink-0">
        <div className="p-4">
          <SqlEditor value={sql} onChange={setSql} onRun={() => execute()} />
          <div className="flex items-center justify-between mt-3">
            <div className="flex items-center gap-2 text-[12px] text-text-muted font-mono">
              {running && <span className="text-accent">执行中...</span>}
              {!running && result && !result.error && result.type === 'QUERY' && (
                <span className="flex items-center gap-2">
                  <Badge variant="pass">成功</Badge>
                  <span>{result.rows?.length ?? 0} 行{result.timing ? `，${result.timing}ms` : ''}</span>
                </span>
              )}
              {!running && result && result.error && (
                <Badge variant="error">ERROR</Badge>
              )}
            </div>
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" onClick={() => { setSql(''); setResult(null); setExplainResult(null); setMessages([]); }}>
                清空
              </Button>
              <Button variant="secondary" size="sm" onClick={explain} disabled={running || !sql.trim().toUpperCase().startsWith('SELECT')}>
                执行计划
              </Button>
              <Button onClick={() => execute()} disabled={running}>
                {running ? '执行中...' : '执行'}
              </Button>
            </div>
          </div>
        </div>
      </div>

      {/* Results section — flex-1 fills remaining height */}
      <div className="flex-1 min-h-0 bg-white rounded-xl border border-border overflow-hidden flex flex-col">
        {/* Tabs */}
        <div className="flex items-center gap-0 px-4 border-b border-border bg-bg-elevated/20 flex-shrink-0">
          {([['result', '结果'], ['plan', '执行计划'], ['messages', '消息']] as const).map(([key, label]) => (
            <button
              key={key}
              onClick={() => setActiveTab(key)}
              className={`px-3 py-2.5 text-[12px] font-mono border-b-2 transition-colors ${
                activeTab === key
                  ? 'border-accent text-accent font-semibold'
                  : 'border-transparent text-text-muted hover:text-text-secondary'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {/* Tab content — flex-1 fills remaining */}
        <div className="flex-1 min-h-0 overflow-auto p-4">
          {activeTab === 'result' && (
            <>
              {result?.error && (
                <div className="px-3 py-2 rounded-lg bg-red-muted font-mono text-[13px] text-red mb-4">
                  {result.error.message}
                </div>
              )}
              {result?.type === 'QUERY' && result.columns && result.rows && (
                <ResultTable columns={result.columns} rows={result.rows} />
              )}
              {result?.type === 'UPDATE' && !result.error && (
                <div className="text-[13px] text-text-secondary font-mono">
                  Statement executed successfully.
                  {result.affectedRows !== undefined && ` ${result.affectedRows} row(s) affected.`}
                </div>
              )}
              {!result && !running && (
                <div className="flex flex-col items-center justify-center h-full text-center">
                  <span className="text-3xl text-text-muted/20 mb-3">▷</span>
                  <span className="text-[14px] text-text-secondary">编写查询并点击执行</span>
                  <span className="text-[13px] text-text-muted mt-1">或按 Ctrl + Enter</span>
                </div>
              )}
            </>
          )}

          {activeTab === 'plan' && (
            <>
              {explainResult?.error && (
                <div className="px-3 py-2 rounded-lg bg-red-muted font-mono text-[13px] text-red mb-4">
                  {explainResult.error.message}
                </div>
              )}
              {explainResult && !explainResult.error && (
                <div className="space-y-4">
                  <div className="flex items-center gap-4 text-[13px] font-mono text-text-muted">
                    <span>访问块数: <span className="text-text-primary font-semibold">{explainResult.blocksAccessed}</span></span>
                    <span>输出记录: <span className="text-text-primary font-semibold">{explainResult.recordsOutput}</span></span>
                  </div>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <div className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider mb-2">优化前</div>
                      <pre className="p-3 bg-bg-input rounded-lg border border-border text-[13px] font-mono text-text-secondary overflow-x-auto whitespace-pre-wrap">
                        {explainResult.planBefore || '—'}
                      </pre>
                    </div>
                    <div>
                      <div className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider mb-2">优化后</div>
                      <pre className="p-3 bg-bg-input rounded-lg border border-border text-[13px] font-mono text-text-secondary overflow-x-auto whitespace-pre-wrap">
                        {explainResult.planAfter || '—'}
                      </pre>
                    </div>
                  </div>
                </div>
              )}
              {!explainResult && (
                <div className="flex flex-col items-center justify-center h-full text-center">
                  <span className="text-3xl text-text-muted/20 mb-3">→</span>
                  <span className="text-[14px] text-text-secondary">点击执行计划查看执行方案</span>
                </div>
              )}
            </>
          )}

          {activeTab === 'messages' && (
            <div className="font-mono text-[13px] space-y-0.5">
              {messages.length === 0 ? (
                <div className="text-[13px] text-text-muted">暂无消息。</div>
              ) : (
                messages.map((msg, i) => (
                  <div key={i} className={`py-0.5 ${msg.includes('ERROR') || msg.includes('EXCEPTION') ? 'text-red' : 'text-text-secondary'}`}>
                    {msg}
                  </div>
                ))
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
