# RECON-W43 — Status

**Wave:** W43 — Epistemic Foundation: Evidence, Meaning & Learning from
Counterexamples
**Branch:** `develop` · **From:** `b303f22e` · **To:** `cd2c6347`
**Date:** 2026-11-02

---

## 0. The seven states, used literally

Every capability below carries one of these, and the word means what it
says. A class that exists is not a capability; a test that passes is not a
demonstration; a benchmark number without a denominator is not a
measurement.

| State | The test it must pass |
|---|---|
| **IMPLEMENTED** | the code exists and is compiled into the artifact |
| **TESTED** | a test exercises it and would fail if it broke |
| **REACHABLE** | a production caller reaches it — named, with file:line |
| **MEASURED** | a benchmark or a probe produces a number, with its denominator |
| **DEMONSTRATED** | it was seen working on a live path, and the evidence is recorded |
| **EXPERIMENTAL** | it runs, but no production path depends on it and no baseline exists |
| **INACTIVE** | it exists and is not reachable from any production path |
| **PLANNED** | it does not exist yet, and this document says so |

---

## 1. What the wave did

### 1.1 The security boundary — IMPLEMENTED, TESTED, REACHABLE, DEMONSTRATED

One decision, in one place: `io.matrix.security.NetworkExposure`.

| Capability | State | Evidence |
|---|---|---|
| `LOCAL_ONLY` binds the loopback and needs no credential | IMPLEMENTED, TESTED, DEMONSTRATED | `NetworkExposure.bindAddress()`; `NetworkExposureTest`; the boundary check reports the bound address `[::ffff:127.0.0.1]:9097` |
| `NETWORK_EXPOSED` requires a credential and refuses to start without one | IMPLEMENTED, TESTED, DEMONSTRATED | `NetworkExposure.guard()`; `StartupGuard` (`@Observes StartupEvent`, `@Startup`); the boundary check sees the process refuse to start and name itself |
| State-changing requests are refused without the credential | IMPLEMENTED, TESTED, DEMONSTRATED | `AuthorizationFilter`; `AuthorizationFilterTest`; the live probe returns **401** |
| Reads stay available | IMPLEMENTED, TESTED, DEMONSTRATED | the live probe returns **200** |
| The tenant is the server's, never the caller's | IMPLEMENTED, TESTED | `TenantFilter.tenantOf(...)`; the `X-Tenant-Id` header is ignored; `AuthorizationFilterTest` asserts the header does not decide |
| Every listener binds through the boundary | IMPLEMENTED, TESTED | eleven classes, including the multicast socket in the P2P process that a server-level audit missed |
| The gateway's 27 routes sit behind the same rule | IMPLEMENTED | `MinimalHttpServer.createContext(...)` |
| No passwordless developer token; no tier from an email | IMPLEMENTED | `MinimalHttpServer.handleLogin`, `configuredTier()` |
| The deployments fail closed | IMPLEMENTED | `docker-compose.yml`, `docker/docker-compose.yml`, `deploy/docker-compose.yml`, `deploy/helm/matrix/` — compose refuses to start without the credential |
| CI runs the boundary check | IMPLEMENTED | the `quality-gates` job |

**The gate:** `scripts/security-boundary-check.sh` — **"the security boundary:
0 — every assertion held"** (2026-11-02).

### 1.2 The gates that can fail — IMPLEMENTED, TESTED, DEMONSTRATED

| Gate | What it does now | The proof |
|---|---|---|
| `scripts/quality-gate.sh` | the violation counter is read and blocks; the literal baseline is the measured value; the frozen drift is an explicit, owned, time-bounded exception | a file with `int x = 12345;` → **exit 1**; removed → **exit 0** |
| The specification registry | all **14** specifications registered, with sources, production callers, tests, properties and gaps | `:matrix-quality:test` → **9 of 9 validator tests PASS** |
| The determinism audit | scans matrix-core, matrix-brain-runtime and matrix-api-gateway; compares against a baseline of **20 groups / 48 findings**, each with a reason | a wall-clock read injected into `io/matrix/knowledge/` → **red**; into `io/matrix/brain/runtime/` → **red**; an unseeded `Random` in `io/matrix/reasoning/` → **red**, in both the plain and the fully qualified form |
| `scripts/formal-gate.sh` | every specification is model-checked | **14 passed, 0 failed** |
| CI | a `quality-gates` job runs all of them, plus the security boundary | `.github/workflows/ci.yml` |

### 1.3 The epistemic model — IMPLEMENTED, TESTED, DEMONSTRATED

`docs-v2/engineering/ADR-W43-01-epistemic-model.md`.

