# PUBLICATION CANDIDATES — what may be published, and what may not

**Status:** `CANONICAL` · **Date:** 2026-11-06 · **Wave:** W49 (tasks W49-08, W49-09) ·
**Branch:** `develop` · **HEAD:** `ed3725d5`
**Owner directive:** OD-048, "W49 DIRECTIVE ADDENDUM — Public Research Presence" (2026-11-06)
**The pipeline these candidates go through:** `docs-v2/research/PUBLICATION-PIPELINE.md`

**What this document is:** the evaluation of three publication candidates against the
repository's evidence, with a verdict for each, and the disposition of the one paper
draft the repository already holds.

**The rule:** *an external publication must not make a stronger claim than the
repository's evidence supports.* The verdict on a candidate is therefore a verdict on the
**evidence**, not on the interest of the subject.

---

## 1. The three verdicts

| the candidate | the subject | the verdict | the reason in one line |
|---|---|---|---|
| **A** | MATRIX architecture and research status — a technical overview of the research direction, the architecture, the serving topology, the implemented capabilities, the open problems and the evidence | **REVISE** | the architecture half is measured and publishable; the "research status" half asserts capability levels whose **targets are not established** — so the scope must shrink to the architecture and the evidence discipline, and the claims about the levels must go |
| **B** | evidence-preserving state and recovery — observation history, learning-state preservation, snapshot compatibility, crash recovery and reproducible restoration | **PROCEED** | it is a **negative-safety** contribution with a typed failure, an executable test (13 tests, 17 s, run today) and a measured drill — the strongest of the three, because everything it claims is already a file in the tree |
| **C** | workload-dependent CPU/CUDA acceleration — a reproducible benchmark note of where CUDA helps and where it does not | **PROCEED** | the *negative* half is the contribution — the GPU is **four times slower** on the single-point call — and the script, the harness and the execution-profile proof make it reproducible on another GPU (run today: exit 0) |
| the existing draft | `docs-v2/research/W31-ARXIV-PAPER-DRAFT.md` | **HISTORICAL — kept, not published, not revised** | §5 |

**And what is not proposed:** a broad "AGI" paper. §6 says why, in the repository's own
words.

---

## 2. Candidate A — MATRIX architecture and research status

### 2.1 What it is

A technical overview: the research direction, the existing architecture, the serving
topology, the capabilities that are implemented, the open problems, and the evidence.

### 2.2 The evidence it already has

| the evidence | where it is | how strong it is |
|---|---|---|
| **the serving topology, measured** — chain A is one Quarkus process with three serving paths, 210 routes and three ports; chain B is one `MinimalHttpServer` with 28 routes that serves a **stub** unless `MATRIX_MODE=production`; nine gateway JAX-RS routes are compiled but never mounted | `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row `W46-SERVING-TOPOLOGY` — implementation `IMPLEMENTED`, integration `INTEGRATION_TESTED`, verification `MEASURED`, operational `SERVED` | **strong.** It is reproducible from the compiled classes: `scripts/w45-chain-isolation-reachability.py` reads the route census from the constant pool and the closure from the call graph — 2 909 classes, 76 858 edges, chain A's closure 1 015 classes, chain B's 465, their intersection 179 library classes, and **chain B reaches 0 classes of `io.matrix.cognitive`** |
| **the four axes of every capability** — **36 rows** measured on 2026-11-06, each with implementation, integration, verification, operational, the serving path, the benchmark evidence, the risks and the next verification condition | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` | **strong**, and it is the repository's most unusual asset: it is publishable *as such* |
| **the decision record** — the registered decisions with their owner, date, status and successor: **120 rows** measured on 2026-11-06 (`grep -cE '^\| (OD|CD|PR|ADR|ND)-' docs-v2/governance/DECISION-REGISTER.md`) | `docs-v2/governance/DECISION-REGISTER.md` | **strong** — a decision register with statuses is rare and it is a real contribution to the "how do you decide in a neuro-symbolic system" question |
| **the research corpus** — two hypothesis registers (38 cards `H-001…H-038` and the `H-039+` waves), 20 preregistration protocols, 68 experiment reports, the protocol's four evidence levels, and the `synthetic-scope` definition | `docs-v2/research/HYPOTHESES.md`, `HYPOTHESES-NEW.md`, `protocols/`, `reports/`, `PROTOCOL.md` | **strong as a method**, weak as a result: everything confirming is `synthetic-scope` |
| **the negative corpus** — the refuted, the corrected and the abandoned material, indexed | `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` | **strong** — and rare |
| **the open problems, as questions with owners** | `docs-v2/governance/OPEN-QUESTIONS.md` | **strong** |

