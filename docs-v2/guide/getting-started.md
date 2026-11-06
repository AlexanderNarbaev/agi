---
layout: default
title: Getting Started with MATRIX
---

**Status:** ACTIVE · **Updated:** 2026-11-06

# Getting started with MATRIX

> **For:** engineers and researchers who want to run the system and check its claims.
> **Prerequisites:** **JDK 25** — the build sets `sourceCompatibility = VERSION_25`, so the JDK
> the build sees must be 25, not just a JRE on your `PATH`. Nothing else: the SQLite driver
> is a declared dependency and the formal tool is tracked in the repository.
> **Source of truth:** [`CONTRIBUTING.md`](../../CONTRIBUTING.md). This page is the short
> version of it; where the two differ, `CONTRIBUTING.md` is right.

There is **no hosted API** in this project. There is no `api.matrix.ai`, no API key, no
rate-limit tier and no pricing plan — none of those exist. What exists is a repository with
**two serving applications that do not meet**, and this page is how to run them.

## The two applications, and the ports they answer on

| | **chain A** — the epistemic application | **chain B** — the brain runtime |
|---|---|---|
| the address | `http://127.0.0.1:9091` | `http://127.0.0.1:8765` |
| the process | Quarkus, `io.matrix.MatrixApplication` | JDK `HttpServer`, `io.matrix.api.MinimalHttpServer` |
| the routes | 210 — `/v1/workbench` (57), `/v1/cognitive` (11), `/v1/chat/completions` (6) and 36 further resources | 28 — `/v1/analyze`, `/v1/explain`, `/v1/learn`, `/v1/teach`, `/v1/sleep`, `/v1/goals`, `/v1/status`, `/health/live`, `/metrics` and the rest |
| the default answer | the real application | **a stub** — 87 lines of `StubBrainCycle` — unless `MATRIX_MODE=production` is set |

## Run chain A — the epistemic application

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi

./gradlew :matrix-core:quarkusDev
```

It answers on **http://127.0.0.1:9091**. The health endpoint is `/q/health` (the
SmallRye root path is configured in `matrix-core/src/main/resources/application.properties`).
Note that the **readiness probe answers 503** — the reactive PostgreSQL datasource gates it
and no production code reads it — so use the liveness endpoint, not the readiness one.

A first call against the workbench:

```bash
# the workbench's own status
curl -s http://127.0.0.1:9091/v1/workbench/status

