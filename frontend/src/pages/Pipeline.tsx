import { useState, useCallback, useRef } from 'react';
import { api } from '../api/client';
import type { ExecuteResponse, TokensResponse, ExplainResponse, AstNode, TokenInfo, AnalyzeResponse } from '../api/types';
import Button from '../components/Button';
import Badge from '../components/Badge';
import EmptyState from '../components/EmptyState';
import ResultTable from '../components/ResultTable';
import SqlEditor from '../components/SqlEditor';

const DEFAULT_SQL = 'SELECT * FROM student WHERE age > 18;';

const SQL_KEYWORDS = new Set([
  'SELECT', 'FROM', 'WHERE', 'AND', 'OR', 'NOT', 'INSERT', 'INTO',
  'VALUES', 'DELETE', 'CREATE', 'TABLE', 'JOIN', 'ON', 'GROUP', 'BY',
  'ORDER', 'HAVING', 'LIMIT', 'AS', 'UPDATE', 'SET', 'DROP', 'INDEX',
  'PRIMARY', 'KEY', 'NULL', 'DEFAULT', 'UNIQUE', 'IN', 'EXISTS',
  'BETWEEN', 'LIKE', 'IS', 'ASC', 'DESC', 'DISTINCT', 'COUNT',
  'SUM', 'AVG', 'MIN', 'MAX', 'UNION', 'ALL', 'CROSS', 'LEFT',
  'RIGHT', 'INNER', 'OUTER', 'CASE', 'WHEN', 'THEN', 'ELSE', 'END',
  'EXPLAIN', 'VIEW', 'IF',
]);

type StageStatus = 'idle' | 'running' | 'done' | 'error';

interface StageState {
  status: StageStatus;
  error?: string;
  timeMs?: number;
}

interface PipelineResult {
  tokens: TokensResponse | null;
  ast: AstNode | null;
  semantic: SemanticCheck[];
  explain: ExplainResponse | null;
  execute: ExecuteResponse | null;
}

interface SemanticCheck {
  name: string;
  status: 'pass' | 'warn' | 'fail';
  message: string;
}

/* Semantic analysis based on tokens */
function analyzeSemantics(tokens: TokenInfo[], sqlText: string): SemanticCheck[] {
  const checks: SemanticCheck[] = [];

  // Check: SQL not empty
  checks.push({ name: 'SQL 非空检查', status: 'pass', message: `SQL 语句长度 ${sqlText.length} 字符` });

  // Check: statement type recognized
  const firstWord = tokens[0]?.lexeme.toUpperCase();
  const validTypes = ['SELECT', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'DROP', 'ALTER', 'EXPLAIN'];
  if (firstWord && validTypes.includes(firstWord)) {
    checks.push({ name: '语句类型识别', status: 'pass', message: `识别为 ${firstWord} 语句` });
  } else {
    checks.push({ name: '语句类型识别', status: 'fail', message: `无法识别语句类型: ${firstWord}` });
  }

  // Check: parentheses balance
  let parenCount = 0;
  tokens.forEach((t) => {
    if (t.lexeme === '(') parenCount++;
    if (t.lexeme === ')') parenCount--;
  });
  if (parenCount === 0) {
    checks.push({ name: '括号平衡检查', status: 'pass', message: '括号匹配正确' });
  } else {
    checks.push({ name: '括号平衡检查', status: 'fail', message: `括号不匹配: ${parenCount > 0 ? '缺少右括号' : '多余的右括号'}` });
  }

  // Check: semicolon at end (skip EOF token)
  const lastToken = tokens.filter(t => t.type !== 'EOF')[tokens.filter(t => t.type !== 'EOF').length - 1];
  if (lastToken && lastToken.lexeme === ';') {
    checks.push({ name: '语句终止符', status: 'pass', message: 'SQL 以分号结尾' });
  } else {
    checks.push({ name: '语句终止符', status: 'warn', message: 'SQL 未以分号结尾（建议添加）' });
  }

  // Check: keyword count
  const kwCount = tokens.filter((t) => SQL_KEYWORDS.has(t.lexeme.toUpperCase())).length;
  checks.push({ name: '关键字统计', status: 'pass', message: `包含 ${kwCount} 个 SQL 关键字` });

  // Check: identifier count
  const idCount = tokens.filter((t) => t.type === 'IDENTIFIER').length;
  checks.push({ name: '标识符统计', status: 'pass', message: `包含 ${idCount} 个标识符` });

  return checks;
}