### 2.3 The evidence it still lacks

| the gap | why it matters | what it would take |
|---|---|---|
| **the capability levels' targets are not established** | DESIGN-58's §6a states that the workbench's measurements do **not** establish L6's 75 % two-hop retrieval target, and that no number in that section was re-measured. A paper that said "the system reaches infant-class cognition" would be a stronger claim than the repository supports | a re-measurement of the levels against DESIGN-58 §6's thresholds, at a named commit, with the dispersion — i.e. a separate task, not a paragraph |
| **the whole confirming corpus is `synthetic-scope`** | the protocol's own term: the conclusion holds as far as the synthetic data reflects the domain, and that is not known | a domain corpus, with its licence, and the protocol's `production-domain` evidence level |
| **the system is red in 24 places** | the W48 reconciliation: 23 native-image failures (the GraalVM toolchain is absent) and one wall-clock compliance failure with **21 unadjudicated wall-clock sites** in the decision-path packages (`docs-v2/engineering/W48-TEST-RECONCILIATION.md`) | the GraalVM toolchain, and an owner RFC on the wall-clock sites. Neither is a paper's job |
| **no third-party reproduction** | a survey of your own system is not independent evidence | a collaborator running the reproduction commands of §6 of the collaboration brief |
| **four published claims are stale or rejected** | `C-01` (the test count), `C-02` ("33,000× faster than LLMs" — `REJECTED`, `REMOVED`), `C-08` ("90 % accuracy retention from LLM distillation" — `REJECTED`, `REMOVED`), `C-09` ("production-grade hybrid AI framework" — `UNKNOWN` on all four axes) | the claims must be restated from the registry, not from the pages that still carry them |
| **no formal release, no DOI, no author list** | a paper needs a citable version | the citation rule of `PUBLICATION-PIPELINE.md` §5: cite the exact commit and its tag |

### 2.4 The verdict

**REVISE.** The architecture half is publishable **today**; the research-status half is
not, and the revision is a subtraction, not an addition.

**The publishable form** is a *systems-and-evidence* paper: two isolated serving chains
with a measured reachability closure, a capability registry that refuses to collapse
four axes into one, a decision register with statuses, and a research corpus that
publishes its own negative results. Its thesis is that **a neuro-symbolic system can
publish its capabilities honestly**, and every claim in it is a registry row.

**The part that must be cut:** the capability-level claims. They are implementation
statuses, and the paper must say so.

**The part that must be added even so:** the mandatory content of the pipeline's §4 —
above all the implementation commit, the environment, the dispersion and the negative
results — and the §3.1 claim-strength pass, sentence by sentence.

**The venue class:** a systems, tools or demo track, or a workshop on cognitive-model
evaluation. **Not** a flagship venue, and **not** a "general intelligence" venue.

---

## 3. Candidate B — evidence-preserving state and recovery

### 3.1 What it is

A focused engineering-research paper on the part of the system where **losing evidence is
the failure mode**: the observation history, the learning state, the snapshot
compatibility contract, crash recovery and a reproducible restoration procedure.

### 3.2 The evidence it already has

