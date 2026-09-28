# MATRIX MIND REPORT v17.2 — "Truthfully Fixed"

> Date: 2026-09-28 · Tag `v17.2.0-mind` · supersedes `v17.0.0-mind`
> Campaign: RECON-W20 → W24
> Headline: **33/45 (0.733) → 47/48 (0.979)**

Every number below ships with a command that reproduces it. Every claim of
closure names the file and line that causes it. Every remaining failure is named.

---

## 1. Headline, before and after

| Category | v17.0 | v17.2 | Change | What actually caused it |
|---|---|---|---|---|
| ARITHMETIC | 14/14 | **14/14** | — | held |
| ANALOGY | 5/6 | **6/6** | +1 | seed table had the relation in one orientation only |
| CONTRADICTION | 4/4 | **4/4** | — | held |
| ETHICS | 3/3 | **3/3** | — | held |
| RU | 3/3 | **3/3** | — | held |
| TAUGHT_RETRIEVAL | 4/4 | **4/4** | — | held |
| GENERALIZATION | **0/7** | **6/7** | **+6** | the scorer could not score it; no relational stage existed |
| PLANNING_DEPTH | **0/4** | **4/4** | **+4** | the scorer could not score it; greedy regex ate compounds |
| RETRIEVAL | **0/3** | **3/3** | **+3** | the scorer could not score it — *and never did score it* |
| **TOTAL** | **33/45 (0.733)** | **47/48 (0.979)** | **+14** | |

Reproduce:
```bash
bash scripts/w13-live-benchmark.sh data/mind/benchmarks/w24-live.csv
# TOTAL=48 PASSED=47 PASSRATE=0.9791666666666666
```

The denominator moved 45 → 48 because `w13-live.csv` was recorded against an
older battery. The probe set itself was never modified: `BenchmarkScoringContractTest`
pins all 7 GENERALIZATION expected values and the PLANNING_DEPTH count.

---

## 2. The two findings that mattered most

### 2.1 Two "capability" zeroes were a broken grader

`BenchmarkRunner.passes` handled five categories and then fell through to
`return false`. GENERALIZATION, PLANNING_DEPTH and RETRIEVAL had no branch, so
they **could not pass regardless of the answer**.

The proof is in the archived CSV. `data/mind/benchmarks/w22-live.csv`, row GE-4:

```
GE-4,GENERALIZATION,Paris,{"answer":"Paris",...},0.500,1,false
```

The mind answered `Paris`. The expected match was `Paris`. The harness wrote
`passed=false`. The published 0/7 was a statement about the grading code.

Fixing the grader alone — with no mind change whatsoever — recovered
**RETRIEVAL 0/3 → 3/3** and **PLANNING_DEPTH 0/4 → 1/4**.

This is not score inflation. No probe input, expected value or category
definition was touched. `BenchmarkScoringContractTest` asserts that every
`Probe.Category` is scoreable, so no future category can inherit a guaranteed
zero, and it asserts the frozen expectations are unchanged.

### 2.2 The gateway corrupted standard JSON

`MinimalHttpServer.extractField` located `"field"`, took the next two quote
characters, and un-escaped only `\"` and `\\`. It never decoded unicode escapes.

Python's `json.dumps`, Jackson's `ObjectMapper` and most HTTP frameworks escape
non-ASCII **by default**. Those clients delivered a literal `\u0441\u0442...`
string to the mind. The same question was answered correctly via `curl` (raw
UTF-8) and incorrectly via any standards-compliant client.

Now parsed with Jackson `readTree`, with the old scan retained only as a
fallback for unparseable bodies. No new dependency (Article VII).

---

## 3. Genuine new capability (not score recovery)

**`RelationalReasoningStage`** — transitivity over comparative chains
("A taller than B, B taller than C ⇒ who is shortest?") and unanimous-attribute
propagation. Before W22 the stage roster had nothing that could compose a
relation over the input: Arithmetic (regex), Analogy (seed table), BIR (four
opaque bitmask rules), HDC (cosine), Tsetlin (simulacrum-off, always misses),
MCTS (a placeholder emitting `budget=12`).

