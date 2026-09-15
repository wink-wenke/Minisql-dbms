import { useState, useCallback } from 'react';
import { api } from '../api/client';
import Button from '../components/Button';
import Badge from '../components/Badge';
import EmptyState from '../components/EmptyState';

interface TestResult {
  name: string;
  sql: string;
  expected: string;
  actual: string;
  status: 'PASS' | 'FAIL';
  timing?: number;
  group: string;
}

const PRESET_TESTS: { name: string; sql: string; expectError?: boolean; group: string }[] = [
  { name: 'DROP TABLE', sql: 'DROP TABLE IF EXISTS test_t;', group: 'Setup' },
  { name: 'CREATE TABLE', sql: 'CREATE TABLE test_t(id INT, name VARCHAR(50));', group: 'DDL' },
  { name: 'INSERT row', sql: "INSERT INTO test_t(id, name) VALUES (1, 'hello');", group: 'DML' },
  { name: 'INSERT row 2', sql: "INSERT INTO test_t(id, name) VALUES (2, 'world');", group: 'DML' },
  { name: 'SELECT *', sql: 'SELECT * FROM test_t;', group: 'Query' },
  { name: 'SELECT with WHERE', sql: 'SELECT id FROM test_t WHERE id > 0;', group: 'Query' },
  { name: 'DELETE', sql: 'DELETE FROM test_t WHERE id = 1;', group: 'DML' },
  { name: 'Error: missing table', sql: 'SELECT * FROM nonexistent;', expectError: true, group: 'Error' },
  { name: 'Error: missing column', sql: 'SELECT bad_col FROM test_t;', expectError: true, group: 'Error' },
  { name: 'Error: syntax', sql: 'SELCT * FROM test_t;', expectError: true, group: 'Error' },
];

const GROUPS = ['Setup', 'DDL', 'DML', 'Query', 'Error'];

type Filter = 'all' | 'passed' | 'failed';

