# CRJ-01 / H2 — the served transfer measurement

**Status:** `CANONICAL` · **Measurement date:** 2026-11-27 · **Baseline:** `7d8c695fa75385ba09e70267e11b7f7301e415c2`

**Classification:** **RESEARCH_INFORMATION_GAIN** · **Decision:** **CONTINUE**

**One sentence.** On the path the product really serves, a learned, versioned procedure that was
promoted on held-out cases and survived a restart **was selected by the kernel for a question it
was never shown**, answered it with evidence grounded in the source's own bytes, and the exact
replay **could not answer a single one of the twelve novel tasks** — but the procedure was **not
better than the stateless reference** (10 of 12 against 12 of 12), the sealed evaluation was
**not executed**, and the milestone therefore reports information, not a product gain.

## What was measured, and what was not

| | |
|---|---|
| **the claim under test** | the contract's REUSE row: "different related task retrieves accepted knowledge/procedure and changes the choice, prediction or result", against a stateless reference (arm A) and an exact replay (arm B) |
| **the served path** | `ArtifactReportResource` → `ReportJourneyPlanner` → the kernel's selector → the method → the bounded writer → an independent reader → the trace. No method was forced in the `c/served` variant: the kernel chose on its own evidence |
| **the corpus** | **new and synthetic**: four documents on one technical subject, owned by this milestone, imported through the **production** import path |
| **the gold** | the sentences the production extraction actually derived, byte-identical, scored by the repository's own `SourceSpanOracle` — a second implementation that re-reads the corpus bytes and never calls the retriever |
| **the split** | 14 cases in four subsets: `OBSERVED` (2, the learning episode), `VALIDATION` (5, the promotion's held-out cases), `NOVEL` (5, the transfer cases), `OUT_OF_SUPPORT` (2, the control) |
| **the arms** | A (reference), B (replay, measured twice: on a novel task and on an exact repeat), C (the promoted procedure, forced and served) — **the same cases, the same denominators** |
| **NOT measured** | a sealed evaluation; a deployment; a second corpus; any claim of generalisation beyond this corpus |

## The numbers

| variant | cases | grounded | inapplicable | effect verified | wall total (ns) | wall median (ns) | CPU total (ns) |
|---|---|---|---|---|---|---|---|
| **a/measured** — the stateless reference | 12 | **12** | 0 | 12 | 112 573 076 | 9 098 253 | 70 369 592 |
| **b/novel** — the replay on a NOVEL task | 12 | **0** | 12 | 0 | 14 035 926 | 960 850 | 14 046 994 |
| **b/repeat** — the replay on an EXACT REPEAT | 12 | **12** | 0 | 12 | 41 383 006 | 2 955 313 | 27 216 044 |
| **c/measured** — the procedure, forced | 12 | **10** | 0 | 12 | 113 286 833 | 9 029 328 | 67 105 842 |
| **c/served** — the procedure, the kernel chooses | 12 | **10** | 0 | 12 | 108 183 164 | 8 920 764 | 63 922 518 |

**These are the numbers of the RETAINED run** — the frozen copy of the evidence artefact in the
evidence directory (`sha256 1f532576336325317536bd22db532ec84e9005161a5b4db7156aba1ff9633c57`). The test rewrites its working copy on every
run, so the timings move, and **the difference between C and A is smaller than the
run-to-run noise**: across this milestone's runs the **median** difference ranged from
**−0.9 % to +3.3 %** and the **total** difference from **−0.4 % to +7.7 %** — **the sign is
not stable**. **No latency number is asserted anywhere, and no cost advantage is
claimed.** The per-outcome breakdown is in the artefact
(`summary.<variant>.byOracleOutcome`), so the headline count is not read as one metric.

**Read per metric, and read the negatives.**

- **Quality (grounding).** C is **10 of 12**, A is **12 of 12**. C is **not better**, and the
  difference is exactly the two `OUT_OF_SUPPORT` controls: the procedure's support set does not
  carry their evidence, so the learned competence does not answer them (the oracle records
  `UNGROUNDED` for both). **Δ(C−A) on grounding is negative**, and no aggregate "win" is
  invented for the milestone after seeing the rows.
- **Arm B is a cache, and the measurement says so.** The replay grounds **0 of 12** novel tasks
  and **12 of 12** exact repeats. The contract's trap — "if arm C wins only exact repeats, do
  not call it learning transfer" — is therefore **measured, not assumed**: the replay's advantage
  lives entirely in the repeat subset, and the two subsets are reported apart.
- **Cost.** **No cost advantage is established.** In the retained run C is at parity with A
  on the median (**+0.8 %** in C's favour) and **−0.6 %** on the total (in A's favour); across the milestone's runs the difference ranged from
  **−0.9 % to +3.3 %** (median) and **−0.4 % to +7.7 %** (total), so **the sign is not
  stable and the difference is inside the noise**. Two things cause that, and both are
  properties of the measurement rather than accidents: the corpus is **small** (59
  claims, so the saving a narrower reading can win is small in absolute terms), and the
  correction makes the procedure's path pay **one extra tokenisation pass** over the
  workspace's claims per reading (see the correction's cost below). **The latency numbers
  are machine-sensitive, are recorded, and are NOT asserted**; a faster answer is not
  evidence of learning. The replay remains the cheapest path (**14.0 ms** in total) and it is also the one
  that answers nothing new.
