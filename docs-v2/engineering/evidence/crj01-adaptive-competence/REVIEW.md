# INDEPENDENT REVIEW — CRJ-01 / ADAPTIVE-COMPETENCE

Reviewer: an independent DSH agent, 2026-11-27. Read-only review; the implementation was not
changed by the reviewer. Baseline: dff12b5883242b74c09cc2388be32061c2b87cf0.

## VERDICT

**FINDINGS MUST BE RESOLVED**

I checked the milestone against its artefacts, not against the implementer's narrative. I read
`CRJ-01-ADAPTIVE-COMPETENCE.md`, `CRJ-01-ADAPTIVE-PREREGISTRATION.md`,
`CRJ-01-ADAPTIVE-EVIDENCE.json`, `CRJ-01-ADAPTIVE-COST-ROWS.json`,
`CRJ-01-ADAPTIVE-DEV-SPLIT.json`, `CRJ-01-ADAPTIVE-CORPUS-REPORT.json`, the retained evidence
directory (README, MANIFESTS, CHECKSUMS, all seven XMLs), the protocol §5.5a/§5.6, the five
production sources (`SupportAdequacy`, `KnowledgeStatistics`, `KnowledgeStatisticsCache`,
`KnowledgeWorkbench.assessSupport`, `KnowledgeRetriever`), `ReportProcedureMethod`,
`ArtifactReportResource`, the 13-test served suite, `scripts/adaptive-competence-gate.py`,
`scripts/adaptive-competence-gate-selftest.sh` and `docs-v2/engineering/evidence/crj01-adaptive-competence/render-cost-table.py`. I re-derived
the corpus digests, the manifest, the per-cell medians and the bootstrap intervals myself, ran
the gate (27 checks, 0 failures, exit 0), and compared every number in the milestone document
with the artefact it cites. I did **not** run Gradle (the implementer owns the build and the
shared lock), so I could not re-execute the suite, the RED, or the corpus instruments; every
claim below about *behaviour* is therefore a claim about the retained XMLs and the code, not
about a fresh run.

**What holds.** The mechanism is sound and the central claims are evidenced. The criterion is
genuinely question-specific and carries no lexical or domain rule: `assessSupport` decides on
the retrieval's own ids and coverages only (`KnowledgeWorkbench.java:717-741`), and the
comparison against the *workspace's* reading is the right one, because the reference method
reads the same unrestricted `selectedEvidence` view, so the anchor is literally the reference's
first citation — the test `theGateAndTheReadingAgreeOnTheBestClaim`
(`AdaptiveCompetenceServedTest.java:322-349`) pins that identity, and I confirmed that the
retained rows show the served arm's citations equal the reference arm's on all 28 cases, with
zero citation-count differences. The refusal happens before any durable side effect:
`preconditionsHold` is evaluated inside `planner.plan(...)`
(`ArtifactReportResource.java:380`), `assessSupport` is side-effect-free (`DialogueContext.expand`
and `resolveSubject` are pure reads — I read both), and the forced-outside-its-domain case
writes no artifact. The statistics cache is keyed by `(workspaceId, viewIdentity)`, is
LRU-bounded and synchronized, survives no restart by construction, and its identity digest is
order-independent (`KnowledgeStatistics.java:168` sorts the material), so the ranking stays
deterministic despite `Map.copyOf`'s randomized iteration order. The competence revision is
promoted in both domains with three-source supports and five held-out cases
(`CRJ-01-ADAPTIVE-EVIDENCE.json` → `revisions`), and the DEV-only declaration is present in
every artefact that carries a number (`sealed: false` in the evidence, the cost rows and the
split). The cost verdicts in the retained artefact are correct: I re-derived the medians and
the bootstrap intervals from the retained rows and reproduced **all eight** verdicts. The
rendered cost table is faithful to the artefact, cell by cell, with no off-by-one and no
mis-parsed interval.

**Why the verdict is not ACCEPTED.** Three of the milestone's *published* claims are contradicted
by its own artefacts (F-01, F-02, F-03), the retained evidence carries a false attribution about
role independence (F-02), and the new statistics cache has a stale-reuse path that the milestone's
own invalidation test does not reach (F-04). None of these requires a re-measurement: F-01–F-03
are corrections to documents and to one JSON field, F-04 is a one-line identity change plus one
test. The defect class this milestone exists to remove — a decision made from a statement about
the workspace rather than about the evidence — has been reintroduced, in the *record*, in three
places.

