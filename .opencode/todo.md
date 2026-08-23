# Mission: Реализовать все запрошенные изменения и планы (SPEC-002 этап A — BIR-ядро)

> Восстановлено из WAL.md (сессия 2026-08-22) и аудита рабочего дерева.
> Квота субагентов исчерпана (WAL «Известные проблемы») — выполнение и верификация ведутся напрямую с tool-evidence.

## M1: Закрытие пробелов SPEC-002 этап A | status: completed
### T1.1: FR-A1 — TT→CLAUSESET минимизация + Avro | agent:direct
- [x] S1.1.1: Espresso/QM-минимизация в `BirCompiler.ttToClauseSet` через `TruthTableMinimizer` (QM exact k≤12, Espresso k>12 с восстановлением точности) | size:M | evidence: git diff BirCompiler.java + TtToClauseSetConverter.java (делегирование)
- [x] S1.1.2: `BirAvroCodec` + схема `resources/avro/bir_artifact.avsc` (additive, backward-compatible) + round-trip тесты | size:M | evidence: файлы существуют; mpdt_neuron.avsc не тронут (FROZEN OK); BirAvroCodecTest присутствует

### T1.2: FR-A2 — BDD-пакет исправления + property-тесты | agent:direct
- [x] S1.2.1: Багфиксы `BddForm.eval` (обход по var[node], level-skip) и const-0 root; `Builder.build(inputBits, provenance, root)` с явным root | size:M | evidence: git diff BddForm.java; вызовы обновлены в BirCompiler/BirEvaluateBenchmark/тестах
- [x] S1.2.2: jqwik `BirPropertiesTest` (каноничность, eval(BDD)=eval(TT), round-trip) | size:S | evidence: файл существует, аннотации @Property посчитаны (121 всего по пакету)

### T1.3: Тестовое покрытие пакета bir | agent:direct
- [x] S1.3.1: Заполнены пустые `JvmSimdBackendTest`, `TtToBddConverterTest`; новые BirInvariantsTest, конвертер-тесты, адаптер-тесты, BirRegistryTest, LineageLedgerTest, BirAvroCodecTest | size:L | evidence: все файлы в git status (untracked/modified), 121 @Test/@Property в пакете
- [x] S1.3.2: Прогон пакета `io.matrix.bir.*` зелёный (0 failed, 0 skipped) | size:S | evidence: gradlew :matrix-core:test BUILD SUCCESSFUL (job_9f49c994); JUnit XML: 15 классов, tests=121 failed=0 skipped=0

### T1.4: INV-2…INV-5 инварианты | agent:direct
- [x] S1.4.1: INV-2 `BirLimits` (matrix.bir.max-literals / MATRIX_BIR_MAX_LITERALS, дефолт 4096) вместо хардкода; подключение в MatrixMetrics; литерал-валидация ClauseSetForm | size:M | evidence: BirLimits.java соответствует комментарию application.properties дословно; FpgaBackend делегирует
- [x] S1.4.2: INV-3 fidelity-валидация ([0,1], NaN) + lossy-фабрики TtForm.lossy / ClauseSetForm.lossy | size:S | evidence: git diff BirForm/TtForm/ClauseSetForm/ClauseSetToTtConverter
- [x] S1.4.3: INV-4 BirRegistry.register отвергает provenance null/blank/"unknown" | size:S | evidence: git diff BirRegistry.java
- [x] S1.4.4: INV-5 `BirCompilerMutationTest` (мутант-гейт в обычной сюите, workflows не тронуты) | size:M | evidence: файл существует; .github/workflows не изменён (FROZEN OK)

### T1.5: FR-A3/A4 — бенчмарк и deprecation | agent:direct
- [x] S1.5.1: JMH legacy vs BIR baseline (`legacyTruthTableEval`) | size:S | evidence: git diff BirEvaluateBenchmark.java; matrix-core/build/results/jmh/results.json существует
- [x] S1.5.2: `@Deprecated` на neuron.TruthTable и neuron.DecisionTree со ссылкой на адаптеры | size:S | evidence: grep подтверждает @Deprecated в обоих файлах

### T1.6: Документация и исследование | agent:DocReviewer
- [x] S1.6.1: `docs/research/notes/BIR-THEORY-scan.md` (living-статус, источники L1–L4, рекомендации) | size:M | evidence: заголовок проверен, статус living, лестница источников на месте
- [x] S1.6.2: AGENTS.md переписан (normative, обзор, команды, ограничения, CI/CD) | size:M | evidence: git diff; все ссылки на документы проверены на существование; README «Конфигурация» есть
- [x] S1.6.3: WAL.md и context.md обновлены по протоколу | size:S | evidence: все 5 секций шаблона WAL присутствуют; claims сверены с реальностью (121 тест, results.json, deprecated)
- [x] S1.6.4: Doc Review верификация документации (claims↔reality, forbidden claims Art.VI, FROZEN-целостность) | size:S | evidence: Verdict: PASS — нарушений нет

## Известные ограничения (вне объёма этой миссии)
- Критерий A (миграция 61 call-site `.evaluate(`) — эпик на отдельные сессии (WAL «Следующее действие» #1)

## M2: BDD-алгебра + каноническая эквивалентность (WAL #2+#3) | status: completed
### T2.1: apply/ITE на computed-cache | agent:Worker(ses_fd4cffac)
- [x] S2.1.1: `apply(BddForm, Op)` / `not()` / `constant()` через рекурсивный ITE, локальный memo per-call, материализация через Builder.mk (reduction сохранён) | size:L
- [x] S2.1.2: Удаление мёртвых полей uniqueTable/computedCache; честный javadoc | size:S

### T2.2: equivalentTo независимо от порядка построения | agent:Worker
- [x] S2.2.1: Мемоизированное структурное сравнение (var-alignment), ограничение = одинаковый порядок переменных | size:M

### T2.3: Тесты и верификация | agent:Worker → Reviewer
- [x] S2.3.1: BirBooleanAlgebraTest: 7 операций vs TT-семантика, инволюция not, константы, order-independence эквивалентности + jqwik свойства | size:M
- [x] S2.3.2: Прогон io.matrix.bir.* зелёный (включая прежние 121 теста) | size:S | depends:S2.1.1,S2.2.1,S2.3.1 | evidence: gradlew BUILD SUCCESSFUL job_0c3be865 после чистки битого каталога test-results; XML: 16 классов, tests=131 failed=0 skipped=0

### T2.4: Исследование коннекторов (видение пользователя) | agent:Planner(ses_fd4cfddd)
- [x] S2.4.1: Карта концепций «коннекторов» в docs/: категории, реализовано vs spec-only, рекомендация следующего milestone | size:S | evidence: grep по docs/ = 0 совпадений «коннектор/connector/дендрит/синапс»; таксономии нет — ближайшие концепции: BDD-композиции apply/ITE (реализовано в M2), BRC-цепочки (reasoning/, max 5 шагов), голосование клауз (этап B tsetlin); рекомендация — этап B как следующий milestone
