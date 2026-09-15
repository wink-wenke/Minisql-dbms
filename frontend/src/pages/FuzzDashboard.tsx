import { useState, useCallback, useRef } from 'react';
import { api } from '../api/client';
import Button from '../components/Button';
import Badge from '../components/Badge';
import EmptyState from '../components/EmptyState';

interface FuzzEntry {
  sql: string;
  result: 'OK' | 'ERROR' | 'CRASH';
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

function randomStr(len: number): string {
  const chars = 'abcdefghijklmnopqrstuvwxyz';
  return Array.from({ length: len }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
}

function randomSql(): string {
  const template = SEEDS[Math.floor(Math.random() * SEEDS.length)];
  const mutations: (() => string)[] = [
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
    () => "SELECT * FROM student WHERE age IN (" + randomStr(3) + ");",
    () => "SELECT * FROM student UNION SELECT * FROM " + randomStr(4),
    () => "/* comment */ SELECT * FROM student;",
    () => "SELECT * FROM student -- comment\nWHERE id = 1;",
    () => "SELECT * FROM student WHERE name = '" + "'".repeat(5) + "';",
    () => "SELECT * FROM student WHERE age BETWEEN " + Math.random() + " AND " + Math.random() + ";",
    () => "SELECT COUNT(*) FROM student GROUP BY " + randomStr(3) + ";",
  ];
  return mutations[Math.floor(Math.random() * mutations.length)]();
}

export default function FuzzDashboard() {
  const [entries, setEntries] = useState<FuzzEntry[]>([]);
  const [running, setRunning] = useState(false);
  const [count, setCount] = useState(50);
  const abortRef = useRef(false);

  const runFuzz = useCallback(async () => {
    setRunning(true);
    setEntries([]);
    abortRef.current = false;
    const results: FuzzEntry[] = [];

    for (let i = 0; i < count && !abortRef.current; i++) {
      const sql = randomSql();
      try {
        const r = await api.execute(sql);
        if (r.error) {
          results.push({ sql, result: 'ERROR', message: r.error.message });
        } else {
          results.push({ sql, result: 'OK', message: r.type === 'QUERY' ? `${r.rows?.length ?? 0} rows` : 'OK' });
        }
      } catch (e) {
        results.push({ sql, result: 'CRASH', message: `CRASH: ${e}` });
      }
      setEntries([...results]);
    }
    setRunning(false);
  }, [count]);

  const stopFuzz = () => { abortRef.current = true; };

  const crashes = entries.filter((e) => e.result === 'CRASH').length;
  const ok = entries.filter((e) => e.result === 'OK').length;
  const errors = entries.filter((e) => e.result === 'ERROR').length;

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-bg-surface">
        <div className="flex items-center gap-3">
          <span className="text-yellow font-mono text-sm">⚡</span>
          <span className="text-sm font-semibold">Fuzz Testing</span>
          {entries.length > 0 && (
            <div className="flex items-center gap-2 ml-2">
              <Badge variant="pass">{ok} OK</Badge>
              <Badge variant="fail">{errors} Error</Badge>
              {crashes > 0 && <Badge variant="warn">{crashes} Crash</Badge>}
            </div>
          )}
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <label className="text-xs text-text-secondary">Iterations:</label>
            <input
              type="number"
              value={count}
              onChange={(e) => setCount(Math.max(1, Math.min(500, +e.target.value)))}
              disabled={running}
              className="w-16 bg-bg-primary text-text-primary text-xs font-mono px-2 py-1 rounded border border-border focus:border-accent focus:outline-none disabled:opacity-50"
            />
          </div>
          {running ? (
            <Button variant="danger" onClick={stopFuzz}>Stop</Button>
          ) : (
            <Button onClick={runFuzz}>Run Fuzz</Button>
          )}
        </div>
      </div>

      {/* Progress */}
      {running && (
        <div className="px-4 py-1.5 border-b border-border bg-bg-surface">
          <div className="w-full h-1 bg-bg-elevated rounded-full overflow-hidden">
            <div
              className="h-full bg-accent rounded-full transition-all duration-200"
              style={{ width: `${(entries.length / count) * 100}%` }}
            />
          </div>
        </div>
      )}

      {/* Log */}
      <div className="flex-1 overflow-auto p-4">
        {entries.length === 0 && !running ? (
          <EmptyState icon="⚡" title="No fuzz tests run yet" description="Click 'Run Fuzz' to generate and test random SQL inputs" />
        ) : (
          <div className="space-y-0.5 font-mono text-xs">
            {entries.map((e, i) => (
              <div
                key={i}
                className={`flex gap-2 py-0.5 ${
                  e.result === 'CRASH' ? 'text-yellow' : e.result === 'ERROR' ? 'text-red' : 'text-text-muted'
                }`}
              >
                <span className="w-8 text-right text-text-muted">{i + 1}</span>
                <span className={`w-10 ${e.result === 'OK' ? 'text-green' : ''}`}>
                  {e.result === 'OK' ? 'PASS' : e.result === 'CRASH' ? 'CRASH' : 'FAIL'}
                </span>
                <span className="truncate flex-1">{e.sql || '(empty)'}</span>
                <span className="text-text-muted truncate max-w-xs">{e.message}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
