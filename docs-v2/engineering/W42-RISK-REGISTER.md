# W42 — Risk Register

**RECON-W42.0** · 2026-11-01 · owner directive Part II §3.5
**Companion to** `docs-v2/engineering/RISK-REGISTER.md` (the W39/W41
register). That document is left as it stands — it is the record of the W39
and W41 waves. **This document carries the risks this wave found**, and
corrects two of that register's verdicts.

Status vocabulary: the W41 register's (`FIXED`, `VERIFIED_NOT_A_DEFECT`,
`OPEN`, `INTENTIONAL_LIMITATION`, `REQUIRES_RFC`, `BLOCKED`, `DEFERRED`).

---

## 1. The correction to the W41 register

| # | The W41 register says | The truth, verified this wave | Action |
|---|---|---|---|
| C1 | risk 7 — *"the brain's cycle output is consumed by the memory layer"*, `VERIFIED_NOT_A_DEFECT` | **False.** `ConsciousBrain` has **0 production construction sites** (67 in `src/test`), and `CognitiveGenesisProfileBuilder.fromCycleReport` has **0 `src/main` callers**. The chain terminates in tests. | **Correct the verdict to `DISCONNECTED`** — the register's own "VERIFIED_NOT_A_DEFECT" is the defect. |
| C2 | risk 4 — the unbounded `CognitiveProcessor.history`, `OPEN` | Accurate, and **understated**: the growth is O(n²) memory (the attention matrix is `double[n][n]`) and O(n³) cumulative time. But the exposure is **test-only today**, because `ConsciousBrain` is unreachable. | Keep `OPEN`; add the qualifier. The risk is real and would fire the moment the brain is wired. |
| C3 | risk 9 — `ErrorDrivenLearner.applyDecay` unwired, `OPEN` | Accurate, and the reason matters: it decays the **accumulator** but never the **weights**, so the activity mask is stale across the error loop and the accumulator falls without bound. Wiring it as-is would silently destroy associations. | Keep `OPEN`; **do not wire it before the four fixes** — bounded below, mask refreshed, magnitude-selective decay, and `errorBindingStrength` reading the accumulator it claims to characterise. |
| C4 | `atrix.knowledge.assessment` (the misspelled key) | **Fixed** in `b724c79d` | closed |

---

## 2. The data-loss risk — the one that matters most

| # | Risk | Status | Evidence |
|---|---|---|---|
| R1 | **The Persistence screen tells the operator to back up the store incorrectly.** It says a copy of `workbench.sqlite` is the backup. The store is in WAL mode, so the committed rows live in the `-wal` file. A copy of the database file alone **silently loses every assessment outcome and every asked-question record**. | **OPEN — fix in W42.0** | `KnowledgeWorkbench:727-728` publishes the instruction; `KnowledgeStore:82-84` sets `journal_mode=WAL`. Proven by experiment: the naive copy showed `assessment_outcome: 0, asked_question: 0` where the live store had `1` and `4`; the three-file copy kept both. |

This is the only risk in this register that can **destroy user data**, and it
is a one-line fix plus a test. The fix must either name the three files or
checkpoint the WAL before telling the operator to copy anything.

---

## 3. The epistemic risks — the ones that make the system wrong rather than incomplete

