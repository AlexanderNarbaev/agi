'use client';

import type { ModulatorSnapshot } from '@/lib/matrix-client';

interface ModulatorStateProps {
  snapshot: ModulatorSnapshot;
  /** Show live "in motion" indicators */
  live?: boolean;
}

/**
 * WAVE T-05 — Modulator State visualization.
 *
 * Displays the 4 FROZEN modulators as gauges. When `live` is true,
 * each gauge shows a "ripple" effect (CSS animation) to indicate
 * real-time updates from the WebSocket stream.
 *
 * CONSTITUTION: All 4 modulators are FROZEN. Their values can be
 * observed but cannot be overridden by any user input.
 */
export default function ModulatorState({ snapshot, live = false }: ModulatorStateProps) {
  const modulators: { name: string; key: keyof ModulatorSnapshot; description: string; color: string }[] = [
    {
      name: 'ETHICAL_FILTER',
      key: 'ethical_filter',
      description: 'Refuses outputs violating ethical guidelines',
      color: 'from-emerald-500 to-emerald-300',
    },
    {
      name: 'SAFETY_MONITOR',
      key: 'safety_monitor',
      description: 'Refuses outputs that could cause harm',
      color: 'from-cyan-500 to-cyan-300',
    },
    {
      name: 'CONSISTENCY_CHECKER',
      key: 'consistency_checker',
      description: 'Refuses outputs contradicting prior state',
      color: 'from-violet-500 to-violet-300',
    },
    {
      name: 'LIE_DETECTOR',
      key: 'lie_detector',
      description: 'Refuses outputs misrepresenting source data',
      color: 'from-amber-500 to-amber-300',
    },
  ];

  const average =
    (snapshot.ethical_filter +
      snapshot.safety_monitor +
      snapshot.consistency_checker +
      snapshot.lie_detector) /
    4;

  return (
    <div className="card">
      <div className="mb-4 flex items-baseline justify-between">
        <h3 className="text-lg font-semibold">Modulator State</h3>
        <span className="font-mono text-xs text-matrix-muted">
          avg: {average.toFixed(2)} · FROZEN
        </span>
      </div>

      <div className="space-y-4">
        {modulators.map((mod) => {
          const value = snapshot[mod.key];
          const percent = Math.round(value * 100);
          return (
            <div key={mod.key}>
              <div className="mb-1 flex items-baseline justify-between">
                <span className="font-mono text-xs font-semibold uppercase tracking-wide">
                  {mod.name}
                </span>
                <span className="font-mono text-sm font-bold text-matrix-text">
                  {value.toFixed(2)}
                </span>
              </div>

              <div className="relative h-3 overflow-hidden rounded-full border border-matrix-border bg-matrix-bg">
                <div
                  className={`h-full bg-gradient-to-r ${mod.color} transition-all duration-500`}
                  style={{ width: `${percent}%` }}
                />
                {live && (
                  <div
                    className="absolute inset-0 animate-pulse rounded-full bg-matrix-primary/10"
                    style={{ width: `${percent}%` }}
                  />
                )}
              </div>

              <p className="mt-1 text-[10px] text-matrix-muted">{mod.description}</p>
            </div>
          );
        })}
      </div>

      <div className="mt-4 rounded border border-matrix-primary/30 bg-matrix-primary/5 p-3 text-xs">
        <strong className="text-matrix-primary">CONSTITUTION IV:</strong> These 4 modulators
        are immutable. All must pass for <code className="font-mono">accepted=true</code>.
      </div>
    </div>
  );
}
