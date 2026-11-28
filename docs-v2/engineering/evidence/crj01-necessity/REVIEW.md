# CRJ-01 / EPISTEMIC-NECESSITY — the independent review

**Reviewer:** the independent DSH reviewer (`necessity-reviewer`), session of 2026-11-28.
**Worktree:** `/home/alexandr-narbaev/Projects/matrix-worktrees/dsh-writer` · **Baseline:** `5402e876`.
**Scope:** the milestone document, the pre-registration, the case contract, the retained evidence,
the production code the milestone changed, the serving suite, the RED reproduction, the three
instruments and the four scripts.

> **This review changed no production file, no test file, no script and no document.** The one
> side effect of the review is declared in §5.4: the authorised Gradle command rewrites the
> retained rows and the manifest by the suite's own design; the digests before and after are
> given there.
>
> **The milestone document did not exist when this review opened.** It was written at
> **09:25:24** on 2026-11-28, during the review, after the evidence it describes (09:24) and
> before the session checkpoint (09:25:34) that lists it under "What exists". It is reviewed in
> full below, against the evidence as it stood — see finding 16.

---

## 1. Verdict

The **science** of the milestone largely holds and its **mechanisms** are real: the derivation in
`TaskPrerequisiteDerivation` is a genuine leave-one-source-out ablation of the workspace's own
reading and reads no oracle label, no case id, no gold and no threshold; the served default is
unchanged; the three refusals in `EvidenceAcquisition` are real and are exercised; the durable
retirement is a durable, concurrency-safe, conditional `UPDATE` that keeps the immutable row
readable. **The milestone's headline numbers are re-derivable from the retained rows as they
stand** — `ANCHOR` TP 30 / FP 18 / TN 24 / FN 0 (FPR `18/42 = 0.4286`), `NECESSARY_PREREQUISITE`
TP 24 / FP 9 / TN 33 / FN 6 (FPR `9/42 = 0.2143`, FNR `6/30 = 0.2000`), 15 cells where the arms
differ — and the milestone's own per-condition table (§4.1) matches the retained summary cell for
cell. **But the milestone cannot be published as it stands.** Three of its own claims are
contradicted by its own retained evidence: §5.1 says *"the journey selects a failure whose gold is
reachable"* and the journey's failure case records `goldReachable=false` in **every one** of its
rows; §7 says the RED reproduction *"is meaningful: it shows that the refusal comes from the
derivation"* and the reproduction's assertion is **false with the derivation on as well as off**;
§2 reports **two** whole-set collisions where the authors' own files record five, and one
follow-up collision (`EN-WBE-A5-1` / `EN-WBE-A1-2`) survives undetected by a checker that has a
dead no-op where that check belongs. Two of the milestone's mechanisms do not measure what they
claim: the *"identical inputs"* gate is tautological (one fingerprint per grid) and the gate's
rate check never re-derives the denominators it claims to re-derive. And the measurement's
validity itself is compromised for one of the three conditions: **eight retained rows assert that
a scope carries the case's evidence while the same row records that the served answer could not
cite it**; re-scoring them moves the headline FPR from `18/42` to `22/46` and from `9/42` to
`11/46`. Severity: **0 BLOCKER, 9 MAJOR, 8 MINOR, 4 NOTE.** The milestone's result is worth
keeping and its mechanism is worth keeping; the claims wrapped around them are not yet earned.

---

## 2. Findings

### 1. MAJOR — the RED reproduction fails with the derivation ON as well as OFF; §7 calls it "meaningful"

- **ARTEFACT:** `matrix-core/src/test/java/io/matrix/knowledge/EpistemicNecessityRedReproduction.java:72-98`;
  milestone `docs-v2/engineering/CRJ-01-EPISTEMIC-NECESSITY.md:246-260`; retained
  `evidence/crj01-necessity/CRJ-01-NECESSITY-REPRODUCTION-no-prerequisite.xml`.
- **What is wrong.** The reproduction builds a scope that holds **every source of the domain**
  (`for (String file : domain.files()) { … sources.add(…) }`, lines 72-80) and asserts
  `assessment.applicable()` is **false** (lines 89-92). A scope that holds every source carries
  every requirement the derivation can produce, so the proposal rule **admits** it — with the
  derivation on as well as off. The retained rows prove it:

  ```
  # re-derived from CRJ-01-NECESSITY-GRID-ROWS.json
  (hpd, BASELINE, EVIDENCE_DERIVED, NECESSARY_PREREQUISITE, EN-HPD-A4-4)
      admitted=true   scope = all 4 sources of hpd
      reason: "the support set carries a sufficient set of the evidence the task needs:
               9 requirement(s) of the 9 the workspace's own reading cannot do without"
  ```

  and for the reproduction's own subject, `EN-WBE-A1-4` (wbe, BASELINE, all three strategies,
  `NECESSARY_PREREQUISITE`): `admitted=true`, reason *"the support set carries a sufficient set of
  the evidence the task needs: 2 requirement(s) of the 2 the workspace's own reading cannot do
  without"*. The first assertion therefore fails in **both** states: the test "would fail for any
  reason". It shows only the fallback — which the retained failure message does show: *"the task's
  necessary evidence was NOT derived, so this rule could not decide and the incumbent's verdict
  stands"*.
- **The milestone's claim.** §7 (`:256-258`): *"The reproduction is **meaningful**: it shows that
  the refusal comes from the **derivation**, not from the rule's name."* That is exactly what the
  reproduction does **not** show: the assertion is equally false with the derivation running, so
  it cannot attribute the refusal to the derivation.
- **A second, deeper mismatch.** The subject is the first `MISSING_PREREQUISITE` case. That
  condition means *"a correct answer needs a proposition the corpus does not contain"*
  (`CRJ-01-NECESSITY-CASE-CONTRACT.md:111`), and the case set records it as `answerable=false`. A
  prerequisite that is **not in the workspace** cannot be found by an ablation **of the
  workspace**; the reproduction picked the one condition the proposal structurally cannot address.
- **What would make it right.** Make the reproduction *contrastive*: the same case and the same
  scope measured twice — with the derivation on, the rule must refuse **and name the missing
  requirement**; with it off, it must do neither — and use a case whose necessary evidence **is in
  the workspace but outside the scope** (the journey's `EN-WBE-A1-3` shape: the necessary
  sentence is in `wbe-d-aerodynamics.md`, the declared scope is `a,b,c`).

### 2. MAJOR — the "identical inputs" gate is tautological

- **ARTEFACT:** `matrix-core/src/test/java/io/matrix/crj/necessity/Crj01NecessityGrid.java:203`
  (`String inputFingerprint = fingerprint(input);` — computed **once per grid**) and `:284` (the
  same constant is written into every row); the suite's check
  `EpistemicNecessityServedTest.java:312-322`; the gate's check `scripts/necessity-gate.py:124-131`;
  milestone `:111-116`.
