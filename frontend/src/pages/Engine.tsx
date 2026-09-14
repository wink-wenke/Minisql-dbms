import { useState } from 'react';
import { api } from '../api/client';
import type { EngineResponse } from '../api/types';

/**
 * 引擎通道页面。
 *
 * 与 Playground 的区别：Playground 走 /api/execute（SimpleDB 原生 Planner），
 * 本页走 /api/engine/execute，链路是 SQL -> LogicalPlan -> Executor，
 * 由成员B的引擎模块实际执行，可演示 UPDATE / JOIN / ORDER BY / GROUP BY。
 */

const EXAMPLES: { label: string; sql: string }[] = [
  {
    label: '建表',
    sql: 'CREATE TABLE student (sid INT, sname VARCHAR(12), majorid INT, score INT)',
  },
  {
    label: '插入',
    sql: "INSERT INTO student (sid, sname, majorid, score) VALUES (101, 'Alice', 1, 90)",
  },
  { label: '全表扫描', sql: 'SELECT * FROM student' },
  { label: 'WHERE 过滤', sql: 'SELECT sname, score FROM student WHERE score > 75' },
  { label: 'ORDER BY', sql: 'SELECT score FROM student ORDER BY score' },
  {
    label: 'GROUP BY 聚合',
    sql: 'SELECT majorid, COUNT(sid), SUM(score), AVG(score), MIN(score), MAX(score) FROM student GROUP BY majorid',
  },
  {
    label: 'JOIN',
    sql: 'SELECT sname, dname FROM student JOIN department ON student.majorid = department.deptid',
  },
  { label: 'UPDATE', sql: 'UPDATE student SET score = 100 WHERE sid = 101' },
  { label: 'DELETE', sql: 'DELETE FROM student WHERE sid = 104' },
];

const SEED = [
  'CREATE TABLE student (sid INT, sname VARCHAR(12), majorid INT, score INT)',
  'CREATE TABLE department (deptid INT, dname VARCHAR(12))',
  "INSERT INTO department (deptid, dname) VALUES (1, 'CS')",
  "INSERT INTO department (deptid, dname) VALUES (2, 'Math')",
  "INSERT INTO student (sid, sname, majorid, score) VALUES (101, 'Alice', 1, 90)",
  "INSERT INTO student (sid, sname, majorid, score) VALUES (102, 'Bob', 1, 80)",
  "INSERT INTO student (sid, sname, majorid, score) VALUES (103, 'Carol', 2, 70)",
  "INSERT INTO student (sid, sname, majorid, score) VALUES (104, 'Dave', 2, 60)",
];

