# W43 — Verified baseline

**Directive:** W43 Epistemic Foundation · 2026-11-02
**Repository:** `AlexanderNarbaev/agi` · branch `develop`
**This file is the verified starting point of the wave.** The W42 audit is a
set of findings to CHECK, not a specification; every claim below carries the
method that settled it.

---

## 1. The repository state, verified

| Fact | Value | How it was checked |
|---|---|---|
| HEAD | `b303f22e44332547c018665a83a932a47d3beb22` | `git rev-parse HEAD` |
| The remote's `develop` | the same commit | `git ls-remote origin develop` |
| The branch | `develop`, in sync with `origin/develop` | `git status -sb` |
| Uncommitted work | the OWNER's, untouched: `.gitignore`, `.opencode/todo.md`, `.opencode/w37-plan.md`, `AGENTS.md`, `opencode.json`, the deleted `opencode.json.bak`, and the untracked `.codex/`, `.veai/`, `.opencode/goals/`, `.opencode/opencode-swarm.json`, `.opencode/tmp/` | `git status --porcelain` |
| The build | needs a **JDK 25 JDK**; the default `java` is a JRE 25 without a compiler, so a bare `./gradlew` fails with *"does not provide the required capabilities: [JAVA_COMPILER]"*. The build runs with `JAVA_HOME=/home/alexandr-narbaev/jdk25` | `java -version`, `./gradlew` output |
| The ecosystem | 14 Gradle modules + 3 pilots + a Next.js site; 2816 Java files; 135 407 lines; 1082 markdown documents (17 MB); 68 scripts | `find`, `ls` |
| The whole build | `./gradlew classes testClasses assemble` → **BUILD SUCCESSFUL**, 68 tasks | run |

---

## 2. The W42 audit, reconciled

Status vocabulary: `CONFIRMED` · `PARTIALLY_CONFIRMED` · `NOT_REPRODUCED` ·
`STALE` · `FALSE_POSITIVE` · `NOT_VERIFIABLE`.

### 2.1 The numbers, with their denominators