| # | Risk | Status | Evidence |
|---|---|---|---|
| R2 | **The system answers questions it cannot support.** Abstention quality is **0.400** on the W42 benchmark: six of the ten unanswerable items were answered. | **OPEN** | `w42-benchmark-v1.json`; `KnowledgeRetriever:35` `DEFAULT_MINIMUM_COVERAGE = 0.25`; the measured run. |
| R3 | **The system agrees with a false premise.** Both FALSE_PREMISE items were answered, and one answer repeated the false premise ("photosynthesis produces oxygen from carbon dioxide"). The system does not check the premise of the question it is asked. | **OPEN** | the benchmark's `mustNotMention` field; the failure list. |
| R4 | **The contradiction machinery never runs.** `KnowledgeStore.recordContradiction` has no production caller, so the `contradiction` table is **always empty** and `GET /contradictions` always returns `[]`. The feature was built in W41 and then not connected. | **OPEN — one-line wiring** | `KnowledgeStore:1545`; the live endpoint returned `[]` while the logic domain has two genuine conflicts. |
| R5 | **The derivation verdict is never persisted.** `ReasoningEngine` computes a `DerivationStatus` in memory; `KnowledgeStore.recordDerivation` is test-only, so every stored derivation stays `UNDECIDED`. | **OPEN — one-line wiring** | `ReasoningEngine:164-167`, `KnowledgeStore:1250`, `KnowledgeImporter:207`. |
| R6 | **The flagship domain has no rules.** `photosynthesis.pack.json` contains **0 derivations** (the logic pack has 3), so the flagship workspace's inference step always reports *"the workspace holds no rule that bears on this question"*. | **OPEN** | the pack; `ReasoningEngine:186`. |
| R7 | **The support score's contradiction field is hard-coded to `NOT_COMPUTED`** even though the retriever holds the claims that would settle it. | **OPEN** | `KnowledgeRetriever:224`. |
| R8 | **The tokenizer every path depends on has zero tests.** `TextFeatures` is referenced 38 times — by the retriever, the grader, the contradiction detector, the reasoning engine, the feature extractor — and has **no test anywhere in the repository**. | **OPEN** | the class census; `TextFeatures.java`. |
| R9 | **Retrieval is English-only.** The stopword list is 80 English words; the suffix fold handles only English morphology; the negation list is English; the task-classification keywords are English. The measured cross-lingual accuracy is **0/5**. | **OPEN — the W42.3 work** | `TextFeatures:30-38, :141-156`; `ReasoningEngine:290-301, :316-331`; `ContradictionDetector:112,139`; the benchmark. |
| R10 | **The learning lifecycle is a computed label.** No stage is stored, no transition is recorded, no legal-transition guard exists. A regression is visible only as a recomputed label, and the regression note fires **only when the last outcome** contradicts an earlier success. | **OPEN — the W42.1 work** | `KnowledgeWorkbench:202-219`; `LearningStateEvaluator:149-150`; no stage table in the schema. |

---

## 4. The unbounded-state risks

| # | Risk | Status | Evidence |
|---|---|---|---|
| R11 | **`HierarchicalMemory`'s eviction never fires on the live path.** The auto-eviction triggers on size, but `evictLowestLevel` removes only **L0** entries, and the production pipeline writes **only L2** (`DefaultBrainPipeline:151-152`). The `entries` map therefore grows without bound on the live path. | **OPEN** | `HierarchicalMemory:180-182, :360-370`; `DefaultBrainPipeline:151-152`. This is the W41 register's "unbounded" family, and this instance is **on a production path**. |
| R12 | **`PersistentHdcStore.vectors` is unbounded**, while the HDC path's other two structures are bounded at 10 000. | **OPEN** | `matrix-brain-runtime/.../PersistentHdcStore:41`. |
| R13 | **`ConsciousBrain.meaningStore` is unbounded** and the whole map is copied twice per cycle, keyed by a continuous float sum. | **OPEN — test-only today** | `ConsciousBrain:31, :167, :173`; `PragmaticTest:122-126`. |
| R14 | **The cognition pipeline's history is unbounded** — `CognitiveProcessor.history` grows by one per call and the attention matrix is O(n²). | **OPEN — test-only today** | `CognitiveProcessor:53,64,75`; `CognitiveAttention:78`. |
| R15 | **`SqliteMemoryBackend.compact` hard-DELETEs rows** — a silent deletion where the mission's §9.1 asks for deliberate, reversible forgetting. `TombstoneService` exists and is not wired to it. | **OPEN** | `matrix-core/.../memory/SqliteMemoryBackend:156`; `io.matrix.privacy.TombstoneService` is live only in the privacy path. |

