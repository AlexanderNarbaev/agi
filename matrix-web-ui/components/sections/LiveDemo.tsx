'use client';

import { useState } from 'react';

/**
 * WAVE T-04 — Live Demo section.
 *
 * Interactive playground: type a question, see a simulated MATRIX response
 * with explain trace. This runs entirely client-side using a deterministic
 * stub (no real API call required for the marketing demo).
 *
 * For the real API call, the user is directed to get an API key.
 */
const DEMO_RESPONSES: Record<string, { reply: string; trace: string[] }> = {
  'capital of france': {
    reply: 'Paris is the capital of France.',
    trace: [
      'INPUT_NORMALIZED (1ms)',
      'BIR_RULES_FIRED (3ms) → rule-42 (capital_query)',
      'HDC_MEMORY_RETRIEVED (5ms) → kb-doc-42 (sim=0.87)',
      'MCTS_PLAN_SELECTED (4ms) → plan-12',
      'MODULATORS_APPLIED (2ms) → ALL_PASSED',
    ],
  },
  '2+2': {
    reply: '4',
    trace: [
      'INPUT_NORMALIZED (1ms)',
      'BIR_RULES_FIRED (2ms) → rule-arithmetic',
      'HDC_MEMORY_RETRIEVED (1ms) → none',
      'MCTS_PLAN_SELECTED (1ms) → trivial',
      'MODULATORS_APPLIED (1ms) → ALL_PASSED',
    ],
  },
  'hello': {
    reply: 'Hello from MATRIX. How can I help you today?',
    trace: [
      'INPUT_NORMALIZED (1ms)',
      'BIR_RULES_FIRED (1ms) → rule-greeting',
      'HDC_MEMORY_RETRIEVED (1ms) → none',
      'MCTS_PLAN_SELECTED (1ms) → plan-greet',
      'MODULATORS_APPLIED (1ms) → ALL_PASSED',
    ],
  },
};

const FALLBACK = {
  reply: 'I acknowledge your input. (demo mode — get an API key for full responses)',
  trace: [
    'INPUT_NORMALIZED (1ms)',
    'BIR_RULES_FIRED (3ms)',
    'HDC_MEMORY_RETRIEVED (5ms)',
    'MCTS_PLAN_SELECTED (4ms)',
    'MODULATORS_APPLIED (2ms) → ALL_PASSED',
  ],
};

export default function LiveDemo() {
  const [input, setInput] = useState('');
  const [result, setResult] = useState<{ reply: string; trace: string[] } | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim()) return;
    setLoading(true);

    // Simulate network latency
    setTimeout(() => {
      const key = input.toLowerCase().trim();
      const matched = DEMO_RESPONSES[key] ||
        Object.entries(DEMO_RESPONSES).find(([k]) => key.includes(k))?.[1] ||
        FALLBACK;
      setResult(matched);
      setLoading(false);
    }, 600);
  };

  return (
    <section id="demo" className="border-t border-matrix-border bg-matrix-surface py-24">
      <div className="container-narrow">
        <div className="mb-12 text-center">
          <p className="mb-2 font-mono text-sm uppercase tracking-wide text-matrix-primary">
            Try it now
          </p>
          <h2 className="mb-4 text-4xl font-bold md:text-5xl">Live Demo</h2>
          <p className="text-lg text-matrix-muted">
            Type a question. See the explain trace. No signup required.
            <br />
            <span className="text-sm">(Demo runs in your browser; full responses require an API key.)</span>
          </p>
        </div>

        <div className="card">
          <form onSubmit={handleSubmit}>
            <label htmlFor="demo-input" className="mb-2 block text-sm font-medium">
              Your query
            </label>
            <div className="flex gap-3">
              <input
                id="demo-input"
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder='Try: "What is the capital of France?" or "hello" or "2+2"'
                className="flex-1 rounded-md border border-matrix-border bg-matrix-bg px-4 py-3 text-sm text-matrix-text placeholder:text-matrix-muted focus:border-matrix-primary focus:outline-none"
              />
              <button
                type="submit"
                disabled={loading || !input.trim()}
                className="btn-primary disabled:opacity-50"
              >
                {loading ? 'Running...' : 'Analyze'}
              </button>
            </div>
            <p className="mt-3 text-xs text-matrix-muted">
              Suggestions: <code className="rounded bg-matrix-bg px-1.5 py-0.5 font-mono text-matrix-primary">capital of france</code>{' '}
              <code className="rounded bg-matrix-bg px-1.5 py-0.5 font-mono text-matrix-primary">2+2</code>{' '}
              <code className="rounded bg-matrix-bg px-1.5 py-0.5 font-mono text-matrix-primary">hello</code>
            </p>
          </form>

          {result && (
            <div className="mt-6 animate-slide-up space-y-4">
              <div className="rounded-md border border-matrix-primary/30 bg-matrix-primary/5 p-4">
                <p className="mb-1 text-xs uppercase tracking-wide text-matrix-primary">Reply</p>
                <p className="text-lg">{result.reply}</p>
                <div className="mt-3 flex gap-2 text-xs">
                  <span className="badge">confidence: 0.95</span>
                  <span className="badge">accepted: true</span>
                  <span className="badge">duration: 13ms</span>
                </div>
              </div>

              <div>
                <p className="mb-2 text-xs uppercase tracking-wide text-matrix-muted">
                  Explain trace (5 steps)
                </p>
                <ol className="space-y-1 font-mono text-xs">
                  {result.trace.map((step, i) => (
                    <li
                      key={i}
                      className="rounded border border-matrix-border bg-matrix-bg px-3 py-2"
                    >
                      <span className="text-matrix-primary">[{i + 1}]</span> {step}
                    </li>
                  ))}
                </ol>
              </div>

              <p className="text-xs text-matrix-muted">
                In production, every step is logged to the immutable audit chain.
                Get an API key to call the real MATRIX server with your data.
              </p>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
