# W53 (the procedure-memory wave) — Completion report

> **Note on the name.** This is the report of the *earlier* W53 wave — the one that made
> the system's procedural memory non-empty. The distributed-cognition wave also carries the
> name W53, and its report is `docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md`. This document was
> renamed so that both waves have an unambiguous, addressable report; nothing in it was
> changed except this note and its title.

**Status:** `CANONICAL` for the wave · **Date:** 2026-11-09 · **Branch:** `develop`
**The objective:** *"Develop MATRIX into a coherent, interacting cognitive computing system."*
**The primary success criterion, in the mandate's own words:** *"not the volume of code written
… a coherent, testable system in which perception, knowledge, memory, reasoning, learning,
action, reflection and extensibility work together."*

W51 built the pieces. W52 made them speak to each other. **W53 makes the system remember what
it learned** — the procedure it derived from the environment is written down with the standing
it actually has, a later cycle consults that record and names it, and the one claim the W45
finding rested on is **proved** instead of asserted. The wave also changed one epistemic rule
and **measured** it: the score did not move, which is reported as the score did not move.

---

## 1. The one-paragraph answer

MATRIX's **procedural memory is no longer empty**. When the cognitive runtime's model settles
on exactly one rule, the system now writes the procedure down — the rule it encodes, the
precondition under which it applies, the method it falls back to, the episode it was learned
in, and **what its standing rests on**: the validation kind, the case count, the measured
agreement, and a note in words that says the agreement was measured over the cases the model
was built from. A later cycle **consults that record**, and the trace of that cycle names the
procedure and the episode it came from — which is the mandate's acceptance point 11 read
literally ("a previously learned **procedure or skill** is reused in a later task"), where
before the wave the same point was demonstrated by a learned *rule* and the system's
procedural memory held nothing at all. The reason the earlier wave refused to write anything
— the lamp world's input domain is exhausted once the model settles, so no observation can be
both independent of the model and available — is **now proved in a test** rather than repeated
as a sentence: with V inputs the space holds 2^(2^V) candidates, each distinct assignment
eliminates exactly half of them, and the model therefore settles only after every assignment
the domain has. On the epistemic side the wave changed one rule — a supersession now requires a
**scoped denial** instead of a better-supported claim with lexically similar words — and
measured the whole 29-case evaluation with it in place: **18 of 29, unchanged, the same eleven
failures**. The change is therefore not a score-motivated one, and the report names what it
does **not** fix: the denial's own scoping is still lexical, which is exactly why the two
revision cases that expect no supersession still get one.

---

## 2. The deliverables of this wave

| # | the deliverable | where it is | the evidence |
|---|---|---|---|
| 1 | **The procedure is written when the model settles** | `io.matrix.cognitive.CognitiveKernel` — `procedureOfTheSettledModel` | `CognitiveKernelTest` — the procedure is written, its kind is `IN_SAMPLE`, its note says what the agreement rests on, and it names the episode it was learned in |
| 2 | **The procedure is reused, and the reuse is named** | the same class — the `SELECT` phase names the consulted procedure; the `LEARN` phase names the reused one | `CognitiveKernelTest` — "a later episode consults the procedure the earlier one wrote, and the trace names it"; `W51DemonstrationTest` — the wave's demonstration writes it and consults it |
| 3 | **The validation's precondition is explicit, and the reason is proved** | `io.matrix.cognitive.algorithms.Procedure` — `learnedInEpisode`, `validationNote`; `CognitiveStore` — the two columns and the immutability check that includes them | `DomainExhaustionTest` — the space holds every function of the inputs, each distinct assignment halves it, and the model settles exactly when the domain is exhausted |
| 4 | **The canonical `Procedure` and `Skill` are filled by the runtime** | `io.matrix.cognitive.canonical.CanonicalCycleBuilder` | `CanonicalContractCoverageTest` — **nothing is seeded**: the runtime settles, writes and carries the two contracts, and the coverage test asserts them |
| 5 | **One epistemic rule changed, and measured** | `io.matrix.knowledge.ModelRevisionDecider` | the whole 29-case evaluation re-run: **18 of 29, unchanged**; `docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json`; CD-030 |
| 6 | **The registers tell the truth** | `CAPABILITY-REGISTRY.yml` (55 rows, 0 defects), `PROJECT-STATE.md` (0 defects), `ROADMAP.md` §4.12, `DECISION-REGISTER.md` CD-030…CD-031 | the two validators and the docs link check |
| 7 | **The report and the closeout** | this document | the commits, the push and the pull request — **the merge is the owner's** |

