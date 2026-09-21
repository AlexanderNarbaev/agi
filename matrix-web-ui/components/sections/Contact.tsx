'use client';

import { useState } from 'react';

export default function Contact() {
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // In production: POST to /api/contact
    setSubmitted(true);
  };

  return (
    <section id="contact" className="border-t border-matrix-border bg-matrix-bg py-24">
      <div className="container-narrow">
        <div className="mb-12 text-center">
          <p className="mb-2 font-mono text-sm uppercase tracking-wide text-matrix-primary">
            Partner Inquiry
          </p>
          <h2 className="mb-4 text-4xl font-bold md:text-5xl">Let&apos;s talk</h2>
          <p className="text-lg text-matrix-muted">
            Enterprise deployments, custom SLAs, on-prem installations, partner programs.
            We&apos;ll get back to you within 1 business day.
          </p>
        </div>

        {!submitted ? (
          <form onSubmit={handleSubmit} className="card space-y-6">
            <div>
              <label htmlFor="name" className="mb-2 block text-sm font-medium">
                Name
              </label>
              <input
                id="name"
                name="name"
                type="text"
                required
                className="w-full rounded-md border border-matrix-border bg-matrix-bg px-4 py-3 text-sm text-matrix-text placeholder:text-matrix-muted focus:border-matrix-primary focus:outline-none"
                placeholder="Your name"
              />
            </div>

            <div>
              <label htmlFor="email" className="mb-2 block text-sm font-medium">
                Work email
              </label>
              <input
                id="email"
                name="email"
                type="email"
                required
                className="w-full rounded-md border border-matrix-border bg-matrix-bg px-4 py-3 text-sm text-matrix-text placeholder:text-matrix-muted focus:border-matrix-primary focus:outline-none"
                placeholder="you@company.com"
              />
            </div>

            <div>
              <label htmlFor="company" className="mb-2 block text-sm font-medium">
                Company
              </label>
              <input
                id="company"
                name="company"
                type="text"
                required
                className="w-full rounded-md border border-matrix-border bg-matrix-bg px-4 py-3 text-sm text-matrix-text placeholder:text-matrix-muted focus:border-matrix-primary focus:outline-none"
                placeholder="Your company"
              />
            </div>

            <div>
              <label htmlFor="use-case" className="mb-2 block text-sm font-medium">
                Use case
              </label>
              <textarea
                id="use-case"
                name="use_case"
                rows={4}
                className="w-full rounded-md border border-matrix-border bg-matrix-bg px-4 py-3 text-sm text-matrix-text placeholder:text-matrix-muted focus:border-matrix-primary focus:outline-none"
                placeholder="Tell us what you're building. We specialize in regulated industries: finance, healthcare, legal, government."
              />
            </div>

            <button type="submit" className="btn-primary w-full">
              Send Inquiry
            </button>

            <p className="text-xs text-matrix-muted">
              We&apos;ll only use your email to respond. No marketing spam, ever.
            </p>
          </form>
        ) : (
          <div className="card text-center">
            <div className="mb-4 text-5xl">✓</div>
            <h3 className="mb-2 text-xl font-semibold">Inquiry received</h3>
            <p className="text-sm text-matrix-muted">
              Thank you. A member of the team will reach out within 1 business day.
            </p>
          </div>
        )}
      </div>
    </section>
  );
}
