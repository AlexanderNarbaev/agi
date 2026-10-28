# Capability registry

**Status:** authoritative · 2026-10-26 · RECON-W38.4 (owner directive §20)

Every capability this project states publicly is listed here with the
evidence behind it. The registry exists because the project's front page
advertised numbers that no measurement in this repository produced, and
because "every claim is backed by tests, benchmarks, or TLA+ specs" was
itself an unbacked claim.

**The rules**

1. A capability is **VERIFIED** only when a named command or report in this
   repository produces the number. The command is recorded so the claim can
   be re-checked.
2. A number that no measurement produced is **UNSOURCED**. It is removed
   from the published front page; it is not quietly retained.
3. A number that a measurement contradicts is **CORRECTED** to the measured
   value, with the original recorded here.
4. A claim about *readiness* ("production-grade") is not a measurement and is
   not presented as one.
5. `ModuleLifecycleRegistryTest` and `CapabilityClaimsGuardTest` keep the
   front page and this registry in step, so a claim cannot drift back.

---

## The registry

| # | Claim as published | State | Evidence | Measured value |
|---|---|---|---|---|
| C-01 | "724 Tests Passing" | **CORRECTED** | `./gradlew test` (the executed count); `./scripts/count-tests.sh` (the source count) | **9,432** tests executed across 14 modules and 1,282 suites, **26 skipped, 0 failures, 0 errors** (`BUILD SUCCESSFUL in 23m 6s`). The source count is **8,898** test methods; the difference is `@ParameterizedTest` cases, which expand into several executed tests. The figure 724 appears in no measurement in this repository; it is also the tokenizer's vocabulary size, which is how it most likely arose. |
| C-01b | "0 LLM calls in the decision path" (new headline stat) | **VERIFIED** | `.github/workflows/ci.yml` — the Article-I gate; `RuntimeLlmGuardTest` scans the runtime modules for legacy LLM imports | **0** — the gate is a required CI check, so the number cannot silently regress. |
| C-01c | "9 TLA+ specs, model-checked in CI" (new headline stat) | **VERIFIED** | `.github/workflows/tla.yml`; per-specification detail in `docs-v2/engineering/TLA-STATUS.md` | All **9** specifications parse and all 9 are model-checked; 4 are fully green and 5 report a recorded property failure, which `docs-v2/engineering/TLA-STATUS.md` states explicitly. |
| C-02 | "33,000× Faster than LLMs" / "Energy Efficiency 33,000× vs GPU-based LLMs" | **CORRECTED** | `docs-v2/research/BENCHMARK-REPORT-W620.md` §4 | The benchmark report records a **100,000× speedup** against a synthetic LLM ops/sec model, with an energy ratio of 0.00x. The published 33,000× matches no measurement: it is derived in `docs-v2/story/overview.md` by dividing 10–100 *tokens/sec* by a *<10 ms inference* — two different quantities. The card was also labelled "Energy Efficiency" while carrying a speedup. |
| C-03 | "100% Constitution Compliant" | **VERIFIED (as gates, not as a number)** | `.github/workflows/ci.yml` — the Article-I gate, `DecisionPathAuditorTest` (13 tests) and `RuntimeLlmGuardTest` run on every build | The compliance mechanisms are real and enforced in CI. "100%" is not a measured quantity, so the page now says which gates are enforced rather than asserting a percentage. |
| C-04 | "75% accuracy on logic tasks" / "vs 25% random baseline" | **CORRECTED (baseline)** | `docs-v2/research/BENCHMARK-REPORT-W620.md` §1 | BIR **75%** on 100 puzzles — correct. The random baseline is **30%**, not 25%. Note also that BIR (75.0%) **ties** the heuristic baseline (75.0%); the page implied a gain over a simpler method that the report does not show. |
| C-05 | "89.9% AUC" / "learns from 50 examples, not 50,000" | **VERIFIED** | `docs-v2/research/BENCHMARK-REPORT-W620.md` §2 | HDC AUC **89.9%** (0.899) with **50** samples to reach 90%, against a random AUC of 25.3% (0.253). |
| C-06 | "92% success in Minecraft survival" | **VERIFIED** | `docs-v2/waves/WAL-620.md` (Minecraft pilot, W610–W611) and `docs-v2/waves/WAL-614.md` | 92% success with 0 deaths for a 5-node federation, 78% for a single agent, in the headless Minecraft simulator. The page now cites the wave. |
| C-07 | "100% Federation Resilience — consensus with 50% nodes killed" | **VERIFIED** | `docs-v2/research/FEDERATION-SCALE-REPORT-W635.md` | 100% consensus with 10%, 30% and 50% of nodes killed, at 100, 500 and 1000 nodes. |
| C-08 | "90% accuracy retention from LLM distillation" | **UNSOURCED — removed** | none | No measurement of distillation accuracy retention exists in this repository. The `DISTILLATION-LEDGER.md` records the pipeline, not a retention figure. The claim is removed from the front page. |
| C-09 | "production-grade hybrid AI framework" | **REWORDED** | — | A statement about readiness, not a measurement. The page now describes what the project is and points at the gates that are enforced, instead of asserting a grade. |
| C-10 | "Every claim is backed by tests, benchmarks, or TLA+ specs." | **REWORDED** | this registry | The sentence was false when written — four of the numbers above had no backing. The page now links to this registry, which states per claim what the evidence is, including where the evidence is absent. |

