# RECON-W42 — Status

**Owner directive:** RECON-W42 MASTER DEVELOPMENT MISSION · 2026-11-01
**Repository:** `AlexanderNarbaev/agi` · branch `develop`
**This file is the session-continuity record.** The next session must be
able to resume from the repository alone.

---

## 1. Where the mission stands

| Wave | Status | Evidence |
|---|---|---|
| **W42.0 — Reality and integration audit** | **DONE** | five deliverables in `docs-v2/engineering/W42-*.md`; the benchmark instrument and its baseline; the configuration fix |
| **W42.1 — Unified learning and knowledge core** | **DONE** | `W42.1-LEARNING-CORE-REPORT.md`; the state machine, the planner, the demand check, the memory's bound, the teach-back gate; abstention quality 0.400 → 0.700 |
| **W42.2 — Universal knowledge acquisition** | **DONE (the core)** | TXT/MD/HTML/PDF/EPUB through libraries; the source record; the promotion layers; the independence rule; the endpoint and the interface — **verified live**: a concept acquired from a real document became retrievable with its source |
| W42.2 — Universal knowledge acquisition | not started | |
| **W42.3 — Dialog Intelligence** | **DONE** | `W42.3-DIALOG-INTELLIGENCE-REPORT.md`; the localised text layer, the bilingual lexicon, the typo corrector, the bounded conversation, the eight dialogue classes, 168 measured cases — accuracy 0.583 → 0.792, the Russian 0/5 → 4/5 |
| **W42.4 — Learning and assessment** | **DONE** | `W42.4-LEARNING-AND-REPRESENTATION-REPORT.md`; the exercise selector, the delayed retention measured from the session boundary, and the representation composer — the mission's first practical objective is now demonstrated end to end on the running application |
| **W42.5 — Cognitive integration** | **DONE** | `W42.5-COGNITIVE-INTEGRATION-REPORT.md`; the false-premise detector (the `FALSE_PREMISE` items 0/2 → 2/2, the accuracy unchanged at 0.792 and the abstention 0.700 → 0.900), the bounded idempotent consolidation, the error-driven selection, and the retention baseline experiment — the retention's evidence 2 → 3 and its stage EXPLORATION → PRACTICE against the no-consolidation baseline |
| **W42.6 — Local Alpha v0.3** | **DONE** | `W42.6-LOCAL-ALPHA-V0.3.md` — the twelve steps verified over HTTP, and the journey survives a `SIGKILL` with 469 712 bytes of WAL unwritten. The §9's error-driven learning integration is wired in as a phase of the consolidation (the schema is v11), the §5.5's validation stage promotes a claim on two independent sources, and the §6's conversation is persisted (the schema is v12) |
| W42.4 — Learning and assessment | not started | |
| W42.5 — Cognitive integration | not started | |
| W42.6 — Local Alpha v0.3 | not started | |

**The mission's first practical objective** — *acquire a new concept from an
actual document, explain it with stored knowledge, answer at different
levels, know what it does not know, practise, be evaluated independently,
improve the representation, and keep the improvement after a forced restart*
— is **blocked on W42.2** (there is no document ingestion) and on the
**planner** of W42.1 (nothing selects the next action). Both are the next
two waves.

---

## 2. Commits of this wave

