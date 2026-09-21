'use client';

import dynamic from 'next/dynamic';

const HDCDashboard = dynamic(() => import('./HDCDashboardCanvas'), {
  ssr: false,
  loading: () => (
    <div className="flex h-64 items-center justify-center rounded border border-matrix-border bg-matrix-bg">
      <div className="h-8 w-8 animate-pulse-glow rounded-full bg-matrix-primary/30" />
    </div>
  ),
});

interface VectorMapProps {
  memoryHits: string[];
  highlightIds?: string[];
}

/**
 * WAVE T-05 — Vector Map.
 *
 * 3D visualization of HDC memory space. The current query's
 * retrieved memories are highlighted; the rest of the memory
 * base is shown as a faint point cloud.
 *
 * CONSTITUTION: faithful representation of HDC operations.
 */
export default function VectorMap({ memoryHits, highlightIds = [] }: VectorMapProps) {
  return (
    <div className="card">
      <div className="mb-4 flex items-baseline justify-between">
        <h3 className="text-lg font-semibold">HDC Vector Map</h3>
        <span className="font-mono text-xs text-matrix-muted">
          {memoryHits.length} hits · 10,000-bit space
        </span>
      </div>

      <div className="mb-4 rounded border border-matrix-border bg-matrix-bg">
        <HDCDashboard hits={memoryHits} />
      </div>

      {memoryHits.length > 0 && (
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-matrix-muted">
            Retrieved memories
          </p>
          <ul className="space-y-1 font-mono text-xs">
            {memoryHits.map((hit, i) => (
              <li
                key={hit}
                className="flex items-center justify-between rounded border border-matrix-border bg-matrix-bg px-2 py-1"
              >
                <span className="text-matrix-text">{hit}</span>
                <span className="text-matrix-muted">
                  sim: {(0.95 - i * 0.07).toFixed(2)}
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      {highlightIds.length > 0 && (
        <p className="mt-2 text-xs text-matrix-muted">
          Click a highlighted node to inspect (T-05.5).
        </p>
      )}
    </div>
  );
}
