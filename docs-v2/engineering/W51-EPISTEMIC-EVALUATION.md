# W51 §6 — the measured epistemic evaluation

**Wave:** W51 §6 · **Date:** 2026-11-08 · **Branch:** `develop` ·
**HEAD:** `a098f283` (the tree the wave started from) ·
**Machine:** AMD Ryzen 9 9955HX, JDK 25.0.4

**One sentence.** The W50 epistemic layer now has a machine-readable
dataset, a committed runner and a measured before/after: **the same 29
cases go from 12 passes to 18**, the six cases that changed are the six
the fixes were aimed at, and the eleven that still fail fail for the
reasons the report names.

---

## 1. What was run, and how

| item | value |
|---|---|
| **The dataset** | `matrix-core/src/main/resources/epistemic-eval/cases.json` — the 29 cases of `docs-v2/demos/local-learning-loop/EVALUATION-SET.md` (version 1.1), transcribed mechanically: id, category, scope, the call, the expectation, and the pass condition. |
| **The runner** | `scripts/w51-epistemic-eval.py` — it starts the packaged application, prepares every scope, runs every case, prints the verdict per case and the summary per category, and writes a machine-readable report. |
| **The command** | `python3 scripts/w51-epistemic-eval.py --port 9090` (from the repository root). |
| **The application** | `matrix-core/build/quarkus-app/quarkus-run.jar`, built by `./gradlew :matrix-core:quarkusBuild`. |
| **The boundary** | The JVM's HTTP and HTTPS clients are pointed at a closed port on the loopback interface (`127.0.0.1:9`), exactly as `scripts/offline-answer-proof.sh` does. **No request left the machine and no model was called.** |
| **The corpus** | The licensed, pre-cached plate-tectonics pool that ships inside the application. |
| **The report** | `docs-v2/engineering/W51-EPISTEMIC-EVALUATION.json` — the machine-readable form of the run below. |

### 1.1 The corpus arrives as part of the run, and the artifact says so

The runner does not assume the workspace is there: it makes the calls that
bring it there and records what each call produced. The artifact carries
the preparations, so a reader can see that the corpus arrived and how big
it was:

| scope | the calls | documents | claims |
|---|---|---|---|
| **S1** `plate-tectonics-eval-seed` | `POST /v1/learning/workspaces/{w}/seed` | 2 | **348** |
| **P1** `plate-tectonics-eval` | `seed` + `POST /v1/learning/workspaces/{w}/learn?question=…&documents=3` | 2 + 3 | 348 + **324** |
| **R1** `plate-tectonics-eval` | P1 + `POST …/revise?file=wikipedia-slab-pull.md` | +1 | +14 |
| **R2** `plate-tectonics-eval` | R1 + `POST …/revise?file=wikipedia-paleomagnetism.md` | +1 | +65 |
| **P1′** `plate-tectonics-eval-neg` | `seed` + `learn` | 2 + 3 | 348 + 324 |
| **P1″** `plate-tectonics-eval-neg2` | `seed` + `learn` | 2 + 3 | 348 + 324 |

The numbers are the W50 reference numbers: 348 claims from the seed
corpus, 324 from the three documents the bounded cycle acquires.

### 1.2 The order of the scopes is the order of the dataset

A scope is the **state of the workspace** a case is asked in, and the
dataset says which cases belong to which scope. The scope `R1` asks its
answer before the scope `R2` makes the second revision, which is the order
the W50 protocol used (step 11 of the protocol, between the two
revisions). A runner that ran the cases in the order they happen to be
written would prepare the second revision first and measure R-03 against a
workspace the dataset does not name — which is exactly what the first
version of this runner did, and why its first result was 11/29 rather
than 12/29.

---

## 2. BEFORE and AFTER

The **BEFORE** measurement is the same runner, the same dataset and the
same protocol, run against a **pristine worktree at HEAD** (`git worktree
add --detach /tmp/w51-baseline a098f283`) with its own packaged jar. It
reproduces the W50 measurement exactly: **12 passes, 17 failures**.