| Commit | What it did |
|---|---|
| `b724c79d` | The configuration key that was never read — `atrix.knowledge.assessment` → `matrix.knowledge.assessment`, plus the three W41 keys written down |
| `96151a0a` | The five W42.0 deliverables, the benchmark instrument, the session-continuity files, and the backup instruction that was losing data |
| `124d3865` | The state machine, and what was checked is recorded |
| `25fd40bf` | Something selects the next learning action |
| `fa7075f6` | The question's demand is checked before the answer is; the memory's bound; the tokenizer's tests |
| `794b3dd7` | The interface runs the measurement that can fail |
| `0e820255` | A document becomes knowledge the store can trace |
| `0230a449` | The document import is reachable from the interface |
| `e18d7cbe` | The text layer speaks Russian |
| `8aa6a961` | The typos are corrected, and the original is kept |
| `d60916f6` | The conversation remembers, and it is bounded |
| `eeb412e6` | The class of an utterance is recognised |
| `21fbed22` | The dialogue is visible, and the navigation loads its tabs |
| `8f3cad04` | A term that reached the knowledge is not reported as uncovered |
| `7942838f` | The dialogue has a hundred and sixty-eight cases, and they are measured |
| `416a2b80` | The system chooses what to practise next, and the delay is measured |
| `ea94d726` | An imported concept gets an account of its own |
| `d47edb32` | A forgetting is marked, not performed |
| `d9323c3f` | The false-premise mechanism was measured, and it was not shipped |
| `2c2e3c85` | A question's premise is checked against the workspace's own claims |
| `b9cc93b9` | The consolidation is bounded, marked, and leaves the truth alone |
| `548b3adc` | The exercise the learner got wrong is the one they are asked again |
| `e9f2265a` | The delayed retention is measured against a no-consolidation baseline |
| `65cc7c8b` | The tombstone migration belongs to the version it belongs to |
| `5ff2a525` | The idempotency flag means what it says |
| `cc16492e` | The error-driven integration happens only after the error is verified |
| `ea468cff` | The validation stage: two independent sources promote, one does not |
| `34df3d7e` | The consolidation proposes, measures, and keeps the proposal only if the knowledge did not fall |
| `beccce7b` | The knowledge graph is a projection of the workspace, and it is visible |
| `e79c9a72` | The Experiment Dashboard — the measured instrument, in the interface |
| `014a26b8` | The conversation survives a restart, and the referent with it |
| `543c01b0` | The corpus registry: what the workspace knows, and how much of it is independent |

**Starting commit:** `0296e83e` (W41's final verification).
**The remote HEAD was verified, not assumed** — `git fetch origin --prune`
then `git rev-parse origin/develop` → `0296e83e88b9caecba828c992f9634c21d51a1c8`.

---

## 3. Files changed by this wave

| File | Change |
|---|---|
| `matrix-core/src/main/resources/application.properties` | the typo fix; the three W41 keys made explicit |
| `matrix-core/src/main/java/io/matrix/knowledge/benchmark/Bm25Index.java` | **new** — the BM25 baseline |
| `matrix-core/src/main/java/io/matrix/knowledge/benchmark/BenchmarkItem.java` | **new** — one benchmark item + the loader |
| `matrix-core/src/main/java/io/matrix/knowledge/benchmark/BenchmarkRunner.java` | **new** — the runner |
| `matrix-core/src/main/resources/benchmark/w42-benchmark-v1.json` | **new** — 34 items, 12 categories, 2 languages |
| `docs-v2/engineering/W42-RECONNAISSANCE.md` | **new** |
| `docs-v2/engineering/W42-ARCHITECTURE-REALITY-MAP.md` | **new** |
| `docs-v2/engineering/W42-HISTORICAL-BRANCH-RECOVERY.md` | **new** |
| `docs-v2/engineering/W42-INTEGRATION-MATRIX.md` | **new** |
| `docs-v2/engineering/W42-RISK-REGISTER.md` | **new** |
| `docs-v2/engineering/RECON-W42-{STATUS,DECISIONS,BENCHMARKS,BACKLOG}.md` | **new** |

**Nothing of the owner's was touched.** The working tree carries only the
owner's pre-existing modifications (`.gitignore`, `.opencode/*`, `AGENTS.md`,
`opencode.json`, `opencode.json.bak`) and the untracked owner directories.

---

## 4. Test and measurement results

