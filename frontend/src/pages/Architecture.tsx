import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Card from '../components/Card';
import Skeleton from '../components/Skeleton';
import { api } from '../api/client';
import type { StatsResponse } from '../api/types';

/* ------------------------------------------------------------------ */
/*  Layer definitions                                                  */
/* ------------------------------------------------------------------ */

interface Layer {
  id: string;
  label: string;
  name: string;
  desc: string;
  interfaces: string[];
  borderColor: string;
  link?: string;
  linkLabel?: string;
}

const layers: Layer[] = [
  {
    id: 'L0',
    label: 'HTTP API / Frontend',
    name: 'HTTP API / Frontend',
    desc: 'REST API endpoints for SQL execution',
    interfaces: ['GET /api/execute', 'POST /api/explain'],
    borderColor: 'border-accent',
    link: '/playground',
    linkLabel: 'Playground',
  },
  {
    id: 'L1',
    label: 'SQL Compiler',
    name: 'SQL Compiler',
    desc: 'Lexer → Parser → AST → Semantic → Logical Plan',
    interfaces: ['Lexer.tokenize()', 'Parser.query()', 'SemanticAnalyzer.analyze()'],
    borderColor: 'border-blue',
    link: '/pipeline',
    linkLabel: 'Pipeline',
  },
  {
    id: 'L2',
    label: 'Execution Engine',
    name: 'Execution Engine',
    desc: 'Plan → Scan → Execute → Result',
    interfaces: ['Executor.execute()', 'PlanConverter.convert()'],
    borderColor: 'border-green',
    link: '/playground',
    linkLabel: 'Playground',
  },
  {
    id: 'L3',
    label: 'Storage Engine',
    name: 'Storage Engine',
    desc: 'Page → Buffer → File → Disk',
    interfaces: ['PageManager.read/write()', 'BufferManager.getPage()'],
    borderColor: 'border-yellow',
    link: '/storage',
    linkLabel: 'Storage',
  },
  {
    id: 'L4',
    label: 'Persistent Disk',
    name: 'Persistent Disk',
    desc: '4KB Pages · WAL Log · ARIES Recovery',
    interfaces: [],
    borderColor: 'border-text-muted',
  },
];

/* ------------------------------------------------------------------ */
/*  Stat card helper                                                   */
/* ------------------------------------------------------------------ */

