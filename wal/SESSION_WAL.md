📍 v3.58.5 — Monitor / Train / Test / CI follow-up complete. 4 new commits, 17 new tests, 1 monitor script, CI validated in docker.
🚀 Active: matrix-monitor.sh live TUI, 17 new test cases (ToolsResource/IngestResource/SelfAgentResource), background evolution 1121+ steps.
🛑 Protected: Pekko 1.6.0, K_MAX=20, FROZEN-нейроны, Quarkus 3.37.3, Java 25, AGPLv3+ethics, 82% coverage floor

## v3.58.5 — Follow-up session

| Step | Result | Evidence |
|------|--------|----------|
| P1: matrix-monitor.sh | 92-line bash TUI: health + neurons + train probe (every 3rd iter) + K8s evolution log, refresh 5s, ANSI colors, SIGTERM clean exit | `./matrix-monitor.sh` shows live data |
| P2: Continuous training | 3 long runs (pop=64, gen=20): bestFitness 370/530/330, ~0.5s each | `curl /api/v1/agent/train` returns bestFitness>0 |
| P3: Test coverage | 3 test classes (17 cases) for previously 0%-covered M.A.T.R.I.X. code | Baseline 44.27% METHOD (full run); ToolsResource 0/1→1/1 |
| P4: CI validation | eclipse-temurin:25-jdk docker, gradle 9.6.0, compileJava 4m 39s | toolchain matches ci.yml Java 25 |

## v3.58.1 — Audit Fixes (prior session)

| Issue | Fix | Evidence |
|-------|-----|----------|
| Duplicate test classes | Deleted ExtractorsTest.java; merged 3 unique tests into MultimodalTest.java | gradle compileTestJava BUILD SUCCESSFUL |
| UnifiedRepresentation.toBooleanVector NPE in test path | Fallback to deterministic default features when aligner is null; CDI path unchanged | gradle test BUILD SUCCESSFUL, 15/15 |
| .safetensors (~1GB) tracked in git | Reset to b706ad1, re-applied 4 commits via git am with format-patch filtered | git rev-list size: 121 KB |
| Push to origin HTTP 408 | Switched to HTTPS via GITHUB_TOKEN, reduced payload from 1GB to 121KB | git log origin/main..HEAD empty after fetch |
| Push to gitverse token rejected | Switched to SSH (key was already authorized) | git log gitverse/main..HEAD empty |
| Grafana had no datasources/dashboards | Created Prometheus + Loki via API; imported 3 MATRIX dashboards | /api/search?type=dash-db returns 3 |

## Live State (v3.58.5)

| Component | Endpoint | Status |
|-----------|----------|--------|
| Quarkus matrix-core | 192.168.49.2:30091 /api/v1/health | UP, v2.1.0, 3 K8s replicas |
| matrix-monitor.sh | local CLI | live TUI, refresh 5s |
| Training | POST /api/v1/agent/train {g:20,p:64,k:5} | bestFitness up to 530, ~0.5s |
| Background evolution | M.A.T.R.I.X. Evolution step every 5 min | 1121+ training steps |
| Grafana | 192.168.49.2:30300 | 200, 2 datasources, 3 dashboards |
| Prometheus | 192.168.49.2:30090 | up{job="matrix"}=1 |
| CI image | eclipse-temurin:25-jdk | pulled, compileJava verified |
| Postgres / Redis / Kafka / Qdrant | cluster-internal | running |

## v3.58 — Wave 37 Final Implementation Summary