## FINDINGS

### F-01 — MATERIAL — The milestone document's cost conclusion contradicts the retained artefact and its own rendered table

**File/lines:** `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md:144`, `:171`, `:277` (and the
summary at `:27-31`).

The retained cost artefact holds **three** `CHEAPER` cells and **five** `NOT ESTABLISHED`:

```
dhx/wide/1   interval [-1570304, -499871]   -> excludes zero -> CHEAPER
dmy/wide/1   interval [-2910752, -1399451]  -> excludes zero -> CHEAPER
dmy/wide/2   interval [-3137098,  -832752]  -> excludes zero -> CHEAPER
```

The document says the opposite:

> **The verdict, plainly: no cost advantage is established.**  (`:144`)

> the grid says the difference is inside the noise in six of eight cells  (`:171`, §3.5)

> the procedure's advantage, where it exists, is a millisecond-scale effect that the
> measurement cannot separate from the noise in six of eight cells  (`:277`, §9)

Six of eight is falsified by the artefact it cites: **five** of the eight intervals contain zero
(the three above exclude it). The rendered table at `:135-142` is itself faithful and shows
three `CHEAPER` rows — so the document contradicts its own table two paragraphs apart.

Two separate problems hide in this. First, the falsifiable count is still in the prose, although
§3.4 `:146-147` claims "the prose carries no count that a re-run could falsify" — that claim is
itself false. Second, "no cost advantage is established" is an **aggregate verdict over the eight
cells**, which the pre-registration forbids in advance: *"The verdict is per cell. There is no
aggregate "winner" over the eight cells, and no aggregate is invented after the rows are seen."*
(`CRJ-01-ADAPTIVE-PREREGISTRATION.md:126-127`). The aggregate here is not merely forbidden, it is
wrong: three cells establish an advantage under the pre-registered rule.

*Why it matters:* the milestone's headline scientific conclusion is the one number a reader will
quote, and it is not the number the evidence holds.

