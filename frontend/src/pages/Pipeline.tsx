import { useState } from 'react';
import { api } from '../api/client';
import type { TokenInfo, ExplainResponse, ExecuteResponse } from '../api/types';

const EXAMPLE_SQL = "SELECT name, age\nFROM student\nWHERE age > 18;";

interface PipelineResult {
  tokens: TokenInfo[] | null;
  explain: ExplainResponse | null;
  execute: ExecuteResponse | null;
}

function TokenTable({ tokens }: { tokens: TokenInfo[] }) {
  const typeColors: Record<string, string> = {
    SELECT: 'text-blue', FROM: 'text-blue', WHERE: 'text-blue',
    AND: 'text-blue', OR: 'text-blue', NOT: 'text-blue',
    INSERT: 'text-blue', INTO: 'text-blue', VALUES: 'text-blue',
    DELETE: 'text-blue', CREATE: 'text-blue', TABLE: 'text-blue',
    IDENTIFIER: 'text-green', INT_CONST: 'text-accent', STRING_CONST: 'text-accent',
    '=': 'text-text-secondary', '!=': 'text-text-secondary', '>': 'text-text-secondary',
    '<': 'text-text-secondary', '>=': 'text-text-secondary', '<=': 'text-text-secondary',
  };
  return (
    <div className="overflow-auto max-h-64">
      <table className="w-full text-xs font-mono">
        <thead>
          <tr className="border-b border-border">
            <th className="px-2 py-1 text-left text-text-muted">类型</th>
            <th className="px-2 py-1 text-left text-text-muted">词素</th>
            <th className="px-2 py-1 text-right text-text-muted">行</th>
            <th className="px-2 py-1 text-right text-text-muted">列</th>
          </tr>
        </thead>
        <tbody>
          {tokens.map((t, i) => (
            <tr key={i} className="border-b border-border-subtle">
              <td className={`px-2 py-0.5 ${typeColors[t.type] || 'text-text-secondary'}`}>{t.type}</td>
              <td className="px-2 py-0.5 text-text-primary">{t.lexeme}</td>
              <td className="px-2 py-0.5 text-right text-text-muted">{t.line}</td>
              <td className="px-2 py-0.5 text-right text-text-muted">{t.column}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PlanTree({ text, title }: { text: string; title: string }) {
  return (
    <div>
      <div className="text-xs text-text-muted font-mono mb-2">{title}</div>
      <pre className="text-xs font-mono text-text-primary bg-bg-primary border border-border rounded p-2 whitespace-pre-wrap overflow-auto max-h-48">
        {text}
      </pre>
    </div>
  );
}

export default function Pipeline() {
  const [sql, setSql] = useState(EXAMPLE_SQL);
  const [result, setResult] = useState<PipelineResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [activeStage, setActiveStage] = useState<number | null>(null);

  const trace = async () => {
    if (!sql.trim()) return;
    setLoading(true);
    setResult(null);
    try {
      const [tokensRes, explainRes, execRes] = await Promise.all([
        api.tokens(sql),
        api.explain(sql),
        api.execute(sql),
      ]);
      setResult({
        tokens: tokensRes.tokens || null,
        explain: explainRes.error ? null : explainRes,
        execute: execRes,
      });
    } catch {
      setResult({ tokens: null, explain: null, execute: null });
    } finally {
      setLoading(false);
    }
  };

  const stages = [
    {
      name: '词法单元流',
      ready: !!result?.tokens,
      content: result?.tokens && <TokenTable tokens={result.tokens} />,
    },
    {
      name: '计划（优化前）',
      ready: !!result?.explain,
      content: result?.explain && <PlanTree text={result.explain.planBefore} title="未优化的计划" />,
    },
    {
      name: '计划（优化后）',
      ready: !!result?.explain,
      content: result?.explain && <PlanTree text={result.explain.planAfter} title="优化后的计划" />,
    },
    {
      name: '执行结果',
      ready: !!result?.execute,
      content: result?.execute && !result.execute.error && (
        <div>
          {result.execute.type === 'QUERY' && result.execute.columns && result.execute.rows && (
            <div className="overflow-auto max-h-48">
              <table className="w-full text-xs font-mono">
                <thead>
                  <tr className="border-b border-border">
                    {result.execute.columns.map((c) => (
                      <th key={c} className="px-2 py-1 text-left text-accent">{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {result.execute.rows.map((row, i) => (
                    <tr key={i} className="border-b border-border-subtle">
                      {row.map((v, j) => (
                        <td key={j} className="px-2 py-0.5 text-text-primary">{String(v)}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {result.execute.type === 'UPDATE' && (
            <div className="text-green text-xs font-mono">
              {result.execute.affectedRows} rows affected
            </div>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-2 border-b border-border bg-bg-surface">
        <span className="text-blue font-mono text-sm">→</span>
        <span className="text-sm font-semibold">流水线浏览器</span>
        <span className="text-xs text-text-muted">— Visualize the complete SQL compilation pipeline</span>
      </div>

      {/* SQL Input */}
      <div className="px-4 py-3 border-b border-border bg-bg-surface">
        <div className="flex gap-2">
          <textarea
            value={sql}
            onChange={(e) => setSql(e.target.value)}
            className="flex-1 bg-bg-primary text-text-primary px-3 py-2 font-mono text-sm rounded border border-border focus:border-accent focus:outline-none resize-none"
            rows={2}
            placeholder="输入 SQL 以查看流水线处理过程..."
          />
          <button
            onClick={trace}
            disabled={loading}
            className="px-4 py-2 bg-accent text-bg-primary rounded text-sm font-medium hover:bg-accent-hover transition-colors disabled:opacity-50 self-end"
          >
            {loading ? '追踪中…' : '追踪'}
          </button>
        </div>
      </div>

      {/* Pipeline Stages */}
      <div className="flex-1 overflow-auto p-4">
        {!result && !loading && (
          <div className="text-text-muted text-sm text-center mt-20">
            输入 SQL 后点击「追踪」查看流水线处理过程。
          </div>
        )}

        {result && (
          <div className="grid grid-cols-2 gap-3">
            {stages.map((stage, i) => (
              <div
                key={i}
                className={`border rounded-lg overflow-hidden transition-colors cursor-pointer ${
                  activeStage === i ? 'border-accent' : 'border-border'
                } ${stage.ready ? 'bg-bg-surface' : 'bg-bg-surface/50'}`}
                onClick={() => setActiveStage(activeStage === i ? null : i)}
              >
                <div className="flex items-center justify-between px-3 py-2 border-b border-border">
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-mono text-text-muted">{i + 1}.</span>
                    <span className="text-sm font-medium text-text-primary">{stage.name}</span>
                  </div>
                  <span className={`w-2 h-2 rounded-full ${stage.ready ? 'bg-green' : 'bg-text-muted'}`} />
                </div>
                {stage.ready && activeStage === i && (
                  <div className="p-3">{stage.content}</div>
                )}
                {stage.ready && activeStage !== i && (
                  <div className="px-3 py-2 text-xs text-text-muted font-mono">
                    {stage.name === '词法单元流' && `${(result?.tokens || []).length} 个词法单元`}
                    {stage.name === '计划（优化前）' && '点击展开'}
                    {stage.name === '计划（优化后）' && result?.explain ? `${result.explain.blocksAccessed} 个块` : '点击展开'}
                    {stage.name === '执行结果' && result?.execute?.type === 'QUERY' ? `${result.execute.rows?.length ?? 0} 行` : '点击展开'}
                  </div>
                )}
                {!stage.ready && (
                  <div className="px-3 py-4 text-xs text-text-muted text-center">
                    等待数据…
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
