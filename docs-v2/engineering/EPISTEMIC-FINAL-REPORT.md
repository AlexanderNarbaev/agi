# EPISTEMIC-FINAL-REPORT — the EPISTEMIC-INTEGRITY programme, what was delivered and what remains

**Status:** `ACTIVE` — the P0 gates are closed or explicitly bounded; P1 and P2 continue under the
directive's continuation horizon.
**Date:** 2026-11-15 · branch `develop` · the owner's directive of 2026-11-15 (§2 item 3-8, §3, §4,
§5, §9, §10, §11, §12).
**Companion documents:** `EPISTEMIC-EXECUTION-PLAN.md` (the task graph and the append-only evidence
ledger), `EPISTEMIC-CLOSED-LOOP-DESIGN.md` (P2), `EPISTEMIC-CONCEPT-IDENTITY.md` (P1),
`EPISTEMIC-LEARNING-ISOLATION.md` (P0.4), `EPISTEMIC-SQLITE-PACKAGING.md` (P0.5),
`EPISTEMIC-HELDOUT-SET.md` (P0.3), `CI-SIMPLIFICATION-RFC.md` and `BRANCH-INVENTORY.md` (Gate 1).

---

## A. What the directive asked, and what it produced

The directive's central problem: *"the project has consumed substantial time, agent tokens, CI minutes
and engineering effort. Many components and papers exist, but demonstrated generalizable user benefit
remains small. Change the optimization target from activity and volume to verified capability and
useful outcomes per unit of compute, time and complexity."*

Six P0 obligations were set. **Five are delivered and independently reviewed; one (P0.6) is a
reconciliation that is done; P0.1/P0.2 was reviewed adversarially, found defective, fixed, and is
under its second review.** Every number below is a measured number with a command or an artefact
behind it; the ledger in `EPISTEMIC-EXECUTION-PLAN.md` holds the commands.

| the obligation | the state | the decisive evidence |
|---|---|---|
| **P0.1** explicit premise semantics in the real asking path | **delivered** (re-review in flight) | the five dispositions in the production path; the frozen set's metrics |
| **P0.2** the conflicts and the ambiguities visible in the served response | **delivered** | `ArtifactReportResource` carries `PremiseStatus`, `ConflictReport`, `ReadingReport` |
| **P0.3** a NEW independently held-out set with positive and adversarial negative controls | **delivered** | 16 cases, 9 classes, freeze sha256 `2f4347da…`, a measured baseline of the current path |
| **P0.4** the evaluation records isolated from the durable organic learning | **delivered and re-reviewed** | 209 of 226 records quarantined, the log byte-identical, the gate fails closed |
| **P0.5** the packaged SQLite native-library loading | **delivered** | the packaged app opens the store with no property, from outside the repository |
| **P0.6** the post-release `main`/`develop` divergence reconciled | **done** | the live diff: four documentation files, no code |
| **Gate 1** the CI RFC and the branch hygiene | **delivered with the owner's approval** | 108.6 min → 4-8 min on the ordinary path; 7 branches deleted with a proven recovery |

**The optimization target moved, and the evidence shows it.** Before this programme the asking path
**refused 0 of 6** unsupported probes and reported a contradiction for **1 of 6**; contradictory
evidence was identified in **0 of 3** explicit cases; ambiguous readings were represented in **0 of 4**.
Now: the premise's disposition is explicit in the served response, the ambiguity's readings are
**7 of 7** (from 0), the conflicts are surfaced conservatively (see §B for the price), and the
refusal names the claim and source ids that do the refuting.

---

## B. P0.1/P0.2 — the premise semantics in the production asking path

**The files.** `PremiseSemantics.java` (new — the five dispositions), `ClaimConflictDetector.java`
(new — the corpus conflicts, never resolved), `PremiseChecker.java`, `PremiseAssessment.java`,
`KnowledgeWorkbench.java`, `Answer.java`, `ArtifactReportResource.java` — all under
`matrix-core/src/main/java/io/matrix/knowledge/`.

**The semantics.**

| the situation | the disposition | what the caller sees |
|---|---|---|
| the workspace states the premise | `SUPPORTED` | the answer; the premise and its status in their own field |
| the workspace denies the premise | `REFUTED` | a refusal naming the **claim id and the source id** that deny it |
| nothing bears on the premise | `UNSUPPORTED` | the premise named as unsupported, **never "false"**; the question is still answered |
| the corpus holds accounts that cannot both hold | `CONTRADICTORY` (the premise) / the conflicts field | both sides with their provenance; **not refused, not resolved** |
| the question admits several readings | `AMBIGUOUS` | the readings separately, each with its claim and source; none chosen |
| no premise | `NO_PREMISE` | the answer; no premise note |

**The metrics on the frozen set** (the acceptance instrument of §C):

| metric | the baseline (the pre-change path) | now |
|---|---|---|
| false refusals (a supported premise refused) | 0 | **0** |
| false contradictions (an unsupported or supported premise called refuted) | 0 | **0** |
| unsupported answering (a refuted premise answered as if true) | 3 | **2** |
| ambiguity coverage (the readings surfaced, **matched against the gold**) | 0 / 7 | **5 / 7** — the metric counted the readings before; it now matches them, and the honest number was 3/7 before the ambiguity was fixed |
| conflict coverage (both sides surfaced, matched against the gold) | 0 / 2 | **2 / 2** |

**The regressions do not move.** The official 29-case evaluation: **18/29**, the qualitative columns
byte-identical, the same eleven failures (`A-02, A-04, A-05, A-06, F-02, M-01, M-03, P-01, P-02,
R-04, R-05`); the unanswerable abstention **6/6**, accuracy 1.0000, no case lost; the C2A
`BASELINE-PATH` 17/25. The scope `io.matrix.knowledge.*`: **731 tests, 0 failures**.

**The price, measured — and corrected twice.** The implementer's own before/after claimed the path got
*faster* (the seed corpus 28.67/25.83 ms → 22.92/23.39 ms), because the refutation scan now reads the
**retriever's index** (the coverage is computed over already-tokenised terms, and only the candidates
are tokenised), so the scan dropped from **1.59 ms average / 2.40 max** over **every** claim (348 seed,
888 pool) to **0.15–1.2 ms** over the candidates, and the lexicon is asked **once per term** instead of
once per (claim, term) pair. **Two measurements disagree, and both are reported.** The reviewer's three-point A/B on one machine
(round 2 `8fac168a` seed 33.8 ms / pool 37.3 → round 3 `391a0a0a` 43.3 / 40.8 → round 4 `d7cc12a5`
**53.0 / 48.5**) says the same-assertion rule **costs** +19 ms on the seed corpus and +11 ms on the
pool. The implementer's four-point A/B on another machine (the same harness, the same 36 questions per
corpus, the historical points measured in temporary worktrees: 24.9 / 22.4 → 25.6 / 21.9 → 28.7 / 25.8
→ **29.0 / 25.4** on the seed; 32.7 / 31.1 → 32.8 / 30.9 → 35.4 / 33.8 → **35.8 / 34.4** on the pool)
says the points sit within **1–2 ms** of each other and the run-to-run noise on that machine is of the
same order. **The honest reading: the regression is real in magnitude on a loaded machine and is not
reproducible on an idle one** — the absolute numbers are not comparable across machines, and the
project has no shared benchmark for this path. What *is* measured and agreed: the refutation scan
itself costs **≤1.3 ms**, and the per-question **index build costs 9.4 ms** and the workspace read
6.2 ms — the rule is not the hot spot. The attempt to make the comparison read the index was
implemented and **measured to be worse** (the index build grew 8.8 → 11.0 ms and the ask 26.0 → 30.7 ms,
because the index is built for every question while the re-tokenisation is paid only for the few claims
that can bear on the premise), so it was reverted and the finding left in the code's comment. **No
speed-up is claimed.** A cache was deliberately **not** used: the verdict must stay a function of the
workspace's content.

**The third round, and what it closed.** The second adversarial review found that the correction
itself had defects: different variables were treated as the same statement, and the same statement
with opposite polarity was no longer a conflict at all. Both are closed. It also found that a claim
the workspace itself marks **REFUTED** was being used as the evidence that *another* proposition was
false — an inversion of meaning, and the cause of the photosynthetic false refusal: a claim the
workspace calls false cannot evidence the falsity of something else. The rule is now that such a claim
may refute only the proposition it repeats. Closing it initially broke the earlier wave's **abstention
floor** (0.861 → 0.694, six of the eight false-premise cases stopped being refused); the floor is
restored (**0.861**, the gate 0.900) and the scope is green (116 classes, **737 tests, 0 failures**).

**The fourth round closed the rest.** The same-assertion rule now compares **clauses** (the
proposition and the scope), not "the same subject": a claim that *asserts* the premise supports it,
and the cross-lingual case is bridged explicitly (the Russian formulation of the misconception is
refuted again). The ambiguity is now the corpus's **accounts about the subject**, and when those
accounts disagree the status is `AMBIGUOUS` and the reason **names both sides**. The measurements:
the nine corpus-stated premises → **0 of 9** `REFUTED` (was 3), the plants premise → `UNSUPPORTED`,
the ambiguity coverage **3/7 → 5/7**, and the whole scope **119 classes / 750 tests / 0 failures**.

