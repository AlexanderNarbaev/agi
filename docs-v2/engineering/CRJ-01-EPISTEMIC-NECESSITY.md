# CRJ-01 / EPISTEMIC-NECESSITY — what a task actually needs

**Baseline:** `5402e8763579b7c785f91f9e03910403e36015be` (branch `dsh-writer`) · **Date:** 2026-11-28
**Pre-registration:** `docs-v2/engineering/CRJ-01-NECESSITY-PREREGISTRATION.md` (written **before**
the first measurement) · **Case contract:** `docs-v2/engineering/CRJ-01-NECESSITY-CASE-CONTRACT.md`
· **Case set:** `CRJ-01-NECESSITY-DEV-CASES.json` (24 cases, **six independent authors**) ·
**Evidence:** `docs-v2/engineering/evidence/crj01-necessity/` ·
**Independent review:** `docs-v2/engineering/evidence/crj01-necessity/REVIEW.md`, and the
implementer's responses to it in `docs-v2/engineering/evidence/crj01-necessity/REVIEW-RESOLUTIONS.md`
(§10)

> **This milestone is DEV.** The corpus is synthetic and test-owned, the measurement was made on
> a corpus the system itself imported through the production acquisition path, and **the sealed
> evaluation was NOT EXECUTED** — the isolation a sealed split needs was not established from
> this executor, and the prior milestone's retained verdict on isolation is reused as it stands
> and **not re-probed**. Nothing here is a claim about the system's behaviour on real data.

---

## 1. The disposition, in one paragraph

The milestone **answered** the question it preregistered and **settled none of the decisions**
that question opens. The prior milestone compared two rules about the **reading** — the anchor
asks whether the scope holds the claim the reading cites first, the competitor asks whether it
holds every claim the reading cites. This milestone compares the incumbent against a rule that
asks about the **task**: does the scope carry a sufficient set of the evidence the task cannot
be done without, where that evidence is **derived experimentally** by leave-one-source-out
ablation of the workspace's own reading. On the 24 independent cases of two synthetic domains
the incumbent **admitted 19 of its 72 measured decisions on scopes the oracle calls inadequate,
and refused none of the 29 it calls adequate** (FPR `19/43 = 0.4419`, FNR `0/29 = 0.0`); the
proposal **admitted 9 and refused 5** (FPR `9/43 = 0.2093`, FNR `5/29 = 0.1724`). The proposal therefore **trades false admissions for
false rejections** on this corpus, with **separate denominators**, and **no winner is declared**:
the pre-registration fixes the comparison, not the outcome. What the milestone also delivers is
the **mechanism** the prior milestone lacked — a missing prerequisite becomes a **named
requirement**, a **bounded typed request** with an authorised channel, and a **durable**
retirement that survives a restart, keeps its reason and its causal link, and does not close
the door on a revalidation.

**The one finding that changes how the numbers must be read** is in §4.2: after the oracle's
"carries" was corrected to mean what the served path means (§10, finding 3), **every one of the
incumbent's 19 false admissions sits on a task whose necessary evidence the workspace's own
reader does not reach**. The incumbent's error on this corpus is therefore not "it admitted a
scope that could have answered" — it is "**it admitted a scope for a task the workspace cannot
do at all**", and no rule that does not derive the task's requirements can see the difference.

## 2. The independent authorship — the gate that failed last time

The prior milestone's gate **FAILED**: two commissioned case authors were given the corpus and
the oracle contract and **neither delivered**. This milestone commissioned **six** separate
bounded assignments of **four** questions each, and **all six delivered**:

| assignment | domain | conditions | slice mix |
|---|---|---|---|
| `A1` | `wbe` | lower-ranked · jointly-necessary · alternative-sufficient · missing-prerequisite | 1 proposal, 1 validation, 2 evaluation |
| `A2` | `wbe` | contradiction · supersession · changed-bindings · ambiguity | 1 / 1 / 2 |
| `A3` | `hpd` | lower-ranked · jointly-necessary · alternative-sufficient · missing-prerequisite | 1 / 1 / 2 |
| `A4` | `hpd` | contradiction · supersession · changed-bindings · unanswerable | 1 / 1 / 2 |
| `A5` | `wbe` | lower-ranked · jointly-necessary · alternative-sufficient · changed-bindings | 4 validation (the follow-up round) |
| `A6` | `hpd` | lower-ranked · jointly-necessary · alternative-sufficient · changed-bindings | 4 validation (the follow-up round) |