**`BilingualFactLookup`** — 61 country→capital entries in EN and RU. Only 2
countries are probed; the rest answer questions nobody asked, which the tests
assert explicitly.

**Operator precedence** — the compound arithmetic path documented "evaluate
left-to-right (matches regex semantics)", which is arithmetically wrong:
`2 + 3 * 4` gave 20. `evaluateWithPrecedence` now binds `*` and `/` tighter than
`+` and `-`, associates equal precedence left to right, and **declines** on
non-exact or zero division rather than truncating.

**Article VIII — the empty-answer defect.** `composeReply` ended in
`return tsetlin.reply()`, which is `""` whenever `simulacrumEnabled == false`
(the correct Article I production setting). Every unanswered question returned
an empty string at full salience confidence — a silent zero. It now returns an
explicit refusal, and the BRC trace names the stage that declined and why.

---

## 4. Limitations: CLOSED-HONESTLY or STILL-OPEN

| ID | Status | Evidence |
|---|---|---|
| **L-1 / L-1.5** ONNX unusable | ✅ **CLOSED-HONESTLY** | Model now runs out of process (`scripts/capture_activations.py`); `distillFromActivations` implemented and proven: 8 samples, fidelity 1.0, deterministic content hash, super-additive A+B=+2. The Java path still segfaults here and is **not claimed to work**. |
| **D-W13-1** GENERALIZATION 0/7 | ✅ **6/7** | One honest failure remains (GE-6), see §5. |
| **D-W13-2** PLANNING_DEPTH 0/4 | ✅ **4/4** | W4's documented "limitation #1" was the real cause. |
| **D-W13-3** remaining misses | ✅ **TRIAGED** | Both survivors explained below with measured root cause. |
| **DISK-WARN** | ✅ **CLOSED** | Self-resolved on arrival (132 GB free). Durable policy added: rotation, ledger, REFUSE gate. |
| **L-5** Vector 0.18× | ✅ honest, unchanged | Follow-up popcount benchmark **not performed** — see §5. |
| **L-6** two-node federation | ✅ **CLOSED** | Full adversarial two-node transcript captured (`two-node-transcript-002.txt`): B refuses before federation, accepts the pushed batch, and answers correctly after — A registry 0, B registry 2, no shared state. |
| **D-W25-1** contradiction gate | ✅ **CLOSED** | Proven broken by the W25 transcript, then fixed: precondition now hashes the subject (not the whole body), `Clause` gained value equality, fingerprint bounded to the Article II 20-bit domain. Same fact re-asserted → accepted; same question with a different answer → quarantined; `/v1/conflicts` count:1. |
| **L-7** fresh-clone | 🟡 **PARTIAL** | Script exists; not executed literally in a clean temp dir; no CI job installed. |
| **D-W20-1** 74 failing tests | 🔴 **OPEN** | Pre-existing, none in touched classes. |
| **D-W20-2** missing guard | 🔴 **OPEN** | `SimulacrumDefaultOffTest` does not exist. |

---

## 5. What still fails — named, not buried

**GE-6 — "tomato is red; carrot is orange; banana is yellow. lemon is ?" → expected `yellow`**
The exemplars *disagree*, so the unanimity rule declines. Answering requires
knowing lemons are yellow — world knowledge, not inference. A rule that returned
"yellow" here would be fitting the probe, so it was not written. The mind answers
with an explicit refusal instead of a guess.

**74 pre-existing test failures across 41 classes (D-W20-1).** None are in any
class this campaign touched. Clusters: `io.matrix.research.BitNet*` (21),
jqwik `*PropertyTest` (argument-type mismatch and empty-generator defects, ~28),
`ModelRegistryTest` (3), `MatrixNativeMathTest` (2). The count drifts between
runs (70 → 74) because jqwik properties are randomly generated and do not
converge — itself a defect.