---

## 5. The integration and governance risks

| # | Risk | Status | Evidence |
|---|---|---|---|
| R16 | **Twenty-one components are implemented, tested and unreachable.** The repository's test count is a **coverage** signal, not a capability signal. A reader of the documentation could reasonably conclude that the system does much more than it does. | **OPEN — the reason this wave exists** | `W42-INTEGRATION-MATRIX.md` §7. |
| R17 | **The rebuilt teach-back experiment is unreachable from the interface.** The UI still calls the W40 containment measurement, while the guide documents that measurement's numbers. A user of the product sees the W40 instrument, not the W41 one. | **OPEN** | `index.html:670` calls `/teach-back`; `/teach-back-experiment` has no UI caller; `LEARNING-WORKBENCH.md` §6 reports the legacy numbers. |
| R18 | **The W40 lesson-containment path is still the one the interface uses.** Its result feeds `CapabilityEvaluator`'s `KNOWLEDGE` stage — so the highest capability stage is gated on a measurement the W41 audit declared invalid. | **OPEN** | `KnowledgeWorkbench:277`; `CapabilityEvaluator:133`. |
| R19 | **Three parallel knowledge systems**, and the provenance-carrying one is invisible to the brain. | **OPEN** | `W42-ARCHITECTURE-REALITY-MAP.md` §7. |
| R20 | **The store's location depends on the working directory.** The paths are relative classpath paths; running the same JAR from the repository root creates a **second, empty** store. | **OPEN** | `application.properties:91-99`; `quarkusRun` sets the CWD to `matrix-core/`. |
| R21 | **Nine shadow classes on `develop`** duplicate live engines and are documented as if they were the product. | **`REQUIRES_OWNER_DECISION`** | `W42-HISTORICAL-BRANCH-RECOVERY.md` §4. |
| R22 | **The GPU metric publishes a ratio of counters as a speedup**, and the engine behind it always reports CPU. | **`REQUIRES_OWNER_DECISION`** — the W41 wave did not touch it | `GpuKernelEngine:45-52, :122-125`. |
| R23 | **The MCTS reward is the constant `0.5`**, so UCB1 degenerates to "prefer the least-visited child" and the selected action is arbitrary. The LATS/PUCT path is unreachable because the six-argument constructor hard-codes `latsMode = false`. | **OPEN** — but the mission's §11.1 forbids adding MCTS without a real objective, so the fix is a real reward or the removal of the claim | `PlanningStage:103-105`; `MctsTree:49-53`; `PlanningStage:162-172` admits it in prose. |
| R24 | **`AgentLoop.checkConvergence` converges on any completed task**, including one completed before the loop started. | **OPEN — unreachable today** | `AgentLoop:430-431, :439-441`; the class has 0 production construction sites. |
| R25 | **The `ConsciousBrain` surprise is identically zero** — the prediction error compares the observation with itself — so the `PREDICTION_ERROR` branch is dead code and `WuWeiPolicy` always sees zero surprise. | **FIXED (W42.8)** — the prediction now comes from the brain's own recall, and the surprise separates the cases it was built to separate | before: `the cycle 0: the surprise = 0.0`, `a very different observation: the surprise = 0.0`; after: `18.468` with nothing to recall, `12.771` for a similar observation, `173.06` for a very different one. The commit is `6fb6c4f7`; the test is `ConsciousBrainPredictionTest`. The class is still `DISCONNECTED` from the knowledge workflow (`W42-INTEGRATION-MATRIX.md`), so the fix is not yet load-bearing for the mission's journey. |
| R27 | **The repository's own quality gate is RED, and has been since before this wave.** `scripts/quality-gate.sh` exits 1: the literal candidates rose from the accepted 270 to 346, and the frozen-zone check finds 785 diff lines in `.github/workflows/` and 14 in `AGENTS.md` against the W30 baseline `b144563e`. The gate is versioned, it runs, and it fails — but nothing in the routine runs it, so the failure was never seen. | **OPEN — `REQUIRES_OWNER_DECISION` for the frozen zones** | `scripts/quality-gate.sh`; the baseline is `docs-v2/quality/quality-baseline.txt`, last written 2026-10-02 by the W30 wave. The baseline was NOT updated: accepting 76 new literals is weakening the gate, and the mission's §16 forbids weakening a formal property to make the build green. The frozen-zone diffs are committed changes to `.github/workflows/ci.yml` and `tla.yml`; the frozen zones are the owner's to accept or revert (§16.1). |
| R28 | **A blind mechanical extraction of the magic numbers broke the build, and was reverted.** The owner's decision D-015 is to fix the literals in the code and NOT to update the gate's baseline; 341 candidates sit in 299 lines of production Java over 167 files. `scripts/extract-magic-literals.py` was written to do the mechanical part under the gate's own rules (every candidate becomes a named constant whose definition lives on a `static final` line, which the gate excludes by its own rule). Applied to 167 files it produced 200 compilation errors, from three measured causes: the fraction was split off its integer (`1000.0` became `CONST_1000.0`); the guard that protects a literal inside a string checked the already-rewritten line, so `0123456789` became an illegal octal integer literal; and the constants were inserted where a type's body does not begin. The change was reverted with `git checkout` and the compile is green again. The decision stands; the METHOD changes: the extraction goes file by file, with the compiler and the package's tests as the gate for each file. | **OPEN — the literals are still to be extracted, file by file** | `scripts/extract-magic-literals.py` (kept: the per-file candidate list it computes is the work list); the reverted application is recorded in `wal/SESSION_WAL.md` (W42.9); the work list is `docs-v2/engineering/RECON-W42-BACKLOG.md` (W42.9). The gate is still RED — R27 stays open. |
| R26 | **The free-energy loss is a placeholder**: with `fantasyState = null` the sleep loss is always 0.0, and the "KL divergence" is a residual variance. It is written and never read. | **OPEN — unreachable today** | `ConsciousBrain:457-471`; `FreeEnergyLoss:13, :59-66, :68`. |

