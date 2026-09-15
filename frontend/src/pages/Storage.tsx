import { useState, useCallback, useEffect, useRef } from 'react';
import { api } from '../api/client';
import type { StatsResponse, BufferSlotInfo, StorageTestState, StorageTestResult } from '../api/types';
import Button from '../components/Button';
import Badge from '../components/Badge';
import Skeleton from '../components/Skeleton';

const POLL_MS = 3000;

// Distinct color palette for different files in buffer pool
const FILE_COLORS: { bg: string; border: string; text: string; light: string }[] = [
  { bg: '#3F704B', border: '#3F704B40', text: '#3F704B', light: '#3F704B18' },  // green
  { bg: '#2A6DB5', border: '#2A6DB540', text: '#2A6DB5', light: '#2A6DB518' },  // blue
  { bg: '#9B59B6', border: '#9B59B640', text: '#9B59B6', light: '#9B59B618' },  // purple
  { bg: '#D1633C', border: '#D1633C40', text: '#D1633C', light: '#D1633C18' },  // terracotta
  { bg: '#A36316', border: '#A3631640', text: '#A36316', light: '#A3631618' },  // amber
  { bg: '#16A34A', border: '#16A34A40', text: '#16A34A', light: '#16A34A18' },  // emerald
  { bg: '#E11D48', border: '#E11D4840', text: '#E11D48', light: '#E11D4818' },  // rose
  { bg: '#0891B2', border: '#0891B240', text: '#0891B2', light: '#0891B218' },  // cyan
];
const FILE_COLORS_DIRTY: { bg: string; border: string; text: string; light: string }[] = [
  { bg: '#CA8A04', border: '#CA8A0440', text: '#CA8A04', light: '#CA8A0418' },  // yellow
  { bg: '#D97706', border: '#D9770640', text: '#D97706', light: '#D9770618' },  // amber
  { bg: '#DC2626', border: '#DC262640', text: '#DC2626', light: '#DC262618' },  // red
  { bg: '#EA580C', border: '#EA580C40', text: '#EA580C', light: '#EA580C18' },  // orange
  { bg: '#D946EF', border: '#D946EF40', text: '#D946EF', light: '#D946EF18' },  // fuchsia
  { bg: '#0D9488', border: '#0D948840', text: '#0D9488', light: '#0D948818' },  // teal
  { bg: '#7C3AED', border: '#7C3AED40', text: '#7C3AED', light: '#7C3AED18' },  // violet
  { bg: '#BE185D', border: '#BE185D40', text: '#BE185D', light: '#BE185D18' },  // pink
];

const fileNameColorMap = new Map<string, number>();
let colorIdx = 0;

function getFileColor(fileName: string, dirty: boolean) {
  if (!fileName) return null;
  let idx = fileNameColorMap.get(fileName);
  if (idx === undefined) {
    idx = colorIdx % FILE_COLORS.length;
    fileNameColorMap.set(fileName, idx);
    colorIdx++;
  }
  return dirty ? FILE_COLORS_DIRTY[idx] : FILE_COLORS[idx];
}

type Tab = 'test' | 'monitor';