- **Disposition (the epistemic one).** The two negative premise cases are scored by their
  **disposition**, not by grounding: `H2-A11` (REFUTED) and `H2-A12` (UNSUPPORTED) are both
  `DISPOSITION_MATCHED` in every arm that ran them.
- **The null.** The null of "a learned competence beats the stateless reference on correctness"
  is **not rejected**. It is **consistent with the measurement**, and with 12 paired cases and
  2 discordant pairs in C's disfavour, the honest verdict is that **this corpus and this split
  cannot support a superiority claim in either direction**.

## What the milestone proved, and what it did not

**PROVED, on the served path, with retained artefacts.**

1. **A learned, versioned competence survives a restart and is used by a process that did not
   learn it.** The durable store was closed and reopened; the procedure, its version, its
   validation count, its agreement, the episodes it was learned from and the cost the promotion
   **measured** all survived, and the kernel selected it afterwards.
2. **The kernel SELECTS the procedure for a question it was never shown.** The route's own
   outcome names the selected method, the test asserts it, and it is retained in the **rows**:
   every `c/served` row carries the method id `procedure.report.procedure.…` and the route's own
   reason — *"selected procedure.…@1.0.0 with score 1.0 over 2 admitted candidate(s)"* — which is
   where the selection is witnessed. (The JUnit XMLs carry the test names and the pass/fail
   status, **not** the plan detail; the rows are the record of the selection.)
3. **The answer is grounded in the source's own bytes**, scored by an oracle that re-reads the
   corpus and never calls the retriever.
4. **The effect is verified by a reader that did not write it**, and the artifact's digest is
   the response's digest.
5. **The exact replay is inapplicable on a novel task** — it is refused (422), and the contract's
   trap is measured.
6. **A changed payload under a reused idempotency key is NOT a stale success** — 409
   `REFUSED_IDENTITY`, and the committed artifact is untouched.
7. **A superseded source revision is not answered from** — no answer cites a claim of the
   superseded source, and the procedure's support no longer holds, so the reference answers.
8. **A CANDIDATE is never cited** — every cited claim carries a citable status.
9. **The provenance names the source the claim came from**, on every cited claim.

**NOT PROVED — and stated here so that nobody has to infer it.**

- **No superiority.** C did not beat A. The milestone reports a negative on the primary
  correctness metric, and the cost difference it does show is **inside the run-to-run noise and
  its sign is not stable** — so **no cost advantage is claimed either**.
- **No sealed evaluation.** The isolation a sealed split needs — an implementer that cannot read
  the gold and an evaluator that cannot read the solver — has **not been established from the
  executor that ran this milestone**. The measurement is a DEV run on the solver's own split,
  and the evidence artefact says so in its own `sealed` field.
- **No generalisation.** One corpus, four documents, one synthetic subject, twelve measured
  cases per arm. Nothing here speaks about a second corpus.
- **No deployment.** The measurement runs the route in process, on synthetic workspaces.
- **No claim about the latency.** The numbers are machine state.
- **The source-restricted public reading was deliberately NOT changed.** It is a candidate for the
  same correction, and it is named as an open question, not silently rewritten.
