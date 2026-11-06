# REPRODUCIBLE OPPORTUNITIES — what a stranger can do with MATRIX today

**Status:** `CANONICAL` · **Date:** 2026-11-06 · **Wave:** W49 (tasks W49-08, W49-09) ·
**Branch:** `develop`
**The commit every command below was executed at:** `ed3725d58c9747533c44a5e03298fd473693a9cb`
(`WAL: the wave closes with the mandatory CI checks green on a clean runner`, 2026-11-06)
**The repository:** `https://github.com/AlexanderNarbaev/agi.git` (the `origin` remote; a
second remote, `git@gitverse.ru:AlexandrNarbaev/agi.git`, holds the same history)

**The rule this document obeys:** *only an opportunity that has actually been executed is
offered here.* Every command below was run on 2026-11-06, in this environment, and its
output is reproduced verbatim. An opportunity that could not be executed is not in this
document — §7 says what was tried and what happened.

**What an opportunity must carry:** the exact repository URL and the commit · the
prerequisites · the exact commands · the expected observable result · the acceptance
criterion · the resource cost · **what a negative result would mean**.

---

## 1. The three, at a glance

| # | the opportunity | a stranger can start with | the cost | executed on 2026-11-06 |
|---|---|---|---|---|
| **O1** | **Model-check the 15 TLA+ specifications, or find a counterexample** | a clone and a JDK — **nothing else** | 19 s, one CPU | ✅ exit 0 — 15 passed, 0 failed |
| **O2** | **Test the snapshot compatibility refusal against a workspace that holds history** | a clone and a JDK — nothing else | 17 s, one CPU | ✅ exit 0 — 13 tests, 0 failures |
| **O3** | **Reproduce the GPU benchmark on a different GPU** | a clone **plus the owner's model files** (not in git) and a GPU | 2 m 37 s, one GPU | ✅ exit 0 — 23.25× / 13.62× / **0.25×** / 1.01× / 11.17× |

---

## 2. O1 — Model-check the formal specifications, or find a counterexample

### 2.1 The repository and the commit

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi
git checkout ed3725d58c9747533c44a5e03298fd473693a9cb
```

### 2.2 The prerequisites

| the prerequisite | the detail |
|---|---|
| a JDK on `PATH` | the gate needs `java` and nothing else from the JDK. The repository builds with OpenJDK 25.0.4.1; the gate does not care which version |
| the pinned model checker | **it is in the repository**: `tools/tla2tools.jar` (4 504 421 bytes). The script verifies its SHA-256 itself and refuses to run against different bytes |
| nothing else | no network, no GPU, no model, no dataset, no database |

### 2.3 The command

```bash
bash scripts/formal-gate.sh
```

### 2.4 The expected observable result

**Exit code 0**, and this output:

```
  OK    BotEthicsPipeline
  OK    BrcStep
  OK    ConjugateBudgeterDP
  OK    Consensus
  OK    FederationConsensus
  OK    FrozenEthicalFNL
  OK    HashChain
  OK    KnowledgeAxes
  OK    KnowledgeLifecycle
  OK    KnowledgeStore
  OK    LearningStateMachine
  OK    MctsLatsVisit
  OK    MemoryM4Causal
  OK    MPDTNeuron
  OK    PromotionStage