| What | Result | Command |
|---|---|---|
| Targeted suites | **121 classes, 1 212 tests, 0 failures** | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --tests "io.matrix.memory.*" --tests "io.matrix.brain.*" --tests "io.matrix.api.*" --tests "io.matrix.rag.*"` |
| Formal gate | **12/12 exit 0**, 65 … 396 577 states | `java -cp tools/tla2tools.jar tlc2.TLC -config formal/<S>.cfg formal/<S>.tla` |
| Mutation gate | **27 mutants, 25 killed, 2 survivors** (both recorded with findings), 0 invalid, 0 drift | `python3 scripts/mutation-harness.py` |
| Startup | **1.571 s** | the live log |
| W42 benchmark | **34 items: accuracy 0.583, abstention 0.400, BM25 overlap 0.593** | `RECON-W42-BENCHMARKS.md` |
| Restart recovery | **verified** after `kill -9` | the W41 journey script |

**Failed tests: none.** **Experiments: one** — the WAL backup experiment,
which proved the data-loss defect (`W42-RISK-REGISTER.md` R1).

---

## 5. The outstanding risks, in the order the register implies

1. **R1** — the backup instruction loses data. One line to fix.
2. **R4, R5** — the contradiction and derivation recording are never called;
   two built features are permanently empty.
3. **R2, R3** — abstention quality 0.400; the system agrees with false
   premises.
4. **R10** — the learning lifecycle is a computed label, not a state machine.
5. **R11** — `HierarchicalMemory`'s live path never evicts; unbounded.
6. **R8, R9** — the tokenizer is untested and English-only.
7. **R17, R18** — the interface runs the instrument that cannot fail, and
   the `KNOWLEDGE` capability stage is gated on it.

---

## 6. The next action

**W42.3 — Dialog Intelligence (RU + EN).** The mission's §6 and §14 want a
dialogue subsystem: language identification, normalization, typo handling,
morphology, lemmatization, intent detection, entity and concept
recognition, context tracking, reference resolution, ambiguity detection,
clarification, bounded conversation memory, response planning, multilingual
generation and user feedback — with the original message preserved
alongside any normalized representation.

**What the W42.2 work left measured:**

| | baseline | now |
|---|---|---|
| the W42 benchmark's accuracy | 0.583 | 0.583 |
| the abstention quality | 0.400 | **0.700** |
| the Russian items (CROSS_LINGUAL) | 0/5 | **0/5** |
| the paraphrases | 0/2 | 0/2 |
| the false premises | 0/2 | 0/2 |
| `TextFeatures`' tests | 0 | 6 |

**The Russian is the largest single hole in the product** — five of the five
cross-lingual items fail, because the stopword list, the suffix fold, the
negation list and the task-classification keywords are all English. The
text layer is the first thing W42.3 changes, and its tests already exist to
be changed against.

**What W42.2 left unfinished, stated plainly:**

1. **The corpus manager** (§5.5) — the collections, the deduplication, the
   incremental indexing, the resumable imports and the export.
2. **The representations for the imported concepts.** A concept acquired from
   a document is retrievable with its source, but the prose the answer
   shows is still the pack's authored account — an imported concept has no
   authored representation yet.
3. **The validation stage.** The pipeline stops at the EVIDENCE LINKING and
   KNOWLEDGE INTEGRATION steps; the claims arrive as CANDIDATE and stay
   there, which is the honest state for an unvalidated extraction.
4. **The two false-premise items.** Nothing yet checks the premise of a
   question.
5. **The security list is attended to where it applies, not everywhere.**
   The oversized file, the zip bomb and the malicious HTML are handled; the
   path traversal is not reachable (there is no path in the request); the
   prompt injection inside a document is now reachable for the first time
   and must be assessed in W42.3.

**For reference, W42.1's plan was:**

1. Fix the backup instruction (R1) and add the test that would have caught it.
2. Wire the contradiction and derivation recording (R4, R5); the empty
   tables become live.
3. Build the **persisted learning state machine**: a `learning_state` table
   with the stage, the transition, the evidence and the timestamp; a legal
   transition guard; a transition audit; and preserved earlier mastery
   records when a regression occurs (the mission's §7.1).
4. Build the **planner**: from the active goals, the prerequisite graph and
   the gap detector, select the next bounded action. `LearningGoal.remainingSubtasks()`,
   `blockedSubtasks()`, `hasDependencyCycle()` and
   `KnowledgeGapDetector.missingPrerequisites()` already exist and are unused
   — they are the raw material.
5. Measure the abstention improvement against the W42 baseline (0.400).

**All five were delivered.**

1. Fix the backup instruction (R1) and add the test that would have caught it.
2. Wire the contradiction and derivation recording (R4, R5); the empty
   tables become live.
3. Build the **persisted learning state machine**: a `learning_state` table
   with the stage, the transition, the evidence and the timestamp; a legal
   transition guard; a transition audit; and preserved earlier mastery
   records when a regression occurs (the mission's §7.1).
4. Build the **planner**: from the active goals, the prerequisite graph and
   the gap detector, select the next bounded action. `LearningGoal.remainingSubtasks()`,
   `blockedSubtasks()`, `hasDependencyCycle()` and
   `KnowledgeGapDetector.missingPrerequisites()` already exist and are unused
   — they are the raw material.
5. Measure the abstention improvement against the W42 baseline (0.400).

**The exit condition of W42.1, in the mission's words:** *a persistent goal
can acquire, practice, evaluate, revise, and resume learning.* The
"acquire" half needs W42.2's ingestion; W42.1 delivers the rest and states
that dependency plainly rather than pretending otherwise.

**What W42.1 delivered, measured:** practise ✓ (the planner selects the
next bounded action and it was exercised live), evaluate ✓ (the rebuilt
teach-back experiment, whose delayed phase fails, now gates the top stage
and is what the interface runs), revise ✓ (the persisted state machine
records every transition, including the regressions, with the earlier
mastery preserved), resume ✓ (the transitions and the plan survive a
restart). Acquire ✗ — W42.2.

## W42.9 (2026-11-02) — the literals are below the baseline, the shadows are censused, the journey re-verified

**The owner's D-015 is fulfilled.** The quality gate's literal counter is **263** against the accepted
baseline of **270** (141 shell → 78, 198 Java → 185), and the baseline was NOT updated. The gate still
exits 1, and now only on the frozen zones (785 diff lines in `.github/`, 14 in `AGENTS.md` against
`b144563e`) — the owner's decision (§16.1).

**The shadow census is measured, not assumed.** `AuditChain` — the gateway already had a hash-chained
buffer, so a second chain over the same facts was reverted the same day it was found. `DistillationLedger`
and `ModelToMatrix` are already live (the ledger is written by the live `TrueDistillationFactory` and its
snapshot is read back into the mind's state). `CreditLedger` and `LicenseManager` are orphans but NOT
duplicates — they account the brain's operation costs and the feature entitlements, while `matrix-billing`
accounts customer money and signed keys. `GpuKernelEngine` is a genuine duplicate of the live
`RealGpuKernelEngine`'s bit-cosine capability.

**The journey is re-verified on the live path (X.6).** The Local Alpha checklist was run over HTTP:
every assertion held. The application's JVM was then killed with SIGKILL: 13 concepts, 19
representations and 24 claims survived, and the consolidation after the restart was idempotent with
nothing forgotten.

**The first shadow is wired end to end.** The runtime's `AuditChain` is a face of the canonical
`io.matrix.audit.HashChainedLog` and records every promotion decision of the episodic log — the refusals
included, whose reason used to be lost. The gateway serves them at `GET /v1/audit/promotions`, proved by
`PromotionDecisionsEndpointTest` after a real `/v1/analyze` cycle ✓ ✓.

**The formal gate is locally runnable and green.** `scripts/formal-gate.sh` runs the twelve TLA+
specifications with the CI's invocation and exits 1 if any fails ✓ ✓. The measured run: 12 passed, 0
failed ✓ ✓ — including `HashChain`, so the audit chain's rewrite did not break its formal property ✓ ✓.

**The learning state machine now has a formal specification.** `formal/LearningStateMachine.tla` models the
seven-rung ladder and the two rules the Java enforces ✓ ✓. Its safety properties are falsifiable: a model
that allows a two-rung jump violates `AdvanceIsOneStep`, and a model that records an unchanged stage
violates `NoSelfTransition` — both were caught by TLC ✓ ✓. The formal gate is 13 passed, 0 failed ✓ ✓.