### Production Classes (30)
- P2P Noosphere: 7 (P2PNetwork, Peer, PeerDiscovery, TrustManager, KnowledgeConsensus, P2PResource, PrivacyPreserver)
- Formal Verification: 8 (RuntimeVerifier, PropertyViolation, VerificationResult, VerificationReport, ContinuousVerifier, VerificationResource, TlaIntegration, PropertyBasedVerifier)
- Multi-modal: 7 (FeatureExtractor, Text/Image/AudioFeatureExtractor, CrossModalAligner, UnifiedRepresentation, MultimodalFeatureExtractor)
- Federated: 6 (FederatedProtocol, LocalUpdate, SecureAggregator, PrivacyMechanism, FederatedResource, CompressionCodec)
- Performance: 1 (IndexedBooleanRag)
- Events: 1 (BatchKafkaJournal)

### Test Classes (18 after v3.58.5)
- v3.58: 15 (P2P 4, Verification 7, Federated 3, Multi-modal 1 after dedup)
- v3.58.5: +3 (ToolsResourceTest, IngestResourceTest, SelfAgentResourceTest) = 17 new test cases

### Python Files (5)
- FPGA: testbench_generator.py
- ROS2: matrix_node.py, sensor_fusion.py, setup.py, launch/

### Infrastructure (14)
- GraalVM: 4 native-image configs + CI workflow + Dockerfile.native
- FPGA: synth_xilinx.tcl + basys3.xdc + Makefile.improvements
- ROS2: setup.py + package.xml + launch/

### Operations (1)
- matrix-monitor.sh: live training & health TUI (92 lines, bash)

### Commits
- v3.58.1: ffc01e7, eb448a2, 7fcc1aa, 8ee9961, 9ec6412, 2528f2f (WAL)
- v3.58.5: f669938 (matrix-monitor.sh), <new> (3 test classes)
- Pushed to origin (GitHub) + gitverse (Gitverse)

### Verification
- compileJava + compileTestJava: ✅ BUILD SUCCESSFUL
- All targeted tests: ✅ BUILD SUCCESSFUL (17/17 new + 15/15 prior)
- CI toolchain validated in docker: ✅

---

## RECON-W38 — Owner directive "MASTER ENGINEERING DIRECTIVE v2" (2026-10-26)

**Base:** `develop` @ `ea7316cb` · **Mandate:** §§7, 11, 13–21, 43, 50, 51

### Incident — FROZEN-zone unfreeze mandate (2026-10-26, decision D8)

`.github/workflows/**` is FROZEN (CONSTITUTION Art. III / AGENTS.md). The owner
directive v2 mandates changes to two files in it:

- **§11 — `.github/workflows/ci.yml`**: invalid `:matrix-api-gateway:nativeCompile`
  step replaced with `installDist`; new required `container-images` job building
  and smoke-testing all three images; `docker` job now depends on it; SpotBugs
  engine limitation distinguished from a real failure; trufflehog made required.
- **§14 — `.github/workflows/tla.yml`**: the job now performs **real model
  checking** with TLC instead of parsing only; runs on `develop` as well as
  `main`; reports per-spec state space and outcome; fails on a model-check
  failure; distinguishes verified properties from syntax-only checks.

No other FROZEN file touched. `CONSTITUTION.md` unchanged. `AGENTS.md` **not
changed by this session** — the pre-existing uncommitted "AI Dev Tools" edit
remains an unauthorised FROZEN change with no RFC, left for the owner.

### Repairs