**What each author was given:** the case contract and the corpus of its own domain. **What each
author was not given:** the implementation, the instruments, the previous case set, the
pre-registration, the governance documents, and the other authors' files. Each file records its
own access in its `independence` field, and the case set records the **SHA-256 of every
author's bytes**.

**Three of the six authors independently diagnosed defects in the checker I had written** — the
corpus splitter used `str.split` where the production extractor uses a regex, and a slice rule
that was unsatisfiable for one assignment — and reported them without touching the checker.
That is evidence that the authorship was real and not a transcription.

**The follow-up round, and why it was needed.** The production promotion rule
(`ProcedureValidator.MINIMUM_VALIDATION_CASES = 5`) decides a promotion on at least **five**
held-out cases, and the first round produced **two** per domain: no competence could be
promoted at all. A second, separate round (`A5`, `A6`) was therefore commissioned under the same
contract — **four `VALIDATION` cases each**, no implementation, no rules, no pre-registration.
The whole-set rule was then checked by the milestone, and the collisions it found were
**returned to their authors**, who replaced the offending cases (§10, finding 4).

**The whole-set rule needed three rounds, and the reason is worth recording.** The rule of
contract §12 is that a case's necessary sentence must not be a sentence **another case of the
same domain** already uses. The first round of the check found **two** collisions — one between
`A1` and `A5` and one between `A1` and `A2` — and both were **returned to the authors**, who
replaced the offending sentences. The **second** round found that the two revisions, made in
parallel by authors who could not see each other's files, had **arrived at the same sentence**,
and that a third author's new sentence had landed on a fourth author's. The third round fixed
it by **assigning each author a specific sentence** rather than letting them choose — the
minimal disclosure the milestone's half of the rule permits: the author is told the sentence
to use, not the other authors' files. **No author file was edited by the implementer.** The
final set has **zero** whole-domain duplicates of a necessary sentence, and the rule is now
checked by **the gate** — the milestone's half — over every assignment of a domain, not only
within one file.

**One discrepancy, resolved against the brief.** The `A3` brief named `AMBIGUITY` as its fourth
condition and the checker named `MISSING_PREREQUISITE`. The **gate won**: `A3` carries
`MISSING_PREREQUISITE` and `A2` carries `AMBIGUITY`, so all nine conditions are covered. **No
author file was edited by the implementer.**

## 3. What was derived, and how — the mechanism

The production derivation (`io.matrix.knowledge.TaskPrerequisiteDerivation`) answers
*what does this task need* **experimentally**, by **leave-one-source-out ablation of the
workspace's own reading**:

1. the **reference answer** is the workspace's own reading of the task over the whole view;
2. `P` is the set of propositions that answer states, and `D` the sources that hold a claim
   stating one of them;
3. `S*` is the **inclusion-minimal** subset of `D` that still reproduces `P`, computed by
   dropping, one source at a time in a fixed order, every source whose removal leaves `P`
   intact;
4. a claim of the reference answer is a **requirement** iff its source is in `S*` **and** no
   other kept source states its proposition — that is, iff the source cannot be dropped;
5. a requirement whose proposition is **also** stated by a claim of another source that can
   carry it alone yields an **alternative sufficient set**.

**What the derivation does not read:** a word of the question, a concept name, a domain, a case
identifier, a confidence number, a coverage threshold, an oracle label or a gold. The only
inputs are the workspace as it is and the question's own text; the only numbers are the
retriever's own.

**What is kept apart, and where.** `EvidenceRequirement` carries the claim, its proposition,
**its provenance**, its standing and its reason — and it judges none of them. **Relevance** is
not in the record: a prerequisite is not a relevance score. **Confidence** is not in the record:
a claim's authored confidence says how sure the source was, not whether the task needs the
claim. **Provenance** is carried, never judged: it decides *carrying* (does the scope hold the
source?), and it never decides *necessity*. **Applicability** is the route's decision — whether
*this* request may be answered by *this* scope — and this milestone does not change it; where
the word is used below it always means the route's decision.

**The incumbent's behaviour and its cost are unchanged.** The derivation is paid for only when
the rule that needs it is the rule that was asked for (`ScopeCriterion.needsPrerequisites()`).