---

## 3. What the system can now do, and what it cannot

### 3.1 It can

- **Write down a procedure it derived from the environment**, with the standing that procedure
  actually has — and say which episode it was learned in.
- **Reuse that procedure in a later cycle**, and name it — in the trace, in the notes, and in
  the canonical form the cycle carries.
- **Distinguish a consistency check from a validation** in its own record, and refuse to call
  the first one the second.
- **Prove, in a test, why an independent validation is unreachable in the environment it has** —
  instead of asserting it in a comment.
- **Refuse to supersede a belief because a new sentence shares words with it**, and say which
  similarity it refused to act on.
- **Say what it cannot do**: the eight citation failures and the two revision false positives
  are named, measured and left visible in §7.4.

### 3.2 It cannot

- **Validate a procedure independently** in the micro-world — the domain is exhausted when the
  model settles, and that is arithmetic, not a defect (§3.2's proof is the test).
- **Say that its learned procedure generalises** — the record says `IN_SAMPLE`, and the note
  says what that means.
- **Answer a question whose answer needs a claim the retrieval does not reach** — the eight
  citation failures are a retrieval limit, and §5.5 names it.
- **Tell two claims about the same subject apart** without the words they share — the workspace
  has no other identity for a subject, and that is why the two revision false positives remain.

---

## 4. The architectural decisions of this wave

**CD-031 — the procedure of the settled model is written down, with the standing it has.**
The W45 finding was true and was also half the story. The fast path was being used as an
in-sample shortcut of the reference, and the record that said so was never written, so the
procedural memory was empty and the system could not claim to have learned a procedure. The
record now says what it rests on: the validation kind, the case count, the agreement, the
episode, and a note that states the limitation in words. The exhaustion is not a defect of the
world; it is the arithmetic of the hypothesis space, and it is proved.

**CD-030 — a supersession requires a scoped denial.** The old rule was "the new claim is about
the same subject **and** it is better supported", and "about the same subject" was decided by
the words two sentences share. The mandate forbids inferring a supersession from a weak lexical
similarity alone, so the relation that decides it is now a relation: the new sentence must deny
what the older claim asserted. A better-supported claim that denies nothing is an addition, and
the decision says so.

---

## 5. The measurements

### 5.1 The wave's own scopes

`JAVA_HOME=$HOME/jdk25 ./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests
"io.matrix.contract.*" --tests "io.matrix.w51.*" --tests "io.matrix.knowledge.*" --tests
"io.matrix.bridge.*" --offline` → **1162 tests, 0 failures, 0 errors, 0 skipped** (the count is
the JUnit XML in `matrix-core/build/test-results/test/`).

The quality gate: `bash scripts/quality-gate.sh` → **PASS at 271 candidate literals**, at the
accepted baseline of 271 — the wave added no new magic literal.

### 5.2 The procedure's life

| the moment | what the system says |
|---|---|
| the model settles | `LEARN`: "the procedure `proc-microworld-settled-model` was written: the settled-model lookup for: lamp is true exactly when power=1,switch=1; it rests on 4 case(s) of IN_SAMPLE evidence (agreement 1.0); the agreement was measured over the 4 case(s) the model was built from: a consistency check, which licenses the shortcut and says nothing about whether the model generalises. The validation set holds 0 observation(s) the model was not built from, and the environment's input domain is exhausted once the model settles, so no observation can be both independent of the model and available" |
| a later cycle | `SELECT`: "the procedure `proc-microworld-settled-model` was consulted — learned in episode `ep-sig-w51-learn-2`, resting on 4 case(s) of IN_SAMPLE evidence" |
| the canonical form | the `Procedure` carries the kind, the case count, the note and **its own provenance** (the episode that wrote it, not the episode that consulted it); the `Skill` carries `learnedFromEpisodeIds` = that episode, and `reusable` = the system's own judgement that the procedure should be offered again — the canonical contract says so: *"Reuse is a decision, not a fact"* |

### 5.3 The exhaustion, proved

`DomainExhaustionTest`:

- the hypothesis space holds every function of the inputs — **16** for the lamp world's two
  variables (2^(2^2));
- each distinct assignment eliminates exactly half: **16 → 8 → 4 → 2 → 1**;
- the model settles **exactly** when every assignment of the domain has been observed;
- the set the promotion rule asks for — the observations the model was **not** built from — is
  empty, and stays empty: there is no assignment left to observe.

⇒ The W45 sentence is now an arithmetic identity with a test, and the record's refusal is
backed by it.

### 5.4 The epistemic evaluation — 18 of 29, and a rule that did not move the score

`python3 scripts/w51-epistemic-eval.py --work /tmp/w53-eval-a --report
docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json`

| the category | the W52 result | the W53 result |
|---|---|---|
| answerable | 2 / 8 | 2 / 8 |
| unanswerable | 6 / 6 | 6 / 6 |
| false-premise | 4 / 5 | 4 / 5 |
| multi-claim | 1 / 3 | 1 / 3 |
| knows-what-it-does-not-know | 2 / 2 | 2 / 2 |
| revision | 3 / 5 | 3 / 5 |
| **the total** | **18 / 29** | **18 / 29** |

**The eleven failures are the same eleven:** A-02, A-04, A-05, A-06, F-02, M-01, M-03, P-01,
P-02, R-04, R-05. No case was excluded, no expectation was changed, no assertion was weakened,
and the dataset was not touched.

**What was measured, and what came of it.**

1. **The candidate the wave set out to test — "for a demand that asks for a quantity, the
   number must come from evidence that covers the demand's content terms" — is moot on this
   dataset.** The unanswerable family already abstains **6 of 6** under the shipped
   configuration, including both quantity cases ("How many tonnes of basalt…" and "How many
   research vessels…"). A rule that changes no case cannot be shown by this dataset to help,
   so it was **not adopted**: shipping an unmeasurable rule would add behaviour without
   evidence, which is the opposite of what the mandate asks for.
2. **The rule that was adopted changes the score by nothing.** The supersession rule was
   replaced and the whole evaluation re-run: **18 of 29, the same eleven failures**. It is
   therefore not a score-motivated change — it is a correctness-motivated one, and the
   measurement is reported so that nobody reads it as an improvement.
3. **The eight citation failures are a retrieval limit, and it is located.** The required
   proposition of A-04 — *"However, because plate motions are global in scale, they are best
   measured by satellite-based methods."* — **exists** in the corpus, its status is
   `SUPPORTED` (so it is citable), and at a floor of 0 it ranks **10th of 161**. At the shipped
   floor of 0.25 it falls out, because it covers three of the question's terms ("plate",
   "motions"↔"movement", "measured") and none of the rare ones ("gps", "space-geodetic",
   "techniques") that carry the IDF weight. **The claim is lexically weaker and semantically
   right**: no threshold on coverage can separate it from the noise, and a lexical discriminator
   cannot either. What it needs is a match that does not require a shared rare term — a
   semantic one — and the workspace has none. That is the blocker.
4. **The two revision false positives are the same blocker from the other side.** The denial's
   own scoping is lexical, so a denial can still be read onto a claim about a different
   subject.

### 5.5 The registers

- the capability registry: **55 rows, 24 fields, 0 defects** (`scripts/validate-project-registry.py --strict`);
- the project state: **0 defects** (`scripts/validate-project-state.py --strict`);
- the decisions: **CD-030** and **CD-031**;
- the docs link check: exit **0** (`python3 scripts/docs-link-check.py --root "$PWD" --strict`).

---

## 6. What changed in the repository

| the file | the change |
|---|---|
| `matrix-core/src/main/java/io/matrix/cognitive/CognitiveKernel.java` | the procedure of the settled model is written, read and named; `promotedProcedure()` became `activeProcedure()` |
| `matrix-core/src/main/java/io/matrix/cognitive/algorithms/Procedure.java` | `learnedInEpisode` and `validationNote`, with the previous shape kept as a constructor |
| `matrix-core/src/main/java/io/matrix/cognitive/CognitiveStore.java` | the two columns, their migration, the write, the read, and the immutability check that includes them |
| `matrix-core/src/main/java/io/matrix/cognitive/canonical/CanonicalCycleBuilder.java` | the procedure's own provenance, the note and the episode in the canonical parameters; the skill's `reusable` is the system's judgement, not a claim of validation |
| `matrix-core/src/main/java/io/matrix/knowledge/ModelRevisionDecider.java` | a supersession requires a scoped denial |
| `matrix-core/src/main/java/io/matrix/w51/W51Demonstration.java` | the wave's demonstration section: the procedure is written, consulted and reported |
| `matrix-core/src/test/java/io/matrix/cognitive/microworld/DomainExhaustionTest.java` | **new** — the proof of the exhaustion |
| `matrix-core/src/test/java/io/matrix/cognitive/CognitiveKernelTest.java` | the procedure's life, and the honest replacement of the W45 assertion |
| `matrix-core/src/test/java/io/matrix/cognitive/canonical/CanonicalContractCoverageTest.java` | **no seeding any more** — the runtime fills the two contracts |
| `matrix-core/src/test/java/io/matrix/cognitive/canonical/CanonicalCycleBuilderTest.java` | reuse is a decision, not a fact; a withdrawn procedure is not offered again |
| `matrix-core/src/test/java/io/matrix/w51/W51DemonstrationTest.java` | the demonstration's procedure section |
| `docs-v2/architecture/CAPABILITY-REGISTRY.yml` | the row `W53-PROCEDURE-MEMORY` (54 → 55) |
| `docs-v2/governance/ROADMAP.md` | §4.12 — the wave's tasks |
| `docs-v2/governance/DECISION-REGISTER.md` | CD-030, CD-031 |
| `docs-v2/governance/PROJECT-STATE.md` | the snapshot of the wave |
| `docs-v2/engineering/W53-PROCEDURE-MEMORY-STATUS.md` | this report |
| `docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json` | the machine-readable evaluation |

---

## 7. The five categories

### 7.1 Implemented and verified

- **The procedure is written, reused and named** — `CognitiveKernelTest`, `W51DemonstrationTest`
  and the demonstration's own report.
- **The canonical `Procedure` and `Skill` are filled by the runtime** — `CanonicalContractCoverageTest`,
  with nothing seeded into the store.
- **The exhaustion is proved** — `DomainExhaustionTest`.
- **A supersession requires a denial** — the rule changed and the whole evaluation was re-run;
  the score did not move, and that is reported.
- **The gates hold**: 1162 tests / 0 failures; the quality gate at 271; the registry at 0
  defects; the project state at 0 defects; the docs link check at exit 0.

### 7.2 Implemented but not fully verified

- **The procedure's durability across a restart.** The procedure is written to the cognitive
  database and read back from it in the same store; the restart path was exercised through the
  demonstration's own restart section, but the procedure's own round-trip after a *process*
  restart was not measured separately.
- **The rule change's effect on the corpus outside the evaluation dataset.** The 29 cases are
  the measurement; the corpus is larger.

### 7.3 Designed but not implemented

- **A validated procedure.** The record can hold one (`ValidationKind.INDEPENDENT`) and the
  promotion path can write one, but no environment in the repository can supply the independent
  evidence — the hypothesis space is the set of all functions of the inputs, so the domain is
  exhausted at the moment the model settles. An environment whose hypothesis space is *smaller*
  than its input domain would.
- **A semantic retriever.** §5.4's third item names exactly what the eight citation failures
  need, and it is not a threshold, a weight or a heuristic.

### 7.4 Blocked

- **The eight citation failures (A-02, A-04, A-05, A-06, M-01, M-03, P-01, P-02).** The
  required claims exist and are citable; they rank immediately below the relevance floor
  because the question's rare terms carry the IDF weight and the answering claims do not
  contain them. **The blocker: the workspace has no match that does not require a shared rare
  term.**
- **The two revision false positives (R-04, R-05).** The denial's scoping is lexical, and the
  workspace has no identity for a subject beyond the words two sentences share. **The blocker
  is the same one, from the other side.**
- **F-02's contradiction verdict** (the lexical rule that decides whether a premise is
  contradicted) — unchanged by this wave and named in the W52 report.

