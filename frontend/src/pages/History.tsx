import { useState, useCallback } from 'react';
import { api } from '../api/client';
import type { ExecuteResponse } from '../api/types';
import ResultTable from '../components/ResultTable';
import EmptyState from '../components/EmptyState';
import Badge from '../components/Badge';
import Button from '../components/Button';

interface HistoryEntry {
  sql: string;
  result: ExecuteResponse;
  timing: number;
  timestamp: string;
}

export default function History() {
  const [history, setHistory] = useState<HistoryEntry[]>(() => {
    try {
      return JSON.parse(localStorage.getItem('minisql-history') || '[]');
    } catch {
      return [];
    }
  });
  const [selected, setSelected] = useState<HistoryEntry | null>(null);
  const [replayed, setReplayed] = useState<ExecuteResponse | null>(null);
  const [replaying, setReplaying] = useState(false);
  const [search, setSearch] = useState('');

  const replay = useCallback(async (entry: HistoryEntry) => {
    setReplaying(true);
    setReplayed(null);
    try {
      const res = await api.execute(entry.sql);
      setReplayed(res);
    } catch (e) {
      setReplayed({ type: 'UPDATE', error: { type: 'Exception', message: String(e) } });
    } finally {
      setReplaying(false);
    }
  }, []);

  const clearHistory = useCallback(() => {
    localStorage.removeItem('minisql-history');
    setHistory([]);
    setSelected(null);
  }, []);

  const filteredHistory = search
    ? history.filter((e) => e.sql.toLowerCase().includes(search.toLowerCase()))
    : history;

  const activeResult = replayed || selected?.result;

  return (
    <div className="flex gap-4 h-full">
      {/* List panel */}
      <div className="w-80 flex-shrink-0 bg-white rounded-xl border border-border flex flex-col overflow-hidden">
        <div className="px-4 py-3 border-b border-border flex items-center justify-between">
          <span className="text-[12px] font-semibold text-text-primary">
            查询历史 ({history.length})
          </span>
          {history.length > 0 && (
            <Button variant="ghost" size="sm" onClick={clearHistory}>清空</Button>
          )}
        </div>

        {/* Search */}
        {history.length > 0 && (
          <div className="px-3 py-2.5 border-b border-border-subtle">
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索查询..."
              className="w-full bg-bg-input text-text-primary text-[13px] font-mono px-2.5 py-1.5 rounded-lg border border-border focus:border-accent focus:outline-none placeholder:text-text-muted/50 transition-colors"
            />
          </div>
        )}

        <div className="flex-1 overflow-auto">
          {filteredHistory.length === 0 ? (
            <div className="p-4">
              <EmptyState icon="↻" title="暂无历史" description="在 SQL 控制台执行查询以建立历史记录" />
            </div>
          ) : (
            <div>
              {filteredHistory.map((entry, i) => (
                <button
                  key={i}
                  onClick={() => { setSelected(entry); setReplayed(null); }}
                  className={`w-full text-left px-4 py-3 border-b border-border-subtle transition-colors ${
                    selected === entry ? 'bg-accent/8 border-l-[3px] border-l-accent' : 'hover:bg-bg-hover/50 border-l-[3px] border-l-transparent'
                  }`}
                >
                  <div className="flex items-center justify-between mb-0.5">
                    <span className="text-[11px] font-mono text-text-muted">{entry.timestamp}</span>
                    <div className="flex items-center gap-1.5">
                      <span className="text-[11px] font-mono text-text-muted">{entry.timing}ms</span>
                      {entry.result.error ? (
                        <Badge variant="error">ERR</Badge>
                      ) : entry.result.type === 'QUERY' ? (
                        <Badge variant="pass">{entry.result.rows?.length ?? 0} rows</Badge>
                      ) : (
                        <Badge variant="info">OK</Badge>
                      )}
                    </div>
                  </div>
                  <div className="font-mono text-[12px] text-text-secondary truncate">{entry.sql}</div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Detail panel */}
      <div className="flex-1 bg-white rounded-xl border border-border p-4 overflow-auto">
        {selected ? (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <span className="text-[12px] font-semibold text-text-primary">SQL</span>
              <Button variant="ghost" size="sm" onClick={() => replay(selected)} disabled={replaying}>
                {replaying ? '重放中...' : '重放'}
              </Button>
            </div>
            <pre className="p-3 bg-bg-input rounded-lg border border-border font-mono text-[13px] text-text-primary overflow-x-auto whitespace-pre-wrap">
              {selected.sql}
            </pre>

            {activeResult?.error && (
              <div className="px-3 py-2 rounded-lg bg-red-muted font-mono text-[13px] text-red">
                {activeResult.error.message}
              </div>
            )}

            {activeResult?.type === 'QUERY' && activeResult.columns && (
              <div>
                <span className="text-[12px] font-semibold text-text-primary block mb-2">结果</span>
                <ResultTable columns={activeResult.columns} rows={activeResult.rows || []} />
              </div>
            )}

            {activeResult?.type === 'UPDATE' && !activeResult.error && (
              <div className="text-[13px] font-mono text-text-secondary">
                语句执行成功。
                {activeResult.affectedRows !== undefined && ` ${activeResult.affectedRows} 行受影响。`}
              </div>
            )}
          </div>
        ) : (
          <EmptyState icon="↻" title="选择查询" description="点击历史记录查看详情" />
        )}
      </div>
    </div>
  );
}
