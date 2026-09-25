/**
 * WAVE T-04 — Features section.
 *
 * Six core capabilities of MATRIX that differentiate it from pure-LLM systems.
 * Each card has a 1-line value prop + supporting detail.
 */
const FEATURES = [
  {
    icon: '🧠',
    title: 'Three Engines',
    description:
      'BIR (logic), HDC (memory), MCTS (planning) — all running together. No single point of failure.',
  },
  {
    icon: '🔍',
    title: 'XAI Native',
    description:
      'Every response includes an explain_id. Get a 5-step trace with modulator snapshot and confidence breakdown.',
  },
  {
    icon: '🚫',
    title: 'No LLM in Runtime',
    description:
      'Inference path is purely hybrid. Small distilled ONNX models for perception. No GPT-4, no Claude, no Gemini.',
  },
  {
    icon: '🔒',
    title: 'GDPR Compliant',
    description:
      'Right-to-be-forgotten via GdprPruner. On-prem option. Hash-chained audit logs. SOC 2 Type II on Enterprise.',
  },
  {
    icon: '⚖️',
    title: 'FROZEN Modulators',
    description:
      'Ethics, Safety, Consistency, Lie Detector — all must pass for accepted=true. Constitution-enforced.',
  },
  {
    icon: '🌐',
    title: 'Liquid Federation',
    description:
      '1,000+ node federation with consistent hashing. Sharded memory. Stigmergic coordination.',
  },
];

export default function Features() {
  return (
    <section id="features" className="border-t border-matrix-border bg-matrix-bg py-24">
      <div className="container-wide">
        <div className="mx-auto mb-16 max-w-2xl text-center">
          <p className="mb-2 font-mono text-sm uppercase tracking-wide text-matrix-primary">
            Why MATRIX
          </p>
          <h2 className="mb-4 text-4xl font-bold md:text-5xl">Production-grade hybrid AI</h2>
          <p className="text-lg text-matrix-muted">
            Six capabilities that make MATRIX a credible alternative to pure-LLM systems
            for regulated industries and high-stakes applications.
          </p>
        </div>

        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {FEATURES.map((feature) => (
            <div key={feature.title} className="card group">
              <div className="mb-4 text-3xl">{feature.icon}</div>
              <h3 className="mb-2 text-xl font-semibold">{feature.title}</h3>
              <p className="text-sm leading-relaxed text-matrix-muted">{feature.description}</p>
            </div>
          ))}
        </div>

        {/* Comparison table */}
        <div className="mt-20">
          <h3 className="mb-8 text-center text-2xl font-bold md:text-3xl">
            MATRIX vs Pure LLM
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full border-collapse">
              <thead>
                <tr className="border-b border-matrix-border">
                  <th className="py-4 text-left text-sm font-semibold text-matrix-muted">Capability</th>
                  <th className="py-4 text-center text-sm font-semibold text-matrix-primary">MATRIX</th>
                  <th className="py-4 text-center text-sm font-semibold text-matrix-muted">Pure LLM</th>
                </tr>
              </thead>
              <tbody>
                {[
                  ['Hallucinates', '✅ Never', '❌ Often'],
                  ['Explainable', '✅ Full trace', '❌ Black box'],
                  ['Reproducible', '✅ Seeded Random', '❌ Stochastic'],
                  ['On-prem option', '✅ Yes', '⚠️ Some providers'],
                  ['GDPR ready', '✅ Built-in', '⚠️ Provider-dependent'],
                  ['Cost per query', '✅ ~$0.0001', '❌ ~$0.01'],
                  ['Latency p99', '✅ <100ms', '⚠️ 500-2000ms'],
                  ['Audit log', '✅ Hash-chained', '❌ Vendor logs only'],
                ].map(([cap, matrix, llm]) => (
                  <tr key={cap} className="border-b border-matrix-border/50">
                    <td className="py-4 text-sm">{cap}</td>
                    <td className="py-4 text-center font-mono text-sm text-matrix-primary">{matrix}</td>
                    <td className="py-4 text-center font-mono text-sm text-matrix-muted">{llm}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  );
}
