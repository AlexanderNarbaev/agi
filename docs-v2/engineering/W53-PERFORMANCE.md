# W53 — The performance of the distributed path, stage by stage

**Status:** measured for one task at a time **and** under a bounded concurrency of four;
the cold start is separated from the steady state; the two processes' own accounting is
measured too. · **Machine-readable form:**
[`W53-PERFORMANCE.json`](W53-PERFORMANCE.json) · **Procedure:**

```bash
bash scripts/w53-distributed-benchmark.sh --tasks 30 --concurrency 1 --report /tmp/single.json
bash scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 4 --report /tmp/concurrent.json
python3 scripts/w53-merge-performance.py /tmp/single.json /tmp/concurrent.json \
  docs-v2/engineering/W53-PERFORMANCE.json
```

The two workloads are measured in two runs on purpose: the cold start belongs to the first
one and must not be hidden inside the second. The merge keeps both, names each section
after its concurrency, and averages nothing.

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

**The stage times separate the peer's own work from the crossing only as far as the peer
reports it.** Chain A times the crossing as one interval (`dispatch_nanos`), and the peer
reports the duration of its own execution (`peer_execution_nanos`); the difference is the
network, the signing and the peer's own bookkeeping. The stages inside the peer are the
peer's to measure, and the peer measures only its total. This report does not pretend
otherwise.

**Two paths are measured, apart.** The first is the first execution of a task. The second
is the replay of the same request — the same request id, the same payload — which the
peer answers from its durable record. §10.2 of the mandate asks for the duplicate-request
handling to be measured separately from the initial execution, and §5b holds those numbers.
The two passes have their own latency, their own stages and their own process metrics.

## 2. The environment

| | |
|---|---|
| OS | Ubuntu 26.04.1 LTS |
| JDK | openjdk 25.0.4.1 |
| Hardware | x86_64 |
| JVM arguments | the defaults of the Quarkus fast-jar and of the gateway's start script |
| Payload | one sentence of input, one expectation |
| Random seed | none — the workload has no randomness |
| the code the numbers belong to | the wave's own tree, which the wave's commits record: `dfcb3b00` on `feature/w53-distributed-cognition` |
| the code that is **not** on the measured path | chain A's reconciliation queue (`CognitiveStore.unfinishedDispatches`), which was changed after the measurement: it is read when chain A starts, and the measured path is the task path |

**These numbers are not comparable with a run on another machine.** They are the numbers of
this machine, in this configuration, on this day.

The process metrics of §4a were sampled with the JVM's own GC log enabled for chain A
(`-Xlog:gc`), which the benchmark adds to the process it measures; the JVM arguments above
are the ones of the run itself, plus that log.

## 3. The end-to-end numbers (30 tasks, one at a time)

| p50 | p95 | p99 | min | max | mean | throughput |
|---|---|---|---|---|---|---|
| **27.43 ms** | **37.73 ms** | 323.31 ms | 24.44 ms | 323.31 ms | 38.20 ms | **26.18 tasks/s** |

