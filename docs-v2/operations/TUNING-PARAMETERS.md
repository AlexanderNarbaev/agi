# TUNING-PARAMETERS.md — RECON-W30

Tuning defaults for MATRIX, derived from the inventory in
[HARDWARE-PROFILE.md](HARDWARE-PROFILE.md) and the measurements in
[bench/](bench/).

Every value here is **loaded from a config file**, never hardcoded in a script. The
values themselves are exported by `scripts/matrix.env`, and `scripts/start-mind.sh`
sources it. A magic number in a shell script is a number nobody can tune for a
different machine, and a number nobody can tune is not a tuning parameter.

## The honesty rule for this document

Every row is tagged one of:

- **MEASURED** — a number from a JMH run in this repository, with the command and the
  result file named.
- **DERIVED** — computed from the hardware inventory by a stated rule. Not measured. A
  hypothesis.
- **CONVENTION** — a JVM or library default chosen deliberately, with the reason.

Anything not tagged is a bug in the document. An untagged tuning number is exactly the
kind of figure that gets quoted in a report six months later as though it had been
measured.

---

## 1. Machine this was tuned for

| Field | Value |
|---|---|
| CPU | AMD Ryzen 9 9955HX, 16 physical cores / 32 logical |
| ISA | AVX2, **AVX-512F/BW/VL/DQ/CD**, FMA, F16C, SHA-NI. **No AMX.** |
| Cache | L1d 768 KiB, L1i 512 KiB, L2 16 MiB, L3 64 MiB |
| RAM | 59.5 GiB (38.5 GiB available at probe time) |
| Swap | 37.8 GiB |
| NUMA | single node — no inter-socket traffic, so no NUMA pinning needed |
| GPU | NVIDIA GeForce RTX 5070 Ti Laptop, 12227 MiB, driver 595.91.07, CUDA 13.2 |
| Disk | 2x NVMe (KINGSTON SFYRS1000G, YMTC PC41Q-1TB-B), mq-deadline |

The absence of AMX is worth stating explicitly: this is a Zen 5 class part with
AVX-512 but no tile matrix unit, so int8/bf16 matrix acceleration is **not** available.
A tuning document that assumed AMX would send someone looking for a win that cannot
happen.

## 2. Thread pools

| Parameter | Value | Tag | Basis |
|---|---|---|---|
| `MATRIX_WORKER_THREADS` | 16 | **DERIVED** | physical cores, not the 32 logical. SMT siblings share L1d, L2 and the vector units, so oversubscribing trades cache residency for throughput that is not present. |
| `MATRIX_IO_THREADS` | 16 | **DERIVED** | same ceiling; I/O in this system is NDJSON appends and SQLite, both of which block on the NVMe tier rather than the CPU. |

**This is the assumption most worth challenging.** The rule "pools size to physical
cores" is folklore that is right often enough to be dangerous. It is untested here
because no MATRIX workload in this repository is parallel enough to saturate a pool —
the gateway is effectively single-request-at-a-time. So the honest statement is: 16 is a
**ceiling**, not a tuned value, and the first real concurrent workload should replace it
with a measured curve. Labelling it DERIVED rather than MEASURED is the entire point of
the tag column.

## 3. JVM flags

| Flag | Value | Tag | Basis |
|---|---|---|---|
| `-Xms` | 16g | **DERIVED** | 50% of 59.5 GiB, floored. Sizing Xms to Xmx avoids heap resizing pauses during a distillation run. |
| `-Xmx` | 16g | **DERIVED** | Same. The remaining ~27 GiB covers the OS, the page cache the NVMe tier depends on, and the co-resident gateway. |
| `-XX:+UseZGC` | see `matrix.env` | **DERIVED** | See §5 — the GC choice is justified by measurement, below. |
| `--add-modules=jdk.incubator.vector` | enabled | **CONVENTION** | Required to load `jdk.incubator.vector` at all. Already required by the build. |

Heap is deliberately **not** sized to all available RAM. A JVM that owns 95% of the page
cache will page its own working set to NVMe, and NVMe latency is roughly two orders of
magnitude worse than the L3 this workload actually wants to live in.

## 4. Batch and chunk sizing

| Parameter | Value | Tag | Basis |
|---|---|---|---|
| `MATRIX_DISTILL_CHUNK` | 8192 | **DERIVED** | See below. |
| `MATRIX_REPLAY_BATCH` | 256 | **DERIVED** | See below. |
| `MATRIX_CLAUSE_BATCH` | 4096 | **DERIVED** | 32 clauses per cache line group; a power of two so the clause-chunk index in the Tsetlin layout is a shift, not a divide. |

