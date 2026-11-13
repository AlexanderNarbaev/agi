# W53 — The performance of the distributed path, stage by stage

**Status:** `ACTIVE` — measured for one task at a time **and** under a bounded concurrency of four,
**and** above that bound; the cold start is separated from the steady state; the two
processes' own accounting is measured too. · **Machine-readable form:**
[`W53-PERFORMANCE.json`](W53-PERFORMANCE.json), schema
`matrix.w53.distributed-performance/v3` · **Procedure:**

```bash
bash scripts/w53-distributed-benchmark.sh --tasks 30 --concurrency 1 --report /tmp/single.json
bash scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 4 --report /tmp/concurrent.json
bash scripts/w53-distributed-benchmark.sh --tasks 24 --concurrency 8 --report /tmp/over-cap.json
python3 scripts/w53-merge-performance.py /tmp/single.json /tmp/concurrent.json /tmp/over-cap.json \
  docs-v2/engineering/W53-PERFORMANCE.json
```

The workloads are measured in separate runs on purpose: the cold start belongs to the
first one and must not be hidden inside the second, and the third one is a different
question again — what the coordinator's concurrency cap does to the work over it (§5d).
The merge keeps each run in its own section, names the sections after the workload they
measured, and averages nothing.

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
network, the signing and the peer's own bookkeeping. Since round 41 the peer measures its
own stages as well and keeps them in its own durable row — §4b has those numbers, and the
answer contract did not change.

**Two paths are measured, apart.** The first is the first execution of a task. The second
is the replay of the same request — the same request id, the same payload — which the
peer answers from its durable record. §10.2 of the mandate asks for the duplicate-request
handling to be measured separately from the initial execution, and §5b holds those numbers.
The two passes have their own latency, their own stages and their own process metrics.

**Three workloads are measured.** The single-task path (§3), the path with four tasks in
flight (§5), and a third with **eight** — more than the coordinator's cap of four, so that
the bound itself is measured and not only asserted (§5d). The third is a workload of its
own and it keeps its own section in the machine-readable report; nothing is averaged
between them.

## 2. The environment

| | |
|---|---|
| OS | Ubuntu 26.04.1 LTS |
| JDK | openjdk 25.0.5 |
| Hardware | x86_64 |
| JVM arguments | the defaults of the Quarkus fast-jar and of the gateway's start script |
| Payload | one sentence of input, one expectation |
| Random seed | none — the workload has no randomness |
| the code the numbers belong to | the wave's own tree as it stands after round 50 — the peer-side stage measurement, the reconciliation timer and the named chain A stores — on `feature/w53-distributed-cognition`; the head it stood on was `64b1657a`, and the tree carries the changes of rounds 41, 43 and 48–50 that are not committed yet |
| the three runs | the single-task path and the four-in-flight path were measured in the runs of the stamps `1794383116-221157` and `1794383275-222320`; the eight-client run (§5d) in the run of the stamp `1794385832-297559` — the same code, the same machine, the same day, each against a store of its own |
| the state the numbers were taken against | **a store of the run's own.** Chain A's cognitive and knowledge stores are named to the benchmark's own directory, so the measurement does not include the state the earlier runs of the wave left behind. §5c measures what the difference is: the same workload against the accumulated store of the wave and against a store of its own gave 32.33 ms and 13.87 ms at p50 |
| what the timer changes on the measured path | the reconciliation pass runs on a timer as well as at the startup; its tick is 30 s, and a 30-task run finishes inside one tick, so the timer adds one idle pass per run and nothing to a task's own path. The dispatch that the pass sends is measured in §5b, not here |

**These numbers are not comparable with a run on another machine.** They are the numbers of
this machine, in this configuration, on this day.

The process metrics of §4a were sampled with the JVM's own GC log enabled for chain A
(`-Xlog:gc`), which the benchmark adds to the process it measures; the JVM arguments above
are the ones of the run itself, plus that log.

## 3. The end-to-end numbers (30 tasks, one at a time)

| p50 | p95 | p99 | min | max | mean | throughput |
|---|---|---|---|---|---|---|
| **14.29 ms** | **21.41 ms** | 272.52 ms | 12.65 ms | 272.52 ms | 23.26 ms | **42.99 tasks/s** |