## 4. The paired measurement

**Identical inputs.** The snapshot is captured **once** per cell, and **each arm takes its own
snapshot of the workspace** and is fingerprinted from what it actually read; the gate requires
the arms of a cell to carry **one** fingerprint. The first version copied one fingerprint into
every row, so the check could not fail — the independent review found it (§10, finding 2).

**The oracle.** `oracle_adequate(case, scope)` is true when the case is answerable **and** the
scope carries the necessary sentences — where "carries" means **the view holds a claim that
states the sentence and that claim's source is in the scope**. Both halves are needed: a scope
that holds the source but whose claim the view no longer carries — because a source revision
took it back — cannot cite it, so it does not carry the sentence. The first version measured the
scope's **source ids alone**, and eight retained rows under `SUPERSESSION` then contradicted
themselves: the oracle called the scope adequate and the served answer did not cite the
evidence (§10, finding 3).

**What the oracle is, precisely.** The oracle is a property of the **(case, scope) pair** and
of the workspace: it is computed from the case's own necessary sentences, the scope's source ids
and the view's claims — **never from an arm's outcome**, so it cannot be read as "the arm was
wrong, therefore the oracle says so". The instrument computes it before the arms of its cell
run; the first version of this document said "after each arm has served", which was false about
the instrument's order and is corrected here. **No oracle label, case id or gold reaches
production code**.

### 4.1 The confusion matrix, with every denominator

Over the whole grid — 2 domains × 3 conditions × 3 scope strategies × 4 evaluation cases × 2
rules = **144 rows**, 72 per rule:

| | oracle ADEQUATE | oracle INADEQUATE |
|---|---|---|
| **ADMIT** | TP = 29 / 24 | FP = 19 / 9 |
| **REFUSE** | FN = 0 / 5 | TN = 24 / 34 |

| rule | TP | FP | TN | FN | **FPR** = FP/(FP+TN) | **FNR** = FN/(FN+TP) | **FP/(TP+FP)** | **FN/(TN+FN)** |
|---|---|---|---|---|---|---|---|---|
| `ANCHOR` (incumbent) | 29 | 19 | 24 | 0 | `19/43 = 0.4419` | `0/29 = 0.0` | `19/48 = 0.3958` | `0/24 = 0.0` |
| `NECESSARY_PREREQUISITE` (proposal) | 24 | 9 | 34 | 5 | `9/43 = 0.2093` | `5/29 = 0.1724` | `9/33 = 0.2727` | `5/39 = 0.1282` |

**No denominator is empty in this grid**, so no rate is undefined here; the convention
(`UNDEFINED (0 denominator)`) is stated in the pre-registration and is applied **per case
condition**, where it does bite:

| rule | case condition | TP | FP | TN | FN | FPR | FNR |
|---|---|---|---|---|---|---|---|
| `ANCHOR` | `ALTERNATIVE_SUFFICIENT` | 7 | 1 | 1 | 0 | `1/2 = 0.500` | `0/7 = 0.000` |
| `ANCHOR` | `CHANGED_BINDINGS` | 8 | 0 | 10 | 0 | `0/10 = 0.000` | `0/8 = 0.000` |
| `ANCHOR` | `JOINTLY_NECESSARY` | 0 | 9 | 0 | 0 | `9/9 = 1.000` | **UNDEFINED (0 denominator)** |
| `ANCHOR` | `MISSING_PREREQUISITE` | 0 | 9 | 9 | 0 | `9/18 = 0.500` | **UNDEFINED (0 denominator)** |
| `ANCHOR` | `SUPERSESSION` | 14 | 0 | 4 | 0 | `0/4 = 0.000` | `0/14 = 0.000` |
| proposal | `ALTERNATIVE_SUFFICIENT` | 5 | 0 | 2 | 2 | `0/2 = 0.000` | `2/7 = 0.286` |
| proposal | `CHANGED_BINDINGS` | 5 | 0 | 10 | 3 | `0/10 = 0.000` | `3/8 = 0.375` |
| proposal | `JOINTLY_NECESSARY` | 0 | 0 | 9 | 0 | `0/9 = 0.000` | **UNDEFINED (0 denominator)** |
| proposal | `MISSING_PREREQUISITE` | 0 | 9 | 9 | 0 | `9/18 = 0.500` | **UNDEFINED (0 denominator)** |
| proposal | `SUPERSESSION` | 14 | 0 | 4 | 0 | `0/4 = 0.000` | `0/14 = 0.000` |