| Claim | Status | Denominator and method |
|---|---|---|
| "1489 targeted tests passed" | **CONFIRMED** | The targeted scope is FIVE packages of `matrix-core` — `io.matrix.knowledge.*`, `io.matrix.memory.*`, `io.matrix.brain.*`, `io.matrix.api.*`, `io.matrix.rag.*` — in 171 test classes: api 617, brain 41, knowledge 468, memory 76, rag 287. `:matrix-core:test --rerun-tasks` reports **1489 tests, 0 failures**. This is **~18 % of matrix-core**, not the core: matrix-core holds **8034 `@Test` annotations in 1149 files**, and its full suite is NOT run here (owner rule D-017) |
| "the ecosystem-wide run reported 1399 tests, one failure and four skipped" | **CONFIRMED, and it is a DIFFERENT SCOPE** | 1399 is the sum of the OTHER **13** modules (`matrix-audit` 40, `matrix-billing` 71, `matrix-observability` 22, `matrix-quality` 40, `matrix-sdk-java` 13, `matrix-spigot` 22 (2 skipped), `matrix-tools-distill` 4, `matrix-operator` 20, `matrix-brain-runtime` 914 (2 skipped), `matrix-api-gateway` 237, the three pilots 16). It does **not** include `matrix-core` at all. The two scopes are **disjoint**: 2888 tests were executed across them, and the repository's true total is **unknown and greater than 9888**, because matrix-core's full suite was not run |
| The one failing test | **CONFIRMED, and it is a REGRESSION** | `:matrix-quality:test > SpecImplementationMapTest.everySpecificationIsRegistered` — the registry holds **12** ids while `formal/` holds **14** `.tla` files. The independent reconciliation established the provenance: at `16c3ae5a^` the tree was **balanced 12/12**, and `16c3ae5a` — four commits before the baseline — added the two `.tla` files without adding their registry entries. So the drift was **introduced by a recent commit**, and CI has been RED since. **FIXED in this wave** (see §3.5) |
| "14 TLA specifications, 12 in the registry" | **CONFIRMED** | `ls formal/*.tla` = 14; `grep -c "^\s*- id:" formal/spec-implementation-map.yml` = 12. The two unregistered are `LearningStateMachine` and `PromotionStage`, and **both are LIVE**: `LearningStateMachine.java:40` ← `KnowledgeWorkbench.java:549`; `PromotionStage.java:20` + `PromotionPolicy.java:37` ← `ClaimValidator.java:245,282` ← `DocumentImporter.java:258` ← `KnowledgeWorkbench.java:606` |
| "the 137-item benchmark reports accuracy 0.842 and abstention 0.861" | **CONFIRMED** | `w42-benchmark-v3.json`, 137 items, splits train 40 / validation 39 / test 58, English 64 / Russian 73. **The denominators: 101 answerable of which 85 correct → 0.842; 36 unanswerable of which 31 refused → 0.861.** Reproduced live: `the v3 benchmark: the items 137 | the accuracy 0.842 | the abstention 0.861 | the BM25 overlap 0.499` |
| "the 34-item benchmark reports accuracy 1.000 and abstention 0.900" | **CONFIRMED** | `w42-benchmark-v1.json`, 34 items. **The denominators: 24 answerable of which 24 correct → 1.000; 10 unanswerable of which 9 refused → 0.900.** The one wrong item is `en-unanswer-2`, answered when it should have been refused |
| The two benchmark numbers compared | **NOT COMPARABLE — and the overlap is now measured** | v1 and v3 are different instruments: **0 byte-identical items and 0 shared ids**, BUT **24 of v1's questions reappear verbatim in v3** (under different ids) and **4 are semantically identical** (`en-amb-1`≡`en-amb-01`, `en-amb-2`≡`en-amb-02`, `en-adversarial-1`≡`en-adv-01`, `en-adversarial-2`≡`en-adv-02`). So v3 is a **superset in content**, not an independent set, and the two accuracies have different denominators (101 against 24 answerable). Treat them as two separate measurements over overlapping-but-different item sets, never as a pair |
| "typo benchmark reports 24/24 for seed 42" | **CONFIRMED** | `TypoBenchmarkRunner`, seed 42, 24 items |
| "the application starts in approximately 15 seconds in the reported configuration" | **NOT_REPRODUCED as stated** | No ~15 s figure exists anywhere in the repository, and "the reported configuration" is not a property of the configuration at all — it is a property of the WORKING DIRECTORY. The mechanism is `AutoTrainer`: `matrix.auto-train.enabled` defaults to **true**, `matrix.auto-train.path` defaults to the RELATIVE `models/training_data/combined_training.json`, resolved against the process CWD, and the training runs **synchronously** in a `StartupEvent` observer. Measured, by two independent sessions: **1.496 s** (clean tree, no corpus and no chain), **7.762 s** (auto-trainer off), **8.310 s** (no corpus present), **143.8-158.5 s** (default, corpus present, chain present). The service DOES become available in the last case — it is merely delayed. The 18-19 s in the W42 documents is the COLD-PAGE-CACHE chain load (7.1 GB), warm is ~8 s |
| "the knowledge core survives forced termination and restart" | **CONFIRMED** | `scripts/local-alpha-checklist.sh` → 12 of 12 assertions; `scripts/local-alpha-restart.sh` → every assertion held, with 13 concepts, 19 representations and 24 claims surviving a `SIGKILL` of the application's own JVM, and the consolidation after the restart reporting 0 candidates, 0 forgotten, idempotent |
| "six modules have no consumers" | **CONFIRMED** | `matrix-billing` (71 tests), `matrix-observability` (22), `matrix-quality` (40), `matrix-spigot` (22), `matrix-operator` (20), `matrix-tools-distill` (4) — no import of their classes from any production tree. `matrix-sdk-java` (13 tests) is a **seventh** in the same state: the pilots declare the Gradle dependency and import none of its classes |
| "nine algorithmic families are disconnected or without demonstrated production value" | **CONFIRMED as a census, but the NUMBER NINE belongs to another list** | The nine are the **shadow classes** — and the W42 audit itself already corrected that to **six shadows + three live** (CreditLedger, AuditChain, FederationRegistry, LicenseManager, GpuKernelEngine, ModelToMatrix, DistillationLedger, SleepConsolidator, ErrorDrivenLearner). The DISCONNECTED-FAMILY census of §13.1 is a different list and an independent recount reproduces it as **10-11 rows**: HdcBinding (3 mentions, 0 constructions), IndexedBooleanRag (0/0), CognitiveRAG, CognitiveAttention, MCTSPlanner (0 constructions), CognitiveGroupedQueryAttention, CognitiveLatent, MultiResolutionAttention, CognitiveFlash/Linear/SparseAttention (0 production mentions) — plus RAG, CRDT, the Bayesian family, the constraint solver, classical search, reinforcement learning, information theory and causal inference. And **case-based reasoning has no class in the repository at all**, which is a census of an absence |
| "security findings describe unauthenticated and externally reachable mutation routes" | **CONFIRMED, and worse than reported** | See §3 |

### 2.2 Findings that were partly wrong

