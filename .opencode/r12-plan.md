# MATRIX — Full State Audit and Forward Plan
# Date: 2026-09-04 22:44
# Branch: origin/main @ ff431854
# Author of this audit: Goal Mode main agent (this turn)

## 1. PROJECT IDENTITY (single source of truth)

**MATRIX** is a deterministic neuro-symbolic cognitive system built
on the **BIR** (Boolean Intermediate Representation) substrate.

Three singleton FROZEN normative documents govern everything:
- `CONSTITUTION.md` (8 articles) — sets invariants
- `AGENTS.md` — sets session procedures
- `WAL.md` — current progress snapshot (running log)

Architecture: 502 Java source files, 401 test files, 45 REST endpoints
(29 `/v1/*`, 16 `/api/v1/*`). 696 commits in branch. Currently running
Quarkus 3.38.3 + Java 25 + GraalVM plugin 1.1.10.

## 2. WHAT IS DELIVERED (verified by tests + measurements)

### Core Boolean IR (Waves A-N, all green)
- BIR compiler with 3 equivalent forms: TT (K_MAX=20), CLAUSESET, BDD
- 37 sites migrated to BIR; INV-1 source-scan guard in CI
- JvmSimd + Fpga backends
- Producers: TsetlinTrainer (B1/B2), WisardProducer (H-010 accepted),
  MpdtGaProducer (baseline)
- ELSP federation: Ed25519 + ML-DSA postquantum (JEP 497)

### Curriculum & lifecycle (Waves O-19)
- CompetenceAssessor, CurriculumEngine (ZPD), MaturityGateKeeper MA-0..5
- CauldronProtocol, FnlGate (SHADOW→PROMOTED), ConsolidationCycle
- PlanRunner (Hoare), PlanPreprocessor (AC-3)
- RicCI knowledge topology (ktopo)

### Experimental results (real measurements, not estimates)
- **H-010 accepted**: WiSARD 9/9 accuracy, 242x faster than Tsetlin
- **H-002/H-003 refuted-toy**: GA beats Tsetlin on synthetic
- **EXP-009B/C**: BIR x149 faster than ORT-CPU, x276 faster than GPU per-call
- **EXP-MATRIX.12 accepted**: BitLinear + hill-climb improves chain by +6.7pp on HellaSwag-30

### MATRIX WAVE (LLM realization, RUN 6-11.1)
The system is now a complete "boolean chain LLM":

| RUN | What | Commit | Result |
|---|---|---|---|
| 6 | BitLinearTrainer | 4a13f64e | training mechanics work |
| 7 | Real LLM + corpus retrieval | 7af0da3d | 8,604 Q&A pairs, multilingual |
| 8 | CRITICAL TensorProjector offset fix | c648f075 | 99.8% neurons no longer empty |
| 9 | Structural chain eval + direct scoring | 0a0e2b58 | varied output across prompts |
| 9.5 | flippedTable bug fix | e300c353 | training actually modifies weights |
| 9.6 | Per-pair flip cap = 200 | 11bd5638 | no mode collapse |
| 9.7 | Wordish bias removal | c3a4f4de | each prompt = unique output |
| 9.7 | /v1/chain/reload endpoint | 7a7f640e | rebuild in 597ms |
| 9.8 | /v1/train/async | 6c64e45e | server stays responsive |
| 10 | LM head projection infra | 3c96c284 | opt-in via env var |
| 11 | Negative sampling (auto-fixed by Goal Guard) | 957557e3 | thread-safe, deterministic seed |
| 11.1 | Audit fixes (opt-in nNegatives, vocab bound) | ff431854 | all 6 audit findings resolved |

**Tests passing**: 32/32
- LmHeadTest 7/7 (incl. negative sampling)
- BooleanChainRunnerTest 5/5 (incl. replaceLayers)
- BitLinearTrainerTest 8/8 (incl. flip cap)
- QaCorpusIndexTest 12/12 (multilingual)

**Chain state**: 24 layers x 915 neurons = 21,960 boolean neurons,
46.2% avg density, 449 empty. Loaded from
`models/external/qwen2.5-0.5b/model.safetensors`.

