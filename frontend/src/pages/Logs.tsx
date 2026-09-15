import { useRef, useState, useCallback, useEffect } from 'react';
import { api } from '../api/client';
import Button from '../components/Button';
import EmptyState from '../components/EmptyState';

interface LogEntry {
  timestamp: string;
  level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG';
  message: string;
}

const levelColor: Record<string, string> = {
  DEBUG: 'text-text-muted',
  INFO: 'text-blue',
  WARN: 'text-yellow',
  ERROR: 'text-red',
};

const levelBg: Record<string, string> = {
  DEBUG: 'bg-bg-elevated',
  INFO: 'bg-blue-muted',
  WARN: 'bg-yellow-muted',
  ERROR: 'bg-red-muted',
};

export default function Logs() {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [input, setInput] = useState('');
  const [running, setRunning] = useState(false);
  const [levelFilter, setLevelFilter] = useState<string>('ALL');
  const scrollRef = useRef<HTMLDivElement>(null);

  const addLog = useCallback((level: LogEntry['level'], message: string) => {
    const now = new Date();
    const ts = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}:${now.getSeconds().toString().padStart(2, '0')}.${now.getMilliseconds().toString().padStart(3, '0')}`;
    setLogs((prev) => [...prev, { timestamp: ts, level, message }]);
  }, []);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [logs]);

  const executeSql = useCallback(async () => {
    const sql = input.trim();
    if (!sql || running) return;

    setRunning(true);
    addLog('INFO', `收到查询: ${sql}`);

    try {
      addLog('DEBUG', '开始词法分析...');
      const tokens = await api.tokens(sql);
      if (tokens.error) {
        addLog('ERROR', `词法错误: ${tokens.error.message}`);
      } else {
        addLog('DEBUG', `词法分析完成: ${tokens.tokens?.length ?? 0} 个词法单元`);
      }

      addLog('DEBUG', '语法分析...');
      addLog('DEBUG', '语义分析...');

      addLog('INFO', '执行查询...');
      const result = await api.execute(sql);

      if (result.error) {
        addLog('ERROR', `${result.error.type}: ${result.error.message}`);
      } else if (result.type === 'QUERY') {
        addLog('INFO', `查询完成: ${result.rows?.length ?? 0} 行${result.timing ? `，${result.timing}ms` : ''}`);
      } else {
        addLog('INFO', `语句执行完成: ${result.affectedRows ?? 0} 行受影响`);
      }
    } catch (e) {
      addLog('ERROR', `异常: ${e}`);
    } finally {
      setRunning(false);
      setInput('');
    }
  }, [input, running, addLog]);

  const filteredLogs = levelFilter === 'ALL' ? logs : logs.filter((l) => l.level === levelFilter);

  return (
    <div className="flex flex-col gap-4 h-full">
      {/* Input bar */}
      <div className="bg-white rounded-xl border border-border flex items-center gap-3 px-4 py-3 flex-shrink-0">
        <div className="flex-1 flex items-center gap-2 bg-bg-input rounded-lg border border-border focus-within:border-accent transition-colors">
          <span className="pl-2.5 text-text-muted/50 text-[13px] font-mono">{'>'}</span>
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && executeSql()}
            placeholder="输入 SQL 跟踪执行..."
            className="flex-1 bg-transparent text-text-primary text-[13px] font-mono py-2 pr-3 outline-none placeholder:text-text-muted/40"
          />
        </div>
        <Button onClick={executeSql} disabled={running || !input.trim()} size="sm">
          {running ? '执行中...' : '执行'}
        </Button>
        <Button variant="ghost" size="sm" onClick={() => setLogs([])} disabled={logs.length === 0}>
          清空
        </Button>
      </div>

      {/* Log output */}
      <div className="flex-1 min-h-0 bg-white rounded-xl border border-border overflow-hidden flex flex-col">
        {/* Level filter tabs */}
        {logs.length > 0 && (
          <div className="flex items-center gap-0 px-4 border-b border-border bg-bg-elevated/20 flex-shrink-0">
            {(['ALL', 'DEBUG', 'INFO', 'WARN', 'ERROR'] as const).map((level) => (
              <button
                key={level}
                onClick={() => setLevelFilter(level)}
                className={`px-3 py-2 text-[12px] font-mono border-b-2 transition-colors ${
                  levelFilter === level
                    ? 'border-accent text-accent font-semibold'
                    : 'border-transparent text-text-muted hover:text-text-secondary'
                }`}
              >
                {level}
                {level !== 'ALL' && (
                  <span className="ml-1 text-[10px] opacity-60">
                    ({logs.filter((l) => l.level === level).length})
                  </span>
                )}
              </button>
            ))}
            <span className="ml-auto text-[11px] font-mono text-text-muted">
              {filteredLogs.length} 条日志
            </span>
          </div>
        )}

        {/* Log entries */}
        <div ref={scrollRef} className="flex-1 min-h-0 overflow-auto">
          {logs.length === 0 ? (
            <div className="p-4">
              <EmptyState icon="≡" title="暂无日志" description="执行 SQL 查询以查看执行跟踪" />
            </div>
          ) : (
            <div className="font-mono text-[13px] leading-relaxed">
              {filteredLogs.map((log, i) => (
                <div key={i} className="flex gap-3 px-4 py-1.5 hover:bg-bg-hover/40 transition-colors">
                  <span className="text-text-muted/50 flex-shrink-0 w-[100px]">{log.timestamp}</span>
                  <span className={`w-14 flex-shrink-0 font-semibold text-center px-1.5 py-0.5 rounded text-[11px] ${levelColor[log.level]} ${levelBg[log.level]}`}>{log.level}</span>
                  <span className="text-text-secondary">{log.message}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