| Claim | Status | What is actually true |
|---|---|---|
| "the quality gate exits 1" | **STALE** | It exits **0**: 263 literal candidates against the accepted baseline of 270. The gate's own defect is different and worse — `VIOLATIONS` is incremented at `scripts/quality-gate.sh:173,187,217` and **never read**, so the FROZEN-1 findings (`.github/` 785 diff lines, `AGENTS.md` 14) cannot fail the run |
| "the CIM integration matrix's `ModelToMatrix` is integrated" | **FALSE_POSITIVE** | `grep -rn "ModelToMatrix" matrix-core/src/main matrix-api-gateway/src/main matrix-brain-runtime/src/main` returns three hits, **all inside its own file**. It is `DISCONNECTED` |
| "the application binds to loopback" | **PARTIALLY_CONFIRMED** | The Quarkus application does, by default (`quarkus.http.host=${QUARKUS_HTTP_HOST:127.0.0.1}`), but `docker-compose.yml:72` overrode it with `0.0.0.0`, and **eleven raw listeners bound the wildcard unconditionally** |
| "the benchmark's 13 tabs and 26 buttons belong to the web UI" | **FALSE_POSITIVE** | They belong to `matrix-core/src/main/resources/META-INF/resources/workbench/index.html` — a static HTML workbench served by Quarkus, correctly wired to `/v1/workbench`. `matrix-web-ui/` is a different product: 2 routes, **0 tabs**, 6 buttons of which **4 have no working action**, a hardcoded base URL of `https://api.matrix.ai` (a domain that does not exist), no proxy, and a `LiveDemo` that **simulates MATRIX in the browser** |

---

## 3. Security: what was found, what was changed, what is still open

### 3.1 The state at the baseline

| Fact | Evidence |
|---|---|
| **278 of 279 HTTP routes performed no identity check** | The only check in the repository is `POST /v1/analyze` in the gateway (`MinimalHttpServer.java:375-398`). Zero occurrences of `@RolesAllowed`, `@PermitAll`, `@Secured`, `@Authenticated` or `SecurityIdentity` |
| The gateway's JWT/RBAC **never runs** | `matrix-api-gateway/build.gradle` applies no Quarkus plugin, so its nine JAX-RS resources are never mounted; the live server is `MinimalHttpServer`, whose 27 routes are plain `http.createContext(...)` |
| `JwtAuthFilter` is a **forgeable stub** | the token is `sub|email|exp|plan|role` with **no signature verification** (`JwtAuthFilter.java:50-56`) — `x|y|99999999999|ENTERPRISE|ADMIN` passes |
| `POST /v1/auth/login` issued a **developer token to anyone, without a password**, with the **tier inferred from a substring of the email** | `MinimalHttpServer.java:920-930`, `inferTierFromEmail` |
| `X-Tenant-Id` **decided the tenant** | `TenantFilter.java:32` — a header the caller chooses |
| **Eleven raw listeners** bound `0.0.0.0` unconditionally | `ConversationStreamServer`, `RealConversationServer`, `BrainTelemetry`, `WebSocketTelemetryServer`, `MinimalHttpServer`, `BrainHttpServer`, `BrainProduction`, `BrainServer`, `BrainIntegrated`, `P2PNetwork` (a DatagramSocket AND a ServerSocket), `PeerDiscovery` |
| `POST /api/v1/tools/invoke` is a **mounted** route that writes arbitrary files, fetches arbitrary URLs and runs a shell whose allow-list checks **only the first word** | `matrix-core/src/main/java/io/matrix/tools/ToolsResource.java:35` → `:97`; the file write `:162-171`, the read `:149-160`, the fetch `:116-142`, the shell `:173-196` |
| `POST /api/v1/ingest/url` is an **SSRF** | `IngestResource.java:109` |
| The container **defeated the loopback default** | `docker-compose.yml:72` set `QUARKUS_HTTP_HOST: 0.0.0.0`, and **no deployment set a security mode at all** |
| The helm chart publishes the service at **`api.matrix.ai`** | `deploy/helm/matrix/values.yaml:13-21` |
| The gateway container is **miswired twice** | `deploy/docker/Dockerfile.api-gateway` exposes and health-checks 8765; `deploy/docker-compose.yml:11-17` publishes 8080 and health-checks `/q/health`, which the gateway does not serve (it serves `/health/ready`) |

### 3.2 What this wave changed

