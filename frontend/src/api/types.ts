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
