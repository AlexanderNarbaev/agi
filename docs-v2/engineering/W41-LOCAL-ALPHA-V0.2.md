# MATRIX Learning Workbench — Local Alpha v0.2

**Status:** Local Alpha. The application runs, the journey works end to end, and
the measurements below were taken on this build. It is not a finished product,
and this document says exactly where it stops.

**Wave:** RECON-W41 (evidence-grounded reasoning and continual learning).
**Base:** W40 checkpoint `5cc7bdaa` / `5cc7e657`.
**This wave's commits:** see `git log --oneline 5cc7bdaa..HEAD`.

---

## 1. What this Alpha is

A workbench that acquires knowledge from curated sources, keeps the provenance
of every claim, refines its models of a domain, answers questions with a
**support score** that is explicitly not a probability, records the inferences
and conflicts it finds, measures a learner's progress with an instrument that
can fail, and keeps an active goal across a restart.

It is not a general knowledge engine. It works on two packaged domains
(photosynthesis, propositional logic), and on a machine learner that reads
English statements. Both limits are named here rather than papered over.

---

## 2. What works, with the command behind each claim

| Capability | Evidence | Command |
|---|---|---|
| Two domains load into the application | photosynthesis 8/17/4/18/13, logic 11/28/2/28/8 + 3 derivations | `curl -X POST localhost:9091/v1/workbench/workspaces/logic/load` |
| Questions are answered from the workspace | answered, 3 sources, 7 claims, support score 0.84 | `curl -X POST .../workspaces/photosynthesis/ask` |
| Unknown questions are refused, not guessed | `answered: false`, uncovered `boiling, point, mercury` | `curl -X POST .../ask` |
| Reasoning is grounded and its stages are visible | task DERIVATION, 3 rules checked, all `DERIVED_VALID` | `curl -X POST .../workspaces/logic/reason` |
| Conflicts are recorded, and neither side wins | 2 conflicts in the logic domain; no vote count exists in the schema | `curl .../workspaces/logic/contradictions` |
| A goal survives a forced stop | goal `Learn the Calvin cycle`, subtask `read` COMPLETED, budget 1/5, checkpoint intact after `kill -9` | `curl .../workspaces/photosynthesis/goals` after restart |
| The teach-back experiment can fail | PRE 0/2, POST 1/1, **DELAYED 0/1**, TRANSFER 1/2; structured 1.00, lexical baseline 0.00 | `curl -X POST .../teach-back-experiment` |
| Calibration is measured, not asserted | 6 forecasts, Brier 0.322, reliability 0.099, resolution 0.000, uncertainty 0.222 | same |
| The formal gate holds | 12 specifications, all exit 0; 26 mutants, 25 killed, 1 recorded survivor, 0 drift | `python3 scripts/mutation-harness.py`, `java -cp tools/tla2tools.jar tlc2.TLC …` |
| The targeted suites hold | 119 classes, 1 205 tests, 0 failures | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --tests "io.matrix.memory.*" --tests "io.matrix.brain.*" --tests "io.matrix.api.*" --tests "io.matrix.rag.*"` |

The application starts in **1.5–1.7 s** and listens on `http://0.0.0.0:9091`
(`grep "started in" /tmp/w41-app4.log`).

---

## 3. What is measured, and what is not

### 3.1 The support score is not a probability

The W40 report spoke of "confidence 0.84". That number was the mean of the
**authored** confidence of the claims that matched the question, weighted by
IDF coverage. No calibration data existed anywhere in the system.

It is now a **support score** with its inputs exposed: evidence coverage, mean
source authority, mean source freshness, claim count, source count, scope
match, contradiction status, and a `calibrationStatus` that reads
*"not calibrated: the repository holds no held-out set against which this score
could be fitted."* The application says this in the answer panel.

### 3.2 The teach-back was measuring the lesson, not the learner

The W40 teach-back scored the learner's answer by checking whether the
**lesson text** contained it — and `SyntheticLearner.answer(item)` ignored the
item it was given. The old number (0.0 → 0.81) therefore measured lesson
containment.

The rebuilt experiment runs four phases over **disjoint** item sets (pre,
post, delayed, transfer), scores with an independent closed-book learner that
never sees the lesson, audits the item sets for leakage, measures calibration
with Brier/Murphy decomposition, and compares itself against the legacy path and
a lexical baseline.

### 3.3 Six things this wave did NOT measure

Stated so that no reader infers more than was measured:

1. **Six probes is a demonstration, not a generalisation.** The teach-back ran
   on one concept with six measured items.
2. **Human learning was not measured.** Every result is a machine-to-machine
   experiment, labelled as such in the result itself.
3. **No global capability claim.** No result here supports a statement such as
   "MATRIX understands biology". The claims are about one packaged domain and
   one machine learner.
4. **The delayed phase failed.** It is reported as a failure, because that is
   what it was.
5. **Calibration was not demonstrated.** The Brier score of 0.322 on six
   forecasts describes those six forecasts; the support score remains
   explicitly uncalibrated.
