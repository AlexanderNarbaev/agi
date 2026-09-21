/** MATRIX SDK TypeScript types matching OpenAPI 3.0 spec. */

export type Plan = 'FREE' | 'PRO' | 'ENTERPRISE';
export type ContentType = 'text' | 'audio' | 'image';

export interface AnalyzeRequest {
  input: string;
  content_type?: ContentType;
  context?: string;
  model?: string;
}

export interface AnalyzeResponse {
  reply: string;
  confidence: number;
  duration_ms: number;
  accepted: boolean;
  explain_id: string;
  modulators_fired?: string[];
}

export interface ExplainStep {
  stage: string;
  action: string;
  duration_ms: number;
}

export interface ModulatorState {
  ethical_filter: number;
  safety_monitor: number;
  consistency_checker: number;
  lie_detector: number;
}

export interface ConfidenceBreakdown {
  bir_confidence: number;
  hdc_confidence: number;
  mcts_confidence: number;
  aggregate: number;
}

export interface ExplainResponse {
  explain_id: string;
  steps: ExplainStep[];
  modulator_snapshot: ModulatorState;
  hdc_memory_hits: string[];
  confidence_breakdown: ConfidenceBreakdown;
}

export interface FederateRequest {
  region: string;
  shard_capacity?: number;
}

export interface FederateResponse {
  node_id: string;
  region: string;
  shard_capacity: number;
  joined_at: string;
  total_nodes: number;
}

export interface FederateNode {
  nodeId: string;
  region: string;
  shardCapacity: number;
  joinedAt: string;
}

export interface AuditEntry {
  timestamp: string;
  user_id: string;
  action: string;
  target: string;
  status_code: number;
}

export class MatrixError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly errorBody: string,
  ) {
    super(`HTTP ${statusCode}: ${errorBody}`);
    this.name = 'MatrixError';
  }
}
