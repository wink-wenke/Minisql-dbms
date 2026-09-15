import { useState, useCallback, useEffect, useRef } from 'react';
import { api } from '../api/client';
import type { StatsResponse, BufferSlotInfo } from '../api/types';
import Button from '../components/Button';
import Badge from '../components/Badge';
import Skeleton from '../components/Skeleton';

const POLL_MS = 3000;

export default function Storage() {
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [slots, setSlots] = useState<BufferSlotInfo[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [tableSchema, setTableSchema] = useState<{ name: string; type: string; length: number }[] | null>(null);
  const [schemaLoading, setSchemaLoading] = useState(false);
  const autoRefreshRef = useRef(autoRefresh);
  autoRefreshRef.current = autoRefresh;

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [s, sl] = await Promise.all([api.stats(), api.bufferSlots()]);
      setStats(s);
      setSlots(sl.slots);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!autoRefresh) return;
    refresh();
    const id = setInterval(() => { if (autoRefreshRef.current) refresh(); }, POLL_MS);
    return () => clearInterval(id);
  }, [autoRefresh, refresh]);

  useEffect(() => {
    if (!selectedTable) { setTableSchema(null); return; }
    setSchemaLoading(true);
    api.schema(selectedTable)
      .then((r) => setTableSchema(r.columns))
      .catch(() => setTableSchema(null))
      .finally(() => setSchemaLoading(false));
  }, [selectedTable]);

  const cache = stats?.cache;
  const tableList = Array.isArray(stats?.tables) ? stats.tables : stats?.tables?.tables ?? [];
  const usedFrames = slots.filter((s) => s.file !== null).length;
  const dirtyFrames = slots.filter((s) => s.dirty).length;
  const freeFrames = slots.length - usedFrames;
  const pinnedFrames = slots.filter((s) => s.pins > 0).length;
  const hitRate = cache ? (cache.hitRate * 100).toFixed(1) : '0.0';

  return (
    <div className="flex gap-4 h-full">
      {/* Left panel - table list */}
      <div className="w-60 flex-shrink-0 bg-white rounded-xl border border-border flex flex-col overflow-hidden">
        <div className="px-4 py-3 border-b border-border flex items-center justify-between">
          <span className="text-[12px] font-semibold text-text-primary">数据库</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setAutoRefresh((v) => !v)}
              className={`flex items-center gap-1 px-1.5 py-0.5 rounded-md text-[11px] font-mono transition-colors ${
                autoRefresh
                  ? 'text-green bg-green-muted'
                  : 'text-text-muted hover:text-text-secondary'
              }`}
            >
              <span className={`inline-block w-1.5 h-1.5 rounded-full ${autoRefresh ? 'bg-green' : 'bg-text-muted/40'}`} />
              自动
            </button>
            <Button variant="ghost" size="sm" onClick={refresh} disabled={loading}>
              刷新
            </Button>
          </div>
        </div>

        <div className="flex-1 overflow-auto py-1">
          {loading && !stats ? (
            <div className="px-4 space-y-2 py-2">
              {Array.from({ length: 3 }, (_, i) => (
                <div key={i}><Skeleton className="h-4 w-24" /></div>
              ))}
            </div>
          ) : tableList.length > 0 ? (
            <div>
              {tableList.map((t) => (
                <button
                  key={t}
                  onClick={() => setSelectedTable(selectedTable === t ? null : t)}
                  className={`w-full text-left px-4 py-2.5 text-[13px] font-mono flex items-center gap-2 transition-colors ${
                    selectedTable === t
                      ? 'bg-accent/10 text-accent font-medium'
                      : 'text-text-secondary hover:text-text-primary hover:bg-bg-hover'
                  }`}
                >
                  <span className="w-1.5 h-1.5 rounded-full bg-green flex-shrink-0" />
                  {t}
                </button>
              ))}
            </div>
          ) : (
            <div className="px-4 py-8 text-center text-[13px] text-text-muted">暂无表格</div>
          )}
        </div>
      </div>

      {/* Right panel - details */}
      <div className="flex-1 overflow-auto">
        {error && (
          <div className="mb-4 px-3 py-2 rounded-lg bg-red-muted text-[13px] text-red font-mono">{error}</div>
        )}

        <div className="space-y-5">
          {/* ========== 1. 页式存储管理 ========== */}
          {loading && !stats ? (
            <div className="grid grid-cols-4 gap-3">
              {Array.from({ length: 4 }, (_, i) => (
                <div key={i} className="px-4 py-3 bg-white rounded-xl border border-border">
                  <Skeleton className="h-3 w-16 mb-1.5" />
                  <Skeleton className="h-5 w-12" />
                </div>
              ))}
            </div>
          ) : stats ? (
            <div>
              <h2 className="text-[12px] font-semibold text-text-primary mb-3">页式存储管理</h2>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border border-l-[3px] border-l-accent">
                  <div className="text-[11px] text-text-muted font-mono">页大小</div>
                  <div className="text-[15px] font-bold text-text-primary mt-0.5 font-mono">{stats.pageSize} B</div>
                </div>
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border border-l-[3px] border-l-accent">
                  <div className="text-[11px] text-text-muted font-mono">缓冲池总帧数</div>
                  <div className="text-[15px] font-bold text-text-primary mt-0.5 font-mono">{stats.bufferSize}</div>
                </div>
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border border-l-[3px] border-l-green">
                  <div className="text-[11px] text-text-muted font-mono">已用帧 (加载页)</div>
                  <div className="text-[15px] font-bold text-green mt-0.5 font-mono">{usedFrames}</div>
                </div>
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border border-l-[3px] border-l-yellow">
                  <div className="text-[11px] text-text-muted font-mono">空闲帧</div>
                  <div className="text-[15px] font-bold text-yellow mt-0.5 font-mono">{freeFrames}</div>
                </div>
              </div>

              {/* Page read/write visualization - slot grid */}
              <div className="mt-3 px-4 py-3.5 bg-white rounded-xl border border-border">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-[12px] font-semibold text-text-primary">页面分配状态</span>
                  <div className="flex items-center gap-3 text-[11px] font-mono text-text-muted">
                    <span className="flex items-center gap-1">
                      <span className="w-2.5 h-2.5 rounded-sm bg-green/30 border border-green/40" />
                      已分配
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="w-2.5 h-2.5 rounded-sm bg-bg-elevated border border-border" />
                      空闲
                    </span>
                  </div>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {slots.map((s) => (
                    <div
                      key={s.slot}
                      className={`w-7 h-7 rounded-md flex items-center justify-center text-[10px] font-mono border transition-colors ${
                        s.file !== null
                          ? s.dirty
                            ? 'bg-yellow/15 border-yellow/30 text-yellow'
                            : 'bg-green/15 border-green/30 text-green'
                          : 'bg-bg-elevated border-border text-text-muted'
                      }`}
                      title={`#${s.slot} ${s.file ?? '空闲'} ${s.file !== null ? `p${s.block}` : ''} ${s.dirty ? '(脏)' : ''}`}
                    >
                      {s.slot}
                    </div>
                  ))}
                </div>
              </div>
            </div>
          ) : null}

          {/* ========== 2. 缓存机制 ========== */}
          {cache && (
            <div>
              <h2 className="text-[12px] font-semibold text-text-primary mb-3">缓存机制 (LRU)</h2>

              {/* Cache stats cards */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border border-l-[3px] border-l-accent">
                  <div className="text-[11px] text-text-muted font-mono">总访问次数</div>
                  <div className="text-[15px] font-bold text-text-primary mt-0.5 font-mono">{cache.accessCount}</div>
                </div>
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border border-l-[3px] border-l-green">
                  <div className="text-[11px] text-text-muted font-mono">命中次数</div>
                  <div className="text-[15px] font-bold text-green mt-0.5 font-mono">{cache.hitCount}</div>
                </div>
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border border-l-[3px] border-l-red">
                  <div className="text-[11px] text-text-muted font-mono">未命中次数</div>
                  <div className="text-[15px] font-bold text-red mt-0.5 font-mono">{cache.missCount}</div>
                </div>
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border border-l-[3px] border-l-yellow">
                  <div className="text-[11px] text-text-muted font-mono">淘汰次数</div>
                  <div className="text-[15px] font-bold text-yellow mt-0.5 font-mono">{cache.evictionCount}</div>
                </div>
              </div>

              {/* Hit rate bar */}
              <div className="mt-3 px-4 py-3.5 bg-white rounded-xl border border-border">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[12px] font-semibold text-text-primary">缓存命中率</span>
                  <span className="text-[13px] font-mono text-green font-semibold">{hitRate}%</span>
                </div>
                <div className="w-full h-2.5 bg-bg-elevated rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full transition-all duration-700 ease-out bg-green"
                    style={{ width: `${cache.hitRate * 100}%` }}
                  />
                </div>
                <div className="flex justify-between mt-1.5 text-[10px] font-mono text-text-muted">
                  <span>0%</span>
                  <span>50%</span>
                  <span>100%</span>
                </div>
              </div>

              {/* Replacement policy & dirty page info */}
              <div className="mt-3 grid grid-cols-2 gap-3">
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border">
                  <div className="text-[11px] text-text-muted font-mono mb-1.5">替换策略</div>
                  <div className="flex items-center gap-2">
                    <Badge variant="info">LRU</Badge>
                    <span className="text-[11px] text-text-secondary">最近最少使用</span>
                  </div>
                </div>
                <div className="px-4 py-3.5 bg-white rounded-xl border border-border">
                  <div className="text-[11px] text-text-muted font-mono mb-1.5">脏页回写</div>
                  <div className="flex items-center gap-2">
                    <Badge variant={dirtyFrames > 0 ? 'warn' : 'pass'}>
                      {dirtyFrames > 0 ? `${dirtyFrames} 页待写` : '已全部回写'}
                    </Badge>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ========== 3. 接口与集成 - 缓冲池槽位详情 ========== */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-[12px] font-semibold text-text-primary">缓冲池槽位 (接口状态)</h2>
              {slots.length > 0 && (
                <div className="flex items-center gap-3 text-[11px] font-mono text-text-muted">
                  <span>总帧: {slots.length}</span>
                  <span>已用: {usedFrames}</span>
                  <span>锁定: {pinnedFrames}</span>
                </div>
              )}
            </div>

            {slots.length > 0 ? (
              <div className="border border-border rounded-xl overflow-hidden bg-white">
                <table className="w-full text-[13px] font-mono">
                  <thead>
                    <tr className="border-b border-border bg-bg-elevated/30">
                      <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">帧号</th>
                      <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">文件</th>
                      <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">块号</th>
                      <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">Pin 数</th>
                      <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">事务号</th>
                      <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">脏页</th>
                      <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">状态</th>
                    </tr>
                  </thead>
                  <tbody>
                    {slots.map((s) => (
                      <tr key={s.slot} className={`border-b border-border-subtle last:border-0 transition-colors ${
                        s.file !== null ? 'hover:bg-bg-hover/50' : 'opacity-50'
                      }`}>
                        <td className="px-4 py-2.5 text-text-muted">#{s.slot}</td>
                        <td className="px-4 py-2.5 text-text-secondary truncate max-w-[120px]">{s.file ?? '—'}</td>
                        <td className="px-4 py-2.5 text-text-secondary">{s.file !== null ? s.block : '—'}</td>
                        <td className="px-4 py-2.5">
                          <span className={`font-semibold ${s.pins > 0 ? 'text-accent' : 'text-text-muted'}`}>
                            {s.pins}
                          </span>
                        </td>
                        <td className="px-4 py-2.5 text-text-secondary">{s.txnum >= 0 ? s.txnum : '—'}</td>
                        <td className="px-4 py-2.5">
                          {s.dirty ? (
                            <Badge variant="warn">脏</Badge>
                          ) : (
                            <span className="text-text-muted">—</span>
                          )}
                        </td>
                        <td className="px-4 py-2.5">
                          {s.file !== null ? (
                            s.dirty ? (
                              <Badge variant="warn">脏页</Badge>
                            ) : s.pins > 0 ? (
                              <Badge variant="info">锁定</Badge>
                            ) : (
                              <Badge variant="pass">干净</Badge>
                            )
                          ) : (
                            <span className="text-text-muted text-[11px]">空闲</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="px-4 py-10 text-center border border-border border-dashed rounded-xl">
                <div className="text-[13px] text-text-muted">点击刷新加载缓冲池状态</div>
              </div>
            )}
          </div>

          {/* ========== 表结构详情 ========== */}
          {selectedTable && (
            <div>
              <h2 className="text-[12px] font-semibold text-text-primary mb-3">
                表结构: {selectedTable}
              </h2>
              {schemaLoading ? (
                <Skeleton className="h-20 w-full" />
              ) : tableSchema ? (
                <div className="border border-border rounded-xl overflow-hidden bg-white">
                  <table className="w-full text-[13px] font-mono">
                    <thead>
                      <tr className="border-b border-border bg-bg-elevated/30">
                        <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">列名</th>
                        <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">类型</th>
                        <th className="px-4 py-3 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">长度</th>
                      </tr>
                    </thead>
                    <tbody>
                      {tableSchema.map((col) => (
                        <tr key={col.name} className="border-b border-border-subtle last:border-0 hover:bg-bg-hover/50 transition-colors">
                          <td className="px-3 py-2.5 text-text-primary font-medium">{col.name}</td>
                          <td className="px-3 py-2.5 text-text-secondary">{col.type}</td>
                          <td className="px-3 py-2.5 text-text-muted">{col.length}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="text-[13px] text-text-muted">无法加载表结构</div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
