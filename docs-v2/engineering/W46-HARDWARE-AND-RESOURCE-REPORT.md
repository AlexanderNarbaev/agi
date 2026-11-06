# W46.0 — HARDWARE AND RESOURCE REPORT

**The machine as it is on 2026-11-04, probed fresh.**
**Probe window:** 2026-11-04, 17:57–18:04 (Europe/Moscow).
**Method:** the probe commands are listed; the independent probe and the repository's own
`scripts/hardware-probe.sh` were run side by side and compared. Nothing was installed, no
system setting was changed, no service was started, and **nothing in the repository was
written** (the probe script's default mode writes into the repo, so it was run with
`--stdout`).

**The previous baseline in the directive is historical.** The committed
`docs-v2/operations/HARDWARE-PROFILE.md` was generated on 2026-10-02 and is **stale in six
fields and misleading in one** — see §4.

---

## 1. The current profile

| the measurement | the value | the source | the confidence |
|---|---|---|---|
| the operating system | Ubuntu 26.04.1 LTS (resolute) | `cat /etc/os-release` | measured |
| the kernel | `7.0.0-42-generic #42-Ubuntu SMP PREEMPT_DYNAMIC` | `uname -a` | measured |
| the machine | MECHREVO JIAOLONG Series | `/sys/class/dmi/id/*` | measured |
| the CPU | AMD Ryzen 9 9955HX (family 26, stepping 0, BogoMIPS 4990.71) | `lscpu` | measured |
| the topology | 1 socket × 16 cores × 2 threads = **32 logical, symmetric** (every `core_id` appears exactly twice) | `lscpu`, `/sys/devices/system/cpu/cpu*/topology/core_id` | measured |
| the maximum frequency | 5460.5269 MHz | `lscpu` | measured |
| the NUMA topology | 1 node | `lscpu` | measured |
| the virtualization | AMD-V, bare metal | `lscpu` | measured |
| the ISA | `avx avx2 avx512f avx512bw avx512vl avx512dq avx512cd avx_vnni avx512_vnni fma f16c sha_ni sse4_2` | `lscpu` flags, exact-token match | measured |
| the AMX extensions | **absent** | the same | measured |
| the caches | L1d 768 KiB ×16, L1i 512 KiB ×16, L2 16 MiB ×16, L3 64 MiB ×2 | `lscpu` | measured |
| the RAM | **60 928.2 MiB = 59.5 GiB** total; **38–40 GiB available** (a moving sample) | `/proc/meminfo` | measured |
| the swap | 37.8 GiB total, **37.8 GiB free** (unused) | `/proc/meminfo` | measured |
| the zram | `zram0` 29.8 GiB; swappiness 60; THP `madvise` | `/sys/block/zram0/disksize`, `/proc/sys/vm/*` | measured |
| the GPU | NVIDIA GeForce RTX 5070 Ti Laptop GPU, **12 227 MiB VRAM** | `nvidia-smi` | measured |
| the GPU right now | 14 MiB used, 6 % utilisation, one process (`gnome-shell`) | `nvidia-smi` | measured |
| the NVIDIA driver | **595.99.02** (`nvidia-595-open`, from `7.0.0-42-generic`) | `nvidia-smi`, `/proc/driver/nvidia/version`, `modinfo nvidia` | measured |
| the CUDA **compatibility** | **13.2** — what the driver supports | the `nvidia-smi` banner | measured |
| the CUDA **toolkit** | **two toolkits installed: the distro `nvidia-cuda-toolkit 12.4.131` (`/usr/bin/nvcc`) and `cuda-toolkit 13.1.1` (`/usr/local/cuda-13.1`, `nvcc` 13.1.115)**. `CUDA_HOME`/`CUDA_PATH` are unset | `nvcc --version`, `ls -d /usr/local/cuda*`, `dpkg -l` | measured |
| the cuDNN | **9** present (`libcudnn.so.9`, `libcudnn_adv.so.9`); pip `nvidia-cudnn-cu12 9.25.1.1` and `-cu13 9.24.0.43` | `ls /usr/lib/x86_64-linux-gnu/libcudnn*`, the venv's `pip list` | measured |
| the storage | `nvme0n1` 953.9 G YMTC PC41Q-1TB-B → `/` (ext4, 476.9 G); `nvme1n1` 931.5 G KINGSTON SFYRS1000G → **unmounted**; `zram0` 29.8 G | `lsblk`, `df` | measured |
| the free disk | **198 GiB** on `/` (56 % used); 29.3 M free inodes | `df -h /`, `df -i /` | measured |
| **`/tmp`** | **a tmpfs with a hard 30 GiB quota**; 1.6 GiB used after the audit's cleanup; 1 048 576 inodes, 3 462 used | `df -h /tmp`, `df -i /tmp` | measured |
| the `java` on PATH | `/usr/lib/jvm/java-25-openjdk-amd64/bin/java` → **25.0.4.1**, and that directory holds only `java jpackage keytool rmiregistry` — **no `javac`** | `java -version`, `ls …/bin/` | measured |
| the `javac` on PATH | **21.0.12.1** — a different JDK | `javac -version` | measured |
| the real JDK | `/home/alexandr-narbaev/jdk25` (a real directory, not a symlink), `javac 25.0.4.1` | `javac -version` | measured |
| `JAVA_HOME` | **unset** in the ambient shell | `echo $JAVA_HOME` | measured |
| the default JVM heap | **14.88 GiB** (ergonomic 25 %), initial 0.94 GiB | `java -XX:+PrintFlagsFinal -version` | measured |
| the Gradle wrapper | **9.6.0** | `gradle/wrapper/gradle-wrapper.properties` | measured |
| the Gradle daemon | `org.gradle.jvmargs=-Xmx4g`; **no** `workers.max`, **no** `org.gradle.parallel`, **no** `maxParallelForks` anywhere in the repository | `gradle.properties`, `grep -rn` | measured (an absence) |
| the test JVM heap | `matrix-core/build.gradle:119,121` — `maxHeapSize = '16g'`, `jvmArgs '-Xmx16g'`; the other modules 1–4 g | `grep -rn maxHeapSize` | measured |
| the Quarkus application heap | `scripts/start_production.sh:13` `-Xmx4g -Xms2g`; `docker/docker-compose.yml:17` `-Xmx2g`; `Dockerfile:39` `-Xmx3g`; k8s `-Xmx512m`; **the journey scripts pass no `-Xmx`**, so they inherit the 14.88 GiB default | `grep -rn "\-Xmx"` | measured |
| the Docker engine | 29.9.3, **CPUs 32, memory 59.5 GiB** (no cgroup limits — the daemon sees the whole host) | `docker info` | measured |
| the Docker state | 11 containers (5 running: postgres 17, redis 7, qdrant, grafana, memorylayer), 15 images (10.18 GB), **a 40.73 GB build cache** (37.02 GB reclaimable), 3.09 GB of volumes | `docker ps`, `docker system df` | measured |
| the system Python | `/usr/bin/python3` = **3.14.4**, only `numpy 2.3.5` — **no torch, no onnx, no onnxruntime** | `python3 -m pip list` | measured |
| the repository venv | `.venv` (python 3.14.4): `onnxruntime 1.29.0`, `torch 2.14.0+cu130` (cuda 13.0, `is_available()=True`), numpy 2.5.2 | the venv's `pip list`, `import torch` | measured |
| the ONNX providers (Python) | `['TensorrtExecutionProvider', 'CUDAExecutionProvider', 'CPUExecutionProvider']`, `get_device() = 'GPU'` | the venv's python | measured |
| the ONNX providers (Java) | `getAvailableProviders() = [CPU, CUDA, TENSOR_RT]` **compiled in**, but the CUDA provider fails to load in MATRIX's own launch — see §6 | a probe compiled against the ORT jars | measured |
| GraalVM / Mandrel | **absent** — no `native-image`, no GraalVM/Mandrel directories, and the mandrel builder image is **not** in the local docker | `which native-image`, `ls /opt`, `docker images` | measured (an absence) |
| the build directories | **7.5 GB** (matrix-core 2.4 G, matrix-api-gateway 2.3 G, matrix-brain-runtime 1.6 G, matrix-tools-distill 1.4 G) | `du -ch */build` | measured |
| the working tree | 34 GB excluding `.git` (468 MB) and `node_modules` — `models/` alone is **14 GB** | `du -sh` | measured |