### 7.5 Deferred

- The method profile's reliability reaching the selector's **score** (it is reported, not
  scored) — W52's finding, unchanged.
- Chain B's request-id registry for the second direction of the bridge is process-scoped.
- The six ARCHIVE candidates from the W51 audit are the owner's call.

---

## 8. The honest limits, in one list

1. The procedure is an **in-sample** shortcut. The record says so, the note says so, and the
   canonical form carries both. It is not a validated procedure and the system does not call it
   one.
2. The **exhaustion is a property of this environment**, proved and not worked around. A
   different environment would have to be built before a validated procedure could exist.
3. The epistemic score **did not move** (18 of 29, the same eleven failures). The wave's rule
   change is a correctness change, and nothing in this report should be read as an improvement
   in the score.
4. The eight citation failures and the two revision false positives **need a capability the
   workspace does not have** — a match that does not require a shared rare term, or an identity
   for a subject that is not the words two sentences share.
5. The wave changed **one** epistemic rule. It did not touch the relevance floor, the status
   filter, the demand analysis or the quantity gate, because none of them was measured to be
   the cause of a named failure.
6. The quantity discriminator the wave set out to test was **measured and found moot** on this
   dataset, and was **not adopted** — no behaviour was shipped without evidence.
7. No claim is made here of general intelligence, human-like understanding, universal learning
   or reliable autonomous self-improvement. What the system demonstrates is a cognitive cycle
   that writes down what it learned, reuses it, and says what it cannot do.

