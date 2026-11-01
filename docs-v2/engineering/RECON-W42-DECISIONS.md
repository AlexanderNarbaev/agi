# RECON-W42 — Decisions

**Owner directive:** RECON-W42 · 2026-11-01
Each entry: the decision, why, what it forecloses, and what would change it.

---

## D-001 — The benchmark is built before any improvement is attempted

**Decision.** W42.0 ships `w42-benchmark-v1.json` (34 items), the BM25
baseline and the runner, and the measured baseline is recorded here and in
`RECON-W42-BENCHMARKS.md`.

**Why.** The mission's §14 asks for retrieval to be measured against BM25 and
for learning to be evaluated independently, and §8.2 requires the sample size,
the baseline and the limitations beside every number. A benchmark written
*after* an improvement invites the improvement to be written against itself.

**Forecloses.** No claim in W42.1–W42.5 may state an improvement without the
before-and-after numbers from this instrument.

**Would change if** the benchmark's own category definitions proved wrong —
then it is fixed and the baseline is re-measured, and the change is recorded
rather than silently applied.

---

## D-002 — No historical branch is merged, restored, or deleted

**Decision.** Every MIND branch is retired; the four ideas worth keeping are
re-implemented against `develop`'s live engines, not copied.

**Why.** All eight MIND waves are already on `develop`; the four "missing"
classes were deleted by a truthfulness pass whose absence is test-enforced
(`SingleInstanceGuardTest:23-30`). The branch code carries fourteen verified
defects, one of which — `ConsolidationCycle:89`, `report.tombstoned =
entries.size() - freq.size()` — **fabricates the number it reports**, and the
branch's own test asserts the fabrication.

**Forecloses.** "Port the sleep consolidation" is off the table as a *port*.
The tombstone idea returns as new code with a test that can fail.

**Would change if** an owner decision restores a branch deliberately — in
which case the §3 defect list travels with it.

---

## D-003 — The `KnowledgeGraphStore` becomes a projection, not a fourth store

**Decision.** The mission's §4.1 forbids a second competing knowledge store.
Two already exist. W42 will not add a fourth; the graph store's machinery
(entity/relation model, community detection, traversal) is kept as a
**read-only projection over `KnowledgeStore`**.

**Why.** `KnowledgeStore` is the only one of the three with provenance, a
versioned schema and a restart guarantee. A graph projection gives the
mission's §10.2 "Knowledge Graph" screen without a fourth source of truth.

**Forecloses.** `KnowledgeGraphStore` may not be written to by the W42
pipeline.

---

## D-004 — The BM25 baseline is a measurement, not a candidate

**Decision.** `Bm25Index` exists to answer *"would a standard lexical ranker
have found the same claims?"* It is not wired into the answer path.

**Why.** The mission's §11.1: prefer the simplest method that meets the
requirement, and measure the alternative before adopting it. MATRIX's own
retriever returns the claims it used and explains them; a bare ranker does
not. The overlap (0.593) is the evidence for whether that difference is
worth anything.

**Forecloses.** No claim that BM25 "should replace" the retriever without a
measurement showing the retriever is worse.

---

## D-005 — The learning state machine is persisted, not recomputed

**Decision.** W42.1 adds a `learning_state` table holding the stage, the
transition, its evidence and its timestamp, plus a legal-transition guard
and a transition audit. The recomputed label does not go away; it becomes a
**view** over the persisted record, and the two must agree.

**Why.** The mission's §7 requires a real state machine with observable,
explained regression and preserved earlier mastery records. The recomputed
label cannot deliver a transition audit, cannot say *when* the state changed,
and its regression note fires only when the last outcome contradicts an
earlier success.

**Forecloses.** The stage may not be derived from the outcome log alone
anywhere in the product.

**Would change if** the persisted machine and the recomputed label disagree —
then the disagreement is a defect in one of them, and it is resolved in
favour of the recorded transition, because a record beats a recomputation.

---

## D-006 — The planner selects; it does not generate

**Decision.** The W42.1 planner chooses the next action **from what exists**:
the goal's subtasks, the prerequisite graph, the gap detector's missing
prerequisites, and the learner's current per-dimension state. It proposes no
new subtask and no new concept.

**Why.** The mission's §4.3 says to reuse `LearningGoal` and `GoalManager` and
to "develop an actual planner rather than treating persistence of goals as
planning". The material is already there and unused:
`LearningGoal.remainingSubtasks()`, `blockedSubtasks()`,
`hasDependencyCycle()`, `KnowledgeGapDetector.missingPrerequisites()`, the
`prerequisite` table's acyclicity check, and `CurriculumOrderer` in
`io.matrix.ktopo` that the knowledge subsystem never references. Generating a
subtask is W42.2's job, because a subtask is a claim and a claim needs
evidence.

**Forecloses.** The planner may not invent content.

---

## D-007 — The mission's §14 targets are read as measurements, not as promises

**Decision.** Every row of §14's table is treated as a target to be
**measured against the baseline**, and the baseline is measured before the
work. Where a target is unreachable as written, it is adjusted **in writing
before** the change, not after.

**Why.** §14 says so itself: *"These are engineering targets, not results. If
a target is inappropriate after baseline measurement, propose a documented
adjustment before changing it."*

**Forecloses.** No wave may report a target as met without the measurement.

---

## D-008 — The defects found by the audit are fixed in the wave that found them

**Decision.** The configuration-key typo was fixed immediately (`b724c79d`),
because it was dead configuration with an accidental correct value and a
one-character repair. The remaining defects wait for their wave: the backup
instruction and the two unwired recordings are W42.1.

**Why.** The mission's §12.1 lists "runtime validation" inside the wave. A
defect found by an audit is fixed in the same wave when the fix is small,
safe and provable; a defect whose fix needs a design decision is recorded
and scheduled.

**Forecloses.** No defect found in W42.0 may remain unowned at the end of
W42.1.

---

## D-009 — The four W41 documentation contradictions are corrected, not rewritten

**Decision.** `LEARNING-WORKBENCH.md` §6, `DESIGN-70` §0, and the two stale
hypothesis statuses are corrected to say what is true **now**, with the W41
numbers kept as the historical record.

**Why.** The mission's §3.4: *"Preserve historical documents as historical
records rather than silently rewriting research history."* The W41 numbers
are evidence; they get a date and a status, not a deletion.

**Forecloses.** No historical measurement is removed from the documentation.

---

## D-010 — The specialist audits are the evidence base, not a summary of them

**Decision.** The four read-only audits — repository archaeology, cognition
and memory, knowledge and learning, agents and application — were run
independently and their findings are quoted with their file:line citations.

**Why.** The mission's §12.2: *"Require evidence and exact file references
from each specialist. The coordinating agent must independently reconcile
conflicting findings."* Two findings were reconciled by re-reading the code:
the `PersistentHdcStore` "dead vs live" question (it is live — 2 production
sites) and the "which spec is the W41 baseline" question (verified against
the remote, not assumed).

**Forecloses.** No finding enters the plan without a citation.
