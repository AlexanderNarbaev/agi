# CRJ-01 / COMPETENCE-REACH — the re-check of the resolutions

**Reviewer:** the same independent DSH reviewer, on the final stable snapshot after the fixes ·
**Date:** 2026-11-27 · **Scope:** every finding `F1`…`F24` of `REVIEW.md`, re-derived from the
**current** retained rows and summary, the current production and test sources, the current gate
and self-test, the current governance records and the current checkpoint.

**Method.** No Gradle was run (per instruction). Every number was re-derived with `python3` over
`CRJ-01-REACH-GRID-ROWS.json` and `CRJ-01-REACH-GRID-SUMMARY.json` as they stand now; the gate,
the self-test and the two validators were executed; the production sources were read; the gate was
probed again with seven fresh mutations (in a temporary copy, with the copy's manifest refreshed
first, so that no mutation could be caught by the manifest alone). No file other than this report
was modified.

---

## VERDICT

**The milestone's headline claims are now supported by its own evidence — with one MAJOR residue
that is a repeat of the original BLOCKER in two non-authoritative artefacts.** Every number I
disputed is now correct in the milestone document, and I re-derived all of them from the current
rows: the strategy axis (same scope at `BASELINE`/`CHANGED_BINDINGS`, different at `SUPERSEDED`),
the criterion axis (189 of 216 case-arms agree, 27 differ), the `CHANGED_BINDINGS` flips (2 of
144), the `SUPERSEDED` false rejections (0 and 3), the admitted-and-incomplete admissions (3 of the
32 admissions of the declared arms), the `BASELINE` and grid-wide false-rejection split (5 of 5;
7 of 8), the 67 false admissions broken down by kind to the unit, and the §9 premise (eight cases
of 24 with two or more necessary claims; one blind-spot instance needing a single claim). The two
falsifications, the unchanged served default and the honest role record still hold. **But the
evidence README and the catalogue's `what_it_does_not_prove` still say that the citation-derived
and the evidence-derived scope "produce the same scope in every cell"** — the sentence this
review called a BLOCKER — and the catalogue, the registry, the milestone document's own §8.2
table, `MANIFESTS.md` and the checkpoint still quote the **old** gate sizes (17 checks / 6
mutations) and, in three places, the **old** completeness metric. Thirteen MINOR items remain,
all of them stale numbers or stale documentation, none of them touching the measurement. The
milestone may be integrated as a DEV result once the MAJOR sentence is corrected; the MINOR
sweep should ride with it.

---

## 1. THE STATUS OF EVERY FINDING

| finding | severity then | status now | where it stands |
|---|---|---|---|
| F1 | BLOCKER | **RESOLVED in the milestone document and the registry; OPEN in two other artefacts** | see M1 below |
| F2 | MAJOR | **RESOLVED** | §3.3 now says 2 of 144; verified |
| F3 | MAJOR | **RESOLVED** | §3.3 now says 0 and 3; verified |
| F4 | MAJOR | **RESOLVED** | §3.2 now says 3 of 32 of 124; verified |
| F5 | MAJOR | **RESOLVED** | §3.2 point 4 now says 5 of 5 at `BASELINE`, 7 of 8 over the grid; verified |
| F6 | MAJOR | **RESOLVED** | §9 rewritten; verified |
| F7 | MAJOR | **RESOLVED as to the demonstrated fooling** | 4 new checks, 11 self-test mutations, manifest refreshed first; residual gaps in m7 |
| F8 | MAJOR | **RESOLVED** | display name + `Crj01ReachCases` header; the XMLs were re-retained |
| F9 | MAJOR | **PARTLY RESOLVED** | the corpus XML is retained and named; the gate counts are stale — m5 |
| F10 | MINOR | **PARTLY RESOLVED** | the learner's Javadoc is fixed; the enum's is not — m9 |
| F11 | MINOR | **RESOLVED** | §1 uses only the two pre-registered terms, per cell and per axis |
| F12 | MINOR | **RESOLVED** | the breakdown now sums to 67 and lists every kind |
| F13 | MINOR | **RESOLVED** | the rows carry 33 fields and the README says 33 |
| F14 | MINOR | **RESOLVED in the instrument; the replacement claim is false** | m1 |
| F15 | MINOR | **RESOLVED** | the verdict now reads the admissions |
| F16 | MINOR | **RESOLVED in the summary and the table; two sentences are stale** | m2 |
| F17 | MINOR | **RESOLVED in the instrument; one sentence is stale** | m10 |
| F18 | MINOR | **RESOLVED in substance** | a wording residue — m13 |
| F19 | MINOR | **PARTLY RESOLVED** | the README and the provenance are fixed; the milestone document, `MANIFESTS.md`, the kind list and the validation layout are not — m8 |
| F20 | MINOR | **RESOLVED** | `byKind` per cell per arm, re-derived by the gate |
| F21 | MINOR | **RESOLVED** | the generator says 40 cases |
| F22 | NOTE | **RESOLVED** | the manifest now covers `REVIEW.md` (see n2) |
| F23 | NOTE | **RESOLVED** | the README states that the grid is an instrument |
| F24 | NOTE | **RESOLVED** | recorded in §8.3 |

**Counts: 15 findings fully resolved, 9 resolved in substance with a residue (M1, m1…m10, m13),
0 unresolved as originally stated.**

---

## 2. WHAT I RE-DERIVED, FINDING BY FINDING

