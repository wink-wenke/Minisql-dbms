import { useState } from 'react';
import { api } from '../api/client';

interface FuzzEntry {
  sql: string;
  result: 'OK' | 'ERROR';
  message: string;
}

const SEEDS = [
  "SELECT * FROM student;",
  "SELECT id, name FROM student WHERE age > 18;",
  "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);",
  "DELETE FROM student WHERE id = 1;",
  "CREATE TABLE test(id INT, name VARCHAR(50));",
  "SELECT * FROM student WHERE age > 18 AND id != 3;",
  "SELECT * FROM student WHERE NOT (age < 20);",
  "SELECT * FROM student WHERE age >= 18 OR id = 1;",
];

function randomSql(): string {
  const template = SEEDS[Math.floor(Math.random() * SEEDS.length)];
  const mutations = [
    () => template,
    () => template.replace(/SELECT/, 'SELCT'),
    () => template.replace(/FROM/, 'FRPM'),
    () => template + " ORR",
    () => "SELECT * FROM " + randomStr(5),
    () => "INSERT INTO student VALUES (" + Math.random() + ");",
    () => template.replace(/WHERE/, 'WERE'),
    () => "SELECT * FROM student WHERE ;",
    () => "",
    () => "!!!###$$$",
  ];
  return mutations[Math.floor(Math.random() * mutations.length)]();
}

function randomStr(len: number): string {
  const chars = 'abcdefghijklmnopqrstuvwxyz';
  return Array.from({ length: len }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
}

export default function FuzzDashboard() {
  const [entries, setEntries] = useState<FuzzEntry[]>([]);
  const [running, setRunning] = useState(false);
  const [count, setCount] = useState(50);

  const runFuzz = async () => {
    setRunning(true);
    setEntries([]);
    const results: FuzzEntry[] = [];

    // Note: Fuzz testing may create temporary tables
    // Database state will be cleaned on server restart

    for (let i = 0; i < count; i++) {
      const sql = randomSql();
      try {
        const r = await api.execute(sql);
        if (r.error) {
          results.push({ sql, result: 'ERROR', message: r.error.message });
        } else {
          results.push({ sql, result: 'OK', message: r.type === 'QUERY' ? `${r.rows?.length ?? 0} rows` : 'OK' });
        }
      } catch (e) {
        results.push({ sql, result: 'ERROR', message: `CRASH: ${e}` });
      }
      setEntries([...results]);
    }
    setRunning(false);
  };

  const crashes = entries.filter((e) => e.message.startsWith('CRASH')).length;
  const ok = entries.filter((e) => e.result === 'OK').length;
  const errors = entries.filter((e) => e.result === 'ERROR' && !e.message.startsWith('CRASH')).length;

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-bg-surface">
        <div className="flex items-center gap-3">
          <span className="text-yellow font-mono text-sm">⚡</span>
          <span className="text-sm font-semibold">Fuzz Testing</span>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <label className="text-xs text-text-secondary">Iterations:</label>
            <input
              type="number"
              value={count}
              onChange={(e) => setCount(Math.max(1, Math.min(500, +e.target.value)))}
              className="w-16 bg-bg-primary text-text-primary text-xs font-mono px-2 py-1 rounded border border-border focus:border-accent focus:outline-none"
            />
          </div>
          <button
            onClick={runFuzz}
            disabled={running}
            className="px-3 py-1.5 bg-accent text-bg-primary rounded text-xs font-medium hover:bg-accent-hover transition-colors disabled:opacity-50"
          >
            {running ? `Running... (${entries.length}/${count})` : 'Run Fuzz'}
          </button>
        </div>
      </div>

      {/* Stats */}
      <div className="flex gap-4 px-4 py-3 border-b border-border bg-bg-surface">
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-green" />
          <span className="text-xs font-mono text-text-secondary">OK: <span className="text-green">{ok}</span></span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-red" />
          <span className="text-xs font-mono text-text-secondary">Error: <span className="text-red">{errors}</span></span>
        </div>
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-yellow" />
          <span className="text-xs font-mono text-text-secondary">Crash: <span className="text-yellow">{crashes}</span></span>
        </div>
        {entries.length > 0 && (
          <span className="text-xs font-mono text-text-muted">
            Crash rate: {((crashes / entries.length) * 100).toFixed(1)}%
          </span>
        )}
      </div>

      {/* Log */}
      <div className="flex-1 overflow-auto p-4">
        <div className="space-y-1 font-mono text-xs">
          {entries.map((e, i) => (
            <div key={i} className={`flex gap-2 py-0.5 ${e.message.startsWith('CRASH') ? 'text-yellow' : e.result === 'ERROR' ? 'text-red' : 'text-text-muted'}`}>
              <span className="w-8 text-right text-text-muted">{i + 1}</span>
              <span className={e.result === 'OK' ? 'text-green' : ''}>{e.result === 'OK' ? 'PASS' : 'FAIL'}</span>
              <span className="truncate flex-1">{e.sql}</span>
              <span className="text-text-muted truncate max-w-xs">{e.message}</span>
            </div>
          ))}
          {entries.length === 0 && !running && (
            <div className="text-center text-text-muted py-20">
              Click "Run Fuzz" to generate and test random SQL inputs
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
