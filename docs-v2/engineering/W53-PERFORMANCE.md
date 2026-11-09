# W53 — The performance of the distributed path, stage by stage

**Status:** measured for one task at a time **and** under a bounded concurrency of four;
the cold start is separated from the steady state. · **Machine-readable form:**
[`W53-PERFORMANCE.json`](W53-PERFORMANCE.json) · **Procedure:**
`bash scripts/w53-distributed-benchmark.sh --tasks 30 --concurrency 1` and
`bash scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 4`

---

## 1. What was measured, and what was not

The workload is one cognitive task sent from chain A to chain B and back: chain A receives
`POST /v1/cognitive/task`, writes the dispatch intent, signs and sends the request across
the bridge, chain B authenticates it, admits it into its durable inbox, runs its production
cognitive path, persists the result and answers with a signed response and a signed report;
chain A verifies both signatures, admits the report into its own epistemic model and
evaluates it.

Two clocks are involved and they are not the same clock:

- **the client's round trip** — the wall time from `POST` to the answer, measured by the
  benchmark's client;
- **chain A's own stages** — the parts chain A can time inside itself, measured with a
  monotonic clock at the four points where the task changes state.

The report states the first as `endToEndMillis` and the second as `stagesMillis`. They do
not have to agree: the client's round trip includes the HTTP stack on both sides, chain B's
own work, and the scheduling of the machine.

**The stage times do not separate chain B's stages from the network round trip.** Chain A
times the crossing as one interval (`dispatch_nanos`), because the stages inside the peer
are the peer's to measure. This report does not pretend otherwise.

## 2. The environment

| | |
|---|---|
| OS | Ubuntu 26.04.1 LTS |
| JDK | openjdk 25.0.4.1 |
| Hardware | x86_64 |
| JVM arguments | the defaults of the Quarkus fast-jar and of the gateway's start script |
| Payload | one sentence of input, one expectation |
| Random seed | none — the workload has no randomness |

**These numbers are not comparable with a run on another machine.** They are the numbers of
this machine, in this configuration, on this day.

## 3. The end-to-end numbers (30 tasks, one at a time)

| p50 | p95 | p99 | min | max | mean | throughput |
|---|---|---|---|---|---|---|
| **20.77 ms** | **28.25 ms** | 330.83 ms | 16.34 ms | 330.83 ms | 31.42 ms | **31.83 tasks/s** |

