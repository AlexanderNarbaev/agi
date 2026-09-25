/**
 * MATRIX JavaScript SDK.
 *
 * Usage:
 *   import { MatrixClient } from '@matrix/sdk';
 *   const client = new MatrixClient({ apiKey: process.env.MATRIX_API_KEY });
 *   const resp = await client.analyze({ input: 'hello' });
 *
 * CONSTITUTION: No LLM is invoked. Pure transport.
 */
import {
  AnalyzeRequest, AnalyzeResponse, ExplainResponse,
  FederateRequest, FederateResponse, FederateNode, AuditEntry,
  MatrixError,
} from './types';

export interface MatrixClientConfig {
  apiKey: string;
  baseUrl?: string;
  timeoutMs?: number;
}

export interface StreamCallbacks {
  onUpdate: (resp: ExplainResponse) => void;
  onComplete?: () => void;
  onError?: (err: Error) => void;
  pollIntervalMs?: number;
}

const DEFAULT_BASE_URL = 'https://api.matrix.ai';

export class MatrixClient {
  private readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly timeoutMs: number;

  constructor(config: MatrixClientConfig) {
    if (!config.apiKey) throw new Error('apiKey is required');
    this.baseUrl = (config.baseUrl ?? DEFAULT_BASE_URL).replace(/\/$/, '');
    this.apiKey = config.apiKey;
    this.timeoutMs = config.timeoutMs ?? 30_000;
  }

  async analyze(request: AnalyzeRequest): Promise<AnalyzeResponse> {
    return this.post<AnalyzeResponse>('/v1/analyze', request);
  }

  async explain(explainId: string): Promise<ExplainResponse> {
    const encoded = encodeURIComponent(explainId);
    return this.get<ExplainResponse>(`/v1/explain/${encoded}`);
  }

  async federate(request: FederateRequest): Promise<FederateResponse> {
    return this.post<FederateResponse>('/v1/federate', request);
  }

  async listFederation(): Promise<FederateNode[]> {
    return this.get<FederateNode[]>('/v1/federate');
  }

  async auditLogs(limit = 100): Promise<AuditEntry[]> {
    return this.get<AuditEntry[]>(`/v1/audit/logs?limit=${limit}`);
  }

  /** Subscribe to explain trace updates (T-08.5: real SSE). */
  streamExplain(explainId: string, callbacks: StreamCallbacks): () => void {
    const intervalMs = callbacks.pollIntervalMs ?? 250;
    let cancelled = false;
    let lastConfidence = -1;

    const tick = async () => {
      if (cancelled) return;
      try {
        const resp = await this.explain(explainId);
        callbacks.onUpdate(resp);
        if (resp.confidence_breakdown.aggregate >= lastConfidence &&
            resp.confidence_breakdown.aggregate > 0) {
          callbacks.onComplete?.();
          return;
        }
        lastConfidence = resp.confidence_breakdown.aggregate;
        setTimeout(tick, intervalMs);
      } catch (err) {
        callbacks.onError?.(err as Error);
      }
    };
    setTimeout(tick, intervalMs);

    return () => { cancelled = true; };
  }

  private async get<T>(path: string): Promise<T> {
    return this.request<T>('GET', path);
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>('POST', path, body);
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);
    let resp: Response;
    try {
      resp = await fetch(`${this.baseUrl}${path}`, {
        method,
        headers: {
          Authorization: `Bearer ${this.apiKey}`,
          'Content-Type': 'application/json',
          Accept: 'application/json',
        },
        body: body ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });
    } finally {
      clearTimeout(timer);
    }
    if (Math.floor(resp.statusCode / 100) !== 2) {
      throw new MatrixError(resp.statusCode, await resp.text());
    }
    return resp.json() as Promise<T>;
  }
}

export * from './types';
