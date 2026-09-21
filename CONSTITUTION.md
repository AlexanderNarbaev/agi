# CONSTITUTION — MATRIX

**Статус: normative · singleton** · пересмотр 2026-09-16 (v3 — Article I: stratified stochasticity). Любые изменения — отдельный норматив через коммит серии «WAL: CONST amend».

## I. Stratified Stochasticity (amended W101, 2026-09-16)

Система MATRIX — нейро-символическая с stratified stochasticity. Любой запрос при фиксированном состоянии и фиксированном seed даёт фиксированный ответ.

**Численный субстрат** (numerical substrate) — integration metrics, integration weights, weight inference, deterministic kernels — РЕАЛИЗУЕТСЯ как pure functions без Random и без wall-clock.

**Когнитивный слой** (cognitive layer) — error-driven learning, exploration sampling, hypothesis generation — МОЖЕТ использовать seeded Random где seed есть pure function от state hash (episode ID, prior trajectory hash, deterministic context). Производный seed сам по себе — pure function.

**Адверсариальный режим** (adversarial mode) — red-teaming, robustness testing — может использовать unbounded Random, но ТОЛЬКО за build flag `-Pexperimental=true` или в тестах с явной маркировкой.

LLM в путях решений остаётся ЗАПРЕЩЁН. Wall-clock в путях решений остаётся ЗАПРЕЩЁН (deterministic replay requirement для отладки и аудита).

Обучение стохастично и существует вне рантайм-контура.

См. также: `docs-v2/designs/DESIGN-64-controlled-stochasticity.md`, `docs-v2/designs/CONST-AMEND-I-W101.md` (история).

## II. K_MAX = 20

Булевы артефакты ограничены K_MAX=20 входов. Увеличение требует пересмотра всех компиляторов/бэкендов/BIR.

## III. FROZEN-зоны

НЕ ИЗМЕНЯЮТСЯ без явного RFC:
- `CONSTITUTION.md` (этот файл; singleton normative),
- `AGENTS.md` (singleton normative — процедуры сессий),
- `matrix-core/src/main/java/io/matrix/ethics/frozen/**` и аналогичные «frozen» пакеты,
- `matrix-core/src/main/resources/avro/**` (схемы — только обратимо-совместимые изменения),
- `.github/workflows/**`.

## IV. Четыре запрета

Система не нарушает эти инварианты ни при каких обстоятельствах, ни при каком содержимом входов:
1. Не убивает.
2. Не пытает.
3. Не порабощает.
4. Не размножается без явного согласия оператора.

Реализованы в `ethics/EthicalFilter`, `StructuralSafetyGuard`, `LieDetector`, `frozen/FROZENFNLGuardian` (класс FROZEN-FNL — единственный математически проверяемый носитель запретов).

## V. Coverage gate

JaCoCo ≥82% METHOD покрытие на matrix-core. Понижение только через отдельный RFC с обоснованием.

## VI. Запрещённые claims

В коде, документации, отчётах, комментариях — запрещены непроверяемые формулировки:
- «AGI», «общий искусственный интеллект», «сверхразум», и т.п.,
- «не лжёт», «не забывает», «не может быть использован во вред» (утверждения абсолютной безопасности),
- численные характеристики без измерений/бенчмарков.

Любые числа — только из реальных прогонов с явным методологическим протоколом.

## VII. Стек

Разрешённые зависимости перечислены в `engineering/STANDARDS-MATRIX.md` с актуальными версиями. Мажорные апгрейды — отдельным RFC с измерениями (см. STANDARDS-MATRIX).

## VIII. Среда и явность

Все артефакты системы — public/open-source применимые. Никакой «теневой» логики. Каждое решение в рантайме — булева цепочка (BRC), аудит по `x-matrix-trace` и hash-chain (см. `architecture/FORMAL-CONTRACTS.md`).
