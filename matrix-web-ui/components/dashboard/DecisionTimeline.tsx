'use client';

import type { ExplainStep } from '@/lib/matrix-client';

interface DecisionTimelineProps {
  steps: ExplainStep[];
  totalDurationMs: number;
}

/**
 * WAVE T-05 — Decision Timeline visualization.
 *
 * Renders the 5-step inference trace as a horizontal timeline with
 * proportional widths based on duration. Color-coded by stage type:
 *   - INPUT_NORMALIZED  → cyan (preprocessing)
 *   - BIR_RULES_FIRED   → green (logic)
 *   - HDC_MEMORY_*      → violet (memory)
 *   - MCTS_PLAN_*       → emerald (planning)
 *   - MODULATORS_*      → amber (governance)
 */
export default function DecisionTimeline({ steps, totalDurationMs }: DecisionTimelineProps) {
  const getColor = (stage: string): string => {
    if (stage.includes('INPUT')) return 'bg-cyan-500';
    if (stage.includes('BIR')) return 'bg-emerald-500';
    if (stage.includes('HDC')) return 'bg-violet-500';
    if (stage.includes('MCTS')) return 'bg-matrix-primary';
    if (stage.includes('MODULATOR')) return 'bg-amber-500';
    return 'bg-matrix-muted';
  };

  const getTextColor = (stage: string): string => {
    if (stage.includes('INPUT')) return 'text-cyan-300';
    if (stage.includes('BIR')) return 'text-emerald-300';
    if (stage.includes('HDC')) return 'text-violet-300';
    if (stage.includes('MCTS')) return 'text-matrix-primary';
    if (stage.includes('MODULATOR')) return 'text-amber-300';
    return 'text-matrix-muted';
  };

  return (
    <div className="card">
      <div className="mb-4 flex items-baseline justify-between">
        <h3 className="text-lg font-semibold">Decision Timeline</h3>
        <span className="font-mono text-xs text-matrix-muted">
          total: {totalDurationMs}ms
        </span>
      </div>

      {/* Horizontal stacked bar */}
      <div className="mb-6 flex h-8 overflow-hidden rounded border border-matrix-border">
        {steps.map((step, i) => {
          const width = totalDurationMs > 0 ? (step.duration_ms / totalDurationMs) * 100 : 0;
          return (
            <div
              key={i}
              className={`${getColor(step.stage)} flex items-center justify-center transition-all hover:opacity-80`}
              style={{ width: `${width}%` }}
              title={`${step.stage}: ${step.duration_ms}ms`}
            >
              {width > 8 && (
                <span className="text-[10px] font-bold text-matrix-bg">
                  {step.duration_ms}ms
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Detailed step list */}
      <ol className="space-y-2">
        {steps.map((step, i) => (
          <li
            key={i}
            className="flex items-start gap-3 rounded border border-matrix-border bg-matrix-bg p-3"
          >
            <span className="flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full border border-matrix-border bg-matrix-surface font-mono text-xs">
              {i + 1}
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex items-baseline justify-between gap-2">
                <span className={`font-mono text-sm font-semibold ${getTextColor(step.stage)}`}>
                  {step.stage}
                </span>
                <span className="font-mono text-xs text-matrix-muted">
                  {step.duration_ms}ms
                </span>
              </div>
              <p className="mt-1 truncate text-xs text-matrix-muted">{step.action}</p>
            </div>
          </li>
        ))}
      </ol>

      {/* Legend */}
      <div className="mt-6 flex flex-wrap gap-3 text-xs">
        {[
          { label: 'INPUT', color: 'bg-cyan-500' },
          { label: 'BIR (logic)', color: 'bg-emerald-500' },
          { label: 'HDC (memory)', color: 'bg-violet-500' },
          { label: 'MCTS (plan)', color: 'bg-matrix-primary' },
          { label: 'MODULATORS', color: 'bg-amber-500' },
        ].map((item) => (
          <div key={item.label} className="flex items-center gap-1.5">
            <div className={`h-3 w-3 rounded ${item.color}`} />
            <span className="text-matrix-muted">{item.label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
