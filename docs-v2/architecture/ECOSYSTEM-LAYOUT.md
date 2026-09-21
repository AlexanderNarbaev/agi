# MATRIX Ecosystem — Architecture (C4 Container View)

**Wave:** T-01
**Date:** 2026-09-21
**Status:** Skeleton established, modules to be populated in T-02..T-10

---

## System Context

```
┌──────────────────────────────────────────────────────────────────────────┐
│                       External Users                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ Partners │  │   Devs   │  │ Auditors │  │ Admins   │  │ Billing  │  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  │
│       │             │              │             │             │         │
└───────┼─────────────┼──────────────┼─────────────┼─────────────┼─────────┘
        │             │              │             │             │
        ▼             ▼              ▼             ▼             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                   MATRIX ECOSYSTEM (this repo)                            │
│                                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  LANDING PAGE / DOCS / DASHBOARD  (Next.js / matrix-web-ui)         │  │
│  │  ── Marketing, pricing, XAI, docs portal ──                         │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│       ▲                                                                  │
│       │ HTTPS / WSS                                                       │
│       ▼                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  API GATEWAY (matrix-api-gateway, Quarkus REST)                     │  │
│  │  ── JWT/OAuth2, RBAC, rate-limit, OpenAPI 3.0 ──                    │  │
│  │  ── /v1/analyze, /v1/explain, /v1/federate, /v1/audit ──            │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│       ▲                          ▲                       ▲               │
│       │                          │                       │               │
│       ▼                          ▼                       ▼               │
│  ┌──────────────┐  ┌─────────────────────┐  ┌────────────────────────┐  │
│  │ matrix-sdk-  │  │ matrix-audit        │  │ matrix-billing         │  │
│  │ java         │  │ (hash-chained       │  │ (Stripe + credits +    │  │
│  │ (Maven       │  │  logs + GDPR        │  │  license engine)       │  │
│  │  Central)    │  │  pruning + PDF)     │  │                        │  │
│  └──────────────┘  └─────────────────────┘  └────────────────────────┘  │
│       ▲                                                                  │
│       │ Java SDK  │  PyPI (matrix-sdk-python)  │  NPM (@matrix/sdk)       │
│       ▼                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  RESEARCH CORE (matrix-core, FROZEN at W1500)                       │  │
│  │  ── BIR (Logic) + HDC (Memory) + MCTS (Plan) ──                     │  │
│  │  ── Omni-modal transcoders (FFT + Sobel + distilled ONNX) ──        │  │
│  │  ── Federation (1000+ nodes, K8s/Spark ready) ──                    │  │
│  │  ── 1,119 passing tests, 0 failing ──                                │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
│       ▲                                                                  │
│       │                                                                  │
│       ▼                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │  OBSERVABILITY (matrix-observability)                               │  │
│  │  ── Prometheus (9090) + Grafana (3000) + Jaeger (16686) ──          │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## Module Matrix (Wave → Module → Stack)

| Wave | Module | Stack | Purpose |
|------|--------|-------|---------|
| **FROZEN** | `matrix-core` | Quarkus + GraalVM native + ONNX + Pekko | Research core (BIR/HDC/MCTS) |
| **FROZEN** | `matrix-tools-distill` | Quarkus | ONNX distillation pipeline |
| **FROZEN** | `matrix-spigot` | Quarkus | Minecraft Spigot bridge |
| **FROZEN** | `matrix-operator` | Quarkus | K8s operator |
| **T-02** | `matrix-api-gateway` | Quarkus REST + OIDC + Redis | External REST/GraphQL |
| **T-08** | `matrix-sdk-java` | JDK 25 HTTP client | Maven Central publish |
| **T-08** | `matrix-sdk-python` | httpx + Pydantic | PyPI publish |
| **T-08** | `matrix-sdk-js` | TypeScript + fetch | NPM publish |
| **T-05** | `matrix-web-ui` | Next.js 14 + React + WebGL | Dashboard |
| **T-04** | `matrix-web-ui` | Next.js + Tailwind | Landing page |
| **T-03** | `docs-v2/` | MkDocs Material + i18n | Bilingual docs |
| **T-06** | `matrix-audit` | SHA-256 + BouncyCastle + PDFBox | Hash-chained logs |
| **T-07** | `matrix-billing` | Stripe Java SDK + Postgres | Subscriptions |
| **T-09** | `matrix-observability` | Prometheus + Grafana + Loki + Jaeger | Monitoring stack |

---

## Cross-Module Dependencies

```
matrix-api-gateway ──▶ matrix-core      (BIR/HDC calls via BrainCycle)
matrix-api-gateway ──▶ matrix-audit     (every request logged)
matrix-api-gateway ──▶ matrix-billing   (rate-limit by credits)
matrix-api-gateway ──▶ matrix-observability  (Prometheus metrics)
matrix-sdk-java    ──▶ matrix-api-gateway (REST client)
matrix-sdk-python  ──▶ matrix-api-gateway (REST client)
matrix-sdk-js      ──▶ matrix-api-gateway (REST client)
matrix-web-ui      ──▶ matrix-api-gateway (REST + WSS)
matrix-core        ──▶ (no upstream deps; self-contained)
matrix-audit       ──▶ (no upstream deps; pure logic)
matrix-billing     ──▶ (no upstream deps; pure logic)
matrix-observability ▶ (no upstream deps; aggregates from all)
```

**No circular dependencies.** All arrows go from new module → existing core.
`matrix-core` has zero knowledge of the API gateway, ensuring the research
core stays a clean library.

---

## Data Flow: Analyze Request

```
[Client] HTTP POST /v1/analyze { text: "What is 2+2?" }
   │
   ▼