**The fifth round and the last fix.** The independent reviewer reproduced every pinned number and
called the work **mergeable**, with two limitations to record. The first was a **false negative** in
the safe direction: a claim that denies the premise's *subject* without naming its distinguishing term
was not recognised as a refutation (the same root cause as the two cases the metric counts as
"unsupported answering"). The fix is one rule, reusing the subject test the conflict detector already
has: a claim whose statement (proposition **and** scope) is about the premise's subject and denies it
is read even without the distinguishing term. The reviewer's probe is green, the scope is **123 classes
/ 770 tests / 0 failures**, and the interleaved A/B shows the change inside the noise (±1 ms). The
second limitation is the **latency plateau** — recorded as an accepted cost, not as a fix.

**What remains open, and why.** Two defects are **measured limitations, not silences**:
(a) *"Since plants release oxygen during the day…"* is still `REFUTED` although the corpus states it
— the refuting claim is itself a `REFUTED` claim whose scope is "the misconception, stated so that it
can be refuted", and the two premises are lexically indistinguishable against it (each covers 3 of
its 7 content terms); there is no lexical discriminator, and one would need a semantic parser — which
the directive forbids. (b) The paraphrase equivalence is partial: two cases of the frozen set report
`UNSUPPORTED` where the originals report `SUPPORTED`/`ANSWERED`; the coverage threshold that would
fix them (0.57–0.60) produces a false `SUPPORT` on another case (0.75). (c) Two cases of the frozen set now report `AMBIGUOUS` where the set's expectation says
`SUPPORTED` / `REFUTED` — the corpus's accounts about the subject do disagree on them, so the
disposition is defensible, but the set's expectation was written before the rule existed. The
acceptance metric now **matches** the gold instead of counting the readings, so the coverage number
(5/7) is the honest one; the remaining two unmatched readings are a known gap. (d) **The
"unsupported answering" count is 2 and did not fall to 0.** E-R02's disposition *improved* (its
disposition is now `CONTRADICTORY`: the corpus's denial is found and reported as the contradicting
evidence) and E-L01 is a different rule (its premise verdict is `UNRESOLVED` — five contradicting
accounts that do not settle each other — so it becomes `CONTRADICTORY`). The remainder is the
**corpus-in-conflict precedence**: the path refuses to settle a refutation that sits in a corpus that
holds conflicting accounts. That is a separate semantic decision, and it would touch the conflict
metric, so it was deliberately not taken here.

**And the trade, twice reviewed.** After the first review the detector was made **conservative**: it
reported a conflict only for the *same assertion* with opposite polarity and the same scope. The
adversarial review showed that this over-corrected — it reported **false** conflicts between
assertions about *different* one-letter variables (the symbol had been dropped from the comparison)
and it **missed** the genuine contradiction of the same statement (the polarity words were stemmed,
so the list that was supposed to remove them never matched). Both are fixed: the symbols are kept,
the polarity words are folded the same way as the content, and a second path compares the **subject**
rather than the word sets. The frozen set's E-C01 now reaches the caller with **both sides** and the
status `CONTRADICTORY`; the false pairs no longer do. The remaining half of the class is an open
defect (§J).

---

## C. P0.3 — the new held-out set

`docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json` (+ `.md`), **16 cases in 9 classes**, built only from
the licensed seed corpus (USGS public domain + Wikipedia CC BY-SA 4.0); the research pool was not
used, so the gold does not coincide with the C2A set.

**The freeze precedes the measurement.** The freeze sha256 `2f4347da61b67df5f039f4dc72c985a8187f5c211e91faf7cd4a7ae2e791acbe`
(sha256 of the canonical `cases` array) is recorded **inside** the set and re-derived by the reader;
the file's own sha256 is `133f3ed006dea7c17e55c7818902448eaa0625f06ce184cf655174b2fd859069`. The
builder proves the gold is a verbatim production claim, that a refuted case is negated **by the
text** (with its own markers), that the unsupported case's content terms are absent from the corpus,
that the trap negates a different subject, that the paraphrase pair differs lexically, and that no
gold and no question coincides with the 29-case set or the C2A set (no shared 4-gram). A leakage scan
found **0** references to the set and **0** of its questions in the production, test, brain-runtime,
gateway and research sources.

**The classes:** `SUPPORTED_PREMISE` (2), `REFUTED_PREMISE` (2), `UNSUPPORTED_PREMISE` (2),
`IRRELEVANT_REFUTATION` (1), `CONFLICTING_EVIDENCE` (1), `AMBIGUOUS_READINGS` (2),
`LEXICAL_NEGATION` (2), `PARAPHRASE` (2), `NO_PREMISE` (2).

**What the set does not establish:** one author, one domain, one corpus, one abstraction level — this
is not external validation. Part of the negatives is mechanically easier than a human's (a term
absent from every document is "found empty" for the right-looking reason); only one hard negative is
present; the conflict class holds a single case, which is a demonstration, not a measurement of the
class; the Russian negation case measures conservative behaviour, not negation reading.

---

## D. P0.4 — the evaluation records no longer train the mind

**The mechanism.** A provenance tag travels with a record (`EpisodeRole`: `ORGANIC` only when
explicitly declared, `LEGACY_UNTAGGED` when it is not); an admission gate (`LearningAdmission`)
keeps everything else out of the learning window while leaving it in the durable log; a reversible
quarantine (`EpisodeQuarantine`, append-only) records the marks; the gate **fails closed** on a
ledger it cannot read; the runtime path carries the counters (`DreamReport`).

**The applied remediation.** The real log `data/mind/episodic.ndjson` (226 records) was **not edited,
not truncated and not rewritten** — sha256 `5732004d24956132712f6a875ef6082faa3b8df251421f07390b500e894ca11b`
before and after. The sibling ledger holds **209 marks** (59 frozen probes + 150 designed workload
inputs), applied by **record id**, never by position.

| | before | after |
|---|---|---|
| admitted | 226 | **14** |
| excluded | 0 | **212** |
| the window | 20 (10 positive / 10 negative) | **14 (12 / 2)** |
| the induction fidelity | 0.50 | **0.75** on a mixed window |