---

## 6. The security risks the mission names (§13.2)

| # | Risk | Status | Evidence |
|---|---|---|---|
| R27 | **Oversized files.** | **FIXED** in W41 | `KnowledgeImporter:40` (4 MiB package), `:49` (64 KiB excerpt); `KnowledgeImportLimitsTest` (4 tests). |
| R28 | **Prompt injection inside an imported document.** | **VERIFIED_NOT_A_DEFECT** — the knowledge store is not on the LLM path at all | `grep -rn "KnowledgeStore\|KnowledgeSnapshot" matrix-core/src/main/java/io/matrix/api/*.java matrix-core/src/main/java/io/matrix/rag/*.java` returns nothing. The system's own generated answers are written to the episodic memory under the tag `generated-dialogue` and are excluded from the generation context. **This verdict must be re-checked when W42.2 adds document ingestion**, because that is the moment a document's text first reaches the system. |
| R29 | **Path traversal.** | **VERIFIED_NOT_A_DEFECT** — the load endpoint takes a fixed workspace id, not a path | `LearningWorkbenchResource:137-145`. |
| R30 | **Malformed documents, zip bombs, malicious HTML, corrupted knowledge packages, invalid signatures, replay attacks, unauthorized access, resource exhaustion.** | **NOT YET ASSESSED** — there is no document ingestion to attack, and no signature scheme in the knowledge store | to be assessed in W42.2, when the ingestion pipeline exists. The mission's §13.2 list is the checklist. |
| R31 | **The API surface has no authentication.** All 36 endpoints are open, including the ones that mutate the store. | **`REQUIRES_OWNER_DECISION`** — the local-alpha posture may be single-user, but the mission's §10.3 says "local autonomy", not "no access control" | `LearningWorkbenchResource` — no `@RolesAllowed`, no auth filter. |

