---
layout: default
title: API Reference
nav_order: 3
parent: Ecosystem
permalink: /ecosystem/api/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# API Reference

> **There is no hosted MATRIX API.** This repository serves two applications from source, on
> two ports, and this page describes the routes they actually register. The previous
> description of a hosted endpoint at `api.matrix.ai`, with API keys, plans and rate
> limits, described a service that does not exist and has been removed rather than kept.

Everything below is read from the code and from the constant-pool census behind
[`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../../architecture/RUNTIME-TOPOLOGY.md).

## The two surfaces

| | **chain A** | **chain B** |
|---|---|---|
| the address | `http://127.0.0.1:9091` | `http://127.0.0.1:8765` |
| the process | Quarkus, `io.matrix.MatrixApplication` | JDK `HttpServer`, `io.matrix.api.MinimalHttpServer` |
| the routes | **210** | **28** |
| the authentication | the security mode of the deployment; the compose stack requires `MATRIX_SECURITY_TOKEN` and the application refuses exposed anonymous writes without it | `/v1/auth/login` returns a bearer token |

## Chain A — the epistemic application

JAX-RS resources, served by Quarkus. The route counts are the census of 2026-11-04.

| the surface | the routes | the resource |
|---|---|---|
| `/v1/workbench` | 57 | `LearningWorkbenchResource` — the epistemic workbench: workspaces, documents, concepts, claims, sources, evidence, representations, revisions, the destructive load and the restore |
| `/v1/cognitive` | 11 | `CognitiveResource` — observe, solve, summary, predictions, errors, procedures, tasks |
| `/v1` (chat) | 6 | `OpenAIChatResource` — `/v1/chat/completions`, `/v1/models`, `/v1/embeddings`, and the `/v1/brain/**` routes (`think`, `plan`, `subagent`) |
| 36 further resources | 136 | `/api/v1/**` and `/v1/**`: `agent`, `benchmark`, `chain-debug`, `chain-status`, `chat/feedback`, `chat/status`, `generate`, `health`, `metrics`, `models-registry`, `onnx`, `qa`, `sandbox`, `state`, `tenant`, `train`, and the `/api/v1/**` family (`bot/ethics`, `bot/headless`, `explain`, `federated`, `generation`, `import`, `ingest`, `multimodal`, `noosphere`, `noosphere/p2p`, `privacy`, `privacy/cascade`, `self-agent`, `tools`, `verification`) |

Health is under the SmallRye root path `/q/health`. **The readiness probe answers 503** — the
reactive PostgreSQL datasource gates it, and no production code reads it.

## Chain B — the brain runtime

The contexts registered by `MinimalHttpServer`:

| the route | what it does |
|---|---|
| `/v1/analyze` | the inference request |
| `/v1/explain` | the count of the stored explanations |
| `/v1/explain/{id}` | one stored explanation, as a trace |
| `/v1/audit`, `/v1/audit/logs`, `/v1/audit/promotions`, `/v1/audit/verify` | the audit log, the promotion decisions — the refusals included — and the chain verification |
| `/v1/bir` | the boolean inference surface |
| `/v1/conflicts` | the recorded conflicts |
| `/v1/distill` | the distillation surface |
| `/v1/federate` | the federation surface |
| `/v1/generalize` | the generalisation surface |
| `/v1/gpu` | the accelerator state — it reports UNAVAILABLE when there is no GPU, and never simulates success |
| `/v1/goals` | the goal tracker |
| `/v1/inbox/scan` | the inbox scan |
| `/v1/learn`, `/v1/teach` | the teaching loop — both answer 503 in stub mode |
| `/v1/sleep` | the sleep cycle |
| `/v1/status` | the runtime status |
| `/v1/transcode/audio`, `/v1/transcode/image` | the transcoders |
| `/v1/auth/login` | the bearer token |
| `/health/live`, `/health/ready` | the probes |
| `/metrics`, `/q/openapi`, `/openapi.yaml`, `/` | the operational and the schema surfaces |

**Two limits of this surface, stated plainly.** The explanations are held **in memory** in
the gateway process, so an `explain_id` is valid only while that process lives. And the
**default mode is `stub`**: without `MATRIX_MODE=production`, `/v1/analyze` answers from an
87-line `StubBrainCycle`, and the mode is reported by `GET /health/live`.

## A first exchange, as it actually is

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -s -X POST http://127.0.0.1:8765/v1/analyze \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input":"What is the capital of France?"}'
```

**The response shape depends on the mode.** In stub mode the answer is a deterministic
keyword reply; in production mode it comes from the real cycle and carries what that cycle
produced. The two are not interchangeable, and the health endpoint says which one you are
talking to.

An unknown `explain_id` answers **404**; a missing or invalid token answers **401**.

## What is not here

- **No hosted endpoint, no key, no plan and no rate limit.** Nothing in this repository
  implements them.
- **No separately published OpenAPI document** — the gateway serves its own schema at
  `/q/openapi` and `/openapi.yaml`.
- **Nine declared JAX-RS routes on the gateway are not mounted**: `BrainQuarkusResource`
  (4), `AnalyzeResource`, `AuditResource`, `ExplainResource` (2) and `FederateResource` (2).
  A path that is declared and not mounted answers 404.

## Next

- [Quickstart](/ecosystem/quickstart/) — the commands that work.
- [The runtime topology](../../architecture/RUNTIME-TOPOLOGY.html) — the measured route census.
- [Getting started](../../guide/getting-started.html) — the full sequence.
