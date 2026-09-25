'use client';

import { useState } from 'react';
import Link from 'next/link';
import { MatrixClient, MatrixApiError, type AnalyzeResponse } from '@/lib/matrix-client';
import { useExplainStream } from '@/lib/use-explain-stream';
import DecisionTimeline from '@/components/dashboard/DecisionTimeline';
import ModulatorState from '@/components/dashboard/ModulatorState';
import ConfidenceScore from '@/components/dashboard/ConfidenceScore';
import VectorMap from '@/components/dashboard/VectorMap';
import CounterfactualSimulator from '@/components/dashboard/CounterfactualSimulator';
import Navbar from '@/components/Navbar';
import Footer from '@/components/Footer';

/**
 * WAVE T-05 — XAI Dashboard (client-facing).
 *
 * Loads an explain_id from URL params or input, fetches the trace,
 * and renders all four visualization components. Uses the WebSocket
 * hook for real-time updates.
 */
export default function DashboardPage() {
  const [apiKey, setApiKey] = useState('');
  const [explainId, setExplainId] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [originalResult, setOriginalResult] = useState<AnalyzeResponse | null>(null);

  const client = new MatrixClient({ apiKey });

  const { explain, isStreaming } = useExplainStream({
    explainId,
    apiKey,
  });

  const totalDuration = explain?.steps.reduce((sum, s) => sum + s.duration_ms, 0) ?? 0;

  const handleAnalyze = async () => {
    if (!query.trim() || !apiKey.trim()) return;
    setLoading(true);
    setError(null);
    try {
      const result = await client.analyze({ input: query.trim() });
      setOriginalResult(result);
      setExplainId(result.explain_id);
    } catch (err) {
      if (err instanceof MatrixApiError) {
        setError(`API Error (${err.status}): ${err.message}`);
      } else {
        setError((err as Error).message);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCounterfactual = async (modifiedInput: string) => {
    if (!apiKey.trim()) throw new Error('API key required');
    const counterClient = new MatrixClient({ apiKey });
    const result = await counterClient.analyze({ input: modifiedInput });
    // Try to fetch explain trace
    let explain = null;
    try {
      explain = await counterClient.explain(result.explain_id);
    } catch {
      // trace may not be available
    }
    return { result, explain };
  };

  return (
    <>
      <Navbar />
      <main className="container-wide pt-32 pb-24">
        <div className="mb-8">
          <h1 className="mb-2 text-4xl font-bold">XAI Dashboard</h1>
          <p className="text-matrix-muted">
            Inspect any MATRIX decision. Every step is auditable. Every trace is reproducible.
          </p>
        </div>

        {/* API key + query input */}
        <div className="card mb-8">
          <div className="mb-3 grid gap-3 md:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase text-matrix-muted">
                API Key
              </label>
              <input
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="sk-matrix-..."
                className="w-full rounded border border-matrix-border bg-matrix-bg px-3 py-2 text-sm focus:border-matrix-primary focus:outline-none"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold uppercase text-matrix-muted">
                Query
              </label>
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleAnalyze()}
                placeholder="What is the capital of France?"
                className="w-full rounded border border-matrix-border bg-matrix-bg px-3 py-2 text-sm focus:border-matrix-primary focus:outline-none"
              />
            </div>
          </div>
          <button
            onClick={handleAnalyze}
            disabled={loading || !apiKey.trim() || !query.trim()}
            className="btn-primary w-full disabled:opacity-50"
          >
            {loading ? 'Analyzing...' : 'Analyze & Inspect'}
          </button>
          {error && (
            <div className="mt-3 rounded border border-matrix-danger/30 bg-matrix-danger/5 p-3 text-xs text-matrix-danger">
              {error}
            </div>
          )}
        </div>

        {/* Empty state */}
        {!explain && !loading && (
          <div className="card text-center">
            <div className="mb-4 text-5xl opacity-50">🔍</div>
            <h3 className="mb-2 text-xl font-semibold">No decision loaded</h3>
            <p className="text-sm text-matrix-muted">
              Enter an API key and a query above to inspect a decision.
              <br />
              Or paste an <code className="font-mono text-matrix-primary">explain_id</code> from a previous response.
            </p>
          </div>
        )}

        {/* Loading state */}
        {loading && (
          <div className="card text-center">
            <div className="mx-auto mb-4 h-12 w-12 animate-pulse-glow rounded-full bg-matrix-primary/30" />
            <p className="text-matrix-muted">Running hybrid inference...</p>
          </div>
        )}

        {/* Results */}
        {explain && (
          <>
            {/* Header card */}
            <div className="card mb-6">
              <div className="flex flex-wrap items-baseline justify-between gap-4">
                <div>
                  <p className="mb-1 font-mono text-xs uppercase tracking-wide text-matrix-primary">
                    Decision loaded
                  </p>
                  <h2 className="text-xl font-semibold">{originalResult?.reply || '(loading...)'}</h2>
                  <p className="mt-1 font-mono text-xs text-matrix-muted">
                    explain_id: {explain.explain_id}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <span className="badge">confidence: {explain.confidence_breakdown.aggregate.toFixed(2)}</span>
                  <span className="badge">duration: {totalDuration}ms</span>
                  {isStreaming && (
                    <span className="badge border-matrix-primary/30 bg-matrix-primary/10 text-matrix-primary">
                      <span className="mr-1 inline-block h-2 w-2 animate-pulse rounded-full bg-matrix-primary" />
                      streaming
                    </span>
                  )}
                </div>
              </div>
            </div>

            {/* Main grid: timeline + modulator */}
            <div className="mb-6 grid gap-6 lg:grid-cols-2">
              <DecisionTimeline steps={explain.steps} totalDurationMs={totalDuration} />
              <ModulatorState snapshot={explain.modulator_snapshot} live={isStreaming} />
            </div>

            {/* Confidence + Vector map */}
            <div className="mb-6 grid gap-6 lg:grid-cols-2">
              <ConfidenceScore breakdown={explain.confidence_breakdown} />
              <VectorMap memoryHits={explain.hdc_memory_hits} />
            </div>

            {/* Counterfactual */}
            <CounterfactualSimulator
              originalInput={query}
              originalResult={originalResult}
              originalExplain={explain}
              apiKey={apiKey}
              onSimulate={handleCounterfactual}
            />

            {/* Export buttons (T-05.5 will wire up real PDF export) */}
            <div className="mt-6 flex gap-3">
              <button className="btn-secondary" disabled>
                📄 Export PDF (T-05.5)
              </button>
              <button className="btn-secondary" disabled>
                🖼️ Export PNG (T-05.5)
              </button>
            </div>
          </>
        )}
      </main>
      <Footer />
    </>
  );
}
