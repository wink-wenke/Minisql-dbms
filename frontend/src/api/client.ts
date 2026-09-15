import type { ExecuteResponse, TokensResponse, ExplainResponse, AstResponse, StatsResponse, SchemaResponse, BufferSlotsResponse, AnalyzeResponse } from './types';

const BASE = '/api';
const TIMEOUT_MS = 10000;

async function request<T>(path: string, options: RequestInit): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(`${BASE}${path}`, { ...options, signal: controller.signal });
    if (!res.ok) {
      const body = await res.json().catch(() => null);
      const msg = body?.error?.message || body?.message || `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return res.json();
  } finally {
    clearTimeout(timer);
  }
}

function post<T>(path: string, body: string): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body,
  });
}

function get<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'GET' });
}

export const api = {
  execute: (sql: string) => post<ExecuteResponse>('/execute', sql),
  explain: (sql: string) => post<ExplainResponse>('/explain', sql),
  tokens: (sql: string) => post<TokensResponse>('/tokens', sql),
  ast: (sql: string) => post<AstResponse>('/ast', sql),
  analyze: (sql: string) => post<AnalyzeResponse>('/analyze', sql),
  stats: () => get<StatsResponse>('/stats'),
  tables: () => get<{ tables: string[] }>('/tables'),
  schema: (table: string) => get<SchemaResponse>(`/schema/${table}`),
  bufferSlots: () => get<BufferSlotsResponse>('/buffer-slots'),
};
