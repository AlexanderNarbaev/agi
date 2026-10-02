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
| `w30-perf-20261002-133831.json` | `TsetlinClauseBenchmark`, **full budget** | clause-update numbers in `TUNING-PARAMETERS.md` §6b |
| `w30-perf-20261002-134016.json` | `MctsRolloutBenchmark`, **full budget** | MCTS numbers in §6b |
| `w30-perf-20261002-134128.json` | `SqliteMemoryBenchmark`, **full budget** | SQLite numbers in §6b |

Earlier `20261002-1329*` / `1332*` / `1333*` / `1334*` / `1336*` files are the `--quick`
smoke runs that validated each kernel before the full-budget pass; the full-budget files
above supersede them.

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

## A benchmark that measured nothing and reported success

`SqliteMemoryBenchmark` is worth a separate note because the failure mode is the one this
whole wave keeps meeting. Its first run failed in `@Setup` with `No suitable driver found
for jdbc:sqlite:` — and **JMH still exited 0, writing a valid but empty result JSON.** A
kernel can be added, compile cleanly, and produce no measurement at all while every
available signal says it worked.

It was caught by the filter post-condition in `perf-probe.sh` (`0 of 0 results match`),
which exists because of the earlier `-p include=` bug in this same file. The post-condition
paid for itself within the same wave it was written.

Root cause was two-layered: the `jmh` source set does not inherit `implementation`, and
the JMH fat jar holds five colliding `META-INF/services/java.sql.Driver` resources that
jar assembly overwrites rather than merges. Production is unaffected — this is a fat-jar
artefact.

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
