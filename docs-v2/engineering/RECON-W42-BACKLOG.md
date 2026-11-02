# RECON-W42 — Backlog

**Owner directive:** RECON-W42 · 2026-11-01
**Ordered by dependency, then by expected measurable benefit** (mission §17).
Each item names its baseline, its exit condition and the risk it closes.

---

## W42.1 — Unified learning and knowledge core — **DONE**

**Exit condition (the mission's):** *a persistent goal can acquire, practise,
evaluate, revise and resume learning.* The "acquire" half needs W42.2's
ingestion; W42.1 delivers the rest and says so.
**Delivered:** the backup instruction ✓, the contradiction and derivation
recording ✓, the persisted state machine ✓, the planner ✓, the memory's
bound ✓, the demand check ✓ (abstention 0.400 → 0.700), the tokenizer's
tests ✓, the teach-back gate ✓.
**The report:** `W42.1-LEARNING-CORE-REPORT.md`.

| # | Item | Baseline | Exit condition | Risk |
|---|---|---|---|---|
| 1.1 | **Fix the backup instruction.** The screen must name the `-wal`/`-shm` files, or the store must checkpoint the WAL before the operator is told to copy. | the naive copy loses every outcome and asked question | a test that copies the store the way the screen says and asserts the rows survive | **R1** |
| 1.2 | **Wire the contradiction recording.** `ReasoningEngine` computes the conflicts; nothing writes them. | the `contradiction` table is always empty; `GET /contradictions` returns `[]` | the logic workspace's two conflicts are visible over HTTP after one reason call | **R4** |
| 1.3 | **Wire the derivation verdict.** The checked `DerivationStatus` is computed in memory and discarded. | every stored derivation stays `UNDECIDED` | a reasoned derivation is stored with its verdict and is read back | **R5** |
| 1.4 | **Persist the learning state machine.** A `learning_state` table: stage, transition, evidence, timestamp. A legal-transition guard. A transition audit. Earlier mastery records preserved when a regression occurs. | the stage is recomputed per request; there is no transition record | a state survives a restart; a regression is recorded with its cause and its earlier record is kept | **R10** |
| 1.5 | **Build the planner.** From the active goals, the prerequisite graph, the gap detector's missing prerequisites and the per-dimension state, select the next bounded action. | nothing selects anything | a goal with blocked subtasks yields a named next action and a reason | — |
| 1.6 | **Bound the live memory.** `HierarchicalMemory`'s eviction never fires because only L2 is written and only L0 is evicted. | the `entries` map grows without bound on the live path | a long-running store evicts and the eviction is observable | **R11** |
| 1.7 | **Improve abstention.** The refusal threshold and the premise check. | abstention quality **0.400**; false premise **0/2** | measured improvement on the W42 benchmark, with the before-and-after recorded | **R2, R3** |
| 1.8 | **Give the interface the instrument that can fail.** The Teach tab must run `/teach-back-experiment`, and the `KNOWLEDGE` capability stage must not be gated on the legacy containment measurement. | the UI calls the W40 path; the guide documents its numbers | the UI's Teach tab shows the four phases, the leakage audit and the calibration | **R17, R18** |

**The raw material already exists and is unused:** `LearningGoal.remainingSubtasks()`,
`blockedSubtasks()`, `hasDependencyCycle()`, `violatesDependencies()`,
`KnowledgeGapDetector.missingPrerequisites()`, the `prerequisite` table's
acyclicity check, `CurriculumOrderer` (`io.matrix.ktopo`).

---

## W42.2 — Universal knowledge acquisition — **the core is DONE**

**Exit condition:** *importing a real book or document produces inspectable,
traceable knowledge.* **Met** for the five formats, the source record, the
promotion layers, the independence rule and the endpoint — verified live on
a real text document ✓ ✓.

**Left, stated plainly:** the corpus manager (§5.5) ✓ ✓; the authored
representations for the imported concepts ✓ ✓; the validation stage ✓ ✓.

| # | Item | Notes |
|---|---|---|
| 2.1 | **A document pipeline:** `SOURCE → EXTRACTION → NORMALIZATION → SEGMENTATION → CONCEPT DISCOVERY → CLAIM EXTRACTION → EVIDENCE LINKING → VALIDATION → INTEGRATION` | the mission's §5.3 verbatim |
| 2.2 | **Formats:** TXT, Markdown, HTML, PDF, EPUB — using established libraries, not hand-written parsers | the mission's §5.1; §13.2's security list applies to each |
| 2.3 | **A source record** preserving the filename, the id, the content hash, the format, the language, the author when known, the publication date when known, the import date, the location, the extracted structure, the page references, the extraction quality and the processing history | the mission's §5.2; **never invent bibliographic metadata** |
| 2.4 | **A promotion state machine:** `RAW → EXTRACTED → CANDIDATE → SUPPORTED → VERIFIED → DISPUTED → SUPERSEDED → INVALIDATED`, with precise transition rules | the mission's §5.4 |
| 2.5 | **Distinguish source agreement from independent corroboration** | ten documents copying one source are not ten confirmations |
| 2.6 | **A corpus manager:** collections, deduplication, incremental indexing, resumable imports, budgets, quality metadata, snapshots, export | the mission's §5.5 |
| 2.7 | **A `Concept` that a document can create** — `KnowledgeRefiner` cannot create one today | the mission's §10.1 item 3 |

**The constraint:** the ingestion must not become a second knowledge store.
Everything it produces lands in `KnowledgeStore`, through the same
provenance rules.

---

## W42.3 — Dialog Intelligence — **DONE**

**Delivered:** the localised text layer ✓ ✓, the bilingual lexicon ✓ ✓, the
typo corrector with the original preserved ✓ ✓, the bounded conversation
with reference resolution ✓ ✓, the eight dialogue classes ✓ ✓, the
168-case dialogue benchmark ✓ ✓, and the language note on the answer ✓ ✓.
**The report:** `W42.3-DIALOG-INTELLIGENCE-REPORT.md`.

**Left, stated plainly:** the answer is not translated ✓ ✓; a failed
explanation does not yet produce a different explanation ✓ ✓; the
conversation is in memory and does not survive a restart ✓ ✓.

## W42.3 (superseded — the original plan)

**Exit condition:** *a user can have a meaningful multi-turn dialogue about
imported materials.*

| # | Item | Baseline |
|---|---|---|
| 3.1 | **Localisation of the text layer**: stopwords, morphology, negation, the task classifier | English-only; cross-lingual **0/5** |
| 3.2 | **Tests for `TextFeatures`** — before anything else in this wave changes | **zero tests repo-wide** |
| 3.3 | **Typo handling** with the original message preserved alongside the normalised one | 3/4 benchmark items |
| 3.4 | **Language identification, intent detection, entity/concept recognition, reference resolution, ambiguity detection, clarification** | none exist |
| 3.5 | **A bounded, persisted conversation memory** — the history is never passed whole into a reasoning call | none exists |
| 3.6 | **Multilingual generation** with the response keeping its language | none |
| 3.7 | **User corrections that trigger re-evaluation, not overwriting** | `KnowledgeRefiner` supersedes rather than overwrites ✓ — the pattern exists |
| 3.8 | **≥ 100 reproducible multilingual test cases** | 0 (the mission's §14) |

---

## W42.4 — Learning and assessment — **DONE**

**Delivered:** the exercise selector ✓ ✓ (the rules are in the order of what
a learner needs first), the delayed retention measured from the session
boundary ✓ ✓, the representation composer ✓ ✓, and the claim extractor's
verb list ✓ ✓.
**The report:** `W42.4-LEARNING-AND-REPRESENTATION-REPORT.md`.
**The objective is demonstrated end to end** ✓ ✓ — acquire, explain, answer
at the levels, know the limits, practise, evaluate independently, improve the
representation, and survive a SIGKILL ✓ ✓ ✓.

**Left, stated plainly:** the false-premise question is still answered ✓ ✓;
the transfer items are answered without the conclusion ✓ ✓; the delayed
teach-back phase fails ✓ ✓; **the consolidation is still the unreachable W41
component** — its result is discarded and the compaction still hard-deletes,
so the mission's prohibition on silently destroying historical evidence is
not yet honoured in the code ✓ ✓; the no-consolidation baseline is not
measured ✓ ✓; the selector's choices are not measured against an alternative
✓ ✓.

## W42.4 (superseded — the original plan)

**Exit condition:** *demonstrable learning improvement on a reproducible
held-out benchmark.*

| # | Item | Baseline |
|---|---|---|
| 4.1 | **An exercise generator and an item selector** — the caller currently supplies the item id | none exist |
| 4.2 | **Delayed retention as a scheduled event**, with the schedule persisted | `LearningDimension.RETENTION` has no dataset item |
| 4.3 | **A no-consolidation baseline** to compare against | does not exist |
| 4.4 | **A consolidation process** with the ten phases of the mission's §9 | `TwoStageConsolidator` exists, is unreachable, and its result is discarded |
| 4.5 | **Reversible forgetting with tombstones** wired into the memory path | `SqliteMemoryBackend.compact` hard-DELETEs; `TombstoneService` is unwired beside it |
| 4.6 | **The learning-comparison matrix**: no learning / repetition / retrieval-only / retrieval+practice / error-driven / consolidation+replay | does not exist |

---

## W42.5 — the false-premise detection — **THE ANALYSIS, AND WHY IT WAS NOT SHIPPED**

The benchmark holds two `FALSE_PREMISE` items, and both were answered:
*"Since photosynthesis produces oxygen from carbon dioxide, how much oxygen
does a plant release in an hour?"* and *"Since every valid argument has
true premises, what are the premises of modus ponens?"*.

**A mechanism was built and measured, and it was not shipped.** A question
that asserts a premise ("Since X, ...") was extracted, and the premise was
checked against the workspace's claims: the premise was SUPPORTED when a
statement of the workspace carried a sufficient share of the premise's
content terms. A premise the workspace could not support was refused, and
the answer named it.

**MEASURED, at the threshold that was best:**

| | the baseline | with the premise rule |
|---|---|---|
| the answer accuracy | 0.792 | 0.750 |
| the abstention quality | 0.700 | 0.900 |
| the items wrong in total | 8 | 7 |

**The trade:** the rule fixed the false premises and the refusal of the
unanswerable question, and it cost a legitimate MULTI_HOP question — *"If
light reactions split water and release oxygen, what does the Calvin cycle
do?"* — whose premise the workspace states in different words.

**Why it was reverted.** The support test is a TERM test, and a false
premise is a RELATION error: the premise says the oxygen comes from carbon
dioxide, and the workspace says it comes from splitting water — the two
statements share the subject and the object and differ in their SOURCE. A
term test cannot see that, and a term test that is tuned until two items
pass is a fit to a sample of 34, not a capability. The mission's §7.1 says
that a metric moving in the wrong direction means stopping and analysing,
and the mission's §16 forbids hiding a failed experiment.

**What a real detector needs**, for the next wave:
1. the **premise clause** of the question — done and kept in the analysis
   above: the marker has to be at the START of the question, and "as " is
   not a premise marker;
2. the **subject, the object and the source** of a proposition — the
   prepositional phrase that names where something comes from, with the
   head noun as the source;
3. a **contradiction test** over that structure: the same subject and the
   same object with a different source are in conflict;
4. the same structure for the **negation** — the W41 `ContradictionDetector`
   already handles opposite polarity over equal terms, and it can be
   reused for that part.

**The measurement that must accompany it:** the false-premise items are
TWO. Whatever is built has to be measured against a set that is larger than
two, or the number is a demonstration and not a capability.

## W42.5 — Cognitive integration — **DONE**

**Delivered:** the false-premise detector ✓ ✓ (the `FALSE_PREMISE` items
0/2 → 2/2, the accuracy unchanged ✓ ✓, the abstention 0.700 → 0.900 ✓ ✓);
the bounded idempotent consolidation with seven kinds of forgetting and
without touching a claim ✓ ✓; the error-driven selection ✓ ✓; the
retention baseline experiment ✓ ✓ — the retention's evidence 2 → 3 and its
stage EXPLORATION → PRACTICE ✓ ✓.
**The report:** `W42.5-COGNITIVE-INTEGRATION-REPORT.md`.

**Left, stated plainly:** the consolidation does not yet affect the
knowledge ✓ ✓; the §9's error-driven learning integration is not wired ✓ ✓;
the `ConsciousBrain` cluster and the nine shadow classes are an owner's
decision ✓ ✓; the paraphrase items and `ru-cross-3` are still wrong ✓ ✓.

## W42.5 (superseded — the original plan)

**Exit condition:** *measurable improvement attributable to specific
cognitive components.*

| # | Item | Baseline |
|---|---|---|
| 5.1 | **A baseline for every mechanism before it is integrated** — HDC, BIR, Tsetlin, WiSARD, RAG, MCTS, the attention family | most have a test count and no baseline |
| 5.2 | **MCTS: a real reward, or the removal of the claim.** The reward is the constant `0.5` | **R23** |
| 5.3 | **`ErrorDrivenLearner`: fix the four weaknesses before wiring it.** The accumulator falls without bound; the mask is stale | **C3** |
| 5.4 | **The `ConsciousBrain` cluster: product or research artefact?** 67 test sites, 0 production sites | **R16** |
| 5.5 | **`HierarchicalMemory` and `PersistentHdcStore`: bounds** | **R11, R12** |
| 5.6 | **The nine shadow classes: wire or retire** — an owner decision | **R21** |

---

## W42.6 — Local Alpha v0.3 — **DONE**

**Delivered:** the Alpha document ✓ ✓ with the twelve steps verified over
HTTP ✓ ✓, the owner's checklist ✓ ✓, the limits named ✓ ✓, and the restart
verified with a `SIGKILL` of the application's own JVM and 469 712 bytes of
unwritten WAL ✓ ✓ ✓.
**The document:** `W42.6-LOCAL-ALPHA-V0.3.md`.
**Also written here:** the **W42.2 acquisition report** ✓ ✓ — the wave was
implemented, committed and measured, but its report was never written, and
the mission's §16 says a task is not complete merely because the code was
written ✓ ✓.

## W42.6 (superseded — the original plan)

**Exit condition:** a reproducible local alpha with honest capability
boundaries — the owner's thirteen-step journey, with the forced restart and
the repeated assessment at the end.

---

## W42.7 — the open items, stated plainly (2026-11-02)

**The mission is not finished, and this is what is left.** Each item names
what it would take and what stands in the way.

| # | Item | Where it stands | What it takes |
|---|---|---|---|
| 7.1 | **The answer's FRAME is translated; the knowledge is not.** A Russian question about an English workspace gets an English statement — and **the answer says so** | **the owner's decision (2026-11-02): translate the frame, not the knowledge.** Implemented in W42.7: the refusal, the language note, the reference note and the level's limitation are said in the user's language; the statements stay in the language the workspace holds them in | nothing further without an external service, which the mission's §16 forbids as a mandatory dependency. The knowledge's translation stays a limit, and it is recorded as one |
| 7.2 | **The corpus registry does not track the versions of a source.** The content hash IS the version: the same document with different content is a different source, and the registry can only report that two sources share a title | **CLOSED in W42.7.** The registry groups the sources by their title and reports the versions: a version is a distinct content under one title, the versions are ordered by the AUTHORED publication date, and a version without one goes last — the registry does not guess a date, and the moment a document happened to be imported is not a publication date. In the same wave the two missing formats were added — DOCX and CSV, which the mission's §5.1 names and which the pipeline could not read; and the corrupted-container defect was fixed: a broken document was imported as an EMPTY one, silently, with a clean result | nothing. The registry reports what it can check and guesses nothing |
| 7.3 | **The delayed teach-back phase fails**, and is reported as failing | **the entry was STALE — the phase measures.** The live run of 2026-11-02: the pre phase 2 items 0 correct, the post 1 item 1 correct, the delayed 1 item 0 correct, the transfer 2 items 1 correct; the expectation was met, and the conclusion says in as many words that the learner is synthetic and that this is not evidence about human retention | the samples are tiny — one item in the delayed phase — so this is a demonstration of the measurement, not a measurement of retention. The delay is LOGICAL (a disjoint item set after the lesson), not a wall-clock wait, and the CONSTITUTION's first article forbids the clock in the decision path |
| 7.4 | **The validation is not run on the workspaces the packages load.** It runs on every import, and it can be run by hand | the import runs it (`POST …/documents` reports what it promoted) | nothing — the packages' claims are authored, and the validation has nothing to group that the authoring did not |
| 7.5 | **`ConsciousBrain` and the nine shadow classes** are documented and unused | **the owner's decision (2026-11-02): wire them to the live engines.** Two are wired. The `ErrorDrivenLearner` — the named example — had NO production caller at all: only its test. It is now the live engine of the consolidation's error-driven phase: the phase builds a `CognitiveErrorStream` from the workspace's VERIFIED learning errors and applies the bounded decay to the workspace's own Hebbian bindings, the state is persisted (the schema v15), and the answer says that the bindings it rests on were weakened. The `SleepConsolidator`'s mechanism was wired next: the consolidation now PROPOSES, measures the knowledge's answerability on the held-out items before and after, and keeps the proposal only if it did not fall | the remaining seven shadow classes, one at a time. Six of them are DUPLICATES of live engines (`CreditLedger`, `AuditChain`, `GpuKernelEngine`, `ModelToMatrix`, `DistillationLedger`, and the `FederationRegistry`, whose live counterpart has different semantics and no persistence) — for those, "wiring" means the live engine's mechanism already serves the capability, and the shadow copy is the thing that duplicates it. `ConsciousBrain` has a test but no production caller; wiring it means naming the capability it would serve |
| 7.6 | **The interface's views** | **CLOSED in W42.7.** The Source Browser was added over the corpus registry, the Knowledge Graph over the new read-only projection of the workspace (the decision D-003, which had never been carried out), and the Experiment Dashboard over the measured instrument | nothing. The interface now carries twelve tabs: Dashboard (the integrity), Ask (the conversation), Reasoning, Knowledge (the library), Sources (the source browser), Graph (the knowledge graph), Experiments (the experiment dashboard), Assess (the assessments), Teach (the learning sessions), Progress, Goals (the learning goals), Persistence (the memory and the consolidation) |

## Cross-cutting

| # | Item | Why |
|---|---|---|
| X.1 | **The documentation consistency registry** must stay current | §3.4; eight contradictions were found in one wave |
| X.2 | **A formal specification for each new state machine** — the promotion machine, the learning state machine, the planner's cycle | §13; extend only where the semantics are stable |
| X.3 | **Mutation coverage for every new critical property** — a property that cannot fail is not a check | §13 |
| X.4 | **The benchmark grows** — 34 items is a demonstration; the mission asks for ≥ 100 multilingual cases | §14.1 |
| X.5 | **The security checklist of §13.2 is assessed when ingestion exists** | §13.2 |
| X.6 | **Every wave ends with a live run, a forced restart, and the numbers** | §12.1 |

---

## The order, and why this order

1. **W42.1 first** — because the mission's §17 says so, and because the
   planner and the persisted state machine are what every later wave needs
   to report progress against.
2. **W42.2 second** — because the mission's first practical objective is
   *acquiring a concept from an actual document*, and nothing can demonstrate
   that without ingestion.
3. **W42.3 third** — because a dialogue is the interface through which a
   person meets the system, and because the localisation work makes the
   Russian half of the benchmark reachable.
4. **W42.4 fourth** — because learning can only be measured against an
   instrument that the first three waves make real.
5. **W42.5 fifth, and only for what measurably helps** — the mission's §11:
   *prefer the simplest method that meets the actual requirement*.
6. **W42.6 last** — packaging what works, with the limitations named.

**One dependency that the order does not change:** W42.1's exit condition
mentions *acquire*, and acquisition needs W42.2. The honest statement is
that W42.1 delivers the other four verbs, and the W42.1 report says which
one it could not deliver and why.
