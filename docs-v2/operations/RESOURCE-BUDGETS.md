# MATRIX — RESOURCE BUDGETS

**Status:** `CANONICAL` · **Date:** 2026-11-04 · **Measured on:** the owner's machine, the
probe window 2026-11-04 17:57–18:04 (Europe/Moscow).
**The full probe, with every command and every confidence label, is
`docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md`.** This document is the **budget**
— the limits an autonomous agent must respect.

---

## 0. The rule that governs every number below

**Every value carries its class:**

- **measured** — observed on this machine, with the command that observed it;
- **conservative default** — a policy chosen because the measurement is a moving sample;
- **hypothesis awaiting benchmark** — a value that has not been measured and must not be
  used as a fact.

**`MemAvailable` is a sample, not a budget.** It moved 38.2 → 40.3 → 38.7 GiB within five
minutes while another agent was working. Every memory limit below is therefore a
**conservative default** against the *available* figure, never against the *total*.

---

## 1. The machine, in the numbers that bind a build

| | the value | the class |
|---|---|---|
| the CPU | AMD Ryzen 9 9955HX, 16 physical cores, 32 logical, symmetric (every `core_id` appears exactly twice) | measured |
| the RAM | **59.5 GiB total**; **38–40 GiB available** | measured |
| the swap / zram | 37.8 GiB swap (unused); a 29.8 GiB zram | measured |
| the `/tmp` quota | **a tmpfs with a hard 30 GiB limit** | measured |
| the free disk | **198 GiB** on `/`; the build directories are 7.5 GB; `models/` is 14 GB | measured |
| the GPU | an RTX 5070 Ti, 12 227 MiB VRAM; the driver 595.99.02; the CUDA **compatibility** 13.2; the **toolkits** 12.4 and 13.1 | measured |
| the JDK | `/home/alexandr-narbaev/jdk25` — the PATH `java` is a **JRE** and the PATH `javac` is **JDK 21** | measured |
| GraalVM / Mandrel | **absent** | measured (an absence) |

---

## 2. The budgets

| the budget | the limit | the class | the evidence |
|---|---|---|---|
| **the Gradle worker count** | **16** | conservative default | today it is **unset**, so Gradle defaults to `nproc` = **32** workers on 16 physical cores — the SMT siblings are oversubscribed |
| **the Gradle daemon heap** | **4 g** | measured | `gradle.properties:2` — `-Xmx4g`; the live daemon's **peak RSS during a compile was 4.08 GiB** |
| **the test fork count** | **1** | measured (an absence) | no `maxParallelForks` anywhere in the repository: one test JVM at a time per module |
| **the test JVM heap** | **16 g** for matrix-core | conservative default (dangerous) | `matrix-core/build.gradle:119,121` — 16 g against ~38.7 GiB available means **two** concurrent matrix-core test runs **cannot both fit** |
| **the Quarkus application heap** | **4 g** in the production launcher; **the journey scripts must be given an explicit `-Xmx4g`** | conservative default / undeclared | `scripts/start_production.sh:13`; the journey scripts launch `java … -jar` with no `-Xmx` and inherit the ergonomic **14.88 GiB** |
| **the concurrent heavy builds** | **2** — one compile and one test — and **never two matrix-core test runs** | conservative default | a compile peaks at 4.08 GiB and takes 6.3 s; a test JVM may reach 16 GiB |
| **the parallel build agents** | **≤ 2** doing build work; **≤ 1** running a matrix-core test | conservative default | 1 test JVM (16 g) + 1 daemon (4.1 g) = 20.1 g against ~38.7 GiB with ~10 GiB already resident; **2 test JVMs (32 g) + 2 daemons (8.2 g) = 40.2 g exceeds `MemAvailable`** |
| **the model-loading budget** | **~3.3 GiB of VRAM** for the shipped qwen05b model (2.5 GB on disk) | measured | the model's data file is 2 520 669 824 B; `nvidia-smi` showed the java process holding **3250 MiB** |
| **the GPU memory budget** | **≤ 8 GiB** of the 12 227 MiB, keeping ≥ 4 GiB of headroom | conservative default | one model = 3.25 GiB; nothing else is resident except the compositor (14 MiB) |
| **the temporary-directory budget** | the **30 GiB** tmpfs quota; the repository scratch `data/tmp` under **5 GiB** | measured | ONNX Runtime extracts **583 MB per JVM** into `java.io.tmpdir` and does not clean it. Two such orphans were still on the disk on the day of the audit. `matrix-core/build.gradle:104-113` already redirects the *test* JVM's tmpdir to `data/tmp`; **the launcher and the journey scripts do not** |
| **the minimum free-disk threshold** | **30 GiB** on `/` | conservative default | 198 GiB free now; the build directories alone are 7.5 GB and `models/` is 14 GB |
| **the Docker cache** | **prune the 40.73 GB build cache** (37.02 GB reclaimable) before a heavy wave | measured | `docker system df` |
| **the benchmark duration by class** | micro-benchmarks **≤ 10 min**; a module suite **≤ 45 min**; the full repository suite **only with the owner's approval** | conservative default + the repository rule | `AGENTS.md` §Тестирование: *«Полный прогон — только по согласованию (риск OOM)»* |
| **the native-image build** | **impossible on this host** | measured (an absence) | no `native-image`, no GraalVM/Mandrel, and the mandrel builder image is not in the local docker |

