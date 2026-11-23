# CRJ-01 — the development record: everything needed to continue on another machine

**Status:** `CANONICAL` · **Written:** 2026-11-24 · **Purpose:** portability

**Why this document exists.** The owner asked for a record of every development, so the
project could be moved to a different computer without losing anything. This is that
record: what the programme is, what was built, what was measured, what is still open, what
is in Git and what is not, and the exact commands to resume.

**It is written to be read on a machine that has never seen this repository.** Every path is
relative to the repository root; every number names its evidence file; every claim names
the document that carries it.

---

## 1. What the programme is

MATRIX is a cognitive system in two serving chains: **Chain A** (`matrix-core`, a Quarkus
application on port 9091) holds the knowledge and the cognitive stores and serves the
report journey; **Chain B** is the brain runtime. A **governed bridge** connects them.

The commission is in `docs-v2/engineering/AGENT-COMMISSION-COGNITIVE-PROGRAM.md`, the
strategic direction in `docs-v2/governance/COGNITIVE-DEVELOPMENT-PROGRAM.md`, and the
initial acceptance contract in `docs-v2/engineering/CRJ-01-ACCEPTANCE.md`. The working
protocol — the split, the oracles, the arms, the metrics, the freeze — is
`docs-v2/engineering/CRJ-01-PROTOCOL.md`. The instruments are described in
`docs-v2/engineering/CRJ-01-INSTRUMENTS.md`.

**The question the programme asks.** Can the system learn something that makes it *more
capable* — answering a **different** task better or more cheaply — rather than merely
repeating what it already knew? The arms are:

| arm | what it is |
|---|---|
| **A — the reference** | the production reading, every time |
| **B — the replay** | the retained episode re-executed (the **null**: it answers a repeat cheaply and correctly) |
| **C — the learned** | a promoted procedure / a learned claim answering a **different** question |

**The null is the point.** Any advantage arm C shows over arm A has to survive arm B, or
it is a cache and not a competence gain. The protocol's own rule: *"If arm C wins only
exact repeats, do not call it a competence gain."*

---

## 2. What was built, and where each thing lives

### 2.1 The instruments (all in the test tree — they are instruments, not product)

| instrument | path | what it does |
|---|---|---|
| the **source-span oracle** | `matrix-core/src/test/java/io/matrix/crj/evidence/SourceSpanOracle.java` | re-reads the corpus file's bytes, re-computes its sha256, and grounds a cited proposition only when it is **byte-identical** to the gold **and** the cited claim id is the **production** id in the workspace under test |
| the **lamp truth oracle** | `matrix-core/src/test/java/io/matrix/crj/microworld/LampTruthOracle.java` | four **literal** rows written from the specification, cross-checked against the world on all four assignments; a disagreement **VOIDS** the run |
| the **corpus report** | `matrix-core/src/test/java/io/matrix/crj/corpus/Crj01CorpusReportBuilder.java` | writes `docs-v2/engineering/CRJ-01-CORPUS-REPORT.json` |
| the **dev split validator** | `matrix-core/src/test/java/io/matrix/crj/corpus/Crj01DevSplitValidatorTest.java` | validates every rule of the protocol's §5.4 on the dev split |
| the **arm runner** | `matrix-core/src/test/java/io/matrix/crj/runner/Crj01ArmRunner.java` | one row per (case, arm) on the **served** path; writes `docs-v2/engineering/CRJ-01-EVIDENCE.json` |
| the **metrics** | `matrix-core/src/test/java/io/matrix/crj/runner/Crj01Metrics.java` | M-A1…M-A6 with a **seeded bootstrap** percentile interval and the **exact McNemar** test; writes `docs-v2/engineering/CRJ-01-METRICS.json` |
| the **domain-B runner** | `matrix-core/src/test/java/io/matrix/crj/runner/Crj01DomainBRunner.java` | the lamp world's three arms; writes `docs-v2/engineering/CRJ-01-EVIDENCE-DOMAIN-B.json` |
| the **freeze record** | `matrix-core/src/test/java/io/matrix/crj/runner/Crj01FreezeRecord.java` | computes the §5.5 hash record from the artefacts; writes `docs-v2/engineering/CRJ-01-FREEZE-RECORD.json` |
| the **load-sensitive class** | `matrix-core/src/test/java/io/matrix/crj/quality/LoadSensitiveAssertionTest.java` | classifies the load-sensitive assertions; **opt-in** (see §5) |