### F1 — the strategy axis · RESOLVED in the document and the registry, OPEN elsewhere

§1 and §3.1 now say what the rows say, and I verified every clause:

* "At `BASELINE` and `CHANGED_BINDINGS` the citation-derived scope and the evidence-derived scope
  are **the same scope**" — **verified**: the two scope **sets** are equal in all four of those
  cells (`wbe/BASELINE`, `wbe/CHANGED_BINDINGS`, `hpd/BASELINE`, `hpd/CHANGED_BINDINGS`), 4 sources
  each.
* "At `SUPERSEDED` they **differ** (the citation-derived scope narrows to three documents … while
  the evidence-derived scope keeps four)" — **verified**: 3 vs 4 in both domains, and the sets are
  not equal (the evidence-derived scope is a strict superset that still holds the superseded
  source).
* "`wbe` 8 against 12 admissions, `hpd` 7 against 11" — **verified** exactly
  (`CITATION_DERIVED/ANCHOR` vs `EVIDENCE_DERIVED/ANCHOR` at `SUPERSEDED`).
* "The **declared** scope is three documents everywhere and differs from both in every cell" —
  **verified**: 3 sources in all six cells, and its set differs from the citation-derived set in
  every cell (at `SUPERSEDED` the two have the same *size* but a different *set*: the declared
  scope keeps `src-54aba107` and drops `src-6bb14557`).
* The registry's `test_evidence` and `limitations` now carry the same per-cell statement.

**But the same false sentence survives in two other artefacts** — see **M1**.

### F2 — `CHANGED_BINDINGS` · RESOLVED

§3.3 now says "the condition changes **2 of the 144** verdicts against `BASELINE`, both in the
**declared** arm of `hpd` (one case that was admitted is refused, one that was refused is
admitted); the other 142 verdicts are identical". **Verified**: comparing the 144 `BASELINE` rows
with the 144 `CHANGED_BINDINGS` rows on `admitted`, exactly two differ — `hpd/DECLARED/ANCHOR`
`RC-HPD-13` (admitted → refused) and `RC-HPD-20` (refused → admitted). The claim is now true, and
the sentence no longer hides the flips behind an aggregate.

### F3 — the `SUPERSEDED` false rejections · RESOLVED

§3.3 now says "the anchor admits 7 with **0** false rejections, the claim-sufficiency rule admits
1 with **3**". **Verified** against the rows and the summary: `hpd/SUPERSEDED/CITATION_DERIVED`
`ANCHOR` → `falseRejections: 0` (denominator 5), `CLAIM_SUFFICIENCY` → `falseRejections: 3`
(denominator 11); the summary's `criterionDifference` records `falseRejections: 3`. The wrong
definition ("refused and carries", without answerability) is gone from the prose.

### F4 — the admitted-and-incomplete admissions · RESOLVED

§3.2 now says "**3 of the 32 admissions of the declared arms** (of 124 in the whole grid) were
admitted **and** incomplete, and all three are in the **declared** scope of the anchor — a
multi-source case answered `ANSWERED`, a misleading-relevance case abstained, and an ambiguous
case qualified". **Verified**: the three are `wbe/DECLARED/ANCHOR` `RC-WBE-14` (`MULTI_SOURCE`,
`ANSWERED`), `hpd/DECLARED/ANCHOR` `RC-HPD-13` (`MISLEADING_RELEVANCE`, `ABSTAINED`) and
`hpd/DECLARED/ANCHOR` `RC-HPD-16` (`AMBIGUOUS`, `QUALIFIED`); the declared arms hold **32**
`BASELINE` admissions of the 48 declared rows, and the whole grid holds **124**.

### F5 — where the false rejections live · RESOLVED

§3.2 point 4 now says "At `BASELINE` the false rejections of the wide scopes are zero: all **5**
false rejections of that condition belong to the `DECLARED` arm … Over the whole grid … at
`SUPERSEDED` … **7 of the 8** false rejections are on the **wide** scopes". **Verified**: 5
`BASELINE` false rejections, all on `DECLARED` arms; 8 over the grid, 7 of them on
`CITATION_DERIVED` arms and all 7 at `SUPERSEDED`.

### F6 — the §9 premise · RESOLVED

§9 now says "**eight** of the twenty-four evaluation cases carry two or more necessary claims, and
**one of the three anchor blind-spot instances needs only ONE** — `RC-HPD-13`" and restates the
blind spot as "the scope holds the claim the reading cites FIRST but not a claim the answer
NEEDS". **Verified**: exactly eight `EVALUATION` cases carry ≥2 necessary claims
(`RC-WBE-14/15/16/19`, `RC-HPD-14/15/16/19`), and `RC-HPD-13` has one. The premise is now the
one the rows support, and the "next decision" is built on the corrected statement rather than on
the refuted one.

### F7 — the gate · RESOLVED as to the demonstrated fooling

The gate now has **21 checks** (I ran it: *checks 21, failed 0*) and the self-test has **11
mutations** (I ran it: *mutations 11, caught by the right check 11*). The four new checks are
real and are the ones I asked for:

1. *"every row names the case of the case set it measures, with that case's own kind and
   answerability"* — crosses every row with the case set (kind, answerability, slice, and the
   `arm`/`strategy`/`criterion` consistency);
2. *"every arm's scope is the scope the summary records, and its validation outcome is consistent
   with its own agreement"* — the rows' scope against the summary's scope, and `validated`
   against `agreement ≥ 1.0`;
