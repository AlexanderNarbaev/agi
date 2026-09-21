import Link from 'next/link';

export default function Footer() {
  return (
    <footer className="border-t border-matrix-border bg-matrix-surface">
      <div className="container-wide py-12">
        <div className="grid gap-8 md:grid-cols-4">
          <div>
            <div className="mb-4 flex items-center gap-2">
              <div className="h-8 w-8 rounded bg-gradient-to-br from-matrix-primary to-matrix-secondary" />
              <span className="text-lg font-bold">MATRIX</span>
            </div>
            <p className="text-sm text-matrix-muted">
              Hybrid neuro-symbolic AI with explainability built in.
              No LLM in runtime.
            </p>
          </div>

          <div>
            <h3 className="mb-4 text-sm font-semibold uppercase tracking-wide text-matrix-text">
              Product
            </h3>
            <ul className="space-y-2 text-sm text-matrix-muted">
              <li><Link href="#features" className="hover:text-matrix-primary">Features</Link></li>
              <li><Link href="#pricing" className="hover:text-matrix-primary">Pricing</Link></li>
              <li><Link href="#demo" className="hover:text-matrix-primary">Live Demo</Link></li>
              <li><Link href="/ecosystem/api" className="hover:text-matrix-primary">API Reference</Link></li>
            </ul>
          </div>

          <div>
            <h3 className="mb-4 text-sm font-semibold uppercase tracking-wide text-matrix-text">
              Developers
            </h3>
            <ul className="space-y-2 text-sm text-matrix-muted">
              <li><Link href="/ecosystem" className="hover:text-matrix-primary">Documentation</Link></li>
              <li><Link href="/ecosystem/sdks" className="hover:text-matrix-primary">SDK Guides</Link></li>
              <li><Link href="/ecosystem/xai" className="hover:text-matrix-primary">XAI Docs</Link></li>
              <li><Link href="https://github.com/AlexanderNarbaev/agi" className="hover:text-matrix-primary">GitHub</Link></li>
            </ul>
          </div>

          <div>
            <h3 className="mb-4 text-sm font-semibold uppercase tracking-wide text-matrix-text">
              Legal
            </h3>
            <ul className="space-y-2 text-sm text-matrix-muted">
              <li><Link href="/ecosystem/constitution" className="hover:text-matrix-primary">Constitution</Link></li>
              <li><span>GDPR Compliant</span></li>
              <li><span>Apache-2.0 Core</span></li>
              <li><span>SOC 2 Type II (Enterprise)</span></li>
            </ul>
          </div>
        </div>

        <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-matrix-border pt-8 text-sm text-matrix-muted md:flex-row">
          <p>© 2026 MATRIX. Open source core. Production-grade ecosystem.</p>
          <p className="font-mono text-xs">v0.1.0-T04 · No LLM in runtime · Apache-2.0</p>
        </div>
      </div>
    </footer>
  );
}