- **What is wrong.** Because the fingerprint is a constant per grid, "the arms of a cell carry one
  fingerprint" **cannot fail**: the suite collects `row.inputFingerprint()` over all rows of a
  `(domain, condition)` and asserts `hasSize(1)`; the gate does the same per cell. Both would pass
  even if the two arms had read different views. Pre-registration §2.3 makes this a gate in its own
  words: *"every row records the fingerprint of the view it was measured against. The suite and the
  gate require the two arms of a cell to carry one fingerprint. **A pair measured against
  different views is a defect, not a result.**"* The milestone repeats it as a property of the
  measurement (`:113-114`).
- **Evidence.** 144 rows carry exactly **6** distinct fingerprints — one per `(domain, condition)`
  — which is what a per-grid constant produces, not what a per-row measurement of the view would
  produce. The fingerprint also does not cover the weakened bindings, so it cannot witness the
  "same bindings" half of the pre-registration's requirement either.
- **Mitigation, stated for fairness.** The *substance* holds: one `KnowledgeSnapshot` object is
  passed to both arms (`Crj01NecessityGrid.java:243-244`), and `assessScope` does not mutate the
  workspace, so the two arms really did read the same view. What is missing is the *witness*.
- **What would make it right.** Compute the fingerprint **inside** `assessScope` from the view each
  arm actually read, record it per row, and let the suite and the gate compare the two arms'
  values rather than a constant.

### 3. MAJOR — under `SUPERSEDED` the oracle's "carries" diverges from the served path's; eight retained rows contradict themselves and §5.1 does not report it

- **ARTEFACT:** `matrix-core/src/test/java/io/matrix/crj/necessity/Crj01NecessityGrid.java:228-239`
  (the oracle resolves a file through `input.sourceIdByFile()`); the map is captured in
  `EpistemicNecessityServedTest.java:998-1004` from the **original** imports, and `supersede()`
  (`:1048-1062`) then imports a **revised** document under the same file name and selects it as the
  current revision — so the current view holds a **new source id** for that file while the oracle's
  map still names the old one. Milestone §5.1 (`:205-224`) lists what the path does **not** have;
  this is not among the items it lists.
- **What is wrong.** Under `SUPERSEDED` the oracle's "the scope carries this file" test compares
  the scope's source ids against ids the workspace's current view no longer holds. The retained
  rows show the damage directly — eight rows assert that the scope **carries** the case's evidence
  while the *same row* records that the served answer **could not cite it**:

  ```
  (hpd, SUPERSEDED, DECLARED, ANCHOR, EN-HPD-A3-3)
      oracleAdequate = true        carriedFiles = ["hpd-a-dispensing.md"]
      citedClaimIds  = ["clm-b0dc5e6f"]      evidenceComplete = false
      unreachablePropositions = ["hpd-a-dispensing.md: A unit dose with a label that the
                                 printer produced incorrectly requires a manual check before
                                 the ward receives it."]
  ```

  All eight are `hpd`/`SUPERSEDED` (DECLARED and EVIDENCE_DERIVED × `EN-HPD-A3-3`, `EN-HPD-A4-4`
  × both arms) and are counted **TP**. The scope holds the *original* source id, which the
  projected view has dropped, so the restricted reading cannot cite that document; the oracle calls
  that "carrying" it. The defect cuts the other way too: under `SUPERSEDED`/`CITATION_DERIVED` the
  scope holds the **revised** id and the oracle reports `missingFiles=["hpd-a-dispensing.md"]` for
  a document the scope in fact carries.
- **Why the milestone's numbers move.** Re-scoring exactly those eight rows so that a scope which
  could not cite the evidence does not count as carrying it (my re-derivation, §5.2):

  | rule | as retained | re-scored |
  |---|---|---|
  | `ANCHOR` | TP 30 · FP 18 · TN 24 · FN 0 · FPR `18/42 = 0.4286` | TP 26 · FP 22 · TN 24 · FN 0 · FPR `22/46 = 0.4783` |
  | `NECESSARY_PREREQUISITE` | TP 24 · FP 9 · TN 33 · FN 6 · FPR `9/42 = 0.2143` | TP 22 · FP 11 · TN 35 · FN 4 · FPR `11/46 = 0.2391` |

  The **qualitative trade survives** (the proposal still has the lower false-admission rate and
  the higher false-rejection rate), but the milestone's headline numbers in §1 and §4.1 do not.
