# EPISTEMIC-LEARNING-ISOLATION — the mechanism, its remediation, and its evidence

**Status:** `MEASURED` — the mechanism is implemented, the remediation has been APPLIED to this
machine's real episodic log, and the numbers below are the measured ones. The independent
re-review of this work is pending.
**Date:** 2026-11-15.
**Directive:** EPISTEMIC-INTEGRITY P0.4 — *isolate all evaluation/probe/synthetic records from
durable organic learning; sandbox logs and stores, tagged source provenance, admission gates,
leakage tests, replay/recovery with zero contamination. Existing contaminated entries must not be
silently deleted or have history rewritten; mark/quarantine via durable history or a documented
reversible mechanism.*

**Why this document exists.** The measured defect: two frozen evaluation probes and 59
deliberately-constructed collection episodes entered the machine's real episodic log
(`data/mind/episodic.ndjson`, 108 → 167 records), and the learning window then trained on them.
Two further collection runs took the log to 226 records. The class balance of the last-20 window
went from 20/0 to 10/10 and the induction fidelity from a trivial 1.00 to 0.50: the mind had
trained on its own examination paper.

---

## 1. The mechanism

**The provenance tag** (`EpisodeRole`) travels with every record through the production write
path: `TrueMindCycle.think(input, original, role)` → `MindResult.role` →
`EpisodicLog.append(..., role)` → the role on disk. The roles are `ORGANIC`, `EVALUATION`,
`EXPERIMENT`, `SYNTHETIC` and `LEGACY_UNTAGGED`.

**The admission gate** (`LearningAdmission`) is the only way into the learning window. A record is
admitted when its role is learning-eligible (`ORGANIC` or `LEGACY_UNTAGGED`) AND its id is not
named in the quarantine ledger. `RealSleepScheduler.runInduction` builds the induction input
through it, so a non-organic record cannot reach `RuleInductionEngine.induce`.

**The second line of defence** is at the trainer: `RuleInductionEngine.induce(ids, features,
labels, roles)` drops every non-learning-eligible record, counts it, and fails closed if nothing
remains.

**The reversible quarantine** (`EpisodeQuarantine`) is an append-only ledger next to the log.
Each event is `{"op":"QUARANTINE"|"RELEASE","id":...,"reason":...,"ts":...}`; the LAST event per
id decides. A quarantine is therefore reversed by appending a `RELEASE`, and the quarantine itself
stays in the history. Nothing is ever deleted and the log is never rewritten: a quarantine is a
MARK, not an edit.

**The probe firewall** was extended to the TRANSLITERATED form of the frozen corpus. The battery
holds `столица франции`; the serving path transliterates before inference, so the log holds
`stolitsa frantsii`. The firewall compared only the Cyrillic form, and two frozen probes walked
straight into the learning feed. The corpus is now derived through the same transliteration the
serving path uses (47 Cyrillic + 5 transliterated entries), and it is a pure function of the
frozen battery — no clock, no randomness.

**The corpus was extended once more, under the owner's authority** (section 4c): a bare
letter-less arithmetic expression is a probe.

---

## 2. The independent review, and what it found

An independent adversarial review of the change found ten defects. They are listed here with the
fix that closed them, because the defects are the reason the mechanism is shaped the way it is.