| the evidence | where it is | how strong it is |
|---|---|---|
| **the defect, named and closed** — a restore of a workspace snapshot is destructive: the erasure set is larger than the payload of an old snapshot. A payload written before OD-036 carries no learning rows; restoring it into a workspace that holds learning rows deletes those rows and **cannot bring them back — and the restore succeeds** | `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md` §1; the commits `4084538a` (the contract and the refusal) and `33d199e2` (the size bound and the retention calendar) | **strong** — a real, silent-loss defect, found and closed |
| **the typed refusal** — `INCOMPLETE_LEGACY_SNAPSHOT`, the payload's **coverage** derived from the payload's own table list rather than from the version number, and the refusal exposed over HTTP with the reason | `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md` §§2–4; `matrix-core/src/main/java/io/matrix/knowledge/SnapshotCoverage.java` | **strong** |
| **the executable test** — 12 compatibility tests plus 1 endpoint test, on **real temporary SQLite databases**, not on mocked payloads. Six of them assert that **the fingerprint of the whole database is identical before and after the refusal**: the refusal leaves no trace | `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md` §5; `matrix-core/src/test/java/io/matrix/knowledge/SnapshotRestoreCompatibilityTest.java`, `SnapshotRestoreEndpointTest.java` | **strong** — and **re-run today**: 13 tests, 0 failures, exit 0, in a clean export of `ed3725d5` |
| **the size bound** — the measured payload of the owner's workspace is **103 075 bytes** (knowledge 57 789 / observation 171 / learning 45 066); the fixture distribution is min 3 312, median 28 681, p95 301 136; the default bound is therefore **8 MiB**, 81× the measured payload, configurable through `matrix.knowledge.snapshot.max-payload-bytes`. An over-bound payload is refused **before the write**, with `SNAPSHOT_PAYLOAD_TOO_LARGE`; **truncation is never a size policy** | `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md` §6; `docs-v2/engineering/W48-SNAPSHOT-RETENTION.md` | **strong** — the number is measured, not assumed |
| **the retention calendar** — three tiers (daily 7 / weekly 4 / monthly 12, per workspace, UTC) over the `workspace_snapshot` **rows**; it never touches the knowledge model, the observations, the adjudication history, the learning rows or the evidence | `docs-v2/engineering/W48-SNAPSHOT-RETENTION.md` | **strong** |
| **the restore drill, performed against a copy** — `scripts/restore-drill.sh` copies with `VACUUM INTO` over a read-only JDBC URI, restores, and verifies: `integrity_check = ok`, the expected schema version, the row counts matching, and the restored database opening under the writer lock without a migration. **The hazard was measured**: the live file's WAL holds the schema of v17–v19 while the main file is still the v16 checkpoint, so a plain `cp` would silently roll the database back three schema versions while still reporting `integrity_check = ok` | `docs-v2/operations/RECOVERY-RUNBOOK.md` §9 | **strong**, and it is the most transferable finding of the three candidates: the hazard is not MATRIX's, it is SQLite's, and anyone doing the same thing has it |
| **the registry row** — the workspace snapshot: `IMPLEMENTED` / `INTEGRATION_TESTED` / `MEASURED` / `SERVED`, chain A, with the serving path and the risks | `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row `W45-SNAPSHOT` | **strong** |

### 3.3 The evidence it still lacks

| the gap | why it matters | what it would take |
|---|---|---|
| **the drill has never been performed over the live file** | by design (the restore is owner-held, `OD-029`), and the paper must say so | the owner's explicit command, on a copy, with the runbook's stages |
| **the payload restore was not exercised by the drill** | the live `workspace_snapshot` table holds **0 rows**, so there was no payload to restore. The two classes that decide whether a payload restore is safe — `SnapshotCoverage` and `IncompatibleSnapshotException` — guard that *other* restore and were therefore not exercised by the drill | a workspace with a payload, and the drill extended to it. The runbook's §9.8 says what that costs |
| **no failure injection below the unit level** | the transaction order and the "no partial replacement" property are asserted by tests, not demonstrated by a killed process at the right moment | a fault-injection harness: kill the process at the transaction boundary and show the fingerprint is unchanged |
| **one mutant survives the mutation gate** | the `Consensus-evaluate-without-quorum` mutant — recorded in `docs-v2/engineering/MUTATION-REPORT.md`, and DESIGN-58 §6a notes that the Consensus entry does not claim a checked quorum because of it | the mutant killed, or the claim narrowed — which the registry already does |
| **no independent reproduction** | the test suite is in-tree, so the claim "the refusal leaves no trace" has only been checked by the authors | a collaborator running §4.3 of the collaboration brief |
| **the crash-consistency design is a design, not a measurement** | the recovery matrix (`docs-v2/engineering/W45.1-RECOVERY-MATRIX.csv`, 13 crash points) is a contract | a run of the 13 crash points |

### 3.4 The verdict

**PROCEED.** This is the strongest of the three, for three reasons that are all
independent of how interesting the subject is:

1. **The contribution is a negative-safety mechanism with a typed failure** — a class of
   bug that is silent and destructive, and a defence that is checkable: the refusal is
   typed, the coverage is derived from the payload itself, and the database fingerprint is
   asserted identical before and after.
2. **Everything it claims is already a file in the tree, and the test runs in 17 seconds.**
   The paper's artefacts exist; a reader can execute them today.
3. **Its most transferable finding is not about MATRIX.** The `VACUUM INTO` hazard — a
   plain `cp` of a WAL-mode SQLite file silently rolling the schema back three versions
   while still reporting `integrity_check = ok` — is a SQLite fact, and the runbook is
   useful to anyone doing the same thing.

**The scope the paper must declare, in its own abstract:** *the restore has been performed
against a copy; a restore over the live file has not; the payload restore was not
exercised by the drill.* Three sentences, and the paper is honest.

**The venue class:** a systems, database or software-engineering venue; a reproducibility
or systems-evaluation workshop; a short paper in a storage or data-management venue.

---

## 4. Candidate C — workload-dependent CPU/CUDA acceleration

### 4.1 What it is

A **reproducible benchmark note**: where the CUDA execution provider helps MATRIX's ONNX
path, where it does not, and how the answer depends on the workload. Not a claim that
MATRIX is accelerated — a measurement of **which** workload.

### 4.2 The evidence it already has

| the evidence | where it is | how strong it is |
|---|---|---|
| **the committed benchmark and its harness** — the script records the preflight (the device, the driver, the CUDA version, the CUDA libraries and their paths, the ONNX Runtime version, the JDK, the model's SHA-256, the free VRAM), runs the CPU and the CUDA case **identically** (same model, same sequence length 32, same warm-up 5, same 20 iterations, same JVM flags, same classpath, the same `LD_LIBRARY_PATH` for both), reports **three separate costs** and never averages them, and **fails hard (exit 5) if the execution profile does not name `CUDAExecutionProvider`** | `scripts/gpu-benchmark.sh`; `matrix-core/src/test/java/io/matrix/benchmark/GpuBenchmarkHarness.java`; the harness's own arithmetic under test in `GpuBenchmarkHarnessTest.java` | **strong** — a benchmark that cannot report a CPU number as a GPU number is the whole point |
| **the execution-profile proof** | the CUDA run's profile names `CUDAExecutionProvider x1431` and `CPUExecutionProvider x1170`; the CPU run's profile names `CPUExecutionProvider x2600` alone | **strong** |
| **the measured series, with dispersion** | the report's summary table gives, per series, the median, the min / p95 / max and the sample standard deviation | **strong** |
| **the historical digit, bounded and not reproduced** — the claim was "about 12.6× faster on this machine" (191.367 ms CPU against 15.190 ms CUDA). The three re-measurements land at **12.06×**, **11.68×** and **11.47×** — all below it. The 12.6× **stays a HISTORICAL figure**: it is not replaced, it is *bounded* | `docs-v2/engineering/W48-GPU-BENCHMARK.md` §§2–4 | **strong** — a benchmark note that keeps its own earlier number visible and bounds it is exactly the artefact this pipeline wants |
|  **the case where the GPU loses** — the ONNX point call (the teacher FFN, batch 1): the CPU median is **0.008 ms** against the CUDA median **0.034 ms** — **0.24×**. The launch overhead dominates the arithmetic | `docs-v2/engineering/W48-GPU-BENCHMARK.md` §5 | **strong, and it is the contribution** |
|  **the case where the GPU does nothing** — the BIR point call (MATRIX's own boolean runtime): **0.94×** — 0.075 µs against 0.080 µs. There is **no GPU implementation** of MATRIX's own runtime | same | **strong** — it bounds the claim on the other side |
|  **the raw compute against the serving cost** — the raw `session.run` is **23.73×** faster on CUDA; MATRIX's own call, which additionally reads the whole logits tensor back and takes the argmax, is **12.06×**. The difference is the readback the serving path pays | same | **strong** — and it is the methodological heart of the note |
|  **an independent re-run, today** — `bash scripts/gpu-benchmark.sh` → **exit 0**, 2 m 37 s, on the same machine: the raw forward **23.25×**, MATRIX's own call **13.62×**, the ONNX point call **0.25×**, the BIR point call **1.01×**, the end-to-end path **11.17×**; the CUDA provider observed | this task's own run, 2026-11-06 | **strong** — and it is the second run of the same script, which is what a reproduction is |
| **the registry row** — the GPU claim: `IMPLEMENTED` / `INTEGRATION_TESTED` / `MEASURED` / `SERVED`, chain A, with the serving path `POST /v1/chat/completions` and the explicit note that the GPU is served **conditionally**: the CPU provider serves the same route when the loader finds a `libcudart` that does not export `cudaLibraryGetKernel` | `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row `W46-GPU` | **strong** |