## 3. WHAT'S BLOCKED (CONSTITUTION VI compliance - no estimates)

| Blocker | Needs | Source |
|---|---|---|
| Real-domain EXP verdicts | Production corpus (was deleted per directive, recoverable from git history) | PLAN.md |
| Energy metrics for H-009 gate | Wattmeter or model-based power model | PLAN.md |
| Audio-events stage 3 | Prioritize DESIGN-06 | PLAN.md |
| Quantum BIR-MPS (FR-D3) | Quantum substrate | PLAN.md |
| FPGA synthesis | yosys/nextpnr toolchain | PLAN.md |
| Hansel chains (DESIGN-09 v2) | Research wave | PLAN.md |
| Native-image build | Mandrel container OR Pekko replacement (user RFC) | EXP-MATRIX.13-native-final |
| HF token | User setup | RUN 10 context.md |
| GPU ONNX runtime | System CUDA 12 + cuDNN9 | STANDARDS-MATRIX.md |
| LM head fluent output | Real chain output as features (slow) + gradient descent | RUN 11 honest caveats |
| TLA+ specs for top packages | reasoning/, mediator/, hades/, memory/, rag/ | PLAN.md SDD-sweep |

## 4. THE HONEST REMAINING GAP (the only user-facing one)

The chain produces **prompt-specific but garbled text** when generating
beyond the corpus. Root causes documented:
1. Hash-based scoring is prompt-aware but not corpus-aligned
2. LM head has degenerate behavior with low vocab coverage
3. Negative sampling is opt-in (nNegatives=0 default) due to perf
4. Chain's actual evaluate() output is mostly zeros (structural issue
   documented in RUN 9 - evaluateWithMagnitude fixed propagation
   but final layer still sparse due to 12,810-bit input vs 256-bit
   actual input)

User-facing behavior:
- OK QA retrieval: REAL answers, multilingual (8,607 entries)
- OK /v1/qa/learn: persist new pairs, immediately retrievable
- OK /v1/chat: routing chooses QA retrieval (primary LLM behavior)
- OK /v1/generate: prompt-specific output, multi-language BPE tokens
- OK /v1/train: capped at 200 flips/pair, async via /v1/train/async
- OK /v1/chain/reload: rebuild from safetensors in <600ms
- OK /v1/lm-head/train + /status: opt-in via nNegatives param

## 5. CURRENT GOAL-GUARD STATE

