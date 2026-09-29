# MATRIX MIND REPORT v17.2 — "Truthfully Fixed"

> Date: 2026-09-28 · Tag `v17.2.0-mind` · supersedes `v17.0.0-mind`
>
> **TAG INTEGRITY (RECON-W27).** `v17.2.0-mind` was cut at `a8322a12`. Nine
> correctness commits landed after it — the HDC distance fix, the Clause value
> equality fix, the Article I rule-id fix, the remaining JSON parsers, the second
> teacher, the smoke-test root cause and their guards. A published tag is
> immutable and force-pushing a correction is denied by policy, so rather than
> move it, the corrected state is published as **`v17.2.1-mind` at `28adab68`**,
> which is this report's true subject. `v17.2.0-mind` is retained unchanged and
> marked superseded. If you fetched `v17.2.0-mind`, use `v17.2.1-mind`.
>
> `v17.2.2-mind` was cut after the mandatory eight-reviewer gate was recorded and is
> the final W27 state. RECON-W28 supersedes it with six further fixes, so the tag
> naming the current tree is the W28 final tag recorded in `SESSION.md`. A published
> tag is immutable and no force-push is permitted, so W28 adds a new tag rather than
> moving an old one. There are three v17.2 tags because each names a genuinely
> different tree state and none of them misrepresents which:
>
> | Tag | Names | State |
> |---|---|---|
> | `v17.2.0-mind` | `a8322a12` | superseded (force-push denied, so immovable) |
> | `v17.2.1-mind` | `28adab68` | B-1 smoke root cause + PID-file landmine |
> | `v17.2.2-mind` | `f60765e2` | eight-reviewer gate |
>
> `git ls-remote --tags` on `origin` and `gitverse` return an identical tag set.
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
| **L-5** Vector 0.18× | ✅ **CLOSED-ON-SCALAR, twice** | W16: Vector API 0.18×. W24 follow-up executed: unroll 0.52×, nibble-LUT 0.31×. Neither clears 1.5×, so scalar stays. The attempt also found a **correctness** bug: the distance kernel folded all words into one long and took a single popcount, which is not the Hamming distance its Javadoc specifies. Fixed. |
| **L-6** two-node federation | ✅ **CLOSED** | Full adversarial two-node transcript captured (`two-node-transcript-002.txt`): B refuses before federation, accepts the pushed batch, and answers correctly after — A registry 0, B registry 2, no shared state. |
| **D-W25-1** contradiction gate | ✅ **CLOSED** | Proven broken by the W25 transcript, then fixed: precondition now hashes the subject (not the whole body), `Clause` gained value equality, fingerprint bounded to the Article II 20-bit domain. Same fact re-asserted → accepted; same question with a different answer → quarantined; `/v1/conflicts` count:1. |
| **L-7** fresh-clone | 🟡 **PARTIAL** | Script exists; not executed literally in a clean temp dir; no CI job installed. |
| **D-W20-1** 74 failing tests | 🔴 **OPEN** | Pre-existing, none in touched classes. |
| **D-W20-2** missing guard | 🔴 **OPEN** | `SimulacrumDefaultOffTest` does not exist. |

---

## 5. What still fails — named, not buried

**RETRACTION: the distillation artifact hash `623cb895` (RECON-W28 B-4).**
This report previously quoted `623cb895` for the two-teacher merge. That figure could
not be reproduced and is withdrawn. Measured directly against the two shipped
captures, the artifacts are **different**: `capacities-8.ndjson` → `33fcfc27`
(truth table `0004400`), `booleans-8.ndjson` → `ad6bccbd` (truth table `0000`).
Neither is `623cb895`.

What is genuinely wrong, and was found while disproving the claim, is worse and
different: **three of the four distillation entry points hashed
`Integer.toHexString(bir.toString().hashCode())`**, and `Bir.toString()` embeds the
provenance string, which carries the source label and the capture path. Reproduced:
byte-identical learning from two differently-named sources hashed differently
(`191f560c` vs `1983cb64`). The "artifact hash" was a **run** identifier, not an
artifact identity, and it broke the content-addressing invariant W21 established. All
four paths now use `contentHash()`, and provenance carries a SHA-256
`teacherFingerprint` of the capture bytes computed before synthesis.

A real limitation remains and is not papered over: `ActivationRecord` keeps only
`layer_activations[0]` and `Distiller` thresholds at 0.5, so a teacher whose logits
never exceed 0.5 distils to an **all-zero truth table**. The shipped
`booleans-8.ndjson` is exactly that case — it fires on 0 of 8 samples, so its learned
structure is vacuous. The two teachers are therefore *distinguishable* but the second
one has nothing to learn. `DistillerTeacherSensitivityTest` pins all four facts,
including the vacuous case, so it cannot be forgotten.