### BEFORE — the runner against HEAD (`a098f283`)

| category | pass | fail | total |
|---|---|---|---|
| answerable | 2 | 6 | 8 |
| unanswerable | 2 | 4 | 6 |
| false premise | 4 | 1 | 5 |
| multi-claim | 1 | 2 | 3 |
| knows what it does not know | 2 | 0 | 2 |
| revision | 1 | 4 | 5 |
| **TOTAL** | **12** | **17** | **29** |

### AFTER — the runner against the W51 §6 build

| category | pass | fail | total | Δ |
|---|---|---|---|---|
| answerable | 2 | 6 | 8 | — |
| unanswerable | **6** | 0 | 6 | **+4** |
| false premise | 4 | 1 | 5 | — |
| multi-claim | 1 | 2 | 3 | — |
| knows what it does not know | 2 | 0 | 2 | — |
| revision | **3** | 2 | 5 | **+2** |
| **TOTAL** | **18** | **11** | **29** | **+6** |

**What the fixes did to the store, measured on the run's own database:**
the seed workspace now holds **348 SUPPORTED and 0 CANDIDATE** claims (the
import's validation stage earned the support, one supporting source per
claim), and every one of the 2 584 claims carries a character span.

**The six cases that changed, and why:**

| case | before | after | the change that moved it |
|---|---|---|---|
| U-01 | FAIL (answered) | **PASS** (abstained, 0 claims, 0 sources) | the quantity demand — "What is the boiling point of water … in degrees Celsius?" asks for a number, and no cited claim states one |
| U-03 | FAIL (abstained but cited 3 claims and a source) | **PASS** (abstained, nothing cited) | the refusal cites nothing — `KnowledgeWorkbench.java:692` |
| U-04 | FAIL (answered) | **PASS** (abstained) | the quantity demand — "How many tonnes …?" |
| U-06 | FAIL (answered) | **PASS** (abstained) | the quantity demand — "How many research vessels …?" |
| R-01 | FAIL (superseded the wrong claim) | **PASS** (the named claim is SUPERSEDED v2, the new claim SUPPORTED v2, the reason names the denial) | the scoped denial — `ModelRevisionDecider.java:194` |
| R-02 | FAIL (4 supersessions) | **PASS** (0 supersessions) | the scoped denial + the bridge no longer selects the candidate |

**R-05 regressed and is reported as it is.** In an intermediate build the
scoped denial stopped R-02 and R-05 from superseding anything; the final
rule — the denial clause stops at the reason, and two claims that deny the
same thing agree rather than deny each other — restored R-02 and left R-05
with one supersession. R-05 therefore FAILS, and §4 says why, with the
claim that was superseded.

---

## 3. Every case, with the code that decided it

The runner reports, for each failing case, the concrete problems and the
file and line where the decision was made. A symptom is not a cause.

