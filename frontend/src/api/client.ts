import type {
  ExecuteResponse,
  TokensResponse,
  ExplainResponse,
  StatsResponse,
  SchemaResponse,
  EngineResponse,
} from './types';

const BASE = '/api';

async function post<T>(path: string, body: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body,
  });
  return res.json();
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  return res.json();
}

export const api = {
  execute: (sql: string) => post<ExecuteResponse>('/execute', sql),
  explain: (sql: string) => post<ExplainResponse>('/explain', sql),
  tokens: (sql: string) => post<TokensResponse>('/tokens', sql),
  stats: () => get<StatsResponse>('/stats'),
  tables: () => get<{ tables: string[] }>('/tables'),
  schema: (table: string) => get<SchemaResponse>(`/schema/${table}`),

  // 成员B引擎通道：SQL -> LogicalPlan -> Executor
  engineExecute: (sql: string) => post<EngineResponse>('/engine/execute', sql),
  engineTables: () => get<{ tables: string[] }>('/engine/tables'),
  engineSchema: (table: string) => get<SchemaResponse>(`/engine/schema/${table}`),
};