| Change | Where | What it does |
|---|---|---|
| **One boundary, decided once** | `io.matrix.security.NetworkExposure` | Two modes: `LOCAL_ONLY` (the default — binds the loopback, no credential needed) and `NETWORK_EXPOSED` (deny-by-default, a credential required). The mode and the token come from `matrix.security.mode` / `matrix.security.token` or the environment equivalents. An unknown mode is an **error**, not a default |
| **A startup guard** | `NetworkExposure.guard(...)`, called by every listener | A component that would be exposed without a credential **refuses to start** and names itself |
| **The application itself is guarded** | `io.matrix.security.StartupGuard` (`@Observes StartupEvent`, `@Startup`) | The Quarkus application refuses to start when it has been declared exposed and no credential is configured. The `@Startup` annotation is not decoration: without it the bean is created lazily, the observer never fires, and the boundary check script found that the application started exposed — the guard was in the code and not in the path |
| **Deny-by-default for mutations** | `io.matrix.api.AuthorizationFilter` (`@Provider @Priority(AUTHORIZATION)`) | Reads are allowed; **writes are refused in exposed mode without the credential**. The rule is in one place because there are 192 matrix-core routes and a rule that must be remembered in 192 places will be forgotten in one of them |
| **The tenant is the server's** | `TenantFilter.tenantOf(...)` | The `X-Tenant-Id` header is **ignored**. The tenant is the server-controlled local identity, or the authenticated identity in exposed mode |
| **Every listener binds through the boundary** | eleven classes | A listener that forgets to consult the boundary would be reachable from another host; the audit question is now a single grep |
| **No passwordless developer token, no tier from an email** | `MinimalHttpServer.handleLogin`, `configuredTier()` | The tier is a configuration value (`matrix.billing.tier`), `FREE` when unset |
| **Every gateway route is behind the same rule** | `MinimalHttpServer.createContext(...)` | The 27 routes are registered through one guarded method rather than twenty-seven handlers |
| **The deployments fail closed** | `docker-compose.yml`, `docker/docker-compose.yml`, `deploy/docker-compose.yml`, `deploy/helm/matrix/` | The published ports bind the **loopback of the host**; the container declares `NETWORK_EXPOSED`; the credential is **required** — compose refuses to start without it (`${VAR:?message}`), and the application refuses to start without it too |

### 3.3 The boundary, as a gate that can go red

`scripts/security-boundary-check.sh` asserts four RUNTIME behaviours — not
constants:

1. `LOCAL_ONLY` binds the loopback, and an anonymous write works;
2. `NETWORK_EXPOSED` without a credential **refuses to start**;
3. `NETWORK_EXPOSED` with a credential: an anonymous write is **401**, an
   authenticated write is **200**, a wrong credential is **401**, and an
   anonymous **tool invocation** is **401**;
4. a read is allowed in every mode.

**Measured 2026-11-02: `the security boundary: 0 — every assertion held`.**

### 3.4 STILL OPEN — release blockers

These are recorded rather than fixed, because fixing them is a decision
about the product, not a mechanical change.

| # | Blocker | Why it is not fixed here |
|---|---|---|
| B1 | **`POST /api/v1/tools/invoke`** writes arbitrary files, fetches arbitrary URLs and runs a shell whose allow-list checks only the first word. It is behind the new boundary in exposed mode, but a LOCAL caller can still reach it, and the allow-list is the thing that is wrong | Disabling a route removes a capability; the mission's §16 requires the owner's decision |
| B2 | **`JwtAuthFilter` verifies no signature.** The one protected gateway route is protected by a forgeable token | Replacing it means choosing an identity provider — a decision, and a dependency |
| B3 | **The gateway's JAX-RS resources are dead code** (no Quarkus plugin) and its 27 live routes are plain contexts, so the JWT/RBAC in the module never runs | The route surface was consolidated behind the boundary instead; the dead resources should be removed or mounted — a decision |
| B4 | **`/api/v1/ingest/url` is an SSRF** | Same shape as B1 |
| B5 | The gateway container is **miswired** (8080 published, `/q/health` health-checked, against a service on 8765 that serves `/health/ready`) | A deployment defect with two possible fixes; the mission's §13.1 names the local path, not this container |
| B6 | **The helm ingress** publishes at `api.matrix.ai`; the chart now requires a credential, but the ingress itself is a decision | The owner's |
| B7 | The `infra/k8s/**` manifests set **no** `QUARKUS_HTTP_HOST`, so the application binds the loopback inside the pod and the Service is dead | A deployment bug, not a security one |
| B8 | **No role model.** One credential, one rule. There is no per-route policy beyond "read" and "write" | §1.3 requires a model; §1.5 requires a policy per route. The policy is implemented as a class, not as a table |
| B9 | The **health endpoints** are public. Their disclosure was not reviewed as the mission asks | The review is a human one |

---

## 3.5 The gates, made capable of failing