3. *"the six arms of every cell were measured against ONE input"* — the new `inputFingerprint`;
4. *"the breakdown by the kind of the case is re-derived from the rows"* — the new `byKind`.

I re-ran my four original fooling mutations against the new gate with the manifest refreshed
first: **every one of them is now caught** (a mislabelled kind, a forged scope, a falsified
validation outcome, a falsified breakdown), and a row moved between cells is now caught by **five**
checks. The self-test refreshes the copy's manifest before each mutation, so no mutation is caught
by the manifest alone — the defect I reported. The residual gaps are in **m7**.

### F8 — the false role attribution · RESOLVED

The suite's display name now reads *"the case set is the **IMPLEMENTER's own**, the slices are
disjoint, and every gold is a sentence of the corpus bytes"* and that string is in the re-retained
GREEN and RED XMLs; `Crj01ReachCases`' header now says *"as the IMPLEMENTER wrote it"* and states
plainly that *"**The IMPLEMENTER wrote both the cases and the measurement**"*. The vacuous
sentence about "the case author owns the gold" is gone. The GREEN XML (8 tests, 0 failures) and
the RED XML (8 tests, 2 failures) both carry the corrected name.

### F9 — the governance records · PARTLY RESOLVED

Fixed: the corpus instrument's XML **is** now retained (`TEST-io.matrix.crj.corpus.Crj01ReachCorpusReportBuilder.xml`,
1 test, 0 failures, its own console output "domains 2 sentences 70 claims 70 citable 70 missing 0")
and is named in the milestone document's evidence table and in the catalogue; the catalogue's
`methods_executed` is now **9** and it says "carries 2 failures of 8"; the registry's counts are
now "9 targeted tests over 2 classes (8 in the served suite and 1 in the corpus instrument)". Not
fixed: the catalogue's `what_it_does_not_prove` and the gate entries, and the registry's gate
description — see **M1** and **m5**.

### F10 — the `DECLARED` Javadoc · PARTLY RESOLVED

`ReportProcedureLearner.scope(...)` now carries *"Why the declared scope **BOUNDS** the learning
instead of refusing it"* and names the pre-registration's §10 and its reason. **`ScopeStrategy.DECLARED`'s
own Javadoc still says the opposite** — see **m9**.

### F11 — the verdict vocabulary · RESOLVED

