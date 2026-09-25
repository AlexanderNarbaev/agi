'use client';

import Script from 'next/script';

/**
 * WAVE T-04 — Plausible analytics integration.
 *
 * Privacy-friendly analytics (no cookies, GDPR-compliant by design).
 * Only loaded in production. Tracked events:
 * - page views (automatic)
 * - signup clicks
 * - demo runs
 * - pricing tier selections
 */
export default function Plausible() {
  const domain = process.env.NEXT_PUBLIC_PLAUSIBLE_DOMAIN;
  if (!domain) return null;

  return (
    <>
      <Script
        src="https://plausible.io/js/script.js"
        data-domain={domain}
        strategy="afterInteractive"
      />
      <Script
        id="plausible-events"
        strategy="afterInteractive"
        dangerouslySetInnerHTML={{
          __html: `
            window.plausible = window.plausible || function() {
              (window.plausible.q = window.plausible.q || []).push(arguments)
            };
          `,
        }}
      />
    </>
  );
}
