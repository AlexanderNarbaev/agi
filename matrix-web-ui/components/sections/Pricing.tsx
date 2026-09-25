import Link from 'next/link';

const PLANS = [
  {
    name: 'FREE',
    price: '$0',
    period: '/forever',
    description: 'For developers, students, and hobby projects.',
    features: [
      '100 API requests / hour',
      '1 user',
      'Community support',
      'Public docs',
      'Sandbox environment',
    ],
    cta: 'Get Started',
    ctaHref: 'https://console.matrix.ai/signup?plan=free',
    highlighted: false,
  },
  {
    name: 'PRO',
    price: '$49',
    period: '/month',
    description: 'For startups and small teams building real products.',
    features: [
      '1,000 API requests / hour',
      '5 users',
      'Email support (24h SLA)',
      'Java + Python + JS SDKs',
      'Live demo with real data',
      'Basic XAI dashboard',
    ],
    cta: 'Start Free Trial',
    ctaHref: 'https://console.matrix.ai/signup?plan=pro',
    highlighted: true,
  },
  {
    name: 'ENTERPRISE',
    price: 'Custom',
    period: 'contact sales',
    description: 'For regulated industries and high-stakes deployments.',
    features: [
      'Unlimited API requests',
      'Unlimited users',
      'On-prem deployment',
      'Dedicated support (4h SLA)',
      'SOC 2 Type II audit reports',
      'Custom SLA & contract',
      'Direct line to engineering',
    ],
    cta: 'Contact Sales',
    ctaHref: '#contact',
    highlighted: false,
  },
];

export default function Pricing() {
  return (
    <section id="pricing" className="border-t border-matrix-border bg-matrix-bg py-24">
      <div className="container-wide">
        <div className="mx-auto mb-16 max-w-2xl text-center">
          <p className="mb-2 font-mono text-sm uppercase tracking-wide text-matrix-primary">
            Pricing
          </p>
          <h2 className="mb-4 text-4xl font-bold md:text-5xl">Start free. Scale when ready.</h2>
          <p className="text-lg text-matrix-muted">
            All plans include the full explainability stack. No surprise overage charges.
            Cancel anytime.
          </p>
        </div>

        <div className="grid gap-6 lg:grid-cols-3">
          {PLANS.map((plan) => (
            <div
              key={plan.name}
              className={
                plan.highlighted
                  ? 'card border-matrix-primary ring-2 ring-matrix-primary/30'
                  : 'card'
              }
            >
              {plan.highlighted && (
                <span className="absolute -top-3 left-6 rounded-full bg-matrix-primary px-3 py-1 text-xs font-semibold uppercase tracking-wide text-matrix-bg">
                  Most Popular
                </span>
              )}

              <h3 className="mb-2 font-mono text-sm uppercase tracking-wide text-matrix-muted">
                {plan.name}
              </h3>
              <div className="mb-4 flex items-baseline gap-1">
                <span className="text-4xl font-bold">{plan.price}</span>
                <span className="text-sm text-matrix-muted">{plan.period}</span>
              </div>
              <p className="mb-6 text-sm text-matrix-muted">{plan.description}</p>

              <ul className="mb-8 space-y-3">
                {plan.features.map((feature) => (
                  <li key={feature} className="flex items-start gap-2 text-sm">
                    <svg
                      className="mt-0.5 h-4 w-4 flex-shrink-0 text-matrix-primary"
                      fill="currentColor"
                      viewBox="0 0 20 20"
                    >
                      <path
                        fillRule="evenodd"
                        d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                        clipRule="evenodd"
                      />
                    </svg>
                    <span className="text-matrix-text">{feature}</span>
                  </li>
                ))}
              </ul>

              <Link
                href={plan.ctaHref}
                className={plan.highlighted ? 'btn-primary w-full' : 'btn-secondary w-full'}
              >
                {plan.cta}
              </Link>
            </div>
          ))}
        </div>

        <p className="mt-12 text-center text-sm text-matrix-muted">
          Need something custom?{' '}
          <Link href="#contact" className="text-matrix-primary hover:underline">
            Contact sales
          </Link>
          .
        </p>
      </div>
    </section>
  );
}