| Capability | State | Evidence |
|---|---|---|
| The evidence relations distinguish supports / contradicts / qualifies / exemplifies / fails to support / is insufficient | IMPLEMENTED, TESTED | `EvidenceRelation`; the counterexample cycle test |
| The claim statuses distinguish observed / hypothesis / insufficient evidence / refined | IMPLEMENTED, TESTED | `ClaimStatus`; `ClaimValidator.stageOf` maps them onto the promotion layers |
| A counterexample is stored with the condition it was made under | IMPLEMENTED, TESTED, DEMONSTRATED | `KnowledgeWorkbench.recordExample(...)`; the journey script |
| The system reads a counterexample against the claim's scope and explains the reading | IMPLEMENTED, TESTED, DEMONSTRATED | `ClaimAssessor`; `CounterexampleReading`; the journey shows `REVEALS_MISSING_CONDITION` and the reason quoting the condition |
| The system does NOT choose one of the six explanations | IMPLEMENTED, TESTED | `CounterexampleReading.unresolvedExplanations()`; a counterexample with no condition leaves the reading `INCONCLUSIVE` and lists what it cannot exclude |
| A narrowing is proposed only when the evidence supports one, and quotes the condition verbatim | IMPLEMENTED, TESTED, DEMONSTRATED | `ClaimAssessor`; the journey shows the proposal quoting "without light" |
| Accepting a refinement keeps the original claim and the revision | IMPLEMENTED, TESTED, DEMONSTRATED | `KnowledgeWorkbench.acceptRefinement(...)`; the original becomes `SUPERSEDED` with its proposition intact |
| Rejecting a refinement keeps the original AND the fact of the rejection | IMPLEMENTED, TESTED | `RevisionKind.REJECTED_REFINEMENT` |
| The whole cycle survives a forced restart | IMPLEMENTED, TESTED, DEMONSTRATED | `scripts/w43-counterexample-journey.sh` — **"the counterexample journey: 0 — every assertion held"** |
| The workbench exposes the cycle over HTTP | IMPLEMENTED, REACHABLE | `POST/GET /v1/workbench/workspaces/{id}/claims/{claimId}/{examples,assessment,refine/accept,refine/reject}` |

### 1.4 The temporary-directory leak — FIXED, and it took the tools down with it

The ONNX runtime extracts a native library of about **583 MB** into a fresh
directory under `java.io.tmpdir` for every session load, and never removes it.
A wave's worth of starts left **52 directories, 19.5 GB**, and filled the
per-user quota of the `/tmp` tmpfs. The consequence was not a slow
degradation: every spawn-based tool — the build, the gates, the shell itself
— began to fail with `EDQUOT`.

Fixed: the launcher sets `java.io.tmpdir` (the JVM reads it at launch, so it
cannot be redirected from inside the application), and `io.matrix.Scratch`
removes what the earlier runs left behind. **Measured: two consecutive starts
leave 0 directories in `/tmp` and 583 MB in `data/tmp` — the same 583 MB
after the second start as after the first.** Recorded as **R48**.

### 1.5 What was NOT done

| Item | State | Why |
|---|---|---|
| The `Case` object as a first-class stored entity | **PLANNED** | it needs the schema-16 migration, which is the owner's decision (ADR §9.1). The slice runs on the existing schema: an observation is a `Source` of kind `OBSERVATION`, and the evidence link says how it bears on the claim |
| `Claim.modality`, `polarity`, `temporalScope`, `preconditions`, `exceptions` | **PLANNED** | same migration |
| `Derivation.goal`, `evidenceIds`, `contradictedClaimIds`, `assumptions` | **PLANNED** | same migration |
| The workbench UI panel for the cycle | **PLANNED** | the API is live and the HTML workbench is a static file; the smallest useful panel is not done in this wave |
| A benchmark for the slice (mission §10) | **IMPLEMENTED, MEASURED** — but it measures the slice's DECISIONS, not understanding | `io.matrix.knowledge.benchmark.SliceBenchmark`; `w43-slice-benchmark-v1.json`; **24 cases, standing 24/24, reading 16/16, proposal 24/24, quotation 24/24; EN 12/12, RU 12/12; train 8, validation 6, test 10** |
| The corpus foundation (mission §7) | **PLANNED** | see the backlog §3 |
| Fine-tuning, model training, weight updates | **NOT STARTED, AND NOT PLANNED IN THIS WAVE** | the prerequisites of mission §9 do not exist |

---

## 2. The measurements, with their denominators