**The evaluation slice covers 5 of the 9 conditions** — `ALTERNATIVE_SUFFICIENT`,
`CHANGED_BINDINGS`, `JOINTLY_NECESSARY`, `MISSING_PREREQUISITE`, `SUPERSESSION`. The other
four (`LOWER_RANKED_NECESSARY`, `CONTRADICTION`, `AMBIGUITY`, `UNANSWERABLE`) are held-out or
learning cases by the contract's slice rule and are **not** in the measured grid.

### 4.2 What the differences are, and what they mean

**15 cells** of the grid carry different verdicts: in **10** of them the incumbent admitted a
scope the oracle calls **inadequate** and the proposal refused it; in **5** of them the
incumbent admitted a scope the oracle calls **adequate** and the proposal refused it. There is
**no** cell in which the proposal admitted what the incumbent refused.

The shape of the trade is legible in the conditions:

- the incumbent's **false admissions are concentrated** in `JOINTLY_NECESSARY` (9 of 9 — the
  scope holds one of two jointly necessary sentences and the anchor cannot see the other) and
  `MISSING_PREREQUISITE` (9 of 18 — the workspace cannot carry the whole answer at all, and the
  anchor admits the scope anyway);
- the proposal's **false rejections are concentrated** in `CHANGED_BINDINGS` (3 of 8) and
  `ALTERNATIVE_SUFFICIENT` (2 of 7) — the cases where the reading reaches a sentence the task
  does not need, and the ablation keeps it as a requirement because the *reading* depends on
  it.

**AND THE QUALIFIER THAT DECIDES HOW THE FIRST NUMBER MUST BE READ.** The reachability of a
case's gold is measured on the **reference reading** and carried by every row
(`goldReachable`, `unreachablePropositions`). **All 19 of the incumbent's false admissions sit
on rows whose gold is unreachable** — on tasks the workspace's own reader never reaches the
necessary sentence of. So the incumbent's false admission on this corpus is not "it admitted a
scope that could have answered"; it is "**it admitted a scope for a task the workspace cannot
do at all**", and the rule that admits it — "does the scope hold the claim the reading cites
first" — cannot tell the two apart. That is the argument for the proposal, and it is an
argument about **visibility**, not about a better answer.

**This is a trade, and the pre-registration forbids calling it a win.** The incumbent never
refuses a scope the oracle calls adequate on this corpus, and the proposal refuses five of them.
A reader who wants the smaller false-admission rate must accept the larger false-rejection
rate; the milestone states both, with their denominators, and changes **nothing** about the
served default.

## 5. The journey on the served route

Walked on the real route (`ArtifactReportResource.journey`), on a task the measurement
selected (not chosen by the test):

1. **the observable failure** — under the incumbent the served answer does **not** carry the
   whole evidence the task needs, and the answer it serves carries no more of that evidence
   than the reference does;
2. **the diagnosis** — under the proposal the same scope is **refused**, the derivation names
   the requirements, and the refusal names the claim ids it does not carry;
3. **the request** — a **bounded, typed** `EvidenceAcquisitionRequest`: the kind (`EVIDENCE`),
   the missing requirements with their propositions, the **authorised channel**
   (`learning.scope_revision`), the **allow-list** of exactly that one channel, and a sentence a
   reader can act on. The route writes it into the journey's own record;
4. **the fulfilment** — the wider scope is **learned and validated** through the production
   learner, and the evidence offered through the authorised channel is accepted;
5. **the disposition change** — the **same task**, under the wider scope, is now **admitted** by
   the proposal, and the served answer cites the evidence the narrow scope could not carry;
6. **the refusals** — four of them, each on **real evidence produced by the production path**:
   an offer through a channel the request does not authorise is **REFUSED_UNAUTHORIZED**; an
   offer of a claim the workspace does not hold is **REFUSED_UNVERIFIED**; an offer of a claim
   the workspace has **taken back** (produced by the production `acceptRefinement`) is
   **REFUSED_STALE**; an offer that contradicts an established claim (produced by the production
   import of a document that states an account and its negation) is **REFUSED_CONFLICTING**,
   decided by the **production** contradiction detector and the production claim-conflict
   detector, not by a comparison written in this milestone;