---

## 2. What `scripts/hardware-probe.sh` does, and what it misses

The script is 328 lines and is good at what it does: it pins `LC_ALL=C`, derives the fields by
label-matching `lscpu` (not by regex), converts `/proc/meminfo` KiB to GiB with 1048576, checks
the ISA flags by exact whitespace-token match, records absent tools as absent, and supports
`--stdout` (print) and `--json` (inventory). **Its default mode writes
`docs-v2/operations/HARDWARE-PROFILE.md` into the repository** — the audit used `--stdout`,
so the committed file was not touched.

**Its eleven real gaps**, each of which matters to an autonomous agent:

1. **It conflates the CUDA driver compatibility with an installed CUDA toolkit.** The `CUDA`
   row is parsed from the `nvidia-smi` banner (`scripts/hardware-probe.sh:288`), which is the
   driver's *compatibility* version (13.2). The script never runs `nvcc --version` and never
   looks at `/usr/local/cuda*`. **On this host the toolkits are 12.4 and 13.1 — neither is
   13.2.** A reader of the profile would believe CUDA 13.2 is installed. It is not.
2. **cuDNN is not probed at all** — and it is the second half of the GPU equation.
3. **The Java row describes a JRE.** It reports the PATH `java`, which here has no `javac`;
   the PATH `javac` is JDK **21**, not 25. The profile's "JVM" row therefore describes a
   runtime that cannot compile the project, and it never mentions that the real JDK lives at
   `/home/alexandr-narbaev/jdk25`.
