# W41.0 — Reality and history audit

RECON-W41 Phase Zero. This is what the repository actually is, established
by running it, not by reading the W40 report. Every number here was
produced by a command in this session; the command is named.

The purpose is not to criticise W40. W40 built a working application and
recorded honestly what it could not do. The purpose is to know exactly
where the foundation stands before building on it, because three of the
things W40 reported as accomplishments turn out, on inspection, to measure
something weaker than the word used for them.

---

## 1. The repository state

| Item | Value | How it was established |
|---|---|---|
| Branch | `develop` | `git rev-parse --abbrev-ref HEAD` |
| HEAD | `5cc7e6570c6d8921d54f45fc93d2b12363a3a541` | `git rev-parse HEAD` |
| Reported checkpoint | `5cc7e657` | matches |
| Divergence from `origin/develop` | none | `git status -sb` |
| W40 commits | 30 | `git log --oneline 6763bdaa..5cc7e657 \| wc -l` |
| Modified, unstaged | `.gitignore`, `.opencode/todo.md`, `.opencode/w37-plan.md`, `AGENTS.md`, `opencode.json`, deleted `opencode.json.bak` | `git status --porcelain` |
| Untracked | `.codex/`, `.opencode/goals/`, `.opencode/opencode-swarm.json`, `.opencode/tmp/`, `.veai/` | `git status --porcelain` |

**The user's changes were not disturbed.** Every one of those entries is
the owner's own work, carried unchanged from the start of the W40 wave and
present at every checkpoint of this one.

---

## 2. The application

`./gradlew :matrix-core:quarkusRun` starts in **1.39–1.50 s** and reports
`Listening on: http://0.0.0.0:9091`. The route `/workbench/` is served by
the static resource `matrix-core/src/main/resources/META-INF/resources/workbench/index.html`.

The initial state reproduces exactly:

```
POST /v1/workbench/workspaces/photosynthesis/load
{"concepts":8,"claims":17,"sources":4,"evidence":18,"representations":13,"revisions":1}
```

These are **state observations, not quality metrics.** They say the seed
package has that shape; they say nothing about whether the knowledge is
right, complete, or useful.

---

## 3. The three claims that do not mean what they appear to mean

### 3.1 The answer's "confidence" is a heuristic, not a probability

The W40 report and the Workbench's own API expose an answer field called
`confidence`, and the value `0.84` was reported as an answer to "What is
photosynthesis?". The mission asks what that number is. It is:

```java
// KnowledgeRetriever.java:206
double claimConfidence = claims.stream().mapToDouble(Claim::confidence).average().orElse(0.0);
double confidence = Math.round(claimConfidence * coverage * 100.0) / 100.0;
```

A product of two things:

1. **The mean of the matched claims' own `confidence` field** — and those
   values were *authored by hand in the knowledge package*: the seed pack
   contains confidences of `0.7, 0.75, 0.8, 0.85, 0.9, 0.95`. They are
   assertions in a data file, not measurements.
2. **The retrieval coverage** — the IDF-weighted fraction of the question's
   terms that the matched claims cover.

So the number is: *how confident the pack's author was, times how much of
the question the retriever matched*. It has never been compared against
anything, and no calibration data exists anywhere in the repository.

**This violates the mission's §6.5** — "Never expose an uncalibrated
heuristic as a probability of factual correctness." The field is named
`confidence`, is shown in the UI, and is 84% precise to two decimal places.
A reader has no way to know it is a heuristic product of an authored
number and a lexical overlap. This is W41's first correction.

### 3.2 The teach-back measures lesson containment, not learning

The W40 report states a teach-back improvement from `0.0` to `0.81`. The
mission asks exactly what that measurement is. It is:

```java
// SyntheticLearner.java
public String answer(AssessmentItem item) {
    List<String> parts = new ArrayList<>();
    for (TeachingStep step : learnedSteps) {
        parts.add(step.statement());
    }
    return String.join(" ", parts);
}
```

**The learner ignores the item.** It returns the concatenation of every
account it was taught, and the grader then measures what fraction of that
item's key terms appear anywhere in that concatenation.

Answering the mission's questions in order:

| Question | Answer |
|---|---|
| How was the learner initialised? | Empty. It holds only what it was taught. |
| What could it access? | Only the taught steps' statements. |
| Did it see the original knowledge package? | No — but the taught steps *are* derived from that package's representations. |
| Was the test hidden? | The held-out items are hidden from the *teacher*. The learner never sees *any* item, so "hidden" is trivially satisfied rather than a design property. |
| Were pre-test and post-test tasks independent? | **No.** The same held-out items are scored before and after. |
| Does the score measure acquired capability? | **No.** It measures whether the lesson *contained* the key terms the assessment asks for. |
| Was a fixed rubric used? | Yes — key-term coverage, threshold 0.6. |

**W40 reported this correctly.** The class's javadoc, the guide and the WAL
all say the score measures whether the lesson contained the content, and
that this is "a necessary condition for transfer and a far from sufficient
one". The honesty was in the writing; the problem is that the API, the UI
and the field names (`beforeScore`, `afterScore`, `gain`) present a
containment measurement in the vocabulary of learning.

The machine-to-machine caveat is also correctly stated throughout: no human
teaching effectiveness is claimed anywhere.

### 3.3 The confidence, the containment score and the mastery labels are three different things

The Workbench currently offers the user a `confidence` on an answer, a
`gain` on a teach-back, and a `stage` on a capability. None of the three
is calibrated against an outcome. They are three heuristics with
measurement-like names, and the user cannot tell that from the interface.

---

## 4. The removal of the false learner is complete

`AgentBrainService.generateFromBir()` no longer exists. The search finds
only three references, all of them explanatory:

- `AcceptanceModel.java` — "This is the replacement for what
  `generateFromBir()` pretended to be";
- `AcceptanceModelTest.java` — the same, in the test's javadoc;
- `OpenAIChatResource.java:395` — a comment recording that the branch was
  removed and why it was unreachable.

No code path constructs or calls it. The corpus trainer it called and the
field it filled are gone with it.

**A methodological note.** A naive grep for `ConsciousBrain` finds 11
other main-source files that mention it — which would suggest a live
component. Checking construction instead of mention gives the truth:

| Component | Production construction sites | Verdict |
|---|---|---|
| `ConsciousBrain` | **0** | dead — the mentions are javadoc and one nested-type import |
| `ErrorDrivenLearner` | **0** | dead — the mission's §7.3 "open production wiring issue" is confirmed |
| `MCTSPlanner` | **0** | dead — §21's "do not use MCTS merely because the module exists" |
| `MindCycle` | 1 | reachable only from a CLI |
| `TrueMindCycle` | 3 | **on a production path** — `ProductionBrainClient` constructs it |

So the W40 audit's claim that the consciousness stack has no production
caller was correct. A liveness audit must count `new X(`, not mentions.

### 4.1 The generated-answer write-back is a self-reinforcing loop

The mission's §5.1 names `DefaultBrainPipeline` and `HierarchicalMemory`
specifically. Here is what they actually do, in `develop`:

```java
// DefaultBrainPipeline.java — Block 1: memory is SEARCHED for the input
var entries = longTermMemory.search(processedInput, 3);
... memCtx.append(e.content()) ...
String prompt = worldContext.isEmpty() ? processedInput
                                       : worldContext + " | " + processedInput;

// DefaultBrainPipeline.java — Block 3: the OUTPUT is written back
longTermMemory.store(HierarchicalMemory.Level.L2_MODULE,
        "Q: " + truncate(processedInput, 200) + " | A: " + truncate(output, 200),
        "brain", Set.of("pipeline", "auto"));
```

So the loop is: **generate → write the generated text into long-term
memory at the MODULE level → retrieve it on the next similar input →
prepend it to the next prompt → generate again.** `OpenAIChatResource`
writes at the same level from a second site.

Three findings:

1. **The layers are not separated.** A generated answer is stored at
   `L2_MODULE` — "Functional modules (medium-term)", importance 0.5 —
   with the tags `pipeline`/`auto` and **no validation step of any kind**.
   There is no distinction between "the assistant said this" and "this is
   true". The mission's §5.1: "No unverified generated output should be
   promoted automatically into trusted semantic knowledge."
2. **The promotion chain is bounded, which is the one mitigating fact.**
   `HierarchicalMemory.promote` refuses to lower a level, and the sleep
   cycle promotes only `L1_PATTERN → L2_MODULE`. Generated answers are
   therefore not carried up to `L3_QUANTUM` or `L4_KERNEL` by the
   consolidation path.
3. **The retrieval that feeds generation is unfiltered.** The pipeline
   takes the top three hits regardless of level and prepends them to the
   prompt, so the model's own previous output is part of its next input.