### 2.2 The product changes that reached `develop`

| change | where | what it does |
|---|---|---|
| the **arm C procedure** | `io.matrix.cognitive.report.ReportProcedureLearner`, `ReportProcedureMethod`, `AlgorithmSelector` | the domain-A procedure **is** a validated support set (`Procedure.applicability`); the first selection rests on a **measurement**, not a declaration (`CostBasis.MEASURED_COMPLEXITY`) |
| the **learned claim** | `io.matrix.cognitive.report.ReportClaimLearner`, `LearnedClaims` | a validated claim with a **containment gate** (the proposition occurs **verbatim** in its source's text); its applicability is the concept **and** a common document |
| the **excerpt** | `io.matrix.knowledge.acquisition.DocumentImporter` | the stored excerpt **covers every claim the workspace asserts** — it is the document's text |
| the **measured cost** | `io.matrix.cognitive.CognitiveStore` | the store carries the promotion's measurement beside the procedure's other validation measurements |
| the **source revision applicability** | `io.matrix.knowledge.SourceObservation`, `SourceRevisionSelection` | the served answer cites **the actually applicable source revision and its own claim identity**; the canonical id from an inactive revision is never attached to an active observer |
| the **frozen premise test** | `io.matrix.crj.runner.FrozenPremiseJourneyTest` | the seven control groups of the internal evaluation protocol (PR #76) |

### 2.3 The documentation

| document | what it holds |
|---|---|
| `docs-v2/engineering/CRJ-01-ARM-C.md` | **the running report** of this programme — §1…§17, each a measurement with its evidence |
| `docs-v2/engineering/CRJ-01-SOURCE-REVISION-APPLICABILITY.md` | the verified source-revision gain (PRODUCT_GAIN) |
| `docs-v2/research/CRJ-01-PREMISE-TEXT-MATCHING-NEGATIVE-RESULT.md` | the **negative result** (PIVOT), with its retained evidence |
| `docs-v2/governance/DECISION-REGISTER.md` | 85 decisions; CRJ-01's are **CD-048…CD-058** |
| `docs-v2/governance/RISK-REGISTER.md` | 24 risks; CRJ-01's are **R-EPIS-01…R-EPIS-24** |
| `docs-v2/governance/PROJECT-STATE.md` | the current state, in one place |
| `docs-v2/engineering/evidence/` | the retained JUnit XMLs and the two retained evidence sets |

---

## 3. What was MEASURED — the numbers, with their evidence

Every number below is reproducible from the repository; the evidence file is named.

### 3.1 The corpus (the sealed split's precondition)

| | | evidence |
|---|---|---|
| the documents | 7 | `docs-v2/engineering/CRJ-01-CORPUS-REPORT.json` |
| the claims the production path derived | 540 | the same |
| of those, **verbatim spans** of the document's text | **483** | the same |
| **fresh** (not already gold) | **470** | the same |
| **fresh AND span** — the candidates for the sealed split | **413** | the same |
| the sealed split needs | 18 | `docs-v2/engineering/CRJ-01-PROTOCOL.md` §5 |
| **the candidates the LEARNER can see** | **413** (was **213** before the excerpt fix) | the same |

**That last row is the fix that mattered.** The excerpt used to be cut at 8 000 characters,
which hid **213 of the 483** real spans — **44 %** — and the learner refused them with the
*false* reason *"it is a paraphrase of the document rather than a span of it". A claim past
the cut was not rejected on the evidence; it was **never examined**.

### 3.2 The arms on the served path (the dev split, 9 cases)

| arm | grounded | the median wall | the note |
|---|---|---|---|
| **A** the reference | **6/9** | 79.41 ms | — |
| **B** the replay, novel | 0/9 | 7.61 ms | inapplicable: a replay cannot prime itself |
| **B** the replay, primed by the reference | **6/9** | 10.97 ms | the **null** |
| **C** the learned | **6/9** | 58.70 ms | **26.1 % cheaper** than A by the median, 33.6 % by the total |

**And the honest reading.** Arm C's advantage over A is **26.1 %** — but arm B is **7.2×**
cheaper, and only on repeats. The **confound** is measured and named: arm A and arm C give
a **different epistemic disposition** for the same case (R-EPIS-20), because arm C's
restricted support changes the account set the premise semantics judge against. **A
grounding-only comparison would compare two different systems**, so the sealed comparison
must score the **disposition** as well as the grounding.

### 3.3 The metrics (the protocol's own instruments)

M-A1 = 12/18 · M-A2 = 0/6 · M-A3 = 0/18 · M-A4 = 0/8 · M-A5 = 0/0 · M-A6 = 18/18.
Paired Δ(C−A) = 0.000, no discordant pairs · Δ(C−B novel) = 0.667 [0.333, 1.000],
McNemar **0.031250** (it reproduces the protocol's own worked example) · Δ(C−B repeat) = 0.000.
Evidence: `docs-v2/engineering/CRJ-01-METRICS.json`.

### 3.4 The cost curve (why a narrow support is the mechanism)

Saving is a strong function of the support fraction, over a 540-claim corpus:
90/540 → **85.46 %** · 182/540 → **77.83 %** · 416/540 → **44.36 %** · 540/540 → **0.02 %**.
**A support that covers most of the corpus saves nothing** — which is why arm C's support is
the five **narrowest** questions' sources.

### 3.5 The learned claim's contribution, scored

20 citations · **20 grounded** · **0 ungrounded** · **0 out of scope** · **14 of the 20**
were evidence the reference **alone** did not carry — the contract's REUSE row as a number.

### 3.6 Domain B — the lamp world has NO transfer case

The protocol's domain-B metrics are built on a transfer. The lamp world has exactly **four**
assignments, and over **every** possible step bound there is either a novel query **or** an
arm that can answer it, **never both**: a bound below the domain's size leaves a novel
assignment the surviving hypotheses **disagree** about (the reference **refuses**), and a
bound of the domain's size settles the model and leaves **no** novel query.
**So M-B4's denominator is ZERO**, and at the pre-committed bound arm C's cost advantage
(0 probes against 36) is **indistinguishable from the replay's**. The owner's decision
(CD-056): **domain A is decisive**; domain B is run **once** as a rehearsal of the null.

### 3.7 The source-revision gain (PRODUCT_GAIN, PR #78)

The served answer cites **the actually applicable source revision and its own claim
identity**; a canonical id from an inactive revision is never attached to an active
observer. **7/7** frozen control groups and **10/10** reliability controls, on the exact
accepted head, with the audit checking 12 REFUTED and 7 UNSUPPORTED responses against
frozen source bytes. Evidence:
`docs-v2/engineering/evidence/crj01-source-revision/`.

### 3.8 The negative result (PIVOT, PR #77)

The tested text-matching candidate failed to establish claim-level premise refutation: the
baseline and the candidate each passed **2 of 7** frozen control groups. The candidate
passed all 18 targeted developer tests and **0** additional frozen groups, and **received
no scientific promotion**. The evidence is retained and verifies (11 of 11, 9 of 9).

### 3.9 The load-sensitive assertion class

**39 assertions over 23 files** assert a measured duration against a constant budget — a
property of the **machine**, not of the **code**. One instance was confirmed and **fixed**
(`TruthTableMinimizerTest`: a wall-clock budget became a **ratio** against a baseline taken
**in the same run**; the factor measured 1.5× alone and 1.6× under a 32-thread load, so it
is **load-invariant**). The remaining candidates hold under a 3.3× load.

### 3.10 The regression baseline

**Full suite on the merged tree: 1383 suites / 9860 tests / 0 failures / 0 errors / 23
skipped**, in about 36 minutes, run **alone** under `flock`. The wide targeted regression:
**249 suites / 1462 tests / 0 failures**. The retained evidence set: **84 files — 81 GREEN
(367 tests) + 3 RED (4 intentional failures)**, the RED ones being the record of a defect
that was found and fixed.

---

## 4. What is still OPEN, and who owns it

| # | the open item | owner |
|---|---|---|
| 1 | **the sealed split's author** — the 18 domain-A cases, written by someone who **implements no arm**. The protocol's §5.6 requires it, and this agent implemented arm C, so it is disqualified. The constraints are now **measured**: a large corpus, a support of about 1/6, a gold that is a **verbatim span** and **fresh**, subjects whose accounts do **not** disagree, and a comparison that scores the **disposition** as well as the grounding | **the owner** |
| 2 | **the freezing reviewer's name** — the freeze record is computed and re-derivable, and it **refuses to be written without its roles**; the two role fields are placeholders until a person is named | **the owner** |
| 3 | the **storage bound** — the excerpt now holds the document's text (2.06× over the pool, about 53 KB over seven documents). The alternative is a hard cap, which would hide spans again | **the owner** |
| 4 | the **review rule** — the repository's ruleset requires a code-owner review that **no one can give**: there is no `CODEOWNERS` file and the only collaborator is the pull request's own author. Every merge has therefore been an **administrative bypass, not a satisfied gate** (CD-057, CD-058) | **the owner** |
| 5 | **domain B** — either a micro-world with a **larger input domain**, or restate the domain-B metrics to measure the episode rather than the transfer (R-EPIS-21) | **the owner** |
| 6 | **R-EPIS-24** — the ShellCheck gate is **dormant** (it only runs when a pull request touches a script) and is armed with about 100 pre-existing findings in 25 scripts. Fixing it means touching `.github/workflows/**`, a **FROZEN** zone, so it needs an RFC | **the owner** |
| 7 | the open defects that are **not** the owner's: the applicability of a learned claim is still the concept **and** a common document (R-EPIS-16, closed but bounded); the learner promotes every candidate that passes the gates and **4.2 %** of the observation triples make the agreement gate satisfiable at all; the arm C saving is measured at **26.1 %** on a dev split, not on the sealed one | the programme |

---

## 5. How to resume — the exact commands

**Environment.** `JAVA_HOME=$HOME/jdk25`; Gradle 9.6 via the wrapper; `--offline`; one heavy
`matrix-core` JVM at a time.

```
export JAVA_HOME=$HOME/jdk25; export PATH=$JAVA_HOME/bin:$PATH
```

**The lock.** Every Gradle invocation is serialised, and the **full suite runs alone**:

```
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --console=plain
```

**Put the `--tests` filters immediately after the task they belong to.** Options placed
after two task names apply to the **last** task only, and the first then runs its full
suite. **Never use `--rerun`**: it re-triggers the Quarkus native-image build. To refresh
the evidence, delete `matrix-core/build/test-results` and re-run the filtered command.

**The targeted suites** (the repository's rule is targeted suites, not the full run):

```
./gradlew :matrix-core:test --tests "io.matrix.crj.*" --offline --console=plain
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*" --offline --console=plain
./gradlew :matrix-core:test --tests "io.matrix.knowledge.*" --offline --console=plain
```

**The load-sensitive class** (opt-in; it is slow and its subject is the machine):

```
./gradlew :matrix-core:test --tests "io.matrix.crj.quality.*" -Dcrj01.loadSensitive=true --offline --console=plain
```

**The document gates** (they run in CI, and they are cheap):

```
python3 scripts/docs-link-check.py --root . --strict
python3 scripts/validate-current-state.py --root . --strict
```

**The commits.** `WAL: <short description>`; one commit = one meaningful change; a push
after each; the frozen zones (`CONSTITUTION.md`, `AGENTS.md`,
`matrix-core/src/main/java/io/matrix/ethics/frozen/`, the avro schemas,
`.github/workflows/**`) need an explicit RFC.

---

## 6. What is IN Git and what is NOT — the portability boundary

**This is the section the owner asked for.** Everything below is what a new machine gets.

### 6.1 IN Git — you lose nothing by moving

| what | where |
|---|---|
| the whole source tree and its history | the repository itself (2 747 commits on `develop`) |
| **every** canonical document: the commission, the protocol, the instruments, the running report, the registers, the project state, the two retained evidence sets | `docs-v2/` |
| the owner's **directive documents** — the autonomous development mandate, the new-chat handoff, the project history handoff, the bootstrap prompt, the release-stabilisation orchestration, and the **source-provenance execution mandate of 2026-11-23** | `OpeAi/` |
| the session's own commission and checkpoint record (the premise milestone's checkpoint, the goal checkpoint, the applied patches, the dispatch, the governance RFC) | `docs-v2/engineering/evidence/crj01-premise-negative/commission/` |
| the retained JUnit XMLs and the evidence sets, with their manifests | `docs-v2/engineering/evidence/` |

### 6.2 NOT in Git — and what it is, and whether it matters

| what | why not | does it matter? |
|---|---|---|
| the **sessions' scratch directories** at the repository root (`matrix-crj-*`, `matrix-dsh-*`, `matrix-revision-*`, `matrix-source-*`) — about 4.7 GB of working copies, evaluator working copies and Gradle build output | **machine state**; a copy of the repository inside the repository is not part of the repository. They are gitignored (2026-11-24) and the document link checker now skips every directory git tracks nothing in | **No** — their *content* is either merged (the branches are older snapshots of `develop`) or retained above |
| the **evaluator's gold** and frozen cases | the mandate forbids publishing hidden gold, and a blind evaluation that publishes its answers is not blind | **Only to the owner** — the digests let the owner verify the retained evidence against the gold they hold |
| the **native-image binary** and the `native-binary` test scope | the build excludes the `native-binary` tag from the ordinary suite; the executable has never been built here | **No** — the scope is excluded by the build's own rules |
| the `integration` test scope | excluded unless `-PincludeIntegration`; those tests need **Docker** | **No** — the same |
| `data/crj01-demo/` (2.4 MB) | runtime demonstration data, gitignored | **No** |
| `.jqwik-database`, `matrix-api-gateway/data/` | runtime state: the gateway's task store and the jqwik property database | **No** |

**If you want the gold and the evaluator's fixtures on the new machine**, copy them from the
old one — the owner's copies are under the session directories listed above, and their
digests are published in the retained records. Everything needed to *verify* the published
results is already in Git.

### 6.3 The one thing Git cannot carry

**The repository's own history is the record.** The two decisive results — the 7/7
source-revision gain and the 2/7 premise negative result — are recorded in Git, in the
documents, and in the retained evidence, with their digests. A new machine that clones the
repository has all three.

---

## 7. The decisions in one paragraph each

The CRJ-01 decisions are **CD-048…CD-058** in `docs-v2/governance/DECISION-REGISTER.md`, each
with its alternatives and its evidence. The three that shape everything else:

- **CD-052** — the sealed split's corpus is a **hard condition**, and the reason is
  **measured**: the bundled photosynthesis package is a **paraphrase** corpus, so the
  oracle grounds **0 of the 7** claims the served journey cites. A case built over it would
  be scored UNGROUNDED on every case.
- **CD-055** — the gold claim id is resolved **in the workspace under test**, because a
  production claim id embeds the workspace id and the protocol requires each arm to have
  its own workspace; comparing against the id the set froze would make at most **one** arm
  groundable.
- **CD-057** — the merges are an **administrative bypass of an unsatisfiable gate**, not a
  satisfied requirement. That is stated in plain words rather than hidden.

---

## 8. The honest summary

**What is proved.** The system can learn a **validated support set** and answer a
**different** question from it; the answer is **equally correct** (36/36 against 36/36 in
domain B, 6/9 against 6/9 in domain A) and **cheaper** (26.1 % by the median on the dev
split); the learned claim's contribution **grounds every time it is cited** and adds
evidence the reference alone did not carry (14 of 20); the source-revision gain passes
**7/7** frozen control groups on the accepted head.

**What is not proved.** No **sealed** measurement exists, so there is no `δ_MI` and no
transfer claim. The cost advantage is measured on a **dev** split, not on an independent
one. And the arm C advantage is, so far, **not distinguishable from the replay's** in the
one domain where the null can be run.

**The decision is CONTINUE**, and the next step is not mine: it is the owner's two names
and two commissions (§4, items 1 and 2).
