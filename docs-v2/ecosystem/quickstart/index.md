---
layout: default
title: Quickstart
nav_order: 2
parent: Ecosystem
permalink: /ecosystem/quickstart/
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# Quickstart — from a clone to a first answer

> **There is no hosted MATRIX API.** No `api.matrix.ai`, no API key, no plan and no rate
> limit — none of them exist. What exists is this repository, and you run the two
> applications from source.

## Prerequisites

- **JDK 25** — the build sets `sourceCompatibility = VERSION_25`, so the JDK the build sees
  must be 25, not merely a JRE on your `PATH`. Check with `java -version`.
- **Git.**
- **Nothing else.** The SQLite driver is a declared dependency, and the formal tool is
  tracked in the repository under `tools/` with its digest recorded in `tools/README.md`.

## Step 1 — get it

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi
```

## Step 2 — start chain A, the epistemic application

```bash
./gradlew :matrix-core:quarkusDev
```

It answers on **http://127.0.0.1:9091**. The health endpoint is `/q/health`:

```bash
curl -s http://127.0.0.1:9091/q/health
```

**The readiness probe answers 503** — the reactive PostgreSQL datasource gates it and no
production code reads it. Use the liveness endpoint.

## Step 3 — start chain B, the brain runtime

```bash
./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain
bash scripts/start-mind.sh
```

It answers on **http://127.0.0.1:8765**. The launcher selects production mode, so this
gives the real brain.

## Step 4 — check which brain is serving

This is the one thing to check first. Started **without** `MATRIX_MODE=production`, the
gateway answers `/v1/analyze` from an 87-line stub and reports `"brain_available": false`;
started through `scripts/start-mind.sh`, it runs the real cycle.

```bash
curl -s http://127.0.0.1:8765/health/live
```

## Step 5 — ask it something

```bash
# the gateway's own login
TOKEN=$(curl -s -X POST http://127.0.0.1:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# teach it, then ask
curl -s -X POST http://127.0.0.1:8765/v1/teach \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input":"What is the capital of France?","response":"Paris is the capital of France"}'

curl -s -X POST http://127.0.0.1:8765/v1/analyze \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input":"What is the capital of France?"}'
```

`/v1/teach` and `/v1/learn` answer **503 with a stated reason** when the gateway is in stub
mode: they need the production brain.

## Step 6 — ask chain A

```bash
curl -s http://127.0.0.1:9091/v1/workbench/status
curl -s http://127.0.0.1:9091/v1/workbench/workspaces
```

## The two modes, and what each one is

| mode | how it is selected | what serves | what you get |
|---|---|---|---|
| **stub** — the default | nothing to set | `StubBrainCycle`, 87 lines | a deterministic keyword answer; the routes that need the real brain answer 503 with a reason |
| **production** | `MATRIX_MODE=production`, which `scripts/start-mind.sh` sets | `ProductionBrainClient` → `TrueMindCycle` → `BirBrainCycle` | the real brain: BIR, HDC, the modulators, the knowledge base, the teaching loop |

The mode is reported in `GET /health/live`. **This is the owner's open question F-01**: a
gateway that serves a stub by default is a gateway whose health check can mislead.

If the `matrix-core` JAR is missing in production mode, `ProductionBrainClient` throws
`BrainUnavailableException` and the gateway answers **503** with `Retry-After: 5`; the stub
is the automatic fallback.

## In containers

```bash
export MATRIX_SECURITY_TOKEN="$(openssl rand -hex 32)"
docker compose up -d
```

The stack refuses to start without the token, and the application refuses exposed
anonymous writes without it. The **`native` profile does not work on the development
host**: the GraalVM toolchain is absent, the native test scope refuses to run rather than
report a pass, and the capability stays `BLOCKED` until a build succeeds.

## Run the tests and the gates

```bash
./gradlew :matrix-core:test --tests "io.matrix.knowledge.*"
bash scripts/quality-gate.sh
bash scripts/security-boundary-check.sh
```

A sanity check from a fresh clone: `./scripts/fresh-clone-smoke.sh`.

## Next

- [Getting started](../../guide/getting-started.html) — the full sequence, with the configuration that exists.
- [API reference](/ecosystem/api/) — the real route surfaces.
- [Architecture](/ecosystem/architecture/) — the module graph.
- [The runtime topology](../../architecture/RUNTIME-TOPOLOGY.html) — the measured serving graph.