export default function Tests() {
  const [results, setResults] = useState<TestResult[]>([]);
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState(0);
  const [filter, setFilter] = useState<Filter>('all');

  const runTests = useCallback(async () => {
    setRunning(true);
    setResults([]);
    setProgress(0);
    setFilter('all');
    const res: TestResult[] = [];

    for (let i = 0; i < PRESET_TESTS.length; i++) {
      const test = PRESET_TESTS[i];
      const start = Date.now();
      try {
        const r = await api.execute(test.sql);
        const timing = Date.now() - start;
        const hasError = !!r.error;
        const pass = test.expectError ? hasError : !hasError;
        res.push({
          name: test.name,
          sql: test.sql,
          expected: test.expectError ? 'ERROR' : 'OK',
          actual: hasError ? `ERROR: ${r.error!.message}` : (r.type === 'QUERY' ? `${r.rows?.length ?? 0} rows` : 'OK'),
          status: pass ? 'PASS' : 'FAIL',
          timing,
          group: test.group,
        });
      } catch (e) {
        res.push({
          name: test.name,
          sql: test.sql,
          expected: test.expectError ? 'ERROR' : 'OK',
          actual: `EXCEPTION: ${e}`,
          status: test.expectError ? 'PASS' : 'FAIL',
          timing: Date.now() - start,
          group: test.group,
        });
      }
      setResults([...res]);
      setProgress(((i + 1) / PRESET_TESTS.length) * 100);
    }
    setRunning(false);
  }, []);

  const passed = results.filter((r) => r.status === 'PASS').length;
  const failed = results.filter((r) => r.status === 'FAIL').length;
  const total = results.length;

  const filteredResults = filter === 'all' ? results
    : filter === 'passed' ? results.filter((r) => r.status === 'PASS')
    : results.filter((r) => r.status === 'FAIL');

  return (
    <div className="flex flex-col gap-4 h-full">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <h1 className="text-[20px] font-bold text-text-primary">测试套件</h1>
          {results.length > 0 && (
            <div className="flex items-center gap-2">
              <Badge variant="pass">{passed} 通过</Badge>
              {failed > 0 && <Badge variant="fail">{failed} 失败</Badge>}
              <span className="text-[12px] font-mono text-text-muted">
                共 {total} 项
              </span>
            </div>
          )}
        </div>
        <div className="flex items-center gap-3">
          {running && (
            <div className="w-40 h-1.5 bg-bg-elevated rounded-full overflow-hidden">
              <div
                className="h-full bg-accent rounded-full transition-all duration-300"
                style={{ width: `${progress}%` }}
              />
            </div>
          )}
          <Button onClick={runTests} disabled={running}>
            {running ? `运行中... (${results.length}/${PRESET_TESTS.length})` : '运行测试'}
          </Button>
        </div>
      </div>

      {/* Filter tabs */}
      {results.length > 0 && (
        <div className="flex items-center gap-0 border-b border-border">
          {([['all', '全部', total], ['passed', '通过', passed], ['failed', '失败', failed]] as const).map(([key, label, count]) => (
            <button
              key={key}
              onClick={() => setFilter(key)}
              className={`px-3 py-2.5 text-[12px] font-mono border-b-2 transition-colors ${
                filter === key
                  ? 'border-accent text-accent font-semibold'
                  : 'border-transparent text-text-muted hover:text-text-secondary'
              }`}
            >
              {label} ({count})
            </button>
          ))}
        </div>
      )}

      {/* Results */}
      <div className="flex-1 min-h-0 overflow-auto">
        {results.length === 0 && !running ? (
          <EmptyState icon="✓" title="暂无测试结果" description="点击「运行测试」执行测试套件" />
        ) : (
          <div className="space-y-5">
            {GROUPS.map((group) => {
              const groupResults = filteredResults.filter((r) => r.group === group);
              if (groupResults.length === 0) return null;
              const groupPassed = groupResults.filter((r) => r.status === 'PASS').length;
              return (
                <div key={group}>
                  <div className="flex items-center gap-2 mb-2">
                    <span className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider">{group}</span>
                    <span className="text-[11px] font-mono text-text-muted">
                      {groupPassed}/{groupResults.length}
                    </span>
                  </div>
                  <div className="border border-border rounded-xl overflow-hidden bg-white">
                    <table className="w-full text-[13px]">
                      <thead>
                        <tr className="border-b border-border bg-bg-elevated/30">
                          <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider w-20">状态</th>
                          <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider w-40">测试项</th>
                          <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">SQL</th>
                          <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider w-20">期望</th>
                          <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider w-48">实际</th>
                          <th className="px-4 py-3 text-right text-[11px] font-semibold text-text-muted uppercase tracking-wider w-20">耗时</th>
                        </tr>
                      </thead>
                      <tbody>
                        {groupResults.map((r, i) => (
                          <tr key={`${r.name}-${i}`} className={`border-b border-border-subtle last:border-0 ${i % 2 === 0 ? 'bg-white' : 'bg-bg-elevated/20'} hover:bg-bg-hover/50 transition-colors`}>
                            <td className="px-4 py-3">
                              <Badge variant={r.status === 'PASS' ? 'pass' : 'fail'}>{r.status}</Badge>
                            </td>
                            <td className="px-4 py-3 text-text-primary text-[12px] font-medium font-mono">{r.name}</td>
                            <td className="px-4 py-3 font-mono text-[12px] text-text-secondary" title={r.sql}>
                              <span className="block truncate max-w-md">{r.sql}</span>
                            </td>
                            <td className="px-4 py-3 font-mono text-[12px] text-text-muted">{r.expected}</td>
                            <td className="px-4 py-3 font-mono text-[12px] text-text-secondary" title={r.actual}>
                              <span className="block truncate max-w-sm">{r.actual}</span>
                            </td>
                            <td className="px-4 py-3 font-mono text-[12px] text-text-muted text-right">{r.timing}ms</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
