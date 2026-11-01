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
| W42.2 — Universal knowledge acquisition | not started | the next wave; the mission's *acquire* verb depends on it |
| W42.2 — Universal knowledge acquisition | not started | |
| W42.3 — Dialog Intelligence | not started | |
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

**W42.2 — universal knowledge acquisition.** The mission's first practical
objective needs it: *acquire a new concept from an actual document*. There
is no ingestion of any kind today — the only import is a hand-curated
package JSON, and `KnowledgeImporter.importPack` has no production caller.

Concretely, in order:

1. The source record — filename, id, content hash, format, language, the
   author and the publication date **when known** and never invented, the
   import date, the location, the extracted structure, the page references,
   the extraction quality and the processing history (§5.2).
2. The pipeline `SOURCE → EXTRACTION → NORMALIZATION → SEGMENTATION →
   CONCEPT DISCOVERY → CLAIM EXTRACTION → EVIDENCE LINKING → VALIDATION →
   KNOWLEDGE INTEGRATION` (§5.3), with established libraries for the
   parsing (§5.1) — never a hand-written PDF or EPUB parser.
3. The promotion state machine `RAW → EXTRACTED → CANDIDATE → SUPPORTED →
   VERIFIED → DISPUTED → SUPERSEDED → INVALIDATED` (§5.4), and the rule that
   ten documents copying one source are not ten confirmations (§5.4).
4. The corpus manager (§5.5).
5. The §13.2 security list, assessed against the ingestion that now exists —
   this is the first moment a document's text reaches the system.

**The dependency is one-way:** W42.2 does not need W42.3. And the measure
is the mission's own: a concept acquired from a real document, explained
from the stored knowledge, with the provenance of every claim pointing at
the document it came from.

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