=== the formal gate: 15 passed, 0 failed
```

**Measured on 2026-11-06:** exit 0, **18.8 s**, 15 specifications, 15 passed, 0 failed.

### 2.5 The acceptance criterion

> The gate exits **0** and prints `15 passed, 0 failed`; every specification's output
> contains the line **`No error has been found.`**

The gate is deliberately unsound-looking and sound: `-continue` is **not** passed, because
with it TLC's exit code is 0 even on a violated property. The script also checks the
output text, so a specification that produces no verdict counts as a failure.

### 2.6 The resource cost

19 seconds of one CPU. The memory ceiling per specification is the tool's own; the
script's timeout is 300 s per specification.

### 2.7 What a negative result would mean

**A negative result here is a serious finding, not a nuisance.**

| what you observe | what it means |
|---|---|
| a specification prints a **violated invariant** or an exception | **the property the system claims to hold does not hold in the model.** That is a defect in the specification or in the design, and it is exactly what the gate exists to find. The gate exits non-zero, and the failing specification is named |
| the gate stops with `the formal tool is not the pinned one` | the `tools/tla2tools.jar` in your checkout is not the bytes the specifications were model-checked with. The upstream release the jar came from was **recreated in place** (2026-11-06), so there is no content-addressed upstream copy to compare against — which is why the jar is pinned by digest in the repository (`docs-v2/engineering/W48-BASELINE.md` §2.5). Do not "fix" it by replacing the jar: that invalidates every property that was ever checked |
| the gate reports `the TLC jar is missing` | `tools/tla2tools.jar` was not fetched — it is tracked, so a shallow or partial clone may have skipped it |
| a specification times out (300 s) | the state space is larger than the timeout allows on your machine. That is a **result about the specification**, not a failure of the check: report the specification and the machine |

**The boundary of what this proves:** the specifications are models. A property that holds
in the model holds **in the model**. The gate says nothing about the implementation, and
the registry's own rule is that a capability may not be `FORMALLY_VERIFIED` merely because
a TLA+ file exists — it needs the model-check evidence, which is what this gate produces.

### 2.8 The interesting version of this opportunity

**Not "does the gate pass" — "does it still pass, and can you make it fail".** The
mutation harness (`scripts/mutation-harness.py`) kills mutants of the specifications to
prove that the gate can go red: `python3 scripts/mutation-harness.py --only FederationConsensus-veto-does-not-win`
is the recorded invocation. **One mutant survives** — `Consensus-evaluate-without-quorum`
— and DESIGN-58 §6a records that the Consensus entry therefore does not claim a checked
quorum. Finding that survivor, or killing it, is a real contribution.

---

## 3. O2 — Test the snapshot compatibility refusal

### 3.1 The repository and the commit

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi
git checkout ed3725d58c9747533c44a5e03298fd473693a9cb
```

### 3.2 The prerequisites