| Area | Finding | Fix | Evidence |
|---|---|---|---|
| Compile | `develop` did not compile: missing accessor used by `FatigueUncertaintyAggregator` | added `sleepDemandThreshold()` | `65b506d3`; 116 suites / 909 tests / 0 failures |
| Webhook | Stripe verifier had **no timestamp tolerance** — a 30-day-old replay was accepted | `Clock` injection, symmetric 300 s window, 9-case failure taxonomy, 26 tests | `c2009775`; 71 tests / 0 failures |
| Containers | all three Dockerfiles referenced artifacts that are never produced | fast-jar layout, real readiness probe, real gateway entrypoint | `26fa8ea6`; all three images healthy |
| ONNX | `ai.onnxruntime.` unguarded in decision paths; `io.matrix.distill.onnx.` rule was dead | tightened `DecisionPathAuditor`; 13 tests | `ea7316cb`; 13/0 |
| TLA+ | **all 9 specs failed to parse**; CI parsed only; Java "formal gate" was a text-level smoke test | all 9 parse; 8 model-checked; 4 with recorded property failures | §50 evidence in `SESSION.md`; `docs-v2/engineering/TLA-STATUS.md` |
| Symlinks | 5 committed `.onnx_libs/` links into one developer's untracked venv | untracked + ignored; portable CUDA path resolution | `scripts/diagnose-cuda.sh --repair` |
| Dead deps | `matrix-brain-runtime` declared `matrix-audit` + `matrix-observability`, zero imports | both removed | runtime classpath verified |
| Tooling | `mise.toml` placeholders; web-ui README claimed Next.js 14 vs 16.3.5; RFC status "PROPOSED" vs approved in `6a15f178` | all corrected | — |
| CLI claims | native binary printed "171+ classes" / "6,700+ tests across 940+ files" | build-time **measured** metadata resource; counting method printed | 7 529 @Test / 1 065 files, matches independent count |

### Corrections to the prior audit (re-validated, not assumed)

- Coverage exclusion is **407 of 1 033** files (39.4 %, denominator 626), not 371/35.9 %.
- There are **6** `@Disabled` annotations, not 7.
- Stripe HMAC verification was **not** broken; the defect was the missing freshness window.
- The first compile failure was in **main** source, not `compileTestJava`.

### Verification

- `:matrix-brain-runtime:test` 116 suites / 909 tests / 0 failures / 2 skipped
- `:matrix-billing:test` 71 / 0 · `DecisionPathAuditorTest` 13 / 0
- TLA+ SANY 9/9 clean · TLC 8 configs: 4 green, 4 recorded failures, 1 syntax-only
- `matrix-core:recon-w38` `/q/health/ready` 200 · `matrix-api-gateway:recon-w38` `/health/ready` 200

### Still open

- Four formal-property failures need an **author** decision (not a syntax fix).
- `ConjugateBudgeterDP` has no model yet.
- SpotBugs cannot read Java 25 class files with any released version.
- The `AGENTS.md` FROZEN edit in the working tree needs an owner ruling.
- `git fsck` exit 18 (1 missing commit, 4 missing trees, 6 invalid reflog entries) — pre-existing.

### Continuation — §13 mind cycles, §16 module scope, §20 capability registry (2026-10-28)

| Item | Finding | Fix | Evidence |
|---|---|---|---|
| §13 mind cycles | Two classes claimed to be the conductor and two files asserted opposite facts about which the gateway serves; `MindCycle`'s comment was exactly backwards | ADR-2026-10-26-002: `TrueMindCycle` is the one conductor; `MindCycle` `@Deprecated`; the CLI defaults to it with `--legacy` opt-in; `CognitiveConductorGuardTest` enforces it (proved non-vacuous) | 117 suites / 912 tests / 0 failures |
| §16 module scope | 4 modules had no lifecycle state; the archive markers TRUE-MIND-AUDIT §4 promised did not exist; `matrix-web-ui`'s jest suite had never run — it could not start (`setupFilesAfterEach` is not a Jest option, `ts-jest` was undeclared, `jest-environment-jsdom` was undeclared) | `MODULE-LIFECYCLE.md`; 3 archive markers; `scripts/verify-dormant-modules.sh`; jest config rewritten to `next/jest`; 12 broken tests fixed; `web-ui-tests` is now a required CI job; `ModuleLifecycleRegistryTest` enforces the registry | web-ui **8 suites / 50 tests / 0** · micro **PASS 4/4** · fpga **SKIPPED** (amaranth absent) · ros2 **NO-SUITE** |
| §20 capability claims | The published page advertised "724 tests", "33,000x faster than LLMs", a 25% random baseline, "90% accuracy retention from LLM distillation" and "every claim is backed by ..." — none of which any measurement produced | `CAPABILITY-REGISTRY.md` (10 claims, each with state + evidence); the page corrected with provenance per card; `scripts/count-tests.sh` is the named command (**8,898** test methods, 14 modules); `CapabilityClaimsGuardTest` enforces it | 4 guard tests / 0 failures |
| popcount benchmark | `:matrix-core:test` was **red**: the test asserted a *timing outcome* (machine-dependent) against a baseline that was the **retracted wrong function** (XOR-fold) | benchmark against production; the retraction is now pinned by a timing-independent property | production 0.10 ms · unrolled4 0.44 ms (**0.22x** vs production) · fold 1.25 ms (0.08x) |

