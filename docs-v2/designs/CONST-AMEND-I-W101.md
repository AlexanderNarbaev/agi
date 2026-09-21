# CONSTITUTION Amendment — Article I (Determinism) — W101

**Date:** 2026-09-16
**Status:** PROPOSED → RATIFIED after W101 close-out review
**Author:** autonomous-execution-wave (per user authorization: "В случае необходимости мы можем поправить CONSTITUTION")
**Motivated by:** DESIGN-64-controlled-stochasticity.md, W92-W100 deliverables
**Effective on:** W101 close-out (after 24h review window)

## Proposed change

### CURRENT Article I (v2, 2026-08-26):

> Система MATRIX — детерминированная нейро-символическая. Любой запрос при
> фиксированном состоянии даёт фиксированный ответ. Рантайм-контур решений
> НЕ вызывает LLM, НЕ использует случайность и wall-clock в путях решений.
> Обучение стохастично и существует вне рантайм-контура.

### PROPOSED Article I (v3, post-W101):

> Система MATRIX — нейро-символическая с stratified stochasticity. Любой
> запрос при фиксированном состоянии и фиксированном seed даёт фиксированный
> ответ.
>
> **Численный субстрат** (numerical substrate) — integration metrics,
> integration weights, weight inference, deterministic kernels — РЕАЛИЗУЕТСЯ
> как pure functions без Random и без wall-clock.
>
> **Когнитивный слой** (cognitive layer) — error-driven learning, exploration
> sampling, hypothesis generation — МОЖЕТ использовать seeded Random где seed
> есть pure function от state hash (episode ID, prior trajectory hash,
> deterministic context). Производный seed сам по себе — pure function.
>
> **Адверсариальный режим** (adversarial mode) — red-teaming, robustness
> testing — может использовать unbounded Random, но ТОЛЬКО за build flag
> `-Pexperimental=true` или в тестах с явной маркировкой.
>
> LLM в путях решений остаётся ЗАПРЕЩЁН (CONSTITUTION §I legacy constraint,
> не подлежит пересмотру в этом amendment).
>
> Wall-clock в путях решений остаётся ЗАПРЕЩЁН (deterministic replay
> requirement для отладки и аудита).
>
> Обучение стохастично и существует вне рантайм-контура — без изменений.

## Rationale

1. **Human cognition is not strictly deterministic** — empirical observation
   (the user's own statement): the same person answering the same question
   twice can produce different answers depending on mood, attention, social
   context, hormonal state. A system that wants to "reason, err, learn,
   synthesize knowledge" must allow some variability.

2. **Pure determinism prevents exploration** — if every cycle produces the
   same output, there is no hypothesis generation, no error correction,
   no learning loop. This contradicts DESIGN-58 L5+ capabilities.

3. **Stratified stochasticity preserves reproducibility where it matters** —
   numerical substrate (math, weights, kernels) stays pure-deterministic.
   Cognitive layer uses seeded Random where the seed is itself a pure
   function of state. Same state → same exploration sequence.

4. **Minsky/Bernstein/Ashby foundations** — W95 cybernetic/constructivist
   research shows that real cognitive systems require bounded exploration.
   Pure determinism is incompatible with Anokhin's reverse-afferent
   feedback, Ashby's ultrastability, Minsky's society of mind.

5. **CONSTITUTION VI alignment** — variability is allowed in cognitive
   mechanisms (action selection, learning updates) but the integration
   metrics remain measurement substrates. We are not claiming consciousness
   in random states.

## Backwards compatibility

- All W60-W91 metrics remain pure-deterministic (Φ_binary, ΦR, ΦF, C_N,
  ticklingFlag, Φ_linGauss, PhiID).
- Existing tests that rely on deterministic behavior of these metrics
  continue to pass.
- New code in `io.matrix.cognitive.*` (CognitiveError, CognitiveErrorStream,
  ExploratoryActionSampler, ErrorDrivenLearner) was already written
  with seeded Random and snapshot hashes; this amendment formalizes
  what was already implemented.

## Affected files

- `CONSTITUTION.md` — Article I rewrite (post-ratification)
- `docs-v2/designs/DESIGN-64-controlled-stochasticity.md` — referenced

## Verification

After ratification, run full test suite to confirm no regressions.
Tests that should remain green:
- All Φ_*, IntegrationMetrics*, TicklingDetector, ConsciousBrain
- All W76-W100 specific tests
- All existing integration tests

New tests must use deterministic state (seeded Random) and expose
their seed source. Tests with unbounded Random must use
`@EnabledIfEnvironmentVariable(named = "EXPERIMENTAL", matches = "true")`
or be marked `@Tag("experimental")` to opt-in.