§1 now states the vocabulary up front ("**DIFFERENCE OBSERVED** and **NOT ESTABLISHED**, and
nothing else") and uses it per cell and per axis. The summary's `verdictVocabulary` is unchanged
and still the two pre-registered terms.

### F12 — the false-admission breakdown · RESOLVED

§3.2 now enumerates every kind and the enumeration sums to the total: 12 `INCOMPLETE` + 20
`MISLEADING_RELEVANCE` (15 `QUALIFIED`, 5 `ABSTAINED`) + 10 `CORROBORATED` + 12 `PLAIN`/`PARAPHRASE`
+ 6 `MULTI_SOURCE` (5 `QUALIFIED`, 1 `ANSWERED`) + 1 `AMBIGUOUS` + 6 `UNSUPPORTED` = **67**.
**Verified** against the rows by kind and by (kind, disposition): exactly that.

### F13 — the row shape · RESOLVED

The rows carry **33** fields (the new `inputFingerprint`) and the README now says "432 rows, 33
fields each, including the **fingerprint of the view** every row was measured against".

### F14 — the golds · RESOLVED in the instrument; the replacement claim is false

The suite now asserts what matters and can fail: it builds the gold set **per slice** and asserts
that no gold of one slice is a gold of another (the vacuous per-case assertion is gone). **The
property holds**: no necessary gold crosses a slice (0 of the 40 cases' golds). But the provenance's
replacement sentence is false — see **m1**.

### F15 — the verdict reads the admissions · RESOLVED

`criterionDifference` now carries `admissions` and the verdict is computed over it. E.g.
`hpd/SUPERSEDED/CITATION_DERIVED` now reports `admissions: -6` with `DIFFERENCE OBSERVED`, where
the old rule would have had to infer it from the false counts.

### F16 — the mean completeness · RESOLVED in the summary and the table; two sentences are stale

The summary's `meanCompleteness` is now over the **admitted** rows with an explicit note ("a
refused row was served by the reference, whose completeness is 1.0 by construction, and a mean over
every row would mix the two paths"), and the §3.2 table shows the admitted-row means
(`0.950`, `0.833`). **Verified**: `wbe/DECLARED/ANCHOR` 0.950 over its ten admitted rows,
`hpd/DECLARED/ANCHOR` 0.833 over its nine. Two sentences still quote the old metric — see **m2**.

### F17 — the cost fields · RESOLVED in the instrument; one sentence is stale

**Verified** across all 432 rows: every refused row has `readNanos == 0` (87/87) and a positive
`fallbackNanos`, every admitted row has `readNanos > 0` (345/345) and `fallbackNanos == 0`, and
`totalNanos == selectionNanos + readNanos + fallbackNanos` for every row. The summary's cost note
and §3.4 now state the three facts a reader must know. One opening sentence still contradicts them
— see **m10**.

### F18 — the case set's author field · RESOLVED in substance

The field now reads `"role": "the IMPLEMENTER (the case author)"` and carries a separate
`corpusRole` that records the corpus's authorship, and `provenance.corpusAuthor` is unchanged and
correct. A wording residue remains — see **m13**.

### F19 — "the corpus carries no gold" · PARTLY RESOLVED

Fixed in the evidence README (§5's last bullet) and in `CASES-PROVENANCE.md` §1 ("It does not
affect the ORACLE … It does affect the CHOICE of the sentences, and therefore the difficulty of the
cases"). **Not fixed** in the milestone document's role table and in `MANIFESTS.md`, and two
sub-items are untouched — see **m8**.

### F20 — the per-cell kind breakdown · RESOLVED

`summary.cells[].strategies[].byKind[criterion][kind]` carries cases, admissions, refusals, false
admissions and false rejections per kind per arm per cell, and the gate re-derives it from the
rows.

### F21 — the generator's docstring · RESOLVED

`scripts/reach-cases.py` now says "**40 cases** over two synthetic DEV domains".

### F22 — the manifest · RESOLVED

`CHECKSUMS.sha256` now lists **8** files, including `REVIEW.md`, and `sha256sum -c` reports OK for
all of them.

### F23 — the grid is an instrument · RESOLVED

The evidence README §5 now says: *"**The grid is an instrument, not a route.** The six arms are
measured against a captured view of a workspace, through the workbench's own reading; only the
lifecycle test drives the **served route** (`ArtifactReportResource.journey`), the durable store
and a restart."*

### F24 — the `last_verified_commit` fields · RESOLVED

§8.3 records that the coordinator updates them at integration and that the milestone writes no
commits.

---

## 3. THE FINDINGS THAT REMAIN

### M1 — the BLOCKER's sentence survives in the evidence README and in the catalogue

**SEVERITY: MAJOR**
**ARTEFACT:** `docs-v2/engineering/evidence/crj01-reach/README.md` §5, second bullet; and
`docs-v2/engineering/TEST-CATALOGUE.yml`, entry `SCOPE-CRJ01-COMPETENCE-REACH`,
`what_it_does_not_prove`.

**WHAT IS WRONG.** The README still says:

> "It does not compare `CITATION_DERIVED` with `EVIDENCE_DERIVED`: on this corpus they produce the
> same scope in every cell, so that comparison is **not established**."

and the catalogue still says:

> "the citation-derived and the evidence-derived strategy produced the **SAME scope in every
> cell**, so that comparison is NOT ESTABLISHED"

Both are the sentence this review graded as a BLOCKER, and both are **false**: at `SUPERSEDED` the
citation-derived scope holds three sources and the evidence-derived scope four, the two arms differ
(`wbe` 8 vs 12 admissions, `hpd` 7 vs 11), and the comparison there **is** established
(`DIFFERENCE OBSERVED`). The milestone document and the registry now say the opposite, so the
repository contradicts itself, and §8.3 of the milestone document claims that F1 was "corrected"
where it was corrected in two places and not in these two.

**PROPOSED RESOLUTION.** In the README's §5, replace the bullet with: *"It does not compare
`CITATION_DERIVED` with `EVIDENCE_DERIVED` at `BASELINE` or `CHANGED_BINDINGS`: there they produce
the same scope in every cell, so that comparison is **not established** there; at `SUPERSEDED` they
differ and the comparison **is** established."* Apply the same correction to the catalogue's
`what_it_does_not_prove`, and to §3.5's unqualified bullet (**m11**).

### m1 — the provenance names three shared golds; there is one

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/evidence/crj01-reach/CASES-PROVENANCE.md` §3, lines 82-88.

**WHAT IS WRONG.** The corrected text says:

> "**Inside** the evaluation slice three golds are used by two cases each (`RC-WBE-08`/`RC-WBE-12`,
> `RC-WBE-13`/`RC-WBE-14`, `RC-HPD-14`/`RC-HPD-16`)"

**Only one pair shares a necessary gold**: `RC-WBE-13` and `RC-WBE-14`, both on *"The yearly energy
production of a wind-turbine decreases as the roughness of its blades increases."* The other two
pairs share nothing (`RC-WBE-08`'s gold is in `wbe-b-inspection.md`, `RC-WBE-12`'s in
`wbe-a-erosion.md`; `RC-HPD-14`'s golds are in the dispensing and reconciliation documents,
`RC-HPD-16`'s in the identification and labelling documents). Worse, `RC-WBE-08` is a **VALIDATION**
case, so the sentence contradicts the sentence immediately above it ("no gold crosses a slice" —
which is true, and is what the suite now asserts).

**PROPOSED RESOLUTION.** Replace the three pairs with the one: *"Inside the evaluation slice one
gold is used by two cases (`RC-WBE-13`/`RC-WBE-14`); the cases differ in their question and in
their other necessary claims."*

### m2 — two sentences still quote the old completeness metric

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.2 item 2 (line 127: *"mean
completeness 1.000 against 0.958 and 0.875"*) and §3.3 (line 161-162: *"the mean completeness
collapses (0.625 in `wbe`, 0.458 in `hpd`)"*).

**WHAT IS WRONG.** The document now declares (line 113) that the completeness column is the mean
over the **admitted** rows, and the table shows **0.950** and **0.833**. The two sentences quoted
above are the **old** all-rows metric and no longer hold under the metric the document declares.
Under the new metric the `SUPERSEDED` per-arm means are 0.812/0.875/0.500/0.438/0.625/0.625
(`wbe`) and 0.643/1.000/0.250/0.200/0.409/0.409 (`hpd`), and the cell means over the admitted rows
are 0.620 and 0.407 — so "0.625 in `wbe`, 0.458 in `hpd`" is wrong under either metric.

**PROPOSED RESOLUTION.** In §3.2 item 2 write "1.000 against 0.950 and 0.833"; in §3.3 give the
admitted-row cell means ("0.620 in `wbe`, 0.407 in `hpd`") or the wide-scope per-arm means, and
say which.

### m3 — the milestone document's own validation table quotes the old gate sizes

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §8.2, the last two rows:
"`bash scripts/reach-gate.sh` | **17 checks, 0 failures**" and "`bash scripts/reach-gate-selftest.sh`
| **6 mutations, 6 caught by the check they belong to**".

**WHAT IS WRONG.** The gate has **21** checks and the self-test **11** mutations; §8.3 of the same
document says so ("The gate gained four checks (21 in all) … The self-test gained the matching
mutations (**11** in all)"), and `MANIFESTS.md` §2 says 21 and 11. §8.2 contradicts §8.3 on the
page.

**PROPOSED RESOLUTION.** Replace the two numbers with 21 and 11.

### m4 — `MANIFESTS.md` still says "seventeen", and repeats a bullet

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/evidence/crj01-reach/MANIFESTS.md` §4 (line 45: *"That check is
one of the seventeen"*) and §5 (lines 49-50 and 58: the bullet *"**No sealed evaluation** was
executed, and no isolation was re-probed…"* appears **twice**).

**WHAT IS WRONG.** The gate has 21 checks, and the last bullet of §5 is a duplicate of the first.

**PROPOSED RESOLUTION.** Write "twenty-one"; delete the duplicated bullet.

### m5 — the catalogue's and the registry's gate descriptions quote 17 checks and 6 mutations

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/TEST-CATALOGUE.yml`, entries `GATE-CRJ01-COMPETENCE-REACH`
(`result: checks: 17`; `what_it_proves` lists the old seventeen) and
`GATE-CRJ01-COMPETENCE-REACH-SELFTEST` (`result: checks: 6`; `what_it_proves` lists the old six);
`docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row `C2A-EVIDENCE-TO-ACTION`, `test_evidence`
("an INDEPENDENT deterministic supervisor gate of **17 checks**, all passing, whose self-test proves
it can go red by **six** mutations").

**WHAT IS WRONG.** The instruments have **21** checks and **11** mutations, and the four new
checks (the row↔case-set crossing, the scope↔summary crossing, the input fingerprint and the
per-kind breakdown) are exactly the checks that make the gate independent of the producer. The
governance records describe an older, weaker gate. Neither validator notices: both report
**0 defects**.

**PROPOSED RESOLUTION.** Update the three records to 21 and 11, and list the four new checks and
the four new mutations in the `what_it_proves` fields.

### m6 — the checkpoint's table still says 17 checks / 6 mutations

**SEVERITY: MINOR**
**ARTEFACT:** the local DSH checkpoint §5 (lines 118-119) against §6 (lines 159-160).

**WHAT IS WRONG.** The §5 state table says "**17 checks, 0 failures**" and "**6 mutations, 6 caught
by the right check**"; the §6 section says "The gate now has **21 checks** (four new) and the
self-test **11 mutations**". The same file contradicts itself.

**PROPOSED RESOLUTION.** Update the two lines in §5.

### m7 — the gate's residual gaps

**SEVERITY: MINOR**
**ARTEFACT:** `scripts/reach-gate.py`.

**WHAT IS WRONG.** The four new checks close the four corruptions I demonstrated, but I probed the
new gate with seven fresh mutations (manifest refreshed first) and four survive:

| mutation | gate verdict |
|---|---|
| the summary's `meanCompleteness` falsified in every arm | **21 checks, 0 failures — GREEN** |
| every cell's `criterionDifference.verdict` falsified | **21 checks, 0 failures — GREEN** |
| the oracle's `dispositionAllowed` relaxed in the rows (with everything derived made consistent) | **21 checks, 0 failures — GREEN** |
| the oracle's `necessaryClaimIds` rewritten in the rows | **21 checks, 0 failures — GREEN** |
| the `byKind` breakdown given a kind no row has | **21 checks, 0 failures — GREEN** |
| a whole cell's `inputFingerprint` restamped | **21 checks, 0 failures — GREEN** |

The first two matter because the milestone's §1 now asserts per-cell **verdicts** and its §3.2
quotes the **mean completeness**; neither is re-derived. The third and fourth are the remaining
parts of the oracle the row↔case-set crossing does not reach: it checks `kind`, `answerability`,
the slice and the arm, but not the **allowed dispositions** and not the **necessary evidence**,
which are the oracle's substance. (The necessary-evidence check is cheap and I verified it holds in
the current data: every row's `necessaryClaimIds` count equals its case's `necessaryEvidence`
count, 432 of 432.) The sixth is structural: the fingerprint check proves that a cell's arms carry
**one** fingerprint, not that the fingerprint is the digest of the view actually read — a producer
could stamp any value.

**PROPOSED RESOLUTION.** Add four more checks — the mean completeness and the mean coverage
re-derived from the rows' admitted rows; the cell verdict re-derived from the compared
aggregates; the row's `dispositionAllowed` and the count of its `necessaryClaimIds` crossed with
the case set; and the `byKind` breakdown checked for kinds that no row has — and add the matching
mutations to the self-test. The fingerprint's own derivation cannot be re-checked by the gate
(it does not hold the view); say so in the gate's own comment rather than leaving the check to
look stronger than it is.

### m8 — "the corpus carries no gold" still stands in two artefacts, and two sub-items of F19 are untouched

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §5, role table (line 262: "**NONE**
— the corpus carries no gold, so the measurement's oracle is unaffected");
`docs-v2/engineering/evidence/crj01-reach/MANIFESTS.md` §3 (line 35: "the corpus carries no gold,
so the oracle is unaffected"); `docs-v2/engineering/evidence/crj01-reach/CASES-PROVENANCE.md` §4
(line 99, the list of `kind`s) and §3 (line 79, the `VALIDATION` gold placement).

**WHAT IS WRONG.** (a) The two role records still carry the claim F19 was about, while the
evidence README and `CASES-PROVENANCE.md` §1 now carry the corrected wording — the repository
disagrees with itself about the same sentence. (b) The provenance's list of the case kinds is
still `PLAIN, PARAPHRASE, MISLEADING_RELEVANCE, MULTI_SOURCE, AMBIGUOUS, INCOMPLETE,
UNSUPPORTED` — it omits `CONFLICT` and `CORROBORATED`, both of which are in the case set (one each
per domain) and both of which the pre-registration names. (c) The `VALIDATION` row still gives the
`hpd` layout ("the **identification** document (four) and the **reconciliation** document (one)")
as if it were both domains'; the `wbe` validation golds are all five in the **inspection**
document.

**PROPOSED RESOLUTION.** Apply the README's wording to the two role records; add `CONFLICT` and
`CORROBORATED` to the kind list; mark the validation layout as the `hpd` one and give the `wbe`
one.

### m9 — `ScopeStrategy.DECLARED`'s Javadoc still describes the pre-amendment rule

**SEVERITY: MINOR**
**ARTEFACT:** `matrix-core/src/main/java/io/matrix/cognitive/report/ScopeStrategy.java`, lines
48-52.

**WHAT IS WRONG.** The enum's Javadoc still says the declared scope *"refuses the learning attempt
instead of widening the support"* — the semantics the pre-registration's §10 amended away and that
the implementation (and the corrected Javadoc of `ReportProcedureLearner.scope(...)`, 15 lines away
in the same package) no longer has. A reader of the enum is told the opposite of what the code
does, on the one rule the milestone amended.

**PROPOSED RESOLUTION.** Rewrite the `DECLARED` Javadoc in the bounding terms and name the §10
amendment, as the learner's Javadoc now does.

### m10 — §3.4's opening sentence contradicts the three caveats below it

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.4, line 172: *"Every arm records
its own selection, read and fallback cost"* — against the three caveats the same section now gives
(lines 179-185).

**WHAT IS WRONG.** The read cost is measured **once per scope** and shared by the two criteria of
that scope (the caveats say so), so it is not "its own" per arm. The caveats are right and the
opening sentence is the old one.

**PROPOSED RESOLUTION.** "Every arm records a selection cost of its own; the read and the fallback
costs are per scope and per case, and the three facts below say how to read them."

### m11 — §3.5's bullet is unqualified

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §3.5, line 191: *"Nothing about the
two strategies that coincided: their comparison is **not established**."*

**WHAT IS WRONG.** Read on its own, the bullet repeats the BLOCKER's claim: the comparison **is**
established at `SUPERSEDED`. §1 and §3.1 now qualify it correctly; this bullet does not. (§9's
last paragraph does qualify it: "produced identical scopes **in half of the cells**" ✓.)

**PROPOSED RESOLUTION.** "Nothing about the two strategies in the cells where they coincide: their
comparison is **not established** there."

### m12 — §4's table says the widened candidate is `1.1.0` and carries the rule

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-COMPETENCE-REACH.md` §4, table row 3 (line 206): *"the
widened candidate is a **separate, inspectable record** (`1.1.0`, the scope in the support set, the
rule in the record)"*.

**WHAT IS WRONG.** The widened candidate in the lifecycle test is learned with
`ScopeCriterion.ANCHOR` (`CompetenceReachServedTest`, line 471-473), and `ReportProcedure.record`
writes `scoped ? VERSION_SCOPED : VERSION` with `scoped = criterion != null && criterion !=
ANCHOR` — so the widened candidate is version **`1.0.0`**, and its applicability field carries
**no** `criterion=` key (only a non-incumbent criterion is written into the record). The `1.1.0`
record with `criterion=CLAIM_SUFFICIENCY` is the *other* competence of the *other* test.
**I did not raise this in the first review**; it is the same class of defect as the numbers I did
raise, and I record it here rather than leave it.

**PROPOSED RESOLUTION.** "the widened candidate is a **separate, inspectable record** (`1.0.0`, the
scope in the support set); the rule travels in the record when it is not the incumbent."

### m13 — the case set's `independence` sentence still calls both commissions "case-author"

**SEVERITY: MINOR**
**ARTEFACT:** `docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json`, `author.independence` ("two
separately commissioned DSH **case-author** subagents were given the corpus and the oracle
contract…"); against `CASES-PROVENANCE.md` §1 ("`corpus-author` | the corpus contract only").

**WHAT IS WRONG.** One of the two commissions was the *corpus* commission, which was given the
corpus contract only and delivered four of the eight corpus documents. The new `corpusRole` field
records that correctly, so the substance of F18 is fixed; the `independence` sentence still
describes the corpus commission as a case-author commission that was given the oracle contract.

**PROPOSED RESOLUTION.** Reword `independence` to name one case-author commission that delivered
nothing and one corpus commission that delivered four documents, as the provenance does.

### n1 — the fingerprint check is structural

**SEVERITY: NOTE**
The new `inputFingerprint` is a real digest of the view (workspace id, the view's `exportedAt`,
the claim ids and the source ids), and the gate checks that a cell's arms carry one. It cannot
re-derive the digest — the gate does not hold the view — so a producer could stamp any value and
stay green. The milestone's §3.1 wording is careful ("require the six arms of a cell to carry
**one**"), so nothing is overstated; a reader should know that the check proves sameness of the
label, not of the view.

### n2 — the manifest will need one more refresh

**SEVERITY: NOTE**
`CHECKSUMS.sha256` now covers `REVIEW.md` but not this report. `sha256sum -c` still passes and the
gate still passes (it verifies the listed files), but the README's description of the manifest
("over every `.md`, `.json` and `.xml` file") is again one file short. One more run of
`scripts/reach-retain-evidence.sh green` (or the suite's own refresh) after this report is accepted
closes it.

### n3 — the per-cell row count is still only checked globally

**SEVERITY: NOTE**
The gate's row-count check is still global (432) and "every cell carries all six arms" still only
requires a count above zero. A row moved between cells is now caught by four other checks (the
denominators, the scope, the fingerprint and the breakdown), so the residual risk is small, but the
per-cell count itself is still not asserted.

### n4 — the validation outcome remains a producer claim

**SEVERITY: NOTE**
The gate now checks the summary's validation outcome against its own agreement (`validated` ⟺
`agreement ≥ 1.0`) and the rows' scope against the summary's, but the validation itself — the five
held-out cases — is not in the retained evidence and cannot be re-derived. The summary's
`validationAgreement` and `validationReason` remain the producer's own account.

---

## 4. WHAT THE HEADLINE CLAIMS NOW REST ON

| headline claim | supported by the current evidence? | evidence I used |
|---|---|---|
| the strategy axis: `CITATION_DERIVED` vs `EVIDENCE_DERIVED` — **NOT ESTABLISHED** at `BASELINE`/`CHANGED_BINDINGS`, **DIFFERENCE OBSERVED** at `SUPERSEDED` | **yes** | the scopes are equal sets in the four cells and different in the two; the arms differ by 4 and 6 admissions; the milestone document and the registry now say so |
| the criterion axis: the rules agree on 189 of the 216 case-arms and differ on 27 (17 in the declared arm, 10 in the wide arms at `SUPERSEDED`) | **yes** | recomputed pair by pair from the rows; exactly 189/27, and the split is exactly 17/10 |
| the two denominators are separate | **yes** | every arm's `falseAdmissionDenominator` is its admissions and its `falseRejectionDenominator` its refusals, in all 36 arms; the gate re-derives them |
| the false admissions of `BASELINE` are 67, broken down by kind | **yes** | recomputed by kind and by (kind, disposition); the document's enumeration sums to 67 |
| the anchor's blind spot: 3 of the 32 admissions of the declared arms, one of them needing a single claim | **yes** | `RC-WBE-14`, `RC-HPD-13`, `RC-HPD-16`, all `DECLARED/ANCHOR`; `RC-HPD-13` has one necessary claim |
| the `CHANGED_BINDINGS` condition changes 2 of the 144 verdicts | **yes** | the two flips are `RC-HPD-13` and `RC-HPD-20` in `hpd/DECLARED/ANCHOR` |
| the `SUPERSEDED` condition: 66 false admissions of 97 admissions, 8 false rejections of 47 refusals, and the criterion's largest difference | **yes** | recomputed over the `SUPERSEDED` rows; the anchor admits 7 with 0 false rejections, the sufficiency rule admits 1 with 3 |
| the durable RETIRE is not supported (the write is accepted as the same definition and dropped) | **yes** | `CognitiveStore.recordProcedure` compares `procedureDefinitionOf(...)`, which does not contain `invalidated_at`, and returns silently on a match; the test asserts the stored record keeps `invalidatedAt = null` and stays active; **unchanged by the fixes** — the production diff grew by five Javadoc lines only |
| the durable NARROW is not supported (a narrower scope is a different competence) | **yes** | `ReportProcedure.idFor(support)` derives the identity from the support set alone; the narrow is refused by the validation and the predecessor stays active; unchanged |
| the served default is unchanged for an older writer's record | **yes** | the anchor's reason templates still match the retained ADAPTIVE evidence's 35 admit and 16 refuse strings exactly, and all 207 anchor rows in the new evidence match the incumbent's templates (plus 9 "reaches no claim" forms) |
| the role record is honest and the result is clearly DEV | **yes, with one wording residue** | the milestone document, the README, `CASES-PROVENANCE.md`, `MANIFESTS.md`, the registry, the case set's `author` field and both test artefacts now say the cases are the implementer's; the two role records that still say "the corpus carries no gold" are m8 |

**One sentence still overstates what the rows show**: §3.5's bullet (m11). **Two numbers still do
not hold under the metric the document declares**: §3.2 item 2 and §3.3 (m2). **One sentence in
§4 misstates a record's version and content**: m12.

---

## 5. WHAT I CHECKED AND FOUND CORRECT (after the fixes)

1. **The retained evidence is internally consistent and complete**: 432 rows × 33 fields; 6 cells
   × 6 arms × 12 cases; 72 rows per cell; the six arms of every cell carry **one**
   `inputFingerprint`; the 36 arms' admissions, refusals, false admissions, false rejections and
   both denominators recomputed from the rows match the summary exactly.
2. **The manifest verifies**: 8 files, all OK; the retained directory holds the rows, the summary,
   the GREEN XML (8 tests / 0 failures), the RED XML (8 tests / 2 failures), the corpus XML (1
   test / 0 failures, "sentences 70 claims 70 citable 70 missing 0"), `CHECKSUMS.sha256`,
   `README.md`, `MANIFESTS.md`, `CASES-PROVENANCE.md` and `REVIEW.md`.
3. **The RED is the same suite as the GREEN**: both 8 tests; the RED carries exactly 2 failures —
   the grid-level criterion difference and the lifecycle's diagnosis — and both are named in §7.1.
4. **The gate runs and is green**: 21 checks, 0 failures. **The self-test runs and is green**: 11
   mutations, 11 caught by the right check, with the copy's manifest refreshed first so that no
   mutation is caught by the manifest alone.
5. **Both validators pass**: the catalogue (19 scopes, 0 defects) and the registry (57
   capabilities, 24 fields, 0 defects). Neither notices the stale counts in m3/m5 — the
   validators check structure, not the numbers inside a free-text field.
6. **No regression to the prior milestone**: `scripts/adaptive-competence-gate.sh` reports *checks
   32, failed 0*, and the ADAPTIVE evidence directory's own `CHECKSUMS.sha256` verifies.
7. **The production change is untouched by the fixes**: the diff against the baseline grew from
   +490 to +495 lines, the five being the §10-amendment Javadoc in `ReportProcedureLearner`; the
   other three production files are byte-identical to what I reviewed.
8. **The new instruments are real**: the suite reads the prior milestone's retained ADAPTIVE
   evidence **read-only** and asserts that the *form* of the anchor's reason — the admitting one
   and the refusing one — is one that evidence records; the suite asserts that no gold crosses a
   slice (a genuine, falsifiable assertion, and the property holds); the row↔case-set crossing,
   the scope↔summary crossing, the input fingerprint and the per-kind breakdown are all re-derived
   by the gate.
9. **The case set is unchanged in substance**: 40 cases, 3 + 5 + 12 per domain, all questions
   unique, every necessary gold verbatim in the corpus file it names, all eight corpus digests
   matching the bytes, no gold crossing a slice, one gold shared inside the evaluation slice.
10. **The corpus is unchanged**: 70 sentences, 70 claims, 0 missing, re-derived from the bytes.
11. **The cost semantics are now coherent**: 87 refused rows with a zero read and a positive
    fallback, 345 admitted rows with a positive read and a zero fallback, and
    `total = selection + read + fallback` on every row.
12. **The new summary metric is the declared one**: `meanCompleteness` over the admitted rows with
    its note; `meanCompletenessNote` present on every arm; the §3.2 table agrees with it.
13. **The verdict vocabulary is honoured** in the document, the summary and the registry: only
    `DIFFERENCE OBSERVED` and `NOT ESTABLISHED`; no winner, no threshold, no timing assertion
    anywhere.

---

## 6. WHAT I COULD NOT CHECK

1. **Whether the suite still passes.** No Gradle was run; I read the retained XMLs. The GREEN XML
   (8/0) and the RED XML (8/2) are consistent with each other, with the rows' timestamps and with
   the assertions in the sources.
2. **That the re-retained rows came from the run that produced the re-retained XMLs.** The
   timestamps are consistent (rows 19:51:11, manifest and XMLs 19:51:18), but I did not observe
   the run.
3. **The corpus instrument's own assertions** beyond its retained output: the XML shows one test,
   0 failures, and the report's own counts.
4. **The `inputFingerprint`'s derivation**: the gate checks that a cell's arms carry one
   fingerprint; it cannot re-derive the digest, so I verified the *implementation* by reading
   `Crj01ReachGrid.fingerprint` rather than by recomputing it.
5. **Whether the new `byKind` breakdown covers every kind the pre-registration names** — the gate
   checks the kinds the rows carry, and a kind with no rows in an arm is absent from the
   breakdown by construction.
6. **The upstream directive**, as before: I have the checkpoint's digest of it, not the directive.
7. **The hidden owner state**, as before: not read.

---

## 7. SUMMARY

| severity | count | ids |
|---|---|---|
| BLOCKER | **0** | (F1 resolved in the milestone document and the registry) |
| MAJOR | **1** | M1 |
| MINOR | **13** | m1, m2, m3, m4, m5, m6, m7, m8, m9, m10, m11, m12, m13 |
| NOTE | **4** | n1, n2, n3, n4 |

**Findings resolved since the first review: 15 fully, 9 in substance with a residue (M1, m1–m10,
m13). Findings still open: 1 MAJOR, 13 MINOR, 4 NOTE.**

**May the milestone be integrated as a DEV result?** **Yes, once M1 is corrected** — one sentence
in the evidence README's §5 and one in the catalogue's `what_it_does_not_prove`, both of which
still repeat the sentence this review graded as a BLOCKER. The MINOR sweep should ride with the
integration: m2 and m3 are the two stale-number families inside the milestone document itself,
m1 is a false statement in the provenance, and m5/m6 are the same stale gate size in three
governance artefacts and the checkpoint. None of the 18 remaining items touches the measurement,
the production change, the corpus, the case set or the retained rows — the evidence is sound, and
what remains is the report of it.