**GE-6 — "tomato is red; carrot is orange; banana is yellow. lemon is ?" → expected `yellow`**
The exemplars *disagree*, so the unanimity rule declines. Answering requires
knowing lemons are yellow — world knowledge, not inference. A rule that returned
"yellow" here would be fitting the probe, so it was not written. The mind answers
with an explicit refusal instead of a guess.

**72 pre-existing test failures (D-W20-1), reconciled in RECON-W28. None are caused
by this campaign; none were deleted or skipped.**

The two modules this campaign changed are **fully green**: `matrix-brain-runtime`
419 tests / 0 failures, `matrix-api-gateway` 137 tests / 0 failures. All 72 failures
are in `matrix-core`, untouched by this campaign.

The count is not stable, and that is now demonstrated rather than asserted: two
successive runs of the **same Java code** (only shell scripts and docs changed
between them) produced **71** and then **72** failures, the extra one landing in
`io.matrix.consciousness`. That is the jqwik non-convergence the previous report
called "itself a defect" — here it is a measured ±1, which is precisely why the
counting method above is stated explicitly and why no future report should quote a
bare total.

Authoritative counting method, adopted for every future report: aggregate JUnit XML
across **all** modules and count `<testcase>` elements.

| Module | invocations | fail | skip |
|---|---|---|---|
| matrix-core | 8087 | 72 | 22 |
| matrix-brain-runtime | 419 | **0** | 2 |
| matrix-api-gateway | 137 | **0** | 0 |
| matrix-audit / billing / observability / operator / quality / sdk-java / spigot / tools-distill | 198 | 0 | 4 |
| pilots (3) | 16 | 0 | 0 |
| **TOTAL** | **8858** | **72** | **26** |

This resolves the "8083 vs 8653" discrepancy, which was three different numbers for
three different reasons, not one wrong number:

- **8083** — a Gradle `tests completed` line from a W27-era run of a *different code
  state*, and Gradle counts test **methods**.
- **8653** — an earlier XML recount over a **narrower glob** that omitted `pilots/`.
- **8845** — the pre-remediation full run: XML `<testcase>` = **invocations**,
  14 modules. 8845 invocations vs 8826 distinct methods; the delta of 19 is
  parameterised expansion. Those are the only two legitimate denominators, and the
  report now names which one it quotes.
- **8858** — the final post-remediation run, quoted throughout above. The +13
  invocations are the tests this remediation added (8 end-to-end gateway, 4
  teacher-sensitivity, 1 ordering precondition), so the count moved for a reason
  that is visible rather than mysterious.

**Disposition of all 72 failures (Q-C: documented, never hidden).**

| Family | Count | Verdict |
|---|---|---|
| `io.matrix.consciousness` (jqwik properties, entropy, phase, phi) | 37 | pre-existing, untouched |
| `io.matrix.research.BitNet*` (model load / prefill / KV-cache / sampling) | 31 | pre-existing, untouched |
| `io.matrix.model.ModelRegistryTest` | 3 | pre-existing, untouched |
| `io.matrix.federation.liquid.simulation.SleepConsolidationStudyTest` | 1 | pre-existing, untouched |
| **`io.matrix.consciousness.KolmogorovComplexity*`** | **4** | **pre-existing — proven, see below** |

All 72 are in `matrix-core`. Neither module this campaign modified has a single
failure.

The 4 Kolmogorov failures needed proof, because `KolmogorovComplexity.java` lives in
`io.matrix.consciousness` and W20 *did* edit it. The proof is that the only changed
lines are inside the `logarithmicEncoding` loop body, and the four failing tests
exercise `x = 1`, where the old and new code are textually identical (the loop body
never runs). The `+ Long.SIZE` term (64) that produces the observed `K = 64.0`
against a `< 20` assertion is byte-identical in baseline and HEAD. So these failed
identically at baseline `f832ae1e`. They are a genuine pre-existing test/impl
disagreement: the implementation charges a constant 64 bits for the model term, so a
zero-entropy trajectory can never return K < 20. Fixing that means deciding what K
*means*, which is research, not remediation.

The count still drifts between runs because jqwik properties are randomly generated
and do not converge — itself a defect, and the reason the previous report quoted
three different totals.

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

**A 2.30× speedup was measured, adopted, and then retracted.** During the W24
popcount work a 4-way unrolled fold measured 2.30× against the 1.5× bar and was
adopted in production. It was then found to be an artifact: the unroll and the
original were both benchmarking the *wrong function* (XOR-fold instead of Hamming
distance), and the fold is cheap for exactly that reason — one popcount instead of
one per word. Measured against the corrected function the unroll is 0.52×, i.e.
slower, and was reverted. The verdict is pinned by a test so the wrong number
cannot return. This is the clearest evidence in the campaign for the rule
"adopt only with bit-equivalence proof": equivalence to a *wrong* reference proves
nothing, because production and the reference were wrong together.