| the prerequisite | the detail |
|---|---|
| a JDK | the repository builds with OpenJDK 25.0.4.1 (`/home/alexandr-narbaev/jdk25` on the author's machine). Set `JAVA_HOME` if `java` is not the one you want |
| the Gradle wrapper | `./gradlew` is in the repository. The first run resolves the dependencies — **this needs the network once** |
| **nothing else** | no GPU, no model, no dataset, **and no database of yours** — the tests use temporary SQLite files of their own |

### 3.3 The command

```bash
JAVA_HOME=/path/to/jdk25 ./gradlew :matrix-core:test \
  --tests "io.matrix.knowledge.SnapshotRestoreCompatibilityTest" \
  --tests "io.matrix.knowledge.SnapshotRestoreEndpointTest" \
  --max-workers=1
```

### 3.4 The expected observable result

**Exit code 0**, `BUILD SUCCESSFUL`, and two JUnit XML files under
`matrix-core/build/test-results/test/`:

| the file | the result |
|---|---|
| `TEST-io.matrix.knowledge.SnapshotRestoreCompatibilityTest.xml` | `tests="12" failures="0" errors="0" skipped="0"` |
| `TEST-io.matrix.knowledge.SnapshotRestoreEndpointTest.xml` | `tests="1" failures="0" errors="0" skipped="0"` |

**Measured on 2026-11-06**, in a clean export of the commit (`git archive ed3725d5` into
an empty directory, so nothing of the author's tree could influence it): **exit 0,
`BUILD SUCCESSFUL in 17s`, 13 tests, 0 failures, 0 errors, 0 skipped.**

### 3.5 The acceptance criterion

> **13 tests, 0 failures, 0 errors, 0 skipped**, and the evidence is the JUnit XML — not
> the Gradle console summary.

The 13 are named in `docs-v2/engineering/W48-SNAPSHOT-COMPATIBILITY.md` §5. Six of the
twelve compatibility tests assert something stronger than "the restore is refused": they
assert that **the fingerprint of the whole database is identical before and after the
refusal** — the refusal leaves no trace.

### 3.6 The resource cost

17 seconds of one CPU after the dependencies are resolved. The tests use temporary
SQLite databases; they do not touch any database of yours.

### 3.7 What a negative result would mean

| what you observe | what it means |
|---|---|
| a test fails | **the refusal is not fail-closed** — either the legacy payload was restored over rows it cannot bring back, or the refusal itself wrote something. This is the defect class the W48-01 work closed (`4084538a`), and it is a P0: the loss is **silent** — the restore succeeds |
| a test errors rather than fails | the environment: a missing dependency, a SQLite that behaves differently, a filesystem that does not permit the temporary files. Report it as an environment problem, not as a defect — the distinction is the repository's own (a failure that is not understood is not reported as a defect of the code) |
| the fingerprint assertion fails | the most serious case: the refusal **left a trace**. The database changed while the restore was refused |

### 3.8 The version of this opportunity that is worth doing

**Not "do the 13 tests pass" — "design a case they do not cover".** The tests cover a
legacy payload, a v2 payload without the learning family, a complete payload, a payload
from a newer build, a corrupt payload, a failure during the restore, and a second
workspace. **They do not cover** a payload whose table list lies about its contents, a
restore that races a concurrent write, a target whose learning rows arrive between the
coverage check and the write, or a payload assembled from two different workspaces. The
contract under test is `SnapshotCoverage.of(tables)` — the coverage is derived from the
payload's **own table list**, not from its version number — and that derivation is the
place to attack.

**The boundary to state in any result:** the restore has been performed **against a copy**,
never over the live file (the restore is owner-held, `OD-029`), and the live
`workspace_snapshot` table holds **0 rows**, so the payload restore was not exercised by
the restore drill. Say so.

---

## 4. O3 — Reproduce the GPU benchmark on a different machine

### 4.1 The repository and the commit

```bash
git clone https://github.com/AlexanderNarbaev/agi.git
cd agi
git checkout ed3725d58c9747533c44a5e03298fd473693a9cb
```

### 4.2 The prerequisites — and the honest caveat

| the prerequisite | the detail |
|---|---|
| **an NVIDIA GPU** and `nvidia-smi` | without it the script exits **3** with `NOT VERIFIED` and writes nothing. That is by design: a CPU number reported as a GPU number is worse than no number |
| **the CUDA libraries** | the script resolves them from the repository's virtual environment (`.venv/lib/*/site-packages/nvidia/*/lib`) and refuses to run if the `libcudart` it finds does not export `cudaLibraryGetKernel` |
| **the ONNX Runtime GPU jar** | resolved by the Gradle build into the Gradle cache; the script looks for it there and exits 3 if it is absent |
| **the model files — NOT in git.** | `/models/` is ignored (`.gitignore:147`); only `models/bitnet/README.md` is tracked. The graph under test is `models/onnx/qwen05b/model.onnx` and its external weights `model.onnx_data` (2.5 GB), and the point-call model is `models/teacher/teacher_ffn16.onnx`. **This is the one prerequisite a stranger cannot get from a clone**, and it is why this opportunity is a *collaboration* rather than a clone-and-run |
| the digests, so the substitute is provably the same model | the graph `5f59f2cd3eb36963746ca22411f83bde429e9dece9722389653882dfc4e0145d`; the weights `72303ef1d77c02e4efc46a35b5cc60a841213518baa0ddae6247d44f1686265a`; the teacher graph `7378d5d7aa9d7ced30a5e62f6c51ce75b93d186c7ee650eac4d16025c3026cc9` |

**The consequence, stated plainly:** O3 is executable by a collaborator who has the model
files and a GPU. It is **not** executable by a stranger with only a clone, and the script
will tell them so (exit 3) rather than produce a number without a GPU.

### 4.3 The command

```bash
bash scripts/gpu-benchmark.sh
```

### 4.4 The expected observable result

**Exit code 0**, a preflight table, three runs (the MATRIX default, a plain CPU run and a
CUDA run), the raw per-iteration samples, the execution-profile proof and the summary
table. The artefacts are written under `matrix-core/build/gpu-benchmark/`.

**Measured on 2026-11-06** on an NVIDIA GeForce RTX 5070 Ti Laptop GPU (driver 595.99.02,
CUDA 13.2 supported, 12.4 toolkit, 12 227 MiB VRAM), OpenJDK 25.0.4.1, ONNX Runtime 1.29.0,
sequence length 32, warm-up 5, 20 iterations — **exit 0, 2 m 37 s**:

| the measurement | the CPU median | the CUDA median | the ratio |
|---|---|---|---|
| the transformer forward pass (the raw `session.run`) | 183.022 ms | 7.871 ms | **23.25×** |
| the transformer forward pass (MATRIX's own call, with the logits readback) | 189.406 ms | 13.908 ms | **13.62×** |
| the ONNX point call (the teacher FFN, batch 1) | 0.009 ms | 0.037 ms | **0.25× — the GPU is four times slower** |
| the BIR point call (MATRIX's own boolean runtime) | 0.081 µs | 0.080 µs | **1.01× — no difference; there is no GPU path** |
| the end-to-end path (tokenise, generate, detokenise) | 2 060.385 ms | 184.443 ms | **11.17×** |

and the execution-profile proof:

```
the execution profile of the CUDA run names: CPUExecutionProvider x1170, CUDAExecutionProvider x1431
the CUDA provider was observed: True
the CPU run's execution profile names: CPUExecutionProvider x2600
```

### 4.5 The acceptance criterion

> **Exit code 0**, **and the CUDA run's execution profile names `CUDAExecutionProvider`.**

The script treats that as a **hard failure (exit 5)**: "a CPU-only run reported as a GPU
result is worse than no result". The exit codes are: 0 completed and the CUDA provider
observed · 2 the script could not run · 3 NOT VERIFIED (no GPU, no usable CUDA libraries,
no GPU jar, or no model — **nothing was measured**) · 4 the provider could not be
registered · 5 the run completed but the profile did not name CUDA.

**The second criterion is the scientific one:** report the **three ratios separately** —
the forward pass, the point call and the end-to-end path — and never average them into
one number. The script reports each with its median and its dispersion (min / p95 / max /
sample standard deviation).

### 4.6 The resource cost

2 minutes 37 seconds, one GPU, and about 12 GB of VRAM for the model's weights. The
benchmark writes only into `matrix-core/build/gpu-benchmark/`, which is inside the
gitignored build directory.

### 4.7 What a negative result would mean

| what you observe | what it means |
|---|---|
| **exit 5** — the profile does not name CUDA | the CUDA provider did not execute. The cause is usually the loader: `LD_LIBRARY_PATH` must expose a `libcudart` that **exports `cudaLibraryGetKernel`**; the loader may otherwise find a different one. This is the same failure the registry records — the GPU is served **conditionally**, and the CPU provider serves the same route when the loader finds an unusable `libcudart` (row `W46-GPU`) |
| **exit 3** | nothing was measured — no GPU, no CUDA libraries, no GPU jar, or no model. **This is not a negative result; it is no result** |
| your ratio is far from 12–14× | **the most likely cause is the machine, not the code.** The W48 record run and this run agree on the qualitative picture and differ in the numbers (12.06× against 13.62× on the same machine, an hour apart; the CPU series has a 26.5 ms standard deviation on a 173.8 ms median). On a different GPU the ratio will differ — **that is the point of the exercise** |
| your point-call ratio is **not** ≈0.25× | the interesting result: the GPU's loss on the single-point call is a property of the launch overhead, and it is expected to depend on the CUDA version, the driver and the card. Find where the crossover is — at what batch size, on what card — and the negative half of the note becomes a general statement instead of a single observation |
| the BIR point call moves at all | the BIR path is **pure Java** and has no GPU implementation. If it appears to move, the difference is measurement noise — the recorded series has one garbage-collection pause of 14.988 µs in twenty samples, which is why the report gives the median and not the mean |

### 4.8 The claim this opportunity may and may not make

**May:** *on this workload class, on this machine, the CUDA provider is ≈12–14× faster on
the transformer forward pass as MATRIX calls it and ≈23× on the raw call, **0.25× —
slower — on the single-point call**, and it makes no difference to MATRIX's own boolean
runtime.*

**May not:** *"MATRIX is 12× faster with the GPU."* It is false in the case that matters
most — the per-call latency of a small inference — and false as a blanket statement about
a workload that has not been named. The historical "12.6×" is **bounded, not replaced**:
the three re-measurements land at 12.06×, 11.68× and 11.47×, all below it
(`docs-v2/engineering/W48-GPU-BENCHMARK.md`).

---

## 5. What makes an opportunity worth offering

Three properties, and all three must hold:

1. **It is executable by someone who has not met the authors.** A command, a commit and a
   stated prerequisite — nothing that depends on the author's machine state.
2. **It has an outcome that is not already known.** "Does the suite pass" is not an
   opportunity when the suite's state is already documented; "find a case the 13 tests do
   not cover" is.
3. **A negative result would be informative.** The best three here are negative-result
   shaped: a counterexample, a silent-loss defect, a workload where the accelerator is
   slower.

---

## 6. What was tried and what happened — the honest list

| the candidate | the attempt | the outcome |
|---|---|---|
| `scripts/quality-gate.sh` | run it on 2026-11-06 | **exit 1**, one violation: DOCS-1. The cause is that the wave's own files do not exist yet — the link checker reports `MISSING_PATH` for `docs-v2/engineering/W49-STATUS.md` <!-- no-link: the wave's own report, written by a concurrent task; the citation names its absence -->, `W49-NATIVE-BUDGET.md` <!-- no-link: the wave's own file, written by a concurrent task -->, `W49-PUBLIC-PRESENCE-AUDIT.md` <!-- no-link: the wave's own file, written by a concurrent task --> and for the three research documents this wave creates. **It becomes green when the wave closes**, so it is not offered here as an opportunity today — it is the wave's own completion criterion |
| `scripts/bench_gpu_vs_bir.py` (the EXP-009C torch script) | read it, checked its imports | it needs `torch`, `onnx` and a CUDA device, and it measures the **torch** path with a batch of 2 000 and 1 000 single calls. It is **not** the benchmark of record: the committed, self-verifying one is `scripts/gpu-benchmark.sh` with the Java harness, which measures the path MATRIX actually calls and can fail hard when the CUDA provider does not execute |
| the snapshot compatibility scope, **in the working tree** | run it on 2026-11-06 | **the tree did not compile**: `matrix-core/src/main/java/io/matrix/autonomy/ImpulseProposalConsumer.java:151` calls `record.shadowPrice()` on a `FireRecord` that has no such method. Those are **uncommitted files from a concurrent task**, not a defect of the committed tree. The scope was therefore executed in a **clean export of `ed3725d5`**, where it passed — and that is the run reported in §3 |

---

## 7. The boundary of this document

- **No opportunity here needs the owner's live database, the owner's `.venv`, or any
  artefact under `data/`.** O1 and O2 need a clone and a JDK. O3 needs the model files,
  and their digests are given so that a substitute can be proved to be the same model.
- **Nothing here is a publication.** The pipeline is
  `docs-v2/research/PUBLICATION-PIPELINE.md`; these are the inputs a paper would need.
- **A result that only exists in a chat is not recorded.** The repository's own rule: the
  journal is local and does not replace the wave report, and the negative-evidence index
  exists so that a refuted hypothesis is discoverable.
- **The success of a collaboration is not money, grants, publicity or contributor counts.**
  It is independent validation, research criticism, a reproducible improvement, a domain
  measurement that leaves `synthetic-scope`, and a recorded negative result. See
  `docs-v2/research/COLLABORATION-BRIEF.md` §10.