**The p99 is not claimed.** One of the thirty samples took 323.31 ms while the p95
is 37.73 ms — that sample is the cold start (the first write to a fresh database,
the class loading, the peer's first cycle) — and a sample of thirty does not establish a
p99, so the report says so. The throughput figure is the sum of the round trips divided
into the elapsed time — with one task at a time it is the reciprocal of the mean latency,
not a saturation measurement.

## 4. The stages, from chain A's own clock

| Stage | p50 | p95 | What it covers |
|---|---|---|---|
| `intent_written_nanos` | 0.7872 ms | 1.3581 ms | chain A writes the dispatch intent **before** the send — the durable record exists before the request leaves |
| `dispatch_nanos` | 7.0904 ms | 11.6578 ms | signing the request, the network round trip, chain B's authentication, its durable inbox write, the production inference, the signed answer and its verification |
| `peer_execution_nanos` | 1.0 ms | 3.0 ms | chain B, as chain B measured it: the production inference itself, rounded to the millisecond |
| `report_admission_nanos` | 10.7906 ms | 14.9989 ms | the report entering chain A's epistemic model through chain A's own bridge receiver |
| `assessment_nanos` | 3.8659 ms | 5.3405 ms | the epistemic evaluation of the answer |
| `total_nanos` | **23.8641 ms** | 33.7081 ms | the whole task, as chain A can see it |

Four facts about these numbers are worth stating plainly.

**The peer's own work is a small part of the crossing.** The peer says its inference took
1 millisecond at the median; the crossing itself took 7.09 milliseconds. The remaining
6.09 milliseconds are the network, the signing on both sides and the peer's own
authentication and inbox write. **The remote cognitive work is not what makes the
distributed path slow — the governed crossing around it is.**

**The epistemic evaluation is nearly free; the admission of the report is not.** The
assessment takes 3865.9 microseconds — it is a comparison of what the report claims against what
chain A already holds. The admission of the report takes 10.8 milliseconds, because the
report enters through chain A's own bridge receiver, which checks the signature, the
version and the rights, and writes the result into the knowledge store. **The expensive part
of receiving a remote answer is not the judgement about it; it is letting it in.**

**Chain A's own total (23.9 ms) is smaller than the client's round trip (27.4 ms).** The
difference is the HTTP stack, the scheduling and the part of the work chain A cannot time
from the inside. Neither number is the "true" latency; they answer different questions.

**Every one of the thirty samples was adjudicated `UNRESOLVED`**, with the reason recorded
in the report: the peer's confidence rests on `MEASURED`, which is not independent evidence,
and chain A's own records settle nothing. The performance of the path and the epistemic
outcome of the path are two separate facts, and this run happened to produce the honest
answer thirty times.

## 4a. What the processes themselves spent, and what they held

The client cannot see the CPU time, the heap or the garbage collection: they belong to the
two processes, each of which owns its own database and its own memory. Since round 23 the
benchmark samples them from outside the processes, from where the kernel and the JVM keep
them (`scripts/w53-process-metrics.py`).

| | chain A (Quarkus) | chain B (the gateway) |
|---|---|---|
| CPU time over the first pass (30 tasks) | 1.68 s | 0.90 s |
| CPU time per task, both processes | **86.0 ms** | |
| the heap the JVM holds at the end | 1.28 GB | |
| the resident set at the end | 4.15 GB | 166 MB |
| the peak resident set (the whole lifetime, the startup included) | 4.61 GB | 166 MB |
| the growth of the resident set over the first pass | **37.6 MB** | 0 MB |
| the garbage collection during the first pass | 0 events, 0 ms | not measured — the gateway's log is not enabled |
| the estimated allocation over the first pass | 227.7 MB | |

**The same numbers for the concurrent workload** (24 tasks, four in flight): the CPU time
per task rises from 86.0 ms to 157.9 ms (chain A 2.52 s, chain B 1.27 s), the growth of
the resident set from 37.6 MB to 61.6 MB, and the estimated allocation per task from
7.59 MB to 9.62 MB. The four tasks in flight cost more CPU and more memory than the four
tasks one after another — the contention the latency table shows has a cost in the
processes themselves.

**The allocation is an estimate, and it is a lower bound.** The JVM does not count the bytes
it allocates. What can be observed is what it collected and how much the heap it held grew;
the estimate is the two added together, and this run collected nothing at all, so the
estimate is a lower bound — the heap may have grown, been collected and grown again without
a visible event. The per-task figure of **7.59 MB** therefore says *at least* that much was
allocated per task, and it includes the cold task, whose class loading dominates it.

**The resident set of chain A (4.15 GB) is the JVM's default heap, not the wave's memory.**
The Quarkus application starts with a heap sized as a fraction of the machine's memory and
touches much of it during the startup; the number that speaks about the *run* is the growth
of the resident set over the run — 37.6 MB for thirty tasks — and the heap the JVM actually
holds at the end (1.28 GB), which is the application's own footprint, warm-up included.

**What this section does not claim.** It does not separate the two processes' startup from
their work: the CPU time is the delta over the run, but the heap and the resident set carry
the startup with them. It does not claim that the allocation belongs to the distributed
path: the estimate covers everything the JVM allocated, the bridge and the HTTP stack
included. And it does not claim a garbage-collection profile for chain B, whose log the
benchmark does not enable.

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
| p50 | 78.93 ms | 420.16 ms | 75.37 ms |
| p95 | 420.16 ms | 423.52 ms | 105.77 ms |
| the maximum | 423.52 ms | 423.52 ms | 111.68 ms |
| the mean | 135.36 ms | 416.72 ms | 79.09 ms |
| the throughput | 7.39 tasks/s | — | 12.64 tasks/s |

**The cold start is separated, and the rule is stated in the report itself:** the first
4 samples are the cold start — the first write to a fresh
database, the class loading of the dispatch path, and the peer's first cognitive cycle.
The single-task run shows the same shape: its p50 is
27.43 ms while its maximum is
323.31 ms, and that maximum is the first sample.

**What the concurrent path costs.** The per-task latency is higher than the single-task
path's (75.37 ms against 27.43 ms at p50): four
tasks in flight contend for the same knowledge store, for the same peer process and for the
same durable writes. The steady-state throughput of
12.64 tasks/s is therefore *below* the
single-task path's 26.18 tasks/s — the concurrency does
not buy throughput on this path, and the report says so rather than hiding it behind the
throughput of a run without the cold start.

**What the peer's own work costs under concurrency.** The peer says its inference took
4 ms at the median with four tasks in flight, against 1 ms with one task at a time; its p95
is 16 ms. The peer is the shared resource: the more the crossing grows under contention,
the more of the crossing is the peer's own queueing and its own bookkeeping.

**What the p99 is.** The run has 24 samples. Its p99 is a sample,
not a percentile of a population, and it is not quoted as one.

## 5a. The noise floor: the same workload, measured three times

The numbers above are one run. The same commands, run again on the same day on the same
machine, do not produce the same numbers — and the difference is large enough that it
belongs in the report rather than in a footnote. Three runs of the same workload are
therefore shown side by side: the run of round 28, the run of round 29 and the run that
produced the numbers above.

| the workload | run of round 28 | run of round 29 | this run | the spread |
|---|---|---|---|---|
| the sequential path, p50 | 20.77 ms | 26.18 ms | 27.43 ms | **+32 %** over the fastest run |
| the sequential path, the throughput | 31.83 tasks/s | 27.14 tasks/s | 26.18 tasks/s | −18 % against the fastest run |
| the concurrent path, the steady-state p50 | 35.57 ms | 66.50 ms | 75.37 ms | **+112 %** over the fastest run |
| the concurrent path, the steady-state throughput | 27.89 tasks/s | 15.28 tasks/s | 12.64 tasks/s | **−55 %** against the fastest run |

**The cause is not established.** The workload, the payload, the commands and the machine
are the same; what differs is the state the machine and the two persistent databases were
in. The report does not name a cause it has not measured.

**The concurrent path is the noisy one.** Its steady-state p50 moved by more than a factor
of two between two runs of the same commands on the same machine, while the sequential path
moved by a third. Any claim about the concurrent path's absolute latency is therefore a
claim about one run, not about the path.

**What the spread says about the peer's own work — and what it cannot say.** The run of
round 28 did not measure the peer's duration, so the three runs cannot be compared on that
stage: the peer said 1 ms at the median and 3 ms at p95 in the runs of rounds 29 and 31,
and whether that is stable across runs is **not established**. What these runs do show is
that the peer's inference is a small part of the crossing (1 ms against 7.1 ms) and that the
crossing is where the spread lives. That is why no optimization was accepted: the part that
varies is the part that was not understood.

**What follows from it.** The mandate asks that an accepted optimization exceed the
measurement noise. The noise on this path is tens of percent, and on the concurrent path it
is a factor of two, so a change smaller than that cannot be told apart from the run it was
measured in. The wave therefore accepted **no** optimization on the strength of a benchmark
run — and the honest consequence is written down: the first measurements of the path are a
baseline with a wide spread, not a comparison against a stable reference.

## 5b. The cost of the durable idempotency path, measured apart

§10.2 of the mandate asks for the duplicate-request handling and the replay of a completed
request to be measured **separately from the initial execution**: they are different paths
with different costs, and one number for both would hide the cost of the idempotency
path. Since round 31 the benchmark sends every request a second time, after the main
loop, with the same request id and the same payload, so the peer must recognise it and
answer from its own durable record.

| the replay path | the sequential run | the concurrent run |
|---|---|---|
| the replays | 30 | 24 |
| the client's round trip, p50 | **14.26 ms** | **13.40 ms** |
| the client's round trip, p95 | 16.62 ms | 15.09 ms |
| `replay_dispatch_nanos` | 3.4561 ms | 3.216 ms |
| `replay_report_admission_nanos` | 1.8416 ms | 1.6809 ms |
| `replay_assessment_nanos` | 4.333 ms | 4.592 ms |
| `replay_total_nanos` | **11.7492 ms** | 11.2578 ms |
| the CPU time per replay, both processes | **23.7 ms** | 22.5 ms |
| the estimated allocation per replay | 3.47 MB | 3.67 MB |
| the growth of the resident set over the replay pass | 1.7 MB | 2.0 MB |
| the replays that answered with the same disposition as the first execution | **30 of 30** | **24 of 24** |

**The replay costs about half the latency of the first execution** (14.26 ms against
27.43 ms at p50), and about a quarter of its CPU (23.7 ms against 86.0 ms per task). The
reason is visible in the stages: the crossing of the replay (3.46 ms) is less than half
of the crossing of the first execution (7.09 ms), and the admission of the report
(1.84 ms) is a sixth of the first admission (10.79 ms), because the report is already in
the store and the receiver's write is a re-write of what it holds. The assessment costs
about the same in both paths (4.33 ms against 3.87 ms) — chain A judges the answer again
either way.

**All thirty replays answered with the same disposition as the first execution.** That is
the check that the replay returned the recorded outcome instead of running the task a
second time: the peer recognised the request id and answered from its own durable record.
It is a check of the *effect*, not a claim about the peer's internals — the replay did
travel to the peer, and the report says so.

**What the replay does not prove.** It does not prove that the peer's logical effect was
applied only once in the strict sense of a distributed exactly-once guarantee: the
guarantee here is at-least-once delivery with an effectively-once logical effect, and the
demonstration of §13.23 (the same request after a restart of chain B) is what supports
that claim. The replay path is also not free: it costs a quarter of the CPU of the first
execution, and it re-judges the answer.

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
- it does not separate chain B's stages from the network round trip: the peer's own total
  is told apart by the duration it reports, and its authentication, its durable inbox write
  and its answer signing are still inside the crossing;
- it does not claim that these numbers are comparable with a run on another machine;
- it does not claim that the concurrent path is faster than the single-task one — it is
  slower per task, and the throughput number includes the cold start;
- **no optimization was accepted on the strength of this run alone** — nothing was
  optimized, because nothing was profiled as a profile; these are the first measurements of
  the path, not a comparison against a baseline;
- it does not claim that the numbers of this run are the numbers of the path: §5a measures
  the spread between three runs of the same workload, and that spread is tens of percent on
  the sequential path and a factor of two on the concurrent one;
- it does not claim that the replay path never reached the peer: the request is sent again
  and the peer answers from its durable record — §5b measures that path rather than
  asserting that the peer was not involved;
- it does not claim that the replay path is free: it costs about half the latency and a
  quarter of the CPU of the first execution, and it judges the answer again;
- it does not claim an exact allocation per task: the allocation is estimated from what the
  JVM collected plus the growth of the heap it held, and this run collected nothing, so the
  estimate is a lower bound;
- it does not claim that the chain A resident set of §4a is the wave's memory: it is the
  JVM's default heap, and only its growth over the run speaks about the wave;
- it does not claim a garbage-collection profile for chain B, whose GC log the benchmark
  does not enable;
- it does not claim that the serialization and the signature cost are separated from the
  network round trip: chain A times the crossing as one interval, and the peer carries one
  duration today — its own execution — so the separation of the peer's remaining stages is
  named as the next measurement;
