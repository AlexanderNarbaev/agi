# RECON-W30 benchmark evidence

Machine-readable results (`*.json`) and environment-stamped transcripts (`*.txt`) for the
microbenchmarks run in RECON-W30. `TUNING-PARAMETERS.md` quotes numbers from these files;
each number is traceable to one of them.

Every `.txt` opens with an environment block — host, CPU model, physical/logical core
counts, AVX2/AVX-512 availability, JVM version, active GC, warmup/measurement budget, and
free disk. **A benchmark number without that block is not evidence**, which is why the
transcripts are committed as `.txt` rather than `.log`: `.gitignore` excludes `*.log`, and
this directory is the provenance for the whole tuning document.

| File | What it is | Use it for |
|---|---|---|
| `w30-hdc-retrieval.json` | HDC Jaccard variants, first run, before the scratch-reuse methodology fix | superseded — see note below |
| `w30-perf-20261002-125143.json` | HDC variants, **corrected** run, filter verified 20/20 | the numbers quoted in `TUNING-PARAMETERS.md` §6 |
| `w30-perf-20261002-131739.json` / `.txt` | `GcPressureBenchmark`, quick budget | GC baseline run |
| `w30-perf-20261002-125114.json` | `GcPressureBenchmark`, quick budget | GC baseline run |
| `w30-perf-20261002-125514.json` | `GcPressureBenchmark`, quick budget | probe self-test after the filter fix |

## Deliberately not committed

`w30-perf-20261002-122937.json` is present in the working tree and **must not be
committed**. It is the run that exposed the filter bug: it was invoked with
`--include HdcRetrievalBenchmark` while its own log header claimed that filter was
applied, but JMH 1.37 treats `-p include=` as a property rather than a run filter, so it
executed the entire jar — **230 result entries across 6 benchmark classes**, 116 of them
from `PerformanceBenchmark`, none of which the log said it was running.

It is kept locally only as evidence of the defect. `perf-probe.sh` now verifies after
every filtered run that the JSON contains only matching benchmarks and exits non-zero
otherwise, so this class of silent mismatch cannot recur unnoticed.

## The methodology fix that changed a conclusion

The first `cosineReusedScratch` measurement reported 27.9 ns against production's 23.4 ns,
which reads as "scratch reuse is a pessimisation". That variant allocated its scratch
`BitSet` **inside the timed region**, so it was measuring allocation rather than reuse —
the exact thing it existed to avoid. After moving the scratch to a `@Setup` field, the
variant is 26.29 ns at dim=1024, still slower than `clone()` there, which is a real
property of `BitSet` at small widths rather than an artifact of the harness.

`w30-hdc-retrieval.json` holds the pre-fix numbers and is retained deliberately: the
corrected table and the wrong one differ, and keeping both makes the error legible to
whoever reads the benchmark next.
