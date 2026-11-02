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

## D-011 — The frame of the answer is translated; the knowledge is not

**Owner decision, 2026-11-02.** The owner was asked how far the
multilingual work should go, and chose: *translate the frame of the answer
into the language of the question*.

**What this means in the code.** The sentences MATRIX writes around an
answer — the refusal, the language note, the reference note, the level's
limitation — are said in the user's language. The workspace's own
statements are not translated: they stay in the language they were written
in, and the answer says so.

**Why not more.** A word-by-word translation of a scientific explanation
is gibberish, and gibberish is worse than a clear boundary. The mission's
§16 forbids making an external service mandatory, so a real translation of
the knowledge needs an owner decision of its own. The frames are a closed
set the system writes itself, so their translations are exact rather than
approximate — that is the difference between a table and a translator.

## D-012 — The shadow classes are wired to the live engines, and their math is verified first

**Owner decision, 2026-11-02.** The nine shadow classes on `develop` are
wired to the live engines rather than deleted. The mission's §12.4 forbids
discarding unsuccessful implementations, and §16.1 reserves the deletion
for the owner.

**The order the owner named.** The `ErrorDrivenLearner` first — because
the mission's §9 says the error-driven integration happens "only after
verifying the math", and because this class's mathematics is what the
error-driven learning actually is.

**What the verification found.** The decay subtracted a fixed step from the
targeted accumulators and had no floor: the same error, repeated, drove
the accumulator arbitrarily far below zero. The mission's §9 forbids
exactly that. The floor is now the lower edge of the Hebbian updater's
deadband, and the properties — the targeted-only decay, the floor, the
determinism, the parameter checks — are pinned by tests.

## D-013 — a benchmark item's POINTER may be corrected; its ETALON may not

**The context.** The W42 benchmark's `ru-cross-3` asks "Что такое цикл
Кальвина?" — about the Calvin cycle — and pointed its `workspace` at
**logic**, the workspace that holds propositional logic, modus ponens and
the truth tables. The Calvin-Benson cycle is a concept of the
**photosynthesis** workspace. The item was therefore **unreachable by any
implementation**: no correct system could answer it, because the
knowledge it asks about was not where it asked.

**The rule.** A benchmark item has two kinds of fields, and they are not
the same kind of thing:

- the **etalon** — `answerable`, `mustMention`, `mustNotMention`, the
  key terms. This says what the RIGHT answer is. **It may not be
  changed to make a number better.** (D-001.)
- the **pointer** — `workspace`. This says where the knowledge lives. A
  pointer that names a workspace without the concept is a defect in the
  data, and correcting it changes no expectation: it makes the item
  reachable, which is what an answerable item must be.

**The test that distinguishes the two.** If the item could be answered
correctly by some implementation as it stands, it is a tuning question and
the etalon stays. If **no** implementation could answer it, the item is
malformed and the pointer is corrected. `ru-cross-3` is the second case:
the logic package holds no Calvin cycle, so the correct behaviour was to
refuse, and the refusal was counted as a wrong answer.

**The evidence.** Every item of the benchmark was checked against its own
workspace: the key terms of every other item appear in the knowledge of
the workspace it names. `ru-cross-3` was the only one that did not.

**What this does not do.** It does not move the target for the Russian
items: the Russian cross-lingual score moves from 4 of 5 to 5 of 5
because the fifth item became answerable, not because the criterion was
eased. The criterion is untouched: `answered && mentions && avoids`.

## D-014 — The one disagreeing benchmark item stays answered, and the disagreement is recorded

**The owner decided this on 2026-11-02.** The item is `en-unanswer-2` —
"Who invented propositional logic?" — whose etalon asks for a refusal and
whose system answers with what propositional logic IS, naming the word
"invented" as uncovered.

The measured reason the etalon cannot be met by a rule: the uncovered
terms of every item were measured, and **no threshold on their number
separates the item from the answerable ones**. `en-para-1` carries five
gaps and is answered; `en-unanswer-2` carries one. The gaps of the
answerable items are the question's glue — "come", "state", "explain" —
and the answer carries the substance; the gap of `en-unanswer-2` is the
RELATION the workspace does not hold, and a rule about the number of
gaps cannot tell the two apart.

**So the behaviour stands: answer what the workspace holds, name the
word it cannot cover, and refuse nothing.** The etalon is not moved
(D-013), the abstention quality stays 0.900, and the disagreement is
printed by the benchmark gate on every run, so it cannot be forgotten.
The target of §14's row 8 is "no fabrication on unsupported questions",
and there is no fabricated statement: the statement is true and the gap
is named.

## D-015 — The magic numbers are fixed in the code; the baseline is NOT updated

**The owner decided this on 2026-11-02.** `scripts/quality-gate.sh` has
been RED since 2026-10-02: the literal candidates rose from the accepted
270 to 346, and the frozen-zone check finds 785 diff lines in
`.github/workflows/` and 14 in `AGENTS.md` against the W30 baseline
`b144563e`.

The literals are extracted to named constants, in the code, so the gate
counts them where they belong. **The baseline is not updated** —
accepting 76 new literals would be weakening the gate, and the
mission's §16 forbids weakening a formal property to make the build
green. The frozen zones are the owner's (§16.1); their diff is a
separate question from the literals and is not touched here.

## D-016 — The shadow classes are wired to the live engines, one at a time, the math first

**The owner decided this on 2026-11-02.** This sharpens D-012 rather
than replacing it: the six shadow classes are not to be deleted, and
each is to be wired to its live engine — with its math verified first,
because that verification has already paid once: the `ConsciousBrain`'s
prediction error was computed against the observation itself, so its
surprise was identically zero, and the error-driven part of the L7 cycle
was dead by construction.

## D-017 — The full repository test suite is not run; the targeted packages stand

**The owner decided this on 2026-11-02.** The AGENTS.md requires the
owner's agreement for the full run because of the OOM risk, and the
owner withheld it for now. The mission therefore continues to measure
with the targeted packages, and the full run remains an open,
owner-held item rather than a silent omission.