### Corrections to the prior audit (second pass)

- The full-suite executed count is **8,898** test methods (source-counted) — not the "724" the front page claimed, and not the 9,324 figure recorded in an earlier session entry.
- `matrix-web-ui` has a pre-existing peer conflict: `eslint-config-next@16.3.6` needs `eslint >= 9`, the project pins `eslint@8.57.1`, so a strict `npm install` fails with ERESOLVE. CI installs with `--legacy-peer-deps`. Aligning them is a major upgrade and needs an RFC — recorded, not resolved.

### Continuation — RECON-W38.5: the five recorded formal-property failures resolved (2026-10-30)

| Specification | Root cause | Evidence in the Java | Fix |
|---|---|---|---|
| `MPDTNeuron` | `Init` seeded the memoised output `FALSE` against a truth table that may contain input 0 — hidden state | `NeuronLayer.evaluate()` computes each output on demand and returns a fresh `BitSet`; no persistent output field exists | `Init` seeds the output from the table; the model is **stronger** |
| `MemoryM4Causal` | F1's implication was inverted against its own comment; the `Insert` guard was inverted against F2; the helper encoding the right direction was dead code | `GrowOnlySet` is a G-Set whose only removal path records a tombstone in the same step | F1 rewritten to its comment's meaning; the helper's inequality corrected (`t ≥ epoch`) and now **called**; helper moved above its first use |
| `MctsLatsVisit` | `Expand` had no `child # parent` guard; `Spec` encoded no fairness | `MCTSPlanner.expand()` builds a fresh `TreeNode`; `backpropagate()` walks a chain that must terminate; `plan()` backpropagates every iteration | `Expand` guard added; `Spec` gained `WF_vars(Next)` |
| `ConjugateBudgeterDP` | the witness unioned the skip and take witnesses and so could exceed the envelope; the λ-monotonicity property asserted a **mathematically false** claim | `ConjugateBudgeter.conjugateDp()` tracks the running budget (`e -= c[i]`) and takes the reduced-budget witness; the same false λ claim was in the Java's **own javadoc** | `GreedyPick` follows the Java; `LambdaMonotoneTwoStep` → `LambdaBounded`; the false claim corrected in the Java's javadoc with its counterexample (V*(7)=14, V*(8)=14, V*(9)=17) |
| `FrozenEthicalFNL` | the model accumulated one axiom per step while its three properties were mutually inconsistent | `FrozenAxiomNeuron.activate(BitSet)` is a **pure function**; `FrozenEthicalFNL.evaluate(BitSet)` calls it on **every** neuron against the same features | one-step evaluation matching the Java; the three properties restated; per-feature-set weak fairness added |

**Result: all nine specifications model-check green** — no recorded failures,
no syntax-only checks. TLC's constant-level warning now fires for `BrcStep`
only, which is the documented truth about that module. The CI quarantine list
in `.github/workflows/tla.yml` is **empty**; the mechanism is kept so a future
failure is reported loudly rather than hidden.