*Smallest resolving change:* replace the three prose counts with the artefact's counts — "five of
eight" in §3.5 and §9 (the renderer already computes both, so render them too) — and replace
"**No cost advantage is established**" with the per-cell statement the pre-registration requires
("three cells are `CHEAPER` under the pre-registered rule and five are `NOT ESTABLISHED; the
verdict is per cell and no aggregate is claimed").

### F-02 — MATERIAL — The retained evidence attributes the cases to a case author that did not author them

**File/lines:** `docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-EVIDENCE.json:17` (`roles.caseAuthor`),
against `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md:185` and `:189-190`.

The canonical evidence artefact says:

> "caseAuthor" : "a SEPARATE DSH agent that authored the two domains and the split from the
> production extraction's report, without reading the implementer's mechanism"

The milestone document says the opposite, in the same milestone:

> **The limitation that must be declared.** The case author **did not deliver** within the
> milestone's time, and the questions were written by the **implementer's deterministic generator**
> — from the separation probe's own focused questions, verbatim.

and the document's own roles table at `:185` repeats the false attribution ("the questions and
the gold of the DEV split"). The split's own `provenance` agrees with the document, not with the
artefact: *"the questions and the gold are selected from the separation probe's narrow claims; the
questions are the probe's own focused questions"*.

*Why it matters:* the role/independence record is what §5.5a of the protocol requires to be
recorded truthfully ("Record the model and session identities, assignments, access attestations,
prior exposure, contamination risk and validation limitations"), and it is the field a reader
audits first to decide how much the DEV numbers are worth. The document retracts the claim; the
artefact still makes it. The milestone's own §4 declares that the case independence "weakens the
independence of the cases and nothing else" — but the artefact asserts an independence that did
not occur.

*Smallest resolving change:* correct `roles.caseAuthor` in the artefact to the truth (the
implementer's generator, over the separation probe's own questions; the case author did not
deliver), correct the document's roles table row at `:185` to match its own limitation paragraph,
regenerate the artefact and re-verify the manifest.

### F-03 — MATERIAL — The README's account of what the RED shows is contradicted by the retained RED

**File/lines:** `docs-v2/engineering/evidence/crj01-adaptive-competence/README.md:48-50`, and the
milestone document's paraphrase of it at `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md:83-85`.

> **Result:** the suite goes red on the out-of-support controls: the procedure is served for a
> question its support cannot answer, the served evidence is the restricted reading, and the
> parity assertion fails. The retained XML is the record.

The retained XML says something different. In `CRJ-01-ADAPTIVE-REPRODUCTION-no-gate.xml` the
four failures are:

| failing test | what the XML shows |
|---|---|
| `anUnsupportedQuestionFallsBackWithItsReason` | `expected: "report.journey.reference.v1" but was: ""` — the served journey was **refused (422)**, not served |
| `aFailureEpisodeYieldsAValidatedRevision` | the revision produced the **same** procedure id as the original — the competence revision, not a durable-effect claim |
| `anAmbiguousQuestionIsNotAnswered` | `expected: 201 but was: 422` — again a refusal |
| `everyCitedClaimIsCitableAndNamed` | the body was `{"error":"method_refused","detail":"...does not carry the workspace's own evidence..."}` — a refusal, not an answer |

So the RED shows: with the presence-only precondition the **selector admits** the procedure, and
the journey then **refuses (422)** instead of falling back to the reference. The restricted
reading was never served, and the parity assertion
(`theServedPathNeverAnswersWhereTheProcedureCannot`) **passed** in the RED — it has no `<failure>`
element. The README's sentence describes the H2 defect, not this reproduction.

The milestone document repeats the error at `:83-85`: *"The four RED failures are exactly the four
claims the milestone makes: the out-of-support fallback, the refusal before any durable effect,
the ambiguity, and the citable status of the cited claims."* — the second failure is the
competence revision, and "the refusal before any durable effect" is precisely the test that
**passed** in the RED (`aForcedProcedureOutsideItsDomainIsRefused`).

*Why it matters:* the README exists precisely so that "a reader can tell what was changed"; a
reader who believes it will believe the wrong reproduction was made. The RED is still evidence of
*something* real — the selector admitting an inapplicable procedure — but not of what is claimed.

*Smallest resolving change:* rewrite the README's "**Result:**" paragraph and the document's
`:83-85` sentence to describe what the XML shows (the selector admits the procedure; the run
then refuses with 422; the four failing test names are the fallback, the revision, the ambiguity
and the citable-status filter), and name the four test names from the XML.

### F-04 — MATERIAL — The view identity does not cover the claim identifiers, so a revision can serve stale statistics

**Files/lines:** `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeStatistics.java:153-170`
(`identityOf`) and `:161-163`; with
`matrix-core/src/main/java/io/matrix/knowledge/acquisition/DocumentImporter.java:482-485`
(`claimId`) and `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeRetriever.java:766-772`.

The identity is a digest over the claim's *proposition* and its concept's name and definition:

```java
material.add(claim.proposition() + FIELD_SEPARATOR
        + (concept == null ? "" : concept.name()) + FIELD_SEPARATOR
        + (concept == null || concept.definition() == null ? "" : concept.definition()));
```

but a claim's identifier is a function of the **document's content hash**:

```java
public static String claimId(String workspaceId, String contentHash, String proposition) {
    return "clm-" + Ids.digest(workspaceId + "|" + contentHash + "|"
            + proposition.strip().toLowerCase(java.util.Locale.ROOT));
}
```

So a source revision that changes the document's bytes changes **every** claim id in it, while
the identity only changes if some *proposition* (or concept name/definition) changed. A revision
that changes the bytes without changing any extracted proposition — whitespace or formatting, a
heading, a sentence the extractor does not propositionise — therefore produces a view whose
claims have **new ids under the old identity**. The cache then serves the previous view's
statistics, which are keyed by the **old** ids, and the retriever drops every claim:

```java
Set<String> terms = claimTerms.get(claim.id());
Set<String> lemmas = claimLemmas.get(claim.id());
if (terms == null || lemmas == null) {
    // a claim the statistics do not index carries no term the question could
    // reach, so it matches nothing and contributes nothing
    continue;
}
```

The failure is silent and total: the workspace stops citing anything and abstains on questions it
could answer, and the procedure becomes inapplicable for every question (`bestClaimId == null`).

The milestone's own new test (`aChangedDocumentInvalidatesTheReusedStatistics`,
`AdaptiveCompetenceServedTest.java:397-435`) does **not** reach this: it appends a sentence, which
changes a proposition, hence the identity — so it exercises the id-preserving case only.

*Why it matters:* this is the exact defect class the review was told to hunt — wrong invalidation
in the statistics cache — and it is silent.

*Smallest resolving change:* include the claim's identifier in the identity material, e.g.
`material.add(claim.id() + FIELD_SEPARATOR + claim.proposition() + ...)`, and extend the new test
with a revision that changes the document's bytes but not any extracted proposition (e.g. a
whitespace-only or heading-only revision), asserting that the identity changes.