**The p99 is not claimed.** One of the thirty samples took 330.83 ms while the p95
is 28.25 ms — that sample is the cold start (the first write to a fresh database,
the class loading, the peer's first cycle) — and a sample of thirty does not establish a
p99, so the report says so. The throughput figure is the sum of the round trips divided
into the elapsed time — with one task at a time it is the reciprocal of the mean latency,
not a saturation measurement.

## 4. The stages, from chain A's own clock

| Stage | p50 | p95 | What it covers |
|---|---|---|---|
| `intent_written_nanos` | 0.81 ms | 1.27 ms | chain A writes the dispatch intent **before** the send — the durable record exists before the request leaves |
| `dispatch_nanos` | 6.60 ms | 12.52 ms | signing the request, the network round trip, chain B's authentication, its durable inbox write, the production inference, the signed answer and its verification |
| `report_admission_nanos` | 7.65 ms | 12.36 ms | the report entering chain A's epistemic model through chain A's own bridge receiver |
| `assessment_nanos` | 0.010 ms | 0.034 ms | the epistemic evaluation of the answer |
| `total_nanos` | **17.23 ms** | 24.84 ms | the whole task, as chain A can see it |

Two facts about these numbers are worth stating plainly.

**The epistemic evaluation is nearly free; the admission of the report is not.** The
assessment takes 10 microseconds — it is a comparison of what the report claims against what
chain A already holds. The admission of the report takes 7.7 milliseconds, because the
report enters through chain A's own bridge receiver, which checks the signature, the
version and the rights, and writes the result into the knowledge store. **The expensive part
of receiving a remote answer is not the judgement about it; it is letting it in.**

**Chain A's own total (17.2 ms) is smaller than the client's round trip (20.8 ms).** The
difference is the HTTP stack, the scheduling and the part of the work chain A cannot time
from the inside. Neither number is the "true" latency; they answer different questions.

**Every one of the thirty samples was adjudicated `UNRESOLVED`**, with the reason recorded
in the report: the peer's confidence rests on `MEASURED`, which is not independent evidence,
and chain A's own records settle nothing. The performance of the path and the epistemic
outcome of the path are two separate facts, and this run happened to produce the honest
answer thirty times.

## 5. The concurrent path: measured, after two defects were fixed

`bash scripts/w53-distributed-benchmark.sh --skip-build --tasks 24 --concurrency 4`

The run **finished**, all 24 tasks were measured, and every one of
them was adjudicated `UNRESOLVED` — the same epistemic result as the single-task path.

### 5.1 Two defects stood between the first attempt and this one

**The first defect was in chain A's knowledge store, and it was real.** The store owns one
SQLite connection, and every write opens a transaction on it. The write path
(`applyChanges`) was *not* serialized, while the class's other write methods were. Two
writers therefore interleaved on one connection: the first writer's `setAutoCommit(false)`
was undone by the second writer's `setAutoCommit(true)`, and the first writer's `commit()`
then failed with `SQLException: database in auto-commit mode` — the transaction was lost,
and the remote report that was being written vanished with it. The fix is to serialize the
store's write transactions on the store's own monitor, the convention the rest of the class
already used. The regression test is
`matrix-core/src/test/java/io/matrix/knowledge/KnowledgeStoreConcurrencyTest.java`: four
writers, one store, one workspace, and the check is the **state** afterwards — every claim
is on record, and still on record after a restart. With the serialization removed, both of
its tests go red.

**The second defect was in this measurement, and it was mine.** The benchmark's concurrent
branch ended with a bare `wait`, which waits for *every* background job of the shell — and
the two servers are background jobs of that shell. The run therefore stopped after the
first batch and hung there, waiting for chain A and chain B to exit. The earlier note in
this report that the concurrent run "did not finish" was true, but its cause was partly
the measurement itself. The script now waits for its own task jobs and for no others.

### 5.2 The numbers

| | the whole run | the cold start | the steady state |
|---|---|---|---|
| the samples | 24 | 4 | 20 |
| p50 | 36.31 ms | 358.46 ms | 35.57 ms |
| p95 | 358.46 ms | 358.83 ms | 51.53 ms |
| the maximum | 358.83 ms | 358.83 ms | 55.31 ms |
| the mean | 89.63 ms | 358.47 ms | 35.86 ms |
| the throughput | 11.16 tasks/s | — | 27.89 tasks/s |

**The cold start is separated, and the rule is stated in the report itself:** the first
4 samples are the cold start — the first write to a fresh
database, the class loading of the dispatch path, and the peer's first cognitive cycle.
The single-task run shows the same shape: its p50 is
20.77 ms while its maximum is
330.83 ms, and that maximum is the first sample.

**What the concurrent path costs.** The per-task latency is higher than the single-task
path's (35.57 ms against 20.77 ms at p50): four
tasks in flight contend for the same knowledge store, for the same peer process and for the
same durable writes. The steady-state throughput of
27.89 tasks/s is therefore *below* the
single-task path's 31.83 tasks/s — the concurrency does
not buy throughput on this path, and the report says so rather than hiding it behind the
throughput of a run without the cold start.

**What the p99 is.** The run has 24 samples. Its p99 is a sample,
not a percentile of a population, and it is not quoted as one.

## 6. A correction that belongs in this report

An earlier report of this wave called a repeated refusal — `the request id req-w53-bench-N
is already dispatched` — a concurrency defect in the distributed path. It was **correct
behaviour**: chain A's cognitive database is persistent by design, the benchmark reused the
request ids of the previous run, and the durable idempotency refused a request id it had
already seen. That refusal is the feature working.

The benchmark now stamps every run (`w53-bench-<runStamp>-<index>`), and the report states
the stamp, so a re-run measures the path rather than the idempotency.
`CognitiveStoreConcurrencyTest` shows that four threads with four distinct request ids are
all recorded on one store — chain A's *cognitive* store is sound. The store that was **not**
sound under concurrency was chain A's *knowledge* store, and it is fixed now: its write
transactions are serialized, and `KnowledgeStoreConcurrencyTest` holds the line.

## 7. What this report does not claim

- it does not claim a p99 from a sample of thirty, nor from a sample of twenty-four;
- it does not separate chain B's stages from the network round trip;
- it does not claim that these numbers are comparable with a run on another machine;
- it does not claim that the concurrent path is faster than the single-task one — it is
  slower per task, and the throughput number includes the cold start;
- **no optimization was accepted on the strength of this run alone** — nothing was
  optimized, because nothing was profiled as a profile; these are the first measurements of
  the path, not a comparison against a baseline.