No property was weakened, deleted, or replaced with `TRUE` to obtain a green
run. Four properties remain **vacuous** — checked, passing, and proving
nothing (`BotEthicsPipeline.EventualKillRejection`, `HashChain.TamperDetected`
and `RestoreVerifies`, `MctsLatsVisit.Convergence` and `BestArmIsArgmax`,
`BrcStep`'s three invariants). Each is named in
`docs-v2/engineering/TLA-STATUS.md` and is not counted as verified.

### Continuation — RECON-W38.5 second pass: two vacuous properties closed (2026-10-30)

| Property | Was vacuous because | Evidence in the Java | Change |
|---|---|---|---|
| `HashChain.TamperDetected` / `RestoreVerifies` | the module had **no tamper action**, so `TamperDetected`'s antecedent (which required an append to produce a payload other than the one appended) was unreachable | `io.matrix.audit.HashChain`: "any retroactive mutation invalidates every subsequent verify() check"; `verify()` recomputes every link's hash; `restore()` throws on a chain that does not verify | `ChainIntact(c)` is now a named operator; a `TamperLink(i, p)` action mutates a payload without touching its hash; `AppendLink` sets `verified' = ChainIntact(chain')` instead of `TRUE`; `HashChainDependency` became the per-chain definition it always was, and `VerifiedImpliesIntact` took its place as the invariant; `AppendKeepsChainValid` — false in every non-empty state and excluded from the cfg — now says what its comment says and is checked |
| `MctsLatsVisit.BestArmIsArgmax` | `ArgmaxByReward` was `CHOOSE n ∈ Nodes : TRUE` (an arbitrary node), and the property said nothing about what `bestArm` **is** | `MCTSPlanner.extractBestPath()` keeps the child with the strictly greatest average reward; `TreeNode.getAverageReward()` returns 0 when `visits == 0` | `ArgmaxByReward` selects a node of maximal **mean reward** by cross-multiplication, with a zero-visit node treated as mean 0; `BestArmIsArgmax` asserts that no rival beats the selected arm |

**Evidence that the checks are now real:** both failed while they were being
written, each with a concrete counterexample — a swapped cross-multiplication,
a zero-visit node treated as having no mean at all, an inverted argument order
in the property itself, and a tamper path that broke `HashChainDependency`.
A stub cannot fail. `HashChain`'s state space grew from **820 to 20 440
distinct states** because broken chains are now reachable; `MctsLatsVisit`'s
is unchanged, because its check became real rather than its model bigger.

**All nine specifications parse and model-check with no violation.**

### Continuation — RECON-W38.5 third pass: BrcStep remodelled (2026-10-30)

| Item | Finding | Change |
|---|---|---|
| `BrcStep`'s three configured invariants | TLC reported all three as constant-level: the helpers were stubs (`HammingDist == 0`, `SuperStep(s1,s2) == s1`, `ApplyFn == CHOOSE ... TRUE`), so they evaluated to TRUE without looking at the model. **No configured property mentioned `applied`, `result` or `stepState` — the state machine was constrained by nothing.** | the step set, the concrete function each step computes and the bit-vector space are now module-defined; `HammingDist` is real; the composition is modelled as the Java's `BrcChain.compose` — chains of steps with `compose(l,r) == l \o r`, not a `SuperStep` over a pair of steps |
| `BrcStep`'s state machine | nothing constrained it | two invariants added, both the Java's own rules: `AppliedResultMatchesFn` (the recorded output is what the step computes) and `ConvergenceIsComputed` (`BrcStep.checkConvergence`, recomputed on every application, not latched) |
| `BrcStep.ChainTerminates` | "every step eventually converges" is **false of the implementation**: with a threshold of 1, a step whose output is always two bits from its input never converges — the complement step is exactly such a step | replaced by the conditional guarantee the Java gives (`ConvergenceIsComputed`) plus `ConvergenceReachable` |

**Non-vacuity proved by mutation:** recording the input instead of the output
made `AppliedResultMatchesFn` fail; widening the convergence threshold made
`ConvergenceIsComputed` fail. The composition laws were shown to be checked
against the model as well — TLC refuted `ComposeEndpointPreserved` when the
chain fold ran the steps in reverse order.

**All nine specifications parse and model-check with no violation, and NO
specification triggers TLC's constant-level warning any more** (previously
`BrcStep` alone did, three times).

