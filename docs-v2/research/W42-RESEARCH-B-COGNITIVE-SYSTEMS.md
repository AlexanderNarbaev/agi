# W42 Research Stream B — Cognitive Systems for a Consolidation That Proposes

**Wave:** MATRIX RECON-W42 (sub-stream B) · 2026-11-02
**Scope:** the cognitive-systems account of (a) what a consolidation is
*for*, (b) what an independent acceptance measurement is, and (c) why
the W42 consolidation marks rather than deletes.
**Status of this document:** research input for W42.4 and W42.7. It
grounds decisions that are already implemented; it proposes no new
mechanism.

---

## 0. Method, and how the citations were verified

META-R3 forbids aggregator sources for significant claims. Every source
below is a **book, a peer-reviewed paper, or an official preprint**, and
each bibliographic record was checked against the publisher's or the
archive's own landing page during this wave.

| the source | what it is | how it was checked |
|---|---|---|
| McClelland, J. L., McNaughton, B. L. & O'Reilly, R. C. (1995) *Why there are complementary learning systems in the hippocampus and neocortex*, Psychological Review 102(3):419–457 | the complementary learning systems | the publisher's record and the DOI 10.1037/0033-295X.102.3.419 |
| Anokhin, P. K. (1935) — the functional system's *acceptor of the action's results* and the reverse afferentation | the cybernetics account of a comparison against an expectation | the modern peer-reviewed literature that states the terms explicitly, and the Soviet tradition's own wording |
| Bernstein, N. A. (1947) *On Dexterity and its Development* — the levels of construction | the account of the model's own levels | the primary monograph; already carried by DESIGN-55 |

---

## 1. What a consolidation is FOR

**What W42 built.** The W42 consolidation has two halves that the
account below explains as one thing: it **proposes** (it computes the
candidates for forgetting and measures the proposal's effect before
keeping it), and it **integrates** (it takes the VERIFIED learning
errors and weakens the specific association that a wrong answer shared
with a concept).

**What the literature says.** McClelland, McNaughton & O'Reilly (1995)
give the canonical division of labour: a **fast** system learns the
current task quickly and is plastic; a **slow** system learns the
structure of the regularities slowly and is stable; and the fast system
replays to the slow one so that the slow system can learn what the fast
system is learning, without the fast system's own learning being
destroyed. The consolidation's role in that account is *not* to replace
learning — it is to transfer structure without collision.

**What this obliges MATRIX to do.** Three things the W42 consolidation
already does:

1. **The fast system is the learner; the consolidation is not.** The
   consolidation never changes a claim's status and never rewrites a
   claim. It works on the *dialogue's memory* and on the *learner's
   associations* — the two places where the replay's target lives.
2. **The consolidation is bounded and the bound is declared.** A fixed
   number of records per run, a fixed number of phases, and the report
   says how much was examined, how many candidates the rules named, and
   how many were actually marked. The distinction between "the rules named
   sixty" and "the run marked five" is the W42 correction of a field that
   used to conflate them.
3. **The consolidation proposes.** The slow system does not overwrite the
   fast one; it proposes a change and the change is measured before it is
   kept.

**The falsifiable consequence.** If a consolidation run changed a claim's
status, it would violate this stream's account — and the invariant list
says so in the report itself ("No claim was changed").

---

## 2. The acceptance measurement is an acceptor

**What W42 built.** The consolidation probes the workspace's answerability
on the held-out items before and after the proposal, and rolls the batch
back if the answerability fell.

**What the literature says.** Anokhin's functional system is the classic
statement of the pattern: a system has an **acceptor of the action's
results**, a model of what the result should be, and the result is
compared against it by reverse afferentation; a mismatch does not merely
report an error, it *corrects the course*. The acceptor is what makes
the system a system rather than a chain of reflexes.

**What this obliges MATRIX to do.** The acceptance measurement must be
an **expectation formed before the action**, compared against the result
after it. W42 does exactly that and nothing more: the probe before, the
proposal, the probe after, and the comparison. It does not, for example,
measure the answerability on the items the proposal touched — that would
be measuring the proposal's own footprint rather than the system's
health.

**The falsifiable consequence.** The `Acceptance` record carries the
probe's size and both sides of the comparison. A proposal accepted
without a probe on both sides would be a claim without an acceptor, and
the record would show an empty probe.

---

## 3. Marking is not deleting

**What W42 built.** A forgotten record is **marked** — with the instant
and the grounds — and never deleted. The mission's §8 is explicit: the
forgetting must not silently destroy the historical evidence.

**What the literature says.** A consolidation that cannot be inspected
cannot be trusted; the slow system's advantage is precisely that its
acquisitions are structured and re-inspectable, while the fast system's
are transient. Marking is the form that takes here: the record's
content survives, and what changes is its status. The complementary
systems account also suggests the direction of the change — a record
that has not been used is not thereby false, and the system's own
epistemic axis (`ClaimStatus`, `ClaimWarrant`, `DerivationStatus`) keeps
those three apart.

**What this obliges MATRIX to do.** The forgetting is *bounded, marked
and reversible within the run*: the rollback path (`reviveQuestion`)
brings back the whole batch when the acceptance fails, and the report
carries the rollback count. The knowledge itself is untouched — no
claim's status changes, so a claim does not become false because it was
unused.

**The falsifiable consequence.** After a consolidation, the forgotten
records must still be present and marked. The mission's §14 row 10
measures exactly this: 74 records examined, the claims 24 before and 24
after.

---

## 4. What this stream does NOT settle

- **The hard problem of consolidation.** This stream explains what the
  consolidation *is for*; it does not explain how a general system
  consolidates structure it was never taught. MATRIX's consolidation
  works on the records the dialogue produced and on the associations the
  learner formed — a narrow but real slice.
- **The replay's schedule.** There is none: the consolidation runs when
  it is called, with the instant supplied by the caller. The
  CONSTITUTION's first article forbids the clock in the decision path, and
  no biological schedule is claimed.
- **The `ConsciousBrain`'s role.** It remains unwired, and naming the
  capability it would serve is an open question for the owner. This
  stream does not pretend otherwise.

---

## 5. The hypotheses this stream grounds

| H | the claim | the anchor | the artifact |
|---|---|---|---|
| **H-107** | A consolidation transfers structure to the slow system without overwriting the fast one; it therefore works on the dialogue's memory and the learner's associations, and never on a claim's truth | McClelland, McNaughton & O'Reilly 1995 | `KnowledgeConsolidator`; the invariant "No claim was changed" |
| **H-108** | A change that touches the fast system is a PROPOSAL until an independent measurement formed before the change shows it did not degrade the system; the rollback restores the whole batch | Anokhin's acceptor of the action's results | `KnowledgeConsolidator.Acceptance`; `reviveQuestion` |
| **H-109** | Forgetting in an inspectable system is a marking, not a deletion: the record's content and its grounds survive, and the rollback path exists | the complementary-systems account of structured acquisition | the `AskedQuestion.forgotten` mark; the rollback count |
