'use client';

import type { ConfidenceBreakdown } from '@/lib/matrix-client';

interface ConfidenceScoreProps {
  breakdown: ConfidenceBreakdown;
}

/**
 * WAVE T-05 — Confidence Score visualization.
 *
 * Shows the per-stage confidence (BIR, HDC, MCTS) and the final
 * aggregate (modulator-adjusted). Rendered as a radial gauge + bar chart.
 *
 * The aggregate is LOWER than any individual score if a modulator failed.
 */
export default function ConfidenceScore({ breakdown }: ConfidenceScoreProps) {
  const stages = [
    { name: 'BIR (logic)', value: breakdown.bir_confidence, color: '#10b981' },
    { name: 'HDC (memory)', value: breakdown.hdc_confidence, color: '#a78bfa' },
    { name: 'MCTS (planning)', value: breakdown.mcts_confidence, color: '#06b6d4' },
  ];

  const aggregate = breakdown.aggregate;
  const aggregatePercent = Math.round(aggregate * 100);
  const aggregateAngle = (aggregate * 360) - 90; // -90 to start at top

  return (
    <div className="card">
      <h3 className="mb-4 text-lg font-semibold">Confidence Score</h3>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Radial gauge */}
        <div className="flex flex-col items-center justify-center">
          <div className="relative h-48 w-48">
            <svg className="h-full w-full -rotate-90" viewBox="0 0 100 100">
              {/* Background ring */}
              <circle
                cx="50"
                cy="50"
                r="40"
                fill="none"
                stroke="currentColor"
                strokeWidth="8"
                className="text-matrix-border"
              />
              {/* Aggregate ring */}
              <circle
                cx="50"
                cy="50"
                r="40"
                fill="none"
                stroke="url(#confidence-gradient)"
                strokeWidth="8"
                strokeDasharray={`${2 * Math.PI * 40}`}
                strokeDashoffset={`${2 * Math.PI * 40 * (1 - aggregate)}`}
                strokeLinecap="round"
                className="transition-all duration-1000"
              />
              <defs>
                <linearGradient id="confidence-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" stopColor="#10b981" />
                  <stop offset="50%" stopColor="#06b6d4" />
                  <stop offset="100%" stopColor="#a78bfa" />
                </linearGradient>
              </defs>
            </svg>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-4xl font-bold text-matrix-text">
                {aggregatePercent}
                <span className="text-xl">%</span>
              </span>
              <span className="font-mono text-xs uppercase text-matrix-muted">
                aggregate
              </span>
            </div>
          </div>
        </div>

        {/* Bar chart */}
        <div className="flex flex-col justify-center space-y-4">
          {stages.map((stage) => {
            const percent = Math.round(stage.value * 100);
            return (
              <div key={stage.name}>
                <div className="mb-1 flex items-baseline justify-between">
                  <span className="text-xs font-medium">{stage.name}</span>
                  <span className="font-mono text-xs font-semibold" style={{ color: stage.color }}>
                    {(stage.value).toFixed(2)}
                  </span>
                </div>
                <div className="h-2 overflow-hidden rounded-full border border-matrix-border bg-matrix-bg">
                  <div
                    className="h-full transition-all duration-700"
                    style={{ width: `${percent}%`, backgroundColor: stage.color }}
                  />
                </div>
              </div>
            );
          })}

          <div className="mt-4 rounded border border-matrix-border bg-matrix-bg p-3 text-xs text-matrix-muted">
            <strong className="text-matrix-text">Aggregate rule:</strong> lower than the
            weakest stage score if a modulator failed (set in matrix-core).
          </div>
        </div>
      </div>
    </div>
  );
}