- **The corpus is the implementer's own, and so are the gold sentences.** The four documents were
  written by the implementer, so the gold propositions are the implementer's own sentences **by
  construction**; the independent case author chose the **questions** against the production
  extraction of that corpus — it did **not** choose the **evidence**. The questions are close
  paraphrases of the sentences they target, so for a lexical retriever the DEV split is close to
  in-distribution. **The grounding number says little about paraphrase robustness**, and this
  milestone measures none.
- **The kernel's selection does not rest on anything about the novel question.** The procedure
  was selected on its **declared** accuracy (1.0 over 5 declared cases) and its **measured**
  cost; the novelty of the question plays no part in the score.
- **The "grounded" headline is two metrics.** The oracle returns a positive verdict for a
  positive case when the **gold proposition is cited**, and for a negative-premise case when the
  **expected disposition and provenance match**. The two are different properties, and the
  artefact's summary now carries the **per-outcome breakdown** so the headline is not read as one
  metric.
- **The correction's own cost is not paid by the reference, and it is per READING, not per
  workspace.** The retriever re-tokenises the statistics view's claims on every reading when the
  two views differ — an extra **O(N)** pass over the workspace's claims. That is an
  **architectural** cost, not a measurement artefact: it is paid on **every** restricted reading
  the procedure serves, it is **not** a cost of the reference path, and it is **not** a cost the
  borrowed 78 % curve carries. The procedure's measured difference **already includes** it.
  Whether that pass can be made **once per workspace** instead of once per reading is the natural
  next engineering question, and it is **not** answered here.
- **A residual comparability asymmetry is NOT closed by the correction.** The concept bridge adds
  a concept's citable claims when the concept's *representation* covers the question, bypassing
  the claim's own coverage floor — so in a workspace where a concept has several claims, a
  restricted reading **can** cite a claim the reference did not (the reference cited a different
  claim of that concept which the restriction removed, so the bridge fires only in the restricted
  reading). In this corpus every concept has exactly one claim, so the bridge is inert and the
  restricted reading was measured to be exactly the reference's **∩ support** for all 14 cases.
  The asymmetry is **pre-existing** and is not closed here.
- **"The support set decides what may be cited" is only approximately true under a source
  revision.** `SourceRevisionView.restrict` — the path taken when the workspace holds source
  revisions — also keeps claims that a *disallowed* source introduced when an allowed source
  independently supports them, and keeps representations differently from
  `KnowledgeWorkbench.restrictedTo`. The H2 workspace holds no revisions during the main
  measurement, so the two were not exercised differently.
- **The historical reading is untested on this path.** The route offers **no procedures** for a
  historical request, and a historical reading would take its statistics from the historical
  view — which is correct, and is **not** measured here.
- **The reproduction of the defect is a procedure, not a claim.** The two RED XMLs are the record
  of the pre-correction behaviour; the **procedure** that produced them is written down in this
  directory's README, so that a reader can tell the statistics change from an unrelated
  breakage.
- **The original baseline runner's bytes are missing** from the milestone's evidence: the legacy
  corpus-wide runners that produce `CRJ-01-EVIDENCE.json`, `CRJ-01-EVIDENCE-DOMAIN-B.json` and
  `CRJ-01-FREEZE-RECORD.json` were not re-run (they rewrite legacy artefacts), so this
  milestone's evidence is its own rows and its own XMLs, not a re-derivation of those files.

## The defect the milestone found, and the one correction

**The defect (RED).** A *restricted* reading — the reading a procedure replays, restricted to its
validated support set — recomputed its **relevance statistics over the smaller set**. The
corpus shrank, so every term's weight **fell** (`log(1 + total/documentFrequency)` over fewer
documents) — and the **common** terms fell proportionally **more** (over the H2 corpus, a term
carried by 12 of 59 claims loses 22 % of its weight, a term carried by one loses 12 %), so a
claim whose matched terms are the common ones **lost coverage faster** than the question's total
weight and fell below the floor. The restricted answer dropped claims the unrestricted answer
cited. A restricted reading was therefore a **different retrieval**, not a
restriction of the reference — and a validated procedure's replay could never be compared with
the reference it was validated against.