| case | category | scope | verdict | expected | observed | the problems, and where they are decided |
|---|---|---|---|---|---|---|
| A-01 | answerable | S1 | **PASS** | answered; ≥2 claims; `wikipedia-plate-tectonics.md`; 2 propositions | answered; 41 claims; 2 sources; 2/2 propositions | — |
| A-02 | answerable | S1 | FAIL | answered; ≥3 claims; 3 propositions | answered; 18 claims; 2 sources; 2/3 | "Convergent boundaries …" not cited — `KnowledgeRetriever.java:386-390`: the claim did not reach the 0.25 coverage floor of the question's terms, so it is not the evidence |
| A-03 | answerable | S1 | **PASS** | answered; ≥3 claims; 3 propositions | answered; 30 claims; 2 sources; 3/3 | — |
| A-04 | answerable | S1 | FAIL | answered; ≥3 claims; `usgs-this-dynamic-earth.md`; 3 propositions | answered; 9 claims; 1 source; 2/3 | "However, because plate motions are global in scale …" not cited — `KnowledgeRetriever.java:386-390` |
| A-05 | answerable | S1 | FAIL | answered; ≥4 claims; ≥2 sources; 4 propositions | answered; 16 claims; 2 sources; 2/4 | "Oceanic crust is formed at sea-floor spreading centers." and the magnetic-stripe sentence not cited — `KnowledgeRetriever.java:386-390` |
| A-06 | answerable | S1 | FAIL | answered; ≥3 claims; `usgs-this-dynamic-earth.md`; 3 propositions | answered; 7 claims; **1 source (Wikipedia)**; 0/3 | the USGS source is absent and 3 propositions are not cited — `KnowledgeRetriever.java:386-390` |
| U-01 | unanswerable | S1 | **PASS** | abstention: no answer, empty statement, nothing cited | abstained; 0 claims; 0 sources | — |
| U-02 | unanswerable | S1 | **PASS** | abstention | abstained; 0 claims; 0 sources | — |
| U-03 | unanswerable | S1 | **PASS** | abstention | abstained; 0 claims; 0 sources | — |
| U-04 | unanswerable | S1 | **PASS** | abstention | abstained; 0 claims; 0 sources | — |
| U-05 | unanswerable | S1 | **PASS** | abstention | abstained; 0 claims; 0 sources | — |
| F-01 | false premise | S1 | **PASS** | SUPPORTED; the claim and its source named; `promoted=false`; 348→348 claims | SUPPORTED; 5 evidence refs; `promoted=false` | — |
| F-02 | false premise | S1 | FAIL | CONTRADICTED | **UNRESOLVED**; 5 evidence refs; `promoted=false` | the verdict is UNRESOLVED — `PremiseAssessment.java:592` (`agreeOn`): five differently-worded denials do not settle each other. **The honest verdict is kept.** See §4.3. |
| F-03 | false premise | S1 | **PASS** | UNSUPPORTED; no evidence; `promoted=false` | UNSUPPORTED; 0 evidence refs | — |
| F-04 | false premise | S1 | **PASS** | UNRESOLVED; the denials named; `promoted=false` | UNRESOLVED; 5 CONTRADICTS refs; `promoted=false` | — |
| F-05 | false premise | P1 | **PASS** | CONTRADICTED; the claim and its source named; `promoted=false`; 672→672 | CONTRADICTED; 2 evidence refs; `promoted=false` | — |
| M-01 | multi-claim | S1 | FAIL | ≥3 claims; 3 propositions | answered; 18 claims; 2 sources; 1/3 | "Convergent boundaries …" and "The San Andreas Fault …" not cited — `KnowledgeRetriever.java:386-390` |
| M-02 | multi-claim | S1 | **PASS** | ≥3 claims; 3 propositions | answered; 86 claims; 2 sources; 3/3 | — |
| M-03 | multi-claim | P1 | FAIL | ≥6 claims; ≥3 sources; 5 propositions | answered; 25 claims; 4 sources; 0/5 | all five propositions not cited — `KnowledgeRetriever.java:386-390` |
| K-01 | knows what it does not know | S1 | **PASS** | answered; the boundary-types claim cited; the gap names "tsunami" | answered; 6 claims; the gap names "tsunami" | — |
| K-02 | knows what it does not know | S1 | **PASS** | answered; one of the two named claims cited; the gap names "tsunami" | answered; 6 claims; the gap names "tsunamis" | — |
| P-01 | answerable | P1 | FAIL | answered; ≥3 claims; `wikipedia-mid-ocean-ridge.md`; 3 propositions | answered; 63 claims; 5 sources; 0/3 | the three propositions not cited — `KnowledgeRetriever.java:386-390` |
| P-02 | answerable | P1 | FAIL | answered; ≥3 claims; `wikipedia-subduction.md`; 3 propositions | answered; 104 claims; 5 sources; 0/3 | the three propositions not cited — `KnowledgeRetriever.java:386-390` |
| U-06 | unanswerable | P1 | **PASS** | abstention | abstained; 0 claims; 0 sources | — |
| R-01 | revision | P1→R1 | **PASS** | ≥1 supersession; the named claim SUPERSEDED v2; the new claim SUPPORTED v2; the reason names the denial | 14 claims added, 14 decisions, **1 supersession**; the named claim SUPERSEDED v2; the new claim SUPPORTED v2; relatedness 0.42 | — |
| R-02 | revision | R1→R2 | **PASS** | **no** supersession; no claim changes status | 65 claims added, 65 decisions, **0 supersessions**; no claim changed | — |
| R-03 | revision | R1 | **PASS** | answered; ≥5 claims; ≥5 sources; both propositions | answered; 126 claims; 7 sources; 2/2 | — |
| R-04 | revision | P1′ | FAIL | **no** supersession | 92 claims added, 92 decisions, **2 supersessions**; 3 claims changed | the supersessions and the changed claims — `ModelRevisionDecider.java:52` (`decide`) and `KnowledgeRefiner.java:100` (`refine`). See §4.3. |
| R-05 | revision | P1″ | FAIL | **no** supersession | 45 claims added, 45 decisions, **1 supersession**; 1 claim changed (`clm-7424f0`) | the supersession — `ModelRevisionDecider.java:52`. See §4.3. |