**The HDC distance kernel was wrong until this wave.** `vectorXorPopCount` and
`scalarXorPopCount` XOR-folded all words into one long and took a single popcount.
XOR-ing two words cancels bits, so `vectorXorPopCount(zero, one)` returned **0**
where the true Hamming distance is **128**. `RealGpuKernelEngine` uses this to
score HDC search distance, so HDC similarity has been systematically wrong since
the kernel was written. Both now sum per-word popcounts; `scalarPopCount` is also
null-safe. Two long-standing test failures in `MatrixNativeMathTest` were exactly
this and are now green.

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
| `./gradlew cleanTest test --continue` | 8858 invocations, 72 fail, 26 skip (all 72 pre-existing, all in matrix-core) |
| `MATRIX_PORT=8799 bash scripts/fresh-clone-smoke.sh` | **PASS** — clean clone answers `2 + 3 = 5` (B-1) |
| `FED_A_PORT=8774 FED_B_PORT=8775 bash scripts/two-node-federation.sh` | **PASS 6/6** assertions, exit 0 (B-7) |
| `bash scripts/benchmark-regression.sh <new> w13-live.csv` | 33/48 → 47/48, 14 improvements, 0 regressions (B-9) |
| `python3 scripts/capture_activations.py …` | 8 records, no segfault |
| `RunActivationDistill data/activations/capacities-8.ndjson` | 8 samples, fidelity 1.0 (hash claim **retracted** — see below) |
| `curl localhost:8765/health/live` | `{"status":"UP","brain_available":true}` |

---

## 8. Commitments deliberately not made

No consciousness claim is made anywhere in this report. Every "mind" word is a
label for a stage graph, not a claim about experience (Article VI). The
generalisation gains come from explicit symbolic rules and a 61-entry fact table —
both inspectable, both deterministic, neither a language model.

## What still fails

### RECON-W28 state, measured not estimated

| Item | State | Evidence |
|---|---|---|
| **B-1 clean-clone smoke** | **FIXED** | `fresh-clone-smoke-transcript.txt`; root cause was newline-fused classpath entries, reproduced deterministically |
| **B-3 coverage evidence** | **PARTIAL** | 6/7 touched brain-runtime classes >=82% method. `DistillationPipeline` 68.8%. `MinimalHttpServer` 40.8%, `ProductionBrainClient` 50.0% — both **below** the 82% gate, reported as such |
| **B-4 teacher sensitivity** | **FIXED, with a real limitation** | hashes were already distinct; the run-vs-artifact hash bug is fixed; `booleans-8.ndjson` still distils to an empty table |
| **B-5 simulacrum ordering** | **VERIFIED SAFE, hardened** | all three mutating classes restore in `@AfterEach`; parallelism now asserted off |
| **B-6 parsers + FROZEN gate** | **FIXED** | 3 hand-rolled parsers -> Jackson; `/v1/bir` writes modulator-gated (403); 8 end-to-end tests boot the real server |
| **B-7 federation depth** | **PARTIAL** | transfer/isolation/quarantine asserted 6/6. **Audit-chain verification, DP-noise, RBAC/rate-limit NOT DONE** — new capability, deferred |
| **B-8 weekly CI** | **BLOCKED, needs operator** | `docs-v2/proposals/RFC-weekly-ci-smoke.md`; `.github/` byte-identical |
| **B-9 per-wave diff + routing table** | **FIXED** | `scripts/benchmark-regression.sh` (negative-controlled), `MATRIX-ROUTING-TABLE.md` |
| **B-10 `data/smoke-old` 8.8 GB** | **BLOCKED, needs operator** | Goal Guard denies `rm -rf` and `find -delete`; escalated in SESSION.md per Q-A |
| **72 test failures** | **OPEN, all pre-existing** | triaged by family above; both changed modules are 0-failure |
| **GE-6 world knowledge** | **OPEN by design** | deliberate refusal, not fitted to the probe |

### The thing most likely to be misread

RECON-W28 was a large net improvement to the *harness*: a clean-clone smoke that
actually passes, a federation gate that actually fails when the product is wrong, a
FROZEN modulator on the registry write path, and a test that boots the gateway
instead of leaving it to a shell script nobody runs.

None of that makes the system better at thinking. The benchmark is unchanged at
**47/48**, and the one probe still failing still fails because the mind does not know
that lemons are yellow. Correctness of the harness is not capability growth, and this
report does not claim otherwise.