**The p99 is not claimed.** One of the thirty samples took 272.52 ms while the p95
is 21.41 ms — that sample is the cold start (the first write to a fresh database,
the class loading, the peer's first cycle), and it is the maximum of the run — and a
sample of thirty does not establish a p99, so the report says so. The throughput figure is the sum of the round trips divided
into the elapsed time — with one task at a time it is the reciprocal of the mean latency,
not a saturation measurement.

## 4. The stages, from chain A's own clock

| Stage | p50 | p95 | What it covers |
|---|---|---|---|
| `intent_written_nanos` | 0.2896 ms | 0.7022 ms | chain A writes the dispatch intent **before** the send — the durable record exists before the request leaves |
| `dispatch_nanos` | 5.8380 ms | 10.1401 ms | signing the request, the network round trip, chain B's authentication, its durable inbox write, the production inference, the signed answer and its verification |
| `peer_execution_nanos` | 1.0 ms | 2.0 ms | chain B, as chain B measured it: the production inference itself, rounded to the millisecond |
| `report_admission_nanos` | 4.3835 ms | 6.1202 ms | the report entering chain A's epistemic model through chain A's own bridge receiver |
| `assessment_nanos` | 0.3725 ms | 0.7164 ms | the epistemic evaluation of the answer |
| `total_nanos` | **11.4124 ms** | 17.3178 ms | the whole task, as chain A can see it |

Four facts about these numbers are worth stating plainly.

**The peer's own work is a small part of the crossing.** The peer says its inference took
1 millisecond at the median; the crossing itself took 5.84 milliseconds. The remaining
4.84 milliseconds are the network, the signing on both sides and the peer's own
authentication and inbox write. **The remote cognitive work is not what makes the
distributed path slow — the governed crossing around it is.**

**The epistemic evaluation is nearly free; the admission of the report is not.** The
assessment takes 372.5 microseconds — it is a comparison of what the report claims against what
chain A already holds. The admission of the report takes 4.38 milliseconds, because the
report enters through chain A's own bridge receiver, which checks the signature, the
version and the rights, and writes the result into the knowledge store. **The expensive part
of receiving a remote answer is not the judgement about it; it is letting it in.**

**Chain A's own total (11.4 ms) is smaller than the client's round trip (14.3 ms).** The
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
| CPU time over the first pass (30 tasks) | 1.06 s | 0.88 s |
| CPU time per task, both processes | **64.7 ms** | |
| the heap the JVM holds at the end | 1.06 GB | |
| the resident set at the end | 4.11 GB | 167 MB |
| the peak resident set (the whole lifetime, the startup included) | 4.57 GB | 167 MB |
| the growth of the resident set over the first pass | **26.9 MB** | 0 MB |
| the garbage collection during the first pass | 0 events, 0 ms | not measured — the gateway's log is not enabled |
| the estimated allocation over the first pass | **32.0 MB — 1.07 MB per task** | |

**The same numbers for the concurrent workload** (24 tasks, four in flight): the CPU time
per task rises from 64.7 ms to 117.5 ms (chain A 1.44 s, chain B 1.38 s), the growth of
the resident set from 26.9 MB to 27.7 MB, and the estimated allocation per task from
1.07 MB to 2.25 MB. The four tasks in flight cost more CPU and more memory than the four
tasks one after another — the contention the latency table shows has a cost in the
processes themselves.

**The allocation is an estimate, and it is a lower bound.** The JVM does not count the
bytes it allocates. What can be observed is what it collected and how much the heap it
held grew; the estimate is the two added together, and this run collected nothing at all,
so the estimate says *at least* that much was allocated per task: **1.07 MB per task** in
the sequential run and **2.25 MB per task** in the concurrent one. An earlier run of the
same workload had a *negative* estimate — the heap it held at the end was smaller than the
one it held at the start — and a negative allocation is not a number; that run is not
quoted here.

**The resident set of chain A (4.11 GB) is the JVM's default heap, not the wave's memory.**
The Quarkus application starts with a heap sized as a fraction of the machine's memory and
touches much of it during the startup; the number that speaks about the *run* is the growth
of the resident set over the run — 26.9 MB for thirty tasks — and the heap the JVM actually
holds at the end (1.06 GB), which is the application's own footprint, warm-up included.

**What this section does not claim.** It does not separate the two processes' startup from
their work: the CPU time is the delta over the run, but the heap and the resident set carry
the startup with them. It does not claim that the allocation belongs to the distributed
path: the estimate covers everything the JVM allocated, the bridge and the HTTP stack
included. And it does not claim a garbage-collection profile for chain B, whose log the
benchmark does not enable.

## 4b. The peer's own stages, measured by the peer

§10.1 asks for the peer's authentication and validation, its durable inbox write and its
response serialization and signing to be measured. Until round 41 the peer reported **one**
duration for all of its work, and the wave's honest list said that separating them needs
a protocol-version decision, because the numbers would have to travel on the wire.

**That was over-cautious: the separation needs no protocol decision at all.** The
measurement is the peer's own bookkeeping about its own work, so the peer keeps it in its
own durable row, and the report reads the peer's own file. The answer contract did not
change — the caller receives exactly what it received before — and the report says so.

| the peer's own stage | p50 | p95 | what it covers |
|---|---|---|---|
| `inboxWrite` | 0.215 ms | 0.351 ms | the durable write of the request into the peer's own inbox |
| `stateWrite` | 0.024 ms | 0.057 ms | the write that moves the request into the running state |
| `execution` | 1.272 ms | 2.912 ms | the production cognitive path the peer really ran |
| `outcomeWrite` | 0.192 ms | 0.417 ms | the durable write of the outcome |
| `answerSigning` | 0.493 ms | 1.254 ms | the serialization and the signing of the answer |
| `deliveryTotal` | 2.530 ms | 4.613 ms | the whole reception of the delivery |
| `authenticationAndValidation` | 0.671 ms | 1.557 ms | the **remainder** of the reception |

**What the remainder is, and what it is not.** It is what the reception cost beyond the
peer's own four writes and its execution: the authentication, the validation, the
idempotency lookup and the receiver's own bookkeeping, together. It is not a measurement
of the signature alone, and this report does not claim it is.

**How stable these are.** §5a measures the same stages across four runs of the identical
code: the median of the peer's own execution holds between 1.28 ms and 1.48 ms, while
its p95 moves by 30 %.

**What the peer's own numbers say about the crossing.** The peer's whole reception took
2.53 ms at the median, while chain A timed the crossing at 5.84 ms. The difference is the
network, the signing of the request on chain A's side and the scheduling: **the crossing
around the peer's work costs about twice the peer's work.**

**The measurement is not free, and the report says where it sits.** The peer writes its
measurement once per delivery. That write happens after the peer's own stages are timed,
so it is **not** inside the numbers above — but it **is** inside the end-to-end round trip
the client measures.

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
| p50 | 27.55 ms | 355.35 ms | 27.03 ms |
| p95 | 355.35 ms | 355.36 ms | 38.03 ms |
| the maximum | 355.36 ms | 355.36 ms | 39.56 ms |
| the mean | 82.38 ms | 354.98 ms | 27.79 ms |
| the throughput | 12.14 tasks/s | — | 143.95 tasks/s |

**The cold start is separated, and the rule is stated in the report itself:** the first
4 samples are the cold start — the first write to a fresh
database, the class loading of the dispatch path, and the peer's first cognitive cycle.
The single-task run shows the same shape: its p50 is
14.29 ms while its maximum is
272.52 ms, and that maximum is the first sample.

**The steady-state throughput is the real one:** the twenty steady tasks finished in
0.14 s of wall time with four in flight, which is **143.95 tasks/s**. The reciprocal of
the steady mean — what the path would reach with one task at a time — is 35.99 tasks/s,
and the report states both so that the two are not confused.

**What the concurrent path costs, and what it buys.** The per-task latency is higher than
the single-task path's (27.03 ms against 14.29 ms at p50): four
tasks in flight contend for the same knowledge store, for the same peer process and for the
same durable writes. The steady-state throughput of
**143.95 tasks/s** is nevertheless **3.3 times** the single-task path's
42.99 tasks/s: the concurrency does buy throughput, at the price of a doubled latency and
of the contention the CPU numbers of §4a show (117.5 ms of CPU per task against 64.7 ms).

**This is a corrected conclusion.** The wave reported earlier that "the concurrency does
not buy throughput on this path". That was measured against the accumulated epistemic
state of the wave (§5c), where every task spent milliseconds comparing the peer's report
against thousands of claims; with a store of its own, the crossing dominates and the four
tasks in flight overlap each other's waiting.

**What the peer's own work costs under concurrency.** The peer's own measurement of its
execution took 3.54 ms at the median with four tasks in flight, against 1.27 ms with one
task at a time; its p95 is 47.57 ms against 2.91 ms. Its whole reception took 6.74 ms at
the median, against 2.53 ms. The peer is the shared resource: the more the crossing grows
under contention, the more of the crossing is the peer's own queueing and its own
bookkeeping.

**What the p99 is.** The run has 24 samples. Its p99 is a sample,
not a percentile of a population, and it is not quoted as one.

## 5a. The measurement's own noise: the same code, against the same state

The numbers above are one run. §10.3 of the mandate asks that an accepted optimization
exceed the measurement noise, so the noise has to be a number and not an adjective. It is
measured here on **identical code** and — the part that the wave got wrong the first time —
**against the same state**: each run gets a store of its own (§5c), so what the runs differ
by is the run and not the history it accumulated.

### The sequential path — seven runs of the same code, each against a fresh store

| the run | the end-to-end p50 | the throughput | the assessment | the peer's own `execution`, p50 |
|---|---|---|---|---|
| one | 13.87 ms | 44.45 tasks/s | 0.348 ms | 1.401 ms |
| two | 14.12 ms | 44.00 tasks/s | 0.371 ms | 1.350 ms |
| three | 14.22 ms | 45.12 tasks/s | 0.342 ms | 1.289 ms |
| four | 14.24 ms | 43.90 tasks/s | 0.355 ms | 1.364 ms |
| five | 14.26 ms | 42.84 tasks/s | 0.346 ms | 1.348 ms |
| six | 14.29 ms | 42.99 tasks/s | 0.373 ms | 1.272 ms |
| seven | 15.24 ms | 42.08 tasks/s | 0.373 ms | 1.429 ms |

**Six of the seven lie inside 13.87–14.29 ms — a spread of three percent. The seventh is
9.9 % above the fastest and 6.6 % above the median, and the spread of all seven is 9.9 %.**
The threshold an accepted
change has to beat is therefore **about ten percent on this path**, and the run this report
quotes (§3) sits at the fast end of that band.

**Isolation lowers the latency but not the variance.** The same code measured against the
*accumulated* store of the wave gave 30.28, 31.30, 31.41 and 32.49 ms — a spread of
**7.3 %**. The variance does not grow with the state in the way the latency does: the
accumulated path is dominated by the assessment, which is comparatively repeatable, while
the isolated path is dominated by the crossing (the network, the signing on both sides), and
that is what varies from run to run. **The two paths are different measurements with
different thresholds, and neither threshold is smaller than ten percent.**

### The concurrent path — two runs of the same code, each against a fresh store

| the workload | the first run | the second | the spread |
|---|---|---|---|
| the steady-state p50 (four in flight) | 27.03 ms | 27.76 ms | **+2.7 %** |
| the whole-run p50 | 27.55 ms | 28.89 ms | +4.9 % |
| the steady-state throughput (four in flight) | 143.95 tasks/s | 142.72 tasks/s | +0.9 % |
| the peer's own `execution`, p50 | 3.54 ms | 2.62 ms | +35 % |
| the peer's own `deliveryTotal`, p95 | 140.76 ms | 103.30 ms | +36 % |

**The concurrent path repeats within three percent at the median**, and its throughput
within one percent — the two runs agree on what the path can do. **Its tail does not**: the
peer's own p95 crossing moved by a third between the two runs, which is the same fact the
sequential path shows, in a bigger number.

### What the earlier table was — and what it was not

| the workload | run of round 28 | run of round 29 | run of round 31 | run of round 41 | run of round 43 | the run of round 50 | the spread |
|---|---|---|---|---|---|---|---|
| the sequential path, p50 | 20.77 ms | 26.18 ms | 27.43 ms | 29.69 ms | 31.41 ms | **14.29 ms** | −31 % against the fastest run |
| the sequential path, the throughput | 31.83 tasks/s | 27.14 tasks/s | 26.18 tasks/s | 23.99 tasks/s | 22.62 tasks/s | **42.99 tasks/s** | +35 % against the slowest run |
| the concurrent path, the steady-state p50 | 35.57 ms | 66.50 ms | 75.37 ms | 91.37 ms | 93.84 ms | **27.03 ms** | −24 % against the fastest run |
| the concurrent path, the steady-state throughput | 27.89 tasks/s | 15.28 tasks/s | 12.64 tasks/s | 11.29 tasks/s | 10.68 tasks/s | **143.95 tasks/s** | +5.2 times the slowest run |

**This table is not a noise measurement, and it never was.** Between those rounds the wave
changed the code — the episode integration, the adjudication of the report, the trace, the
timer's in-flight set — **and the state the measurement ran against**: the stores were not
named, so every run grew them (§5c). The run of round 50 is the first one measured against a
store of its own, and it is **faster than every earlier run** — 14.29 ms against 20.77 ms
for the fastest of them, and 31.41 ms for the run of round 43. **The wave's earlier reading
of its own history was a reading of the laboratory, not of the path.**

**What follows from it.** The mandate asks that an accepted optimization exceed the
measurement noise. On this path that means **about ten percent on the sequential path and
about three percent at the median of the concurrent one**; anything smaller cannot be told
apart from the run it was measured in. The wave therefore accepted **no** optimization on the
strength of a benchmark run — and the reason is now a measured number rather than a shrug:
the differences the runs showed were below the noise, and the honest consequence is written
down. The first measurements of the path are a baseline with a known spread, not a
comparison against a stable reference.

**The peer's own stages, measured across the seven isolated runs.** The peer keeps its own
measurement in its own row, so every run that used the same script collected it:

| the peer's own stage | the fastest | the slowest | the spread |
|---|---|---|---|
| `execution`, p50 | 1.272 ms | 1.429 ms | +12 % |
| `execution`, p95 | 2.772 ms | 4.652 ms | +68 % |
| `deliveryTotal`, p50 | 2.529 ms | 2.727 ms | +8 % |
| `deliveryTotal`, p95 | 4.501 ms | 7.256 ms | +61 % |

**The peer's inference is stable at the median and its tail is not.** The median of the
peer's own execution sits between 1.27 ms and 1.43 ms across the seven runs of the same
code — the remote cognitive work is a small, stable part of the crossing (1.27 ms against
5.84 ms). The p95 of the same stage moves by two thirds — **the tail of the peer's execution
is where the variance of the path lives**, not its median. That is a fact about the tail, not
a diagnosis of it, and the report does not turn it into one.

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
| the client's round trip, p50 | **8.07 ms** | **7.59 ms** |
| the client's round trip, p95 | 10.19 ms | 9.62 ms |
| `replay_dispatch_nanos` | 3.3366 ms | 3.1551 ms |
| `replay_report_admission_nanos` | 1.5410 ms | 1.5160 ms |
| `replay_assessment_nanos` | 0.4812 ms | 0.4062 ms |
| `replay_total_nanos` | **5.6202 ms** | 5.4403 ms |
| the CPU time per replay, both processes | **18.3 ms** | 16.2 ms |
| the estimated allocation per replay | 0.53 MB | 0.42 MB |
| the growth of the resident set over the replay pass | 2.5 MB | 2.6 MB |
| the replays that answered with the same disposition as the first execution | **30 of 30** | **24 of 24** |

**The replay costs about half the latency of the first execution** (8.07 ms against
14.29 ms at p50), and about a third of its CPU (18.3 ms against 64.7 ms per task). The
reason is visible in the stages: the crossing of the replay (3.34 ms) is about three fifths
of the crossing of the first execution (5.84 ms), and the admission of the report (1.54 ms)
is about a third of the first admission (4.38 ms), because the report is already in the
store and the receiver's write is a re-write of what it holds. The assessment costs a
little more in the replay (0.48 ms against 0.37 ms) — chain A judges the answer again
either way, and against the store of the first execution there is more to judge against.

**All thirty replays answered with the same disposition as the first execution.** That is
the check that the replay returned the recorded outcome instead of running the task a
second time: the peer recognised the request id and answered from its own durable record.
It is a check of the *effect*, not a claim about the peer's internals — the replay did
travel to the peer, and the report says so.

**What the replay does not prove.** It does not prove that the peer's logical effect was
applied only once in the strict sense of a distributed exactly-once guarantee: the
guarantee here is at-least-once delivery with an effectively-once logical effect, and the
demonstration of §13.23 (the same request after a restart of chain B) is what supports
that claim. The replay path is also not free: it costs about a third of the CPU of the
first execution, and it re-judges the answer.

## 5c. The path against a growing history

§16 asks the system to stay bounded under a **growing audit history** and a **growing
cognitive history**, and §10.2 asks the benchmark to include increasing history sizes.
Neither was measurable before this round, because the benchmark always started from an
empty store — and, as §6.3 of the ADR records, it did not even always start from an
empty store.

**How it was measured.** The benchmark takes the directory it works in
(`--work-dir`). Four runs of the same workload (thirty tasks, one at a time) against two
such directories: three against the same one, so the history grows from run to run, and a
fourth against a fresh one, as the control.

| the run | the store | the dispatches and claims in it | the end-to-end p50 | the assessment |
|---|---|---|---|---|
| A | fresh | 30 | 14.26 ms | 0.346 ms |
| B | the same, grown | 60 | 14.20 ms | 0.562 ms |
| C | the same, grown | 90 | 15.26 ms | 0.773 ms |
| D | fresh again (the control) | 30 | 15.24 ms | 0.373 ms |

**What the history costs, and where.** The end-to-end cost of tripling the history is
**one millisecond at p50** — from 14.26 ms to 15.26 ms, and that is the size of the
measurement's own noise (the control, A against D, differs by 15.24 against 14.26 ms).
**The path stays bounded under a growing history.**

The growing term is identifiable, though: **the assessment grows with the history and
more than doubles** — 0.346 ms, 0.562 ms, 0.773 ms — because it compares the peer's report
against chain A's knowledge, and the knowledge grows with the history. It is still a
small part of the path (0.77 ms of 15.26 ms at the median), and the report says where the
growth is rather than only that it is small.

**What this says about the wave's earlier numbers.** The wave's own runs were not
isolated, so the store they measured against grew from run to run — and it had reached
4850 claims and 860 dispatch rows. That is the difference between the path measured
against a store of its own (the p50 of §3) and the path measured against the store the
wave left behind (32.33 ms at p50 for the same workload): the accumulated state, not the
crossing, was the dominant cost. **The history has a price, the price is in the
assessment, and a path measured against its own state is a different measurement from one
measured against a laboratory's.**

## 5d. The bound itself: eight tasks against a cap of four

§10.1 asks the report for the **queue depth and the wait time**, §16 asks for the
**bounded retry behaviour**, and §12 asks for a test of **concurrent requests exceeding the
configured limit**. The first two of those are numbers about a bound, and a bound that was
never exceeded has no number. The coordinator's cap is four dispatches in flight
(`DispatchPolicy.standard()`, `DEFAULT_MAX_CONCURRENT_DISPATCHES = 4`) and the four-task
workload of §5 sits exactly at it. This section measures the fifth task and the ones
after it: **the same 24 tasks with eight clients**, so that half of them arrive while the
cap is full.

**What the excess met.** The coordinator **refuses** the work over the cap instead of
queueing it — an unbounded queue is an unbounded memory and an unbounded latency. The
measured consequence:

| | the tasks | the share |
|---|---|---|
| refused at the cap (`FAILED`, no verdict) | 12 | **50 %** of the 24 |
| admitted and adjudicated (`UNRESOLVED`) | 12 | 50 % of the 24 |

**The queue depth is 0 and the wait time is 0, because nothing waits.** The refused task's
client round trip was **3.19 ms at its quickest and 5.13 ms at the median** (the
maximum, 32.80 ms, is the moment the machine was busiest) — and the refused task carries
**no stage timings at all**: the refusal happens before the durable intent is written, so
the task never became a dispatch.

**The refusal left no record, and the replay proves it.** Of the 24 replays, 12 came back
answered from the durable record and **12 came back with no state and no timings at all**
(median 2.28 ms) — the replay of a request that was never dispatched is refused with
"no dispatched task with that id". That is the proof of the rule the coordinator states:
**a refusal leaves no trace of a dispatch that never happened**, and the durable trace of
a refused task is empty rather than misleading.

**What the admitted half cost under the load.** The twelve admitted tasks ran while eight
clients were competing for a cap of four, and their stages show where the time went:

| the stage | one task at a time | four in flight | eight offered (four admitted) |
|---|---|---|---|
| the durable write of the intent | 0.29 ms | 0.60 ms | 0.95 ms |
| the crossing (the request, the peer's work, the answer) | 5.84 ms | 12.00 ms | **27.03 ms** |
| the peer's own execution, as the peer measured it | 1.0 ms | 4.0 ms | **13.0 ms** |
| the admission of the report into chain A's model | 4.38 ms | 10.99 ms | **13.00 ms** |
| the assessment | 0.37 ms | 0.39 ms | 0.41 ms |
| the whole task, as chain A sees it | 11.41 ms | 24.23 ms | 41.12 ms |

**The database's part of the path and its contention.** The one stage that is a pure
durable write — the intent, 0.29 ms alone — costs **0.60 ms with four tasks in flight
and 0.95 ms with eight offered**: chain A's store has one writer, and the write waits
for it. The admission of the report (4.38 → 10.99 → 13.00 ms) mixes the bridge's
verification with the epistemic write, so it is an **upper bound** for the database's
share and not a measurement of it. What the three columns do show is the shape of the
contention: the assessment is flat (0.37 → 0.39 → 0.41 ms — it compares the report with
what chain A holds, and that does not change with the load), while everything that waits
grows. **The peer is the biggest single term under the load**: its own execution went from
1.0 ms to 13.0 ms, because the peer is the shared resource the four admitted tasks all
compete for.

**The retry rate.** The measured runs retried **nothing**: every request id is stamped per
run, so no request was a duplicate and no retry budget was spent. The retry budget is
what bounds the *recovery* path — a request whose answer was lost is dialled again until
the budget is spent, and then it is closed as failed with the reason (the coordinator's
own wording: "the retry budget of N attempt(s) is spent, so the request is not sent
again"). The recovery path is exercised by the coordinator's recovery test and by the
demonstration's restart, and §5b measures the cost of a replay that the peer answered
from its record.

**What this section does not claim.** It does not claim that the path sustains eight
tasks in flight — it does not, and it says so: the cap is four. It does not claim that
the refusal is free: the refused task cost a round trip of 3–33 ms of the client's time,
and the work it was refused is work that was **not** done. It does not claim a queue
latency, because there is no queue; the refusal *is* the bound.

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
- it does not separate chain B's stages from the network round trip **on chain A's side**:
  chain A times the crossing as one interval. The peer's own stages ARE separated now, in
  the peer's own record (§4b) — the peer measures them and keeps them there, and the
  answer contract did not change;
- it does not claim that these numbers are comparable with a run on another machine;
- it does not quote an exact allocation per task: the allocation is estimated from what the
  JVM collected plus the growth of the heap it held, and this run collected nothing, so the
  estimate is a **lower bound** — 1.07 MB per task in the sequential run and 2.25 MB in the
  concurrent one;
- it does not claim that the concurrent path is faster **per task** than the single-task one —
  it is slower per task (27.03 ms against 14.29 ms at the median) — but it does claim that
  it reaches a higher throughput: four tasks in flight gave 143.95 tasks/s against the
  single-task path's 42.99, and the whole-run figure (12.14 tasks/s) includes the cold start;
- **no optimization was accepted on the strength of this run alone** — nothing was
  optimized, because nothing was profiled as a profile; these are the first measurements of
  the path, not a comparison against a baseline;
- it does not claim that the numbers of this run are the numbers of the path: §5a measures
  the spread between **seven runs of the same code against the same state** — nine point
  nine percent on the sequential path, two point seven percent at the median of the
  concurrent one — and the table that spans the wave's rounds is the path's history, not
  the measurement's variance;
- it does not claim that the replay path never reached the peer: the request is sent again
  and the peer answers from its durable record — §5b measures that path rather than
  asserting that the peer was not involved;
- it does not claim a queue latency for the path: **there is no queue.** The coordinator
  refuses the work over its concurrency cap of four, and §5d measures that refusal — 12 of
  24 tasks refused at eight clients, at a client round trip of 3–33 ms, with no durable
  record left behind;
- it does not separate the database's share of the admission stage: that stage carries the
  bridge's verification together with the epistemic write, so it is an upper bound for the
  database's part and not a measurement of it (§5d);
- it does not claim that the replay path is free: it costs about half the latency and a
  third of the CPU of the first execution, and it judges the answer again;
- it does not claim that the chain A resident set of §4a is the wave's memory: it is the
  JVM's default heap, and only its growth over the run speaks about the wave;
- it does not claim a garbage-collection profile for chain B, whose GC log the benchmark
  does not enable;
- it does not claim that the serialization and the signature cost are separated from the
  network round trip **on chain A's side**: chain A times the crossing as one interval.
  The peer's own stages ARE separated, in the peer's own record (§4b) — the peer measures
  them and keeps them there, and the answer contract did not change;