[matrix-api-gateway] JwtAuthFilter ──▶ RBAC check ──▶ rate-limit (Redis)
   │
   ▼
[matrix-api-gateway] AnalyzeResource.analyze()
   │
   ├─▶ [matrix-audit] HashChainedLog.append(request)
   ├─▶ [matrix-billing] CreditLedger.consume(1)
   │
   ▼
[matrix-core] BrainCycle.cycle(text)
   │  ── BirBrainCycle → HDC memory → MCTS plan
   │  ── Modulators: Ethics + Safety + Consistency (FROZEN)
   │
   ▼
[matrix-core] CycleResult { reply, confidence, trace }
   │
   ▼
[matrix-api-gateway] ExplainResource.build(trace) ──▶ XAI breakdown
   │
   ├─▶ [matrix-audit] HashChainedLog.append(response)
   ├─▶ [matrix-observability] Prometheus counter ++
   │
   ▼
[Client] HTTP 200 { reply, confidence, explain_id, trace }
```

---

## GraalVM Native Compatibility

- `matrix-core` already compiles to native (126MB binary via Mandrel).
- `matrix-api-gateway` will follow the same pattern (Quarkus + native).
- `matrix-sdk-java` is a library — no native compilation needed.
- `matrix-web-ui` is JavaScript — N/A.
- `matrix-audit`, `matrix-billing`, `matrix-observability` are libraries
  or stand-alone servers — Quarkus-native optional.

---

## CONSTITUTION Compliance Boundary

```
┌──────────────────────────────────────────────────────────┐
│  CONSTITUTION ENFORCED HERE                               │
│  ── No LLM in any runtime path ──                        │
│  ── Seeded Random only ──                                │
│  ── FROZEN modulators (Ethics, Safety, Consistency) ──   │
└──────────────────────────────────────────────────────────┘
              ▲
              │ ALL inference flows into matrix-core
              │
┌─────────────┴─────────────────────────────────────────────┐
│  ECOSYSTEM CODE — pure transport, billing, audit, UI     │
│  No inference happens here. Only calls into matrix-core. │
└───────────────────────────────────────────────────────────┘
```

This separation guarantees that the ecosystem can evolve rapidly
(billing changes, UI redesigns, new SDK languages) without ever
violating the CONSTITUTION.

---

## Repository Layout

```
agi/                                  ← single monorepo (research + ecosystem)
├── matrix-core/                      ← FROZEN (W1500 state)
├── matrix-tools-distill/             ← FROZEN
├── matrix-spigot/                    ← FROZEN
├── matrix-operator/                  ← FROZEN
├── matrix-api-gateway/               ← NEW (T-02)
├── matrix-sdk-java/                  ← NEW (T-08)
├── matrix-audit/                     ← NEW (T-06)
├── matrix-billing/                   ← NEW (T-07)
├── matrix-observability/             ← NEW (T-09)
├── matrix-fpga/, matrix-micro/, matrix-ros2/   ← experimental
│
├── docs-v2/                          ← bilingual docs (RU + EN)
│   ├── architecture/                 ← C4 diagrams, decisions
│   ├── research/                     ← benchmark reports
│   ├── story/                        ← narrative
│   └── ...
│
├── .github/workflows/                ← CI/CD
├── BRANCHING-STRATEGY.md
├── CONSTITUTION.md
├── SESSION.md
└── WAL.md
```

Polyglot SDKs (matrix-sdk-python, matrix-sdk-js, matrix-web-ui) live
in **separate repositories** to keep language toolchains isolated:

- `github.com/AlexanderNarbaev/matrix-sdk-python`
- `github.com/AlexanderNarbaev/matrix-sdk-js`
- `github.com/AlexanderNarbaev/matrix-web-ui`

This allows pip/npm/CI tools to run without dragging in Java/Gradle.

---

**Last updated:** 2026-09-21 (Wave T-01)
