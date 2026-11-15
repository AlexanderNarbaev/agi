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
| F9 | NIT | `EvalBattery.standardBattery()` carried an unused `Random r = new Random(42L)` inside the frozen corpus builder — an invitation for the next change to make the firewall non-reproducible. | Removed, together with the import. |
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
| `io.matrix.brain.runtime.*` | 968 | 0 | 0 | 4 (one is the opt-in remediation) |
| `io.matrix.api.*` | 286 | 0 | 0 | 0 |

The classes that carry this work: `LearningIsolationTest` (12), `EpisodeQuarantineTest` (12),
`LearningWindowDefinitionTest` (3), `LearningIsolationEvidenceTest` (1),
`AdversarialIsolationProbeTest` (6), `CollectionHarnessIsolationTest` (2),
`ProductionLearningIsolationRemediation` (1, opt-in), and in the gateway
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

## 7. What was NOT verified

- The HDC retrieval substrate was not audited for earlier contamination; it is fed by
  `KnowledgeForgeIngest` and not by the query path, but that was read from the source, not
  measured.
- The gateway's other writers were not traced; only `ProductionBrainClient.cycle` writes
  episodes.
- CI was not run — only the two local scopes above.
- The arithmetic probes with a trailing question mark (`100-7?`) are still matched by neither
  rule; the residual is reported, not fixed.