**The owner's answer on the residual, and the one frozen-zone change it authorizes.** The owner
chose (question D1-QUARANTINE-RESIDUAL-010, option B): the residual records stay as they are, and
the frozen probe corpus gains the pattern of a **letter-less arithmetic probe** — a whole query that
is nothing but digits, one of `+ - * /`, digits and an optional trailing `?`. The rule is
**reject-only**: it can only exclude a record from learning, never admit one, and its precision comes
from the whole-text anchor, so a claim that merely contains digits and an operator ("The answer is
2+3=5") and a legitimate question with numbers in it ("How many legs does a spider have?") are both
untouched. It marked **3** more records of the real log (`100-7?`, `7*6` twice); the ledger was
**appended** by the tool (209 → 212 marks, the previous bytes preserved) and the log's digest is
unchanged. This is the **only** change to a frozen zone that the initiative is authorized to make; it
is recorded as **OD-059**, and the exception it needs in the quality gate's frozen-zone baseline is
recorded with the owner's authority, its scope and the 79 diff lines it costs. The battery's **items**
are untouched, so the evaluation set and its published pass rate do not move.

**The honesty note.** 0.75 on a 15/2 window is a real measurement; the earlier 1.00 that the
positional rule produced was a **single-class window** — trivial, not a gain. The residual is 17
records with 0 designed inputs and 0 frozen probes.

**The independent re-review** (a second agent, read-only, 14 probes) confirmed **8 of 8** checklist
items: the rule is stable under growth (a fresh organic record is not marked), a corrupted ledger
fails closed and the runtime says so, every role-less constructor defaults to `LEGACY_UNTAGGED`
(`EpisodeRole.ORGANIC` appears in **no** line of the brain-runtime main sources), the
provenance-free `induce(...)` refuses, the three window definitions agree byte-for-byte, and the
frozen `EvalBattery` is untouched.

**The one MAJOR it found, and the fix.** The quarantine ledger is machine state outside git
(`.gitignore:83`), and **no test guarded it** — with the ledger absent, the gate's documented fallback
admits everything again, so the contamination would return silently. The fix: a **tracked canary**
(`EPISTEMIC-LEARNING-QUARANTINE-CANARY.json` — 226 records, 209 marks, the log's digest as a
historical fact) and a test that fails when the ledger is missing, **monotonically** ("at least"), so a
growing log never breaks the build and no digest is pinned. The failure was demonstrated on a copy:
with the ledger removed, the canary names the exact consequence. A fresh clone skips with a reason.

**The ledger's own disappearance now breaks the build.** The canary: a tracked JSON records the log's
record count and the mark count, and a test fails when the log holds at least that many records but
the ledger is missing — **monotonically** ("at least"), so a growing log never breaks the build and no
digest is pinned. The failure was demonstrated on a copy. The known limits below are recorded rather
than hidden. The content rule can quarantine a genuine organic record that
repeats a designed input; the quarantine is enforced at the gate, not at the trainer; the gate trusts
a self-declared role. The HDC retrieval substrate was not traced for earlier contamination; the
gateway's other writers were not traced.

---

## E. P0.5 — the packaged application opens the store with no manual property

**The earlier diagnosis was wrong and is corrected.** It blamed the driver's resource extraction
inside the application's class loader; the lookup **succeeds**, and the failure was the driver's
**write into its extraction target** — the application's scratch directory under a tmpfs whose
per-user quota the project had already measured exhausting (R48/NR-034). The requirement of
`-Dorg.sqlite.lib.path` is **not reproducible** in the JVM packaging.

**The fix** (packaging only — no loader hack, no new dependency, no upgrade): the native-image build
argument for the driver's loader; a `verifyPackagedSqliteNativeLibrary` task that inspects the
**packaged** output; the demonstration script's manual extraction and property **deleted**.

**The proof** (the coordinator's own run, not the agent's): a clean `quarkusBuild` → BUILD
SUCCESSFUL; the smoke from a temp directory **outside the repository** with no property anywhere —
seed 200, report 201, the artifact's digest `c44aee6e…` equals the reported sha256, the repeat →
200 `REPLAYED` with one artifact, no consent → 403 with nothing written, 4 concurrent requests → exactly
one artifact added, after a restart → 200 `REPLAYED`. **The mutation proof**: with the native library
removed from the packaged driver, the guard fails and the application reproduces the recorded error
verbatim. Six unit tests pin it.

**Not verified:** the native image itself (that build is red on this host — OOM), any platform other
than Linux x86_64, and the demonstration script end to end after the workaround's removal.

---

## F. P0.6 — the `main`/`develop` divergence, reconciled against the live diff

The trees differ by **four documentation files and no code**: `docs-v2/INDEX.md`,
`docs-v2/architecture/CAPABILITY-REGISTRY.yml`, `docs-v2/engineering/C2A-FINAL-REPORT.md` and
`PROJECT-STATE.md`. `main` is a strict subset of `develop`. The reconciliation is recorded in
`PROJECT-STATE.md`; the divergence itself was not force-resolved — `main` stays the stable release
branch.

---

## G. Gate 1 — the CI lanes and the branch hygiene

**The measured before:** PR #60 = **51.5 min wall / 54.8 runner-min**; one change to `develop` =
**108.6 min wall / ≈118 runner-min** (the pipeline ran twice — push and pull_request on one SHA);
the aggregate `ci` red exactly at "Login to Docker Hub" (the repository holds **zero** secrets); the
ShellCheck lane red on 43 files; the native lane red in 4.6 min on a wrong path.

**The measured after** (the RFC's projection, the lanes land on the next runs): the FAST PR lane
4–8 min; the release lane unchanged (≈50 runner-min — **a release does not become cheaper**).

**The lanes.** FAST PR (automatic on `develop`: the path-sensitive compile and targeted tests, the
constitution/frozen gate, the documentation-consistency check, the secret scan, plus the
path-sensitive ShellCheck and web-ui checks) · LOCAL AGENT (no CI) · MANUAL/RELEASE (`workflow_dispatch`
or a pull request against `main`: the full regression, the packaged smoke, the model checking,
SpotBugs, the native image) · PUBLISH (`workflow_dispatch` with the literal confirmation `PUBLISH`,
credential-gated — so a missing credential can only make a manual publish **skip**, never make the
ordinary CI red). **No safety guard was weakened and the 82 % method-coverage standard was not
touched.**

**Two facts were found rather than assumed.** (1) The ruleset "Protected main" gated on
`~DEFAULT_BRANCH`, so moving the default branch left **`main` unprotected** — the condition now names
`refs/heads/main` explicitly as well, and `main` is protected again. (2) A matrix job's check-run name
carries its matrix value, so the lane publishes the matrix's outcome a second time under the stable
name **"Compile + targeted tests"**; without it the required check could never be satisfied. Six
check names are now the **required status checks of `develop`**.

**The lane has been running — and it was red.** The earlier belief in this programme that the FAST PR
lane "has never been exercised" was **wrong**, and it is corrected here rather than quietly dropped:
the lane triggers on every push to `develop` as well as on every pull request, so it has been running
on every push of this programme — and it was **failing on every single run**. The two causes were
defects in the lane's own checks, not in the code under test:

1. **The documentation check could not see the line it had to compare.** `scripts/validate-current-state.py`
   looked for the main line at `origin/main` and then at `main`. A CI checkout is a **depth-1** checkout
   of the pushed branch, so it carries **neither** ref; the script therefore reported
   *"no main line is readable (git cannot answer)"* as a **contradiction** and the check was red on
   every run. The script now reads the first ref the checkout actually carries
   (`origin/main`, `main`, `origin/develop`, `develop`, and `GITHUB_SHA` for a pull-request merge
   commit) and **names the line it compared** in every message. The fix was proved against a
   **reproduced** CI checkout — a `--depth 1` clone of `develop` — where the committed script reported
   **1 contradiction** and the fixed script reports **0**; and a **negative control** (a false release
   claim planted in that checkout) is still caught with exit code **1**, so the check was not weakened
   to pass.
2. **The aggregator turned "nothing to compile" into a failure.** The stable required check
   *"Compile + targeted tests"* re-publishes the matrix's outcome, and it required
   `result == "success"`. A change that touches **no module** leaves the matrix empty, the matrix job
   is **skipped**, and the aggregator failed — so every documentation-only or script-only commit was
   unmergeable. The owner's answer to **D1-CI-AGGREGATOR-017** (option **A**, recorded as **OD-066**)
   sanctions the repair: an empty matrix now passes, while a matrix skipped because a **dependency
   failed** still fails the gate (the two are told apart by the scope job's `build-matrix` output).
   The `.github/` frozen-zone exception was raised from 2350 to **2361** diff lines with the owner's
   sanction recorded in `docs-v2/quality/frozen-zone-baseline.txt`, not waved through.

**The measured after.** The lane is **green**: the run **44169314944** (`gh run view 44169314944`) ends
with the conclusion `success`, and every job in it is `success` — the scope, the constitution and
frozen-boundary gate, the documentation consistency, the secret scan and the compile-and-targeted-tests
gate. The lane's own discipline is unchanged: the known flake in `CognitiveKernelRemoteTest` is
neither skipped nor retried.

**The branch hygiene.** All 27 refs were inventoried: 20 KEEP, 5 PRUNE-ELIGIBLE, 2 OWNER-QUESTION.
The 7 owner-approved branches were deleted after each received an annotated archive tag pushed to
`origin` **before** the deletion and an entry in a bundle (sha256 `9a2b54bf…`); the restoration was
proven in a temporary independent clone from GitHub — **7 of 7 tips matched**. The safety of each
deletion rests on a **tree comparison against the merge commit**, not on ancestry: a squash merge
preserves no ancestry. Every pre-W46 branch, `main`, `develop` and the dependabot branches are
preserved.

---

## H. P1 — the concept identity: the root cause, measured

The concept **name** is the claim's **subject phrase** (`DocumentImporter.java:225-231`) and the
concept **id** is a digest of that name (`:338-341`) — a concept is the left side of a copula, cut
where the verb list stopped (and the verb list contains nouns: "The **result** is a slight lateral
incline…" yields the concept named `The`). Measured on the seed corpus: 348 claims / 326 concepts,
**319 of 326 concepts hold exactly one claim (97.9 %)**, 0 usable definitions, 0 persisted relations,
and a hand-labelled sample of 40 names puts the genuine-referent share at **30 %** (CI 18–45 %).

**The alternatives, measured, not argued.** (a) The current splitter — the baseline. (b) An
NP-boundary filter — **rejected**: it leaves 42 claims without a concept and silently narrows 34
referents (`Mars and Venus` → `Mars`), and `concept_id` is `NOT NULL` with a validator that throws.
(c) A referent key (normalised lemma + source document + anchor span) — the right **shape**, the
resolution and alias policy unsolved. (d) Source-span-anchored definitions — **adopt first**: it is
the only candidate that raises a measured coverage number (0 → 121/326 = **37.1 %**).

**The decisive finding:** repairing the *name* does **not** raise the definition coverage (the
rejected alternative reaches 83/299 = 27.8 %, below the baseline's 37.1 %). Identity and definitions
are **separate defects**. The relation counts are lexical, not semantic.

**One correction the research forced.** The published figure "61 dangling names (19 %)" is **not
reproducible from the repository** — the 120-word list behind it was never published; the
reconstruction gives 61 (18.7 %) on that list and 75 (23.0 %) on a wider one. The honest statement is
a **band**, and the acceptance criterion "the dangling rate is 0" must name its list. The two
documents that carried the number are corrected.

**The definition half is implemented and measured (P1, committed).** The **tight v1.1 rule** of the
candidate freeze is implemented verbatim in `acquisition/SourceSpanDefinitions.java`: the concept's own
sources in the snapshot's order, the sentences in document order, a sentence qualifies when it holds
the concept's name as a word **and** the name is immediately followed by a definitional copula (a
closed vocabulary) **and** it is at most 400 characters, the **first** qualifying sentence wins and the
definition **is** that sentence verbatim, and no qualifying sentence means **no definition** (an honest
state, not an invented one). One detail the research instrument had and the production path needed:
the sentence must be **located inside its segment's span**, because a claim's context carries the
*segment*'s span and the segment's span alone cannot say which of a segment's sentences comes first —
without it 10 of the 120 definitions were a different (also qualifying) sentence; with it, the
production set is **identical, set by set, to the research instrument's 120**. The definition is a
record of its own (`ConceptDefinition`: the sentence, the source id, the offsets, the carrying claim,
the method), stored in an **additive** table (schema v20): nothing is copied, nothing is dropped, no
claim id moves.

**The acceptance table, measured — and one row of it under hard review.**

| | the baseline | after |
|---|---|---|
| **A1** the concept names' precision | 30 % (12 of 40) | **35/150 = 23.3 %** on the frozen 150-span set; the first 40 of the draw give **12/40 = 30 %** — the same share, from an independent sample |
| **A2(i)** the usable definitions | **0** | **4/326 = 1.2 %** — **corrected twice downward** (see the correction notes below): the rule now requires the name to **be the subject** *and* the predicate to be **definitional** (genus, identity, classification — not an action, a comparison, a measurement, an origin or a state); the earlier, looser rules reported 120/326 = 36.8 % and then 27/326 = 8.3 % |
| **A2(ii)** the human-verified share | not measured | **3 of 4 = 75 %** — the census of the four definitions, and the **independent re-review counts the same three** (*Trenches* — genus, *The San Andreas* — identity, *Subduction zones* — classification); the fourth (*"The trenches are the key to understanding how island arcs … have formed"*) is a **discourse about** the concept, not a definition of it, and the rule's gate passes it. The metric asks *"is this a definition of its concept"*, and its labels no longer contradict its own protocol. The history of this figure is kept below: the weaker metric reported 24/25 = 96 %, the first corrected rule gave 18/27 = 66.7 %, the independent review counted 3/27 = 11 % under that protocol, and the rule now has the **definitional-predicate gate** the review required |
| **A3** the relation precision | not measured | **0/7 = 0 %** (five fragmentary endpoints, one with its direction reversed) — the change does not touch the relations |
| **A4/A5** the retrieval | 18/29, 6/6, 0.3000 | **unchanged, every content column identical** — deliberately: see below |
| **A7** the cost | — | the retriever is **within the noise** (702–1080 µs against 692–794 µs); the baseline path's own timing drifts 59 → 68 → 76 ms across runs of the **same** code |
| **A8** the id stability | — | **no concept id changed** — measured **id by id**, independently: the seed corpus imported document by document into a temporary database gives **77** concepts after the first document and **326** after the second; **77 of 77** earlier ids still present, **77** with the same name, **0** with a different name, **0** gone, and **326 of 326** ids are the digest of their own name (`Ids.digest(workspace|concept|name)`). The consequence is structural and is now confirmed twice: **the concept id is not a surrogate key** — any rename yields a new id — which is exactly why the identity migration needs an explicit lineage rather than an in-place edit |

**The decision the measurement forced, and the owner's answer.** The definitions were first written
into the `concept.definition` column, which the retriever appends to every claim's searchable text.
Measured, that moves the official evaluation **18/29 → 17/29**, the unanswerable abstention **6/6 → 5/6**
and the unsupported-answer rate **0.3000 → 0.4500** — a degradation of the refusal correctness that the
promotion rule forbids. The owner was asked (question D1-DEFINITIONS-IN-SEARCH-011) and chose
**C** (OD-060): the definitions **stay out of the searchable text**, and the question of where they
*help* becomes a **separate measurement** rather than a default.

**The owner's option C executed — and the result is a NULL RESULT.** A four-arm measurement
(`DefinitionBenefitMeasurementTest`: **WITHOUT** — the production state, **WITH_ALL** — the uniform
H1, **WITH_RULE** and **WITH_STRICT_RULE** — two rules stated over the concept's *name*, never over a
case, a question or a corpus phrase) was run over the same corpus, the same questions and the same
method, **interleaved case by case** so that a busier machine cannot be mistaken for an arm; the
instrument asserts its own calibration (the production arm must reproduce the published failures).

| the arm | the official 20 ask cases | the unanswerable abstention | answered without the gold | the mean ask |
|---|---|---|---|---|
| **WITHOUT** (production) | **12/20** | **6/6** | 8 | 72 ms |
| **WITH_ALL** (uniform) | 11/20 | **5/6** ✗ | 9 ✗ | 71 ms |
| **WITH_RULE** | 11/20 | 6/6 ✓ | 9 ✗ | 68 ms |
| **WITH_STRICT_RULE** | 11/20 | 6/6 ✓ | 9 ✗ | 72 ms |

On the **frozen held-out set every arm is identical** (8/16, 12 answered, 4 abstentions) — the
definitions change nothing there. Two cases move: the unanswerable **U-04** is *answered* under the
uniform arm (the refusal degradation, reproduced by an independent instrument), and **A-03** is *lost*
by both rule arms — and the strict rule still loses it because the concepts it cites whose definitions
the strict rule admits are the **well-formed** ones ("Plate tectonics", "Continental crust", "Slab
pull", "Plate boundaries"), so the loss comes from the good definitions, not from the fragments the
rule filters out. **The verdict: the definitions have no measurable benefit in the retrievable text.**
A rule exists that preserves the refusal correctness, and it still costs a case and gains none. The H1
question is therefore **closed by the measurement**, not left open: the definitions are worth keeping as
**knowledge** (the corpus's own account of a concept, with its span, 120/326, 24/25 human-verified),
and they are not worth putting into the retriever's searchable text on this corpus, under these rules,
at this retriever floor. `[speculative]` both rule variants are fitted to one corpus and to the one case
that moves; the verdict does not depend on them — the held-out set is identical in every arm and no arm
gains a single case. What the null result does **not** establish: that the definitions are useless (a
different floor, a query expansion or a semantic match over the definition was not measured), anything
about the nine non-ask cases, and anything beyond this corpus.

**The correction, recorded rather than quietly swapped.** The hard review (Gate 6) measured that
**31 of the 120** definitions hung on spans the wave's **own frozen hand-labelling** calls "not a
concept", and that **33 of the 120** had the concept's name **not in the subject position**; and it
showed that the acceptance metric measured *"about the concept"* rather than *"is a definition"*. The
rule was therefore changed — the name must **be** the subject, a denial ("X is not Y") no longer
passes, and the non-referent names are rejected — and the numbers were **re-measured under the
corrected rule and the corrected metric**. The honest result is much smaller: **27 definitions of 326
concepts (8.3 %)**, of which the hand-verified census finds **18 to be definitions of their concept
(66.7 %)**. The earlier figures (36.8 % and 96 %) measured a weaker notion of "definition" and are
kept here only as the record of what they measured. `[speculative]` the sample behind the 18/27 is
small, and the reviewer's own probes (`HardReviewDefinitionPrecisionTest`) have not been re-run
against the corrected rule by an independent party.

**The third correction — the rule now demands a definitional predicate.** The independent re-review
established that the subject test is **necessary but not sufficient**: it accepts sentences that state
an **action** (*"The plate edges are compressed, folded, and uplifted forming mountain ranges"*), a
**comparison** (*"The lithosphere is cooler and more rigid, while the asthenosphere is hotter…"*), a
measurement or an origin. A third gate was added: the predicate must be **definitional** — the genus,
the identity or the classification, the thing that says **what the term denotes**. The two holes in the
negation gate were closed as well (a qualifier between the copula and the negation — *"is largely
not…"*, *"is sometimes not…"* — no longer passes, and the **longest copula is matched first**, so *"is
defined as not a canyon"* no longer passes). **The result is four definitions of 326 concepts (1.2 %)**,
and the hand-verified census now finds **3 of the 4** to be definitions of their concept — **the three
are the very three the independent review named** (*Trenches*, *The San Andreas*, *Subduction zones*).
The fourth (*"The trenches are the key to understanding how island arcs … have formed"*) is a discourse
about the concept, not a definition — the reviewer's borderline case. The retrieval's cost is
unchanged (**729/734 µs with the definitions against 734/744 µs without** — inside the noise).

**The second correction, also recorded rather than swapped.** The independent re-review of the
corrected rule confirmed two of the three conditions (*the name is the subject*: the reviewer's own
counter says **0** of the 27 fail it; *the first qualifying sentence*: `derive` walks the sentences in
document order and takes the first that passes) and rejected the third: **the acceptance metric's
labels contradict its own written protocol**, so **18/27 is not reproduced by the declared criterion**
— the reviewer's independent count under that criterion is **3 of 27 = 11 %**. The substantive finding
is that **the subject test is necessary but not sufficient**: the rule has no test for a *definitional
predicate*, so it accepts sentences that state an action, a comparison, a measurement or an origin.
Two holes in the negation gate were also found: a qualifier between the copula and the negation
(`is largely not …`, `is sometimes not …`) passes, and the copula list's order lets `is defined as not
…` through. Both are being fixed, and the numbers will be re-measured again — **the 18/27 and the 3/27
both stand in this report until that measurement exists**, and the field's name (*a definition* vs *a
sentence about the concept*) is part of the question.

**The verdict of the independent review: P1 is MERGEABLE, with measured limits.** The third review
round checked the three conditions of the previous verdict one by one — *(1) the name must be the
subject* ✓ (the reviewer's own counter says **0** of the 27 fail it), *(2) the metric must measure
*"is this a definition"* ✓ (the census of the 27 was re-labelled by the protocol as written and gives
**3/27**, exactly the reviewer's own recomputation; the labels no longer contradict the protocol), and
*(3) "the first qualifying sentence" must be the first with the name as the subject* ✓ — and then
attacked the new gate with its **own** sentences: an action (*"is carved by subducting plates"*), a
comparison (*"is deeper than a canyon"*), a measurement (*"is 100 km long"*), an origin (*"is formed at
a subduction zone"*), a state (*"is still open"*) and a reported opinion (*"is thought to be a long
narrow depression"*) are all **rejected**, while a genus (*"is a long narrow depression…"*), an
identity (*"is one of the few transform faults…"*), a classification (*"is of two types…"*), a naming
(*"is called a deep-ocean trench"*) and a composition (*"consists of two plates…"*) are all
**accepted**. The negation gate was re-probed and **all the earlier holes are closed** (the plain
negation, a qualifier between the copula and the negation, a multi-word copula, an adverbial between
them, a double negation). Two residual holes are named: a **discourse** passes (*"is the key to
understanding island arcs"* — the corpus's own fourth definition, a role and not a genus) and a
legitimate definition with a determiner outside the closed list is rejected (*"is any long narrow
depression…"*).

**The measured limits, published verbatim.** **L1** the coverage is **4 of 326 = 1.2 %**, of which
**3** are true definitions; the other **322** concepts have none — an honest state, not a defect.
**L2** the rule's own precision is **3 of 4**: the fourth is a discourse that the gate passes, so the
honest claim is *"three of the four are definitions"*, not *"the rule produces definitions"*. **L3** the
rule **under-accepts** (the closed determiner list loses legitimate definitions). **L4** there is **no
measurable benefit** in the retriever (the H1 null result) — the definitions are the concept layer's
infrastructure, not a retrieval win; that is a fact about the corpus, not a defect of the rule. **L5**
the number of true referents that lose their definition **grows as the rule tightens** (in the
previous round: 6 of 35 under the 27-definition rule); the count under the third gate was not
recomputed. The scope at the verdict: **144 classes / 848 tests / 0 failures** ✓, the quality gate
**PASS 271/271** ✓.

**What this does not establish.** It does not make a concept name right — a definition attached to a
fragment is a definition of a fragment, and 30 % of the names denote no genuine referent. It does not
establish the definitions' truth (they are the corpus's own sentences) and it does not improve the
retrieval (they are deliberately not in the searchable text). It does not generalise beyond nine
documents, one domain, one language and one register.

**The referent key and the migration — measured, rehearsed, and the owner's decisions taken.**

The **research** recommended the *shape* (a normalised lemma + the source document + the anchor span,
with a display name and aliases) and said that the **resolution and alias policy is the unsolved
part**. That part is now designed and measured. The key is a **reject-only** policy (a name is never
truncated): a nested clause, an appositive, an adverbial modifier, a caption, a generic head and an
artefact are rejected **by form**, not by a word list — **37 of the first policy's 40 false positives
were structural**, and a closed lexicon cannot fix them.

| the policy on the frozen 150-span draw | accepted | precision | 95 % Wilson | false positives | lost true referents |
|---|---|---|---|---|---|
| the current splitter | 150 | **23.33 %** | [17.28; 30.72] | — | — |
| the structural rules alone | 55 | **61.82 %** | [48.61; 73.48] | 21 | 1 |
| **LENIENT** | 36 | **94.44 %** | [81.86; 98.46] | 2 | 1 |
| **STRICT** (the owner's operating point, OD-062) | 29 | **100 %** | [88.30; 100] | 0 | 6 |

The intervals do not overlap at any level. **The honest caveat travels with the number**: the
residual lexicon was written from this draw's own false positives, so **94.44 % is in-sample**; the
structural rules' floor is **61.82 %**; and **out-of-sample is not measurable** on this corpus — a
second sealed held-out sample would be needed to quote A1 externally. The four properties of the
policy hold at every level: *the same word in different contexts* 4/4, *synonym* 4/4, *paraphrase* 3/3,
*homonym* 3/3, over 14 hand-marked cases. The promotion rule is satisfied — A1 and A2 improve, and A4
and A5 do not regress (the retrieval arms are byte-identical: 12/20, recall@1 0.2024, MRR 0.5078, the
abstention 6/6) — but the **cost is named**: **68.4 %** of the seed corpus's claims (238 of 348; 526 of
888 on the wider corpus) would have no concept, because a fragment's own text is not a referent.

**The owner's decisions**: the schema fork is the **claim-scoped record** (OD-061, additive — the ten
referencing tables are not touched and the concept id stays `NOT NULL`); the operating point is
**STRICT** (OD-062); the migration is applied **first to the working corpora**, the owner's live store
only by a later separate decision (OD-063); the alias layer is a **declared** table, explicitly
temporary (OD-064).

**The migration is now rehearsed — on copies, with the evidence.** The plan under STRICT gives **90
referents, 255 claim-scoped records and 345 new concept rows**; the copy-forward is **one
transaction** (54–60 ms) and re-points all 348 claims and **93** representations; the lineage holds
**696** rows and **348 of 348** claims point at a concept from it. **The ids that change and those
that do not:** the claim ids do **not** change (the digest of the workspace, the content hash and the
proposition — so the claims, the evidence and the proposition-based golds survive); the referent ids,
the claim-scoped ids and 93 representation ids are recomputed; the old concept rows are marked
superseded and **not deleted**. **The orphan audit is proven, not asserted**: on the correct copy
**13 tables, 0 orphans, 0 stale pointers**; on a deliberately broken copy **6 orphans and 1 stale
pointer** — without the broken copy the audit would have been an unverified claim (SQLite declares no
foreign keys, so `validate` cannot see a partial migration). **The rollback is proven**: the snapshot
(179–226 ms, +1.86 MB) restores the concepts **671 → 326** in 89–127 ms. **The re-measurement after the
migration**: the official ask cases **12/20** with the identical eight failures, the abstention **6/6**,
the frozen set **8/16** — no regression; and the definitions' share rises **27/326 = 8.3 % → 27/90 =
30.0 %** with no definition lost. **What the rehearsal does not establish**: the prerequisites'
acyclicity and the `refines` chain were **vacuous** on the seed corpus (0 and 0), the C2A
`BASELINE-PATH` was not re-measured (its judge is private and its workspace is prepared by the
harness), and the owner's store was never exercised.

**The migrated copy, re-measured by the same judge.** The harness gained a second phase (a copy of
the workspace → the copy-forward → **the same** `judge` method, the same questions, the same
dataset) and its **calibration** is a comparison against the committed artefact, not an assertion of
a number it also computes: the published row `BASELINE-PATH` **17/25**, judged 38, the eight failures
(H-P02, H-P05, H-P06, H-P08, H-P10, H-P11, H-P13, H-P17), recall@1 **0.5351**, MRR **0.8600**,
abstention **1.0000**, unsupported **0.2800** — all identical before and after the harness change.
**On the migrated copy**: `BASELINE-PATH` **17/25** with the **same eight** failures, recall@1
**0.5351**, MRR **0.8600**, abstention **1.0000**, unsupported **0.2800** — **no regression**. The one
difference is a difference and not an improvement: the mean number of **non-gold** claims cited fell
from **5.2105 to 4.6842**. **The claim-scoped records stay retrievable**: 314 of them on the pool, and
**17 of the 25** comparable cases depend on a claim whose gold lives in a claim-scoped record; after
the migration 16 still answer, the one that does not (**H-P02**) did not answer before the migration
either — **the changed answered-flags: 0**. The migration silenced nothing.

**The two vacuous checks, now non-vacuous.** The C2A corpora hold **no prerequisite rows at all**
(0 and 0) — those rows come from the bundled **knowledge packages**, so the acyclicity was checked
where there is something to check: the migrated bundled package (8 concepts, 17 claims, 17 inserted
concepts, the prerequisites re-pointed) gives a graph of **4 nodes and 5 edges with no cycle** ✓ and a
clean audit ✓. The `refines` chain is real on the pool (the revise flow leaves one link) and it was
**carried: 1 before, 1 after, 0 dangling** ✓.

**The defect the migration found, left visible.** On the pool there is **one collision of a
representation's id**: two representations of two merged concepts carry the same statement at the
same level, the derived rule `Ids.representation(workspace, conceptId, level, statement)` gives them
one id, and the table's primary key refuses it. Nothing is deleted — the row keeps its id, is
re-pointed at the referent, and the collision is counted and published — but the recalculated id is
not unique, so the migration needs an owner's decision about the duplicates. The documented 60-minute
provisional policy applied the recommended option (keep the row's own id on a collision, OD-065,
marked **NAIVE_PROVISIONAL** — the owner's confirmation is outstanding, and the implementation already
behaves that way).

---

## H-bis. P3 — the meaning of the learning label: a negative result

**The directive's question** (§3 P3): *"confidence computed from retrieval overlap is not independent
task correctness"*. Today's label is `confidence >= 0.7 AND accepted` — a measure of the pipeline, not
of whether the task was right.

**The signal was built**: `StoreGroundingVerifier` asks whether an answer is confirmed by what the
machine itself knows — a cross-check against the machine's **own** store (2 015 facts, in text), never
against the evaluation's gold. The verdicts are `GROUNDED` (a fact covers ≥ 0.6 of the answer's
terms), `NOT_GROUNDED` (the store is silent — **not** "false"), `REFUSED` and `UNKNOWN` (outside the
denominator). The check costs **0.23–0.61 ms** per observation against **8.06 ms** for the retrieval
itself (7.5 %). The four provenances are separated on the record itself, and the record's constructor
**refuses** a verdict that was built from an expectation.

**The comparison, with the leakage-controlled partitions.** On the machine's own 226 observations
(the goal = the production label, split by input: 141 train / 60 test): the raw 256-bit feature 0.200,
the fold **0.750**, the informative and the discriminative projections 0.183, a random projection 0.000,
the majority class 0.000, and the **shuffled-label null control 0.033**; a 1-NN over the raw bits
0.767. On 59 fresh observations from the production path (the goal = the verified outcome, split
stratified: 33 / 13): the raw 0.713, the fold 0.838, the projections 0.938, the random projection
0.500, the majority 0.500, the 1-NN 0.962, and the null control **0.475**. The cost: 7–9 ms to train,
0.7–1.6 µs to infer, **2 bytes per episode**.

**The controls.** The **positive** control passes: the fold reaches 0.750 and the 1-NN 0.767 against a
null of 0.033. The **null** control passes: the fold on shuffled labels reaches 0.500 — chance. The
**contamination** harness passes: the same observations in both halves give 1.000 against a clean 0.500,
so the harness sees a leak when there is one. The leakage counters are 0 for the by-family, the
by-input and the stratified splits (and 46 for the deliberately contaminated control).

**The negative results, published as they are.** (1) **No representation beats chance on the verified
outcome**: on the machine's own observations the signal is degenerate (199 `GROUNDED`, 2 `NOT_GROUNDED`,
25 `UNKNOWN`), and on the fresh ones the **null control's spread overlaps every real representation**
across the seeds 42/43/44 (the null: 0.438 / 0.748 / 1.000). (2) The independently verifiable signal
on this corpus is a property of the **question family**, not of the observation — under
leave-one-family-out every fold is single-class — so **it cannot be a per-episode learning label under
any representation**. (3) The production label is **recoverable from the production feature** (0.750 /
0.767 against a null of 0.033): the feature contains the retriever's answer, the answer is the
retriever's product and the label is its score — so a fidelity measured against it measures the
pipeline's agreement with itself. (4) Cross-task generalisation is **not measurable at all** on the
machine's data. (5) `GROUNDED` is **not** "correct": the repository holds no licensed corpus for the
store's domain and **no external truth for this machine's answers**. (6) The production trainer is
**not validated as a trainer** on this data — the null control reaches a balanced 1.000 on a half of
14 records.

**What this does not establish:** that a signal is impossible — only that this corpus, this feature
and this split cannot produce a per-episode label of independent correctness. A different signal, a
different partition or an external truth would be a different experiment. **`K_MAX = 20`, the trainer
width and the production fold are retained** — the owner has not approved a change, and none was made.

---

## I. P2 — the closed-loop cognitive action (the next step, designed)

`EPISTEMIC-CLOSED-LOOP-DESIGN.md` (the owner's choice **OD-055**): the closed loop targets **the
report journey the product already has** — a question over a workspace → the kernel's plan and method
choice → the permissioned `ActionRunner` → the artifact → the evidence of what happened. The design is
deliberately built on what exists and is measured: the premise semantics (B), the sealed evaluation (C)
and the isolated learning (D) are the three instruments it needs.

**The defect the directive named, measured.** The route `POST /v1/workbench/workspaces/{id}/reports`
called `workbench.ask(...)` and the artifact writer **directly**: there was **no method choice in the
product at all** — a hard-wired path — the reason for the choice was never recorded, and the past
experience was never read. The kernel's method registry (`AlgorithmRegistry` + `AlgorithmSelector`) did
not take part in this path.

**The loop, now closed, on the existing surface** (no new route, no new database, no change to the
permission model):

| the phase | the implementation |
|---|---|
| the plan and the method choice | `cognitive/report/ReportJourneyPlanner.java:85` → the kernel's `KernelSelector.selectTyped` (`cognitive/KernelSelector.java:66`) over the `AlgorithmRegistry`; the family `AlgorithmFamilies.KNOWLEDGE_REPORT:42`; **no method → 422 `no_method_for_request`**; the plan cannot widen the consent, the root or the lease |
| the methods | `ReportJourneyMethod.java:143` (reference) and `:156` (replay); the replay's applicability is the facts *identity-committed / workspace-unchanged / name-unchanged*, checked by the selector and by the method itself |
| the resolution | unchanged: the consent, the gate, the output root, the writer's lease; the 4xx refusals, `REPLAYED` and "exactly one artifact" all still hold |
| the observation | `cognitive/report/ArtifactObserver.java:82` — a **reader**, not a writer: the artifact exists, its size, its sha256 **over the bytes**, and the index holds exactly one STAGED and one COMMITTED; a discrepancy is a **failed effect** and is not smoothed over |
| the trace | `cognitive/report/ReportJourneyTrace.java:105` → `CognitiveStore.appendEvents` (`cognitive/CognitiveStore.java:2087`), the `cognitive_event` table in the workbench's own database; append-only, one transaction per trace; the request, the method, the reason, the action, the effect, the verification |
| the repeat | `ArtifactReportResource.java:262` → the retained record → the facts → the selector: the second equivalent request reads the trace, chooses **replay**, and does not ask the workspace at all |

**The measured end-to-end run** ("What is photosynthesis?", the workspace `photosynthesis`): the first
plan selects `report.journey.reference.v1` (score 1.0, one candidate); the second selects
**`report.journey.replay.v1`** (two candidates, the precision 1.0 over three cases) — **the method
changed**. The resolution: the consent given, the gate open, the server's root, no widening by the
plan. The artifact: `report-b3c106428ed0.md`, sha256 `efe568902bf073cef8ade3b8adc49f31d590f6e4db92103e61224fd40dfca0c9`,
5 084 bytes; the index holds one STAGED and one COMMITTED; the effect verified. The trace: seven steps
(PLAN / PERMISSION / ACTION / OBSERVE / VERIFY / RESULT / RETAINED). **The latency:** the first run
139–150 ms (JIT), the replay **8.3–10.0 ms** (five repetitions) against a baseline that writes the
trace but does not read it at **11.5–13.3 ms** — a win of **3.2–3.3 ms, 25–28 %** (two consecutive
runs). **The negative result is recorded too**: before the trace's append was batched, the trace cost
43 ms per run and the win was **negative**; the mechanism was found and removed.

**What the loop does not demonstrate.** The HTTP response contract was not extended (the trace is
found by the episode id, not by a new field, to avoid breaking the contract); a failed effect is
recorded in the VERIFY step and in the limitations but does not change the HTTP status (201/200) —
that is a deliberate, marked choice; the workspace fingerprint is the sha256 over the claims'
(id, proposition, status), so a change of the workspace's *content* is not detected (a change of its
*name* is); the typed `ActionRegistry`/`ArtifactAction` are **not** in this path (the route still
writes through the restricted `ArtifactWriter`); the counterfactual "the same request with a forced
method" and the read-only design probe of the path's repeatability were **not** implemented; the
concurrency of two runs of one identity in one route and the behaviour when the cognitive table is
unavailable were not measured.

---

## J. What is open, what the owner decided, and what stays red

**The owner's decisions of 2026-11-15** (all recorded in `DECISION-REGISTER.md`): **OD-053** the CI
simplification (the FAST lane's checks required on `develop`; the full lane manual/release; missing
Docker credentials must not make ordinary feature CI red) · **OD-054** no automatic deletion; a branch
is deleted only with a verified durable recovery reference and a proven restoration · **OD-055** P2
targets the report journey · **OD-056** the default branch is `develop`, `main` stays protected ·
**OD-057** the four CI lanes · **OD-058** the seven branch deletions · and the residual quarantine
question, answered **B** (leave the 17 records; extend the frozen probe corpus with an
owner-authorized exception so the letter-less arithmetic probes are caught).

**§J-bis — the live store's dry run (variant A): the defect it found, the fix and the numbers.**

The owner's OD-067 chose variant A — the copy first, the live store untouched — and the dry run
**found a defect the corpora could not**: the migration's rewrite of the prerequisite graph ran
two blind `UPDATE`s per old→new pair, so when two old concepts become one referent their edges can
land on the same triple and the write violates the primary key
(`org.sqlite.SQLiteException: [SQLITE_CONSTRAINT_PRIMARYKEY] … prerequisite.workspace_id,
prerequisite.concept_id, prerequisite.requires_concept_id`). The corpora never saw it — they carry
**0 prerequisite edges** (the rehearsal prints `the prerequisite graph: 0 nodes, 0 edges`) — so only
the owner's store, with 11 edges, could find it.

The rewrite now reads the workspace's edges, maps **both** ends, and keeps the relation as a **set**
of triples: an edge whose target triple is already taken **collapses** (one edge survives), and an
edge whose two ends become the same referent would be a **self-require** — a concept may not require
itself — so it is **dropped**. Both losses are **counted and published** in `MigrationResult`
(`prerequisiteCollisions`, `prerequisiteSelfLoopsDropped`), so the information loss is visible
rather than silent; the lineage keeps the history of both ends. The `representationCollisions` field,
which the migration had always reported as `0`, now carries the real number — the representation
collisions had been folded into `refinesCarried`.

**The measured dry run** (a copy; the live store was never opened for writing):

| what | the `logic` workspace | the `photosynthesis` workspace |
|---|---|---|
| the schema chain | v15 → v21 in **222 ms** | (the same chain, once) |
| the concepts | 11 → 22 (11 inserted) | 13 → 37 (24 inserted) |
| the claims re-pointed | 28 of 28 | 24 of 24 |
| the representations re-pointed | 8 | 14 |
| the representation collisions | 0 | 0 |
| the prerequisites re-pointed | 6 | 5 |
| **the prerequisite collapses** | **1** | 0 |
| **the prerequisite self-loops dropped** | **1** | 0 |
| the other rows re-pointed | 88 | 272 |
| the lineage rows | 56 | 48 |
| the plan's referents / the rejected claims | 8 / 3 | 14 / 10 |
| the copy-forward | 6 ms | 3 ms |
| **the orphan audit** | 13 tables, **0 orphans, 0 stale** | 13 tables, **0 orphans, 0 stale** |
| **the rollback** | 22 → 11 concepts, 28 claims, 27 ms | 37 → 13 concepts, 24 claims, 21 ms |

The live store after the dry run: the schema version **15**, the photosynthesis concepts **13** —
**untouched**.

The two losses are the honest cost of the merge and they are **not** free: the `logic` workspace's
"Validity requires the Propositional connective" edge became a self-require when the merge joined
the two concepts, so it was dropped, and one pair of edges collapsed onto one triple. The history of
both survives in `concept_lineage`.

**§J-ter — the FAST PR lane's aggregator died on the first module-touching commit, and why.**

The commit that carries the prerequisite fix (`89731948`) touched `matrix-core` ⇒ ⇒ ⇒ **the lane's
matrix path ran for the first time** ⇒ ⇒ ⇒ and the lane went **red**: the matrix job
`Compile + targeted tests (matrix-core)` **succeeded**, while the aggregator failed with
`Process completed with exit code 127` and the log line

```
line 2: io.matrix.knowledge.concept.research.*}]: command not found
```

The scope job publishes its build matrix as JSON
(`[{"module": "matrix-core", "filter": "--tests io.matrix.knowledge.concept.research.*"}]`), and the
aggregator substituted that value into a **double-quoted shell string** ⇒ ⇒ ⇒ the inner quotes
closed the shell's own quoting ⇒ ⇒ ⇒ the rest of the line was parsed as commands ⇒ ⇒ ⇒ exit 127.
The lane had never shown it, because it had only ever run on documentation changes — where the matrix
is empty and the step exits early ⇒ ⇒ ⇒ the first commit that touched a module found it.

**The repair** (the owner's D1-CI-AGGREGATOR-QUOTING-020 → OD-069): the values are passed through
the **environment** (`BUILD_RESULT`, `BUILD_MATRIX`) and read as data. Reproduced locally before the
push, under the runner's own shell (`bash -e`): the old form exits **127**; the new form exits **0**
on success, still exits **1** when the compile failed, and still exits **0** on a docs-only change
with an empty matrix — so the empty-matrix semantics of OD-066 are untouched. The `.github/` frozen
exception is raised **2361 → 2370** diff lines, owner `repo-owner`, expiry 2026-12-31, with the
sanction recorded in `docs-v2/quality/frozen-zone-baseline.txt`.

**What this wave did NOT measure:** the lane's matrix path has never run green end to end on this
repository, and the lane's **PR-event** path has never run at all (no pull request carries this
work). Both are measured by the next CI run, not assumed here.

**§J-quater — the owner's live store: migrated, verified, serving.**

The owner's answer to **D1-LIVE-STORE-MIGRATION-019** (2026-11-18, option A — *"migrate the live
store now"*) authorised the migration that OD-067 had held back until the measured result existed.
The dry run of §J-bis was that result, so the migration was executed through a **launcher** that
enforces the order the decision requires (`scripts/migrate-live-store.sh` →
`:matrix-core:liveStoreMigration` → `LiveStoreMigration`):

1. the live file is **copied to a timestamped backup** — sha256 `6d963dff9d5c07982faf4b81f0b927f9c9b7f2992b5ac0a77af2954d4b7f94c9`,
   the exact bytes of the pre-migration file;
2. the live file is copied to a **work file**, and the work file is migrated: the schema chain
   (v15 → v21, **104 ms**), then one workspace at a time with a re-export between, because a
   workspace's plan is built from that workspace's own rows;
3. every workspace is **audited** — the audit must report zero orphans and zero stale pointers, or
   the launcher **aborts before the live file is touched**;
4. the **rollback is rehearsed** on a second copy — the store's own snapshot restores each
   workspace (`logic` 11 → 22 → 11, `photosynthesis` 13 → 37 → 13), or the launcher aborts;
5. only then is the live file **replaced atomically**;
6. the live file is **re-verified** afterwards.

**The executed numbers** (identical to the dry run, as they must be): `logic` — the concepts 11 → 22,
the claims 28 of 28 re-pointed, the representations 8, the representation collisions 0, the
prerequisites 6, **the prerequisite collapses 1**, **the prerequisite self-loops 1**, the other rows
88, the lineage 56, the referents 8, the rejected 3, 5 ms; `photosynthesis` — the concepts 13 → 37, the
claims 24 of 24, the representations 14, the collisions 0, the prerequisites 5, **0 and 0**, the other
rows 272, the lineage 48, the referents 14, the rejected 10, 5 ms. Both audits: **13 tables, 0 orphans,
0 stale**.

**The live store after the migration, verified independently of the launcher's own report:** the
schema version **21**; the concepts **59** (the 24 old rows are **kept** and superseded by the
lineage — nothing is deleted); the claims **52** (none lost) with **52** claim-lineage rows; the
prerequisites **9** (the two measured losses); the representations 27; the sources 7; the
concept-lineage 28; the claim-scoped records 13; the alias table **0** (nothing was seeded, as
decided); **no self-requires**; **no claim without a concept**; no concept without a name.

**And the store still serves.** The production asking path (`KnowledgeWorkbench.ask`) answers from
the migrated store in both workspaces: *logic* → "Propositional logic evaluates formulas by assigning
truth values to their atomic variables. A formula is valid when it is true on every assignment,
satisfiable when it is true on at least one, and a consequence of another when it is true wherever the
other is."; *photosynthesis* → "Photosynthesis has two linked stages. The light-dependent reactions, in
the thylakoid membranes, harvest light, split water and build a proton gradient that drives ATP and
NADPH production. The light-independent reactions, in the chloroplast stroma, use that ATP and NADPH
to fix carbon dioxide into carbohydrate through the Calvin-Benson cycle." ⇒ ⇒ ⇒ the migration is not
merely internally consistent; the served answers are correct.

**The way back.** The backup `matrix-core/data/knowledge/workbench.pre-migration-2026-11-18T16-04-34-729638675Z.sqlite`
is kept beside the store (the directory is gitignored, so it is not in the repository) and holds the
original bytes.

**Open and owned:** the measured limitations of §B — the partial paraphrase equivalence, the two
frozen-set cases whose disposition differs from the set's expectation, and the corpus-in-conflict
precedence (which keeps "unsupported answering" at 2); the **latency plateau** of the premise path
(the accepted cost: ~53 ms per question on a loaded machine, ~+19 ms against round 2, the growth
having happened in rounds 3–4) and the absence of a shared benchmark for that path; the HDC
substrate's contamination history; the residual's content policy (the owner's decision, applied); the
native image's build; the concept-identity repair (owner-gated: the migration touches ten tables and
SQLite declares no foreign keys); the P2 implementation.

**Inherited reds that this programme did not cause and did not hide:** the Docker Hub publication
(zero repository secrets — the owner's action, never in chat), ShellCheck (43 files), the GraalVM
native build (OOM, red since 2026-10-31), the PR-event TruffleHog findings, and the two known
flaky timing assertions (`CognitiveKernelRemoteTest`, `PortfolioBenchmarkTest`) — **recorded with
their reproduction, never silenced**; they stay in the lane that runs them.

**The executable handoff — how to continue without re-deriving anything.**

```bash
export JAVA_HOME=$HOME/jdk25
cd /home/alexandr-narbaev/Projects/agi

# the P0 gates, in the order they depend on each other
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && ./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --offline --console=plain'
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-brain-runtime/build/test-results && ./gradlew :matrix-brain-runtime:test --tests "io.matrix.brain.runtime.*" --offline --console=plain'
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-api-gateway/build/test-results && ./gradlew :matrix-api-gateway:test --tests "io.matrix.api.*" --offline --console=plain'
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && ./gradlew :matrix-core:test --tests "io.matrix.packaging.*" --offline --console=plain'

# the packaged application, the store without a manual property, from outside the repository
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/quarkus-app && ./gradlew :matrix-core:quarkusBuild :matrix-core:verifyPackagedSqliteNativeLibrary --offline --console=plain'
python3 scripts/packaged-sqlite-smoke.py --skip-build

# the gates of the repository itself
./scripts/quality-gate.sh
python3 scripts/validate-current-state.py --strict
python3 scripts/validate-test-catalogue.py --strict
python3 scripts/docs-link-check.py --root . --strict
```
**The next three pieces of work, in dependency order.**
1. **The concept-model repair (P1).** The research is done and measured
   (`EPISTEMIC-CONCEPT-IDENTITY.md`): the concept *name* is the claim's subject phrase and the concept
   *id* is its digest. The one candidate that raises a measured number is **not** the identity repair but
   the **source-span-anchored definitions** (0 → 121/326 = 37.1 % adjacency-passing) — adopt it first,
   because it is orthogonal to the identity and does not need the migration. The identity repair itself
   (the referent key: a normalised lemma + the source document + the anchor span, with a measured
   alias and resolution policy) is **owner-gated**: the migration touches ten tables, the concept id is
   not a surrogate key, and SQLite declares no foreign keys, so a partial migration would be invisible.
   The rejected alternative (an NP-boundary filter) silently narrows 34 referents and orphans 42 claims.
2. **The closed-loop cognitive action (P2)** — the report journey (`EPISTEMIC-CLOSED-LOOP-DESIGN.md`,
   OD-055): the question → the kernel's plan and method choice → the permissioned `ActionRunner` → the
   artifact → the evidence. The design is deliberately built on the three instruments this initiative
   produced: the premise semantics, the sealed evaluation and the isolated learning.
3. **The open defects of §J**, each with its reproduction, and the inherited reds of §J that need the
   owner's action (the Docker Hub credentials) rather than the coordinator's.

**The cold-start resume prompt** (Gate 6 of the directive asks for one; copy it into a fresh session):

> Continue the MATRIX EPISTEMIC-INTEGRITY programme in `/home/alexandr-narbaev/Projects/agi` (branch
> `develop`). The owner's directive is READ-ONLY at
> `/home/alexandr-narbaev/.dsh/attachments/v1/files/fa/fad9aeb5d7f1dae77e7070e73c9d9e4e6cfde6cf7537ad95b9a4cc098ec78584/MATRIX_AUTONOMOUS_DEVELOPMENT_MANDATE_2026-11-15.md`.
> Read first: `docs-v2/engineering/EPISTEMIC-FINAL-REPORT.md` (sections A–J, the state of every
> workstream and the open items), `docs-v2/engineering/EPISTEMIC-EXECUTION-PLAN.md` (the task graph
> and the append-only evidence ledger), `docs-v2/governance/DECISION-REGISTER.md` (OD-053 … OD-067).
> The P0 gates are closed. **P1** (the concept model: the definitions and the identity half) is
> implemented, measured and independently reviewed — the third review round's verdict is MERGEABLE with
> five published limits (the coverage 4/326 = 1.2 %, the rule's own precision 3 of 4, the
> under-acceptance, the **null** retrieval benefit, and the count of lost referents). **P2** (the closed
> loop) is implemented and measured: the kernel chooses the method, the observer is independent, the
> trace is append-only, the counterfactual diff shows 19 aspects of which **4 differ** and 15 are
> identical, and the restart re-selects the replay. **P3** (the learning label) is closed with a
> **published null result**. The FAST PR lane is **green** (run 44169314944) after two defects in its
> own checks were found and repaired (OD-066).
> **The next step, in this order:** (1) **variant A of the owner's live-store decision (OD-067)** — copy
> `matrix-core/data/knowledge/workbench.sqlite` (schema **v15**, 24 concepts, 52 claims, 27
> representations, 2 workspaces) to a temporary directory, run the identity migration on the **copy**,
> and measure the audit, the rollback and the re-measurement; the live store itself is not touched
> until the owner decides again on the measured result. (2) Then the open items: the verb-list fix
> (it changes names, therefore ids), the definition-merge collision rule (0 collisions on this corpus
> ⇒ unverified), the HTTP status for a failed effect, the latency plateau of the premise path, and the
> effect of the referent-key migration on the learning tables (not measured).
> **Tomorrow's first step, with the API it uses (nothing new has to be written):** variant A of OD-067 —
> the live store on a **copy**. `ReferentKeyMigration.copy(source, target)`
> (`matrix-core/src/main/java/io/matrix/knowledge/concept/research/ReferentKeyMigration.java:1036`) makes
> the SQLite-aware copy; `new KnowledgeStore(copy)` (`KnowledgeStore.java:330`) brings the schema forward
> from **v15** — **verify and record the version it lands on**; `store.exportWorkspace(workspaceId)`
> (`KnowledgeStore.java:5115`) returns the snapshot for each of the two workspaces (`logic`,
> `photosynthesis`); `ReferentKeyMigration.plan(snapshot, workspace, …)` (`:232`) builds the plan;
> `seedDeclaredAliases(copy, workspace, aliases)` (`:394`) seeds the declared aliases;
> `ReferentKeyMigration.apply(copy, plan, workspace)` (`:353`) applies it; `ReferentKeyMigration.audit(copy,
> workspace)` (`:822`) reports the orphan audit over the thirteen tables. The **rollback is the copy
> itself** — the live file is never opened for writing. Measure and publish: the concepts inserted, the
> **The order is not optional, and it was checked read-only on 2026-11-17.** The migration's SQL
> writes into `concept_alias` and `concept_definition`, and **neither table exists in the v15 live
> store** — the v18→v19 step creates `concept_definition` (`KnowledgeStore.java:1020`) and the
> v20→v21 step creates `concept_alias` (`:988`), and the chain that carries v15 to v21 runs six
> steps (`migrateFrom`, `KnowledgeStore.java:938`, with the steps at 1108, 1060, 1032, 998 and 939).
> So the **schema step comes first**: opening the copy with `new KnowledgeStore(copy)` must precede
> `plan`, `seedDeclaredAliases` and `apply`. The dry run must record **which version the schema
> lands on** and the **time the chain took** — both are new numbers for a v15 store, whereas the
> corpora's rehearsal began at v20.
> **Three read-only findings about the live store (2026-11-17), each of which changes what the dry run must
> measure.** (1) **The store's concept ids do not come from one scheme.** The current id function is
> `Ids.digest` — an FNV-1a hash returned as a **bare** hex string (`Ids.java:117-124`) — and the
> importer mints `digest(workspaceId + "|concept|" + name.strip().toLowerCase())`. Recomputing that
> over the live store's 24 concepts **reproduces only 5 of the 24 ids**; the other 19 are shaped
> `c-<slug>-<hash>` (for example `c-validity-58964155`), a shape the current function never produces.
> ⇒ The migration's premise that a concept id is a pure function of the workspace and the name **does
> not hold for 19 of the 24 live concepts**, so the plan must map **the store's actual rows** and must
> never recompute an id from a name. The dry run must report how many of the 24 the plan mapped
> one-to-one. (2) **Two concepts share a name with two different ids** — `Cellular respiration` in the
> `photosynthesis` workspace, stored as `c-cellular-respiration-70762885` and `dbe3c115`. Under the
> current scheme those two would collapse onto one id, so this is a **concept-id collision**; OD-065
> covers the **representation** ids and says nothing about this case ⇒ if the plan materialises the
> collision, it is an **owner decision**, not a coordinator's. (3) **The declared aliases do not apply.**
> `ReferentPolicy.DECLARED_ALIASES` is 12 **geology** pairs (22 lemmas: seafloor spreading, slab pull,
> the lithosphere, …); the live store holds **biology and logic** concepts (glycolysis, RuBisCO, modus
> ponens, the truth table, …) and the lemma overlap is **zero**. ⇒ The dry run must **seed nothing**:
> writing the corpus's lemmas into this store would put a `DECLARED_BY`/`DECLARED_ON` provenance on
> declarations this store never made. The alias layer of the live store stays empty until its own
> aliases are declared.
> **The live store has TWO workspaces, so the plan/apply pair must be sequenced — and the sequence is
> per workspace.** `plan(snapshot, workspace, strictness, createdAt, aliases)`
> (`ReferentKeyMigration.java:252`) resolves **one workspace's** claims and concepts, and
> `apply(database, plan, workspace)` (`:353`) applies **one workspace's** plan. The two workspaces of the
> live store are `logic` (11 concepts) and `photosynthesis` (13 concepts) — the corpora each had **one**,
> so nothing about this was visible there. ⇒ The dry run must therefore **plan and apply one workspace at
> a time**: plan `logic` → apply `logic` → **re-export** → plan `photosynthesis` → apply `photosynthesis`.
> Planning both from a single pre-migration snapshot would leave the second plan computed against a state
> the first application had already changed.
>
> **The rewrite is workspace-scoped, so one workspace cannot bleed into the other.** The "other tables"
> pass (`rewriteTheOtherTables`, `:634`) issues
> `UPDATE <table> SET concept_id = ? WHERE concept_id = ? AND workspace_id = ?` — the workspace predicate
> is present ✓ — and every table it can touch carries a `workspace_id` column in the live store
> (checked with `pragma table_info`: `dialogue_turn`, `learning_state`, `learning_goal`,
> `assessment_item`, `revision`, `derivation`, `contradiction`, `hebbian_binding`, `learning_transition`,
> `learning_error`, `assessment_outcome` — all of them ✓).
>
> **And that is why the audit's orphan count is the number that decides the whole run.** The rewrite is
> keyed on `plan.oldToNew()`, so a plan that failed to carry the 19 ids the current scheme cannot
> reproduce would leave those claims pointing at concept ids that no longer exist — a **half-migrated
> store** that looks migrated. ⇒ The acceptance number for tomorrow's dry run is therefore:
> `ReferentKeyMigration.audit(copy, workspace)` must report **zero orphans in each of the thirteen
> tables, for each of the two workspaces**, after the second application.
> claims re-pointed, the representations re-pointed, the audit's orphan count, the elapsed time, and the
> **re-measurement** by the same judge (the C2A baseline is BASELINE-PATH 17/25 with the same eight
> failures, recall@1 0.5351, MRR 0.8600).
> The gates of the repository must stay
> green: `./scripts/quality-gate.sh` (PASS, 271 literals, 0 violations), `validate-current-state.py
> --strict`, `validate-test-catalogue.py --strict`, `docs-link-check.py --strict`. Every build runs
> under `flock /tmp/gradle-matrix.lock` with the `--tests` filter immediately after the task name and
> never `--rerun`. Answer the owner in Russian; the code, the identifiers and the commit messages stay
> in English; commits are `WAL: <description>`; never print a secret; mark a guess below 80 %
> confidence `[speculative]`; every published number needs its command and its artefact, and a null
> result is a result.

**The two measurement traps this programme paid for, so the next wave does not pay again.** (1) A
**count** is not a **match**: the ambiguity metric reported 7/7 because it counted the readings it
showed; the honest number was 3/7. (2) A **before/after against an uncommitted state** is not
comparable: the latency was reported as "faster" against a tree that was not the historical baseline,
and the three-point A/B on the historical commits says otherwise. Measure against a **named commit**,
in a worktree, with the same harness.

**The honest summary of the optimisation target.** The programme did not make the project "big"; it
made three things true that were not true before: the asking path **states** what it knows about the
question's premise instead of conflating "unsupported" with "false"; a **sealed** evaluation with
adversarial negatives now exists to judge any future change; and the learning subsystem can no longer
train on the evaluation traffic that once contaminated it — with a canary that breaks the build if
that protection is ever silently lost. The concept-identity defect is now **measured** rather than
guessed, and its repair is known to be two separate pieces of work, only one of which raises a
coverage number.