From the Goal Guard plugin state:
- Goal Contract: 9 acceptance criteria recorded (long-running goal)
- Review cycles: 1 (cycle #0 auto-applied audit fixes in 957557e3)
- Working tree: clean
- 14 review gates tracked; latest cycle completed via auto-fix

The latest user message provided a manual review verdict (FAIL with
6 blocking findings + 3 polish items). All 6 blocking items were
addressed in ff431854:
1. OK Silent behavior change -> opt-in default (nNegatives=0)
2. OK Wall-clock RNG -> deterministic seed (auto-fixed in cycle #0)
3. OK Vocab bound bug -> negMax=200000
4. OK Uncommitted -> committed as ff431854
5. OK Inconsistent defaults -> both 2-arg overloads default 0
6. OK Missing docs -> FINALSUMMARY section XIX + context.md + WAL.md

3 polish items also fixed:
- OK Redundant Math.min(200000, 100000) removed
- OK "positive update" comment now correctly labeled
- OK Tests use deterministic isBetween(2, 4) instead of >= 4

---

# FORWARD PLAN - RUN 12 ONWARD

This plan is organized by **what value each RUN delivers**, not by
file touched. Each RUN ends with: committed code + docs + tests.

---

## RUN 12 - ARCHITECTURE FIRST: Complete the Brain Loop

**Goal**: Make /v1/chat use the consciousness loop (DESIGN-18) instead
of the current direct QA retrieval. This unblocks H-042..H-050.

**Why this is next**: 12 hypothesis cards (H-042..H-050) are unverified
because the brain loop is not wired in. The MATRIX spec declares the
consciousness loop as the production decision path; currently the
codebase has the components but they're not connected end-to-end.

**Concrete tasks**:
1. io.matrix.reasoning.ConsciousnessLoop exists but is not injected
   into /v1/chat. Replace the QA-first routing with a loop-driven
   routing where each step routes through:
   - perception -> attention -> deliberation -> gate -> action -> consolidation
2. Add x-matrix-trace header to responses with the actual step path
3. Tests:
   - ConsciousnessLoopTest: verify each phase is invoked once per chat
   - ChatLoopIntegrationTest: verify header reflects trace
4. Spec: SPEC-006 already normative; update SPEC-006 to mark
   "production path" instead of "draft integration"
5. Honest measure: response time for 1 chat request (p50, p99)

**Done criteria**: /v1/chat response carries x-matrix-trace header
showing all 5 phases invoked; H-042 latency budget testable.

**Time estimate**: 2-3 hours.

---

## RUN 13 - SDD SWEEP (close the spec gap)

**Goal**: Write SPEC-XXX.md for the 5 top "needs-spec" packages
called out in PLAN.md and SDD-COVERAGE.md.

**Why this is next**: AGENTS.md mandates "new classes have tests"; the
PLAN.md mandates SDD coverage. The 5 packages are:
- reasoning/BrcChain (referenced by BrcStep but no spec)
- mediator/ (InstanceMediator + hierarchy)
- hades/ (BurdenLiftingRitual, Eleutheria, DerangementDetector)
- memory/ (HierarchicalMemory exists but no canonical spec)
- rag/ (IndexedBooleanRag exists)

**Concrete tasks**:
1. SPEC-008-reasoning-brcchain.md: normalize BrcChain spec (currently
   code-first, no normative doc)
2. SPEC-009-mediator.md: mediator hierarchy semantics
3. SPEC-010-hades.md: burden-lifting ritual + derangement detector
4. SPEC-011-memory-hierarchy.md: hierarchical memory + consolidation
5. SPEC-012-rag-boolean.md: indexed boolean RAG (5 Java files)

Each spec follows the existing template (see SPEC-006 for layout).

**Done criteria**: 5 new normative SPEC-*.md files committed; cross-
references in PLAN.md removed; SDD-COVERAGE.md updated.

**Time estimate**: 1.5-2 hours.

---

## RUN 14 - TLA+ FORMAL CONTRACTS (close the proof gap)

**Goal**: Write 4 TLA+ specs called out in PLAN.md as next-format-contracts:
- BRC-Step (Boolean Reasoning Chain step)
- ConjugateBudgeter-DP
- Memory-M4-Causal
- MCTS-LATS-Visit

**Why this is next**: FORMAL-CONTRACTS.md lists these as the next
contracts. Without TLA+ specs, we have no model-checked invariants
for the core reasoning loops. AGENTS.md mentions JMH gates for
DESIGN-04/14 - TLA+ is the analogous gate for reasoning.

**Concrete tasks**:
1. Each TLA+ module in matrix-core/src/main/resources/tla/:
   - Module declaration + invariants
   - Type definitions matching Java
   - State machine with Init/Next
   - Safety properties
   - Liveness properties (where applicable)
2. TLAPS proof obligations for each (sketched if formal proof too slow)
3. CI step: tla2tools.jar runs TLC on each spec (or skip for now)
4. Cross-link from SPEC-* to TLA+ via formal/ subdir

**Done criteria**: 4 TLA+ specs committed; FORMAL-CONTRACTS.md updated;
TLAPS sketch at minimum.

**Time estimate**: 2-3 hours (sketches, not full proofs).

---

## RUN 15 - LM HEAD: use REAL chain output as features

**Goal**: Replace hash-fingerprint features in LmHeadTrainer with the
chain's actual evaluate() output. Add cache to avoid recomputing.

**Why this is next**: The LM head is the only remaining architectural
gap for fluent text generation. The current hash fingerprint is a
proxy; using the real chain output should give cleaner signal.

**Concrete tasks**:
1. Add chain_output_cache.json for question -> chain_output mapping
2. LmHeadTrainer.trainOne() checks cache first; populates if missing
3. Initial cache build: run chain on all 8,606 corpus questions (~2 min
   for 24-layer x 21,960-neuron eval at 174us each ~= 1.5s total)
4. Retrain LM head with real features, nNegatives=5
5. Verify: training completes in <5 minutes (vs >10 min previously)
6. Test: new test verifies cache hit/miss behavior

**Done criteria**: lm_head_weights.bin retrained with real features;
status shows vocabCoverage > 5000; chain generation produces more
corpus-aligned output (test by counting tokens that appear in
corpus answers).

**Time estimate**: 1.5-2 hours.

---

## RUN 16 - H-043, H-046 verification (close the brain hypothesis gap)

**Goal**: Run EXP-MATRIX.14 and EXP-MATRIX.15 to verify H-043 and H-046
(recently retuned per session note). These are the closest-to-ready
hypothesis cards in the MATRIX wave.

**Why this is next**: With brain loop wired (RUN 12), we can finally
test the brain hypothesis cards that have been parked.

**Concrete tasks**:
1. EXP-MATRIX.14 - Decentralized digest (H-043): anonymizer + DP-noise,
   measure utility >=0.7 vs baseline. Use federation package.
2. EXP-MATRIX.15 - Subconscious gate filter accuracy (H-046): generate
   impulse stream, ground-truth from FROZEN-FNL, measure accuracy >=0.9.
3. Update HYPOTHESES.md with results: accepted / refuted
4. If accepted: add CLAIM to FINALSUMMARY with measured numbers
5. If refuted: document the falsification, propose improved hypothesis

**Done criteria**: 2 EXP-*.md reports committed; HYPOTHESES.md table
updated; FINALSUMMARY section XX (section after current XIX).

**Time estimate**: 3-4 hours (depends on H-046 having clean test corpus).

---

## RUN 17 - Production-domain EXP reruns (close the verifier gap)

**Goal**: Rerun EXP-002, EXP-003, EXP-009, EXP-010 on production-domain
corpus (recovered from git history per PLAN.md).

**Why this is next**: All current hypothesis verdicts are synthetic-scope
or refuted-toy. Production verdicts unblock the "to verify" -> "verified"
transition in HYPOTHESES.md.

**Concrete tasks**:
1. Restore models/training_data/ from git history (per PLAN.md note)
2. EXP-002-production: CLAUSESET vs MPDT-GA on real corpus
3. EXP-003-production: Tsetlin convergence on real corpus
4. EXP-009-production: BIR distillation on real (preserved) corpus
5. EXP-010-production: WiSARD vs Tsetlin on real
6. Update all 4 EXP-*.md reports with production numbers
7. If verdicts flip: HYPOTHESES.md updated; release-notes updated

**Done criteria**: 4 production EXP reports committed; HYPOTHESES table
has at least 2 promoted from synthetic-scope to production.

**Time estimate**: 4-6 hours (most of it is corpus + experiment runtime).

---

## RUN 18 - Mandrel native build (close the deployment gap)

**Goal**: Produce a working native-image binary via Mandrel container.

**Why this is next**: User RFC requirement; EXP-MATRIX.13-native-final
documented this as the path forward. Native image is required for
production deployment.

**Concrete tasks**:
1. Container build: ./gradlew :matrix-core:quarkusBuild -Dquarkus.native.container-build=true
2. Iterative --initialize-at-build-time whack-a-mole per
   EXP-MATRIX.13-native-final findings
3. If too slow: fallback to option (2) - drop Pekko cluster actor
   for non-Scala alternative (ClusterMediator -> simple HTTP-based)
4. Document final native-image size + startup time

**Done criteria**: matrix-core/build/matrix-core-runner produced and
runs (./matrix-core-runner starts server in <2s, no JVM).

**Time estimate**: 2-4 hours; requires user RFC for container pull.

---

## RUN 19 - Continuous LM head training (close the loop)

**Goal**: ChatDrivenTrainer automatically trains the LM head on every
chat interaction (negative feedback -> decrements, positive -> increments).

**Why this is next**: The system has /v1/chat but the LM head doesn't
learn from chat history. This is the natural extension that makes
the system actually adapt to user feedback.

**Concrete tasks**:
1. Add x-matrix-feedback header support in /v1/chat/feedback
2. On positive feedback: increment LM head weights for the chain
   output x actual answer tokens
3. On negative feedback: decrement LM head weights
4. Persist updated weights to disk on shutdown (LmHead.save())
5. Test: verify weights change after feedback

**Done criteria**: After /v1/chat/feedback, LM head status shows
updateCount > 0 and /v1/generate output shifts slightly toward
positively-rated patterns.

**Time estimate**: 1.5-2 hours.

---

## RUN 20 - End-to-end bilingual QA stress test

**Goal**: Run 1000 mixed Russian/English Q&A pairs through /v1/chat and
measure: response time, retrieval accuracy, hallucination rate.

**Why this is the natural FINALSUMMARY capstone**: it exercises the
entire user-facing API in a representative workload.

**Concrete tasks**:
1. EXP-MATRIX.16: 1000 mixed-language Q&A, measure p50/p99 latency
2. Hallucination detection: any answer not in corpus = hallucination
3. Compare against baseline (random answer): measure lift
4. Document in EXP-MATRIX.16-report.md
5. If lift > 10x: claim ACCEPTED in HYPOTHESES (new card H-051)
6. Update FINALSUMMARY with measured numbers (CONSTITUTION VI compliant)

**Done criteria**: EXP-MATRIX.16 report committed; measured numbers
(not estimates) for p50/p99/lift/hallucination rate.

**Time estimate**: 2-3 hours.

---

## RUN 21 - Documentation stabilization

**Goal**: Single pass that closes all the loose ends:
- WAL.md trim (currently 106 lines, mix of historic + current)
- .opencode/context.md clean rewrite for RUN 21 state
- FINALSUMMARY has Sections X-XX (cap at XX; archive older to docs-v2/archive/)
- README.md updated with latest measurement numbers
- CHANGELOG entry

**Time estimate**: 1 hour.

---

## CRITICAL PATH (what unblocks the most)

If forced to pick 3 highest-leverage RUNs:

1. **RUN 12** (Brain Loop) - unblocks H-042..H-050 verification AND
   makes the MATRIX system actually work as the architecture declares
2. **RUN 16** (H-043, H-046 verification) - first real hypothesis
   verifications in the MATRIX wave; proves the methodology works
3. **RUN 20** (E2E stress test) - concrete measured numbers for
   CONSTITUTION VI compliance; publishable result

These three together turn MATRIX from "system with all components
built" to "system with verified architecture claims".

---

## NON-CRITICAL / DEFERRED

These should not block forward progress:

- **RUN 18** (native build) - useful but not blocking; current Quarkus
  uber-jar works for all tests
- **RUN 17** (production EXP) - required by CONSTITUTION VI eventually,
  but synthetic-scope results are already published
- **Energy metrics, audio-events, quantum, FPGA, Hansel chains** -
  documented as BLOCKED-EXT in PLAN.md; user RFC required
- **Native GPU ONNX** - needs system CUDA 12 + cuDNN9

---

## WORKFLOW NOTES (for whoever picks this up next)

1. **Server is running** at http://localhost:9091 (PID via /tmp/q-r48.pid)
   - MATRIX_AUTO_TRAIN_ENABLED=false (disable auto-train at boot)
   - Panama bridge wired (libtruthy.so)
   - 32/32 tests pass

2. **Git workflow**:
   - Branch: origin/main @ ff431854
   - Commit format: WAL: <description> (AGENTS.md)
   - One commit = one meaningful change
   - Push after every commit
   - Update WAL.md with substantial findings

3. **CONSTITUTION VI compliance** - no unverified numbers in docs.
   All performance claims must come from gradle test / gradle jmh
   output, not estimates.

4. **AGENTS.md mandates**: tests for every new class; targeted suite
   gradle :matrix-core:test --tests "io.matrix.<pkg>.*" before commit.

5. **Goal Guard plugin auto-runs on stop**. If you implement, verify,
   then stop - it will run the full review cycle and fix audit issues
   automatically (cycle #0 example in 957557e3).

---

End of audit + forward plan.