| Measurement | Value | Denominator |
|---|---|---|
| The targeted suite | **1565 tests, 0 failures** | 8 packages of `matrix-core`: `knowledge`, `memory`, `brain`, `api`, `rag`, `security`, `auditor` |
| The registry validator | **9 of 9 pass** | `matrix-quality`'s `SpecImplementationMapTest` |
| The formal gate | **14 passed, 0 failed** | the 14 `.tla` specifications in `formal/` |
| The security boundary | **0 failures** | 10 runtime assertions across 3 process configurations |
| The counterexample journey | **0 failures** | 18 runtime assertions, including a `kill -9` and a restart |
| The slice benchmark | **24 cases: standing 24/24, reading 16/16, proposal 24/24, quotation 24/24** | 24 cases; the reading is expected in 16 of them; EN 12/12, RU 12/12; train 8, validation 6, **test 10** |
| The quality gate | **exit 0** at 271 literal candidates against an accepted baseline of 271 | the accepted baseline is the MEASURED value, so the next literal fails |
| The determinism gate | **0 mismatches** | 20 accepted groups over 48 findings, in three roots |
| The Local Alpha journey | **12 of 12 assertions** | the previous wave's checklist, re-verified after the security change |

**What the slice benchmark does NOT measure — stated plainly.** The slice's
decisions are **language-independent by construction**: the reading depends on
the evidence relation and on whether a condition was recorded, not on the
words. The Russian subset therefore measures that the text is carried
through unchanged and the structure is read — **not** that the system
understands Russian. And a perfect score here means **the rules are applied
consistently**, not that the system reasons well: the cases exercise the
rules the slice implements. The benchmark of the mission's §10 — with
ambiguity, false premises, temporal changes, typo variants and clarification
— is still to be built, and it is the backlog's first item.

**Not measured in this wave:** the full repository test suite (owner rule
D-017 — the OOM risk); the matrix-core full suite (8034 `@Test`
annotations — the targeted scope is ~18 % of the module).

---

## 3. The W42 audit, reconciled

The full reconciliation is in `docs-v2/engineering/W43-BASELINE.md`. The
corrections that change a previous document:

| The W42 documents said | The truth |
|---|---|
| the registry drift was pre-existing | it was a **regression**: `16c3ae5a` added two `.tla` files without their entries, and CI was red since |
| the application starts in ~15 s | **NOT_REPRODUCED**: the span is 1.5 s to 158 s and it depends on the **working directory**, not on a configuration |
| 1489 is "the core" | it is **five packages** — ~18 % of matrix-core |
| 1399 is "the ecosystem" | it is the **13 other modules**; matrix-core is excluded from it; the two scopes are disjoint |
| nine algorithmic families are disconnected | **nine is the shadow-class count** (six shadows + three live); the disconnected-family census is 10-11 rows |
| the two benchmark accuracies can be read together | they cannot: different denominators, and v3 **reuses 24 of v1's questions** |

---

## 4. The open decisions, for the owner

Twenty-one decisions. They are listed in full in §4 of `W43-BASELINE.md`;
the ones that block the next wave are these:

| # | Decision | What it blocks |
|---|---|---|
| 1 | The `Case` table and the schema-16 migration | the first-class counterexample, the claim's modality and polarity, the reasoning result's fields |
| 2 | Whether `Evidence.quality` is removed or demoted to an explicitly uncalibrated hint | every consumer of that field |
| 3 | Who decides the counterexample reading: the system, the user, or the system proposing and the user confirming | the semantics of the whole slice |
| 4 | The OpenAPI mismatch: the gateway declares a global `bearerAuth` it does not enforce, and matrix-core publishes no OpenAPI | requirement §1.8 |
| 5 | The `POST /api/v1/tools/invoke` route: arbitrary file write, arbitrary URL fetch, and a shell whose allow-list checks only the first word | a release blocker |
| 6 | The unsigned JWT in the gateway | a release blocker |
| 7 | The `X-Tenant-Id` path parameter in `TenantQaResource` — still client-chosen | a release blocker |
| 8 | The gateway container's port and health-check mismatch | a deployment defect |
| 9 | The helm ingress at `api.matrix.ai` | a deployment decision |
| 10 | Whether the W42 roadmap's missing W43+ sections are authored | the whole next wave |
| 11 | Whether the auto-trainer may train synchronously at startup | the 143-second startup |
| 12 | A local full-suite run | the unmeasured 8034 tests of matrix-core |
| 13 | The fate of the 6 unconsumed modules, `matrix-sdk-java`, the 9 disconnected families, `matrix-web-ui` and the stale risk statuses | the capability registry |

---

## 5. The commits

| Commit | What it did |
|---|---|
| `cc0493bc` | the security boundary: one decision, eleven listeners, deny-by-default, fail-closed deployments |
| `c6db583e` | the gates that can fail: the violation counter, the registry, the determinism audit, the CI job |
| `cd2c6347` | the epistemic ADR and the counterexample slice, with the live journey |