6. **The full repository test suite was not re-run.** Only the targeted
   packages above were run, to avoid the OOM risk the standing rules name. The
   repository's other packages are therefore unverified **by this wave**.

---

## 4. Known limitations, named

1. **Two curated domains only.** The engine has no corpus beyond the two
   packs. The logic domain exists to show the engine is not tied to one
   curated package; it does not show generality.
2. **The entailment checker is bounded.** It enumerates ground propositional
   formulas up to 16 atoms. Beyond that it answers `UNDECIDED` — it does not
   guess. A "BIR execution" is not a proof of anything; the checker is a
   separate, exact procedure with its own bounds.
3. **The support score is not calibrated** and is not claimed to be. It is a
   ranking aid with disclosed inputs.
4. **The learner is a machine.** The structured learner scores an answer by
   term overlap, polarity, scope and evidence. A high score means the answer
   names the right things, not that a person would understand them.
5. **The delayed phase fails** on the adversarial set. Retention is the
   weakest measured axis.
6. **Goals are persisted, not planned.** The manager starts, advances,
   checkpoints and resumes goals; it does not choose the next subtask.
7. **The conflict detector is conservative by design.** Two claims contradict
   only when they say the same thing with opposite polarity. It will miss
   conflicts that need world knowledge.
8. **The history of the cognition pipeline is unbounded** — inherited from
   W40, not addressed by this wave. See the risk register.
9. **Formal results are evidence about the configured model**, not about the
   Java. The registry records the abstraction gap for each specification.

---

## 5. How to run

```bash
export JAVA_HOME=$HOME/jdk25
export PATH="$JAVA_HOME/bin:$PATH"

# the application
./gradlew :matrix-core:quarkusRun
# → http://localhost:9091/workbench/

# the targeted suites
./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" \
                            --tests "io.matrix.memory.*" \
                            --tests "io.matrix.brain.*" \
                            --tests "io.matrix.api.*" \
                            --tests "io.matrix.rag.*"

# the formal gate
for cfg in formal/*.cfg; do
  java -cp tools/tla2tools.jar tlc2.TLC -config "$cfg" "$(basename "$cfg" .cfg).tla"
done
python3 scripts/mutation-harness.py --report docs-v2/engineering/MUTATION-REPORT.md
```

The full repository suite is not run by default; the standing rule requires
the owner's agreement because of the OOM risk.

---

## 6. The journey, as verified over HTTP

```
Create    POST /workspaces/{id}/load              → concepts, claims, sources, evidence
Learn     POST /workspaces/{id}/ask               → answer + support score + inputs
          POST /workspaces/{id}/reason            → the eight stages of the pipeline
Assess    POST /workspaces/{id}/assess            → the outcome, held-out flag included
Save      the outcome, the goals and the derivations are written to SQLite
Force stop  kill -9
Restart   the same database, the same schema version
Reload    GET  /workspaces/{id}/goals              → the goal, its subtasks, its checkpoint
Reassess  POST /workspaces/{id}/ask               → the same answer as before
```

The restart was verified with the process killed with `SIGKILL`, not with a
graceful shutdown: the counts, the goal, the subtask's completion, the spent
budget, the checkpoint, the assessment outcome and the derivations' premise
order were all intact afterwards.

---

## 7. The next wave (W42), in the order the evidence suggests

1. **Calibrate the support score or stop calling it one.** The machinery
   (Brier, Murphy decomposition, held-out sets) exists; the held-out sets do
   not, at scale.
2. **Fix retention.** The delayed phase is the failing axis; a learner that
   scores on the post test and forgets by the delayed test has learned a
   retrieval cue, not a capability.
3. **Give the goal manager a planner.** Right now it resumes; it does not
   choose. The dependency graph is there to be used.
4. **Bound the cognition history** inherited from W40.
5. **A third domain** — and this time chosen to break the engine rather than
   to flatter it, with items written to be wrong in plausible ways.

---

## 8. Where the evidence lives

| Document | What it holds |
|---|---|
| `docs-v2/engineering/W41-REALITY-AUDIT.md` | the nine sections of the Phase Zero audit |
| `docs-v2/designs/DESIGN-70-evidence-grounded-reasoning.md` | the design of the reasoning engine and the epistemic axes |
| `docs-v2/research/W41-RESEARCH-A-LEARNING-SCIENCE.md` | stream A: what the teach-back may claim |
| `docs-v2/research/W41-RESEARCH-B-COGNITIVE-SYSTEMS.md` | stream B: frames, eligibility, error-gated learning |
| `docs-v2/research/W41-RESEARCH-C-KNOWLEDGE-ENGINEERING.md` | stream C: warrants, validity, conflict |
| `docs-v2/engineering/MUTATION-REPORT.md` | the mutation results, including the one recorded survivor |
| `docs-v2/engineering/TLA-STATUS.md` | the state of every specification |
| `formal/spec-implementation-map.yml` | the specification-to-implementation registry |
| `wal/SESSION_WAL.md` | the work log, including the incidents |
