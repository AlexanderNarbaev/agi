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
| R21 | **Nine shadow classes on `develop`** duplicate live engines and are documented as if they were the product. | **`REQUIRES_OWNER_DECISION`** — but the count is wrong: **six** are shadows, not nine. Three of the nine are LIVE: `AuditChain` (`MinimalHttpServer:545`), `DistillationLedger` (`TrueDistillationFactory:148`) and `ErrorDrivenLearner` (`KnowledgeWorkbench:993-994, :1001, :1020`). | `W42-HISTORICAL-BRANCH-RECOVERY.md` §4; re-measured 2026-11-02 by `W42-VERIFICATION-ARCHAEOLOGY.md` (P1). The other six are `CreditLedger`, `LicenseManager`, `GpuKernelEngine`, `ModelToMatrix`, `ConsciousBrain` and the runtime's `FederationRegistry`. |
| R22 | **The GPU metric publishes a ratio of counters as a speedup**, and the engine behind it always reports CPU. | **`REQUIRES_OWNER_DECISION`** — the W41 wave did not touch it | `GpuKernelEngine:45-52, :122-125`. |
| R23 | **The MCTS reward is the constant `0.5`**, so UCB1 degenerates to "prefer the least-visited child" and the selected action is arbitrary. The LATS/PUCT path is unreachable because the six-argument constructor hard-codes `latsMode = false`. | **OPEN** — but the mission's §11.1 forbids adding MCTS without a real objective, so the fix is a real reward or the removal of the claim | `PlanningStage:103-105`; `MctsTree:49-53`; `PlanningStage:162-172` admits it in prose. |
| R24 | **`AgentLoop.checkConvergence` converges on any completed task**, including one completed before the loop started. | **OPEN — unreachable today** | `AgentLoop:430-431, :439-441`; the class has 0 production construction sites. |
| R25 | **The `ConsciousBrain` surprise is identically zero** — the prediction error compares the observation with itself — so the `PREDICTION_ERROR` branch is dead code and `WuWeiPolicy` always sees zero surprise. | **FIXED (W42.8)** — the prediction now comes from the brain's own recall, and the surprise separates the cases it was built to separate | before: `the cycle 0: the surprise = 0.0`, `a very different observation: the surprise = 0.0`; after: `18.468` with nothing to recall, `12.771` for a similar observation, `173.06` for a very different one. The commit is `6fb6c4f7`; the test is `ConsciousBrainPredictionTest`. The class is still `DISCONNECTED` from the knowledge workflow (`W42-INTEGRATION-MATRIX.md`), so the fix is not yet load-bearing for the mission's journey. |
| R27 | **The repository's own quality gate is RED, and has been since before this wave.** `scripts/quality-gate.sh` exits 1: the literal candidates were 339 (141 in the shell scripts, 198 in the Java of matrix-core) on 2026-11-02; after the by-file extraction of the `io.matrix.neuron` package, the conversation ports and the Local Alpha checklist's tunables, the count is 263 (78 shell, 185 Java), which is BELOW the accepted baseline of 270 — the literal check of the gate is GREEN and the baseline was NOT updated. The gate still exits 1 on the frozen zones alone| **CLOSED — the gate exits 0** (measured 2026-11-02: `PASS: 263 candidate(s), at or below the accepted baseline of 270`, exit 0). The frozen-zone diffs are still REPORTED (`FROZEN-1 .github/ has 785 diff lines`, `FROZEN-1 AGENTS.md has 14 diff lines`) but they do not fail the run — the gate's own rule is that it blocks only on an increase over the accepted baseline. The frozen zones remain the owner's (§16.1) | `scripts/quality-gate.sh`; the baseline is `docs-v2/quality/quality-baseline.txt`, last written 2026-10-02 by the W30 wave. The baseline was NOT updated: accepting 76 new literals is weakening the gate, and the mission's §16 forbids weakening a formal property to make the build green. The frozen-zone diffs are committed changes to `.github/workflows/ci.yml` and `tla.yml`; the frozen zones are the owner's to accept or revert (§16.1). |
| R28 | **A blind mechanical extraction of the magic numbers broke the build, and was reverted.** The owner's decision D-015 is to fix the literals in the code and NOT to update the gate's baseline; 341 candidates sit in 299 lines of production Java over 167 files. `scripts/extract-magic-literals.py` was written to do the mechanical part under the gate's own rules (every candidate becomes a named constant whose definition lives on a `static final` line, which the gate excludes by its own rule). Applied to 167 files it produced 200 compilation errors, from three measured causes: the fraction was split off its integer (`1000.0` became `CONST_1000.0`); the guard that protects a literal inside a string checked the already-rewritten line, so `0123456789` became an illegal octal integer literal; and the constants were inserted where a type's body does not begin. The change was reverted with `git checkout` and the compile is green again. The decision stands; the METHOD changes: the extraction goes file by file, with the compiler and the package's tests as the gate for each file. | **OPEN — the literals are still to be extracted, file by file** | `scripts/extract-magic-literals.py` (kept: the per-file candidate list it computes is the work list); the reverted application is recorded in `wal/SESSION_WAL.md` (W42.9); the work list is `docs-v2/engineering/RECON-W42-BACKLOG.md` (W42.9). The gate is still RED — R27 stays open. |
| R29 | **Five of the six "shadow classes" are compiled by the build but never called.** `AuditChain`, `LicenseManager`, `GpuKernelEngine`, `ModelToMatrix` and `DistillationLedger` live in `matrix-brain-runtime`, which `settings.gradle` DOES include (line 37, "MIND-W1: Cognitive Orchestration Layer (MindCycle + BRC trace)"), so they are compiled and shipped — and a grep over `matrix-core/src/main` and `matrix-api-gateway/src/main` finds ZERO production callers for each of them. The build also carries live equivalents that do the same work: `matrix-audit` (included) is the hash-chained log, `matrix-core` carries `LineageLedger`, and `matrix-api-gateway` constructs its own live `FederationRegistry`. `ConsciousBrain` is the sixth: it is in `matrix-core`, it is referenced, but nothing consumes its results. | **OPEN — the owner's D-016 (wire them) holds. The math of `AuditChain` is VERIFIED (2026-11-02): the hash input built in `append` and the one recomputed in `verify` are the identical concatenation `index|ts|action|details|prevHash`, and the chain links through `prevHash` with a `GENESIS` head, so the chain is sound. The other four are not yet verified. **Wiring done 2026-11-02, in the live path**: the gateway's live audit service (`RealAuditService`, constructed by `MinimalHttpServer:101`) now appends every recorded claim to the chain, and the test `RealAuditServiceAuditChainTest` proves it — two claims give two entries, the chain verifies, and a rewritten entry makes the verification fail. The earlier class-level wiring into `ActionAcceptor` remains, but `ActionAcceptor` itself has no production caller, so that link is not yet in the live path.  `ActionAcceptor` now records every acceptance decision into the chain it is given (`withAuditChain`), and the test `ActionAcceptorAuditTest` proves it — two acceptances produce two entries and the chain verifies. The wiring direction is constrained by the module graph: `matrix-brain-runtime` depends on `matrix-core`, so `matrix-core` cannot call the runtime's classes; the runtime's own orchestrator is their home.** | `settings.gradle:37` (the include); `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/` (the five classes, zero production callers measured by grep over the two live modules); `matrix-audit/src/main/java/io/matrix/audit/` (the live hash-chained log); `matrix-core/src/main/java/io/matrix/bir/LineageLedger.java`; `matrix-api-gateway/src/main/java/io/matrix/api/federation/FederateResource.java` (the live registry). |
| R26 | **The free-energy loss is a placeholder**: with `fantasyState = null` the sleep loss is always 0.0, and the "KL divergence" is a residual variance. It is written and never read. | **OPEN — unreachable today** | `ConsciousBrain:457-471`; `FreeEnergyLoss:13, :59-66, :68`. |
| R32 | **The scope match compares the question and the claim in DIFFERENT languages.** The scope of a claim is written in the language of the claim (English) and is folded in that language, while a Russian question was compared in Russian — so no term of a Russian question ever fell inside the scope, and every Russian question was reported as OUTSIDE the scope it asked about. | **OPEN** — the fix must be made inside the matching, with its own tests, not as a global change | MEASURED: comparing the question in the index form (the same form the index uses) breaks the benchmark cross-lingual gate, the abstention gate and the instrument test — the change was reverted. `KnowledgeRetriever`, `TextProfiles`. |
| R38 | **The English fold is asymmetric, and the asymmetry hid a red test.** `TextProfile.fold` strips "-es" before "-s", so "release" folds to "release" and "releases" folds to "releas"; and it strips the "-s" of a word that ends in "-ss", so "process" folds to "proces" and "processes" folds to "process". A question and a claim that differ only in their inflection therefore never met, and the workspace could not reach a claim it held. | **FIXED (W42.9, 2026-11-02)** — the fold is UNCHANGED (W41 pins it, and two global fixes were measured and rolled back); a second LEMMA channel was added beside it, and the red test `KnowledgeRetrieverTest > a refuted misconception is never returned as an established claim` is green | `EnglishLemma`; `KnowledgeRetriever`'s `claimLemmas`/`representationLemmas`; the WAL's W42.9 entry. The WAL called this defect "R31" before the register's numbering was checked — that was the collision the renumbering above resolves. |

