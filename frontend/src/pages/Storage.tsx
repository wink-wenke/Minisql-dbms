import { useEffect, useState, useCallback } from 'react';
import { api } from '../api/client';
import type { StatsResponse } from '../api/types';

export default function Storage() {
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const s = await api.stats();
      setStats(s);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 3000);
    return () => clearInterval(interval);
  }, [refresh]);

  const cache = stats?.cache;

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-2 border-b border-border bg-bg-surface">
        <div className="flex items-center gap-3">
          <span className="text-green font-mono text-sm">▦</span>
          <span className="text-sm font-semibold">存储检查器</span>
        </div>
        <button
          onClick={refresh}
          disabled={loading}
          className="px-3 py-1.5 border border-border rounded text-xs text-text-secondary hover:text-text-primary hover:border-text-muted transition-colors"
        >
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
      </div>

      <div className="flex-1 overflow-auto p-4 space-y-4">
        {/* Cache Statistics */}
        <div className="grid grid-cols-5 gap-3">
          <StatCard label="Access" value={cache?.accessCount ?? 0} color="text-text-primary" />
          <StatCard label="Hits" value={cache?.hitCount ?? 0} color="text-green" />
          <StatCard label="Misses" value={cache?.missCount ?? 0} color="text-red" />
          <StatCard label="Evictions" value={cache?.evictionCount ?? 0} color="text-yellow" />
          <StatCard label="Hit Rate" value={cache ? `${(cache.hitRate * 100).toFixed(1)}%` : '—'} color="text-accent" />
        </div>

        {/* Hit Rate Bar */}
        {cache && (
          <div className="border border-border rounded-lg p-4 bg-bg-surface">
            <div className="text-xs text-text-muted font-mono mb-2">缓存命中率</div>
            <div className="w-full h-4 bg-bg-primary rounded-full overflow-hidden">
              <div
                className="h-full bg-green rounded-full transition-all duration-500"
                style={{ width: `${cache.hitRate * 100}%` }}
              />
            </div>
            <div className="flex justify-between mt-1 text-xs text-text-muted font-mono">
              <span>0%</span>
              <span>{(cache.hitRate * 100).toFixed(1)}%</span>
              <span>100%</span>
            </div>
          </div>
        )}

        {/* Buffer Pool Info */}
        <div className="grid grid-cols-2 gap-4">
          <div className="border border-border rounded-lg p-4 bg-bg-surface">
            <div className="text-xs text-text-muted font-mono mb-3">缓冲池</div>
            <div className="space-y-2">
              <div className="flex justify-between">
                <span className="text-sm text-text-secondary">缓冲池大小</span>
                <span className="text-sm font-mono text-text-primary">{stats?.bufferSize ?? '—'} slots</span>
              </div>
              <div className="flex justify-between">
                <span className="text-sm text-text-secondary">页面大小</span>
                <span className="text-sm font-mono text-text-primary">{stats?.pageSize ?? '—'} bytes</span>
              </div>
            </div>
          </div>

          <div className="border border-border rounded-lg p-4 bg-bg-surface">
            <div className="text-xs text-text-muted font-mono mb-3">数据表</div>
            {stats && stats.tables.length > 0 ? (
              <div className="space-y-1">
                {stats.tables.map((t) => (
                  <div key={t} className="flex items-center gap-2 text-sm">
                    <span className="w-2 h-2 rounded-full bg-green" />
                    <span className="font-mono text-text-primary">{t}</span>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-sm text-text-muted">暂无数据表</div>
            )}
          </div>
        </div>

        {/* Buffer Visualization */}
        {stats && (
          <div className="border border-border rounded-lg p-4 bg-bg-surface">
            <div className="text-xs text-text-muted font-mono mb-3">缓冲池可视化</div>
            <div className="grid grid-cols-8 gap-2">
              {Array.from({ length: stats.bufferSize }, (_, i) => {
                const used = i < (cache?.accessCount ?? 0) % stats.bufferSize;
                return (
                  <div
                    key={i}
                    className={`aspect-square rounded border flex items-center justify-center text-xs font-mono ${
                      used
                        ? 'border-green/30 bg-green/10 text-green'
                        : 'border-border bg-bg-primary text-text-muted'
                    }`}
                  >
                    {i}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value, color }: { label: string; value: string | number; color: string }) {
  return (
    <div className="border border-border rounded-lg p-3 bg-bg-surface">
      <div className="text-xs text-text-muted font-mono mb-1">{label}</div>
      <div className={`text-xl font-bold font-mono ${color}`}>{value}</div>
    </div>
  );
}