7. **the revision** — the predecessor is **retired durably**, with its reason and the causal
   link to its successor.

### 5.1 The mechanisms this path does NOT have — reported, not papered over

- **The corpus's own contradiction is invisible to the production detectors.** The two
  rain-drop sentences contradict each other, but they differ in more than the negation
  ("changes" against "does not change"), so the production contradiction detector does not see
  them as a contradiction. The suite **asserts this as a finding** and exercises the refusal
  branch with a pair in the detector's own shape. A contradiction that the detectors cannot see
  is a contradiction the workspace cannot adjudicate.
- **A case's gold may be unreachable.** Four of the eight evaluation cases have necessary
  sentences the workspace's own reading does **not** reach, and **every** false admission of
  the incumbent is on such a case. The reachability is **measured and retained** per row
  (`goldReachable`, `unreachablePropositions`) rather than assumed away.
- **The promotion rule constrains the validation slice.** One independent held-out question per
  domain reaches a document the declared scope leaves out, and the production promotion rule
  then refuses **every** scope. The milestone validates on the **confined** held-out subset,
  measures how many questions reach outside, and **uses** the unconfined one as the journey's
  unseen task.
- **The exact replay refuses what it has not recorded.** The replay branch answers a task it
  has a record of and **refuses** (422) a task it has not; the milestone reports the outcome it
  measured rather than the one it wished for.
- **No network, no new endpoint, no new permission, no model call.** The acquisition path
  reuses the existing `learning` and `receipt.import` channels; the deterministic runtime
  makes no model call.

### 5.2 Two defects the milestone's own runs found — one in production code, one in the environment

**IN PRODUCTION CODE.**

`ContradictionDetector.shortHash` took the first eight characters of `Ids.digest(...)`, and
`Ids.digest` prints its hash **without padding** — so a hash whose top nibble is zero is six
characters long and the substring **throws**. The defect was latent and would have surfaced on
any pair of claims whose identifiers happened to hash that way. The milestone's test found it
and the production code now takes the prefix safely.

**IN THE ENVIRONMENT.** The shell of this executor carries `GIT_CONFIG_COUNT=10` with an **empty**
`GIT_CONFIG_KEY_0`, and every `git` call that reads a configuration fails with
`missing config key GIT_CONFIG_KEY_0` and exit code **128**. The repository's own
`Crj01FreezeRecordTest` resolves the repository's HEAD by shelling out to `git rev-parse HEAD`,
so **that test fails in this environment for a reason that has nothing to do with this
milestone** — and it passes the moment the variable is unset (`env -u GIT_CONFIG_COUNT …`).
The milestone reports it because a reader who runs the suite here will see it, and because
"the suite is red" must never be confused with "the milestone broke something". The same
environment defect rewrites the **prior milestones'** retained evidence whenever their suites
run, and those files were **restored byte-for-byte** from `HEAD` after the final runs.

## 6. The durable lifecycle (P3)

Inside the canonical store's own contracts, with **no new table, no new registry and no frozen
surface touched**:

| transition | how | status |
|---|---|---|
| **durable invalidation** | `CognitiveStore.retireProcedure(...)` writes the instant, the reason and the causal link into the version's **own row** with **one conditional `UPDATE`** (`AND invalidated_at IS NULL`), so two racing callers produce one retirement and one refusal. The **definition** is untouched, so the immutable record is still immutable. | **BUILT** |
| **the retirement survives a restart** | the row carries the standing; the suite closes the store, reopens it and reads the version back retired | **BUILT** |
| **prevention of stale selection on restart** | the route offers only `procedure.active()` versions; after the restart the retired competence has **no** active version, and forcing it is **refused** | **BUILT** |
| **retirement retaining immutable historical evidence** | the retired version stays **readable** with its definition, its counters and its promotion instant; the suite asserts the definition is unchanged | **BUILT** |
| **validated narrowing/replacement** | the replacement is learned and **validated** through the production learner against the held-out slice before it is written | **BUILT** |
| **revalidation** | `ReportProcedureLearner.learn(..., revalidates)` writes a **new version** that names the version it revalidates; the retired version stays retired, and the new one is active. The version is **derived** from the version being revalidated, so a second revalidation does not collide with the first | **BUILT** |
| **migration compatibility** | the two new columns are added through the store's own additive `addColumnIfMissing` path; a row written before this milestone keeps `null` in both, which is the truth about it | **BUILT** |
| **concurrency safety** | the retirement is a single conditional statement inside the store's `synchronized` boundary; the suite asserts the second retirement is refused | **BUILT** |

