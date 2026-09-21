'use client';

import { useEffect, useRef, useState, useCallback } from 'react';
import type { ExplainResponse } from './matrix-client';

/**
 * WAVE T-05 — useExplainStream hook.
 *
 * Subscribes to real-time updates for a given explain_id via WebSocket.
 * The matrix-api-gateway exposes /v1/explain/stream/{id} (T-02.5) which
 * pushes step-by-step updates as the inference pipeline runs.
 *
 * Fallback: if WebSocket is unavailable, polls REST every 250ms.
 *
 * CONSTITUTION: No LLM is invoked. All data comes from matrix-core.
 */
export interface UseExplainStreamOptions {
  explainId: string | null;
  apiKey: string;
  baseUrl?: string;
  /** Enable WebSocket (default: true). Disable to force polling. */
  useWebSocket?: boolean;
}

export interface UseExplainStreamResult {
  explain: ExplainResponse | null;
  isStreaming: boolean;
  error: string | null;
}

export function useExplainStream({
  explainId,
  apiKey,
  baseUrl = 'https://api.matrix.ai',
  useWebSocket = true,
}: UseExplainStreamOptions): UseExplainStreamResult {
  const [explain, setExplain] = useState<ExplainResponse | null>(null);
  const [isStreaming, setIsStreaming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const wsRef = useRef<WebSocket | null>(null);
  const pollRef = useRef<NodeJS.Timeout | null>(null);

  const fetchOnce = useCallback(async () => {
    if (!explainId) return;
    try {
      const res = await fetch(`${baseUrl}/v1/explain/${explainId}`, {
        headers: { Authorization: `Bearer ${apiKey}` },
      });
      if (res.ok) {
        const data = (await res.json()) as ExplainResponse;
        setExplain(data);
        setError(null);
      } else if (res.status === 404) {
        // not yet available
      } else {
        setError(`HTTP ${res.status}`);
      }
    } catch (err) {
      setError((err as Error).message);
    }
  }, [explainId, apiKey, baseUrl]);

  useEffect(() => {
    if (!explainId) {
      setExplain(null);
      setIsStreaming(false);
      return;
    }

    setIsStreaming(true);

    // Try WebSocket first
    if (useWebSocket && typeof WebSocket !== 'undefined') {
      const wsUrl = baseUrl.replace(/^http/, 'ws') + `/v1/explain/stream/${explainId}`;
      try {
        const ws = new WebSocket(wsUrl, []);
        wsRef.current = ws;

        ws.onopen = () => {
          ws.send(JSON.stringify({ type: 'auth', apiKey }));
        };

        ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data) as ExplainResponse;
            setExplain(data);
            setError(null);
            if (data.confidence_breakdown.aggregate > 0) {
              setIsStreaming(false);
            }
          } catch (err) {
            setError((err as Error).message);
          }
        };

        ws.onerror = () => {
          // Fall back to polling
          ws.close();
          wsRef.current = null;
          startPolling();
        };

        ws.onclose = () => {
          wsRef.current = null;
          setIsStreaming(false);
        };

        return () => {
          ws.close();
          wsRef.current = null;
        };
      } catch {
        startPolling();
      }
    } else {
      startPolling();
    }

    function startPolling() {
      fetchOnce();
      pollRef.current = setInterval(fetchOnce, 250);
    }

    return () => {
      if (pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, [explainId, apiKey, baseUrl, useWebSocket, fetchOnce]);

  return { explain, isStreaming, error };
}