### F-05 — MATERIAL — The parity assertion is an equality of counts, not "28 of 28", and the gate inherits the weakness

**Files/lines:** `matrix-core/src/test/java/io/matrix/knowledge/AdaptiveCompetenceServedTest.java:1082-1084`;
`scripts/adaptive-competence-gate.py:200-205`.

```java
assertThat(servedGrounded)
        .as("the served path grounds as many cases as the stateless reference")
        .isEqualTo(referenceGrounded);
```

The assertion compares two counts. If both arms degraded together to, say, 20 of 28, the
assertion still passes and the artefact's `summary.parity` is still `true` — and the gate, which
reads exactly that flag, still passes:

```python
gate.check("the served path's grounding is at parity with the reference's",
           summary.get("parity") is True, ...)
```

The retained artefact happens to hold 28/28/28, so the document's "28 of 28" is **true**; but
nothing in the suite or the gate *enforces* it, so the claim is asserted nowhere it could fail.

*Smallest resolving change:* assert `servedGrounded == cases && referenceGrounded == cases` in
the suite, and in the gate check `summary.servedGrounded == summary.cases` **and**
`summary.referenceGrounded == summary.cases` (or that every `served` row's `grounded` is true)
instead of reading the artefact's self-declared `parity` flag.

### F-06 — MATERIAL — The pre-registered rule is applied in a weaker form than it was written in, and its bootstrap parameters are not named in the evidence

**Files/lines:** `matrix-core/src/test/java/io/matrix/knowledge/AdaptiveCompetenceServedTest.java:754-758`;
`docs-v2/engineering/CRJ-01-ADAPTIVE-PREREGISTRATION.md:117-118` and `:114-116`.

The rule as pre-registered:

> 3. the procedure arm's answers are **grounded on the same matched cases** as the reference's —
>    a cheaper answer to a different question is not a cheaper answer.

The implementation checks only the procedure arm:

```java
boolean grounded = rows.stream()
        .filter(row -> row.cell().equals(cell.key()) && "c".equals(row.arm())
                && "warm".equals(row.pass()))
        .allMatch(CostRow::grounded);
```

The reference arm's grounding on the same matched cases is never checked, so a cell whose
reference answers were ungrounded would still be declared `CHEAPER` under the implementation and
`NOT ESTABLISHED` under the rule as written. **For the retained run this changes nothing** — I
checked every row: all `a`, `c`, `s` and `f` rows in all eight cells are grounded, and my
independent re-derivation reproduced all eight verdicts — so the retained verdicts are sound. The
rule as written is nevertheless not the rule as applied.

The same clause requires that "the number of resamples is named in the evidence"; the retained
artefact names neither the count (2000) nor the seed (20261127) — the cells carry only
`pairedDifferenceIntervalNanos`.

*Smallest resolving change:* require the `a` arm's warm rows to be grounded as well, and add
`bootstrapDraws` and `bootstrapSeed` to the cost artefact (the test already holds both
constants), so the interval can be reproduced from the evidence alone.

### F-07 — MINOR — The gate does not re-derive the verdicts against the pre-registered rule, although its own docstring says it does

**File/lines:** `scripts/adaptive-competence-gate.py:21-22` (docstring) vs `:266-279`
(implementation).

> 5. the cost grid's artefact carries a verdict for every cell, and the pre-registered rule is
>    the one the verdicts were read against;

The implementation checks only that a `verdict` key exists in each cell, that rows exist, and
that the artefact **names** the pre-registration file:

```python
gate.check("the cost grid names its pre-registration",
           str(cost.get("preregistration", "")) == PREREGISTRATION, ...)
```

The rule is never read by the gate; the verdicts are computed inside the test that produced the
timings, by code that *is* the rule, and the gate only checks that a string matches. So the one
place where an independent re-reading would matter — a decision rule read against itself — has no
independent reader. (I supplied that re-reading myself: F-06's paragraph, and it reproduced the
artefact's verdicts.)

*Smallest resolving change:* re-derive the eight verdicts in the gate from the retained rows
(median comparison, paired bootstrap with the named seed and draw count, grounding of **both**
arms) and compare them with the artefact's `verdict` fields; or reword the docstring to say that
the gate checks the verdicts' *presence* and the pre-registration's *presence*.

### F-08 — MINOR — The gate's self-test does not assert that the intended check fired

**File/lines:** `scripts/adaptive-competence-gate-selftest.sh:10-18` (header) and `:59-71`.

The header promises that each mutation "must exit NON-ZERO **and must name the check that
failed**", but the implementation only tests the exit code and *prints* whatever FAIL lines the
gate produced:

```bash
output="$(cd "$copy" && python3 scripts/adaptive-competence-gate.py 2>&1)" || status=$?
if [ "$status" -ne 0 ]; then
  note "PASS  $name — the gate refused the mutated evidence (exit $status):"
  printf '%s\n' "$output" | grep '^FAIL' | sed 's/^/        /' | cut -c1-130
```

For mutations 1 (the retained XML carries a failure) and 6 (the RED stops failing) the mutated
file is covered by `CHECKSUMS.sha256`, so the manifest check fires **as well** — the self-test
cannot distinguish "the intended check fired" from "some check fired". The other four mutations
are clean (they touch files outside the manifest), and the self-test's other properties are
honest: it really does copy the evidence into a `mktemp -d` tree and never writes to the
repository, and a mutation that fails to apply is reported as a failure rather than silently
green (`|| true` is on the mutation, not on the verdict).

*Smallest resolving change:* capture the gate's output and require the expected line, e.g.
`grep -q '^FAIL the serving suite passed' <<<"$output"` for mutation 1 and
`grep -q '^FAIL the RED carries failures'` for mutation 6.

### F-09 — MINOR — A fallback caused by the support set's absence records no reason at all

**Files/lines:** `matrix-core/src/main/java/io/matrix/cognitive/report/ReportProcedureMethod.java:238-245`
and `:255-258`; `matrix-core/src/main/java/io/matrix/knowledge/ArtifactReportResource.java:592-636`;
`matrix-core/src/main/java/io/matrix/cognitive/algorithms/AlgorithmSelector.java:404`.

`refusalOf` returns the presence reason **before** the adequacy check runs:

```java
if (!present.containsAll(support)) {
    return "its support set is no longer present in the workspace";
}
...
SupportAdequacy assessment = adequacyOf(input);
```

so on a presence failure `adequacyOf` is never called, `lastAssessment()` is null, and
`fallbackNotes` adds **no note**. The journey then records only the selector's generic wording —
"the method reported that its own preconditions do not hold for this payload" — and the caller's
response carries no reason. The milestone's headline claim ("the reason is recorded with the
evidence it was made from") therefore holds only for the adequacy failure, and the suite
exercises only that path (all 42 fallback rows in the retained evidence are adequacy failures).

*Smallest resolving change:* remember the refusal string on the method (e.g. a `lastRefusal`
field set in `refusalOf`) and let `fallbackNotes` fall back to it when there is no assessment.

### F-10 — MINOR — The recorded reason can understate the support set's evidence

**File/lines:** `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:722-725`
and `:753-757`.

```java
double supportBest = 0.0;
for (String cited : supportRelevance.relevantClaimIds()) {
    supportBest = Math.max(supportBest, supportRelevance.coverage().getOrDefault(cited, 0.0));
}
```

`supportBest` is the maximum over the support's **relevant** (above-the-floor) claims only. A
support claim that *matched* the question but fell below the coverage floor is not counted, so
the recorded reason can say "the support set's best evidence reaches 0.000" for a support set
that does hold evidence — the number quoted in the reason is not the support set's best coverage.
The verdict is unaffected; the *record* is.

*Smallest resolving change:* take the maximum over `supportRelevance.coverage().values()` (the
coverage map holds every claim that matched), and keep the current wording only when the
support set reached no claim at all.

### F-11 — MINOR — A test assertion contradicts the implementation's own documented contract

**File/lines:** `matrix-core/src/test/java/io/matrix/knowledge/AdaptiveCompetenceServedTest.java:334-339`;
against `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:729-732`.

```java
if (adequacy.bestClaimId() == null) {
    assertThat(answer.claims())
            .as("no claim reaches the floor, so the reading cites none: %s", testCase.id())
            .isEmpty();
```

`assessSupport`'s own comment states the opposite contract: *"A workspace whose own reading
reaches no claim at all is answered by the concept bridge, and the bridge reaches a concept's
claims regardless of their own coverage"*. When the bridge answers, `answer.claims()` is **not**
empty, so this assertion encodes an incidental behaviour of the present corpus rather than a
property of the mechanism — it would fail on any corpus whose question is answered through the
bridge.

*Smallest resolving change:* assert that no claim of the answer is one the gate would have named
as best (or that the answer's claims are bridge claims), rather than that the answer cites none.

### F-12 — MINOR — The retained RED predates the current suite (12 tests retained, 13 in the tree)

**Files/lines:** `docs-v2/engineering/evidence/crj01-adaptive-competence/CRJ-01-ADAPTIVE-REPRODUCTION-no-gate.xml:2`
(12 tests, 4 failures) against
`docs-v2/engineering/evidence/crj01-adaptive-competence/TEST-io.matrix.knowledge.AdaptiveCompetenceServedTest.xml:2`
(13 tests) and `AdaptiveCompetenceServedTest.java:397-435` (the thirteenth test).

The RED's stack-trace line numbers (275, 461, 926, 1132, 403) do not correspond to the current
1700-line test file — the RED was taken against an earlier revision of the suite. The milestone
document's "**fails 4 of 12**" is true of the retained XML, but the suite it was taken from no
longer exists in the tree, and the README's re-derivation command would produce a 13-test record
whose failure count and line numbers differ.

*Smallest resolving change:* re-run the RED against the current suite and re-retain it (and
re-verify the manifest), or state the suite revision the RED was taken on.

### F-13 — MINOR — The cost-table renderer would mis-render a positive lower bound

**File/lines:** `docs-v2/engineering/evidence/crj01-adaptive-competence/render-cost-table.py:26-32`.

```python
return "[−{:.2f}, {}]{}".format(abs(low) / 1_000_000.0, ...)
```

The low bound is always prefixed with "−", whatever its sign, while the high bound chooses its
sign from the value. All eight retained cells have a negative low bound, so **today's rendered
table is faithful** — I verified all eight rows cell by cell against the artefact (medians to two
decimals, intervals, verdicts) and found no off-by-one and no mis-parse — but a cell whose
interval excluded zero on the positive side would be rendered as "[−x, +y]".

*Smallest resolving change:* choose the low bound's sign from its value, as the high bound
already does.

### F-14 — NOTE — "cases measured" counts a different population than the word means elsewhere

**File/lines:** `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md:77`; the suite's own
`measured(domain)` helper at `AdaptiveCompetenceServedTest.java:1388-1397`; the pre-registration
at `CRJ-01-ADAPTIVE-PREREGISTRATION.md:37-43`.

The document's "cases measured | 28 (two domains × fourteen)" is the whole split (OBSERVED,
NOVEL, VALIDATION and OUT_OF_SUPPORT), while the pre-registration calls only the NOVEL cases the
"matched cases" of the grid and says of the others that "they are not measured for cost", and the
suite's `measured()` helper returns NOVEL + OUT_OF_SUPPORT (14 cases in total). The number 28 is
right for the evidence artefact; the label is ambiguous against two other definitions in the same
milestone.

### F-15 — NOTE — Two narrated events have no retained witness

**File/lines:** `docs-v2/engineering/CRJ-01-ADAPTIVE-COMPETENCE.md:113-118` (the refused first
revision) and `:223` (the rejected tie rule).

The revision's first refusal ("the validation case `AC-DHX-13` asks the question the procedure was
proposed from, so it is not held out") and the tie rule's failure ("with it, the out-of-support
control was still served by the procedure") are historical events of the implementation session;
neither is in a retained artefact. The code comments corroborate the mechanisms
(`separateValidationCases`, `AdaptiveCompetenceServedTest.java:1448-1460`), but the events
themselves are unevidenced. Not wrong — just not witnessed, which is the milestone's own standard
applied to itself.

### F-16 — NOTE — The criterion embeds an assumption, and the milestone names it

**File/lines:** `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:727-741`.

I checked the criterion against the attack the task asks for, and it is sound: it knows no word,
no concept and no domain; it decides on the retrieval's own ids and coverages; it is
question-specific (two questions over the same support set get different verdicts); and the
comparison is against the *workspace's* reading, which is the right reference, because the
reference method reads the same unrestricted view, so the anchor is the reference's own first
citation (verified by `theGateAndTheReadingAgreeOnTheBestClaim`). What the criterion does embed is
an assumption — that the reference's **first** citation is the necessary anchor, a sufficient and
conservative condition, not a necessary one: a support set that holds the reference's second
citation but not its first is refused, although it could answer the question. That is the
milestone's §9.2 open question, correctly stated, and I record it here as a confirmed assumption
rather than a defect.

One latent caveat belongs with it: `HebbianBindings.stateFor(snapshot, conceptId)`
(`matrix-core/src/main/java/io/matrix/knowledge/HebbianBindings.java:287-308`) aggregates over the
claims of the concept **in the given snapshot**, so in a workspace with weakened bindings the
same claim's coverage can differ between the unrestricted and the restricted reading. The
milestone's corpora have no weakened bindings, so this does not arise in the retained evidence,
but it means "the two readings differ only in the claims they may cite" is not exactly true in
general, and it can make the recorded reason untrue (the support set *does* hold the anchor, but
the anchor's coverage falls below the floor in the restricted reading).

## WHAT I COULD NOT VERIFY

1. **No fresh execution.** I did not run Gradle, so I could not re-run the served suite, the
   deliberate RED, the corpus instruments or the two regression suites. My statements about
   behaviour rest on the retained XMLs (which I read in full) and on the source. Re-running
   `./gradlew :matrix-core:test --tests "io.matrix.knowledge.AdaptiveCompetenceServedTest"` and
   the gate self-test under the shared lock would settle whether the tree as it stands still
   produces the retained artefacts — note that the suite **writes** the two JSON artefacts, so a
   re-run will overwrite them with new timings and a new verdict distribution (F-01 will have to
   be re-read after any re-run).
2. **The RED's derivation.** I could not confirm what single expression was changed to produce
   the RED: the README's quoted pair is not the tree's code (`preconditionsHold` is
   `return refusalOf(input) == null;`, and `refusalOf` holds both checks as separate statements),
   and the RED's own failure messages show the adequacy check still firing inside `execute()`.
   What the RED *demonstrates* I could read off the XML (F-03); what was *edited* I could not.
   Reproducing it needs the implementer's exact diff, or a fresh RED with the edit documented in
   the README's own terms.
3. **The RED's failure count against the current suite.** The retained RED is a 12-test record
   and the tree now has 13 tests (F-12). Whether the current suite still goes red 4 times with
   the gate removed needs a re-run.
4. **The protocol's sealed path.** `CRJ-01-PROTOCOL.md` §5.5a/§5.6 govern a sealed evaluation
   that was **not** executed; I verified that the DEV-only declaration is present and consistent
   in every artefact that carries a number (`sealed: false` plus a `sealedNote` in the evidence,
   the cost rows and the split; the milestone document's status is `MEASURED — DEV ONLY`), and I
   found no claim in the milestone document or the evidence that a sealed result exists. I could
   not verify the isolation inspector's canary results themselves — they are reported in prose,
   with no retained transcript — but the conclusion (DEV-only) is the one the protocol permits
   for non-frozen work.
5. **The H2 inheritance.** I verified that the inherited manifest still verifies 20 of 20 and
   that the milestone's gate checks it; I did not re-derive the H2 rows themselves.
6. **The corpus's semantic quality and the oracle's strictness.** The oracle is an existing
   component (`SourceSpanOracle`) that I did not audit; I took its verdicts as the ground truth,
   as the milestone does. The separation probe's "16 of 96 claims reach a second document" (§9.1)
   I did not re-derive.
7. **The "independent case author" claim as a historical fact.** F-02 is a contradiction between
   two documents in the same milestone; whether a separate agent authored anything at all is a
   question about the session's history, which no artefact in the tree answers.