# the workspaces it knows
curl -s http://127.0.0.1:9091/v1/workbench/workspaces
```

The chain-A surface is documented in the canonical topology:
[`docs-v2/architecture/RUNTIME-TOPOLOGY.md`](../architecture/RUNTIME-TOPOLOGY.md).

## Run chain B — the brain runtime

```bash
./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain
bash scripts/start-mind.sh
```

It answers on **http://127.0.0.1:8765**. `scripts/start-mind.sh` selects production mode, so
this launcher gives the **real** brain (`TrueMindCycle` → `BirBrainCycle`).

```bash
# which mode is serving, and whether the brain is there
curl -s http://127.0.0.1:8765/health/live
```

**The mode is the thing to check.** Started by hand without `MATRIX_MODE=production`, the
gateway answers `/v1/analyze` from the stub and reports `"brain_available": false`. That is
the owner's open question F-01, and it is why the first call below is a health check.

```bash
# authenticate (the gateway's own /v1/auth/login)
TOKEN=$(curl -s -X POST http://127.0.0.1:8765/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# teach the brain, then ask it
curl -s -X POST http://127.0.0.1:8765/v1/teach \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input":"What is the capital of France?","response":"Paris is the capital of France"}'

curl -s -X POST http://127.0.0.1:8765/v1/analyze \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"input":"What is the capital of France?"}'
```

The routes that need the production brain answer **503 with a stated reason** when the
gateway is in stub mode (`/v1/teach` and `/v1/learn`).

## Run it in containers

`docker-compose.yml` brings up PostgreSQL, Redis, Kafka and the application, plus a
native-image profile and a Minecraft server.

```bash
export MATRIX_SECURITY_TOKEN="$(openssl rand -hex 32)"
docker compose up -d
```

**Two things to know before you start it.** The stack **refuses to start without
`MATRIX_SECURITY_TOKEN`** — the `${VAR:?message}` in the compose file is deliberate — and
the application refuses exposed anonymous writes without it. And the **`native` profile
does not work here**: the GraalVM toolchain is absent on the development host, the native
test scope refuses to run rather than report a pass, and the capability stays `BLOCKED`
until a build succeeds.

## Run the tests — a targeted scope, not the whole suite

The full `matrix-core` suite is **not** run in this repository's practice: it needs the
owner's approval, because of its memory cost. The baseline is a set of **scopes**, each
verified on a named commit, and the per-scope registry is
[`docs-v2/engineering/TEST-CATALOGUE.yml`](../engineering/TEST-CATALOGUE.yml).

```bash
./gradlew :matrix-core:test --tests "io.matrix.knowledge.*"
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*"
```

The rules that matter:

- **the JUnit XML is the evidence**, not the Gradle console summary — it lives under
  `<module>/build/test-results/test/*.xml`;
- **a baseline is commit-specific** — a stale count is not a baseline;
- **a scope that could not run is not a pass** — the native-binary scope says so and refuses
  to run.

A sanity check from a fresh clone: `./scripts/fresh-clone-smoke.sh`.

## Run the gates

```bash
bash scripts/quality-gate.sh            # numeric literals outside named constants
bash scripts/formal-gate.sh             # every specification in formal/ is model-checked
bash scripts/security-boundary-check.sh # an anonymous write is refused
```

The formal gate model-checks the **15 TLA+ specifications** with a tool that is tracked in
the repository and pinned by digest (`tools/tla2tools.jar`), not downloaded from a URL that
can be replaced in place.

A benchmark that a claim depends on is a JMH run, not a timer:

```bash
./gradlew :matrix-core:jmh -PjmhBenchmark=Name
```

## The configuration that actually exists

| the property | the default | what it does |
|---|---|---|
| `matrix.bir.max-literals` | `4096` | the maximum number of literals a BIR form may hold |
| `quarkus.http.port` | `9091` | chain A's HTTP port (`QUARKUS_HTTP_PORT`) |
| `quarkus.http.host` | `127.0.0.1` | chain A's bind address (`QUARKUS_HTTP_HOST`) |
| `matrix.mode` / `MATRIX_MODE` | `stub` | which brain chain B serves |
| `matrix.mind.dir` / `MATRIX_MIND_DIR` | `data/mind` | chain B's persistent mind and HDC knowledge base |
| `MATRIX_SECURITY_MODE`, `MATRIX_SECURITY_TOKEN` | — | the exposed-network mode; the token is required in it |

The tuning constants of the gateway launcher live in `scripts/matrix.env`, not in the
script.

## The project structure

```
agi/
├── matrix-core/           # chain A: the epistemic store, the cognitive store, the workbench,
│   └── src/…/io/matrix/   #   the reasoner, the CLI — and the only Quarkus application
├── matrix-api-gateway/    # chain B: the JDK HttpServer
├── matrix-brain-runtime/  # TrueMindCycle, the stages, the persistent HDC store
├── matrix-quality/        # the 14 goal-guard gates
├── matrix-sdk-java/       # io.matrix:matrix-sdk-java (a snapshot version)
├── matrix-web-ui/         # a Next.js project, outside Gradle, with its own CI job
├── matrix-tools-distill/  # the offline distillation CLI, deliberately isolated
├── docs-v2/               # this documentation
│   ├── architecture/       #   the canonical runtime topology and the capability registry
│   ├── engineering/        #   the wave reports and the test catalogue
│   ├── governance/         #   the project state, the decisions, the roadmap
│   └── research/           #   the hypothesis registers, the protocols, the reports
└── scripts/               # the gates, the launcher, the drill, the benchmarks
```

## What is not available yet

| the thing | the state |
|---|---|
| a **native executable** | **BLOCKED** — the GraalVM toolchain is absent here; the capability stays `BLOCKED` until a build succeeds |
| a **release version** | none — the tags that exist predate several waves of work |
| a **whole-database backup** | the SQLite backup API appears nowhere in the repository; `scripts/restore-drill.sh` works on a copy |
| a **hosted API and an SDK on Maven Central** | the Java SDK exists in the repository at a snapshot version; the Python and JavaScript SDKs are outside Gradle |
| the **GPU as a requirement** | it is an optional accelerator (OD-047): **12.06×** on the serving path over three runs, and about **4× slower** on a single-point call |

## Next

- [Contributing](../../CONTRIBUTING.md) — the two contribution paths, the gates, the evidence rules.
- [The runtime topology](../architecture/RUNTIME-TOPOLOGY.md) — what runs and what it does not reach.
- [The capability registry](../architecture/CAPABILITY-REGISTRY.yml) — every capability on four axes.
- [The research index](../research/RESEARCH-INDEX.md) — the hypothesis registers and the experiment reports.
- [The story](../story/overview.html) — what the system is, without the metaphor.