The generation itself is the MPDT neuron generator, not an LLM, so the
constitutional prohibition on LLMs in decision paths is not violated. But
the *epistemic* prohibition is: the store cannot tell an episode from a
fact, and W41.1 has to give it that distinction.

---

## 5. The formal gate reproduces

All eleven specifications model-check with exit code 0:

```
BotEthicsPipeline 481 | BrcStep 1 501 | ConjugateBudgeterDP 16 381
Consensus 55 057 | FrozenEthicalFNL 65 | HashChain 188 416
KnowledgeLifecycle 2 808 | KnowledgeStore 145 | MPDTNeuron 39 568
MctsLatsVisit 326 593 | MemoryM4Causal 396 577
```

The mutation harness: 22 mutants generated, 21 killed, 1 surviving (the
recorded Consensus quorum finding), 0 invalid, 0 drift.

The registry `formal/spec-implementation-map.yml` parses and lists all
eleven; the CI path filter in `.github/workflows/tla.yml` is regenerated
from it and the guard test `SpecImplementationMapTest` passes.

**What this is evidence about:** the configured models and the abstractions
they declare. `KnowledgeLifecycle` models a refinement as one atomic step
over three claims; the real store has an importer that replaces a
workspace, an incremental change set, and a refiner. The registry says so
in its gaps. A passing model is not proof of every Java path, and the
registry is careful not to claim it.

---

## 6. Historical lineage — what was lost, and what it was worth

### 6.1 The consolidation cycle is already half-reproduced

`feature/mind-w2-persistent-mind` carries `ConsolidationCycle.java`
(167 lines), `EpisodicLog.java` and `SleepScheduler.java`. Its javadoc
describes four steps:

1. **Frequency analysis** of the episodic log;
2. **Promotion** — recurring patterns (≥ 2 occurrences) get a canonical
   identifier so paraphrases collapse;
3. **Pruning** — stale, low-confidence entries are **tombstoned, not
   deleted**;
4. **A dream report** summarising learned / merged / forgotten /
   contradicted.

**Steps 1, 2 and 4 are already in `develop`**, in a different form:
`QuestionConsolidator.canonicalKey` is the sorted set of folded content
terms (not a hash — a deliberate improvement), `PROMOTION_THRESHOLD = 2`
is the same rule, and `ConsolidationReport` has the same role. W40
re-derived this mechanism rather than copying it.

**Step 3 is not present and is worth taking**, in the form the mission's
§7.4 asks for: the distinction between *reduced retrieval priority*,
*archival*, *user-requested deletion* and *permanent erasure*. W40's
resolution keeps a record and marks it `RESOLVED` — the safe direction —
but nothing implements *forgetting*, and the mission wants the distinctions
named rather than one undifferentiated delete.

**`GoalTracker.java`** is on `feature/mind-w3-sleep-consolidation`. Its
own javadoc is candid: *"Backed by in-memory state (W4) — a future W7 wave
can persist this to SQLite."* That wave never came. The concept is right
for the mission's §7.1 — a goal with an identifier, a status and progress
— and the implementation is **not** reusable: it is in-memory, it uses
`UUID.randomUUID()`, and it has no checkpoint, no subtasks, no failure
record and no resume path. W41.6 must write it again, not port it.

### 6.2 The branch names are shifted from their subjects

The branch names do not match what is on them, which is a trap for anyone
auditing by name:

| Branch | Actually contains |
|---|---|
| `feature/mind-w2-persistent-mind` | `ConsolidationCycle`, `EpisodicLog`, `SleepScheduler` |
| `feature/mind-w3-sleep-consolidation` | `GoalTracker`, `InboxWatcher` |
| `feature/mind-w4-autonomy-goals-inbox` | `DistillationLedger`, `ModelToMatrix` |

### 6.3 Other historical components

`MindCycle`, `TrueMindCycle`, `ConsciousBrain`, `ErrorDrivenLearner`,
`MCTSPlanner`, `HierarchicalMemory` and `PersistentHdcStore` are all
present in `develop`. Their liveness is in §4 above. `HierarchicalMemory`
(19 referencing files) and `PersistentHdcStore` (15) are genuinely wired;
the three dead ones are the ones the mission asks about.

---

## 7. The capability map