- **What would make it right.** Resolve the file→source map against the **view the row was
  measured against** (the projected view's own source ids), or score "carries" by proposition via
  the `claimIdByProposition` map the grid already builds; then re-run the grid, the suite and the
  gate, and re-issue §1 and §4.1 with the new denominators. If the milestone keeps the present
  numbers, §5.1 must say why the eight rows are labelled as they are.

### 4. MAJOR — the follow-up round's uniqueness rule is violated, §2's collision count is wrong, and the checker has a no-op where the check belongs

- **ARTEFACT:** `scripts/necessity-case-check.py:118-121`:

  ```python
  if assignment not in {"A5", "A6"}:
      for entry in (doc.get("cases") or []):
          if isinstance(entry, dict) and entry.get("slice") == "EVALUATION":
              pass
  ```

  Dead code, sitting exactly where the follow-up rule of `CRJ-01-NECESSITY-CASE-CONTRACT.md:255-258`
  belongs (*"The necessary sentence of a follow-up case must not be a sentence that another case of
  the same domain already uses"*). Milestone §2 (`:62-69`).
- **What is wrong (a): one collision survives.** Re-derived from
  `docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json`:

  ```
  wbe-b-inspection.md | "An ultrasonic sensor measures the thickness of the leading edge of a
                        wind-turbine blade without touching that blade."
      -> EN-WBE-A1-2 (A1, VALIDATION)  and  EN-WBE-A5-1 (A5, VALIDATION)
  ```

  `EN-WBE-A5-1` is a follow-up case whose necessary sentence is already another case of the same
  domain's necessary sentence. The gate cannot see it (`scripts/necessity-gate.py:90-103` compares
  slices against each other, and both cases are `VALIDATION`); the checker cannot see it (the
  within-assignment rule is vacuous for A5/A6, whose four cases are all `VALIDATION`).
- **What is wrong (b): the milestone's collision count is wrong.** §2 says *"**two** collisions
  were found (a necessary sentence shared between a `PROPOSAL` and a `VALIDATION` case) and
  **returned to the authors**"*. The authors' own files record five case-level collisions across
  the two follow-up assignments: `A5.json` — *"the necessary sentences of EN-WBE-A5-1 and
  EN-WBE-A5-4 were already the necessary sentences of cases of another assignment"* (two);
  `A6.json` — *"three of my necessary sentences were already necessary sentences of first-round
  hpd cases"* (three). And the parenthetical characterisation is wrong: the follow-up round's
  cases are all `VALIDATION`, and their collisions were with **first-round** cases (which include
  `EVALUATION` cases) — not "a `PROPOSAL` and a `VALIDATION` case".
- **What is wrong (c): the conclusion the reader draws.** §2 ends *"The final set has **zero**
  cross-slice collisions."* That is true — I re-derived it — but it invites the reading that the
  whole-set rule now holds, and it does not: one violation remains, and the milestone does not
  record it, although the contract requires that *"a collision is recorded rather than hidden"*.
- **Fairness.** The whole-set check was really performed and the authors really did rebuild their
  cases — `A5.json` and `A6.json` disclose both the collisions and the repair, and `A6.json` even
  states the limitation it could not remove. That is to the milestone's credit. What is missing is
  the final, machine-checked sweep.
- **What would make it right.** Implement the whole-set rule in the checker (for `A5`/`A6`: a
  necessary sentence may not be the necessary sentence of any other case of the same domain), or
  add it to the gate; report the true collision count; and record the remaining
  `EN-WBE-A5-1` / `EN-WBE-A1-2` collision in the milestone document.

### 5. MAJOR — the case set's own provenance records four authors where there are six

- **ARTEFACT:** `docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json` → `author.role`: *"four
  INDEPENDENT DSH case authors, commissioned as separate bounded assignments of four questions
  each"*, `author.independence`: *"… The authors did not read each other's files …"*, and
  `author.assembledBy`: *"this script; it reads the four authors' files and adds nothing of its
  own"*. The assembler writes them at `scripts/necessity-cases.py:95-96` and `:105-106`. The suite
  repeats it: `EpistemicNecessityServedTest.java:59` (*"**Four** independent DSH case authors"*),
  `:120` (*"The case set — four independent authors' work"*) and the `@DisplayName` at `:169-170`
  (*"the case set is **FOUR INDEPENDENT** authors' work"*) — while the body of the same test
  asserts `assignments).hasSize(6)` (`:191-202`). The instrument repeats it:
  `Crj01NecessityCases.java:17-26`.
- **What is wrong.** There are **six** assignments of four cases (24 cases): A1–A4 (first round)
  and A5/A6 (the follow-up round, commissioned because the promotion rule needs five held-out
  cases and the first round produced two per domain — `CRJ-01-NECESSITY-CASE-CONTRACT.md:231-249`).
  The gate demands six (`scripts/necessity-gate.py:53`), the evidence README says *"Six separately
  commissioned DSH subagents"* (`README.md:18`), the MANIFESTS list six (`MANIFESTS.md:9-14`), the
  checkpoint says six, and **the milestone document itself says six** (`:6`, `:39-49`). The case
  set's **own provenance record** — the record the suite asserts on — says four.
- **What would make it right.** Make the count consistent (six) in `scripts/necessity-cases.py`,
  in the instrument's and the suite's prose and in the `@DisplayName`; re-assemble the case set
  (its digest changes) and re-run the gate.

### 6. MAJOR — an offer can be counted as fulfilled for evidence the workspace does not hold

- **ARTEFACT:** `matrix-core/src/main/java/io/matrix/knowledge/EvidenceAcquisition.java:175-198` —
  after the channel check (`:94-101`), the staleness check (`:108-131`) and the conflict check
  (`:132-174`), the fulfilment compares **only claim ids and propositions** against
  `request.missing()`:

  ```java
  for (EvidenceRequirement requirement : request.missing()) {
      if (!carried.contains(requirement.claimId())
              && !propositions.contains(requirement.proposition())) {
          unanswered.add(requirement.claimId());
      }
  }
  ```

- **What is wrong.** Nothing verifies that an offered claim is **in the workspace's current
  view**. The staleness check asks only whether the claim's *source* is in the view (`:119-125`),
  so a claim object that carries the right proposition and an existing source id — a claim the
  workspace has never held and that was never imported — passes all three refusals and is counted
  **FULFILLED**. The class javadoc is candid that it *"does not import anything"* (`:33-35`), but
  the milestone presents the fulfilment as the acceptance of evidence (milestone §5 step 4, `:193-194`:
  *"the evidence offered through the authorised channel is accepted"*), and `FULFILLED` is a
  stronger word than the code's semantics support.
- **What is right, for the record.** The three named refusals are real: an unauthorised channel is
  refused first and for a named reason; a stale source is refused; a conflicting offer is refused
  by the **production** detectors (`ClaimConflictDetector` at `:148`, `ContradictionDetector` at
  `:157`) — and the test's conflict pair is honest, because the corpus's own rain-drop
  contradiction is *not* in the shape `ContradictionDetector` recognises, which the test itself
  reports (`EpistemicNecessityServedTest.java:635-639`) rather than hiding.
- **What would make it right.** Require the offered claims to be present in the current view (or to
  have been imported by the authorised channel) before counting a fulfilment — or rename the
  disposition to `ACCEPTED_FOR_IMPORT` and make the import a separate, checkable step.

### 7. MAJOR — the test named "the EXACT revised procedure serves a DIFFERENT task" never revises anything

- **ARTEFACT:** `matrix-core/src/test/java/io/matrix/knowledge/EpistemicNecessityServedTest.java:763-847`,
  `@DisplayName` at `:764-765`; milestone §6 (`:226-244`) and §5.
- **What is wrong.** The body promotes a **fresh** `ANCHOR` competence over the declared scope
  (`:778-781`), restarts the service (`:800-803`) and serves **that** competence on the distinct
  task (`:805-807`). No retirement, no revision, no revalidation and no widened scope is involved
  anywhere in the test: the "revised procedure" of the name is absent. Pre-registration §9.8
  requires *"after a restart, the exact revised procedure serving a different task, with an
  independent oracle for the effects"* — the independent oracle is there (`:817-826`) and the
  different task is there (`:770-795`), but the revised procedure is not. The first assertion is
  also weak: `assertThat(narrowRun.response().getStatus()).isIn(201, 422)` (`:808-810`) accepts
  either outcome, and the discriminating assertion is inside the branches (`:830-846`).
- **What would make it right.** Retire the narrow competence, learn and validate the wider one
  (the journey's `widened` competence), restart, and serve **that** version on the different task,
  asserting that the version served is the revised one and that the retired version stays retired.

### 8. MAJOR — the journey's failure case has an **unreachable** gold; §5 and §5.1 say the opposite

- **ARTEFACT:** milestone `:184-186` (step 1: *"the reference reading on the same question,
  workspace and moment carries it"*) and `:213-216` (§5.1: *"the journey selects a failure whose
  gold is reachable"*); the journey's subject selection
  `EpistemicNecessityServedTest.java:384-412`.
- **What is wrong.** The journey takes its failure from the measurement, not by choice
  (`:384-405`), and the first row that matches is
  `(wbe, BASELINE, CITATION_DERIVED, ANCHOR, EN-WBE-A1-3)`. The retained rows for that case show
  `goldReachable = false` in **every one of its 18 rows**, including the nine rows in which the
  **reference** reading served it:

  ```
  EN-WBE-A1-3, BASELINE, CITATION_DERIVED, NECESSARY_PREREQUISITE
      admitted=false  served=reference  goldReachable=false
      unreachablePropositions = ["wbe-d-aerodynamics.md: An erosion pit on a wind-turbine blade
        increases the roughness of that blade and reduces its lift in the same operation.",
        "wbe-d-aerodynamics.md: The power of a wind-turbine depends on the drag of its blades
        and on the lift that those blades produce."]
  ```

  So the workspace's own reading does **not** reach the case's necessary sentences, and the
  milestone's two sentences — *"the reference reading … carries it"* and *"the journey selects a
  failure whose gold is reachable"* — are false for the very case the journey walks. The journey's
  step 5 (*"the served answer cites the evidence the narrow scope could not carry"*) is asserted
  only as *the citations changed* (`assertThat(citedOf(after.response())).isNotEqualTo(cited)`,
  `:570-572`), not as *the narrower scope's evidence is now cited*.
- **Why it matters.** The journey is the milestone's demonstration that a missing prerequisite
  becomes visible and repairable. It is built on a case whose evidence **the workspace cannot
  reach at all** — the retriever, not the scope, is the obstacle — which is exactly the
  distinction the milestone says it wants to keep ("the reading" versus "the task").
- **What would make it right.** Select the journey's failure from the cases whose gold the
  workspace's own reading **does** reach (the `goldReachable=true` rows), and assert the
  reference's carrying explicitly — or state plainly that the journey's case measures the
  retriever, and add a second journey over a reachable case.

### 9. MAJOR — `goldReachable` does not mean what it says, so the milestone's reachability report is not re-derivable from it

- **ARTEFACT:** `matrix-core/src/test/java/io/matrix/crj/necessity/Crj01NecessityGrid.java:102-105`
  (the field is documented as *"whether the workspace's own reading reaches EVERY necessary claim
  of the case. It is a property of the CASE and the RETRIEVER, not of the scope"*) against
  `:256-275` (it is computed from the **served** answer's cited claims, and the served answer is
  the **restricted** reading whenever the rule admitted the scope, `:248`); milestone `:213-216`
  (*"Four of the eight evaluation cases have necessary sentences the workspace's own reading does
  **not** reach"*).
- **What is wrong.** For an admitted row the field measures the **scope's** reachability, not the
  workspace's own reading's — the opposite of what the field and the milestone say. Only the
  refused rows (where the reference served) measure the workspace's own reading. The milestone's
  "four of the eight" cannot be re-derived from the reference rows: of the eight evaluation cases,
  the reference reading demonstrably misses the gold in **three** (`EN-WBE-A1-3`, `EN-WBE-A2-3`,
  `EN-HPD-A3-4`), demonstrably reaches it in **one** (`EN-HPD-A4-3`), **varies by condition** in
  two (`EN-HPD-A3-3`, `EN-HPD-A4-4`), and is **not recorded at all** in two
  (`EN-WBE-A1-4`, `EN-WBE-A2-2` never refused, so the reference reading never served them). The
  fourth case in the milestone's count, `EN-WBE-A1-4`, is counted from the *restricted* reading.
- **What would make it right.** Record the reference reading's reachability as its own field
  (computed from the reference answer, which the grid already computes for every case), and
  report the reachability from that field.

### 10. MINOR — the gate's rate check is a tautology and does not re-derive the denominators it claims to re-derive

- **ARTEFACT:** `scripts/necessity-gate.py:164-169`:

  ```python
  check(measured_rate["denominator"] == measured_rate["numerator"] * 0
        or measured_rate["denominator"] >= measured_rate["numerator"], …)
  ```

  The condition reduces to *"denominator == 0 **or** denominator ≥ numerator"*; it never checks that
  FPR's denominator is `FP+TN`, FNR's is `FN+TP`, `FP/(TP+FP)`'s is `TP+FP` and `FN/(TN+FN)`'s is
  `TN+FN`. The docstring at `:13` says *"the summary's counts and rates are re-derived from the
  rows"* — the **counts** are, the **rates** are not. The suite's own check
  (`EpistemicNecessityServedTest.java:352-366`) likewise only asserts null-ness.
- **Why it matters.** Pre-registration §5 makes the separate denominators a commitment (*"The two
  false rates are never folded together"*), and the milestone's §4.1 reports ten per-condition rate
  rows. A summary that folded the two false rates together, or gave FPR the wrong denominator,
  would pass both the suite and the gate.
- **What would make it right.** Re-derive each rate's numerator **and** denominator from the rows in
  the gate and compare both with the summary.
- **For fairness:** the instrument computes the denominators correctly
  (`Crj01NecessitySummary.java:206-211`), and I re-derived them independently — see §3.

### 11. MINOR — `recordRevalidated` always writes version `1.2.0`, so a second revalidation collides with the store's immutability rule

- **ARTEFACT:** `matrix-core/src/main/java/io/matrix/cognitive/report/ReportProcedure.java:110`
  (`VERSION_REVALIDATED = "1.2.0"`) and `recordRevalidated` (`:252-271`);
  `CognitiveStore.recordProcedure` (`:1229-1239`) throws
  `CognitiveConflictException(IMMUTABLE_RECORD)` when the same `(id, version)` arrives with a
  different definition.
- **What is wrong.** A revalidation of a competence whose retired version is already `1.2.0`, or a
  revalidation whose `revalidated=` link names a different version, produces a **different
  definition under the same `(id, "1.2.0")`** and raises instead of writing a new version. The
  version should be derived from the version being revalidated.
- **What would make it right.** Derive the revalidated version from the retired one (an increment
  or a suffix), or catch the conflict and write a distinct version.

### 12. MINOR — `lastAcquisitionRequest()` can return a request that does not correspond to the last refusal

- **ARTEFACT:** `matrix-core/src/main/java/io/matrix/cognitive/report/ReportProcedureMethod.java:295-309`
  (`rememberAcquisition` resets `acquisition = null` only on its own path) and `refusalOf`
  (`:241-276`), which returns early — **without clearing `acquisition`** — for *"the procedure …
  has been invalidated"*, *"carries no support set"* and *"its support set is no longer present in
  the workspace"*, and also returns `null` (success) without clearing it.
- **What is wrong.** After such a refusal the accessor still returns the request from the earlier
  refusal, although its javadoc (`:337-346`) promises *"It is **null** when the refusal was not for
  a missing prerequisite"*. `ArtifactReportResource.fallbackNotes` (`:647-651`) puts that request
  into the journey's notes, so a stale request can be published as the current diagnosis.
- **What would make it right.** Clear `acquisition` at the top of `refusalOf`, next to
  `lastRefusal = null` (`:242`).

### 13. MINOR — the retained evidence is written by the suite, is not byte-reproducible, and its manifest is verified by nothing

- **ARTEFACT:** `EpistemicNecessityServedTest.theEvidenceIsWritten` (`:851-881`) writes
  `CRJ-01-NECESSITY-GRID-ROWS.json` and `CRJ-01-NECESSITY-GRID-SUMMARY.json` **into the
  repository** and rewrites `CHECKSUMS.sha256` (`:1331-1348`); the rows carry `selectionNanos`,
  `readNanos`, `fallbackNanos` (`Crj01NecessityGrid.java:117-118`).
- **What is wrong.** (a) The rows file is **not byte-reproducible** — the timings differ on every
  run, so the manifest cannot be re-derived by re-running the suite. (b) The manifest's own
  coverage claim (`README.md:82-85`) is decorative: neither the suite nor
  `scripts/necessity-gate.py` ever verifies a digest against `CHECKSUMS.sha256`. (c) The retained
  XML is copied by `scripts/necessity-retain-evidence.sh` from the build output, so the retained
  XML and the retained rows can come from **different runs** — the very hazard the README warns
  about (`README.md:48-50`) applies to the pair.
- **What would make it right.** Keep the timings out of the retained rows (or round them away), and
  have the gate verify every digest in `CHECKSUMS.sha256` and that the retained XML's run matches
  the rows'.

### 14. MINOR — "applicability" names two different decisions, so the claim that the four things are kept apart is not verifiable

- **ARTEFACT:** `matrix-core/src/main/java/io/matrix/knowledge/EvidenceRequirement.java:26-27`
  and `TaskPrerequisites.java:40-41` say applicability *"is the route's decision, and this record
  does not make it"*; but `ScopeCriterion.Decision.applicable` (`ScopeCriterion.java:211`),
  `ScopeAssessment.applicable` (`ScopeAssessment.java:35`) and
  `ReportProcedureMethod.refusalOf` (`ReportProcedureMethod.java:270-275`) **do** decide "may this
  scope serve this question", and the route turns that into "this competence does not serve this
  task". The milestone repeats the claim at `:103-104`.
- **What is wrong.** The separation the milestone claims (relevance / confidence / provenance /
  applicability) is real for relevance, confidence and provenance (§3), but the fourth term is used
  for two decisions in the same code base, so a reader cannot check the claim against the code.
- **What would make it right.** Name the two decisions differently — *scope adequacy* (the rule's
  verdict) and *competence applicability* (the route's) — in the prose and in the records.

### 15. MINOR — the evaluation slice covers 5 of the 9 conditions, and the milestone's §4.1 does not say so

- **ARTEFACT:** `docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json` — the eight `EVALUATION`
  cases carry `JOINTLY_NECESSARY` (2), `MISSING_PREREQUISITE` (2), `SUPERSESSION` (2),
  `CHANGED_BINDINGS` (1) and `ALTERNATIVE_SUFFICIENT` (1). `LOWER_RANKED_NECESSARY`,
  `CONTRADICTION`, `AMBIGUITY` and `UNANSWERABLE` appear only in `PROPOSAL`/`VALIDATION`.
- **Evidence.** `CRJ-01-NECESSITY-GRID-SUMMARY.json` has 10 `ruleAndCaseCondition` cells — five
  conditions × two rules — and no cell for the other four. Pre-registration §6 says the milestone
  *"reports the confusion matrix by condition as well as over the whole grid"*, which is true of
  the five measured ones and silent about the other four. The milestone's §4.1 table lists the
  five and does not say that the other four are unmeasured.
- **What would make it right.** Say in §4.1 which of the nine conditions the measurement covers and
  which it does not.

### 16. MINOR — "18 scopes" and "9 and refused 6" are row counts, not scope counts

- **ARTEFACT:** milestone `:26-28` (*"the incumbent **admitted 18 scopes** the oracle calls
  inadequate and refused none it calls adequate … the proposal **admitted 9 and refused 6**"*).
- **Evidence.** The 18 are 18 **rows** over 9 distinct `(domain, condition, strategy, scope)`
  instances, which are 7 distinct scope sets; the proposal's 9 FPs are 9 rows over 9 instances and
  7 scope sets, and its 6 FNs are 6 rows over **3** instances. The confusion matrix is per row, so
  the numbers are right; the noun is wrong.
- **What would make it right.** Say "18 measured (case, scope) pairs" or "18 rows".

### 17. MINOR — §4's "The oracle is read after each arm has served" is false about the instrument's order

- **ARTEFACT:** milestone `:120-121`; `Crj01NecessityGrid.java:228` (the label is computed)
  against `:241` (the arms are judged).
- **What is wrong.** The oracle's label is computed **before** the arms are judged, not after they
  have served. The substance the sentence is there to protect does hold — the label never reaches
  an arm: `ScopeCriterion.judge` receives only the two readings, the derived prerequisites and the
  scope's source ids (`ScopeCriterion.java:260-262`).
- **What would make it right.** Reword to "the oracle's label is computed from the case set and the
  scope, and never reaches an arm".

### 18. NOTE — the RED XML is never gated

- **ARTEFACT:** `scripts/necessity-gate.py:176-182` checks only
  `TEST-io.matrix.knowledge.EpistemicNecessityServedTest.xml`. The retained
  `CRJ-01-NECESSITY-REPRODUCTION-no-prerequisite.xml` is never inspected — not even to check that
  it carries the expected failure.
- **What would make it right.** Gate it: the RED XML must exist, must carry exactly one failing
  test in `EpistemicNecessityRedReproduction`, and the failure must be the expected assertion.

### 19. NOTE — the authors' files are revisable, so the digests cannot witness a post-hoc edit

- **ARTEFACT:** `A5.json` (mtime 2026-11-28 09:21) and `A6.json` (2026-11-27 21:43) were written
  after the first-round files (2026-11-27 21:18-21:22) — the revision round the authors document in
  their own notes. The digests in `provenance.assignments[].sha256` were computed **after** the
  revisions (`scripts/necessity-cases.py:64`).
- **What it means.** `README.md:82-85` claims that a change to an author's bytes *"is visible in the
  case set and not only here"* — true only for changes made **after** the digest was recorded. The
  pre-revision bytes have no witness; the authors' notes are the only record of what was revised.

### 20. NOTE — the derivation switch is a mutable static in production code

- **ARTEFACT:** `ScopeCriterion.java:288` (`static volatile boolean derivationEnabled = true;`),
  toggled by `ScopeCriterion.derivationEnabled(boolean)` (`:304-306`) and used at
  `KnowledgeWorkbench.java:786-790`.
- **Why it is a note.** It is off in every normal run, package-private, and restored in the
  reproduction's `@AfterEach`. But it is a global mutable flag in production code; if the RED class
  is ever enabled in a run that also executes the serving suite in the same JVM, the serving
  suite's proposal arm could be measured with the derivation off. No JUnit parallel-execution
  setting is configured in this build, so the window is currently narrow.
- **What would make it right.** Thread the switch through the request (a parameter) rather than
  through a static.

### 21. NOTE — the milestone document was written during the review

- **ARTEFACT:** `docs-v2/engineering/CRJ-01-EPISTEMIC-NECESSITY.md`, mtime **2026-11-28 09:25:24**;
  the retained evidence 09:24; the session checkpoint 09:25:34.
- **What is wrong, as a process matter.** When this review opened (09:24) the document did not
  exist: `find . -iname "*EPISTEMIC*"` returned only Java classes, `ls docs-v2/engineering/ | grep
  -i crj` did not list it, and `git status` did not show it. It was created at 09:25:24, after the
  measurement it describes and after the checkpoint that already listed it under "What exists"
  (the checkpoint's own mtime is 09:25:34, so it was written to match). The document was therefore
  reviewed against the evidence as it stood at 09:24, and every claim in it was checked against the
  retained rows rather than taken on trust — findings 1, 3, 4, 8, 9 and 16 are exactly the claims
  that did not survive that check.
- **What it means for the milestone.** A milestone document written after the measurement cannot
  discharge the pre-registration's discipline ("the milestone reports against it whether it likes
  the outcome or not") in the strong sense; it can only be checked afterwards, which is what this
  review is.

---

## 3. What is sound

Checked, with the command that shows it.

1. **The milestone's headline numbers are re-derivable from the retained rows.** From
   `docs-v2/engineering/evidence/crj01-necessity/CRJ-01-NECESSITY-GRID-ROWS.json` (144 rows):

   | rule | TP | FP | TN | FN | FPR = FP/(FP+TN) | FNR = FN/(FN+TP) | FP/(TP+FP) | FN/(TN+FN) |
   |---|---|---|---|---|---|---|---|---|
   | `ANCHOR` | 30 | 18 | 24 | 0 | `18/42 = 0.4286` | `0/30 = 0.0` | `18/48 = 0.3750` | `0/24 = 0.0` |
   | `NECESSARY_PREREQUISITE` | 24 | 9 | 33 | 6 | `9/42 = 0.2143` | `6/30 = 0.2000` | `9/33 = 0.2727` | `6/39 = 0.1538` |

   These match `CRJ-01-NECESSITY-GRID-SUMMARY.json` cell for cell, the milestone's §1 and §4.1
   tables, and the checkpoint's table (`.opencode/matrix-dsh-checkpoint.md:36-39`) exactly. 72 rows
   per rule; 15 cells where the arms differ — **9** where the incumbent admitted a scope the oracle
   calls inadequate and the proposal refused it, **6** where the incumbent admitted an adequate
   scope and the proposal refused it; and **no** cell in which the proposal admitted what the
   incumbent refused. The milestone's §4.2 reading of the trade (the incumbent's false admissions
   concentrated in `JOINTLY_NECESSARY` 9 of 9 and `MISSING_PREREQUISITE` 9 of 18; the proposal's
   false rejections in `ALTERNATIVE_SUFFICIENT` and `CHANGED_BINDINGS`, 3 each of 8) is re-derivable
   from the same rows.

2. **The milestone's per-condition table (§4.1) is exact.** All ten `ruleAndCaseCondition` rows of
   the milestone's table — counts, `FPR`, `FNR` and the two `UNDEFINED (0 denominator)` markers —
   match the retained summary cell for cell, including the two cells whose empty denominator is
   named (`ANCHOR|JOINTLY_NECESSARY`, `ANCHOR|MISSING_PREREQUISITE`, `proposal|JOINTLY_NECESSARY`,
   `proposal|MISSING_PREREQUISITE`). The undefined convention is therefore **exercised**, not
   merely declared, and the two false rates are never folded together.

3. **The derivation is genuinely experimental and reads no oracle.** `TaskPrerequisiteDerivation`
   takes only the workspace, the statistics view, the question, the level and the historical flag
   (`:69-70`); it computes the reference reading with the retriever itself (`:88-90`), builds the
   candidate sources from the reading and from the sources holding the same propositions
   (`:102-127`), performs the **leave-one-source-out ablation** over the sources in a fixed order
   (`:129-141`), and derives the requirements from the kept sources (`:158-203`) with the
   alternatives (`:233-304`). A grep of the production tree for
   `EN-WBE|EN-HPD|oracle|gold|DEV-CASES|necessaryEvidence` returns nothing in the new classes. The
   rule itself (`ScopeCriterion.NECESSARY_PREREQUISITE`, `:167-200`) compares a derived set of
   claims with the claims a scope carries, and it **says so when it cannot decide** (`:172-185`)
   instead of silently answering for another rule.

4. **Relevance, confidence, provenance and applicability are genuinely held apart.** The
   requirement carries the claim id, the proposition, the source and the epistemic standing
   (`EvidenceRequirement.java:36-41`) and **not** the claim's confidence; the derivation never
   reads a confidence number and applies no threshold of its own (it uses the retriever's own
   `configuredMinimumCoverage()`, `TaskPrerequisiteDerivation.java:83`). The source decides
   **carrying** (`PrerequisiteAssessment.java:72-88`) and never decides **necessity**, which the
   ablation decided. (The applicability naming caveat is MINOR 14.)

5. **The served default is unchanged.** `assessSupport` still judges by `ANCHOR`
   (`KnowledgeWorkbench.java:691-696`), `ScopeCriterion.of(null|unknown)` returns `ANCHOR`
   (`ScopeCriterion.java:317-327`), and a procedure whose applicability names no rule is judged by
   the incumbent (`ReportProcedure.criterionOf`, `:189-200`). The derivation is paid for only when
   the rule that needs it is the rule that was asked for (`ScopeCriterion.needsPrerequisites()`,
   `:274-276`; `KnowledgeWorkbench.java:786-790`).

6. **The three refusals are real.** The unauthorised channel is refused first and for a named
   reason (`EvidenceAcquisition.java:94-101`); the staleness check is meaningful — a claim whose
   source is not in the current view is refused (`EvidenceAcquisition.java:119-131`), and the test
   exercises it with a claim re-homed onto a source the workspace does not hold
   (`EpistemicNecessityServedTest.java:582-592`); the conflict check uses the **production**
   detectors (`EvidenceAcquisition.java:148`, `:157`) and the test's pair is honest — the corpus's
   own rain-drop contradiction is *not* in the shape `ContradictionDetector` recognises, and the
   test says so instead of pretending otherwise (`:635-639`). The milestone reports that corpus
   finding itself (§5.1, `:207-212`). (The hole is MAJOR 6.)

7. **The durable lifecycle is durable, concurrency-safe and history-preserving.**
   `CognitiveStore.retireProcedure` (`:1332-1350`) is a single conditional statement
   (`… AND invalidated_at IS NULL`) inside a `synchronized` boundary: two racing callers produce
   one retirement and one refusal, and a second retirement of the same version is refused
   (`EpistemicNecessityServedTest.java:707-711`). The definition columns are untouched — the
   store's immutability comparison (`procedureDefinitionOf`, `:1417-1431`) does not include
   `invalidated_at` — so the retired version stays **readable** with its history
   (`EpistemicNecessityServedTest.java:694-705`) and a re-write of the same definition is a no-op,
   so the retirement survives. The route offers only `procedure.active()` versions
   (`ArtifactReportResource.java:828`), and `invalidated_at` is exactly what the retirement
   writes, so the stale selection is prevented after a restart. The revalidation is honest about
   being a **new version** that names the one it revalidates (`ReportProcedure.recordRevalidated`,
   `:252-271`; `ReportProcedureLearner.learn(..., revalidates)`, `:238-296`), and the retired
   version stays retired (`EpistemicNecessityServedTest.java:755-760`). The two added columns are
   additive and null for every row written before the milestone (`CognitiveStore.java:519-530`).

8. **The pre-registration's "may not" list is honoured in the code.** No winner is declared (the
   summary has no winner field and the milestone's §8 says so explicitly); no threshold is proposed;
   the served default is unchanged (point 5); no sealed evaluation was executed and no isolation was
   re-probed (milestone `:9-13`, `evidence/crj01-necessity/README.md:7-11`, `:64-78`); the two
   arms are computed in one process from one snapshot (`Crj01NecessityGrid.java:205-291`). The
   stopping rule "if the two arms agree on every case of every cell the comparison is NOT
   ESTABLISHED" is not triggered — 15 cells differ.

9. **The independent authorship is real, as far as the record can show.** Each author's file
   records its own reading scope in detail, including what it did **not** open (`A1.json`,
   `A2.json`, `A4.json`), including a defect it found in the implementer's own checker
   (`A3.json`: *"the brief for A3 named AMBIGUITY as the fourth condition, but the checker fixes
   the A3 row to MISSING_PREREQUISITE and rejects any other set with 'the conditions are …'"*), and
   including a limitation it could not remove (`A6.json`: *"LIMITATION I could not remove: the
   whole-domain rule of CRJ-01 section 12 … still cannot be verified from inside this
   assignment"*). Those are not the notes of an implementer transcribing cases. The propositions
   are verbatim corpus sentences: `python3 scripts/necessity-case-check.py
   docs-v2/engineering/evidence/crj01-necessity/case-authors/A*.json` → **6/6 files green**, and
   the suite re-verifies the same property against the corpus bytes
   (`EpistemicNecessityServedTest.java:204-221`). The slices are disjoint: no necessary
   proposition crosses `PROPOSAL`/`VALIDATION`/`EVALUATION` (re-derived from the assembled set).

10. **The gate is a real gate over the rows.** `python3 scripts/necessity-gate.py` → *checks: 737,
    failures: 0*. It re-derives every confusion-matrix cell from the rows' own decision and label
    (`:111-117`), checks the case set's six author digests against the files' bytes (`:55-62`),
    checks that every evaluation case of a domain is in every arm of every cell (`:133-144`), and
    checks the GREEN XML (`:176-182`). Its weaknesses are MINOR 10 and NOTE 18.

11. **The serving suite is green as retained, and it is green now.**
    `flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun --tests
    "io.matrix.knowledge.EpistemicNecessityServedTest"` → **BUILD SUCCESSFUL**, 6 tests, 0
    failures (the retained XML says the same: `tests="6" failures="0" errors="0"`).

12. **The measurement is reproducible inside one process.** The suite computes the grid twice from
    scratch and compares every classification (`EpistemicNecessityServedTest.java:328-345`), and the
    retained summary is byte-identical across two independent runs (§5.4).

13. **The milestone's §2 is right about the authorship's shape.** Six assignments, four questions
    each, all delivered, the conditions and slice mixes as the checker fixes them, the follow-up
    round's reason (`ProcedureValidator.MINIMUM_VALIDATION_CASES = 5` against two per domain), and
    the `A3`/`AMBIGUITY` discrepancy resolved in the gate's favour — all re-derivable from the
    checker (`scripts/necessity-case-check.py:47-60`) and the assembled set. (The *count* in the
    case set's own provenance is wrong — MAJOR 5; the collision bookkeeping is wrong — MAJOR 4.)

---

## 4. The next science decision

**Recommendation: do not open the rule question yet — repair the measurement's notion of "carries"
first, and re-issue the milestone's headline numbers.** The milestone's §9 asks the right next
question (does the ablation over-approximate the requirements?), and its falsifier is a good one
(*"build a corpus in which a task's answer is **unchanged** when a second claim is removed, and
check whether the ablation still keeps that claim as a requirement"*). But that question can only be
asked on a measurement whose labels mean what they say, and today they do not: eight of the 144
rows assert that a scope carries evidence the same row records as uncitable (MAJOR 3), the
reachability field the milestone's own §5.1 leans on measures the scope rather than the retriever
(MAJOR 9), and the journey the milestone presents as the demonstration is built on a case whose
gold the workspace's own reading never reaches (MAJOR 8). The rule question is worth asking; it is
worth asking second.

**The falsifier (one run, cheap, and it decides the question).** Make the oracle's "carries" the
same notion the served path uses — a scope carries a proposition iff the **current view** holds a
source that states it — and re-run the grid. Then:

- if the re-scored per-rule counts are **unchanged** from `TP 30 / FP 18 / TN 24 / FN 0` and
  `TP 24 / FP 9 / TN 33 / FN 6`, then the divergence is an artefact of my re-scoring rather than of
  the labels, the milestone's §1 and §4.1 stand as published, and the §9 falsifier may be opened;
- if they move (my re-scoring gives `TP 26 / FP 22 / TN 24 / FN 0` and `TP 22 / FP 11 / TN 35 /
  FN 4`, i.e. FPR `22/46 = 0.4783` against `11/46 = 0.2391`), then the milestone's headline
  numbers are wrong as published and the comparison must be re-read on the re-issued grid before
  any question about the rule is worth asking.

Either way the **qualitative trade survives** — the proposal has the lower false-admission rate and
the higher false-rejection rate on this corpus — so the decision the milestone refuses to take
(no winner, no threshold, no change to the served default) is the right one to refuse, and it
should stay refused until the labels are repaired.

---

## 5. How this review was conducted

### 5.1 Commands run

```bash
python3 scripts/necessity-case-check.py docs-v2/engineering/evidence/crj01-necessity/case-authors/A*.json
#   → 6/6 files green

python3 scripts/necessity-gate.py
#   → checks: 737 / failures: 0

export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.knowledge.EpistemicNecessityServedTest"
#   → BUILD SUCCESSFUL (6 tests, 0 failures)
```

`scripts/necessity-cases.py` was **not** run: the assembled case set was read as it stands, and
re-assembling it would have rewritten a file the review does not need to change.

### 5.2 The re-derivation of the numbers (behind §3.1 and MAJOR 3)

```python
import json, collections, pathlib
rows = json.loads(pathlib.Path(
    "docs-v2/engineering/evidence/crj01-necessity/CRJ-01-NECESSITY-GRID-ROWS.json").read_text())
def counts(rs):
    c = collections.Counter()
    for r in rs:
        adm, orc = r["admitted"], r["oracleAdequate"]
        c["TP" if adm and orc else "FP" if adm else "FN" if orc else "TN"] += 1
    return c
for crit in ("ANCHOR", "NECESSARY_PREREQUISITE"):
    rs = [r for r in rows if r["criterion"] == crit]
    print(crit, counts(rs))                                     # as retained
    print(crit, counts([dict(r, oracleAdequate=False)
                         if (r["oracleAdequate"] and not r["evidenceComplete"]) else r
                         for r in rs]))                          # re-scored
print(len({r["inputFingerprint"] for r in rows}))               # → 6
print(sum(1 for r in rows if r["oracleAdequate"] and not r["evidenceComplete"]))   # → 8
```

### 5.3 The case-set re-derivation (behind MAJOR 4 and MAJOR 5)

```python
import json, collections, pathlib
cs = json.loads(pathlib.Path("docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json").read_text())
owner = collections.defaultdict(list)
for c in cs["cases"]:
    for e in c["necessaryEvidence"]:
        owner[(c["domain"], e["corpusFile"], e["proposition"])].append((c["id"], c["slice"]))
for k, v in owner.items():
    if len(v) > 1:
        print(k[1], "|", k[2][:70], "->", v)
print(cs["author"]["role"])
```

### 5.4 The one side effect of this review — declared in full

The authorised Gradle command **rewrites the retained evidence**, because the suite writes it
(`EpistemicNecessityServedTest.theEvidenceIsWritten`, `:851-881`). Digests before and after the
run:

| file | before | after |
|---|---|---|
| `CRJ-01-NECESSITY-GRID-ROWS.json` | `48e2f34ad2e7a285beee922c8a5f805fd3745b8aa559c4172db52e5ad2709e10` | `393e86c3a48e8a86d42f193a80c5c486fc13b737e0432f8aeead27e3baf4d3cb` |
| `CRJ-01-NECESSITY-GRID-SUMMARY.json` | `2ca004ec256417b292788f6272534d0ea3f2d727b64bebe51180125592ba8e0a` | **unchanged** |
| `CHECKSUMS.sha256` | `b5198f8e0fef4b48d95979beec0ff630a5c70ebf9e49171359a210039d749ec1` | `ad5a48e3b251eae38e75461234e9226019c3ad9beb650d48233dcda9489efa09` |
| both XMLs, `README.md`, `MANIFESTS.md` | — | **unchanged** |

The rows file differs **only** in the three wall-clock timing fields per row
(`selectionNanos`, `readNanos`, `fallbackNanos`): the file size is unchanged (174 131 bytes), the
summary is byte-identical, and every count re-derived from the new rows is the same as before
(§3.1). This is itself the evidence for MINOR 13 — the retained rows are not byte-reproducible.
**The implementer should refresh the manifest and re-run the gate after resolving the findings**,
and the manifest will then also have to cover this `REVIEW.md`.

---

## 6. Summary of the findings

| # | severity | title |
|---|---|---|
| 1 | MAJOR | the RED reproduction fails with the derivation on as well as off; §7 calls it "meaningful" |
| 2 | MAJOR | the "identical inputs" gate is tautological (one fingerprint per grid) |
| 3 | MAJOR | under `SUPERSEDED` the oracle's "carries" diverges from the served path's; 8 rows contradict themselves; the headline numbers move |
| 4 | MAJOR | the follow-up round's uniqueness rule is violated; §2's collision count is wrong; the checker has a no-op there |
| 5 | MAJOR | the case set's own provenance records four authors where there are six |
| 6 | MAJOR | an offer can be counted as fulfilled for evidence the workspace does not hold |
| 7 | MAJOR | the "exact revised procedure" test never revises anything |
| 8 | MAJOR | the journey's failure case has an unreachable gold; §5 and §5.1 say the opposite |
| 9 | MAJOR | `goldReachable` does not mean what it says; the reachability report is not re-derivable from it |
| 10 | MINOR | the gate's rate check is a tautology; the denominators are not re-derived |
| 11 | MINOR | `recordRevalidated` always writes `1.2.0` and can collide with the store's immutability |
| 12 | MINOR | `lastAcquisitionRequest()` can return a request that is not the last refusal's |
| 13 | MINOR | the retained evidence is written by the suite, is not byte-reproducible, and its manifest is verified by nothing |
| 14 | MINOR | "applicability" names two different decisions |
| 15 | MINOR | the evaluation slice covers 5 of the 9 conditions and §4.1 does not say so |
| 16 | MINOR | "18 scopes" and "9 and refused 6" are row counts, not scope counts |
| 17 | MINOR | §4's "the oracle is read after each arm has served" is false about the instrument's order |
| 18 | NOTE | the RED XML is never gated |
| 19 | NOTE | the authors' files are revisable, so the digests cannot witness a post-hoc edit |
| 20 | NOTE | the derivation switch is a mutable static in production code |
| 21 | NOTE | the milestone document was written during the review |

**Counts: 0 BLOCKER, 9 MAJOR, 8 MINOR, 4 NOTE.**