4. **`/tmp` is invisible.** The profile reports only the root filesystem. `/tmp` is a
   **30 GiB tmpfs quota** — the exact resource that has taken down a wave before.
5. **Gradle is not probed**: no daemon heap, no worker count, no `maxParallelForks`, no test
   JVM heap — the numbers that actually bound a build.
6. **Docker is not probed**: no CPU/memory limits, no image or cache footprint (40.73 GB of
   build cache here).
7. **The Python/ML stack is not probed** beyond "python3 exists". It cannot tell that the
   system python has no torch or onnxruntime, and that the repository's `.venv` has torch
   2.14.0+cu130 with a working CUDA execution provider.
8. **ONNX Runtime's actual execution providers are not probed** — the profile reports the
   *card*, not whether any MATRIX code path can use it.
9. **GraalVM/native-image is not probed** — a native build is impossible here and the profile
   does not say so.
10. **The build-directory sizes are not probed** (7.5 GB now, plus 14 GB of models).
11. **`Virtualization: unavailable`** is read from a `Hypervisor vendor` row that does not
    exist on bare metal; the machine reports `Virtualization: AMD-V`.

**The repairs to the probe belong to W46.3** (a script change is inside the agent's remit).
**The regeneration of the committed profile belongs to W46.2** — it writes into the
repository, and W46.0 is read-only.

---

## 3. The resource envelope

Every value is classified: **measured** (observed on this machine), **conservative default**
(a policy chosen because the measurement is a moving sample), or **hypothesis awaiting
benchmark**.

| the limit | the value | the class | the evidence |
|---|---|---|---|
| the Gradle worker count | **16** (the recommended setting; today it is **unset**, so Gradle defaults to `nproc` = 32) | conservative default | no `workers.max` anywhere; 32 workers on 16 physical cores oversubscribes the SMT siblings |
| the Gradle daemon heap | **4 g** | measured | `gradle.properties:2`; the live daemon reports `-XX:MaxHeapSize=4294967296`, 1.79 GB committed, 978 MB used, **a 4.08 GiB RSS peak** during a compile |
| the test fork count | **1** | measured (an absence) | `grep -rn maxParallelForks` → 0 hits: one test JVM at a time per module |
| the test JVM heap (matrix-core) | **16 g** | conservative default (dangerous) | `matrix-core/build.gradle:119,121`; 16 g against ~38.7 GiB available means **two** concurrent matrix-core test runs cannot both fit |
| the Quarkus application heap | **4 g** for the production launcher; **the journey scripts must be given an explicit `-Xmx4g`** instead of the 14.88 GiB default | conservative default / undeclared | `scripts/start_production.sh:13`; the journey scripts launch `java … -jar` with no `-Xmx` |
| the memory budget for parallel subagents | **≤ 2 agents doing build work; ≤ 1 matrix-core test run** | conservative default | 1 test JVM (16 g) + 1 daemon (4.1 g) = 20.1 g, against ~38.7 GiB available with ~10 GiB already resident in the desktop and the harness; **2 test JVMs (32 g) + 2 daemons (8.2 g) = 40.2 g exceeds `MemAvailable`** |
| the concurrent heavy builds | **2** (one compile + one test), **never two matrix-core test runs** | conservative default | a compile is cheap (a 4.08 GiB peak, 6.3 s); a test JVM may reach 16 GiB |
| the model-loading budget | **~3.3 GiB VRAM** for the shipped qwen05b model (2.5 GB on disk) | measured | `models/onnx/qwen05b/model.onnx_data` = 2 520 669 824 B; `nvidia-smi` during the run showed the java process holding **3 250 MiB** |
| the GPU memory budget | **≤ 8 GiB** of the 12 227 MiB, keeping ≥ 4 GiB of headroom | conservative default | one model = 3.25 GiB; nothing else is resident except the compositor (14 MiB) |
| the temporary-directory budget | **the hard 30 GiB tmpfs quota**; the repository scratch `data/tmp` under **5 GiB** | measured | `df -h /tmp` → `tmpfs 30G`; ONNX Runtime extracts **583 MB per JVM** into `java.io.tmpdir` and does not clean it |
| the minimum free-disk threshold | **30 GiB** on `/` | conservative default | 198 GiB free now; the build directories alone are 7.5 GB and `models/` is 14 GB |
| the Docker cache | **prune the 40.73 GB build cache** before a heavy wave (37.02 GB reclaimable) | measured | `docker system df` |
| the maximum benchmark duration by task class | micro-benchmarks ≤ 10 min; module suites ≤ 45 min; **the full repository suite is not to be run without the owner's approval** | conservative default + the repository rule | `AGENTS.md` §Тестирование: *«Полный прогон — только по согласованию (риск OOM)»* |
| the native-image build | **impossible on this host** | measured (an absence) | no `native-image`, no GraalVM/Mandrel, and `matrix-core/build.gradle:485` pins a mandrel builder image that is a multi-GB pull |

**A live leak, found during the probe:** **two orphan ONNX Runtime directories of 583 MB
each** were sitting in `/tmp` from 14:37 and from 17:24 on the day of the audit — the
extraction of the previous session's own work. `matrix-core/build.gradle:104-113` already
redirects the *test* JVM's `java.io.tmpdir` to `data/tmp` for exactly this reason; the
**launcher and the journey scripts do not**. The audit removed its own artefacts; the two
orphans are recorded here for the owner.

---

## 4. The discrepancies against the committed profile

| the field | the committed profile (2026-10-02) | now (2026-11-04) | which is right |
|---|---|---|---|
| the kernel | `7.0.0-34-generic` | `7.0.0-42-generic` | now |
| the JVM | `25.0.4` | `25.0.4.1` — **a JRE** | now |
| the driver | `595.91.07` | **595.99.02** | now (`nvidia-smi` and `modinfo` agree) |
| the free root | 108 GiB | **198 GiB** | now |
| the root device | `/dev/nvme1n1p2` | `/dev/nvme0n1p2` | now (the NVMe enumeration order changes across boots) |
| the `nvme0n1` model | KINGSTON SFYRS1000G 931.5 G | **YMTC PC41Q-1TB-B 953.9 G** | now |
| the `nvme1n1` model | YMTC PC41Q-1TB-B 953.9 G | **KINGSTON SFYRS1000G 931.5 G** | now |
| the available RAM | 15.8 GiB | 38–40 GiB | now (an instantaneous sample, not a ceiling) |
| the `CUDA` row | 13.2 | 13.2 (compatibility) — **the toolkits are 12.4 and 13.1** | **neither is complete**: the row never meant "toolkit" |
| the Gradle daemon heap | absent | `-Xmx4g` | — |
| the `/tmp` quota | absent | **a 30 GiB tmpfs** | — |

---

## 5. The build cost, measured

`JAVA_HOME=/home/alexandr-narbaev/jdk25 ./gradlew :matrix-core:compileJava --rerun-tasks`,
with `/proc/<pid>/status:VmRSS` sampled at 2 Hz for every java process:

| the quantity | the value |
|---|---|
| the wall time (a warm daemon, incremental) | **6.29 s** |
| the Gradle **daemon** — the peak RSS | **4182.3 MiB = 4.08 GiB** (the heap used 978 MB, committed 1.79 GB, 96 threads) |
| the Gradle **build/launcher** JVM — the peak RSS | **532.4 MiB** |
| the Gradle **client** JVM — the peak RSS | **123.7 MiB** |

**The honest limit:** this is **one incremental compile with a warm daemon**, not a cold
build. A cold `--rerun-tasks` recompile of matrix-core (≈ 3000 sources) was **not** measured,
and the peak RSS of a matrix-core **test** JVM was **not** measured — running the module
suite is the OOM-risk action that `AGENTS.md` gates behind the owner's approval.

---

## 6. The GPU truth — resolved

**The answer: the GPU is fully available and usable, MATRIX's Java path does not use it, and
the reason is one missing environment variable. The GPU *is* measurably faster for a
transformer forward pass — 12.6×.**

### 6.1 The evidence

1. **The hardware and the driver are healthy.** `nvidia-smi`: RTX 5070 Ti Laptop, 12 227 MiB,
   driver 595.99.02, CUDA compatibility 13.2, 14 MiB used, 6 % utilisation.
2. **The GPU build of ONNX Runtime is on the classpath and the CUDA provider is compiled in.**
   `matrix-core/build.gradle:77` declares `com.microsoft.onnxruntime:onnxruntime_gpu:1.29.0`;
   the probe printed `getAvailableProviders() = [CPU, CUDA, TENSOR_RT]`.
3. **The registration fails in MATRIX today, and the adapter swallows it.**
   `OnnxRuntimeAdapter.java:104-114` calls `options.addCUDA(cudaOpts)` inside a `try/catch`
   whose catch logs *«CUDA not available, falling back to CPU»* and continues. Running the
   probe exactly as MATRIX runs it produced:
   ```
   addCUDA() FAILED: OrtException: ORT_FAIL … provider_bridge_ort.cc:1988 …
     Failed to load library …/libonnxruntime_providers_cuda.so with error:
     undefined symbol: cudaLibraryGetKernel, version libcudart.so.12
   ```
   **Verified by the coordinator in the source:** `matrix-core/build.gradle:135-155` builds the
   library path into the JVM system property `java.library.path` — but ONNX Runtime loads the
   provider with a **native `dlopen`**, and a native `dlopen` consults the process's
   **`LD_LIBRARY_PATH` environment variable**, not `-Djava.library.path`. Without it the
   loader finds the distro's `libcudart.so.12` (CUDA **12.4.127**), which does not export
   `cudaLibraryGetKernel`:
   ```
   nm -D --defined-only /usr/lib/x86_64-linux-gnu/libcudart.so.12 | grep -c cudaLibraryGetKernel → 0
   nm -D --defined-only .venv/…/nvidia/cuda_runtime/lib/libcudart.so.12 | grep cudaLibraryGetKernel
     0000000000075a60 T cudaLibraryGetKernel@@libcudart.so.12   → present
   ```
4. **With `LD_LIBRARY_PATH` exported, the provider loads and the GPU is used.** The same
   probe, the same classpath, one environment variable added: `addCUDA() OK`,
   `createSession OK: cudaAdded=true`, and `nvidia-smi --query-compute-apps` showed the java
   process holding **3250 MiB**.
5. **An equivalent-workload comparison** — the same model
   (`models/onnx/qwen05b/model.onnx`, which is `OnnxRuntimeAdapter.DEFAULT_MODEL`,
   `OnnxRuntimeAdapter.java:48`), the same sequence length (32), the same iteration count (10
   after one warm-up), the same machine, the same minute:

   | the execution provider | the ms per forward |
   |---|---|
   | the CPU | **191.367 ms** |
   | the CUDA | **15.190 ms** |

   → **12.6× faster on the CUDA provider.**
6. **The historical EXP-009C still reproduces.** `scripts/bench_gpu_vs_bir.py` re-run today:
   `device=NVIDIA GeForce RTX 5070 Ti Laptop GPU gpuBatchMs=0.0208 gpuSingleUs=17.49`,
   against the retained `gpuBatchMs=0.0200`, `gpuSingleUs=17.25` in
   `docs-v2/research/reports/EXP-009-report.md:27-28` — **within 1.4 %**, with torch
   2.14.0+cu130 instead of the recorded 2.12.1+cu130.

### 6.2 The status of each historical claim

| the claim | the status today |
|---|---|
| *"the GPU is unavailable to this machine"* (recorded in `H-009-distillation.md:55-57` as *"Failed to find CUDA shared provider"*) | **FALSE.** It was a loader-path fact, not a hardware fact |
| *"the GPU is used by MATRIX"* | **FALSE for the ONNX path.** The adapter requests the GPU (`useGpu = true`, `OnnxRuntimeAdapter.java:54`) and silently gets the CPU |
| *the diagnostic that reports which provider is active* | **FALSE — it cannot report the truth.** `OnnxRuntimeAdapter.java:118-123` sets `activeProviders = new String[]{"CPUExecutionProvider"}` **unconditionally**, with the comment *«Best-effort: just record what we know»*. ONNX Runtime 1.29.0 exposes no `OrtSession.getProviders()` (verified with `javap`), so the field is a **hardcoded string**: any dashboard reading it shows "CPU" whether the GPU is in use or not |
| *"the GPU is measurably faster"* | **TRUE for a transformer forward pass** (12.6× measured today) and **FALSE for point calls** (EXP-009C: 17.49 µs per call against ~62 ns for a BIR evaluation — the GPU's launch overhead dominates; `EXP-009-report.md:33`) |

**The historical EXP-009 results are retained and are not generalised.** The torch path
reproduces; the ONNX path does not use the GPU; and the two paths must not be conflated.

### 6.3 What this costs and what it needs

- **The fix is one environment variable in the launcher** — `LD_LIBRARY_PATH` pointing at the
  venv's `nvidia/*/lib` and `/usr/local/cuda/lib64` — and it turns **191 ms into 15 ms** per
  transformer forward on the model MATRIX already ships.
- **It is a runtime behaviour change**, so it is **Level 2 at least** and touches the serving
  path. It is **not** made in W46.0, which is read-only, and it is put to the owner as
  question `Q-W46-07` and in the consolidation plan.
- **The lying diagnostic is a separate defect**: `activeProviders()` should report *requested*
  versus *observed*, or be renamed so that it stops implying a measurement. ONNX Runtime
  1.29.0 offers `OrtEnvironment.getEpDevices()` and the registration exception as the honest
  sources.

---

## 7. The measurements that could not be taken, and why

| not measured | why |
|---|---|
| the cold full-recompile cost | a cold `--rerun-tasks` on a cold daemon would need minutes of a machine other agents are using |
| the peak RSS of a **matrix-core test JVM** (the 16 g ceiling) | running the module suite is the OOM-risk action `AGENTS.md` gates behind the owner's approval; the 16 g figure is read from the build file |
| whether the distro's CUDA 12.4 could satisfy a different ONNX Runtime build | it would need another artifact |
| the native-image build | no GraalVM/Mandrel locally and no mandrel image in docker; pulling the builder is a multi-GB download |
| the per-container Docker limits and the real memory of the five running services | `docker info` reports no cgroup limits, and measuring would mean starting containers |
| the energy cost of the GPU leg | `H-009-distillation.md:47-48` already rules the laptop wattmeter out as non-representative |
| whether the second NVMe can host the model cache | it is unmounted, and mounting it changes a system setting |

---

## 8. The uncertainty that must be carried forward

1. **The build-cost number is incremental, not cold.**
2. **`MemAvailable` is a sample, not a budget.** It moved 38.2 → 40.3 → 38.7 GiB within five
   minutes while another agent was working. Every memory recommendation here is therefore a
   *conservative default*, not a measurement of what is free.
3. **How much of the 59.5 GiB the desktop holds** (Chrome, Firefox, the harness web UI) was
   not summed.
4. **The CUDA compatibility (13.2) against the toolkits (12.4, 13.1)** was not tested by
   compilation.
5. **The NVMe model swap** between the committed profile and today is an observation, not a
   diagnosis: the enumeration order changes across boots, or the disks were re-seated.
6. **The ONNX timing used 10 iterations after one warm-up** — a comparison, not a
   benchmark-grade statistic.
