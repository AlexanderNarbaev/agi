'use client';

import { useState } from 'react';
import type { AnalyzeResponse, ExplainResponse } from '@/lib/matrix-client';

interface CounterfactualSimulatorProps {
  originalInput: string;
  originalResult: AnalyzeResponse | null;
  originalExplain: ExplainResponse | null;
  apiKey: string;
  baseUrl?: string;
  onSimulate: (modifiedInput: string) => Promise<{ result: AnalyzeResponse; explain: ExplainResponse | null }>;
}

/**
 * WAVE T-05 — Counterfactual Simulator.
 *
 * "What if the input had been different?" — replays the decision
 * with a modified input and shows which steps changed. Used for
 * debugging, what-if analysis, and user education.
 *
 * Compares side-by-side: original vs modified.
 */
export default function CounterfactualSimulator({
  originalInput,
  originalResult,
  originalExplain,
  apiKey,
  baseUrl = 'https://api.matrix.ai',
  onSimulate,
}: CounterfactualSimulatorProps) {
  const [modifiedInput, setModifiedInput] = useState(originalInput);
  const [loading, setLoading] = useState(false);
  const [modifiedResult, setModifiedResult] = useState<AnalyzeResponse | null>(null);
  const [modifiedExplain, setModifiedExplain] = useState<ExplainResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSimulate = async () => {
    if (modifiedInput === originalInput) {
      setError('Modified input must differ from original');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const { result, explain } = await onSimulate(modifiedInput);
      setModifiedResult(result);
      setModifiedExplain(explain);
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="card">
      <h3 className="mb-4 text-lg font-semibold">Counterfactual Simulator</h3>
      <p className="mb-4 text-sm text-matrix-muted">
        What if the input had been different? See how the decision changes.
      </p>

      <div className="mb-4 grid gap-3 md:grid-cols-2">
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase text-matrix-muted">
            Original
          </label>
          <div className="rounded border border-matrix-border bg-matrix-bg px-3 py-2 text-sm">
            {originalInput}
          </div>
        </div>
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase text-matrix-muted">
            Modified
          </label>
          <input
            type="text"
            value={modifiedInput}
            onChange={(e) => setModifiedInput(e.target.value)}
            className="w-full rounded border border-matrix-border bg-matrix-bg px-3 py-2 text-sm focus:border-matrix-primary focus:outline-none"
          />
        </div>
      </div>

      <button
        onClick={handleSimulate}
        disabled={loading || modifiedInput === originalInput}
        className="btn-secondary w-full disabled:opacity-50"
      >
        {loading ? 'Running counterfactual...' : 'Run Counterfactual'}
      </button>

      {error && (
        <div className="mt-3 rounded border border-matrix-danger/30 bg-matrix-danger/5 p-3 text-xs text-matrix-danger">
          {error}
        </div>
      )}

      {(modifiedResult || originalResult) && (
        <div className="mt-4 space-y-3">
          <ComparisonRow
            label="Reply"
            original={originalResult?.reply || '—'}
            modified={modifiedResult?.reply || '—'}
          />
          <ComparisonRow
            label="Confidence"
            original={originalResult ? originalResult.confidence.toFixed(2) : '—'}
            modified={modifiedResult ? modifiedResult.confidence.toFixed(2) : '—'}
          />
          <ComparisonRow
            label="Accepted"
            original={originalResult?.accepted ? 'true' : 'false'}
            modified={modifiedResult?.accepted ? 'true' : 'false'}
          />
          <ComparisonRow
            label="Modulators"
            original={originalResult?.modulators_fired?.join(', ') || '—'}
            modified={modifiedResult?.modulators_fired?.join(', ') || '—'}
          />
          <ComparisonRow
            label="Duration"
            original={originalResult ? `${originalResult.duration_ms}ms` : '—'}
            modified={modifiedResult ? `${modifiedResult.duration_ms}ms` : '—'}
          />
        </div>
      )}
    </div>
  );
}

function ComparisonRow({
  label,
  original,
  modified,
}: {
  label: string;
  original: string;
  modified: string;
}) {
  const changed = original !== modified;
  return (
    <div className="grid grid-cols-3 gap-2 rounded border border-matrix-border bg-matrix-bg p-3 text-sm">
      <div className="text-xs font-semibold uppercase text-matrix-muted">{label}</div>
      <div className="text-matrix-text">{original}</div>
      <div className={changed ? 'font-bold text-matrix-primary' : 'text-matrix-text'}>
        {modified}
        {changed && <span className="ml-2 text-xs">← changed</span>}
      </div>
    </div>
  );
}