---

## 9. How to reproduce everything in this report

```bash
export JAVA_HOME=$HOME/jdk25

# the wave's scopes — 1162 tests
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --tests "io.matrix.contract.*" \
  --tests "io.matrix.w51.*" --tests "io.matrix.knowledge.*" --tests "io.matrix.bridge.*" --offline

# the proof of the exhaustion
./gradlew :matrix-core:test --tests "io.matrix.cognitive.microworld.DomainExhaustionTest" --offline

# the procedure's life
./gradlew :matrix-core:test --tests "io.matrix.cognitive.CognitiveKernelTest" --offline

# the canonical contracts, filled by the runtime
./gradlew :matrix-core:test --tests "io.matrix.cognitive.canonical.*" --offline

# the epistemic evaluation — 18 of 29
./gradlew :matrix-core:quarkusBuild --offline
python3 scripts/w51-epistemic-eval.py --work /tmp/w53-eval-a \
  --report docs-v2/engineering/W53-EPISTEMIC-EVALUATION.json

# the gates
bash scripts/quality-gate.sh                    # PASS at 271
bash scripts/w45-chain-isolation-check.sh        # the isolation gate
bash scripts/w45-chain-isolation-selftest.sh    # the gate's own control
python3 scripts/validate-project-registry.py --strict   # 0 defects
python3 scripts/validate-project-state.py --strict      # 0 defects
python3 scripts/docs-link-check.py --root "$PWD" --strict
```

---

## 10. The state of the working tree

The wave's changes are committed on `develop` and pushed. The pull request `develop` → `main`
is **prepared and reviewed by an agent, and NOT merged by one**: the merge is the owner's
decision, as it was for every previous wave.
