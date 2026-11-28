# CRJ-01 / EPISTEMIC-NECESSITY — the retained evidence

**Milestone:** `docs-v2/engineering/CRJ-01-EPISTEMIC-NECESSITY.md` ·
**Pre-registration:** `docs-v2/engineering/CRJ-01-NECESSITY-PREREGISTRATION.md` ·
**Case contract:** `docs-v2/engineering/CRJ-01-NECESSITY-CASE-CONTRACT.md`

> **This evidence is DEV.** The corpus is synthetic and test-owned, the measurement was made on
> a corpus the system itself imported, and **the sealed evaluation was NOT EXECUTED** — the
> isolation a sealed split needs was not established from this executor, and the prior
> milestone's retained verdict on isolation is reused as it stands and **not re-probed**.

---

## 1. What each artefact is

| artefact | what it is |
|---|---|
| `REVIEW.md` | the **independent DSH reviewer's** own file — the review of the milestone, with 9 MAJOR findings and no BLOCKER. |
| `REVIEW-RESOLUTIONS.md` | the implementer's response to that review: what was changed, in which artefact, and how it was verified. |
| `case-authors/A1.json` … `A6.json` | **the independent case authors' own bytes.** Six separately commissioned DSH subagents, each given the case contract and its own domain's corpus and nothing else — no implementation, no instrument, no previous case set, no pre-registration, no governance document. The files are read by the checker and by the assembler, and **never edited by the implementer**. |
| `CRJ-01-NECESSITY-GRID-ROWS.json` | every measured row: one case, one condition, one scope, one rule. Each row carries the scope, the rule's verdict and reason, the requirements the scope does not carry, the oracle's label, the confusion-matrix cell, the evidence the scope carried and did not, the served path, the disposition, the reachability of the case's gold, and the fingerprint of the view the row was measured against. |
| `CRJ-01-NECESSITY-GRID-SUMMARY.json` | the aggregation: the counts and the four rates per rule and per case condition, **with every denominator**, and the places where the two rules decided differently. |
| `TEST-io.matrix.knowledge.EpistemicNecessityServedTest.xml` | the GREEN run of the serving suite. |
| `CRJ-01-NECESSITY-REPRODUCTION-no-prerequisite.xml` | the RED reproduction: the same suite with the proposal collapsed onto the incumbent. |
| `CHECKSUMS.sha256` | the manifest of the directory. |

## 2. How it was produced

```bash
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home

# the six authors' files are checked by the contract's own checker
python3 scripts/necessity-case-check.py \
  docs-v2/engineering/evidence/crj01-necessity/case-authors/A*.json

# the case set is assembled from the authors' bytes and nothing else
python3 scripts/necessity-cases.py

# the serving suite
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --rerun \
  --tests "io.matrix.knowledge.EpistemicNecessityServedTest"

# the retained XMLs and the manifest
./scripts/necessity-retain-evidence.sh green

# the gate: the verdict is its exit code
python3 scripts/necessity-gate.py
```

> **`--rerun` is mandatory.** The case set and the corpus are not declared inputs of the test
> task, so Gradle considers the task up to date and keeps the **previous** XML. This happened
> twice in the previous milestone; every retained XML here was produced with `--rerun`.

## 3. What this evidence shows

- That the **two rules** — the incumbent anchor and the necessary-prerequisite proposal — were
  measured on **identical inputs**, and where they decided differently.
- The **confusion matrix** of each rule against the independent oracle, with the denominators
  stated and the empty denominators named as undefined.
- The **served journey**: an unseen task whose available competence fails, the missing
  prerequisite named, a bounded typed request for it, an authorised fulfilment, the same task's
  disposition changing, and the refusal of evidence that is unauthorised, not the workspace's
  own, stale, or conflicting.
- The **durable lifecycle**: a retirement that survives a restart, prevents the stale selection,
  keeps the historical evidence, and does not close the door on a revalidation.

## 4. What this evidence does NOT show

- **Nothing about real data.** The corpus is synthetic and test-owned.
- **Nothing about the two rules in general.** The comparison is over **24** independent cases
  on two synthetic domains, of which the **8** evaluation cases are the measured ones. It says where the rules differ on **this** corpus; it does not say
  which rule should be served, and the pre-registration forbids that conclusion.
- **Nothing about the sealed evaluation.** It was **NOT EXECUTED**, and no isolation was
  re-probed.
- **Nothing about generalisation or superiority.** The user-observable outcome of each run is
  reported in the rows; a transfer or a generalisation claim is a different claim and is not
  made here.
- **Nothing about the unreachable golds.** Some cases' necessary sentences are **not reached**
  by the workspace's own reading. That is measured and retained (`goldReachable`,
  `unreachablePropositions`) rather than hidden, and the milestone states what it means for the
  measurement.

## 5. What this directory does NOT contain

This directory is the milestone's **own** evidence. It does **not** contain, and the
milestone does **not** deliver, the artefacts of the prior milestones (`evidence/crj01-reach/`,
`evidence/crj01-adaptive-competence/`, the `C2A-*` files). Those suites **rewrite their own
retained evidence whenever they run**, so a broad test run regenerates them; the files were
**restored byte-for-byte from `HEAD`** after the final runs, and they are **not** part of this
milestone's deliverables.

## 6. The manifest

`CHECKSUMS.sha256` covers every file of this directory. The `case-authors/` sub-directory is
**not** covered: those are the authors' files, and the milestone records their digests in the
case set's provenance instead, so that a change to an author's bytes is visible in the case set
and not only here.
