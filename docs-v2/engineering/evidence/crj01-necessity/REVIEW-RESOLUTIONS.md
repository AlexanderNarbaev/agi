# CRJ-01 / EPISTEMIC-NECESSITY — the implementer's responses to the independent review

**Reviewer:** `docs-v2/engineering/evidence/crj01-necessity/REVIEW.md` (independent DSH reviewer,
**0 BLOCKER, 9 MAJOR, 8 MINOR, 4 NOTE**) · **Date:** 2026-11-28

This file is the coordinator's response to the review. It does **not** amend the review: the
reviewer's file is retained as it was written, and every response below says **what was
changed, in which artefact, and how it was verified**.

---

## The MAJOR findings

| # | finding | resolution | verified by |
|---|---|---|---|
| 1 | **the RED reproduction fails with the derivation ON as well as OFF** — the scope held every source, the proposal admitted it, so the assertion was false for any reason | the reproduction now **finds the question and the scope on which the two rules disagree** — the incumbent admits, the rule refuses — by **asking both rules**; it asserts the "on" verdict first and only then the "off" verdict, so the derivation is the **only** difference between the two assessments. No oracle label, case id or gold is read to find the scope. | `matrix-core/src/test/java/io/matrix/knowledge/EpistemicNecessityRedReproduction.java`; the retained XML now fails **at the intended assertion**, with the fallback's own reason in the message |
| 2 | **the "identical inputs" gate is tautological** — 144 rows carried exactly 6 distinct fingerprints because the fingerprint was computed once per grid | each arm now takes **its own snapshot** of the workspace and is fingerprinted from **what it read**; the fingerprint's material is the workspace and what it holds, and deliberately **not** the export instant, so two snapshots of an unchanged workspace are the same input. The gate's one-fingerprint-per-cell check can now fail. | `Crj01NecessityGrid.java`; `scripts/necessity-gate.py` |
| 3 | **under `SUPERSEDED` the oracle's "carries" diverged from the served path's** — 8 rows contradicted themselves (`oracleAdequate=true` and `evidenceComplete=false`), and the headline numbers moved on re-scoring | "carries" now means **the view holds a claim that states the sentence and that claim's source is in the scope**. The numbers were **re-measured** and the milestone **re-issued**: `ANCHOR` TP 30→**29**, FP 18→**19**, FPR `18/42 = 0.4286`→**`19/43 = 0.4419`**; proposal FP 9, FN 6→**5**, FNR `6/30 = 0.2`→**`5/29 = 0.1724`**. The qualitative trade survives; the published numbers did not, and they were replaced. | `Crj01NecessityGrid.java`; the milestone's §4, §4.1; the re-measured rows and summary |
| 4 | **the follow-up round's uniqueness rule is violated; §2's collision count is wrong; the checker has a no-op where that check belongs** | the dead no-op is **removed**; the **whole-domain** rule of contract §12 ("a case's necessary sentence must not be a sentence another case of the same domain already uses") is now checked by **the gate** — the milestone's half — over every assignment of a domain, not only within one file. The collisions the review found were **returned to their authors** (`A1`, `A2`, `A5`), who replaced the offending cases; the milestone's §2 now reports the rule and the final state, not a count the reviewer could not confirm. | `scripts/necessity-case-check.py` (no-op removed), `scripts/necessity-gate.py` (whole-domain check), the authors' own files, the milestone's §2 |
| 5 | **the case set's own provenance records four authors where there are six** | the assembler and the suite's display name now say **six** — four in the first round and two in the follow-up — and the assembled case set carries that record. | `scripts/necessity-cases.py`, `EpistemicNecessityServedTest.java`, `CRJ-01-NECESSITY-DEV-CASES.json` |
| 6 | **an offer can be counted as fulfilled for evidence the workspace does not hold** | a fourth disposition **`REFUSED_UNVERIFIED`** refuses an offer of a claim the workspace does not hold, **against a request for evidence the workspace holds** (a request for evidence the workspace does *not* hold is an `OBSERVATION`, and new evidence is exactly what it is for). The journey exercises it on a fabricated claim that names a source the workspace has. | `EvidenceAcquisition.java`, the journey's refusal block |
| 7 | **the test named "the EXACT revised procedure serves a DIFFERENT task" never revises anything** | it now **promotes the narrow competence, learns and validates the wider one, retires the predecessor with a reason and a causal link, restarts, and serves the exact revised version**; the retired version has no active version after the restart and the route **refuses** it; the matched stateless reference and the exact replay are both asked on the same task, and the replay's outcome is **measured** (it answers what it recorded and refuses what it has not). | `EpistemicNecessityServedTest.java` — the restart test |
| 8 | **the journey's failure case has an unreachable gold; §5 and §5.1 say the opposite** | the journey now takes the measurement's **false admission** and **measures** the reference's reach instead of asserting that the reference carries the gold; the milestone's §4.2 and §5.1 now state the finding plainly — **every one of the incumbent's 19 false admissions sits on a task whose necessary evidence the workspace's reader does not reach** — and §5 no longer claims a comparison the measurement does not support. | `EpistemicNecessityServedTest.java`; the milestone's §4.2, §5, §5.1 |
| 9 | **`goldReachable` does not mean what it says; the reachability report is not re-derivable from it** | reachability is measured on the **reference reading** — the workspace's own reading of the task over the whole view — and not on the served answer, which made a narrow scope make an unreachable gold look reachable. The rows carry it, and the milestone reports it from the rows. | `Crj01NecessityGrid.java`; the milestone's §4.2 |

