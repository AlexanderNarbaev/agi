# TESTING-STRATEGY — How Tests Are Authored and Run

**Status:** normative · **Version:** v1 · **Date:** 2026-09-07

> Companion to ENGINEERING-INVARIANTS.md §2. Describes the test
> taxonomy, when each kind applies, and how to run them.

---

## Test Taxonomy

| Kind | Tag | When | Speed | Cost |
|---|---|---|---|---|
| Unit | `@Tag("unit")` | Always | ms | 1x |
| Property | `@Tag("property")` | For invariants | ms–s | 1x |
| Integration | `@Tag("integration")` | Multi-class | s | 2x |
| End-to-End | `@Tag("e2e")` | Cross-module | s | 5x |
| EXP | `@Tag("exp")` | Full system | s–m | 10x |
| TLA+ TLC | separate | Decision specs | s | 5x |

Default `./gradlew test` runs **unit + property + integration**.
EXP and TLA+ require explicit task (`./gradlew expTest`, `./gradlew tlaCheck`).

---

## Test Authoring Rules

### 1. Naming
- Test class: same name as class under test + `Test`.
- Test method: `condition_under_test_expected_outcome`.
  - `kMax_is_20_even_with_21_inputs_throws_IllegalArgument`
  - `textEncoder_same_input_same_output_100_times`
  - `consciousLoop_budget_exceeded_raises_budget_exception`

### 2. Structure
- AAA pattern: arrange, act, assert.
- One logical assertion per test (may have multiple AssertJ `assertThat`).
- Always include a message with the actual value when relevant.

### 3. Determinism
- Fixed RNG seed in property tests: `0x5A5A5A5A`.
- No real time: inject `Clock` (or use `LoopClock.frozen()`).
- No real RNG: inject `RandomSource` interface.
- No real network: use mocks/fakes.

### 4. Fixtures
- Reuse via `@BeforeEach` for cheap setup.
- Heavy setup: `@TempDir` or in-memory backends.
- Cross-test: Never share mutable state.

### 5. Coverage
- New class ≥82% method coverage.
- If below, justify in PR or add tests.
- Branch coverage tracked but not gated.

---

## Property Tests (jqwik or JUnit-Quickcheck)

When to use:
- Invariants across many inputs
- Determinism assertions
- Equivalence (implemented vs spec)

Example:
```java
@Property
void bir_step_is_deterministic(
    @ForAll("validInput") int[] input
) {
    BirUnit u = BirUnit.of(W, threshold, K_MAX);
    boolean first = u.fire(input);
    for (int i = 0; i < 100; i++) {
        boolean next = u.fire(input);
        assertThat(next).isEqualTo(first);
    }
}
```

---

## Integration Tests

When:
- Multiple classes wired via CDI
- Database/SQLite interaction
- Kafka-like in-memory broker

Rules:
- Use in-memory backends by default.
- Tear down state in `@AfterEach` or with `@TempDir`.

---

## EXP Tests

When:
- Full pipeline exercise
- Numerical claim is being made
- Hypothesis is being verified or refuted

Rules:
- Annotated `@EnabledIfSystemProperty(named = "exp.run", matches = "true")`.
- Or use a gradle tag-based filter.
- Each EXP test produces a markdown report via
  `EXPReporter.publish(...)`.

EXP Markdown report template:
```
# EXP-<NNNN>-report — <title>

## Hypothesis
<one paragraph>

## Setup
- code version: <sha>
- environment: <CPU/GPU/mem>
- input: <reference to corpus>

## Procedure
1. ...
2. ...

## Results
| metric | value | units |
...

## Conclusion
- ACCEPTED / REFUTED / INCONCLUSIVE

## Artifacts
- `research/reports/EXP-<NNNN>-data.csv`
```

---

## Anti-patterns

- ❌ Tests that depend on each other's order
- ❌ Tests that print to stdout instead of asserting
- ❌ Tests that don't actually exercise the code path
- ❌ Tests named `test1`, `testFoo` (non-descriptive)
- ❌ Flaky tests — quarantined, then fixed (CONSTITUTION VI)
- ❌ Adding `Thread.sleep` to "wait for the thing"
- ❌ Ignoring exceptions (`catch (Exception e) {}`)

---

## Coverage Targets per Layer

| Layer | Target | Rationale |
|---|---|---|
| `bir/` | 90% | Core math, fail loud if broken |
| `reasoning/` | 85% | Decision path |
| `consciousness/` | 85% | Decision path |
| `actions/` | 85% | Decision path |
| `ethics/` | 95% | FROZEN must be rigorously tested |
| `memory/` | 80% | Persistence paths |
| `mediator/` | 80% | Coordination |
| `noosphere/` | 75% | Sharing layer |
| `tools/distill/` | 70% | Offline, less critical |
| `cli/` | 60% | Wiring |