The RED is retained: `docs-v2/engineering/evidence/crj01-h2-served-transfer/CRJ-01-REPRODUCTION-restricted-reading-statistics.xml`
— the same test with the pre-correction behaviour; **all 7 tests fail**, and the promotion is
refused with *"the candidate disagreed with the reference on the held-out case 'H2-A05', so it
was not promoted"*. A **second, sharper RED** is retained beside it —
`…-restricted-reading-statistics-pinned.xml` — the correction's own regression with the
correction reverted: **1 test, 1 failure**, and the failure names the defect in numbers (the
unrestricted reading cited **4** claims, the restricted one **2**). **The procedure that produced
both** is written down in the evidence directory's README, so that a reader can tell the
statistics change from an unrelated breakage.

**The correction (one, minimal, reusable).** The support set decides **WHAT MAY BE CITED**; the
workspace's statistics decide **HOW RELEVANT** a claim is. The two are separate questions, and
the retriever now answers them separately: `KnowledgeRetriever` gained a constructor that takes
the statistics from another view, `KnowledgeWorkbench.askInView` gained an overload that says
which view the statistics come from, and the two procedure paths pass the view the reference was
read from. **Every existing caller is unchanged**: the overload without a statistics view keeps
the behaviour it always had, and the correction is applied only to the two procedure paths this
milestone measured.

**The scope of the fix is deliberate.** The **source-restricted public reading**
(`KnowledgeWorkbench.ask(workspaceId, question, level, sourceIds, historicalEventId)`) keeps its
previous scoring — its statistics are the restricted view's own. That path has **its own retained
evidence** (`evidence/crj01-source-revision/`, seven independently evaluated synthetic groups),
and changing its scoring without re-running that independent evaluation would leave retained
evidence describing behaviour that no longer exists. **It is worth measuring** — with one source
in the restriction every term has `documentFrequency = 1`, so the IDF cannot discriminate — but
it is a **separate decision with its own measurements** (`R-EPIS-26`).

**The failure class.** Not learning, not retrieval, not method selection, not provenance, not
applicability, not action validity: it was **the validity of the comparison the promotion rests
on** — a restricted reading that is not comparable with the reference cannot validate a
procedure, and a procedure that cannot be validated cannot be promoted. The milestone's first
RED was exactly that refusal.

## The independent negative controls

| control | what it establishes | where |
|---|---|---|
| **replay vs novel** | the exact replay answers **0 of 12** novel tasks and **12 of 12** exact repeats: a repeat is not a transfer | `b/novel`, `b/repeat` rows |
| **changed payload, reused key** | HTTP **409** `REFUSED_IDENTITY`; the committed artifact is untouched | `CRJ01H2ServedTransferTest` |
| **superseded revision** | no answer cites the superseded source; the procedure's support no longer holds and the reference answers | `CRJ01H2ServedTransferTest` |
| **CANDIDATE never cited** | every cited claim carries a citable status | `CRJ01H2ServedTransferTest` |
| **abstention / negative premise** | the negative cases are scored by their **disposition** (`REFUTED`, `UNSUPPORTED`), not by grounding | `H2-A11`, `H2-A12` rows |
| **restart and durability** | the procedure, its version, its validation count, its agreement, its episodes and its measured cost survive a store close/reopen | `CRJ01H2ServedTransferTest` |
| **out-of-support control** | the learned competence does **not** answer outside its support: 0 grounded — and this is exactly where C's 10/12 comes from | `H2-A13`, `H2-A14` rows |
| **the corpus bytes** | every gold is verbatim in its document, and every document's digest is frozen in the corpus report, the split and the validator | `Crj01H2DevSplitValidatorTest` |

## The exact evidence