## How the test count is measured

The published figure is the **executed** count — the number of tests the
build actually ran, and the number that passed:

```bash
./gradlew test
python3 - <<'PYEOF'
import re, glob
total = skipped = failures = errors = 0
for f in glob.glob('**/build/test-results/test/*.xml', recursive=True):
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"',
                  open(f, errors='ignore').read())
    if m:
        t, s, fl, e = map(int, m.groups())
        total += t; skipped += s; failures += fl; errors += e
print(total, skipped, failures, errors)
PYEOF
```

Measured on 2026-10-28: **9 432 tests, 26 skipped, 0 failures, 0 errors**,
`BUILD SUCCESSFUL in 23m 6s`.

There is also a **source** count, produced in seconds by
`./scripts/count-tests.sh` (**8,898**). It counts every source line declaring
`@Test`, `@ParameterizedTest`, `@TestFactory` or `@RepeatedTest` under
`src/test`, per module. It is the same counting method the build itself uses
(`:matrix-core:generateBuildInfo`), so the number a binary prints and this
number are produced the same way.

The two differ by 534: a `@ParameterizedTest` method is one source line and
several executed tests, and a `@Disabled` method is counted by the source
count but not executed. The page publishes the **executed** figure, because
"Tests Passing" is a claim about an execution and must therefore be backed by
one — which is exactly the error the original "724" was.

## Not capability claims

For the avoidance of doubt, these are **not** claims about capability and are
not in the registry:

- the interactive modulator simulator on the front page — it is a client-side
  illustration of a rule, not a measurement;
- the narrative in `docs-v2/story/` — it is a story, and where it states a
  number it links to the report that produced it;
- the TLA+ specification status — it is recorded per specification in
  `docs-v2/engineering/TLA-STATUS.md`, including the five specifications whose
  properties genuinely fail.

## What was found, and what was deliberately not changed

The published page was corrected, not deleted, and the claims that *are*
backed by a measurement were kept. Two things were left alone on purpose:

- **`docs-v2/story/overview.md` still contains the 33,000× comparison.** It is
  a narrative page, and rewriting a story page is a different task from
  correcting a published capability claim. The registry names the file and
  line so the inconsistency is visible rather than hidden.
- **The benchmark report itself was not edited.** A measurement record is
  evidence: changing it to agree with a page would destroy its value. The
  report says 100,000×; the page now says what the report says.