## The MINOR findings

| # | finding | resolution |
|---|---|---|
| 10 | the gate's rate check is a tautology and does not re-derive the denominators it claims to re-derive | the gate now **computes** every rate's numerator and denominator from the rows' own counts and compares them with the summary's — a denominator of zero must carry no value, and a non-zero one must carry one |
| 11 | `recordRevalidated` always writes version `1.2.0`, so a second revalidation collides with the store's immutability rule | the version is now **derived** from the version being revalidated (`versionForRevalidation`), so a second revalidation of the same competence does not collide with the first |
| 12 | `lastAcquisitionRequest()` can return a request that does not correspond to the last refusal | a successful run now **clears** the remembered request, so it always answers for the **last refusal** |
| 13 | the retained evidence is written by the suite, is not byte-reproducible, and its manifest is verified by nothing | the manifest is now **verified against the files' own digests** by the gate; the non-reproducibility of the wall-clock timing fields is stated here (the summary is byte-identical between runs; the rows differ in three timing fields) |
| 14 | "applicability" names two different decisions, so the claim that the four things are kept apart is not verifiable | the milestone's §3 now says which decision the word means — the route's — and states that the milestone does not change it |
| 15 | the evaluation slice covers 5 of the 9 conditions, and §4.1 does not say so | §4.1 now names the five conditions and the four that the slice rule holds out |
| 16 | "18 scopes" and "9 and refused 6" are row counts, not scope counts | §1 and §4 now say **measured decisions** |
| 17 | §4's "The oracle is read after each arm has served" is false about the instrument's order | §4 now says what the oracle **is**: a property of the (case, scope) pair and the workspace, computed from the case's own sentences, the scope's source ids and the view's claims — **never from an arm's outcome** — and it states the instrument's order as it is |
| 18 | the RED XML is never gated | the gate now requires the RED reproduction to be **present and failing** |

## The NOTE findings

| # | finding | response |
|---|---|---|
| 19 | the authors' files are revisable, so the digests cannot witness a post-hoc edit | **agreed, and stated.** The digests make an edit **visible**; they cannot prove authorship after the fact. What the milestone can show is that **no author file was edited by the implementer** — the collisions were found by the milestone's own whole-set check and returned to the authors, who made the replacements. |
| 20 | the derivation switch is a mutable static in production code | **agreed, and stated.** It is package-private, off in every normal run, and read in exactly one place — but it is still a seam in production code. Removing it would mean constructing the rule with its derivation, which is a larger change than this milestone is authorised to make. |
| 21 | the milestone document was written during the review | **agreed, and corrected.** The document has been **re-issued** against the re-measured rows, and this file records what changed. |

## The coordinator's acceptance — three boundary corrections

The coordinator's **final acceptance** found three boundary issues in the milestone's own
artefacts, after the review above had been answered. They are corrected in the milestone's
§9a and summarised here.

| finding | resolution |
|---|---|
| `ArtifactReportResource.lastOfferedMethods` — shared mutable last-request state carrying workspace method objects and typed acquisition request metadata | **removed**. The typed acquisition metadata is **request-local**: the journey collects it in a request-local collector while it runs and carries it in the `JourneyResult` as an immutable list of immutable records. The two-argument `JourneyResult(response, outcome)` constructor is kept, so the current call sites compile and behave as before. The suite asserts the absence of the bleed directly — two journeys of **different** questions over the same resource, each carrying only its own request — and asserts that the resource offers no such accessor at all. |
| the mutable static RED switch in production code | **removed**. The derivation of the task's prerequisites is now an **immutable input of the workbench**: every workbench the product builds derives, and the factory that builds one that does **not** is **package-private**, so only the reproduction can ask for the negative control. The served behaviour is unchanged, and the reproduction still fails at the same assertion for the same reason. |
| a separately validated replacement is exercised, but an atomic durable same-scope narrowing is not implemented | **stated**. The suite exercises a **separately validated replacement** — a wider competence learned and validated against the held-out slice, the predecessor retired afterwards — and the store implements **no** atomic durable narrowing of the same scope: the two are **separate writes**, and a crash between them leaves the narrowing half-done. |

## What the review got right about the milestone

The review re-derived **every published number** from the retained rows and found them exact —
the confusion matrix, all four rates with their denominators, the 15 differing cells and the
per-condition table, including the four undefined denominators. It also found the one thing
that mattered most: the **oracle's "carries" was not the served path's "carries"**, and the
correction moved the headline. That finding is the reason the numbers in the milestone are the
numbers they are now.