### 4.3 The evidence it still lacks

| the gap | why it matters | what it would take |
|---|---|---|
| **one machine, one GPU** | every number is an NVIDIA GeForce RTX 5070 Ti Laptop GPU, driver 595.99.02, CUDA 13.2 supported / 12.4 toolkit, 12 227 MiB of VRAM | **the collaboration ask**: reproduce the script on a different GPU — a datacenter A100, an L40S, an H100, or a consumer card. The note's value *is* the spread across machines |
| **one model, one shape** | the transformer under test is a 0.5B Qwen graph at sequence length 32 | a sweep over sequence lengths and batch sizes — the point call's verdict is almost certainly batch-dependent, and nobody has measured where the crossover is |
| **the CPU series is noisy** | the CPU forward has a median of 173.8 ms with a standard deviation of 26.5 ms and a spread of 113.7–204.2 ms; it uses all 32 threads and feels the machine's other load | a quieter machine, more iterations, and the interquartile range — the median is already the right statistic, and the note says why |
| **the point-call verdict is measured on one CUDA version** | the 4× launch-overhead loss is a property of the launch path as much as of the hardware | the same measurement across CUDA versions |
| **no independent reproduction on a second machine** | the second run is on the author's own hardware | a collaborator's run, published beside the author's |
| **no energy measurement** | the registry's rejected `C-02` records that the old energy claim was `0.00×` — the project has no measured energy number | a power measurement, or the honest statement that there is none |