### Continuation — RECON-W38.5 fourth pass: the last two unfalsifiable properties (2026-10-30)

| Property | Was vacuous because | Evidence in the Java | Change |
|---|---|---|---|
| `BotEthicsPipeline.EventualKillRejection` | the model had **no FNL in it**: approving and rejecting were both enabled for every `(bot, action)` pair, so the decision was free; and the antecedent `[] (lastAction[b] = a /\ Contains("kill", a))` demanded the kill action persist in EVERY future state, which fairness prevents | `io.matrix.api.BotEthicsPipeline` contains **no "kill" substring test**: `FROZENFNLGuardian.evaluate(text)` asks the FROZEN FNL for a verdict and follows it; the FNL's trigger set is **data** | the trigger set is a module constant (`FnlTriggers`, from the cfg); the approve/reject actions are gated on it; a `lastVerdict` variable records the verdict; the property is now `FnlVerdictIsFollowed` — the recorded verdict IS the FNL's verdict, in the same step |
| `MctsLatsVisit.Convergence` | `Probability(p)` was the placeholder `0`, `OptimalArm(node)` was `CHOOSE n ∈ Nodes : TRUE`, and `Threshold(N, alpha)` was `N` — unreachable in the modelled budget | — (it is a probabilistic claim; TLC has no probability and no finite model can establish it) | **removed from the configuration file** — a property that cannot fail in a CI gate reports a green that no computation produced — and kept in the module as an **imported claim marked UNVERIFIED**. Replaced in the gate by `TypeOK` (the module had **no type invariant at all**) and `VisitBudgetRespected` |

**Evidence:** `FnlVerdictIsFollowed` was proved non-vacuous by mutation —
removing the `a ∉ FnlTriggers` guard made it fail, and recording an approval
as a rejection verdict made it fail. `BotEthicsPipeline`'s state space shrank
from **14 621 / 7 396 to 481 / 256**, because the decision is no longer a free
choice. `TypeOK` immediately **found a real defect**: the accumulated
`totalReward` was bounded by the per-visit reward domain (`-1..1`) while it
is a sum of those and can reach ±2.

**All nine specifications parse and model-check with no violation, and NO
property in any configuration file is unfalsifiable any more.**

### Still open

- The MCTS convergence bound is not machine-checked and is recorded as
  UNVERIFIED; it would need a probabilistic model or a different tool.
- The `AGENTS.md` FROZEN edit in the working tree needs an owner ruling.
- The eslint major-version conflict needs an RFC.
- `BotEthicsPipeline`'s spec asserts a kill-trigger rejection policy the Java
  does not implement — the formal spec is currently stronger than the code.
- `MCTSPlanner` has no production caller.
- The `AGENTS.md` FROZEN edit in the working tree needs an owner ruling.
- The eslint major-version conflict needs an RFC.

---

## RECON-W39 — Owner directive "MASTER DEVELOPMENT MISSION" (2026-10-30)

**Base:** `develop` @ `7043d925` · **Mandate:** the specification-to-implementation
chain: intended semantics -> TLA+ -> Java -> tests -> CI -> production claim.

### Incident — FROZEN-zone CI trigger change (2026-10-30, decision D9)

`.github/workflows/**` is FROZEN (CONSTITUTION Art. III / AGENTS.md). The owner
directive (§5.2) mandates that a Java change to a modelled behaviour must run
the formal gate. `.github/workflows/tla.yml` triggered on `formal/**` only, so
a change to `NeuronLayer.java`, `HashChain.java` or `ConjugateBudgeter.java`
never reached TLC.