**The prior milestone falsified the durable retire and the durable narrow.** The durable retire
is now **inside the existing contracts and working**.

**AND THE LIMIT THAT REMAINS — WHAT IS EXERCISED AND WHAT IS NOT.** The suite exercises a
**separately validated replacement**: a **wider** competence is learned, **validated** against
the held-out slice, and only then written; the predecessor is retired with a reason and a
causal link, and the survivor is the new version. What the suite does **NOT** exercise, and
what the store does **NOT** implement, is an **atomic durable narrowing of the same scope** —
there is no operation that takes a promoted competence, narrows its support set in place, and
writes that narrowing atomically and durably under the same version. The two halves are
**separate writes**: a new version is written and validated, and the old one is retired
afterwards. A crash between them leaves **both** versions standing — the retired one is no
longer active, but the narrowing is not one transaction, and the milestone says so rather
than calling the pair an atomic narrowing.

## 7. The RED reproduction

`CRJ-01-NECESSITY-REPRODUCTION-no-prerequisite.xml` is the run of
`io.matrix.knowledge.EpistemicNecessityRedReproduction` with the **derivation switched off**.
Its assertion — *the proposal refuses the scope and names what is missing* — **fails**, and the
failure says why:

> without the derivation the rule refuses the scope (the reason it gives is: the task's
> necessary evidence was NOT derived, so this rule could not decide and the incumbent's verdict
> stands: the support set carries the workspace's own evidence for this question: the
> workspace's reading would cite clm-8822d26b (coverage 0.558) and the support set holds that
> claim and reaches 0.558)

**What makes the reproduction meaningful**, and what the first version got wrong (it failed for
any reason, §10, finding 1):

- the reproduction **finds the question and the scope on which the two rules disagree** — the
  incumbent admits, the rule refuses — and it finds them by **asking both rules**; no oracle
  label, no case id and no gold is read to do it;
- it then assesses **the same question and the same scope** with the derivation **on** and
  **off**, so the derivation is the **only** difference between the two assessments, and it
  asserts the "on" verdict first;
- the switch is **package-private**, **off in every normal run**, and enabled only by the
  reproduction.

## 8. What is NOT claimed

- **No winner.** The pre-registration fixes the comparison, not the outcome. The proposal has
  the smaller false-admission rate and the larger false-rejection rate, and **the served default
  is unchanged** — the route still applies `ANCHOR` unless a caller says otherwise.
- **No threshold, no new cost grid, no universal rule.**
- **No claim about real data.** The corpus is synthetic and test-owned.
- **No claim of generalisation or superiority.** The user-observable outcome of each run is
  reported in the rows; a transfer claim is a different claim.
- **No sealed evaluation, no new isolation probe.**
- **No architectural change.** Everything is inside the existing contracts; where the prior
  milestone found a transition unsupported, this milestone either repairs it inside the
  contracts or leaves the finding standing.

## 9. The next science decision

The measurement leaves **two** questions open, and the first one is the one the independent
review named:

> **The oracle's "carries" had to be corrected before the numbers could be read at all** — and
> the correction changed them. The next decision is therefore not about the rule: it is
> **whether the oracle's notion of "adequate" is the right one**, and the falsifier is
> concrete: build a corpus in which a scope holds the source of a necessary sentence **and the
> workspace has taken the claim back**, and check whether the oracle still calls the scope
> adequate. If it does, the oracle over-counts adequacy, and every rate that depends on it is
> wrong in the same direction.

> **The proposal's false rejections come from the ablation keeping the *reading's* dependencies
> as requirements.** A task may need a claim the reading reaches only *incidentally*. The
> ablation cannot tell an incidental dependency from a necessary one — it sees only what the
> reading would lose.

The falsifier: build a corpus in which a task's answer is **unchanged** when a second claim is
removed, and check whether the ablation still keeps that claim as a requirement. If it does, the
ablation over-approximates the requirements, and the falsifier is met. The alternative — and the
one the milestone recommends — is to **derive the requirements from the ANSWER's own structure**
(the propositions the answer states, and what each of them is used for) rather than from the
reading's reach, and to measure the two derivations side by side on the same cases.