| Capability | State | Evidence |
|---|---|---|
| Import a curated package | **working** | 8/17/4/18/13/1 on load |
| Answer a supported question, with sources | **working** | confidence 0.84, 3 sources |
| Abstain honestly | **working** | "boiling point of mercury" refused, uncovered terms named |
| Answer at a requested abstraction level | **working** | mechanistic account differs from introductory |
| Assess and grade an answer | **working** | key-term coverage, threshold 0.6 |
| Track per-dimension learning state | **working** | 12 dimensions, 7 stages, regression observable |
| Derive a capability ladder position | **working** | §9's ladder, with the top stages gated on evidence |
| Record a correction without destroying the belief | **working** | supersede + revision, the old claim kept |
| Detect and consolidate knowledge gaps | **working** | canonical collapse, promotion, tombstone-style resolution |
| Report metrics with provenance and unmeasured list | **working** | version, dataset, procedure, sample size, 6 unmeasured |
| Transfer knowledge to another learner | **working** | identifiers re-keyed, history left behind |
| Survive a forced termination | **working** | killed with `kill -9`, a new process found everything |
| **Answer a question by DERIVATION** | **absent** | the retriever selects claims by lexical overlap; there is no inference step |
| **Distinguish a simplified account from a wrong one** | **absent** | representations have a level and a scope, but no validity judgement |
| **Detect a contradiction between claims** | **absent** | `EvidenceRelation.CONTRADICTS` exists in the model but nothing computes it |
| **Calibrate confidence** | **absent** | no calibration data exists anywhere |
| **Teach for real** | **partial** | the measurement is lesson containment, not learning |
| **Persist a goal across restart** | **absent** | the historical `GoalTracker` was never persisted |
| **Explain an answer's derivation** | **absent** | the answer carries claims and sources, but no steps |

---

## 8. The backlog W41 works from

Ordered by what the mission requires and by what unblocks the most. Each
item is independently verifiable.

**W41.1 — Epistemic integrity**
- The confidence field becomes an explicitly named **support score** with
  its inputs disclosed, or a calibration set is built. Not a probability
  until calibration exists.
- The information layers are separated in the store: source material,
  candidate claims, validated claims, hypotheses, dialogue history, skills.
- `DefaultBrainPipeline`'s write-back into `HierarchicalMemory` is
  revalidated: a generated answer must not become a validated claim.

**W41.2 — Progressive knowledge models**
- Representations carry an explicit relation set: refines, elaborates,
  specialises, generalises, supersedes, contradicts, depends-on,
  applies-under, excludes.
- A representation's validity state is judged: simplified-valid,
  incomplete, context-dependent, superseded, refuted, uncertain,
  contradictory, unsupported.
- The prerequisite graph is traversable, and a cycle cannot yield mastery.

**W41.3 — Evidence-grounded reasoning**
- The pipeline becomes `intent → classification → retrieval → evidence
  selection → inference → verification → answer → provenance`.
- The first reasoning capabilities: exact retrieval, relationship
  traversal, prerequisite resolution, single- and multi-hop deduction,
  contradiction detection, scope checking, counterexample identification,
  evidence sufficiency, explicit abstention.
- Every answer states the claims it used and the operations it performed.

**W41.4 — Learning and understanding**
- Assessment extends from recall to application, prediction, reasoning and
  transfer, with the dimensions scored separately.
- Mastery requires independent acceptance evidence, not a self-assessment.

**W41.5 — Teachability**
- The learner receives only the intended content.
- The pre/post tasks are independent; a delayed check and a novel transfer
  task are added.
- The learner must answer *from* the lesson, not by echoing it.

**W41.6 — Persistent goals and consolidation**
- A goal with an identifier, success criteria, subtasks, progress
  evidence, failed attempts, remaining work, a budget, a checkpoint and
  recovery instructions — persisted, and resumed after restart.
- The consolidation cycle gains the tombstone-based *reduced retrieval
  priority* and *archival* distinctions the mission lists, and keeps
  deletion separate from both.

**W41.7 — Cognitive integration**
- Only components that measurably improve the learning loop are connected.
- The dead `ErrorDrivenLearner`, `MCTSPlanner` and `ConsciousBrain` are
  either wired with a measured purpose or explicitly retired.

**W41.8 — Local Alpha v0.2**
- The Workbench grows the screens the mission lists, every one with a
  working action.
- A second domain — Boolean logic and basic computational reasoning — to
  test the engine outside the curated biology package.

**The measurement discipline applies to all of it:** every number is
produced by a command; a demonstration is described as a demonstration; and
nothing that was not run is reported as a result.
