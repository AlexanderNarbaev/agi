/**
 * WAVE T-04 — Trust section.
 *
 * Compliance badges, audit proof, and infrastructure certifications.
 */
const BADGES = [
  { label: 'GDPR', icon: '🇪🇺', description: 'Right to be forgotten, data residency' },
  { label: 'SOC 2', icon: '🔒', description: 'Type II audit (Enterprise)' },
  { label: 'ISO 27001', icon: '🛡️', description: 'Information security management' },
  { label: 'Apache-2.0', icon: '⚖️', description: 'Open source core' },
  { label: 'HIPAA-ready', icon: '🏥', description: 'Available on Enterprise' },
  { label: 'CCPA', icon: '📋', description: 'California Consumer Privacy Act' },
];

const INFRA = [
  { metric: '99.9%', label: 'API uptime SLA' },
  { metric: '<100ms', label: 'p99 latency' },
  { metric: '1,000+', label: 'Federation nodes' },
  { metric: '1,119+', label: 'Passing tests' },
];

export default function Trust() {
  return (
    <section className="border-t border-matrix-border bg-matrix-surface py-24">
      <div className="container-wide">
        <div className="mx-auto mb-16 max-w-2xl text-center">
          <p className="mb-2 font-mono text-sm uppercase tracking-wide text-matrix-primary">
            Trust & Compliance
          </p>
          <h2 className="mb-4 text-4xl font-bold md:text-5xl">Production-grade from day one</h2>
          <p className="text-lg text-matrix-muted">
            MATRIX ships with the certifications and audit infrastructure that
            enterprise procurement teams need to sign off.
          </p>
        </div>

        {/* Compliance badges */}
        <div className="mb-16 grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
          {BADGES.map((badge) => (
            <div key={badge.label} className="card text-center">
              <div className="mb-2 text-3xl">{badge.icon}</div>
              <div className="mb-1 text-sm font-semibold">{badge.label}</div>
              <div className="text-xs text-matrix-muted">{badge.description}</div>
            </div>
          ))}
        </div>

        {/* Infrastructure metrics */}
        <div className="grid gap-4 md:grid-cols-4">
          {INFRA.map((stat) => (
            <div key={stat.label} className="card text-center">
              <div className="mb-1 text-3xl font-bold text-gradient">{stat.metric}</div>
              <div className="text-xs uppercase tracking-wide text-matrix-muted">
                {stat.label}
              </div>
            </div>
          ))}
        </div>

        {/* Audit proof */}
        <div className="mt-16 rounded-lg border border-matrix-border bg-matrix-bg p-8">
          <h3 className="mb-4 text-xl font-bold">Immutable Audit Trail</h3>
          <p className="mb-4 text-sm text-matrix-muted">
            Every API call is logged to a hash-chained, append-only ledger.
            Each entry contains the previous hash, making any tampering mathematically
            detectable. Compliance reports for SOC 2, HIPAA, and GDPR are generated
            automatically from this ledger.
          </p>
          <div className="rounded border border-matrix-border bg-matrix-surface p-4 font-mono text-xs">
            <div className="text-matrix-muted"># Sample audit entry</div>
            <div>
              <span className="text-matrix-primary">hash:</span>{' '}
              <span className="text-matrix-text">a3f8b9c2...</span>
            </div>
            <div>
              <span className="text-matrix-primary">prev_hash:</span>{' '}
              <span className="text-matrix-text">7d2e1c4a...</span>
            </div>
            <div>
              <span className="text-matrix-primary">timestamp:</span>{' '}
              <span className="text-matrix-text">2026-09-21T17:00:00Z</span>
            </div>
            <div>
              <span className="text-matrix-primary">user_id:</span>{' '}
              <span className="text-matrix-text">user-1 (alice@example.com)</span>
            </div>
            <div>
              <span className="text-matrix-primary">action:</span>{' '}
              <span className="text-matrix-text">POST /v1/analyze</span>
            </div>
            <div>
              <span className="text-matrix-primary">explain_id:</span>{' '}
              <span className="text-matrix-text">expl_abc123def456</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
