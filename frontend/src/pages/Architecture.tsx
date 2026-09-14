import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { StatsResponse } from '../api/types';

const layers = [
  {
    name: 'SQL Compiler',
    color: 'border-accent',
    bg: 'bg-accent/5',
    desc: 'Lexer → Parser → AST → Semantic → Logical Plan',
    interfaces: ['Lexer.tokenize()', 'Parser.query()', 'SemanticAnalyzer.analyze()', 'Planner.createQueryPlan()'],
  },
  {
    name: 'Execution Engine',
    color: 'border-blue',
    bg: 'bg-blue/5',
    desc: 'Plan → Scan → Execute → Result',
    interfaces: ['Executor.execute()', 'PlanConverter.convert()', 'CatalogReader / CatalogWriter'],
  },
  {
    name: 'Storage Engine',
    color: 'border-green',
    bg: 'bg-green/5',
    desc: 'Page → Buffer → File → Disk',
    interfaces: ['PageManager.read/write()', 'BufferManager.getPage()', 'FileManager'],
  },
];

export default function Architecture() {
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    api.stats().then(setStats).catch(() => {});
  }, []);

  return (
    <div className="flex-1 overflow-auto p-6">
      {/* Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-text-primary mb-1">系统架构</h1>
        <p className="text-text-secondary text-sm">
          MiniSQL DBMS — A complete SQL pipeline from text to disk
        </p>
      </div>

      <div className="flex gap-6">
        {/* Architecture Diagram */}
        <div className="flex-1 space-y-3">
          {/* Top Layer */}
          <div className="border border-border rounded-lg p-4 bg-bg-surface">
            <div className="text-xs text-text-muted font-mono mb-1">第 0 层 — 接口层</div>
            <div className="flex items-center justify-between">
              <div>
                <div className="text-sm font-semibold text-text-primary">HTTP 接口 / 前端</div>
                <div className="text-xs text-text-secondary mt-1">提供 SQL 执行能力的 REST 接口</div>
              </div>
              <button
                onClick={() => navigate('/playground')}
                className="px-3 py-1.5 bg-accent text-bg-primary rounded text-xs font-medium hover:bg-accent-hover transition-colors"
              >
                Open Playground →
              </button>
            </div>
          </div>

          {/* Arrow */}
          <div className="flex justify-center text-text-muted text-xs font-mono">↓ SQL Input ↓</div>

          {/* Middle Layers */}
          {layers.map((layer) => (
            <div
              key={layer.name}
              className={`border ${layer.color} rounded-lg p-4 ${layer.bg}`}
            >
              <div className="text-xs text-text-muted font-mono mb-1">层</div>
              <div className="text-sm font-semibold text-text-primary mb-1">{layer.name}</div>
              <div className="text-xs text-text-secondary mb-3">{layer.desc}</div>
              <div className="flex flex-wrap gap-2">
                {layer.interfaces.map((iface) => (
                  <span key={iface} className="px-2 py-0.5 rounded bg-bg-primary text-text-secondary text-xs font-mono">
                    {iface}
                  </span>
                ))}
              </div>
            </div>
          ))}

          {/* Bottom Layer */}
          <div className="flex justify-center text-text-muted text-xs font-mono">↓ Page I/O ↓</div>
          <div className="border border-border rounded-lg p-4 bg-bg-surface">
            <div className="text-xs text-text-muted font-mono mb-1">第 3 层 — 持久化层</div>
            <div className="text-sm font-semibold text-text-primary">持久化磁盘</div>
            <div className="text-xs text-text-secondary mt-1">4KB pages · WAL log · ARIES recovery</div>
          </div>
        </div>

        {/* Stats Panel */}
        <div className="w-64 space-y-3">
          <div className="border border-border rounded-lg p-4 bg-bg-surface">
            <div className="text-xs text-text-muted font-mono mb-3">系统状态</div>
            <div className="space-y-3">
              <div>
                <div className="text-xs text-text-secondary">数据表</div>
                <div className="text-lg font-bold text-accent font-mono">
                  {stats?.tables.length ?? '—'}
                </div>
              </div>
              <div>
                <div className="text-xs text-text-secondary">缓存命中率</div>
                <div className="text-lg font-bold text-green font-mono">
                  {stats ? `${(stats.cache.hitRate * 100).toFixed(1)}%` : '—'}
                </div>
              </div>
              <div>
                <div className="text-xs text-text-secondary">缓冲池大小</div>
                <div className="text-lg font-bold text-blue font-mono">
                  {stats?.bufferSize ?? '—'}
                </div>
              </div>
              <div>
                <div className="text-xs text-text-secondary">页面大小</div>
                <div className="text-lg font-bold text-text-primary font-mono">
                  {stats ? `${stats.pageSize} B` : '—'}
                </div>
              </div>
            </div>
          </div>

          {stats && stats.tables.length > 0 && (
            <div className="border border-border rounded-lg p-4 bg-bg-surface">
              <div className="text-xs text-text-muted font-mono mb-2">数据表</div>
              <div className="space-y-1">
                {stats.tables.map((t) => (
                  <div key={t} className="text-sm text-text-secondary font-mono px-2 py-1 rounded bg-bg-primary">
                    {t}
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="border border-border rounded-lg p-4 bg-bg-surface">
            <div className="text-xs text-text-muted font-mono mb-2">数据流</div>
            <div className="text-xs text-text-secondary font-mono space-y-1">
              <div>SQL → 词法单元</div>
              <div>词法单元 → 抽象语法树</div>
              <div>抽象语法树 → 逻辑计划</div>
              <div>计划 → 物理计划</div>
              <div>计划 → 执行</div>
              <div>执行 → 页面</div>
              <div>页面 → 磁盘</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