| | |
|---|---|
| **baseline SHA** | `7d8c695fa75385ba09e70267e11b7f7301e415c2` |
| **the corpus report** | `docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json` — sha256 `7b8c50c01667f80be076a5dcfe79f1a53da913d9d02de368467e776bc0a25ed5`; 4 documents, **59 claims, all `SUPPORTED` (citable)** |
| **the DEV split** | `docs-v2/engineering/CRJ-01-H2-DEV-SPLIT.json` — sha256 `d20be0f006f10733320bda1663ca196e3500aaff320a3d2ab53edc867e4017be` |
| **the rows** | the **retained** copy is `docs-v2/engineering/evidence/crj01-h2-served-transfer/CRJ-01-H2-EVIDENCE.json` — sha256 `1f532576336325317536bd22db532ec84e9005161a5b4db7156aba1ff9633c57`. The test's working copy is `docs-v2/engineering/CRJ-01-H2-EVIDENCE.json`, and it is **rewritten on every run** — the retained copy is the canonical one |
| **the corpus bytes** | `matrix-core/src/test/resources/crj01-h2/h2-magnetic-anomalies.md` `7d2fda67fe94b0a99433854ccfd2dbde6726070848dbd5b4abcad676d8647dc0`; `h2-spreading-rate.md` `3faae818275b56b9bea8342ca23c0ee50d130958b3b6991a861e4291cbf64c93`; `h2-deep-trenches.md` `17ebb9a0948730c831d51f10029775c6dbcba22e319113cf9dc55cbf3c3de1e2`; `h2-hydrothermal-vents.md` `16277f5766d57ad4372df1e8a566c14a10f2e49a16c9a4c29be8f338526a1d50` |
| **the retained evidence** | `docs-v2/engineering/evidence/crj01-h2-served-transfer/` — 15 GREEN JUnit XMLs + 2 deliberate RED XMLs + the retained rows + `README.md` + `MANIFESTS.md` + `CHECKSUMS.sha256` (**20 of 20 OK**) |
| **the test totals** | **43 tests, 0 failures, 0 errors, 0 skipped** across the 15 GREEN suites |
| **the command** | `JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --tests "io.matrix.knowledge.CRJ01H2ServedTransferTest" --tests "io.matrix.knowledge.RestrictedReadingStatisticsTest" --tests "io.matrix.crj.corpus.Crj01H2CorpusReportBuilder" --tests "io.matrix.crj.corpus.Crj01H2DevSplitValidatorTest" --tests "io.matrix.knowledge.SourceRevisionSelectionTest" --tests "io.matrix.knowledge.ClosedLoopReportJourneyTest" --tests "io.matrix.knowledge.ClosedLoopReportJourneyMeasurementTest" --tests "io.matrix.cognitive.report.*" --tests "io.matrix.cli.LearnReportProcedureCommandTest" --tests "io.matrix.knowledge.EpistemicDurabilityTest" --tests "io.matrix.knowledge.KnowledgeProvenanceTest"` |

## The roles, and the sealed evaluation that was NOT run

- **Implementer:** the DeepSeek Harness headless session the local supervisor dispatched
  (`minimax-coding-plan/MiniMax-M3.1-Flash`, the subscription-backed route).
- **DEV case author:** a **separate** DSH agent that authored the split from the corpus report
  only, without reading the implementer's test. Its token usage is **NOT_INSTRUMENTED**.