| # | Severity | Defect | Fix (file:line) |
|---|---|---|---|
| F1 | BLOCKER | The remediation existed only in a test's temporary directory. The real log had no ledger, every record read back `LEGACY_UNTAGGED`, and the production window was still the contaminated tail: `admitted=226 excluded=0`, window 10+/10−, fidelity 0.5 — the measured defect, unchanged. | The remediation is applied (section 4): `data/mind/episodic.quarantine.ndjson` plus the reviewable decision file `data/mind/episodic.quarantine.ids`, written by the opt-in test `ProductionLearningIsolationRemediation`. |
| F2 | MAJOR | The only production writer dropped the role: `ProductionBrainClient.cycle` called the five-argument `episodicLog.append(...)`, which declares nothing, so every gateway episode was organic by construction. | `ProductionBrainClient.java:388` now passes `mr.role()`; the client declares its traffic at its own boundary (`EpisodeRole.ORGANIC` by default, a constructor argument otherwise). Test: `ProductionBrainClientProvenanceTest`. |
| F3 | MAJOR | An absent declaration meant ORGANIC **in code**, and the false claim was written into the permanent audit trail (a record on disk carried `"role":"organic"` for traffic nobody had declared). | Every constructor that carries no role now defaults to `LEGACY_UNTAGGED`: `EpisodicLog.Entry`, `EpisodicLog.append` (both overloads), `PromotionGate.Candidate`/`forInteraction`/`forRetrieval`, `PromotionGate.evaluate`, `MindResult`, `TrueMindCycle.think`. Only an explicit `ORGANIC` declaration writes "organic". |
| F4 | MAJOR | The quarantine ledger failed OPEN: a truncated line was skipped, the mark it carried was lost, and the contaminated record re-entered the learning feed — while the class's own javadoc claimed the opposite. | The ledger write is now ATOMIC (a temporary file moved over the ledger under a writer lock), so a torn line cannot be produced; the gate FAILS CLOSED on a ledger it cannot read in full (`EpisodeQuarantine.readable()`), and the counter is on the runtime path (`DreamReport.quarantineLedgerReadable` / `quarantineMalformedLines`, and the status snapshot). |
| F5 | MAJOR | The remediation rule was positional (`log position >= 108`). A position is not an identity: re-applied to a grown log it marks a FRESH ORGANIC interaction and frees a contaminated one. It also missed the earlier contamination — 36 of the 53 records that survived it were designed workload inputs in the pre-collection region. | The rule is by CONTENT and applied by ID: RULE A = frozen probe (Cyrillic or transliterated), RULE B = the collection harness's own designed workload inputs. The positional command was REMOVED from `EpisodeQuarantineTool`; `quarantine-ids` and `quarantine-inputs` replaced it. |
| F6 | MINOR | The "after" fidelity of 1.00 was the degenerate single-class window, presented next to the "before" 0.50 as if it were a gain. | The class balance of the window is now reported on the runtime path (`windowPositives`/`windowNegatives`) and the evidence test ASSERTS that both classes are present in the measured window. The measured fidelity after the remediation is 0.75 on a 15+/2− window — not 1.00. |
| F7 | MINOR | The gate asked the ledger about every record separately, and every question re-read and re-parsed the whole file: the cost of a pass grew with the square of the log size. | The ledger is read ONCE per pass (`LearningAdmission.admitted`); the reads are counted (`EpisodeQuarantine.ledgerReads()`) and the test asserts one read for the whole pass. |
| F8 | MINOR | The production window was defined in three places (the scheduler, the log's API, the operator tool). | One definition: `LearningAdmission.assess` reads and admits, `LearningAdmission.window` selects. The scheduler, `EpisodicLog.learningWindow()` and the tool all go through them; the test asserts that the three call sites agree and that the ids the induction was given ARE the window's ids. |
| F9 | NIT | `EvalBattery.standardBattery()` carries an unused `Random r = new Random(42L)` inside the frozen corpus builder — an invitation for the next change to make the firewall non-reproducible. | **NOT CHANGED, by the coordinator's instruction: the frozen `EvalBattery` must stay untouched.** The corpus's purity is pinned by a test instead (`LearningWindowDefinitionTest.theFrozenCorpusIsTheSameEveryTimeItIsBuilt`: two builds of the battery are equal, and neither corpus may shrink). The unused handle is a hazard only if somebody consumes it; until then, changing a frozen file is the larger risk. |
| F10 | NIT | `RuleInductionEngine.induce(ids, features, labels)` applied NO role filter: a training call that could not say where its episodes came from. | The overload now REFUSES (`IllegalStateException`). Every training call states its roles; a caller with no provenance to state says `LEGACY_UNTAGGED`. The two unit tests of the fold were updated to state their fixtures' roles. |

---

## 3. What did NOT change

- The label rule (`confidence >= 0.7 AND accepted`) — untouched.
- `K_MAX = 20` — untouched (the literal was replaced by the named constant).
- The production fold (`TsetlinTrainer` + `MpdtGaProducer`) — untouched.
- The HDC retrieval substrate — untouched; it is fed by `KnowledgeForgeIngest`, not by the query
  path, so evaluation traffic does not reach it through the episodic log.
- The C2A collection harness stays opt-in, and now writes every record as `EXPERIMENT`.

---

## 4. The production remediation — measured

The remediation ran against this machine's real log. It appends marks to the ledger and writes
the decision next to it; it does not touch the log.

```
MATRIX_P0_4_REMEDIATE=true ./gradlew :matrix-brain-runtime:test \
    --tests "io.matrix.brain.runtime.ProductionLearningIsolationRemediation" --offline
```

| Measure | Before | After |
|---|---|---|
| The log's sha256 | `5732004d24956132712f6a875ef6082faa3b8df251421f07390b500e894ca11b` | **identical** (the byte count and the mtime are unchanged too) |
| Records in the log | 226 | 226 (nothing added, nothing removed) |
| The ledger | absent | `data/mind/episodic.quarantine.ndjson`, 209 events, 209 quarantined ids |
| The decision | — | `data/mind/episodic.quarantine.ids`, 209 lines (`id<TAB>rule`) |
| Rule A (frozen probe) | — | 59 records |
| Rule B (designed workload input) | — | 150 records |
| Admitted / excluded | 226 / 0 | **17 / 209** |
| The learning window | 20 records, 10+/10− | **17 records, 15+/2−** |
| The induction fidelity | 0.50 | **0.75** |

**How to read the fidelity.** 0.75 on a 15+/2− window is a real measurement. The 1.00 that the
first (positional) rule produced was NOT an improvement: it was a window with no negative class
at all, and the directive itself names a single-class 1.00 as trivial. The honest statement is
that the contamination is gone and that the remaining organic history of this machine is small
(17 records) and mostly positive — which is a fact about the machine, not a result.

**The residual, honestly.** The 17 admitted records carry the inputs `gravity`, `100-7?`, `7*6`,
`What is gravity?`, `What is the capital of Chile?`, `What is the chemical symbol for iron?`,
`What is the capital of Australia?`, `What is the capital of Poland?`,
`Which continent is Japan located on?`, `What is the chemical symbol for gold?`,
`Kakoj khimicheskij simvol u zheleza?`, `audio:frame=8 hdc_dim=256`. The test ASSERTS that none
of them is a designed workload input and none is a frozen probe. Some of them nevertheless LOOK
like evaluation traffic from earlier waves (`100-7?` is the arithmetic battery item with a
trailing question mark; the frozen corpus deliberately excludes letter-less probes shorter than
six characters from substring matching). Closing that is a CONTENT-POLICY decision about the
earlier waves' ad-hoc probe sets, not a mechanical fix, and every one of the 17 can be
quarantined by id today (`EpisodeQuarantineTool quarantine-ids`).

**Reversibility.** `EpisodeQuarantineTool release <id>` re-admits a record, and the quarantine
stays in the ledger's history; `release-all` reverses every mark. The log is never rewritten.

---

## 4b. The canary — the ledger is machine state, and the machine state is guarded

The quarantine ledger lives next to the log it governs, under `data/mind/`, which is
gitignored (`.gitignore:83`) for the same reason the log is: **both are machine state, not
repository content**. That is correct, and it left one hole: nothing in the repository guarded
the ledger. A cleanup that removed `data/mind/` would take the ledger with it, the admission
gate would fall back to its documented default ("nothing is quarantined"), all 226 records would
be admitted again, and the contamination would return **without a trace**.

The hole is closed by a small TRACKED file,
`docs-v2/engineering/EPISTEMIC-LEARNING-QUARANTINE-CANARY.json`, and by
`LearningIsolationLedgerCanaryTest`:

| Field | Value | What it is |
|---|---|---|
| `recordedRecords` | 226 | how many records the log held when the remediation ran |
| `recordedMarks` | 209 | how many quarantine marks the ledger held then |
| `logSha256AtRemediation` | `5732004d…` | the log's digest AT THAT MOMENT — a historical fact, **not asserted** |
| `rule` | RULE A ∨ RULE B, by record id | the identity of the rule that decided |

**The rule the test enforces.** If the real log exists and holds **at least** the recorded
number of records, then the ledger **must** exist and hold **at least** the recorded number of
marks. A missing ledger is then a build failure, not a silent fallback.

**Why the comparison is monotonic.** The log grows with every organic interaction and the
ledger is append-only, so an exact count or a pinned digest would break the build on the next
real conversation. "At least" is the only comparison that survives growth; the digest is
recorded as history and is deliberately not asserted.

**A fresh clone skips, and says why.** With no `data/mind/episodic.ndjson` there is no machine
state to watch, and the test is skipped with that reason — it does not fail a clone that has
never run a mind.

**The failure is demonstrated, on a copy.** `theCanaryFailsWhenTheLedgerIsMissing` copies the
machine state into a temporary directory, deletes the ledger copy, and shows what the build
would say:

```
[CANARY] the demonstration of the failure: the quarantine ledger is MISSING:
/tmp/p04-canary-…/episodic.quarantine.ndjson — the log holds 226 records and the canary
records 209 quarantine marks, but the ledger that holds them does not exist. Without it the
admission gate falls back to 'nothing is quarantined', every record is admitted again, and the
contamination returns without a trace. Re-apply the remediation: MATRIX_P0_4_REMEDIATE=true
./gradlew :matrix-brain-runtime:test
--tests "io.matrix.brain.runtime.ProductionLearningIsolationRemediation" --offline
```

The same test then shows that the copy WITH its ledger passes, and that a ledger holding fewer
marks than recorded also fails.

---

## 4c. The owner's exception — the one authorised change to the frozen zone

**Under whose authority.** The owner's answer of **2026-11-15** to question
**D1-QUARANTINE-RESIDUAL-010**: *"A + an owner-authorized exception for the frozen corpus"* — the 17
residual records **stay as they are** (no further quarantine), and the frozen probe corpus is
extended so that the **letter-less arithmetic probes** (`100-7?`, `7*6`, …) are caught as probes
from now on. This is the **only** change to a frozen zone that this initiative is authorised to
make; the owner's answer is the RFC-mandate the project AGENTS.md requires.

**What was changed in the frozen zone.** Exactly one addition to `EvalBattery` — the corpus
RULE, not the corpus's items:

| Where | What |
|---|---|
| `EvalBattery.java` | `ARITHMETIC_PROBE_PATTERN` and `isArithmeticProbe(String)` — a constant pattern and a predicate. The battery's **items are untouched**, so the evaluation set and its published pass rate do not change. |
| `PromotionGate.java` | `isFrozenProbe` consults the rule after the two corpora. |

The `Random` handle that the earlier review asked to remove from `EvalBattery` was **left as it
is**: the corpus rule does not need it, and the frozen file was not to be touched beyond the
authorised change.

**THE RULE, exactly.** After the text is trimmed, the WHOLE text must be digits, ONE of the four
arithmetic operators (plus, minus, times, divided by), digits, and an OPTIONAL trailing question
mark — nothing may surround it. The pattern's exact spelling is asserted in
`ArithmeticProbeCorpusTest.theCorpusIsStillAPureFunctionOfItsSource`.

| Text | Caught? | Why |
|---|---|---|
| `100-7?` | **yes** | the owner's example: digits, operator, digits, question mark |
| `7*6` | **yes** | the owner's example, without the question mark |
| `2+3`, `12*12`, `100/4`, `50/2`, `1000*12345` | **yes** | the battery's own arithmetic family |
| `100 - 7?` | **yes** | spaces around the operator are allowed |
| `The answer is 2+3=5` | no | the expression is not the whole text — a claim that merely CONTAINS digits and an operator is ordinary content |
| `100-7 is 93` | no | two operators: not the query shape |
| `audio:frame=8 hdc_dim=256` | no | not a bare expression |
| `How many legs does a spider have?` | no | a legitimate question with numbers in it |
| `What is the chemical symbol for iron?` | no | a legitimate question with numbers in it |
| `What is 2+3?` | yes, but **by the corpus**, not by this rule | it is a battery item (`AR-5`/`RT-1`) |

**Reject-only.** The rule can only ever turn a record INTO a probe — that is, exclude it from
learning. It never turns a probe into content and never widens what may be learned from. It is a
constant pattern: no clock, no randomness, and the corpus remains a pure function of its source
(`ArithmeticProbeCorpusTest.theCorpusIsStillAPureFunctionOfItsSource`).

**What it does and does not change.** It changes the FIREWALL, not the evaluation: the battery's
items, their categories, their expected answers and the published pass rates are exactly as they
were, and the transliterated corpus still catches the probes that really leaked
(`столица франции`, `Moskva stolitsa Rossii?`, `stolitsa frantsii`, `stolitsa anglii`,
`Skolko budet 5+3?`). It does NOT quarantine anything by itself: a record is excluded only when
its id is in the ledger, as always.

**How a reviewer can tell this is the authorised change and not an unrecorded edit.** The change
is (a) one constant and one predicate in `EvalBattery`, (b) one condition in `PromotionGate`,
(c) named in the javadoc of both with the owner's answer and the question id, (d) pinned by
`ArithmeticProbeCorpusTest` (6 tests, including the boundary cases above and the corpus's
purity), and (e) visible in the machine state: the three additional ledger marks carry the
reason *"extended frozen corpus: letter-less arithmetic probe (owner D1-QUARANTINE-RESIDUAL-010)"*.
The canary's `recordedMarks` was raised from 209 to 212 for the same reason, and the canary's
`rule` field names the extended rule.

**The re-measurement.** On the real log (a copy for the measurement; the log itself untouched):
the extended rule marks **11** records of the log as arithmetic probes — the inputs `100-7?`,
`7*6` (twice), `17+25`, `144/12`, `9*9`, `63-18` — of which **3** were in the residual and are
newly quarantined.

| Measure | Before the extension | After |
|---|---|---|
| The log's sha256 | `5732004d…` | **identical** (the log was not touched; only the ledger was appended) |
| Quarantine marks in the ledger | 209 | **212** (append-only; the previous bytes are preserved) |
| Admitted / excluded | 17 / 209 | **14 / 212** |
| The learning window | 17 records, 15+/2− | **14 records, 12+/2−** |
| The residual inputs | 17 | 14 — the two arithmetic inputs are gone |

**The honesty note.** The window still holds BOTH classes (12 positive, 2 negative), so the
fidelity of 0.75 measured before the extension is still a measurement of a mixed window and not a
trivial number. Had the window become single-class, its fidelity would have been trivial and
would NOT have been presented as a gain. The residual of 14 records is what the owner decided to
leave: `gravity`, `What is gravity?`, `What is the capital of Chile?`, `What is the chemical
symbol for iron?`, `What is the capital of Australia?`, `What is the capital of Poland?`, `Which
continent is Japan located on?`, `What is the chemical symbol for gold?`, `Kakoj khimicheskij
simvol u zheleza?`, `audio:frame=8 hdc_dim=256`. Nothing was deleted, and no record was
quarantined beyond what the rule names on its own terms.

---

## 5. The evidence

```
export JAVA_HOME=$HOME/jdk25
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-brain-runtime/build/test-results && \
    ./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.*" --offline --console=plain'
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-api-gateway/build/test-results && \
    ./gradlew :matrix-api-gateway:test --tests "io.matrix.api.*" --offline --console=plain'
```

| Scope | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `io.matrix.brain.runtime.*` | 993 | 0 | 0 | 4 (one is the opt-in remediation) |
| `io.matrix.api.*` | 286 | 0 | 0 | 0 |

The classes that carry this work: `LearningIsolationTest` (12), `EpisodeQuarantineTest` (14),
`LearningWindowDefinitionTest` (4), `LearningIsolationEvidenceTest` (1),
`LearningIsolationLedgerCanaryTest` (2), `AdversarialIsolationProbeTest` (6),
`CollectionHarnessIsolationTest` (2), `ProductionLearningIsolationRemediation` (1, opt-in),
`ReReviewIsolationProbeTest` (14, the independent re-review's own probes), and in the gateway
`ProductionBrainClientProvenanceTest` (2). The JUnit XML is in
`matrix-brain-runtime/build/test-results/test/` and `matrix-api-gateway/build/test-results/test/`.

`AdversarialIsolationProbeTest` is the review's own probe file, kept and inverted: each probe
asserts the FIXED behaviour of a defect the review found, so a defect that is only written down
cannot come back silently.

---

## 6. What a fresh reviewer must re-check

1. **That the remediation is still there and still means something.** The ledger is machine state
   under the gitignored `data/mind/`. Confirm `data/mind/episodic.quarantine.ndjson` exists with
   209 events, that the log's sha256 is still
   `5732004d24956132712f6a875ef6082faa3b8df251421f07390b500e894ca11b`, and that
   `EpisodeQuarantineTool scan data/mind/episodic.ndjson` reports `admitted=17 excluded=209` and a
   window with both classes.
2. **That the rule is stable under growth.** Append ONE organic interaction to a COPY of the log
   and re-apply the rule: it must not mark the new record. (This is the defect F5 was.)
3. **That the fail-closed path really fails closed.** Corrupt one line of a ledger in a temp copy
   and confirm that the gate admits nothing and that the runtime reports the unreadable ledger.
4. **That no declaration means ORGANIC.** Check every constructor without a role:
   `EpisodicLog.Entry`, `EpisodicLog.append`, `PromotionGate.Candidate`, `MindResult`,
   `TrueMindCycle.think`. Each must default to `LEGACY_UNTAGGED`.
5. **That the provenance-free training overload still refuses**, and that no production caller
   trains without roles.
6. **That the three window definitions still agree** — the scheduler's induced ids, the log's
   `learningWindow()`, and the tool's window.
7. **The residual.** Decide whether the 17 residual records should be quarantined as well
   (section 4), and whether the frozen corpus should match the letter-less arithmetic probes with
   a trailing question mark.
8. **That the canary is armed.** Delete a COPY of the ledger and confirm
   `LearningIsolationLedgerCanaryTest` fails; and confirm that the canary JSON still holds
   `recordedRecords` / `recordedMarks` that are no larger than the machine's real numbers (the
   comparison is "at least", so a canary that overstates the marks would fail the build
   forever).

## 7. Known limits — accepted, documented, not changed

These were raised by the independent re-review as INFO. They are **accepted as they are** and
recorded here with their consequence, because each of them is a property of the design rather
than a defect that a small change would remove.

| Limit | Consequence | Why it stays |
|---|---|---|
| **The content rule quarantines a GENUINE record that happens to repeat a designed input.** RULE B matches on the record's INPUT, so an organic user who asks a question the collection harness also asked is quarantined with the designed traffic. | A real interaction can be excluded from learning. The rule cannot tell a coincidence of wording from a designed execution. | The alternative — deciding per record whether the wording is a coincidence — needs a judgement no mechanical rule can make. The ledger is reversible, so an operator can release the genuine record, and the release is in the history. |
| **The quarantine is enforced at the GATE, not at the TRAINER.** `RuleInductionEngine.induce` refuses a record whose ROLE says it is non-organic; it cannot know that a record is in the ledger, because the ledger names ids and the engine is given roles. | A training call that bypasses `LearningAdmission` (a future caller that reads the log itself) would train on quarantined records. | The gate is the single place that knows about the ledger, and the trainer is the single place that knows about provenance. The trainer's check is the second line of defence, not the first. A caller that bypasses the gate also bypasses the window's definition — which is why `LearningAdmission.assess` is the ONE read-and-admit (section 2, F8). |
| **The gate trusts a SELF-DECLARED role.** A caller that declares `ORGANIC` for evaluation traffic is believed. | A lie in the declaration puts the traffic back into the learning feed. | A role cannot be inferred from content reliably (see the first limit), so the declaration is the only handle. The gateway declares at its own boundary (`ProductionBrainClient`), the collection harness declares `EXPERIMENT`, and the quarantine covers the history that no declaration could cover. |

## 8. What was NOT verified

- The HDC retrieval substrate was not audited for earlier contamination; it is fed by
  `KnowledgeForgeIngest` and not by the query path, but that was read from the source, not
  measured.
- The gateway's other writers were not traced; only `ProductionBrainClient.cycle` writes
  episodes.
- CI was not run — only the two local scopes above.
- The arithmetic probes with a trailing question mark (`100-7?`) are still matched by neither
  rule; the residual is reported, not fixed.
- The canary was demonstrated on a COPY (the ledger deleted from the copy, the check failing
  with the message above); the real ledger was never removed, so the "build actually fails"
  path was shown by the check, not by a red build.
- Two processes appending to the same ledger at once were reasoned about (a writer lock
  serialises them) but not stress-tested.
- The F9 nit (the unused `Random` in the frozen `EvalBattery`) was implemented and then
  REVERTED, because the coordinator requires the frozen file to stay untouched. The frozen
  file in the tree still carries the handle; the corpus's purity is pinned by a test
  instead. This is a deliberate divergence from the review's recommendation, recorded here
  rather than quietly dropped.
- The `EpisodeQuarantine.readable()` contract: the pre-read state is explicit
  (`state() == NOT_READ_YET`) and the method keeps its "the last read parsed every line"
  meaning, because the independent re-review's probe pins that meaning. A caller that wants
  to know whether the ledger has been LOOKED AT must ask `state()`.