---

## 4. The eight defects: where each is fixed, and what proves it

Each row names the place the fix lives, the test that proves the fix, and
what the 29-case run did with the cases the defect touched. **A defect is
not counted as fixed because the code changed; it is counted as fixed when
a test proves it and the run shows the effect.**

### 4.1 D1 — candidate claims enter answers without status filtering

| | |
|---|---|
| **Was** | `KnowledgeRetriever.java:340` iterated `snapshot.claims()` with no status condition; the W50 run measured 569 of 571 cited claims as `CANDIDATE` and one as `SUPERSEDED`. |
| **Now** | `EpistemicPolicy.java:67` — `CITABLE_STATUSES = {SUPPORTED, VERIFIED, CONTESTED}`, one named set, one predicate (`EpistemicPolicy.java:124`). The retriever filters on it at `KnowledgeRetriever.java:386-390`, the concept bridge at `:452`, and the refusal at `:446` says which gate refused. A claim the workspace has withdrawn is reported in the answer's limitations (`:644`) and is **not** evidence. |
| **Proven by** | `EpistemicPolicyTest.onlyAnEstablishedClaimMayBeCited` (the named set and the predicate); `KnowledgeRetrieverTest.aRefutedClaimIsMarkedAsSuch` (the withdrawn claim is not in the evidence and is named in the limitations). |
| **The cases** | R-01's answer (R-03) no longer cites a withdrawn claim; the six `answerable` cases that fail now fail for the *relevance* reason (the retriever cites what it ranks), not because it cited an unestablished claim. |

### 4.2 D2 — irrelevant or insufficiently supported claims

