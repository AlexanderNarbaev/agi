'use client';

import dynamic from 'next/dynamic';
import Link from 'next/link';

// WebGL component loaded client-side only to avoid SSR issues with Three.js
const HDCVectorSpace = dynamic(() => import('@/components/three/HDCVectorSpace'), {
  ssr: false,
  loading: () => (
    <div className="absolute inset-0 flex items-center justify-center">
      <div className="h-12 w-12 animate-pulse-glow rounded-full bg-matrix-primary/30" />
    </div>
  ),
});

export default function Hero() {
  return (
    <section className="relative isolate overflow-hidden pt-32 pb-24">
      {/* WebGL background */}
      <div className="absolute inset-0 -z-10 bg-matrix-bg">
        <div className="absolute inset-0 bg-grid opacity-30" />
        <HDCVectorSpace />
        <div className="absolute inset-0 bg-gradient-to-t from-matrix-bg via-transparent to-matrix-bg/50" />
      </div>

      <div className="container-narrow text-center">
        <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-matrix-primary/30 bg-matrix-primary/10 px-4 py-1.5 text-xs">
          <span className="inline-block h-2 w-2 animate-pulse rounded-full bg-matrix-primary" />
          <span className="font-mono text-matrix-primary">v0.1.0-T04 · GA in Q4 2026</span>
        </div>

        <h1 className="mb-6 text-5xl font-bold leading-tight tracking-tight md:text-7xl">
          Hybrid AI with
          <br />
          <span className="text-gradient">explainability built in.</span>
        </h1>

        <p className="mx-auto mb-10 max-w-2xl text-lg text-matrix-muted md:text-xl">
          MATRIX combines Boolean Inference Rules, Hyperdimensional Computing,
          and Monte Carlo Tree Search. Every decision is auditable. No LLM in
          runtime. GDPR-compliant. On-prem available.
        </p>

        <div className="flex flex-col items-center justify-center gap-4 sm:flex-row">
          <Link href="#pricing" className="btn-primary">
            Get API Key — Free 100 req/hr
          </Link>
          <Link href="#demo" className="btn-secondary">
            Try Live Demo
          </Link>
        </div>

        <div className="mt-16 flex flex-wrap items-center justify-center gap-x-8 gap-y-4 text-sm text-matrix-muted">
          <div className="flex items-center gap-2">
            <svg className="h-4 w-4 text-matrix-primary" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                clipRule="evenodd"
              />
            </svg>
            <span>Apache-2.0 core</span>
          </div>
          <div className="flex items-center gap-2">
            <svg className="h-4 w-4 text-matrix-primary" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                clipRule="evenodd"
              />
            </svg>
            <span>GDPR-ready</span>
          </div>
          <div className="flex items-center gap-2">
            <svg className="h-4 w-4 text-matrix-primary" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                clipRule="evenodd"
              />
            </svg>
            <span>SOC 2 Type II (Enterprise)</span>
          </div>
          <div className="flex items-center gap-2">
            <svg className="h-4 w-4 text-matrix-primary" fill="currentColor" viewBox="0 0 20 20">
              <path
                fillRule="evenodd"
                d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                clipRule="evenodd"
              />
            </svg>
            <span>No LLM in runtime</span>
          </div>
        </div>
      </div>
    </section>
  );
}
