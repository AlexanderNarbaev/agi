# W42 Research Stream A — Learning Science for the Measurement of Learning

**Wave:** MATRIX RECON-W42 (sub-stream A) · 2026-11-02
**Scope:** the learning science that governs (a) what may be called a
calibrated confidence, (b) what a consolidation may do to a fast learner
without destroying it, and (c) what a held-out measurement must mean
before a claim of learning is made.
**Status of this document:** research input for W42.4 and W42.7. It
grounds decisions that are already implemented; it proposes no new
mechanism.

---

## 0. Method, and how the citations were verified

META-R3 forbids aggregator sources for significant claims. Every source
below is a **book, a peer-reviewed paper, or an official preprint**, and
each bibliographic record was checked against the publisher's or the
archive's own landing page during this wave — not from memory.

| the source | what it is | how it was checked |
|---|---|---|
| Gneiting, T. & Raftery, A. E. (2007) *Strictly Proper Scoring Rules, Prediction, and Estimation*, JASA 102(477):359–378 | the calibration theory | the ACM DL / publisher record and the DOI 10.1198/016214506000001437 |
| van de Ven, G. M., Soures, N. & Kudithipudi, D. (2024) *Continual Learning and Catastrophic Forgetting*, arXiv:2403.05175 | the modern view of the consolidation | the arXiv record |
| Wang, J. et al. (2024) *A Comprehensive Survey of Continual Learning: Theory, Method and Application*, IEEE TPAMI, doi:10.1109/TPAMI.2024.3367329 | the survey that cites the complementary-learning-systems literature | the publisher's record and the Europe PMC entry |

---

## 1. The finding that mattered most: a support score is not a probability

**What W42 found.** The W41 epistemic model carries a `SupportScore`,
and it was explicitly marked `SupportScore.UNCALIBRATED`. W42.7 did not
change that, and this wave confirms the mark was right rather than
cautious.

**What the literature says.** Gneiting & Raftery (2007) separate two
questions that are easy to confuse: how well a forecast is *scored* on
average, and whether a stated probability is *honest*. A scoring rule is
**strictly proper** when the expected score is maximised only by reporting
the true probability — that is the condition under which a stated number
can be compared with an outcome at all. A system that reports "0.8" and
is right 80 % of the time is not thereby calibrated: the strictness of
the rule is what ties the number to the outcomes.

**What this obliges MATRIX to do.** Two things, both of which the W41
model already does:

1. **The support score must not be read as a probability.** It is a
   bounded, ordinal quantity computed from the entailment structure. It
   says "the workspace's own support for this is at this level", not "this
   is 0.8 likely". The `UNCALIBRATED` marker is the honest name for that
   gap, and W42 kept it.
2. **A calibration claim requires its own instrument.** The learner's
   forecasts in the teach-back experiment are collected with their
   claimed confidences, and the Brier score over six forecasts is
   reported (0.322 in the live run) — a proper scoring rule applied to
   the one place where MATRIX does claim confidences. The number is a
   measurement over six forecasts and is labelled as such; it is not
   evidence of calibration.

**The falsifiable consequence.** If MATRIX ever presents the support
score as a probability, this wave says it is wrong. If MATRIX ever
presents a calibration claim without a strictly proper scoring rule and
an adequate sample, this wave says it is wrong. Both are checkable.

---

## 2. The consolidation must not overwrite the fast learner

**What W42 built.** The consolidation is bounded, proposes rather than
applies, measures the workspace's answerability on the **held-out** items
before and after the proposal, keeps the proposal only if the measurement
did not fall, and rolls the batch back otherwise. The error-driven phase
weakens the association that a wrong answer shared with a concept, over
the VERIFIED errors only, and stops at the deadband's lower edge.

**What the literature says.** The catastrophic-forgetting literature —
van de Ven et al. (2024) and the TPAMI survey — treats consolidation as
the mechanism that trades stability against plasticity, and its recurring
failure mode is exactly what W42's rule forbids: a consolidation step
that improves the consolidated thing while destroying the thing that is
still being learned. The complementary-learning-systems account (see
stream B) makes the same point structurally.

**What this obliges MATRIX to do.** The acceptance measurement must be
**independent of the thing being consolidated**. W42 uses the held-out
items — the ones the learner was never allowed to practise with — and it
measures BEFORE and AFTER, so that a proposal that merely removes
evidence is visible as a fall. This is why the consolidation's report
carries an `Acceptance` record with the probe's size, the answers before,
the answers after and the rollback count: a proposal with no measurement
behind it is not a proposal.

**The falsifiable consequence.** A consolidation that lowers the
held-out answerability is rolled back. A consolidation that lowers it
and is kept is a defect, and the acceptance record is the evidence that
would show it.

---

## 3. A measurement of learning is not a measurement of the lesson

**What W42 found.** The W41 benchmark's MISSPELLED and TEACH categories
were four items each. W42.7 built a synthetic typo benchmark with a
label — the question that was MEANT — and measured the **intent
preservation**: 24 of 24. The measure is not "does the answer mention
the lesson"; it is "does the answer to the misspelled question cover
everything the answer to the intended question covered".

**What the literature says.** The teach-back and the transfer literature
share one methodological demand: the measurement must be independent of
the material that produced the answer, or it measures the material. W42
honours this by construction — the held-out items, the disjoint phase
sets, the lexical baseline that scores 0.00 where the structured learner
scores 1.00, and the leakage audit that counts how many probes restated
the lesson (0 of 6 in the live run).

**What this obliges MATRIX to do.** Every claim of learning in the W42
reports carries its instrument and its sample size. The reports say
"a machine-to-machine measurement: the learner is synthetic" and "168
cases and 34 items are a demonstration, not a generalisation" — because
the literature's demand is not honesty as a style but honesty as a
condition of the claim being usable.

**The falsifiable consequence.** A learning claim whose instrument
reuses the lesson's text is not a learning claim. The W42 instruments do
not, and the leakage audit is the check.

---

## 4. What this stream does NOT settle

- **Retention over time.** The delayed phase of the teach-back measures a
  *logical* delay (a disjoint item set after the lesson), not a wall-clock
  one — the CONSTITUTION's first article forbids the clock in the decision
  path. The real retention of a human learner over days is not measured
  and is not claimed.
- **Calibration of the support score.** It is explicitly uncalibrated, and
  this wave gives no instrument that would calibrate it. That would need a
  labelled corpus of natural statements, which the mission forbids
  manufacturing.
- **Transfer to a human.** No human has been taught by this system. The
  `MetricsReport` says so in as many words.

---

## 5. The hypotheses this stream grounds

| H | the claim | the anchor | the artifact |
|---|---|---|---|
| **H-104** | A support score computed from an entailment structure is an ordinal quantity and must not be presented as a probability; a calibration claim requires a strictly proper scoring rule and an adequate sample | Gneiting & Raftery 2007 | `SupportScore.UNCALIBRATED`; the Brier score over the teach-back's forecasts |
| **H-105** | A consolidation may only be accepted if an INDEPENDENT measurement — over items held back from the learning — does not fall; otherwise the batch is rolled back | van de Ven et al. 2024; the TPAMI survey 2024 | `KnowledgeConsolidator.Acceptance`; the rollback via `reviveQuestion` |
| **H-106** | A measurement of learning is invalid if its instrument reuses the material that produced the answer; the leakage audit is the check | the teach-back and transfer methodology | the teach-back's `restatements` counter; the lexical baseline |