function StatItem({
  label,
  value,
  color,
  loading,
}: {
  label: string;
  value?: string;
  color?: string;
  loading?: boolean;
}) {
  return (
    <div>
      <div className="text-xs text-text-secondary">{label}</div>
      {loading ? (
        <Skeleton className="!h-6 !w-16 mt-1" />
      ) : (
        <div className={`text-lg font-bold font-mono mt-0.5 ${color ?? 'text-text-primary'}`}>
          {value ?? '—'}
        </div>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Architecture page                                                  */
/* ------------------------------------------------------------------ */

export default function Architecture() {
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    api
      .stats()
      .then(setStats)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="flex-1 overflow-auto p-6">
      {/* -------- Header -------- */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-text-primary mb-1">System Architecture</h1>
        <p className="text-text-secondary text-sm">
          MiniSQL DBMS &mdash; A complete SQL pipeline from text to disk
        </p>
      </div>

      <div className="flex gap-6 items-start">
        {/* -------- Main column: layers -------- */}
        <div className="flex-1 flex flex-col items-center max-w-3xl mx-auto w-full">
          {layers.map((layer, i) => (
            <div key={layer.id} className="flex flex-col items-center w-full">
              {/* SVG arrow between cards */}
              {i > 0 && (
                <svg
                  width="32"
                  height="36"
                  viewBox="0 0 32 36"
                  fill="none"
                  xmlns="http://www.w3.org/2000/svg"
                  className="my-1 shrink-0"
                >
                  <line x1="16" y1="0" x2="16" y2="26" stroke="#484f58" strokeWidth="1.5" />
                  <polygon points="10,24 16,34 22,24" fill="#484f58" />
                </svg>
              )}

              {/* Layer card */}
              <div
                className={`
                  w-full border-l-4 ${layer.borderColor}
                  border border-border rounded-lg
                  bg-bg-surface
                  p-4 pl-5
                  transition-all duration-200
                  ${layer.link ? 'hover:bg-bg-elevated hover:shadow-lg hover:shadow-black/20 cursor-pointer group' : ''}
                `}
                onClick={layer.link ? () => navigate(layer.link!) : undefined}
              >
                <div className="flex items-start justify-between gap-4">
                  {/* Left content */}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1.5">
                      <span className="inline-flex items-center justify-center px-2 py-0.5 rounded text-xs font-mono font-bold bg-bg-elevated text-text-muted border border-border-subtle">
                        {layer.id}
                      </span>
                      <span className="text-sm font-bold text-text-primary">{layer.name}</span>
                    </div>
                    <p className="text-xs text-text-secondary mb-2.5">{layer.desc}</p>
                    {layer.interfaces.length > 0 && (
                      <div className="flex flex-wrap gap-1.5">
                        {layer.interfaces.map((iface) => (
                          <span
                            key={iface}
                            className="px-2 py-0.5 rounded bg-bg-primary text-text-secondary text-xs font-mono border border-border-subtle"
                          >
                            {iface}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Link button */}
                  {layer.link && (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        navigate(layer.link!);
                      }}
                      className="
                        shrink-0 mt-0.5
                        px-3 py-1.5 rounded
                        bg-accent/10 text-accent
                        text-xs font-medium
                        border border-accent/20
                        opacity-0 group-hover:opacity-100
                        transition-opacity duration-200
                        hover:bg-accent hover:text-bg-primary
                      "
                    >
                      {layer.linkLabel} &rarr;
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* -------- Right sidebar: system stats -------- */}
        <div className="w-64 shrink-0 space-y-4">
          <Card title="System Status" className="sticky top-0">
            <div className="space-y-4">
              {/* Tables */}
              <StatItem
                label="Tables"
                value={stats?.tables ? String(Array.isArray(stats.tables) ? stats.tables.length : stats.tables.tables?.length ?? 0) : undefined}
                color="text-accent"
                loading={loading}
              />

              {/* Cache hit rate */}
              <div>
                <div className="text-xs text-text-secondary">Cache Hit Rate</div>
                {loading ? (
                  <Skeleton className="!h-6 !w-16 mt-1" />
                ) : (
                  <>
                    <div className="text-lg font-bold font-mono text-green mt-0.5">
                      {stats ? `${(stats.cache.hitRate * 100).toFixed(1)}%` : '—'}
                    </div>
                    {/* Progress bar */}
                    <div className="w-full h-1.5 bg-bg-elevated rounded-full mt-1.5 overflow-hidden">
                      <div
                        className="h-full bg-green rounded-full transition-all duration-700 ease-out"
                        style={{ width: stats ? `${stats.cache.hitRate * 100}%` : '0%' }}
                      />
                    </div>
                    {stats && (
                      <div className="flex justify-between mt-1 text-[10px] text-text-muted font-mono">
                        <span>{stats.cache.hitCount} hits</span>
                        <span>{stats.cache.missCount} misses</span>
                      </div>
                    )}
                  </>
                )}
              </div>

              {/* Buffer pool size */}
              <StatItem
                label="Buffer Pool Size"
                value={stats?.bufferSize !== undefined ? String(stats.bufferSize) : undefined}
                color="text-blue"
                loading={loading}
              />

              {/* Page size */}
              <StatItem
                label="Page Size"
                value={stats?.pageSize !== undefined ? `${stats.pageSize} B` : undefined}
                color="text-text-primary"
                loading={loading}
              />
            </div>
          </Card>

          {/* Data flow card */}
          <Card title="Data Flow">
            <div className="text-xs text-text-secondary font-mono space-y-1">
              <div className="flex items-center gap-2">
                <span className="text-accent">SQL</span>
                <span className="text-text-muted">→</span>
                <span>Token</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-blue">Token</span>
                <span className="text-text-muted">→</span>
                <span>AST</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-blue">AST</span>
                <span className="text-text-muted">→</span>
                <span>Logical Plan</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-green">Plan</span>
                <span className="text-text-muted">→</span>
                <span>Physical Plan</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-green">Execute</span>
                <span className="text-text-muted">→</span>
                <span>Pages</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-yellow">Pages</span>
                <span className="text-text-muted">→</span>
                <span>Disk</span>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
