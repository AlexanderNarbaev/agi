# COLLABORATION BRIEF — MATRIX

**Status:** `CANONICAL` · **Date:** 2026-11-06 · **Wave:** W49 (tasks W49-08, W49-09) ·
**Branch:** `develop` · **HEAD:** `ed3725d5`
**Owner directive:** OD-048, "W49 DIRECTIVE ADDENDUM — Public Research Presence" (2026-11-06)

**Who this is for:** a researcher or engineer who wants to check something in MATRIX, test
something on their own data or hardware, or argue with one of its results.

**The rule this brief obeys:** **every opportunity below is labelled with the capability's
real implementation state, taken from `docs-v2/architecture/CAPABILITY-REGISTRY.yml`.** A
subsystem that is `DORMANT`, `EXPERIMENTAL`, `ISLAND` or `BLOCKED` is **not** available
for a production pilot, and this brief says so wherever it mentions one. The registry's
own rule: a capability may not be `SERVED` without a named serving path.

---

## 1. What MATRIX currently is

**A neuro-symbolic research system under active construction, whose claims are published
with their evidence.** Concretely:

| | |
|---|---|
| **the substrate** | knowledge is a boolean structure — a truth table, a clause set or a binary decision diagram — evaluated by a boolean runtime |
| **the architecture** | **two isolated serving chains**, and the isolation is measured, not asserted: chain A is a Quarkus application (the epistemic workbench, the cognitive kernel, the chat route), chain B is a hand-written `MinimalHttpServer` brain runtime. Their reachable closures intersect in 179 library classes, and **chain B reaches 0 classes of `io.matrix.cognitive`** (`docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row `W46-SERVING-TOPOLOGY`; the measurement is reproducible with `scripts/w45-chain-isolation-reachability.py`) |
| **the cognition** | six cycles, each separately registered, each with its own serving path |
| **the evidence discipline** | every capability carries **four independent axes** — implementation, integration, verification, operational — and never one green checkmark; every hypothesis has a preregistered threshold; every measurement names its command, its environment and its dispersion; the negative results are indexed, not buried |
| **the size of the corpus** | two hypothesis registers (38 cards `H-001…H-038` and the `H-039+` waves), 20 preregistration protocols, 68 experiment reports, 36 registered capabilities (measured 2026-11-06), 120 recorded decision rows, 15 TLA+ specifications model-checked by a gate that exits non-zero when a property is violated |

**The state of the work, honestly:** the full `matrix-core` suite was run once, under the
owner's approval `OD-041`, and it is **red in 24 places** — 23 because the GraalVM
toolchain is absent from the machine, and one because 21 wall-clock sites in the
decision-path packages are not yet adjudicated
(`docs-v2/engineering/W48-TEST-RECONCILIATION.md`). Every one of the 24 was proved
**pre-existing**; none was introduced by the wave that found it.

---

## 2. What MATRIX is NOT

**Say this plainly to a collaborator before they spend a week.**

| it is not | the evidence |
|---|---|
| **not AGI, and not a claim of one** | the published readiness claim "production-grade hybrid AI framework" is `UNKNOWN` on all four axes (`docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row `C-09`); the published "33,000× faster than LLMs" was **REJECTED** and **REMOVED** (row `C-02`) — the figure "appears in no measurement"; "90 % accuracy retention from LLM distillation" was **REJECTED** and **REMOVED** (row `C-08`) |
| **not production-ready** | the gateway serves a **stub** unless `MATRIX_MODE=production`; nine of its JAX-RS routes are compiled but never mounted; its facade is "a stub-shaped decoy" (the registry's own words, row `W46-SERVING-TOPOLOGY`) |
| **not validated on a domain** | every confirming measurement in the project is **`synthetic-scope`** — seed-controlled synthetic corpora. The protocol says it in one line: the conclusion is valid as far as the synthetic data reflects the domain, **and that is not known** (`docs-v2/research/PROTOCOL.md`) |
| **not a claim that its capability levels are reached** | DESIGN-58 §6a states that the workbench's measurements do **not** establish the levels' thresholds, and that no number in that section was re-measured (`docs-v2/designs/DESIGN-58-capability-levels-roadmap.md`) |
| **not a system with an established release** | there are **no releases**; the citable unit is a commit and its tag (`docs-v2/research/PUBLICATION-PIPELINE.md` §5) |

---

## 3. The capability levels, and their real state

**The levels are DESIGN-58's L0–L6.** Their §0 table marks the *implementation* as done —
that is a statement about the classes existing. Their *targets* are not established. The
state that governs any collaboration is the **registry's**, not the level's:

| the area | the registry's state | what a collaborator may therefore do |
|---|---|---|
| the cognitive kernel, the brain loop, the epistemic workbench, the goal manager | `IMPLEMENTED` / `INTEGRATION_TESTED` / `TESTED` / **`SERVED`** (chain A) | use them through the HTTP routes, and test them |
| the algorithm portfolio (8 methods over 4 families) with its selection policy | `IMPLEMENTED` / `INTEGRATION_TESTED` / `TESTED` / **`SERVED`** (chain A) | evaluate a method on their own data, through `POST /v1/cognitive/solve` |
| the workspace snapshot, with its size bound and its retention calendar | `IMPLEMENTED` / `INTEGRATION_TESTED` / `MEASURED` / **`SERVED`** (chain A) | test the compatibility refusal, and the restore drill |
| the chain isolation, the fail-closed reload, the writer lock | `IMPLEMENTED` / `INTEGRATION_TESTED` / `TESTED` / **`SERVED`** (both chains) | check the isolation property |
| the GPU path (`OnnxRuntimeAdapter`) | `IMPLEMENTED` / `INTEGRATION_TESTED` / `MEASURED` / **`SERVED`** — but **conditionally**: the CPU provider serves the same route when the loader cannot find a usable `libcudart` (row `W46-GPU`) | reproduce the benchmark |
| the connectome, the biochemical modulator island, the RUN-158 research brain loop | `IMPLEMENTED` / **`ISLAND`** / `TESTED` / **`DORMANT`** | **not available for a pilot.** They are in the tree and not in a serving path |
| the `MindCycle` | `IMPLEMENTED` / **`ISLAND`** / `TESTED` / **`EXPERIMENTAL`** | **not available for a production pilot** |
| the native (GraalVM) image of `matrix-core` | **`BLOCKED`** / `ISLAND` / `UNTESTED` / **`DORMANT`** | **not available.** The toolchain is absent; the CI-side enabler was fixed in W48-03 |

---

## 4. The concrete asks

**Six asks, each one executable today by a stranger.** Each is labelled with the registry
state of the capability it touches.

### A1 — Model-check the formal specifications, or find a counterexample

| | |
|---|---|
| **the ask** | run the 15 TLA+ specifications under `formal/` and check whether the properties they state actually hold — and, if you can, **find a specification that fails** |
| **the command** | `bash scripts/formal-gate.sh` — exit 0 when every specification holds, 1 otherwise |
| **the state it touches** | the FROZEN ethics pipeline and the core contracts; the specifications are the `FORMALLY_VERIFIED` evidence of several registry rows |
| **the cost** | 19 seconds on one CPU, a JDK, and the pinned `tools/tla2tools.jar` (the script verifies the jar's SHA-256 itself) |
| **the result that matters** | a **counterexample**: a state in which a property the system claims to hold does not hold. The gate's own contract is that a specification that fails makes the script exit non-zero — so a disagreement is a finding, not a nuisance |

### A2 — Reproduce the GPU benchmark on a different GPU

| | |
|---|---|
| **the ask** | run `scripts/gpu-benchmark.sh` on a **different** GPU — a datacenter A100, an L40S, an H100, a consumer card — and report the three ratios |
| **the command** | `bash scripts/gpu-benchmark.sh` — it records the whole preflight itself (device, driver, CUDA version, the libraries and their paths, the ORT version, the JDK, the model's SHA-256, the free VRAM) and writes the artefacts under `matrix-core/build/gpu-benchmark/` |
| **the state it touches** | `W46-GPU` — `IMPLEMENTED` / `INTEGRATION_TESTED` / `MEASURED` / `SERVED` (chain A) |
| **the question it settles** | the benchmark's finding is **workload-dependent**, and the interesting half is negative: on this machine the CUDA provider is **0.25× — four times *slower* — on the single-point call**, and it makes no difference at all to MATRIX's own boolean runtime (0.94×), which has no GPU implementation. Where the crossover lies on another card is unknown |
| **what is already known** | the W48 record run: 23.73× on the raw forward, **12.06×** on the forward as MATRIX calls it, **0.24×** on the point call, 0.94× on the BIR point call, 12.69× end-to-end; and the historical "12.6×" is **bounded, not reproduced** (`docs-v2/engineering/W48-GPU-BENCHMARK.md`) |

### A3 — Test the snapshot compatibility refusal against a workspace that holds history

| | |
|---|---|
| **the ask** | run the 13 compatibility tests, and then design a case they do **not** cover — a payload whose table list lies about its contents, a payload restored concurrently with a write, a target whose learning rows arrive between the coverage check and the write |
| **the command** | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.SnapshotRestoreCompatibilityTest" --tests "io.matrix.knowledge.SnapshotRestoreEndpointTest" --max-workers=1` — 13 tests, 0 failures, exit 0, in 17 seconds on a clean checkout |
| **the state it touches** | `W45-SNAPSHOT` — `IMPLEMENTED` / `INTEGRATION_TESTED` / `MEASURED` / `SERVED` (chain A) |
| **the why it matters** | the restore is **destructive**, and the defect it closes was silent: a legacy payload restores into a workspace that holds learning rows, deletes them, and **succeeds**. The defence is a typed refusal (`INCOMPLETE_LEGACY_SNAPSHOT`) with the coverage derived from the payload's own table list, and six of the tests assert that the **whole database's fingerprint is identical before and after the refusal** |
| **the boundary** | the restore has been performed **against a copy**, never over the live file (the live `workspace_snapshot` table holds 0 rows, so the payload restore was not exercised by the drill). Say so in any result |

### A4 — Evaluate an algorithm method on your own data

| | |
|---|---|
| **the ask** | take one of the portfolio's methods, run it on **your** corpus against **your** baseline, and report whether the selection policy picked the right one |
| **the surface** | `POST /v1/cognitive/solve` (chain A) — the selector's choice and the reason for it are returned in the answer; `POST /v1/cognitive/observe` runs the same selector inside the kernel's SELECT step |
| **the state it touches** | `W44-ALGO-PORTFOLIO` — `IMPLEMENTED` / `INTEGRATION_TESTED` / `TESTED` / **`SERVED`** (chain A); 8 methods over 4 families — exact integer arithmetic, exact entailment, and two others |
| **the why it matters** | the whole project is `synthetic-scope`. **A domain corpus is the missing input**, and this is the smallest surface that accepts one |
| **the cost** | a corpus with a licence, a baseline, and a threshold you agree on in advance |

### A5 — Argue with the hypothesis register

| | |
|---|---|
| **the ask** | take a card that is still `running` or `proposed` and **refute it**, or find the measurement that settles it |
| **where** | `docs-v2/research/HYPOTHESES.md` (H-001…H-038) and `docs-v2/research/HYPOTHESES-NEW.md` (H-039+). The cards carry their metric, their threshold, their baseline and their blocker; 20 cards have a preregistered protocol under `docs-v2/research/protocols/` |
| **the good targets** | `H-004` (Ricci-fingerprint detects degradation **before** the Recall@5 drop — needs a domain corpus), `H-009` (the energy and the full gate are still pending), `H-012` (TaskCell p99 against a direct cluster query — needs a load), `H-016`, `H-021`, the `H-023…034` duality series (domain data and small models), and the `H-039+` block on the impulse filter, the promotion criteria and the calibration | <!-- no-id: the register's own row carries the same marker; the card is not a declared identifier (Q-W46-19) -->
| **the state it touches** | the research body, not a capability: the registry does not describe a hypothesis |
| **the rule** | the refutation must respect the protocol's four evidence levels — a single-run `nanoTime` measurement does **not** give an accept verdict, and a `synthetic-scope` result is marked as such |

### A6 — Review an architectural proposal

| | |
|---|---|
| **the ask** | read one of the recorded decisions — **120 rows** on 2026-11-06 — and argue with it — as a reviewer, not as a contributor |
| **where** | `docs-v2/governance/DECISION-REGISTER.md` — each row carries the decision, the kind (owner / coordinator / proposal / normative), the date, the status, the successor, the document that holds it and the evidence or the scope of the approval |
| **the state it touches** | the decision register; the architecture rows it references carry the registry's four axes |
| **the useful kind of objection** | a decision whose *successor* is missing, a `PROPOSED` decision that has never been ruled on, or a decision whose evidence has since been superseded |

---

## 5. What can be independently tested NOW

**Everything in §4, today, with the commands as written.** Nothing there needs a GPU, a
model download, a dataset licence or an access request:

| | the command | the expected result |
|---|---|---|
| the formal gate | `bash scripts/formal-gate.sh` | 15 passed, 0 failed, exit 0 |
| the GPU benchmark | `bash scripts/gpu-benchmark.sh` | exit 0; the execution profile names `CUDAExecutionProvider`; three ratios reported with their dispersion |
| the snapshot compatibility scope | `./gradlew :matrix-core:test --tests "io.matrix.knowledge.SnapshotRestoreCompatibilityTest" --tests "io.matrix.knowledge.SnapshotRestoreEndpointTest" --max-workers=1` | 13 tests, 0 failures |
| the chain isolation | `python3 scripts/w45-chain-isolation-reachability.py` | the closure of the two chains and their intersection |
| the documentation gate | `python3 scripts/docs-link-check.py --root . --strict` | exit 0 when the wave's own files exist |

**The three opportunities are written out in full, with the observed output, in
`docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md`.**

---

## 6. What is only a future research direction

**Named as a direction, never as an offer.** These are the things a collaborator may
*ask about*, and may propose a **design** for — but there is nothing to pilot, and this
brief does not pretend otherwise.

| the direction | its real state | why it is not an offer |
|---|---|---|
| the **native (GraalVM) image** of `matrix-core` | `BLOCKED` / `ISLAND` / `UNTESTED` / `DORMANT` | the toolchain is absent on this host; 23 test failures are the consequence. The CI-side enabler was fixed in W48-03 (`graalvm-community`), so CI can now build it; the local host cannot |
| the **connectome**, the **biochemical modulator island**, the **RUN-158 research brain loop** | `IMPLEMENTED` / `ISLAND` / `TESTED` / `DORMANT` | they are in the tree and **not in a serving path**. A pilot on them is a pilot on a library |
| the **`MindCycle`** | `IMPLEMENTED` / `ISLAND` / `TESTED` / `EXPERIMENTAL` | experimental, not served |
| the **capability levels L0–L6 as a measured result** | implementation done; the **targets are not established** (DESIGN-58 §6a) | a level claim needs the DESIGN-58 §6 thresholds measured at a named commit — that is a task, not a collaboration |
| the **impulse / autonomy work** (the `H-039+` block) | in flight | the cards are still `running`; their thresholds are preregistered and their outcomes are not yet written |
| the **LLM-distillation path** | the published retention claim was **REJECTED** | the capability is real; the number is not |

---

## 7. The hardware and the datasets that would help

**Hardware — named, with the reason:**

| the hardware | what it unblocks |
|---|---|
| **a second, different GPU** — an A100, an L40S, an H100, or simply another consumer card | the crossover in A2 is unknown, and one machine cannot place it |
| **a CUDA version other than 12.4/13.1** | the point call's 4× loss is as much about the launch path as about the hardware |
| **a quieter machine, or a pinned core count** | the CPU forward series has a 26.5 ms standard deviation on a 173.8 ms median; it feels the machine's other load |
| **a machine with the GraalVM toolchain** (`native-image` for GraalVM 25+, or Mandrel 24.x) | the 23 native-image failures — and, with them, the native path's own coverage |
| **a machine with more RAM and a single test JVM** | the full suite is owner-held for an OOM reason; a machine that can run it in one JVM without the 8 GiB cap would change what the run means |

**Datasets — named, with the requirement:**

| the dataset | what it unblocks |
|---|---|
| **a real, licensed corpus for the cognitive kernel's retrieval** | `H-004`, and the `synthetic-scope` limitation on every positive claim |
| **a labelled reasoning / entailment corpus** | `H-016`, `H-021`, the `H-023…034` duality series, and the workbench's own measured bounds | <!-- no-id: the register's own row carries the same marker; the card is not a declared identifier (Q-W46-19) -->
| **a load profile for the TaskCell** | `H-012` — the p99 latency question needs a load, not a synthetic corpus | <!-- no-id: the register's own row carries the same marker; the card is not a declared identifier (Q-W46-19) -->
| **a second domain for the BIR / knowledge-stack path** | the protocol's `production-domain` evidence level — the only level that gives a full verdict |

**The requirement on a dataset:** it must have a **licence** that permits the use, and the
licence must be stated. A corpus that cannot be named in a paper cannot be a paper's
evidence.

---

## 8. How to propose a pilot

**Four steps, and the third one is the one that matters.**

1. **Name the question, not the feature.** "Does the snapshot refusal leave the database
   byte-identical when the target holds a half-written learning row?" is a question.
   "I want to work on snapshots" is not.
2. **Name the capability row it touches**, and say its four axes out loud. If the axis you
   need is `DORMANT` or `ISLAND`, say that too — the honest answer is often "this is a
   library, and a pilot on it is a pilot on a library", and that is a fine thing to
   propose.
3. **State the acceptance criterion and the baseline BEFORE the run**, and state the
   evidence level you are claiming (the protocol's four levels). A pilot whose criterion
   is written afterwards is a demonstration.
4. **Say what the result will be recorded as** — and where (§9).

**What the pilot may assume:** a clone of the repository, the commands in this brief, and
the reports they produce. **What it may not assume:** that a change will be merged, that
a result will be published, or that a capability will be made `SERVED`. Those are owner
decisions, and they are recorded as decisions
(`docs-v2/governance/DECISION-REGISTER.md`).

---

## 9. How results are recorded and reviewed

| the step | what happens | where |
|---|---|---|
| **the pilot's own record** | the protocol, the command, the environment, the observed output, the negative result | the experiment report under `docs-v2/research/reports/`, named `EXP-*.md`, with its data as a sibling `EXP-*.json` where the run produced data |
| **the hypothesis it decides** | the card moves to `accepted` / `refuted` / `superseded`, with the evidence named | `docs-v2/research/HYPOTHESES.md` or `docs-v2/research/HYPOTHESES-NEW.md` |
| **a negative result** | it is **indexed**, not deleted | `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` — the index exists so that a refuted hypothesis is discoverable and cannot be re-proposed by accident |
| **a capability that moved** | its four axes are updated, with the commit at which the claim was checked | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` |
| **an architecture decision** | it is registered with its kind, its date, its status and its successor | `docs-v2/governance/DECISION-REGISTER.md` |
| **the review** | the coordinator reads the artefact against the **files**, not against the summary; and the negative-evidence pass runs against the subject area | the wave report, `docs-v2/engineering/W<N>-STATUS.md` |

**A result that is only in a chat is not recorded.** The repository's own rule: the
journal is local and does not replace the wave report.

---

## 10. What counts as a successful collaboration

**Not** money, grants, publicity, stars or contributor counts. **Not** the number of
papers. The outcomes that count, in the order the project values them:

| the outcome | what it looks like |
|---|---|
| **independent validation** | someone else ran the reproduction commands and got the same answer, on their own machine, and said so |
| **research criticism** | a refutation, a counterexample, or an argument that a preregistered threshold was the wrong threshold — with the protocol respected |
| **a reproducible improvement** | a change that makes a measurement better and that the measurement shows |
| **concrete domain testing** | the result on a real corpus, with its licence, that moves a claim out of `synthetic-scope` |
| **a recorded negative result** | a refutation that is indexed and therefore cannot be re-proposed by accident |

**And the two things that would count against a collaboration:** a claim made stronger
than the artefact supports, and a result that is not recorded where the next reader will
find it.

---

## 11. The companion documents

| the document | what it holds |
|---|---|
| `docs-v2/research/PUBLICATION-PIPELINE.md` | the workflow, the seven states, the mandatory content of a public article, the claim-strength rule, the CITATION.cff evaluation |
| `docs-v2/research/PUBLICATION-CANDIDATES.md` | the three candidates and their verdicts, the disposition of the W31 arXiv draft, and what is not proposed |
| `docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md` | the three opportunities, each one executed, with the exact commands and the observed output |
| `docs-v2/architecture/CAPABILITY-REGISTRY.yml` | the four axes of every capability — **the authority for every label in this brief** |
| `docs-v2/research/RESEARCH-INDEX.md` · `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` | the index of the research body, and of its negative evidence |