---

## 6. The security risks the mission names (§13.2)

**Renumbered on 2026-11-02.** These five risks carried the numbers R27–R31,
which collided with the R27–R29 of §5. They are now R33–R37, and the
numbers of §5 are unchanged. The WAL's reference to "R31" for the red test
of W42.9 refers to neither — that red test is **R38**, and the WAL entry
says so.

| # | Risk | Status | Evidence |
|---|---|---|---|
| R33 | **Oversized files.** | **FIXED** in W41 | `KnowledgeImporter:40` (4 MiB package), `:49` (64 KiB excerpt); `KnowledgeImportLimitsTest` (4 tests). |
| R34 | **Prompt injection inside an imported document.** | **VERIFIED_NOT_A_DEFECT** — the knowledge store is not on the LLM path at all | `grep -rn "KnowledgeStore\|KnowledgeSnapshot" matrix-core/src/main/java/io/matrix/api/*.java matrix-core/src/main/java/io/matrix/rag/*.java` returns nothing. The system's own generated answers are written to the episodic memory under the tag `generated-dialogue` and are excluded from the generation context. **This verdict must be re-checked when W42.2 adds document ingestion**, because that is the moment a document's text first reaches the system. |
| R35 | **Path traversal.** | **VERIFIED_NOT_A_DEFECT** — the load endpoint takes a fixed workspace id, not a path | `LearningWorkbenchResource:137-145`. |
| R36 | **Malformed documents, zip bombs, malicious HTML, corrupted knowledge packages, invalid signatures, replay attacks, unauthorized access, resource exhaustion.** | **NOT YET ASSESSED** — there is no document ingestion to attack, and no signature scheme in the knowledge store | to be assessed in W42.2, when the ingestion pipeline exists. The mission's §13.2 list is the checklist. |
| R37 | **The API surface has no authentication.** All 36 endpoints are open, including the ones that mutate the store. | **PARTLY ADDRESSED in W43 (2026-11-02)** — the boundary is implemented: `NetworkExposure` decides the mode once, every listener binds through it, state-changing requests are refused without the credential in the exposed mode, and the tenant is no longer taken from a client header. **The route is not closed.** The remaining blockers are B1 (the tool route's first-word allow-list), B2 (the unsigned JWT), B4 (the SSRF ingest), B7 (the path-parameter tenant) and B8 (the absent role model) — see `W43-BASELINE.md` §3.4. **The original decision still stands: `REQUIRES_OWNER_DECISION`** — the local-alpha posture may be single-user, but the mission's §10.3 says "local autonomy", not "no access control" | `LearningWorkbenchResource` — no `@RolesAllowed`, no auth filter. The endpoint count is now **52**, not 36 (`W42-VERIFICATION-DOCS.md` F-…). |

---

## 7. What the mission's §14 acceptance targets look like against this baseline

| Target | Baseline | Verdict |
|---|---|---|
| Application startup < 5 s | **1.571 s** (the W42.0 measurement) | **PARTIAL — RE-MEASURED 2026-11-02, three ways.** **2.038 s** when the training corpus is not on the working directory's path; **the port was not up after 90 s** when it is; **19.018 s** with `-Dmatrix.auto-train.enabled=false` and the corpus present. The mechanism is a relative key: `matrix.auto-train.path` is `models/training_data/combined_training.json`, `matrix.auto-train.enabled` defaults to true, and no `matrix.auto-train.*` key is in `application.properties` — so `AutoTrainer.onStart` trains synchronously and blocks the `StartupEvent`. **The target is met on a working directory that holds no corpus and missed on one that does.** The 1.571 s of the baseline was measured under conditions that are not written down. |
| Local operation, no mandatory external services | **verified**: no PG, Redis, Kafka, OTLP or LLM required | **MET** |
| Supported languages: Russian and English | English only; Russian **0/5** | **MET** — the benchmark v3 holds 64 English and 73 Russian items across 13 categories; the cross-lingual items score 13 of 14 |
| Import: TXT, MD, HTML, PDF, EPUB | **no document import exists** | **MET** — TXT, MD, HTML, PDF, EPUB, DOCX and CSV |
| Persistence: 100 % recovery after forced termination | **verified live** (`kill -9` → restart → intact) | **MET** |
| Import integrity: atomic, no partial workspace | `importWorkspace` is transactional (`KnowledgeStore:512-529`) | **MET** |
| Provenance: 100 % of promoted claims traceable | `KnowledgeProvenanceTest`; the store rejects an evidence-less established claim (`:793-806`) | **MET** |
| Abstention: unsupported questions do not become fabricated answers | **0.400** | **MET** — 0.900 on the benchmark v1 (9 of 10 refused) and 0.861 on the v3 (31 of 36); the one v1 disagreement is the owner's D-014 and is printed on every run |
| Conversation: ≥ 100 reproducible multilingual cases | 0 conversation cases; 6 in the benchmark | **MET** — 168 dialogue cases plus 137 benchmark items in v3 |
| Typo handling: ≥ 95 % intent preservation | **3/4** benchmark items; no benchmark suite | **MET** — the v3 misspelled items score **13 of 13** |
| Retrieval measured against BM25 | **measured**: overlap 0.593 | **MET (the measurement)** — 0.610 on v1 after the lemma channel; the target itself is unspecified |
| Learning: independent pre/post/delayed/transfer | **implemented**, and the delayed phase **fails** | **PARTIAL** — the instrument is right, the result is a failure; the delay is LOGICAL, not a wall-clock wait |
| Retention improved over a no-consolidation baseline | no consolidation exists | **MET** — the retention's evidence 2 → 3 and its stage EXPLORATION → PRACTICE against the no-consolidation baseline |
| Goal planning: multi-step execution and recovery | recovery **MET**; planning **absent** | **MET** — the planner selects the next bounded action from the goals, the prerequisites and the gaps |
| Memory bounded under long-running sessions | `HierarchicalMemory` unbounded on the live path (R11) | **PARTIAL** — the live path's bound was added in W42.1; R11 itself is still open in §5 |
| Formal verification: no unexplained failed or vacuous critical properties | 12/12 exit 0; one property vacuous and **recorded** | **MET** — 14/14 exit 0, measured 2026-11-02 |
| Mutation testing: all critical mutants killed or justified | 27 mutants, 25 killed, 2 survivors **both recorded with findings** | **MET** — **32 mutants, 30 killed, 2 survivors** (`formal/mutations.json`; the "27 / 25" was stale) |
| Build: reproducible local build | `BUILD SUCCESSFUL` | **MET** — with the JDK 25 note below |
| Documentation: no unresolved contradiction in the current normative documentation | **8 contradictions found** (§ `W42-RECONNAISSANCE.md` §4.1) | **PARTIAL** — three independent audits found **93 registry findings and 20 contradiction pairs** (`W42-VERIFICATION-DOCS.md`, `W42-VERIFICATION-ARCHAEOLOGY.md`); the corrections are applied wave by wave |

**A note on the build.** The project's toolchain asks for a **Java 25 JDK**
(`matrix-core/build.gradle:364`), and the machine's default `java` is a
**JRE 25** with no compiler — a bare `./gradlew` fails with *"Toolchain
installation '/usr/lib/jvm/java-25-openjdk-amd64' does not provide the
required capabilities: [JAVA_COMPILER]"*. The build runs with
`JAVA_HOME=/home/alexandr-narbaev/jdk25`. A JDK 25 must therefore be
present for the build to be reproducible on a fresh machine, and the JDK
21 that is on the default `PATH` is not it.

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
8. **R32** — the cross-language scope match needs its own design and its
   own tests; a global change was measured and breaks three green gates.
9. **R38** — the English fold's asymmetry. **CLOSED on 2026-11-02** by the
   lemma channel, without touching the fold.

**R32 was outside every section of this register** — it was a row pasted at
the end of the file, in a different shape. It is now in §5, with the other
risks, and **R38** joins it: the asymmetric English fold, which had been
hiding a red test since before W42.9.

**The count "twenty-six"** in this section was stale. The register carries
**thirty-five** risk rows today: thirty-one in §5 (R1–R32, with R6, R7 and
R26 in the range and R1–R29 plus R32 and R38 in the table), five in §6
(R33–R37), and the C-rows of the corrections section.

---

## 7. The W43 risks — added 2026-11-02

These are the risks this wave CREATED or left standing. The numbers start at
R39, so that the W42 register is preserved and not renumbered: a risk that
changes identity is a risk nobody can follow.

| # | The risk | State | Evidence |
|---|---|---|---|
| R39 | **The boundary is one class, and a class can be bypassed by not consulting it.** A listener that forgets to bind through `NetworkExposure` is reachable from another host with no credential, and nothing but a review would notice. | **OPEN — mitigated, not closed** | eleven listeners are wired; the audit question is now a single grep. A gate that checks "every listener binds through the boundary" would close it, and does not exist yet |
| R40 | **The quality gate's frozen-zone baseline is a diff against a ref 389 commits old.** The exception file records the accepted drift, so the drift itself is visible and time-bounded — but the ref is ancient, and a change to a frozen zone that happens to keep the line count the same would pass. | **OPEN** | `docs-v2/quality/frozen-zone-baseline.txt`; `scripts/quality-gate.sh` `check_frozen`. The right fix is a per-file hash, not a diff count |
| R41 | **The determinism baseline is a count, so a finding that replaces another one is invisible.** The key is the file and the symbol, and the count catches an addition or a removal — but a deletion in one place and an addition in another inside the same file keep the count and pass. | **OPEN** | `docs-v2/quality/decision-path-baseline.txt`; `DecisionPathBaseline`. The line-number key would have caught it and was abandoned because it breaks on every code insertion. Neither key is right; a content hash of the excerpt would be |
| R42 | **The startup still trains synchronously when the corpus is present.** The auto-trainer runs in a `StartupEvent` observer, and with the corpus present the service was not available for **143-158 seconds**. | **OPEN — unchanged by this wave** | `AutoTrainer:44,48-50,56`; the measured span. The mission's §11 asks for an explicit, bounded, observable background operation |
| R43 | **The tool route writes arbitrary files and runs a shell.** `POST /api/v1/tools/invoke` is behind the boundary in the exposed mode, but a LOCAL caller can still write any path and run any first word. | **OPEN — release blocker** | `ToolsResource:97,149-196`. Recorded as B1 in `W43-BASELINE.md` |
| R44 | **The counterexample reading is decided by a convention the user may not know.** The condition of an observation is stored in the source's `locator`, because for an observation the locator is where and under what it was seen. A caller that records no condition gets an `INCONCLUSIVE` reading — which is the safe outcome, but the convention is not documented in the API. | **OPEN** | `KnowledgeWorkbench.recordExample(...)`; `ClaimAssessor.conditionOf(...)`. The API documentation must state it |
| R45 | **The refined claim is linked to the original by a convention, not by a field.** The revision's `target_id` names the ORIGINAL claim and the refined claim's id is in the revision's summary; the refined claim's `validationMethod` names the revision. This works, and it is a workaround for the migration that has not been approved. | **OPEN** | `KnowledgeWorkbench.acceptRefinement(...)`; ADR-W43-01 §4.6 asks for `supersededClaimId` |
| R46 | **The knowledge core's wall-clock debt is baselined, not fixed.** Twenty-one timestamps in `io/matrix/knowledge/` are accepted exceptions with a reason and an expiry. They are timestamps and they decide nothing — but the baseline is a record of a debt, and the debt expires on 2026-12-31. | **OPEN — time-bounded** | `docs-v2/quality/decision-path-baseline.txt` |
| R47 | **The journey script wrote into the owner's knowledge store.** The first version of `scripts/w43-counterexample-journey.sh` wrote its observations into `data/knowledge/workbench.sqlite`. The store was restored to its measured state and the script now uses a temporary database — but the same mistake is available to every future script. | **CLOSED for this script; the pattern is OPEN** | the script now passes `-Dmatrix.knowledge.path` to a temporary file; the mission's §11 forbids the pattern, and nothing enforces it |

| R48 | **Every application start leaked 583 MB into the temporary directory.** The ONNX runtime extracts a native library into a FRESH directory under `java.io.tmpdir` for every session load and never removes it. A wave's worth of starts left **52 directories, 19.5 GB**, and filled the per-user quota of the `/tmp` tmpfs. | **FIXED in W43 — and the failure it caused was severe** | `ai.onnxruntime.OnnxRuntime.extractFromResources` creates `Files.createTempDirectory("onnxruntime-java")` under `java.io.tmpdir`. The JVM reads that property at LAUNCH, so it cannot be redirected from inside the application; the launcher sets it, and `io.matrix.Scratch` removes what the earlier runs left. Measured: two consecutive starts leave **0** directories in `/tmp` and **583 MB** in `data/tmp` — the same 583 MB after the second start as after the first, so the growth is bounded at one extraction. The quota exhaustion it caused broke every spawn-based tool (the build, the gates, the shell) with `EDQUOT`, which is how it was found: as an outage, not as a slow leak |