### 4.4 The verdict

**PROCEED**, as a **short reproducible benchmark note**, with one condition: the title and
the abstract must be about the **workload dependence**, not about the speed-up.

**The form of the claim that is true:** *on this workload class, on this machine, the
CUDA provider is ≈12–14× faster on the transformer forward pass as MATRIX calls it and
≈23× on the raw call, **0.25× — slower — on the single-point call**, and it makes no
difference to MATRIX's own boolean runtime, which has no GPU implementation.*

**The form of the claim that is false, and that the note must never make:** *"MATRIX is
12× faster with the GPU."* It is false in the case that matters most — the per-call
latency of a small inference — and it is false as a blanket statement about a workload
that has not been named.

**The mechanism of the note:** three rows in one table (the forward pass, the point call,
the end-to-end path), each with its median and its dispersion, each with the execution
profile that proves which provider ran it — and the historical 12.6× **kept and bounded**,
not deleted.

**The venue class:** a workshop or short paper on reproducibility, ML systems or
accelerators; an artifact-evaluation venue; or — and this is the honest one — **an
engineering report plus a public benchmark artefact**, with a collaborator's re-run on a
second GPU as the third-party evidence the note currently lacks.

---

## 5. The W31 arXiv draft — the decision

**Decision: `HISTORICAL`. The draft stays in the repository, unchanged in its body, with a
dated disposition note at its top. It is not submitted, and it is not revised into a
submission.**

The note is now at the top of `docs-v2/research/W31-ARXIV-PAPER-DRAFT.md`, and it states
the six findings that produced the verdict:

1. **the headline speed-ups are not MATRIX's measurements** — "3.5× memory reduction and
   2.7× faster inference vs FP16 baselines" are Ma et al. (2024)'s numbers, cited in the
   draft's own §2 as the BitNet paper's result;
2. **the throughput figures are single-run JVM measurements**, which the repository's own
   protocol classes as preliminary and which do not give an accept verdict;
3. **the abstract contradicts §5** — "270 unit and integration tests" against "248 tests";
4. **the capability table reports implementation status, not capability** — DESIGN-58 §6a
   states that the workbench's measurements do not establish the levels' targets;
5. **the system the draft describes is not the system in the tree** — the brain runtime
   was restructured into the two isolated chains;
6. **every confirming measurement is `synthetic-scope`**, and the draft names no dataset,
   licence, baseline measurement, dispersion, negative result or implementation commit.

**What it is still good for, and what it is not.** It is the record of what the W31 wave
wrote, and it names the intended venues for the capability-level question (a
cognitive-model-evaluation workshop, ALIFE, BICA — `docs-v2/designs/DESIGN-58-capability-levels-roadmap.md`
§7). **That intention survives; the draft does not.** If the edge-AI hybrid question
deserves a paper, it deserves a **new** one, written from the artefacts of the present
tree under `docs-v2/research/PUBLICATION-PIPELINE.md` — not from this text.

**And the general rule this establishes:** a paper draft in the repository is not a
publication candidate by virtue of existing. It becomes one by passing the pipeline's
OUTLINE criterion — a claim, the evidence that would support it, and every number traced
to a file.

---

## 6. What is not proposed, and why

**A broad AGI paper is not proposed.** The reasons are the repository's own, not a matter
of taste:

| the reason | the evidence |
|---|---|
| the published readiness claim is not established | `docs-v2/architecture/CAPABILITY-REGISTRY.yml` row `C-09` ("production-grade hybrid AI framework"): `UNKNOWN` on all four axes, and the row's own note — "a readiness claim has no measurement that would settle it; it is retired by rewording, not by evidence" |
| the published speed-up claim was **rejected** | row `C-02` ("33,000× faster than LLMs"): verification `REJECTED`, operational `REMOVED`. The row records that the figure "appears in no measurement; it is derived in `docs-v2/story/overview.md` by dividing 10–100 tokens/sec by a <10 ms inference — two different quantities" |
| the published retention claim was **rejected** | row `C-08` ("90 % accuracy retention from LLM distillation"): verification `REJECTED`, operational `REMOVED` |
| the "every claim is backed" sentence was **false when it was written** | row `C-10`: four of the numbers in the same registry had no backing |
| the capability levels' targets are not established | `docs-v2/designs/DESIGN-58-capability-levels-roadmap.md` §6a |
| everything confirming is `synthetic-scope` | `docs-v2/research/PROTOCOL.md` |

**The amount of material is not an argument for a paper.** The repository holds a large
research corpus because the project runs many waves; the corpus's size measures the
project's activity, not the size of a contribution. **A paper needs a claim, a baseline,
a measurement and a limitation** — and a candidate that cannot produce those four does
not become a paper by being large.

---

## 7. The vocabulary of this document

| the term | the meaning |
|---|---|
| **PROCEED** | the candidate may go into the pipeline's OUTLINE state now: the evidence it would claim exists |
| **REVISE** | the candidate may proceed only after a named change to its scope; the change is stated |
| **HOLD** | the candidate may not proceed: the evidence it would claim does not exist |
| **HISTORICAL** | the artefact stays in the repository as the record of a past state; it is neither published nor revised |
| **the claim-strength rule** | `docs-v2/research/PUBLICATION-PIPELINE.md` §3.1 |
| **`synthetic-scope`** | the protocol's own term for a measurement obtained on a synthetic, seed-controlled corpus |
| **the four axes** | the registry's implementation / integration / verification / operational axes — never collapsed into one |

**The companion documents:** `docs-v2/research/PUBLICATION-PIPELINE.md` (the workflow),
`docs-v2/research/COLLABORATION-BRIEF.md` (what an outside collaborator may work on),
`docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md` (the three opportunities, each one
executed).
