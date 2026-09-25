/**
 * WAVE T-05 — API client for matrix-api-gateway.
 *
 * Thin wrapper over fetch() that handles:
 * - Bearer token injection
 * - Base URL configuration
 * - Error normalization (typed MatrixApiError)
 * - TypeScript types matching OpenAPI spec
 *
 * CONSTITUTION: No LLM is invoked anywhere in the client.
 */

// ============================================================================
// Types (mirror matrix-api-gateway/src/main/resources/openapi.yaml)
// ============================================================================

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

export interface ModulatorSnapshot {
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
  modulator_snapshot: ModulatorSnapshot;
  hdc_memory_hits: string[];
  confidence_breakdown: ConfidenceBreakdown;
}

export interface FederateJoinRequest {
  region: string;
  shard_capacity?: number;
}

export interface FederateJoinResponse {
  node_id: string;
  region: string;
  shard_capacity: number;
  joined_at: string;
  total_nodes: number;
}

export interface FederationNode {
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

export type Role = 'ADMIN' | 'DEVELOPER' | 'VIEWER';
export type Plan = 'FREE' | 'PRO' | 'ENTERPRISE';

// ============================================================================
// Error class
// ============================================================================

export class MatrixApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly code?: string,
  ) {
    super(message);
    this.name = 'MatrixApiError';
  }
}

// ============================================================================
// Client
// ============================================================================

export interface MatrixClientConfig {
  baseUrl?: string;
  apiKey?: string;
}

export class MatrixClient {
  private baseUrl: string;
  private apiKey: string;

  constructor(config: MatrixClientConfig = {}) {
    this.baseUrl = config.baseUrl ?? 'https://api.matrix.ai';
    this.apiKey = config.apiKey ?? '';
  }

  async analyze(request: AnalyzeRequest): Promise<AnalyzeResponse> {
    return this.post<AnalyzeResponse>('/v1/analyze', request);
  }

  async explain(id: string): Promise<ExplainResponse> {
    return this.get<ExplainResponse>(`/v1/explain/${encodeURIComponent(id)}`);
  }

  async joinFederation(request: FederateJoinRequest): Promise<FederateJoinResponse> {
    return this.post<FederateJoinResponse>('/v1/federate', request);
  }

  async listFederation(): Promise<FederationNode[]> {
    return this.get<FederationNode[]>('/v1/federate');
  }

  async auditLogs(limit = 100): Promise<AuditEntry[]> {
    return this.get<AuditEntry[]>(`/v1/audit/logs?limit=${limit}`);
  }

  private async get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path);
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>('POST', path, body);
  }

  private async request<T>(
    method: string,
    path: string,
    body?: unknown,
  ): Promise<T> {
    const url = `${this.baseUrl}${path}`;
    const headers: Record<string, string> = {
      Authorization: `Bearer ${this.apiKey}`,
      Accept: 'application/json',
    };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    let response: Response;
    try {
      response = await fetch(url, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch (err) {
      throw new MatrixApiError(0, `Network error: ${(err as Error).message}`);
    }

    if (!response.ok) {
      let message = response.statusText;
      try {
        const errBody = await response.json();
        if (errBody && typeof errBody.error === 'string') {
          message = errBody.error;
        }
      } catch {
        // ignore JSON parse errors
      }
      throw new MatrixApiError(response.status, message);
    }

    return response.json() as Promise<T>;
  }
}
