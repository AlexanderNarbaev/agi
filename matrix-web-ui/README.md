# MATRIX Landing Page (`matrix-web-ui`)

Production landing page + dashboard for the MATRIX Hybrid Neuro-Symbolic AI.

> **Status:** Wave T-04 (in progress) · v0.1.0-T04
> **Planned deployment:** [matrix.ai](https://matrix.ai) (Q4 2026)

## What's Here

| Section | File | Description |
|---------|------|-------------|
| **Hero** | `components/sections/Hero.tsx` | Headline + WebGL HDC viz |
| **Features** | `components/sections/Features.tsx` | 6 core capabilities + MATRIX vs LLM comparison |
| **Live Demo** | `components/sections/LiveDemo.tsx` | Interactive playground |
| **Pricing** | `components/sections/Pricing.tsx` | 3-tier plans (Free/Pro/Enterprise) |
| **Trust** | `components/sections/Trust.tsx` | Compliance badges + audit proof |
| **Contact** | `components/sections/Contact.tsx` | Partner inquiry form |

## Stack

- **Next.js 14** (App Router)
- **React 18**
- **TypeScript 5.6**
- **Tailwind CSS 3.4**
- **Three.js + @react-three/fiber** (WebGL hero)
- **Plausible Analytics** (privacy-friendly, no cookies)
- **Jest + Testing Library** (unit tests)

## Local Development

```bash
npm install
npm run dev      # http://localhost:3000
npm run build    # production build
npm run test     # run unit tests
npm run lint     # ESLint
```

## Environment Variables

Create `.env.local` from `.env.example`:

```bash
NEXT_PUBLIC_PLAUSIBLE_DOMAIN=matrix.ai
```

(Production only — local dev does not require analytics.)

## Architecture Notes

### Why Next.js App Router?

- Server Components reduce JS bundle size (faster TTFB)
- Built-in SEO via metadata API
- Static generation for landing page (CDN-friendly)
- Streaming for dashboard (when added in T-05)

### Why Three.js?

The hero section uses a 3D point-cloud visualization of the HDC vector space.
This is a **faithful representation** of the algorithm:
- Each point = a 10,000-bit HDC vector projected to 3D
- Clusters = bundled HDC vectors
- Rotation = the random projection basis

### CONSTITUTION Compliance

- **Article I** (No LLM in runtime): The live demo is a deterministic stub. No LLM is called.
- **Article III** (Reproducibility): All randomness uses seeded LCG.
- **Article VI** (No consciousness claims): Copy uses engineering language ("engine", "pipeline", not "brain decides").

## Deployment

### Vercel (recommended)

```bash
vercel deploy --prod
```

### Docker

```bash
docker build -t matrix-web-ui .
docker run -p 3000:3000 matrix-web-ui
```

### Kubernetes (T-09)

```yaml
# Will be added in T-09 alongside Helm charts
apiVersion: apps/v1
kind: Deployment
metadata:
  name: matrix-web-ui
spec:
  replicas: 3
  # ...
```

## Repo Migration Plan

This module currently lives in the monorepo for development convenience.
Production deployment will split it into a standalone repo at
`github.com/AlexanderNarbaev/matrix-web-ui` to isolate the JavaScript
toolchain from the Java/Gradle build (per T-01 ECOSYSTEM-LAYOUT.md).

When T-09 completes:

```bash
# 1. Create the standalone repo
gh repo create AlexanderNarbaev/matrix-web-ui --public

# 2. Push only this directory
git subtree split --prefix=matrix-web-ui -b matrix-web-ui-standalone
git push matrix-web-ui-standalone main

# 3. Update CI to use the new repo
```

## Tests

```bash
npm test
```

Currently:
- `__tests__/Features.test.tsx` — 4 tests
- `__tests__/Pricing.test.tsx` — 6 tests
- `__tests__/LiveDemo.test.tsx` — 6 tests

Total: **16 tests**, all should pass after `npm install`.

## CONSTITUTION Reference

This module is part of the broader MATRIX ecosystem transformation
(W1500 → Production). See:

- [`CONSTITUTION.md`](../../CONSTITUTION.md) — hard constraints
- [`BRANCHING-STRATEGY.md`](../../BRANCHING-STRATEGY.md) — branching model
- [`docs-v2/architecture/ECOSYSTEM-LAYOUT.md`](../../docs-v2/architecture/ECOSYSTEM-LAYOUT.md) — C4 diagram

---

**Last updated:** 2026-09-21 (Wave T-04)