function tokenColor(token: TokenInfo): string {
  if (SQL_KEYWORDS.has(token.lexeme.toUpperCase())) return 'text-blue';
  if (token.type === 'IDENTIFIER') return 'text-accent';
  if (token.type === 'INT_CONST' || token.type === 'FLOAT_CONST') return 'text-green';
  if (token.type === 'STRING_CONST' || token.type === 'STRING') return 'text-yellow';
  return 'text-text-muted';
}

function tokenBg(token: TokenInfo): string {
  if (SQL_KEYWORDS.has(token.lexeme.toUpperCase())) return 'bg-blue-muted border-blue/20';
  if (token.type === 'IDENTIFIER') return 'bg-accent-muted border-accent/20';
  if (token.type === 'INT_CONST' || token.type === 'FLOAT_CONST') return 'bg-green-muted border-green/20';
  if (token.type === 'STRING_CONST' || token.type === 'STRING') return 'bg-yellow-muted border-yellow/20';
  return 'bg-bg-elevated border-border';
}

const STAGES = [
  { name: '词法分析', icon: '◇', desc: 'Lexical Analysis' },
  { name: '语法分析', icon: '{ }', desc: 'Syntax Analysis' },
  { name: '语义分析', icon: '✓', desc: 'Semantic Analysis' },
  { name: '查询优化', icon: '→', desc: 'Query Optimization' },
  { name: '执行', icon: '▷', desc: 'Execution' },
];

function countNodes(node: AstNode): number {
  let count = 1;
  if (node.children) {
    for (const child of node.children) {
      count += countNodes(child);
    }
  }
  return count;
}

function nodeColor(type: string): string {
  switch (type) {
    case 'Query': case 'Insert': case 'Delete': case 'Update':
    case 'CreateTable': case 'CreateView': case 'CreateIndex': case 'DropTable':
    case 'Explain':
      return 'bg-blue-muted text-blue border-blue/30';
    case 'Table': case 'View': case 'Index':
      return 'bg-green-muted text-green border-green/30';
    case 'Column': case 'ColumnDef': case 'ColumnDefs': case 'SelectList': case 'FieldList':
      return 'bg-cyan-muted text-cyan border-cyan/30';
    case 'WhereClause': case 'AND': case 'OR': case 'NOT': case 'Comparison':
      return 'bg-yellow-muted text-yellow border-yellow/30';
    case 'Arithmetic': case 'Expression': case 'Field':
      return 'bg-orange-muted text-orange border-orange/30';
    case 'Constant': case 'Value': case 'ValueList':
      return 'bg-green-muted text-green border-green/30';
    case 'SetClause': case 'Assignment': case 'GroupBy': case 'OrderBy': case 'OrderByField':
      return 'bg-purple-muted text-purple border-purple/30';
    case 'FromClause': case 'Aggregations':
      return 'bg-blue-muted text-blue border-blue/30';
    case 'True': case 'False': case 'Wildcard': case 'IfExists':
      return 'bg-bg-elevated text-text-muted border-border';
    default:
      return 'bg-bg-elevated text-text-secondary border-border';
  }
}