- **Independent code and evidence review:** a **separate** DSH agent (`h2-reviewer`), reading the
  diff and the evidence and reporting its verdict; its usage is **NOT_INSTRUMENTED**. **Its first
  verdict was `DEFECTIVE`** — "the milestone's *substantive* claim survives my scrutiny, but the
  change set as it stands is not publishable" — and it found **2 blockers, 6 major and 5 minor
  findings**. **Every one of them was fixed**, and the fixes are in the tree this document
  describes: the manifest listed itself and could not verify (now 20 of 20 OK); the retained
  GREEN evidence predated the last production edit (the whole named suite was re-run and
  re-retained); **the stated mechanism was backwards** — the corpus shrinking makes every term's
  weight **fall**, not grow, and it is the **common** terms that fall more, so the claims that
  drop are the ones whose matched terms are common (the corrected arithmetic is now in the
  retriever's javadoc and in this document); the correction's own cost was undisclosed and the
  78 % cost curve belonged to a **different code path** (both are now stated, and the curve is
  no longer cited as the procedure's expectation); a count was asserted against `>= 0` and could
  not fail (now asserted against the case count); the correction's regression test had a
  tautological half (now it **discriminates**, and it was proved to fail when the correction is
  reverted); the three legacy artefacts were characterised as identical when the freeze record's
  `branchSha` had in fact moved (the difference is now named, and the files are excluded from
  the deliverable set); the claim "the plan detail is retained in the XML" was false (the rows
  are the record of the selection); the evidence index did not list the new directory (it does);
  and the summary's single "grounded" key mixed two metrics (the per-outcome breakdown is now in
  the artefact). **The reviewer's own numbers agree with this document's, exactly.**
- **A second pass over the fixes** found **five mechanical defects** in the published
  documents — not in the code, the measurement or the reasoning: a bulk digest replacement had
  put the **rows'** digest into the corpus report's and the split's rows of the digest table
  (**two of the three entries named the wrong artefact** — the milestone's own defect class, in
  the record that exists to be checked); **two stale cost figures** still claimed an advantage
  the measurement had withdrawn, one of them in the **canonical governance registers**
  (`ROADMAP.md`'s COG-04 and `PROJECT-STATE.md`); and two miscounted checksum totals. **All five
  are fixed**, every digest the document cites was re-derived from its artefact and **matches**,
  and the manifest verifies **20 of 20**. The second pass's verdict is
  **`PUBLISHABLE WITH DISCLOSED LIMITS`**.
- **Sealed evaluation: NOT EXECUTED.** Isolation from this executor has not been established,
  and the coordinator's instruction records that explicitly. **No sealed case or gold was read,
  written or transmitted by this milestone** — the sealed split does not exist in this
  repository, and the protocol's §5.6 requirements were not met.

**These are AI-assisted internal reviews under `SINGLE_OWNER_AI_ASSISTED` (OD-083). No claim of
independent HUMAN review is made anywhere in this document.** The owner is accountable.

## The disposition, and the next experiment

**RESEARCH_INFORMATION_GAIN · CONTINUE.**

The milestone produced information it did not have: **a served, durable, kernel-selected,
grounded learning-transfer path exists and is measurable**, **the replay is not a transfer**, and
**the learned competence is at best equal to the stateless reference on a synthetic corpus** —
with the specific, actionable mechanism named (the support set's presence is the applicability
domain, so the procedure is offered for a question its support cannot answer, and the served
answer is then the restricted reading).

**The next experiment this result points at, and it is NOT more of the same:** the negative is
about **corpus shape and workload**, not about the mechanism. The procedure is cheaper where the
support set covers the workspace and the reference reads more claims than the support does. So
the next question is **whether the advantage appears on a corpus where the support set is a
small, discriminative part of a large one** — the shape this synthetic corpus cannot have (4
documents, the support is 2 of them).

**And the curve that motivates it must be measured on the procedure's OWN path, not borrowed.**
The earlier cost curve (`CRJ-01-ARM-C.md`, fraction 0.167 → 78 % saving) was measured on the
**5-argument `ask(…, sourceIds)` path**, which this milestone deliberately left unchanged. The
procedure's path is **not** that path, and after the correction it is **more expensive** than the
reference in one respect: the retriever **re-tokenises the statistics view's claims on every
reading** when the statistics view differs from the retrieval view — an extra **O(N)** pass over
the workspace's claims, against the reference's own single **O(N)** pass. So:

- the procedure's own measured difference **on this corpus** is **+0.8 % (median) / −0.6 %
  (total)** in the retained run — **the two metrics of the same run disagree on the sign** — and
  that figure **already includes** the extra tokenisation pass;
- the **78 %** curve **does not describe the procedure's path**, and citing it as the next
  experiment's expectation would be citing a curve for different code;
- the next experiment must therefore **measure the procedure path's own cost curve** over a
  corpus with a low support fraction, and must **count the extra statistics pass** in it.

That is a real cost of the correction, and it is stated here rather than absorbed: the
correction buys comparability, and it pays for it in one tokenisation pass per reading. Whether
that pass can be made once per workspace instead of once per reading is the natural next
engineering question, and it is **not** answered here.

**What must NOT happen:** re-tuning the same recogniser, re-running the same 12 cases, or
reading this milestone's negative as "the mechanism does not work". The negative is measured on
one corpus with 12 paired cases; it is an honest null, not a refutation of the hypothesis.
