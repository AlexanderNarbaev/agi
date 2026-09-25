---
layout: default
title: Architecture
nav_order: 6
parent: Ecosystem
permalink: /ecosystem/architecture/
---

# Architecture

MATRIX is a **monorepo** with 9 modules organized into two layers:

## Layer 1 — Research Core (FROZEN at W1500)

- `matrix-core` — 1,039 Java files, 1,119+ tests, BIR/HDC/MCTS/transcoders
- `matrix-tools-distill` — ONNX distillation pipeline
- `matrix-spigot` — Minecraft Spigot bridge (legacy)
- `matrix-operator` — Kubernetes operator (legacy)

These modules are **stable** and don't accept breaking changes outside of
explicit CONSTITUTION amendments.

## Layer 2 — Ecosystem (T-01 .. T-10)

- `matrix-api-gateway` — REST API + auth + rate limit
- `matrix-sdk-java` — Java SDK (Maven Central)
- `matrix-audit` — hash-chained audit logs (T-06)
- `matrix-billing` — Stripe + credit ledger (T-07)
- `matrix-observability` — Prometheus/Grafana stack (T-09)

These modules **depend on matrix-core** but never modify it.

## C4 Container Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                  MATRIX ECOSYSTEM                           │
├─────────────────────────────────────────────────────────────┤
│   [WEB UI]  Next.js — Landing, Dashboard, Docs Portal      │
│       ↓ HTTPS/WSS                                            │
│   [API GATEWAY]  Quarkus REST                                │
│       ├── JWT + OAuth2  (matrix-api-gateway/security)       │
│       ├── RBAC  (Admin / Developer / Viewer)                │
│       ├── Rate Limiter  (Redis, 100/1k/∞ per hour)         │
│       └── OpenAPI 3.0  (/q/swagger-ui)                      │
│       ↓ internal calls                                       │
│   [RESEARCH CORE]  matrix-core  (FROZEN)                     │
│       ├── BIR (logic)        ─┐                             │
│       ├── HDC (memory)        ├─ HYBRID INFERENCE           │
│       ├── MCTS (planning)    ─┘                             │
│       ├── FROZEN modulators (Ethics/Safety/Consistency/Lie)  │
│       └── 1,119+ tests, GraalVM native binary                │
│       ↓ cross-cutting                                         │
│   [AUDIT]  Hash-chained logs  (SHA-256, immutable)          │
│   [BILLING]  Stripe + credits + license engine              │
│   [OBSERVABILITY]  Prometheus / Grafana / Loki / Jaeger     │
└─────────────────────────────────────────────────────────────┘
```

## Data Flow

```
[Client] HTTP POST /v1/analyze
  ↓
[API Gateway] JwtAuthFilter → RBAC → RateLimit
  ↓
[Audit] HashChainedLog.append(request)
  ↓
[Research Core] BrainCycle.cycle(input)
   ├─ BIR rules fire
   ├─ HDC memory retrieval
   ├─ MCTS plan search
   └─ 4 Modulators vote
  ↓
[Research Core] CycleResult { reply, confidence, trace }
  ↓
[Audit] HashChainedLog.append(response)
  ↓
[Observability] Prometheus counter++
  ↓
[Client] HTTP 200 { reply, confidence, explain_id, trace }
```

## Module Dependency Graph

```
matrix-api-gateway → matrix-core
matrix-api-gateway → matrix-audit (T-06)
matrix-api-gateway → matrix-billing (T-07)
matrix-api-gateway → matrix-observability (T-09)
matrix-sdk-java    → matrix-api-gateway
matrix-web-ui      → matrix-api-gateway (REST + WSS)

matrix-core ← NO INCOMING from ecosystem (one-way)
```

## CONSTITUTION Boundary

All inference happens in **matrix-core**. The ecosystem modules are pure
transport. This separation guarantees:

- ✅ Ecosystem can evolve rapidly without CONSTITUTION violations
- ✅ Billing/UI/SDK changes never affect decision quality
- ✅ Research core stays a clean library

## Scaling Characteristics

- **Single node**: 1,000+ requests/sec, 50ms p99 latency
- **Federation pool**: 1,000+ nodes via consistent hashing (T-23)
- **Audit**: append-only, 100k entries/instance, hash-chain verifiable
- **Rate limit**: per-user, per-plan, sliding window

## Detailed Architecture Documents

- [ECOSYSTEM-LAYOUT.md](/architecture/ECOSYSTEM-LAYOUT) — full C4 diagram
- [MODULES.md](/architecture/MODULES) — module-by-module overview
- [RUNTIME-TOPOLOGY.md](/architecture/RUNTIME-TOPOLOGY) — federation topology
- [FEDERATION-MODULE.md](/architecture/FEDERATION-MODULE) — federation internals

## Next

- 📜 [Constitution →](/ecosystem/constitution/) — ethical constraints
- 🔍 [XAI Deep Dive →](/ecosystem/xai/) — explanation pipeline
- 📚 [Algorithms →](/ecosystem/algorithms/) — deep dives on each technique

---

**Last updated:** 2026-09-21 (Wave T-03)
