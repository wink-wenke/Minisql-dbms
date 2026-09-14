export interface ExecuteResponse {
  success?: boolean;
  type: 'QUERY' | 'UPDATE' | 'EXPLAIN';
  columns?: string[];
  rows?: (string | number)[][];
  affectedRows?: number;
  planText?: string;
  timing?: number;
  error?: {
    type: string;
    message: string;
    line?: number;
    column?: number;
  };
}

export interface TokenInfo {
  type: string;
  lexeme: string;
  line: number;
  column: number;
}

export interface TokensResponse {
  tokens?: TokenInfo[];
  error?: { type: string; message: string };
}

export interface ExplainResponse {
  sql: string;
  planBefore: string;
  planAfter: string;
  blocksAccessed: number;
  recordsOutput: number;
  error?: { type: string; message: string };
}

export interface CacheStats {
  accessCount: number;
  hitCount: number;
  missCount: number;
  evictionCount: number;
  hitRate: number;
}

export interface StatsResponse {
  cache: CacheStats;
  tables: string[];
  pageSize: number;
  bufferSize: number;
}

export interface ColumnInfo {
  name: string;
  type: string;
  length: number;
}

export interface SchemaResponse {
  table: string;
  columns: ColumnInfo[];
}

/**
 * 成员B引擎通道（/api/engine/*）的响应。
 * 与 ExecuteResponse 字段基本一致，额外带 engine 标识与 rowCount。
 */
export interface EngineResponse extends ExecuteResponse {
  /** 固定为 "memberB"，用于确认这条 SQL 确实走了引擎模块而不是原生 Planner */
  engine?: string;
  rowCount?: number;
}