## 9a. The coordinator's acceptance — three boundary corrections

The coordinator's final acceptance found **three** boundary issues in the milestone's own
artefacts. All three are corrected here, and the corrections are **bounded**: they touch the
milestone's own files and nothing else.

### 9a.1 The resource's shared mutable state is removed

`ArtifactReportResource` kept a **field** — `lastOfferedMethods` — holding the **last**
request's offered method objects, workspace methods with their per-request state inside
them. The resource is long-lived and the methods are not, so that field was a **channel
between requests**: one journey could read another's methods, and one journey's typed
acquisition metadata could be handed to the next. It is **removed**.

The typed acquisition metadata now travels with the **journey that produced it**: the journey
collects it while it runs, in a **request-local** collector, and carries it in its
`JourneyResult` as an **immutable** list of **immutable** records. The existing
`JourneyResult(response, outcome)` constructor is kept, so the current call sites compile and
behave as before. The suite now asserts the absence of the bleed directly: two journeys of
**different** questions over the same resource, each carrying only its own request, and the
resource offering no `lastOfferedMethods` at all.

### 9a.2 The mutable static RED switch is removed

The derivation was switched off for the RED reproduction by a **mutable static** in production
code — `ScopeCriterion.derivationEnabled` — that any caller could move and that the served
path read. It is **removed**. The derivation is now an **immutable input of the workbench**:
`KnowledgeWorkbench` carries a final flag, every workbench the product builds **derives**, and
the factory that builds one that does **not** is **package-private**, so only the reproduction
can ask for the negative control. **The served behaviour is unchanged**, and the reproduction
still fails at the same assertion for the same reason (§7).

### 9a.3 What is exercised, and what is not

See §6: a **separately validated replacement** is exercised; an **atomic durable same-scope
narrowing is not implemented**, and the milestone says so.

An independent DSH reviewer read the whole milestone against its own evidence and wrote
`docs-v2/engineering/evidence/crj01-necessity/REVIEW.md`: **0 BLOCKER, 9 MAJOR, 8 MINOR,
4 NOTE**. Every MAJOR finding is resolved in the code and in this document; the resolutions are
listed in §10.1, and the implementer's responses are in
`docs-v2/engineering/evidence/crj01-necessity/REVIEW-RESOLUTIONS.md`.

### 10.1 The MAJOR findings and their resolutions

| # | finding | resolution |
|---|---|---|
| 1 | the RED reproduction failed with the derivation on as well as off | the reproduction now **finds the question and scope on which the two rules disagree**, asserts the "on" verdict first, and only then the "off" verdict — §7 |
| 2 | the "identical inputs" gate was tautological | each arm now takes **its own snapshot** and is fingerprinted from what it read; the gate's one-fingerprint check can now fail — §4 |
| 3 | under `SUPERSESSION` the oracle's "carries" diverged from the served path's; eight rows contradicted themselves | "carries" now means **the view holds the claim and the scope holds its source**; the numbers were re-measured and re-issued — §4, §4.2 |
| 4 | the follow-up round's uniqueness rule was violated and the checker had a no-op where the check belonged | the dead check is **removed**, the **whole-domain** rule is checked by the gate, and the collisions were **returned to their authors** — §2 |
| 5 | the case set's provenance recorded four authors where there are six | the assembler and the suite now say **six** — §2 |
| 6 | an offer could be counted as fulfilled for evidence the workspace does not hold | a new disposition **`REFUSED_UNVERIFIED`** refuses an offer of a claim the workspace does not hold against a request for evidence it does hold — §5 |
| 7 | the test named "the exact REVISED procedure" never revised anything | it now **promotes, revises, retires, restarts and serves the revised version**, and the retired one is refused — §5, §6 |
| 8 | the journey's failure case had an unreachable gold, while §5 and §5.1 said the opposite | the journey now takes the measurement's **false admission** and **measures** the reference's reach instead of asserting it; the milestone states the finding — §4.2, §5.1 |
| 9 | `goldReachable` did not mean what it said | reachability is measured on the **reference reading**, not on the served answer — §4, §4.2 |

### 10.2 The MINOR findings, the NOTE findings, and two limits of this evidence

