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
| **W42.3 — Dialog Intelligence** | **DONE** | `W42.3-DIALOG-INTELLIGENCE-REPORT.md`; the localised text layer, the bilingual lexicon, the typo corrector, the bounded conversation, the eight dialogue classes, 168 measured cases — accuracy 0.583 → 0.792, the Russian 0/5 → 4/5 |
| **W42.4 — Learning and assessment** | **DONE** | `W42.4-LEARNING-AND-REPRESENTATION-REPORT.md`; the exercise selector, the delayed retention measured from the session boundary, and the representation composer — the mission's first practical objective is now demonstrated end to end on the running application |
| **W42.5 — Cognitive integration** | **DONE** | `W42.5-COGNITIVE-INTEGRATION-REPORT.md`; the false-premise detector (the `FALSE_PREMISE` items 0/2 → 2/2, the accuracy unchanged at 0.792 and the abstention 0.700 → 0.900), the bounded idempotent consolidation, the error-driven selection, and the retention baseline experiment — the retention's evidence 2 → 3 and its stage EXPLORATION → PRACTICE against the no-consolidation baseline |
| **W42.6 — Local Alpha v0.3** | **DONE** | `W42.6-LOCAL-ALPHA-V0.3.md` — the twelve steps verified over HTTP, and the journey survives a `SIGKILL` with 469 712 bytes of WAL unwritten. The §9's error-driven learning integration is wired in as a phase of the consolidation (the schema is v11), the §5.5's validation stage promotes a claim on two independent sources, and the §6's conversation is persisted (the schema is v12) |
| **W42.7 — the open items** | **DONE** | `RECON-W42-BACKLOG.md` §W42.7; the corpus registry's versions, the Source Browser, the Knowledge Graph, the Experiment Dashboard, the Learning Sessions, the persisted conversation, the validation stage, the authored representations, the error-driven decay, and the consolidation that proposes and measures |
| **W42.9 — the fold, the Russian premise, the audit** | **DONE** | the section at the end of this file: the asymmetric English fold, the Russian false premise, the lemma channel, the scoped negation, and three independent audits of the W42 documentation |


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

