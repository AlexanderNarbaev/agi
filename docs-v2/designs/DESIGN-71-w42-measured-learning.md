# DESIGN-71 — The W42 Measured-Learning Design

**Status:** normative design · RECON-W42 · 2026-11-02
**Author:** Coordinator, RECON-W42
**Supersedes:** nothing. **Amends:** the measurement claims of
`docs-v2/engineering/RECON-W42-BENCHMARKS.md` and the consolidation's
invariants.
**Builds on:** CONSTITUTION Articles I, IV and VI; `DESIGN-70` (the three
epistemic axes); `docs-v2/engineering/W42-INTEGRATION-MATRIX.md` (the
integration census).
**Research:** `docs-v2/research/W42-RESEARCH-A-LEARNING-SCIENCE.md`,
`W42-RESEARCH-B-COGNITIVE-SYSTEMS.md`,
`W42-RESEARCH-C-KNOWLEDGE-ENGINEERING.md`.
**Hypotheses:** H-104..H-112.

---

## 1. What this design is

The W42 wave built instruments. This design states what those instruments
are allowed to claim, and what they are not. It exists because the
wave's own history showed the failure mode: an instrument that measures
the wrong thing produces a confident number — and the number is the part
that gets believed.

The three research streams each produced a rule that tightened a claim:

- **Stream A** (learning science): a support score is ordinal; a
  calibration claim needs a strictly proper scoring rule; a consolidation
  must be measured independently before it is kept.
- **Stream B** (cognitive systems): a consolidation proposes; the
  acceptance is an acceptor formed before the action; forgetting marks.
- **Stream C** (knowledge engineering): a lexical ranking measures, it
  does not answer; a typo correction is a hypothesis; a translation is a
  warranted claim about an inferential relation.

## 2. The rules

| # | the rule | the instrument that enforces it |
|---|---|---|
| **R-1** | A number is a claim about a measurement, and the report names the measurement, the sample and the instrument | the §14 acceptance table; every report's `invariants` list |
| **R-2** | The support score is not a probability, and is marked `UNCALIBRATED` | `SupportScore.UNCALIBRATED` |
| **R-3** | A calibration claim requires a strictly proper scoring rule and an adequate sample | the Brier score over the teach-back's forecasts, reported with its count |
| **R-4** | A consolidation that touches the fast system is a proposal until an independent held-out measurement shows no fall | `KnowledgeConsolidator.Acceptance`; the batch rollback |
| **R-5** | A learning measurement whose instrument reuses the lesson is invalid | the teach-back's `restatements` audit; the lexical baseline |
| **R-6** | A lexical ranking never stands in for the answer's gate | the entailment checker, the contradiction detector, the three epistemic axes |
| **R-7** | A typo correction adds; it never rewrites, and an ambiguous correction is refused | `TypoCorrector`; the synthetic typo benchmark's intent label |
| **R-8** | Forgetting marks; it never deletes, and a knowledge claim does not become false because it was unused | the `forgotten` mark; "No claim was changed" |
| **R-9** | A measurement of the code's integration is reproducible, and a syntactic reference is not a workflow | `scripts/integration-census.py` |
| **R-10** | An integration status is asserted with cited evidence; the census screens it and does not replace the judgement | the matrix's `Evidence` column |

## 3. What the rules changed in the implementation

The rules are not decoration. Each of them corrected something that was
measurably wrong:

- **R-4 and R-8** — the consolidation's invariant claimed that "a second
  run over an unchanged workspace forgets nothing", and the live
  measurement says otherwise: a bounded consolidation with more to forget
  forgets more. The invariant now says that, and the report's `marked`
  field holds the records the run actually marked, not every candidate
  the rules named.
- **R-7** — the typo benchmark read 0.292 while the system answered every
  misspelled question with the reference's answer. The fault was the
  benchmark's, twice: the label was attached to one case of each concept,
  and then the bar was the concept's key terms rather than the intended
  answer. The measured number is now 24 of 24, and the benchmark's own
  history is recorded rather than tidied away.
- **R-9 and R-10** — the census found six stale rows in the integration
  matrix, four of them genuinely: the `DistillationLedger` was marked
  disconnected although its snapshot is read back into the mind's state,
  and the `FederationRegistry` row conflated two different classes that
  share a name.
- **R-1** — the archive was reproducible only halfway, because
  `java.util.Properties.store` writes the current time into a comment and
  the build metadata carried the wall clock into the jar. Two clean builds
  now produce the same jar.

## 4. What this design does NOT claim

- **It does not claim human learning.** The learner in the teach-back is
  a script. The `MetricsReport` names "transfer to a human learner" as
  not measured.
- **It does not claim generality.** The benchmark's 168 cases and 34 items
  are a demonstration. The typo benchmark's 24 cases are a demonstration
  over a fixed seed.
- **It does not claim that the knowledge can be translated.** The frame
  is translated; the knowledge is not, and that limit is recorded as a
  limit.
- **It does not claim that the consolidation is a general one.** It works
  on the dialogue's records and the learner's associations — a narrow but
  real slice.
- **It does not claim the full repository suite passes.** It has not been
  re-run; the targeted packages are 166 classes and 1486 tests, and the
  full run needs the owner's agreement because of the OOM risk.

## 5. The continuation

The W42's remaining open questions are the owner's, not the system's:
the six shadow classes (wire or delete), the capability `ConsciousBrain`
would serve, the path for translating the knowledge, the one ambiguous
benchmark item against §6, and the full test suite. Each is recorded in
`docs-v2/engineering/RECON-W42-BACKLOG.md` with the reason it is
waiting, and none of them stops the wave's rules from being true.
