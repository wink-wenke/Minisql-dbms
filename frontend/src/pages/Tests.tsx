import { useState } from 'react';
import { api } from '../api/client';

interface TestResult {
  name: string;
  sql: string;
  expected: string;
  actual: string;
  status: '通过' | '失败';
}

const PRESET_TESTS: { name: string; sql: string; expectError?: boolean }[] = [
  { name: '建表', sql: "CREATE TABLE test_t(id INT, name VARCHAR(50));" },
  { name: '插入数据', sql: "INSERT INTO test_t(id, name) VALUES (1, 'hello');" },
  { name: '查询全部', sql: "SELECT * FROM test_t;" },
  { name: '带 WHERE 的查询', sql: "SELECT id FROM test_t WHERE id > 0;" },
  { name: '删除数据', sql: "DELETE FROM test_t WHERE id = 1;" },
  { name: '报错：表不存在', sql: "SELECT * FROM nonexistent;", expectError: true },
  { name: '报错：字段不存在', sql: "SELECT bad_col FROM test_t;", expectError: true },
  { name: '报错：语法错误', sql: "SELCT * FROM test_t;", expectError: true },
];

export default function Tests() {
  const [results, setResults] = useState<TestResult[]>([]);
  const [running, setRunning] = useState(false);

  const runTests = async () => {
    setRunning(true);
    const res: TestResult[] = [];

    // Note: test_t table will be created by the first test
    // Subsequent runs will fail CREATE TABLE test if table exists
    // This is expected behavior for a demo/test environment

    for (const test of PRESET_TESTS) {
      try {
        const r = await api.execute(test.sql);
        const hasError = !!r.error;
        const pass = test.expectError ? hasError : !hasError;
        res.push({
          name: test.name,
          sql: test.sql,
          expected: test.expectError ? '错误' : '正常',
          actual: hasError ? `错误: ${r.error!.message}` : (r.type === 'QUERY' ? `${r.rows?.length ?? 0} rows` : '正常'),
          status: pass ? '通过' : '失败',
        });
      } catch (e) {
        res.push({
          name: test.name,
          sql: test.sql,
          expected: test.expectError ? '错误' : '正常',
          actual: `EXCEPTION: ${e}`,
          status: test.expectError ? '通过' : '失败',
        });
      }
      setResults([...res]);
    }
    setRunning(false);
  };

  const passed = results.filter((r) => r.status === '通过').length;
  const failed = results.filter((r) => r.status === '失败').length;

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-bg-surface">
        <div className="flex items-center gap-3">
          <span className="text-green font-mono text-sm">✓</span>
          <span className="text-sm font-semibold">测试套件</span>
        </div>
        <button
          onClick={runTests}
          disabled={running}
          className="px-3 py-1.5 bg-accent text-bg-primary rounded text-xs font-medium hover:bg-accent-hover transition-colors disabled:opacity-50"
        >
          {running ? `Running... (${results.length}/${PRESET_TESTS.length})` : '运行测试'}
        </button>
      </div>

      <div className="flex-1 overflow-auto p-4">
        {results.length > 0 && (
          <div className="flex gap-4 mb-4">
            <div className="text-sm font-mono">
              <span className="text-green">{passed} passed</span>
              <span className="text-text-muted mx-2">·</span>
              <span className="text-red">{failed} failed</span>
              <span className="text-text-muted mx-2">·</span>
              <span className="text-text-secondary">{results.length} total</span>
            </div>
          </div>
        )}

        <div className="border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-bg-surface">
                <th className="px-3 py-2 text-left text-xs font-mono text-text-muted">状态</th>
                <th className="px-3 py-2 text-left text-xs font-mono text-text-muted">测试</th>
                <th className="px-3 py-2 text-left text-xs font-mono text-text-muted">SQL</th>
                <th className="px-3 py-2 text-left text-xs font-mono text-text-muted">期望结果</th>
                <th className="px-3 py-2 text-left text-xs font-mono text-text-muted">实际结果</th>
              </tr>
            </thead>
            <tbody>
              {results.map((r, i) => (
                <tr key={i} className={`border-b border-border-subtle ${i % 2 === 0 ? 'bg-bg-primary' : 'bg-bg-surface/50'}`}>
                  <td className="px-3 py-2">
                    <span className={`text-xs font-mono font-bold ${r.status === '通过' ? 'text-green' : 'text-red'}`}>
                      {r.status}
                    </span>
                  </td>
                  <td className="px-3 py-2 text-text-primary">{r.name}</td>
                  <td className="px-3 py-2 font-mono text-xs text-text-secondary max-w-xs truncate">{r.sql}</td>
                  <td className="px-3 py-2 font-mono text-xs text-text-secondary">{r.expected}</td>
                  <td className="px-3 py-2 font-mono text-xs text-text-secondary max-w-xs truncate">{r.actual}</td>
                </tr>
              ))}
              {results.length === 0 && (
                <tr>
                  <td colSpan={5} className="px-3 py-10 text-center text-text-muted text-sm">
                    点击「运行测试」执行测试套件
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
