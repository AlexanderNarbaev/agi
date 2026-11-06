---
layout: default
title: Ecosystem
nav_order: 5
has_children: true
permalink: /ecosystem/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# The MATRIX ecosystem

This section describes what can be **built on and run from this repository** — the two
serving applications, their interfaces, their deployment assumptions, and the limits that a
caller has to know about.

Everything here is stated against the running system. Where a page in this section cannot be
sourced against the code or a canonical document, it says so rather than asserting.

## What the ecosystem is today

MATRIX is **not** a hosted service. There is no `api.matrix.ai`, no API key, no rate-limit
tier and no pricing plan — none of those exist, and the pages that described them have been
corrected rather than kept. The deliverable is **the repository**: two applications you run
from source, a capability registry that states what each of them actually does, and the
research record behind the claims.

| | **chain A** — the epistemic application | **chain B** — the brain runtime |
|---|---|---|
| the process | Quarkus, `io.matrix.MatrixApplication` | JDK `HttpServer`, `io.matrix.api.MinimalHttpServer` |
| the module | `matrix-core` | `matrix-api-gateway` |
| the port | **9091** (HTTP, `127.0.0.1` by default) | **8765** (`127.0.0.1` unless the security mode says otherwise) |
| the routes | **210** | **28** |
| the main surfaces | `/v1/workbench` (57), `/v1/cognitive` (11), `/v1` chat (6), and 36 further resources | `/v1/analyze`, `/v1/explain`, `/v1/learn`, `/v1/teach`, `/v1/sleep`, `/v1/goals`, `/v1/federate`, `/v1/audit*`, `/v1/gpu`, `/v1/distill`, `/v1/bir`, `/v1/conflicts`, `/v1/generalize`, `/v1/status`, `/v1/transcode/*`, `/v1/inbox/scan`, `/v1/auth/login`, and the health and OpenAPI routes |
| the default answer | the real application | **`stub`** — an 87-line `StubBrainCycle`, unless `MATRIX_MODE=production` |

The two chains **do not meet**: chain B reaches zero classes of the epistemic and cognitive
stores, and the isolation is proved by a gate that fails when they do. The canonical
description is [`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../architecture/RUNTIME-TOPOLOGY.md).

## What is in this section

| the page | what it holds |
|---|---|
| [Concept](/ecosystem/concept/) | what the system is, and what the terms mean |
| [Quickstart](/ecosystem/quickstart/) | running the two chains from a clone, with the real commands |
| [API Reference](/ecosystem/api/) | the real route surfaces, and the ports they answer on |
| [SDK Guides](/ecosystem/sdks/) | the SDK that exists in this repository, and the ones that do not |
| [XAI Deep Dive](/ecosystem/xai/) | how a decision is explained, and where the trace comes from |
| [Architecture](/ecosystem/architecture/) | the module graph, with each module's real status |
| [Constitution](/ecosystem/constitution/) | the ethical constraints and the frozen zones |
| [Algorithms](/ecosystem/algorithms/) | the algorithms of the stack, one page each |

## The status that is claimed, and where it comes from

The maturity of a capability in this repository is stated on **four independent axes** — does
the source exist, is it wired in, what has been executed, and what is it doing now — and the
registry that holds them is
[`docs-v2/architecture/CAPABILITY-REGISTRY.yml`](../architecture/CAPABILITY-REGISTRY.yml).
Three rules keep it honest:

1. a capability may not be marked `SERVED` without a **named serving path**;
2. a capability may not be marked `MEASURED` without a **reproducible benchmark**;
3. a capability may not be marked `FORMALLY_VERIFIED` because a specification file exists —
   it needs the model-check evidence.

## The deployment assumptions, stated as they are

- **There is no deployment.** The repository is the deliverable; there is no release version,
  and the tags that exist predate several waves of work.
- The **container stack** in `docker-compose.yml` is real and it is what it says: PostgreSQL,
  Redis and Kafka, plus the Quarkus application on `8080` inside the container (published on
  the host's loopback) and a native-image profile. The stack **refuses to start** without
  `MATRIX_SECURITY_TOKEN`, and the application refuses to serve exposed writes without it.
- The **native-image profile is blocked**: the GraalVM toolchain is absent on the development
  host, the native scope refuses to run rather than report a pass, and the capability stays
  `BLOCKED` until a build succeeds.
- The **GPU is an optional accelerator** (the owner's decision OD-047 of 2026-11-06), not a
  requirement of the serving path. The measured factor on the serving path is **12.06×**
  (12.06 / 11.68 / 11.47 over three runs), while a single-point ONNX call is **0.24×** — the
  GPU is about four times slower there. The measurement is in
  [`docs-v2/engineering/W48-GPU-BENCHMARK.md`](../engineering/W48-GPU-BENCHMARK.md).

## The limits a caller has to know about

| the limit | what it means for you |
|---|---|
| the gateway **serves a stub by default** | `/v1/analyze` answers from an 87-line stub unless `MATRIX_MODE=production` is set. The active mode is visible in `GET /health/live` |
| chain A binds **two undeclared ports** | 9093 (multicast) and 9094 (TCP) bind without appearing in any serving record — the owner's open question F-02 |
| the **readiness probe is permanently 503** | the reactive PostgreSQL datasource gates it and no production code reads it; use the liveness probe instead |
| **no whole-database backup exists** | the SQLite backup API appears nowhere in the repository; the restore drill works on a copy |
| the **quality gates do not block** | they live in a CI job named "Quality gates (can fail)" |
| the **corpus properties skip on CI** | the two classes that need the owner's corpus are skipped on a clean runner, because the corpus is not in git. A skip is not a pass |

The full list, with the owner each item waits on, is in
[`docs-v2/governance/PROJECT-STATE.md`](../governance/PROJECT-STATE.md) §4.

## Where to go next

- [Getting started](../guide/getting-started.html) — the commands that actually work.
- [The runtime topology](../architecture/RUNTIME-TOPOLOGY.html) — the measured serving graph.
- [The capability registry](../architecture/CAPABILITY-REGISTRY.yml) — every claim and its evidence.
- [Contributing](../../CONTRIBUTING.md) — the two contribution paths.