export default function Engine() {
  const [sql, setSql] = useState(EXAMPLES[2].sql);
  const [result, setResult] = useState<EngineResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [seedMsg, setSeedMsg] = useState('');

  async function run() {
    if (!sql.trim()) return;
    setLoading(true);
    setSeedMsg('');
    try {
      const r = await api.engineExecute(sql);
      setResult(r);
    } catch (e) {
      setResult({
        success: false,
        type: 'UPDATE',
        error: { type: 'CLIENT', message: String(e) },
      });
    } finally {
      setLoading(false);
    }
  }

  /** 初始化演示数据：逐条执行，已存在的表报错则跳过 */
  async function seed() {
    setLoading(true);
    setSeedMsg('');
    let ok = 0;
    let fail = 0;
    try {
      for (const s of SEED) {
        const r = await api.engineExecute(s);
        if (r.success) ok++;
        else fail++;
      }
      setSeedMsg(`演示数据初始化完成：成功 ${ok} 条，跳过/失败 ${fail} 条（表已存在会跳过）`);
    } catch (e) {
      setSeedMsg('初始化失败：' + String(e));
    } finally {
      setLoading(false);
    }
  }

  const err = result?.error;
  const isQuery = result?.type === 'QUERY';
  const rows = result?.rows ?? [];

  return (
    <div className="h-full overflow-auto p-6">
      {/* 标题 */}
      <div className="mb-5">
        <div className="flex items-center gap-3">
          <h1 className="text-xl font-semibold text-text-primary">引擎通道</h1>
          <span className="px-2 py-0.5 rounded text-xs font-mono bg-accent/15 text-accent border border-accent/30">
            memberB · LogicalPlan → Executor
          </span>
        </div>
        <p className="mt-1 text-sm text-text-secondary">
          这里的 SQL 由引擎模块（Executor）实际执行，可演示 UPDATE / JOIN / ORDER BY / GROUP BY 聚合。
          语法为教学子集，不支持嵌套子查询。
        </p>
      </div>

      {/* 示例 + 初始化 */}
      <div className="mb-4 flex flex-wrap items-center gap-2">
        {EXAMPLES.map((ex) => (
          <button
            key={ex.label}
            onClick={() => setSql(ex.sql)}
            className="px-2.5 py-1 rounded text-xs border border-border bg-bg-surface text-text-secondary hover:text-text-primary hover:border-accent/50 transition-colors"
          >
            {ex.label}
          </button>
        ))}
        <button
          onClick={seed}
          disabled={loading}
          className="ml-auto px-3 py-1 rounded text-xs border border-green/40 bg-green/10 text-green hover:bg-green/20 transition-colors disabled:opacity-50"
        >
          初始化演示数据
        </button>
      </div>
      {seedMsg && (
        <div className="mb-3 px-3 py-2 rounded text-xs bg-bg-elevated border border-border text-text-secondary">
          {seedMsg}
        </div>
      )}

      {/* 输入区 */}
      <div className="mb-4 rounded-lg border border-border bg-bg-surface overflow-hidden">
        <div className="flex items-center justify-between px-3 py-2 border-b border-border bg-bg-elevated">
          <span className="text-xs text-text-secondary font-mono">SQL</span>
          <button
            onClick={run}
            disabled={loading}
            className="px-3 py-1 rounded text-xs font-medium bg-accent text-bg-primary hover:bg-accent-hover transition-colors disabled:opacity-50"
          >
            {loading ? '执行中…' : '执行 (Ctrl+Enter)'}
          </button>
        </div>
        <textarea
          value={sql}
          onChange={(e) => setSql(e.target.value)}
          onKeyDown={(e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') run();
          }}
          rows={4}
          spellCheck={false}
          className="w-full bg-transparent px-3 py-3 text-sm font-mono text-text-primary outline-none resize-y"
          placeholder="SELECT * FROM student"
        />
      </div>

      {/* 结果区 */}
      {err && (
        <div className="mb-4 rounded-lg border border-red/40 bg-red/10 px-4 py-3">
          <div className="text-sm font-medium text-red">
            {err.type === 'CLIENT' ? '请求失败' : '执行错误'}
          </div>
          <div className="mt-1 text-xs font-mono text-red/90">{err.message}</div>
        </div>
      )}

      {result?.success && (
        <div className="space-y-4">
          {/* 概要 */}
          <div className="flex flex-wrap items-center gap-4 rounded-lg border border-border bg-bg-surface px-4 py-3 text-xs">
            <span className="text-text-secondary">
              类型 <span className="text-text-primary font-mono">{result.type}</span>
            </span>
            <span className="text-text-secondary">
              耗时 <span className="text-text-primary font-mono">{result.timing} ms</span>
            </span>
            {isQuery ? (
              <span className="text-text-secondary">
                行数 <span className="text-text-primary font-mono">{result.rowCount ?? rows.length}</span>
              </span>
            ) : (
              <span className="text-text-secondary">
                影响行数{' '}
                <span className="text-text-primary font-mono">{result.affectedRows}</span>
              </span>
            )}
            {result.engine && (
              <span className="ml-auto px-2 py-0.5 rounded font-mono bg-green/10 text-green border border-green/30">
                engine = {result.engine}
              </span>
            )}
          </div>

          {/* 结果表 */}
          {isQuery && (
            <div className="rounded-lg border border-border bg-bg-surface overflow-hidden">
              <div className="px-3 py-2 border-b border-border bg-bg-elevated text-xs text-text-secondary">
                查询结果
              </div>
              <div className="overflow-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-border">
                      {(result.columns ?? []).map((c) => (
                        <th
                          key={c}
                          className="px-3 py-2 text-left text-xs font-medium text-text-secondary font-mono"
                        >
                          {c}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.length === 0 && (
                      <tr>
                        <td
                          className="px-3 py-4 text-center text-xs text-text-muted"
                          colSpan={Math.max((result.columns ?? []).length, 1)}
                        >
                          没有数据
                        </td>
                      </tr>
                    )}
                    {rows.map((row, i) => (
                      <tr key={i} className="border-b border-border-subtle last:border-0">
                        {row.map((cell, j) => (
                          <td key={j} className="px-3 py-1.5 font-mono text-xs text-text-primary">
                            {String(cell)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* 逻辑计划 */}
          {result.planText && (
            <div className="rounded-lg border border-border bg-bg-surface overflow-hidden">
              <div className="px-3 py-2 border-b border-border bg-bg-elevated text-xs text-text-secondary">
                逻辑计划（LogicalPlan）
              </div>
              <pre className="px-3 py-3 text-xs font-mono text-blue whitespace-pre-wrap">
                {result.planText}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