---

## 3. The three rules that follow from the numbers

### 3.1 One matrix-core test run at a time — and why it is not a preference

The test JVM asks for **16 g**. The machine has **38–40 GiB available** with ~10 GiB already
held by the desktop and the harness. Two test JVMs plus two daemons is **40.2 g** — over the
available figure.

**This is not hypothetical.** A previous wave ran two concurrent Gradle builds in one module
and produced **16 spurious failures** (`NoClassDefFoundError` / `ClassNotFoundException`) in
the W43 scope; the scope passed cleanly on a solo re-run — **182 classes, 1563 tests, 0
failures**. The failure looked like a regression and was not one.

### 3.2 The `/tmp` quota is the most likely way to take a wave down

A previous wave recorded that every start leaked **583 MB** into `/tmp` until the quota took
the tools down. The leak is still live: ONNX Runtime extracts its native libraries into
`java.io.tmpdir` and does not clean them up.

**The rule:** every JVM an agent launches should have an explicit `java.io.tmpdir` that points
at the repository's `data/tmp` — exactly what `matrix-core/build.gradle:104-113` already
does for the test task — and a harness must clean up **only its own** temporary directory.

### 3.3 The machine's limits are not the project's limits

**The full `matrix-core` suite is not run here.** The W44 archaeology measured 9816
executions in one run; a full run needs a resource estimate and the owner's approval. The
**targeted scopes** are the project's normal verification, and they are fast: the cognitive
and epistemic scopes together are 910 tests in under a minute of wall time.

---

## 4. What may not be assumed

| the assumption | the truth |
|---|---|
| "32 logical CPUs means 32 safe parallel builds" | **no** — 16 physical cores, and the Gradle worker count is currently 32 by accident |
| "64 GB of RAM is available to the daemon" | **no** — 59.5 GiB total, 38–40 GiB available, ~10 GiB held by the desktop |
| "the CUDA compatibility version means the toolkit is installed" | **no** — 13.2 is what the driver supports; the installed toolkits are 12.4 and 13.1 |
| "a GPU on the machine means MATRIX uses it" | **no** — the ONNX CUDA registration **fails** today; the path is set as a JVM system property and a native `dlopen` consults `LD_LIBRARY_PATH` |
| "`/tmp` is the root filesystem" | **no** — it is a **30 GiB tmpfs quota** |
| "the system `java` can build the project" | **no** — it is a JRE without `javac`, and the system `javac` is JDK 21 while the project needs 25 |
| "the native-image tests are failing" | **no** — they **cannot run**: GraalVM is absent |
| "a stale test count is a baseline" | **no** — a baseline is commit-specific; see `docs-v2/engineering/TEST-CATALOGUE.yml` |

---

## 5. How to change a budget

A budget may be raised or lowered **only** with evidence:

- a **measured** change needs the command, the environment and the observation;
- a **conservative default** may be tightened on experience, and the experience must be
  written down here;
- a **hypothesis** may be used as a *starting* value but **never** as a fact, and it must be
  marked as such until it is measured.

Every change to this document is a change to a canonical governance document: it is recorded
in the decision register, and the change is pushed to the owner.