export default function Storage() {
  const [tab, setTab] = useState<Tab>('test');
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

  // Storage test state
  const [testState, setTestState] = useState<StorageTestState | null>(null);
  const [testLoading, setTestLoading] = useState(false);
  const [testLog, setTestLog] = useState<string[]>([]);
  const [writeOffset, setWriteOffset] = useState('0');
  const [writeValue, setWriteValue] = useState('0');
  const [readOffset, setReadOffset] = useState('0');
  const [activePolicy, setActivePolicy] = useState<string>('LRU');
  const logEndRef = useRef<HTMLDivElement>(null);

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

  const refreshTestState = useCallback(async () => {
    try {
      const state = await api.storageState();
      setTestState(state);
    } catch (e) {
      setError(String(e));
    }
  }, []);

  useEffect(() => {
    refreshTestState();
  }, [refreshTestState]);

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

  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [testLog]);

  const addLog = (msg: string) => setTestLog((prev) => [...prev, msg]);

  const handleAlloc = async () => {
    setTestLoading(true);
    try {
      const r: StorageTestResult = await api.storageAlloc();
      if (r.success) {
        addLog(`LOAD   → 页 #${r.pageNum} (${r.cacheEvent?.hit ? 'HIT' : 'MISS'})`);
        setTestState((prev) => prev ? { ...prev, stats: r.stats!, slots: r.slots!, allocatedPages: [...prev.allocatedPages, { pageNum: r.pageNum!, fileName: r.cacheEvent!.file }] } : prev);
      } else {
        addLog(`LOAD FAILED: ${r.error}`);
      }
    } catch (e) { addLog(`ERROR: ${e}`); }
    setTestLoading(false);
  };

  const handleFree = async (pageNum: number) => {
    setTestLoading(true);
    try {
      const r: StorageTestResult = await api.storageFree(pageNum);
      if (r.success) {
        addLog(`FREE   → 页 #${pageNum} 已释放`);
        setTestState((prev) => prev ? { ...prev, stats: r.stats!, slots: r.slots!, allocatedPages: prev.allocatedPages.filter((p) => p.pageNum !== pageNum) } : prev);
      } else {
        addLog(`FREE FAILED: ${r.error}`);
      }
    } catch (e) { addLog(`ERROR: ${e}`); }
    setTestLoading(false);
  };

  const handleWrite = async (pageNum: number) => {
    const offset = parseInt(writeOffset) || 0;
    const value = parseInt(writeValue) || 0;
    setTestLoading(true);
    try {
      const r: StorageTestResult = await api.storageWrite(pageNum, offset, value);
      if (r.success) {
        addLog(`WRITE  → 页 #${pageNum} 偏移${offset} = ${value} (${r.cacheEvent?.hit ? 'HIT' : 'MISS'})`);
        setTestState((prev) => prev ? { ...prev, stats: r.stats!, slots: r.slots! } : prev);
      } else {
        addLog(`WRITE FAILED: ${r.error}`);
      }
    } catch (e) { addLog(`ERROR: ${e}`); }
    setTestLoading(false);
  };

  const handleRead = async (pageNum: number) => {
    const offset = parseInt(readOffset) || 0;
    setTestLoading(true);
    try {
      const r: StorageTestResult = await api.storageRead(pageNum, offset);
      if (r.success) {
        addLog(`READ   → 页 #${pageNum} 偏移${offset} = ${r.value} (${r.cacheEvent?.hit ? 'HIT' : 'MISS'})`);
        setTestState((prev) => prev ? { ...prev, stats: r.stats!, slots: r.slots! } : prev);
      } else {
        addLog(`READ FAILED: ${r.error}`);
      }
    } catch (e) { addLog(`ERROR: ${e}`); }
    setTestLoading(false);
  };

  const handleAccess = async (pageNum: number) => {
    setTestLoading(true);
    try {
      const r: StorageTestResult = await api.storageAccess(pageNum);
      if (r.success) {
        addLog(`ACCESS → 页 #${pageNum} ${r.hit ? '✓ HIT' : '✗ MISS'}`);
        setTestState((prev) => prev ? { ...prev, stats: r.stats!, slots: r.slots! } : prev);
      } else {
        addLog(`ACCESS FAILED: ${r.error}`);
      }
    } catch (e) { addLog(`ERROR: ${e}`); }
    setTestLoading(false);
  };

  const handlePolicy = async (policy: string) => {
    setTestLoading(true);
    try {
      await api.storagePolicy(policy);
      setActivePolicy(policy);
      addLog(`POLICY → 切换为 ${policy}`);
    } catch (e) { addLog(`ERROR: ${e}`); }
    setTestLoading(false);
  };

  const handleReset = async () => {
    setTestLoading(true);
    try {
      const state = await api.storageReset();
      setTestState(state);
      setTestLog([]);
      addLog('RESET  → 测试环境已重置');
    } catch (e) { addLog(`ERROR: ${e}`); }
    setTestLoading(false);
  };

  const cache = stats?.cache;
  const tableList = Array.isArray(stats?.tables) ? stats.tables : stats?.tables?.tables ?? [];
  const usedFrames = slots.filter((s) => s.file !== null).length;
  const dirtyFrames = slots.filter((s) => s.dirty).length;
  const freeFrames = slots.length - usedFrames;
  const pinnedFrames = slots.filter((s) => s.pins > 0).length;
  const hitRate = cache ? (cache.hitRate * 100).toFixed(1) : '0.0';

  const tCache = testState?.stats;
  const tSlots = testState?.slots ?? [];
  const tHistory = testState?.accessHistory ?? [];
  const tPages = testState?.allocatedPages ?? [];

  return (
    <div className="flex flex-col h-full">
      {/* Error banner */}
      {error && (
        <div className="mb-3 px-3 py-2 rounded-lg bg-red-muted text-[13px] text-red font-mono flex-shrink-0">{error}</div>
      )}

      {/* Tab bar + controls row */}
      <div className="flex items-center justify-between mb-3 flex-shrink-0">
        <div className="flex items-center gap-1">
          <button
            onClick={() => setTab('test')}
            className={`px-3 py-1.5 rounded-lg text-[12px] font-semibold transition-colors ${
              tab === 'test' ? 'bg-accent text-white' : 'text-text-secondary hover:text-text-primary hover:bg-bg-hover'
            }`}
          >
            交互测试
          </button>
          <button
            onClick={() => setTab('monitor')}
            className={`px-3 py-1.5 rounded-lg text-[12px] font-semibold transition-colors ${
              tab === 'monitor' ? 'bg-accent text-white' : 'text-text-secondary hover:text-text-primary hover:bg-bg-hover'
            }`}
          >
            运行监控
          </button>
        </div>

        {tab === 'test' && (
          <div className="flex items-center gap-2">
            <Button onClick={handleAlloc} disabled={testLoading} size="sm">加载页面</Button>
            <Button onClick={handleReset} disabled={testLoading} variant="danger" size="sm">重置</Button>
            <div className="flex items-center gap-1 ml-2">
              <span className="text-[11px] text-text-muted font-mono">策略:</span>
              {['LRU', 'FIFO'].map((p) => (
                <button
                  key={p}
                  onClick={() => handlePolicy(p)}
                  className={`px-2 py-0.5 rounded text-[11px] font-mono transition-colors ${
                    activePolicy === p ? 'bg-accent text-white' : 'bg-bg-elevated text-text-secondary hover:text-text-primary'
                  }`}
                >
                  {p}
                </button>
              ))}
            </div>
          </div>
        )}

        {tab === 'monitor' && (
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
              自动刷新
            </button>
            <Button variant="ghost" size="sm" onClick={refresh} disabled={loading}>
              刷新
            </Button>
          </div>
        )}
      </div>

      {/* Main content area — fills remaining height */}
      {tab === 'test' ? (
        /* ==================== 交互测试面板 — 三列全屏布局 ==================== */
        <div className="flex-1 flex gap-3 min-h-0">
          {/* Left column: Cache visualization */}
          <div className="flex-1 flex flex-col min-w-0 gap-3">
            {/* Cache stats row */}
            {tCache && (
              <div className="flex gap-2 flex-shrink-0">
                <div className="flex-1 px-4 py-3 bg-white rounded-xl border border-border border-l-[3px] border-l-accent">
                  <div className="text-[11px] text-text-muted font-mono">访问</div>
                  <div className="text-[18px] font-bold text-text-primary font-mono">{tCache.accessCount}</div>
                </div>
                <div className="flex-1 px-4 py-3 bg-white rounded-xl border border-border border-l-[3px] border-l-green">
                  <div className="text-[11px] text-text-muted font-mono">命中</div>
                  <div className="text-[18px] font-bold text-green font-mono">{tCache.hitCount}</div>
                </div>
                <div className="flex-1 px-4 py-3 bg-white rounded-xl border border-border border-l-[3px] border-l-red">
                  <div className="text-[11px] text-text-muted font-mono">未命中</div>
                  <div className="text-[18px] font-bold text-red font-mono">{tCache.missCount}</div>
                </div>
                <div className="flex-1 px-4 py-3 bg-white rounded-xl border border-border border-l-[3px] border-l-yellow">
                  <div className="text-[11px] text-text-muted font-mono">淘汰</div>
                  <div className="text-[18px] font-bold text-yellow font-mono">{tCache.evictionCount}</div>
                </div>
              </div>
            )}

            {/* Hit rate bar */}
            {tCache && (
              <div className="px-4 py-3 bg-white rounded-xl border border-border flex-shrink-0">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[12px] font-semibold text-text-primary">缓存命中率</span>
                  <span className="text-[14px] font-mono text-green font-bold">
                    {(tCache.hitRate * 100).toFixed(1)}%
                  </span>
                </div>
                <div className="w-full h-3 bg-bg-elevated rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full transition-all duration-500 bg-green"
                    style={{ width: `${tCache.hitRate * 100}%` }}
                  />
                </div>
              </div>
            )}

            {/* Buffer pool slot grid */}
            <div className="flex-1 px-4 py-3 bg-white rounded-xl border border-border min-h-0 flex flex-col">
              <div className="flex items-center justify-between mb-3 flex-shrink-0">
                <span className="text-[12px] font-semibold text-text-primary">缓冲池槽位</span>
                <div className="flex items-center gap-3 text-[10px] font-mono text-text-muted">
                  <span className="flex items-center gap-1">
                    <span className="w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: FILE_COLORS[0].light, border: `1px solid ${FILE_COLORS[0].border}` }} />
                    按表着色
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: FILE_COLORS_DIRTY[0].light, border: `1px solid ${FILE_COLORS_DIRTY[0].border}` }} />
                    脏页
                  </span>
                  <span className="flex items-center gap-1">
                    <span className="w-2.5 h-2.5 rounded-sm bg-bg-elevated border border-border" />
                    空闲
                  </span>
                </div>
              </div>
              <div className="flex-1 overflow-auto">
                <div className="grid grid-cols-6 gap-2">
                  {tSlots.map((s) => {
                    const fc = getFileColor(s.file ?? '', s.dirty);
                    return (
                      <div
                        key={s.slot}
                        className={`aspect-square rounded-lg flex flex-col items-center justify-center text-[10px] font-mono border transition-all cursor-default ${
                          s.file !== null
                            ? 'shadow-sm'
                            : 'bg-bg-elevated border-border text-text-muted'
                        }`}
                        style={s.file !== null && fc ? {
                          backgroundColor: fc.light,
                          borderColor: fc.border,
                          color: fc.text,
                        } : undefined}
                        title={`#${s.slot} ${s.file ?? '空闲'} 块${s.block} pin=${s.pins} ${s.dirty ? '脏' : ''}`}
                      >
                        <span className="text-[11px] font-bold">#{s.slot}</span>
                        {s.file !== null && (
                          <span className="text-[9px] opacity-70 mt-0.5">B{s.block}</span>
                        )}
                        {s.pins > 0 && (
                          <span className="text-[8px]" style={{ color: fc?.text ?? '#D1633C' }}>pin:{s.pins}</span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          {/* Middle column: Page operations */}
          <div className="w-80 flex flex-col min-w-0 gap-3">
            {/* Page list with operations */}
            <div className="px-4 py-3 bg-white rounded-xl border border-border flex-shrink-0">
              <div className="flex items-center justify-between mb-3">
                <span className="text-[12px] font-semibold text-text-primary">已加载页面</span>
                <div className="flex items-center gap-2">
                  <span className="text-[11px] font-mono text-text-muted">{tPages.length} 个</span>
                  <span className="text-[10px] font-mono text-text-muted bg-bg-elevated px-1.5 py-0.5 rounded">
                    缓冲池: {tSlots.filter(s => s.file !== null).length}/{tSlots.length}
                  </span>
                </div>
              </div>
              {tPages.length === 0 ? (
                <div className="text-[12px] text-text-muted py-4 text-center border border-dashed border-border rounded-lg">
                  点击"加载页面"开始测试
                </div>
              ) : (
                <div className="space-y-1 max-h-56 overflow-auto">
                  {tPages.map((p) => {
                    const inBuffer = tSlots.some(s => s.file !== null && s.block === p.pageNum);
                    return (
                    <div key={p.pageNum} className={`flex items-center gap-1.5 ${!inBuffer ? 'opacity-50' : ''}`}>
                      <span className="text-[12px] font-mono text-text-secondary w-10 font-semibold">#{p.pageNum}</span>
                      {inBuffer ? (
                        <span className="text-[9px] font-mono text-green bg-green/10 px-1 py-0.5 rounded">内存中</span>
                      ) : (
                        <span className="text-[9px] font-mono text-text-muted bg-bg-elevated px-1 py-0.5 rounded">已淘汰</span>
                      )}
                      <button
                        onClick={() => handleAccess(p.pageNum)}
                        disabled={testLoading}
                        className="flex-1 px-2 py-1.5 rounded text-[11px] font-mono bg-accent/10 text-accent hover:bg-accent/20 transition-colors"
                      >
                        访问
                      </button>
                      <button
                        onClick={() => handleWrite(p.pageNum)}
                        disabled={testLoading}
                        className="flex-1 px-2 py-1.5 rounded text-[11px] font-mono bg-yellow/10 text-yellow hover:bg-yellow/20 transition-colors"
                      >
                        写入
                      </button>
                      <button
                        onClick={() => handleRead(p.pageNum)}
                        disabled={testLoading}
                        className="flex-1 px-2 py-1.5 rounded text-[11px] font-mono bg-blue/10 text-blue hover:bg-blue/20 transition-colors"
                      >
                        读取
                      </button>
                      <button
                        onClick={() => handleFree(p.pageNum)}
                        disabled={testLoading}
                        className="px-2 py-1.5 rounded text-[11px] font-mono bg-red/10 text-red hover:bg-red/20 transition-colors"
                      >
                        释放
                      </button>
                    </div>
                  );
                  })}
                </div>
              )}
              <div className="mt-3 pt-3 border-t border-border">
                <p className="text-[10px] text-text-muted leading-relaxed">
                  <span className="font-semibold">说明：</span>缓冲池是数据库共享资源。"加载页面"将页面从磁盘读入内存，
                  当缓冲池满时会触发淘汰（LRU/FIFO）。已淘汰的页面在列表中显示为半透明。
                </p>
              </div>
            </div>

            {/* Read/Write params */}
            <div className="px-4 py-3 bg-white rounded-xl border border-border flex-shrink-0">
              <div className="text-[12px] font-semibold text-text-primary mb-3">读写参数</div>
              <div className="space-y-2.5">
                <div className="flex items-center gap-2">
                  <label className="text-[11px] font-mono text-text-muted w-12">偏移</label>
                  <input
                    type="number"
                    value={writeOffset}
                    onChange={(e) => { setWriteOffset(e.target.value); setReadOffset(e.target.value); }}
                    className="flex-1 px-2.5 py-1.5 rounded-lg border border-border text-[12px] font-mono bg-bg-elevated text-text-primary focus:outline-none focus:border-accent"
                  />
                </div>
                <div className="flex items-center gap-2">
                  <label className="text-[11px] font-mono text-text-muted w-12">写值</label>
                  <input
                    type="number"
                    value={writeValue}
                    onChange={(e) => setWriteValue(e.target.value)}
                    className="flex-1 px-2.5 py-1.5 rounded-lg border border-border text-[12px] font-mono bg-bg-elevated text-text-primary focus:outline-none focus:border-accent"
                  />
                </div>
              </div>
            </div>

            {/* Operation log */}
            <div className="flex-1 px-4 py-3 bg-white rounded-xl border border-border min-h-0 flex flex-col">
              <div className="flex items-center justify-between mb-2 flex-shrink-0">
                <span className="text-[12px] font-semibold text-text-primary">操作日志</span>
                <button
                  onClick={() => setTestLog([])}
                  className="text-[11px] text-text-muted hover:text-text-secondary transition-colors"
                >
                  清空
                </button>
              </div>
              <div className="flex-1 overflow-auto space-y-0.5 font-mono text-[11px]">
                {testLog.length === 0 ? (
                  <div className="text-text-muted py-4 text-center text-[11px]">暂无日志</div>
                ) : (
                  testLog.map((line, i) => (
                    <div
                      key={i}
                      className={`px-2 py-1 rounded ${
                        line.includes('FAILED') || line.includes('ERROR')
                          ? 'text-red bg-red/5'
                          : 'text-text-secondary'
                      }`}
                    >
                      {line}
                    </div>
                  ))
                )}
              </div>
              <div ref={logEndRef} />
            </div>
          </div>

          {/* Right column: Access history timeline */}
          <div className="w-72 flex flex-col min-w-0">
            <div className="flex-1 px-4 py-3 bg-white rounded-xl border border-border min-h-0 flex flex-col">
              <div className="flex items-center justify-between mb-3 flex-shrink-0">
                <span className="text-[12px] font-semibold text-text-primary">访问历史</span>
                <span className="text-[11px] font-mono text-text-muted">{tHistory.length} 条</span>
              </div>
              <div className="flex-1 overflow-auto space-y-1">
                {tHistory.length === 0 ? (
                  <div className="text-[12px] text-text-muted py-6 text-center">暂无操作记录</div>
                ) : (
                  tHistory.map((e) => (
                    <div
                      key={e.seq}
                      className={`flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-[11px] font-mono ${
                        e.type === 'ACCESS'
                          ? e.hit
                            ? 'bg-green/8 text-green border border-green/20'
                            : 'bg-red/8 text-red border border-red/20'
                          : e.type === 'ALLOC'
                            ? 'bg-accent/8 text-accent border border-accent/20'
                            : e.type === 'WRITE'
                              ? 'bg-yellow/8 text-yellow border border-yellow/20'
                              : 'bg-blue/8 text-blue border border-blue/20'
                      }`}
                    >
                      <span className="w-5 text-right text-text-muted text-[10px]">{e.seq}</span>
                      <span className={`w-14 font-bold text-[10px] uppercase tracking-wide ${
                        e.type === 'ACCESS' ? (e.hit ? 'text-green' : 'text-red') :
                        e.type === 'ALLOC' ? 'text-accent' :
                        e.type === 'WRITE' ? 'text-yellow' : 'text-blue'
                      }`}>{e.type}</span>
                      <span className="flex-1">页#{e.block}</span>
                      {e.type === 'ACCESS' && (
                        <Badge variant={e.hit ? 'pass' : 'fail'}>{e.hit ? 'HIT' : 'MISS'}</Badge>
                      )}
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
        ) : (
          /* ==================== 运行监控面板 — 全屏双列布局 ==================== */
          <div className="flex-1 flex gap-3 min-h-0">
            {/* Left column: Storage + Slot grid */}
            <div className="flex-1 flex flex-col min-w-0 gap-3">
              {/* Page stats row */}
              {loading && !stats ? (
                <div className="grid grid-cols-4 gap-3 flex-shrink-0">
                  {Array.from({ length: 4 }, (_, i) => (
                    <div key={i} className="px-4 py-3 bg-white rounded-xl border border-border">
                      <Skeleton className="h-3 w-16 mb-1.5" />
                      <Skeleton className="h-5 w-12" />
                    </div>
                  ))}
                </div>
              ) : stats ? (
                <div className="flex-shrink-0">
                  <h2 className="text-[13px] font-semibold text-text-primary mb-2">页式存储管理</h2>
                  <div className="grid grid-cols-4 gap-3">
                    <div className="px-4 py-3 bg-white rounded-xl border border-border border-l-[3px] border-l-accent">
                      <div className="text-[11px] text-text-muted font-mono">页大小</div>
                      <div className="text-[16px] font-bold text-text-primary mt-0.5 font-mono">{stats.pageSize} B</div>
                    </div>
                    <div className="px-4 py-3 bg-white rounded-xl border border-border border-l-[3px] border-l-accent">
                      <div className="text-[11px] text-text-muted font-mono">缓冲池总帧数</div>
                      <div className="text-[16px] font-bold text-text-primary mt-0.5 font-mono">{stats.bufferSize}</div>
                    </div>
                    <div className="px-4 py-3 bg-white rounded-xl border border-border border-l-[3px] border-l-green">
                      <div className="text-[11px] text-text-muted font-mono">已用帧</div>
                      <div className="text-[16px] font-bold text-green mt-0.5 font-mono">{usedFrames}</div>
                    </div>
                    <div className="px-4 py-3 bg-white rounded-xl border border-border border-l-[3px] border-l-yellow">
                      <div className="text-[11px] text-text-muted font-mono">空闲帧</div>
                      <div className="text-[16px] font-bold text-yellow mt-0.5 font-mono">{freeFrames}</div>
                    </div>
                  </div>
                </div>
              ) : null}

              {/* Slot grid */}
              <div className="flex-1 px-4 py-3 bg-white rounded-xl border border-border min-h-0 flex flex-col">
                <div className="flex items-center justify-between mb-3 flex-shrink-0">
                  <span className="text-[13px] font-semibold text-text-primary">页面分配状态</span>
                  <div className="flex items-center gap-3 text-[11px] font-mono text-text-muted">
                    <span className="flex items-center gap-1">
                      <span className="w-2.5 h-2.5 rounded-sm" style={{ backgroundColor: FILE_COLORS[0].light, border: `1px solid ${FILE_COLORS[0].border}` }} />
                      按表着色
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="w-2.5 h-2.5 rounded-sm bg-bg-elevated border border-border" />
                      空闲
                    </span>
                  </div>
                </div>
                <div className="flex-1 overflow-auto">
                  <div className="grid grid-cols-8 gap-2">
                    {slots.map((s) => {
                      const fc = getFileColor(s.file ?? '', s.dirty);
                      return (
                        <div
                          key={s.slot}
                          className={`aspect-square rounded-lg flex flex-col items-center justify-center text-[10px] font-mono border transition-colors cursor-default ${
                            s.file !== null
                              ? 'shadow-sm'
                              : 'bg-bg-elevated border-border text-text-muted'
                          }`}
                          style={s.file !== null && fc ? {
                            backgroundColor: fc.light,
                            borderColor: fc.border,
                            color: fc.text,
                          } : undefined}
                          title={`#${s.slot} ${s.file ?? '空闲'} ${s.file !== null ? `p${s.block}` : ''} ${s.dirty ? '(脏)' : ''}`}
                        >
                          <span className="text-[11px] font-bold">{s.slot}</span>
                          {s.file !== null && (
                            <span className="text-[9px] opacity-70">B{s.block}</span>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>

              {/* Buffer pool details table */}
              <div className="flex-1 min-h-0 flex flex-col">
                <div className="flex items-center justify-between mb-2 flex-shrink-0">
                  <h2 className="text-[13px] font-semibold text-text-primary">缓冲池槽位详情</h2>
                  {slots.length > 0 && (
                    <div className="flex items-center gap-3 text-[11px] font-mono text-text-muted">
                      <span>总帧: {slots.length}</span>
                      <span>已用: {usedFrames}</span>
                      <span>锁定: {pinnedFrames}</span>
                    </div>
                  )}
                </div>

                {slots.length > 0 ? (
                  <div className="flex-1 border border-border rounded-xl overflow-hidden bg-white min-h-0">
                    <div className="overflow-auto h-full">
                      <table className="w-full text-[13px] font-mono">
                        <thead className="sticky top-0 bg-bg-elevated/80 backdrop-blur-sm">
                          <tr className="border-b border-border">
                            <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">帧号</th>
                            <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">文件</th>
                            <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">块号</th>
                            <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">Pin</th>
                            <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">事务号</th>
                            <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">脏页</th>
                            <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">状态</th>
                          </tr>
                        </thead>
                        <tbody>
                          {slots.map((s) => {
                            const fc = getFileColor(s.file ?? '', s.dirty);
                            return (
                            <tr key={s.slot} className={`border-b border-border-subtle last:border-0 transition-colors ${
                              s.file !== null ? 'hover:bg-bg-hover/50' : 'opacity-50'
                            }`}>
                              <td className="px-3 py-2 text-text-muted">
                                <span className="inline-flex items-center gap-1.5">
                                  {s.file !== null && fc ? (
                                    <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: fc.bg }} />
                                  ) : (
                                    <span className="w-2 h-2 rounded-full flex-shrink-0 bg-bg-elevated border border-border" />
                                  )}
                                  #{s.slot}
                                </span>
                              </td>
                              <td className="px-3 py-2 text-text-secondary truncate max-w-[140px]">{s.file ?? '—'}</td>
                              <td className="px-3 py-2 text-text-secondary">{s.file !== null ? s.block : '—'}</td>
                              <td className="px-3 py-2">
                                <span className={`font-semibold ${s.pins > 0 ? 'text-accent' : 'text-text-muted'}`}>
                                  {s.pins}
                                </span>
                              </td>
                              <td className="px-3 py-2 text-text-secondary">{s.txnum >= 0 ? s.txnum : '—'}</td>
                              <td className="px-3 py-2">
                                {s.dirty ? (
                                  <Badge variant="warn">脏</Badge>
                                ) : (
                                  <span className="text-text-muted">—</span>
                                )}
                              </td>
                              <td className="px-3 py-2">
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
                          );
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ) : (
                  <div className="flex-1 px-4 py-10 text-center border border-border border-dashed rounded-xl flex items-center justify-center">
                    <div className="text-[13px] text-text-muted">点击刷新加载缓冲池状态</div>
                  </div>
                )}
              </div>
            </div>

            {/* Right column: Cache stats + Table list + Schema */}
            <div className="w-80 flex flex-col min-w-0 gap-3">
              {/* Cache stats */}
              {cache && (
                <div className="flex-shrink-0">
                  <h2 className="text-[13px] font-semibold text-text-primary mb-2">缓存机制 (LRU)</h2>
                  <div className="grid grid-cols-2 gap-2">
                    <div className="px-3 py-2.5 bg-white rounded-xl border border-border border-l-[3px] border-l-accent">
                      <div className="text-[10px] text-text-muted font-mono">总访问</div>
                      <div className="text-[15px] font-bold text-text-primary font-mono">{cache.accessCount}</div>
                    </div>
                    <div className="px-3 py-2.5 bg-white rounded-xl border border-border border-l-[3px] border-l-green">
                      <div className="text-[10px] text-text-muted font-mono">命中</div>
                      <div className="text-[15px] font-bold text-green font-mono">{cache.hitCount}</div>
                    </div>
                    <div className="px-3 py-2.5 bg-white rounded-xl border border-border border-l-[3px] border-l-red">
                      <div className="text-[10px] text-text-muted font-mono">未命中</div>
                      <div className="text-[15px] font-bold text-red font-mono">{cache.missCount}</div>
                    </div>
                    <div className="px-3 py-2.5 bg-white rounded-xl border border-border border-l-[3px] border-l-yellow">
                      <div className="text-[10px] text-text-muted font-mono">淘汰</div>
                      <div className="text-[15px] font-bold text-yellow font-mono">{cache.evictionCount}</div>
                    </div>
                  </div>

                  <div className="mt-2 px-3 py-2.5 bg-white rounded-xl border border-border">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-[12px] font-semibold text-text-primary">缓存命中率</span>
                      <span className="text-[14px] font-mono text-green font-bold">{hitRate}%</span>
                    </div>
                    <div className="w-full h-2.5 bg-bg-elevated rounded-full overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all duration-700 ease-out bg-green"
                        style={{ width: `${cache.hitRate * 100}%` }}
                      />
                    </div>
                    <div className="flex justify-between mt-1 text-[10px] font-mono text-text-muted">
                      <span>0%</span>
                      <span>50%</span>
                      <span>100%</span>
                    </div>
                  </div>

                  <div className="mt-2 grid grid-cols-2 gap-2">
                    <div className="px-3 py-2.5 bg-white rounded-xl border border-border">
                      <div className="text-[10px] text-text-muted font-mono mb-1">替换策略</div>
                      <Badge variant="info">LRU</Badge>
                    </div>
                    <div className="px-3 py-2.5 bg-white rounded-xl border border-border">
                      <div className="text-[10px] text-text-muted font-mono mb-1">脏页回写</div>
                      <Badge variant={dirtyFrames > 0 ? 'warn' : 'pass'}>
                        {dirtyFrames > 0 ? `${dirtyFrames} 页待写` : '已全部回写'}
                      </Badge>
                    </div>
                  </div>
                </div>
              )}

              {/* Table list */}
              <div className="flex-1 flex flex-col min-h-0">
                <div className="flex items-center justify-between mb-2 flex-shrink-0">
                  <span className="text-[13px] font-semibold text-text-primary">数据库表</span>
                  <span className="text-[11px] font-mono text-text-muted">{tableList.length} 个</span>
                </div>
                <div className="flex-1 overflow-auto bg-white rounded-xl border border-border">
                  {loading && !stats ? (
                    <div className="px-4 space-y-2 py-3">
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
                          className={`w-full text-left px-4 py-2.5 text-[13px] font-mono flex items-center gap-2 transition-colors border-b border-border-subtle last:border-0 ${
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

              {/* Table schema */}
              {selectedTable && (
                <div className="flex-1 min-h-0 flex flex-col">
                  <h2 className="text-[13px] font-semibold text-text-primary mb-2">
                    表结构: {selectedTable}
                  </h2>
                  {schemaLoading ? (
                    <Skeleton className="h-20 w-full" />
                  ) : tableSchema ? (
                    <div className="flex-1 border border-border rounded-xl overflow-hidden bg-white min-h-0">
                      <div className="overflow-auto h-full">
                        <table className="w-full text-[13px] font-mono">
                          <thead className="sticky top-0 bg-bg-elevated/80 backdrop-blur-sm">
                            <tr className="border-b border-border">
                              <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">列名</th>
                              <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">类型</th>
                              <th className="px-3 py-2.5 text-left text-[11px] font-semibold text-text-muted uppercase tracking-wider">长度</th>
                            </tr>
                          </thead>
                          <tbody>
                            {tableSchema.map((col) => (
                              <tr key={col.name} className="border-b border-border-subtle last:border-0 hover:bg-bg-hover/50 transition-colors">
                                <td className="px-3 py-2 text-text-primary font-medium">{col.name}</td>
                                <td className="px-3 py-2 text-text-secondary">{col.type}</td>
                                <td className="px-3 py-2 text-text-muted">{col.length}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  ) : (
                    <div className="text-[13px] text-text-muted">无法加载表结构</div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
    </div>
  );
}