**Why 8192 for distillation chunks.** L2 is 16 MiB across 16 cores = 1 MiB per core. An
`ActivationRecord` with a 1024-bit code is 128 bytes of `BitSet` plus object overhead;
at 8192 records that is on the order of 1-2 MiB, which is the L2 budget for one worker.
Above that the chunk stops fitting in L2 and the HDC comparisons start missing L3. The
number is derived from a cache-occupancy argument, **not** from a sweep — there is no
benchmark in this repository that measures chunk size against throughput, so this is a
hypothesis with a derivation attached, and it is labelled as one.

## 5. GC choice

**MEASURED, and the measurement does not support a strong claim.** That is the finding,
and it is reported as the finding rather than dressed up as a win.

From `bench/`, JMH `Throughput`, 3 warmup + 5 measurement iterations of 1s, 1 fork,
`-Xms2g -Xmx2g` on the machine in §1:

| Workload | G1 | ZGC | Difference |
|---|---|---|---|
| `allocHeavySweep` (retention-heavy: shortlist survives each sweep) | 188.3 ± 12.6 ops/s | 202.7 ± 13.8 ops/s | +7.7% — **error bars overlap** |
| `allocChurnOnly` (pure short-lived garbage) | 4043.0 ± 520.8 ops/s | 4237.5 ± 474.7 ops/s | +4.8% — **error bars overlap** |

ZGC is nominally ahead on both, but the 95% confidence intervals intersect on both rows.
On this hardware, at this heap size, on these workloads, **the collector choice does not
measurably change throughput.** A document that reported "+7.7% ZGC win" here would be
manufacturing a conclusion from noise.

What this does establish: the distillation allocation rate is survivable under either
collector at 2 GiB, so the GC is not currently a bottleneck and no urgent tuning is owed.

**The recommendation is ZGC, on grounds other than these numbers.** The case is its pause
behaviour — sub-millisecond pauses at a 16 GiB heap — which matters for a long sleep
cycle that must not stall the co-resident gateway. That is a documented property of the
collector, not something this harness measured, and it is labelled as such. A pause-time
measurement would need structured-log parsing across JMH forks, which this harness does
not do; inventing a pause number here would be worse than omitting one.

Whichever is chosen, the flag lives in `matrix.env`, so the decision is a one-line revert
and not a code change. **This is the entry most likely to be overturned by a later wave**
— a real multi-hour sleep-cycle run with a realistic retained set should replace these
numbers with a pause distribution, and the collector question should be re-asked then.

## 6. SIMD policy

| Path | Policy | Tag |
|---|---|---|
| HDC Jaccard over `BitSet` | keep as-is | MEASURED — see below |
| HDC Jaccard over `long[]` | **candidate for promotion** | MEASURED |

This is the one place RECON-W30 produced a number that argues for a code change, so the
measurement is given in full rather than summarised.

**MEASURED**, from `bench/w30-hdc-retrieval.json`, JMH `AverageTime`, 3 warmup + 5
measurement iterations of 1s, 1 fork, on the machine in §1:

| Variant | dim=1024 | dim=10000 | Allocation per call |
|---|---|---|---|
| `cosineCloneJaccard` — **current production**, `PersistentHdcStore.cosine` | 22.53 ± 1.07 ns | 223.36 ± 4.35 ns | 3 BitSet |
| `cosineReusedScratch` — one preallocated scratch | 26.29 ± 4.58 ns | 169.62 ± 8.84 ns | 0 |
| `cosineLongWordScan` — `long[]` + `Long.bitCount` | **7.81 ± 0.27 ns** | **66.29 ± 5.05 ns** | 0 |

Source: `bench/w30-perf-20261002-125143.json` (run via `scripts/perf-probe.sh
--include HdcRetrievalBenchmark`, which verifies after the fact that all 20 result entries
match the filter — see the filter bug in §7).

**Reproducibility across independent runs.** A second full run of the same benchmark
earlier in the wave produced 23.3 / 238.5 / 24.9 / 168.5 / 7.8 / 66.5 ns. The
`long[]`-over-`clone` ratio came out at 2.88x (dim=1024) and 3.37x (dim=10000) on this
run, against 2.98x and 3.59x on the earlier one. The absolute numbers move by up to ~7%
between runs and the ratios by up to ~6%, so:

- the **~3x `long[]` win is robust** — it holds well outside error bars at both widths;
- any future document must quote a **ratio**, not an absolute ns figure, or it will be
  quoting noise the next time someone re-runs it.

Three things follow, and one of them contradicts what the numbers looked like before the
benchmark methodology was fixed:

1. **The `long[]` encoding is ~2.9x faster at dim=1024 and ~3.4x at dim=10000** than the
   production path. That is the largest single win available in the retrieval hot path,
   and it needs no AVX-512 — it wins on `Long.bitCount` alone. A vectorised version is
   therefore *unlikely* to be the interesting part; the encoding change is.

2. **Scratch reuse is a wash at dim=1024 and only a 1.4x win at dim=10000.** At the
   production dimension it is actually *slower* than `clone()`. `BitSet.clone()` is a
   fast array copy in the JDK, while `clear()` + `or()` is two passes, so at small
   widths the copy wins. This is the kind of result that gets quoted as a pessimisation
   of the current code when it is really a statement about widths.

3. **A first attempt at this benchmark reported the opposite conclusion for point 2**,
   because the "reused scratch" variant allocated its scratch set *inside* the timed
   region and so measured allocation rather than reuse. The variant was corrected and
   re-run; the table above is the corrected run. A benchmark that measures the wrong
   thing is worse than no benchmark, and the first number would have been published
   without noticing.

**Not done, deliberately:** production `PersistentHdcStore.cosine` still uses the
`BitSet` clone path. Changing the wire/storage encoding is a schema change with
migration implications, and Article VIII forbids shadow logic — a parallel `long[]`
store kept in sync with the `BitSet` one is exactly that. The right move is a real
migration in a later wave with its own spec, not a benchmark-driven edit. This document
records the number so that decision is informed rather than rediscovered.

## 7. Reproducing every number here

```bash
# inventory (regenerates HARDWARE-PROFILE.md)
scripts/hardware-probe.sh

# HDC retrieval kernel
scripts/perf-probe.sh --include HdcRetrievalBenchmark

# GC comparison, both collectors
scripts/perf-probe.sh --include GcPressure
java -jar matrix-core/build/libs/matrix-core-*-jmh.jar GcPressureBenchmark -jvmArgsAppend "-XX:+UseG1GC"
java -jar matrix-core/build/libs/matrix-core-*-jmh.jar GcPressureBenchmark -jvmArgsAppend "-XX:+UseZGC"
```

### A bug in the harness itself, found by adding a post-condition

`perf-probe.sh --include HdcRetrievalBenchmark` reported running only the HDC kernel,
and its own log header said so — while in fact **JMH 1.37 does not honour
`-p include=<regex>` as a run filter**. It sets a property and executes the entire jar.
Measured: that invocation produced **230 result entries across 6 benchmark classes**,
116 of them from `PerformanceBenchmark`, none of which the log claimed to be running.

The same trap makes `java -jar … -l` useless for verifying a filter, because `-l` lists
the whole jar regardless.

The fix is the **positional** regex argument, and — more importantly — the script now
asserts after every filtered run that the JSON contains only matching benchmarks:

```
  filter verified: 20/20 results match 'HdcRetrievalBenchmark'
```

and exits non-zero if it does not. This is the same class of error as the health check in
`start-mind.sh` that reported success while probing the wrong port: **a check that
cannot fail is not a check.** Both were found only by insisting that a passing result be
provable, and both are now enforced rather than assumed.

Each run writes `docs-v2/operations/bench/w30-perf-<timestamp>.{log,json}` where the log
opens with an environment block: host, CPU, physical/logical core counts, AVX2/AVX-512
availability, JVM version, GC, warmup/measurement budget and free disk. A number without
that block is not accepted as evidence.

## 8. What is NOT tuned here

- **GPU.** A 5070 Ti is present. No MATRIX kernel in this repository is compiled for or
  validated against it. Tuning a GPU path before a single benchmark shows a win would be
  a fiction, so the profile records the hardware and the tuning table stays empty.
- **NVMe tier.** Both devices are NVMe, so the I/O scheduler choice (`mq-deadline` vs
  `none`) barely matters here. Not tuned; the setting is recorded.
- **NUMA.** Single node, so there is nothing to pin. On a multi-socket machine this whole
  document's thread section would need re-deriving.