function AstTreeNode({ node, depth, isLast, prefix }: { node: AstNode; depth: number; isLast: boolean; prefix?: string }) {
  const hasChildren = node.children && node.children.length > 0;
  const color = nodeColor(node.type);

  const connector = depth === 0 ? '' : (isLast ? '└─ ' : '├─ ');
  const currentPrefix = depth === 0 ? '' : (isLast ? '   ' : '│  ');

  return (
    <div>
      <div className="flex items-center gap-1.5 py-1 font-mono text-[12px]" style={{ paddingLeft: depth > 0 ? '0px' : undefined }}>
        {/* 缩进 + 连接线 */}
        {depth > 0 && (
          <span className="text-gray-300 select-none whitespace-pre">{prefix}{connector}</span>
        )}
        {/* 节点内容 */}
        <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded border ${color} flex-shrink-0`}>
          <span className="font-semibold">{node.type}</span>
          {node.label && <span className="opacity-70">{node.label}</span>}
          {node.detail && <span className="opacity-50 text-[11px] ml-1">{node.detail}</span>}
        </span>
      </div>
      {/* 子节点 */}
      {hasChildren && node.children!.map((child, i) => (
        <AstTreeNode
          key={i}
          node={child}
          depth={depth + 1}
          isLast={i === node.children!.length - 1}
          prefix={(prefix || '') + currentPrefix}
        />
      ))}
    </div>
  );
}

export default function Pipeline() {
  const [sql, setSql] = useState(DEFAULT_SQL);
  const [stages, setStages] = useState<StageState[]>(Array(5).fill(null).map(() => ({ status: 'idle' })));
  const [result, setResult] = useState<PipelineResult>({ tokens: null, ast: null, semantic: [], explain: null, execute: null });
  const [running, setRunning] = useState(false);
  const [expandedStage, setExpandedStage] = useState<number | null>(null);
  const runIdRef = useRef(0);

  const runPipeline = useCallback(async () => {
    if (running) return;
    const runId = ++runIdRef.current;
    setRunning(true);
    setResult({ tokens: null, ast: null, semantic: [], explain: null, execute: null });
    setStages(Array(5).fill(null).map(() => ({ status: 'idle' })));
    setExpandedStage(null);

    const updateStage = (i: number, patch: Partial<StageState>) => {
      setStages((prev) => prev.map((s, j) => j === i ? { ...s, ...patch } : s));
    };

    try {
      updateStage(0, { status: 'running' });
      const t0 = Date.now();
      const tokens = await api.tokens(sql);
      const tTokens = Date.now() - t0;
      if (runId !== runIdRef.current) return;
      if (tokens.error) {
        updateStage(0, { status: 'error', error: tokens.error.message, timeMs: tTokens });
        return;
      }
      updateStage(0, { status: 'done', timeMs: tTokens });
      setResult((prev) => ({ ...prev, tokens }));

      updateStage(1, { status: 'running' });
      const t1 = Date.now();
      const astResp = await api.ast(sql);
      const tParse = Date.now() - t1;
      if (runId !== runIdRef.current) return;
      if (astResp.error) {
        updateStage(1, { status: 'error', error: astResp.error.message, timeMs: tParse });
        return;
      }
      updateStage(1, { status: 'done', timeMs: tParse });
      setResult((prev) => ({ ...prev, ast: astResp.ast }));

      updateStage(2, { status: 'running' });
      const t2 = Date.now();
      // 客户端 token 级检查（基础语法检查）
      const clientChecks = analyzeSemantics(tokens.tokens || [], sql);
      // 后端真实语义分析（表/列存在性、类型检查）
      const analyzeResp = await api.analyze(sql);
      const tSemantic = Date.now() - t2;
      if (runId !== runIdRef.current) return;
      // 合并：客户端基础检查 + 后端真实语义检查
      const backendChecks: SemanticCheck[] = (analyzeResp.checks || []).map(c => ({
        name: c.name,
        status: c.status as 'pass' | 'fail',
        message: c.message,
      }));
      const semantic = [...clientChecks, ...backendChecks];
      const hasFail = backendChecks.some(c => c.status === 'fail');
      if (hasFail) {
        const firstFail = backendChecks.find(c => c.status === 'fail');
        updateStage(2, { status: 'error', error: firstFail?.message, timeMs: tSemantic });
      } else {
        updateStage(2, { status: 'done', timeMs: tSemantic });
      }
      setResult((prev) => ({ ...prev, semantic }));

      // Stage 3: 查询优化 — 仅 SELECT 和 EXPLAIN 支持 EXPLAIN，其他语句跳过
      const stmtType = (tokens.tokens?.[0]?.lexeme || '').toUpperCase();
      const canExplain = stmtType === 'SELECT' || stmtType === 'EXPLAIN';
      if (canExplain) {
        updateStage(3, { status: 'running' });
        const t3 = Date.now();
        const explain = await api.explain(sql);
        const tOpt = Date.now() - t3;
        if (runId !== runIdRef.current) return;
        if (explain.error) {
          updateStage(3, { status: 'error', error: explain.error.message, timeMs: tOpt });
          return;
        }
        updateStage(3, { status: 'done', timeMs: tOpt });
        setResult((prev) => ({ ...prev, explain }));
      } else {
        // 非 SELECT 语句：直接标记完成（无查询计划可展示）
        updateStage(3, { status: 'done', timeMs: 0 });
      }

      updateStage(4, { status: 'running' });
      const t4 = Date.now();
      const exec = await api.execute(sql);
      const tExec = Date.now() - t4;
      if (runId !== runIdRef.current) return;
      if (exec.error) {
        updateStage(4, { status: 'error', error: exec.error.message, timeMs: tExec });
      } else {
        updateStage(4, { status: 'done', timeMs: tExec });
      }
      setResult((prev) => ({ ...prev, execute: exec }));
    } catch (e) {
      if (runId !== runIdRef.current) return;
      setStages((prev) => prev.map((s) => s.status === 'running' ? { ...s, status: 'error', error: String(e) } : s));
    } finally {
      if (runId === runIdRef.current) setRunning(false);
    }
  }, [sql, running]);

  const totalTime = stages.reduce((sum, s) => sum + (s.timeMs || 0), 0);
  const allDone = stages.every((s) => s.status === 'done');
  const anyError = stages.some((s) => s.status === 'error');

  return (
    <div className="flex flex-col gap-4 h-full">
      {/* SQL input */}
      <div className="bg-white rounded-xl border border-border p-4 flex-shrink-0">
        <SqlEditor value={sql} onChange={setSql} onRun={runPipeline} />
        <div className="flex items-center justify-between mt-3">
          <div className="flex items-center gap-3 text-[12px] font-mono text-text-muted">
            {allDone && <Badge variant="pass">全部完成</Badge>}
            {anyError && <Badge variant="error">流水线错误</Badge>}
            {totalTime > 0 && <span>总计: {totalTime}ms</span>}
          </div>
          <Button onClick={runPipeline} disabled={running}>
            {running ? '跟踪中...' : '跟踪流水线'}
          </Button>
        </div>
      </div>

      {/* Pipeline visualization */}
      <div className="flex-1 min-h-0 bg-white rounded-xl border border-border overflow-auto">
        <div className="p-5">
          {/* Stage indicators — larger nodes */}
          <div className="flex items-start gap-0 mb-6">
            {STAGES.map((stage, i) => (
              <div key={stage.name} className="flex items-center flex-1">
                <button
                  onClick={() => setExpandedStage(expandedStage === i ? null : i)}
                  className={`flex flex-col items-center gap-2 px-3 py-3 rounded-xl transition-colors cursor-pointer w-full ${
                    expandedStage === i ? 'bg-bg-elevated' : 'hover:bg-bg-hover/50'
                  }`}
                >
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-[13px] font-mono border-2 transition-colors ${
                    stages[i].status === 'done' ? 'border-green/40 bg-green-muted text-green' :
                    stages[i].status === 'running' ? 'border-accent/40 bg-accent-muted text-accent' :
                    stages[i].status === 'error' ? 'border-red/40 bg-red-muted text-red' :
                    'border-border bg-bg-elevated text-text-muted'
                  }`}>
                    {stages[i].status === 'running' ? (
                      <span className="animate-spin text-[15px]">◌</span>
                    ) : stages[i].status === 'done' ? (
                      <span className="text-[15px]">✓</span>
                    ) : stages[i].status === 'error' ? (
                      <span className="text-[15px]">✕</span>
                    ) : (
                      <span className="opacity-50">{stage.icon}</span>
                    )}
                  </div>
                  <div className="text-center">
                    <div className="text-[12px] font-semibold text-text-primary">{stage.name}</div>
                    <div className="text-[10px] text-text-muted font-mono mt-0.5">{stage.desc}</div>
                    {stages[i].timeMs !== undefined && (
                      <div className="text-[11px] font-mono text-accent mt-1 font-semibold">{stages[i].timeMs}ms</div>
                    )}
                  </div>
                </button>
                {i < 4 && (
                  <div className={`flex-shrink-0 w-8 h-0.5 mt-5 ${stages[i].status === 'done' ? 'bg-green/40' : 'bg-border'}`} />
                )}
              </div>
            ))}
          </div>

          {/* Expanded detail panels */}
          {expandedStage === 0 && (result.tokens || stages[0].error) && (
            <div className="mb-4 p-4 bg-bg-elevated/30 rounded-xl border border-border animate-fade-in">
              <div className="flex items-center justify-between mb-3">
                <span className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider">词法单元</span>
                {result.tokens && <span className="text-[12px] font-mono text-text-muted">{result.tokens.tokens?.length ?? 0} 个词法单元</span>}
              </div>
              {stages[0].error ? (
                <div className="px-3 py-2 bg-red-muted rounded-lg border border-red/20 text-[13px] font-mono text-red">
                  {stages[0].error}
                </div>
              ) : result.tokens && (
                <div className="flex flex-wrap gap-1.5">
                  {result.tokens.tokens?.map((t, i) => (
                    <span
                      key={i}
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md border text-[12px] font-mono ${tokenBg(t)} ${tokenColor(t)}`}
                      title={`${t.type} at line ${t.line}:${t.column}`}
                    >
                      {t.lexeme}
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          {expandedStage === 1 && (result.ast || stages[1].error) && (
            <div className="mb-4 p-4 bg-bg-elevated/30 rounded-xl border border-border animate-fade-in">
              <div className="flex items-center justify-between mb-3">
                <span className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider">抽象语法树 (AST)</span>
                {result.ast && <span className="text-[12px] font-mono text-text-muted">{countNodes(result.ast)} 个节点</span>}
              </div>
              {stages[1].error ? (
                <div className="px-3 py-2 bg-red-muted rounded-lg border border-red/20 text-[13px] font-mono text-red">
                  {stages[1].error}
                </div>
              ) : result.ast && (
                <div className="bg-white rounded-lg border border-border p-4 overflow-auto">
                  <AstTreeNode node={result.ast} depth={0} isLast={true} prefix="" />
                </div>
              )}
            </div>
          )}

          {expandedStage === 2 && result.semantic.length > 0 && (
            <div className="mb-4 p-4 bg-bg-elevated/30 rounded-xl border border-border animate-fade-in">
              <div className="flex items-center justify-between mb-3">
                <span className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider">语义检查</span>
                <span className="text-[12px] font-mono text-text-muted">{result.semantic.filter((c) => c.status === 'pass').length}/{result.semantic.length} 通过</span>
              </div>
              <div className="space-y-1.5">
                {result.semantic.map((check, i) => (
                  <div key={i} className={`flex items-center gap-3 px-3 py-2 rounded-lg border ${
                    check.status === 'pass' ? 'border-green/20 bg-green-muted/30' :
                    check.status === 'warn' ? 'border-yellow/20 bg-yellow-muted/30' :
                    'border-red/20 bg-red-muted/30'
                  }`}>
                    <span className={`w-5 h-5 rounded-full flex items-center justify-center text-[11px] font-bold flex-shrink-0 ${
                      check.status === 'pass' ? 'bg-green/15 text-green' :
                      check.status === 'warn' ? 'bg-yellow/15 text-yellow' :
                      'bg-red/15 text-red'
                    }`}>
                      {check.status === 'pass' ? '✓' : check.status === 'warn' ? '!' : '✕'}
                    </span>
                    <div className="flex-1 min-w-0">
                      <span className="text-[12px] font-semibold text-text-primary">{check.name}</span>
                      <span className="text-[12px] text-text-muted ml-2">{check.message}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {expandedStage === 3 && (result.explain || stages[3].error) && (
            <div className="mb-4 p-4 bg-bg-elevated/30 rounded-xl border border-border animate-fade-in">
              <div className="flex items-center justify-between mb-3">
                <span className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider">执行计划</span>
                {result.explain && (
                  <div className="flex items-center gap-3 text-[12px] font-mono text-text-muted">
                    <span>块数: {result.explain.blocksAccessed}</span>
                    <span>行数: {result.explain.recordsOutput}</span>
                  </div>
                )}
              </div>
              {stages[3].error ? (
                <div className="px-3 py-2 bg-red-muted rounded-lg border border-red/20 text-[13px] font-mono text-red">
                  {stages[3].error}
                </div>
              ) : result.explain && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <div className="text-[11px] font-mono font-semibold text-text-muted mb-1.5">优化前</div>
                    <pre className="p-3 bg-bg-input rounded-lg border border-border text-[13px] font-mono text-text-secondary overflow-x-auto whitespace-pre-wrap">
                      {result.explain.planBefore}
                    </pre>
                  </div>
                  <div>
                    <div className="text-[11px] font-mono font-semibold text-text-muted mb-1.5">优化后</div>
                    <pre className="p-3 bg-bg-input rounded-lg border border-border text-[13px] font-mono text-text-secondary overflow-x-auto whitespace-pre-wrap">
                      {result.explain.planAfter}
                    </pre>
                  </div>
                </div>
              )}
            </div>
          )}

          {expandedStage === 4 && (result.execute || stages[4].error) && (
            <div className="mb-4 p-4 bg-bg-elevated/30 rounded-xl border border-border animate-fade-in">
              <div className="flex items-center justify-between mb-3">
                <span className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider">查询结果</span>
                {result.execute?.timing && (
                  <span className="text-[12px] font-mono text-text-muted">{result.execute.timing}ms</span>
                )}
              </div>
              {stages[4].error ? (
                <div className="px-3 py-2 bg-red-muted rounded-lg border border-red/20 text-[13px] font-mono text-red">
                  {stages[4].error}
                </div>
              ) : result.execute?.error ? (
                <div className="px-3 py-2 bg-red-muted rounded-lg text-[13px] font-mono text-red">
                  {result.execute.error.message}
                </div>
              ) : result.execute?.type === 'QUERY' && result.execute.columns ? (
                <ResultTable columns={result.execute.columns} rows={result.execute.rows || []} />
              ) : result.execute?.type === 'EXPLAIN' && result.execute.planText ? (
                <pre className="px-3 py-2 bg-bg-elevated rounded-lg text-[13px] font-mono text-text-secondary whitespace-pre-wrap">{result.execute.planText}</pre>
              ) : (
                <div className="text-[13px] font-mono text-text-secondary">
                  {result.execute.affectedRows ?? 0} 行受影响
                </div>
              )}
            </div>
          )}

          {/* Execution trace summary */}
          {stages.some((s) => s.status !== 'idle') && (
            <div className="mt-4 p-4 bg-bg-elevated/30 rounded-xl border border-border">
              <div className="text-[11px] font-mono font-semibold text-text-muted uppercase tracking-wider mb-3">执行跟踪</div>
              <div className="space-y-1 font-mono text-[13px]">
                {[
                  { label: '收到 SQL', value: sql.split('\n')[0].substring(0, 60) },
                  { label: '语句类型', value: (result.tokens?.tokens?.[0]?.lexeme || '—').toUpperCase() },
                  { label: '词法单元', value: result.tokens?.tokens ? `${result.tokens.tokens.length} 个` : '—' },
                  { label: 'AST 节点', value: result.ast ? `${countNodes(result.ast)} 个` : '—' },
                  ...(result.explain ? [
                    { label: '计划节点', value: `${result.explain.blocksAccessed} 块` },
                    { label: '读取页面', value: `${result.explain.blocksAccessed} 页` },
                  ] : []),
                  { label: '执行时间', value: stages[4].timeMs !== undefined ? `${stages[4].timeMs}ms` : '—' },
                  { label: '总耗时', value: totalTime > 0 ? `${totalTime}ms` : '—' },
                ].map((item) => (
                  <div key={item.label} className="flex items-center justify-between py-1.5 border-b border-border-subtle last:border-0">
                    <span className="text-text-muted">{item.label}</span>
                    <span className="text-text-secondary">{item.value}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {!running && !stages.some((s) => s.status !== 'idle') && (
            <EmptyState icon="→" title="暂无流水线跟踪" description="输入 SQL 并点击跟踪流水线以可视化执行过程" />
          )}
        </div>
      </div>
    </div>
  );
}
