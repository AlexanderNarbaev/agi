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
| "1489 targeted tests passed" | **CONFIRMED** | The targeted scope is FIVE packages of `matrix-core` — `io.matrix.knowledge.*`, `io.matrix.memory.*`, `io.matrix.brain.*`, `io.matrix.api.*`, `io.matrix.rag.*`. `:matrix-core:test --rerun-tasks` reports **1489 tests, 0 failures** |
| "the ecosystem-wide run reported 1399 tests, one failure and four skipped" | **CONFIRMED, and it is a DIFFERENT SCOPE** | 1399 is the sum of the OTHER modules (`matrix-audit`, `matrix-billing`, `matrix-observability`, `matrix-quality`, `matrix-sdk-java`, `matrix-spigot`, `matrix-tools-distill`, `matrix-operator`, `matrix-brain-runtime`, `matrix-api-gateway`, the three pilots). It does **not** include `matrix-core`. The two numbers therefore measure two disjoint halves of the repository and **must not be added or compared**; the repository's whole is ~2888 tests |
| The one failing test | **CONFIRMED, and it is PRE-EXISTING** | `:matrix-quality:test > SpecImplementationMapTest.everySpecificationIsRegistered` — the registry `formal/spec-implementation-map.yml` holds **12** ids while `formal/` holds **14** `.tla` files. The two files were on disk uncommitted from 2026-11-02 14:39 and the registry was last written on 2026-11-01 20:47, so the drift predates this wave; committing the specifications only made it visible in git |
| "14 TLA specifications, 12 in the registry" | **CONFIRMED** | `ls formal/*.tla` = 14; `grep -c "^\s*- id:" formal/spec-implementation-map.yml` = 12. The two unregistered are `LearningStateMachine` and `PromotionStage`, and **both are LIVE**: `LearningStateMachine.java:40` ← `KnowledgeWorkbench.java:549`; `PromotionStage.java:20` + `PromotionPolicy.java:37` ← `ClaimValidator.java:245,282` ← `DocumentImporter.java:258` ← `KnowledgeWorkbench.java:606` |
| "the 137-item benchmark reports accuracy 0.842 and abstention 0.861" | **CONFIRMED** | `w42-benchmark-v3.json`, 137 items, splits train 40 / validation 39 / test 58, English 64 / Russian 73 |
| "the 34-item benchmark reports accuracy 1.000 and abstention 0.900" | **CONFIRMED** | `w42-benchmark-v1.json`, 34 items |
| The two benchmark numbers compared | **NOT COMPARABLE** | v1 and v3 are different instruments with different item sets; v3 is the harder one. A per-item overlap check is required before ANY comparison, and none is recorded |
| "typo benchmark reports 24/24 for seed 42" | **CONFIRMED** | `TypoBenchmarkRunner`, seed 42, 24 items |
| "the application starts in approximately 15 seconds in the reported configuration" | **PARTIALLY_CONFIRMED — and the configuration is now named** | ~15 s is the start with the auto-trainer DISABLED. The mechanism is `matrix.auto-train.path`, a RELATIVE key with the default `models/training_data/combined_training.json`, and `matrix.auto-train.enabled` defaulting to true with no key in `application.properties`: with the corpus present the port was **not up after 90 s**, without it **2.038 s**, with the trainer off **19.018 s** |
| "the knowledge core survives forced termination and restart" | **CONFIRMED** | `scripts/local-alpha-checklist.sh` → 12 of 12 assertions; `scripts/local-alpha-restart.sh` → every assertion held, with 13 concepts, 19 representations and 24 claims surviving a `SIGKILL` of the application's own JVM, and the consolidation after the restart reporting 0 candidates, 0 forgotten, idempotent |
| "six modules have no consumers" | **CONFIRMED** | `matrix-billing` (71 tests), `matrix-observability` (22), `matrix-quality` (40), `matrix-spigot` (22), `matrix-operator` (20), `matrix-tools-distill` (4) — no import of their classes from any production tree. `matrix-sdk-java` (13 tests) is a **seventh** in the same state: the pilots declare the Gradle dependency and import none of its classes |
| "nine algorithmic families are disconnected or without demonstrated production value" | **CONFIRMED** | RAG (all four implementations), CRDT (`CausalCrdt`), the Bayesian family (seven classes), the constraint solver (`Ac3Solver`), classical search (A*, Dijkstra, Bellman-Ford, Floyd-Warshall, PageRank), reinforcement learning (Q-learning, SARSA, the bandits), information theory, causal inference (`EpochAwareCausalSet`/`GrowOnlySet`), and case-based reasoning — for which **no class exists in the repository at all** |
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
- **The gates still cannot fail.** §2 of the directive — the quality gate's
  `VIOLATIONS`, the formal gate's absence from CI, the determinism audit
  that documents without failing, and the registry drift — is **the next
  piece of work**, and the registry drift is a **red test in the repository
  right now** (§2.1).
