import { useState, useEffect, useMemo, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { StatsResponse, BufferSlotsResponse } from '../api/types';
import Skeleton from '../components/Skeleton';

/* ── Helpers ── */

const SYSTEM_TABLES = new Set(['tblcat', 'fldcat', 'viewcat', 'idxcat']);
const SYSTEM_FILES = new Set(['tblcat.tbl', 'fldcat.tbl', 'viewcat.tbl', 'idxcat.tbl']);
const classifyTable = (name: string) => SYSTEM_TABLES.has(name) ? '系统表' : '用户表';

function formatTime(d: Date) {
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
}

/* ── Main Component ── */

export default function Dashboard() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<StatsResponse | null>(null);
  const [slots, setSlots] = useState<BufferSlotsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());
  const [tableSearch, setTableSearch] = useState('');
  const [tableFilter, setTableFilter] = useState<'all' | 'system' | 'user'>('all');
  const [tableSchemas, setTableSchemas] = useState<Record<string, { columnCount: number; columns: string[] }>>({});
  const [refreshing, setRefreshing] = useState(false);

  const fetchAll = useCallback(async () => {
    try {
      const [statsRes, slotsRes] = await Promise.all([api.stats(), api.bufferSlots()]);
      setStats(statsRes);
      setSlots(slotsRes);
      setLastUpdated(new Date());

      const tableList = Array.isArray(statsRes.tables) ? statsRes.tables : statsRes.tables.tables ?? [];
      const schemaResults = await Promise.allSettled(
        tableList.map(async (t) => {
          const s = await api.schema(t);
          return { name: t, columnCount: s.columns.length, columns: s.columns.map(c => c.name) };
        })
      );
      const map: Record<string, { columnCount: number; columns: string[] }> = {};
      schemaResults.forEach((r) => {
        if (r.status === 'fulfilled') map[r.value.name] = { columnCount: r.value.columnCount, columns: r.value.columns };
      });
      setTableSchemas(map);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchAll();
  };

  /* ── Derived data ── */

  const cache = stats?.cache;
  const tableList = useMemo(() => {
    const raw = Array.isArray(stats?.tables) ? stats?.tables : stats?.tables?.tables ?? [];
    return raw as string[];
  }, [stats?.tables]);

  const hitRate = cache ? (cache.hitRate * 100).toFixed(1) : '0.0';
  const bufferSlots = slots?.slots ?? [];
  const occupiedSlots = bufferSlots.filter(s => s.file !== null);
  const usedSlots = occupiedSlots.length;
  const totalSlots = stats?.bufferSize ?? 0;
  const dirtyPages = occupiedSlots.filter(s => s.dirty).length;
  const pinnedSlots = occupiedSlots.filter(s => s.pins > 0).length;

  const filteredTables = useMemo(() => {
    return tableList
      .filter(t => {
        if (tableFilter === 'system') return SYSTEM_TABLES.has(t);
        if (tableFilter === 'user') return !SYSTEM_TABLES.has(t);
        return true;
      })
      .filter(t => !tableSearch || t.toLowerCase().includes(tableSearch.toLowerCase()));
  }, [tableList, tableSearch, tableFilter]);

  /* Page classification from buffer slots */
  const pageCount = useMemo(() => {
    let systemPages = 0;
    let userPages = 0;
    let indexPages = 0;
    occupiedSlots.forEach(s => {
      if (s.file === null) return;
      const f = s.file.toLowerCase();
      if (f.endsWith('.idx') || f.includes('idx')) indexPages++;
      else if (SYSTEM_FILES.has(f) || f.startsWith('tblcat') || f.startsWith('fldcat') || f.startsWith('viewcat') || f.startsWith('idxcat')) systemPages++;
      else userPages++;
    });
    return { systemPages, userPages, indexPages, total: occupiedSlots.length };
  }, [occupiedSlots]);

  /* ── Loading skeleton ── */

  if (loading) {
    return (
      <div className="space-y-5">
        <div className="space-y-2">
          <Skeleton className="h-7 w-36" />
          <Skeleton className="h-4 w-64" />
        </div>
        <div className="grid grid-cols-4 gap-4">
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="p-5 bg-white rounded-xl border border-border">
              <Skeleton className="h-3 w-16 mb-3" />
              <Skeleton className="h-8 w-20" />
              <Skeleton className="h-3 w-24 mt-2" />
            </div>
          ))}
        </div>
        <div className="grid grid-cols-12 gap-4">
          <div className="col-span-8 p-5 bg-white rounded-xl border border-border">
            <Skeleton className="h-4 w-24 mb-4" />
            <Skeleton className="h-48 w-full" />
          </div>
          <div className="col-span-4 p-5 bg-white rounded-xl border border-border">
            <Skeleton className="h-4 w-24 mb-4" />
            <Skeleton className="h-48 w-full" />
          </div>
        </div>
      </div>
    );
  }

  /* ── Stat Cards Data ── */

  const statCards = [
    {
      label: '数据库状态',
      value: '运行中',
      sub: 'Database Engine Ready',
      color: 'text-green',
      dot: true,
    },
    {
      label: '数据表',
      value: tableList.length,
      sub: '张表',
      color: 'text-text-primary',
      mono: true,
    },
    {
      label: 'Buffer Pool',
      value: `${hitRate}%`,
      sub: '命中率',
      color: 'text-green',
      mono: true,
    },
    {
      label: 'SQL 请求',
      value: cache?.accessCount ?? 0,
      sub: '总执行次数',
      color: 'text-text-primary',
      mono: true,
    },
  ];

  return (
    <div className="space-y-5">

      {/* ── Page Header ── */}
      <div className="flex items-end justify-between">
        <div>
          <h1 className="text-[22px] font-bold text-text-primary leading-tight">数据库概览</h1>
          <p className="text-[13px] text-text-muted mt-0.5">MiniSQL 数据库运行状态与核心指标</p>
        </div>
        <span className="text-[12px] text-text-muted font-mono">
          更新于 {formatTime(lastUpdated)}
        </span>
      </div>

      {error && (
        <div className="px-4 py-2.5 rounded-lg bg-red-muted text-[13px] text-red font-mono border border-red/20">
          {error}
        </div>
      )}

      {/* ── Stat Cards ── */}
      <div className="grid grid-cols-4 gap-4">
        {statCards.map((card) => (
          <div key={card.label} className="px-5 py-4 bg-white rounded-xl border border-border">
            <div className="text-[12px] text-text-muted font-medium">{card.label}</div>
            <div className="flex items-center gap-2 mt-2">
              {card.dot && (
                <span className="w-2 h-2 rounded-full bg-green flex-shrink-0" />
              )}
              <span className={`text-[28px] font-bold leading-none tracking-tight ${card.color} ${card.mono ? 'font-mono' : ''}`}>
                {card.value}
              </span>
            </div>
            <div className="text-[12px] text-text-muted mt-1.5">{card.sub}</div>
          </div>
        ))}
      </div>

      {/* ── Middle Row: Buffer Pool + Quick Actions ── */}
      <div className="grid grid-cols-12 gap-4">

        {/* Buffer Pool Performance */}
        <div className="col-span-8 bg-white rounded-xl border border-border">
          <div className="px-5 py-3.5 border-b border-border-subtle">
            <h2 className="text-[13px] font-semibold text-text-primary">缓冲池性能</h2>
          </div>
          <div className="p-5">
            <div className="flex items-baseline gap-3 mb-4">
              <span className="text-[32px] font-bold font-mono text-green leading-none">{hitRate}%</span>
              <span className="text-[13px] text-text-muted">缓冲池命中率</span>
            </div>

            {/* Progress bar */}
            <div className="w-full h-2.5 bg-bg-elevated rounded-full overflow-hidden mb-5">
              <div
                className="h-full rounded-full bg-green transition-all duration-700 ease-out"
                style={{ width: `${cache ? cache.hitRate * 100 : 0}%` }}
              />
            </div>

            {/* Stats row */}
            <div className="grid grid-cols-3 gap-6">
              <div className="flex items-baseline gap-2">
                <span className="text-[22px] font-bold font-mono text-green leading-none">
                  {cache?.hitCount ?? 0}
                </span>
                <span className="text-[12px] text-text-muted">次命中</span>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-[22px] font-bold font-mono text-yellow leading-none">
                  {cache?.missCount ?? 0}
                </span>
                <span className="text-[12px] text-text-muted">次未命中</span>
              </div>
              <div className="flex items-baseline gap-2">
                <span className="text-[22px] font-bold font-mono text-text-secondary leading-none">
                  {cache?.evictionCount ?? 0}
                </span>
                <span className="text-[12px] text-text-muted">次驱逐</span>
              </div>
            </div>

            {/* Buffer slot usage */}
            {totalSlots > 0 && (
              <div className="mt-5 pt-4 border-t border-border-subtle">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[12px] text-text-muted">槽位使用</span>
                  <span className="text-[12px] font-mono text-text-secondary">{usedSlots} / {totalSlots}</span>
                </div>
                <div className="w-full h-2 bg-bg-elevated rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full bg-accent transition-all duration-500"
                    style={{ width: `${totalSlots > 0 ? (usedSlots / totalSlots) * 100 : 0}%` }}
                  />
                </div>
                <div className="flex items-center gap-5 mt-3">
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-green" />
                    <span className="text-[12px] text-text-muted">空闲 {totalSlots - usedSlots}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-accent" />
                    <span className="text-[12px] text-text-muted">已占用 {usedSlots}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-yellow" />
                    <span className="text-[12px] text-text-muted">脏页 {dirtyPages}</span>
                  </div>
                </div>
                <div className="flex items-center justify-between mt-3">
                  <span className="text-[12px] text-text-muted">页面大小: {stats?.pageSize ?? '—'} bytes</span>
                  <span className="text-[12px] text-text-muted">固定槽位: {pinnedSlots}</span>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="col-span-4 bg-white rounded-xl border border-border">
          <div className="px-5 py-3.5 border-b border-border-subtle">
            <h2 className="text-[13px] font-semibold text-text-primary">快速操作</h2>
          </div>
          <div className="p-4">
            <div className="space-y-2 mb-4">
              <Link
                to="/console"
                className="flex items-center gap-3 px-4 py-3 rounded-lg bg-accent text-white text-[13px] font-medium hover:bg-accent-hover transition-colors"
              >
                <span className="text-[15px]">+</span>
                <span>新建 SQL 查询</span>
              </Link>
              <div className="grid grid-cols-2 gap-2">
                <Link
                  to="/console"
                  className="flex items-center gap-2 px-3 py-2.5 rounded-lg border border-border text-[13px] text-text-primary font-medium hover:bg-bg-hover transition-colors"
                >
                  <span className="text-green text-[13px]">&#9654;</span>
                  <span>执行 SQL</span>
                </Link>
                <Link
                  to="/tests"
                  className="flex items-center gap-2 px-3 py-2.5 rounded-lg border border-border text-[13px] text-text-primary font-medium hover:bg-bg-hover transition-colors"
                >
                  <span className="text-[13px]">&#9881;</span>
                  <span>运行测试</span>
                </Link>
              </div>
            </div>

            <div className="border-t border-border-subtle pt-3 space-y-0.5">
              {[
                { to: '/history', label: '查询历史' },
                { to: '/logs', label: '执行日志' },
                { to: '/storage', label: '存储检查' },
              ].map((action) => (
                <Link
                  key={action.to}
                  to={action.to}
                  className="flex items-center justify-between px-3 py-2.5 rounded-lg text-[13px] text-text-secondary hover:text-text-primary hover:bg-bg-hover transition-colors"
                >
                  <span>{action.label}</span>
                  <span className="text-text-muted text-[12px]">&rarr;</span>
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* ── Database Tables ── */}
      <div className="bg-white rounded-xl border border-border">
        <div className="px-5 py-3.5 border-b border-border-subtle flex items-center justify-between">
          <h2 className="text-[13px] font-semibold text-text-primary">数据库表</h2>
          <div className="flex items-center gap-2">
            <input
              type="text"
              placeholder="搜索表名"
              value={tableSearch}
              onChange={(e) => setTableSearch(e.target.value)}
              className="h-8 px-3 rounded-lg border border-border bg-bg-elevated text-[13px] text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:border-border-focus w-36 transition-colors"
            />
            <select
              value={tableFilter}
              onChange={(e) => setTableFilter(e.target.value as 'all' | 'system' | 'user')}
              className="h-8 px-2 rounded-lg border border-border bg-bg-elevated text-[13px] text-text-secondary focus:outline-none focus:border-border-focus cursor-pointer transition-colors"
            >
              <option value="all">全部</option>
              <option value="system">系统表</option>
              <option value="user">用户表</option>
            </select>
            <button
              onClick={handleRefresh}
              disabled={refreshing}
              className="h-8 px-3 rounded-lg border border-border bg-white text-[13px] text-text-secondary hover:bg-bg-hover transition-colors disabled:opacity-50"
            >
              {refreshing ? '刷新中...' : '刷新'}
            </button>
          </div>
        </div>

        {filteredTables.length > 0 ? (
          <table className="w-full" style={{ tableLayout: 'fixed' }}>
            <colgroup>
              <col style={{ width: '28%' }} />
              <col style={{ width: '14%' }} />
              <col style={{ width: '14%' }} />
              <col style={{ width: '14%' }} />
              <col style={{ width: '30%' }} />
            </colgroup>
            <thead>
              <tr className="border-b border-border-subtle">
                <th className="px-5 py-3 text-left text-[12px] font-semibold text-text-muted uppercase tracking-wider">表名</th>
                <th className="px-5 py-3 text-left text-[12px] font-semibold text-text-muted uppercase tracking-wider">类型</th>
                <th className="px-5 py-3 text-left text-[12px] font-semibold text-text-muted uppercase tracking-wider">列数</th>
                <th className="px-5 py-3 text-left text-[12px] font-semibold text-text-muted uppercase tracking-wider">状态</th>
                <th className="px-5 py-3 text-right text-[12px] font-semibold text-text-muted uppercase tracking-wider">操作</th>
              </tr>
            </thead>
            <tbody>
              {filteredTables.map((t, i) => {
                const isSystem = SYSTEM_TABLES.has(t);
                const info = tableSchemas[t];
                const colCount = info?.columnCount ?? '—';
                return (
                  <tr
                    key={t}
                    className={`border-b border-border-subtle last:border-0 hover:bg-bg-hover/40 transition-colors cursor-default ${
                      i % 2 === 1 ? 'bg-bg-elevated/20' : ''
                    }`}
                  >
                    <td className="px-5 py-3">
                      <span className="font-mono text-[13px] text-accent font-medium">{t}</span>
                    </td>
                    <td className="px-5 py-3">
                      <span className={`text-[12px] font-mono ${isSystem ? 'text-text-muted' : 'text-text-secondary'}`}>
                        {classifyTable(t)}
                      </span>
                    </td>
                    <td className="px-5 py-3">
                      <span className="text-[13px] font-mono text-text-secondary">{colCount}</span>
                    </td>
                    <td className="px-5 py-3">
                      <span className="flex items-center gap-1.5 text-[12px] text-green">
                        <span className="w-1.5 h-1.5 rounded-full bg-green" />
                        活跃
                      </span>
                    </td>
                    <td className="px-5 py-3 text-right">
                      <button
                        onClick={() => navigate('/console')}
                        className="text-[12px] text-accent hover:text-accent-hover font-medium transition-colors"
                      >
                        查看 &rarr;
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        ) : (
          <div className="px-5 py-12 text-center">
            <div className="text-[13px] text-text-muted">
              {tableList.length === 0 ? '暂无表格' : '未找到匹配的表格'}
            </div>
            {tableList.length === 0 && (
              <div className="text-[12px] text-text-muted/60 mt-1">前往 SQL 控制台创建</div>
            )}
          </div>
        )}
      </div>

      {/* ── System Status ── */}
      <div className="bg-white rounded-xl border border-border">
        <div className="px-5 py-3.5 border-b border-border-subtle">
          <h2 className="text-[13px] font-semibold text-text-primary">系统状态</h2>
        </div>
        <div className="grid grid-cols-4">
          {[
            { label: '数据库', status: '运行中' },
            { label: '存储系统', status: '正常' },
            { label: 'Buffer Pool', status: '正常' },
            { label: '执行引擎', status: '就绪' },
          ].map((item, i) => (
            <div
              key={item.label}
              className={`flex items-center justify-between px-5 py-3.5 ${
                i < 3 ? 'border-r border-border-subtle' : ''
              }`}
            >
              <span className="text-[13px] text-text-secondary">{item.label}</span>
              <span className="flex items-center gap-1.5 text-[12px] text-green font-medium">
                <span className="w-1.5 h-1.5 rounded-full bg-green" />
                {item.status}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* ── DBMS Info Row: SQL Stats + Storage + Execution + Logs ── */}
      <div className="grid grid-cols-4 gap-4">

        {/* SQL Execution Statistics */}
        <div className="bg-white rounded-xl border border-border">
          <div className="px-5 py-3.5 border-b border-border-subtle">
            <h2 className="text-[13px] font-semibold text-text-primary">SQL 执行统计</h2>
          </div>
          <div className="p-5">
            <div className="space-y-3">
              {[
                { label: 'SELECT', count: '—', desc: '查询语句' },
                { label: 'INSERT', count: '—', desc: '插入语句' },
                { label: 'UPDATE', count: '—', desc: '更新语句' },
                { label: 'DELETE', count: '—', desc: '删除语句' },
              ].map((item) => (
                <div key={item.label} className="flex items-center justify-between py-1.5">
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[12px] font-semibold text-text-primary w-14">{item.label}</span>
                    <span className="text-[12px] text-text-muted">{item.desc}</span>
                  </div>
                  <span className="font-mono text-[13px] font-semibold text-text-secondary">{item.count}</span>
                </div>
              ))}
            </div>
            <div className="mt-4 pt-3 border-t border-border-subtle">
              <div className="flex items-center justify-between">
                <span className="text-[12px] text-text-muted">总执行次数</span>
                <span className="font-mono text-[14px] font-bold text-accent">{cache?.accessCount ?? 0}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Storage System */}
        <div className="bg-white rounded-xl border border-border">
          <div className="px-5 py-3.5 border-b border-border-subtle">
            <h2 className="text-[13px] font-semibold text-text-primary">存储系统</h2>
          </div>
          <div className="p-5">
            <div className="space-y-3">
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">页面大小</span>
                <span className="font-mono text-[13px] font-semibold text-text-secondary">{stats?.pageSize ?? '—'} bytes</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">缓冲池容量</span>
                <span className="font-mono text-[13px] font-semibold text-text-secondary">{stats?.bufferSize ?? '—'} 槽位</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">系统页</span>
                <span className="font-mono text-[13px] font-semibold text-text-secondary">{pageCount.systemPages}</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">数据页</span>
                <span className="font-mono text-[13px] font-semibold text-text-secondary">{pageCount.userPages}</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">索引页</span>
                <span className="font-mono text-[13px] font-semibold text-text-secondary">{pageCount.indexPages}</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">空闲页</span>
                <span className="font-mono text-[13px] font-semibold text-green">{totalSlots - usedSlots}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Execution Engine */}
        <div className="bg-white rounded-xl border border-border">
          <div className="px-5 py-3.5 border-b border-border-subtle">
            <h2 className="text-[13px] font-semibold text-text-primary">执行引擎</h2>
          </div>
          <div className="p-5">
            <div className="space-y-3">
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">引擎状态</span>
                <span className="flex items-center gap-1.5 text-[12px] text-green font-medium">
                  <span className="w-1.5 h-1.5 rounded-full bg-green" />
                  就绪
                </span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">总访问次数</span>
                <span className="font-mono text-[13px] font-semibold text-text-secondary">{cache?.accessCount ?? 0}</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">命中次数</span>
                <span className="font-mono text-[13px] font-semibold text-green">{cache?.hitCount ?? 0}</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">未命中次数</span>
                <span className="font-mono text-[13px] font-semibold text-yellow">{cache?.missCount ?? 0}</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">驱逐次数</span>
                <span className="font-mono text-[13px] font-semibold text-text-secondary">{cache?.evictionCount ?? 0}</span>
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[12px] text-text-muted">命中率</span>
                <span className="font-mono text-[13px] font-bold text-green">{hitRate}%</span>
              </div>
            </div>
          </div>
        </div>

        {/* Recent Logs */}
        <div className="bg-white rounded-xl border border-border">
          <div className="px-5 py-3.5 border-b border-border-subtle flex items-center justify-between">
            <h2 className="text-[13px] font-semibold text-text-primary">最近日志</h2>
            <Link to="/logs" className="text-[12px] text-accent hover:text-accent-hover transition-colors">查看全部 &rarr;</Link>
          </div>
          <div className="p-5">
            <div className="space-y-3">
              <div className="flex items-start gap-3 py-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-green mt-1.5 flex-shrink-0" />
                <div className="min-w-0">
                  <div className="text-[13px] text-text-primary font-medium truncate">数据库引擎初始化</div>
                  <div className="text-[12px] text-text-muted font-mono mt-0.5">System startup</div>
                </div>
              </div>
              <div className="flex items-start gap-3 py-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-green mt-1.5 flex-shrink-0" />
                <div className="min-w-0">
                  <div className="text-[13px] text-text-primary font-medium truncate">Buffer Pool 已加载</div>
                  <div className="text-[12px] text-text-muted font-mono mt-0.5">{totalSlots} 槽位就绪</div>
                </div>
              </div>
              <div className="flex items-start gap-3 py-1.5">
                <span className="w-1.5 h-1.5 rounded-full bg-green mt-1.5 flex-shrink-0" />
                <div className="min-w-0">
                  <div className="text-[13px] text-text-primary font-medium truncate">元数据管理器就绪</div>
                  <div className="text-[12px] text-text-muted font-mono mt-0.5">{tableList.length} 张表已加载</div>
                </div>
              </div>
              {cache && cache.accessCount > 0 && (
                <div className="flex items-start gap-3 py-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-blue mt-1.5 flex-shrink-0" />
                  <div className="min-w-0">
                    <div className="text-[13px] text-text-primary font-medium truncate">缓存统计可用</div>
                    <div className="text-[12px] text-text-muted font-mono mt-0.5">命中率 {hitRate}%</div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