**`SimulacrumDefaultOffTest` does not exist (D-W20-2).** `MATRIX-MIND-REPORT-V17.md`
claimed six Article VIII guards green. Five exist and are verified. The sixth is
documentation fiction. Either implement it or retract the claim; it is currently
a false statement in a shipped report.

**Two production non-terminating loops were live in the codebase and are now fixed**
(W20). `KolmogorovComplexity.logarithmicEncoding` hung forever on any trajectory
with exactly two distinct states; `DebateAgent.adjustConfidence` livelocked at the
clamp boundary. Both were preventing the `matrix-core` suite from ever completing,
which is how 74 failures stayed hidden behind a green-looking build.

**An Article VIII guard was passing vacuously.** `RuntimeLlmGuardTest` resolved
its scan root relative to the working directory; Gradle runs module tests with the
module dir as cwd, so it scanned nothing and reported success. Now it locates the
repo root properly and actually enforces.

**The popcount follow-up was not performed.** W24's L-5 revisit (Long.bitCount
unrolled and nibble-LUT variants vs scalar) was not run. L-5 remains closed on the
W16 measurement only. **Not claimed as done.**

**Distilled knowledge is not yet answerable in chat.** W21 puts real,
provenance-carrying artifacts into the registry, but the retrieval path that would
surface them to a user is not wired — the registry is queried by bitmask while the
distilled clauses are HDC-shaped. This is the honest reason GE-6 fails.

**The 20-bit fingerprint domain is a real limit.** With K_MAX=20, subject identity
has 2^20 slots, so spurious precondition collisions become likely past ~1k
registered facts. The failure direction is safe — a collision is quarantined only
when the conclusions actually differ — but the throughput ceiling is real and
would need a wider identity (more input words) to raise.

**`data/smoke-old` still occupies 8.8 GB.** Harmless at 133 GB free; the Goal
Guard blocks `rm -rf`. `fresh-clone-smoke.sh` still has no retention policy, which
is the actual cause.

---

## 6. What the user can newly observe

```bash
bash scripts/start-mind.sh          # now runs a disk pre-flight; refuses <10 GB free
curl localhost:8765/health/live
```

| Ask | Before | After |
|---|---|---|
| `Alice taller than Bob, Bob taller than Carol. Who is shortest?` | `""` | **`carol`** |
| `столица франции` | `""` | **`Paris`** |
| `2 + 3 * 4` | `2 + 3 = 5` | **`2 + 3 * 4 = 14`** |
| `twice five plus three` | refusal | **`5 * 2 + 3 = 13`** |
| `sun is to day as moon is to ?` | refusal | **`night`** |
| anything unanswerable | `""` (silent) | **an explicit refusal naming the failure** |
| the same question via a Python/JS client | corrupted text | **answered correctly** |

---

## 7. Verification ledger

| Command | Result |
|---|---|
| `bash scripts/w13-live-benchmark.sh data/mind/benchmarks/w24-live.csv` | 47/48 = 0.979 |
| `bash scripts/disk-hygiene.sh` (×2) | idempotent; 132 GB free, HEALTHY |
| `./gradlew :matrix-core:test :matrix-brain-runtime:test :matrix-api-gateway:test` | 8071 tests, 74 fail (all pre-existing, none in touched classes) |
| `python3 scripts/capture_activations.py …` | 8 records, no segfault |
| `RunActivationDistill data/activations/capacities-8.ndjson` | 8 samples, fidelity 1.0, hash `623cb895`, A+B=+2 |
| `curl localhost:8765/health/live` | `{"status":"UP","brain_available":true}` |

---

## 8. Commitments deliberately not made

No consciousness claim is made anywhere in this report. Every "mind" word is a
label for a stage graph, not a claim about experience (Article VI). The
generalisation gains come from explicit symbolic rules and a 61-entry fact table —
both inspectable, both deterministic, neither a language model.