---

## 7. What the mission's §14 acceptance targets look like against this baseline

| Target | Baseline | Verdict |
|---|---|---|
| Application startup < 5 s | **1.571 s** | **MET** |
| Local operation, no mandatory external services | **verified**: no PG, Redis, Kafka, OTLP or LLM required | **MET** |
| Supported languages: Russian and English | English only; Russian **0/5** | **NOT MET** — the W42.3 work |
| Import: TXT, MD, HTML, PDF, EPUB | **no document import exists** | **NOT MET** — the W42.2 work |
| Persistence: 100 % recovery after forced termination | **verified live** (`kill -9` → restart → intact) | **MET** |
| Import integrity: atomic, no partial workspace | `importWorkspace` is transactional (`KnowledgeStore:512-529`) | **MET** |
| Provenance: 100 % of promoted claims traceable | `KnowledgeProvenanceTest`; the store rejects an evidence-less established claim (`:793-806`) | **MET** |
| Abstention: unsupported questions do not become fabricated answers | **0.400** | **NOT MET** — R2 |
| Conversation: ≥ 100 reproducible multilingual cases | 0 conversation cases; 6 in the benchmark | **NOT MET** — the W42.3 work |
| Typo handling: ≥ 95 % intent preservation | **3/4** benchmark items; no benchmark suite | **NOT MEASURABLE YET** — the benchmark must grow |
| Retrieval measured against BM25 | **measured**: overlap 0.593 | **MET (the measurement)** — the target itself is unspecified |
| Learning: independent pre/post/delayed/transfer | **implemented**, and the delayed phase **fails** | **PARTIAL** — the instrument is right, the result is a failure |
| Retention improved over a no-consolidation baseline | no consolidation exists | **NOT MET** — the W42.4 work |
| Goal planning: multi-step execution and recovery | recovery **MET**; planning **absent** | **PARTIAL** — the W42.1 work |
| Memory bounded under long-running sessions | `HierarchicalMemory` unbounded on the live path (R11) | **NOT MET** |
| Formal verification: no unexplained failed or vacuous critical properties | 12/12 exit 0; one property vacuous and **recorded** | **MET** — the vacuity is explained, not hidden |
| Mutation testing: all critical mutants killed or justified | 27 mutants, 25 killed, 2 survivors **both recorded with findings** | **MET** |
| Build: reproducible local build | `BUILD SUCCESSFUL` | **MET** |
| Documentation: no unresolved contradiction in the current normative documentation | **8 contradictions found** (§ `W42-RECONNAISSANCE.md` §4.1) | **NOT MET** — this wave fixes them |

---

## 8. The register's own summary

The W41 register ended with: *"The four open items are recorded, not
fixed."* This wave adds **twenty-six** and closes one.

That number is not a criticism of the last wave — it is the measurement the
mission asked for. The W41 wave was asked to audit one subsystem and did it
honestly. This wave was asked to audit the *whole* repository, and the
whole repository turns out to contain a large, carefully built, well-tested
island that no product reaches.

**The order the register implies, by expected measurable benefit:**

1. **R1** — the backup instruction (data loss, one line).
2. **R4, R5** — wire the contradiction and derivation recording (two
   one-liners that turn two built features from always-empty into live).
3. **R2, R3** — abstention quality and false premises (the epistemic
   defects; the benchmark exists to measure them).
4. **R10** — the persisted state machine (W42.1).
5. **R11** — the unbounded live memory map (one place).
6. **R8, R9** — the untested, English-only tokenizer (W42.3).
7. **R17, R18** — the interface must run the instrument that can fail, and
   the `KNOWLEDGE` stage must not be gated on the invalid one.