The W42 audit found four gate defects. All four are fixed, and each fix was
**proved by injecting the violation it is supposed to catch**.

| Gate | What was wrong | What it does now | The proof |
|---|---|---|---|
| `scripts/quality-gate.sh` | `VIOLATIONS` was incremented at three places and **never read**, so the CFG-1 and FROZEN-1 rules could not fail the run. The literal baseline (270) sat ABOVE the measured count (264), so six new literals were tolerated. And the frozen zones had drifted (785 diff lines in `.github/`, 14 in `AGENTS.md`) with no explicit exception | The counter is read and blocks. The baseline is the **measured** value. The frozen drift is recorded per zone in `docs-v2/quality/frozen-zone-baseline.txt` with an owner and an expiry, and a zone whose drift grows past its accepted number — or whose exception has expired — is a violation | A file with `int x = 12345;` in `matrix-core` → **exit 1**; removed → **exit 0**. And the injection found a real hole: the structural-literal filter ran on the line NUMBER produced by `grep -n`, and the number itself matched the structural set, so a literal on line 2, 8, 16, 100, 400 or 401 was **invisible**. Eight hidden literals were found at once |
| The specification registry | `formal/` held **14** `.tla` files and the registry held **12** ids — a red test in the repository, and CI had been red for four commits | Both specifications are registered with their sources, production callers, tests, properties and conformance gaps, and `./gradlew :matrix-quality:regenerateFormalPaths` updated the CI path filter | `:matrix-quality:test` → **9 of 9 validator tests PASS**, including "every specification in formal/ appears in the registry" |
| The determinism audit | `Exp144DecisionPathAuditTest` printed its findings and asserted only that it had scanned more than zero files. It scanned **neither runtime module**, and its package list never covered the knowledge core | Two tests now: the report test and `DecisionPathGateTest`, which compares the blocking findings of **matrix-core, matrix-brain-runtime and matrix-api-gateway** against an explicit baseline of 48 entries, each with its reason, an owner and an expiry. The baseline fails when a finding APPEARS **and** when an entry STOPS holding, so it cannot become a blanket | A wall-clock read injected into `io/matrix/knowledge/` → **red**; into `io/matrix/brain/runtime/` → **red**; an unseeded `new Random()` in `io/matrix/reasoning/` → **red** — in both the plain and the fully qualified form, which the pattern had missed. Clean → **green** |
| CI | `quality-gate.sh` and `formal-gate.sh` were run by **ZERO** workflows | The `quality-gates` job runs the clean-code gate, the formal gate (every specification model-checked) and the security boundary check, with a full-depth checkout so that the frozen-zone baseline ref exists | The formal gate measured **14 passed, 0 failed** |

The mutation-survivor discipline is unchanged and stated: a mutation that
survives is recorded with a reason. The two surviving mutations of the
promotion model and the two of the learning-state model are recorded in the
specifications themselves, with the WAL entries that rejected them.

---

## 4. The reconciliation of the two benchmark instruments

| | v1 | v3 |
|---|---|---|
| Items | 34 | 137 |
| Categories | 12 | 13 |
| Languages | English + Russian | English 64 / Russian 73 |
| Splits | none | train 40 / validation 39 / test 58 |
| Accuracy | 1.000 | 0.842 |
| Abstention | 0.900 | 0.861 |
| Reachability check | none | every item, test-enforced |
| Purpose | the frozen baseline instrument (decision D-001) | the harder instrument |

**The two accuracies are not comparable**, and the W42 documents must not
present them as a pair. The v1 instrument is frozen and must not be moved;
v3 is the instrument that grows.

---

## 5. What this wave has NOT done

Stated plainly, because the mission's §16 forbids a capability claim that
the evidence does not carry.

- **No epistemic model yet.** The architecture decision (§3 of the
  directive) is not written; no new source/evidence/case/reasoning-result
  entity exists.
- **No counterexample-learning slice.** The scenario of §5 is not
  implemented.
- **No benchmark for the slice.** §10's metrics are not measured.
- **No corpus foundation.** §7 is untouched.
- **The gates can fail now** (§3.5), and the registry drift that was a red
  test in the repository is fixed. What is NOT done is the rest of §2:
  the mutation harness does not yet record its survivors with reasons, and
  the formal gate's CI path filter covers the registry's paths but the
  formal gate itself still runs only on the `formal/**` trigger.
- **The OpenAPI mismatch stands.** The gateway's `openapi.yaml` declares a
  global `bearerAuth` that the implementation does not enforce, and
  matrix-core publishes no OpenAPI at all. The boundary closes the routes,
  not the document.
