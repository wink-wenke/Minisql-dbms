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

export interface AstNode {
  type: string;
  label: string;
  children?: AstNode[];
  detail?: string;
}

export interface AstResponse {
  ast: AstNode;
  type: string;
  error?: { type: string; message: string; line?: number; column?: number };
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
  tables: { tables: string[] } | string[];
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

export interface BufferSlotInfo {
  slot: number;
  file: string | null;
  block: number;
  pins: number;
  dirty: boolean;
  txnum: number;
}

export interface BufferSlotsResponse {
  slots: BufferSlotInfo[];
}

export interface AnalyzeCheck {
  name: string;
  status: 'pass' | 'fail';
  message: string;
}

export interface AnalyzeResponse {
  success: boolean;
  checks: AnalyzeCheck[];
  error?: { type: string; message: string };
}

// ===== Storage Test Types =====

export interface CacheEvent {
  seq: number;
  type: string;       // ALLOC, READ, WRITE, ACCESS
  file: string;
  block: number;
  hit: boolean;
}

export interface AllocatedPage {
  pageNum: number;
  fileName: string;
}

export interface StorageTestState {
  allocatedPages: AllocatedPage[];
  stats: CacheStats;
  slots: BufferSlotInfo[];
  accessHistory: CacheEvent[];
}

export interface StorageTestResult {
  success: boolean;
  error?: string;
  pageNum?: number;
  hit?: boolean;
  cacheEvent?: CacheEvent;
  stats?: CacheStats;
  slots?: BufferSlotInfo[];
  value?: number;
  offset?: number;
}