| | |
|---|---|
| **Was** | the retriever ranked on IDF-weighted lexical overlap alone; `Claim.confidence` was averaged into the support score and never gated eligibility. |
| **Now** | relevance and epistemic support are separate: the coverage floor decides *whether* a claim is retrieved (`KnowledgeRetriever.java:386`), the status filter decides *whether* it may be cited (`:388`), and the support score is computed over the **established** claims only (`KnowledgeRetriever.java:609-616`). The answer says how many of the claims it cites carry no authored confidence (`:640-643`), and how many claims the status filter removed (`:404`). |
| **Proven by** | `EpistemicPolicyTest.aClaimWithNoAuthoredConfidenceCarriesNoNumericSupport`; `SupportScoreTest` (the score's decomposition is pinned and now says what it counts). |
| **The cases** | unchanged by design: the six `answerable` cases still fail, because the retriever cites the claims it RANKS and the question's demand terms are not among them. That is D2's remaining half and it is reported, not hidden. |

### 4.3 D3 — the system answers where it should abstain

| | |
|---|---|
| **Was** | `QuestionDemand.java:102` — `MINIMUM_DEMAND_COVERAGE = 0.0`, and the only gate left was the retriever's 0.25 coverage floor. The W50 run measured U-01, U-04 and U-06 answered, at coverage 0.268, 0.288 and 0.319. |
| **Now** | a named boundary in two parts. **`QuestionDemand.Kind.QUANTITY`** (`:179`) — a question that asks for a number is a demand of its own kind, with the markers named at `QuestionDemand.java:328`. **`EpistemicPolicy.MINIMUM_QUANTITY_EVIDENCE = 1`** (`:113`) — a question that asks for a number is answered only when the evidence states one; the gate is `KnowledgeWorkbench.java:639-672`. And a refusal cites **nothing** (`KnowledgeWorkbench.java:692`). |
| **Why this boundary and not the coverage floor** | measured: the unanswerable "How many tonnes of basalt are produced each year by the Mid-Atlantic Ridge?" scored 0.288 against a floor of 0.25, while the answerable "How does subduction at a convergent boundary produce deep earthquakes and volcanoes?" scored 0.286 and had to be answered. No threshold on coverage separates the two families — the W50 report said so, and the measurement confirms it. The question "what is being asked for" is a different question from "how much of it is covered". |
| **Proven by** | `EpistemicPolicyTest.aQuestionThatAsksForANumberIsAQuantityDemand` (a "how many" question is a quantity demand; "How is … measured?" is not); `EpistemicPolicyTest.theEvidenceThatStatesANumberIsCounted`. |
| **The cases** | **U-01, U-03, U-04, U-06 now PASS** — 6 of 6 unanswerable, against 2 of 6 before. |

### 4.4 D4 — source provenance without precise offsets

| | |
|---|---|
| **Was** | the source record's locator was the constant `"the whole document"` for every record, and the W50 run measured 0 of 689 cited claims with a source that carried an exact location. |
| **Now** | the convention is declared and the span is measured. `ClaimSpan.java` holds it: the span is `io.matrix.contract.Provenance.Span` with `Unit.CHARACTERS`, the start inclusive and the end exclusive — **the canonical contract's own convention, not a second one**. `acquisition/TextDocumentExtractor.java:172-180` measures the characters of every segment of the extracted text; `acquisition/DocumentImporter.java:239` writes the span into the claim's context in the declared form; `acquisition/SourceRecord.java:57` names the "whole document" case explicitly, and `acquisition/DocumentImporter.java:161` says the source's extent in the locator. `acquisition/CorpusImport.java:163` reports the claim's **context** — the span — where it used to report the claim's subject. |
| **Proven by** | `EpistemicPolicyTest.aClaimCarriesTheSpanItWasExtractedFrom` (the claim's context carries `[characters 0-…]`, the span parses back to a `Provenance.Span` with `Unit.CHARACTERS`, and the source says what its extent is); `EpistemicPolicyTest.aContextThatCarriesNoSpanSaysSo` (a document that gives no exact span says "the whole document" and nothing that looks like an offset). |
| **The cases** | the cases do not measure the provenance; the test and the database do. Measured on the run's own database: **2 584 of 2 584 claims carry a character span** in their context (`usgs-this-dynamic-earth.md: "This Dynamic Earth" (line 1) [characters 22-17144]`), and every one of the 25 source records names its document **and** its extent (`usgs-this-dynamic-earth.md (the whole document)`) instead of the bare constant. The W50 measurement's "0 of 689 cited claims with an exact location" is therefore true only of the *source* row, and the *claim* row now says exactly where the sentence sits. |

### 4.5 D5 — the status counts can be empty or incorrect

| | |
|---|---|
| **Was** | `KnowledgeWorkbench.status()` counted the workspaces by reading the `workspace` table, and the learning path never wrote that table: `applyChanges` wrote concepts, sources, claims, evidence, representations and revisions and nothing else. The W50 run measured six workspaces holding 4 080 claims, an empty `workspace` table and a status route that reported `"workspaces": []` and `"counts": {}`. |
| **Now** | the source of truth is fixed: `KnowledgeStore.java:2490` registers the workspace on its first write (`registerWorkspace`, `:2532`, with OR IGNORE so the name and the instant of the first write are the ones that stay), and `KnowledgeStore.java:2664` counts the claims **by status** — a breakdown that existed nowhere in the repository — and the status route reports it at `KnowledgeWorkbench.java:2176` (`claimsByStatus`). |
| **Proven by** | `EpistemicDurabilityTest.theClaimsTheirEvidenceAndTheRevisionSurviveARestart` (after the restart the store still knows the workspace and `claimStatusCounts` holds SUPERSEDED and SUPPORTED). |
| **The cases** | the cases do not measure the status route; the test and the database do. Measured on the run's own database: the seed workspace holds **348 SUPPORTED and 0 CANDIDATE** claims, the pool workspace **750 SUPPORTED and 2 SUPERSEDED**, and the four workspaces are now in the `workspace` table, so `GET /v1/workbench/status` reports them with their counts and their breakdown by status. |

### 4.6 D6 — source records can be rewritten in place

| | |
|---|---|
| **Was** | `KnowledgeStore.upsertSource` was `INSERT OR REPLACE`, and the refinement's source identifier was derived from the **locator** — the constant `"the whole document"` — so every refinement in a workspace derived the SAME identifier. The W50 run measured the record `s-publication-66bb6e0c` rewritten from "wikipedia-slab-pull.md" to "wikipedia-paleomagnetism.md", with five claims following it to a document they were not read from. |
| **Now** | two rules. **The identity of a source is never its locator**: `Ids.refinementSource` (`Ids.java:59`) derives it from the source the refinement was read from and the proposition it carries, and `KnowledgeRefiner.java:184` uses it. **A source record is immutable**: `KnowledgeStore.upsertSource` (`:2586`) refuses to write over a record that is there, and refuses a collision whose content hash differs. |
| **Proven by** | `EpistemicPolicyTest.aSourceRecordIsNotRewrittenWhenTheSameDocumentIsImportedAgain` (the second import of the same document leaves the record — and its `addedAt` — exactly as it was). |
| **The cases** | R-02's supersession of the *first* revision's source record is gone; the revisions of R-01 and R-02 are two records. |

### 4.7 D7 — revision decisions can supersede the wrong claims

| | |
|---|---|
| **Was** | the candidate was the claim with the highest overlap with the new **document's text** — and a document about anything shares words with a document about something else; the denial branch short-circuited the strength comparison; a `CANDIDATE` claim could be superseded. The W50 run measured R-01 superseding a claim that the new evidence did not deny, and R-02, R-04 and R-05 superseding 4, 5 and 2 claims where the dataset expects none. |
| **Now** | `ModelRevisionDecider.java:159` — only a claim the workspace established may be revised (`EpistemicPolicy.isInForce`). The candidate is selected by the **propositions**, not by the document (`:75-97`): the direct relatedness decides that two propositions are about the same subject, and the **bridge** — the overlap with the new document's whole text — no longer selects anything. The denial is **scoped** (`ModelRevisionDecider.java:194`): the negated clause has to be about the candidate's subject, the clause stops at the reason (`:243`), and two claims that deny the same thing agree rather than deny each other (`:216-221`). The reason the decision gives says that the relation was read from the text and is a lexical relation, not a demonstration of meaning (`:110`). |
| **Proven by** | the run: **R-01 now PASSES** (the claim the dataset names is the one superseded, v2, and the new claim is SUPPORTED v2, and the reason names the denial) and **R-02 now PASSES** (0 supersessions). |
| **The cases that still fail** | **R-04** supersedes 2 claims and **R-05** supersedes 1. The two claims the R-04 run superseded are "Spreading rate is the rate at which an ocean basin widens due to seafloor spreading." and "The depth-age relation can be modeled by the cooling of a lithosphere plate or mantle half-space."; the first was denied by the document's own sentence about the depth-age relation, and the second by "Depth is better explained by a cooling lithosphere plate model rather than the cooling mantle half-space." — the coverage of the denied clause is 1.00, the highest possible, and the supersession looks **correct**: the document denies the alternative the workspace held. **The expectation of the dataset rests on an assumption about the corpus that the corpus does not support** ("the seafloor-spreading document adds evidence without denying anything the workspace holds"), and the expectation was **not** changed. R-05's one supersession is of `clm-7424f0` and the same kind of lexical false positive: the document's "not bordered by plates that are being pulled into subduction zones" is read as a denial of a claim about which plates are subducted. **This is the honest limit of a lexical rule and it is reported as a failure, not argued away.** |

### 4.8 D8 — lexical polarity used beyond what it can establish

| | |
|---|---|
| **Was** | a marker list decided that one proposition denies another, and the decision it produced was presented as a verdict. |
| **Now** | the marker list is a **candidate**, and it says so: `PremiseAssessment.RELATION_BASIS` (`:261`) states that the relation is read from the text of the two sides and is a lexical candidate and not a demonstration of meaning, and every reason the assessment gives carries it. The verdict stays the honest one: `CONTRADICTED` when the contradicting evidence settles the premise, `UNRESOLVED` when the workspace holds contradicting accounts that do not settle each other (`PremiseAssessment.java:592`). The revision decider's own reason says the same about the relation it read (`:110`). |
| **Proven by** | `PremiseAssessmentTest` (the verdicts and the reasons) and `EpistemicPolicyTest` (the policy's rules). |
| **The cases** | **F-02 still FAILS**, and it should: the dataset expects CONTRADICTED, the workspace returns UNRESOLVED, and the reason is that five differently-worded denials do not settle each other. **An honest UNRESOLVED is the correct verdict here, and the expectation was not changed to make the case pass.** The lead's instruction was explicit on this point, and it is honoured: the case stays a failure and is described. |

---

## 5. Where a test's expectation changed, and why that is not a weakening

Four existing tests changed their expectation. In each case the old
expectation encoded the **defect** this wave was asked to remove, and the
new expectation is **stronger**, not weaker.

| test | before | after | why the old expectation was wrong |
|---|---|---|---|
| `acquisition/DocumentImportTest.aPlainTextDocumentBecomesClaimsWithEvidenceBehindThem` | every imported claim is `CANDIDATE` — "nothing has validated them yet" | every imported claim is `SUPPORTED` — one source supports it — and its `validationMethod` records "the validation" | the import **is** the validation stage, and `PromotionPolicy.mayPromote(CANDIDATE, SUPPORTED, 1, hasEvidence=true, disputed=false)` has always been `true` — the very same test asserts it at the bottom. The gate for VERIFIED (two **independent** content hashes) was being applied to the step that needs only one supporting source, so every imported claim stayed a candidate for ever. The new expectation is *narrower* (SUPPORTED, never VERIFIED) and it is **earned by a procedure**, not assigned at extraction. |
| `KnowledgeRetrieverTest.aRefutedClaimIsMarkedAsSuch` | the withdrawn claim **appears** in the answer's claims, marked REFUTED, "so that a learner can be shown the mistake" | the withdrawn claim is **not** in the answer's claims; the answer names it in its limitations; the statement does not assert it | the answer's `claims` **are** the evidence, and the W50 run measured a withdrawn claim inside the evidence of an answer. The old test pinned the defect as a design. The invariant the test was written for — "the refuted misconception is never presented as established" — is now asserted **directly** instead of through a proxy. |
| `SupportScoreTest.theValueIsUnchanged` | the support score is `0.85` | the support score is `0.84` | the score is the mean of the confidences of the claims the answer **cites**, and the score now counts the established claims only. The refuted claim used to be in the evidence and used to carry its confidence into the mean. A withdrawn claim's confidence is not support. The retrieval did not change — the same claims are matched with the same coverages — and the number still means what `SupportScore` says it means. |
| `MultilingualTextTest.theLexiconDoesNotChangeWhatAnEnglishQuestionRetrieves` | the same pinned `0.85` | the same pinned `0.84` | the same reason, in the second place the value is pinned. The test's own claim — "the lexicon must not move it" — still holds: the value is the same with and without the lexicon. |

**No assertion was weakened, no case was excluded, and no expectation of
the 29-case dataset was changed.** The dataset's expectations are the
ones in `EVALUATION-SET.md`, transcribed verbatim; the two cases that fail
because the system's behaviour is genuinely in question (F-02, R-04, R-05)
fail.

---

## 6. The tests

| | |
|---|---|
| **The scope** | `JAVA_HOME=$HOME/jdk25 ./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --offline` |
| **BEFORE** | **628 tests, 0 failures** — measured in a pristine worktree at `a098f283` (`git worktree add --detach /tmp/w51-baseline a098f283`), the same scope, the same command. The XML is under `/tmp/w51-baseline/matrix-core/build/test-results/test/TEST-io.matrix.knowledge.*.xml` (97 files). |
| **AFTER** | **641 tests, 0 failures** — 628 plus the 13 new tests of `EpistemicPolicyTest` (11) and `EpistemicDurabilityTest` (2). The XML is under `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.*.xml`. |
| **The new tests** | `EpistemicPolicyTest` — the status sets, the withdrawn statuses, the numeric-support predicate, the quantity boundary and its markers, the evidence that states a number, the imported claim is SUPPORTED and not VERIFIED, two independent sources verify, a source record is not rewritten, the claim carries its span, a context that carries no span says so. `EpistemicDurabilityTest` — the claims, their evidence and the revision survive a restart (write → close → open → read), and the schema version does not move. |

**A note on the shared build directory.** Several agents run Gradle in the
same tree, and a concurrent run wipes `matrix-core/build/test-results/`.
Two of the runs in this wave died with
`NoSuchFileException: …/test-results/test/binary/in-progress-results-generic.bin`
and one transient failure (`SnapshotRetentionBoundTest`) was caused by it —
the test passes on its own and in the clean run. The numbers above are
from the console of the runs themselves, and the XML is the appendix.

---

## 7. What is still wrong

The eleven remaining failures are honest, and this section says what each
of them is:

1. **Six `answerable` cases and one `multi-claim` case fail because the
   retriever cites the claims it RANKS.** The question "What are the three
   types of plate boundaries and what happens at each of them?" is answered
   with 18 claims of which the convergent-boundary claim is not one; the
   coverage floor of 0.25 of the question's *terms* is not the question's
   *demand*. This is D2's remaining half and it is the next piece of work:
   the answer needs to rank the claims the question needs, not the claims
   that share its words. It is not fixed here, and no expectation was
   changed to hide it.
2. **F-02 stays UNRESOLVED** where the dataset expects CONTRADICTED. The
   workspace holds five differently-worded denials and they do not settle
   each other. The honest verdict is UNRESOLVED; the dataset's expectation
   is not changed.
3. **R-04 and R-05 supersede a claim each**, where the dataset expects
   none. For R-04 the supersession looks correct on the evidence — the
   document does deny the workspace's alternative — and for R-05 it is a
   lexical false positive. Both are reported as failures.
4. **The lexical denial rule remains a lexical rule.** It is now scoped,
   it is now labelled as a candidate, and it no longer decides anything on
   its own — but a sentence that denies something else can still be read as
   a denial of a claim that shares its vocabulary. The honest name for that
   is a heuristic with a measured error rate, and the report gives the
   coverage numbers that show where it errs (the genuine denial of R-01
   scores 0.67, the false positives score 0.22 to 0.54).

---

## 8. The artefacts

| artefact | where |
|---|---|
| the machine-readable dataset | `matrix-core/src/main/resources/epistemic-eval/cases.json` |
| the runner | `scripts/w51-epistemic-eval.py` |
| the report of THIS run | `docs-v2/engineering/W51-EPISTEMIC-EVALUATION.json` |
| the report of the BEFORE run | `/tmp/w51-baseline/docs-v2/engineering/W51-EPISTEMIC-EVALUATION.json` (the pristine worktree; the same runner, the same dataset, the HEAD build) |
| the BEFORE console output | `/tmp/w51-eval-before3.log` |
| the AFTER console output | `/tmp/w51-eval-after3.log` |
| the test XML | `matrix-core/build/test-results/test/TEST-io.matrix.knowledge.*.xml` (AFTER) and `/tmp/w51-baseline/matrix-core/build/test-results/test/TEST-io.matrix.knowledge.*.xml` (BEFORE) |