The **MINOR** findings were resolved in the code or in this document: the gate's rate check now
**re-derives** every numerator and denominator from the rows instead of asking whether the
denominator was at least the numerator; the gate now **verifies the manifest** against the
files' own digests and **gates the RED reproduction** (it must be present **and** it must
fail); `recordRevalidated` **derives** its version from the version it revalidates, so a second
revalidation does not collide with the first; `lastAcquisitionRequest()` is cleared by a
successful run, so it always answers for the **last refusal**; "applicability" is disambiguated
in §3; §4.1 states that the evaluation slice covers **5 of the 9** conditions; §1 and §4 state
the counts as **measured decisions**, not as scopes; and the oracle's order is stated as it is.

Two **NOTE** findings are limits of this evidence and are stated rather than papered over:

- **The authors' files are revisable.** The case set records the SHA-256 of every author's
  bytes, so a later edit is **visible**; but a digest cannot prove that an edit did not happen,
  and the milestone cannot prove authorship after the fact. What it can show is that **no author
  file was edited by the implementer** — the revisions in §2 were made by the authors
  themselves, and the collisions were found by the milestone's own whole-set check and
  returned.
- **The review found a mutable static switch for the RED reproduction.** The final acceptance change removed it in favor of an immutable derivation per workbench (§9a.2).

### 10.2 The coordinator's acceptance, and what it changed

| finding | resolution |
|---|---|
| `ArtifactReportResource.lastOfferedMethods` is shared mutable last-request state carrying workspace method objects and typed acquisition request metadata | **removed**; the metadata is **request-local**, collected during the journey and carried in the `JourneyResult` as an immutable list; the two-argument constructor is kept for the current call sites; the suite asserts the absence of the bleed between two journeys of different questions — §9a.1 |
| the mutable static RED switch in production code | **removed**; the derivation is an **immutable input of the workbench**, the negative control's factory is package-private, and the served behaviour is unchanged — §9a.2 |
| a separately validated replacement is exercised, but atomic durable same-scope narrowing is not implemented | **stated**: the suite exercises a **separately validated replacement** — a wider competence learned and validated, the predecessor retired afterwards — and the store implements **no** atomic durable narrowing of the same scope; the two are separate writes — §6, §9a.3 |

## 11. The commands

```bash
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home

python3 scripts/necessity-case-check.py docs-v2/engineering/evidence/crj01-necessity/case-authors/A*.json
python3 scripts/necessity-cases.py
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.knowledge.EpistemicNecessityServedTest"
./scripts/necessity-retain-evidence.sh green
CRJ01_NECESSITY_RED=true flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.knowledge.EpistemicNecessityRedReproduction"
cp matrix-core/build/test-results/test/TEST-io.matrix.knowledge.EpistemicNecessityRedReproduction.xml \
  docs-v2/engineering/evidence/crj01-necessity/CRJ-01-NECESSITY-REPRODUCTION-no-prerequisite.xml
python3 scripts/necessity-gate.py
```

> **`--rerun` is mandatory.** The case set and the corpus are not declared inputs of the test
> task, so Gradle considers the task up to date and keeps the **previous** XML. This happened
> twice in the previous milestone; every retained XML here was produced with `--rerun`.

> **`GIT_CONFIG_COUNT` must be unset for the repository's own freeze-record test.** See §5.2.
> Every command above that touches `git` is run as `env -u GIT_CONFIG_COUNT git …`.

## 12. Case isolation

- Every workspace of the grid is its **own** SQLite file under the test's `@TempDir`, named
  `crj01-necessity-<domain>-<condition>`; no case reads another case's workspace.
- The case set is read from `docs-v2/engineering/CRJ-01-NECESSITY-DEV-CASES.json`, and the
  environment override `CRJ01_NECESSITY_CASES` exists so that the machinery can be exercised
  against a throwaway case set **without** touching the authors' file.
- The action's output root is a fresh `@TempDir` with `rwx------` permissions; the action's
  own gate is closed unless the process is local-only, and the suite runs it that way.
- The RED reproduction writes only under a `Files.createTempDirectory` of its own and deletes
  it afterwards.
- The corpus is imported through the **production** acquisition path
  (`KnowledgeWorkbench.importDocument`), so the claims the measurement reads are the claims the
  product would have.