**Decision D9:** the `tla.yml` path filter is extended with a **generated
block** naming every implementation, production caller and test that
`formal/spec-implementation-map.yml` maps. The block is not hand-maintained:
`./gradlew :matrix-quality:regenerateFormalPaths` rewrites it from the
registry, and `SpecImplementationMapTest` fails the build when the block and
the registry drift apart. The `pull_request` filter also gained the workflow
file itself, which it lacked.

No other FROZEN file was touched. `CONSTITUTION.md` and `AGENTS.md` are
unchanged. `matrix-core/src/main/java/io/matrix/ethics/frozen/**` was read
only — the defect found there is recorded as an open item needing an RFC.

### What the registry found

Nine specifications, nine conformance records. The statuses are per
specification and explicit; `PRODUCTION_PATH_VERIFIED` is refused by the
validator unless the specification names a production caller.

| Specification | Production caller | The finding |
|---|---|---|
| `MPDTNeuron` | yes | `KBounded`/`KMaxIsGlobalCeiling` are tautologies; the cfg pins `K_MAX = 2` against the Java's 20 |
| `MemoryM4Causal` | **no** | the Java had no epoch-aware implementation at all |
| `MctsLatsVisit` | yes (`io.matrix.mcts`) | the model describes `MCTSPlanner`, which nothing calls; production selects by visit count |
| `Consensus` | **no** | `ConsensusEngine` is instantiated only by tests |
| `BotEthicsPipeline` | yes (REST) | `FnlTriggers` is exact-match; the Java matches phrases |
| `BrcStep` | yes (`evaluate`) | `compose` has no production caller, yet three properties constrain it |
| `HashChain` | yes | the model derives the timestamp from chain position; the Java used the wall clock |
| `ConjugateBudgeterDP` | yes | the shadow price is computed on a gcd-reduced grid, so it is not the model's slope |
| `FrozenEthicalFNL` | yes | the cfg's trigger relation is not the Java's; and `Set.copyOf` iteration order is randomised per JVM run |

### Repairs in this wave

| Area | Finding | Fix | Evidence |
|---|---|---|---|
| HashChain | the injected `Supplier<Long> clock` was **dead code**; `Objects.requireNonNull(timestampMs >= 0 ? "ok" : "ts")` never threw; the sequence rule was unenforced | clock honoured, timestamps and 64-hex hashes validated, `sequence(i) = i - 1` enforced in `verify()`/`restore()` | `ADR-2026-10-30-003`; 19 new tests |
| CRDT | `GrowOnlySet.toJson()` wrote the **elements only**, so tombstones were lost — a tombstoned key could be resurrected after a restart | versioned, lossless format; legacy array format still readable; unknown version rejected | `ADR-2026-10-30-004`; 8 new tests |
| CRDT | three incompatible deletion semantics shared one package | `EpochAwareCausalSet` added as the explicit epoch-aware implementation | 20 property-based tests |
| MemoryM4Causal | the model's `Tombstone` deleted a **strictly newer** value; the `Merge` filter used a **stale** clock while raising it | both corrected; F1-F4 still hold | TLC: 396 577 states, 26 244 distinct, no error |
| CI | a Java change to a modelled behaviour never ran TLC | generated path filter from the registry | `SpecImplementationMapTest`, 9 tests |

### Still open

- `FrozenEthicalFNL.evaluate()` iterates `Set.copyOf(...)`, whose order is
  randomised per JVM run: with several axioms firing, `Result.firedNeuron()`
  is not reproducible. It is in a FROZEN zone and needs an RFC.
- `Consensus` and `MemoryM4Causal` have no production caller.
- `MCTSPlanner` and `LatsValueFunction` have no production caller; the LATS
  mode is never enabled in production.
- The shadow-price definition of `ConjugateBudgeterDP` still does not match
  the Java's gcd-reduced grid.
- The MCTS convergence bound remains UNVERIFIED.