**The HEAD when the W42.9 wave began was `6ea07101`**, verified the same way
(`git ls-remote origin develop` → `6ea0710197e8440b0ea9c2ed035d368be769104a`,
equal to the local `develop`). It is **176 commits** after `0296e83e`, and
nine of them landed on the night of 2026-11-01 — which is why the W42.0
reports, both anchored to `0296e83e`, are stale in a dozen places
(`W42-VERIFICATION-ARCHAEOLOGY.md`).

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
| Targeted suites | **121 classes, 1 212 tests, 0 failures** (the W42.0 baseline) | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --tests "io.matrix.memory.*" --tests "io.matrix.brain.*" --tests "io.matrix.api.*" --tests "io.matrix.rag.*"` |
| Formal gate | **14/14 exit 0** (measured 2026-11-02; the W42.0 baseline was 12) | `scripts/formal-gate.sh` |
| Mutation gate | **32 mutants, 30 killed, 2 survivors** (both recorded with findings), 0 invalid, 0 drift — `formal/mutations.json` and `MUTATION-REPORT.md`; the "27 / 25 / 2" that this file carried earlier was stale | `python3 scripts/mutation-harness.py` |
| Startup | **1.571 s** (the W42.0 measurement) — see the W42.9 section for the re-measurement | the live log |
| W42 benchmark | **34 items: accuracy 0.583, abstention 0.400, BM25 overlap 0.593** (the W42.0 baseline) | `RECON-W42-BENCHMARKS.md` |
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

**The promotion machine has a formal specification, and X.2 is closed.** `formal/PromotionStage.tla` models
the eight layers, the layer graph and the policy's target requirements ✓ ✓, and holds §5.4's line: a claim
cannot reach VERIFIED on one source copied many times ✓ ✓. Three mutated models were caught by TLC ✓ ✓.
The formal gate is 14 passed, 0 failed ✓ ✓, and the learning machine, the promotion machine and the BRC
step all have specifications ✓ ✓.

### W42.9 (continued, 2026-11-02) — the fold's asymmetry, the Russian premise, and three audits

**THE OLD RED TEST IS GREEN.** `KnowledgeRetrieverTest > a refuted misconception is
never returned as an established claim` was failing since before W42.9 (the WAL
established it also fails on `384e24ff`, a commit predating the wave). The
cause was measured, not guessed: **the English fold is asymmetric**, and it is
asymmetric in both directions — `release` folds to `release` and `releases`
folds to `releas`; `process` folds to `proces` and `processes` folds to
`process`. A question and a claim that differ only in their inflection never
met, so "Where does the oxygen plants release come from?" never reached the
claim "Oxygenic photosynthesis releases oxygen as a byproduct of splitting
water."

**THE FOLD WAS NOT CHANGED.** Two global fixes had already been measured and
rolled back (the WAL records both), the fold's behaviour is pinned by W41's
own test (`fold("molecules") == "molecul"`), and the mission's §16 forbids
weakening a property to make a number better. So a **second channel** was
added beside the folded terms: `EnglishLemma` computes the base of an `-es`
plural — after a sibilant (`ss`, `x`, `z`, `ch`, `sh`) the plural is `-es`,
and otherwise the base ends in a silent `e` and the plural is `-s` — and the
retriever matches a term when either channel agrees. The folded terms still
decide the ranking, the coverage threshold and the reported gaps; only the
REACH of a claim changes. **The benchmark v1 is unchanged: accuracy 1.000,
abstention 0.900; the BM25 overlap moved 0.593 → 0.610.**

**THE RUSSIAN FALSE PREMISE IS NOW REFUSED.** The W42.9 diagnosis was that the
detector "exists, it does not work in Russian": an English question refused
with an exact explanation, a Russian question answered. Three causes, all
measured: the premise was compared with the claims in **RAW** words while the
bilingual lexicon is a table of **FOLDED** stems, so a Russian term never
reached it; the claim's text did not carry its **concept's name**, so the
Russian term for the concept had no counterpart on the claim's side; and the
Russian suffix list had `-его` **without `-ого`**, so "углекислого" never met
the lexicon's "углекислый". The premise is now compared in the index form,
the bridge handles a multi-word counterpart, and `-ого` was added. **The
Russian question is refused, and the premise the workspace HOLDS is still
answered.**

**THE NEGATION IS NOW SCOPED.** The first version of the detector asked only
whether a claim carried a negation word, and that is not a denial: "the two
processes are **not** alternatives in time" negates a proposition about
alternatives in time and says nothing about whether photosynthesis converts
light energy into chemical energy. The bare test refused a premise the
workspace holds. **A denial is now a NEGATED CLAUSE whose subject is the
premise's subject** — the clause must cover the premise's terms.

**THREE INDEPENDENT AUDITS.** The wave's own documentation was audited by
three read-only specialists, against the code and not against the reports:
`W42-VERIFICATION-ARCHAEOLOGY.md` (80 verdicts on the two reconnaissance
reports: 51 TRUE, 11 PARTLY TRUE, 16 FALSE, 2 UNVERIFIABLE — **nine of the
sixteen were TRUE at the base and are stale**, because both reports are
anchored to `0296e83e` and `develop` is 176 commits ahead),
`W42-VERIFICATION-DOCS.md` (93 registry findings, 20 contradiction pairs) and a
third audit of the integration matrix. Their corrections are applied in the
wave's own documents, and what they could not settle is marked UNVERIFIED
rather than guessed.

**The benchmark grew to 137 items.** `w42-benchmark-v3.json` holds 137 items
in 13 categories, English 64 and Russian 73, split train 40 / validation 39 /
test 58, with the pointer of every item checked against the workspace it
names — the defect class D-013 caught — and the check itself proven able to
fail. **Measured 2026-11-02, after this wave's changes: accuracy 0.842
(85 of 101 answerable), abstention 0.861 (31 of 36 refused), BM25 overlap
0.499; the FALSE_PREMISE items score 8 of 8 refused and the MISSPELLED items
13 of 13.** These are a *different, harder* instrument from v1, and both
numbers are true of their own instrument.

**Two negative findings, recorded rather than smoothed.** The **quality gate
exits 0** (263 candidates against the accepted baseline of 270) — the earlier
"the gate still exits 1" was stale. And the **application's startup does not
meet the mission's 5-second target**: the packaged application reports
`started in 19.018s` with the auto-trainer disabled, and **the port was not
up after 180 s with the default configuration**, because `AutoTrainer.onStart`
trains synchronously over a 3.6 MB corpus and blocks the `StartupEvent`. The
`1.571 s` of the W42.0 baseline was measured under different conditions and
cannot be quoted against that target any more.

**The build needs a JDK 25 that the machine does not put on the `PATH`.** The
default `java` is a JRE 25 without a compiler, so a bare `./gradlew` fails
with *"does not provide the required capabilities: [JAVA_COMPILER]"*. The
build runs with `JAVA_HOME=/home/alexandr-narbaev/jdk25`. A fresh clone
therefore needs a JDK 25 to build at all, and that is a fact about the
machine, not about the repository.

**Committed in this wave:** `EnglishLemma` and its test, the lemma channel in
`KnowledgeRetriever`, the index-form premise comparison, the scoped negation,
the Russian `-ого` ending, `RussianPremiseTest`,
`FalsePremiseAcrossLanguagesTest`, `EnglishLemmaTest`, the two formal
specifications that were on disk but uncommitted (`LearningStateMachine.tla`,
`PromotionStage.tla` — the formal gate measures **14 passed, 0 failed** with
them), `formal/.gitignore` for TLC's state directories, the three audit
reports, and the corrections they imply.
